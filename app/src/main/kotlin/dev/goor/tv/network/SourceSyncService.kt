package dev.goor.tv.network

import android.util.Log
import dev.goor.tv.data.db.dao.ChannelDao
import dev.goor.tv.data.db.dao.SourceDao
import dev.goor.tv.data.model.Source
import dev.goor.tv.data.model.SourceType
import dev.goor.tv.data.model.headersMap
import io.ktor.client.HttpClient
import io.ktor.client.request.*
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.contentLength
import io.ktor.http.isSuccess
import io.ktor.utils.io.core.readText
import io.ktor.utils.io.exhausted
import io.ktor.utils.io.readRemaining
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentHashMap

private val PREFIX_REGEX = Regex("""^[\[(]?([A-Z]{2,5})[)\]|:\s]""")

fun extractPrefix(name: String): String? =
    PREFIX_REGEX.find(name.trimStart())?.groupValues?.get(1)

class SourceSyncService(
    private val sourceDao: SourceDao,
    private val channelDao: ChannelDao,
    private val httpClient: HttpClient,
    private val xtreamApi: XtreamApi,
) {
    // Serializes concurrent syncs of the same source. Different sources can still
    // sync in parallel. Without this, two overlapping sync(source) calls race on the
    // read-merge-write of channel rows and can drop user data (favorites,
    // lastWatchedAt) from whichever fetched list lost the race.
    private val syncMutexes = ConcurrentHashMap<Long, Mutex>()
    private fun mutexFor(sourceId: Long): Mutex = syncMutexes.computeIfAbsent(sourceId) { Mutex() }

    /**
     * Syncs all non-manual sources whose [Source.lastSyncedAt] is older than
     * [throttleMs]. Each failed source is retried with exponential backoff up to
     * [MAX_ATTEMPTS] before being reported. Returns one [SyncFailure] per source
     * that ultimately failed.
     *
     * Manual refresh from settings should pass `throttleMs = 0L` to bypass the
     * skip filter.
     */
    suspend fun syncAll(throttleMs: Long = DEFAULT_THROTTLE_MS): List<SyncFailure> {
        val now = System.currentTimeMillis()
        return sourceDao.getAll().first()
            .filter { it.type != SourceType.MANUAL }
            .filter { (it.lastSyncedAt ?: 0L) + throttleMs <= now }
            .mapNotNull { source ->
                syncWithRetry(source)?.let {
                    Log.e(TAG, "Sync gave up on '${source.name}': ${it.message}")
                    SyncFailure(source.name, it)
                }
            }
    }

    private suspend fun syncWithRetry(source: Source): Throwable? {
        var lastErr: Throwable? = null
        for (attempt in 0 until MAX_ATTEMPTS) {
            try {
                sync(source)
                return null
            } catch (e: Exception) {
                lastErr = e
                Log.w(TAG, "Sync attempt ${attempt + 1}/$MAX_ATTEMPTS failed for '${source.name}': ${e.message}")
                if (!e.isRetriableSyncError()) {
                    Log.w(TAG, "Permanent failure for '${source.name}', not retrying: ${e.message}")
                    return e
                }
                if (attempt < MAX_ATTEMPTS - 1) delay(backoffMs(attempt))
            }
        }
        return lastErr
    }

    suspend fun sync(source: Source) = mutexFor(source.id).withLock {
        val fetched: List<dev.goor.tv.data.model.Channel>
        var discoveredEpgUrl: String? = null
        when (source.type) {
            SourceType.M3U -> {
                val response = httpClient.get(source.url) {
                    source.headersMap().forEach { (k, v) -> header(k, v) }
                }
                // Without this check a 5xx body would feed into the parser, which
                // would happily return an empty list — and `replaceForSourcePreservingUserData`
                // would then wipe the source's channels (and the user data attached
                // to them). Throwing lets `syncWithRetry` retry, then surface the
                // failure to the user.
                if (!response.status.isSuccess()) {
                    throw SyncException.Http(
                        response.status.value,
                        "M3U HTTP ${response.status.value} for '${source.name}'",
                    )
                }
                val content = readBoundedBody(response, source)
                val parsed = M3uParser.parse(source.id, content)
                fetched = parsed.channels
                discoveredEpgUrl = parsed.urlTvg
            }
            SourceType.XTREAM -> fetched = xtreamApi.fetchLiveChannels(source)
            SourceType.MANUAL -> return@withLock
        }
        // Derive `group` from name prefix when upstream didn't supply one. Done
        // outside the DAO call so the DAO method stays purely about persistence.
        val prepared = fetched.map { ch -> ch.copy(group = ch.group ?: extractPrefix(ch.name)) }
        // Atomic: read existing user data, merge, delete+insert — all in one
        // transaction. Concurrent reads can't observe the table mid-delete, and
        // a partial-insert failure rolls back the delete.
        channelDao.replaceForSourcePreservingUserData(source.id, prepared)
        sourceDao.updateLastSyncedAt(source.id, System.currentTimeMillis())
        // Auto-discover EPG URL from playlist header on first sync if user hasn't set one.
        if (source.type == SourceType.M3U && source.epgUrl.isNullOrBlank() && !discoveredEpgUrl.isNullOrBlank()) {
            sourceDao.updateEpgUrl(source.id, discoveredEpgUrl)
        }
    }

    /**
     * Reads the playlist body, bounding the download so a huge or hostile playlist
     * can't OOM a low-RAM TV box. Fails early when the server declares an over-cap
     * length, otherwise reads at most [MAX_PLAYLIST_BYTES] and fails if the stream
     * isn't exhausted by then.
     */
    private suspend fun readBoundedBody(response: HttpResponse, source: Source): String {
        response.contentLength()?.let { declared ->
            if (declared > MAX_PLAYLIST_BYTES) {
                throw SyncException.TooLarge(
                    "M3U too large ($declared bytes, max $MAX_PLAYLIST_BYTES) for '${source.name}'",
                )
            }
        }
        val channel = response.bodyAsChannel()
        val packet = channel.readRemaining(MAX_PLAYLIST_BYTES)
        if (!channel.exhausted()) {
            throw SyncException.TooLarge("M3U exceeds $MAX_PLAYLIST_BYTES bytes for '${source.name}'")
        }
        return packet.readText()
    }

    companion object {
        private const val TAG = "SourceSync"
        private const val MAX_ATTEMPTS = 3
        private const val MAX_PLAYLIST_BYTES = 50L * 1024 * 1024  // 50 MB
        private const val INITIAL_BACKOFF_MS = 30_000L
        private const val MAX_BACKOFF_MS = 5L * 60L * 1000L  // 5 min
        private const val DEFAULT_THROTTLE_MS = 60L * 60L * 1000L  // 1 hour

        /** Exponential backoff capped at [MAX_BACKOFF_MS]. Pure for testability. */
        internal fun backoffMs(attempt: Int): Long =
            minOf(INITIAL_BACKOFF_MS shl attempt, MAX_BACKOFF_MS)
    }
}

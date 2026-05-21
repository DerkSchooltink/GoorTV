package dev.goor.tv.network

import android.util.Log
import dev.goor.tv.data.db.dao.ChannelDao
import dev.goor.tv.data.db.dao.ProgrammeDao
import dev.goor.tv.data.db.dao.SourceDao
import dev.goor.tv.data.model.Programme
import dev.goor.tv.data.model.Source
import dev.goor.tv.data.model.SourceType
import dev.goor.tv.data.model.headersMap
import dev.goor.tv.data.model.isEpgEligible
import dev.goor.tv.util.ChannelNameNormalizer
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.Url
import io.ktor.http.isSuccess
import io.ktor.http.encodeURLParameter
import io.ktor.utils.io.jvm.javaio.toInputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.InputStream
import java.util.zip.GZIPInputStream

class EpgSyncService(
    private val sourceDao: SourceDao,
    private val programmeDao: ProgrammeDao,
    private val channelDao: ChannelDao,
) {
    /**
     * Syncs EPG for all eligible sources, skipping any whose [Source.lastEpgSyncedAt] is
     * younger than [throttleMs]. Each failed source is retried with exponential backoff
     * up to [MAX_ATTEMPTS]. Returns one [Throwable] per source that ultimately failed.
     */
    suspend fun syncAll(throttleMs: Long = DEFAULT_THROTTLE_MS): List<Throwable> {
        val now = System.currentTimeMillis()
        return sourceDao.getAll().first()
            .filter { it.isEpgEligible() }
            .filter { (it.lastEpgSyncedAt ?: 0L) + throttleMs <= now }
            .mapNotNull { source ->
                syncWithRetry(source)?.also {
                    Log.e(TAG, "EPG sync gave up on '${source.name}' after $MAX_ATTEMPTS attempts: ${it.message}")
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
                Log.w(TAG, "EPG sync attempt ${attempt + 1}/$MAX_ATTEMPTS failed for '${source.name}': ${e.message}")
                if (attempt < MAX_ATTEMPTS - 1) delay(backoffMs(attempt))
            }
        }
        return lastErr
    }

    /** Manual sync — ignores throttle. Persists last-synced timestamp or error. */
    suspend fun sync(source: Source) {
        if (!source.isEpgEligible()) return
        val url = epgUrlFor(source) ?: return
        try {
            val response: HttpResponse = httpClient.get(url) {
                source.headersMap().forEach { (k, v) -> header(k, v) }
            }
            if (!response.status.isSuccess()) {
                error("EPG HTTP ${response.status.value} for '${source.name}'")
            }
            val input = response.bodyAsChannel().toInputStream().maybeGunzip()
            input.use { processXmltv(source.id, it) }
            sourceDao.markEpgSynced(source.id, System.currentTimeMillis())
        } catch (e: Exception) {
            sourceDao.setEpgError(source.id, e.message ?: e::class.simpleName)
            throw e
        }
    }

    /**
     * Parses an XMLTV stream into programmes + a display-name index, then runs the
     * tvg-id backfill. Extracted from [sync] as a test seam — callers without a live
     * HTTP response can drive it directly with a `ByteArrayInputStream`.
     */
    internal suspend fun processXmltv(sourceId: Long, stream: InputStream) = withContext(Dispatchers.IO) {
        // Drop existing programmes for this source so re-syncs don't leave stale rows
        // when a programme's startMs shifts (REPLACE only matches exact PK).
        programmeDao.deleteBySource(sourceId)
        val buffer = ArrayList<Programme>(BATCH_SIZE)
        // Display-name key -> tvg-id, used to backfill channels whose provider didn't
        // ship an epg_channel_id. Canonical key wins; looser variants only fill gaps.
        val nameToTvgId = HashMap<String, String>()
        XmltvParser.parse(
            input = stream,
            onChannel = { ch ->
                ch.displayNames.forEach { dn ->
                    val keys = ChannelNameNormalizer.keys(dn)
                    keys.firstOrNull()?.let { nameToTvgId[it] = ch.tvgChannelId }
                    for (i in 1 until keys.size) nameToTvgId.putIfAbsent(keys[i], ch.tvgChannelId)
                }
                // Also index the id itself — many providers reuse it as a display name.
                ChannelNameNormalizer.canonical(ch.tvgChannelId)
                    ?.let { nameToTvgId.putIfAbsent(it, ch.tvgChannelId) }
            },
        ) { p ->
            buffer.add(
                Programme(
                    sourceId = sourceId,
                    tvgChannelId = p.tvgChannelId,
                    startMs = p.startMs,
                    endMs = p.endMs,
                    title = p.title,
                    description = p.description,
                    category = p.category,
                    iconUrl = p.iconUrl,
                )
            )
            if (buffer.size >= BATCH_SIZE) {
                programmeDao.insertAll(buffer)
                buffer.clear()
            }
        }
        if (buffer.isNotEmpty()) programmeDao.insertAll(buffer)

        backfillTvgIds(sourceId, nameToTvgId)
    }

    /**
     * For channels in [sourceId] with a blank `tvgChannelId`, look up a matching id by
     * normalising the channel name against [nameToTvgId] (built from XMLTV display-names).
     * Tries the most-specific key first, then progressively looser variants. All matched
     * updates are applied in a single transaction to avoid WAL thrash.
     */
    private suspend fun backfillTvgIds(sourceId: Long, nameToTvgId: Map<String, String>) {
        if (nameToTvgId.isEmpty()) return
        val candidates = channelDao.getMissingTvgIdsBySource(sourceId)
        if (candidates.isEmpty()) return
        val assignments = candidates.mapNotNull { ch ->
            val hit = ChannelNameNormalizer.keys(ch.name).firstNotNullOfOrNull { nameToTvgId[it] }
            hit?.let { ch.id to it }
        }
        if (assignments.isNotEmpty()) {
            channelDao.applyTvgChannelIdAssignments(assignments)
            Log.i(TAG, "Backfilled tvg-id for ${assignments.size}/${candidates.size} channels in source $sourceId")
        }
    }

    private fun epgUrlFor(source: Source): String? = when (source.type) {
        SourceType.M3U -> source.epgUrl?.takeIf { it.isNotBlank() }
        SourceType.XTREAM -> {
            val u = source.username?.takeIf { it.isNotBlank() } ?: return null
            val p = source.password?.takeIf { it.isNotBlank() } ?: return null
            val parsed = Url(source.url)
            val port = parsed.specifiedPort.takeIf { it > 0 } ?: parsed.protocol.defaultPort
            val base = if (port > 0) "${parsed.protocol.name}://${parsed.host}:$port"
                       else "${parsed.protocol.name}://${parsed.host}"
            "$base/xmltv.php?username=${u.encodeURLParameter()}&password=${p.encodeURLParameter()}"
        }
        SourceType.MANUAL -> null
    }

    /**
     * Sniffs the first two bytes — `1f 8b` indicates gzip. Wraps stream with [GZIPInputStream]
     * if so; otherwise returns the (still-buffered) original stream.
     */
    private fun InputStream.maybeGunzip(): InputStream {
        val buffered = if (this is BufferedInputStream) this else BufferedInputStream(this)
        buffered.mark(2)
        val b1 = buffered.read()
        val b2 = buffered.read()
        buffered.reset()
        return if (b1 == 0x1f && b2 == 0x8b) GZIPInputStream(buffered) else buffered
    }

    companion object {
        private const val TAG = "EpgSyncService"
        private const val BATCH_SIZE = 500
        private const val DEFAULT_THROTTLE_MS = 6L * 3600L * 1000L
        private const val RETENTION_MS = 6L * 3600L * 1000L
        private const val MAX_ATTEMPTS = 3
        private const val INITIAL_BACKOFF_MS = 30_000L
        private const val MAX_BACKOFF_MS = 5L * 60L * 1000L  // 5 min

        /** Exponential backoff capped at [MAX_BACKOFF_MS]. Pure for testability. */
        internal fun backoffMs(attempt: Int): Long =
            minOf(INITIAL_BACKOFF_MS shl attempt, MAX_BACKOFF_MS)
    }
}

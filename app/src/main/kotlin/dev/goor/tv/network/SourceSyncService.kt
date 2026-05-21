package dev.goor.tv.network

import android.util.Log
import dev.goor.tv.data.db.dao.ChannelDao
import dev.goor.tv.data.db.dao.SourceDao
import dev.goor.tv.data.model.Source
import dev.goor.tv.data.model.SourceType
import dev.goor.tv.data.model.headersMap
import io.ktor.client.call.*
import io.ktor.client.request.*
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
) {
    // Serializes concurrent syncs of the same source. Different sources can still
    // sync in parallel. Without this, two overlapping sync(source) calls race on the
    // read-merge-write of channel rows and can drop user data (favorites,
    // lastWatchedAt) from whichever fetched list lost the race.
    private val syncMutexes = ConcurrentHashMap<Long, Mutex>()
    private fun mutexFor(sourceId: Long): Mutex = syncMutexes.computeIfAbsent(sourceId) { Mutex() }

    suspend fun syncAll(): List<Throwable> {
        return sourceDao.getAll().first()
            .filter { it.type != SourceType.MANUAL }
            .mapNotNull { source ->
                runCatching { sync(source) }
                    .exceptionOrNull()
                    ?.also { Log.e("SourceSync", "Failed to sync '${source.name}': ${it.message}") }
            }
    }

    suspend fun sync(source: Source) = mutexFor(source.id).withLock {
        val fetched: List<dev.goor.tv.data.model.Channel>
        var discoveredEpgUrl: String? = null
        when (source.type) {
            SourceType.M3U -> {
                val content: String = httpClient.get(source.url) {
                    source.headersMap().forEach { (k, v) -> header(k, v) }
                }.body()
                val parsed = M3uParser.parse(source.id, content)
                fetched = parsed.channels
                discoveredEpgUrl = parsed.urlTvg
            }
            SourceType.XTREAM -> fetched = XtreamApi.fetchLiveChannels(source)
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
}

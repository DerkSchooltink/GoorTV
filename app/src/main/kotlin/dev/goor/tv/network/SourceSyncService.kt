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

private val PREFIX_REGEX = Regex("""^[\[(]?([A-Z]{2,5})[)\]|:\s]""")

fun extractPrefix(name: String): String? =
    PREFIX_REGEX.find(name.trimStart())?.groupValues?.get(1)

class SourceSyncService(
    private val sourceDao: SourceDao,
    private val channelDao: ChannelDao,
) {
    suspend fun syncAll(): List<Throwable> {
        return sourceDao.getAll().first()
            .filter { it.type != SourceType.MANUAL }
            .mapNotNull { source ->
                runCatching { sync(source) }
                    .exceptionOrNull()
                    ?.also { Log.e("SourceSync", "Failed to sync '${source.name}': ${it.message}") }
            }
    }

    suspend fun sync(source: Source) {
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
            SourceType.MANUAL -> return
        }
        // Preserve user data (favorites, last watched) when reinserting after sync
        val existing = channelDao.getBySourceOnce(source.id)
        val userDataByUrl = existing.associate { it.url to Pair(it.isFavorite, it.lastWatchedAt) }
        val merged = fetched.map { ch ->
            val (fav, lastWatched) = userDataByUrl[ch.url] ?: Pair(false, null)
            ch.copy(
                isFavorite = fav,
                lastWatchedAt = lastWatched,
                group = ch.group ?: extractPrefix(ch.name),
            )
        }
        channelDao.replaceForSource(source.id, merged)
        sourceDao.updateLastSyncedAt(source.id, System.currentTimeMillis())
        // Auto-discover EPG URL from playlist header on first sync if user hasn't set one.
        if (source.type == SourceType.M3U && source.epgUrl.isNullOrBlank() && !discoveredEpgUrl.isNullOrBlank()) {
            sourceDao.updateEpgUrl(source.id, discoveredEpgUrl)
        }
    }
}

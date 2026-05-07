package dev.goor.tv.network

import android.util.Log
import dev.goor.tv.data.db.dao.ChannelDao
import dev.goor.tv.data.db.dao.SourceDao
import dev.goor.tv.data.model.Source
import dev.goor.tv.data.model.SourceType
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
        return sourceDao.getAll().first().mapNotNull { source ->
            runCatching { sync(source) }
                .exceptionOrNull()
                ?.also { Log.e("SourceSync", "Failed to sync '${source.name}': ${it.message}") }
        }
    }

    suspend fun sync(source: Source) {
        val fetched = when (source.type) {
            SourceType.M3U -> {
                val content: String = httpClient.get(source.url).body()
                M3uParser.parse(source.id, content)
            }
            SourceType.XTREAM -> XtreamApi.fetchLiveChannels(source)
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
    }
}

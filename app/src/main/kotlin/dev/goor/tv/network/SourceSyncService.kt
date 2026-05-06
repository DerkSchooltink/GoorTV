package dev.goor.tv.network

import dev.goor.tv.data.db.dao.ChannelDao
import dev.goor.tv.data.db.dao.SourceDao
import dev.goor.tv.data.model.Source
import dev.goor.tv.data.model.SourceType
import io.ktor.client.call.*
import io.ktor.client.request.*
import kotlinx.coroutines.flow.first

class SourceSyncService(
    private val sourceDao: SourceDao,
    private val channelDao: ChannelDao,
) {
    suspend fun syncAll() {
        sourceDao.getAll().first().forEach { source ->
            runCatching { sync(source) }
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
            ch.copy(isFavorite = fav, lastWatchedAt = lastWatched)
        }
        channelDao.deleteBySource(source.id)
        channelDao.insertAll(merged)
    }
}

package dev.goor.tv.network

import dev.goor.tv.data.db.dao.ChannelDao
import dev.goor.tv.data.db.dao.SourceDao
import dev.goor.tv.data.model.Source
import dev.goor.tv.data.model.SourceType
import io.ktor.client.call.*
import io.ktor.client.request.*

class SourceSyncService(
    private val sourceDao: SourceDao,
    private val channelDao: ChannelDao,
) {
    suspend fun syncAll() {
        sourceDao.getAll().collect { sources ->
            sources.forEach { sync(it) }
        }
    }

    suspend fun sync(source: Source) {
        val channels = when (source.type) {
            SourceType.M3U -> {
                val content: String = httpClient.get(source.url).body()
                M3uParser.parse(source.id, content)
            }
            SourceType.XTREAM -> XtreamApi.fetchLiveChannels(source)
        }
        channelDao.deleteBySource(source.id)
        channelDao.insertAll(channels)
    }
}

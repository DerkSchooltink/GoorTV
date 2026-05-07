package dev.goor.tv.util

import dev.goor.tv.data.model.Channel
import dev.goor.tv.data.model.Source
import dev.goor.tv.data.model.SourceType

fun testChannel(
    id: Long = 1L,
    sourceId: Long = 1L,
    name: String = "Test Channel",
    url: String = "http://example.com/stream.m3u8",
    group: String? = null,
    logoUrl: String? = null,
    isFavorite: Boolean = false,
    lastWatchedAt: Long? = null,
) = Channel(
    id = id,
    sourceId = sourceId,
    name = name,
    url = url,
    group = group,
    logoUrl = logoUrl,
    isFavorite = isFavorite,
    lastWatchedAt = lastWatchedAt,
)

fun testSource(
    id: Long = 1L,
    name: String = "Test Source",
    type: SourceType = SourceType.M3U,
    url: String = "http://example.com/playlist.m3u",
    lastSyncedAt: Long? = null,
) = Source(id = id, name = name, type = type, url = url, lastSyncedAt = lastSyncedAt)

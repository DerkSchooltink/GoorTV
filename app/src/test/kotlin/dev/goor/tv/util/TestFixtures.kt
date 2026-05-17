package dev.goor.tv.util

import dev.goor.tv.data.model.Channel
import dev.goor.tv.data.model.Programme
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
    tvgChannelId: String? = null,
) = Channel(
    id = id,
    sourceId = sourceId,
    name = name,
    url = url,
    group = group,
    logoUrl = logoUrl,
    isFavorite = isFavorite,
    lastWatchedAt = lastWatchedAt,
    tvgChannelId = tvgChannelId,
)

fun testSource(
    id: Long = 1L,
    name: String = "Test Source",
    type: SourceType = SourceType.M3U,
    url: String = "http://example.com/playlist.m3u",
    lastSyncedAt: Long? = null,
    epgUrl: String? = null,
    lastEpgSyncedAt: Long? = null,
    epgLastError: String? = null,
) = Source(
    id = id,
    name = name,
    type = type,
    url = url,
    lastSyncedAt = lastSyncedAt,
    epgUrl = epgUrl,
    lastEpgSyncedAt = lastEpgSyncedAt,
    epgLastError = epgLastError,
)

fun testProgramme(
    sourceId: Long = 1L,
    tvgChannelId: String = "test.tv",
    startMs: Long = 0L,
    endMs: Long = 3_600_000L,
    title: String = "Test Show",
    description: String? = null,
    category: String? = null,
    iconUrl: String? = null,
) = Programme(
    sourceId = sourceId,
    tvgChannelId = tvgChannelId,
    startMs = startMs,
    endMs = endMs,
    title = title,
    description = description,
    category = category,
    iconUrl = iconUrl,
)

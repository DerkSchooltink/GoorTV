package dev.goor.tv.data.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "channels",
    foreignKeys = [ForeignKey(
        entity = Source::class,
        parentColumns = ["id"],
        childColumns = ["sourceId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [
        Index("sourceId"),
        Index(value = ["group", "name"]),
        Index("tvgChannelId"),
        // Covers the (sourceId, tvgChannelId) lookups that `ProgrammeDao.observeAllNow`
        // does via its EXISTS subquery and that `observeWindowForChannels` does via
        // `WHERE sourceId = ? AND tvgChannelId IN (…)`. Without it SQLite falls back to
        // scanning all of `channels` per programme, which can hang the guide for minutes
        // on large playlists.
        Index(value = ["sourceId", "tvgChannelId"]),
    ]
)
data class Channel(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sourceId: Long,
    val name: String,
    val url: String,
    val group: String? = null,
    val logoUrl: String? = null,
    val isFavorite: Boolean = false,
    val lastWatchedAt: Long? = null,
    val tvgChannelId: String? = null,
)

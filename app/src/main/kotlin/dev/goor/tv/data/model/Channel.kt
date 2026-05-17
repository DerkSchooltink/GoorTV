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
    indices = [Index("sourceId"), Index(value = ["group", "name"]), Index("tvgChannelId")]
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

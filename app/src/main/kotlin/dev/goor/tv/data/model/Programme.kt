package dev.goor.tv.data.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(
    tableName = "programmes",
    primaryKeys = ["sourceId", "tvgChannelId", "startMs"],
    foreignKeys = [ForeignKey(
        entity = Source::class,
        parentColumns = ["id"],
        childColumns = ["sourceId"],
        onDelete = ForeignKey.CASCADE,
    )],
    indices = [
        Index("sourceId"),
        Index(value = ["sourceId", "tvgChannelId", "endMs"]),
    ],
)
data class Programme(
    val sourceId: Long,
    val tvgChannelId: String,
    val startMs: Long,
    val endMs: Long,
    val title: String,
    val description: String? = null,
    val category: String? = null,
    val iconUrl: String? = null,
)

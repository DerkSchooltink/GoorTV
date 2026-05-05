package dev.goor.tv.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class SourceType { M3U, XTREAM }

@Entity(tableName = "sources")
data class Source(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val type: SourceType,
    val url: String,
    val username: String? = null,
    val password: String? = null,
)

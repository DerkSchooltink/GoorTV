package dev.goor.tv.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class SourceType { M3U, XTREAM, MANUAL }

@Entity(tableName = "sources")
data class Source(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val type: SourceType,
    val url: String,
    val username: String? = null,
    val password: String? = null,
    // null = show all (legacy), "" = show nothing (default for new), "AU|US" = pipe-separated allow-list
    val includedGroups: String? = "",
    val lastSyncedAt: Long? = null,
    // one "Name: Value" entry per line; null means no custom headers
    val headers: String? = null,
    // 0 = unlimited
    val maxConcurrentStreams: Int = 0,
)

fun Source.headersMap(): Map<String, String> =
    headers?.lines()
        ?.filter { ':' in it }
        ?.associate { line ->
            val idx = line.indexOf(':')
            line.substring(0, idx).trim() to line.substring(idx + 1).trim()
        }
    ?: emptyMap()

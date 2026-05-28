package dev.goor.tv.data.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

enum class SourceType { M3U, XTREAM, MANUAL }

/**
 * Container/extension for Xtream live stream URLs (`/live/user/pass/<id>.<ext>`).
 * Providers vary: some serve MPEG-TS, others HLS. Persisted by enum name (Room).
 */
enum class XtreamOutput(val ext: String) { TS("ts"), M3U8("m3u8") }

@Entity(
    tableName = "sources",
    // Rejects duplicate (type, url) at the DB layer — the UI also pre-checks and
    // surfaces a friendly snackbar, but the index is the backstop for any DAO
    // caller (e.g. the manual-source bootstrap race in HomeViewModel).
    indices = [Index(value = ["type", "url"], unique = true)],
)
data class Source(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val type: SourceType,
    val url: String,
    // Xtream credentials — encrypted at rest via SecretConverter (A3.1, Path B).
    val username: Secret? = null,
    val password: Secret? = null,
    // Container for Xtream live URLs. Ignored for M3U/MANUAL.
    val xtreamOutput: XtreamOutput = XtreamOutput.TS,
    // null = show all (legacy), "" = show nothing (default for new), "AU|US" = pipe-separated allow-list
    val includedGroups: String? = "",
    val lastSyncedAt: Long? = null,
    // one "Name: Value" entry per line; null means no custom headers
    val headers: String? = null,
    // 0 = unlimited
    val maxConcurrentStreams: Int = 0,
    // XMLTV URL. For M3U: user-provided, or auto-extracted from `#EXTM3U url-tvg="..."`.
    // For XTREAM: ignored (xmltv.php is derived from base URL + credentials).
    val epgUrl: String? = null,
    val lastEpgSyncedAt: Long? = null,
    val epgLastError: String? = null,
)

/**
 * True when this source can supply EPG data. UI surfaces ("Sync EPG" button, status line)
 * gate on this so we don't show controls whose action would be a silent no-op in
 * [dev.goor.tv.network.EpgSyncService.sync].
 */
fun Source.isEpgEligible(): Boolean = when (type) {
    SourceType.XTREAM -> !username?.value.isNullOrBlank() && !password?.value.isNullOrBlank()
    SourceType.M3U -> !epgUrl.isNullOrBlank()
    SourceType.MANUAL -> false
}

// RFC 7230 token: visible ASCII excluding separators. Values: no CR/LF (header injection guard).
private val HEADER_NAME_REGEX = Regex("""^[!#$%&'*+\-.^_`|~0-9A-Za-z]+$""")

fun Source.headersMap(): Map<String, String> =
    headers?.lines()
        ?.filter { ':' in it }
        ?.mapNotNull { line ->
            val idx = line.indexOf(':')
            val name = line.substring(0, idx).trim()
            val value = line.substring(idx + 1).trim()
            if (!HEADER_NAME_REGEX.matches(name)) return@mapNotNull null
            if (value.any { it == '\r' || it == '\n' }) return@mapNotNull null
            name to value
        }
        ?.toMap()
    ?: emptyMap()

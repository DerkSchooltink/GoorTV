package dev.goor.tv.cast

import android.net.Uri
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.google.android.gms.cast.MediaInfo
import com.google.android.gms.cast.MediaLoadRequestData
import com.google.android.gms.cast.MediaMetadata
import com.google.android.gms.cast.framework.CastContext
import com.google.android.gms.cast.framework.CastSession
import com.google.android.gms.cast.framework.SessionManagerListener
import com.google.android.gms.common.images.WebImage
import dev.goor.tv.data.model.Channel
import org.json.JSONObject

/**
 * Compose-friendly bridge to the Cast SDK's [SessionManagerListener].
 *
 * Returns the current [CastSession] (nullable) and updates the State whenever a
 * session starts, resumes, or ends. Caller can react via `LaunchedEffect(session) { ... }`
 * to pause local playback and load media on the receiver.
 */
@Composable
fun rememberCastSession(): State<CastSession?> {
    val context = LocalContext.current
    val session = remember { mutableStateOf<CastSession?>(null) }
    DisposableEffect(Unit) {
        val sessionManager = CastContext.getSharedInstance(context).sessionManager
        session.value = sessionManager.currentCastSession
        val listener = object : SessionManagerListener<CastSession> {
            override fun onSessionStarted(s: CastSession, sessionId: String) {
                session.value = s
            }
            override fun onSessionResumed(s: CastSession, wasSuspended: Boolean) {
                session.value = s
            }
            override fun onSessionEnded(s: CastSession, error: Int) {
                session.value = null
            }
            override fun onSessionStartFailed(s: CastSession, error: Int) {
                session.value = null
            }
            override fun onSessionResumeFailed(s: CastSession, error: Int) {
                session.value = null
            }
            override fun onSessionSuspended(s: CastSession, reason: Int) {}
            override fun onSessionStarting(s: CastSession) {}
            override fun onSessionEnding(s: CastSession) {}
            override fun onSessionResuming(s: CastSession, sessionId: String) {}
        }
        sessionManager.addSessionManagerListener(listener, CastSession::class.java)
        onDispose {
            sessionManager.removeSessionManagerListener(listener, CastSession::class.java)
        }
    }
    return session
}

/**
 * Loads [channel] on the receiver attached to [session]. No-op if the session has
 * no [com.google.android.gms.cast.framework.media.RemoteMediaClient] yet (the
 * SDK creates it asynchronously after the session is established).
 *
 * Custom [headers] are embedded in `MediaInfo.customData` under the `headers` key.
 * The default media receiver (`CC1AD845`) doesn't honor them — they're carried so
 * a future custom receiver can pick them up.
 */
fun loadOnCastSession(
    session: CastSession,
    channel: Channel,
    headers: Map<String, String> = emptyMap(),
) {
    val client = session.remoteMediaClient ?: run {
        Log.w(TAG, "RemoteMediaClient not ready; load skipped")
        return
    }

    val metadata = MediaMetadata(MediaMetadata.MEDIA_TYPE_TV_SHOW).apply {
        putString(MediaMetadata.KEY_TITLE, channel.name)
        channel.logoUrl?.takeIf { it.isNotBlank() }?.let {
            addImage(WebImage(Uri.parse(it)))
        }
    }

    val customData = if (headers.isNotEmpty()) {
        JSONObject().put("headers", JSONObject(headers.toMap()))
    } else null

    val mediaInfo = MediaInfo.Builder(channel.url)
        .setStreamType(MediaInfo.STREAM_TYPE_LIVE)
        .setContentType(inferContentType(channel.url))
        .setMetadata(metadata)
        .apply { customData?.let { setCustomData(it) } }
        .build()

    client.load(
        MediaLoadRequestData.Builder()
            .setMediaInfo(mediaInfo)
            .setAutoplay(true)
            .build(),
    )
}

/**
 * URL-suffix-based MIME inference. IPTV providers almost always serve HLS, so
 * unknown URLs default to `application/x-mpegURL`. The default media receiver
 * supports HLS, DASH, and MP4 — but **not** raw MPEG-TS over HTTP.
 */
internal fun inferContentType(url: String): String {
    val path = url.substringBefore('?').lowercase()
    return when {
        path.endsWith(".m3u8") -> "application/x-mpegURL"
        path.endsWith(".mpd") -> "application/dash+xml"
        path.endsWith(".ts") -> "video/mp2t"
        path.endsWith(".mp4") -> "video/mp4"
        path.endsWith(".mkv") -> "video/x-matroska"
        else -> "application/x-mpegURL"
    }
}

private const val TAG = "Cast"

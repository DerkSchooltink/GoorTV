@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package dev.goor.tv.ui.screens.player

import android.content.Context
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import dev.goor.tv.R

/**
 * Side-effect seam for the playback engine. Production wraps Media3's [ExoPlayer]
 * (see [rememberPlayerEngine]); androidTests pass [NoOpPlayerEngine] so the
 * test process doesn't try to construct a real player + decoder pipeline.
 *
 * Why the seam exists: without it, creating [ExoPlayer] inside the screen's
 * `remember{}` block crashes the Compose test runner with SIGKILL on the device
 * (composition is torn down, "No compose hierarchies found" by the time the
 * test asserts). With this seam the test process stays alive and the screen
 * can be exercised without real media.
 */
interface PlayerEngine {
    val isBuffering: MutableState<Boolean>
    val errorMessage: MutableState<String?>

    fun prepare(uri: String, headers: Map<String, String>)
    fun pause()
    fun play()
    fun release()

    /** Returns a [View] to host inside an `AndroidView`. May be null for fakes. */
    fun createPlayerView(context: Context): View?

    /**
     * Apply the user-selected aspect-ratio resize mode to the player view.
     * No-ops for fakes.
     */
    fun applyResizeMode(view: View?, mode: ResizeMode)

    enum class ResizeMode { FIT, FILL, ZOOM }
}

private class ExoPlayerEngine(private val context: Context) : PlayerEngine {
    private val player: ExoPlayer = ExoPlayer.Builder(context).build()
    override val isBuffering = mutableStateOf(true)
    override val errorMessage = mutableStateOf<String?>(null)

    private val listener = object : Player.Listener {
        override fun onPlaybackStateChanged(state: Int) {
            isBuffering.value = state == Player.STATE_BUFFERING
        }
        override fun onPlayerError(error: PlaybackException) {
            errorMessage.value = error.message ?: context.getString(R.string.player_playback_failed)
            isBuffering.value = false
        }
    }

    init { player.addListener(listener) }

    override fun prepare(uri: String, headers: Map<String, String>) {
        errorMessage.value = null
        isBuffering.value = true
        if (headers.isEmpty()) {
            player.setMediaItem(MediaItem.fromUri(uri))
        } else {
            val source = DefaultMediaSourceFactory(
                DefaultDataSource.Factory(
                    context,
                    DefaultHttpDataSource.Factory().setDefaultRequestProperties(headers),
                )
            ).createMediaSource(MediaItem.fromUri(uri))
            player.setMediaSource(source)
        }
        player.prepare()
        player.play()
    }

    override fun pause() = player.pause()
    override fun play() = player.play()
    override fun release() {
        player.removeListener(listener)
        player.release()
    }

    override fun createPlayerView(context: Context): View = PlayerView(context).apply {
        this.player = this@ExoPlayerEngine.player
        useController = false
    }

    override fun applyResizeMode(view: View?, mode: PlayerEngine.ResizeMode) {
        (view as? PlayerView)?.resizeMode = when (mode) {
            PlayerEngine.ResizeMode.FIT -> AspectRatioFrameLayout.RESIZE_MODE_FIT
            PlayerEngine.ResizeMode.FILL -> AspectRatioFrameLayout.RESIZE_MODE_FILL
            PlayerEngine.ResizeMode.ZOOM -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
        }
    }
}

/** No-op engine for tests + Compose previews. */
object NoOpPlayerEngine : PlayerEngine {
    override val isBuffering = mutableStateOf(false)
    override val errorMessage = mutableStateOf<String?>(null)
    override fun prepare(uri: String, headers: Map<String, String>) = Unit
    override fun pause() = Unit
    override fun play() = Unit
    override fun release() = Unit
    override fun createPlayerView(context: Context): View? = null
    override fun applyResizeMode(view: View?, mode: PlayerEngine.ResizeMode) = Unit
}

@Composable
fun rememberPlayerEngine(): PlayerEngine {
    val context = LocalContext.current
    return remember { ExoPlayerEngine(context) }
}

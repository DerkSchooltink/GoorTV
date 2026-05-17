package dev.goor.tv.ui.screens.player

import android.app.Activity
import android.view.WindowManager
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Warning
import androidx.mediarouter.app.MediaRouteButton
import com.google.android.gms.cast.framework.CastButtonFactory
import dev.goor.tv.cast.loadOnCastSession
import dev.goor.tv.cast.rememberCastSession
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.border
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.goor.tv.data.model.Programme
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import coil3.compose.AsyncImage
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

private enum class AspectRatioMode(val label: String) {
    FIT("Fit"),
    FILL("Fill"),
    ZOOM("Zoom"),
    RATIO_16_9("16:9"),
    RATIO_4_3("4:3"),
}

private val AspectRatioMode.next: AspectRatioMode
    get() = AspectRatioMode.entries[(ordinal + 1) % AspectRatioMode.entries.size]

@Composable
fun PlayerScreen(
    channelId: Long,
    onBack: () -> Unit,
    vm: PlayerViewModel = koinViewModel(parameters = { parametersOf(channelId) }),
) {
    val channel by vm.channel.collectAsStateWithLifecycle()
    val headers by vm.headers.collectAsStateWithLifecycle()
    val stopped by vm.stopped.collectAsStateWithLifecycle()
    val nowAndNext by vm.nowAndNext.collectAsStateWithLifecycle()
    val nowMs by vm.nowMs.collectAsStateWithLifecycle()
    val castSession by rememberCastSession()
    val isCasting = castSession != null
    val context = LocalContext.current
    val activity = context as? Activity

    LaunchedEffect(stopped) {
        if (stopped) onBack()
    }

    val player = remember { ExoPlayer.Builder(context).build() }
    var hasError by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isBuffering by remember { mutableStateOf(true) }
    var showControls by remember { mutableStateOf(false) }
    var aspectRatioMode by remember { mutableStateOf(AspectRatioMode.FIT) }
    val backFocusRequester = remember { FocusRequester() }
    var backFocused by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        try { backFocusRequester.requestFocus() } catch (_: IllegalStateException) {}
    }

    LaunchedEffect(showControls) {
        if (showControls) {
            delay(4000)
            showControls = false
        }
    }

    channel?.let { ch ->
        LaunchedEffect(ch.url, headers, castSession) {
            hasError = false
            errorMessage = null
            val session = castSession
            if (session != null) {
                // Cast path — pause local, hand off to receiver.
                player.pause()
                isBuffering = false
                loadOnCastSession(session, ch, headers)
            } else {
                // Local path — (re-)prepare ExoPlayer.
                isBuffering = true
                if (headers.isEmpty()) {
                    player.setMediaItem(MediaItem.fromUri(ch.url))
                } else {
                    val mediaSource = DefaultMediaSourceFactory(
                        DefaultDataSource.Factory(
                            context,
                            DefaultHttpDataSource.Factory().setDefaultRequestProperties(headers),
                        )
                    ).createMediaSource(MediaItem.fromUri(ch.url))
                    player.setMediaSource(mediaSource)
                }
                player.prepare()
                player.play()
            }
        }
    }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                isBuffering = state == Player.STATE_BUFFERING
            }
            override fun onPlayerError(error: PlaybackException) {
                hasError = true
                isBuffering = false
                errorMessage = error.message ?: "Playback failed"
            }
        }
        player.addListener(listener)
        onDispose { player.removeListener(listener) }
    }

    DisposableEffect(Unit) {
        activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val insetsController = activity?.window?.let { window ->
            WindowCompat.getInsetsController(window, window.decorView).also {
                it.hide(WindowInsetsCompat.Type.systemBars())
                it.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        }
        onDispose {
            activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            insetsController?.show(WindowInsetsCompat.Type.systemBars())
            player.release()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        // Player — always mounted while channel is loaded so it isn't recreated on error
        if (channel != null) {
            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        this.player = player
                        useController = false
                    }
                },
                update = { pv ->
                    pv.resizeMode = when (aspectRatioMode) {
                        AspectRatioMode.FIT -> AspectRatioFrameLayout.RESIZE_MODE_FIT
                        AspectRatioMode.FILL -> AspectRatioFrameLayout.RESIZE_MODE_FILL
                        AspectRatioMode.ZOOM -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                        AspectRatioMode.RATIO_16_9 -> AspectRatioFrameLayout.RESIZE_MODE_FIT
                        AspectRatioMode.RATIO_4_3 -> AspectRatioFrameLayout.RESIZE_MODE_FIT
                    }
                },
                modifier = (when (aspectRatioMode) {
                    AspectRatioMode.RATIO_16_9 -> Modifier.aspectRatio(16f / 9f).align(Alignment.Center)
                    AspectRatioMode.RATIO_4_3 -> Modifier.aspectRatio(4f / 3f).align(Alignment.Center)
                    else -> Modifier.fillMaxSize()
                }).clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() },
                ) { showControls = !showControls },
            )
        }

        // Loading / buffering overlay
        if (channel == null || (isBuffering && !hasError && !isCasting)) {
            CircularProgressIndicator(
                modifier = Modifier.align(Alignment.Center),
                color = Color.White,
            )
        }

        // Casting overlay — covers the frozen local PlayerView so it's obvious
        // playback is happening on the receiver, not the phone.
        if (isCasting) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "Casting",
                        color = Color.White,
                        style = MaterialTheme.typography.titleLarge,
                    )
                    castSession?.castDevice?.friendlyName?.let { name ->
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "to $name",
                            color = Color.White.copy(alpha = 0.7f),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                    channel?.let { ch ->
                        Spacer(Modifier.height(12.dp))
                        Text(
                            ch.name,
                            color = Color.White,
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    }
                }
            }
        }

        // Error overlay (semi-transparent scrim over the player)
        if (hasError) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.85f)),
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Icon(
                        Icons.Default.Warning,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = Color.White,
                    )
                    Text(
                        errorMessage ?: "Playback failed",
                        color = Color.White,
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Button(onClick = {
                        hasError = false
                        channel?.let { ch ->
                            if (headers.isEmpty()) {
                                player.setMediaItem(MediaItem.fromUri(ch.url))
                            } else {
                                val mediaSource = DefaultMediaSourceFactory(
                                    DefaultDataSource.Factory(
                                        context,
                                        DefaultHttpDataSource.Factory().setDefaultRequestProperties(headers),
                                    )
                                ).createMediaSource(MediaItem.fromUri(ch.url))
                                player.setMediaSource(mediaSource)
                            }
                            player.prepare()
                            player.play()
                        }
                    }) { Text("Retry") }
                }
            }
        }

        // Controls footer — synced with ExoPlayer controller visibility
        AnimatedVisibility(
            visible = showControls && !hasError,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.5f))
                    .navigationBarsPadding()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = { aspectRatioMode = aspectRatioMode.next }) {
                    Text(
                        aspectRatioMode.label,
                        color = Color.White,
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
                AndroidView<MediaRouteButton>(
                    factory = { ctx ->
                        MediaRouteButton(ctx).also { CastButtonFactory.setUpMediaRouteButton(ctx, it) }
                    },
                    modifier = Modifier.size(48.dp),
                )
            }
        }

        // Back button — always visible, focus-ring for D-pad visibility
        IconButton(
            onClick = onBack,
            modifier = Modifier
                .align(Alignment.TopStart)
                .statusBarsPadding()
                .padding(8.dp)
                .onFocusChanged { backFocused = it.isFocused }
                .focusRequester(backFocusRequester)
                .then(
                    if (backFocused) Modifier.border(2.dp, Color.White, CircleShape)
                    else Modifier
                ),
        ) {
            Icon(
                Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Back",
                tint = Color.White,
            )
        }

        // Channel info overlay at the top — shown only with controls
        channel?.let { ch ->
            AnimatedVisibility(
                visible = showControls && !hasError,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.align(Alignment.TopCenter),
            ) {
                Row(
                    modifier = Modifier
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    ch.logoUrl?.let { url ->
                        AsyncImage(
                            model = url,
                            contentDescription = null,
                            modifier = Modifier.size(40.dp).clip(CircleShape),
                            contentScale = ContentScale.Crop,
                        )
                    }
                    Column {
                        Text(
                            ch.name,
                            color = Color.White,
                            style = MaterialTheme.typography.titleMedium,
                        )
                        ProgrammeOverlay(
                            now = nowAndNext.getOrNull(0),
                            next = nowAndNext.getOrNull(1),
                            nowMs = nowMs,
                        )
                    }
                }
            }
        }
    }

}

private val hmFormatter: SimpleDateFormat by lazy {
    SimpleDateFormat("HH:mm", Locale.getDefault())
}

private fun formatHm(epochMs: Long): String = hmFormatter.format(Date(epochMs))

@Composable
private fun ProgrammeOverlay(now: Programme?, next: Programme?, nowMs: Long) {
    if (now == null && next == null) return
    Column(modifier = Modifier.padding(top = 2.dp)) {
        now?.let { p ->
            Text(
                "${formatHm(p.startMs)}–${formatHm(p.endMs)}  ${p.title}",
                color = Color.White,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val span = (p.endMs - p.startMs).coerceAtLeast(1L)
            val progress = ((nowMs - p.startMs).toFloat() / span).coerceIn(0f, 1f)
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .padding(top = 4.dp)
                    .width(220.dp)
                    .height(2.dp),
                color = Color.White,
                trackColor = Color.White.copy(alpha = 0.3f),
                drawStopIndicator = {},
            )
        }
        next?.let { p ->
            Text(
                "Next  ${formatHm(p.startMs)}  ${p.title}",
                color = Color.White.copy(alpha = 0.7f),
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

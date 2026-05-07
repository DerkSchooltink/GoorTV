package dev.goor.tv.ui.screens.player

import android.app.Activity
import android.view.View
import android.view.WindowManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Cast
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import dev.goor.tv.dlna.DlnaDevice
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
    val dlnaDevices by vm.dlnaDevices.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val activity = context as? Activity

    val player = remember { ExoPlayer.Builder(context).build() }
    var hasError by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isBuffering by remember { mutableStateOf(true) }
    val showControls = remember { mutableStateOf(false) }
    var showCastDialog by remember { mutableStateOf(false) }
    var aspectRatioMode by remember { mutableStateOf(AspectRatioMode.FIT) }

    channel?.let { ch ->
        LaunchedEffect(ch.url) {
            hasError = false
            errorMessage = null
            isBuffering = true
            player.setMediaItem(MediaItem.fromUri(ch.url))
            player.prepare()
            player.play()
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
        onDispose {
            activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
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
                        setShowNextButton(false)
                        setShowPreviousButton(false)
                        setControllerVisibilityListener(PlayerView.ControllerVisibilityListener { v ->
                            showControls.value = v == View.VISIBLE
                        })
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
                modifier = when (aspectRatioMode) {
                    AspectRatioMode.RATIO_16_9 -> Modifier.aspectRatio(16f / 9f).align(Alignment.Center)
                    AspectRatioMode.RATIO_4_3 -> Modifier.aspectRatio(4f / 3f).align(Alignment.Center)
                    else -> Modifier.fillMaxSize()
                },
            )
        }

        // Loading / buffering overlay
        if (channel == null || (isBuffering && !hasError)) {
            CircularProgressIndicator(
                modifier = Modifier.align(Alignment.Center),
                color = Color.White,
            )
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
                            player.setMediaItem(MediaItem.fromUri(ch.url))
                            player.prepare()
                            player.play()
                        }
                    }) { Text("Retry") }
                }
            }
        }

        // Controls footer — synced with ExoPlayer controller visibility
        AnimatedVisibility(
            visible = showControls.value && !hasError,
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
                IconButton(onClick = { showCastDialog = true }) {
                    Icon(
                        Icons.Default.Cast,
                        contentDescription = "Cast to device",
                        tint = if (dlnaDevices.isNotEmpty()) MaterialTheme.colorScheme.primary else Color.White,
                    )
                }
            }
        }

        // Back button — always visible
        IconButton(
            onClick = onBack,
            modifier = Modifier
                .align(Alignment.TopStart)
                .statusBarsPadding()
                .padding(8.dp),
        ) {
            Icon(
                Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Back",
                tint = Color.White,
            )
        }

        // Channel info overlay at the top — visible when playing without error
        channel?.let { ch ->
            if (!hasError) {
                Row(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .statusBarsPadding()
                        .padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    ch.logoUrl?.let { url ->
                        AsyncImage(
                            model = url,
                            contentDescription = null,
                            modifier = Modifier.size(32.dp).clip(CircleShape),
                            contentScale = ContentScale.Crop,
                        )
                    }
                    Text(
                        ch.name,
                        color = Color.White,
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
            }
        }
    }

    if (showCastDialog) {
        CastDialog(
            devices = dlnaDevices,
            onDismiss = { showCastDialog = false },
            onSelect = { device ->
                vm.castTo(device)
                showCastDialog = false
            },
        )
    }
}

@Composable
private fun CastDialog(
    devices: List<DlnaDevice>,
    onDismiss: () -> Unit,
    onSelect: (DlnaDevice) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Cast to device") },
        text = {
            if (devices.isEmpty()) {
                Text(
                    "Searching for DLNA renderers on your network…",
                    style = MaterialTheme.typography.bodyMedium,
                )
            } else {
                LazyColumn {
                    items(devices, key = { it.udn }) { device ->
                        TextButton(
                            onClick = { onSelect(device) },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(device.name, style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

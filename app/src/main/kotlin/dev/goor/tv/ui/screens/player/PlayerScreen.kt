package dev.goor.tv.ui.screens.player

import android.app.Activity
import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
fun PlayerScreen(
    channelId: Long,
    onBack: () -> Unit,
    vm: PlayerViewModel = koinViewModel(parameters = { parametersOf(channelId) }),
) {
    val channel by vm.channel.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val activity = context as? Activity

    val player = remember { ExoPlayer.Builder(context).build() }
    var hasError by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    channel?.let { ch ->
        LaunchedEffect(ch.url) {
            hasError = false
            errorMessage = null
            player.setMediaItem(MediaItem.fromUri(ch.url))
            player.prepare()
            player.play()
        }
    }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                hasError = true
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
                    }
                },
                modifier = Modifier.fillMaxSize(),
            )
        }

        // Loading overlay
        if (channel == null) {
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
}

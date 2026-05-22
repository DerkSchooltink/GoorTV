@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package dev.goor.tv.ui.screens.player

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Warning
import androidx.mediarouter.app.MediaRouteButton
import com.google.android.gms.cast.framework.CastButtonFactory
import com.google.android.gms.cast.framework.CastSession
import androidx.compose.runtime.State
import dev.goor.tv.cast.loadOnCastSession
import dev.goor.tv.cast.rememberCastSession
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.goor.tv.data.model.Programme
import dev.goor.tv.ui.util.SystemBarsController
import dev.goor.tv.ui.util.focusBorder
import dev.goor.tv.ui.util.rememberSystemBarsController
import dev.goor.tv.ui.util.rememberTvFocus
import dev.goor.tv.ui.util.trackTvFocus
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import coil3.compose.AsyncImage
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

private const val CONTROLS_AUTO_HIDE_MS = 4_000L

private enum class AspectRatioMode(val label: String) {
    FIT("Fit"),
    FILL("Fill"),
    ZOOM("Zoom"),
    RATIO_16_9("16:9"),
    RATIO_4_3("4:3"),
}

private val AspectRatioMode.next: AspectRatioMode
    get() = AspectRatioMode.entries[(ordinal + 1) % AspectRatioMode.entries.size]

/** Saves the enum ordinal — `autoSaver()` doesn't know how to serialize enums. */
private val AspectRatioModeSaver: Saver<AspectRatioMode, Int> = Saver(
    save = { it.ordinal },
    restore = { AspectRatioMode.entries[it] },
)

@Composable
fun PlayerScreen(
    channelId: Long,
    onBack: () -> Unit,
    vm: PlayerViewModel = koinViewModel(parameters = { parametersOf(channelId) }),
    systemBars: SystemBarsController = rememberSystemBarsController(),
    // Injectable so tests don't have to spin up `CastContext.getSharedInstance`
    // (Google Play Services) — that's the other side-effect that tears down the
    // Compose test host. Default uses the real Cast session listener.
    castSessionState: State<CastSession?> = rememberCastSession(),
    // Injectable so tests pass [NoOpPlayerEngine] instead of constructing a real
    // ExoPlayer (which SIGKILLs the test process on the emulator).
    playerEngine: PlayerEngine = rememberPlayerEngine(),
) {
    val channel by vm.channel.collectAsStateWithLifecycle()
    val headers by vm.headers.collectAsStateWithLifecycle()
    val stopped by vm.stopped.collectAsStateWithLifecycle()
    val nowAndNext by vm.nowAndNext.collectAsStateWithLifecycle()
    val nowMs by vm.nowMs.collectAsStateWithLifecycle()
    val castSession by castSessionState
    val isCasting = castSession != null
    val context = LocalContext.current

    LaunchedEffect(stopped) {
        if (stopped) onBack()
    }

    val isBuffering by playerEngine.isBuffering
    val engineError by playerEngine.errorMessage
    val hasError = engineError != null
    val errorMessage = engineError
    var castError by remember { mutableStateOf<String?>(null) }
    var showControls by remember { mutableStateOf(false) }
    // User-selected aspect ratio persists across config changes (rotation,
    // dark-mode toggle, etc.). Stored as ordinal because Saver can't reflect
    // through enums without a custom Saver.
    var aspectRatioMode by rememberSaveable(stateSaver = AspectRatioModeSaver) {
        mutableStateOf(AspectRatioMode.FIT)
    }
    val backFocusRequester = remember { FocusRequester() }
    val backFocus = rememberTvFocus()
    val aspectFocus = rememberTvFocus()
    val castFocus = rememberTvFocus()

    BackHandler { onBack() }

    // Land focus on the back button once composition completes so the user can
    // press D-pad back/right immediately without first navigating into the
    // overlay tree. LaunchedEffect runs after the focus tree is built — the
    // earlier try/catch around IllegalStateException is no longer needed.
    LaunchedEffect(Unit) { backFocusRequester.requestFocus() }

    LaunchedEffect(showControls) {
        if (showControls) {
            delay(CONTROLS_AUTO_HIDE_MS)
            showControls = false
        }
    }

    channel?.let { ch ->
        LaunchedEffect(ch.url, headers, castSession) {
            castError = null
            val session = castSession
            if (session != null) {
                // Cast path — pause local, hand off to receiver.
                playerEngine.pause()
                runCatching { loadOnCastSession(session, ch) }
                    .onFailure { castError = "Cast failed: ${it.message ?: it::class.simpleName}" }
            } else {
                playerEngine.prepare(ch.url, headers)
            }
        }
    }

    DisposableEffect(Unit) {
        systemBars.hideAndKeepScreenOn()
        onDispose {
            systemBars.restore()
            playerEngine.release()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        // Player — always mounted while channel is loaded so it isn't recreated on error.
        // The engine returns null for fakes (NoOpPlayerEngine in tests), so we
        // skip the AndroidView entirely in that case.
        if (channel != null) {
            val resizeMode = when (aspectRatioMode) {
                AspectRatioMode.FIT -> PlayerEngine.ResizeMode.FIT
                AspectRatioMode.FILL -> PlayerEngine.ResizeMode.FILL
                AspectRatioMode.ZOOM -> PlayerEngine.ResizeMode.ZOOM
                AspectRatioMode.RATIO_16_9 -> PlayerEngine.ResizeMode.FIT
                AspectRatioMode.RATIO_4_3 -> PlayerEngine.ResizeMode.FIT
            }
            val viewModifier = (when (aspectRatioMode) {
                AspectRatioMode.RATIO_16_9 -> Modifier.aspectRatio(16f / 9f).align(Alignment.Center)
                AspectRatioMode.RATIO_4_3 -> Modifier.aspectRatio(4f / 3f).align(Alignment.Center)
                else -> Modifier.fillMaxSize()
            }).clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
            ) { showControls = !showControls }
            val playerView = remember(playerEngine) { playerEngine.createPlayerView(context) }
            if (playerView != null) {
                AndroidView(
                    factory = { playerView },
                    update = { playerEngine.applyResizeMode(it, resizeMode) },
                    modifier = viewModifier,
                )
            } else {
                // No player view (test/preview) — still honour click-to-toggle-controls
                // so UI tests can exercise that path.
                Box(modifier = viewModifier)
            }
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
                    if (castError != null) {
                        Text(
                            "Cast failed",
                            color = Color.White,
                            style = MaterialTheme.typography.titleLarge,
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            castError!!,
                            color = Color.White.copy(alpha = 0.7f),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    } else {
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

        // Error overlay (semi-transparent scrim over the player). Hidden during cast —
        // the cast overlay carries its own error state.
        if (hasError && !isCasting) {
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
                        channel?.let { ch -> playerEngine.prepare(ch.url, headers) }
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
                TextButton(
                    onClick = { aspectRatioMode = aspectRatioMode.next },
                    modifier = Modifier
                        .trackTvFocus(aspectFocus)
                        .focusBorder(aspectFocus.value, CircleShape, Color.White),
                ) {
                    Text(
                        aspectRatioMode.label,
                        color = Color.White,
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
                // Wrap MediaRouteButton in a focusable Box so it participates in
                // D-pad traversal — the raw AndroidView isn't reachable via the
                // remote otherwise. The Box owns the focus ring; the inner
                // AndroidView still handles touch. D-pad Enter / Center is
                // dispatched manually via performClick() since AndroidView
                // doesn't bubble key events to a Compose .clickable.
                var routeButton by remember { mutableStateOf<MediaRouteButton?>(null) }
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .trackTvFocus(castFocus)
                        .focusBorder(castFocus.value, CircleShape, Color.White)
                        .onPreviewKeyEvent { event ->
                            val isClick = event.key == Key.DirectionCenter ||
                                event.key == Key.Enter ||
                                event.key == Key.NumPadEnter
                            if (isClick && event.type == KeyEventType.KeyUp) {
                                routeButton?.performClick() == true
                            } else false
                        }
                        .focusable(),
                ) {
                    AndroidView<MediaRouteButton>(
                        factory = { ctx ->
                            MediaRouteButton(ctx).also {
                                CastButtonFactory.setUpMediaRouteButton(ctx, it)
                                routeButton = it
                            }
                        },
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }

        // Back button — always visible, focus-ring for D-pad visibility.
        // Requested into focus by the LaunchedEffect above so D-pad has somewhere
        // to land on first composition.
        IconButton(
            onClick = onBack,
            modifier = Modifier
                .align(Alignment.TopStart)
                .statusBarsPadding()
                .padding(8.dp)
                .focusRequester(backFocusRequester)
                .trackTvFocus(backFocus)
                .focusBorder(backFocus.value, CircleShape, Color.White),
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
                            contentDescription = ch.name,
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

// java.time formatters are immutable + thread-safe (unlike SimpleDateFormat).
private val hmFormatter: DateTimeFormatter =
    DateTimeFormatter.ofPattern("HH:mm", Locale.getDefault()).withZone(ZoneId.systemDefault())

private fun formatHm(epochMs: Long): String = hmFormatter.format(Instant.ofEpochMilli(epochMs))

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

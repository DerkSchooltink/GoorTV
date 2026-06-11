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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Warning
import androidx.mediarouter.app.MediaRouteButton
import com.google.android.gms.cast.framework.CastButtonFactory
import com.google.android.gms.cast.framework.CastSession
import androidx.compose.runtime.State
import dev.goor.tv.cast.isCastAvailable
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.annotation.StringRes
import dev.goor.tv.R
import dev.goor.tv.data.model.Channel
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
private const val STREAM_LIMIT_NOTICE_MS = 3_000L
// A stream that connects then stalls indefinitely never fires onPlayerError, so
// the buffering spinner would otherwise spin forever. Treat a buffer this long
// as a failure and surface the error overlay with a Retry.
private const val BUFFER_WATCHDOG_MS = 25_000L
// Brief pause before the one automatic retry, so a transient blip self-heals
// without the user ever seeing an error.
private const val AUTO_RETRY_DELAY_MS = 1_500L
// How long the "Cast failed, playing locally" notice stays up after fallback.
private const val CAST_NOTICE_MS = 5_000L

private enum class AspectRatioMode(@StringRes val labelRes: Int) {
    FIT(R.string.player_aspect_fit),
    FILL(R.string.player_aspect_fill),
    ZOOM(R.string.player_aspect_zoom),
    RATIO_16_9(R.string.player_aspect_16_9),
    RATIO_4_3(R.string.player_aspect_4_3),
}

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
        if (stopped) {
            // Show the limit notice briefly before popping, so the user
            // understands why the channel didn't open instead of being bounced
            // back to Home with no explanation.
            delay(STREAM_LIMIT_NOTICE_MS)
            onBack()
        }
    }

    val isBuffering by playerEngine.isBuffering
    val engineError by playerEngine.errorMessage
    val hasError = engineError != null
    val errorMessage = engineError
    var castError by remember { mutableStateOf<String?>(null) }
    // Set once we've given up on casting this channel and resumed local playback,
    // so the (auto-clearing) cast notice doesn't let the casting overlay re-cover
    // the local video.
    var castFellBack by remember { mutableStateOf(false) }
    // Per-channel playback recovery state — reset when the channel changes.
    var stalled by remember(channel?.id) { mutableStateOf(false) }
    var autoRetried by remember(channel?.id) { mutableStateOf(false) }
    var retrying by remember(channel?.id) { mutableStateOf(false) }
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

    // Stable callbacks for the overlay sub-composables — without `remember`,
    // these fresh-lambda each composition and force the (skippable) overlays
    // to recompose on every parent frame.
    val onAspectSelected = remember { { mode: AspectRatioMode -> aspectRatioMode = mode } }
    val onRetry = remember(channel?.id, headers) {
        {
            // Manual retry clears both recovery flags so the watchdog + auto-retry
            // get a fresh budget for the new attempt.
            autoRetried = false
            stalled = false
            channel?.let { ch -> playerEngine.prepare(ch.url, headers) } ?: Unit
        }
    }

    // "Effectively playing locally" — either not casting, or we fell back to local
    // after a cast load failure. Gates the local spinner / error overlay.
    val showingLocal = !isCasting || castFellBack
    val showErrorOverlay = showingLocal && (stalled || (hasError && autoRetried && !retrying))
    val overlayMessage =
        if (stalled && !hasError) stringResource(R.string.player_stream_not_responding) else errorMessage
    val showSpinner = channel == null || (isBuffering && !hasError && showingLocal && !stalled)

    BackHandler { onBack() }

    // Buffering watchdog: a stream that connects then stalls forever never fires
    // onPlayerError, so surface the error overlay once buffering outlasts the
    // watchdog. Re-keys (and resets) whenever buffering / error / cast state flips.
    LaunchedEffect(isBuffering, hasError, showingLocal, channel?.id) {
        stalled = false
        if (isBuffering && !hasError && showingLocal) {
            delay(BUFFER_WATCHDOG_MS)
            stalled = true
        }
    }

    // Auto-retry once on error before bothering the user. prepare() clears the
    // error; if it fails again, autoRetried is already set so the overlay shows.
    LaunchedEffect(hasError, showingLocal, channel?.id) {
        if (hasError && showingLocal && !autoRetried) {
            autoRetried = true
            retrying = true
            delay(AUTO_RETRY_DELAY_MS)
            channel?.let { playerEngine.prepare(it.url, headers) }
            retrying = false
        }
    }

    // Cast notice auto-dismisses; castFellBack keeps the casting overlay from
    // re-covering the now-local video once it clears.
    LaunchedEffect(castError) {
        if (castError != null) {
            delay(CAST_NOTICE_MS)
            castError = null
        }
    }

    LaunchedEffect(showControls) {
        if (showControls) {
            delay(CONTROLS_AUTO_HIDE_MS)
            showControls = false
        } else {
            // Controls hidden — the footer (aspect/cast buttons) leaves
            // composition, taking any focus on it with it and stranding the
            // D-pad on dead video. Hand focus back to the always-present back
            // button. This also fires on initial composition (showControls
            // starts false), giving the user a landing target right away.
            // LaunchedEffect runs after the focus tree is built, so no try/catch
            // around IllegalStateException is needed.
            backFocusRequester.requestFocus()
        }
    }

    val castFailedMessage = stringResource(R.string.player_cast_failed_fallback)
    channel?.let { ch ->
        LaunchedEffect(ch.url, headers, castSession) {
            castError = null
            castFellBack = false
            val session = castSession
            if (session != null) {
                // Cast path — pause local, hand off to receiver.
                playerEngine.pause()
                runCatching { loadOnCastSession(session, ch) }
                    .onFailure {
                        // Don't strand the user on a dead casting screen — resume
                        // local playback and show a brief notice.
                        castError = castFailedMessage
                        castFellBack = true
                        playerEngine.prepare(ch.url, headers)
                    }
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
            }).testTag("player_surface").clickable(
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
        if (showSpinner) {
            CircularProgressIndicator(
                modifier = Modifier.align(Alignment.Center),
                color = Color.White,
            )
        }

        if (isCasting && !castFellBack) {
            CastingOverlay(
                channelName = channel?.name,
                deviceName = castSession?.castDevice?.friendlyName,
            )
        }

        if (showErrorOverlay) {
            PlaybackErrorOverlay(
                message = overlayMessage,
                onRetry = onRetry,
            )
        }

        // Transient notice after a cast load failure / local fallback.
        castError?.let { msg ->
            Text(
                msg,
                color = Color.White,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(top = 8.dp, start = 56.dp, end = 16.dp)
                    .background(Color.Black.copy(alpha = 0.6f))
                    .padding(horizontal = 12.dp, vertical = 6.dp),
            )
        }

        PlayerControlsFooter(
            visible = showControls && !showErrorOverlay,
            aspectMode = aspectRatioMode,
            onAspectSelected = onAspectSelected,
            aspectFocus = aspectFocus,
            castFocus = castFocus,
            modifier = Modifier.align(Alignment.BottomCenter),
        )

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
                contentDescription = stringResource(R.string.common_back),
                tint = Color.White,
            )
        }

        channel?.let { ch ->
            ChannelInfoOverlay(
                visible = showControls && !showErrorOverlay,
                channel = ch,
                nowAndNext = nowAndNext,
                nowMs = nowMs,
                modifier = Modifier.align(Alignment.TopCenter),
            )
        }

        // Stream refused by the concurrency limit — cover everything with an
        // explanatory notice; the LaunchedEffect above pops back after a beat.
        if (stopped) {
            StreamLimitOverlay()
        }
    }
}

@Composable
private fun StreamLimitOverlay() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                Icons.Default.Warning,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = Color.White,
            )
            Text(
                stringResource(R.string.player_stream_limit_title),
                color = Color.White,
                style = MaterialTheme.typography.titleLarge,
            )
            Text(
                stringResource(R.string.player_stream_limit_body),
                color = Color.White.copy(alpha = 0.7f),
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun CastingOverlay(
    channelName: String?,
    deviceName: String?,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                stringResource(R.string.player_casting),
                color = Color.White,
                style = MaterialTheme.typography.titleLarge,
            )
            if (deviceName != null) {
                Spacer(Modifier.height(4.dp))
                Text(
                    stringResource(R.string.player_casting_to, deviceName),
                    color = Color.White.copy(alpha = 0.7f),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            if (channelName != null) {
                Spacer(Modifier.height(12.dp))
                Text(channelName, color = Color.White, style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}

@Composable
private fun PlaybackErrorOverlay(
    message: String?,
    onRetry: () -> Unit,
) {
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
                message ?: stringResource(R.string.player_playback_failed),
                color = Color.White,
                style = MaterialTheme.typography.bodyLarge,
            )
            Button(onClick = onRetry) { Text(stringResource(R.string.player_retry)) }
        }
    }
}

@Composable
private fun PlayerControlsFooter(
    visible: Boolean,
    aspectMode: AspectRatioMode,
    onAspectSelected: (AspectRatioMode) -> Unit,
    aspectFocus: MutableState<Boolean>,
    castFocus: MutableState<Boolean>,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = modifier,
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
            Box {
                var menuOpen by remember { mutableStateOf(false) }
                TextButton(
                    onClick = { menuOpen = true },
                    modifier = Modifier
                        .trackTvFocus(aspectFocus)
                        .focusBorder(aspectFocus.value, CircleShape, Color.White),
                ) {
                    Text(
                        stringResource(R.string.player_aspect_label, stringResource(aspectMode.labelRes)),
                        color = Color.White,
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
                // A popup of all modes — the user can see and pick directly
                // instead of blind-cycling a single toggle.
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    AspectRatioMode.entries.forEach { mode ->
                        DropdownMenuItem(
                            text = { Text(stringResource(mode.labelRes)) },
                            onClick = {
                                onAspectSelected(mode)
                                menuOpen = false
                            },
                            leadingIcon = if (mode == aspectMode) {
                                // The checkmark is the only thing conveying which mode is
                                // active, so it needs a description for screen readers.
                                {
                                    Icon(
                                        Icons.Default.Check,
                                        contentDescription = stringResource(R.string.common_selected),
                                    )
                                }
                            } else null,
                        )
                    }
                }
            }
            // Skip the Cast button on devices without Google Play Services —
            // CastButtonFactory.setUpMediaRouteButton internally calls
            // CastContext.getSharedInstance which throws
            // ModuleUnavailableException on AOSP TV / Fire TV / de-Googled
            // devices and crashes the process.
            val context = LocalContext.current
            val castAvailable = remember { context.isCastAvailable() }
            if (castAvailable) {
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
    }
}

@Composable
private fun ChannelInfoOverlay(
    visible: Boolean,
    channel: Channel,
    nowAndNext: List<Programme>,
    nowMs: Long,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = modifier,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            channel.logoUrl?.let { url ->
                AsyncImage(
                    model = url,
                    contentDescription = channel.name,
                    modifier = Modifier.size(40.dp).clip(CircleShape),
                    contentScale = ContentScale.Crop,
                )
            }
            Column {
                Text(
                    channel.name,
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
                stringResource(R.string.player_now_programme, formatHm(p.startMs), formatHm(p.endMs), p.title),
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
                stringResource(R.string.player_next_programme, formatHm(p.startMs), p.title),
                color = Color.White.copy(alpha = 0.7f),
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

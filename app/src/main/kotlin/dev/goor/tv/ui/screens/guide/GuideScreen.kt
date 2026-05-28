package dev.goor.tv.ui.screens.guide

import androidx.compose.animation.Crossfade
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.core.updateTransition
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import dev.goor.tv.data.model.Channel
import dev.goor.tv.data.model.Programme
import dev.goor.tv.ui.util.focusBorder
import dev.goor.tv.ui.util.rememberTvFocus
import dev.goor.tv.ui.util.trackTvFocus
import org.koin.androidx.compose.koinViewModel
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val DP_PER_MINUTE = 3.dp
private val CHANNEL_COL_WIDTH = 160.dp
private val ROW_HEIGHT = 64.dp
private val TIME_HEADER_HEIGHT = 24.dp
private val PROGRAMME_GAP = 1.dp
// Single source of truth lives on the ViewModel so the rail-fetch quantization
// and the on-screen viewport buffer can't drift apart.
private val SLOT_MINUTES = GuideViewModel.SLOT_MINUTES

// java.time formatters are immutable + thread-safe (unlike SimpleDateFormat).
private val hmFormatter: DateTimeFormatter =
    DateTimeFormatter.ofPattern("HH:mm", Locale.getDefault()).withZone(ZoneId.systemDefault())

private fun formatHm(epochMs: Long): String = hmFormatter.format(Instant.ofEpochMilli(epochMs))

private fun minutesBetween(from: Long, to: Long): Long = (to - from) / 60_000L

@OptIn(ExperimentalMaterial3Api::class, ExperimentalAnimationApi::class)
@Composable
fun GuideScreen(
    onBack: () -> Unit,
    onWatch: (channelId: Long) -> Unit,
    onGoToSettings: () -> Unit = {},
    vm: GuideViewModel = koinViewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val nowMs by vm.nowMs.collectAsStateWithLifecycle()
    val windowStartMs by vm.windowStartMs.collectAsStateWithLifecycle()
    val windowEndMs by vm.windowEndMs.collectAsStateWithLifecycle()

    val scrollState = rememberScrollState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Guide") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        // Crossfade between Loading / Empty / Ready so the spinner doesn't snap off
        // when EPG arrives. The `Transition<T>.Crossfade` overload accepts a
        // `contentKey` — we key on the state *class* so re-emissions of `Ready`
        // with updated rows don't pin two `rows` lists in heap for the fade
        // duration (a non-trivial cost on large playlists). The top-level
        // `Crossfade(T, …)` overload does *not* expose `contentKey`, which is
        // why this path uses `updateTransition` directly.
        val transition = updateTransition(targetState = state, label = "GuideState")
        transition.Crossfade(contentKey = { it::class }) { s ->
            when (s) {
                is GuideState.Loading -> CenteredSpinner(
                    label = "Waiting for EPG…",
                    modifier = Modifier.padding(padding),
                )
                is GuideState.Empty -> EmptyState(
                    reason = s.reason,
                    onGoToSettings = onGoToSettings,
                    modifier = Modifier.padding(padding),
                )
                is GuideState.Ready -> GuideGrid(
                    rows = s.rows,
                    windowStartMs = windowStartMs,
                    windowEndMs = windowEndMs,
                    nowMs = nowMs,
                    scrollState = scrollState,
                    onWatch = onWatch,
                    modifier = Modifier.padding(padding),
                )
            }
        }
    }
}

@Composable
private fun GuideGrid(
    rows: List<GuideRow>,
    windowStartMs: Long,
    windowEndMs: Long,
    nowMs: Long,
    scrollState: androidx.compose.foundation.ScrollState,
    onWatch: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val windowMinutes = minutesBetween(windowStartMs, windowEndMs)
    val railWidth = DP_PER_MINUTE * windowMinutes.toInt()
    val slotMs = SLOT_MINUTES * 60_000L
    // Ceiling-align to the next 30-minute slot. Using "/ + 1" would skip a label
    // when windowStartMs lands exactly on a slot boundary.
    val firstSlotMs = ((windowStartMs + slotMs - 1) / slotMs) * slotMs

    val density = LocalDensity.current
    val dpPerMinPx = with(density) { DP_PER_MINUTE.toPx() }
    // Stable lambda so LazyColumn item rows don't recompose just because the
    // callback identity changed on each parent recomposition.
    val onWatchProgramme: (Long) -> Unit = remember(onWatch) { { id -> onWatch(id) } }

    // The rail area is wrapped in BoxWithConstraints so we know the on-screen
    // viewport width and can compute which programme cells actually intersect
    // it. Without this, every row's full-window-wide rail composed every
    // programme (tens per channel × hundreds of channels) — the dominant heap
    // cost that pushed the app into OOM territory.
    BoxWithConstraints(modifier = modifier) {
        val railViewportPx = with(density) {
            (maxWidth - CHANNEL_COL_WIDTH).toPx().coerceAtLeast(0f)
        }
        // 30-min buffer on each side so cells just outside the viewport still
        // get composed and stay scroll-stable. derivedStateOf snapshots only
        // re-fire when the *clamped* visible range actually changes, so
        // small scroll deltas inside one slot don't churn recompositions.
        val visibleRange by remember(windowStartMs, windowEndMs, dpPerMinPx, railViewportPx) {
            derivedStateOf {
                val scrollPx = scrollState.value.toFloat()
                val startMin = (scrollPx / dpPerMinPx).toLong() - SLOT_MINUTES
                val visibleMin = (railViewportPx / dpPerMinPx).toLong() + 2 * SLOT_MINUTES
                val start = (windowStartMs + startMin * 60_000L).coerceAtLeast(windowStartMs)
                val end = (start + visibleMin * 60_000L).coerceAtMost(windowEndMs)
                start..end
            }
        }

        // Auto-scroll horizontally to centre "now" once when the grid first lands.
        // BoxWithConstraints gives us the real viewport width, so the centring is
        // accurate (the old GuideScreen-level effect ran before layout and used a
        // hard-coded -200 px offset).
        LaunchedEffect(Unit) {
            val nowOffsetPx = minutesBetween(windowStartMs, nowMs) * dpPerMinPx
            val target = (nowOffsetPx - railViewportPx / 2f).toInt().coerceAtLeast(0)
            scrollState.scrollTo(target)
        }

        // Land D-pad focus on the first channel header once the grid appears, so
        // a remote has somewhere to go instead of nothing being focused. One-shot
        // and runCatching-guarded: if the node isn't placed yet we simply fall
        // back to today's no-initial-focus behaviour rather than crashing.
        val firstRowFocus = remember { FocusRequester() }
        LaunchedEffect(Unit) {
            if (rows.isNotEmpty()) runCatching { firstRowFocus.requestFocus() }
        }

        Column(modifier = Modifier.fillMaxSize()) {
            // Time-axis header
            Row(modifier = Modifier.height(TIME_HEADER_HEIGHT)) {
                Spacer(Modifier.width(CHANNEL_COL_WIDTH))
                Box(modifier = Modifier.horizontalScroll(scrollState).width(railWidth)) {
                    TimeHeader(
                        firstSlotMs = firstSlotMs,
                        windowStartMs = windowStartMs,
                        windowEndMs = windowEndMs,
                        visibleRange = visibleRange,
                    )
                }
            }
            // Channel rows
            Box(modifier = Modifier.weight(1f)) {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    itemsIndexed(rows, key = { _, r -> "${r.channel.sourceId}_${r.channel.id}" }) { index, row ->
                        Row(modifier = Modifier.height(ROW_HEIGHT)) {
                            ChannelHeaderCell(
                                channel = row.channel,
                                onClick = { onWatchProgramme(row.channel.id) },
                                modifier = if (index == 0) Modifier.focusRequester(firstRowFocus) else Modifier,
                            )
                            Box(
                                modifier = Modifier
                                    .horizontalScroll(scrollState)
                                    .width(railWidth)
                                    .fillMaxHeight(),
                            ) {
                                ProgrammeRail(
                                    programmes = row.programmes,
                                    windowStartMs = windowStartMs,
                                    windowEndMs = windowEndMs,
                                    nowMs = nowMs,
                                    visibleRange = visibleRange,
                                    channelId = row.channel.id,
                                    onWatch = onWatchProgramme,
                                )
                            }
                        }
                    }
                }
                // Now-line overlay — drawn over the rails, clipped to the rail area.
                NowIndicator(
                    windowStartMs = windowStartMs,
                    nowMs = nowMs,
                    scrollState = scrollState,
                )
            }
        }
    }
}

@Composable
private fun TimeHeader(
    firstSlotMs: Long,
    windowStartMs: Long,
    windowEndMs: Long,
    visibleRange: LongRange,
) {
    Box(modifier = Modifier.fillMaxSize()) {
        var slot = firstSlotMs
        while (slot < windowEndMs) {
            if (slot in visibleRange) {
                val offsetMin = minutesBetween(windowStartMs, slot).toInt()
                Text(
                    formatHm(slot),
                    modifier = Modifier
                        .padding(start = DP_PER_MINUTE * offsetMin),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            slot += SLOT_MINUTES * 60_000L
        }
    }
}

@Composable
private fun ChannelHeaderCell(channel: Channel, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .width(CHANNEL_COL_WIDTH)
            .fillMaxHeight()
            .background(MaterialTheme.colorScheme.surface)
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (!channel.logoUrl.isNullOrBlank()) {
            AsyncImage(
                model = channel.logoUrl,
                contentDescription = channel.name,
                modifier = Modifier.size(36.dp).clip(CircleShape),
                contentScale = ContentScale.Crop,
            )
            Spacer(Modifier.width(8.dp))
        }
        Text(
            channel.name,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun ProgrammeRail(
    programmes: List<Programme>,
    windowStartMs: Long,
    windowEndMs: Long,
    nowMs: Long,
    visibleRange: LongRange,
    channelId: Long,
    onWatch: (Long) -> Unit,
) {
    // Stable click callback for every ProgrammeBlock in this row — re-allocated
    // only when the channel or the parent onWatch identity changes.
    val onClick = remember(channelId, onWatch) { { onWatch(channelId) } }
    Box(modifier = Modifier.fillMaxSize()) {
        programmes.forEach { p ->
            // Skip cells outside the on-screen viewport before composing them. This
            // is what virtualizes the rail: without it, a full-window-wide rail
            // composed every cell across every visible row. `visibleRange` is
            // widened by one slot on each side so scrolling stays seamless.
            if (p.endMs < visibleRange.first || p.startMs > visibleRange.last) return@forEach
            val visibleStart = p.startMs.coerceAtLeast(windowStartMs)
            val visibleEnd = p.endMs.coerceAtMost(windowEndMs)
            if (visibleEnd <= visibleStart) return@forEach
            val offsetMin = minutesBetween(windowStartMs, visibleStart).toInt()
            val widthMin = minutesBetween(visibleStart, visibleEnd).toInt()
            val isLive = nowMs in p.startMs..p.endMs
            ProgrammeBlock(
                programme = p,
                offsetDp = DP_PER_MINUTE * offsetMin,
                widthDp = (DP_PER_MINUTE * widthMin - PROGRAMME_GAP).coerceAtLeast(0.dp),
                isLive = isLive,
                onWatch = onClick,
            )
        }
    }
}

@Composable
private fun ProgrammeBlock(
    programme: Programme,
    offsetDp: Dp,
    widthDp: Dp,
    isLive: Boolean,
    onWatch: () -> Unit,
) {
    val focus = rememberTvFocus()
    val bg = when {
        focus.value -> MaterialTheme.colorScheme.primary
        isLive -> MaterialTheme.colorScheme.primaryContainer
        else -> MaterialTheme.colorScheme.surfaceVariant
    }
    val fg = when {
        focus.value -> MaterialTheme.colorScheme.onPrimary
        isLive -> MaterialTheme.colorScheme.onPrimaryContainer
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Column(
        modifier = Modifier
            .padding(start = offsetDp, end = PROGRAMME_GAP, top = 2.dp, bottom = 2.dp)
            .width(widthDp)
            .fillMaxHeight()
            .background(bg)
            // Square focus border, no corner radius — matches the block fill.
            .focusBorder(focus.value, shape = RectangleShape)
            .trackTvFocus(focus)
            .clickable(onClick = onWatch)
            .padding(horizontal = 6.dp, vertical = 4.dp),
    ) {
        Text(
            programme.title,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.bodySmall,
            color = fg,
        )
        Text(
            "${formatHm(programme.startMs)}–${formatHm(programme.endMs)}",
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.labelSmall,
            color = fg.copy(alpha = 0.7f),
        )
    }
}

@Composable
private fun BoxScope.NowIndicator(
    windowStartMs: Long,
    nowMs: Long,
    scrollState: androidx.compose.foundation.ScrollState,
) {
    val density = LocalDensity.current
    val minutesIn = minutesBetween(windowStartMs, nowMs).toInt()
    if (minutesIn < 0) return
    val nowOffsetPx = with(density) { (DP_PER_MINUTE * minutesIn).toPx() }
    val scrollPx = scrollState.value.toFloat()
    val visibleX = nowOffsetPx - scrollPx
    val color = MaterialTheme.colorScheme.error
    Canvas(
        modifier = Modifier
            .matchParentSize()
            .padding(start = CHANNEL_COL_WIDTH),
    ) {
        val x = visibleX
        if (x < 0 || x > size.width) return@Canvas
        drawLine(
            color = color,
            start = Offset(x, 0f),
            end = Offset(x, size.height),
            strokeWidth = 1.5.dp.toPx(),
        )
    }
}

@Composable
private fun CenteredSpinner(label: String, modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator()
            Spacer(Modifier.height(12.dp))
            Text(
                label,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun EmptyState(
    reason: GuideEmptyReason,
    onGoToSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            when (reason) {
                GuideEmptyReason.NoSources -> EmptyMessage(
                    title = "No EPG configured",
                    body = "Configure a source with EPG support — Xtream credentials or an M3U with an EPG URL — to populate the guide.",
                    action = "Open Settings" to onGoToSettings,
                )
                GuideEmptyReason.Fetching -> EmptyMessage(
                    title = "Fetching guide…",
                    body = "The first sync can take a few minutes.",
                    showSpinner = true,
                )
                GuideEmptyReason.NoTvgIds -> EmptyMessage(
                    title = "No channels match the guide",
                    body = "The EPG was fetched, but your channels don't carry a tvg-id attribute to match programmes to. Check your playlist provider.",
                )
                GuideEmptyReason.NoProgrammes -> EmptyMessage(
                    title = "Guide is empty",
                    body = "Your channels are configured but no programmes were found. The EPG feed may not cover them.",
                )
                is GuideEmptyReason.EpgError -> EmptyMessage(
                    title = "EPG sync failed",
                    body = "“${reason.sourceName}”: ${reason.message}",
                    action = "Open Settings" to onGoToSettings,
                )
            }
        }
    }
}

@Composable
private fun EmptyMessage(
    title: String,
    body: String,
    showSpinner: Boolean = false,
    action: Pair<String, () -> Unit>? = null,
) {
    if (showSpinner) {
        CircularProgressIndicator()
        Spacer(Modifier.height(12.dp))
    }
    Text(title, style = MaterialTheme.typography.titleMedium)
    Spacer(Modifier.height(8.dp))
    Text(
        body,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    if (action != null) {
        Spacer(Modifier.height(16.dp))
        Button(onClick = action.second) { Text(action.first) }
    }
}

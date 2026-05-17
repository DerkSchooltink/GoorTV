package dev.goor.tv.ui.screens.guide

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import dev.goor.tv.data.model.Channel
import dev.goor.tv.data.model.Programme
import org.koin.androidx.compose.koinViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val DP_PER_MINUTE = 3.dp
private val CHANNEL_COL_WIDTH = 160.dp
private val ROW_HEIGHT = 64.dp
private val TIME_HEADER_HEIGHT = 24.dp
private val PROGRAMME_GAP = 1.dp
private val SLOT_MINUTES = 30L

private val hmFormatter: SimpleDateFormat by lazy {
    SimpleDateFormat("HH:mm", Locale.getDefault())
}

private fun formatHm(epochMs: Long): String = hmFormatter.format(Date(epochMs))

private fun minutesBetween(from: Long, to: Long): Long = (to - from) / 60_000L

@OptIn(ExperimentalMaterial3Api::class)
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
    val density = LocalDensity.current

    // Auto-scroll horizontally to "now" once when the screen first lands.
    LaunchedEffect(Unit) {
        val xPx = with(density) {
            (minutesBetween(windowStartMs, nowMs) * DP_PER_MINUTE.toPx() / 1).toInt()
        }
        // Center "now" by subtracting half the visible width — best-effort, viewport size is unknown here.
        scrollState.scrollTo((xPx - 200).coerceAtLeast(0))
    }

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
        when (val s = state) {
            is GuideState.Loading -> CenteredSpinner(label = "Waiting for EPG…", modifier = Modifier.padding(padding))
            is GuideState.Empty -> EmptyState(reason = s.reason, onGoToSettings = onGoToSettings, modifier = Modifier.padding(padding))
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

    Column(modifier = modifier.fillMaxSize()) {
        // Time-axis header
        Row(modifier = Modifier.height(TIME_HEADER_HEIGHT)) {
            Spacer(Modifier.width(CHANNEL_COL_WIDTH))
            Box(modifier = Modifier.horizontalScroll(scrollState).width(railWidth)) {
                TimeHeader(
                    firstSlotMs = firstSlotMs,
                    windowStartMs = windowStartMs,
                    windowEndMs = windowEndMs,
                )
            }
        }
        // Channel rows
        Box(modifier = Modifier.weight(1f)) {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(rows, key = { "${it.channel.sourceId}_${it.channel.id}" }) { row ->
                    Row(modifier = Modifier.height(ROW_HEIGHT)) {
                        ChannelHeaderCell(channel = row.channel, onClick = { onWatch(row.channel.id) })
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
                                onWatch = { onWatch(row.channel.id) },
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

@Composable
private fun TimeHeader(firstSlotMs: Long, windowStartMs: Long, windowEndMs: Long) {
    Box(modifier = Modifier.fillMaxSize()) {
        var slot = firstSlotMs
        while (slot < windowEndMs) {
            val offsetMin = minutesBetween(windowStartMs, slot).toInt()
            Text(
                formatHm(slot),
                modifier = Modifier
                    .padding(start = DP_PER_MINUTE * offsetMin),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            slot += SLOT_MINUTES * 60_000L
        }
    }
}

@Composable
private fun ChannelHeaderCell(channel: Channel, onClick: () -> Unit) {
    Row(
        modifier = Modifier
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
                contentDescription = null,
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
    onWatch: () -> Unit,
) {
    Box(modifier = Modifier.fillMaxSize()) {
        programmes.forEach { p ->
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
                onWatch = onWatch,
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
    var isFocused by remember { mutableStateOf(false) }
    val bg = when {
        isFocused -> MaterialTheme.colorScheme.primary
        isLive -> MaterialTheme.colorScheme.primaryContainer
        else -> MaterialTheme.colorScheme.surfaceVariant
    }
    val fg = when {
        isFocused -> MaterialTheme.colorScheme.onPrimary
        isLive -> MaterialTheme.colorScheme.onPrimaryContainer
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Column(
        modifier = Modifier
            .padding(start = offsetDp, end = PROGRAMME_GAP, top = 2.dp, bottom = 2.dp)
            .width(widthDp)
            .fillMaxHeight()
            .background(bg)
            .then(if (isFocused) Modifier.border(2.dp, MaterialTheme.colorScheme.primary) else Modifier)
            .onFocusChanged { isFocused = it.isFocused }
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
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            when (reason) {
                GuideEmptyReason.NoSources -> {
                    Text(
                        "No EPG configured",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Add an EPG URL to one of your sources to populate the guide.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(16.dp))
                    Button(onClick = onGoToSettings) { Text("Open Settings") }
                }
                GuideEmptyReason.Fetching -> {
                    CircularProgressIndicator()
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "Fetching guide…",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "This can take a few minutes the first time — large XMLTV files run 50 MB or more.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                GuideEmptyReason.NoTvgIds -> {
                    Text(
                        "No channels match the guide",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "The EPG was fetched, but your channels don't carry a tvg-id attribute to match programmes to. Check your playlist provider.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                GuideEmptyReason.NoProgrammes -> {
                    Text(
                        "Guide is empty",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Your channels have tvg-ids but no programmes overlap the current 26-hour window. The EPG feed may not cover these channels.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

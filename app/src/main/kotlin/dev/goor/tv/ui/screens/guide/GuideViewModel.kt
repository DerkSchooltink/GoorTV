package dev.goor.tv.ui.screens.guide

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.goor.tv.data.db.dao.ChannelDao
import dev.goor.tv.data.db.dao.ProgrammeDao
import dev.goor.tv.data.db.dao.SourceDao
import dev.goor.tv.data.model.Channel
import dev.goor.tv.data.model.Programme
import dev.goor.tv.data.model.isEpgEligible
import dev.goor.tv.util.TimeProvider
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/** One channel row in the guide grid: channel header + its programmes in the window. */
data class GuideRow(
    val channel: Channel,
    val programmes: List<Programme>,
)

/**
 * Reason the grid is empty, picked by the state reducer. UI maps each to a copy
 * variant. `EpgError` carries the source name + message verbatim from
 * `Source.epgLastError` so users can act on the actual failure.
 */
sealed interface GuideEmptyReason {
    /** No source is EPG-eligible — fresh install, or no source has been configured for EPG. */
    data object NoSources : GuideEmptyReason
    /** Eligible source(s) exist but none have been synced yet — fetch is likely in flight. */
    data object Fetching : GuideEmptyReason
    /** EPG fetched but no channels carry a `tvg-id` to match programmes to. */
    data object NoTvgIds : GuideEmptyReason
    /** Channels with `tvg-id` exist but no programmes overlap the current window. */
    data object NoProgrammes : GuideEmptyReason
    /** Most recent sync attempt errored. Takes precedence over the silent-empty reasons. */
    data class EpgError(val sourceName: String, val message: String) : GuideEmptyReason
}

sealed interface GuideState {
    data object Loading : GuideState
    data class Empty(val reason: GuideEmptyReason) : GuideState
    data class Ready(val rows: List<GuideRow>) : GuideState
}

@OptIn(ExperimentalCoroutinesApi::class)
class GuideViewModel(
    private val sourceDao: SourceDao,
    private val channelDao: ChannelDao,
    private val programmeDao: ProgrammeDao,
    timeProvider: TimeProvider,
) : ViewModel() {

    /** Minute-cadence clock — drives the now-indicator and "isLive" highlighting only. */
    val nowMs: StateFlow<Long> = timeProvider.nowMs

    /**
     * Slot-aligned anchor used to derive the programme-fetch window. Floored to
     * a 30-minute boundary so the underlying Room query only restarts every 30 min
     * instead of every minute — keeps a fresh `List<Programme>` (potentially huge
     * for big playlists) from being materialized 60× more often than necessary.
     */
    private val slotAlignedAnchorMs: StateFlow<Long> = nowMs
        .map { (it / SLOT_MS) * SLOT_MS }
        .distinctUntilChanged()
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            (System.currentTimeMillis() / SLOT_MS) * SLOT_MS,
        )

    /** Window start: [WINDOW_BACK_MS] before the slot-aligned anchor. */
    val windowStartMs: StateFlow<Long> = slotAlignedAnchorMs
        .map { it - WINDOW_BACK_MS }
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            slotAlignedAnchorMs.value - WINDOW_BACK_MS,
        )

    /** Window end: [WINDOW_FORWARD_MS] after the slot-aligned anchor. */
    val windowEndMs: StateFlow<Long> = slotAlignedAnchorMs
        .map { it + WINDOW_FORWARD_MS }
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            slotAlignedAnchorMs.value + WINDOW_FORWARD_MS,
        )

    /**
     * Rows in the grid. One per channel that has a `tvgChannelId`. Each row carries
     * the programmes overlapping the current window. Used only as an input to [state];
     * UI should consume [state] which wraps this list with empty-state semantics.
     */
    private val rows: StateFlow<List<GuideRow>?> = combine(
        windowStartMs,
        windowEndMs,
        channelDao.getVisibleWithTvgId(),
    ) { from, to, channels -> Triple(from, to, channels) }
        .flatMapLatest { (from, to, visible) ->
            if (visible.isEmpty()) return@flatMapLatest flowOf(emptyList<GuideRow>())
            // Query programmes per source, restricted to the visible channels' tvg-ids.
            // Avoids scanning the full EPG (thousands of unrelated tvg-ids and tens of
            // thousands of programmes) just to discard 99% of it in Kotlin. SQLite's
            // default SQLITE_MAX_VARIABLE_NUMBER is high enough for typical playlists
            // — only worry if a single source contributes >999 unique tvg-ids.
            val perSourceFlows = visible.groupBy { it.sourceId }.map { (sourceId, list) ->
                val tvgIds = list.mapNotNull { it.tvgChannelId }.distinct()
                programmeDao.observeWindowForChannels(sourceId, tvgIds, from, to)
                    .map { programmes -> sourceId to programmes.groupBy { it.tvgChannelId } }
            }
            combine(perSourceFlows) { perSource ->
                val byKey = HashMap<Pair<Long, String>, List<Programme>>(visible.size)
                perSource.forEach { (sourceId, byTvgId) ->
                    byTvgId.forEach { (tvgId, list) -> byKey[sourceId to tvgId] = list }
                }
                visible.map { ch ->
                    val tvgId = ch.tvgChannelId!! // non-blank by the `visible` filter above
                    GuideRow(channel = ch, programmes = byKey[ch.sourceId to tvgId].orEmpty())
                }
            }
        }
        .distinctUntilChanged()
        // null = upstream hasn't emitted yet → state stays Loading instead of flashing
        // an empty message while Room is still building the first result.
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    /**
     * Reduced render state. UI displays the grid for [GuideState.Ready], a spinner
     * for [GuideState.Loading], or a reason-specific empty message for
     * [GuideState.Empty]. The reducer prioritises actionable diagnostics
     * (`EpgError`) over silent-empty fall-throughs.
     */
    val state: StateFlow<GuideState> = combine(
        sourceDao.getAll(),
        rows,
    ) { sources, rows ->
        // rows == null → still loading from Room; keep the spinner up.
        if (rows == null) return@combine GuideState.Loading
        val eligible = sources.filter { it.isEpgEligible() }
        val erroredSource = eligible.firstOrNull { !it.epgLastError.isNullOrBlank() }
        val anySuccess = eligible.any { it.lastEpgSyncedAt != null }
        val rowsHaveProgrammes = rows.any { it.programmes.isNotEmpty() }
        when {
            eligible.isEmpty() -> GuideState.Empty(GuideEmptyReason.NoSources)
            // Hard error before any successful sync — surface the message, don't pretend we're still fetching.
            !anySuccess && erroredSource != null ->
                GuideState.Empty(GuideEmptyReason.EpgError(erroredSource.name, erroredSource.epgLastError!!))
            !anySuccess -> GuideState.Empty(GuideEmptyReason.Fetching)
            rows.isEmpty() -> GuideState.Empty(GuideEmptyReason.NoTvgIds)
            // No programmes AND a sync failed since the last success — the failure is the more useful message.
            !rowsHaveProgrammes && erroredSource != null ->
                GuideState.Empty(GuideEmptyReason.EpgError(erroredSource.name, erroredSource.epgLastError!!))
            !rowsHaveProgrammes -> GuideState.Empty(GuideEmptyReason.NoProgrammes)
            else -> GuideState.Ready(rows)
        }
    }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), GuideState.Loading)

    companion object {
        private const val WINDOW_BACK_MS = 2L * 60L * 60L * 1000L      // 2 hours
        // Forward window shrunk from 24h → 6h to bound the size of the programme
        // list materialized per emission. A 24h forward window over a large EPG
        // produced multi-hundred-MB heap retention and an OOM crash.
        private const val WINDOW_FORWARD_MS = 6L * 60L * 60L * 1000L   // 6 hours
        private const val SLOT_MS = 30L * 60L * 1000L                  // 30-min slot
    }
}

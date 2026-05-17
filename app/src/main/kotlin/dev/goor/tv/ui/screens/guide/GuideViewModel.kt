package dev.goor.tv.ui.screens.guide

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.goor.tv.data.db.dao.ChannelDao
import dev.goor.tv.data.db.dao.ProgrammeDao
import dev.goor.tv.data.db.dao.SourceDao
import dev.goor.tv.data.model.Channel
import dev.goor.tv.data.model.Programme
import dev.goor.tv.data.model.isEpgEligible
import dev.goor.tv.util.minuteTicker
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
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
) : ViewModel() {

    /** Window anchor — rebased each minute so the "now" indicator stays accurate. */
    val nowMs: StateFlow<Long> = minuteTicker()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), System.currentTimeMillis())

    /** Window start: 2 hours before now. */
    val windowStartMs: StateFlow<Long> = nowMs
        .map { it - WINDOW_BACK_MS }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), System.currentTimeMillis() - WINDOW_BACK_MS)

    /** Window end: 24 hours after now. */
    val windowEndMs: StateFlow<Long> = nowMs
        .map { it + WINDOW_FORWARD_MS }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), System.currentTimeMillis() + WINDOW_FORWARD_MS)

    /**
     * Rows in the grid. One per channel that has a `tvgChannelId`. Each row carries
     * the programmes overlapping the current window. Used only as an input to [state];
     * UI should consume [state] which wraps this list with empty-state semantics.
     */
    private val rows: StateFlow<List<GuideRow>> = combine(
        windowStartMs,
        windowEndMs,
    ) { from, to -> from to to }
        .flatMapLatest { (from, to) ->
            combine(
                channelDao.getAllVisible(),
                programmeDao.observeWindowAll(from, to),
            ) { channels, programmes ->
                val byKey = programmes.groupBy { it.sourceId to it.tvgChannelId }
                channels.mapNotNull { ch ->
                    val tvgId = ch.tvgChannelId?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                    GuideRow(channel = ch, programmes = byKey[ch.sourceId to tvgId].orEmpty())
                }
            }
        }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

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
        private const val WINDOW_FORWARD_MS = 24L * 60L * 60L * 1000L  // 24 hours
    }
}

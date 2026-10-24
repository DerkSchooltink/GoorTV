package dev.goor.tv.ui.screens.home

import android.util.Log
import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.cachedIn
import androidx.paging.insertSeparators
import androidx.paging.map
import dev.goor.tv.R
import dev.goor.tv.data.ManualSourceManager
import dev.goor.tv.data.SearchHistoryRepository
import dev.goor.tv.data.db.dao.ChannelDao
import dev.goor.tv.data.db.dao.ProgrammeDao
import dev.goor.tv.data.db.dao.SourceDao
import dev.goor.tv.data.model.Channel
import dev.goor.tv.data.preferences.SortOrder
import dev.goor.tv.data.preferences.UserPreferencesRepository
import dev.goor.tv.network.SourceSyncService
import dev.goor.tv.network.SyncFailure
import dev.goor.tv.util.TimeProvider
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModel(
    private val channelDao: ChannelDao,
    private val sourceDao: SourceDao,
    private val syncService: SourceSyncService,
    private val searchHistoryRepo: SearchHistoryRepository,
    private val prefsRepository: UserPreferencesRepository,
    private val programmeDao: ProgrammeDao,
    private val manualSource: ManualSourceManager,
    timeProvider: TimeProvider,
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    val searchQuery = _searchQuery.asStateFlow()

    private val _showFavoritesOnly = MutableStateFlow(false)
    val showFavoritesOnly = _showFavoritesOnly.asStateFlow()

    private val _isSyncing = MutableStateFlow(false)
    val isSyncing = _isSyncing.asStateFlow()

    /** Per-source sync failures from the network layer; the UI localizes them. */
    private val _syncErrors = MutableStateFlow<List<SyncFailure>>(emptyList())
    val syncErrors = _syncErrors.asStateFlow()

    /** One-shot message resource for a failed user action (favourite toggle, hide, etc.). */
    private val _actionError = MutableStateFlow<Int?>(null)
    val actionError: StateFlow<Int?> = _actionError.asStateFlow()

    fun clearActionError() { _actionError.value = null }

    /**
     * Runs a Room write off the UI, surfacing [failureMessageRes] if it throws.
     * Previously these were fire-and-forget `launch {}` blocks that swallowed
     * write failures (disk full, corruption, constraint) with no feedback.
     */
    private fun launchWrite(@StringRes failureMessageRes: Int, block: suspend () -> Unit) {
        viewModelScope.launch {
            runCatching { block() }.onFailure {
                Log.e(TAG, "Write failed: ${it.message}", it)
                _actionError.value = failureMessageRes
            }
        }
    }

    /** ID of the singleton "Custom Channels" source, or null if it hasn't been created yet. */
    val manualSourceId: StateFlow<Long?> = manualSource.manualSourceId

    val searchHistory = searchHistoryRepo.history

    val sources = sourceDao.getAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val groups = channelDao.getGroups()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val recentlyWatched = channelDao.getRecentlyWatched()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val sortOrder = prefsRepository.sortOrder
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SortOrder.BY_GROUP)

    /** Shared minute-cadence clock; used to refresh "now playing". */
    val nowMs: StateFlow<Long> = timeProvider.nowMs

    /**
     * "Now and next" programme per channel, keyed by `(sourceId, tvgChannelId)`.
     * Recomputed whenever [nowMs] ticks or the Room programmes table changes.
     * A channel with no EPG is simply absent from the map; the row then falls
     * back to its group name.
     */
    val nowNextByChannel: StateFlow<Map<Pair<Long, String>, ChannelNowNext>> = nowMs
        .flatMapLatest { now ->
            combine(
                programmeDao.observeAllNow(now),
                programmeDao.observeAllNext(now),
            ) { current, upcoming -> mergeNowNext(current, upcoming) }
        }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    /**
     * Trimmed search text — the single policy every consumer shares. Blank means
     * "no search": the clock is pinned to 0, SQL receives an empty query, and
     * group headers stay enabled. The raw text remains in [searchQuery] so the
     * field shows exactly what the user typed.
     */
    private val effectiveQuery: Flow<String> = _searchQuery
        .map { it.trim() }
        .distinctUntilChanged()

    /**
     * Clock value handed to the paged search query. Pinned to 0 while the
     * effective query is empty so the minute tick doesn't tear down and recreate
     * the pager (which would reset the scroll position) when no programme-title
     * search is needed.
     */
    private val searchNowMs: Flow<Long> = combine(nowMs, effectiveQuery) { now, query ->
        if (query.isEmpty()) 0L else now
    }.distinctUntilChanged()

    private data class PagingRequest(
        /** Trimmed user text, used for UI decisions such as group headers. */
        val query: String,
        /** [query] escaped for SQLite `LIKE … ESCAPE '\'`. */
        val likeQuery: String,
        val favOnly: Boolean,
        val sort: SortOrder,
        val nowMs: Long,
    )

    val pagingData = combine(
        effectiveQuery,
        _showFavoritesOnly,
        prefsRepository.sortOrder,
        searchNowMs,
    ) { query, favOnly, sort, now ->
        PagingRequest(query, escapeLikePattern(query), favOnly, sort, now)
    }.distinctUntilChanged().flatMapLatest { request ->
        Pager(PagingConfig(pageSize = 30, prefetchDistance = 90, enablePlaceholders = false)) {
            when (request.sort) {
                SortOrder.BY_NAME ->
                    channelDao.getChannelsPagedByName(null, request.likeQuery, request.favOnly, request.nowMs)
                SortOrder.BY_LAST_WATCHED ->
                    channelDao.getChannelsPagedByLastWatched(null, request.likeQuery, request.favOnly, request.nowMs)
                SortOrder.BY_GROUP ->
                    channelDao.getChannelsPaged(null, request.likeQuery, request.favOnly, request.nowMs)
            }
        }.flow.map { paging ->
            paging.map { ChannelListItem.Item(it) as ChannelListItem }
                .insertSeparators { before, after ->
                    if (request.sort != SortOrder.BY_GROUP || request.query.isNotEmpty() || request.favOnly) {
                        return@insertSeparators null
                    }
                    val afterGroup = (after as? ChannelListItem.Item)?.channel?.group ?: return@insertSeparators null
                    val beforeGroup = (before as? ChannelListItem.Item)?.channel?.group
                    if (beforeGroup != afterGroup) ChannelListItem.Header(afterGroup) else null
                }
        }
    }.cachedIn(viewModelScope)

    fun addCustomChannel(name: String, url: String, logoUrl: String?, group: String?) =
        launchWrite(R.string.home_error_add_channel) { manualSource.addChannel(name, url, logoUrl, group) }

    fun updateCustomChannel(channel: Channel) =
        launchWrite(R.string.home_error_update_channel) { manualSource.updateChannel(channel) }

    fun deleteCustomChannel(channel: Channel) =
        launchWrite(R.string.home_error_delete_channel) { manualSource.deleteChannel(channel) }

    /** User-initiated refresh — bypasses the throttle that the background coordinator respects. */
    fun sync() {
        viewModelScope.launch {
            _isSyncing.value = true
            _syncErrors.value = syncService.syncAll(throttleMs = 0L)
            _isSyncing.value = false
        }
    }

    fun setSearchQuery(query: String) { _searchQuery.value = query }
    fun addToSearchHistory(query: String) { searchHistoryRepo.add(query) }
    fun toggleFavoritesOnly() { _showFavoritesOnly.value = !_showFavoritesOnly.value }

    fun toggleFavorite(channelId: Long) =
        launchWrite(R.string.home_error_update_favourite) { channelDao.toggleFavorite(channelId) }

    fun hideChannel(channelId: Long) =
        launchWrite(R.string.home_error_hide_channel) { channelDao.setHidden(channelId, true) }

    fun clearRecentlyWatched() =
        launchWrite(R.string.home_error_clear_recently_watched) { channelDao.clearRecentlyWatched() }

    fun setSortOrder(order: SortOrder) =
        launchWrite(R.string.home_error_change_sort_order) { prefsRepository.setSortOrder(order) }

    private companion object {
        const val TAG = "HomeViewModel"
    }
}

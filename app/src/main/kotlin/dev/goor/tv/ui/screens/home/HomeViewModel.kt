package dev.goor.tv.ui.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.cachedIn
import androidx.paging.insertSeparators
import androidx.paging.map
import dev.goor.tv.data.SearchHistoryRepository
import dev.goor.tv.data.db.dao.ChannelDao
import dev.goor.tv.data.db.dao.ProgrammeDao
import dev.goor.tv.data.db.dao.SourceDao
import dev.goor.tv.data.model.Channel
import dev.goor.tv.data.model.Programme
import dev.goor.tv.data.model.Source
import dev.goor.tv.data.model.SourceType
import dev.goor.tv.data.preferences.SortOrder
import dev.goor.tv.data.preferences.UserPreferencesRepository
import dev.goor.tv.network.SourceSyncService
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
    timeProvider: TimeProvider,
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    val searchQuery = _searchQuery.asStateFlow()

    private val _showFavoritesOnly = MutableStateFlow(false)
    val showFavoritesOnly = _showFavoritesOnly.asStateFlow()

    private val _isSyncing = MutableStateFlow(false)
    val isSyncing = _isSyncing.asStateFlow()

    private val _syncErrors = MutableStateFlow<List<String>>(emptyList())
    val syncErrors = _syncErrors.asStateFlow()

    private val _manualSourceId = MutableStateFlow<Long?>(null)
    val manualSourceId = _manualSourceId.asStateFlow()

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
     * "Now playing" programme per channel, keyed by `(sourceId, tvgChannelId)`.
     * Recomputed whenever [nowMs] ticks or the Room programmes table changes.
     */
    val nowByChannel: StateFlow<Map<Pair<Long, String>, Programme>> = nowMs
        .flatMapLatest { now -> programmeDao.observeAllNow(now) }
        .map { list -> list.associateBy { it.sourceId to it.tvgChannelId } }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    val pagingData = combine(_searchQuery, _showFavoritesOnly, prefsRepository.sortOrder) { query, favOnly, sort ->
        Triple(query, favOnly, sort)
    }.flatMapLatest { (query, favOnly, sort) ->
        Pager(PagingConfig(pageSize = 30, prefetchDistance = 90, enablePlaceholders = false)) {
            when (sort) {
                SortOrder.BY_NAME -> channelDao.getChannelsPagedByName(null, query, favOnly)
                SortOrder.BY_LAST_WATCHED -> channelDao.getChannelsPagedByLastWatched(null, query, favOnly)
                SortOrder.BY_GROUP -> channelDao.getChannelsPaged(null, query, favOnly)
            }
        }.flow.map { paging ->
            paging.map { ChannelListItem.Item(it) as ChannelListItem }
                .insertSeparators { before, after ->
                    if (sort != SortOrder.BY_GROUP || query.isNotBlank() || favOnly) return@insertSeparators null
                    val afterGroup = (after as? ChannelListItem.Item)?.channel?.group ?: return@insertSeparators null
                    val beforeGroup = (before as? ChannelListItem.Item)?.channel?.group
                    if (beforeGroup != afterGroup) ChannelListItem.Header(afterGroup) else null
                }
        }
    }.cachedIn(viewModelScope)

    init {
        // Background sync is owned by AppSyncCoordinator (kicked off in App.onCreate)
        // so opening directly to Guide/Settings still syncs. Only screen-local state
        // lookup remains here.
        viewModelScope.launch {
            sourceDao.getManualSource()?.let { _manualSourceId.value = it.id }
        }
    }

    private suspend fun getOrCreateManualSourceId(): Long {
        _manualSourceId.value?.let { return it }
        val existing = sourceDao.getManualSource()
        if (existing != null) {
            _manualSourceId.value = existing.id
            return existing.id
        }
        val id = sourceDao.insert(
            Source(name = "Custom Channels", type = SourceType.MANUAL, url = "", includedGroups = null)
        )
        _manualSourceId.value = id
        return id
    }

    fun addCustomChannel(name: String, url: String, logoUrl: String?, group: String?) {
        viewModelScope.launch {
            val sourceId = getOrCreateManualSourceId()
            channelDao.insert(Channel(sourceId = sourceId, name = name, url = url, logoUrl = logoUrl, group = group))
        }
    }

    fun updateCustomChannel(channel: Channel) {
        viewModelScope.launch { channelDao.update(channel) }
    }

    fun deleteCustomChannel(channel: Channel) {
        viewModelScope.launch { channelDao.delete(channel) }
    }

    /** User-initiated refresh — bypasses the throttle that the background coordinator respects. */
    fun sync() {
        viewModelScope.launch {
            _isSyncing.value = true
            _syncErrors.value = syncService.syncAll(throttleMs = 0L).map { it.message ?: "Unknown error" }
            _isSyncing.value = false
        }
    }

    fun setSearchQuery(query: String) { _searchQuery.value = query }
    fun addToSearchHistory(query: String) { searchHistoryRepo.add(query) }
    fun toggleFavoritesOnly() { _showFavoritesOnly.value = !_showFavoritesOnly.value }

    fun toggleFavorite(channelId: Long) {
        viewModelScope.launch { channelDao.toggleFavorite(channelId) }
    }

    fun clearRecentlyWatched() {
        viewModelScope.launch { channelDao.clearRecentlyWatched() }
    }

    fun setSortOrder(order: SortOrder) {
        viewModelScope.launch { prefsRepository.setSortOrder(order) }
    }
}

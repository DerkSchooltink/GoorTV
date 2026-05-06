package dev.goor.tv.ui.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.cachedIn
import androidx.paging.insertSeparators
import androidx.paging.map
import dev.goor.tv.data.db.dao.ChannelDao
import dev.goor.tv.data.db.dao.SourceDao
import dev.goor.tv.network.SourceSyncService
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModel(
    private val channelDao: ChannelDao,
    private val sourceDao: SourceDao,
    private val syncService: SourceSyncService,
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    val searchQuery = _searchQuery.asStateFlow()

    private val _selectedGroup = MutableStateFlow<String?>(null)
    val selectedGroup = _selectedGroup.asStateFlow()

    private val _showFavoritesOnly = MutableStateFlow(false)
    val showFavoritesOnly = _showFavoritesOnly.asStateFlow()

    private val _isSyncing = MutableStateFlow(false)
    val isSyncing = _isSyncing.asStateFlow()

    private val _syncErrors = MutableStateFlow<List<String>>(emptyList())
    val syncErrors = _syncErrors.asStateFlow()

    val sources = sourceDao.getAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val groups = channelDao.getGroups()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val recentlyWatched = channelDao.getRecentlyWatched()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val pagingData = combine(_searchQuery, _selectedGroup, _showFavoritesOnly) {
        query, group, favOnly -> Triple(query, group, favOnly)
    }.flatMapLatest { (query, group, favOnly) ->
        Pager(PagingConfig(pageSize = 30, prefetchDistance = 90, enablePlaceholders = false)) {
            channelDao.getChannelsPaged(group, query, favOnly)
        }.flow.map { paging ->
            paging.map { ChannelListItem.Item(it) as ChannelListItem }
                .insertSeparators { before, after ->
                    if (group != null || query.isNotBlank() || favOnly) return@insertSeparators null
                    val afterGroup = (after as? ChannelListItem.Item)?.channel?.group ?: return@insertSeparators null
                    val beforeGroup = (before as? ChannelListItem.Item)?.channel?.group
                    if (beforeGroup != afterGroup) ChannelListItem.Header(afterGroup) else null
                }
        }
    }.cachedIn(viewModelScope)

    init {
        viewModelScope.launch {
            if (channelDao.count() == 0) sync()
        }
    }

    fun sync() {
        viewModelScope.launch {
            _isSyncing.value = true
            _syncErrors.value = syncService.syncAll().map { it.message ?: "Unknown error" }
            _isSyncing.value = false
        }
    }

    fun setSearchQuery(query: String) { _searchQuery.value = query }
    fun selectGroup(group: String?) { _selectedGroup.value = group }
    fun toggleFavoritesOnly() { _showFavoritesOnly.value = !_showFavoritesOnly.value }

    fun toggleFavorite(channelId: Long) {
        viewModelScope.launch { channelDao.toggleFavorite(channelId) }
    }

    fun clearRecentlyWatched() {
        viewModelScope.launch { channelDao.clearRecentlyWatched() }
    }
}

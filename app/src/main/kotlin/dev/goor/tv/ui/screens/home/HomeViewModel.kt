package dev.goor.tv.ui.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.goor.tv.data.db.dao.ChannelDao
import dev.goor.tv.data.db.dao.SourceDao
import dev.goor.tv.data.model.Channel
import dev.goor.tv.network.SourceSyncService
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

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

    val sources = sourceDao.getAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val allChannels = channelDao.getAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val channels: StateFlow<List<Channel>> = combine(
        allChannels, _searchQuery, _selectedGroup, _showFavoritesOnly,
    ) { all, query, group, favOnly ->
        all
            .filter { group == null || it.group == group }
            .filter { !favOnly || it.isFavorite }
            .filter { query.isBlank() || it.name.contains(query, ignoreCase = true) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val groups: StateFlow<List<String>> = allChannels
        .map { channels -> channels.mapNotNull { it.group }.distinct().sorted() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val recentlyWatched: StateFlow<List<Channel>> = allChannels
        .map { channels ->
            channels
                .filter { it.lastWatchedAt != null }
                .sortedByDescending { it.lastWatchedAt }
                .take(10)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        viewModelScope.launch {
            _isSyncing.value = true
            syncService.syncAll()
            _isSyncing.value = false
        }
    }

    fun setSearchQuery(query: String) { _searchQuery.value = query }
    fun selectGroup(group: String?) { _selectedGroup.value = group }
    fun toggleFavoritesOnly() { _showFavoritesOnly.value = !_showFavoritesOnly.value }

    fun toggleFavorite(channelId: Long) {
        viewModelScope.launch { channelDao.toggleFavorite(channelId) }
    }
}

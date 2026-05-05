package dev.goor.tv.ui.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.goor.tv.data.db.dao.SourceDao
import dev.goor.tv.data.model.Source
import dev.goor.tv.data.model.SourceType
import dev.goor.tv.network.SourceSyncService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class SettingsViewModel(
    private val sourceDao: SourceDao,
    private val syncService: SourceSyncService,
) : ViewModel() {
    val sources = sourceDao.getAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _syncingIds = MutableStateFlow<Set<Long>>(emptySet())
    val syncingIds = _syncingIds.asStateFlow()
    val syncing = _syncingIds.map { it.isNotEmpty() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    fun addM3uSource(name: String, url: String) {
        viewModelScope.launch {
            val id = sourceDao.insert(Source(name = name, type = SourceType.M3U, url = url))
            syncSource(sourceDao.getById(id) ?: return@launch)
        }
    }

    fun addXtreamSource(name: String, url: String, username: String, password: String) {
        viewModelScope.launch {
            val id = sourceDao.insert(Source(name = name, type = SourceType.XTREAM, url = url, username = username, password = password))
            syncSource(sourceDao.getById(id) ?: return@launch)
        }
    }

    fun deleteSource(source: Source) {
        viewModelScope.launch { sourceDao.delete(source) }
    }

    fun syncSource(source: Source) {
        viewModelScope.launch {
            _syncingIds.update { it + source.id }
            runCatching { syncService.sync(source) }
            _syncingIds.update { it - source.id }
        }
    }
}

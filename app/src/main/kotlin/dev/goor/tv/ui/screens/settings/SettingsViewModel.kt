package dev.goor.tv.ui.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import android.database.sqlite.SQLiteConstraintException
import dev.goor.tv.data.db.dao.ChannelDao
import dev.goor.tv.data.db.dao.SourceDao
import dev.goor.tv.data.model.Source
import dev.goor.tv.data.model.SourceType
import dev.goor.tv.network.EpgSyncService
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
    private val channelDao: ChannelDao,
    private val syncService: SourceSyncService,
    private val epgSyncService: EpgSyncService,
) : ViewModel() {
    val sources = sourceDao.getAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _syncingIds = MutableStateFlow<Set<Long>>(emptySet())
    val syncingIds = _syncingIds.asStateFlow()
    val syncing = _syncingIds.map { it.isNotEmpty() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    private val _epgSyncingIds = MutableStateFlow<Set<Long>>(emptySet())
    val epgSyncingIds = _epgSyncingIds.asStateFlow()

    private val _snackbarMessage = MutableStateFlow<String?>(null)
    val snackbarMessage = _snackbarMessage.asStateFlow()

    fun clearSnackbar() { _snackbarMessage.value = null }

    fun addM3uSource(name: String, url: String, headers: String? = null, maxConcurrentStreams: Int = 0) {
        viewModelScope.launch {
            if (sourceDao.findByTypeAndUrl(SourceType.M3U.name, url) != null) {
                _snackbarMessage.value = "An M3U source with that URL already exists"
                return@launch
            }
            // Pre-check is racy against the unique (type, url) index — catch the race here
            // so concurrent adds surface the same friendly message instead of a crash.
            val id = try {
                sourceDao.insert(Source(name = name, type = SourceType.M3U, url = url, headers = headers?.takeIf { it.isNotBlank() }, maxConcurrentStreams = maxConcurrentStreams))
            } catch (_: SQLiteConstraintException) {
                _snackbarMessage.value = "An M3U source with that URL already exists"
                return@launch
            }
            val src = sourceDao.getById(id) ?: return@launch
            syncSource(src)
            syncEpg(src)
        }
    }

    fun addXtreamSource(name: String, url: String, username: String, password: String, headers: String? = null, maxConcurrentStreams: Int = 0) {
        viewModelScope.launch {
            if (sourceDao.findByTypeAndUrl(SourceType.XTREAM.name, url) != null) {
                _snackbarMessage.value = "An Xtream source with that URL already exists"
                return@launch
            }
            val id = try {
                sourceDao.insert(Source(name = name, type = SourceType.XTREAM, url = url, username = username, password = password, headers = headers?.takeIf { it.isNotBlank() }, maxConcurrentStreams = maxConcurrentStreams))
            } catch (_: SQLiteConstraintException) {
                _snackbarMessage.value = "An Xtream source with that URL already exists"
                return@launch
            }
            val src = sourceDao.getById(id) ?: return@launch
            syncSource(src)
            syncEpg(src)
        }
    }

    fun updateSource(source: Source) {
        viewModelScope.launch { sourceDao.update(source) }
    }

    fun deleteSource(source: Source) {
        viewModelScope.launch { sourceDao.delete(source) }
    }

    fun getGroupsForSource(sourceId: Long) = channelDao.getGroupsForSource(sourceId)

    fun updateIncludedGroups(sourceId: Long, groups: Set<String>) {
        viewModelScope.launch {
            sourceDao.updateIncludedGroups(sourceId, if (groups.isEmpty()) "" else groups.joinToString("|"))
        }
    }

    fun syncSource(source: Source) {
        viewModelScope.launch {
            _syncingIds.update { it + source.id }
            runCatching { syncService.sync(source) }
                .onFailure { _snackbarMessage.value = "Failed to sync \"${source.name}\": ${it.message}" }
            _syncingIds.update { it - source.id }
        }
    }

    fun syncEpg(source: Source) {
        viewModelScope.launch {
            _epgSyncingIds.update { it + source.id }
            runCatching { epgSyncService.sync(source) }
                .onFailure { _snackbarMessage.value = "Failed to sync EPG for \"${source.name}\": ${it.message}" }
            _epgSyncingIds.update { it - source.id }
        }
    }

    fun updateEpgUrl(sourceId: Long, url: String?) {
        viewModelScope.launch {
            val trimmed = url?.takeIf { it.isNotBlank() }
            sourceDao.updateEpgUrl(sourceId, trimmed)
            if (trimmed != null) {
                sourceDao.getById(sourceId)?.let { syncEpg(it) }
            }
        }
    }
}

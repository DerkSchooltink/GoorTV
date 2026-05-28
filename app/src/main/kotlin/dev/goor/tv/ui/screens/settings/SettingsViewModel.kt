package dev.goor.tv.ui.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import android.database.sqlite.SQLiteConstraintException
import dev.goor.tv.data.db.dao.ChannelDao
import dev.goor.tv.data.db.dao.SourceDao
import dev.goor.tv.data.model.Secret
import dev.goor.tv.data.model.Source
import dev.goor.tv.data.model.SourceType
import dev.goor.tv.data.model.XtreamOutput
import dev.goor.tv.network.EpgSyncService
import dev.goor.tv.network.SourceSyncService
import io.ktor.http.Url
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

    /** Count of user-hidden channels — drives the "Hidden channels (N)" Settings entry. */
    val hiddenCount = channelDao.getHiddenCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

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
            if (!isValidSourceUrl(url)) {
                _snackbarMessage.value = "Enter a valid http:// or https:// URL"
                return@launch
            }
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

    fun addXtreamSource(name: String, url: String, username: String, password: String, headers: String? = null, maxConcurrentStreams: Int = 0, xtreamOutput: XtreamOutput = XtreamOutput.TS) {
        viewModelScope.launch {
            if (!isValidSourceUrl(url)) {
                _snackbarMessage.value = "Enter a valid http:// or https:// URL"
                return@launch
            }
            if (sourceDao.findByTypeAndUrl(SourceType.XTREAM.name, url) != null) {
                _snackbarMessage.value = "An Xtream source with that URL already exists"
                return@launch
            }
            val id = try {
                sourceDao.insert(
                    Source(
                        name = name,
                        type = SourceType.XTREAM,
                        url = url,
                        username = username.takeIf { it.isNotBlank() }?.let(::Secret),
                        password = password.takeIf { it.isNotBlank() }?.let(::Secret),
                        headers = headers?.takeIf { it.isNotBlank() },
                        maxConcurrentStreams = maxConcurrentStreams,
                        xtreamOutput = xtreamOutput,
                    ),
                )
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
        viewModelScope.launch {
            if (!isValidSourceUrl(source.url)) {
                _snackbarMessage.value = "Enter a valid http:// or https:// URL"
                return@launch
            }
            sourceDao.update(source)
        }
    }

    /**
     * Rejects unparseable / non-http(s) URLs at the entry point so a malformed
     * source URL can't burn the sync layer's full retry budget on a permanent
     * `Url(...)` parse failure before surfacing.
     */
    private fun isValidSourceUrl(url: String): Boolean {
        val trimmed = url.trim()
        if (!trimmed.startsWith("http://", ignoreCase = true) &&
            !trimmed.startsWith("https://", ignoreCase = true)
        ) return false
        return runCatching { Url(trimmed).host.isNotBlank() }.getOrDefault(false)
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

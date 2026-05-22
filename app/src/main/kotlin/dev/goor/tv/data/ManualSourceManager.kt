package dev.goor.tv.data

import dev.goor.tv.data.db.dao.ChannelDao
import dev.goor.tv.data.db.dao.SourceDao
import dev.goor.tv.data.model.Channel
import dev.goor.tv.data.model.Source
import dev.goor.tv.data.model.SourceType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Owns the lifecycle of the singleton MANUAL source (the "Custom Channels"
 * holder used for user-added channels) and the CRUD on its channels.
 *
 * Extracted from `HomeViewModel` to consolidate the manual-source concern in
 * one place — previously `getOrCreateManualSourceId` was racy across concurrent
 * `addCustomChannel` calls (cached value, then DB lookup, then insert) and
 * could create two MANUAL rows on a fresh install if the user added channels
 * faster than the init load could finish. The unique `(type, url)` index
 * caught it at the DB layer, but the user saw a constraint error.
 *
 * Mutex serializes `getOrCreate` so only one insert wins. The `manualSourceId`
 * StateFlow lets the UI know whether a custom-channel section should render.
 */
class ManualSourceManager(
    private val sourceDao: SourceDao,
    private val channelDao: ChannelDao,
    /** Overridable so tests can drive the init load with their own dispatcher. */
    scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
) {
    private val scope = scope
    private val ensureMutex = Mutex()

    private val _manualSourceId = MutableStateFlow<Long?>(null)
    val manualSourceId: StateFlow<Long?> = _manualSourceId.asStateFlow()

    init {
        scope.launch {
            sourceDao.getManualSource()?.let { _manualSourceId.value = it.id }
        }
    }

    suspend fun addChannel(name: String, url: String, logoUrl: String?, group: String?) {
        val sourceId = getOrCreate()
        channelDao.insert(
            Channel(sourceId = sourceId, name = name, url = url, logoUrl = logoUrl, group = group)
        )
    }

    suspend fun updateChannel(channel: Channel) = channelDao.update(channel)

    suspend fun deleteChannel(channel: Channel) = channelDao.delete(channel)

    private suspend fun getOrCreate(): Long = ensureMutex.withLock {
        _manualSourceId.value?.let { return@withLock it }
        sourceDao.getManualSource()?.let {
            _manualSourceId.value = it.id
            return@withLock it.id
        }
        val id = sourceDao.insert(
            Source(name = "Custom Channels", type = SourceType.MANUAL, url = "", includedGroups = null)
        )
        _manualSourceId.value = id
        id
    }
}

package dev.goor.tv.ui.screens.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.goor.tv.data.StreamConcurrencyTracker
import dev.goor.tv.data.db.dao.ChannelDao
import dev.goor.tv.data.db.dao.SourceDao
import dev.goor.tv.data.model.Channel
import dev.goor.tv.data.model.headersMap
import dev.goor.tv.dlna.DlnaDevice
import dev.goor.tv.dlna.DlnaService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class PlayerViewModel(
    private val channelId: Long,
    private val channelDao: ChannelDao,
    private val sourceDao: SourceDao,
    private val dlnaService: DlnaService,
    private val concurrencyTracker: StreamConcurrencyTracker,
) : ViewModel() {
    private val _channel = MutableStateFlow<Channel?>(null)
    val channel = _channel.asStateFlow()

    private val _headers = MutableStateFlow<Map<String, String>>(emptyMap())
    val headers = _headers.asStateFlow()

    private val _stopped = MutableStateFlow(false)
    val stopped = _stopped.asStateFlow()

    val dlnaDevices = dlnaService.devices

    private var unregisterStream: () -> Unit = {}

    init {
        viewModelScope.launch {
            val ch = channelDao.getById(channelId)
            _channel.value = ch
            if (ch != null) {
                channelDao.updateLastWatched(channelId, System.currentTimeMillis())
                val source = sourceDao.getById(ch.sourceId)
                _headers.value = source?.headersMap() ?: emptyMap()
                unregisterStream = concurrencyTracker.register(
                    sourceId = ch.sourceId,
                    maxConcurrent = source?.maxConcurrentStreams ?: 0,
                    onForceStop = { _stopped.value = true },
                )
            }
        }
        dlnaService.startDiscovery()
    }

    fun castTo(device: DlnaDevice) {
        val ch = _channel.value ?: return
        dlnaService.castTo(device, ch.url, ch.name)
    }

    override fun onCleared() {
        unregisterStream()
        dlnaService.stopDiscovery()
    }
}

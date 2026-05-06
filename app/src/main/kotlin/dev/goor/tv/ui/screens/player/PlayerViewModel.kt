package dev.goor.tv.ui.screens.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.goor.tv.data.db.dao.ChannelDao
import dev.goor.tv.data.model.Channel
import dev.goor.tv.dlna.DlnaDevice
import dev.goor.tv.dlna.DlnaService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class PlayerViewModel(
    private val channelId: Long,
    private val channelDao: ChannelDao,
    private val dlnaService: DlnaService,
) : ViewModel() {
    private val _channel = MutableStateFlow<Channel?>(null)
    val channel = _channel.asStateFlow()

    val dlnaDevices = dlnaService.devices

    init {
        viewModelScope.launch {
            val ch = channelDao.getById(channelId)
            _channel.value = ch
            if (ch != null) {
                channelDao.updateLastWatched(channelId, System.currentTimeMillis())
            }
        }
        dlnaService.startDiscovery()
    }

    fun castTo(device: DlnaDevice) {
        val ch = _channel.value ?: return
        dlnaService.castTo(device, ch.url, ch.name)
    }

    override fun onCleared() {
        dlnaService.stopDiscovery()
    }
}

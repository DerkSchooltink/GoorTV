package dev.goor.tv.ui.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.goor.tv.data.db.dao.ChannelDao
import dev.goor.tv.data.model.Channel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class HiddenChannelsViewModel(
    private val channelDao: ChannelDao,
) : ViewModel() {
    val hidden = channelDao.getHidden()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList<Channel>())

    fun unhide(channelId: Long) {
        viewModelScope.launch { channelDao.setHidden(channelId, false) }
    }
}

package dev.goor.tv.ui.screens.home

import dev.goor.tv.data.model.Channel

sealed interface ChannelListItem {
    data class Header(val title: String) : ChannelListItem
    data class Item(val channel: Channel) : ChannelListItem
}

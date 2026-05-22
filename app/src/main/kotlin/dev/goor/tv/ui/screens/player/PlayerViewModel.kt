package dev.goor.tv.ui.screens.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.goor.tv.data.StreamConcurrencyTracker
import dev.goor.tv.data.db.dao.ChannelDao
import dev.goor.tv.data.db.dao.ProgrammeDao
import dev.goor.tv.data.db.dao.SourceDao
import dev.goor.tv.data.model.Channel
import dev.goor.tv.data.model.Programme
import dev.goor.tv.data.model.headersMap
import dev.goor.tv.util.TimeProvider
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class PlayerViewModel(
    private val channelId: Long,
    private val channelDao: ChannelDao,
    private val sourceDao: SourceDao,
    private val concurrencyTracker: StreamConcurrencyTracker,
    private val programmeDao: ProgrammeDao,
    timeProvider: TimeProvider,
) : ViewModel() {
    private val _channel = MutableStateFlow<Channel?>(null)
    val channel = _channel.asStateFlow()

    private val _headers = MutableStateFlow<Map<String, String>>(emptyMap())
    val headers = _headers.asStateFlow()

    private val _stopped = MutableStateFlow(false)
    val stopped = _stopped.asStateFlow()

    val nowMs: StateFlow<Long> = timeProvider.nowMs

    /**
     * Current + next programme for this channel. Empty list when the channel has
     * no `tvgChannelId` or no matching programmes exist.
     */
    val nowAndNext: StateFlow<List<Programme>> = combine(_channel, nowMs) { ch, now -> ch to now }
        .flatMapLatest { (ch, now) ->
            val tvgId = ch?.tvgChannelId
            if (ch == null || tvgId.isNullOrBlank()) flowOf(emptyList())
            else programmeDao.observeNowAndNext(ch.sourceId, tvgId, now)
        }
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

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
    }

    override fun onCleared() {
        unregisterStream()
    }
}

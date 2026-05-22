package dev.goor.tv.util

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

/**
 * Single shared minute-cadence clock. Replaces the per-ViewModel
 * `minuteTicker().stateIn(viewModelScope, ...)` pattern so all screens tick on
 * the same edge and the timer stops 5 s after the last subscriber unsubscribes.
 *
 * Tests inject a fake by mocking this class and returning a controllable
 * [kotlinx.coroutines.flow.MutableStateFlow] from [nowMs].
 */
open class TimeProvider(
    intervalMs: Long = 60_000L,
    scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
) {
    open val nowMs: StateFlow<Long> = minuteTicker(intervalMs)
        .stateIn(scope, SharingStarted.WhileSubscribed(5_000L), System.currentTimeMillis())
}

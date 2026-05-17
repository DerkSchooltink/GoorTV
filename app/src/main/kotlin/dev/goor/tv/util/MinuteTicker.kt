package dev.goor.tv.util

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * Emits `System.currentTimeMillis()` immediately, then again every [intervalMs].
 * Default cadence (60 s) is appropriate for EPG "now playing" UIs — the
 * underlying programme set changes at most once per minute, and a tighter
 * cadence would just thrash recompositions.
 */
fun minuteTicker(intervalMs: Long = 60_000L): Flow<Long> = flow {
    while (true) {
        emit(System.currentTimeMillis())
        delay(intervalMs)
    }
}

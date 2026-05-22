package dev.goor.tv.util

import app.cash.turbine.test
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TimeProviderTest {

    @Test
    fun `nowMs emits an initial value immediately`() = runTest {
        val provider = TimeProvider(scope = CoroutineScope(StandardTestDispatcher(testScheduler)))
        // first() must complete without advancing — StateFlow always has a value.
        provider.nowMs.first()
    }

    @Test
    fun `nowMs ticks at the configured interval while subscribed`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val provider = TimeProvider(intervalMs = 1_000L, scope = CoroutineScope(dispatcher))

        provider.nowMs.test {
            awaitItem() // initial emission
            advanceTimeBy(1_500L)
            awaitItem() // tick after one interval
            cancelAndIgnoreRemainingEvents()
        }
    }
}

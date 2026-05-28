package dev.goor.tv.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StreamConcurrencyTrackerTest {

    @Test
    fun `unlimited source never force-stops`() {
        val tracker = StreamConcurrencyTracker()
        var stopped = false
        val unregister = tracker.register(sourceId = 1L, maxConcurrent = 0) { stopped = true }
        assertFalse(stopped)
        unregister() // no-op, must not throw
    }

    @Test
    fun `streams under the limit are accepted`() {
        val tracker = StreamConcurrencyTracker()
        var aStopped = false
        var bStopped = false
        tracker.register(1L, maxConcurrent = 2) { aStopped = true }
        tracker.register(1L, maxConcurrent = 2) { bStopped = true }
        assertFalse(aStopped)
        assertFalse(bStopped)
    }

    @Test
    fun `at capacity the new stream is refused, existing ones keep running`() {
        val tracker = StreamConcurrencyTracker()
        var firstStopped = false
        var secondStopped = false
        tracker.register(1L, maxConcurrent = 1) { firstStopped = true }
        tracker.register(1L, maxConcurrent = 1) { secondStopped = true }
        // Refuse the newcomer (the provider would reject the surplus
        // connection), not the stream the user is already watching.
        assertFalse("existing stream must keep playing", firstStopped)
        assertTrue("new stream must be refused", secondStopped)
    }

    @Test
    fun `unregistering frees a slot for a later stream`() {
        val tracker = StreamConcurrencyTracker()
        var firstStopped = false
        var secondStopped = false
        val unregisterFirst = tracker.register(1L, maxConcurrent = 1) { firstStopped = true }
        unregisterFirst()
        tracker.register(1L, maxConcurrent = 1) { secondStopped = true }
        assertFalse(firstStopped)
        assertFalse("slot freed — new stream accepted", secondStopped)
    }

    @Test
    fun `limits are tracked per source`() {
        val tracker = StreamConcurrencyTracker()
        var otherSourceStopped = false
        var sameSourceStopped = false
        tracker.register(sourceId = 1L, maxConcurrent = 1) { }
        // Different source has its own slot — accepted.
        tracker.register(sourceId = 2L, maxConcurrent = 1) { otherSourceStopped = true }
        assertFalse(otherSourceStopped)
        // Second stream on source 1 → at capacity, refused.
        tracker.register(sourceId = 1L, maxConcurrent = 1) { sameSourceStopped = true }
        assertTrue(sameSourceStopped)
    }
}

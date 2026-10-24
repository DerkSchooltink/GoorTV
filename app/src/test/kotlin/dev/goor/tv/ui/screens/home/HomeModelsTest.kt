package dev.goor.tv.ui.screens.home

import dev.goor.tv.util.testChannel
import dev.goor.tv.util.testProgramme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeModelsTest {

    // ── LIKE escaping ────────────────────────────────────────────────────────

    @Test
    fun `escapeLikePattern leaves ordinary text untouched`() {
        assertEquals("evening news", escapeLikePattern("evening news"))
    }

    @Test
    fun `escapeLikePattern escapes percent and underscore literals`() {
        assertEquals("100\\%", escapeLikePattern("100%"))
        assertEquals("A\\_B", escapeLikePattern("A_B"))
        assertEquals("50\\% \\_x\\_", escapeLikePattern("50% _x_"))
    }

    @Test
    fun `escapeLikePattern escapes the escape character itself first`() {
        assertEquals("C:\\\\path", escapeLikePattern("C:\\path"))
    }

    // ── Now/next row mapping ─────────────────────────────────────────────────

    @Test
    fun `mergeNowNext keeps now and next per source and tvg id`() {
        val now = testProgramme(sourceId = 1L, tvgChannelId = "a.tv", startMs = 1_000L, endMs = 2_000L, title = "Now")
        val next = testProgramme(sourceId = 1L, tvgChannelId = "a.tv", startMs = 2_000L, endMs = 3_000L, title = "Next")

        val merged = mergeNowNext(listOf(now), listOf(next))

        assertEquals(ChannelNowNext(now = now, next = next), merged[1L to "a.tv"])
    }

    @Test
    fun `a channel with no programmes resolves to the group-name fallback`() {
        val channel = testChannel(sourceId = 1L, tvgChannelId = "a.tv", name = "BBC One")
        val merged = mergeNowNext(current = emptyList(), upcoming = emptyList())

        val row = resolveNowNext(channel, merged)

        assertNull(row.now)
        assertNull(row.next)
        assertFalse(row.hasProgrammes)
    }

    @Test
    fun `a channel without a tvg id never resolves EPG data`() {
        val channel = testChannel(sourceId = 1L, tvgChannelId = null)
        val now = testProgramme(sourceId = 1L, tvgChannelId = "a.tv", title = "Now")
        val merged = mergeNowNext(listOf(now), emptyList())

        val row = resolveNowNext(channel, merged)

        assertFalse(row.hasProgrammes)
    }

    @Test
    fun `a next-only channel maps to a row with NEXT but no NOW`() {
        val channel = testChannel(sourceId = 1L, tvgChannelId = "a.tv")
        val next = testProgramme(
            sourceId = 1L,
            tvgChannelId = "a.tv",
            startMs = 5_000L,
            endMs = 6_000L,
            title = "Later",
        )

        val row = resolveNowNext(channel, mergeNowNext(emptyList(), listOf(next)))

        assertNull(row.now)
        assertEquals("Later", row.next?.title)
        assertTrue(row.hasProgrammes)
    }
}

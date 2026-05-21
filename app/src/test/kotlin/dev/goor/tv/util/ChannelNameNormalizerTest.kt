package dev.goor.tv.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChannelNameNormalizerTest {

    @Test
    fun `simple name canonical key is lowercased alphanumeric`() {
        assertEquals("bbcone", ChannelNameNormalizer.canonical("BBC One"))
    }

    @Test
    fun `strips bracketed qualifiers`() {
        assertEquals("espn", ChannelNameNormalizer.canonical("(NL) ESPN (D)"))
    }

    @Test
    fun `strips quality and live event tags`() {
        assertEquals("espn", ChannelNameNormalizer.canonical("ESPN [LIVE EVENTS] FHD"))
    }

    @Test
    fun `does not strip bare single-letter tokens`() {
        // Channel names like "BBC N" or "RTL S" must not collapse to "bbc"/"rtl".
        assertEquals("bbcn", ChannelNameNormalizer.canonical("BBC N"))
        assertEquals("rtls", ChannelNameNormalizer.canonical("RTL S"))
    }

    @Test
    fun `multi-digit suffix is preserved`() {
        // "ESPN 10" must NOT collapse to "espn1" (would collide with ESPN 1's id).
        val keys = ChannelNameNormalizer.keys("ESPN 10")
        assertEquals(listOf("espn10"), keys)
    }

    @Test
    fun `keys widen by dropping trailing digits`() {
        val keys = ChannelNameNormalizer.keys("ESPN 01")
        // Leading zero in '01' is stripped so 'espn1' is the most specific key.
        assertTrue("expected 'espn1' in $keys", keys.contains("espn1"))
        assertTrue("expected 'espn' variant, got $keys", keys.contains("espn"))
        assertEquals("espn1", keys.first())
    }

    @Test
    fun `strips leading zeros from digit groups`() {
        assertEquals("espn2", ChannelNameNormalizer.canonical("ESPN 02"))
        assertEquals("espn10", ChannelNameNormalizer.canonical("ESPN 10"))
    }

    @Test
    fun `keys widen by dropping leading country code`() {
        val keys = ChannelNameNormalizer.keys("NL - ESPN")
        assertTrue("expected 'espn' variant, got $keys", keys.contains("espn"))
    }

    @Test
    fun `NL prefixed channel with trailing number matches ESPN`() {
        val keys = ChannelNameNormalizer.keys("NL - ESPN 01[LIVE EVENTS]")
        // Should yield "nlespn01" first and "espn" as a fallback variant.
        assertTrue("expected 'espn' fallback in $keys", keys.contains("espn"))
    }

    @Test
    fun `pure noise yields no keys`() {
        assertEquals(emptyList<String>(), ChannelNameNormalizer.keys("   [LIVE EVENTS] (HD)   "))
    }

    @Test
    fun `dotted id passes through as alnum`() {
        // Used when indexing display-name 'espn.nl' style ids.
        assertEquals("espnnl", ChannelNameNormalizer.canonical("espn.nl"))
    }
}

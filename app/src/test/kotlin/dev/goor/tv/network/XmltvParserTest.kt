package dev.goor.tv.network

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.time.Instant

class XmltvParserTest {

    private val noon = Instant.parse("2026-01-17T12:00:00Z").toEpochMilli()
    private val onepm = Instant.parse("2026-01-17T13:00:00Z").toEpochMilli()

    private suspend fun parse(xml: String): List<XmltvParser.ParsedProgramme> {
        val out = mutableListOf<XmltvParser.ParsedProgramme>()
        XmltvParser.parse(ByteArrayInputStream(xml.toByteArray(Charsets.UTF_8))) { out.add(it) }
        return out
    }

    @Test
    fun `parseXmltvTime handles UTC offset`() {
        assertEquals(noon, XmltvParser.parseXmltvTime("20260117120000 +0000"))
    }

    @Test
    fun `parseXmltvTime handles positive timezone`() {
        // 2026-01-17 14:00 +0200 == 12:00 UTC
        assertEquals(noon, XmltvParser.parseXmltvTime("20260117140000 +0200"))
    }

    @Test
    fun `parseXmltvTime handles negative timezone`() {
        // 2026-01-17 07:00 -0500 == 12:00 UTC
        assertEquals(noon, XmltvParser.parseXmltvTime("20260117070000 -0500"))
    }

    @Test
    fun `parseXmltvTime assumes UTC when timezone missing`() {
        assertEquals(noon, XmltvParser.parseXmltvTime("20260117120000"))
    }

    @Test
    fun `parseXmltvTime handles half-hour offset`() {
        // 2026-01-17 17:30 +0530 == 12:00 UTC
        assertEquals(noon, XmltvParser.parseXmltvTime("20260117173000 +0530"))
    }

    @Test
    fun `parseXmltvTime accepts time without seconds`() {
        assertEquals(noon, XmltvParser.parseXmltvTime("202601171200 +0000"))
    }

    @Test
    fun `parseXmltvTime returns null on garbage`() {
        assertNull(XmltvParser.parseXmltvTime("nonsense"))
        assertNull(XmltvParser.parseXmltvTime(""))
        assertNull(XmltvParser.parseXmltvTime("20260117"))
    }

    @Test
    fun `parses complete programme with all optional fields`() = runTest {
        val xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <tv>
              <programme channel="bbc.one.uk" start="20260117120000 +0000" stop="20260117130000 +0000">
                <title>The News</title>
                <desc>Today's headlines.</desc>
                <category>News</category>
                <icon src="https://example.com/icon.png"/>
              </programme>
            </tv>
        """.trimIndent()

        val programmes = parse(xml)

        assertEquals(1, programmes.size)
        val p = programmes[0]
        assertEquals("bbc.one.uk", p.tvgChannelId)
        assertEquals(noon, p.startMs)
        assertEquals(onepm, p.endMs)
        assertEquals("The News", p.title)
        assertEquals("Today's headlines.", p.description)
        assertEquals("News", p.category)
        assertEquals("https://example.com/icon.png", p.iconUrl)
    }

    @Test
    fun `omits null fields when XML lacks them`() = runTest {
        val xml = """
            <tv>
              <programme channel="x" start="20260117120000 +0000" stop="20260117130000 +0000">
                <title>Bare</title>
              </programme>
            </tv>
        """.trimIndent()

        val p = parse(xml).single()
        assertEquals("Bare", p.title)
        assertNull(p.description)
        assertNull(p.category)
        assertNull(p.iconUrl)
    }

    @Test
    fun `handles programmes in different timezones`() = runTest {
        val xml = """
            <tv>
              <programme channel="a" start="20260117140000 +0200" stop="20260117150000 +0200">
                <title>Berlin</title>
              </programme>
              <programme channel="b" start="20260117070000 -0500" stop="20260117080000 -0500">
                <title>NewYork</title>
              </programme>
            </tv>
        """.trimIndent()

        val programmes = parse(xml)
        assertEquals(2, programmes.size)
        // Both start at 12:00 UTC.
        assertEquals(noon, programmes[0].startMs)
        assertEquals(noon, programmes[1].startMs)
    }

    @Test
    fun `handles programme spanning midnight`() = runTest {
        val xml = """
            <tv>
              <programme channel="a" start="20260117230000 +0000" stop="20260118003000 +0000">
                <title>LateShow</title>
              </programme>
            </tv>
        """.trimIndent()

        val p = parse(xml).single()
        assertEquals(90L * 60_000L, p.endMs - p.startMs)
    }

    @Test
    fun `skips malformed programme and continues`() = runTest {
        val xml = """
            <tv>
              <programme channel="a" start="not-a-date" stop="20260117130000 +0000">
                <title>Bad</title>
              </programme>
              <programme channel="b" start="20260117120000 +0000" stop="20260117130000 +0000">
                <title>Good</title>
              </programme>
            </tv>
        """.trimIndent()

        val programmes = parse(xml)
        assertEquals(1, programmes.size)
        assertEquals("Good", programmes[0].title)
    }

    @Test
    fun `rejects programme with end before start`() = runTest {
        val xml = """
            <tv>
              <programme channel="a" start="20260117130000 +0000" stop="20260117120000 +0000">
                <title>Inverted</title>
              </programme>
            </tv>
        """.trimIndent()

        assertTrue(parse(xml).isEmpty())
    }

    @Test
    fun `rejects programme with missing channel`() = runTest {
        val xml = """
            <tv>
              <programme start="20260117120000 +0000" stop="20260117130000 +0000">
                <title>Orphan</title>
              </programme>
            </tv>
        """.trimIndent()

        assertTrue(parse(xml).isEmpty())
    }

    @Test
    fun `ignores channel elements outside programme`() = runTest {
        val xml = """
            <tv>
              <channel id="a"><display-name>A</display-name></channel>
              <programme channel="a" start="20260117120000 +0000" stop="20260117130000 +0000">
                <title>Show</title>
              </programme>
            </tv>
        """.trimIndent()

        val p = parse(xml).single()
        assertNotNull(p)
        assertEquals("Show", p.title)
    }
}

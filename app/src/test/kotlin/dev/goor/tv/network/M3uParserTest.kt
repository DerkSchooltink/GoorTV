package dev.goor.tv.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class M3uParserTest {

    @Test
    fun `parses channel when EXTVLCOPT precedes URL`() {
        val content = """
            #EXTM3U
            #EXTINF:-1 tvg-logo="logo.png" group-title="|NL| Sport",|NL| ESPN 1 UHD
            #EXTVLCOPT:http-user-agent=Mozilla/5.0
            #EXTVLCOPT:http-referrer=https://example.com
            http://server/stream/espn1uhd
        """.trimIndent()

        val result = M3uParser.parse(sourceId = 1L, content = content)

        assertEquals(1, result.channels.size)
        assertEquals("|NL| ESPN 1 UHD", result.channels[0].name)
        assertEquals("|NL| Sport", result.channels[0].group)
        assertEquals("http://server/stream/espn1uhd", result.channels[0].url)
    }

    @Test
    fun `parses multiple channels with mixed directive lines and blanks`() {
        val content = """
            #EXTM3U
            #EXTINF:-1 group-title="A",Ch1
            http://s/1

            #EXTINF:-1 group-title="A",Ch2
            #EXTGRP:A
            #KODIPROP:inputstream=ffmpegdirect
            http://s/2
            #EXTINF:-1 group-title="B",Ch3
            #EXTVLCOPT:http-referrer=https://x
            http://s/3
        """.trimIndent()

        val result = M3uParser.parse(sourceId = 1L, content = content)

        assertEquals(3, result.channels.size)
        assertEquals(listOf("Ch1", "Ch2", "Ch3"), result.channels.map { it.name })
        assertEquals(listOf("http://s/1", "http://s/2", "http://s/3"), result.channels.map { it.url })
    }

    @Test
    fun `parses simple back-to-back entries (no directives)`() {
        val content = """
            #EXTM3U
            #EXTINF:-1 group-title="G",One
            http://s/one
            #EXTINF:-1 group-title="G",Two
            http://s/two
        """.trimIndent()

        val result = M3uParser.parse(sourceId = 1L, content = content)

        assertEquals(2, result.channels.size)
        assertEquals("One", result.channels[0].name)
        assertEquals("Two", result.channels[1].name)
    }

    @Test
    fun `extracts url-tvg from EXTM3U header`() {
        val content = """
            #EXTM3U url-tvg="https://x.example/epg.xml.gz"
            #EXTINF:-1,One
            http://s/one
        """.trimIndent()

        val result = M3uParser.parse(sourceId = 1L, content = content)

        assertEquals("https://x.example/epg.xml.gz", result.urlTvg)
    }

    @Test
    fun `extracts x-tvg-url alias`() {
        val content = """
            #EXTM3U x-tvg-url="https://x.example/guide.xml"
            #EXTINF:-1,One
            http://s/one
        """.trimIndent()

        val result = M3uParser.parse(sourceId = 1L, content = content)

        assertEquals("https://x.example/guide.xml", result.urlTvg)
    }

    @Test
    fun `takes first comma-separated url-tvg`() {
        val content = """
            #EXTM3U url-tvg="https://a/epg.xml,https://b/epg.xml"
        """.trimIndent()

        val result = M3uParser.parse(sourceId = 1L, content = content)

        assertEquals("https://a/epg.xml", result.urlTvg)
    }

    @Test
    fun `urlTvg is null when header omits it`() {
        val content = """
            #EXTM3U
            #EXTINF:-1,One
            http://s/one
        """.trimIndent()

        val result = M3uParser.parse(sourceId = 1L, content = content)

        assertNull(result.urlTvg)
    }

    @Test
    fun `extracts tvg-id per channel`() {
        val content = """
            #EXTM3U
            #EXTINF:-1 tvg-id="bbc.one.uk" group-title="UK",BBC One
            http://s/bbcone
            #EXTINF:-1 group-title="UK",ITV
            http://s/itv
        """.trimIndent()

        val result = M3uParser.parse(sourceId = 1L, content = content)

        assertEquals("bbc.one.uk", result.channels[0].tvgChannelId)
        assertNull(result.channels[1].tvgChannelId)
    }
}

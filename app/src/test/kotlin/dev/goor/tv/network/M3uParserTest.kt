package dev.goor.tv.network

import org.junit.Assert.assertEquals
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

        val channels = M3uParser.parse(sourceId = 1L, content = content)

        assertEquals(1, channels.size)
        assertEquals("|NL| ESPN 1 UHD", channels[0].name)
        assertEquals("|NL| Sport", channels[0].group)
        assertEquals("http://server/stream/espn1uhd", channels[0].url)
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

        val channels = M3uParser.parse(sourceId = 1L, content = content)

        assertEquals(3, channels.size)
        assertEquals(listOf("Ch1", "Ch2", "Ch3"), channels.map { it.name })
        assertEquals(listOf("http://s/1", "http://s/2", "http://s/3"), channels.map { it.url })
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

        val channels = M3uParser.parse(sourceId = 1L, content = content)

        assertEquals(2, channels.size)
        assertEquals("One", channels[0].name)
        assertEquals("Two", channels[1].name)
    }
}

package dev.goor.tv.cast

import org.junit.Assert.assertEquals
import org.junit.Test

class CastSessionTest {

    @Test
    fun `m3u8 maps to HLS`() {
        assertEquals("application/x-mpegURL", inferContentType("http://x/a.m3u8"))
    }

    @Test
    fun `mpd maps to DASH`() {
        assertEquals("application/dash+xml", inferContentType("http://x/a.mpd"))
    }

    @Test
    fun `ts maps to MPEG-TS`() {
        assertEquals("video/mp2t", inferContentType("http://x/a.ts"))
    }

    @Test
    fun `mp4 maps to MP4`() {
        assertEquals("video/mp4", inferContentType("http://x/a.mp4"))
    }

    @Test
    fun `mkv maps to Matroska`() {
        assertEquals("video/x-matroska", inferContentType("http://x/a.mkv"))
    }

    @Test
    fun `unknown extension defaults to HLS`() {
        assertEquals("application/x-mpegURL", inferContentType("http://x/stream"))
    }

    @Test
    fun `query string is stripped before suffix check`() {
        assertEquals("video/mp2t", inferContentType("http://x/live/u/p/123.ts?token=abc"))
        assertEquals("application/x-mpegURL", inferContentType("http://x/playlist.m3u8?session=1"))
    }

    @Test
    fun `case is normalised`() {
        assertEquals("application/x-mpegURL", inferContentType("http://X/A.M3U8"))
        assertEquals("video/mp4", inferContentType("http://x/A.MP4"))
    }
}

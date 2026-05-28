package dev.goor.tv.network

import dev.goor.tv.data.model.Secret
import dev.goor.tv.data.model.SourceType
import dev.goor.tv.data.model.XtreamOutput
import dev.goor.tv.util.testSource
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class XtreamApiTest {

    private val sampleJson = """
        [{"stream_id":42,"name":"BBC One","stream_icon":"http://logo/bbc.png",
          "category_name":"UK","epg_channel_id":"bbc.uk"}]
    """.trimIndent()

    private fun apiReturning(json: String): XtreamApi {
        val engine = MockEngine {
            respond(
                content = json,
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }
        return XtreamApi(defaultHttpClient(engine))
    }

    private fun xtreamSource(output: XtreamOutput) =
        testSource(id = 1L, type = SourceType.XTREAM, url = "http://host:8080").copy(
            username = Secret("user"),
            password = Secret("pass"),
            xtreamOutput = output,
        )

    @Test
    fun `live stream url uses the configured container`() = runTest {
        val channels = apiReturning(sampleJson).fetchLiveChannels(xtreamSource(XtreamOutput.M3U8))

        assertEquals("http://host:8080/live/user/pass/42.m3u8", channels.single().url)
    }

    @Test
    fun `live stream url defaults to ts container`() = runTest {
        val channels = apiReturning(sampleJson).fetchLiveChannels(xtreamSource(XtreamOutput.TS))

        assertEquals("http://host:8080/live/user/pass/42.ts", channels.single().url)
    }

    @Test
    fun `maps fields from the json payload`() = runTest {
        val channel = apiReturning(sampleJson).fetchLiveChannels(xtreamSource(XtreamOutput.TS)).single()

        assertEquals("BBC One", channel.name)
        assertEquals("UK", channel.group)
        assertEquals("http://logo/bbc.png", channel.logoUrl)
        assertEquals("bbc.uk", channel.tvgChannelId)
    }
}

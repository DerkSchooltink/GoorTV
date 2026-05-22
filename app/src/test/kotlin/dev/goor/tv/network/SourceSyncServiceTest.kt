package dev.goor.tv.network

import dev.goor.tv.data.db.dao.ChannelDao
import dev.goor.tv.data.db.dao.SourceDao
import dev.goor.tv.data.model.Channel
import dev.goor.tv.data.model.Source
import dev.goor.tv.data.model.SourceType
import dev.goor.tv.util.MainDispatcherRule
import dev.goor.tv.util.testSource
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockEngineConfig
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondError
import io.ktor.client.engine.mock.respondOk
import java.io.IOException
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.utils.io.ByteReadChannel
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

private const val SAMPLE_M3U = """#EXTM3U
#EXTINF:-1 tvg-id="bbc.uk" tvg-logo="http://logo/bbc.png" group-title="UK",BBC One
http://stream.example.com/bbc.ts
#EXTINF:-1 tvg-id="cnn.us",CNN
http://stream.example.com/cnn.ts
"""

@OptIn(ExperimentalCoroutinesApi::class)
class SourceSyncServiceTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val sourceDao = mockk<SourceDao>(relaxed = true)
    private val channelDao = mockk<ChannelDao>(relaxed = true)
    private val xtreamApi = mockk<XtreamApi>(relaxed = true)

    @Before
    fun stubSourceWrites() {
        // Most tests don't care about the side-effect writes; relax to keep
        // call sites short. Tests that assert specific writes still coVerify.
        coEvery { sourceDao.updateLastSyncedAt(any(), any()) } just Runs
        coEvery { sourceDao.updateEpgUrl(any(), any()) } just Runs
        coEvery { channelDao.replaceForSourcePreservingUserData(any(), any()) } just Runs
    }

    private fun service(engine: MockEngine = okEngine(SAMPLE_M3U)) = SourceSyncService(
        sourceDao = sourceDao,
        channelDao = channelDao,
        httpClient = defaultHttpClient(engine),
        xtreamApi = xtreamApi,
    )

    @Test
    fun `sync(M3U) fetches, parses, and writes channels`() = runTest {
        val source = testSource(id = 1L, type = SourceType.M3U, url = "http://example.com/playlist.m3u")
        service().sync(source)

        coVerify {
            channelDao.replaceForSourcePreservingUserData(1L, match { fetched ->
                fetched.size == 2 &&
                    fetched[0].name == "BBC One" &&
                    fetched[0].tvgChannelId == "bbc.uk" &&
                    fetched[1].name == "CNN"
            })
        }
        coVerify { sourceDao.updateLastSyncedAt(eq(1L), any()) }
    }

    @Test
    fun `syncAll skips sources synced within throttle`() = runTest {
        val now = System.currentTimeMillis()
        val fresh = testSource(id = 1L, type = SourceType.M3U, url = "http://example.com/a", lastSyncedAt = now)
        val stale = testSource(
            id = 2L, type = SourceType.M3U, url = "http://example.com/b",
            lastSyncedAt = now - 7L * 3600L * 1000L,
        )
        every { sourceDao.getAll() } returns flowOf(listOf(fresh, stale))

        val errors = service().syncAll()  // default throttle = 1h

        assertTrue("no errors expected: $errors", errors.isEmpty())
        // Only the stale source got synced.
        coVerify(exactly = 1) { channelDao.replaceForSourcePreservingUserData(any(), any()) }
        coVerify { channelDao.replaceForSourcePreservingUserData(eq(2L), any()) }
    }

    @Test
    fun `syncAll passes throttleMs = 0L to bypass throttle`() = runTest {
        val recent = testSource(
            id = 1L, type = SourceType.M3U, url = "http://example.com/a",
            lastSyncedAt = System.currentTimeMillis(),
        )
        every { sourceDao.getAll() } returns flowOf(listOf(recent))

        val errors = service().syncAll(throttleMs = 0L)

        assertTrue(errors.isEmpty())
        coVerify(exactly = 1) { channelDao.replaceForSourcePreservingUserData(eq(1L), any()) }
    }

    @Test
    fun `syncAll skips MANUAL sources`() = runTest {
        val manual = testSource(id = 1L, type = SourceType.MANUAL, url = "")
        every { sourceDao.getAll() } returns flowOf(listOf(manual))

        val errors = service().syncAll()

        assertTrue(errors.isEmpty())
        coVerify(exactly = 0) { channelDao.replaceForSourcePreservingUserData(any(), any()) }
    }

    @Test
    fun `syncAll retries on transient failure then succeeds`() = runTest {
        val source = testSource(id = 1L, type = SourceType.M3U, url = "http://example.com/p", lastSyncedAt = null)
        every { sourceDao.getAll() } returns flowOf(listOf(source))

        // Fail twice with a network-level exception (Ktor doesn't throw on
        // non-2xx by default — SourceSyncService relies on transport errors to
        // surface as exceptions), then succeed. `runTest`'s TestDispatcher
        // resolves the backoff `delay()` calls in virtual time so the test
        // doesn't actually wait minutes.
        val attempt = AtomicInteger(0)
        val engine = MockEngine { _ ->
            if (attempt.incrementAndGet() < 3) throw IOException("network unreachable")
            respondOk(SAMPLE_M3U)
        }

        val errors = service(engine).syncAll()

        assertTrue("expected success after retries, got $errors", errors.isEmpty())
        assertEquals(3, attempt.get())
        coVerify(exactly = 1) { channelDao.replaceForSourcePreservingUserData(eq(1L), any()) }
    }

    @Test
    fun `syncAll gives up after MAX_ATTEMPTS and reports the error`() = runTest {
        val source = testSource(id = 1L, type = SourceType.M3U, url = "http://example.com/p", lastSyncedAt = null)
        every { sourceDao.getAll() } returns flowOf(listOf(source))

        val attempt = AtomicInteger(0)
        val engine = MockEngine { _ ->
            attempt.incrementAndGet()
            throw IOException("network unreachable")
        }

        val errors = service(engine).syncAll()

        assertEquals(1, errors.size)
        assertEquals(3, attempt.get())  // MAX_ATTEMPTS = 3
        coVerify(exactly = 0) { channelDao.replaceForSourcePreservingUserData(any(), any()) }
    }

    @Test
    fun `sync(M3U) treats HTTP 5xx as a failure and does NOT wipe channels`() = runTest {
        // Regression: previously a 5xx returned an empty/error body, the M3U
        // parser silently returned an empty channel list, and
        // replaceForSourcePreservingUserData wiped the source's channels.
        val source = testSource(id = 1L, type = SourceType.M3U, url = "http://example.com/p", lastSyncedAt = null)
        every { sourceDao.getAll() } returns flowOf(listOf(source))
        val engine = MockEngine { respondError(HttpStatusCode.InternalServerError) }

        val errors = service(engine).syncAll()

        assertEquals(1, errors.size)
        coVerify(exactly = 0) { channelDao.replaceForSourcePreservingUserData(any(), any()) }
    }

    @Test
    fun `sync(XTREAM) delegates to XtreamApi and persists`() = runTest {
        val source = testSource(id = 1L, type = SourceType.XTREAM, url = "http://x/")
        coEvery { xtreamApi.fetchLiveChannels(source) } returns listOf(
            Channel(sourceId = 1L, name = "Sky", url = "http://x/sky.ts"),
        )

        service().sync(source)

        coVerify { xtreamApi.fetchLiveChannels(source) }
        coVerify {
            channelDao.replaceForSourcePreservingUserData(
                eq(1L),
                match { it.size == 1 && it[0].name == "Sky" },
            )
        }
    }

    @Test
    fun `M3U url-tvg auto-discovery persists when source had no epgUrl`() = runTest {
        val source = testSource(id = 1L, type = SourceType.M3U, url = "http://example.com/p", epgUrl = null)
        val m3uWithTvg = """#EXTM3U url-tvg="http://example.com/epg.xml.gz"
#EXTINF:-1 tvg-id="bbc.uk",BBC One
http://stream.example.com/bbc.ts
"""

        service(okEngine(m3uWithTvg)).sync(source)

        coVerify { sourceDao.updateEpgUrl(eq(1L), eq("http://example.com/epg.xml.gz")) }
    }

    @Test
    fun `M3U url-tvg auto-discovery does NOT overwrite an existing epgUrl`() = runTest {
        val source = testSource(
            id = 1L,
            type = SourceType.M3U,
            url = "http://example.com/p",
            epgUrl = "http://existing/epg.xml",
        )
        val m3uWithTvg = """#EXTM3U url-tvg="http://example.com/epg.xml.gz"
#EXTINF:-1,Foo
http://stream.example.com/foo.ts
"""

        service(okEngine(m3uWithTvg)).sync(source)

        coVerify(exactly = 0) { sourceDao.updateEpgUrl(any(), any()) }
    }

    private fun okEngine(body: String): MockEngine = MockEngine {
        respond(
            content = ByteReadChannel(body),
            status = HttpStatusCode.OK,
            headers = headersOf("Content-Type", "audio/x-mpegurl"),
        )
    }
}

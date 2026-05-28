package dev.goor.tv.network

import dev.goor.tv.data.db.dao.ChannelDao
import dev.goor.tv.data.db.dao.ChannelIdName
import dev.goor.tv.data.db.dao.ProgrammeDao
import dev.goor.tv.data.db.dao.SourceDao
import dev.goor.tv.data.model.SourceType
import dev.goor.tv.util.testSource
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respondError
import io.ktor.http.HttpStatusCode
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.just
import io.mockk.mockk
import io.mockk.slot
import io.mockk.Runs
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.ByteArrayInputStream

class EpgSyncServiceTest {

    private val sourceDao = mockk<SourceDao>(relaxed = true)
    private val programmeDao = mockk<ProgrammeDao>(relaxed = true)
    private val channelDao = mockk<ChannelDao>(relaxed = true)

    // EPG tests exercise processXmltv directly via the test seam OR call sync()
    // which expects the HTTP layer; use a real client with MockEngine so we
    // avoid mockk-proxying io.ktor.HttpClient (which can blow up mockk's
    // bytecode cache on large test suites).
    private fun service() = EpgSyncService(
        sourceDao = sourceDao,
        programmeDao = programmeDao,
        channelDao = channelDao,
        httpClient = defaultHttpClient(MockEngine { respondError(HttpStatusCode.InternalServerError) }),
    )

    @Test
    fun `MANUAL sources are skipped in syncAll`() = runTest {
        val manual = testSource(id = 1L, type = SourceType.MANUAL, url = "")
        coEvery { sourceDao.getAll() } returns flowOf(listOf(manual))

        val errors = service().syncAll()

        assert(errors.isEmpty())
        coVerify(exactly = 0) { sourceDao.markEpgSynced(any(), any()) }
    }

    @Test
    fun `M3U source without epgUrl is skipped`() = runTest {
        val src = testSource(id = 2L, type = SourceType.M3U, epgUrl = null)
        coEvery { sourceDao.getAll() } returns flowOf(listOf(src))

        val errors = service().syncAll()

        assert(errors.isEmpty())
        coVerify(exactly = 0) { sourceDao.markEpgSynced(any(), any()) }
    }

    @Test
    fun `recently synced source is skipped under throttle`() = runTest {
        val now = System.currentTimeMillis()
        val src = testSource(
            id = 3L,
            type = SourceType.M3U,
            epgUrl = "http://example.com/epg.xml",
            lastEpgSyncedAt = now - 1_000L, // 1 second ago
        )
        coEvery { sourceDao.getAll() } returns flowOf(listOf(src))

        val errors = service().syncAll(throttleMs = 6L * 3600L * 1000L)

        assert(errors.isEmpty())
        coVerify(exactly = 0) { sourceDao.markEpgSynced(any(), any()) }
    }

    @Test
    fun `manual sync skips ineligible source without error`() = runTest {
        val src = testSource(id = 4L, type = SourceType.M3U, epgUrl = null)

        // Should return without throwing or touching DAOs beyond no-ops.
        service().sync(src)

        coVerify(exactly = 0) { sourceDao.markEpgSynced(any(), any()) }
        coVerify(exactly = 0) { sourceDao.setEpgError(any(), any()) }
    }

    @Test
    fun `XTREAM source without credentials is skipped`() = runTest {
        val src = testSource(
            id = 6L,
            type = SourceType.XTREAM,
            url = "http://example.com",
        ) // username/password default null in fixture
        coEvery { sourceDao.getAll() } returns flowOf(listOf(src))

        val errors = service().syncAll()

        assert(errors.isEmpty())
        coVerify(exactly = 0) { sourceDao.markEpgSynced(any(), any()) }
        coVerify(exactly = 0) { sourceDao.setEpgError(any(), any()) }
    }

    @Test
    fun `processXmltv backfills tvg-id via display-name`() = runTest {
        // Channels carry blank tvgChannelId; EPG knows them by display-name.
        coEvery { channelDao.getMissingTvgIdsBySource(1L) } returns listOf(
            ChannelIdName(id = 10L, name = "NL - ESPN 1"),
            ChannelIdName(id = 11L, name = "NL - ESPN 02[LIVE EVENTS]"),
            ChannelIdName(id = 12L, name = "BBC One HD"),
            ChannelIdName(id = 13L, name = "Channel With No Match"),
        )
        val captured = slot<List<Pair<Long, String>>>()
        coEvery { channelDao.applyTvgChannelIdAssignments(capture(captured)) } just Runs

        val xml = """
            <tv>
              <channel id="espn.nl"><display-name>ESPN</display-name></channel>
              <channel id="espn2.nl"><display-name>ESPN 2</display-name></channel>
              <channel id="bbcone.uk"><display-name>BBC One</display-name></channel>
            </tv>
        """.trimIndent()

        service().processXmltv(1L, ByteArrayInputStream(xml.toByteArray(Charsets.UTF_8)))

        coVerify { channelDao.applyTvgChannelIdAssignments(any()) }
        val byId = captured.captured.toMap()
        assertEquals("espn.nl", byId[10L])     // ESPN 1 → trailing-digit fallback to "espn"
        assertEquals("espn2.nl", byId[11L])    // ESPN 02 → leading-zero strip → "espn2"
        assertEquals("bbcone.uk", byId[12L])   // BBC One HD → quality tag stripped
        assertEquals(null, byId[13L])          // No EPG entry for this one
        assertEquals(3, captured.captured.size)
    }

    @Test
    fun `processXmltv prunes programmes older than the retention window`() = runTest {
        val xml = "<tv><channel id=\"x.tv\"><display-name>X</display-name></channel></tv>"

        service().processXmltv(1L, ByteArrayInputStream(xml.toByteArray(Charsets.UTF_8)))

        // After re-inserting, the past tail is trimmed for this source.
        coVerify { programmeDao.deleteOlderThan(eq(1L), any()) }
    }

    @Test
    fun `processXmltv applies zero assignments when no matches`() = runTest {
        coEvery { channelDao.getMissingTvgIdsBySource(1L) } returns listOf(
            ChannelIdName(id = 20L, name = "Some Obscure Channel"),
        )
        val xml = "<tv><channel id=\"x.tv\"><display-name>Unrelated</display-name></channel></tv>"

        service().processXmltv(1L, ByteArrayInputStream(xml.toByteArray(Charsets.UTF_8)))

        coVerify(exactly = 0) { channelDao.applyTvgChannelIdAssignments(any()) }
    }

    @Test
    fun `network error is persisted to epgLastError`() = runTest {
        val src = testSource(
            id = 5L,
            type = SourceType.M3U,
            epgUrl = "http://127.0.0.1:1/does-not-exist.xml",
        )
        coEvery { sourceDao.setEpgError(any(), any()) } just Runs

        runCatching { service().sync(src) }

        coVerify { sourceDao.setEpgError(eq(5L), any()) }
    }
}

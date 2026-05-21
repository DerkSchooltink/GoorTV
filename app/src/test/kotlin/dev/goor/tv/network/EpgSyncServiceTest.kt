package dev.goor.tv.network

import dev.goor.tv.data.db.dao.ChannelDao
import dev.goor.tv.data.db.dao.ProgrammeDao
import dev.goor.tv.data.db.dao.SourceDao
import dev.goor.tv.data.model.SourceType
import dev.goor.tv.util.testSource
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.just
import io.mockk.mockk
import io.mockk.Runs
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Test

class EpgSyncServiceTest {

    private val sourceDao = mockk<SourceDao>(relaxed = true)
    private val programmeDao = mockk<ProgrammeDao>(relaxed = true)
    private val channelDao = mockk<ChannelDao>(relaxed = true)

    private fun service() = EpgSyncService(sourceDao, programmeDao, channelDao)

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

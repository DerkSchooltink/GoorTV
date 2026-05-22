package dev.goor.tv.data

import dev.goor.tv.data.db.dao.ChannelDao
import dev.goor.tv.data.db.dao.SourceDao
import dev.goor.tv.data.model.Channel
import dev.goor.tv.data.model.Source
import dev.goor.tv.data.model.SourceType
import dev.goor.tv.util.MainDispatcherRule
import dev.goor.tv.util.testChannel
import dev.goor.tv.util.testSource
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ManualSourceManagerTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val sourceDao = mockk<SourceDao>(relaxed = true)
    private val channelDao = mockk<ChannelDao>(relaxed = true)

    @Test
    fun `addChannel creates manual source on first call`() = runTest {
        coEvery { sourceDao.getManualSource() } returns null
        coEvery { sourceDao.insert(any()) } returns 1L

        ManualSourceManager(sourceDao, channelDao, backgroundScope)
            .addChannel("My Channel", "http://x/s", null, "Sports")
        advanceUntilIdle()

        coVerify { sourceDao.insert(match { it.type == SourceType.MANUAL && it.includedGroups == null }) }
        coVerify {
            channelDao.insert(match {
                it.sourceId == 1L && it.name == "My Channel" && it.group == "Sports"
            })
        }
    }

    @Test
    fun `addChannel reuses existing manual source`() = runTest {
        val existing = testSource(id = 42L, type = SourceType.MANUAL)
        coEvery { sourceDao.getManualSource() } returns existing

        val manager = ManualSourceManager(sourceDao, channelDao, backgroundScope)
        advanceUntilIdle()  // let init's getManualSource lookup complete

        manager.addChannel("Ch", "http://x/s", null, null)
        advanceUntilIdle()

        coVerify(exactly = 0) { sourceDao.insert(any()) }
        coVerify { channelDao.insert(match { it.sourceId == 42L }) }
    }

    @Test
    fun `concurrent addChannel calls only insert one MANUAL source`() = runTest {
        coEvery { sourceDao.getManualSource() } returns null
        coEvery { sourceDao.insert(any<Source>()) } coAnswers { 1L }

        val manager = ManualSourceManager(sourceDao, channelDao, backgroundScope)
        // Fire two adds before either has a chance to populate the cached id.
        val a = async { manager.addChannel("A", "http://x/a", null, null) }
        val b = async { manager.addChannel("B", "http://x/b", null, null) }
        a.await(); b.await()

        coVerify(exactly = 1) { sourceDao.insert(any<Source>()) }
        coVerify(exactly = 2) { channelDao.insert(any<Channel>()) }
    }

    @Test
    fun `updateChannel delegates to ChannelDao`() = runTest {
        val channel = testChannel(id = 5L)
        ManualSourceManager(sourceDao, channelDao, backgroundScope).updateChannel(channel)
        coVerify { channelDao.update(channel) }
    }

    @Test
    fun `deleteChannel delegates to ChannelDao`() = runTest {
        val channel = testChannel(id = 7L)
        ManualSourceManager(sourceDao, channelDao, backgroundScope).deleteChannel(channel)
        coVerify { channelDao.delete(channel) }
    }

    @Test
    fun `manualSourceId reflects existing source after init`() = runTest {
        val existing = testSource(id = 99L, type = SourceType.MANUAL)
        coEvery { sourceDao.getManualSource() } returns existing

        val manager = ManualSourceManager(sourceDao, channelDao, backgroundScope)
        // Await the init load — `advanceUntilIdle` is unreliable across the
        // backgroundScope/test-scope boundary on Standard test dispatchers.
        val id = manager.manualSourceId.filterNotNull().first()

        assertEquals(99L, id)
    }
}

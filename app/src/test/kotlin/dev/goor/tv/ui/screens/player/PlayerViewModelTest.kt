package dev.goor.tv.ui.screens.player

import dev.goor.tv.data.StreamConcurrencyTracker
import dev.goor.tv.data.db.dao.ChannelDao
import dev.goor.tv.data.db.dao.SourceDao
import dev.goor.tv.dlna.DlnaService
import dev.goor.tv.util.MainDispatcherRule
import dev.goor.tv.util.testChannel
import dev.goor.tv.util.testSource
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.slot
import io.mockk.Runs
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class PlayerViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val channelDao = mockk<ChannelDao>()
    private val sourceDao = mockk<SourceDao>()
    private val dlnaService = mockk<DlnaService>(relaxed = true)

    @Before
    fun setup() {
        every { dlnaService.devices } returns MutableStateFlow(emptyList())
    }

    private fun makeVm(channelId: Long) =
        PlayerViewModel(channelId = channelId, channelDao = channelDao, sourceDao = sourceDao, dlnaService = dlnaService, concurrencyTracker = StreamConcurrencyTracker())

    @Test
    fun `channel is loaded from DAO by id`() = runTest {
        val channel = testChannel(id = 42L, name = "BBC One", sourceId = 1L)
        val source = testSource(id = 1L)
        coEvery { channelDao.getById(42L) } returns channel
        coEvery { channelDao.updateLastWatched(any(), any()) } just Runs
        coEvery { sourceDao.getById(1L) } returns source

        val vm = makeVm(42L)
        advanceUntilIdle()

        assertEquals(channel, vm.channel.value)
    }

    @Test
    fun `channel is null when id not found`() = runTest {
        coEvery { channelDao.getById(99L) } returns null

        val vm = makeVm(99L)
        advanceUntilIdle()

        assertNull(vm.channel.value)
    }

    @Test
    fun `lastWatchedAt is updated with current timestamp when channel exists`() = runTest {
        val channel = testChannel(id = 5L, sourceId = 1L)
        val source = testSource(id = 1L)
        val beforeMs = System.currentTimeMillis()
        val tsSlot = slot<Long>()
        coEvery { channelDao.getById(5L) } returns channel
        coEvery { channelDao.updateLastWatched(any(), capture(tsSlot)) } just Runs
        coEvery { sourceDao.getById(1L) } returns source

        makeVm(5L)
        advanceUntilIdle()

        coVerify { channelDao.updateLastWatched(id = 5L, timestamp = any()) }
        val afterMs = System.currentTimeMillis()
        assertTrue("timestamp should be >= beforeMs", tsSlot.captured >= beforeMs)
        assertTrue("timestamp should be <= afterMs", tsSlot.captured <= afterMs)
    }

    @Test
    fun `lastWatchedAt is not updated when channel is not found`() = runTest {
        coEvery { channelDao.getById(99L) } returns null

        makeVm(99L)
        advanceUntilIdle()

        coVerify(exactly = 0) { channelDao.updateLastWatched(any(), any()) }
    }

    @Test
    fun `headers are populated from source when channel exists`() = runTest {
        val channel = testChannel(id = 1L, sourceId = 7L)
        val source = testSource(id = 7L).copy(headers = "User-Agent: TestApp\nX-Token: abc123")
        coEvery { channelDao.getById(1L) } returns channel
        coEvery { channelDao.updateLastWatched(any(), any()) } just Runs
        coEvery { sourceDao.getById(7L) } returns source

        val vm = makeVm(1L)
        advanceUntilIdle()

        assertEquals(mapOf("User-Agent" to "TestApp", "X-Token" to "abc123"), vm.headers.value)
    }

    @Test
    fun `headers are empty when source has no headers`() = runTest {
        val channel = testChannel(id = 1L, sourceId = 7L)
        val source = testSource(id = 7L)
        coEvery { channelDao.getById(1L) } returns channel
        coEvery { channelDao.updateLastWatched(any(), any()) } just Runs
        coEvery { sourceDao.getById(7L) } returns source

        val vm = makeVm(1L)
        advanceUntilIdle()

        assertTrue(vm.headers.value.isEmpty())
    }
}

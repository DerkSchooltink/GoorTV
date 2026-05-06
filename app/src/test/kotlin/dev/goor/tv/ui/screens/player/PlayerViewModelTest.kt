package dev.goor.tv.ui.screens.player

import dev.goor.tv.data.db.dao.ChannelDao
import dev.goor.tv.util.MainDispatcherRule
import dev.goor.tv.util.testChannel
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.just
import io.mockk.mockk
import io.mockk.Runs
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

class PlayerViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val channelDao = mockk<ChannelDao>()

    @Test
    fun `channel is loaded from DAO by id`() = runTest {
        val channel = testChannel(id = 42L, name = "BBC One")
        coEvery { channelDao.getById(42L) } returns channel
        coEvery { channelDao.updateLastWatched(any(), any()) } just Runs

        val vm = PlayerViewModel(channelId = 42L, channelDao = channelDao)
        advanceUntilIdle()

        assertEquals(channel, vm.channel.value)
    }

    @Test
    fun `channel is null when id not found`() = runTest {
        coEvery { channelDao.getById(99L) } returns null

        val vm = PlayerViewModel(channelId = 99L, channelDao = channelDao)
        advanceUntilIdle()

        assertNull(vm.channel.value)
    }

    @Test
    fun `lastWatchedAt is updated with current timestamp when channel exists`() = runTest {
        val channel = testChannel(id = 5L)
        val beforeMs = System.currentTimeMillis()
        coEvery { channelDao.getById(5L) } returns channel
        coEvery { channelDao.updateLastWatched(any(), any()) } just Runs

        PlayerViewModel(channelId = 5L, channelDao = channelDao)
        advanceUntilIdle()

        coVerify {
            channelDao.updateLastWatched(
                id = eq(5L),
                timestamp = and(gte(beforeMs), lte(System.currentTimeMillis())),
            )
        }
    }

    @Test
    fun `lastWatchedAt is not updated when channel is not found`() = runTest {
        coEvery { channelDao.getById(99L) } returns null

        PlayerViewModel(channelId = 99L, channelDao = channelDao)
        advanceUntilIdle()

        coVerify(exactly = 0) { channelDao.updateLastWatched(any(), any()) }
    }
}

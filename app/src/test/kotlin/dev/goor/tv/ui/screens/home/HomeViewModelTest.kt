package dev.goor.tv.ui.screens.home

import androidx.paging.PagingSource
import dev.goor.tv.data.SearchHistoryRepository
import dev.goor.tv.data.db.dao.ChannelDao
import dev.goor.tv.data.db.dao.SourceDao
import dev.goor.tv.data.model.Channel
import dev.goor.tv.data.preferences.SortOrder
import dev.goor.tv.data.preferences.UserPreferencesRepository
import dev.goor.tv.network.SourceSyncService
import dev.goor.tv.util.MainDispatcherRule
import dev.goor.tv.util.testChannel
import dev.goor.tv.util.testSource
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.Runs
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class HomeViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val channelDao = mockk<ChannelDao>()
    private val sourceDao = mockk<SourceDao>()
    private val syncService = mockk<SourceSyncService>()
    private val searchHistoryRepo = mockk<SearchHistoryRepository>()
    private val prefsRepository = mockk<UserPreferencesRepository>()

    @Before
    fun setup() {
        coEvery { syncService.syncAll() } returns emptyList()
        every { sourceDao.getAll() } returns flowOf(listOf(testSource()))
        every { channelDao.getRecentlyWatched() } returns flowOf(emptyList())
        every { channelDao.getChannelsPaged(any(), any(), any()) } returns mockk(relaxed = true)
        every { channelDao.getChannelsPagedByName(any(), any(), any()) } returns mockk(relaxed = true)
        every { channelDao.getChannelsPagedByLastWatched(any(), any(), any()) } returns mockk(relaxed = true)
        coEvery { channelDao.count() } returns 0
        every { searchHistoryRepo.history } returns MutableStateFlow(emptyList())
        every { prefsRepository.sortOrder } returns flowOf(SortOrder.BY_GROUP)
    }

    private fun makeVm() = HomeViewModel(channelDao, sourceDao, syncService, searchHistoryRepo, prefsRepository)

    @Test
    fun `setSearchQuery updates searchQuery state`() = runTest {
        val vm = makeVm()
        vm.setSearchQuery("bbc")
        assertEquals("bbc", vm.searchQuery.value)
    }

    @Test
    fun `clearing search resets searchQuery to empty`() = runTest {
        val vm = makeVm()
        vm.setSearchQuery("BBC")
        vm.setSearchQuery("")
        assertEquals("", vm.searchQuery.value)
    }

    @Test
    fun `toggleFavoritesOnly flips showFavoritesOnly`() = runTest {
        val vm = makeVm()
        assertFalse(vm.showFavoritesOnly.value)
        vm.toggleFavoritesOnly()
        assertTrue(vm.showFavoritesOnly.value)
        vm.toggleFavoritesOnly()
        assertFalse(vm.showFavoritesOnly.value)
    }

    @Test
    fun `toggleFavorite delegates to ChannelDao`() = runTest {
        coEvery { channelDao.toggleFavorite(any()) } just Runs

        makeVm().toggleFavorite(42L)
        advanceUntilIdle()

        coVerify { channelDao.toggleFavorite(42L) }
    }

    @Test
    fun `syncAll is called on init when no channels exist`() = runTest {
        coEvery { channelDao.count() } returns 0

        makeVm()
        advanceUntilIdle()

        coVerify { syncService.syncAll() }
    }

    @Test
    fun `syncAll is NOT called on init when channels already exist`() = runTest {
        coEvery { channelDao.count() } returns 5

        makeVm()
        advanceUntilIdle()

        coVerify(exactly = 0) { syncService.syncAll() }
    }

    @Test
    fun `recentlyWatched reflects data from channelDao`() = runTest {
        val now = System.currentTimeMillis()
        val channels = listOf(
            testChannel(id = 1, name = "Recent", lastWatchedAt = now),
            testChannel(id = 2, name = "Old", lastWatchedAt = now - 10_000),
        )
        every { channelDao.getRecentlyWatched() } returns flowOf(channels)

        val vm = makeVm()
        backgroundScope.launch { vm.recentlyWatched.collect {} }
        advanceUntilIdle()

        assertEquals(2, vm.recentlyWatched.value.size)
        assertEquals("Recent", vm.recentlyWatched.value[0].name)
    }

    @Test
    fun `recentlyWatched is empty when no channels watched`() = runTest {
        every { channelDao.getRecentlyWatched() } returns flowOf(emptyList())

        val vm = makeVm()
        backgroundScope.launch { vm.recentlyWatched.collect {} }
        advanceUntilIdle()

        assertTrue(vm.recentlyWatched.value.isEmpty())
    }
}

package dev.goor.tv.ui.screens.home

import androidx.paging.PagingSource
import dev.goor.tv.data.SearchHistoryRepository
import dev.goor.tv.data.db.dao.ChannelDao
import dev.goor.tv.data.db.dao.SourceDao
import dev.goor.tv.data.model.Channel
import dev.goor.tv.data.model.SourceType
import dev.goor.tv.data.preferences.SortOrder
import dev.goor.tv.data.preferences.UserPreferencesRepository
import dev.goor.tv.network.EpgSyncService
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
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val channelDao = mockk<ChannelDao>()
    private val sourceDao = mockk<SourceDao>()
    private val syncService = mockk<SourceSyncService>()
    private val searchHistoryRepo = mockk<SearchHistoryRepository>()
    private val prefsRepository = mockk<UserPreferencesRepository>()
    private val epgSyncService = mockk<EpgSyncService>()

    @Before
    fun setup() {
        coEvery { syncService.syncAll() } returns emptyList()
        coEvery { epgSyncService.syncAll(any()) } returns emptyList()
        every { sourceDao.getAll() } returns flowOf(listOf(testSource()))
        coEvery { sourceDao.getManualSource() } returns null
        coEvery { sourceDao.insert(any()) } returns 99L
        every { channelDao.getRecentlyWatched() } returns flowOf(emptyList())
        every { channelDao.getGroups() } returns flowOf(emptyList())
        every { channelDao.getChannelsPaged(any(), any(), any()) } returns mockk(relaxed = true)
        every { channelDao.getChannelsPagedByName(any(), any(), any()) } returns mockk(relaxed = true)
        every { channelDao.getChannelsPagedByLastWatched(any(), any(), any()) } returns mockk(relaxed = true)
        coEvery { channelDao.count() } returns 0
        coEvery { channelDao.insert(any()) } returns 1L
        coEvery { channelDao.update(any()) } just Runs
        coEvery { channelDao.delete(any()) } just Runs
        every { searchHistoryRepo.history } returns MutableStateFlow(emptyList())
        every { prefsRepository.sortOrder } returns flowOf(SortOrder.BY_GROUP)
    }

    private fun makeVm() = HomeViewModel(channelDao, sourceDao, syncService, searchHistoryRepo, prefsRepository, epgSyncService)

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

    @Test
    fun `addCustomChannel creates manual source and inserts channel`() = runTest {
        makeVm().addCustomChannel("My Channel", "http://example.com/stream", null, "Sports")
        advanceUntilIdle()

        coVerify { sourceDao.insert(match { it.type == SourceType.MANUAL && it.includedGroups == null }) }
        coVerify { channelDao.insert(match { it.name == "My Channel" && it.url == "http://example.com/stream" && it.group == "Sports" }) }
    }

    @Test
    fun `addCustomChannel reuses existing manual source`() = runTest {
        val existing = testSource(id = 42L, type = SourceType.MANUAL)
        coEvery { sourceDao.getManualSource() } returns existing

        makeVm().addCustomChannel("Ch", "http://x.com/s", null, null)
        advanceUntilIdle()

        coVerify(exactly = 0) { sourceDao.insert(any()) }
        coVerify { channelDao.insert(match { it.sourceId == 42L }) }
    }

    @Test
    fun `updateCustomChannel delegates to ChannelDao`() = runTest {
        val channel = testChannel(id = 5L, name = "Updated")
        makeVm().updateCustomChannel(channel)
        advanceUntilIdle()

        coVerify { channelDao.update(channel) }
    }

    @Test
    fun `deleteCustomChannel delegates to ChannelDao`() = runTest {
        val channel = testChannel(id = 7L)
        makeVm().deleteCustomChannel(channel)
        advanceUntilIdle()

        coVerify { channelDao.delete(channel) }
    }

    @Test
    fun `manualSourceId initialized from existing manual source on startup`() = runTest {
        val existing = testSource(id = 55L, type = SourceType.MANUAL)
        coEvery { sourceDao.getManualSource() } returns existing

        val vm = makeVm()
        advanceUntilIdle()

        assertEquals(55L, vm.manualSourceId.value)
    }
}

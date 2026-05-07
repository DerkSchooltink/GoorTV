package dev.goor.tv.ui.screens.home

import dev.goor.tv.data.SearchHistoryRepository
import dev.goor.tv.data.db.dao.ChannelDao
import dev.goor.tv.data.db.dao.SourceDao
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
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
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

    @Before
    fun setup() {
        every { sourceDao.getAll() } returns flowOf(listOf(testSource()))
        every { channelDao.getRecentlyWatched() } returns flowOf(emptyList())
        every { prefsRepository.sortOrder } returns flowOf(SortOrder.BY_GROUP)
        every { searchHistoryRepo.history } returns flowOf(emptyList())
        coEvery { channelDao.count() } returns 1
    }

    private fun makeVm() = HomeViewModel(channelDao, sourceDao, syncService, searchHistoryRepo, prefsRepository)

    @Test
    fun `sortOrder defaults to BY_GROUP`() = runTest {
        val vm = makeVm()
        advanceUntilIdle()

        assertEquals(SortOrder.BY_GROUP, vm.sortOrder.value)
    }

    @Test
    fun `setSortOrder persists the chosen order via repository`() = runTest {
        coEvery { prefsRepository.setSortOrder(any()) } just Runs

        makeVm().setSortOrder(SortOrder.BY_NAME)
        advanceUntilIdle()

        coVerify { prefsRepository.setSortOrder(SortOrder.BY_NAME) }
    }

    @Test
    fun `setSortOrder persists BY_LAST_WATCHED`() = runTest {
        coEvery { prefsRepository.setSortOrder(any()) } just Runs

        makeVm().setSortOrder(SortOrder.BY_LAST_WATCHED)
        advanceUntilIdle()

        coVerify { prefsRepository.setSortOrder(SortOrder.BY_LAST_WATCHED) }
    }

    @Test
    fun `sync does not run on init when channels already exist`() = runTest {
        makeVm()
        advanceUntilIdle()

        coVerify(exactly = 0) { syncService.syncAll() }
    }

    @Test
    fun `sync runs on init when channel table is empty`() = runTest {
        coEvery { channelDao.count() } returns 0
        coEvery { syncService.syncAll() } returns emptyList()

        makeVm()
        advanceUntilIdle()

        coVerify { syncService.syncAll() }
    }

    @Test
    fun `toggleFavorite delegates to ChannelDao`() = runTest {
        coEvery { channelDao.toggleFavorite(any()) } just Runs

        makeVm().toggleFavorite(42L)
        advanceUntilIdle()

        coVerify { channelDao.toggleFavorite(42L) }
    }

    @Test
    fun `clearRecentlyWatched delegates to ChannelDao`() = runTest {
        coEvery { channelDao.clearRecentlyWatched() } just Runs

        makeVm().clearRecentlyWatched()
        advanceUntilIdle()

        coVerify { channelDao.clearRecentlyWatched() }
    }

    @Test
    fun `recentlyWatched emits from ChannelDao`() = runTest {
        val now = System.currentTimeMillis()
        val channel = testChannel(id = 1, name = "Recent", lastWatchedAt = now)
        every { channelDao.getRecentlyWatched() } returns flowOf(listOf(channel))

        val vm = makeVm()
        advanceUntilIdle()

        assertEquals(listOf(channel), vm.recentlyWatched.value)
    }
}

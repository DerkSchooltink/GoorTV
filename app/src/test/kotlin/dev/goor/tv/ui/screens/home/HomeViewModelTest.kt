package dev.goor.tv.ui.screens.home

import androidx.paging.PagingSource
import dev.goor.tv.R
import dev.goor.tv.data.ManualSourceManager
import dev.goor.tv.data.SearchHistoryRepository
import dev.goor.tv.data.db.dao.ChannelDao
import dev.goor.tv.data.db.dao.ProgrammeDao
import dev.goor.tv.data.db.dao.SourceDao
import dev.goor.tv.data.model.Channel
import dev.goor.tv.data.model.SourceType
import dev.goor.tv.data.preferences.SortOrder
import dev.goor.tv.data.preferences.UserPreferencesRepository
import dev.goor.tv.network.EpgSyncService
import dev.goor.tv.network.SourceSyncService
import dev.goor.tv.util.FakeTimeProvider
import dev.goor.tv.util.MainDispatcherRule
import dev.goor.tv.util.testChannel
import dev.goor.tv.util.testProgramme
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
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
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
    private val programmeDao = mockk<ProgrammeDao>()
    private val manualSource = mockk<ManualSourceManager>(relaxed = true)

    @Before
    fun setup() {
        coEvery { syncService.syncAll(any()) } returns emptyList()
        coEvery { epgSyncService.syncAll(any()) } returns emptyList()
        every { programmeDao.observeAllNow(any()) } returns flowOf(emptyList())
        every { programmeDao.observeAllNext(any()) } returns flowOf(emptyList())
        every { sourceDao.getAll() } returns flowOf(listOf(testSource()))
        coEvery { sourceDao.getManualSource() } returns null
        coEvery { sourceDao.insert(any()) } returns 99L
        every { channelDao.getRecentlyWatched() } returns flowOf(emptyList())
        every { channelDao.getGroups() } returns flowOf(emptyList())
        every { channelDao.getChannelsPaged(any(), any(), any(), any()) } returns mockk(relaxed = true)
        every { channelDao.getChannelsPagedByName(any(), any(), any(), any()) } returns mockk(relaxed = true)
        every { channelDao.getChannelsPagedByLastWatched(any(), any(), any(), any()) } returns mockk(relaxed = true)
        coEvery { channelDao.count() } returns 0
        coEvery { channelDao.insert(any()) } returns 1L
        coEvery { channelDao.update(any()) } just Runs
        coEvery { channelDao.delete(any()) } just Runs
        every { searchHistoryRepo.history } returns MutableStateFlow(emptyList())
        every { prefsRepository.sortOrder } returns flowOf(SortOrder.BY_GROUP)
    }

    private fun makeVm(clock: FakeTimeProvider = FakeTimeProvider()) =
        HomeViewModel(
            channelDao,
            sourceDao,
            syncService,
            searchHistoryRepo,
            prefsRepository,
            programmeDao,
            manualSource,
            clock,
        )

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
    fun `failed write surfaces an actionError message`() = runTest {
        coEvery { channelDao.toggleFavorite(any()) } throws RuntimeException("disk full")

        val vm = makeVm()
        vm.toggleFavorite(42L)
        advanceUntilIdle()

        assertEquals(R.string.home_error_update_favourite, vm.actionError.value)
    }

    @Test
    fun `clearActionError resets the message`() = runTest {
        coEvery { channelDao.setHidden(any(), any()) } throws RuntimeException("io error")

        val vm = makeVm()
        vm.hideChannel(1L)
        advanceUntilIdle()
        assertNotNull(vm.actionError.value)

        vm.clearActionError()
        assertNull(vm.actionError.value)
    }

    @Test
    fun `manual sync bypasses throttle`() = runTest {
        coEvery { syncService.syncAll(0L) } returns emptyList()

        makeVm().sync()
        advanceUntilIdle()

        coVerify { syncService.syncAll(throttleMs = 0L) }
    }

    @Test
    fun `init does not trigger sync — that is AppSyncCoordinator's job now`() = runTest {
        makeVm()
        advanceUntilIdle()

        coVerify(exactly = 0) { syncService.syncAll(any()) }
        coVerify(exactly = 0) { epgSyncService.syncAll(any()) }
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
    fun `addCustomChannel delegates to ManualSourceManager`() = runTest {
        makeVm().addCustomChannel("My Channel", "http://example.com/stream", null, "Sports")
        advanceUntilIdle()

        coVerify { manualSource.addChannel("My Channel", "http://example.com/stream", null, "Sports") }
    }

    @Test
    fun `updateCustomChannel delegates to ManualSourceManager`() = runTest {
        val channel = testChannel(id = 5L, name = "Updated")
        makeVm().updateCustomChannel(channel)
        advanceUntilIdle()

        coVerify { manualSource.updateChannel(channel) }
    }

    @Test
    fun `deleteCustomChannel delegates to ManualSourceManager`() = runTest {
        val channel = testChannel(id = 7L)
        makeVm().deleteCustomChannel(channel)
        advanceUntilIdle()

        coVerify { manualSource.deleteChannel(channel) }
    }

    @Test
    fun `nowNextByChannel pairs the current and next programme per channel`() = runTest {
        val now = testProgramme(
            sourceId = 1L,
            tvgChannelId = "a.tv",
            startMs = 1_000L,
            endMs = 2_000L,
            title = "Now Show",
        )
        val next = testProgramme(
            sourceId = 1L,
            tvgChannelId = "a.tv",
            startMs = 2_000L,
            endMs = 3_000L,
            title = "Next Show",
        )
        every { programmeDao.observeAllNow(any()) } returns flowOf(listOf(now))
        every { programmeDao.observeAllNext(any()) } returns flowOf(listOf(next))

        val vm = makeVm()
        backgroundScope.launch { vm.nowNextByChannel.collect {} }
        runCurrent()

        val entry = vm.nowNextByChannel.value[1L to "a.tv"]
        assertEquals("Now Show", entry?.now?.title)
        assertEquals("Next Show", entry?.next?.title)
    }

    @Test
    fun `nowNextByChannel includes a channel that only has a next programme`() = runTest {
        val next = testProgramme(
            sourceId = 2L,
            tvgChannelId = "b.tv",
            startMs = 5_000L,
            endMs = 6_000L,
            title = "Later",
        )
        every { programmeDao.observeAllNext(any()) } returns flowOf(listOf(next))

        val vm = makeVm()
        backgroundScope.launch { vm.nowNextByChannel.collect {} }
        runCurrent()

        val entry = vm.nowNextByChannel.value[2L to "b.tv"]
        assertNull(entry?.now)
        assertEquals("Later", entry?.next?.title)
    }

    @Test
    fun `nowNextByChannel re-queries the current programme on each clock tick`() = runTest {
        val clock = FakeTimeProvider(initial = 1_000L)
        val first = testProgramme(
            sourceId = 1L,
            tvgChannelId = "a.tv",
            startMs = 1_000L,
            endMs = 2_000L,
            title = "First",
        )
        val second = testProgramme(
            sourceId = 1L,
            tvgChannelId = "a.tv",
            startMs = 61_000L,
            endMs = 62_000L,
            title = "Second",
        )
        every { programmeDao.observeAllNow(1_000L) } returns flowOf(listOf(first))
        every { programmeDao.observeAllNow(61_000L) } returns flowOf(listOf(second))
        every { programmeDao.observeAllNext(any()) } returns flowOf(emptyList())

        val vm = makeVm(clock)
        backgroundScope.launch { vm.nowNextByChannel.collect {} }
        runCurrent()
        assertEquals("First", vm.nowNextByChannel.value[1L to "a.tv"]?.now?.title)

        clock.tick(61_000L)
        runCurrent()

        assertEquals("Second", vm.nowNextByChannel.value[1L to "a.tv"]?.now?.title)
    }

    @Test
    fun `manualSourceId re-exports state from ManualSourceManager`() = runTest {
        every { manualSource.manualSourceId } returns MutableStateFlow(55L)

        val vm = makeVm()
        advanceUntilIdle()

        assertEquals(55L, vm.manualSourceId.value)
    }
}

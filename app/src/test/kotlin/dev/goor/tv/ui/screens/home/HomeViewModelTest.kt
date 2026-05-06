package dev.goor.tv.ui.screens.home

import dev.goor.tv.data.db.dao.ChannelDao
import dev.goor.tv.data.db.dao.SourceDao
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
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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

    @Before
    fun setup() {
        coEvery { syncService.syncAll() } just Runs
        every { sourceDao.getAll() } returns flowOf(listOf(testSource()))
    }

    private fun makeVm() = HomeViewModel(channelDao, sourceDao, syncService)

    @Test
    fun `search filters channels by name case-insensitively`() = runTest {
        every { channelDao.getAll() } returns flowOf(listOf(
            testChannel(id = 1, name = "BBC One"),
            testChannel(id = 2, name = "CNN"),
            testChannel(id = 3, name = "BBC Two"),
        ))

        val vm = makeVm()
        backgroundScope.launch { vm.channels.collect {} }

        vm.setSearchQuery("bbc")
        advanceUntilIdle()

        assertEquals(2, vm.channels.value.size)
        assertTrue(vm.channels.value.all { "BBC" in it.name })
    }

    @Test
    fun `clearing search shows all channels`() = runTest {
        every { channelDao.getAll() } returns flowOf(listOf(
            testChannel(id = 1, name = "BBC One"),
            testChannel(id = 2, name = "CNN"),
        ))

        val vm = makeVm()
        backgroundScope.launch { vm.channels.collect {} }

        vm.setSearchQuery("BBC")
        vm.setSearchQuery("")
        advanceUntilIdle()

        assertEquals(2, vm.channels.value.size)
    }

    @Test
    fun `group filter shows only channels in the selected group`() = runTest {
        every { channelDao.getAll() } returns flowOf(listOf(
            testChannel(id = 1, name = "BBC One", group = "UK"),
            testChannel(id = 2, name = "CNN", group = "US"),
            testChannel(id = 3, name = "BBC Two", group = "UK"),
        ))

        val vm = makeVm()
        backgroundScope.launch { vm.channels.collect {} }

        vm.selectGroup("UK")
        advanceUntilIdle()

        assertEquals(2, vm.channels.value.size)
        assertTrue(vm.channels.value.all { it.group == "UK" })
    }

    @Test
    fun `selecting null group shows all channels`() = runTest {
        every { channelDao.getAll() } returns flowOf(listOf(
            testChannel(id = 1, group = "UK"),
            testChannel(id = 2, group = "US"),
        ))

        val vm = makeVm()
        backgroundScope.launch { vm.channels.collect {} }

        vm.selectGroup("UK")
        vm.selectGroup(null)
        advanceUntilIdle()

        assertEquals(2, vm.channels.value.size)
    }

    @Test
    fun `favorites filter shows only favorited channels`() = runTest {
        every { channelDao.getAll() } returns flowOf(listOf(
            testChannel(id = 1, name = "Starred", isFavorite = true),
            testChannel(id = 2, name = "Not starred", isFavorite = false),
        ))

        val vm = makeVm()
        backgroundScope.launch { vm.channels.collect {} }

        vm.toggleFavoritesOnly()
        advanceUntilIdle()

        assertEquals(1, vm.channels.value.size)
        assertEquals("Starred", vm.channels.value.first().name)
    }

    @Test
    fun `toggling favoritesOnly twice shows all channels again`() = runTest {
        every { channelDao.getAll() } returns flowOf(listOf(
            testChannel(id = 1, isFavorite = true),
            testChannel(id = 2, isFavorite = false),
        ))

        val vm = makeVm()
        backgroundScope.launch { vm.channels.collect {} }

        vm.toggleFavoritesOnly()
        vm.toggleFavoritesOnly()
        advanceUntilIdle()

        assertEquals(2, vm.channels.value.size)
    }

    @Test
    fun `toggleFavorite delegates to ChannelDao`() = runTest {
        every { channelDao.getAll() } returns flowOf(emptyList())
        coEvery { channelDao.toggleFavorite(any()) } just Runs

        makeVm().toggleFavorite(42L)
        advanceUntilIdle()

        coVerify { channelDao.toggleFavorite(42L) }
    }

    @Test
    fun `groups are extracted from channels, deduplicated and sorted`() = runTest {
        every { channelDao.getAll() } returns flowOf(listOf(
            testChannel(id = 1, group = "Sports"),
            testChannel(id = 2, group = "News"),
            testChannel(id = 3, group = "Sports"),
            testChannel(id = 4, group = null),
        ))

        val vm = makeVm()
        backgroundScope.launch { vm.groups.collect {} }
        advanceUntilIdle()

        assertEquals(listOf("News", "Sports"), vm.groups.value)
    }

    @Test
    fun `recentlyWatched returns watched channels sorted by lastWatchedAt descending`() = runTest {
        val now = System.currentTimeMillis()
        every { channelDao.getAll() } returns flowOf(listOf(
            testChannel(id = 1, name = "Old", lastWatchedAt = now - 10_000),
            testChannel(id = 2, name = "Never watched", lastWatchedAt = null),
            testChannel(id = 3, name = "Recent", lastWatchedAt = now),
        ))

        val vm = makeVm()
        backgroundScope.launch { vm.recentlyWatched.collect {} }
        advanceUntilIdle()

        val recent = vm.recentlyWatched.value
        assertEquals(2, recent.size)
        assertEquals("Recent", recent[0].name)
        assertEquals("Old", recent[1].name)
    }

    @Test
    fun `recentlyWatched excludes channels never watched`() = runTest {
        every { channelDao.getAll() } returns flowOf(listOf(
            testChannel(id = 1, lastWatchedAt = null),
            testChannel(id = 2, lastWatchedAt = null),
        ))

        val vm = makeVm()
        backgroundScope.launch { vm.recentlyWatched.collect {} }
        advanceUntilIdle()

        assertTrue(vm.recentlyWatched.value.isEmpty())
    }

    @Test
    fun `syncAll is called on ViewModel init`() = runTest {
        every { channelDao.getAll() } returns flowOf(emptyList())

        makeVm()
        advanceUntilIdle()

        coVerify { syncService.syncAll() }
    }

    @Test
    fun `search and group filter combine correctly`() = runTest {
        every { channelDao.getAll() } returns flowOf(listOf(
            testChannel(id = 1, name = "BBC One", group = "UK"),
            testChannel(id = 2, name = "BBC World", group = "International"),
            testChannel(id = 3, name = "ITV", group = "UK"),
        ))

        val vm = makeVm()
        backgroundScope.launch { vm.channels.collect {} }

        vm.selectGroup("UK")
        vm.setSearchQuery("BBC")
        advanceUntilIdle()

        assertEquals(1, vm.channels.value.size)
        assertEquals("BBC One", vm.channels.value.first().name)
    }
}

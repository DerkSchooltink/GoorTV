package dev.goor.tv.ui.screens.settings

import dev.goor.tv.R
import dev.goor.tv.data.db.dao.ChannelDao
import dev.goor.tv.data.db.dao.SourceDao
import dev.goor.tv.network.EpgSyncService
import dev.goor.tv.network.SourceSyncService
import dev.goor.tv.util.MainDispatcherRule
import dev.goor.tv.util.testSource
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.Runs
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
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
class SettingsViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val sourceDao = mockk<SourceDao>(relaxed = true)
    private val channelDao = mockk<ChannelDao>()
    private val syncService = mockk<SourceSyncService>()
    private val epgSyncService = mockk<EpgSyncService>()

    @Before
    fun setup() {
        every { sourceDao.getAll() } returns flowOf(emptyList())
        every { channelDao.getHiddenCount() } returns flowOf(0)
    }

    private fun makeVm() = SettingsViewModel(sourceDao, channelDao, syncService, epgSyncService)

    @Test
    fun `syncSource removes source from syncingIds on success`() = runTest {
        val source = testSource(id = 1L)
        coEvery { syncService.sync(source) } just Runs

        val vm = makeVm()
        vm.syncSource(source)
        advanceUntilIdle()

        assertFalse(source.id in vm.syncingIds.value)
        assertNull(vm.snackbarMessage.value)
    }

    @Test
    fun `syncSource sets snackbarMessage on failure`() = runTest {
        val source = testSource(id = 1L, name = "Bad Source")
        coEvery { syncService.sync(source) } throws RuntimeException("connection refused")

        val vm = makeVm()
        vm.syncSource(source)
        advanceUntilIdle()

        val msg = vm.snackbarMessage.value
        assertNotNull(msg)
        assertEquals(R.string.settings_error_sync_failed, msg!!.resId)
        assertTrue(msg.args.contains("Bad Source"))
        assertTrue(msg.args.any { it.toString().contains("connection refused") })
    }

    @Test
    fun `syncSource removes source from syncingIds even on failure`() = runTest {
        val source = testSource(id = 1L)
        coEvery { syncService.sync(source) } throws RuntimeException("timeout")

        val vm = makeVm()
        vm.syncSource(source)
        advanceUntilIdle()

        assertFalse(source.id in vm.syncingIds.value)
    }

    @Test
    fun `clearSnackbar sets message to null`() = runTest {
        val source = testSource(id = 1L)
        coEvery { syncService.sync(source) } throws RuntimeException("error")

        val vm = makeVm()
        vm.syncSource(source)
        advanceUntilIdle()
        assertNotNull(vm.snackbarMessage.value)

        vm.clearSnackbar()
        assertNull(vm.snackbarMessage.value)
    }

    @Test
    fun `updateSource delegates to SourceDao`() = runTest {
        coEvery { sourceDao.update(any()) } just Runs

        val updated = testSource(id = 1L, name = "Renamed")
        makeVm().updateSource(updated)
        advanceUntilIdle()

        coVerify { sourceDao.update(updated) }
    }

    @Test
    fun `addM3uSource rejects an invalid url without inserting`() = runTest {
        val vm = makeVm()
        vm.addM3uSource(name = "Bad", url = "not-a-url")
        advanceUntilIdle()

        assertNotNull(vm.snackbarMessage.value)
        assertEquals(R.string.settings_error_invalid_url, vm.snackbarMessage.value!!.resId)
        coVerify(exactly = 0) { sourceDao.insert(any()) }
    }

    @Test
    fun `addXtreamSource rejects a non-http url without inserting`() = runTest {
        val vm = makeVm()
        vm.addXtreamSource(name = "Bad", url = "ftp://example.com", username = "u", password = "p")
        advanceUntilIdle()

        assertNotNull(vm.snackbarMessage.value)
        coVerify(exactly = 0) { sourceDao.insert(any()) }
    }

    @Test
    fun `addM3uSource accepts a valid http url`() = runTest {
        coEvery { sourceDao.findByTypeAndUrl(any(), any()) } returns null

        val vm = makeVm()
        vm.addM3uSource(name = "Good", url = "https://example.com/playlist.m3u")
        advanceUntilIdle()

        coVerify { sourceDao.insert(any()) }
    }

    @Test
    fun `updateSource rejects an invalid url without updating`() = runTest {
        val vm = makeVm()
        vm.updateSource(testSource(id = 1L, url = "garbage"))
        advanceUntilIdle()

        assertNotNull(vm.snackbarMessage.value)
        coVerify(exactly = 0) { sourceDao.update(any()) }
    }

    @Test
    fun `deleteSource delegates to SourceDao`() = runTest {
        val source = testSource(id = 1L)
        coEvery { sourceDao.delete(any()) } just Runs

        makeVm().deleteSource(source)
        advanceUntilIdle()

        coVerify { sourceDao.delete(source) }
    }

    @Test
    fun `syncEpg removes source from epgSyncingIds on success`() = runTest {
        val source = testSource(id = 1L, epgUrl = "http://example.com/epg.xml")
        coEvery { epgSyncService.sync(source) } just Runs

        val vm = makeVm()
        vm.syncEpg(source)
        advanceUntilIdle()

        assertFalse(source.id in vm.epgSyncingIds.value)
        assertNull(vm.snackbarMessage.value)
    }

    @Test
    fun `syncEpg sets snackbarMessage and clears epgSyncingIds on failure`() = runTest {
        val source = testSource(id = 1L, name = "Bad EPG", epgUrl = "http://example.com/epg.xml")
        coEvery { epgSyncService.sync(source) } throws RuntimeException("404 Not Found")

        val vm = makeVm()
        vm.syncEpg(source)
        advanceUntilIdle()

        val msg = vm.snackbarMessage.value
        assertNotNull(msg)
        assertEquals(R.string.settings_error_epg_sync_failed, msg!!.resId)
        assertTrue(msg.args.contains("Bad EPG"))
        assertTrue(msg.args.any { it.toString().contains("404") })
        assertFalse(source.id in vm.epgSyncingIds.value)
    }

    @Test
    fun `epgSyncingIds reflects in-flight EPG sync`() = runTest {
        val source = testSource(id = 1L, epgUrl = "http://example.com/epg.xml")
        val gate = CompletableDeferred<Unit>()
        coEvery { epgSyncService.sync(source) } coAnswers { gate.await() }

        val vm = makeVm()
        val job = launch { vm.syncEpg(source) }
        advanceUntilIdle()

        assertTrue(source.id in vm.epgSyncingIds.value)

        gate.complete(Unit)
        job.join()
        advanceUntilIdle()

        assertFalse(source.id in vm.epgSyncingIds.value)
    }

    @Test
    fun `updateEpgUrl trims and forwards non-blank values to SourceDao`() = runTest {
        coEvery { sourceDao.updateEpgUrl(any(), any()) } just Runs

        val vm = makeVm()
        vm.updateEpgUrl(7L, "https://example.com/epg.xml")
        advanceUntilIdle()

        coVerify { sourceDao.updateEpgUrl(7L, "https://example.com/epg.xml") }
    }

    @Test
    fun `updateEpgUrl forwards null when blank`() = runTest {
        coEvery { sourceDao.updateEpgUrl(any(), any()) } just Runs

        val vm = makeVm()
        vm.updateEpgUrl(8L, "")
        advanceUntilIdle()

        coVerify { sourceDao.updateEpgUrl(8L, null) }
    }

    @Test
    fun `syncing is true while sync is in progress`() = runTest {
        val source = testSource(id = 1L)
        // Use a channel to pause the sync mid-flight
        val gate = CompletableDeferred<Unit>()
        coEvery { syncService.sync(source) } coAnswers { gate.await() }

        val vm = makeVm()
        // Launch sync without waiting
        val job = launch { vm.syncSource(source) }
        advanceUntilIdle()

        assertTrue(source.id in vm.syncingIds.value)

        gate.complete(Unit)
        job.join()
        advanceUntilIdle()

        assertFalse(source.id in vm.syncingIds.value)
    }
}

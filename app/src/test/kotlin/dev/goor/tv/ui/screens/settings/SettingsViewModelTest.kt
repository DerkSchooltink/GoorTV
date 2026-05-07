package dev.goor.tv.ui.screens.settings

import dev.goor.tv.data.db.dao.ChannelDao
import dev.goor.tv.data.db.dao.SourceDao
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
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class SettingsViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val sourceDao = mockk<SourceDao>()
    private val channelDao = mockk<ChannelDao>()
    private val syncService = mockk<SourceSyncService>()

    @Before
    fun setup() {
        every { sourceDao.getAll() } returns flowOf(emptyList())
    }

    private fun makeVm() = SettingsViewModel(sourceDao, channelDao, syncService)

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
        assertTrue(msg!!.contains("Bad Source"))
        assertTrue(msg.contains("connection refused"))
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
    fun `deleteSource delegates to SourceDao`() = runTest {
        val source = testSource(id = 1L)
        coEvery { sourceDao.delete(any()) } just Runs

        makeVm().deleteSource(source)
        advanceUntilIdle()

        coVerify { sourceDao.delete(source) }
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

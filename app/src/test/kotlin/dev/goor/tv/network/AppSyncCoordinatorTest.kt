package dev.goor.tv.network

import dev.goor.tv.util.MainDispatcherRule
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AppSyncCoordinatorTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val testDispatcher get() = mainDispatcherRule.testDispatcher

    @Test
    fun `start runs source sync then epg sync in order`() = runTest {
        val sourceService = mockk<SourceSyncService>()
        val epgService = mockk<EpgSyncService>()
        coEvery { sourceService.syncAll(any()) } returns emptyList()
        coEvery { epgService.syncAll(any()) } returns emptyList()

        val coordinator = AppSyncCoordinator(sourceService, epgService, testDispatcher)
        coordinator.start().join()

        coVerifyOrder {
            sourceService.syncAll(any())
            epgService.syncAll(any())
        }
        assertNull(coordinator.lastTopLevelError.value)
    }

    @Test
    fun `top-level source failure does not cancel epg sync`() = runTest {
        val sourceService = mockk<SourceSyncService>()
        val epgService = mockk<EpgSyncService>()
        val boom = IllegalStateException("DB write blew up")
        coEvery { sourceService.syncAll(any()) } throws boom
        coEvery { epgService.syncAll(any()) } returns emptyList()

        val coordinator = AppSyncCoordinator(sourceService, epgService, testDispatcher)
        coordinator.start().join()

        coVerify { epgService.syncAll(any()) }
        assertEquals(boom, coordinator.lastTopLevelError.value)
    }

    @Test
    fun `top-level epg failure is recorded`() = runTest {
        val sourceService = mockk<SourceSyncService>()
        val epgService = mockk<EpgSyncService>()
        val boom = IllegalStateException("xmltv parse blew up")
        coEvery { sourceService.syncAll(any()) } returns emptyList()
        coEvery { epgService.syncAll(any()) } throws boom

        val coordinator = AppSyncCoordinator(sourceService, epgService, testDispatcher)
        coordinator.start().join()

        assertEquals(boom, coordinator.lastTopLevelError.value)
    }

    @Test
    fun `stop cancels in-flight sync`() = runTest {
        val sourceService = mockk<SourceSyncService>()
        val epgService = mockk<EpgSyncService>()
        coEvery { sourceService.syncAll(any()) } coAnswers {
            kotlinx.coroutines.awaitCancellation()
        }
        coEvery { epgService.syncAll(any()) } returns emptyList()

        val coordinator = AppSyncCoordinator(sourceService, epgService, testDispatcher)
        val job = coordinator.start()
        coordinator.stop()
        advanceUntilIdle()

        assertTrue(job.isCancelled)
        coVerify(exactly = 0) { epgService.syncAll(any()) }
    }
}

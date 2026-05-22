package dev.goor.tv.ui.screens.player

import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.android.gms.cast.framework.CastSession
import dev.goor.tv.data.StreamConcurrencyTracker
import dev.goor.tv.data.db.dao.ChannelDao
import dev.goor.tv.data.db.dao.ProgrammeDao
import dev.goor.tv.data.db.dao.SourceDao
import dev.goor.tv.data.model.Channel
import dev.goor.tv.data.model.Source
import dev.goor.tv.data.model.SourceType
import dev.goor.tv.ui.theme.GoorTVTheme
import dev.goor.tv.ui.util.NoOpSystemBarsController
import dev.goor.tv.util.TimeProvider
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.flow.flowOf
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

private const val CHANNEL_ID = 7L

@RunWith(AndroidJUnit4::class)
class PlayerScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val channelDao = mockk<ChannelDao>(relaxed = true)
    private val sourceDao = mockk<SourceDao>(relaxed = true)
    private val programmeDao = mockk<ProgrammeDao>(relaxed = true)
    private val concurrencyTracker = mockk<StreamConcurrencyTracker>(relaxed = true)

    @Before
    fun stubFlows() {
        every { programmeDao.observeNowAndNext(any(), any(), any()) } returns flowOf(emptyList())
    }

    private fun playerVm() = PlayerViewModel(
        channelId = CHANNEL_ID,
        channelDao = channelDao,
        sourceDao = sourceDao,
        concurrencyTracker = concurrencyTracker,
        programmeDao = programmeDao,
        timeProvider = TimeProvider(),
    )

    @Test
    fun backButton_firesOnBackCallback() {
        // Channel null → no playback path; we're just verifying the Back IconButton
        // wires to onBack. NoOpSystemBarsController keeps the test activity's
        // window untouched (the real controller's hide(systemBars()) tears the
        // Compose host's composition down — that's the seam this refactor opened).
        coEvery { channelDao.getById(CHANNEL_ID) } returns null

        var backFired = false
        composeTestRule.setContent {
            GoorTVTheme {
                PlayerScreen(
                    channelId = CHANNEL_ID,
                    onBack = { backFired = true },
                    vm = playerVm(),
                    systemBars = NoOpSystemBarsController,
                    castSessionState = remember { mutableStateOf<CastSession?>(null) },
                    playerEngine = NoOpPlayerEngine,
                )
            }
        }

        composeTestRule.onNodeWithContentDescription("Back").assertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Back").performClick()
        assertTrue(backFired)
    }

    @Test
    fun forcedStopFromConcurrencyTracker_triggersOnBack() {
        // VM init registers with the tracker passing an `onForceStop` lambda.
        // We capture it via MockK and invoke directly — that flips `stopped` to
        // true and the LaunchedEffect in the screen must call onBack.
        val channel = Channel(
            id = CHANNEL_ID,
            sourceId = 1L,
            name = "BBC One",
            url = "http://example.com/stream.ts",
        )
        val source = Source(id = 1L, name = "Src", type = SourceType.M3U, url = "http://example.com")
        coEvery { channelDao.getById(CHANNEL_ID) } returns channel
        coEvery { sourceDao.getById(1L) } returns source

        val onForceStop = slot<() -> Unit>()
        every {
            concurrencyTracker.register(
                sourceId = 1L,
                maxConcurrent = any(),
                onForceStop = capture(onForceStop),
            )
        } returns { /* unregister no-op */ }

        var backFired = false
        composeTestRule.setContent {
            GoorTVTheme {
                PlayerScreen(
                    channelId = CHANNEL_ID,
                    onBack = { backFired = true },
                    vm = playerVm(),
                    systemBars = NoOpSystemBarsController,
                    castSessionState = remember { mutableStateOf<CastSession?>(null) },
                    playerEngine = NoOpPlayerEngine,
                )
            }
        }

        composeTestRule.waitUntil(timeoutMillis = 5_000) { onForceStop.isCaptured }
        composeTestRule.runOnUiThread { onForceStop.captured.invoke() }
        composeTestRule.waitUntil(timeoutMillis = 5_000) { backFired }
        assertTrue(backFired)
    }
}

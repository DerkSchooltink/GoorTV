package dev.goor.tv.ui.screens.player

import android.content.Context
import android.view.View
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
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

    @Test
    fun playbackError_showsRetryOverlayAfterAutoRetry() {
        // FakePlayerEngine starts in error and prepare() does NOT clear it, so the
        // single auto-retry fails and the error overlay (with its Retry button)
        // surfaces after AUTO_RETRY_DELAY_MS.
        val channel = liveChannel()
        coEvery { channelDao.getById(CHANNEL_ID) } returns channel
        coEvery { sourceDao.getById(1L) } returns m3uSource()

        val engine = FakePlayerEngine(initialError = "Network error")

        composeTestRule.setContent {
            GoorTVTheme {
                PlayerScreen(
                    channelId = CHANNEL_ID,
                    onBack = {},
                    vm = playerVm(),
                    systemBars = NoOpSystemBarsController,
                    castSessionState = remember { mutableStateOf<CastSession?>(null) },
                    playerEngine = engine,
                )
            }
        }

        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            composeTestRule.onAllNodesWithText("Retry").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithText("Retry").assertIsDisplayed()
        composeTestRule.onNodeWithText("Network error").assertIsDisplayed()
    }

    @Test
    fun aspectRatioDropdown_updatesButtonLabelOnSelection() {
        // Tap the video surface to reveal the controls footer, open the aspect
        // dropdown, pick "Fill", and assert the button label updates.
        val channel = liveChannel()
        coEvery { channelDao.getById(CHANNEL_ID) } returns channel
        coEvery { sourceDao.getById(1L) } returns m3uSource()

        composeTestRule.setContent {
            GoorTVTheme {
                PlayerScreen(
                    channelId = CHANNEL_ID,
                    onBack = {},
                    vm = playerVm(),
                    systemBars = NoOpSystemBarsController,
                    castSessionState = remember { mutableStateOf<CastSession?>(null) },
                    playerEngine = NoOpPlayerEngine,
                )
            }
        }

        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            composeTestRule.onAllNodesWithTag("player_surface").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithTag("player_surface").performClick()

        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            composeTestRule.onAllNodesWithText("Aspect: Fit").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithText("Aspect: Fit").performClick()

        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            composeTestRule.onAllNodesWithText("Fill").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithText("Fill").performClick()

        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            composeTestRule.onAllNodesWithText("Aspect: Fill").fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithText("Aspect: Fill").assertIsDisplayed()
    }

    @Test
    fun forcedStop_showsStreamLimitOverlayBeforePoppingBack() {
        // Same force-stop seam as the back-pop test, but here we assert the
        // explanatory "Stream limit reached" overlay shows in the window before
        // onBack runs (it's delayed ~3s after `stopped` flips).
        val channel = liveChannel()
        coEvery { channelDao.getById(CHANNEL_ID) } returns channel
        coEvery { sourceDao.getById(1L) } returns m3uSource()

        val onForceStop = slot<() -> Unit>()
        every {
            concurrencyTracker.register(
                sourceId = 1L,
                maxConcurrent = any(),
                onForceStop = capture(onForceStop),
            )
        } returns { /* unregister no-op */ }

        composeTestRule.setContent {
            GoorTVTheme {
                PlayerScreen(
                    channelId = CHANNEL_ID,
                    onBack = {},
                    vm = playerVm(),
                    systemBars = NoOpSystemBarsController,
                    castSessionState = remember { mutableStateOf<CastSession?>(null) },
                    playerEngine = NoOpPlayerEngine,
                )
            }
        }

        composeTestRule.waitUntil(timeoutMillis = 5_000) { onForceStop.isCaptured }
        composeTestRule.runOnUiThread { onForceStop.captured.invoke() }

        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            composeTestRule.onAllNodesWithText("Stream limit reached")
                .fetchSemanticsNodes().isNotEmpty()
        }
        composeTestRule.onNodeWithText("Stream limit reached").assertIsDisplayed()
    }

    private fun liveChannel() = Channel(
        id = CHANNEL_ID,
        sourceId = 1L,
        name = "BBC One",
        url = "http://example.com/stream.ts",
    )

    private fun m3uSource() =
        Source(id = 1L, name = "Src", type = SourceType.M3U, url = "http://example.com")
}

/**
 * Drivable [PlayerEngine] fake. Unlike the shared [NoOpPlayerEngine] object, this
 * has per-instance [isBuffering] / [errorMessage] state the test can mutate, and
 * its [prepare] deliberately does NOT clear [errorMessage] so the auto-retry path
 * still ends in the error overlay.
 */
private class FakePlayerEngine(initialError: String? = null) : PlayerEngine {
    override val isBuffering = mutableStateOf(false)
    override val errorMessage = mutableStateOf(initialError)
    override fun prepare(uri: String, headers: Map<String, String>) = Unit
    override fun pause() = Unit
    override fun play() = Unit
    override fun release() = Unit
    override fun createPlayerView(context: Context): View? = null
    override fun applyResizeMode(view: View?, mode: PlayerEngine.ResizeMode) = Unit
}

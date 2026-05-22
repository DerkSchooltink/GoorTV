package dev.goor.tv.ui.screens.home

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.goor.tv.data.ManualSourceManager
import dev.goor.tv.data.SearchHistoryRepository
import dev.goor.tv.data.db.dao.ChannelDao
import dev.goor.tv.data.db.dao.ProgrammeDao
import dev.goor.tv.data.db.dao.SourceDao
import dev.goor.tv.data.model.Channel
import dev.goor.tv.data.model.Source
import dev.goor.tv.data.model.SourceType
import dev.goor.tv.data.preferences.UserPreferencesRepository
import dev.goor.tv.network.EpgSyncService
import dev.goor.tv.network.SourceSyncService
import dev.goor.tv.ui.theme.GoorTVTheme
import dev.goor.tv.util.TimeProvider
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HomeScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val channelDao = mockk<ChannelDao>(relaxed = true)
    private val sourceDao = mockk<SourceDao>(relaxed = true)
    private val syncService = mockk<SourceSyncService> {
        coEvery { syncAll() } returns emptyList()
    }
    private val searchHistoryRepo = mockk<SearchHistoryRepository>(relaxed = true)
    private val prefsRepository = mockk<UserPreferencesRepository>(relaxed = true)
    private val epgSyncService = mockk<EpgSyncService> {
        coEvery { syncAll(any()) } returns emptyList()
    }
    private val programmeDao = mockk<ProgrammeDao>(relaxed = true)
    private val manualSource = mockk<ManualSourceManager>(relaxed = true)

    private fun homeVm() = HomeViewModel(
        channelDao = channelDao,
        sourceDao = sourceDao,
        syncService = syncService,
        searchHistoryRepo = searchHistoryRepo,
        prefsRepository = prefsRepository,
        programmeDao = programmeDao,
        manualSource = manualSource,
        timeProvider = TimeProvider(),
    )

    // ── Empty states ──────────────────────────────────────────────────────────

    @Test
    fun emptyState_noSources_showsAddSourcePrompt() {
        every { channelDao.getAll() } returns flowOf(emptyList())
        every { sourceDao.getAll() } returns flowOf(emptyList())

        composeTestRule.setContent {
            GoorTVTheme { HomeScreen(onChannelClick = {}, onSettingsClick = {}, vm = homeVm()) }
        }

        composeTestRule.onNodeWithText("No sources added").assertIsDisplayed()
        composeTestRule.onNodeWithText("Add a source").assertIsDisplayed()
    }

    @Test
    fun emptyState_addSourceButton_firesCallback() {
        every { channelDao.getAll() } returns flowOf(emptyList())
        every { sourceDao.getAll() } returns flowOf(emptyList())

        var clicked = false
        composeTestRule.setContent {
            GoorTVTheme {
                HomeScreen(
                    onChannelClick = {},
                    onSettingsClick = { clicked = true },
                    vm = homeVm(),
                )
            }
        }

        // "Add a source" CTA in empty state navigates to settings
        composeTestRule.onNodeWithText("Add a source").performClick()
        assertTrue(clicked)
    }

    // ── Channel list ──────────────────────────────────────────────────────────

    @Test
    fun channelList_showsChannelNamesAndGroups() {
        every { channelDao.getAll() } returns flowOf(listOf(
            Channel(id = 1, sourceId = 1, name = "BBC One", url = "http://example.com/1", group = "UK"),
            Channel(id = 2, sourceId = 1, name = "CNN", url = "http://example.com/2", group = "US"),
        ))
        every { sourceDao.getAll() } returns flowOf(listOf(
            Source(id = 1, name = "Test", type = SourceType.M3U, url = "http://example.com")
        ))

        composeTestRule.setContent {
            GoorTVTheme { HomeScreen(onChannelClick = {}, onSettingsClick = {}, vm = homeVm()) }
        }

        composeTestRule.onNodeWithText("BBC One").assertIsDisplayed()
        composeTestRule.onNodeWithText("CNN").assertIsDisplayed()
        composeTestRule.onNodeWithText("UK").assertIsDisplayed()
    }

    @Test
    fun channelList_clickingChannel_firesCallback() {
        every { channelDao.getAll() } returns flowOf(listOf(
            Channel(id = 7, sourceId = 1, name = "BBC One", url = "http://example.com/1"),
        ))
        every { sourceDao.getAll() } returns flowOf(listOf(
            Source(id = 1, name = "Test", type = SourceType.M3U, url = "http://example.com")
        ))

        var clickedId = -1L
        composeTestRule.setContent {
            GoorTVTheme {
                HomeScreen(
                    onChannelClick = { clickedId = it },
                    onSettingsClick = {},
                    vm = homeVm(),
                )
            }
        }

        composeTestRule.onNodeWithText("BBC One").performClick()
        assertTrue(clickedId == 7L)
    }

    // ── Search ────────────────────────────────────────────────────────────────

    @Test
    fun search_barAppearsAfterTappingSearchIcon() {
        every { channelDao.getAll() } returns flowOf(emptyList())
        every { sourceDao.getAll() } returns flowOf(emptyList())

        composeTestRule.setContent {
            GoorTVTheme { HomeScreen(onChannelClick = {}, onSettingsClick = {}, vm = homeVm()) }
        }

        composeTestRule.onNodeWithText("Search channels…").assertDoesNotExist()
        composeTestRule.onNodeWithContentDescription("Search").performClick()
        composeTestRule.onNodeWithText("Search channels…").assertIsDisplayed()
    }

    @Test
    fun search_closingSearchBarClearsText() {
        every { channelDao.getAll() } returns flowOf(listOf(
            Channel(id = 1, sourceId = 1, name = "BBC One", url = "http://example.com/1"),
        ))
        every { sourceDao.getAll() } returns flowOf(listOf(
            Source(id = 1, name = "Test", type = SourceType.M3U, url = "http://example.com")
        ))

        composeTestRule.setContent {
            GoorTVTheme { HomeScreen(onChannelClick = {}, onSettingsClick = {}, vm = homeVm()) }
        }

        // Open search and type
        composeTestRule.onNodeWithContentDescription("Search").performClick()
        composeTestRule.onNodeWithText("Search channels…").performTextInput("xyz")

        // Close search — channel should be visible again (search cleared)
        composeTestRule.onNodeWithContentDescription("Close search").performClick()
        composeTestRule.onNodeWithText("BBC One").assertIsDisplayed()
    }

    // ── Settings navigation ───────────────────────────────────────────────────

    @Test
    fun settingsIcon_firesCallback() {
        every { channelDao.getAll() } returns flowOf(emptyList())
        every { sourceDao.getAll() } returns flowOf(emptyList())

        var clicked = false
        composeTestRule.setContent {
            GoorTVTheme {
                HomeScreen(
                    onChannelClick = {},
                    onSettingsClick = { clicked = true },
                    vm = homeVm(),
                )
            }
        }

        composeTestRule.onNodeWithContentDescription("Settings").performClick()
        assertTrue(clicked)
    }
}

package dev.goor.tv.ui.screens.home

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.paging.PagingSource
import androidx.paging.PagingState
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.goor.tv.data.ManualSourceManager
import dev.goor.tv.data.SearchHistoryRepository
import dev.goor.tv.data.db.dao.ChannelDao
import dev.goor.tv.data.db.dao.ProgrammeDao
import dev.goor.tv.data.db.dao.SourceDao
import dev.goor.tv.data.model.Channel
import dev.goor.tv.data.model.Source
import dev.goor.tv.data.model.SourceType
import dev.goor.tv.data.preferences.SortOrder
import dev.goor.tv.data.preferences.UserPreferencesRepository
import dev.goor.tv.network.SourceSyncService
import dev.goor.tv.ui.theme.GoorTVTheme
import dev.goor.tv.util.TimeProvider
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * In-memory replacement for the Room-backed [PagingSource] returned by
 * `ChannelDao.getChannelsPaged*`. One page, no keys — enough to drive Compose
 * paging into rendering all items.
 */
private class FakeChannelPagingSource(private val items: List<Channel>) : PagingSource<Int, Channel>() {
    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, Channel> =
        LoadResult.Page(data = items, prevKey = null, nextKey = null)
    override fun getRefreshKey(state: PagingState<Int, Channel>): Int? = null
}

@RunWith(AndroidJUnit4::class)
class HomeScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val channelDao = mockk<ChannelDao>(relaxed = true)
    private val sourceDao = mockk<SourceDao>(relaxed = true)
    private val syncService = mockk<SourceSyncService> {
        coEvery { syncAll() } returns emptyList()
    }
    private val searchHistoryRepo = mockk<SearchHistoryRepository>()
    private val prefsRepository = mockk<UserPreferencesRepository>()
    private val programmeDao = mockk<ProgrammeDao>(relaxed = true)
    private val manualSource = mockk<ManualSourceManager>()

    /** Seed for the paging source. Mutate before calling [render]. */
    private val channels = mutableListOf<Channel>()

    @Before
    fun stubVmFlows() {
        // Real flows for everything the VM observes. relaxed mocks return Java
        // proxies that crash when downstream casts them to List/StateFlow.
        every { searchHistoryRepo.history } returns MutableStateFlow(emptyList())
        // Search history is read-only for most tests but the search-close path
        // writes the in-flight query to it. Accept any add() call as a no-op.
        every { searchHistoryRepo.add(any()) } returns Unit
        every { prefsRepository.sortOrder } returns flowOf(SortOrder.BY_GROUP)
        every { manualSource.manualSourceId } returns MutableStateFlow<Long?>(null)
        every { channelDao.getGroups() } returns flowOf(emptyList())
        every { channelDao.getRecentlyWatched() } returns flowOf(emptyList())
        every { programmeDao.observeAllNow(any()) } returns flowOf(emptyList())
        // pagingData reads from one of these three depending on sortOrder; stub all
        // three so a sort change in a test doesn't accidentally hit an unstubbed path.
        // favOnly is the third arg — honour it so the favourites-filter test can
        // observe the list actually shrink.
        every { channelDao.getChannelsPaged(any(), any(), any()) } answers {
            FakeChannelPagingSource(channelsForFavOnly(thirdArg()))
        }
        every { channelDao.getChannelsPagedByName(any(), any(), any()) } answers {
            FakeChannelPagingSource(channelsForFavOnly(thirdArg()))
        }
        every { channelDao.getChannelsPagedByLastWatched(any(), any(), any()) } answers {
            FakeChannelPagingSource(channelsForFavOnly(thirdArg()))
        }
    }

    private fun channelsForFavOnly(favOnly: Boolean): List<Channel> =
        if (favOnly) channels.filter { it.isFavorite } else channels.toList()

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

    private fun render(onChannelClick: (Long) -> Unit = {}, onSettingsClick: () -> Unit = {}) {
        composeTestRule.setContent {
            GoorTVTheme {
                HomeScreen(
                    onChannelClick = onChannelClick,
                    onSettingsClick = onSettingsClick,
                    vm = homeVm(),
                )
            }
        }
    }

    // ── Empty states ──────────────────────────────────────────────────────────

    @Test
    fun emptyState_noSources_showsAddSourcePrompt() {
        every { sourceDao.getAll() } returns flowOf(emptyList())

        render()

        composeTestRule.onNodeWithText("No sources added").assertIsDisplayed()
        composeTestRule.onNodeWithText("Add a source").assertIsDisplayed()
    }

    @Test
    fun emptyState_addSourceButton_firesCallback() {
        every { sourceDao.getAll() } returns flowOf(emptyList())

        var clicked = false
        render(onSettingsClick = { clicked = true })

        composeTestRule.onNodeWithText("Add a source").performClick()
        assertTrue(clicked)
    }

    // ── Channel list ──────────────────────────────────────────────────────────

    @Test
    fun channelList_showsChannelNamesAndGroups() {
        channels += listOf(
            Channel(id = 1, sourceId = 1, name = "BBC One", url = "http://example.com/1", group = "UK"),
            Channel(id = 2, sourceId = 1, name = "CNN", url = "http://example.com/2", group = "US"),
        )
        every { sourceDao.getAll() } returns flowOf(listOf(
            Source(id = 1, name = "Test", type = SourceType.M3U, url = "http://example.com")
        ))

        render()

        composeTestRule.onNodeWithText("BBC One").assertIsDisplayed()
        composeTestRule.onNodeWithText("CNN").assertIsDisplayed()
        // BY_GROUP sort + blank search + !favOnly → group headers injected by
        // insertSeparators. "UK" appears as a header above the BBC One row AND
        // is also rendered inline on the row itself, so assert "≥1 node" rather
        // than the exact-one default of assertIsDisplayed().
        composeTestRule.onAllNodesWithText("UK").assertCountEquals(2)
    }

    @Test
    fun channelList_clickingChannel_firesCallback() {
        channels += Channel(id = 7, sourceId = 1, name = "BBC One", url = "http://example.com/1")
        every { sourceDao.getAll() } returns flowOf(listOf(
            Source(id = 1, name = "Test", type = SourceType.M3U, url = "http://example.com")
        ))

        var clickedId = -1L
        render(onChannelClick = { clickedId = it })

        composeTestRule.onNodeWithText("BBC One").performClick()
        assertTrue(clickedId == 7L)
    }

    // ── Favorites ─────────────────────────────────────────────────────────────

    @Test
    fun favoriteIcon_onChannelRow_callsToggleFavorite() {
        channels += Channel(id = 42, sourceId = 1, name = "BBC One", url = "http://example.com/1", isFavorite = false)
        every { sourceDao.getAll() } returns flowOf(listOf(
            Source(id = 1, name = "Test", type = SourceType.M3U, url = "http://example.com")
        ))

        render()

        // Non-favorite channel exposes "Add favorite" on the row's icon button.
        composeTestRule.onNodeWithContentDescription("Add favorite").performClick()

        coVerify { channelDao.toggleFavorite(42L) }
    }

    @Test
    fun favoritesOnlyFilter_filtersListToFavoritesAfterToggle() {
        channels += listOf(
            Channel(id = 1, sourceId = 1, name = "FavChannel", url = "http://example.com/1", isFavorite = true),
            Channel(id = 2, sourceId = 1, name = "OtherChannel", url = "http://example.com/2", isFavorite = false),
        )
        every { sourceDao.getAll() } returns flowOf(listOf(
            Source(id = 1, name = "Test", type = SourceType.M3U, url = "http://example.com")
        ))

        render()

        // Default: both channels visible.
        composeTestRule.onNodeWithText("FavChannel").assertIsDisplayed()
        composeTestRule.onNodeWithText("OtherChannel").assertIsDisplayed()

        // Toggle: only favorites remain. The icon's content-description is
        // "Favourites" before the toggle (announces what flipping will do).
        composeTestRule.onNodeWithContentDescription("Favourites").performClick()
        composeTestRule.waitUntil(timeoutMillis = 5_000) {
            composeTestRule.onAllNodesWithText("OtherChannel").fetchSemanticsNodes().isEmpty()
        }

        composeTestRule.onNodeWithText("FavChannel").assertIsDisplayed()
        composeTestRule.onNodeWithText("OtherChannel").assertDoesNotExist()
    }

    // ── Search ────────────────────────────────────────────────────────────────

    @Test
    fun search_barAppearsAfterTappingSearchIcon() {
        every { sourceDao.getAll() } returns flowOf(emptyList())

        render()

        composeTestRule.onNodeWithText("Search channels…").assertDoesNotExist()
        composeTestRule.onNodeWithContentDescription("Search").performClick()
        composeTestRule.onNodeWithText("Search channels…").assertIsDisplayed()
    }

    @Test
    fun search_closingSearchBarClearsText() {
        channels += Channel(id = 1, sourceId = 1, name = "BBC One", url = "http://example.com/1")
        every { sourceDao.getAll() } returns flowOf(listOf(
            Source(id = 1, name = "Test", type = SourceType.M3U, url = "http://example.com")
        ))

        render()

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
        every { sourceDao.getAll() } returns flowOf(emptyList())

        var clicked = false
        render(onSettingsClick = { clicked = true })

        composeTestRule.onNodeWithContentDescription("Settings").performClick()
        assertTrue(clicked)
    }
}

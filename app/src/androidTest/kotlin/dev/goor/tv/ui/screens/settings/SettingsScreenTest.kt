package dev.goor.tv.ui.screens.settings

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.goor.tv.data.db.dao.ChannelDao
import dev.goor.tv.data.db.dao.SourceDao
import dev.goor.tv.data.model.Source
import dev.goor.tv.data.model.SourceType
import dev.goor.tv.network.EpgSyncService
import dev.goor.tv.network.SourceSyncService
import dev.goor.tv.ui.theme.GoorTVTheme
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SettingsScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val sourceDao = mockk<SourceDao>()
    private val channelDao = mockk<ChannelDao>(relaxed = true)
    private val syncService = mockk<SourceSyncService>(relaxed = true)
    private val epgSyncService = mockk<EpgSyncService>(relaxed = true)

    private fun settingsVm() = SettingsViewModel(sourceDao, channelDao, syncService, epgSyncService)

    // ── Source list ───────────────────────────────────────────────────────────

    @Test
    fun sourceList_displaysSourceNameAndType() {
        every { sourceDao.getAll() } returns flowOf(listOf(
            Source(id = 1, name = "My Playlist", type = SourceType.M3U, url = "http://example.com"),
        ))

        composeTestRule.setContent {
            GoorTVTheme { SettingsScreen(onBack = {}, vm = settingsVm()) }
        }

        composeTestRule.onNodeWithText("My Playlist").assertIsDisplayed()
        composeTestRule.onNodeWithText("M3U").assertIsDisplayed()
    }

    @Test
    fun emptyList_noSourcesShown() {
        every { sourceDao.getAll() } returns flowOf(emptyList())

        composeTestRule.setContent {
            GoorTVTheme { SettingsScreen(onBack = {}, vm = settingsVm()) }
        }

        composeTestRule.onNodeWithText("My Playlist").assertDoesNotExist()
    }

    // ── Add source dialog ─────────────────────────────────────────────────────

    @Test
    fun addButton_opensAddSourceDialog() {
        every { sourceDao.getAll() } returns flowOf(emptyList())

        composeTestRule.setContent {
            GoorTVTheme { SettingsScreen(onBack = {}, vm = settingsVm()) }
        }

        composeTestRule.onNodeWithContentDescription("Add source").performClick()
        composeTestRule.onNodeWithText("Add Source").assertIsDisplayed()
    }

    @Test
    fun addDialog_cancelDismissesWithoutSaving() {
        every { sourceDao.getAll() } returns flowOf(emptyList())

        composeTestRule.setContent {
            GoorTVTheme { SettingsScreen(onBack = {}, vm = settingsVm()) }
        }

        composeTestRule.onNodeWithContentDescription("Add source").performClick()
        composeTestRule.onNodeWithText("Cancel").performClick()
        composeTestRule.onNodeWithText("Add Source").assertDoesNotExist()
    }

    @Test
    fun addDialog_switchingToXtream_showsCredentialFields() {
        every { sourceDao.getAll() } returns flowOf(emptyList())

        composeTestRule.setContent {
            GoorTVTheme { SettingsScreen(onBack = {}, vm = settingsVm()) }
        }

        composeTestRule.onNodeWithContentDescription("Add source").performClick()
        composeTestRule.onNodeWithText("XTREAM").performClick()

        composeTestRule.onNodeWithText("Username").assertIsDisplayed()
        composeTestRule.onNodeWithText("Password").assertIsDisplayed()
    }

    // ── Edit source dialog ────────────────────────────────────────────────────

    @Test
    fun editButton_opensEditSourceDialog() {
        every { sourceDao.getAll() } returns flowOf(listOf(
            Source(id = 1, name = "My Source", type = SourceType.M3U, url = "http://example.com"),
        ))

        composeTestRule.setContent {
            GoorTVTheme { SettingsScreen(onBack = {}, vm = settingsVm()) }
        }

        composeTestRule.onNodeWithContentDescription("Edit").performClick()
        composeTestRule.onNodeWithText("Edit Source").assertIsDisplayed()
    }

    @Test
    fun editDialog_isPrefilledWithCurrentValues() {
        every { sourceDao.getAll() } returns flowOf(listOf(
            Source(id = 1, name = "Existing Name", type = SourceType.M3U, url = "http://existing.com"),
        ))

        composeTestRule.setContent {
            GoorTVTheme { SettingsScreen(onBack = {}, vm = settingsVm()) }
        }

        composeTestRule.onNodeWithContentDescription("Edit").performClick()
        composeTestRule.onNodeWithText("Existing Name").assertIsDisplayed()
        composeTestRule.onNodeWithText("http://existing.com").assertIsDisplayed()
    }

    // ── Navigation ────────────────────────────────────────────────────────────

    @Test
    fun backButton_firesCallback() {
        every { sourceDao.getAll() } returns flowOf(emptyList())

        var backPressed = false
        composeTestRule.setContent {
            GoorTVTheme { SettingsScreen(onBack = { backPressed = true }, vm = settingsVm()) }
        }

        composeTestRule.onNodeWithContentDescription("Back").performClick()
        assertTrue(backPressed)
    }
}

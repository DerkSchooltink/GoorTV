package dev.goor.tv.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import dev.goor.tv.ui.screens.guide.GuideScreen
import dev.goor.tv.ui.screens.home.HomeScreen
import dev.goor.tv.ui.screens.player.PlayerScreen
import dev.goor.tv.ui.screens.settings.SettingsScreen
import kotlinx.serialization.Serializable

/**
 * Compose Navigation 2.8+ type-safe routes. Destinations are `@Serializable`
 * data class / object instances so the compiler enforces argument types
 * end-to-end — no more `"player/{channelId}"` interpolation,
 * `navArgument(...)` declarations, or defensive `channelId ?: -1L` fallback.
 */
@Serializable
internal object Home

@Serializable
internal data class Player(val channelId: Long)

@Serializable
internal object Settings

@Serializable
internal object Guide

@Composable
fun AppNavigation() {
    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = Home) {
        composable<Home> {
            HomeScreen(
                onChannelClick = { navController.navigate(Player(channelId = it)) },
                onSettingsClick = { navController.navigate(Settings) },
                onGuideClick = { navController.navigate(Guide) },
            )
        }
        composable<Player> { entry ->
            val route: Player = entry.toRoute()
            PlayerScreen(
                channelId = route.channelId,
                onBack = { navController.popBackStack() },
            )
        }
        composable<Settings> {
            SettingsScreen(onBack = { navController.popBackStack() })
        }
        composable<Guide> {
            GuideScreen(
                onBack = { navController.popBackStack() },
                onWatch = { navController.navigate(Player(channelId = it)) },
                onGoToSettings = { navController.navigate(Settings) },
            )
        }
    }
}

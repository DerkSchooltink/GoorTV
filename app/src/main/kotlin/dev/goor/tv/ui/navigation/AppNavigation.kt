package dev.goor.tv.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import dev.goor.tv.ui.screens.guide.GuideScreen
import dev.goor.tv.ui.screens.home.HomeScreen
import dev.goor.tv.ui.screens.player.PlayerScreen
import dev.goor.tv.ui.screens.settings.SettingsScreen

sealed class Screen(val route: String) {
    object Home : Screen("home")
    object Player : Screen("player/{channelId}") {
        fun createRoute(channelId: Long) = "player/$channelId"
    }
    object Settings : Screen("settings")
    object Guide : Screen("guide")
}

@Composable
fun AppNavigation() {
    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = Screen.Home.route) {
        composable(Screen.Home.route) {
            HomeScreen(
                onChannelClick = { navController.navigate(Screen.Player.createRoute(it)) },
                onSettingsClick = { navController.navigate(Screen.Settings.route) },
                onGuideClick = { navController.navigate(Screen.Guide.route) },
            )
        }
        composable(
            Screen.Player.route,
            arguments = listOf(navArgument("channelId") { type = NavType.LongType })
        ) { entry ->
            val channelId = entry.arguments?.getLong("channelId") ?: -1L
            if (channelId <= 0L) {
                LaunchedEffect(Unit) { navController.popBackStack() }
            } else {
                PlayerScreen(
                    channelId = channelId,
                    onBack = { navController.popBackStack() }
                )
            }
        }
        composable(Screen.Settings.route) {
            SettingsScreen(onBack = { navController.popBackStack() })
        }
        composable(Screen.Guide.route) {
            GuideScreen(
                onBack = { navController.popBackStack() },
                onWatch = { channelId ->
                    navController.navigate(Screen.Player.createRoute(channelId))
                },
                onGoToSettings = { navController.navigate(Screen.Settings.route) },
            )
        }
    }
}

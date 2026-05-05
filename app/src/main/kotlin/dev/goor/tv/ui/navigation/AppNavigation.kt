package dev.goor.tv.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import dev.goor.tv.ui.screens.home.HomeScreen
import dev.goor.tv.ui.screens.player.PlayerScreen
import dev.goor.tv.ui.screens.settings.SettingsScreen

sealed class Screen(val route: String) {
    object Home : Screen("home")
    object Player : Screen("player/{channelId}") {
        fun createRoute(channelId: Long) = "player/$channelId"
    }
    object Settings : Screen("settings")
}

@Composable
fun AppNavigation() {
    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = Screen.Home.route) {
        composable(Screen.Home.route) {
            HomeScreen(
                onChannelClick = { navController.navigate(Screen.Player.createRoute(it)) },
                onSettingsClick = { navController.navigate(Screen.Settings.route) }
            )
        }
        composable(
            Screen.Player.route,
            arguments = listOf(navArgument("channelId") { type = NavType.LongType })
        ) { entry ->
            PlayerScreen(
                channelId = entry.arguments!!.getLong("channelId"),
                onBack = { navController.popBackStack() }
            )
        }
        composable(Screen.Settings.route) {
            SettingsScreen(onBack = { navController.popBackStack() })
        }
    }
}

package com.llamacpp.mobile.ui.navigation

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.llamacpp.mobile.di.AppContainer
import com.llamacpp.mobile.ui.chat.ChatScreen
import com.llamacpp.mobile.ui.completion.CompletionScreen
import com.llamacpp.mobile.ui.serverinfo.ServerInfoScreen
import com.llamacpp.mobile.ui.servers.ServerEditScreen
import com.llamacpp.mobile.ui.servers.ServersScreen
import com.llamacpp.mobile.ui.settings.SettingsScreen
import com.llamacpp.mobile.ui.settings.ToolsScreen

@Composable
fun AppNavHost(container: AppContainer) {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = Routes.CHAT,
        enterTransition = { fadeIn(tween(160)) },
        exitTransition = { fadeOut(tween(160)) },
        popEnterTransition = { fadeIn(tween(160)) },
        popExitTransition = { fadeOut(tween(160)) },
    ) {
        composable(Routes.CHAT) {
            ChatScreen(container = container, onNavigate = { navController.navigate(it) })
        }
        composable(Routes.COMPLETION) {
            CompletionScreen(container = container, onBack = { navController.popBackStack() })
        }
        composable(Routes.SERVER_INFO) {
            ServerInfoScreen(container = container, onBack = { navController.popBackStack() })
        }
        composable(Routes.SETTINGS) {
            SettingsScreen(
                container = container,
                onBack = { navController.popBackStack() },
                onManageServers = { navController.navigate(Routes.SERVERS) },
                onOpenTools = { navController.navigate(Routes.TOOLS) },
            )
        }
        composable(Routes.TOOLS) {
            ToolsScreen(container = container, onBack = { navController.popBackStack() })
        }
        composable(Routes.SERVERS) {
            ServersScreen(
                container = container,
                onBack = { navController.popBackStack() },
                onEdit = { id -> navController.navigate(Routes.serverEdit(id)) },
            )
        }
        composable(
            route = "${Routes.SERVER_EDIT}?${Routes.ARG_SERVER_ID}={${Routes.ARG_SERVER_ID}}",
            arguments = listOf(
                navArgument(Routes.ARG_SERVER_ID) {
                    type = NavType.StringType
                    defaultValue = ""
                },
            ),
        ) { entry ->
            val id = entry.arguments?.getString(Routes.ARG_SERVER_ID)?.ifBlank { null }
            ServerEditScreen(
                container = container,
                serverId = id,
                onBack = { navController.popBackStack() },
            )
        }
    }
}

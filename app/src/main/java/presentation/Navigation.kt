package com.example.customgalleryviewer.presentation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument

@Composable
fun NavigationGraph(navController: NavHostController) {
    NavHost(navController = navController, startDestination = "home") {
        composable("home") {
            HomeScreen(
                onNavigateToPlayer = { playlistId ->
                    navController.navigate("player/$playlistId")
                },
                onNavigateToAdd = {
                    navController.navigate("add_playlist")
                },
                onNavigateToSettings = {
                    navController.navigate("settings")
                },
                onNavigateToEdit = { playlistId ->
                    navController.navigate("edit_playlist/$playlistId")
                }
            )
        }

        composable(
            "player/{playlistId}",
            arguments = listOf(navArgument("playlistId") { type = NavType.LongType })
        ) { backStackEntry ->
            val playlistId = backStackEntry.arguments?.getLong("playlistId") ?: 0L
            PlayerScreen(playlistId = playlistId)
        }

        composable(
            "edit_playlist/{playlistId}",
            arguments = listOf(navArgument("playlistId") { type = NavType.LongType })
        ) { backStackEntry ->
            val playlistId = backStackEntry.arguments?.getLong("playlistId") ?: 0L
            EditPlaylistScreen(
                playlistId = playlistId,
                onBackClick = { navController.popBackStack() }
            )
        }

        composable("settings") {
            SettingsScreen(
                onBackClick = { navController.popBackStack() }
            )
        }

        composable("add_playlist") {
            AddPlaylistScreen(
                onBack = { navController.popBackStack() }
            )
        }
    }
}
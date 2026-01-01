package com.example.customgalleryviewer

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.customgalleryviewer.presentation.AddPlaylistScreen
import com.example.customgalleryviewer.presentation.EditPlaylistScreen
import com.example.customgalleryviewer.presentation.HomeScreen
import com.example.customgalleryviewer.presentation.PlayerScreen
import com.example.customgalleryviewer.presentation.SettingsScreen
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val navController = rememberNavController()

                    NavHost(navController = navController, startDestination = "home") {

                        // 1. מסך הבית
                        composable("home") {
                            HomeScreen(
                                onNavigateToPlayer = { id -> navController.navigate("player/$id") },
                                onNavigateToAdd = { navController.navigate("add_playlist") },
                                onNavigateToSettings = { navController.navigate("settings") },
                                onNavigateToEdit = { id -> navController.navigate("edit_playlist/$id") }
                            )
                        }

                        // 2. מסך יצירת פלייליסט
                        composable("add_playlist") {
                            AddPlaylistScreen(
                                onBack = { navController.popBackStack() }
                            )
                        }

                        // 3. הנגן
                        composable(
                            route = "player/{playlistId}",
                            arguments = listOf(navArgument("playlistId") { type = NavType.LongType })
                        ) { backStackEntry ->
                            val playlistId = backStackEntry.arguments?.getLong("playlistId") ?: -1L
                            PlayerScreen(playlistId = playlistId)
                        }

                        // 4. עריכת פלייליסט
                        composable(
                            route = "edit_playlist/{playlistId}",
                            arguments = listOf(navArgument("playlistId") { type = NavType.LongType })
                        ) { backStackEntry ->
                            val playlistId = backStackEntry.arguments?.getLong("playlistId") ?: -1L
                            EditPlaylistScreen(
                                playlistId = playlistId,
                                onBackClick = { navController.popBackStack() }
                            )
                        }

                        // 5. הגדרות
                        composable("settings") {
                            SettingsScreen(
                                onBackClick = { navController.popBackStack() }
                            )
                        }
                    }
                }
            }
        }
    }
}
package com.example.customgalleryviewer

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import kotlin.math.sin
import kotlin.math.cos
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import kotlinx.coroutines.delay
import com.example.customgalleryviewer.presentation.AddPlaylistScreen
import com.example.customgalleryviewer.presentation.DeviceFolderScreen
import com.example.customgalleryviewer.presentation.EditPlaylistScreen
import com.example.customgalleryviewer.presentation.HomeScreen
import com.example.customgalleryviewer.presentation.PlayerScreen
import com.example.customgalleryviewer.presentation.SettingsScreen
import com.nowwhat.customgalleryviewer.ui.theme.CustomGalleryViewerTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            CustomGalleryViewerTheme {
                var showSplash by remember { mutableStateOf(true) }

                Box(modifier = Modifier.fillMaxSize()) {

                // One-time permission request at startup
                val permissionLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.RequestMultiplePermissions()
                ) { /* permissions granted or denied — no action needed */ }

                LaunchedEffect(Unit) {
                    val neededPermissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        arrayOf(
                            Manifest.permission.READ_MEDIA_IMAGES,
                            Manifest.permission.READ_MEDIA_VIDEO
                        )
                    } else {
                        arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
                    }

                    val notGranted = neededPermissions.filter {
                        ContextCompat.checkSelfPermission(this@MainActivity, it) !=
                                PackageManager.PERMISSION_GRANTED
                    }

                    if (notGranted.isNotEmpty()) {
                        permissionLauncher.launch(notGranted.toTypedArray())
                    }

                    // Request MANAGE_EXTERNAL_STORAGE for full filesystem access (m3u8 detection, etc.)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && !Environment.isExternalStorageManager()) {
                        try {
                            val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                                data = Uri.parse("package:${this@MainActivity.packageName}")
                            }
                            this@MainActivity.startActivity(intent)
                        } catch (_: Exception) {
                            // Fallback for devices that don't support per-app intent
                            val intent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                            this@MainActivity.startActivity(intent)
                        }
                    }
                }

                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val navController = rememberNavController()

                    NavHost(navController = navController, startDestination = "home") {
                        composable("home") {
                            HomeScreen(
                                onNavigateToPlayer = { id -> navController.navigate("player/$id") },
                                onNavigateToAdd = { navController.navigate("add_playlist") },
                                onNavigateToSettings = { navController.navigate("settings") },
                                onNavigateToEdit = { id -> navController.navigate("edit_playlist/$id") },
                                onNavigateToDeviceFolder = { bucketId -> navController.navigate("device_folder/$bucketId") }
                            )
                        }

                        composable("add_playlist") {
                            AddPlaylistScreen(
                                onBack = { navController.popBackStack() }
                            )
                        }

                        composable(
                            route = "player/{playlistId}",
                            arguments = listOf(navArgument("playlistId") { type = NavType.LongType })
                        ) { backStackEntry ->
                            val playlistId = backStackEntry.arguments?.getLong("playlistId") ?: -1L
                            PlayerScreen(
                                playlistId = playlistId,
                                onBackToHome = { navController.popBackStack() }
                            )
                        }

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

                        composable("settings") {
                            SettingsScreen(
                                onBackClick = { navController.popBackStack() }
                            )
                        }

                        composable(
                            route = "device_folder/{bucketId}",
                            arguments = listOf(navArgument("bucketId") { type = NavType.LongType })
                        ) { backStackEntry ->
                            val bucketId = backStackEntry.arguments?.getLong("bucketId") ?: 0L
                            DeviceFolderScreen(
                                bucketId = bucketId,
                                onBack = { navController.popBackStack() }
                            )
                        }
                    }
                }

                // Splash overlay on top of main content
                AnimatedVisibility(
                    visible = showSplash,
                    exit = fadeOut(animationSpec = tween(500))
                ) {
                    val infiniteTransition = rememberInfiniteTransition(label = "splash")
                    val time by infiniteTransition.animateFloat(
                        initialValue = 0f, targetValue = 360f,
                        animationSpec = infiniteRepeatable(tween(2000, easing = LinearEasing)),
                        label = "time"
                    )
                    Box(
                        modifier = Modifier.fillMaxSize().background(Color(0xFF0D0D0D)),
                        contentAlignment = Alignment.Center
                    ) {
                        val gridSize = 5
                        val colors = listOf(
                            Color(0xFF00E5FF), Color(0xFF6200EE), Color(0xFFFF6B6B),
                            Color(0xFF03DAC5), Color(0xFFBB86FC), Color(0xFFFFD700),
                            Color(0xFF00E5FF), Color(0xFF6200EE), Color(0xFFFF6B6B),
                            Color(0xFF03DAC5), Color(0xFFBB86FC), Color(0xFFFFD700),
                            Color(0xFF00E5FF), Color(0xFF6200EE), Color(0xFFFF6B6B),
                            Color(0xFF03DAC5), Color(0xFFBB86FC), Color(0xFFFFD700),
                            Color(0xFF00E5FF), Color(0xFF6200EE), Color(0xFFFF6B6B),
                            Color(0xFF03DAC5), Color(0xFFBB86FC), Color(0xFFFFD700),
                            Color(0xFF00E5FF)
                        )
                        Column(
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            for (row in 0 until gridSize) {
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    for (col in 0 until gridSize) {
                                        val index = row * gridSize + col
                                        val phase = index * 30f
                                        val animScale = remember { Animatable(0f) }
                                        LaunchedEffect(Unit) {
                                            delay((index * 40).toLong())
                                            animScale.animateTo(1f, animationSpec = spring(dampingRatio = 0.4f, stiffness = 300f))
                                        }
                                        val wave = sin(Math.toRadians((time + phase).toDouble())).toFloat()
                                        val offsetX = wave * 12f
                                        val offsetY = cos(Math.toRadians((time + phase * 1.3).toDouble())).toFloat() * 12f
                                        val rotation = wave * 60f
                                        val dynamicScale = 0.6f + (wave + 1f) * 0.25f
                                        Box(
                                            modifier = Modifier
                                                .size(42.dp)
                                                .graphicsLayer {
                                                    scaleX = animScale.value * dynamicScale
                                                    scaleY = animScale.value * dynamicScale
                                                    rotationZ = rotation
                                                    translationX = offsetX
                                                    translationY = offsetY
                                                    alpha = animScale.value
                                                }
                                                .clip(RoundedCornerShape((4 + wave * 6).dp))
                                                .background(colors[index % colors.size].copy(alpha = 0.9f))
                                        )
                                    }
                                }
                            }
                            Spacer(Modifier.height(32.dp))
                            Text("Gallery Viewer", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        }
                    }
                }

                LaunchedEffect(Unit) {
                    delay(2200)
                    showSplash = false
                }

                } // end Box
            }
        }
    }
}

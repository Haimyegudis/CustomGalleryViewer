package com.example.customgalleryviewer.presentation

import android.app.Activity
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.customgalleryviewer.data.MediaFilterType

@Composable
fun DeviceFolderScreen(
    bucketId: Long,
    onBack: () -> Unit,
    viewModel: DeviceFolderViewModel = hiltViewModel(),
    settingsViewModel: SettingsViewModel = hiltViewModel()
) {
    val files by viewModel.files.collectAsState()
    val currentMedia by viewModel.currentMedia.collectAsState()
    val isGalleryMode by viewModel.isGalleryMode.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val watchPositions by viewModel.watchPositions.collectAsState()
    val navigationMode by settingsViewModel.navigationMode.collectAsState()
    val context = LocalContext.current

    var gridColumns by remember { mutableIntStateOf(3) }
    var searchQuery by remember { mutableStateOf("") }
    var mediaFilter by remember { mutableStateOf(MediaFilterType.MIXED) }

    // Filter files
    val filteredFiles = remember(files, searchQuery, mediaFilter) {
        var result = files
        if (mediaFilter != MediaFilterType.MIXED) {
            result = result.filter { uri ->
                val isVideoFile = isVideo(uri.toString())
                when (mediaFilter) {
                    MediaFilterType.VIDEO_ONLY -> isVideoFile
                    MediaFilterType.PHOTOS_ONLY -> !isVideoFile
                    else -> true
                }
            }
        }
        if (searchQuery.isNotEmpty()) {
            result = result.filter {
                it.lastPathSegment?.contains(searchQuery, ignoreCase = true) == true
            }
        }
        result
    }

    BackHandler(enabled = true) {
        if (!isGalleryMode) {
            viewModel.setGalleryMode(true)
        } else {
            onBack()
        }
    }

    // Toggle system bars based on gallery mode
    LaunchedEffect(isGalleryMode) {
        val window = (context as? Activity)?.window ?: return@LaunchedEffect
        val insetsController = WindowCompat.getInsetsController(window, window.decorView)
        if (isGalleryMode) {
            insetsController.show(WindowInsetsCompat.Type.systemBars())
        } else {
            insetsController.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            insetsController.hide(WindowInsetsCompat.Type.systemBars())
        }
    }

    DisposableEffect(Unit) {
        val window = (context as? Activity)?.window
        window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        viewModel.loadFolder(bucketId)
        onDispose {
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            if (window != null) {
                WindowCompat.getInsetsController(window, window.decorView).show(WindowInsetsCompat.Type.systemBars())
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        if (isGalleryMode) {
            GalleryGridView(
                items = filteredFiles,
                currentUri = currentMedia,
                columns = gridColumns,
                searchQuery = searchQuery,
                isLoading = isLoading,
                mediaFilter = mediaFilter,
                watchPositions = watchPositions,
                onItemClick = { uri -> viewModel.jumpToItem(uri) },
                onColumnsChange = { gridColumns = it },
                onSearchQueryChange = { searchQuery = it },
                onMediaFilterChange = { mediaFilter = it },
                onBackToHome = onBack
            )
        } else {
            // Read persistent playback settings
            val prefs = remember { context.getSharedPreferences("gallery_settings", android.content.Context.MODE_PRIVATE) }
            var isShuffleOn by remember { mutableStateOf(prefs.getBoolean("shuffle_on", false)) }
            var isRepeatListOn by remember { mutableStateOf(prefs.getBoolean("repeat_list_on", false)) }

            PlayerContentView(
                currentMedia = currentMedia,
                navigationMode = navigationMode,
                isShuffleOn = isShuffleOn,
                isRepeatListOn = isRepeatListOn,
                onNext = { viewModel.onNext() },
                onPrev = { viewModel.onPrevious() },
                onToggleGallery = { viewModel.setGalleryMode(true) },
                onToggleShuffle = {
                    isShuffleOn = !isShuffleOn
                    prefs.edit().putBoolean("shuffle_on", isShuffleOn).apply()
                },
                onToggleRepeatList = {
                    isRepeatListOn = !isRepeatListOn
                    prefs.edit().putBoolean("repeat_list_on", isRepeatListOn).apply()
                },
                onSaveWatchPosition = { uri, pos, dur -> viewModel.saveWatchPosition(uri, pos, dur) },
                getWatchPosition = { uri -> viewModel.getWatchPosition(uri) },
                onToggleFavorite = { uri -> viewModel.toggleFavorite(uri) },
                isFavoriteCheck = { uri -> viewModel.isFavorite(uri) }
            )
        }
    }
}

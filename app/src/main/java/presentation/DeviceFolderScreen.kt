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

    DisposableEffect(Unit) {
        val window = (context as? Activity)?.window
        window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        if (window != null) {
            val insetsController = WindowCompat.getInsetsController(window, window.decorView)
            insetsController.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            insetsController.hide(WindowInsetsCompat.Type.systemBars())
        }
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
                onItemClick = { uri -> viewModel.jumpToItem(uri) },
                onColumnsChange = { gridColumns = it },
                onSearchQueryChange = { searchQuery = it },
                onMediaFilterChange = { mediaFilter = it },
                onBackToHome = onBack
            )
        } else {
            PlayerContentView(
                currentMedia = currentMedia,
                navigationMode = navigationMode,
                onNext = { viewModel.onNext() },
                onPrev = { viewModel.onPrevious() },
                onToggleGallery = { viewModel.setGalleryMode(true) }
            )
        }
    }
}

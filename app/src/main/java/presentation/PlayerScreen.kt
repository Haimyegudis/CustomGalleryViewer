package com.example.customgalleryviewer.presentation

import android.app.Activity
import android.net.Uri
import android.view.WindowManager
import android.webkit.MimeTypeMap
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.example.customgalleryviewer.presentation.components.ActionMenuDialog
import com.example.customgalleryviewer.presentation.components.MediaInfoDialog
import com.example.customgalleryviewer.presentation.components.VideoPlayer
import java.util.Locale

@Composable
fun PlayerScreen(
    playlistId: Long,
    viewModel: PlayerViewModel = hiltViewModel()
) {
    val currentMedia by viewModel.currentMedia.collectAsState()
    val context = LocalContext.current

    var showInfoButton by remember { mutableStateOf(false) }
    var showInfoDialog by remember { mutableStateOf(false) }
    var showActionMenu by remember { mutableStateOf(false) }

    DisposableEffect(Unit) {
        val window = (context as? Activity)?.window
        window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        if (window != null) {
            val insetsController = WindowCompat.getInsetsController(window, window.decorView)
            insetsController.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            insetsController.hide(WindowInsetsCompat.Type.systemBars())
        }

        viewModel.loadPlaylist(playlistId)

        onDispose {
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            if (window != null) {
                WindowCompat.getInsetsController(window, window.decorView).show(WindowInsetsCompat.Type.systemBars())
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        currentMedia?.let { uri ->
            val isVideoContent = isVideo(uri.toString())

            if (isVideoContent) {
                VideoPlayer(
                    uri = uri,
                    onNext = { viewModel.onNext() },
                    onPrev = { viewModel.onPrevious() },
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                // Image viewer
                AsyncImage(
                    model = uri,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit
                )

                // Info button (top-left when center is tapped)
                AnimatedVisibility(
                    visible = showInfoButton,
                    enter = fadeIn(),
                    exit = fadeOut(),
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(16.dp)
                ) {
                    IconButton(
                        onClick = { showInfoDialog = true },
                        modifier = Modifier
                            .size(48.dp)
                            .background(Color.Black.copy(0.6f), RoundedCornerShape(24.dp))
                    ) {
                        Icon(
                            Icons.Default.Info,
                            contentDescription = "Info",
                            tint = Color.White,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                }

                Row(modifier = Modifier.fillMaxSize()) {
                    Box(
                        modifier = Modifier
                            .weight(0.35f)
                            .fillMaxHeight()
                            .pointerInput(Unit) {
                                detectTapGestures(
                                    onTap = { viewModel.onPrevious() },
                                    onLongPress = { showActionMenu = true }
                                )
                            }
                    )
                    Box(
                        modifier = Modifier
                            .weight(0.3f)
                            .fillMaxHeight()
                            .pointerInput(Unit) {
                                detectTapGestures(
                                    onTap = { showInfoButton = !showInfoButton },
                                    onLongPress = { showActionMenu = true }
                                )
                            }
                    )
                    Box(
                        modifier = Modifier
                            .weight(0.35f)
                            .fillMaxHeight()
                            .pointerInput(Unit) {
                                detectTapGestures(
                                    onTap = { viewModel.onNext() },
                                    onLongPress = { showActionMenu = true }
                                )
                            }
                    )
                }

                // Info Dialog
                if (showInfoDialog) {
                    MediaInfoDialog(
                        uri = uri,
                        isVideo = false,
                        onDismiss = { showInfoDialog = false }
                    )
                }

                // Action Menu (Share, Open With)
                if (showActionMenu) {
                    ActionMenuDialog(
                        uri = uri,
                        isVideo = false,
                        onDismiss = { showActionMenu = false }
                    )
                }
            }
        } ?: CircularProgressIndicator(
            modifier = Modifier.align(Alignment.Center),
            color = Color.White
        )
    }
}

fun isVideo(path: String): Boolean {
    val lowercasePath = path.lowercase(Locale.getDefault())

    if (lowercasePath.endsWith(".mp4") ||
        lowercasePath.endsWith(".mkv") ||
        lowercasePath.endsWith(".mov") ||
        lowercasePath.endsWith(".avi") ||
        lowercasePath.endsWith(".webm") ||
        lowercasePath.endsWith(".3gp")) {
        return true
    }

    val extension = MimeTypeMap.getFileExtensionFromUrl(path)
    val mimeType = if (extension != null) {
        MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension)
    } else {
        null
    }
    return mimeType?.startsWith("video") == true
}
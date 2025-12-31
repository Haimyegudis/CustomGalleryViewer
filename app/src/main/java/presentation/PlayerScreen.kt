package com.example.customgalleryviewer.presentation

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.view.WindowManager
import android.webkit.MimeTypeMap
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.example.customgalleryviewer.presentation.components.VideoPlayer
import com.example.customgalleryviewer.presentation.components.shareMedia
import java.io.File
import java.util.Locale

@Composable
fun PlayerScreen(
    playlistId: Long,
    viewModel: PlayerViewModel = hiltViewModel()
) {
    val currentMedia by viewModel.currentMedia.collectAsState()
    val context = LocalContext.current

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
        if (currentMedia != null) {
            val uri = currentMedia!!
            // שימוש בפונקציית הזיהוי המשופרת
            val isVideoContent = isVideo(uri.toString())

            if (isVideoContent) {
                VideoPlayer(
                    uri = uri,
                    onNext = { viewModel.onNext() },
                    onPrev = { viewModel.onPrevious() },
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                AsyncImage(
                    model = uri,
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(Unit) {
                            detectTapGestures(
                                onLongPress = { shareMedia(context, uri, false) },
                                onTap = { offset ->
                                    val w = size.width
                                    if (offset.x > w * 0.75) viewModel.onNext()
                                    else if (offset.x < w * 0.25) viewModel.onPrevious()
                                }
                            )
                        },
                    contentScale = ContentScale.Fit
                )
            }
        } else {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.Center), color = Color.White)
        }
    }
}

// --- התיקון החשוב: זיהוי קבצים משופר ---
fun isVideo(path: String): Boolean {
    val lowercasePath = path.lowercase(Locale.getDefault())

    // בדיקה מהירה לפי סיומות נפוצות (עובד גם בלי MimeType)
    if (lowercasePath.endsWith(".mp4") ||
        lowercasePath.endsWith(".mkv") ||
        lowercasePath.endsWith(".mov") ||
        lowercasePath.endsWith(".avi") ||
        lowercasePath.endsWith(".webm") ||
        lowercasePath.endsWith(".3gp")) {
        return true
    }

    // ניסיון שני דרך MimeType
    val extension = MimeTypeMap.getFileExtensionFromUrl(path)
    val mimeType = if (extension != null) {
        MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension)
    } else {
        null
    }
    return mimeType?.startsWith("video") == true
}
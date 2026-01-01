package com.example.customgalleryviewer.presentation

import android.app.Activity
import android.net.Uri
import android.view.WindowManager
import android.webkit.MimeTypeMap
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import coil.decode.VideoFrameDecoder
import coil.request.ImageRequest
import com.example.customgalleryviewer.presentation.components.ActionMenuDialog
import com.example.customgalleryviewer.presentation.components.VideoPlayer
import java.util.Locale
import kotlin.math.max
import kotlin.math.min

@Composable
fun PlayerScreen(
    playlistId: Long,
    viewModel: PlayerViewModel = hiltViewModel(),
    settingsViewModel: SettingsViewModel = hiltViewModel()
) {
    val currentMedia by viewModel.currentMedia.collectAsState()
    val isGalleryMode by viewModel.isGalleryMode.collectAsState()
    val galleryItems by viewModel.galleryItems.collectAsState()
    val gridColumns by viewModel.gridColumns.collectAsState()
    val navigationMode by settingsViewModel.navigationMode.collectAsState()
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

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        if (isGalleryMode) {
            GalleryGridView(
                items = galleryItems,
                currentUri = currentMedia,
                columns = gridColumns,
                onItemClick = { uri -> viewModel.jumpToItem(uri) },
                onColumnsChange = { viewModel.setGridColumns(it) },
                onBackToPlayer = { viewModel.toggleGalleryMode() }
            )
        } else {
            PlayerContentView(
                currentMedia = currentMedia,
                navigationMode = navigationMode,
                onNext = { viewModel.onNext() },
                onPrev = { viewModel.onPrevious() },
                onToggleGallery = { viewModel.toggleGalleryMode() }
            )
        }
    }
}

@Composable
fun GalleryGridView(
    items: List<Uri>,
    currentUri: Uri?,
    columns: Int,
    onItemClick: (Uri) -> Unit,
    onColumnsChange: (Int) -> Unit,
    onBackToPlayer: () -> Unit
) {
    // משתנה צבירה לזום בגלריה
    var accumulatedZoom by remember { mutableFloatStateOf(1f) }

    // תיקון 1: העברת זיהוי המחווה לרמת ה-Column הראשי כדי לתפוס בכל המסך
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(Unit) {
                detectTransformGestures { _, _, zoom, _ ->
                    accumulatedZoom *= zoom
                    // תיקון 1 (המשך): הגברת רגישות. שינוי של 5% מספיק כדי לעורר פעולה.
                    if (accumulatedZoom > 1.05f) { // Zoom In -> פחות עמודות (תמונות גדולות יותר)
                        if (columns > 2) {
                            onColumnsChange(columns - 1)
                            accumulatedZoom = 1f // איפוס לאחר שינוי
                        }
                    } else if (accumulatedZoom < 0.95f) { // Zoom Out -> יותר עמודות (תמונות קטנות יותר)
                        if (columns < 8) {
                            onColumnsChange(columns + 1)
                            accumulatedZoom = 1f // איפוס לאחר שינוי
                        }
                    }
                }
            }
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().background(Color.Black.copy(0.8f)).padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBackToPlayer) { Icon(Icons.Default.ArrowBack, "Back", tint = Color.White) }
            Text("Gallery", color = Color.White, modifier = Modifier.padding(start = 8.dp))
        }

        Box(modifier = Modifier.fillMaxSize()) {
            LazyVerticalGrid(
                columns = GridCells.Fixed(columns),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(2.dp)
            ) {
                items(items) { uri ->
                    val isSelected = uri == currentUri
                    val isVideo = isVideo(uri.toString())
                    val context = LocalContext.current

                    Box(
                        modifier = Modifier
                            .padding(1.dp)
                            .aspectRatio(1f)
                            .border(if (isSelected) 3.dp else 0.dp, if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent)
                            .clickable { onItemClick(uri) }
                    ) {
                        AsyncImage(
                            model = ImageRequest.Builder(context)
                                .data(uri)
                                .decoderFactory(VideoFrameDecoder.Factory())
                                .crossfade(true)
                                .build(),
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop,
                            error = ColorPainter(Color.DarkGray)
                        )
                        if (isVideo) {
                            Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(0.3f)))
                            // תיקון 2: הקטנת גודל כפתור ה-Play
                            Icon(
                                imageVector = Icons.Default.PlayCircle,
                                contentDescription = null,
                                tint = Color.White.copy(0.9f),
                                modifier = Modifier.align(Alignment.Center).size(24.dp) // שונה מ-36dp ל-24dp
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PlayerContentView(
    currentMedia: Uri?,
    navigationMode: String,
    onNext: () -> Unit,
    onPrev: () -> Unit,
    onToggleGallery: () -> Unit
) {
    var showActionMenu by remember { mutableStateOf(false) }
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }

    // איפוס זום במעבר תמונה
    LaunchedEffect(currentMedia) {
        scale = 1f
        offset = Offset.Zero
    }

    Box(modifier = Modifier.fillMaxSize()) {
        currentMedia?.let { uri ->
            if (isVideo(uri.toString())) {
                VideoPlayer(uri = uri, onNext = onNext, onPrev = onPrev, modifier = Modifier.fillMaxSize())
            } else {
                // תצוגת תמונה עם זום וגרירה
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clipToBounds()
                        .pointerInput(Unit) {
                            detectTransformGestures { _, pan, zoom, _ ->
                                // חישוב זום עם גבולות
                                val newScale = (scale * zoom).coerceIn(1f, 4f)
                                scale = newScale

                                // חישוב גרירה (רק אם בזום)
                                if (scale > 1f) {
                                    val maxTranslateX = (size.width * (scale - 1)) / 2
                                    val maxTranslateY = (size.height * (scale - 1)) / 2
                                    offset = Offset(
                                        x = (offset.x + pan.x * scale).coerceIn(-maxTranslateX, maxTranslateX),
                                        y = (offset.y + pan.y * scale).coerceIn(-maxTranslateY, maxTranslateY)
                                    )
                                } else {
                                    offset = Offset.Zero
                                }
                            }
                        }
                        // Swipe Navigation - עובד רק כשאין זום (scale == 1)
                        .pointerInput(navigationMode, scale) {
                            if (navigationMode == "SWIPE" && scale == 1f) {
                                detectHorizontalDragGestures { _, dragAmount ->
                                    if (dragAmount < -50) onNext()
                                    else if (dragAmount > 50) onPrev()
                                }
                            }
                        }
                ) {
                    AsyncImage(
                        model = uri,
                        contentDescription = null,
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                scaleX = scale
                                scaleY = scale
                                translationX = offset.x
                                translationY = offset.y
                            },
                        contentScale = ContentScale.Fit
                    )

                    // Tap Navigation Zones (רק אם TAP מופעל ואין זום)
                    if (scale == 1f) {
                        Row(modifier = Modifier.fillMaxSize()) {
                            Box(modifier = Modifier.weight(0.3f).fillMaxHeight().pointerInput(navigationMode) {
                                detectTapGestures(onTap = { if (navigationMode == "TAP") onPrev() }, onLongPress = { showActionMenu = true })
                            })
                            Box(modifier = Modifier.weight(0.4f).fillMaxHeight().pointerInput(Unit) {
                                detectTapGestures(onLongPress = { showActionMenu = true })
                            })
                            Box(modifier = Modifier.weight(0.3f).fillMaxHeight().pointerInput(navigationMode) {
                                detectTapGestures(onTap = { if (navigationMode == "TAP") onNext() }, onLongPress = { showActionMenu = true })
                            })
                        }
                    }

                    if (showActionMenu) ActionMenuDialog(uri = uri, isVideo = false, onDismiss = { showActionMenu = false })
                }
            }
            IconButton(
                onClick = onToggleGallery,
                modifier = Modifier.align(Alignment.TopEnd).padding(16.dp, end = 48.dp).background(Color.Black.copy(0.5f), MaterialTheme.shapes.small)
            ) {
                Icon(Icons.Default.GridView, "Gallery", tint = Color.White)
            }
        }
    }
}

fun isVideo(path: String): Boolean {
    val lowercasePath = path.lowercase(Locale.getDefault())
    if (lowercasePath.endsWith(".mp4") || lowercasePath.endsWith(".mkv") || lowercasePath.endsWith(".mov")) return true
    val extension = MimeTypeMap.getFileExtensionFromUrl(path)
    return MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension)?.startsWith("video") == true
}
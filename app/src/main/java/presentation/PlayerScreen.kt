package com.example.customgalleryviewer.presentation

import android.app.Activity
import android.net.Uri
import android.view.WindowManager
import android.webkit.MimeTypeMap
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
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

    // לוג למעקב אחרי שינויים
    LaunchedEffect(isGalleryMode, galleryItems.size) {
        android.util.Log.d("PlayerScreen", "State: isGalleryMode=$isGalleryMode, galleryItems=${galleryItems.size}, currentMedia=$currentMedia")
    }

    DisposableEffect(Unit) {
        val window = (context as? Activity)?.window
        window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        if (window != null) {
            val insetsController = WindowCompat.getInsetsController(window, window.decorView)
            insetsController.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            insetsController.hide(WindowInsetsCompat.Type.systemBars())
        }

        android.util.Log.d("PlayerScreen", "DisposableEffect: Loading playlist $playlistId")
        viewModel.loadPlaylist(playlistId)
        // תיקון: נכנסים תמיד למצב גלריה בהתחלה
        viewModel.setGalleryMode(true)

        onDispose {
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            if (window != null) {
                WindowCompat.getInsetsController(window, window.decorView).show(WindowInsetsCompat.Type.systemBars())
            }
        }
    }

    // תיקון 1: כפתור אחורה במצב גלריה חוזר לרשימות
    BackHandler {
        if (isGalleryMode) {
            // במצב גלריה - חזרה למסך הרשימות
            (context as? Activity)?.finish()
        } else {
            // במצב תמונה - חזרה לגלריה
            viewModel.toggleGalleryMode()
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        if (isGalleryMode) {
            GalleryGridView(
                items = galleryItems,
                currentUri = currentMedia,
                columns = gridColumns,
                onItemClick = { uri ->
                    viewModel.jumpToItem(uri)
                    viewModel.toggleGalleryMode() // עובר למצב תמונה אחרי בחירה
                },
                onColumnsChange = { viewModel.setGridColumns(it) },
                onBackToHome = { (context as? Activity)?.finish() }
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GalleryGridView(
    items: List<Uri>,
    currentUri: Uri?,
    columns: Int,
    onItemClick: (Uri) -> Unit,
    onColumnsChange: (Int) -> Unit,
    onBackToHome: () -> Unit
) {
    android.util.Log.d("GalleryGridView", "Rendering with ${items.size} items, isGalleryMode=true")

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Gallery", style = MaterialTheme.typography.headlineSmall) },
                navigationIcon = {
                    IconButton(onClick = onBackToHome) {
                        Icon(Icons.Default.ArrowBack, "Back to playlists")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(MaterialTheme.colorScheme.background)
        ) {
            // סליידר גודל
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.GridView,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(
                        "Grid size",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f)
                    )
                    Slider(
                        value = columns.toFloat(),
                        onValueChange = { onColumnsChange(it.toInt()) },
                        valueRange = 2f..8f,
                        steps = 5,
                        modifier = Modifier.width(150.dp),
                        colors = SliderDefaults.colors(
                            thumbColor = MaterialTheme.colorScheme.primary,
                            activeTrackColor = MaterialTheme.colorScheme.primary
                        )
                    )
                    Text(
                        columns.toString(),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }
            }

            if (items.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.ImageNotSupported,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(16.dp))
                        Text(
                            "No media found",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            "Check your playlist items",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(columns),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(items) { uri ->
                        val isSelected = uri == currentUri
                        val isVideo = isVideo(uri.toString())
                        val context = LocalContext.current

                        Card(
                            modifier = Modifier
                                .aspectRatio(1f)
                                .clickable { onItemClick(uri) },
                            elevation = CardDefaults.cardElevation(
                                defaultElevation = if (isSelected) 8.dp else 2.dp
                            ),
                            border = if (isSelected) BorderStroke(3.dp, MaterialTheme.colorScheme.primary) else null
                        ) {
                            Box(modifier = Modifier.fillMaxSize()) {
                                AsyncImage(
                                    model = ImageRequest.Builder(context)
                                        .data(uri)
                                        .decoderFactory(VideoFrameDecoder.Factory())
                                        .crossfade(true)
                                        .build(),
                                    contentDescription = null,
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop,
                                    error = ColorPainter(MaterialTheme.colorScheme.errorContainer)
                                )
                                if (isVideo) {
                                    Surface(
                                        modifier = Modifier
                                            .align(Alignment.Center)
                                            .size(48.dp),
                                        shape = MaterialTheme.shapes.large,
                                        color = Color.Black.copy(0.6f)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.PlayCircle,
                                            contentDescription = null,
                                            tint = Color.White,
                                            modifier = Modifier.padding(8.dp)
                                        )
                                    }
                                }
                                if (isSelected) {
                                    Surface(
                                        modifier = Modifier
                                            .align(Alignment.TopEnd)
                                            .padding(8.dp)
                                            .size(24.dp),
                                        shape = MaterialTheme.shapes.small,
                                        color = MaterialTheme.colorScheme.primary
                                    ) {
                                        Icon(
                                            Icons.Default.Check,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onPrimary,
                                            modifier = Modifier.padding(4.dp)
                                        )
                                    }
                                }
                            }
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

    LaunchedEffect(currentMedia) {
        scale = 1f
        offset = Offset.Zero
    }

    Box(modifier = Modifier.fillMaxSize()) {
        currentMedia?.let { uri ->
            val isVideoFile = isVideo(uri.toString())

            if (isVideoFile) {
                VideoPlayer(
                    uri = uri,
                    onNext = onNext,
                    onPrev = onPrev,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clipToBounds()
                        .pointerInput(Unit) {
                            detectTransformGestures { _, pan, zoom, _ ->
                                val newScale = (scale * zoom).coerceIn(1f, 4f)
                                scale = newScale
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
                        .pointerInput(navigationMode, scale) {
                            if (navigationMode == "SWIPE" && scale == 1f) {
                                detectHorizontalDragGestures { _, dragAmount ->
                                    // תיקון: משמאל לימין = קדימה, מימין לשמאל = אחורה
                                    if (dragAmount > 50) onNext()
                                    else if (dragAmount < -50) onPrev()
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

                    if (scale == 1f) {
                        Row(modifier = Modifier.fillMaxSize()) {
                            Box(modifier = Modifier.weight(0.3f).fillMaxHeight().pointerInput(navigationMode) {
                                detectTapGestures(
                                    onTap = { if (navigationMode == "TAP") onPrev() },
                                    onLongPress = { showActionMenu = true }
                                )
                            })
                            Box(modifier = Modifier.weight(0.4f).fillMaxHeight().pointerInput(Unit) {
                                detectTapGestures(onLongPress = { showActionMenu = true })
                            })
                            Box(modifier = Modifier.weight(0.3f).fillMaxHeight().pointerInput(navigationMode) {
                                detectTapGestures(
                                    onTap = { if (navigationMode == "TAP") onNext() },
                                    onLongPress = { showActionMenu = true }
                                )
                            })
                        }
                    }

                    if (showActionMenu) {
                        ActionMenuDialog(
                            uri = uri,
                            isVideo = false,
                            onDismiss = { showActionMenu = false }
                        )
                    }
                }
            }

            FloatingActionButton(
                onClick = onToggleGallery,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(16.dp),
                containerColor = MaterialTheme.colorScheme.primaryContainer
            ) {
                Icon(
                    Icons.Default.GridView,
                    "Gallery",
                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }
    }
}

fun isVideo(path: String): Boolean {
    val lowercasePath = path.lowercase(Locale.getDefault())
    if (lowercasePath.endsWith(".mp4") || lowercasePath.endsWith(".mkv") ||
        lowercasePath.endsWith(".mov") || lowercasePath.endsWith(".avi") ||
        lowercasePath.endsWith(".webm") || lowercasePath.endsWith(".3gp")) return true
    val extension = MimeTypeMap.getFileExtensionFromUrl(path)
    return MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension)?.startsWith("video") == true
}
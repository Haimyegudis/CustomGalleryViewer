// PlayerScreen.kt
package com.example.customgalleryviewer.presentation

import android.app.Activity
import android.net.Uri
import android.view.WindowManager
import android.webkit.MimeTypeMap
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale

@Composable
fun PlayerScreen(
    playlistId: Long,
    onBackToHome: () -> Unit = {},
    viewModel: PlayerViewModel = hiltViewModel(),
    settingsViewModel: SettingsViewModel = hiltViewModel()
) {
    val currentMedia by viewModel.currentMedia.collectAsState()
    val isGalleryMode by viewModel.isGalleryMode.collectAsState()
    val filteredGalleryItems by viewModel.filteredGalleryItems.collectAsState()
    val gridColumns by viewModel.gridColumns.collectAsState()
    val navigationMode by settingsViewModel.navigationMode.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val context = LocalContext.current

    BackHandler(enabled = true) {
        if (!isGalleryMode) {
            viewModel.setGalleryMode(true)
        } else {
            onBackToHome()
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
                items = filteredGalleryItems,
                currentUri = currentMedia,
                columns = gridColumns,
                searchQuery = searchQuery,
                isLoading = isLoading,
                onItemClick = { uri -> viewModel.jumpToItem(uri) },
                onColumnsChange = { viewModel.setGridColumns(it) },
                onSearchQueryChange = { viewModel.setSearchQuery(it) },
                onBackToHome = onBackToHome
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
    searchQuery: String,
    isLoading: Boolean,
    onItemClick: (Uri) -> Unit,
    onColumnsChange: (Int) -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onBackToHome: () -> Unit
) {
    var showSearch by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        TopAppBar(
            title = {
                Text(
                    if (isLoading) "Loading... ${items.size} files" else "Gallery - ${items.size} files",
                    color = Color.White
                )
            },
            navigationIcon = {
                IconButton(onClick = onBackToHome) {
                    Icon(Icons.Default.ArrowBack, "Back", tint = Color.White)
                }
            },
            actions = {
                IconButton(onClick = { showSearch = !showSearch }) {
                    Icon(Icons.Default.Search, "Search", tint = Color.White)
                }
                Text("Size", color = Color.White, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(end = 8.dp))
                Slider(
                    value = columns.toFloat(),
                    onValueChange = { onColumnsChange(it.toInt()) },
                    valueRange = 2f..8f,
                    steps = 5,
                    modifier = Modifier.width(100.dp),
                    colors = SliderDefaults.colors(
                        thumbColor = MaterialTheme.colorScheme.primary,
                        activeTrackColor = MaterialTheme.colorScheme.primary,
                        inactiveTrackColor = Color.White.copy(0.5f)
                    )
                )
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Black.copy(0.8f))
        )

        if (showSearch) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = onSearchQueryChange,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                placeholder = { Text("Search files...", color = Color.Gray) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { onSearchQueryChange("") }) {
                            Icon(Icons.Default.Clear, "Clear", tint = Color.White)
                        }
                    }
                },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = Color.Gray
                ),
                singleLine = true
            )
        }

        if (items.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    if (isLoading) {
                        CircularProgressIndicator(color = Color.White)
                        Spacer(Modifier.height(16.dp))
                        Text("Scanning files...", color = Color.White)
                    } else {
                        Icon(
                            Icons.Default.PhotoLibrary,
                            null,
                            modifier = Modifier.size(64.dp),
                            tint = Color.White.copy(0.6f)
                        )
                        Spacer(Modifier.height(16.dp))
                        Text("No files found", color = Color.White)
                    }
                }
            }
        } else {
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
                            .border(
                                if (isSelected) 3.dp else 0.dp,
                                if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent
                            )
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
                            Icon(
                                imageVector = Icons.Default.PlayCircle,
                                contentDescription = null,
                                tint = Color.White.copy(0.9f),
                                modifier = Modifier.align(Alignment.Center).size(24.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

// PlayerScreen.kt - PlayerContentView function only
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
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

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
                    navigationMode = navigationMode,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                var isDraggingBrightness by remember { mutableStateOf(false) }
                var startBrightness by remember { mutableFloatStateOf(0f) }
                var currentBrightnessLevel by remember { mutableFloatStateOf(0.5f) }
                var showBrightnessIndicator by remember { mutableStateOf(false) }
                var totalDragDistanceY by remember { mutableFloatStateOf(0f) }

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clipToBounds()
                        .pointerInput(scale) {
                            detectTransformGestures { _, pan, zoom, _ ->
                                if (scale > 1f || zoom != 1f) {
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
                        }
                        .pointerInput(scale) {
                            detectTapGestures(
                                onDoubleTap = { tapOffset ->
                                    val width = size.width
                                    val height = size.height
                                    val centerXStart = width * 0.3f
                                    val centerXEnd = width * 0.7f
                                    val centerYStart = height * 0.3f
                                    val centerYEnd = height * 0.7f

                                    val isInCenter = tapOffset.x in centerXStart..centerXEnd &&
                                            tapOffset.y in centerYStart..centerYEnd

                                    if (isInCenter) {
                                        if (scale == 1f) {
                                            scale = 2.5f
                                        } else {
                                            scale = 1f
                                            offset = Offset.Zero
                                        }
                                    }
                                },
                                onLongPress = { showActionMenu = true }
                            )
                        }
                        .pointerInput(scale) {
                            detectDragGestures(
                                onDragStart = { dragOffset ->
                                    if (scale == 1f) {
                                        val isRightSide = dragOffset.x > size.width / 2
                                        if (isRightSide) {
                                            isDraggingBrightness = true
                                            val activity = context as? Activity
                                            if (activity != null) {
                                                startBrightness = getCurrentBrightness(activity)
                                                currentBrightnessLevel = startBrightness
                                            }
                                            totalDragDistanceY = 0f
                                            showBrightnessIndicator = true
                                        }
                                    }
                                },
                                onDragEnd = {
                                    if (isDraggingBrightness) {
                                        scope.launch {
                                            delay(500)
                                            showBrightnessIndicator = false
                                            isDraggingBrightness = false
                                        }
                                    }
                                },
                                onDragCancel = {
                                    showBrightnessIndicator = false
                                    isDraggingBrightness = false
                                }
                            ) { _, dragAmount ->
                                if (scale == 1f) {
                                    if (isDraggingBrightness) {
                                        totalDragDistanceY += dragAmount.y
                                        val sensitivity = 2000f
                                        val deltaPercent = -totalDragDistanceY / sensitivity
                                        val newBrightness = (startBrightness + deltaPercent).coerceIn(0f, 1f)
                                        val activity = context as? Activity
                                        if (activity != null) {
                                            setAppBrightness(activity, newBrightness)
                                            currentBrightnessLevel = newBrightness
                                        }
                                    } else {
                                        val (dx, _) = dragAmount
                                        if (navigationMode == "SWIPE") {
                                            if (dx < -50) onNext() else if (dx > 50) onPrev()
                                        }
                                    }
                                } else {
                                    val (dx, _) = dragAmount
                                    if (dx < -100) onNext() else if (dx > 100) onPrev()
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

                    if (navigationMode == "TAP") {
                        Row(modifier = Modifier.fillMaxSize()) {
                            Box(
                                modifier = Modifier
                                    .weight(0.3f)
                                    .fillMaxHeight()
                                    .clickable(
                                        interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                                        indication = null
                                    ) { onPrev() }
                            )
                            Spacer(modifier = Modifier.weight(0.4f))
                            Box(
                                modifier = Modifier
                                    .weight(0.3f)
                                    .fillMaxHeight()
                                    .clickable(
                                        interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                                        indication = null
                                    ) { onNext() }
                            )
                        }
                    }

                    if (showBrightnessIndicator) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.CenterEnd)
                                .padding(end = 30.dp)
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Default.Brightness6, null, tint = Color.Yellow, modifier = Modifier.size(32.dp))
                                LinearProgressIndicator(
                                    progress = { currentBrightnessLevel },
                                    modifier = Modifier.height(150.dp).width(12.dp),
                                    color = Color.Yellow
                                )
                            }
                        }
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

            IconButton(
                onClick = onToggleGallery,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(16.dp)
                    .background(Color.Black.copy(0.5f), MaterialTheme.shapes.small)
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

fun getCurrentBrightness(activity: Activity): Float {
    val lp = activity.window.attributes
    return if (lp.screenBrightness < 0) 0.5f else lp.screenBrightness
}

fun setAppBrightness(activity: Activity, percent: Float) {
    val lp = activity.window.attributes
    lp.screenBrightness = percent.coerceIn(0.01f, 1f)
    activity.window.attributes = lp
}
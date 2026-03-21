// PlayerScreen.kt
package com.example.customgalleryviewer.presentation

import android.app.Activity
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.MediaStore
import android.view.WindowManager
import android.webkit.MimeTypeMap
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items as lazyListItems
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Size
import androidx.core.content.FileProvider
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import coil.decode.VideoFrameDecoder
import coil.request.ImageRequest
import com.example.customgalleryviewer.data.MediaFilterType
import com.example.customgalleryviewer.presentation.components.ActionMenuDialog
import com.example.customgalleryviewer.presentation.components.VideoPlayer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale
import kotlin.math.abs
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import com.example.customgalleryviewer.presentation.components.FileOperation
import com.example.customgalleryviewer.presentation.components.FolderPickerDialog
import com.example.customgalleryviewer.presentation.components.MultiFolderPickerDialog

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
    val mediaFilter by viewModel.mediaFilter.collectAsState()
    val isShuffleOn by viewModel.isShuffleOn.collectAsState()
    val isRepeatListOn by viewModel.isRepeatListOn.collectAsState()
    val isBrowseMode by viewModel.isBrowseMode.collectAsState()
    val browseItems by viewModel.browseItems.collectAsState()
    val folderStack by viewModel.folderStack.collectAsState()
    val playlistFolders by viewModel.playlistFolders.collectAsState()
    val favoriteUris by viewModel.favoriteUris.collectAsState()
    val watchPositions by viewModel.watchPositions.collectAsState()
    val context = LocalContext.current

    // Hoist scroll position to survive gallery/player toggle
    val scrollIndex = remember { mutableIntStateOf(0) }
    val scrollOffset = remember { mutableIntStateOf(0) }

    BackHandler(enabled = true) {
        if (!isGalleryMode) {
            viewModel.setGalleryMode(true)
        } else if (isBrowseMode) {
            viewModel.navigateBackInBrowse()
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

    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        if (isGalleryMode) {
            if (isBrowseMode) {
                FolderBrowseView(
                    items = browseItems,
                    folderStack = folderStack,
                    columns = gridColumns,
                    mediaFilter = mediaFilter,
                    onFolderClick = { uri, name -> viewModel.openSubfolder(uri, name) },
                    onFileClick = { uri -> viewModel.jumpToItem(uri) },
                    onBack = { viewModel.navigateBackInBrowse() },
                    onColumnsChange = { viewModel.setGridColumns(it) },
                    onMediaFilterChange = { viewModel.setMediaFilter(it) },
                    getFolderCover = { viewModel.getFolderCover(it) },
                    onSetFolderCover = { folder, cover -> viewModel.setFolderCover(folder, cover) },
                    getMediaFilesInFolder = { viewModel.getMediaFilesInFolder(it) }
                )
            } else {
                GalleryGridView(
                    items = filteredGalleryItems,
                    currentUri = currentMedia,
                    columns = gridColumns,
                    searchQuery = searchQuery,
                    isLoading = isLoading,
                    mediaFilter = mediaFilter,
                    playlistFolders = playlistFolders,
                    favoriteUris = favoriteUris,
                    watchPositions = watchPositions,
                    onItemClick = { uri -> viewModel.jumpToItem(uri) },
                    onColumnsChange = { viewModel.setGridColumns(it) },
                    onSearchQueryChange = { viewModel.setSearchQuery(it) },
                    onMediaFilterChange = { viewModel.setMediaFilter(it) },
                    onBackToHome = onBackToHome,
                    onBrowseFolder = { uri, name -> viewModel.enterBrowseMode(uri, name) },
                    onToggleFavorite = { uri -> viewModel.toggleFavorite(uri) },
                    onDeleteItem = { uri -> viewModel.removeFromList(uri) },
                    initialScrollIndex = scrollIndex.intValue,
                    initialScrollOffset = scrollOffset.intValue,
                    onScrollChanged = { idx, off -> scrollIndex.intValue = idx; scrollOffset.intValue = off }
                )
            }
        } else {
            PlayerContentView(
                currentMedia = currentMedia,
                navigationMode = navigationMode,
                isShuffleOn = isShuffleOn,
                isRepeatListOn = isRepeatListOn,
                onNext = { viewModel.onNext() },
                onPrev = { viewModel.onPrevious() },
                onToggleGallery = { viewModel.toggleGalleryMode() },
                onToggleShuffle = { viewModel.toggleShuffle() },
                onToggleRepeatList = { viewModel.toggleRepeatList() },
                onSaveWatchPosition = { uri, pos, dur -> viewModel.saveWatchPosition(uri, pos, dur) },
                getWatchPosition = { uri -> viewModel.getWatchPosition(uri) },
                onToggleFavorite = { uri -> viewModel.toggleFavorite(uri) },
                isFavoriteCheck = { uri -> viewModel.isFavorite(uri) }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun GalleryGridView(
    items: List<Uri>,
    currentUri: Uri?,
    columns: Int,
    searchQuery: String,
    isLoading: Boolean,
    mediaFilter: MediaFilterType,
    playlistFolders: List<Pair<Uri, String>> = emptyList(),
    favoriteUris: Set<String> = emptySet(),
    watchPositions: Map<String, com.example.customgalleryviewer.data.WatchPositionEntity> = emptyMap(),
    onItemClick: (Uri) -> Unit,
    onColumnsChange: (Int) -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onMediaFilterChange: (MediaFilterType) -> Unit,
    onBackToHome: () -> Unit,
    onBrowseFolder: (Uri, String) -> Unit = { _, _ -> },
    onToggleFavorite: (Uri) -> Unit = {},
    onDeleteItem: (Uri) -> Unit = {},
    initialScrollIndex: Int = 0,
    initialScrollOffset: Int = 0,
    onScrollChanged: (Int, Int) -> Unit = { _, _ -> }
) {
    var showSearch by remember { mutableStateOf(false) }
    var showSortMenu by remember { mutableStateOf(false) }
    var localSort by remember { mutableStateOf("default") }
    val gridState = rememberLazyGridState(initialFirstVisibleItemIndex = initialScrollIndex, initialFirstVisibleItemScrollOffset = initialScrollOffset)

    // Report scroll position
    LaunchedEffect(Unit) {
        snapshotFlow { gridState.firstVisibleItemIndex to gridState.firstVisibleItemScrollOffset }
            .collect { (idx, off) -> onScrollChanged(idx, off) }
    }
    val listState = rememberLazyListState()

    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // View mode: "grid" or "list"
    var viewMode by remember { mutableStateOf("grid") }

    // Context menu state
    var contextMenuUri by remember { mutableStateOf<Uri?>(null) }

    // Rename dialog state
    var renameUri by remember { mutableStateOf<Uri?>(null) }
    var renameText by remember { mutableStateOf("") }

    // Multi-select state
    var isSelectionMode by remember { mutableStateOf(false) }
    var selectedItems by remember { mutableStateOf<Set<Uri>>(emptySet()) }
    var showSelectionMoreMenu by remember { mutableStateOf(false) }

    // Lazy duration cache - loads per visible item, never blocks UI
    val durationCache = remember { androidx.compose.runtime.mutableStateMapOf<Uri, Long>() }

    // Local sort for this gallery view, with favorites pinned to top
    val sortedItems = remember(items, localSort, if (localSort == "length") durationCache.size else 0, favoriteUris) {
        val sorted = when (localSort) {
            "name" -> items.sortedBy { it.lastPathSegment?.lowercase() ?: "" }
            "name_desc" -> items.sortedByDescending { it.lastPathSegment?.lowercase() ?: "" }
            "date" -> items.sortedByDescending {
                try { java.io.File(it.path ?: "").lastModified() } catch (_: Exception) { 0L }
            }
            "size" -> items.sortedByDescending {
                try { java.io.File(it.path ?: "").length() } catch (_: Exception) { 0L }
            }
            "length" -> items.sortedByDescending { durationCache[it] ?: 0L }
            else -> items
        }
        if (favoriteUris.isNotEmpty()) {
            val (favs, rest) = sorted.partition { favoriteUris.contains(it.toString()) }
            favs + rest
        } else sorted
    }

    Column(modifier = Modifier.fillMaxSize()) {
        if (isSelectionMode) {
            // Selection mode top bar
            TopAppBar(
                title = {
                    Text("${selectedItems.size} selected", fontWeight = FontWeight.SemiBold)
                },
                navigationIcon = {
                    TextButton(onClick = {
                        isSelectionMode = false
                        selectedItems = emptySet()
                    }) {
                        Text("Cancel", color = MaterialTheme.colorScheme.primary)
                    }
                },
                actions = {
                    TextButton(onClick = {
                        selectedItems = sortedItems.toSet()
                    }) {
                        Text("Select All", color = MaterialTheme.colorScheme.primary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        } else {
        // Clean top bar
        TopAppBar(
            title = {},
            navigationIcon = {
                TextButton(onClick = onBackToHome) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack, null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text("Library", color = MaterialTheme.colorScheme.primary)
                }
            },
            actions = {
                Box {
                    IconButton(onClick = { showSortMenu = true }) {
                        Icon(
                            Icons.Default.Sort, "Sort",
                            tint = if (localSort != "default") MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurface.copy(0.6f)
                        )
                    }
                    DropdownMenu(
                        expanded = showSortMenu,
                        onDismissRequest = { showSortMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Default", fontWeight = if (localSort == "default") FontWeight.Bold else FontWeight.Normal) },
                            onClick = { localSort = "default"; showSortMenu = false }
                        )
                        DropdownMenuItem(
                            text = { Text("Name A-Z", fontWeight = if (localSort == "name") FontWeight.Bold else FontWeight.Normal) },
                            onClick = { localSort = "name"; showSortMenu = false }
                        )
                        DropdownMenuItem(
                            text = { Text("Name Z-A", fontWeight = if (localSort == "name_desc") FontWeight.Bold else FontWeight.Normal) },
                            onClick = { localSort = "name_desc"; showSortMenu = false }
                        )
                        DropdownMenuItem(
                            text = { Text("Date (Newest)", fontWeight = if (localSort == "date") FontWeight.Bold else FontWeight.Normal) },
                            onClick = { localSort = "date"; showSortMenu = false }
                        )
                        DropdownMenuItem(
                            text = { Text("Size (Largest)", fontWeight = if (localSort == "size") FontWeight.Bold else FontWeight.Normal) },
                            onClick = { localSort = "size"; showSortMenu = false }
                        )
                        DropdownMenuItem(
                            text = { Text("Length (Longest)", fontWeight = if (localSort == "length") FontWeight.Bold else FontWeight.Normal) },
                            onClick = { localSort = "length"; showSortMenu = false }
                        )
                    }
                }
                IconButton(onClick = { showSearch = !showSearch }) {
                    Icon(
                        Icons.Default.Search,
                        "Search",
                        tint = if (showSearch) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurface.copy(0.6f)
                    )
                }
                // View mode toggle
                IconButton(onClick = { viewMode = if (viewMode == "grid") "list" else "grid" }) {
                    Icon(
                        if (viewMode == "grid") Icons.Default.ViewList else Icons.Default.GridView,
                        "Toggle view",
                        tint = MaterialTheme.colorScheme.onSurface.copy(0.6f)
                    )
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.background
            )
        )
        } // end else (not selection mode)

        // Title + count
        Column(modifier = Modifier.padding(horizontal = 20.dp)) {
            Text(
                if (isLoading) "Loading..." else "Gallery",
                fontSize = 34.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                "${sortedItems.size} files",
                fontSize = 15.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp, bottom = 12.dp)
            )
        }

        // Segmented filter + grid size
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Segmented control for filter
            SingleChoiceSegmentedButtonRow(modifier = Modifier.weight(1f)) {
                val options = listOf(
                    "Mix" to MediaFilterType.MIXED,
                    "Photos" to MediaFilterType.PHOTOS_ONLY,
                    "Videos" to MediaFilterType.VIDEO_ONLY
                )
                options.forEachIndexed { index, (label, type) ->
                    SegmentedButton(
                        selected = mediaFilter == type,
                        onClick = { onMediaFilterChange(type) },
                        shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
                        colors = SegmentedButtonDefaults.colors(
                            activeContainerColor = MaterialTheme.colorScheme.primary.copy(0.15f),
                            activeContentColor = MaterialTheme.colorScheme.primary,
                            inactiveContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                            inactiveContentColor = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    ) {
                        Text(label, fontSize = 13.sp)
                    }
                }
            }

            Spacer(Modifier.width(12.dp))

            // Grid size control
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.GridView, null,
                    tint = MaterialTheme.colorScheme.onSurface.copy(0.4f),
                    modifier = Modifier.size(16.dp)
                )
                Slider(
                    value = columns.toFloat(),
                    onValueChange = { onColumnsChange(it.toInt()) },
                    valueRange = 2f..8f,
                    steps = 5,
                    modifier = Modifier.width(72.dp),
                    colors = SliderDefaults.colors(
                        thumbColor = MaterialTheme.colorScheme.primary,
                        activeTrackColor = MaterialTheme.colorScheme.primary,
                        inactiveTrackColor = MaterialTheme.colorScheme.onSurface.copy(0.1f)
                    )
                )
            }
        }

        // Search bar
        AnimatedVisibility(visible = showSearch) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = onSearchQueryChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                placeholder = { Text("Search files...", color = MaterialTheme.colorScheme.onSurface.copy(0.3f)) },
                leadingIcon = {
                    Icon(Icons.Default.Search, null, tint = MaterialTheme.colorScheme.primary)
                },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { onSearchQueryChange("") }) {
                            Icon(Icons.Default.Clear, "Clear", tint = MaterialTheme.colorScheme.onSurface.copy(0.5f))
                        }
                    }
                },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = MaterialTheme.colorScheme.onSurface,
                    unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                    cursorColor = MaterialTheme.colorScheme.primary,
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant
                ),
                singleLine = true,
                shape = RoundedCornerShape(12.dp)
            )
        }

        if (sortedItems.isEmpty() && playlistFolders.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            color = MaterialTheme.colorScheme.primary,
                            strokeWidth = 3.dp
                        )
                        Spacer(Modifier.height(16.dp))
                        Text(
                            "Scanning files...",
                            color = MaterialTheme.colorScheme.onSurface.copy(0.5f)
                        )
                    } else {
                        Icon(
                            Icons.Default.PhotoLibrary, null,
                            modifier = Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.onSurface.copy(0.15f)
                        )
                        Spacer(Modifier.height(12.dp))
                        Text(
                            "No files found",
                            color = MaterialTheme.colorScheme.onSurface.copy(0.3f),
                            fontSize = 15.sp
                        )
                    }
                }
            }
        } else {
            // Show folder browse chips if there are folders
            if (false && playlistFolders.isNotEmpty()) { // Hidden — show files only, no folder navigation
                Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)) {
                    Text(
                        "Folders",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    val folderGridState = rememberLazyGridState()
                    LazyVerticalGrid(
                        state = folderGridState,
                        columns = GridCells.Fixed(2),
                        modifier = Modifier.heightIn(max = 200.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(playlistFolders.size) { index ->
                            val (folderUri, folderName) = playlistFolders[index]
                            Surface(
                                onClick = { onBrowseFolder(folderUri, folderName) },
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        Icons.Default.Folder,
                                        null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(24.dp)
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        folderName.substringAfterLast('/'),
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1
                                    )
                                }
                            }
                        }
                    }
                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 8.dp),
                        color = MaterialTheme.colorScheme.outline.copy(0.2f)
                    )
                }
            }

            if (viewMode == "list") {
                // List view
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize().weight(1f),
                    contentPadding = PaddingValues(2.dp)
                ) {
                    lazyListItems(sortedItems) { uri ->
                        val isVideoItem = isVideo(uri.toString())
                        val itemContext = LocalContext.current
                        val fileName = uri.lastPathSegment?.substringAfterLast('/') ?: ""
                        val isFav = favoriteUris.contains(uri.toString())
                        val isInSelection = selectedItems.contains(uri)

                        // Lazy load duration for visible video items
                        if (isVideoItem && uri !in durationCache) {
                            LaunchedEffect(uri) {
                                withContext(Dispatchers.IO) {
                                    durationCache[uri] = getVideoDurationMs(itemContext, uri)
                                }
                            }
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .combinedClickable(
                                    onClick = {
                                        if (isSelectionMode) {
                                            selectedItems = if (isInSelection) selectedItems - uri else selectedItems + uri
                                        } else {
                                            onItemClick(uri)
                                        }
                                    },
                                    onLongClick = {
                                        if (!isSelectionMode) {
                                            contextMenuUri = uri
                                        }
                                    }
                                )
                                .background(
                                    if (isInSelection) MaterialTheme.colorScheme.primary.copy(0.1f)
                                    else Color.Transparent
                                )
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (isSelectionMode) {
                                Checkbox(
                                    checked = isInSelection,
                                    onCheckedChange = {
                                        selectedItems = if (isInSelection) selectedItems - uri else selectedItems + uri
                                    },
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(Modifier.width(8.dp))
                            }
                            AsyncImage(
                                model = ImageRequest.Builder(itemContext)
                                    .data(uri)
                                    .decoderFactory(VideoFrameDecoder.Factory())
                                    .crossfade(true)
                                    .size(128)
                                    .build(),
                                contentDescription = null,
                                modifier = Modifier.size(48.dp).clip(RoundedCornerShape(4.dp)),
                                contentScale = ContentScale.Crop,
                                error = ColorPainter(MaterialTheme.colorScheme.surfaceVariant)
                            )
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    fileName,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    fontSize = 14.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                if (isVideoItem) {
                                    val durationMs = durationCache[uri] ?: 0L
                                    if (durationMs > 0) {
                                        Text(
                                            formatDuration(durationMs),
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            fontSize = 12.sp
                                        )
                                    }
                                }
                            }
                            if (isFav) {
                                Icon(
                                    Icons.Default.Star, null,
                                    tint = Color(0xFFFFD700),
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                            if (isVideoItem) {
                                Spacer(Modifier.width(4.dp))
                                Icon(
                                    Icons.Default.PlayArrow, null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
            } else {
            // Apple Photos-style edge-to-edge grid with scrollbar
            Box(modifier = Modifier.fillMaxSize().weight(1f)) {
            LazyVerticalGrid(
                state = gridState,
                columns = GridCells.Fixed(columns),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(2.dp),
                horizontalArrangement = Arrangement.spacedBy(1.5.dp),
                verticalArrangement = Arrangement.spacedBy(1.5.dp)
            ) {
                items(sortedItems) { uri ->
                    val isCurrent = uri == currentUri
                    val isVideoItem = isVideo(uri.toString())
                    val itemContext = LocalContext.current
                    val fileName = uri.lastPathSegment?.substringAfterLast('/') ?: ""
                    val isFav = favoriteUris.contains(uri.toString())
                    val isInSelection = selectedItems.contains(uri)

                    // Lazy load duration for visible video items
                    if (isVideoItem && uri !in durationCache) {
                        LaunchedEffect(uri) {
                            withContext(Dispatchers.IO) {
                                durationCache[uri] = getVideoDurationMs(itemContext, uri)
                            }
                        }
                    }

                    Box(
                        modifier = Modifier
                            .aspectRatio(1f)
                            .clip(RoundedCornerShape(2.dp))
                            .combinedClickable(
                                onClick = {
                                    if (isSelectionMode) {
                                        selectedItems = if (isInSelection) selectedItems - uri else selectedItems + uri
                                    } else {
                                        onItemClick(uri)
                                    }
                                },
                                onLongClick = {
                                    if (!isSelectionMode) {
                                        contextMenuUri = uri
                                    }
                                }
                            )
                    ) {
                        AsyncImage(
                            model = ImageRequest.Builder(itemContext)
                                .data(uri)
                                .decoderFactory(VideoFrameDecoder.Factory())
                                .crossfade(true)
                                .size(256)
                                .build(),
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop,
                            error = ColorPainter(MaterialTheme.colorScheme.surfaceVariant)
                        )

                        // Bottom gradient with file name
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .fillMaxWidth()
                                .background(
                                    Brush.verticalGradient(
                                        colors = listOf(Color.Transparent, Color.Black.copy(0.75f))
                                    )
                                )
                                .padding(horizontal = 4.dp, vertical = 3.dp)
                        ) {
                            Column {
                                Text(
                                    fileName,
                                    color = Color.White,
                                    fontSize = 8.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    lineHeight = 10.sp
                                )
                                if (isVideoItem) {
                                    val durationMs = durationCache[uri] ?: 0L
                                    if (durationMs > 0) {
                                        Text(
                                            formatDuration(durationMs),
                                            color = Color.White.copy(0.7f),
                                            fontSize = 8.sp,
                                            lineHeight = 10.sp
                                        )
                                    }
                                }
                            }
                        }

                        // Video play icon
                        if (isVideoItem) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(3.dp)
                                    .size(18.dp)
                                    .background(Color.Black.copy(0.5f), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Default.PlayArrow, null,
                                    tint = Color.White,
                                    modifier = Modifier.size(12.dp)
                                )
                            }
                        }

                        // Watch progress bar for videos
                        if (isVideoItem) {
                            val wp = watchPositions[uri.toString()]
                            if (wp != null && wp.duration > 0) {
                                val progress = (wp.position.toFloat() / wp.duration).coerceIn(0f, 1f)
                                if (progress > 0.02f && progress < 0.95f) {
                                    LinearProgressIndicator(
                                        progress = { progress },
                                        modifier = Modifier
                                            .align(Alignment.BottomCenter)
                                            .fillMaxWidth()
                                            .height(3.dp),
                                        color = Color(0xFF00E5FF),
                                        trackColor = Color.Black.copy(0.5f)
                                    )
                                }
                            }
                        }

                        // Favorite heart overlay (always visible, tappable)
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .size(32.dp)
                                .pointerInput(uri) {
                                    detectTapGestures { onToggleFavorite(uri) }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                if (isFav) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                null,
                                tint = if (isFav) Color(0xFFFF6B6B) else Color.White.copy(0.4f),
                                modifier = Modifier.size(16.dp)
                            )
                        }

                        // Selection checkbox overlay
                        if (isSelectionMode) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(2.dp)
                            ) {
                                Checkbox(
                                    checked = isInSelection,
                                    onCheckedChange = {
                                        selectedItems = if (isInSelection) selectedItems - uri else selectedItems + uri
                                    },
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }

                        // Current highlight
                        if (isCurrent) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .border(
                                        3.dp,
                                        MaterialTheme.colorScheme.primary,
                                        RoundedCornerShape(2.dp)
                                    )
                            )
                        }

                        // Selection highlight
                        if (isInSelection) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(MaterialTheme.colorScheme.primary.copy(0.2f))
                            )
                        }
                    }
                }
            }

            // Fast scrollbar
            FastScrollbar(
                gridState = gridState,
                itemCount = sortedItems.size,
                columns = columns,
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .fillMaxHeight()
                    .width(12.dp)
                    .padding(vertical = 8.dp)
            )
            } // end Box for grid + scrollbar
            } // end else (grid mode)

            // Selection mode bottom bar
            if (isSelectionMode && selectedItems.isNotEmpty()) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    tonalElevation = 4.dp
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box {
                            IconButton(onClick = { showSelectionMoreMenu = true }) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(Icons.Default.MoreVert, "More")
                                    Text("More", fontSize = 10.sp)
                                }
                            }
                            var multiSelectOp by remember { mutableStateOf<com.example.customgalleryviewer.presentation.components.FileOperation?>(null) }

                            if (multiSelectOp != null) {
                                com.example.customgalleryviewer.presentation.components.MultiFolderPickerDialog(
                                    uris = selectedItems.toList(),
                                    operation = multiSelectOp!!,
                                    onDismiss = { multiSelectOp = null },
                                    onComplete = {
                                        multiSelectOp = null
                                        selectedItems = emptySet()
                                        isSelectionMode = false
                                    }
                                )
                            }

                            DropdownMenu(
                                expanded = showSelectionMoreMenu,
                                onDismissRequest = { showSelectionMoreMenu = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Copy to...") },
                                    leadingIcon = { Icon(Icons.Default.FileCopy, null) },
                                    onClick = {
                                        showSelectionMoreMenu = false
                                        multiSelectOp = com.example.customgalleryviewer.presentation.components.FileOperation.COPY
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Move to...") },
                                    leadingIcon = { Icon(Icons.Default.DriveFileMove, null) },
                                    onClick = {
                                        showSelectionMoreMenu = false
                                        multiSelectOp = com.example.customgalleryviewer.presentation.components.FileOperation.MOVE
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Share") },
                                    leadingIcon = { Icon(Icons.Default.Share, null) },
                                    onClick = {
                                        showSelectionMoreMenu = false
                                        val shareUris = ArrayList(selectedItems.map { uri ->
                                            if (uri.scheme == "file" && uri.path != null) {
                                                try {
                                                    androidx.core.content.FileProvider.getUriForFile(
                                                        context, "${context.packageName}.fileprovider", java.io.File(uri.path!!)
                                                    )
                                                } catch (_: Exception) { uri }
                                            } else uri
                                        })
                                        val shareIntent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                                            type = "*/*"
                                            putParcelableArrayListExtra(Intent.EXTRA_STREAM, shareUris)
                                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                        }
                                        context.startActivity(Intent.createChooser(shareIntent, "Share"))
                                    }
                                )
                            }
                        }
                        IconButton(onClick = {
                            selectedItems.forEach { uri -> onDeleteItem(uri) }
                            selectedItems = emptySet()
                            isSelectionMode = false
                        }) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Default.Delete, "Delete", tint = Color(0xFFFF5252))
                                Text("Delete", fontSize = 10.sp, color = Color(0xFFFF5252))
                            }
                        }
                    }
                }
            }
        }

        // Context menu dialog
        contextMenuUri?.let { menuUri ->
            val menuFileName = menuUri.lastPathSegment?.substringAfterLast('/') ?: ""
            val isFav = favoriteUris.contains(menuUri.toString())
            AlertDialog(
                onDismissRequest = { contextMenuUri = null },
                title = {
                    Text(menuFileName, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                },
                text = {
                    Column {
                        // Select
                        TextButton(
                            onClick = {
                                contextMenuUri = null
                                isSelectionMode = true
                                selectedItems = setOf(menuUri)
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.CheckBox, null, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(12.dp))
                            Text("Select", modifier = Modifier.weight(1f))
                        }
                        // Favorite
                        TextButton(
                            onClick = {
                                onToggleFavorite(menuUri)
                                contextMenuUri = null
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(
                                if (isFav) Icons.Default.Star else Icons.Default.StarBorder,
                                null,
                                tint = if (isFav) Color(0xFFFFD700) else MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(Modifier.width(12.dp))
                            Text(if (isFav) "Unfavorite" else "Favorite", modifier = Modifier.weight(1f))
                        }
                        // Rename
                        TextButton(
                            onClick = {
                                contextMenuUri = null
                                renameUri = menuUri
                                renameText = menuFileName
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Edit, null, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(12.dp))
                            Text("Rename", modifier = Modifier.weight(1f))
                        }
                        // Share
                        TextButton(
                            onClick = {
                                contextMenuUri = null
                                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                    type = "*/*"
                                    putExtra(Intent.EXTRA_STREAM, menuUri)
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                context.startActivity(Intent.createChooser(shareIntent, "Share"))
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Share, null, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(12.dp))
                            Text("Share", modifier = Modifier.weight(1f))
                        }
                        // Open With
                        TextButton(
                            onClick = {
                                contextMenuUri = null
                                val openIntent = Intent(Intent.ACTION_VIEW).apply {
                                    setDataAndType(menuUri, "*/*")
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                context.startActivity(Intent.createChooser(openIntent, "Open with"))
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.OpenInNew, null, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(12.dp))
                            Text("Open With", modifier = Modifier.weight(1f))
                        }
                        // Delete
                        TextButton(
                            onClick = {
                                val uri = contextMenuUri
                                contextMenuUri = null
                                if (uri != null) onDeleteItem(uri)
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Delete, null, tint = Color(0xFFFF5252), modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(12.dp))
                            Text("Delete", color = Color(0xFFFF5252), modifier = Modifier.weight(1f))
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { contextMenuUri = null }) {
                        Text("Cancel")
                    }
                },
                shape = RoundedCornerShape(20.dp)
            )
        }

        // Rename dialog
        renameUri?.let { rUri ->
            AlertDialog(
                onDismissRequest = { renameUri = null },
                title = { Text("Rename") },
                text = {
                    OutlinedTextField(
                        value = renameText,
                        onValueChange = { renameText = it },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                },
                confirmButton = {
                    TextButton(onClick = {
                        val oldFile = rUri.path?.let { File(it) }
                        if (oldFile != null && oldFile.exists()) {
                            val newFile = File(oldFile.parent, renameText)
                            val success = oldFile.renameTo(newFile)
                            if (success) {
                                Toast.makeText(context, "Renamed", Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(context, "Rename failed", Toast.LENGTH_SHORT).show()
                            }
                        }
                        renameUri = null
                    }) {
                        Text("Rename")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { renameUri = null }) {
                        Text("Cancel")
                    }
                },
                shape = RoundedCornerShape(20.dp)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FolderBrowseView(
    items: List<com.example.customgalleryviewer.data.GalleryItem>,
    folderStack: List<Pair<Uri, String>>,
    columns: Int,
    mediaFilter: MediaFilterType,
    onFolderClick: (Uri, String) -> Unit,
    onFileClick: (Uri) -> Unit,
    onBack: () -> Unit,
    onColumnsChange: (Int) -> Unit,
    onMediaFilterChange: (MediaFilterType) -> Unit,
    getFolderCover: (Uri) -> Uri? = { null },
    onSetFolderCover: (Uri, Uri) -> Unit = { _, _ -> },
    getMediaFilesInFolder: (Uri) -> List<Uri> = { emptyList() }
) {
    val gridState = rememberLazyGridState()
    val currentFolderName = folderStack.lastOrNull()?.second?.substringAfterLast('/') ?: "Browse"

    // Sort state per browse session
    var currentSort by remember { mutableStateOf("name") }
    var showSortMenu by remember { mutableStateOf(false) }

    val browseContext = LocalContext.current

    // Cache video durations asynchronously
    var browseDurationCache by remember { mutableStateOf<Map<Uri, Long>>(emptyMap()) }
    LaunchedEffect(items) {
        withContext(Dispatchers.IO) {
            val cache = mutableMapOf<Uri, Long>()
            items.filterIsInstance<com.example.customgalleryviewer.data.GalleryItem.MediaFile>().forEach {
                if (isVideo(it.uri.toString())) {
                    cache[it.uri] = getVideoDurationMs(browseContext, it.uri)
                }
            }
            browseDurationCache = cache
        }
    }

    // Sort the items
    val sortedItems = remember(items, currentSort, browseDurationCache) {
        val folders = items.filterIsInstance<com.example.customgalleryviewer.data.GalleryItem.Folder>()
        val files = items.filterIsInstance<com.example.customgalleryviewer.data.GalleryItem.MediaFile>()
        val sortedFiles = when (currentSort) {
            "name" -> files.sortedBy { it.uri.lastPathSegment?.lowercase() ?: "" }
            "name_desc" -> files.sortedByDescending { it.uri.lastPathSegment?.lowercase() ?: "" }
            "date" -> files.sortedByDescending {
                try { java.io.File(it.uri.path ?: "").lastModified() } catch (_: Exception) { 0L }
            }
            "size" -> files.sortedByDescending {
                try { java.io.File(it.uri.path ?: "").length() } catch (_: Exception) { 0L }
            }
            "length" -> files.sortedByDescending { browseDurationCache[it.uri] ?: 0L }
            else -> files
        }
        folders + sortedFiles
    }

    // Cover picker state
    var coverPickerFolder by remember { mutableStateOf<com.example.customgalleryviewer.data.GalleryItem.Folder?>(null) }
    var coverPickerFiles by remember { mutableStateOf<List<Uri>>(emptyList()) }

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = {},
            navigationIcon = {
                TextButton(onClick = { onBack() }) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack, null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        if (folderStack.size <= 1) "Gallery" else folderStack.dropLast(1).last().second.substringAfterLast('/'),
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            },
            actions = {
                Box {
                    IconButton(onClick = { showSortMenu = true }) {
                        Icon(
                            Icons.Default.Sort, "Sort",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    DropdownMenu(
                        expanded = showSortMenu,
                        onDismissRequest = { showSortMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Name A-Z", fontWeight = if (currentSort == "name") FontWeight.Bold else FontWeight.Normal) },
                            onClick = { currentSort = "name"; showSortMenu = false }
                        )
                        DropdownMenuItem(
                            text = { Text("Name Z-A", fontWeight = if (currentSort == "name_desc") FontWeight.Bold else FontWeight.Normal) },
                            onClick = { currentSort = "name_desc"; showSortMenu = false }
                        )
                        DropdownMenuItem(
                            text = { Text("Date (Newest)", fontWeight = if (currentSort == "date") FontWeight.Bold else FontWeight.Normal) },
                            onClick = { currentSort = "date"; showSortMenu = false }
                        )
                        DropdownMenuItem(
                            text = { Text("Size (Largest)", fontWeight = if (currentSort == "size") FontWeight.Bold else FontWeight.Normal) },
                            onClick = { currentSort = "size"; showSortMenu = false }
                        )
                        DropdownMenuItem(
                            text = { Text("Length (Longest)", fontWeight = if (currentSort == "length") FontWeight.Bold else FontWeight.Normal) },
                            onClick = { currentSort = "length"; showSortMenu = false }
                        )
                    }
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.background
            )
        )

        Column(modifier = Modifier.padding(horizontal = 20.dp)) {
            Text(
                currentFolderName,
                fontSize = 34.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
            val folderCount = sortedItems.count { it is com.example.customgalleryviewer.data.GalleryItem.Folder }
            val fileCount = sortedItems.count { it is com.example.customgalleryviewer.data.GalleryItem.MediaFile }
            Text(
                buildString {
                    if (folderCount > 0) append("$folderCount folders")
                    if (folderCount > 0 && fileCount > 0) append(" · ")
                    if (fileCount > 0) append("$fileCount files")
                },
                fontSize = 15.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp, bottom = 12.dp)
            )
        }

        // Filter + grid size
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SingleChoiceSegmentedButtonRow(modifier = Modifier.weight(1f)) {
                val options = listOf(
                    "Mix" to MediaFilterType.MIXED,
                    "Photos" to MediaFilterType.PHOTOS_ONLY,
                    "Videos" to MediaFilterType.VIDEO_ONLY
                )
                options.forEachIndexed { index, (label, type) ->
                    SegmentedButton(
                        selected = mediaFilter == type,
                        onClick = { onMediaFilterChange(type) },
                        shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
                        colors = SegmentedButtonDefaults.colors(
                            activeContainerColor = MaterialTheme.colorScheme.primary.copy(0.15f),
                            activeContentColor = MaterialTheme.colorScheme.primary,
                            inactiveContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                            inactiveContentColor = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    ) {
                        Text(label, fontSize = 13.sp)
                    }
                }
            }

            Spacer(Modifier.width(12.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.GridView, null,
                    tint = MaterialTheme.colorScheme.onSurface.copy(0.4f),
                    modifier = Modifier.size(16.dp)
                )
                Slider(
                    value = columns.toFloat(),
                    onValueChange = { onColumnsChange(it.toInt()) },
                    valueRange = 2f..8f,
                    steps = 5,
                    modifier = Modifier.width(72.dp),
                    colors = SliderDefaults.colors(
                        thumbColor = MaterialTheme.colorScheme.primary,
                        activeTrackColor = MaterialTheme.colorScheme.primary,
                        inactiveTrackColor = MaterialTheme.colorScheme.onSurface.copy(0.1f)
                    )
                )
            }
        }

        if (sortedItems.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.FolderOpen, null,
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.onSurface.copy(0.15f)
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "Empty folder",
                        color = MaterialTheme.colorScheme.onSurface.copy(0.3f),
                        fontSize = 15.sp
                    )
                }
            }
        } else {
            LazyVerticalGrid(
                state = gridState,
                columns = GridCells.Fixed(columns),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(2.dp),
                horizontalArrangement = Arrangement.spacedBy(1.5.dp),
                verticalArrangement = Arrangement.spacedBy(1.5.dp)
            ) {
                items(sortedItems.size) { index ->
                    when (val item = sortedItems[index]) {
                        is com.example.customgalleryviewer.data.GalleryItem.Folder -> {
                            val customCover = getFolderCover(item.uri)
                            val displayThumb = customCover ?: item.thumbnailUri
                            val scope = rememberCoroutineScope()

                            Box(
                                modifier = Modifier
                                    .aspectRatio(1f)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                                    .pointerInput(item.uri) {
                                        detectTapGestures(
                                            onTap = { onFolderClick(item.uri, item.name) },
                                            onLongPress = {
                                                // Load media files for cover picker
                                                scope.launch(Dispatchers.IO) {
                                                    val files = getMediaFilesInFolder(item.uri)
                                                    withContext(Dispatchers.Main) {
                                                        coverPickerFiles = files
                                                        coverPickerFolder = item
                                                    }
                                                }
                                            }
                                        )
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                if (displayThumb != null) {
                                    val context = LocalContext.current
                                    AsyncImage(
                                        model = ImageRequest.Builder(context)
                                            .data(displayThumb)
                                            .decoderFactory(VideoFrameDecoder.Factory())
                                            .crossfade(true)
                                            .size(256)
                                            .build(),
                                        contentDescription = null,
                                        modifier = Modifier.fillMaxSize(),
                                        contentScale = ContentScale.Crop,
                                        error = ColorPainter(MaterialTheme.colorScheme.surfaceVariant)
                                    )
                                }

                                // Folder overlay
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(
                                            Brush.verticalGradient(
                                                colors = listOf(
                                                    Color.Transparent,
                                                    Color.Black.copy(0.7f)
                                                )
                                            )
                                        )
                                )

                                Column(
                                    modifier = Modifier
                                        .align(Alignment.BottomStart)
                                        .padding(6.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Folder,
                                        null,
                                        tint = Color(0xFF00E5FF),
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Text(
                                        item.name,
                                        color = Color.White,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 2
                                    )
                                    if (item.childCount > 0) {
                                        Text(
                                            "${item.childCount}",
                                            color = Color.White.copy(0.6f),
                                            fontSize = 9.sp
                                        )
                                    }
                                }
                            }
                        }
                        is com.example.customgalleryviewer.data.GalleryItem.MediaFile -> {
                            val isVideoItem = isVideo(item.uri.toString())
                            val fileContext = LocalContext.current
                            val fileName = item.uri.lastPathSegment?.substringAfterLast('/') ?: ""

                            Box(
                                modifier = Modifier
                                    .aspectRatio(1f)
                                    .clip(RoundedCornerShape(2.dp))
                                    .clickable { onFileClick(item.uri) }
                            ) {
                                AsyncImage(
                                    model = ImageRequest.Builder(fileContext)
                                        .data(item.uri)
                                        .decoderFactory(VideoFrameDecoder.Factory())
                                        .crossfade(true)
                                        .size(256)
                                        .build(),
                                    contentDescription = null,
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop,
                                    error = ColorPainter(MaterialTheme.colorScheme.surfaceVariant)
                                )

                                // Bottom gradient with file name + duration
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.BottomCenter)
                                        .fillMaxWidth()
                                        .background(
                                            Brush.verticalGradient(
                                                colors = listOf(Color.Transparent, Color.Black.copy(0.75f))
                                            )
                                        )
                                        .padding(horizontal = 4.dp, vertical = 3.dp)
                                ) {
                                    Column {
                                        Text(
                                            fileName,
                                            color = Color.White,
                                            fontSize = 8.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            lineHeight = 10.sp
                                        )
                                        if (isVideoItem) {
                                            val durationMs = browseDurationCache[item.uri] ?: 0L
                                            if (durationMs > 0) {
                                                Text(
                                                    formatDuration(durationMs),
                                                    color = Color.White.copy(0.7f),
                                                    fontSize = 8.sp,
                                                    lineHeight = 10.sp
                                                )
                                            }
                                        }
                                    }
                                }

                                // Video play icon
                                if (isVideoItem) {
                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.TopEnd)
                                            .padding(3.dp)
                                            .size(18.dp)
                                            .background(Color.Black.copy(0.5f), CircleShape),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            Icons.Default.PlayArrow, null,
                                            tint = Color.White,
                                            modifier = Modifier.size(12.dp)
                                        )
                                    }
                                }

                                // Show m3u8 indicator
                                val ext = item.uri.toString().substringAfterLast('.', "").lowercase()
                                if (ext == "m3u8" || ext == "m3u") {
                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.TopStart)
                                            .padding(4.dp)
                                            .background(Color(0xFF6C63FF).copy(0.85f), RoundedCornerShape(4.dp))
                                            .padding(horizontal = 4.dp, vertical = 1.dp)
                                    ) {
                                        Text("HLS", color = Color.White, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Cover picker dialog
        if (coverPickerFolder != null && coverPickerFiles.isNotEmpty()) {
            AlertDialog(
                onDismissRequest = {
                    coverPickerFolder = null
                    coverPickerFiles = emptyList()
                },
                title = {
                    Text(
                        "Choose Cover",
                        fontWeight = FontWeight.SemiBold
                    )
                },
                text = {
                    Column {
                        Text(
                            coverPickerFolder!!.name,
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 12.dp)
                        )
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(3),
                            modifier = Modifier.heightIn(max = 400.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            items(coverPickerFiles.size) { idx ->
                                val fileUri = coverPickerFiles[idx]
                                val context = LocalContext.current
                                Box(
                                    modifier = Modifier
                                        .aspectRatio(1f)
                                        .clip(RoundedCornerShape(6.dp))
                                        .clickable {
                                            onSetFolderCover(coverPickerFolder!!.uri, fileUri)
                                            coverPickerFolder = null
                                            coverPickerFiles = emptyList()
                                        }
                                ) {
                                    AsyncImage(
                                        model = ImageRequest.Builder(context)
                                            .data(fileUri)
                                            .decoderFactory(VideoFrameDecoder.Factory())
                                            .crossfade(true)
                                            .size(200)
                                            .build(),
                                        contentDescription = null,
                                        modifier = Modifier.fillMaxSize(),
                                        contentScale = ContentScale.Crop,
                                        error = ColorPainter(MaterialTheme.colorScheme.surfaceVariant)
                                    )
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        coverPickerFolder = null
                        coverPickerFiles = emptyList()
                    }) {
                        Text("Cancel")
                    }
                },
                shape = RoundedCornerShape(20.dp)
            )
        }
    }
}

@Composable
fun MediaFilterChip(label: String, isSelected: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        color = if (isSelected) MaterialTheme.colorScheme.primary.copy(0.15f)
        else MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.height(32.dp)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.padding(horizontal = 14.dp)
        ) {
            Text(
                label,
                color = if (isSelected) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 13.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
            )
        }
    }
}

@Composable
fun PlayerContentView(
    currentMedia: Uri?,
    navigationMode: String,
    isShuffleOn: Boolean = false,
    isRepeatListOn: Boolean = false,
    onNext: () -> Unit,
    onPrev: () -> Unit,
    onToggleGallery: () -> Unit,
    onToggleShuffle: () -> Unit = {},
    onToggleRepeatList: () -> Unit = {},
    onSaveWatchPosition: (Uri, Long, Long) -> Unit = { _, _, _ -> },
    getWatchPosition: suspend (Uri) -> Long = { 0L },
    onToggleFavorite: (Uri) -> Unit = {},
    isFavoriteCheck: (Uri) -> Boolean = { false }
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
                var initialPosition by remember(uri) { mutableLongStateOf(-1L) }
                LaunchedEffect(uri) {
                    initialPosition = withContext(Dispatchers.IO) { getWatchPosition(uri) }
                }
                if (initialPosition < 0) {
                    // Wait for position to load - show black screen briefly
                    Box(modifier = Modifier.fillMaxSize().background(Color.Black))
                } else {
                val isFav = isFavoriteCheck(uri)
                VideoPlayer(
                    uri = uri,
                    onNext = onNext,
                    onPrev = onPrev,
                    onToggleShuffle = onToggleShuffle,
                    onToggleRepeatList = onToggleRepeatList,
                    isShuffleOn = isShuffleOn,
                    isRepeatListOn = isRepeatListOn,
                    navigationMode = navigationMode,
                    initialPosition = initialPosition,
                    onSavePosition = onSaveWatchPosition,
                    onToggleFavorite = onToggleFavorite,
                    isFavorite = isFav,
                    modifier = Modifier.fillMaxSize()
                )
                } // end if initialPosition >= 0
            } else {
                // Image viewer - smooth pinch zoom + pan + swipe navigation
                var swipeTriggered by remember { mutableStateOf(false) }

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black)
                        .clipToBounds()
                        .pointerInput(Unit) {
                            detectTransformGestures { _, pan, zoom, _ ->
                                val newScale = (scale * zoom).coerceIn(1f, 10f)
                                scale = newScale
                                if (scale > 1f) {
                                    val maxTranslateX = (size.width * (scale - 1)) / 2
                                    val maxTranslateY = (size.height * (scale - 1)) / 2
                                    offset = Offset(
                                        x = (offset.x + pan.x).coerceIn(-maxTranslateX, maxTranslateX),
                                        y = (offset.y + pan.y).coerceIn(-maxTranslateY, maxTranslateY)
                                    )
                                } else {
                                    offset = Offset.Zero
                                }
                            }
                        }
                        .pointerInput(Unit) {
                            detectTapGestures(
                                onDoubleTap = { tapOffset ->
                                    if (scale > 1f) {
                                        scale = 1f
                                        offset = Offset.Zero
                                    } else {
                                        scale = 3f
                                        // Zoom toward tap point
                                        val centerX = size.width / 2f
                                        val centerY = size.height / 2f
                                        offset = Offset(
                                            x = (centerX - tapOffset.x) * 2f,
                                            y = (centerY - tapOffset.y) * 2f
                                        )
                                    }
                                },
                                onLongPress = { showActionMenu = true }
                            )
                        }
                        .pointerInput(navigationMode) {
                            if (navigationMode == "SWIPE") {
                                detectDragGestures(
                                    onDragStart = { swipeTriggered = false },
                                    onDragEnd = {},
                                    onDragCancel = {}
                                ) { _, dragAmount ->
                                    if (scale == 1f && !swipeTriggered) {
                                        if (dragAmount.x < -30) { swipeTriggered = true; onNext() }
                                        else if (dragAmount.x > 30) { swipeTriggered = true; onPrev() }
                                    }
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
                        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                            Row(modifier = Modifier.fillMaxSize()) {
                                Box(
                                    modifier = Modifier
                                        .weight(0.3f)
                                        .fillMaxHeight()
                                        .clickable(
                                            interactionSource = remember { MutableInteractionSource() },
                                            indication = null
                                        ) { onPrev() }
                                )
                                Spacer(modifier = Modifier.weight(0.4f))
                                Box(
                                    modifier = Modifier
                                        .weight(0.3f)
                                        .fillMaxHeight()
                                        .clickable(
                                            interactionSource = remember { MutableInteractionSource() },
                                            indication = null
                                        ) { onNext() }
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

            // Gallery toggle — only visible via back button (no floating icon)
        }
    }
}

private val videoExtensions = setOf("mp4", "mkv", "avi", "mov", "flv", "wmv", "3gp", "webm", "ts", "m4v", "mpg", "mpeg", "vob", "m3u8", "m3u", "f4v", "ogv", "divx", "asf", "rm", "rmvb", "m2ts", "mts", "rec", "mxf")

fun isVideo(path: String): Boolean {
    val lowercasePath = path.lowercase(Locale.getDefault())
    // Use last path segment for extension (avoids matching dir names like "foo.m3u8/playlist")
    val lastSegment = lowercasePath.substringAfterLast('/').substringBefore('?')
    val ext = lastSegment.substringAfterLast('.', "")
    if (ext in videoExtensions) return true
    // Detect HLS playlists by filename containing m3u8/m3u (e.g., "playlist_m3u8")
    if (lastSegment.contains("m3u8") || lastSegment.contains("m3u")) return true
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

fun getVideoDurationMs(context: android.content.Context, uri: Uri): Long {
    return try {
        if (uri.scheme == "content") {
            context.contentResolver.query(
                uri,
                arrayOf(MediaStore.Video.VideoColumns.DURATION),
                null, null, null
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val idx = cursor.getColumnIndex(MediaStore.Video.VideoColumns.DURATION)
                    if (idx >= 0) cursor.getLong(idx) else 0L
                } else 0L
            } ?: 0L
        } else {
            val retriever = MediaMetadataRetriever()
            try {
                val path = uri.path
                if (path != null) {
                    retriever.setDataSource(path)
                    retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
                } else 0L
            } finally {
                retriever.release()
            }
        }
    } catch (_: Exception) { 0L }
}

@Composable
fun FastScrollbar(
    gridState: androidx.compose.foundation.lazy.grid.LazyGridState,
    itemCount: Int,
    columns: Int,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    val totalRows = (itemCount + columns - 1) / columns
    if (totalRows <= 0) return

    val firstVisibleRow by remember {
        derivedStateOf {
            gridState.firstVisibleItemIndex / columns
        }
    }
    val visibleRowCount by remember {
        derivedStateOf {
            val info = gridState.layoutInfo
            val visibleItems = info.visibleItemsInfo.size
            (visibleItems + columns - 1) / columns
        }
    }

    val thumbFraction = (visibleRowCount.toFloat() / totalRows).coerceIn(0.05f, 1f)
    val scrollFraction = if (totalRows <= visibleRowCount) 0f
        else (firstVisibleRow.toFloat() / (totalRows - visibleRowCount)).coerceIn(0f, 1f)

    if (thumbFraction >= 1f) return // no scrollbar needed

    Box(
        modifier = modifier
            .pointerInput(totalRows, columns) {
                detectDragGestures { change, _ ->
                    change.consume()
                    val y = change.position.y
                    val fraction = (y / size.height).coerceIn(0f, 1f)
                    val targetRow = (fraction * (totalRows - 1)).toInt()
                    val targetIndex = (targetRow * columns).coerceIn(0, (itemCount - 1).coerceAtLeast(0))
                    scope.launch {
                        gridState.scrollToItem(targetIndex)
                    }
                }
            }
    ) {
        val trackColor = Color.Gray.copy(0.2f)
        val thumbColor = Color.Gray.copy(0.5f)

        // Track
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(trackColor, RoundedCornerShape(4.dp))
        )

        // Thumb
        val thumbHeight = (thumbFraction * 1f) // fraction of parent
        val thumbOffset = scrollFraction * (1f - thumbFraction)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(thumbHeight)
                .offset(y = with(androidx.compose.ui.platform.LocalDensity.current) {
                    // We need pixel calculation but offset takes dp
                    0.dp // handled by padding instead
                })
                .padding(top = with(androidx.compose.ui.platform.LocalDensity.current) {
                    // approximate: fill parent and position thumb proportionally
                    0.dp
                })
        )

        // Draw thumb using drawWithContent
        Box(
            modifier = Modifier
                .fillMaxSize()
                .drawWithContent {
                    drawContent()
                    val trackH = size.height
                    val tHeight = trackH * thumbFraction
                    val tTop = trackH * thumbOffset
                    drawRoundRect(
                        color = thumbColor,
                        topLeft = androidx.compose.ui.geometry.Offset(0f, tTop),
                        size = Size(size.width, tHeight),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(4f, 4f)
                    )
                }
        )
    }
}

fun formatDuration(ms: Long): String {
    val totalSec = ms / 1000
    val h = totalSec / 3600
    val m = (totalSec % 3600) / 60
    val s = totalSec % 60
    return if (h > 0) String.format("%d:%02d:%02d", h, m, s)
    else String.format("%d:%02d", m, s)
}

package com.example.customgalleryviewer.presentation

import android.app.Activity
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import coil.decode.VideoFrameDecoder
import coil.request.ImageRequest
import com.example.customgalleryviewer.data.PlaylistWithItems
import com.example.customgalleryviewer.util.MediaFolder

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onNavigateToPlayer: (Long) -> Unit,
    onNavigateToAdd: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToEdit: (Long) -> Unit,
    onNavigateToDeviceFolder: (Long) -> Unit = {},
    viewModel: HomeViewModel = hiltViewModel()
) {
    val playlists by viewModel.playlists.collectAsState()
    val deviceFolders by viewModel.deviceFolders.collectAsState()
    val isScanning by viewModel.isScanning.collectAsState()
    val showHidden by viewModel.showHidden.collectAsState()
    val selectedTab by viewModel.selectedTab.collectAsState()
    val context = LocalContext.current

    // Double-back-to-exit
    var backPressedOnce by remember { mutableStateOf(false) }
    BackHandler {
        if (backPressedOnce) {
            (context as? Activity)?.finish()
        } else {
            backPressedOnce = true
            Toast.makeText(context, "Press back again to exit", Toast.LENGTH_SHORT).show()
        }
    }
    LaunchedEffect(backPressedOnce) {
        if (backPressedOnce) {
            kotlinx.coroutines.delay(2000)
            backPressedOnce = false
        }
    }

    // View mode persistence
    val settingsViewModel: SettingsViewModel = hiltViewModel()
    var homeViewMode by remember { mutableStateOf(settingsViewModel.getHomeViewMode()) }

    LaunchedEffect(selectedTab) {
        if (selectedTab == 1) viewModel.loadDeviceFolders(force = true)
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        floatingActionButton = {
            if (selectedTab == 0) {
                FloatingActionButton(
                    onClick = onNavigateToAdd,
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    shape = CircleShape,
                    modifier = Modifier.size(64.dp)
                ) {
                    Icon(Icons.Default.Add, "Add Playlist", modifier = Modifier.size(28.dp))
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Header
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(top = 60.dp, bottom = 8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        if (selectedTab == 0) "Library" else "All",
                        fontSize = 34.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        // View mode toggle
                        IconButton(
                            onClick = {
                                homeViewMode = when (homeViewMode) {
                                    "hero" -> "list"
                                    "list" -> "grid"
                                    else -> "hero"
                                }
                                settingsViewModel.setHomeViewMode(homeViewMode)
                            },
                            modifier = Modifier
                                .size(40.dp)
                                .background(
                                    MaterialTheme.colorScheme.surfaceVariant,
                                    CircleShape
                                )
                        ) {
                            Icon(
                                when (homeViewMode) {
                                    "hero" -> Icons.Default.ViewAgenda
                                    "list" -> Icons.Default.ViewList
                                    else -> Icons.Default.GridView
                                },
                                "View Mode",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        IconButton(
                            onClick = onNavigateToSettings,
                            modifier = Modifier
                                .size(40.dp)
                                .background(
                                    MaterialTheme.colorScheme.surfaceVariant,
                                    CircleShape
                                )
                        ) {
                            Icon(
                                Icons.Default.Settings,
                                "Settings",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }

                val subtitle = when {
                    selectedTab == 0 && playlists.isNotEmpty() ->
                        "${playlists.size} collection${if (playlists.size != 1) "s" else ""}"
                    selectedTab == 1 && deviceFolders.isNotEmpty() ->
                        "${deviceFolders.size} folder${if (deviceFolders.size != 1) "s" else ""}"
                    else -> null
                }
                if (subtitle != null) {
                    Text(
                        subtitle,
                        fontSize = 15.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
            }

            // Tab selector
            SingleChoiceSegmentedButtonRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp)
            ) {
                val tabs = listOf("My Lists", "All")
                tabs.forEachIndexed { index, label ->
                    SegmentedButton(
                        selected = selectedTab == index,
                        onClick = { viewModel.setSelectedTab(index) },
                        shape = SegmentedButtonDefaults.itemShape(index = index, count = tabs.size),
                        colors = SegmentedButtonDefaults.colors(
                            activeContainerColor = MaterialTheme.colorScheme.primary.copy(0.15f),
                            activeContentColor = MaterialTheme.colorScheme.primary,
                            inactiveContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                            inactiveContentColor = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    ) {
                        Text(label, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                    }
                }
            }

            // Content
            if (selectedTab == 0) {
                // My Lists tab
                MyListsContent(
                    playlists = if (showHidden) playlists else playlists.filter { !it.playlist.isHidden },
                    viewMode = homeViewMode,
                    onNavigateToPlayer = onNavigateToPlayer,
                    onDelete = { viewModel.deletePlaylist(it) },
                    onEdit = onNavigateToEdit,
                    onHide = { viewModel.hidePlaylist(it) },
                    onUnhide = { viewModel.unhidePlaylist(it) },
                    onRename = { id, name -> viewModel.renamePlaylist(id, name) },
                    onNavigateToAdd = onNavigateToAdd
                )
            } else {
                // All Photos tab
                AllPhotosContent(
                    folders = deviceFolders,
                    isScanning = isScanning,
                    onFolderClick = { folder ->
                        onNavigateToDeviceFolder(folder.bucketId)
                    },
                    viewModel = viewModel,
                    viewMode = homeViewMode
                )
            }
        }
    }
}

@Composable
private fun MyListsContent(
    playlists: List<PlaylistWithItems>,
    viewMode: String = "hero",
    onNavigateToPlayer: (Long) -> Unit,
    onDelete: (Long) -> Unit,
    onEdit: (Long) -> Unit,
    onHide: (Long) -> Unit,
    onUnhide: (Long) -> Unit,
    onRename: (Long, String) -> Unit,
    onNavigateToAdd: () -> Unit
) {
    // Multi-select state
    var selectedIds by remember { mutableStateOf(setOf<Long>()) }
    var isSelectionMode by remember { mutableStateOf(false) }

    if (isSelectionMode) {
        // Selection header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.primaryContainer)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = { isSelectionMode = false; selectedIds = emptySet() }) {
                Text("Cancel")
            }
            Text("${selectedIds.size} selected", fontWeight = FontWeight.SemiBold)
            TextButton(
                onClick = {
                    selectedIds.forEach { onDelete(it) }
                    selectedIds = emptySet()
                    isSelectionMode = false
                },
                enabled = selectedIds.isNotEmpty()
            ) {
                Text("Delete", color = if (selectedIds.isNotEmpty()) MaterialTheme.colorScheme.error else Color.Gray)
            }
        }
    }

    if (playlists.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .background(
                            MaterialTheme.colorScheme.surfaceVariant,
                            RoundedCornerShape(24.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.PhotoLibrary, null,
                        modifier = Modifier.size(40.dp),
                        tint = MaterialTheme.colorScheme.onSurface.copy(0.2f)
                    )
                }
                Text(
                    "No Collections",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface.copy(0.6f)
                )
                Text(
                    "Create your first collection to get started",
                    fontSize = 15.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(0.35f)
                )
            }
        }
    } else {
        when (viewMode) {
            "grid" -> {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(playlists) { playlistWithItems ->
                        val isSelected = selectedIds.contains(playlistWithItems.playlist.id)
                        PlaylistGridCard(
                            item = playlistWithItems,
                            isSelected = isSelected,
                            onClick = {
                                if (isSelectionMode) {
                                    selectedIds = if (isSelected) selectedIds - playlistWithItems.playlist.id
                                    else selectedIds + playlistWithItems.playlist.id
                                } else {
                                    onNavigateToPlayer(playlistWithItems.playlist.id)
                                }
                            },
                            onLongClick = {
                                isSelectionMode = true
                                selectedIds = selectedIds + playlistWithItems.playlist.id
                            }
                        )
                    }
                }
            }
            "list" -> {
                LazyColumn(contentPadding = PaddingValues(bottom = 100.dp)) {
                    items(playlists) { playlistWithItems ->
                        val isSelected = selectedIds.contains(playlistWithItems.playlist.id)
                        PlaylistListRow(
                            item = playlistWithItems,
                            isSelected = isSelected,
                            onClick = {
                                if (isSelectionMode) {
                                    selectedIds = if (isSelected) selectedIds - playlistWithItems.playlist.id
                                    else selectedIds + playlistWithItems.playlist.id
                                } else {
                                    onNavigateToPlayer(playlistWithItems.playlist.id)
                                }
                            },
                            onLongClick = {
                                isSelectionMode = true
                                selectedIds = selectedIds + playlistWithItems.playlist.id
                            }
                        )
                    }
                }
            }
            else -> { // "hero"
                LazyColumn(contentPadding = PaddingValues(bottom = 100.dp)) {
                    items(playlists) { playlistWithItems ->
                        PlaylistHeroCard(
                            item = playlistWithItems,
                            onClick = {
                                if (isSelectionMode) {
                                    val id = playlistWithItems.playlist.id
                                    selectedIds = if (selectedIds.contains(id)) selectedIds - id else selectedIds + id
                                } else {
                                    onNavigateToPlayer(playlistWithItems.playlist.id)
                                }
                            },
                            onDelete = { onDelete(playlistWithItems.playlist.id) },
                            onEdit = { onEdit(playlistWithItems.playlist.id) },
                            onHide = { onHide(playlistWithItems.playlist.id) },
                            onUnhide = { onUnhide(playlistWithItems.playlist.id) },
                            onRename = { name -> onRename(playlistWithItems.playlist.id, name) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AllPhotosContent(
    folders: List<MediaFolder>,
    isScanning: Boolean,
    onFolderClick: (MediaFolder) -> Unit,
    viewModel: HomeViewModel,
    viewMode: String = "hero"
) {
    // Cover picker state
    var coverPickerFolder by remember { mutableStateOf<MediaFolder?>(null) }
    var coverPickerFiles by remember { mutableStateOf<List<Uri>>(emptyList()) }
    var menuFolder by remember { mutableStateOf<MediaFolder?>(null) }
    var renameFolderTarget by remember { mutableStateOf<MediaFolder?>(null) }
    var renameText by remember { mutableStateOf("") }
    var deleteFolderConfirm by remember { mutableStateOf<MediaFolder?>(null) }
    var selectedFolders by remember { mutableStateOf(setOf<Long>()) }
    val isFolderSelectionMode = selectedFolders.isNotEmpty()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    if (isScanning && folders.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator(
                    color = MaterialTheme.colorScheme.primary,
                    strokeWidth = 3.dp
                )
                Spacer(Modifier.height(16.dp))
                Text(
                    "Scanning device...",
                    color = MaterialTheme.colorScheme.onSurface.copy(0.5f)
                )
            }
        }
    } else if (folders.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                "No media folders found",
                color = MaterialTheme.colorScheme.onSurface.copy(0.3f)
            )
        }
    } else {
        Column {
        // Selection header for folders
        if (isFolderSelectionMode) {
            Row(
                modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.primaryContainer).padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = { selectedFolders = emptySet() }) { Text("Cancel", color = MaterialTheme.colorScheme.primary) }
                Text("${selectedFolders.size} selected", fontWeight = FontWeight.SemiBold)
                IconButton(onClick = { selectedFolders = folders.map { it.bucketId }.toSet() }) { Icon(Icons.Default.SelectAll, "Select All") }
            }
        }

        when (viewMode) {
            "list" -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().weight(1f),
                    contentPadding = PaddingValues(vertical = 4.dp)
                ) {
                    items(folders) { folder ->
                        val customCover = viewModel.getFolderCoverByBucket(folder.bucketId)
                        val displayUri = customCover ?: folder.thumbnailUri
                        val isSel = folder.bucketId in selectedFolders
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(if (isSel) MaterialTheme.colorScheme.primary.copy(0.15f) else Color.Transparent)
                                .pointerInput(isFolderSelectionMode) {
                                    detectTapGestures(
                                        onTap = {
                                            if (isFolderSelectionMode) selectedFolders = if (isSel) selectedFolders - folder.bucketId else selectedFolders + folder.bucketId
                                            else onFolderClick(folder)
                                        },
                                        onLongPress = { menuFolder = folder }
                                    )
                                }
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(modifier = Modifier.size(56.dp).clip(RoundedCornerShape(10.dp))) {
                                if (displayUri != null) {
                                    AsyncImage(
                                        model = ImageRequest.Builder(context).data(displayUri)
                                            .decoderFactory(VideoFrameDecoder.Factory()).size(128).crossfade(true).build(),
                                        contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop
                                    )
                                } else {
                                    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
                                        Icon(Icons.Default.Folder, null, tint = MaterialTheme.colorScheme.onSurface.copy(0.3f))
                                    }
                                }
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(folder.name, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text("${folder.mediaCount} files", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface.copy(0.5f))
                            }
                        }
                    }
                }
            }
            else -> {
                // Grid view (default and "hero")
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(folders) { folder ->
                        val customCover = viewModel.getFolderCoverByBucket(folder.bucketId)
                        val isSel = folder.bucketId in selectedFolders
                        DeviceFolderCard(
                            folder = folder,
                            customCoverUri = customCover,
                            isSelected = isSel,
                            isSelectionMode = isFolderSelectionMode,
                            onClick = {
                                if (isFolderSelectionMode) selectedFolders = if (isSel) selectedFolders - folder.bucketId else selectedFolders + folder.bucketId
                                else onFolderClick(folder)
                            },
                            onLongPress = { menuFolder = folder }
                        )
                    }
                }
            }
        }

        // Multi-folder delete confirmation
        var showMultiDeleteConfirm by remember { mutableStateOf(false) }
        if (showMultiDeleteConfirm) {
            AlertDialog(
                onDismissRequest = { showMultiDeleteConfirm = false },
                title = { Text("Delete ${selectedFolders.size} folders?", fontWeight = FontWeight.SemiBold) },
                text = { Text("All files inside will be permanently deleted. This cannot be undone.", color = MaterialTheme.colorScheme.onSurface.copy(0.7f)) },
                confirmButton = {
                    TextButton(onClick = {
                        showMultiDeleteConfirm = false
                        val toDelete = selectedFolders.toSet()
                        scope.launch(Dispatchers.IO) {
                            var deleted = 0
                            toDelete.forEach { bucketId ->
                                val f = folders.find { it.bucketId == bucketId }
                                if (f != null) {
                                    val dir = java.io.File(f.path)
                                    if (dir.exists() && dir.deleteRecursively()) deleted++
                                }
                            }
                            withContext(Dispatchers.Main) {
                                android.widget.Toast.makeText(context, "Deleted $deleted folders", android.widget.Toast.LENGTH_SHORT).show()
                                selectedFolders = emptySet()
                                viewModel.loadDeviceFolders(force = true)
                            }
                        }
                    }) { Text("Delete", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.SemiBold) }
                },
                dismissButton = { TextButton(onClick = { showMultiDeleteConfirm = false }) { Text("Cancel") } },
                shape = RoundedCornerShape(20.dp)
            )
        }

        // Bottom bar for folder selection
        if (isFolderSelectionMode) {
            Surface(color = MaterialTheme.colorScheme.surfaceVariant, shadowElevation = 8.dp) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.clickable {
                            val toDelete = selectedFolders.toSet()
                            scope.launch(Dispatchers.IO) {
                                var deleted = 0
                                toDelete.forEach { bucketId ->
                                    val f = folders.find { it.bucketId == bucketId }
                                    if (f != null) {
                                        val dir = java.io.File(f.path)
                                        android.util.Log.w("HomeScreen", "Deleting folder: ${dir.absolutePath} exists=${dir.exists()}")
                                        if (dir.exists()) {
                                            val ok = dir.deleteRecursively()
                                            android.util.Log.w("HomeScreen", "Delete result: $ok")
                                            if (ok) deleted++
                                        }
                                    }
                                }
                                withContext(Dispatchers.Main) {
                                    android.widget.Toast.makeText(context, "Deleted $deleted of ${toDelete.size} folders", android.widget.Toast.LENGTH_SHORT).show()
                                    selectedFolders = emptySet()
                                    viewModel.loadDeviceFolders(force = true)
                                }
                            }
                        }.padding(horizontal = 16.dp, vertical = 4.dp)
                    ) {
                        Icon(Icons.Default.Delete, "Delete", tint = MaterialTheme.colorScheme.onSurface)
                        Text("Delete", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface)
                    }
                }
            }
        }
        } // end Column
    }

    // Folder long-press menu
    if (menuFolder != null) {
        val folder = menuFolder!!
        AlertDialog(
            onDismissRequest = { menuFolder = null },
            title = { Text(folder.name, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            text = {
                Column {
                    TextButton(onClick = {
                        selectedFolders = selectedFolders + folder.bucketId
                        menuFolder = null
                    }) {
                        Icon(Icons.Default.CheckBox, null, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(12.dp))
                        Text("Select")
                    }
                    TextButton(onClick = {
                        menuFolder = null
                        renameText = folder.name
                        renameFolderTarget = folder
                    }) {
                        Icon(Icons.Default.Edit, null, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(12.dp))
                        Text("Rename")
                    }
                    TextButton(onClick = {
                        menuFolder = null
                        scope.launch(Dispatchers.IO) {
                            val files = viewModel.getFilesInFolder(folder.bucketId)
                            withContext(Dispatchers.Main) {
                                coverPickerFiles = files
                                coverPickerFolder = folder
                            }
                        }
                    }) {
                        Icon(Icons.Default.Image, null, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(12.dp))
                        Text("Change Cover")
                    }
                    TextButton(onClick = { menuFolder = null; deleteFolderConfirm = folder }) {
                        Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(12.dp))
                        Text("Delete", color = MaterialTheme.colorScheme.error)
                    }
                }
            },
            confirmButton = { TextButton(onClick = { menuFolder = null }) { Text("Cancel") } },
            shape = RoundedCornerShape(20.dp)
        )
    }

    // Rename folder dialog
    if (renameFolderTarget != null) {
        AlertDialog(
            onDismissRequest = { renameFolderTarget = null },
            title = { Text("Rename Folder", fontWeight = FontWeight.SemiBold) },
            text = {
                OutlinedTextField(
                    value = renameText,
                    onValueChange = { renameText = it },
                    singleLine = true,
                    label = { Text("Folder name") }
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (renameText.isNotBlank()) {
                        val folder = renameFolderTarget!!
                        val oldDir = java.io.File(folder.path)
                        val newDir = java.io.File(oldDir.parent, renameText.trim())
                        if (oldDir.exists() && oldDir.renameTo(newDir)) {
                            viewModel.loadDeviceFolders(force = true)
                        }
                        renameFolderTarget = null
                    }
                }) { Text("Save", fontWeight = FontWeight.SemiBold) }
            },
            dismissButton = { TextButton(onClick = { renameFolderTarget = null }) { Text("Cancel") } },
            shape = RoundedCornerShape(20.dp)
        )
    }

    // Delete folder confirmation
    if (deleteFolderConfirm != null) {
        val folder = deleteFolderConfirm!!
        AlertDialog(
            onDismissRequest = { deleteFolderConfirm = null },
            title = { Text("Delete Folder?", fontWeight = FontWeight.SemiBold) },
            text = { Text("\"${folder.name}\" and all ${folder.mediaCount} files will be permanently deleted.", color = MaterialTheme.colorScheme.onSurface.copy(0.7f)) },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch(Dispatchers.IO) {
                        java.io.File(folder.path).deleteRecursively()
                        withContext(Dispatchers.Main) {
                            viewModel.loadDeviceFolders(force = true)
                            deleteFolderConfirm = null
                        }
                    }
                }) { Text("Delete", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.SemiBold) }
            },
            dismissButton = { TextButton(onClick = { deleteFolderConfirm = null }) { Text("Cancel") } },
            shape = RoundedCornerShape(20.dp)
        )
    }

    // Cover picker dialog
    if (coverPickerFolder != null) {
        AlertDialog(
            onDismissRequest = {
                coverPickerFolder = null
                coverPickerFiles = emptyList()
            },
            title = {
                Text("Choose Cover", fontWeight = FontWeight.SemiBold)
            },
            text = {
                Column {
                    Text(
                        coverPickerFolder!!.name,
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                    if (coverPickerFiles.isEmpty()) {
                        Text(
                            "No files found in this folder",
                            color = MaterialTheme.colorScheme.onSurface.copy(0.4f),
                            fontSize = 13.sp
                        )
                    }
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(3),
                        modifier = Modifier.heightIn(max = 400.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        items(coverPickerFiles.size) { idx ->
                            val fileUri = coverPickerFiles[idx]
                            val ctx = LocalContext.current
                            Box(
                                modifier = Modifier
                                    .aspectRatio(1f)
                                    .clip(RoundedCornerShape(6.dp))
                                    .clickable {
                                        viewModel.setFolderCoverByBucket(coverPickerFolder!!.bucketId, fileUri)
                                        coverPickerFolder = null
                                        coverPickerFiles = emptyList()
                                    }
                            ) {
                                AsyncImage(
                                    model = ImageRequest.Builder(ctx)
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

@Composable
private fun DeviceFolderCard(
    folder: MediaFolder,
    customCoverUri: Uri? = null,
    isSelected: Boolean = false,
    isSelectionMode: Boolean = false,
    onClick: () -> Unit,
    onLongPress: () -> Unit = {}
) {
    val context = LocalContext.current
    val displayUri = customCoverUri ?: folder.thumbnailUri

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(0.85f)
            .then(if (isSelected) Modifier.border(3.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(16.dp)) else Modifier),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(onClick, onLongPress) {
                    detectTapGestures(
                        onTap = { onClick() },
                        onLongPress = { onLongPress() }
                    )
                }
        ) {
            if (displayUri != null) {
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(displayUri)
                        .decoderFactory(VideoFrameDecoder.Factory())
                        .crossfade(true)
                        .size(400)
                        .build(),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            }

            // Gradient overlay
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Transparent,
                                Color.Transparent,
                                Color.Black.copy(0.8f)
                            )
                        )
                    )
            )

            // Info at bottom
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(12.dp)
            ) {
                Text(
                    folder.name,
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    "${folder.mediaCount} files",
                    color = Color.White.copy(0.6f),
                    fontSize = 12.sp
                )
            }

            // Selection checkbox - visible on all folders when in selection mode
            if (isSelected || isSelectionMode) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                        .size(24.dp)
                        .background(
                            if (isSelected) MaterialTheme.colorScheme.primary else Color.Black.copy(0.4f),
                            CircleShape
                        )
                        .then(if (!isSelected) Modifier.border(2.dp, Color.White.copy(0.6f), CircleShape) else Modifier),
                    contentAlignment = Alignment.Center
                ) {
                    if (isSelected) {
                        Icon(Icons.Default.Check, null, tint = Color.White, modifier = Modifier.size(16.dp))
                    }
                }
            }
        }
    }
}

@Composable
fun PlaylistHeroCard(
    item: PlaylistWithItems,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    onEdit: () -> Unit,
    onHide: () -> Unit = {},
    onUnhide: () -> Unit = {},
    onRename: (String) -> Unit = {}
) {
    val context = LocalContext.current
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showLongPressMenu by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var renameText by remember(item.playlist.name) { mutableStateOf(item.playlist.name) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp)
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { onClick() },
                    onLongPress = { showLongPressMenu = true }
                )
            },
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            if (item.playlist.thumbnailUri != null) {
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(Uri.parse(item.playlist.thumbnailUri))
                        .decoderFactory(VideoFrameDecoder.Factory())
                        .crossfade(true)
                        .build(),
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp),
                    contentScale = ContentScale.Crop
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color.Transparent,
                                    Color.Transparent,
                                    Color.Black.copy(0.8f)
                                )
                            )
                        )
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                        .background(
                            Brush.linearGradient(
                                colors = listOf(
                                    MaterialTheme.colorScheme.primary.copy(0.3f),
                                    MaterialTheme.colorScheme.secondary.copy(0.4f),
                                    MaterialTheme.colorScheme.surfaceVariant
                                )
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        item.playlist.name.take(2).uppercase(),
                        fontSize = 48.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimary.copy(0.3f)
                    )
                }
            }

            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Text(
                    item.playlist.name,
                    fontWeight = FontWeight.Bold,
                    fontSize = 22.sp,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (item.playlist.isHidden) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = Color.White.copy(0.2f),
                        modifier = Modifier.padding(top = 4.dp)
                    ) {
                        Text(
                            "Hidden",
                            fontSize = 10.sp,
                            color = Color.White.copy(0.7f),
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                        )
                    }
                }
                Spacer(Modifier.height(4.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        "${item.items.size} item${if (item.items.size != 1) "s" else ""}",
                        fontSize = 14.sp,
                        color = Color.White.copy(0.7f)
                    )
                    val filterLabel = item.playlist.mediaFilterType.name
                        .replace("_", " ").lowercase()
                        .replaceFirstChar { it.uppercase() }
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color.White.copy(0.15f)
                    ) {
                        Text(
                            filterLabel,
                            fontSize = 11.sp,
                            color = Color.White.copy(0.8f),
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }
            }

            Row(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                IconButton(
                    onClick = onEdit,
                    modifier = Modifier
                        .size(36.dp)
                        .background(Color.Black.copy(0.4f), CircleShape)
                ) {
                    Icon(
                        Icons.Default.Edit, "Edit",
                        tint = Color.White.copy(0.9f),
                        modifier = Modifier.size(16.dp)
                    )
                }
                IconButton(
                    onClick = { showDeleteConfirm = true },
                    modifier = Modifier
                        .size(36.dp)
                        .background(Color.Black.copy(0.4f), CircleShape)
                ) {
                    Icon(
                        Icons.Default.Delete, "Delete",
                        tint = MaterialTheme.colorScheme.error.copy(0.9f),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete Collection?", fontWeight = FontWeight.SemiBold) },
            text = {
                Text(
                    "\"${item.playlist.name}\" and all its data will be permanently removed.",
                    color = MaterialTheme.colorScheme.onSurface.copy(0.7f)
                )
            },
            confirmButton = {
                TextButton(onClick = { onDelete(); showDeleteConfirm = false }) {
                    Text("Delete", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.SemiBold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancel") }
            },
            shape = RoundedCornerShape(20.dp)
        )
    }

    if (showLongPressMenu) {
        AlertDialog(
            onDismissRequest = { showLongPressMenu = false },
            title = { Text(item.playlist.name, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            text = {
                Column {
                    TextButton(onClick = { showLongPressMenu = false; showRenameDialog = true }) {
                        Icon(Icons.Default.Edit, null, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(12.dp))
                        Text("Rename")
                    }
                    if (item.playlist.isHidden) {
                        TextButton(onClick = { onUnhide(); showLongPressMenu = false }) {
                            Icon(Icons.Default.Visibility, null, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(12.dp))
                            Text("Unhide")
                        }
                    } else {
                        TextButton(onClick = { onHide(); showLongPressMenu = false }) {
                            Icon(Icons.Default.VisibilityOff, null, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(12.dp))
                            Text("Hide")
                        }
                    }
                    TextButton(onClick = { showLongPressMenu = false; showDeleteConfirm = true }) {
                        Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(12.dp))
                        Text("Delete", color = MaterialTheme.colorScheme.error)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showLongPressMenu = false }) { Text("Cancel") }
            },
            shape = RoundedCornerShape(20.dp)
        )
    }

    if (showRenameDialog) {
        AlertDialog(
            onDismissRequest = { showRenameDialog = false },
            title = { Text("Rename", fontWeight = FontWeight.SemiBold) },
            text = {
                OutlinedTextField(
                    value = renameText,
                    onValueChange = { renameText = it },
                    singleLine = true,
                    label = { Text("Name") }
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (renameText.isNotBlank()) {
                        onRename(renameText.trim())
                        showRenameDialog = false
                    }
                }) { Text("Save", fontWeight = FontWeight.SemiBold) }
            },
            dismissButton = {
                TextButton(onClick = { showRenameDialog = false }) { Text("Cancel") }
            },
            shape = RoundedCornerShape(20.dp)
        )
    }
}

@Composable
private fun PlaylistGridCard(
    item: PlaylistWithItems,
    isSelected: Boolean = false,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val context = LocalContext.current
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(0.85f)
            .then(
                if (isSelected) Modifier.background(
                    MaterialTheme.colorScheme.primary.copy(0.2f),
                    RoundedCornerShape(16.dp)
                ) else Modifier
            ),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = { onClick() },
                        onLongPress = { onLongClick() }
                    )
                }
        ) {
            if (item.playlist.thumbnailUri != null) {
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(Uri.parse(item.playlist.thumbnailUri))
                        .decoderFactory(VideoFrameDecoder.Factory())
                        .crossfade(true)
                        .size(400)
                        .build(),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color.Transparent, Color.Transparent, Color.Black.copy(0.8f))
                        )
                    )
            )
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(12.dp)
            ) {
                Text(
                    item.playlist.name,
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    "${item.items.size} items",
                    color = Color.White.copy(0.6f),
                    fontSize = 12.sp
                )
            }
            if (isSelected) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                        .size(24.dp)
                        .background(MaterialTheme.colorScheme.primary, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Check, null, tint = Color.White, modifier = Modifier.size(16.dp))
                }
            }
        }
    }
}

@Composable
private fun PlaylistListRow(
    item: PlaylistWithItems,
    isSelected: Boolean = false,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val context = LocalContext.current
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { onClick() },
                    onLongPress = { onLongClick() }
                )
            },
        shape = RoundedCornerShape(12.dp),
        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer
        else MaterialTheme.colorScheme.surfaceVariant
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surface)
            ) {
                if (item.playlist.thumbnailUri != null) {
                    AsyncImage(
                        model = ImageRequest.Builder(context)
                            .data(Uri.parse(item.playlist.thumbnailUri))
                            .decoderFactory(VideoFrameDecoder.Factory())
                            .crossfade(true)
                            .size(200)
                            .build(),
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.PhotoLibrary, null,
                            tint = MaterialTheme.colorScheme.onSurface.copy(0.3f)
                        )
                    }
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    item.playlist.name,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    "${item.items.size} items",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (isSelected) {
                Icon(
                    Icons.Default.CheckCircle,
                    null,
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

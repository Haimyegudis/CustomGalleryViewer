package com.example.customgalleryviewer.presentation

import android.net.Uri
import android.os.Environment
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.customgalleryviewer.data.ItemType
import com.example.customgalleryviewer.data.MediaFilterType
import java.io.File
import java.net.URLDecoder

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddPlaylistScreen(
    onBack: () -> Unit,
    viewModel: AddPlaylistViewModel = hiltViewModel()
) {
    var playlistName by remember { mutableStateOf("") }
    var selectedFilter by remember { mutableStateOf(MediaFilterType.MIXED) }
    val selectedItems = remember { mutableStateListOf<Pair<Uri, ItemType>>() }
    val context = LocalContext.current
    var isProcessing by remember { mutableStateOf(false) }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let {
            try {
                context.contentResolver.takePersistableUriPermission(
                    it, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
                selectedItems.add(it to ItemType.FILE)
            } catch (e: Exception) {
                Toast.makeText(context, "Error adding file", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // Folder browser state — uses File API directly (MANAGE_EXTERNAL_STORAGE)
    var showFolderBrowser by remember { mutableStateOf(false) }
    var browserCurrentDir by remember { mutableStateOf(Environment.getExternalStorageDirectory()) }

    val folderPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        uri?.let {
            try {
                context.contentResolver.takePersistableUriPermission(
                    it, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
                selectedItems.add(it to ItemType.FOLDER)
            } catch (e: Exception) {
                Toast.makeText(context, "Error adding folder", Toast.LENGTH_SHORT).show()
            }
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {},
                navigationIcon = {
                    TextButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack, null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text("Back", color = MaterialTheme.colorScheme.primary)
                    }
                },
                actions = {
                    TextButton(
                        onClick = {
                            if (playlistName.isBlank()) {
                                Toast.makeText(context, "Enter a name", Toast.LENGTH_SHORT).show()
                            } else if (selectedItems.isEmpty()) {
                                Toast.makeText(context, "Add content first", Toast.LENGTH_SHORT).show()
                            } else {
                                isProcessing = true
                                viewModel.createPlaylist(playlistName, selectedItems, selectedFilter) {
                                    isProcessing = false
                                    onBack()
                                }
                            }
                        },
                        enabled = !isProcessing && playlistName.isNotBlank() && selectedItems.isNotEmpty()
                    ) {
                        if (isProcessing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.primary
                            )
                        } else {
                            Text(
                                "Done",
                                fontWeight = FontWeight.Bold,
                                color = if (playlistName.isNotBlank() && selectedItems.isNotEmpty())
                                    MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurface.copy(0.3f)
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(bottom = 40.dp)
        ) {
            // Title
            item {
                Text(
                    "New Collection",
                    fontSize = 34.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
                )
            }

            // Name input — grouped section
            item {
                GroupedSection(modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) {
                    TextField(
                        value = playlistName,
                        onValueChange = { playlistName = it },
                        placeholder = {
                            Text(
                                "Collection Name",
                                color = MaterialTheme.colorScheme.onSurface.copy(0.3f)
                            )
                        },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        enabled = !isProcessing,
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            focusedTextColor = MaterialTheme.colorScheme.onSurface,
                            unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                            cursorColor = MaterialTheme.colorScheme.primary
                        ),
                        textStyle = LocalTextStyle.current.copy(fontSize = 17.sp)
                    )
                }
            }

            // Media type — segmented control
            item {
                Column(modifier = Modifier.padding(horizontal = 20.dp)) {
                    Text(
                        "MEDIA TYPE",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface.copy(0.4f),
                        letterSpacing = 0.5.sp,
                        modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
                    )
                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                        val options = listOf(
                            "Mix" to MediaFilterType.MIXED,
                            "Photos" to MediaFilterType.PHOTOS_ONLY,
                            "Videos" to MediaFilterType.VIDEO_ONLY
                        )
                        options.forEachIndexed { index, (label, type) ->
                            SegmentedButton(
                                selected = selectedFilter == type,
                                onClick = { selectedFilter = type },
                                shape = SegmentedButtonDefaults.itemShape(
                                    index = index,
                                    count = options.size
                                ),
                                colors = SegmentedButtonDefaults.colors(
                                    activeContainerColor = MaterialTheme.colorScheme.primary.copy(0.15f),
                                    activeContentColor = MaterialTheme.colorScheme.primary,
                                    inactiveContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                                    inactiveContentColor = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            ) {
                                Text(label, fontSize = 14.sp)
                            }
                        }
                    }
                }
            }

            // Add content — grouped section with two rows
            item {
                Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp)) {
                    Text(
                        "ADD CONTENT",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface.copy(0.4f),
                        letterSpacing = 0.5.sp,
                        modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
                    )
                    GroupedSection {
                        // Folder row
                        SettingsRow(
                            icon = Icons.Default.Folder,
                            iconTint = MaterialTheme.colorScheme.secondary,
                            title = "Choose Folder",
                            subtitle = "Scan all media in a folder",
                            onClick = {
                                if (!isProcessing) {
                                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R
                                        && Environment.isExternalStorageManager()) {
                                        browserCurrentDir = Environment.getExternalStorageDirectory()
                                        showFolderBrowser = true
                                    } else {
                                        folderPickerLauncher.launch(null)
                                    }
                                }
                            }
                        )
                        HorizontalDivider(
                            modifier = Modifier.padding(start = 56.dp),
                            color = MaterialTheme.colorScheme.outline
                        )
                        // File row
                        SettingsRow(
                            icon = Icons.Default.InsertDriveFile,
                            iconTint = MaterialTheme.colorScheme.primary,
                            title = "Choose File",
                            subtitle = "Pick individual files",
                            onClick = {
                                if (!isProcessing) filePickerLauncher.launch(arrayOf("image/*", "video/*"))
                            }
                        )
                    }
                }
            }

            // Selected items
            if (selectedItems.isNotEmpty()) {
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "SELECTED (${selectedItems.size})",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface.copy(0.4f),
                            letterSpacing = 0.5.sp
                        )
                        TextButton(onClick = { selectedItems.clear() }) {
                            Text(
                                "Clear All",
                                color = MaterialTheme.colorScheme.error,
                                fontSize = 14.sp
                            )
                        }
                    }
                }

                item {
                    GroupedSection(modifier = Modifier.padding(horizontal = 20.dp)) {
                        selectedItems.forEachIndexed { index, (uri, type) ->
                            SelectedItemRow(uri, type) {
                                if (!isProcessing) selectedItems.remove(uri to type)
                            }
                            if (index < selectedItems.lastIndex) {
                                HorizontalDivider(
                                    modifier = Modifier.padding(start = 56.dp),
                                    color = MaterialTheme.colorScheme.outline
                                )
                            }
                        }
                    }
                }
            }

            // Empty state
            if (selectedItems.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 48.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                Icons.Default.AddCircleOutline, null,
                                tint = MaterialTheme.colorScheme.onSurface.copy(0.12f),
                                modifier = Modifier.size(48.dp)
                            )
                            Spacer(Modifier.height(12.dp))
                            Text(
                                "No content added",
                                color = MaterialTheme.colorScheme.onSurface.copy(0.3f),
                                fontSize = 15.sp
                            )
                        }
                    }
                }
            }
        }
    }

    // Simple folder browser dialog — no SAF permission needed
    if (showFolderBrowser) {
        AlertDialog(
            onDismissRequest = { showFolderBrowser = false },
            title = {
                Column {
                    Text("Choose Folder", fontWeight = FontWeight.SemiBold)
                    Text(
                        browserCurrentDir.absolutePath.removePrefix(
                            Environment.getExternalStorageDirectory().absolutePath
                        ).ifEmpty { "/" },
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(0.5f)
                    )
                }
            },
            text = {
                val subDirs = remember(browserCurrentDir) {
                    browserCurrentDir.listFiles()
                        ?.filter { it.isDirectory && !it.name.startsWith(".") }
                        ?.sortedBy { it.name.lowercase() }
                        ?: emptyList()
                }
                LazyColumn(modifier = Modifier.heightIn(max = 400.dp)) {
                    // Go up button
                    if (browserCurrentDir != Environment.getExternalStorageDirectory()) {
                        item {
                            Surface(
                                onClick = { browserCurrentDir = browserCurrentDir.parentFile ?: browserCurrentDir },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.ArrowUpward, null, modifier = Modifier.size(20.dp))
                                    Spacer(Modifier.width(12.dp))
                                    Text("..", fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                    items(subDirs) { dir ->
                        Surface(
                            onClick = { browserCurrentDir = dir },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.Folder, null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(Modifier.width(12.dp))
                                Text(dir.name, maxLines = 1)
                            }
                        }
                    }
                    if (subDirs.isEmpty()) {
                        item {
                            Text(
                                "No subfolders",
                                modifier = Modifier.padding(16.dp),
                                color = MaterialTheme.colorScheme.onSurface.copy(0.4f)
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    selectedItems.add(Uri.fromFile(browserCurrentDir) to ItemType.FOLDER)
                    showFolderBrowser = false
                }) { Text("Select This Folder") }
            },
            dismissButton = {
                TextButton(onClick = { showFolderBrowser = false }) { Text("Cancel") }
            },
            shape = RoundedCornerShape(20.dp)
        )
    }
}

@Composable
fun GroupedSection(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Column(content = content)
    }
}

@Composable
fun SettingsRow(
    icon: ImageVector,
    iconTint: Color,
    title: String,
    subtitle: String? = null,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .background(iconTint.copy(0.15f), RoundedCornerShape(8.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, null, tint = iconTint, modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                title,
                fontSize = 16.sp,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (subtitle != null) {
                Text(
                    subtitle,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Icon(
            Icons.Default.ChevronRight, null,
            tint = MaterialTheme.colorScheme.onSurface.copy(0.2f),
            modifier = Modifier.size(20.dp)
        )
    }
}

@Composable
fun SelectedItemRow(uri: Uri, type: ItemType, onRemove: () -> Unit) {
    val displayName = getDisplayName(uri, type)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .background(
                    if (type == ItemType.FOLDER) MaterialTheme.colorScheme.secondary.copy(0.15f)
                    else MaterialTheme.colorScheme.primary.copy(0.15f),
                    RoundedCornerShape(8.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                if (type == ItemType.FOLDER) Icons.Default.Folder else Icons.Default.InsertDriveFile,
                null,
                tint = if (type == ItemType.FOLDER) MaterialTheme.colorScheme.secondary
                else MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp)
            )
        }
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                displayName,
                fontSize = 15.sp,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                fontWeight = FontWeight.Medium
            )
            Text(
                if (type == ItemType.FOLDER) "Folder" else "File",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        IconButton(onClick = onRemove, modifier = Modifier.size(32.dp)) {
            Icon(
                Icons.Default.Close, "Remove",
                tint = MaterialTheme.colorScheme.onSurface.copy(0.3f),
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

private fun getDisplayName(uri: Uri, type: ItemType): String {
    val uriStr = uri.toString()
    return try {
        val decoded = URLDecoder.decode(uriStr, "UTF-8")
        val segments = decoded.split("/")
        val lastSegment = segments.lastOrNull() ?: "Unknown"
        lastSegment
            .replace("primary:", "")
            .replace("tree/primary/", "")
            .split(":").lastOrNull() ?: lastSegment
    } catch (e: Exception) {
        uri.lastPathSegment ?: "Unknown"
    }
}

// Kept for backward compatibility
@Composable
fun BigActionButton(
    icon: ImageVector,
    text: String,
    modifier: Modifier,
    onClick: () -> Unit,
    enabled: Boolean = true
) {
    // No-op, replaced by SettingsRow
}

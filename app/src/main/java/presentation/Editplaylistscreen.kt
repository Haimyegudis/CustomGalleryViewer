package com.example.customgalleryviewer.presentation

import android.net.Uri
import android.os.Environment
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditPlaylistScreen(
    playlistId: Long,
    onBackClick: () -> Unit,
    viewModel: EditPlaylistViewModel = hiltViewModel()
) {
    val playlistName by viewModel.playlistName.collectAsState()
    val items by viewModel.items.collectAsState()
    var showRenameDialog by remember { mutableStateOf(false) }
    var renameText by remember(playlistName) { mutableStateOf(playlistName) }

    val filePickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isNotEmpty()) viewModel.addItems(uris)
    }

    var showFolderBrowser by remember { mutableStateOf(false) }
    var browserCurrentDir by remember { mutableStateOf(Environment.getExternalStorageDirectory()) }

    val folderPickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        uri?.let { viewModel.addFolder(it) }
    }

    LaunchedEffect(playlistId) {
        viewModel.loadPlaylist(playlistId)
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {},
                navigationIcon = {
                    TextButton(onClick = onBackClick) {
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
                    TextButton(onClick = { viewModel.savePlaylist(); onBackClick() }) {
                        Text(
                            "Save",
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
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
            contentPadding = PaddingValues(bottom = 100.dp)
        ) {
            // Large title
            item {
                Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
                    Text(
                        "Edit Collection",
                        fontSize = 34.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    if (playlistName.isNotEmpty()) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clickable { showRenameDialog = true }
                                .padding(top = 2.dp)
                        ) {
                            Text(
                                playlistName,
                                fontSize = 17.sp,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(Modifier.width(6.dp))
                            Icon(
                                Icons.Default.Edit, "Rename",
                                tint = MaterialTheme.colorScheme.primary.copy(0.6f),
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                }
            }

            // Add content section
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
                        SettingsRow(
                            icon = Icons.Default.Folder,
                            iconTint = MaterialTheme.colorScheme.secondary,
                            title = "Add Folder",
                            subtitle = "Scan all media in a folder",
                            onClick = {
                                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R
                                    && Environment.isExternalStorageManager()) {
                                    browserCurrentDir = Environment.getExternalStorageDirectory()
                                    showFolderBrowser = true
                                } else {
                                    folderPickerLauncher.launch(null)
                                }
                            }
                        )
                        HorizontalDivider(
                            modifier = Modifier.padding(start = 56.dp),
                            color = MaterialTheme.colorScheme.outline
                        )
                        SettingsRow(
                            icon = Icons.Default.InsertDriveFile,
                            iconTint = MaterialTheme.colorScheme.primary,
                            title = "Add Files",
                            subtitle = "Pick individual files",
                            onClick = { filePickerLauncher.launch(arrayOf("image/*", "video/*")) }
                        )
                    }
                }
            }

            if (items.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 48.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                Icons.Default.PhotoLibrary, null,
                                modifier = Modifier.size(48.dp),
                                tint = MaterialTheme.colorScheme.onSurface.copy(0.12f)
                            )
                            Spacer(Modifier.height(12.dp))
                            Text(
                                "No items yet",
                                color = MaterialTheme.colorScheme.onSurface.copy(0.3f),
                                fontSize = 15.sp
                            )
                        }
                    }
                }
            } else {
                // Items header
                item {
                    Text(
                        "ITEMS (${items.size})",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface.copy(0.4f),
                        letterSpacing = 0.5.sp,
                        modifier = Modifier.padding(start = 24.dp, top = 8.dp, bottom = 8.dp)
                    )
                }

                item {
                    GroupedSection(modifier = Modifier.padding(horizontal = 20.dp)) {
                        items.forEachIndexed { index, uri ->
                            EditItemRow(
                                uri = uri,
                                onRemove = { viewModel.removeItem(uri) }
                            )
                            if (index < items.lastIndex) {
                                HorizontalDivider(
                                    modifier = Modifier.padding(start = 56.dp),
                                    color = MaterialTheme.colorScheme.outline
                                )
                            }
                        }
                    }
                }
            }
        }
    }

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
                    viewModel.addFolder(Uri.fromFile(browserCurrentDir))
                    showFolderBrowser = false
                }) { Text("Select This Folder") }
            },
            dismissButton = {
                TextButton(onClick = { showFolderBrowser = false }) { Text("Cancel") }
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
                        viewModel.renamePlaylist(renameText.trim())
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
private fun EditItemRow(
    uri: Uri,
    onRemove: () -> Unit
) {
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
                    MaterialTheme.colorScheme.primary.copy(0.12f),
                    RoundedCornerShape(8.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Default.InsertDriveFile, null,
                tint = MaterialTheme.colorScheme.primary.copy(0.7f),
                modifier = Modifier.size(18.dp)
            )
        }
        Spacer(Modifier.width(14.dp))
        Text(
            text = uri.lastPathSegment ?: "Unknown",
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = 15.sp,
            maxLines = 1,
            modifier = Modifier.weight(1f)
        )
        IconButton(onClick = onRemove, modifier = Modifier.size(32.dp)) {
            Icon(
                Icons.Default.RemoveCircleOutline, "Remove",
                tint = MaterialTheme.colorScheme.error.copy(0.7f),
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

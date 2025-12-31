package com.example.customgalleryviewer.presentation

import android.net.Uri
import android.os.Environment
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.customgalleryviewer.data.ItemType
import com.example.customgalleryviewer.data.MediaFilterType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

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

    // מצבים לניהול ה-UI
    var showInternalFolderPicker by remember { mutableStateOf(false) }
    var isProcessing by remember { mutableStateOf(false) }

    // --- Launcher לקבצים בודדים (עדיין נשתמש במערכת לקבצים בודדים) ---
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let {
            selectedItems.add(it to ItemType.FILE)
        }
    }

    fun trySave() {
        if (playlistName.isBlank()) {
            Toast.makeText(context, "Playlist name is required", Toast.LENGTH_SHORT).show()
        } else if (selectedItems.isEmpty()) {
            Toast.makeText(context, "Select content first", Toast.LENGTH_SHORT).show()
        } else {
            isProcessing = true
            viewModel.createPlaylist(playlistName, selectedItems, selectedFilter) {
                isProcessing = false
                onBack()
            }
        }
    }

    // אם בוחר התיקיות הפנימי פתוח - מציגים אותו על כל המסך
    if (showInternalFolderPicker) {
        InternalFolderPicker(
            onFolderSelected = { file ->
                selectedItems.add(Uri.fromFile(file) to ItemType.FOLDER)
                showInternalFolderPicker = false
            },
            onCancel = { showInternalFolderPicker = false }
        )
        return // יוצאים מהפונקציה כדי לא לצייר את שאר המסך מתחת
    }

    Scaffold(
        topBar = {
            MediumTopAppBar(
                title = { Text("New Playlist") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Back") }
                }
            )
        },
        bottomBar = {
            Box(modifier = Modifier.padding(16.dp)) {
                Button(
                    onClick = { trySave() },
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    enabled = !isProcessing
                ) {
                    if (isProcessing) CircularProgressIndicator(color = Color.White)
                    else Text("Create Playlist")
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // שם
            OutlinedTextField(
                value = playlistName,
                onValueChange = { playlistName = it },
                label = { Text("Playlist Name") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            // פילטר
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(
                    "Mixed" to MediaFilterType.MIXED,
                    "Photos" to MediaFilterType.PHOTOS_ONLY,
                    "Video" to MediaFilterType.VIDEO_ONLY
                ).forEach { (label, type) ->
                    FilterChip(
                        selected = selectedFilter == type,
                        onClick = { selectedFilter = type },
                        label = { Text(label) },
                        leadingIcon = if (selectedFilter == type) { { Icon(Icons.Default.Check, null) } } else null
                    )
                }
            }

            Divider()

            // כפתורי הוספה
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                // כפתור תיקייה - פותח את הבוחר הפנימי שלנו
                BigActionButton(
                    icon = Icons.Default.FolderOpen,
                    text = "Select Folder",
                    modifier = Modifier.weight(1f),
                    onClick = { showInternalFolderPicker = true }
                )
                // כפתור קובץ - פותח את הבוחר של המערכת
                BigActionButton(
                    icon = Icons.Default.InsertDriveFile,
                    text = "Select File",
                    modifier = Modifier.weight(1f),
                    onClick = { filePickerLauncher.launch(arrayOf("*/*")) }
                )
            }

            // רשימה
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(selectedItems) { (uri, type) ->
                    SelectedItemCard(uri, type) { selectedItems.remove(uri to type) }
                }
            }
        }
    }
}

// --- רכיב בוחר תיקיות פנימי (כמו במחשב) ---
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InternalFolderPicker(
    onFolderSelected: (File) -> Unit,
    onCancel: () -> Unit
) {
    // מתחילים מהתיקייה הראשית של הטלפון
    var currentPath by remember { mutableStateOf(Environment.getExternalStorageDirectory()) }
    val files = remember(currentPath) {
        // מציג רק תיקיות, מסנן קבצים, וממיין לפי א-ב
        currentPath.listFiles()
            ?.filter { it.isDirectory && !it.isHidden }
            ?.sortedBy { it.name.lowercase() }
            ?: emptyList()
    }

    // טיפול בכפתור "חזור" של הטלפון
    BackHandler {
        if (currentPath == Environment.getExternalStorageDirectory()) {
            onCancel()
        } else {
            currentPath = currentPath.parentFile ?: currentPath
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(currentPath.name, style = MaterialTheme.typography.titleMedium, maxLines = 1)
                },
                navigationIcon = {
                    IconButton(onClick = {
                        if (currentPath == Environment.getExternalStorageDirectory()) onCancel()
                        else currentPath = currentPath.parentFile ?: currentPath
                    }) {
                        Icon(Icons.Default.ArrowBack, null)
                    }
                }
            )
        },
        bottomBar = {
            Button(
                onClick = { onFolderSelected(currentPath) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .height(56.dp)
            ) {
                Text("Select This Folder")
            }
        }
    ) { padding ->
        LazyColumn(modifier = Modifier.padding(padding)) {
            if (files.isEmpty()) {
                item {
                    Text(
                        "Empty Folder",
                        modifier = Modifier.fillMaxWidth().padding(32.dp),
                        style = MaterialTheme.typography.bodyLarge,
                        color = Color.Gray
                    )
                }
            }
            items(files) { file ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { currentPath = file } // נכנס לתיקייה בלחיצה
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Folder, null, tint = MaterialTheme.colorScheme.secondary)
                    Spacer(Modifier.width(16.dp))
                    Text(file.name, style = MaterialTheme.typography.bodyLarge)
                }
                Divider(color = Color.LightGray.copy(alpha = 0.3f))
            }
        }
    }
}

// --- כרטיסיות עיצוב ---

@Composable
fun SelectedItemCard(uri: Uri, type: ItemType, onRemove: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                if (type == ItemType.FOLDER) Icons.Default.Folder else Icons.Default.InsertDriveFile,
                null, tint = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.width(12.dp))
            Text(
                uri.path?.split("/")?.lastOrNull() ?: "Unknown",
                modifier = Modifier.weight(1f),
                maxLines = 1
            )
            IconButton(onClick = onRemove) {
                Icon(Icons.Default.Close, "Remove", tint = Color.Gray)
            }
        }
    }
}

@Composable
fun BigActionButton(icon: ImageVector, text: String, modifier: Modifier, onClick: () -> Unit) {
    Column(
        modifier = modifier
            .height(80.dp)
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
            .clip(RoundedCornerShape(12.dp))
            .clickable { onClick() }
            .background(MaterialTheme.colorScheme.surface),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
        Text(text)
    }
}
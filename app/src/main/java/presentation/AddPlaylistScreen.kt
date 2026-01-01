package com.example.customgalleryviewer.presentation

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.customgalleryviewer.data.ItemType
import com.example.customgalleryviewer.data.MediaFilterType

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddPlaylistScreen(
    onBack: () -> Unit,
    viewModel: AddPlaylistViewModel = hiltViewModel()
) {
    var playlistName by remember { mutableStateOf("") }
    var selectedFilter by remember { mutableStateOf(MediaFilterType.MIXED) }
    // שימוש ב-SnapshotStateList לעדכון מידי של הממשק
    val selectedItems = remember { mutableStateListOf<Pair<Uri, ItemType>>() }
    val context = LocalContext.current
    var isProcessing by remember { mutableStateOf(false) }

    // --- Launcher לקבצים (File Picker) ---
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let {
            // הוספת הרשאה מתמשכת לקובץ (חשוב לגישה עתידית)
            try {
                context.contentResolver.takePersistableUriPermission(
                    it,
                    android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (e: Exception) {
                e.printStackTrace()
            }
            selectedItems.add(it to ItemType.FILE)
        }
    }

    // --- Launcher לתיקיות (Folder Picker - USB Support) ---
    // זה פותר את הבעיה: משתמשים בבוחר המערכת שתומך ב-USB
    val folderPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        uri?.let {
            try {
                // הוספת הרשאה מתמשכת לתיקייה
                context.contentResolver.takePersistableUriPermission(
                    it,
                    android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (e: Exception) {
                e.printStackTrace()
            }
            selectedItems.add(it to ItemType.FOLDER)
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
            // שם הפלייליסט
            OutlinedTextField(
                value = playlistName,
                onValueChange = { playlistName = it },
                label = { Text("Playlist Name") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            // בחירת סוג מדיה (פילטר)
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
                // כפתור בחירת תיקייה (עכשיו תומך ב-USB דרך המערכת)
                BigActionButton(
                    icon = Icons.Default.FolderOpen,
                    text = "Select Folder",
                    modifier = Modifier.weight(1f),
                    onClick = { folderPickerLauncher.launch(null) }
                )
                // כפתור בחירת קובץ
                BigActionButton(
                    icon = Icons.Default.InsertDriveFile,
                    text = "Select File",
                    modifier = Modifier.weight(1f),
                    onClick = { filePickerLauncher.launch(arrayOf("*/*")) }
                )
            }

            // רשימת הפריטים שנבחרו
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(selectedItems) { (uri, type) ->
                    SelectedItemCard(uri, type) { selectedItems.remove(uri to type) }
                }
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
            // ניסיון להציג שם קריא מה-URI
            val displayName = uri.path?.split("/")?.lastOrNull()?.replace("primary:", "") ?: "Selected Item"
            Text(
                displayName,
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
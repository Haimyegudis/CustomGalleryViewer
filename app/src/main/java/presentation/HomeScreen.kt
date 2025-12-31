package com.example.customgalleryviewer.presentation

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.example.customgalleryviewer.data.PlaylistWithItems
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onNavigateToPlayer: (Long) -> Unit,
    onNavigateToAdd: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val playlists by viewModel.playlists.collectAsState()

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= 30 && !Environment.isExternalStorageManager()) {
            Toast.makeText(context, "Grant 'All files access' to see thumbnails", Toast.LENGTH_LONG).show()
            try {
                val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                intent.data = Uri.parse("package:${context.packageName}")
                context.startActivity(intent)
            } catch (e: Exception) {
                context.startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
            }
        }
        viewModel.loadPlaylists()
    }

    Scaffold(
        topBar = {
            LargeTopAppBar(
                title = { Text("Playlists", fontWeight = FontWeight.ExtraBold) },
                colors = TopAppBarDefaults.largeTopAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onNavigateToAdd, containerColor = MaterialTheme.colorScheme.primary) {
                Icon(Icons.Default.Add, "Add")
            }
        }
    ) { padding ->
        if (playlists.isEmpty()) {
            EmptyStateView(padding)
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(playlists) { item ->
                    ModernPlaylistCard(item, { onNavigateToPlayer(item.playlist.id) }, { viewModel.deletePlaylist(item.playlist) })
                }
            }
        }
    }
}

@Composable
fun ModernPlaylistCard(item: PlaylistWithItems, onClick: () -> Unit, onDelete: () -> Unit) {
    val date = SimpleDateFormat("MMM dd", Locale.getDefault()).format(Date(item.playlist.createdAt))
    var thumbnailUri by remember { mutableStateOf<Uri?>(null) }

    // טעינת תמונה ברקע
    LaunchedEffect(item) {
        withContext(Dispatchers.IO) {
            thumbnailUri = getImageUriForPlaylist(item)
        }
    }

    val bg = if (thumbnailUri == null) Brush.linearGradient(listOf(Color(0xFF6200EE), Color(0xFF03DAC5))) else SolidColor(Color.Black)

    Card(modifier = Modifier.fillMaxWidth().height(110.dp).clickable { onClick() }, shape = RoundedCornerShape(16.dp), elevation = CardDefaults.cardElevation(4.dp)) {
        Row(modifier = Modifier.fillMaxSize()) {
            Box(modifier = Modifier.width(110.dp).fillMaxHeight().background(bg), contentAlignment = Alignment.Center) {
                if (thumbnailUri != null) {
                    AsyncImage(model = thumbnailUri, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                    Box(Modifier.fillMaxSize().background(Color.Black.copy(0.3f)))
                } else {
                    Icon(Icons.Default.Folder, null, tint = Color.White, modifier = Modifier.size(40.dp))
                }
                Icon(Icons.Default.PlayArrow, null, tint = Color.White.copy(0.9f), modifier = Modifier.size(32.dp))
            }
            Column(modifier = Modifier.weight(1f).padding(12.dp), verticalArrangement = Arrangement.SpaceBetween) {
                Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                    Text(text = item.playlist.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 1, modifier = Modifier.weight(1f))
                    IconButton(onClick = onDelete, modifier = Modifier.size(24.dp)) { Icon(Icons.Default.Delete, "Delete", tint = Color.Gray) }
                }
                Text("$date • ${item.items.size} sources", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
            }
        }
    }
}

fun getImageUriForPlaylist(item: PlaylistWithItems): Uri? {
    try {
        val firstItem = item.items.firstOrNull() ?: return null
        // אם זה קובץ - החזר אותו
        if (firstItem.type.name == "FILE") return Uri.parse(firstItem.uriString)

        // אם זו תיקייה - נסה למצוא תמונה ראשונה בתוכה
        val rawPath = Uri.parse(firstItem.uriString).path ?: return null
        // המרה גסה לנתיב קובץ (מותאם למה שעשינו ב-FileScanner)
        val cleanPath = if (rawPath.contains("primary:")) "/storage/emulated/0/" + rawPath.split("primary:")[1] else rawPath

        val folder = File(cleanPath)
        if (folder.exists() && folder.isDirectory) {
            val files = folder.listFiles()
            // מחפש את התמונה הראשונה
            val firstImage = files?.firstOrNull {
                val name = it.name.lowercase()
                name.endsWith(".jpg") || name.endsWith(".png") || name.endsWith(".jpeg")
            }
            if (firstImage != null) return Uri.fromFile(firstImage)
        }
    } catch (e: Exception) {
        return null
    }
    return null
}

@Composable
fun EmptyStateView(padding: PaddingValues) {
    Column(modifier = Modifier.fillMaxSize().padding(padding), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(Icons.Default.Collections, null, modifier = Modifier.size(60.dp), tint = Color.Gray)
        Text("No Playlists", style = MaterialTheme.typography.headlineSmall)
    }
}
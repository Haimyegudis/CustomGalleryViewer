package com.example.customgalleryviewer.presentation.components

import android.content.Context
import android.net.Uri
import android.os.Environment
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
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
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import android.media.MediaScannerConnection
import coil.compose.AsyncImage
import coil.decode.VideoFrameDecoder
import coil.request.ImageRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

enum class FileOperation { COPY, MOVE }

@Composable
fun FolderPickerDialog(
    uri: Uri,
    operation: FileOperation,
    onDismiss: () -> Unit,
    onComplete: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val rootDir = Environment.getExternalStorageDirectory()
    var currentDir by remember { mutableStateOf(rootDir) }
    var subFolders by remember { mutableStateOf(listOf<File>()) }
    var previewFiles by remember { mutableStateOf(listOf<Uri>()) }
    var showNewFolderDialog by remember { mutableStateOf(false) }
    var isProcessing by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }

    val mediaExtensions = remember { setOf("jpg","jpeg","png","webp","bmp","gif","heic","heif","avif","mp4","mkv","avi","mov","flv","wmv","3gp","webm","m4v","m3u8") }

    LaunchedEffect(currentDir) {
        isLoading = true
        withContext(Dispatchers.IO) {
            val allItems = currentDir.listFiles() ?: emptyArray()
            val dirs = allItems.filter { it.isDirectory && !it.isHidden }.sortedBy { it.name.lowercase() }
            val files = allItems.filter { it.isFile && !it.isHidden && it.extension.lowercase() in mediaExtensions }
                .sortedBy { it.name.lowercase() }
                .map { Uri.fromFile(it) }
            withContext(Dispatchers.Main) {
                subFolders = dirs
                previewFiles = files
                isLoading = false
            }
        }
    }

    val title = if (operation == FileOperation.COPY) "Copy to" else "Move to"

    Dialog(
        onDismissRequest = { if (!isProcessing) onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .fillMaxHeight(0.85f),
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        if (operation == FileOperation.COPY) Icons.Default.FileCopy else Icons.Default.DriveFileMove,
                        null, tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(title, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                }

                // Current path + back + new folder
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (currentDir.absolutePath != rootDir.absolutePath) {
                        IconButton(onClick = { currentDir.parentFile?.let { currentDir = it } }) {
                            Icon(Icons.Default.ArrowBack, "Back")
                        }
                    }
                    Text(
                        currentDir.absolutePath.removePrefix(rootDir.absolutePath).ifEmpty { "/" },
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = { showNewFolderDialog = true }) {
                        Icon(Icons.Default.CreateNewFolder, "New Folder", tint = MaterialTheme.colorScheme.primary)
                    }
                }

                // File count info
                if (!isLoading && !isProcessing) {
                    Text(
                        "${subFolders.size} folders, ${previewFiles.size} files",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                    )
                }

                HorizontalDivider(Modifier.padding(horizontal = 16.dp))

                // Content
                if (isProcessing) {
                    Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator()
                            Spacer(Modifier.height(8.dp))
                            Text(if (operation == FileOperation.COPY) "Copying..." else "Moving...")
                        }
                    }
                } else if (isLoading) {
                    Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(3),
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        contentPadding = PaddingValues(8.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        // Folders as navigable tiles
                        items(subFolders.size) { idx ->
                            val dir = subFolders[idx]
                            // Load thumbnail asynchronously to avoid blocking
                            var thumbUri by remember(dir) { mutableStateOf<Uri?>(null) }
                            var itemCount by remember(dir) { mutableIntStateOf(-1) }
                            LaunchedEffect(dir) {
                                withContext(Dispatchers.IO) {
                                    val files = dir.listFiles()
                                    val count = files?.size ?: 0
                                    val thumb = files?.firstOrNull { f ->
                                        f.isFile && f.extension.lowercase() in setOf("jpg","jpeg","png","webp","mp4","mkv","avi","mov")
                                    }?.let { Uri.fromFile(it) }
                                    withContext(Dispatchers.Main) { thumbUri = thumb; itemCount = count }
                                }
                            }
                            Surface(
                                onClick = { currentDir = dir },
                                modifier = Modifier.aspectRatio(1f),
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant
                            ) {
                                Box(modifier = Modifier.fillMaxSize()) {
                                    if (thumbUri != null) {
                                        AsyncImage(
                                            model = ImageRequest.Builder(context)
                                                .data(thumbUri)
                                                .decoderFactory(VideoFrameDecoder.Factory())
                                                .size(150).crossfade(false).allowHardware(true).build(),
                                            contentDescription = null,
                                            modifier = Modifier.fillMaxSize(),
                                            contentScale = ContentScale.Crop
                                        )
                                        Box(Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Color.Black.copy(0.4f)))
                                    }
                                    Column(
                                        modifier = Modifier.fillMaxSize().padding(6.dp),
                                        verticalArrangement = Arrangement.Center,
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Icon(Icons.Default.Folder, null,
                                            tint = if (thumbUri != null) androidx.compose.ui.graphics.Color.White else MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(28.dp))
                                        Text(dir.name, fontSize = 11.sp, maxLines = 2, overflow = TextOverflow.Ellipsis,
                                            color = if (thumbUri != null) androidx.compose.ui.graphics.Color.White else MaterialTheme.colorScheme.onSurface,
                                            fontWeight = FontWeight.Medium,
                                            modifier = Modifier.padding(top = 2.dp))
                                        if (itemCount > 0) {
                                            Text("$itemCount items", fontSize = 9.sp,
                                                color = if (thumbUri != null) androidx.compose.ui.graphics.Color.White.copy(0.7f) else MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    }
                                }
                            }
                        }
                        // Media file previews (non-clickable, just for context)
                        items(previewFiles.size) { idx ->
                            val fileUri = previewFiles[idx]
                            Box(
                                modifier = Modifier.aspectRatio(1f).clip(RoundedCornerShape(6.dp))
                            ) {
                                AsyncImage(
                                    model = ImageRequest.Builder(context)
                                        .data(fileUri)
                                        .decoderFactory(VideoFrameDecoder.Factory())
                                        .crossfade(false).allowHardware(true).size(150).build(),
                                    contentDescription = null,
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop,
                                    error = ColorPainter(MaterialTheme.colorScheme.surfaceVariant)
                                )
                            }
                        }
                        if (subFolders.isEmpty() && previewFiles.isEmpty()) {
                            item(span = { GridItemSpan(3) }) {
                                Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                                    Text("Empty folder", color = MaterialTheme.colorScheme.onSurface.copy(0.5f))
                                }
                            }
                        }
                    }
                }

                HorizontalDivider()

                // Bottom buttons
                Row(
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss, enabled = !isProcessing) {
                        Text("Cancel")
                    }
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = {
                            isProcessing = true
                            scope.launch(Dispatchers.IO) {
                                val success = performFileOperation(context, uri, currentDir, operation)
                                withContext(Dispatchers.Main) {
                                    isProcessing = false
                                    if (success) {
                                        Toast.makeText(
                                            context,
                                            if (operation == FileOperation.COPY) "Copied" else "Moved",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                        try { onComplete() } catch (_: Exception) { onDismiss() }
                                    } else {
                                        Toast.makeText(context, "Operation failed", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }
                        },
                        enabled = !isProcessing
                    ) {
                        Text("${title} here")
                    }
                }
            }
        }
    }

    // New folder dialog
    if (showNewFolderDialog) {
        var folderName by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showNewFolderDialog = false },
            title = { Text("New Folder") },
            text = {
                OutlinedTextField(
                    value = folderName,
                    onValueChange = { folderName = it },
                    label = { Text("Folder name") },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (folderName.isNotBlank()) {
                        val newDir = File(currentDir, folderName.trim())
                        if (newDir.mkdirs() || newDir.exists()) {
                            currentDir = newDir
                        } else {
                            Toast.makeText(context, "Failed to create folder", Toast.LENGTH_SHORT).show()
                        }
                    }
                    showNewFolderDialog = false
                }) { Text("Create") }
            },
            dismissButton = {
                TextButton(onClick = { showNewFolderDialog = false }) { Text("Cancel") }
            },
            shape = RoundedCornerShape(20.dp)
        )
    }
}

private fun performFileOperation(context: Context, uri: Uri, destDir: File, operation: FileOperation): Boolean {
    return try {
        val sourceFile = when {
            uri.scheme == "file" && uri.path != null -> File(uri.path!!)
            else -> {
                val name = uri.lastPathSegment?.substringAfterLast('/') ?: "file"
                val destFile = File(destDir, name)
                context.contentResolver.openInputStream(uri)?.use { input ->
                    destFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
                if (destFile.exists()) {
                    MediaScannerConnection.scanFile(context, arrayOf(destFile.absolutePath), null, null)
                }
                return destFile.exists()
            }
        }

        if (!sourceFile.exists()) return false

        val destFile = File(destDir, sourceFile.name)
        if (destFile.absolutePath == sourceFile.absolutePath) return false

        // Handle name collision
        val finalDest = if (destFile.exists()) {
            val baseName = destFile.nameWithoutExtension
            val ext = destFile.extension
            var counter = 1
            var candidate: File
            do {
                candidate = File(destDir, "${baseName} ($counter)${if (ext.isNotEmpty()) ".$ext" else ""}")
                counter++
            } while (candidate.exists())
            candidate
        } else destFile

        if (operation == FileOperation.COPY) {
            sourceFile.copyTo(finalDest, overwrite = false)
            MediaScannerConnection.scanFile(context, arrayOf(finalDest.absolutePath), null, null)
        } else {
            // Try rename first (fast, same filesystem)
            val renamed = sourceFile.renameTo(finalDest)
            if (!renamed) {
                // Fallback: copy then delete source
                sourceFile.copyTo(finalDest, overwrite = false)
                if (finalDest.exists()) {
                    sourceFile.delete()
                }
            }
            // Notify MediaStore: remove old, add new
            val sourcePath = sourceFile.absolutePath
            MediaScannerConnection.scanFile(context, arrayOf(sourcePath, finalDest.absolutePath), null, null)
            // Also remove from MediaStore directly if file is gone
            if (!sourceFile.exists()) {
                try {
                    context.contentResolver.delete(
                        android.provider.MediaStore.Files.getContentUri("external"),
                        "${android.provider.MediaStore.MediaColumns.DATA}=?",
                        arrayOf(sourcePath)
                    )
                } catch (_: Exception) {}
            }
        }
        finalDest.exists()
    } catch (e: Exception) {
        android.util.Log.e("FolderPicker", "File operation failed", e)
        false
    }
}

@Composable
fun MultiFolderPickerDialog(
    uris: List<Uri>,
    operation: FileOperation,
    onDismiss: () -> Unit,
    onComplete: (File) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val rootDir = Environment.getExternalStorageDirectory()
    var currentDir by remember { mutableStateOf(rootDir) }
    var subFolders by remember { mutableStateOf(listOf<File>()) }
    var previewFiles by remember { mutableStateOf(listOf<Uri>()) }
    var showNewFolderDialog by remember { mutableStateOf(false) }
    var isProcessing by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }
    var progress by remember { mutableIntStateOf(0) }

    val mediaExtensions = remember { setOf("jpg","jpeg","png","webp","bmp","gif","heic","heif","avif","mp4","mkv","avi","mov","flv","wmv","3gp","webm","m4v","m3u8") }

    LaunchedEffect(currentDir) {
        isLoading = true
        withContext(Dispatchers.IO) {
            val allItems = currentDir.listFiles() ?: emptyArray()
            val dirs = allItems.filter { it.isDirectory && !it.isHidden }.sortedBy { it.name.lowercase() }
            val files = allItems.filter { it.isFile && !it.isHidden && it.extension.lowercase() in mediaExtensions }
                .sortedBy { it.name.lowercase() }.map { Uri.fromFile(it) }
            withContext(Dispatchers.Main) { subFolders = dirs; previewFiles = files; isLoading = false }
        }
    }

    val title = if (operation == FileOperation.COPY) "Copy ${uris.size} files to" else "Move ${uris.size} files to"

    Dialog(
        onDismissRequest = { if (!isProcessing) onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(0.94f).fillMaxHeight(0.85f),
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(if (operation == FileOperation.COPY) Icons.Default.FileCopy else Icons.Default.DriveFileMove, null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(12.dp))
                    Text(title, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                }
                Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (currentDir.absolutePath != rootDir.absolutePath) {
                        IconButton(onClick = { currentDir.parentFile?.let { currentDir = it } }) { Icon(Icons.Default.ArrowBack, "Back") }
                    }
                    Text(currentDir.absolutePath.removePrefix(rootDir.absolutePath).ifEmpty { "/" }, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    IconButton(onClick = { showNewFolderDialog = true }) { Icon(Icons.Default.CreateNewFolder, "New Folder", tint = MaterialTheme.colorScheme.primary) }
                }
                if (!isLoading && !isProcessing) {
                    Text("${subFolders.size} folders, ${previewFiles.size} files", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
                }
                HorizontalDivider(Modifier.padding(horizontal = 16.dp))

                if (isProcessing) {
                    Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) { CircularProgressIndicator(); Spacer(Modifier.height(8.dp)); Text("$progress / ${uris.size}") }
                    }
                } else if (isLoading) {
                    Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(3), modifier = Modifier.weight(1f).fillMaxWidth(),
                        contentPadding = PaddingValues(8.dp), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        items(subFolders.size) { idx ->
                            val dir = subFolders[idx]
                            val thumbFile = remember(dir) { dir.listFiles()?.firstOrNull { f -> f.isFile && f.extension.lowercase() in setOf("jpg","jpeg","png","webp","mp4","mkv","avi","mov") } }
                            Surface(onClick = { currentDir = dir }, modifier = Modifier.aspectRatio(1f), shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
                                Box(modifier = Modifier.fillMaxSize()) {
                                    if (thumbFile != null) {
                                        AsyncImage(model = ImageRequest.Builder(context).data(Uri.fromFile(thumbFile)).decoderFactory(VideoFrameDecoder.Factory()).size(150).crossfade(false).allowHardware(true).build(), contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                                        Box(Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Color.Black.copy(0.4f)))
                                    }
                                    Column(Modifier.fillMaxSize().padding(6.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                                        Icon(Icons.Default.Folder, null, tint = if (thumbFile != null) androidx.compose.ui.graphics.Color.White else MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
                                        Text(dir.name, fontSize = 11.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, color = if (thumbFile != null) androidx.compose.ui.graphics.Color.White else MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Medium, modifier = Modifier.padding(top = 2.dp))
                                    }
                                }
                            }
                        }
                        items(previewFiles.size) { idx ->
                            Box(modifier = Modifier.aspectRatio(1f).clip(RoundedCornerShape(6.dp))) {
                                AsyncImage(model = ImageRequest.Builder(context).data(previewFiles[idx]).decoderFactory(VideoFrameDecoder.Factory()).crossfade(false).allowHardware(true).size(150).build(), contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop, error = ColorPainter(MaterialTheme.colorScheme.surfaceVariant))
                            }
                        }
                        if (subFolders.isEmpty() && previewFiles.isEmpty()) {
                            item(span = { GridItemSpan(3) }) { Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) { Text("Empty folder", color = MaterialTheme.colorScheme.onSurface.copy(0.5f)) } }
                        }
                    }
                }
                HorizontalDivider()
                Row(modifier = Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss, enabled = !isProcessing) { Text("Cancel") }
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = {
                            isProcessing = true
                            val destDir = currentDir
                            scope.launch(Dispatchers.IO) {
                                var count = 0
                                uris.forEach { uri ->
                                    performFileOperation(context, uri, destDir, operation)
                                    count++
                                    withContext(Dispatchers.Main) { progress = count }
                                }
                                withContext(Dispatchers.Main) {
                                    isProcessing = false
                                    Toast.makeText(
                                        context,
                                        if (operation == FileOperation.COPY) "Copied $count files"
                                        else "Moved $count files",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                    onComplete(destDir)
                                }
                            }
                        },
                        enabled = !isProcessing
                    ) {
                        Text(if (operation == FileOperation.COPY) "Copy here" else "Move here")
                    }
                }
            }
        }
    }

    if (showNewFolderDialog) {
        var folderName by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showNewFolderDialog = false },
            title = { Text("New Folder") },
            text = {
                OutlinedTextField(
                    value = folderName,
                    onValueChange = { folderName = it },
                    label = { Text("Folder name") },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (folderName.isNotBlank()) {
                        val newDir = File(currentDir, folderName.trim())
                        if (newDir.mkdirs() || newDir.exists()) { currentDir = newDir }
                        else { Toast.makeText(context, "Failed to create folder", Toast.LENGTH_SHORT).show() }
                    }
                    showNewFolderDialog = false
                }) { Text("Create") }
            },
            dismissButton = {
                TextButton(onClick = { showNewFolderDialog = false }) { Text("Cancel") }
            },
            shape = RoundedCornerShape(20.dp)
        )
    }
}

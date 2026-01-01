package com.example.customgalleryviewer.presentation

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.customgalleryviewer.data.SortOrder

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBackClick: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val playbackSort by viewModel.playbackSort.collectAsState()
    val gallerySort by viewModel.gallerySort.collectAsState()
    val currentNavMode by viewModel.navigationMode.collectAsState()
    val cacheInfo by viewModel.cacheInfo.collectAsState()

    var showClearCacheDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) { Icon(Icons.Default.ArrowBack, "Back") }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            // --- 1. Gallery Display Sort ---
            Text("Gallery Display Order", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            Text("How images appear in grid view", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(bottom = 8.dp))

            SortOrderOption("By Name", "Alphabetical", gallerySort == SortOrder.BY_NAME) { viewModel.setGallerySort(SortOrder.BY_NAME) }
            Spacer(Modifier.height(4.dp))
            SortOrderOption("By Date", "Newest First", gallerySort == SortOrder.BY_DATE) { viewModel.setGallerySort(SortOrder.BY_DATE) }

            Divider(Modifier.padding(vertical = 16.dp))

            // --- 2. Playback Sequence ---
            Text("Playback Sequence", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            Text("Order when swiping/tapping next", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(bottom = 8.dp))

            SortOrderOption("By Name", "Alphabetical", playbackSort == SortOrder.BY_NAME) { viewModel.setPlaybackSort(SortOrder.BY_NAME) }
            Spacer(Modifier.height(4.dp))
            SortOrderOption("By Date", "Newest First", playbackSort == SortOrder.BY_DATE) { viewModel.setPlaybackSort(SortOrder.BY_DATE) }
            Spacer(Modifier.height(4.dp))
            SortOrderOption("Random", "Shuffle all media", playbackSort == SortOrder.RANDOM) { viewModel.setPlaybackSort(SortOrder.RANDOM) }

            Divider(Modifier.padding(vertical = 16.dp))

            // --- 3. Navigation Style ---
            Text("Navigation Style", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(8.dp))

            SortOrderOption("Tap", "Tap sides to navigate", currentNavMode == "TAP") { viewModel.setNavigationMode("TAP") }
            Spacer(Modifier.height(4.dp))
            SortOrderOption("Swipe", "Swipe screen to navigate", currentNavMode == "SWIPE") { viewModel.setNavigationMode("SWIPE") }

            Divider(Modifier.padding(vertical = 16.dp))

            // --- 4. Cache Management ---
            Text("Cache Management", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            Text("Speed up loading by caching scanned folders", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(bottom = 8.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Cached Folders", style = MaterialTheme.typography.bodyLarge)
                            Text(
                                "${cacheInfo.size} folders • ${cacheInfo.values.sumOf { it.fileCount }} files",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        IconButton(
                            onClick = { showClearCacheDialog = true },
                            enabled = cacheInfo.isNotEmpty()
                        ) {
                            Icon(
                                Icons.Default.Delete,
                                "Clear Cache",
                                tint = if (cacheInfo.isNotEmpty())
                                    MaterialTheme.colorScheme.error
                                else
                                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                            )
                        }
                    }

                    if (cacheInfo.isNotEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Cache saves time on next load. Clear if folders content changed.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }

    // Dialog לאישור ניקוי Cache
    if (showClearCacheDialog) {
        AlertDialog(
            onDismissRequest = { showClearCacheDialog = false },
            title = { Text("Clear Cache?") },
            text = {
                Text("This will remove all cached folder data. Next scan will be slower but will pick up any new files.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.clearCache()
                        showClearCacheDialog = false
                    }
                ) {
                    Text("Clear", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearCacheDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun SortOrderOption(title: String, description: String, isSelected: Boolean, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = title, style = MaterialTheme.typography.bodyLarge)
                Text(text = description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (isSelected) {
                Icon(Icons.Default.Check, "Selected", tint = MaterialTheme.colorScheme.primary)
            }
        }
    }
}
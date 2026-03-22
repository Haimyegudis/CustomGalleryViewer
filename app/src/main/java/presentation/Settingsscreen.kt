package com.example.customgalleryviewer.presentation

import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.customgalleryviewer.data.GestureAction
import com.example.customgalleryviewer.data.GestureType
import com.example.customgalleryviewer.data.SortOrder
import com.nowwhat.customgalleryviewer.ui.theme.accentColorOptions

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
    val showHidden by viewModel.showHidden.collectAsState()
    val themeMode by viewModel.themeMode.collectAsState()
    val accentColor by viewModel.accentColor.collectAsState()

    var showClearCacheDialog by remember { mutableStateOf(false) }

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
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
        ) {
            // Large title
            Text(
                "Settings",
                fontSize = 34.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
            )

            Spacer(Modifier.height(16.dp))

            // Gallery Display Order
            SectionHeader("GALLERY DISPLAY")
            GroupedSection(modifier = Modifier.padding(horizontal = 20.dp)) {
                SelectableRow(
                    title = "By Name",
                    subtitle = "Alphabetical order",
                    isSelected = gallerySort == SortOrder.BY_NAME,
                    onClick = { viewModel.setGallerySort(SortOrder.BY_NAME) }
                )
                InsetDivider()
                SelectableRow(
                    title = "By Date",
                    subtitle = "Newest first",
                    isSelected = gallerySort == SortOrder.BY_DATE,
                    onClick = { viewModel.setGallerySort(SortOrder.BY_DATE) }
                )
                InsetDivider()
                SelectableRow(
                    title = "By Size",
                    subtitle = "Largest first",
                    isSelected = gallerySort == SortOrder.BY_SIZE,
                    onClick = { viewModel.setGallerySort(SortOrder.BY_SIZE) }
                )
                InsetDivider()
                SelectableRow(
                    title = "By Duration",
                    subtitle = "Longest videos first",
                    isSelected = gallerySort == SortOrder.BY_DURATION,
                    onClick = { viewModel.setGallerySort(SortOrder.BY_DURATION) }
                )
            }

            Spacer(Modifier.height(24.dp))

            // Playback Sequence
            SectionHeader("PLAYBACK SEQUENCE")
            GroupedSection(modifier = Modifier.padding(horizontal = 20.dp)) {
                SelectableRow(
                    title = "By Name",
                    subtitle = "Alphabetical order",
                    isSelected = playbackSort == SortOrder.BY_NAME,
                    onClick = { viewModel.setPlaybackSort(SortOrder.BY_NAME) }
                )
                InsetDivider()
                SelectableRow(
                    title = "By Date",
                    subtitle = "Newest first",
                    isSelected = playbackSort == SortOrder.BY_DATE,
                    onClick = { viewModel.setPlaybackSort(SortOrder.BY_DATE) }
                )
                InsetDivider()
                SelectableRow(
                    title = "By Size",
                    subtitle = "Largest first",
                    isSelected = playbackSort == SortOrder.BY_SIZE,
                    onClick = { viewModel.setPlaybackSort(SortOrder.BY_SIZE) }
                )
                InsetDivider()
                SelectableRow(
                    title = "By Duration",
                    subtitle = "Longest videos first",
                    isSelected = playbackSort == SortOrder.BY_DURATION,
                    onClick = { viewModel.setPlaybackSort(SortOrder.BY_DURATION) }
                )
                InsetDivider()
                SelectableRow(
                    title = "Random",
                    subtitle = "Shuffle all media",
                    isSelected = playbackSort == SortOrder.RANDOM,
                    onClick = { viewModel.setPlaybackSort(SortOrder.RANDOM) }
                )
            }

            Spacer(Modifier.height(24.dp))

            // Navigation Style
            SectionHeader("NAVIGATION")
            GroupedSection(modifier = Modifier.padding(horizontal = 20.dp)) {
                SelectableRow(
                    title = "Tap",
                    subtitle = "Tap sides to navigate",
                    isSelected = currentNavMode == "TAP",
                    onClick = { viewModel.setNavigationMode("TAP") }
                )
                InsetDivider()
                SelectableRow(
                    title = "Swipe",
                    subtitle = "Swipe to navigate",
                    isSelected = currentNavMode == "SWIPE",
                    onClick = { viewModel.setNavigationMode("SWIPE") }
                )
            }

            Spacer(Modifier.height(24.dp))

            // Files
            SectionHeader("FILES")
            GroupedSection(modifier = Modifier.padding(horizontal = 20.dp)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { viewModel.setShowHidden(!showHidden) }
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Show Hidden Files", fontSize = 16.sp)
                        Text(
                            "Show files and folders starting with '.'",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = showHidden,
                        onCheckedChange = { viewModel.setShowHidden(it) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = MaterialTheme.colorScheme.primary,
                            checkedTrackColor = MaterialTheme.colorScheme.primary.copy(0.3f)
                        )
                    )
                }
            }

            Spacer(Modifier.height(24.dp))

            // Cache
            SectionHeader("STORAGE")
            GroupedSection(modifier = Modifier.padding(horizontal = 20.dp)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(enabled = cacheInfo.isNotEmpty()) {
                            showClearCacheDialog = true
                        }
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "Clear Cache",
                            fontSize = 16.sp,
                            color = if (cacheInfo.isNotEmpty())
                                MaterialTheme.colorScheme.error
                            else MaterialTheme.colorScheme.onSurface.copy(0.3f)
                        )
                        Text(
                            "${cacheInfo.size} folders cached",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (cacheInfo.isNotEmpty()) {
                        Text(
                            "${cacheInfo.values.sumOf { it.fileCount }} files",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(Modifier.height(24.dp))

            // Theme
            SectionHeader("APPEARANCE")
            GroupedSection(modifier = Modifier.padding(horizontal = 20.dp)) {
                SelectableRow(
                    title = "Dark",
                    subtitle = "Dark theme",
                    isSelected = themeMode == "dark",
                    onClick = { viewModel.setThemeMode("dark") }
                )
                InsetDivider()
                SelectableRow(
                    title = "Light",
                    subtitle = "Light theme",
                    isSelected = themeMode == "light",
                    onClick = { viewModel.setThemeMode("light") }
                )
                InsetDivider()
                SelectableRow(
                    title = "AMOLED",
                    subtitle = "Pure black background",
                    isSelected = themeMode == "amoled",
                    onClick = { viewModel.setThemeMode("amoled") }
                )
                InsetDivider()
                SelectableRow(
                    title = "System",
                    subtitle = "Follow system setting",
                    isSelected = themeMode == "system",
                    onClick = { viewModel.setThemeMode("system") }
                )
            }

            Spacer(Modifier.height(16.dp))

            // Accent Color
            SectionHeader("ACCENT COLOR")
            GroupedSection(modifier = Modifier.padding(horizontal = 20.dp)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    accentColorOptions.forEach { (name, color) ->
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(color)
                                .then(
                                    if (accentColor == name) Modifier.border(3.dp, Color.White, CircleShape)
                                    else Modifier
                                )
                                .clickable { viewModel.setAccentColor(name) }
                        ) {
                            if (accentColor == name) {
                                Icon(
                                    Icons.Default.Check,
                                    null,
                                    tint = Color.White,
                                    modifier = Modifier
                                        .size(20.dp)
                                        .align(Alignment.Center)
                                )
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(24.dp))

            // Background Wallpaper (Skin)
            SectionHeader("BACKGROUND SKIN")
            GroupedSection(modifier = Modifier.padding(horizontal = 20.dp)) {
                val skinContext = LocalContext.current
                val currentWallpaper = viewModel.getWallpaper()
                val wallpaperLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
                    androidx.activity.result.contract.ActivityResultContracts.GetContent()
                ) { uri ->
                    if (uri != null) {
                        // Take persistent permission
                        try {
                            skinContext.contentResolver.takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        } catch (_: Exception) {}
                        viewModel.setWallpaper(uri.toString())
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Background Image", fontWeight = FontWeight.Medium)
                        Text(
                            if (currentWallpaper != null) "Custom image set" else "None",
                            fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { wallpaperLauncher.launch("image/*") }) {
                            Text("Choose", fontSize = 13.sp)
                        }
                        if (currentWallpaper != null) {
                            TextButton(onClick = { viewModel.setWallpaper(null) }) {
                                Text("Clear", fontSize = 13.sp, color = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(24.dp))

            // Gesture Customization
            SectionHeader("GESTURES")
            GroupedSection(modifier = Modifier.padding(horizontal = 20.dp)) {
                val gestureSettings = remember { viewModel.getGestureSettings() }
                var gestureMap by remember { mutableStateOf(gestureSettings) }
                var expandedGesture by remember { mutableStateOf<GestureType?>(null) }

                gestureMap.forEach { (gestureType, currentAction) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { expandedGesture = gestureType }
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            gestureType.name.replace("_", " ").lowercase()
                                .replaceFirstChar { it.uppercase() },
                            fontSize = 14.sp,
                            modifier = Modifier.weight(1f)
                        )
                        Box {
                            Text(
                                currentAction.label,
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.primary
                            )
                            DropdownMenu(
                                expanded = expandedGesture == gestureType,
                                onDismissRequest = { expandedGesture = null }
                            ) {
                                GestureAction.entries.forEach { action ->
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                action.label,
                                                fontWeight = if (action == currentAction) FontWeight.Bold else FontWeight.Normal
                                            )
                                        },
                                        onClick = {
                                            viewModel.setGestureAction(gestureType, action)
                                            gestureMap = gestureMap.toMutableMap().apply { put(gestureType, action) }
                                            expandedGesture = null
                                        }
                                    )
                                }
                            }
                        }
                    }
                    InsetDivider()
                }
            }

            Spacer(Modifier.height(40.dp))
        }
    }

    if (showClearCacheDialog) {
        AlertDialog(
            onDismissRequest = { showClearCacheDialog = false },
            title = { Text("Clear Cache?", fontWeight = FontWeight.SemiBold) },
            text = {
                Text(
                    "Cached folder data will be removed. The next scan may take longer.",
                    color = MaterialTheme.colorScheme.onSurface.copy(0.7f)
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.clearCache()
                    showClearCacheDialog = false
                }) {
                    Text("Clear", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.SemiBold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearCacheDialog = false }) {
                    Text("Cancel")
                }
            },
            shape = RoundedCornerShape(20.dp)
        )
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        title,
        fontSize = 13.sp,
        fontWeight = FontWeight.Medium,
        color = MaterialTheme.colorScheme.onSurface.copy(0.4f),
        letterSpacing = 0.5.sp,
        modifier = Modifier.padding(start = 36.dp, bottom = 8.dp)
    )
}

@Composable
private fun InsetDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(start = 16.dp),
        color = MaterialTheme.colorScheme.outline
    )
}

@Composable
private fun SelectableRow(
    title: String,
    subtitle: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                title,
                fontSize = 16.sp,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = if (isSelected) FontWeight.Medium else FontWeight.Normal
            )
            Text(
                subtitle,
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (isSelected) {
            Icon(
                Icons.Default.Check, "Selected",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(22.dp)
            )
        }
    }
}

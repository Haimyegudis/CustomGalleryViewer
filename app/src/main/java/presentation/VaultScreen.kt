package com.example.customgalleryviewer.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import coil.decode.VideoFrameDecoder
import coil.request.ImageRequest
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import com.example.customgalleryviewer.data.VaultEntity

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VaultScreen(
    onBack: () -> Unit,
    viewModel: VaultViewModel = hiltViewModel()
) {
    val vaultItems by viewModel.vaultItems.collectAsState()
    val isAuthenticated by viewModel.isAuthenticated.collectAsState()
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("vault_settings", android.content.Context.MODE_PRIVATE) }

    // PIN state
    var enteredPin by remember { mutableStateOf("") }
    var isSettingPin by remember { mutableStateOf(false) }
    var newPin by remember { mutableStateOf("") }
    var confirmPin by remember { mutableStateOf("") }
    var pinError by remember { mutableStateOf<String?>(null) }
    val savedPin = remember { prefs.getString("vault_pin", null) }
    val hasPin = savedPin != null

    // If no PIN is set, show setup screen
    LaunchedEffect(Unit) {
        if (!hasPin) isSettingPin = true
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Vault") },
                navigationIcon = {
                    TextButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Back", color = MaterialTheme.colorScheme.primary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        }
    ) { padding ->
        if (isSettingPin) {
            // PIN Setup
            PinScreen(
                title = if (confirmPin.isEmpty()) "Set 4-digit PIN" else "Confirm PIN",
                subtitle = if (confirmPin.isEmpty()) "Choose a PIN to protect your vault" else "Enter the same PIN again",
                error = pinError,
                modifier = Modifier.fillMaxSize().padding(padding),
                onPinComplete = { pin ->
                    if (confirmPin.isEmpty()) {
                        // First entry
                        confirmPin = pin
                        pinError = null
                    } else {
                        // Confirm
                        if (pin == confirmPin) {
                            prefs.edit().putString("vault_pin", pin).apply()
                            isSettingPin = false
                            viewModel.setAuthenticated(true)
                            pinError = null
                        } else {
                            pinError = "PINs don't match. Try again."
                            confirmPin = ""
                        }
                    }
                }
            )
        } else if (!isAuthenticated) {
            // PIN Entry
            PinScreen(
                title = "Enter PIN",
                subtitle = "Enter your 4-digit PIN to unlock",
                error = pinError,
                modifier = Modifier.fillMaxSize().padding(padding),
                onPinComplete = { pin ->
                    if (pin == prefs.getString("vault_pin", null)) {
                        viewModel.setAuthenticated(true)
                        pinError = null
                    } else {
                        pinError = "Wrong PIN"
                    }
                }
            )
        } else if (vaultItems.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.Lock, null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.3f))
                    Spacer(Modifier.height(16.dp))
                    Text("Vault is empty", fontSize = 18.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("Long-press any file → Hide in Vault", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface.copy(0.4f))
                }
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(8.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(vaultItems) { item ->
                    var showMenu by remember { mutableStateOf(false) }
                    Box(
                        modifier = Modifier
                            .aspectRatio(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { showMenu = true }
                    ) {
                        AsyncImage(
                            model = ImageRequest.Builder(context)
                                .data(java.io.File(item.vaultPath))
                                .decoderFactory(VideoFrameDecoder.Factory())
                                .size(200).build(),
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                        if (item.isVideo) {
                            Icon(Icons.Default.PlayCircle, null, tint = Color.White.copy(0.8f),
                                modifier = Modifier.size(28.dp).align(Alignment.Center))
                        }

                        if (showMenu) {
                            AlertDialog(
                                onDismissRequest = { showMenu = false },
                                title = { Text(item.fileName, fontWeight = FontWeight.SemiBold, maxLines = 1) },
                                text = {
                                    Column {
                                        TextButton(onClick = { showMenu = false; viewModel.restoreItem(item.id) }) {
                                            Icon(Icons.Default.RestorePage, null, modifier = Modifier.size(20.dp))
                                            Spacer(Modifier.width(8.dp))
                                            Text("Restore to original location")
                                        }
                                        TextButton(onClick = { showMenu = false; viewModel.deleteItem(item.id) }) {
                                            Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(20.dp))
                                            Spacer(Modifier.width(8.dp))
                                            Text("Delete permanently", color = MaterialTheme.colorScheme.error)
                                        }
                                    }
                                },
                                confirmButton = {}
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PinScreen(
    title: String,
    subtitle: String,
    error: String?,
    modifier: Modifier = Modifier,
    onPinComplete: (String) -> Unit
) {
    var pin by remember { mutableStateOf("") }

    // Reset pin when title changes (new screen)
    LaunchedEffect(title) { pin = "" }

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(Icons.Default.Lock, null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(24.dp))
        Text(title, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Text(subtitle, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(24.dp))

        // PIN dots
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            repeat(4) { i ->
                Box(
                    modifier = Modifier
                        .size(20.dp)
                        .clip(CircleShape)
                        .background(
                            if (i < pin.length) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.surfaceVariant
                        )
                        .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
                )
            }
        }

        if (error != null) {
            Spacer(Modifier.height(12.dp))
            Text(error, color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
        }

        Spacer(Modifier.height(32.dp))

        // Number pad
        val buttons = listOf(
            listOf("1", "2", "3"),
            listOf("4", "5", "6"),
            listOf("7", "8", "9"),
            listOf("", "0", "⌫")
        )
        buttons.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                row.forEach { key ->
                    if (key.isEmpty()) {
                        Spacer(Modifier.size(64.dp))
                    } else {
                        Surface(
                            onClick = {
                                when (key) {
                                    "⌫" -> if (pin.isNotEmpty()) pin = pin.dropLast(1)
                                    else -> if (pin.length < 4) {
                                        pin += key
                                        if (pin.length == 4) {
                                            onPinComplete(pin)
                                            pin = ""
                                        }
                                    }
                                }
                            },
                            modifier = Modifier.size(64.dp),
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.surfaceVariant
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(key, fontSize = 24.sp, fontWeight = FontWeight.Medium)
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
        }
    }
    } // end CompositionLocalProvider
}

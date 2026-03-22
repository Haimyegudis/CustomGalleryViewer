package com.example.customgalleryviewer.presentation

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.RestorePage
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.FragmentActivity
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import coil.decode.VideoFrameDecoder
import coil.request.ImageRequest
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

    LaunchedEffect(Unit) {
        if (isAuthenticated) return@LaunchedEffect
        // Try biometric authentication
        try {
            val activity = context as? FragmentActivity
            if (activity != null) {
                val biometricPrompt = androidx.biometric.BiometricPrompt(
                    activity,
                    object : androidx.biometric.BiometricPrompt.AuthenticationCallback() {
                        override fun onAuthenticationSucceeded(result: androidx.biometric.BiometricPrompt.AuthenticationResult) {
                            viewModel.setAuthenticated(true)
                        }
                        override fun onAuthenticationFailed() {
                            // Stay locked
                        }
                        override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                            // Allow access if biometric not available
                            if (errorCode == androidx.biometric.BiometricPrompt.ERROR_HW_NOT_PRESENT ||
                                errorCode == androidx.biometric.BiometricPrompt.ERROR_NO_BIOMETRICS ||
                                errorCode == androidx.biometric.BiometricPrompt.ERROR_HW_UNAVAILABLE) {
                                viewModel.setAuthenticated(true)
                            }
                        }
                    }
                )
                val promptInfo = androidx.biometric.BiometricPrompt.PromptInfo.Builder()
                    .setTitle("Unlock Vault")
                    .setSubtitle("Authenticate to access your vault")
                    .setDeviceCredentialAllowed(true)
                    .build()
                biometricPrompt.authenticate(promptInfo)
            } else {
                viewModel.setAuthenticated(true)
            }
        } catch (_: Exception) {
            viewModel.setAuthenticated(true)
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Vault") },
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
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { padding ->
        if (!isAuthenticated) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.Lock, null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(16.dp))
                    Text("Vault is locked", fontSize = 18.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("Authenticate to access", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.6f))
                }
            }
        } else if (vaultItems.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Text("Vault is empty", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(vaultItems) { item ->
                    VaultItemRow(
                        item = item,
                        onRestore = { viewModel.restoreItem(item.id) },
                        onDelete = { viewModel.deleteItem(item.id) }
                    )
                }
            }
        }
    }
}

@Composable
private fun VaultItemRow(
    item: VaultEntity,
    onRestore: () -> Unit,
    onDelete: () -> Unit
) {
    val context = LocalContext.current
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(java.io.File(item.vaultPath))
                    .decoderFactory(VideoFrameDecoder.Factory())
                    .size(80)
                    .build(),
                contentDescription = null,
                modifier = Modifier
                    .size(50.dp)
                    .clip(RoundedCornerShape(8.dp)),
                contentScale = ContentScale.Crop
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(item.fileName, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                Text(
                    if (item.isVideo) "Video" else "Image",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = onRestore) {
                Icon(Icons.Default.RestorePage, "Restore", tint = MaterialTheme.colorScheme.primary)
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, "Delete", tint = MaterialTheme.colorScheme.error)
            }
        }
    }
}

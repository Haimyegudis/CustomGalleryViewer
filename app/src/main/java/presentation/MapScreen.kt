package com.example.customgalleryviewer.presentation

import android.content.Intent
import android.graphics.drawable.BitmapDrawable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import coil.ImageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapScreen(
    onBack: () -> Unit,
    viewModel: MapViewModel = hiltViewModel()
) {
    val geoMedia by viewModel.geoMedia.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    DisposableEffect(Unit) {
        Configuration.getInstance().apply {
            load(context, context.getSharedPreferences("osmdroid", 0))
            userAgentValue = context.packageName
        }
        onDispose {}
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Map View") },
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
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.height(16.dp))
                        Text("Loading geotagged media...", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            } else if (geoMedia.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("No geotagged media found", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(8.dp))
                        Text("Photos need GPS data in EXIF to appear here", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(0.4f))
                    }
                }
            } else {
                var mapView by remember { mutableStateOf<MapView?>(null) }

                AndroidView(
                    factory = { ctx ->
                        MapView(ctx).apply {
                            setTileSource(TileSourceFactory.MAPNIK)
                            setMultiTouchControls(true)
                            setBuiltInZoomControls(false)
                            controller.setZoom(10.0)
                            if (geoMedia.isNotEmpty()) {
                                controller.setCenter(GeoPoint(geoMedia.first().latitude, geoMedia.first().longitude))
                            }
                            mapView = this
                        }
                    },
                    update = { mv ->
                        mv.overlays.clear()
                        val imageLoader = ImageLoader(context)
                        geoMedia.forEach { media ->
                            try {
                                val marker = Marker(mv)
                                marker.position = GeoPoint(media.latitude, media.longitude)
                                marker.title = media.name
                                marker.snippet = "Tap to open"
                                marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)

                                // Load thumbnail for marker icon
                                scope.launch {
                                    try {
                                        val result = withContext(Dispatchers.IO) {
                                            imageLoader.execute(
                                                ImageRequest.Builder(context)
                                                    .data(media.uri)
                                                    .size(96)
                                                    .allowHardware(false)
                                                    .build()
                                            )
                                        }
                                        if (result is SuccessResult) {
                                            val bmp = (result.drawable as? BitmapDrawable)?.bitmap
                                            if (bmp != null) {
                                                // Create rounded thumbnail
                                                val scaled = android.graphics.Bitmap.createScaledBitmap(bmp, 64, 64, true)
                                                marker.icon = BitmapDrawable(context.resources, scaled)
                                                mv.invalidate()
                                            }
                                        }
                                    } catch (_: Exception) {}
                                }

                                marker.setOnMarkerClickListener { m, _ ->
                                    // Open image
                                    try {
                                        val intent = Intent(Intent.ACTION_VIEW).apply {
                                            setDataAndType(media.uri, "image/*")
                                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                        }
                                        context.startActivity(intent)
                                    } catch (_: Exception) {}
                                    true
                                }

                                mv.overlays.add(marker)
                            } catch (_: Exception) {}
                        }
                        mv.invalidate()
                    },
                    modifier = Modifier.fillMaxSize()
                )

                DisposableEffect(Unit) {
                    onDispose { mapView?.onDetach() }
                }

                // Item count badge
                Surface(
                    modifier = Modifier.align(Alignment.TopEnd).padding(16.dp),
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Text(
                        "${geoMedia.size} photos",
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
        }
    }
}

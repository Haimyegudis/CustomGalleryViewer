package com.example.customgalleryviewer.presentation.components

import android.net.Uri
import android.widget.FrameLayout
import android.widget.Toast
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.example.customgalleryviewer.util.VideoTrimUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable
fun VideoTrimmerDialog(
    uri: Uri,
    durationMs: Long,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var startMs by remember { mutableLongStateOf(0L) }
    var endMs by remember { mutableLongStateOf(durationMs) }
    var isTrimming by remember { mutableStateOf(false) }
    var progress by remember { mutableFloatStateOf(0f) }
    var currentPosition by remember { mutableLongStateOf(0L) }
    var isPlaying by remember { mutableStateOf(false) }

    val exoPlayer = remember {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(uri))
            prepare()
            playWhenReady = false
        }
    }

    // Track position
    LaunchedEffect(Unit) {
        while (true) {
            currentPosition = exoPlayer.currentPosition
            isPlaying = exoPlayer.isPlaying
            // Auto-loop within trim range during preview
            if (exoPlayer.isPlaying && exoPlayer.currentPosition >= endMs) {
                exoPlayer.seekTo(startMs)
            }
            delay(100)
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            exoPlayer.stop()
            exoPlayer.release()
        }
    }

    androidx.compose.ui.window.Dialog(
        onDismissRequest = { if (!isTrimming) onDismiss() },
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(0.95f).fillMaxHeight(0.85f),
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Trim Video", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    Text(
                        "Duration: ${formatTrimTime(endMs - startMs)}",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Video preview
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .background(Color.Black)
                ) {
                    AndroidView(
                        factory = { ctx ->
                            PlayerView(ctx).apply {
                                player = exoPlayer
                                useController = false
                                resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                                layoutParams = FrameLayout.LayoutParams(-1, -1)
                                // Disable touch so Compose buttons work above it
                                setOnTouchListener { _, _ -> false }
                                isClickable = false
                                isFocusable = false
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    )

                    // Play/pause button — elevated above PlayerView
                    Surface(
                        onClick = {
                            if (exoPlayer.isPlaying) exoPlayer.pause()
                            else {
                                if (exoPlayer.currentPosition < startMs || exoPlayer.currentPosition >= endMs) {
                                    exoPlayer.seekTo(startMs)
                                }
                                exoPlayer.playWhenReady = true
                                exoPlayer.play()
                            }
                        },
                        modifier = Modifier.align(Alignment.Center).size(64.dp),
                        shape = androidx.compose.foundation.shape.CircleShape,
                        color = Color.Black.copy(0.6f)
                    ) {
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(64.dp)) {
                            Icon(
                                if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                null, tint = Color.White, modifier = Modifier.size(32.dp)
                            )
                        }
                    }
                }

                // Time labels
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Start: ${formatTrimTime(startMs)}", fontSize = 13.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Medium)
                    Text("Current: ${formatTrimTime(currentPosition)}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("End: ${formatTrimTime(endMs)}", fontSize = 13.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Medium)
                }

                // Timeline with draggable handles
                var trackWidthPx by remember { mutableFloatStateOf(1f) }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(64.dp)
                        .padding(horizontal = 16.dp)
                        .onGloballyPositioned { trackWidthPx = it.size.width.toFloat().coerceAtLeast(1f) }
                ) {
                    val startFrac = if (durationMs > 0) startMs.toFloat() / durationMs else 0f
                    val endFrac = if (durationMs > 0) endMs.toFloat() / durationMs else 1f
                    val posFrac = if (durationMs > 0) currentPosition.toFloat() / durationMs else 0f

                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val w = size.width
                        val h = size.height
                        val barTop = h / 2 - 12f
                        val barBot = h / 2 + 12f

                        // Background track
                        drawRoundRect(Color.Gray.copy(0.3f), topLeft = Offset(0f, barTop), size = androidx.compose.ui.geometry.Size(w, 24f), cornerRadius = androidx.compose.ui.geometry.CornerRadius(6f))
                        // Selected range
                        drawRect(Color(0xFF00E5FF).copy(0.3f), topLeft = Offset(startFrac * w, barTop), size = androidx.compose.ui.geometry.Size((endFrac - startFrac) * w, 24f))
                        // Current position
                        drawLine(Color.White, Offset(posFrac * w, barTop - 4f), Offset(posFrac * w, barBot + 4f), strokeWidth = 2f)
                        // Start handle
                        drawRoundRect(Color(0xFF4CAF50), topLeft = Offset(startFrac * w - 8f, barTop - 8f), size = androidx.compose.ui.geometry.Size(16f, 40f), cornerRadius = androidx.compose.ui.geometry.CornerRadius(4f))
                        // End handle
                        drawRoundRect(Color(0xFFFF5252), topLeft = Offset(endFrac * w - 8f, barTop - 8f), size = androidx.compose.ui.geometry.Size(16f, 40f), cornerRadius = androidx.compose.ui.geometry.CornerRadius(4f))
                    }

                    // Single drag zone — detects which handle is closest
                    var draggingHandle by remember { mutableStateOf<String?>(null) }
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .pointerInput(durationMs, trackWidthPx) {
                                detectHorizontalDragGestures(
                                    onDragStart = { offset ->
                                        val touchFrac = offset.x / size.width.toFloat()
                                        val distToStart = kotlin.math.abs(touchFrac - startFrac)
                                        val distToEnd = kotlin.math.abs(touchFrac - endFrac)
                                        draggingHandle = if (distToStart <= distToEnd) "start" else "end"
                                        exoPlayer.pause()
                                    },
                                    onDragEnd = { draggingHandle = null }
                                ) { change, dragAmount ->
                                    change.consume()
                                    if (draggingHandle == null) {
                                        val touchFrac = change.position.x / size.width.toFloat()
                                        val distToStart = kotlin.math.abs(touchFrac - startFrac)
                                        val distToEnd = kotlin.math.abs(touchFrac - endFrac)
                                        draggingHandle = if (distToStart <= distToEnd) "start" else "end"
                                        exoPlayer.pause()
                                    }
                                    val delta = (dragAmount / trackWidthPx * durationMs).toLong()
                                    if (draggingHandle == "start") {
                                        startMs = (startMs + delta).coerceIn(0L, endMs - 1000L)
                                        exoPlayer.seekTo(startMs)
                                    } else {
                                        endMs = (endMs + delta).coerceIn(startMs + 1000L, durationMs)
                                        exoPlayer.seekTo(endMs)
                                    }
                                }
                            }
                    )
                }

                // Preset buttons
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    AssistChip(onClick = { startMs = 0L; endMs = durationMs / 2; exoPlayer.seekTo(0) }, label = { Text("First Half") })
                    AssistChip(onClick = { startMs = durationMs / 2; endMs = durationMs; exoPlayer.seekTo(durationMs / 2) }, label = { Text("Second Half") })
                    AssistChip(onClick = { startMs = 0L; endMs = durationMs; exoPlayer.seekTo(0) }, label = { Text("Reset") })
                }

                // Progress
                if (isTrimming) {
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text("Trimming... ${(progress * 100).toInt()}%", fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp))
                }

                // Bottom buttons
                Row(
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss, enabled = !isTrimming) { Text("Cancel") }
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = {
                            isTrimming = true
                            exoPlayer.pause()
                            scope.launch {
                                val result = withContext(Dispatchers.IO) {
                                    VideoTrimUtil.trimVideo(context, uri, startMs, endMs) { p -> progress = p }
                                }
                                isTrimming = false
                                if (result != null) {
                                    Toast.makeText(context, "Saved: ${result.name}", Toast.LENGTH_SHORT).show()
                                    onDismiss()
                                } else {
                                    Toast.makeText(context, "Trim failed", Toast.LENGTH_SHORT).show()
                                }
                            }
                        },
                        enabled = !isTrimming
                    ) {
                        Icon(Icons.Default.ContentCut, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Trim & Save")
                    }
                }
            }
        }
    }
}

private fun formatTrimTime(ms: Long): String {
    val totalSeconds = ms / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
    else String.format(Locale.US, "%d:%02d", minutes, seconds)
}

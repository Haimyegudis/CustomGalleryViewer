package com.example.customgalleryviewer.presentation.components

import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.customgalleryviewer.util.VideoTrimUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

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

    AlertDialog(
        onDismissRequest = { if (!isTrimming) onDismiss() },
        title = { Text("Trim Video", fontWeight = FontWeight.SemiBold) },
        text = {
            Column {
                Text(
                    "Start: ${formatTrimTime(startMs)}  -  End: ${formatTrimTime(endMs)}",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.height(16.dp))

                // Timeline bar
                val density = LocalDensity.current
                var timelineWidthPx by remember { mutableFloatStateOf(0f) }
                val timelineWidthDp = with(density) { timelineWidthPx.toDp() }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .onGloballyPositioned { coords ->
                            timelineWidthPx = coords.size.width.toFloat()
                        }
                ) {
                    // Background
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(24.dp)
                            .align(Alignment.Center)
                            .clip(RoundedCornerShape(4.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                    )
                    // Selected range
                    val startFraction = if (durationMs > 0) startMs.toFloat() / durationMs else 0f
                    val endFraction = if (durationMs > 0) endMs.toFloat() / durationMs else 1f
                    val handleWidthDp = 12.dp
                    val usableWidthDp = timelineWidthDp - handleWidthDp
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(endFraction - startFraction)
                            .height(24.dp)
                            .align(Alignment.Center)
                            .offset(x = handleWidthDp / 2 + usableWidthDp * startFraction)
                            .clip(RoundedCornerShape(4.dp))
                            .background(MaterialTheme.colorScheme.primary.copy(0.3f))
                    )

                    // Start handle
                    Box(
                        modifier = Modifier
                            .offset(x = usableWidthDp * startFraction)
                            .size(width = handleWidthDp, height = 48.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(MaterialTheme.colorScheme.primary)
                            .align(Alignment.CenterStart)
                            .pointerInput(timelineWidthPx) {
                                detectHorizontalDragGestures { _, dragAmount ->
                                    if (timelineWidthPx > 0f) {
                                        val delta = (dragAmount / timelineWidthPx * durationMs).toLong()
                                        startMs = (startMs + delta).coerceIn(0L, endMs - 1000L)
                                    }
                                }
                            }
                    )

                    // End handle
                    Box(
                        modifier = Modifier
                            .offset(x = usableWidthDp * endFraction)
                            .size(width = handleWidthDp, height = 48.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(MaterialTheme.colorScheme.primary)
                            .align(Alignment.CenterStart)
                            .pointerInput(timelineWidthPx) {
                                detectHorizontalDragGestures { _, dragAmount ->
                                    if (timelineWidthPx > 0f) {
                                        val delta = (dragAmount / timelineWidthPx * durationMs).toLong()
                                        endMs = (endMs + delta).coerceIn(startMs + 1000L, durationMs)
                                    }
                                }
                            }
                    )
                }

                // Preset buttons
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AssistChip(
                        onClick = { startMs = 0L; endMs = durationMs / 2 },
                        label = { Text("First Half") }
                    )
                    AssistChip(
                        onClick = { startMs = durationMs / 2; endMs = durationMs },
                        label = { Text("Second Half") }
                    )
                }

                if (isTrimming) {
                    Spacer(Modifier.height(12.dp))
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier.fillMaxWidth(),
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        "Trimming... ${(progress * 100).toInt()}%",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    isTrimming = true
                    scope.launch {
                        val result = withContext(Dispatchers.IO) {
                            VideoTrimUtil.trimVideo(context, uri, startMs, endMs) { p ->
                                progress = p
                            }
                        }
                        isTrimming = false
                        if (result != null) {
                            Toast.makeText(context, "Saved to ${result.name}", Toast.LENGTH_SHORT).show()
                            onDismiss()
                        } else {
                            Toast.makeText(context, "Trim failed", Toast.LENGTH_SHORT).show()
                        }
                    }
                },
                enabled = !isTrimming
            ) {
                Icon(Icons.Default.ContentCut, null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("Trim")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isTrimming) {
                Text("Cancel")
            }
        },
        shape = RoundedCornerShape(20.dp)
    )
}

private fun formatTrimTime(ms: Long): String {
    val totalSeconds = ms / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.US, "%d:%02d", minutes, seconds)
    }
}

package com.example.customgalleryviewer.presentation.components

import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.customgalleryviewer.util.MediaCompressor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun CompressDialog(
    uri: Uri,
    isVideo: Boolean,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var quality by remember { mutableIntStateOf(80) }
    var maxWidth by remember { mutableIntStateOf(1920) }
    var isCompressing by remember { mutableStateOf(false) }
    var progress by remember { mutableFloatStateOf(0f) }

    val resolutions = listOf(
        "Original" to 0,
        "1920x1080" to 1920,
        "1280x720" to 1280,
        "854x480" to 854,
        "640x360" to 640
    )
    var selectedRes by remember { mutableIntStateOf(0) }

    AlertDialog(
        onDismissRequest = { if (!isCompressing) onDismiss() },
        title = { Text("Compress", fontWeight = FontWeight.SemiBold) },
        text = {
            Column {
                if (!isVideo) {
                    Text("Quality: $quality%", fontSize = 14.sp)
                    Slider(
                        value = quality.toFloat(),
                        onValueChange = { quality = it.toInt() },
                        valueRange = 10f..100f,
                        steps = 8,
                        colors = SliderDefaults.colors(
                            thumbColor = MaterialTheme.colorScheme.primary,
                            activeTrackColor = MaterialTheme.colorScheme.primary
                        )
                    )
                    Spacer(Modifier.height(12.dp))
                }

                Text("Resolution", fontSize = 14.sp, fontWeight = FontWeight.Medium)
                Spacer(Modifier.height(8.dp))
                resolutions.forEachIndexed { index, (label, width) ->
                    Row(modifier = Modifier.fillMaxWidth()) {
                        RadioButton(
                            selected = selectedRes == index,
                            onClick = { selectedRes = index; if (width > 0) maxWidth = width }
                        )
                        Text(
                            label,
                            modifier = Modifier.padding(start = 8.dp, top = 12.dp),
                            fontSize = 14.sp
                        )
                    }
                }

                if (isCompressing) {
                    Spacer(Modifier.height(12.dp))
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier.fillMaxWidth(),
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        "Compressing... ${(progress * 100).toInt()}%",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    isCompressing = true
                    scope.launch {
                        val result = withContext(Dispatchers.IO) {
                            if (isVideo) {
                                MediaCompressor.compressVideo(context, uri) { p -> progress = p }
                            } else {
                                val mw = if (selectedRes == 0) Int.MAX_VALUE else maxWidth
                                val mh = if (selectedRes == 0) Int.MAX_VALUE else (maxWidth * 9 / 16)
                                MediaCompressor.compressImage(context, uri, quality, mw, mh) { p -> progress = p }
                            }
                        }
                        isCompressing = false
                        if (result != null) {
                            Toast.makeText(context, "Saved: ${result.name}", Toast.LENGTH_SHORT).show()
                            onDismiss()
                        } else {
                            Toast.makeText(context, "Compression failed", Toast.LENGTH_SHORT).show()
                        }
                    }
                },
                enabled = !isCompressing
            ) {
                Text("Compress")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isCompressing) {
                Text("Cancel")
            }
        },
        shape = RoundedCornerShape(20.dp)
    )
}

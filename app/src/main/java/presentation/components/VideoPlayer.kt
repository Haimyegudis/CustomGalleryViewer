package com.example.customgalleryviewer.presentation.components

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.net.Uri
import android.provider.MediaStore
import android.widget.FrameLayout
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.FileProvider
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.math.abs

@OptIn(UnstableApi::class)
@Composable
fun VideoPlayer(
    uri: Uri,
    onNext: () -> Unit,
    onPrev: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var isPlaying by remember { mutableStateOf(true) }
    var isControlsVisible by remember { mutableStateOf(false) }
    var showInfoButton by remember { mutableStateOf(false) }
    var showInfoDialog by remember { mutableStateOf(false) }
    var showActionMenu by remember { mutableStateOf(false) }
    var currentPosition by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(0L) }
    var isLooping by remember { mutableStateOf(false) }
    var hasEnded by remember { mutableStateOf(false) }
    var isDragging by remember { mutableStateOf(false) }

    var showVolumeIndicator by remember { mutableStateOf(false) }
    var currentVolumeLevel by remember { mutableFloatStateOf(0.5f) }
    var showBrightnessIndicator by remember { mutableStateOf(false) }
    var currentBrightnessLevel by remember { mutableFloatStateOf(0.5f) }
    var showSeekIndicator by remember { mutableStateOf(false) }
    var seekPreviewTime by remember { mutableLongStateOf(0L) }

    val exoPlayer = remember {
        ExoPlayer.Builder(context).build().apply {
            playWhenReady = true
            repeatMode = Player.REPEAT_MODE_OFF
        }
    }

    LaunchedEffect(uri) {
        hasEnded = false
        isDragging = false
        showSeekIndicator = false
        showVolumeIndicator = false
        showBrightnessIndicator = false
        showInfoButton = false
        showInfoDialog = false
        showActionMenu = false

        exoPlayer.stop()
        exoPlayer.clearMediaItems()
        val mediaItem = MediaItem.fromUri(uri)
        exoPlayer.setMediaItem(mediaItem)
        exoPlayer.seekTo(0)
        exoPlayer.prepare()
        exoPlayer.playWhenReady = true
        delay(200)
        exoPlayer.play()
    }

    LaunchedEffect(isLooping) {
        exoPlayer.repeatMode = if (isLooping) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
    }

    LaunchedEffect(Unit) {
        while (true) {
            if (!isDragging) {
                currentPosition = exoPlayer.currentPosition
                duration = exoPlayer.duration.coerceAtLeast(0L)
                isPlaying = exoPlayer.isPlaying

                if (exoPlayer.playbackState == Player.STATE_ENDED && !isLooping && !hasEnded) {
                    hasEnded = true
                    delay(200)
                    onNext()
                }
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

    Box(modifier = modifier.fillMaxSize()) {
        AndroidView(
            factory = {
                PlayerView(context).apply {
                    player = exoPlayer
                    useController = false
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                    layoutParams = FrameLayout.LayoutParams(-1, -1)
                }
            },
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = { isDragging = true },
                        onDragEnd = {
                            if (showSeekIndicator) {
                                exoPlayer.seekTo(seekPreviewTime)
                                if (!exoPlayer.isPlaying) {
                                    exoPlayer.play()
                                }
                            }
                            scope.launch {
                                delay(1500)
                                showSeekIndicator = false
                                showVolumeIndicator = false
                                showBrightnessIndicator = false
                                isDragging = false
                            }
                        },
                        onDragCancel = {
                            showSeekIndicator = false
                            showVolumeIndicator = false
                            showBrightnessIndicator = false
                            isDragging = false
                        }
                    ) { change, dragAmount ->
                        val (dx, dy) = dragAmount

                        if (abs(dx) > abs(dy) * 1.2f && abs(dx) > 10) {
                            val seekSensitivity = 100f
                            val seekDelta = (dx * seekSensitivity).toLong()
                            seekPreviewTime = (currentPosition + seekDelta).coerceIn(0, duration)
                            showSeekIndicator = true
                            showVolumeIndicator = false
                            showBrightnessIndicator = false
                        } else if (abs(dy) > 15) {
                            val isRightSide = change.position.x > size.width / 2
                            val sensitivity = 2000f

                            if (isRightSide) {
                                currentBrightnessLevel = adjustAppBrightness(context, -dy / sensitivity)
                                showBrightnessIndicator = true
                                showVolumeIndicator = false
                                showSeekIndicator = false
                            } else {
                                currentVolumeLevel = adjustAppVolume(context, -dy / sensitivity)
                                showVolumeIndicator = true
                                showBrightnessIndicator = false
                                showSeekIndicator = false
                            }
                        }
                    }
                }
        )

        AnimatedVisibility(
            visible = showSeekIndicator,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.Center)
        ) {
            Box(
                modifier = Modifier
                    .background(Color.Black.copy(0.8f), RoundedCornerShape(16.dp))
                    .padding(24.dp)
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    val diff = seekPreviewTime - currentPosition
                    val sign = if (diff > 0) "+" else ""
                    Text(
                        text = formatTime(seekPreviewTime),
                        color = Color.White,
                        style = MaterialTheme.typography.headlineLarge
                    )
                    Text(
                        text = "($sign${TimeUnit.MILLISECONDS.toSeconds(diff)}s)",
                        color = if (diff > 0) Color.Green else Color.Red,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }

        AnimatedVisibility(
            visible = showVolumeIndicator,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.CenterStart).padding(start = 30.dp)
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Default.VolumeUp, null, tint = Color.White, modifier = Modifier.size(32.dp))
                Spacer(Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = { currentVolumeLevel },
                    modifier = Modifier
                        .height(150.dp)
                        .width(12.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color.Gray.copy(0.5f)),
                    color = Color.White,
                )
                Spacer(Modifier.height(8.dp))
                Text("${(currentVolumeLevel * 100).toInt()}%", color = Color.White, style = MaterialTheme.typography.bodySmall)
            }
        }

        AnimatedVisibility(
            visible = showBrightnessIndicator,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.CenterEnd).padding(end = 30.dp)
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Default.Brightness6, null, tint = Color.Yellow, modifier = Modifier.size(32.dp))
                Spacer(Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = { currentBrightnessLevel },
                    modifier = Modifier
                        .height(150.dp)
                        .width(12.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color.Gray.copy(0.5f)),
                    color = Color.Yellow,
                )
                Spacer(Modifier.height(8.dp))
                Text("${(currentBrightnessLevel * 100).toInt()}%", color = Color.White, style = MaterialTheme.typography.bodySmall)
            }
        }

        // Info button (top-left when center is tapped)
        AnimatedVisibility(
            visible = showInfoButton,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(16.dp)
        ) {
            IconButton(
                onClick = { showInfoDialog = true },
                modifier = Modifier
                    .size(48.dp)
                    .background(Color.Black.copy(0.6f), RoundedCornerShape(24.dp))
            ) {
                Icon(
                    Icons.Default.Info,
                    contentDescription = "Info",
                    tint = Color.White,
                    modifier = Modifier.size(32.dp)
                )
            }
        }

        AnimatedVisibility(
            visible = isControlsVisible,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.fillMaxSize().background(Color.Black.copy(0.4f))
        ) {
            Column(
                modifier = Modifier.fillMaxSize().padding(16.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    IconButton(onClick = { isControlsVisible = false }) {
                        Icon(Icons.Default.Close, null, tint = Color.White, modifier = Modifier.size(24.dp))
                    }
                    IconButton(onClick = { isLooping = !isLooping }) {
                        Icon(
                            if (isLooping) Icons.Default.RepeatOne else Icons.Default.Repeat,
                            null,
                            tint = if (isLooping) MaterialTheme.colorScheme.primary else Color.White,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onPrev) {
                        Icon(Icons.Default.SkipPrevious, null, tint = Color.White, modifier = Modifier.size(48.dp))
                    }
                    IconButton(onClick = {
                        if (isPlaying) exoPlayer.pause() else exoPlayer.play()
                    }) {
                        Icon(
                            if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            null, tint = Color.White, modifier = Modifier.size(64.dp)
                        )
                    }
                    IconButton(onClick = onNext) {
                        Icon(Icons.Default.SkipNext, null, tint = Color.White, modifier = Modifier.size(48.dp))
                    }
                }
                Column {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(formatTime(currentPosition), color = Color.White)
                        Text(formatTime(duration), color = Color.White)
                    }
                    Slider(
                        value = if (duration > 0) currentPosition.toFloat() else 0f,
                        onValueChange = { exoPlayer.seekTo(it.toLong()) },
                        valueRange = 0f..duration.toFloat().coerceAtLeast(1f),
                        colors = SliderDefaults.colors(
                            thumbColor = MaterialTheme.colorScheme.primary,
                            activeTrackColor = MaterialTheme.colorScheme.primary,
                            inactiveTrackColor = Color.White.copy(0.5f)
                        )
                    )
                }
            }
        }

        if (!isControlsVisible && !isDragging) {
            Row(modifier = Modifier.fillMaxSize()) {
                Box(
                    modifier = Modifier
                        .weight(0.35f)
                        .fillMaxHeight()
                        .pointerInput(Unit) {
                            detectTapGestures(
                                onTap = { onPrev() },
                                onLongPress = { showActionMenu = true }
                            )
                        }
                )
                Box(
                    modifier = Modifier
                        .weight(0.3f)
                        .fillMaxHeight()
                        .pointerInput(Unit) {
                            detectTapGestures(
                                onTap = {
                                    showInfoButton = !showInfoButton
                                    if (!showInfoButton) {
                                        isControlsVisible = true
                                    }
                                },
                                onLongPress = { showActionMenu = true }
                            )
                        }
                )
                Box(
                    modifier = Modifier
                        .weight(0.35f)
                        .fillMaxHeight()
                        .pointerInput(Unit) {
                            detectTapGestures(
                                onTap = { onNext() },
                                onLongPress = { showActionMenu = true }
                            )
                        }
                )
            }
        }

        // Info Dialog
        if (showInfoDialog) {
            MediaInfoDialog(
                uri = uri,
                isVideo = true,
                onDismiss = { showInfoDialog = false }
            )
        }

        // Action Menu (Share, Open With)
        if (showActionMenu) {
            ActionMenuDialog(
                uri = uri,
                isVideo = true,
                onDismiss = { showActionMenu = false }
            )
        }
    }
}

@Composable
fun MediaInfoDialog(
    uri: Uri,
    isVideo: Boolean,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val mediaInfo = remember { getMediaInfo(context, uri) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Media Information") },
        text = {
            Column {
                InfoRow("Name:", mediaInfo.name)
                InfoRow("Size:", mediaInfo.size)
                InfoRow("Path:", mediaInfo.path)
                InfoRow("Date:", mediaInfo.date)
                if (isVideo) {
                    InfoRow("Duration:", mediaInfo.duration)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}

@Composable
fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.width(80.dp)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
fun ActionMenuDialog(
    uri: Uri,
    isVideo: Boolean,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    // 1. שליפת המידע על הקובץ (שימוש בפונקציה שכבר קיימת בקובץ)
    val mediaInfo = remember { getMediaInfo(context, uri) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Media Options") }, // שיניתי כותרת למשהו כללי יותר
        text = {
            Column {
                // 2. הצגת המידע (כמו שהיה בכפתור ה-i)
                Text(
                    "Properties",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                InfoRow("Name:", mediaInfo.name)
                InfoRow("Size:", mediaInfo.size)
                InfoRow("Path:", mediaInfo.path)
                InfoRow("Date:", mediaInfo.date)
                if (isVideo) {
                    InfoRow("Duration:", mediaInfo.duration)
                }

                Divider(modifier = Modifier.padding(vertical = 16.dp))

                // 3. הכפתורים המקוריים (שיתוף ופתיחה)
                Text(
                    "Actions",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                TextButton(
                    onClick = {
                        shareMedia(context, uri, isVideo)
                        onDismiss()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = 4.dp) // יישור לימין/שמאל
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Share, null, modifier = Modifier.size(24.dp))
                        Spacer(Modifier.width(12.dp))
                        Text("Share", style = MaterialTheme.typography.bodyLarge)
                    }
                }

                Spacer(Modifier.height(4.dp))

                TextButton(
                    onClick = {
                        openWith(context, uri, isVideo)
                        onDismiss()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = 4.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.OpenInNew, null, modifier = Modifier.size(24.dp))
                        Spacer(Modifier.width(12.dp))
                        Text("Open With App", style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}

data class MediaInfo(
    val name: String,
    val size: String,
    val path: String,
    val date: String,
    val duration: String
)

fun getMediaInfo(context: Context, uri: Uri): MediaInfo {
    var name = ""
    var size = ""
    var path = uri.toString()
    var date = ""
    var duration = ""

    // מקרה 1: קובץ מהגלריה (Content Provider)
    if (uri.scheme == "content") {
        val cursor = context.contentResolver.query(uri, null, null, null, null)
        cursor?.use {
            if (it.moveToFirst()) {
                val nameIndex = it.getColumnIndex(MediaStore.MediaColumns.DISPLAY_NAME)
                if (nameIndex >= 0) name = it.getString(nameIndex) ?: "Unknown"

                val sizeIndex = it.getColumnIndex(MediaStore.MediaColumns.SIZE)
                if (sizeIndex >= 0) size = formatFileSize(it.getLong(sizeIndex))

                val dataIndex = it.getColumnIndex(MediaStore.MediaColumns.DATA)
                if (dataIndex >= 0) path = it.getString(dataIndex) ?: path

                val dateIndex = it.getColumnIndex(MediaStore.MediaColumns.DATE_MODIFIED)
                if (dateIndex >= 0) {
                    val dateModified = it.getLong(dateIndex) * 1000
                    val sdf = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
                    date = sdf.format(Date(dateModified))
                }

                val durationIndex = it.getColumnIndex(MediaStore.Video.Media.DURATION)
                if (durationIndex >= 0) {
                    val durationMs = it.getLong(durationIndex)
                    if (durationMs > 0) duration = formatTime(durationMs)
                }
            }
        }
    }
    // מקרה 2: קובץ רגיל (File Path) - התיקון שהיה חסר
    else if (uri.scheme == "file" || uri.path?.startsWith("/") == true) {
        val file = File(uri.path ?: "")
        if (file.exists()) {
            name = file.name
            size = formatFileSize(file.length())
            path = file.absolutePath

            val sdf = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
            date = sdf.format(Date(file.lastModified()))
        }
    }

    if (name.isEmpty()) {
        name = uri.lastPathSegment ?: "Unknown"
    }

    return MediaInfo(name, size, path, date, duration)
}

fun formatFileSize(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val kb = bytes / 1024.0
    if (kb < 1024) return String.format("%.2f KB", kb)
    val mb = kb / 1024.0
    if (mb < 1024) return String.format("%.2f MB", mb)
    val gb = mb / 1024.0
    return String.format("%.2f GB", gb)
}

fun adjustAppVolume(context: Context, deltaPercent: Float): Float {
    val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    val current = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
    val max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
    val change = (deltaPercent * max).toInt()
    if (change == 0 && abs(deltaPercent) < 0.01) return current.toFloat() / max.toFloat()
    val effectiveChange = if (change == 0) (if (deltaPercent > 0) 1 else -1) else change
    val newVol = (current + effectiveChange).coerceIn(0, max)
    audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, newVol, 0)
    return newVol.toFloat() / max.toFloat()
}

fun adjustAppBrightness(context: Context, deltaPercent: Float): Float {
    val activity = context as? Activity ?: return 0.5f
    val lp = activity.window.attributes
    var current = lp.screenBrightness
    if (current == -1f) current = 0.5f
    val newBright = (current + deltaPercent).coerceIn(0.01f, 1f)
    lp.screenBrightness = newBright
    activity.window.attributes = lp
    return newBright
}

fun shareMedia(context: Context, uri: Uri, isVideo: Boolean) {
    try {
        val shareUri = if (uri.scheme == "file") {
            val file = File(uri.path ?: return)
            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        } else {
            uri
        }

        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = if (isVideo) "video/*" else "image/*"
            putExtra(Intent.EXTRA_STREAM, shareUri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(shareIntent, "Share"))
    } catch (e: Exception) {
        e.printStackTrace()
    }
}

fun openWith(context: Context, uri: Uri, isVideo: Boolean) {
    try {
        val openUri = if (uri.scheme == "file") {
            val file = File(uri.path ?: return)
            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        } else {
            uri
        }

        val openIntent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(openUri, if (isVideo) "video/*" else "image/*")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(openIntent, "Open with"))
    } catch (e: Exception) {
        e.printStackTrace()
    }
}

fun formatTime(ms: Long): String {
    val m = TimeUnit.MILLISECONDS.toMinutes(ms)
    val s = TimeUnit.MILLISECONDS.toSeconds(ms) - TimeUnit.MINUTES.toSeconds(m)
    return String.format("%02d:%02d", m, s)
}
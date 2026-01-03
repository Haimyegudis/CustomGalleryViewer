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
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
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
    var showActionMenu by remember { mutableStateOf(false) }

    var currentPosition by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(0L) }
    var isLooping by remember { mutableStateOf(false) }
    var hasEnded by remember { mutableStateOf(false) }

    var isDragging by remember { mutableStateOf(false) }
    var isSeekMode by remember { mutableStateOf(false) }

    var showVolumeIndicator by remember { mutableStateOf(false) }
    var currentVolumeLevel by remember { mutableFloatStateOf(0.5f) }
    var showBrightnessIndicator by remember { mutableStateOf(false) }
    var currentBrightnessLevel by remember { mutableFloatStateOf(0.5f) }
    var showSeekIndicator by remember { mutableStateOf(false) }

    var showDoubleTapIndicator by remember { mutableStateOf(false) }
    var doubleTapDirection by remember { mutableStateOf(0) }
    val doubleTapScale by animateFloatAsState(if (showDoubleTapIndicator) 1.3f else 1f, label = "scale")

    var seekPreviewTime by remember { mutableLongStateOf(0L) }
    var seekStartPosition by remember { mutableLongStateOf(0L) }

    var startVolume by remember { mutableFloatStateOf(0f) }
    var startBrightness by remember { mutableFloatStateOf(0f) }

    var totalDragDistanceX by remember { mutableFloatStateOf(0f) }
    var totalDragDistanceY by remember { mutableFloatStateOf(0f) }

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
        showActionMenu = false
        isControlsVisible = false

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

    LaunchedEffect(isControlsVisible, isDragging) {
        if (isControlsVisible && !isDragging) {
            delay(3000)
            isControlsVisible = false
        }
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

    LaunchedEffect(showDoubleTapIndicator) {
        if (showDoubleTapIndicator) {
            delay(800)
            showDoubleTapIndicator = false
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            exoPlayer.stop()
            exoPlayer.release()
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        AndroidView(
            factory = {
                PlayerView(context).apply {
                    player = exoPlayer
                    useController = false
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                    layoutParams = FrameLayout.LayoutParams(-1, -1)
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = { offset ->
                            val width = size.width
                            val height = size.height

                            if (offset.y > height * 0.85f) {
                                isControlsVisible = true
                                return@detectTapGestures
                            }

                            if (offset.x < width * 0.3) {
                                onPrev()
                            } else if (offset.x > width * 0.7) {
                                onNext()
                            } else {
                                isControlsVisible = !isControlsVisible
                            }
                        },
                        onDoubleTap = { offset ->
                            val width = size.width
                            val height = size.height

                            if (offset.y > height * 0.85f) return@detectTapGestures

                            // תיקון: ימין = קדימה (+10), שמאל = אחורה (-10)
                            if (offset.x > width * 0.6) {
                                val newPos = (exoPlayer.currentPosition + 10000).coerceAtMost(exoPlayer.duration)
                                exoPlayer.seekTo(newPos)
                                doubleTapDirection = 1
                                showDoubleTapIndicator = true
                            } else if (offset.x < width * 0.4) {
                                val newPos = (exoPlayer.currentPosition - 10000).coerceAtLeast(0)
                                exoPlayer.seekTo(newPos)
                                doubleTapDirection = -1
                                showDoubleTapIndicator = true
                            }
                        },
                        onLongPress = { showActionMenu = true }
                    )
                }
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = { offset ->
                            if (offset.y > size.height * 0.85f) return@detectDragGestures

                            isDragging = true
                            seekStartPosition = currentPosition
                            startVolume = getCurrentVolume(context)
                            startBrightness = getCurrentBrightness(context)

                            totalDragDistanceX = 0f
                            totalDragDistanceY = 0f
                            isSeekMode = false
                        },
                        onDragEnd = {
                            if (isSeekMode && showSeekIndicator) {
                                exoPlayer.seekTo(seekPreviewTime)
                                if (!exoPlayer.isPlaying) exoPlayer.play()
                            }
                            scope.launch {
                                delay(500)
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
                        totalDragDistanceX += dx
                        totalDragDistanceY += dy

                        if (!isSeekMode && !showVolumeIndicator && !showBrightnessIndicator) {
                            if (abs(totalDragDistanceX) > abs(totalDragDistanceY)) {
                                if (abs(totalDragDistanceX) > 10) isSeekMode = true
                            } else {
                                if (abs(totalDragDistanceY) > 10) {
                                    isSeekMode = false
                                    val isRightSide = change.position.x > size.width / 2
                                    if (isRightSide) {
                                        showBrightnessIndicator = true
                                        currentBrightnessLevel = startBrightness
                                    } else {
                                        showVolumeIndicator = true
                                        currentVolumeLevel = startVolume
                                    }
                                }
                            }
                        }

                        if (isSeekMode) {
                            // תיקון: משמאל לימין = קדימה (חיובי), מימין לשמאל = אחורה (שלילי)
                            val seekSensitivity = 150f
                            val seekDelta = (totalDragDistanceX * seekSensitivity).toLong()
                            seekPreviewTime = (seekStartPosition + seekDelta).coerceIn(0, duration)
                            showSeekIndicator = true
                            showVolumeIndicator = false
                            showBrightnessIndicator = false
                        } else {
                            val sensitivity = 2000f
                            val deltaPercent = -totalDragDistanceY / sensitivity

                            if (showBrightnessIndicator) {
                                val newBrightness = (startBrightness + deltaPercent).coerceIn(0f, 1f)
                                setAppBrightness(context, newBrightness)
                                currentBrightnessLevel = newBrightness
                            } else if (showVolumeIndicator) {
                                val newVolume = (startVolume + deltaPercent).coerceIn(0f, 1f)
                                setAppVolume(context, newVolume)
                                currentVolumeLevel = newVolume
                            }
                        }
                    }
                }
        )

        AnimatedVisibility(
            visible = isControlsVisible,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.fillMaxSize()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Black.copy(0.7f),
                                Color.Transparent,
                                Color.Black.copy(0.7f)
                            )
                        )
                    )
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { isControlsVisible = false }
            ) {
                Column(
                    modifier = Modifier.fillMaxSize().padding(16.dp),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = MaterialTheme.shapes.medium,
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(0.3f)
                        ) {
                            IconButton(onClick = { isLooping = !isLooping }) {
                                Icon(
                                    if (isLooping) Icons.Default.RepeatOne else Icons.Default.Repeat,
                                    "Loop",
                                    tint = if (isLooping) MaterialTheme.colorScheme.primary else Color.White
                                )
                            }
                        }
                        Surface(
                            shape = MaterialTheme.shapes.medium,
                            color = MaterialTheme.colorScheme.errorContainer.copy(0.3f)
                        ) {
                            IconButton(onClick = { isControlsVisible = false }) {
                                Icon(Icons.Default.Close, "Close", tint = Color.White)
                            }
                        }
                    }

                    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Surface(
                            shape = MaterialTheme.shapes.large,
                            color = MaterialTheme.colorScheme.primaryContainer.copy(0.3f)
                        ) {
                            IconButton(
                                onClick = {
                                    if (isPlaying) exoPlayer.pause() else exoPlayer.play()
                                    scope.launch { isControlsVisible = true }
                                },
                                modifier = Modifier.size(80.dp)
                            ) {
                                Icon(
                                    if (isPlaying) Icons.Default.PauseCircle else Icons.Default.PlayCircle,
                                    "Play/Pause",
                                    tint = Color.White,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                        }
                    }

                    Surface(
                        shape = MaterialTheme.shapes.medium,
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(0.3f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            val displayTime = if (isSeekMode) seekPreviewTime else currentPosition
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    formatTime(displayTime),
                                    color = Color.White,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                Text(
                                    formatTime(duration),
                                    color = Color.White,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                            Spacer(Modifier.height(8.dp))

                            // סליידר מודרני עם כיוון נכון (שמאל=0, ימין=duration)
                            Slider(
                                value = if (duration > 0) displayTime.toFloat() else 0f,
                                onValueChange = {
                                    isDragging = true
                                    exoPlayer.seekTo(it.toLong())
                                },
                                onValueChangeFinished = { isDragging = false },
                                valueRange = 0f..duration.toFloat().coerceAtLeast(1f),
                                colors = SliderDefaults.colors(
                                    thumbColor = MaterialTheme.colorScheme.primary,
                                    activeTrackColor = MaterialTheme.colorScheme.primary,
                                    inactiveTrackColor = Color.White.copy(0.3f)
                                )
                            )
                        }
                    }
                }
            }
        }

        // אינדיקטורים מודרניים
        AnimatedVisibility(
            visible = showSeekIndicator,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.Center)
        ) {
            Surface(
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surfaceVariant.copy(0.95f)
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(24.dp)
                ) {
                    val diff = seekPreviewTime - seekStartPosition
                    val sign = if (diff > 0) "+" else ""
                    Text(
                        formatTime(seekPreviewTime),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.headlineLarge
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "($sign${TimeUnit.MILLISECONDS.toSeconds(diff)}s)",
                        color = if (diff > 0) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            }
        }

        AnimatedVisibility(
            visible = showDoubleTapIndicator,
            enter = fadeIn(),
            exit = fadeOut(),
            // תיקון: ימין = קדימה (CenterEnd), שמאל = אחורה (CenterStart)
            modifier = Modifier
                .align(if (doubleTapDirection > 0) Alignment.CenterEnd else Alignment.CenterStart)
                .padding(horizontal = 50.dp)
        ) {
            Surface(
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.primaryContainer.copy(0.8f),
                modifier = Modifier.scale(doubleTapScale)
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(20.dp)
                ) {
                    Icon(
                        if (doubleTapDirection > 0) Icons.Default.Forward10 else Icons.Default.Replay10,
                        null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(56.dp)
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        if (doubleTapDirection > 0) "+10s" else "-10s",
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        style = MaterialTheme.typography.titleLarge
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
            Surface(
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surfaceVariant.copy(0.9f)
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(16.dp)
                ) {
                    Icon(
                        Icons.Default.VolumeUp,
                        null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(32.dp)
                    )
                    Spacer(Modifier.height(12.dp))
                    LinearProgressIndicator(
                        progress = { currentVolumeLevel },
                        modifier = Modifier
                            .height(150.dp)
                            .width(12.dp)
                            .clip(RoundedCornerShape(6.dp)),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "${(currentVolumeLevel * 100).toInt()}%",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        AnimatedVisibility(
            visible = showBrightnessIndicator,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.CenterEnd).padding(end = 30.dp)
        ) {
            Surface(
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surfaceVariant.copy(0.9f)
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(16.dp)
                ) {
                    Icon(
                        Icons.Default.Brightness6,
                        null,
                        tint = MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier.size(32.dp)
                    )
                    Spacer(Modifier.height(12.dp))
                    LinearProgressIndicator(
                        progress = { currentBrightnessLevel },
                        modifier = Modifier
                            .height(150.dp)
                            .width(12.dp)
                            .clip(RoundedCornerShape(6.dp)),
                        color = MaterialTheme.colorScheme.tertiary,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "${(currentBrightnessLevel * 100).toInt()}%",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        if (showActionMenu) {
            ActionMenuDialog(uri = uri, isVideo = true, onDismiss = { showActionMenu = false })
        }
    }
}

@Composable
fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(80.dp)
        )
        Text(
            value,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
fun ActionMenuDialog(uri: Uri, isVideo: Boolean, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val mediaInfo = remember { getMediaInfo(context, uri) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Media Options") },
        text = {
            Column {
                InfoRow("Name:", mediaInfo.name)
                InfoRow("Size:", mediaInfo.size)
                InfoRow("Date:", mediaInfo.date)
                Divider(Modifier.padding(vertical = 16.dp))

                FilledTonalButton(
                    onClick = {
                        shareMedia(context, uri, isVideo)
                        onDismiss()
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Share, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Share")
                }

                Spacer(Modifier.height(8.dp))

                FilledTonalButton(
                    onClick = {
                        openWith(context, uri, isVideo)
                        onDismiss()
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.OpenInNew, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Open With")
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
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

    try {
        if (uri.scheme == "content") {
            context.contentResolver.query(uri, null, null, null, null)?.use {
                if (it.moveToFirst()) {
                    val ni = it.getColumnIndex(MediaStore.MediaColumns.DISPLAY_NAME)
                    if (ni >= 0) name = it.getString(ni) ?: ""
                    val si = it.getColumnIndex(MediaStore.MediaColumns.SIZE)
                    if (si >= 0) size = formatFileSize(it.getLong(si))
                    val di = it.getColumnIndex(MediaStore.MediaColumns.DATE_MODIFIED)
                    if (di >= 0) date = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
                        .format(Date(it.getLong(di) * 1000))
                }
            }
        } else if (uri.scheme == "file") {
            val f = File(uri.path ?: "")
            if (f.exists()) {
                name = f.name
                size = formatFileSize(f.length())
                date = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
                    .format(Date(f.lastModified()))
            }
        }
        if (name.isEmpty()) name = uri.lastPathSegment ?: "Unknown"
    } catch (e: Exception) {
        e.printStackTrace()
        name = "Error reading info"
    }

    return MediaInfo(name, size, path, date, duration)
}

fun formatFileSize(bytes: Long): String {
    val mb = bytes / (1024.0 * 1024.0)
    return String.format("%.2f MB", mb)
}

fun getCurrentVolume(context: Context): Float {
    val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    val max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
    val cur = am.getStreamVolume(AudioManager.STREAM_MUSIC)
    return if (max > 0) cur.toFloat() / max else 0f
}

fun setAppVolume(context: Context, percent: Float) {
    val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    val max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
    val target = (percent * max).toInt()
    am.setStreamVolume(AudioManager.STREAM_MUSIC, target, 0)
}

fun getCurrentBrightness(context: Context): Float {
    val act = context as? Activity ?: return 0.5f
    val lp = act.window.attributes
    return if (lp.screenBrightness < 0) 0.5f else lp.screenBrightness
}

fun setAppBrightness(context: Context, percent: Float) {
    val act = context as? Activity ?: return
    val lp = act.window.attributes
    lp.screenBrightness = percent.coerceIn(0.01f, 1f)
    act.window.attributes = lp
}

fun shareMedia(context: Context, uri: Uri, isVideo: Boolean) {
    try {
        val shareUri = when {
            uri.scheme == "file" -> {
                val file = File(uri.path ?: return)
                FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    file
                )
            }
            else -> uri
        }

        val intent = Intent(Intent.ACTION_SEND).apply {
            type = if (isVideo) "video/*" else "image/*"
            putExtra(Intent.EXTRA_STREAM, shareUri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Share"))
    } catch (e: Exception) {
        e.printStackTrace()
        try {
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = if (isVideo) "video/*" else "image/*"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(intent, "Share"))
        } catch (e2: Exception) {
            e2.printStackTrace()
        }
    }
}

fun openWith(context: Context, uri: Uri, isVideo: Boolean) {
    try {
        val openUri = when {
            uri.scheme == "file" -> {
                val file = File(uri.path ?: return)
                FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    file
                )
            }
            else -> uri
        }

        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(openUri, if (isVideo) "video/*" else "image/*")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Open with"))
    } catch (e: Exception) {
        e.printStackTrace()
        try {
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, if (isVideo) "video/*" else "image/*")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(intent, "Open with"))
        } catch (e2: Exception) {
            e2.printStackTrace()
        }
    }
}

fun formatTime(ms: Long): String {
    val m = TimeUnit.MILLISECONDS.toMinutes(ms)
    val s = TimeUnit.MILLISECONDS.toSeconds(ms) % 60
    return String.format("%02d:%02d", m, s)
}
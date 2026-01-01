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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
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
    navigationMode: String = "TAP",
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current

    var isPlaying by remember { mutableStateOf(true) }
    var isControlsVisible by remember { mutableStateOf(false) }
    var showActionMenu by remember { mutableStateOf(false) }

    var currentPosition by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(0L) }
    var isLooping by remember { mutableStateOf(false) }
    var hasEnded by remember { mutableStateOf(false) }

    var isDragging by remember { mutableStateOf(false) }
    var isSeekMode by remember { mutableStateOf(false) }
    var isSliderDragging by remember { mutableStateOf(false) }
    var sliderPosition by remember { mutableLongStateOf(0L) }

    var showVolumeIndicator by remember { mutableStateOf(false) }
    var currentVolumeLevel by remember { mutableFloatStateOf(0.5f) }
    var showBrightnessIndicator by remember { mutableStateOf(false) }
    var currentBrightnessLevel by remember { mutableFloatStateOf(0.5f) }
    var showSeekIndicator by remember { mutableStateOf(false) }

    var seekPreviewTime by remember { mutableLongStateOf(0L) }
    var seekStartPosition by remember { mutableLongStateOf(0L) }
    var startVolume by remember { mutableFloatStateOf(0f) }
    var startBrightness by remember { mutableFloatStateOf(0f) }
    var totalDragDistanceX by remember { mutableFloatStateOf(0f) }
    var totalDragDistanceY by remember { mutableFloatStateOf(0f) }

    // אזור הסליידר - 20% תחתונים של המסך כשהcontrol מוצג
    var sliderAreaTop by remember { mutableFloatStateOf(Float.MAX_VALUE) }
    var screenHeight by remember { mutableFloatStateOf(0f) }

    val exoPlayer = remember {
        ExoPlayer.Builder(context).build().apply {
            playWhenReady = true
            repeatMode = Player.REPEAT_MODE_OFF
        }
    }

    LaunchedEffect(uri) {
        hasEnded = false
        isDragging = false
        isSliderDragging = false
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
        if (isControlsVisible && !isDragging && !isSliderDragging) {
            delay(3000)
            isControlsVisible = false
        }
    }

    LaunchedEffect(Unit) {
        while (true) {
            if (!isDragging && !isSliderDragging) {
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

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .onGloballyPositioned { coordinates ->
                screenHeight = coordinates.size.height.toFloat()
                // הסליידר נמצא ב-20% תחתונים כשה-controls מוצגים
                sliderAreaTop = screenHeight * 0.8f
            }
    ) {
        // שכבת הווידאו
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

        // שכבת gesture detection
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = { offset ->
                            val width = size.width
                            when {
                                offset.x < width * 0.3 -> if (navigationMode == "TAP") onPrev()
                                offset.x > width * 0.7 -> if (navigationMode == "TAP") onNext()
                                else -> isControlsVisible = !isControlsVisible
                            }
                        },
                        onDoubleTap = { offset ->
                            // DOUBLE TAP לקפיצת 10 שניות
                            val width = size.width
                            when {
                                offset.x < width * 0.3 -> {
                                    // Double tap שמאל = -10 שניות
                                    val newPos = (currentPosition - 10000).coerceAtLeast(0)
                                    exoPlayer.seekTo(newPos)
                                }
                                offset.x > width * 0.7 -> {
                                    // Double tap ימין = +10 שניות
                                    val newPos = (currentPosition + 10000).coerceAtMost(duration)
                                    exoPlayer.seekTo(newPos)
                                }
                                else -> {
                                    // Double tap באמצע = toggle controls
                                    isControlsVisible = !isControlsVisible
                                }
                            }
                        },
                        onLongPress = { showActionMenu = true }
                    )
                }
                .pointerInput(navigationMode, isControlsVisible) {
                    detectDragGestures(
                        onDragStart = { offset ->
                            // בדיקה אם המגע בתוך אזור הסליידר
                            val isInSliderArea = isControlsVisible && offset.y > sliderAreaTop

                            if (!isInSliderArea) {
                                isDragging = true
                                seekStartPosition = currentPosition
                                startVolume = getCurrentVolume(context)
                                startBrightness = getCurrentBrightness(context)
                                totalDragDistanceX = 0f
                                totalDragDistanceY = 0f
                                isSeekMode = false
                            }
                        },
                        onDragEnd = {
                            if (isDragging) {
                                // SEEK - משחרר אצבע, קופץ לזמן החדש
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
                            }
                        },
                        onDragCancel = {
                            showSeekIndicator = false
                            showVolumeIndicator = false
                            showBrightnessIndicator = false
                            isDragging = false
                        }
                    ) { change, dragAmount ->
                        if (!isDragging) return@detectDragGestures

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
                            val seekSensitivity = 150f
                            // הפיכת הכיוון: שמאלה=קדימה, ימינה=אחורה
                            val seekDelta = (-totalDragDistanceX * seekSensitivity).toLong()
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

        // Controls overlay
        AnimatedVisibility(
            visible = isControlsVisible,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.fillMaxSize()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(0.4f))
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
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        IconButton(onClick = { isLooping = !isLooping }) {
                            Icon(
                                if (isLooping) Icons.Default.RepeatOne else Icons.Default.Repeat,
                                "Loop",
                                tint = if (isLooping) MaterialTheme.colorScheme.primary else Color.White
                            )
                        }
                        IconButton(onClick = { isControlsVisible = false }) {
                            Icon(Icons.Default.Close, "Close", tint = Color.White)
                        }
                    }

                    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
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

                    // אזור הסליידר - ללא gesture detection!
                    Column(modifier = Modifier.fillMaxWidth()) {
                        val displayTime = if (isSliderDragging) sliderPosition else currentPosition

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(formatTime(displayTime), color = Color.White)
                            Text(formatTime(duration), color = Color.White)
                        }

                        Slider(
                            value = if (duration > 0) {
                                if (isSliderDragging) sliderPosition.toFloat()
                                else currentPosition.toFloat()
                            } else 0f,
                            onValueChange = { newValue ->
                                isSliderDragging = true
                                sliderPosition = newValue.toLong()
                            },
                            onValueChangeFinished = {
                                exoPlayer.seekTo(sliderPosition)
                                isSliderDragging = false
                            },
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
        }

        // Indicators
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
                    val diff = seekPreviewTime - seekStartPosition
                    val sign = if (diff > 0) "+" else ""
                    Text(
                        formatTime(seekPreviewTime),
                        color = Color.White,
                        style = MaterialTheme.typography.headlineLarge
                    )
                    Text(
                        "($sign${TimeUnit.MILLISECONDS.toSeconds(diff)}s)",
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
                LinearProgressIndicator(
                    progress = { currentVolumeLevel },
                    modifier = Modifier.height(150.dp).width(12.dp).clip(RoundedCornerShape(6.dp)),
                    color = Color.White
                )
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
                LinearProgressIndicator(
                    progress = { currentBrightnessLevel },
                    modifier = Modifier.height(150.dp).width(12.dp).clip(RoundedCornerShape(6.dp)),
                    color = Color.Yellow
                )
            }
        }

        if (showActionMenu) {
            ActionMenuDialog(uri = uri, isVideo = true, onDismiss = { showActionMenu = false })
        }
    }
}

@Composable
fun InfoRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.width(80.dp))
        Text(value, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
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
                TextButton(onClick = { shareMedia(context, uri, isVideo); onDismiss() }) {
                    Icon(Icons.Default.Share, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Share")
                }
                TextButton(onClick = { openWith(context, uri, isVideo); onDismiss() }) {
                    Icon(Icons.Default.OpenInNew, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Open With")
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } }
    )
}

data class MediaInfo(val name: String, val size: String, val path: String, val date: String, val duration: String)

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
                    if (di >= 0) date = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date(it.getLong(di) * 1000))
                }
            }
        } else if (uri.scheme == "file") {
            val f = File(uri.path ?: "")
            if (f.exists()) {
                name = f.name
                size = formatFileSize(f.length())
                date = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date(f.lastModified()))
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
    val i = Intent(Intent.ACTION_SEND).apply {
        type = if (isVideo) "video/*" else "image/*"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(i, "Share"))
}

fun openWith(context: Context, uri: Uri, isVideo: Boolean) {
    val i = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(uri, if (isVideo) "video/*" else "image/*")
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(i, "Open"))
}

fun formatTime(ms: Long): String {
    val m = TimeUnit.MILLISECONDS.toMinutes(ms)
    val s = TimeUnit.MILLISECONDS.toSeconds(ms) % 60
    return String.format("%02d:%02d", m, s)
}
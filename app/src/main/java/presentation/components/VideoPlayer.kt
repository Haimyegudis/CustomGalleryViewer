package com.example.customgalleryviewer.presentation.components

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.view.ViewGroup
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
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit
import kotlin.math.abs

@OptIn(UnstableApi::class)
@Composable
fun VideoPlayer(
    uri: android.net.Uri,
    onNext: () -> Unit,
    onPrev: () -> Unit,
    modifier: Modifier = Modifier,
    startMuted: Boolean = true // נשאיר, אבל הנגן ישלוט בווליום
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // --- מצבים ---
    var isPlaying by remember { mutableStateOf(true) }
    var isControlsVisible by remember { mutableStateOf(false) }
    var currentPosition by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(0L) }
    var isLooping by remember { mutableStateOf(false) }

    // --- אינדיקטורים ---
    var showVolumeIndicator by remember { mutableStateOf(false) }
    var currentVolumeLevel by remember { mutableFloatStateOf(0.5f) }

    var showBrightnessIndicator by remember { mutableStateOf(false) }
    var currentBrightnessLevel by remember { mutableFloatStateOf(0.5f) }

    // Seek
    var showSeekIndicator by remember { mutableStateOf(false) }
    var seekPreviewTime by remember { mutableLongStateOf(0L) }

    // ExoPlayer Setup
    val exoPlayer = remember {
        ExoPlayer.Builder(context).build().apply {
            playWhenReady = true // ברירת מחדל לניגון אוטומטי
            repeatMode = Player.REPEAT_MODE_OFF
        }
    }

    // Load Media & Auto Play Logic
    LaunchedEffect(uri) {
        val mediaItem = MediaItem.fromUri(uri)
        if (exoPlayer.currentMediaItem?.localConfiguration?.uri != uri) {
            exoPlayer.setMediaItem(mediaItem)
            exoPlayer.prepare()
            exoPlayer.play() // פקודה מפורשת לנגן!
        }
    }

    // Handle Loop
    LaunchedEffect(isLooping) {
        exoPlayer.repeatMode = if (isLooping) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
    }

    // Loop for progress updates
    LaunchedEffect(exoPlayer) {
        while (true) {
            currentPosition = exoPlayer.currentPosition
            duration = exoPlayer.duration.coerceAtLeast(0L)
            isPlaying = exoPlayer.isPlaying

            if (exoPlayer.playbackState == Player.STATE_ENDED && !isLooping) {
                onNext()
            }
            delay(500)
        }
    }

    DisposableEffect(Unit) {
        onDispose { exoPlayer.release() }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(Unit) {
                detectTapGestures(
                    onLongPress = { shareMedia(context, uri, true) },
                    onTap = { offset ->
                        val w = size.width
                        if (offset.x < w * 0.25) onPrev()
                        else if (offset.x > w * 0.75) onNext()
                        else isControlsVisible = !isControlsVisible
                    }
                )
            }
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { },
                    onDragEnd = {
                        // --- התיקון ל-SEEK ---
                        // מבצעים את הקפיצה רק כשהמשתמש עוזב את האצבע
                        if (showSeekIndicator) {
                            exoPlayer.seekTo(seekPreviewTime)
                            // מיד אחרי Seek, אם הנגן היה במצב Pause או Buffering, נחזיר אותו לניגון
                            exoPlayer.play()
                            showSeekIndicator = false
                        }

                        scope.launch {
                            delay(1000)
                            showVolumeIndicator = false
                            showBrightnessIndicator = false
                        }
                    }
                ) { change, dragAmount ->
                    change.consume()
                    val (dx, dy) = dragAmount

                    if (abs(dx) > abs(dy)) {
                        // --- גלילה אופקית (Seek) ---
                        // רגישות Seek: פיקסל אחד = 100 מילי-שניות (למשל)
                        val seekDelta = (dx * 100).toLong()
                        seekPreviewTime = (currentPosition + seekDelta).coerceIn(0, duration)

                        // עדכון ה-UI בלבד (לא מבצעים Seek בנגן עדיין כדי למנוע קרטועים)
                        showSeekIndicator = true

                    } else {
                        // --- גלילה אנכית (Volume / Brightness) ---
                        val isRightSide = change.position.x > size.width / 2

                        // רגישות מופחתת מאוד: מחלקים ב-3000 כדי שיהיה שינוי איטי
                        // הערך של dy הוא בפיקסלים, יכול להיות מהיר מאוד
                        val sensitivity = 500f // מספר גדול = שינוי איטי יותר

                        if (isRightSide) {
                            // בהירות
                            val changeAmount = -dy / sensitivity // מינוס כי למעלה זה שלילי ב-Android
                            currentBrightnessLevel = adjustAppBrightness(context, changeAmount)
                            showBrightnessIndicator = true
                            showVolumeIndicator = false
                        } else {
                            // ווליום
                            val changeAmount = -dy / sensitivity
                            currentVolumeLevel = adjustAppVolume(context, changeAmount)
                            showVolumeIndicator = true
                            showBrightnessIndicator = false
                        }
                    }
                }
            }
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

        // Seek Indicator
        AnimatedVisibility(
            visible = showSeekIndicator,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.Center)
        ) {
            Box(
                modifier = Modifier
                    .background(Color.Black.copy(0.7f), RoundedCornerShape(16.dp))
                    .padding(24.dp)
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    // הצגת זמן + וכמה שניות קפצנו
                    val diff = seekPreviewTime - currentPosition
                    val sign = if (diff > 0) "+" else ""
                    Text(
                        text = "${formatTime(seekPreviewTime)}",
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

        // Volume Indicator
        AnimatedVisibility(
            visible = showVolumeIndicator,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.CenterStart).padding(start = 30.dp)
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Default.VolumeUp, null, tint = Color.White)
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
            }
        }

        // Brightness Indicator
        AnimatedVisibility(
            visible = showBrightnessIndicator,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.CenterEnd).padding(end = 30.dp)
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Default.Brightness6, null, tint = Color.Yellow)
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
            }
        }

        // Controls
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
                    IconButton(onClick = { isLooping = !isLooping }) {
                        Icon(
                            if (isLooping) Icons.Default.RepeatOne else Icons.Default.Repeat,
                            null, tint = if (isLooping) MaterialTheme.colorScheme.primary else Color.White,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onPrev) { Icon(Icons.Default.SkipPrevious, null, tint = Color.White, modifier = Modifier.size(48.dp)) }
                    IconButton(onClick = {
                        if (isPlaying) exoPlayer.pause() else exoPlayer.play()
                        isPlaying = !isPlaying
                    }) {
                        Icon(
                            if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            null, tint = Color.White, modifier = Modifier.size(64.dp)
                        )
                    }
                    IconButton(onClick = onNext) { Icon(Icons.Default.SkipNext, null, tint = Color.White, modifier = Modifier.size(48.dp)) }
                }
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(formatTime(currentPosition), color = Color.White)
                        Text(formatTime(duration), color = Color.White)
                    }
                    Slider(
                        value = currentPosition.toFloat(),
                        onValueChange = {
                            currentPosition = it.toLong()
                            exoPlayer.seekTo(currentPosition)
                        },
                        valueRange = 0f..duration.toFloat(),
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
}

// --- פונקציות מתוקנות לרגישות נמוכה ---

fun adjustAppVolume(context: Context, deltaPercent: Float): Float {
    val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    val current = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
    val max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)

    // deltaPercent הוא מספר קטן (למשל 0.05). נכפיל אותו ב-max כדי לדעת כמה לשנות
    // אבל אנחנו רוצים שינוי איטי. אז נקבע רף מינימלי לשינוי

    val change = (deltaPercent * max).toInt()
    // אם השינוי קטן מדי, לא עושים כלום (כדי למנוע ריצוד)
    if (change == 0 && abs(deltaPercent) < 0.02) return current.toFloat() / max.toFloat()

    // מוודאים שלפחות יש שינוי של 1 אם הגלילה משמעותית
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

    // שינוי הבהירות. deltaPercent הוא השינוי היחסי.
    val newBright = (current + deltaPercent).coerceIn(0.01f, 1f)

    lp.screenBrightness = newBright
    activity.window.attributes = lp
    return newBright
}

fun shareMedia(context: Context, uri: android.net.Uri, isVideo: Boolean) {
    try {
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = if (isVideo) "video/*" else "image/*"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(shareIntent, "Share Media"))
    } catch (e: Exception) {
        // Ignore
    }
}

fun formatTime(ms: Long): String {
    val m = TimeUnit.MILLISECONDS.toMinutes(ms)
    val s = TimeUnit.MILLISECONDS.toSeconds(ms) - TimeUnit.MINUTES.toSeconds(m)
    return String.format("%02d:%02d", m, s)
}
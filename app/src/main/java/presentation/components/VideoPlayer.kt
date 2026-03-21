package com.example.customgalleryviewer.presentation.components

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.media.AudioManager
import android.net.Uri
import android.provider.MediaStore
import android.view.TextureView
import android.widget.FrameLayout
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import android.util.Log
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
    onToggleShuffle: () -> Unit = {},
    onToggleRepeatList: () -> Unit = {},
    isShuffleOn: Boolean = false,
    isRepeatListOn: Boolean = false,
    navigationMode: String = "TAP",
    initialPosition: Long = 0L,
    onSavePosition: (Uri, Long, Long) -> Unit = { _, _, _ -> },
    onToggleFavorite: (Uri) -> Unit = {},
    isFavorite: Boolean = false,
    onDelete: (Uri) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefs = remember { context.getSharedPreferences("gallery_settings", android.content.Context.MODE_PRIVATE) }

    var isPlaying by remember { mutableStateOf(true) }
    var isControlsVisible by remember { mutableStateOf(false) }
    var showActionMenu by remember { mutableStateOf(false) }
    var showSpeedMenu by remember { mutableStateOf(false) }

    var currentPosition by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(0L) }
    var isLooping by remember { mutableStateOf(prefs.getBoolean("loop_one", false)) }
    var playbackSpeed by remember { mutableFloatStateOf(1f) }
    var manualNavTime by remember { mutableLongStateOf(0L) }

    var mediaGeneration by remember { mutableIntStateOf(0) }
    var endHandledForGeneration by remember { mutableIntStateOf(-1) }

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

    var screenHeight by remember { mutableFloatStateOf(0f) }
    var isMirrored by remember { mutableStateOf(false) }
    var isMuted by remember { mutableStateOf(prefs.getBoolean("is_muted", false)) }

    val speedOptions = listOf(0.25f, 0.5f, 1f, 1.5f, 2f, 4f)

    val exoPlayer = remember {
        ExoPlayer.Builder(context).build().apply {
            playWhenReady = true
            repeatMode = Player.REPEAT_MODE_OFF
        }
    }

    LaunchedEffect(uri) {
        mediaGeneration++
        isDragging = false
        isSliderDragging = false
        showSeekIndicator = false
        showVolumeIndicator = false
        showBrightnessIndicator = false
        showActionMenu = false
        isControlsVisible = false
        currentPosition = 0L
        duration = 0L

        exoPlayer.stop()
        exoPlayer.clearMediaItems()
        val uriStr = uri.toString().lowercase()
        // Detect HLS by file extension at end of path, not substring match
        val ext = uriStr.substringAfterLast('.', "").substringBefore('?')
        // Also detect by filename containing m3u8 (e.g., "playlist_m3u8" with no extension)
        val lastSegment = uri.lastPathSegment?.lowercase() ?: ""
        val isHls = ext == "m3u8" || ext == "m3u" ||
            (lastSegment.contains("m3u8") || lastSegment.contains("m3u")) && ext != "ts" ||
            detectHlsFromContent(context, uri)
        Log.w("VideoPlayer", "Loading URI: $uri ext=$ext lastSeg=$lastSegment isHls=$isHls")
        val mediaItem = when {
            isHls ->
                MediaItem.Builder()
                    .setUri(uri)
                    .setMimeType(MimeTypes.APPLICATION_M3U8)
                    .build()
            ext == "mpd" ->
                MediaItem.Builder()
                    .setUri(uri)
                    .setMimeType(MimeTypes.APPLICATION_MPD)
                    .build()
            else -> MediaItem.fromUri(uri)
        }
        Log.w("VideoPlayer", "MediaItem MIME: ${mediaItem.localConfiguration?.mimeType ?: "auto"}")
        exoPlayer.setMediaItem(mediaItem)
        exoPlayer.seekTo(if (initialPosition > 0) initialPosition else 0)
        exoPlayer.prepare()
        exoPlayer.playWhenReady = true
        exoPlayer.volume = if (isMuted) 0f else 1f
    }

    LaunchedEffect(isLooping) {
        exoPlayer.repeatMode = if (isLooping) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
    }

    LaunchedEffect(playbackSpeed) {
        exoPlayer.setPlaybackSpeed(playbackSpeed)
    }

    LaunchedEffect(isControlsVisible, isDragging, isSliderDragging) {
        if (isControlsVisible && !isDragging && !isSliderDragging) {
            delay(4000)
            isControlsVisible = false
        }
    }

    LaunchedEffect(Unit) {
        while (true) {
            if (!isDragging && !isSliderDragging) {
                currentPosition = exoPlayer.currentPosition
                duration = exoPlayer.duration.coerceAtLeast(0L)
                isPlaying = exoPlayer.isPlaying

                if (exoPlayer.playbackState == Player.STATE_ENDED &&
                    !isLooping &&
                    endHandledForGeneration != mediaGeneration &&
                    duration > 0 &&
                    currentPosition > 0 &&
                    System.currentTimeMillis() - manualNavTime > 500
                ) {
                    endHandledForGeneration = mediaGeneration
                    delay(300)
                    if (System.currentTimeMillis() - manualNavTime > 500) {
                        onNext()
                    }
                }
            }
            delay(100)
        }
    }

    // Save watch position every 5 seconds
    LaunchedEffect(uri) {
        while (true) {
            delay(5000)
            val pos = exoPlayer.currentPosition
            val dur = exoPlayer.duration.coerceAtLeast(0L)
            if (pos > 0 && dur > 0) {
                onSavePosition(uri, pos, dur)
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            val pos = exoPlayer.currentPosition
            val dur = exoPlayer.duration.coerceAtLeast(0L)
            if (pos > 0 && dur > 0) {
                onSavePosition(uri, pos, dur)
            }
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
            }
    ) {
        // Video layer
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    player = exoPlayer
                    useController = false
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                    layoutParams = FrameLayout.LayoutParams(-1, -1)
                }
            },
            update = { pv ->
                pv.scaleX = if (isMirrored) -1f else 1f
            },
            modifier = Modifier.fillMaxSize()
        )

        // Gesture layer — only active when controls are HIDDEN
        // When controls visible, the overlay handles dismissal internally
        if (!isControlsVisible) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(navigationMode) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val downTime = System.currentTimeMillis()
                        val downPos = down.position

                        seekStartPosition = currentPosition
                        startVolume = getCurrentVolume(context)
                        startBrightness = getCurrentBrightness(context)
                        totalDragDistanceX = 0f
                        totalDragDistanceY = 0f
                        isSeekMode = false

                        var dragStarted = false
                        var gestureHandled = false

                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull() ?: break
                            if (change.changedToUp()) {
                                val upTime = System.currentTimeMillis()
                                val elapsed = upTime - downTime

                                if (dragStarted && !isControlsVisible) {
                                    // Drag finished
                                    if (isSeekMode && showSeekIndicator) {
                                        exoPlayer.seekTo(seekPreviewTime)
                                        if (!exoPlayer.isPlaying) exoPlayer.play()
                                    }
                                    scope.launch {
                                        delay(500)
                                        showSeekIndicator = false
                                        showVolumeIndicator = false
                                        showBrightnessIndicator = false
                                    }
                                    isDragging = false
                                    gestureHandled = true
                                } else if (elapsed > 400 && !dragStarted) {
                                    // Long press
                                    showActionMenu = true
                                    gestureHandled = true
                                }

                                if (!gestureHandled) {
                                    // Could be tap or first tap of double-tap
                                    // Wait briefly for possible second tap
                                    val secondDown = withTimeoutOrNull(300) {
                                        awaitFirstDown(requireUnconsumed = false)
                                    }
                                    if (secondDown != null) {
                                        // Double tap detected — wait for its up
                                        while (true) {
                                            val ev = awaitPointerEvent()
                                            if (ev.changes.firstOrNull()?.changedToUp() == true) break
                                        }
                                        val width = size.width
                                        when {
                                            downPos.x < width * 0.3 -> {
                                                manualNavTime = System.currentTimeMillis()
                                                Log.d("VideoPlayer", "Double-tap prev")
                                                onPrev()
                                            }
                                            downPos.x > width * 0.7 -> {
                                                manualNavTime = System.currentTimeMillis()
                                                Log.d("VideoPlayer", "Double-tap next")
                                                onNext()
                                            }
                                            else -> isControlsVisible = !isControlsVisible
                                        }
                                    } else {
                                        // Single tap
                                        Log.w("VideoPlayer", "TAP: toggling controls ${!isControlsVisible}")
                                        isControlsVisible = !isControlsVisible
                                    }
                                }
                                break
                            }

                            val dragDelta = change.positionChange()
                            if (dragDelta == androidx.compose.ui.geometry.Offset.Zero) continue

                            // Only handle drags when controls are hidden
                            if (isControlsVisible) continue

                            totalDragDistanceX += dragDelta.x
                            totalDragDistanceY += dragDelta.y

                            val totalAbsX = abs(totalDragDistanceX)
                            val totalAbsY = abs(totalDragDistanceY)

                            if (!dragStarted && (totalAbsX > 15 || totalAbsY > 15)) {
                                dragStarted = true
                                isDragging = true
                                change.consume()
                            }

                            if (!dragStarted) continue
                            change.consume()

                            if (!isSeekMode && !showVolumeIndicator && !showBrightnessIndicator) {
                                if (totalAbsX > totalAbsY) {
                                    if (totalAbsX > 10) isSeekMode = true
                                } else {
                                    if (totalAbsY > 10) {
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
                }
        )
        }

        // Controls overlay — single AnimatedVisibility with ALL controls including seek bar
        AnimatedVisibility(
            visible = isControlsVisible,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.fillMaxSize()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(0.45f))
            ) {
                // Dismissal layer — first child (lowest z). Taps on empty space dismiss controls.
                // Slider/buttons are later children (higher z) and get events first.
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(Unit) {
                            detectTapGestures(
                                onTap = { isControlsVisible = false },
                                onLongPress = { showActionMenu = true }
                            )
                        }
                )

                // Top controls
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 8.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Left group
                    Surface(
                        shape = RoundedCornerShape(24.dp),
                        color = Color.Black.copy(0.4f)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            ControlIconButton(
                                icon = Icons.Default.RepeatOne,
                                label = "Repeat",
                                isActive = isLooping,
                                onClick = {
                                    isLooping = !isLooping
                                    prefs.edit().putBoolean("loop_one", isLooping).apply()
                                    // Mutually exclusive: turn off repeat list if loop one is on
                                    if (isLooping && isRepeatListOn) onToggleRepeatList()
                                }
                            )
                            ControlIconButton(
                                icon = Icons.Default.Repeat,
                                label = "Repeat List",
                                isActive = isRepeatListOn,
                                onClick = {
                                    onToggleRepeatList()
                                    // Mutually exclusive: turn off loop one if repeat list is on
                                    if (!isRepeatListOn && isLooping) {
                                        isLooping = false
                                        prefs.edit().putBoolean("loop_one", false).apply()
                                    }
                                }
                            )
                            ControlIconButton(
                                icon = Icons.Default.Shuffle,
                                label = "Shuffle",
                                isActive = isShuffleOn,
                                onClick = onToggleShuffle
                            )
                        }
                    }

                    // Right group
                    Surface(
                        shape = RoundedCornerShape(24.dp),
                        color = Color.Black.copy(0.4f)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            ControlIconButton(
                                icon = Icons.Default.Flip,
                                label = "Mirror",
                                isActive = isMirrored,
                                onClick = { isMirrored = !isMirrored }
                            )
                            ControlIconButton(
                                icon = Icons.Default.ScreenRotation,
                                label = "Rotate",
                                isActive = false,
                                onClick = {
                                    val activity = context as? Activity ?: return@ControlIconButton
                                    activity.requestedOrientation =
                                        if (activity.requestedOrientation == ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE)
                                            ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                                        else
                                            ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                                }
                            )
                            ControlIconButton(
                                icon = if (isMuted) Icons.Default.VolumeOff else Icons.Default.VolumeUp,
                                label = "Mute",
                                isActive = isMuted,
                                onClick = {
                                    isMuted = !isMuted
                                    exoPlayer.volume = if (isMuted) 0f else 1f
                                    prefs.edit().putBoolean("is_muted", isMuted).apply()
                                }
                            )
                            // Speed
                            Box {
                                TextButton(
                                    onClick = { showSpeedMenu = !showSpeedMenu },
                                    modifier = Modifier.height(40.dp),
                                    contentPadding = PaddingValues(horizontal = 8.dp)
                                ) {
                                    Text(
                                        "${playbackSpeed}x",
                                        color = if (playbackSpeed != 1f) Color(0xFF00E5FF) else Color.White.copy(0.7f),
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                DropdownMenu(
                                    expanded = showSpeedMenu,
                                    onDismissRequest = { showSpeedMenu = false }
                                ) {
                                    speedOptions.forEach { speed ->
                                        DropdownMenuItem(
                                            text = {
                                                Text(
                                                    "${speed}x",
                                                    fontWeight = if (speed == playbackSpeed) FontWeight.Bold else FontWeight.Normal,
                                                    color = if (speed == playbackSpeed) Color(0xFF00E5FF) else Color.Unspecified
                                                )
                                            },
                                            onClick = {
                                                playbackSpeed = speed
                                                showSpeedMenu = false
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Center play controls
                Row(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalArrangement = Arrangement.spacedBy(40.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = { onPrev() },
                        modifier = Modifier.size(52.dp)
                    ) {
                        Icon(
                            Icons.Default.SkipPrevious, "Previous",
                            tint = Color.White,
                            modifier = Modifier.size(36.dp)
                        )
                    }

                    Surface(
                        onClick = {
                            if (isPlaying) exoPlayer.pause() else exoPlayer.play()
                            scope.launch { isControlsVisible = true }
                        },
                        shape = CircleShape,
                        color = Color.White.copy(0.15f),
                        modifier = Modifier.size(72.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                "Play/Pause",
                                tint = Color.White,
                                modifier = Modifier.size(44.dp)
                            )
                        }
                    }

                    IconButton(
                        onClick = { onNext() },
                        modifier = Modifier.size(52.dp)
                    ) {
                        Icon(
                            Icons.Default.SkipNext, "Next",
                            tint = Color.White,
                            modifier = Modifier.size(36.dp)
                        )
                    }
                }

                // Bottom timeline with seek bar — inside the controls overlay
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(Color.Transparent, Color.Black.copy(0.7f))
                            )
                        )
                        .padding(horizontal = 20.dp)
                        .padding(bottom = 20.dp, top = 32.dp)
                ) {
                    val displayTime = if (isSliderDragging) sliderPosition else currentPosition

                    // Custom seek bar — raw Canvas + pointerInput, no Compose Slider
                    val trackHeight = 4.dp
                    val thumbRadius = 8.dp
                    val hitTargetHeight = 48.dp

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(hitTargetHeight)
                            .pointerInput(duration) {
                                awaitEachGesture {
                                    val down = awaitFirstDown(requireUnconsumed = true)
                                    down.consume()
                                    isSliderDragging = true

                                    // Calculate position from touch
                                    val w = size.width.toFloat()
                                    val fraction = (down.position.x / w).coerceIn(0f, 1f)
                                    sliderPosition = (fraction * duration).toLong()

                                    // Track drag
                                    while (true) {
                                        val event = awaitPointerEvent()
                                        val change = event.changes.firstOrNull() ?: break
                                        if (change.changedToUp()) {
                                            change.consume()
                                            Log.w("VideoPlayer", "SEEKBAR seek to $sliderPosition")
                                            exoPlayer.seekTo(sliderPosition)
                                            isSliderDragging = false
                                            break
                                        }
                                        change.consume()
                                        val dragFraction = (change.position.x / w).coerceIn(0f, 1f)
                                        sliderPosition = (dragFraction * duration).toLong()
                                    }
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Canvas(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(trackHeight)
                        ) {
                            val w = size.width
                            val h = size.height
                            val progress = if (duration > 0) {
                                if (isSliderDragging) sliderPosition.toFloat() / duration
                                else currentPosition.toFloat() / duration
                            } else 0f
                            val thumbX = w * progress
                            val rPx = thumbRadius.toPx()

                            // Track background
                            drawRoundRect(
                                color = Color.White.copy(0.2f),
                                size = androidx.compose.ui.geometry.Size(w, h),
                                cornerRadius = androidx.compose.ui.geometry.CornerRadius(h / 2)
                            )
                            // Track progress
                            if (thumbX > 0) {
                                drawRoundRect(
                                    color = Color.White,
                                    size = androidx.compose.ui.geometry.Size(thumbX, h),
                                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(h / 2)
                                )
                            }
                            // Thumb
                            drawCircle(
                                color = Color.White,
                                radius = rPx,
                                center = androidx.compose.ui.geometry.Offset(thumbX.coerceIn(rPx, w - rPx), h / 2)
                            )
                        }
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 2.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(formatTime(displayTime), color = Color.White.copy(0.7f), fontSize = 12.sp)
                        Text(formatTime(duration), color = Color.White.copy(0.4f), fontSize = 12.sp)
                    }
                }
            }
        }

        // Seek indicator
        AnimatedVisibility(
            visible = showSeekIndicator,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.Center)
        ) {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = Color.Black.copy(0.8f)
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(horizontal = 28.dp, vertical = 16.dp)
                ) {
                    val diff = seekPreviewTime - seekStartPosition
                    val sign = if (diff > 0) "+" else ""
                    Text(
                        formatTime(seekPreviewTime),
                        color = Color.White,
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "($sign${TimeUnit.MILLISECONDS.toSeconds(diff)}s)",
                        color = if (diff > 0) Color(0xFF4CAF50) else Color(0xFFFF5252),
                        fontSize = 14.sp
                    )
                }
            }
        }

        // Volume indicator
        AnimatedVisibility(
            visible = showVolumeIndicator,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(start = 20.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = Color.Black.copy(0.6f)
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(14.dp)
                ) {
                    Icon(Icons.Default.VolumeUp, null, tint = Color.White, modifier = Modifier.size(22.dp))
                    Spacer(Modifier.height(8.dp))
                    LinearProgressIndicator(
                        progress = { currentVolumeLevel },
                        modifier = Modifier
                            .height(100.dp)
                            .width(5.dp)
                            .clip(RoundedCornerShape(3.dp)),
                        color = Color.White,
                        trackColor = Color.White.copy(0.15f)
                    )
                    Spacer(Modifier.height(6.dp))
                    Text("${(currentVolumeLevel * 100).toInt()}%", color = Color.White.copy(0.8f), fontSize = 11.sp)
                }
            }
        }

        // Brightness indicator
        AnimatedVisibility(
            visible = showBrightnessIndicator,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 20.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = Color.Black.copy(0.6f)
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(14.dp)
                ) {
                    Icon(Icons.Default.Brightness6, null, tint = Color.Yellow, modifier = Modifier.size(22.dp))
                    Spacer(Modifier.height(8.dp))
                    LinearProgressIndicator(
                        progress = { currentBrightnessLevel },
                        modifier = Modifier
                            .height(100.dp)
                            .width(5.dp)
                            .clip(RoundedCornerShape(3.dp)),
                        color = Color.Yellow,
                        trackColor = Color.White.copy(0.15f)
                    )
                    Spacer(Modifier.height(6.dp))
                    Text("${(currentBrightnessLevel * 100).toInt()}%", color = Color.White.copy(0.8f), fontSize = 11.sp)
                }
            }
        }

        if (showActionMenu) {
            ActionMenuDialog(
                uri = uri,
                isVideo = true,
                onDismiss = { showActionMenu = false },
                onToggleFavorite = onToggleFavorite,
                isFavorite = isFavorite,
                onDelete = onDelete
            )
        }
    }
}

@Composable
private fun ControlIconButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    isActive: Boolean,
    onClick: () -> Unit
) {
    IconButton(
        onClick = onClick,
        modifier = Modifier.size(40.dp)
    ) {
        Icon(
            icon, label,
            tint = if (isActive) Color(0xFF00E5FF) else Color.White.copy(0.5f),
            modifier = Modifier.size(20.dp)
        )
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
fun ActionMenuDialog(
    uri: Uri,
    isVideo: Boolean,
    onDismiss: () -> Unit,
    onFileChanged: () -> Unit = {},
    onToggleFavorite: (Uri) -> Unit = {},
    isFavorite: Boolean = false,
    onDelete: (Uri) -> Unit = {},
    onRenameFile: (Uri, String) -> Unit = { _, _ -> }
) {
    val context = LocalContext.current
    val mediaInfo = remember { getMediaInfo(context, uri) }
    var folderPickerOp by remember { mutableStateOf<FileOperation?>(null) }
    var showRenameDialog by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Media Info", fontWeight = FontWeight.SemiBold) },
        text = {
            Column {
                InfoRow("Name:", mediaInfo.name)
                InfoRow("Size:", mediaInfo.size)
                InfoRow("Date:", mediaInfo.date)
                HorizontalDivider(Modifier.padding(vertical = 12.dp))
                TextButton(onClick = { onToggleFavorite(uri); onDismiss() }) {
                    Icon(
                        if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        null,
                        tint = if (isFavorite) Color(0xFFFF4081) else MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(if (isFavorite) "Unfavorite" else "Favorite")
                }
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
                TextButton(onClick = { folderPickerOp = FileOperation.COPY }) {
                    Icon(Icons.Default.FileCopy, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Copy to...")
                }
                TextButton(onClick = { folderPickerOp = FileOperation.MOVE }) {
                    Icon(Icons.Default.DriveFileMove, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Move to...")
                }
                TextButton(onClick = { showRenameDialog = true }) {
                    Icon(Icons.Default.Edit, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Rename")
                }
                TextButton(onClick = { onDelete(uri); onDismiss() }) {
                    Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error)
                    Spacer(Modifier.width(8.dp))
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
        shape = RoundedCornerShape(20.dp)
    )

    if (folderPickerOp != null) {
        FolderPickerDialog(
            uri = uri,
            operation = folderPickerOp!!,
            onDismiss = { folderPickerOp = null },
            onComplete = {
                folderPickerOp = null
                onFileChanged()
                onDismiss()
            }
        )
    }

    if (showRenameDialog) {
        var newName by remember { mutableStateOf(mediaInfo.name) }
        AlertDialog(
            onDismissRequest = { showRenameDialog = false },
            title = { Text("Rename") },
            text = {
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    label = { Text("File name") },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (newName.isNotBlank()) {
                        onRenameFile(uri, newName.trim())
                    }
                    showRenameDialog = false
                    onDismiss()
                }) { Text("Rename") }
            },
            dismissButton = {
                TextButton(onClick = { showRenameDialog = false }) { Text("Cancel") }
            },
            shape = RoundedCornerShape(20.dp)
        )
    }
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
        name = "Error reading info"
    }

    return MediaInfo(name, size, path, date, duration)
}

fun formatFileSize(bytes: Long): String {
    val mb = bytes / (1024.0 * 1024.0)
    return if (mb >= 1) String.format("%.1f MB", mb)
    else String.format("%.0f KB", bytes / 1024.0)
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

private fun toContentUri(context: Context, uri: Uri): Uri {
    if (uri.scheme == "file" && uri.path != null) {
        return try {
            androidx.core.content.FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                File(uri.path!!)
            )
        } catch (e: Exception) {
            uri
        }
    }
    return uri
}

fun shareMedia(context: Context, uri: Uri, isVideo: Boolean) {
    val contentUri = toContentUri(context, uri)
    val i = Intent(Intent.ACTION_SEND).apply {
        type = if (isVideo) "video/*" else "image/*"
        putExtra(Intent.EXTRA_STREAM, contentUri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(i, "Share"))
}

fun openWith(context: Context, uri: Uri, isVideo: Boolean) {
    val contentUri = toContentUri(context, uri)
    val i = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(contentUri, if (isVideo) "video/*" else "image/*")
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(i, "Open"))
}

fun detectHlsFromContent(context: Context, uri: Uri): Boolean {
    try {
        if (uri.scheme == "file") {
            val file = File(uri.path ?: return false)
            if (file.exists() && file.length() < 1_000_000) { // Only check small files
                val header = file.bufferedReader().use { it.readLine() }
                return header?.trim() == "#EXTM3U"
            }
        } else {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                val header = stream.bufferedReader().readLine()
                return header?.trim() == "#EXTM3U"
            }
        }
    } catch (_: Exception) { }
    return false
}

fun formatTime(ms: Long): String {
    val h = TimeUnit.MILLISECONDS.toHours(ms)
    val m = TimeUnit.MILLISECONDS.toMinutes(ms) % 60
    val s = TimeUnit.MILLISECONDS.toSeconds(ms) % 60
    return if (h > 0) String.format("%d:%02d:%02d", h, m, s)
    else String.format("%02d:%02d", m, s)
}

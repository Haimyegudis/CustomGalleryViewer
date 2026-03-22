package com.example.customgalleryviewer.presentation.components

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ColorMatrixColorFilter
import android.graphics.Matrix
import android.media.MediaScannerConnection
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.decode.VideoFrameDecoder
import coil.request.ImageRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

enum class EditorTab { TRANSFORM, ADJUST, FILTER, CROP, DRAW, RETOUCH }
enum class RetouchTool { CLONE, BLUR, PIXELATE }

data class DrawPath(val points: List<Offset>, val color: Color, val width: Float)

data class FilterPreset(
    val name: String,
    val matrix: ColorMatrix
)

private val filterPresets = listOf(
    FilterPreset("Original", ColorMatrix()),
    FilterPreset("B&W", ColorMatrix(floatArrayOf(
        0.33f, 0.33f, 0.33f, 0f, 0f,
        0.33f, 0.33f, 0.33f, 0f, 0f,
        0.33f, 0.33f, 0.33f, 0f, 0f,
        0f, 0f, 0f, 1f, 0f
    ))),
    FilterPreset("Sepia", ColorMatrix(floatArrayOf(
        0.393f, 0.769f, 0.189f, 0f, 0f,
        0.349f, 0.686f, 0.168f, 0f, 0f,
        0.272f, 0.534f, 0.131f, 0f, 0f,
        0f, 0f, 0f, 1f, 0f
    ))),
    FilterPreset("Warm", ColorMatrix(floatArrayOf(
        1.2f, 0f, 0f, 0f, 10f,
        0f, 1.05f, 0f, 0f, 5f,
        0f, 0f, 0.9f, 0f, -10f,
        0f, 0f, 0f, 1f, 0f
    ))),
    FilterPreset("Cool", ColorMatrix(floatArrayOf(
        0.9f, 0f, 0f, 0f, -10f,
        0f, 1.0f, 0f, 0f, 0f,
        0f, 0f, 1.2f, 0f, 15f,
        0f, 0f, 0f, 1f, 0f
    ))),
    FilterPreset("Vivid", ColorMatrix(floatArrayOf(
        1.3f, -0.1f, -0.1f, 0f, 10f,
        -0.1f, 1.3f, -0.1f, 0f, 10f,
        -0.1f, -0.1f, 1.3f, 0f, 10f,
        0f, 0f, 0f, 1f, 0f
    ))),
    FilterPreset("Fade", ColorMatrix(floatArrayOf(
        1f, 0f, 0f, 0f, 30f,
        0f, 1f, 0f, 0f, 30f,
        0f, 0f, 1f, 0f, 30f,
        0f, 0f, 0f, 0.9f, 0f
    ))),
    FilterPreset("Noir", ColorMatrix(floatArrayOf(
        0.5f, 0.5f, 0.5f, 0f, -40f,
        0.3f, 0.3f, 0.3f, 0f, -20f,
        0.2f, 0.2f, 0.2f, 0f, -10f,
        0f, 0f, 0f, 1f, 0f
    ))),
    FilterPreset("Invert", ColorMatrix(floatArrayOf(
        -1f, 0f, 0f, 0f, 255f,
        0f, -1f, 0f, 0f, 255f,
        0f, 0f, -1f, 0f, 255f,
        0f, 0f, 0f, 1f, 0f
    )))
)

@Composable
fun MediaEditorDialog(
    uri: Uri,
    onDismiss: () -> Unit,
    onSaved: (Uri) -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // Load bitmap
    var originalBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var isSaving by remember { mutableStateOf(false) }
    var selectedTab by remember { mutableStateOf(EditorTab.TRANSFORM) }

    // Transform state
    var rotation by remember { mutableFloatStateOf(0f) }
    var flipH by remember { mutableStateOf(false) }
    var flipV by remember { mutableStateOf(false) }

    // Adjust state
    var brightness by remember { mutableFloatStateOf(0f) } // -100 to 100
    var contrast by remember { mutableFloatStateOf(1f) }   // 0.5 to 2.0
    var saturation by remember { mutableFloatStateOf(1f) }  // 0 to 2.0

    // Filter state
    var selectedFilter by remember { mutableIntStateOf(0) }

    // Image display rect (for mapping draw coords to bitmap coords)
    var imgDisplayOffsetX by remember { mutableFloatStateOf(0f) }
    var imgDisplayOffsetY by remember { mutableFloatStateOf(0f) }
    var imgDisplayW by remember { mutableFloatStateOf(1f) }
    var imgDisplayH by remember { mutableFloatStateOf(1f) }

    // Draw state
    val drawPaths = remember { mutableStateListOf<DrawPath>() }
    var currentDrawPoints = remember { mutableStateListOf<Offset>() }
    var drawColor by remember { mutableStateOf(Color.Red) }
    var drawWidth by remember { mutableFloatStateOf(8f) }

    val drawColors = listOf(
        Color.Red, Color(0xFFFF9800), Color.Yellow, Color(0xFF4CAF50),
        Color(0xFF2196F3), Color(0xFF9C27B0), Color.White, Color.Black
    )

    // Retouch state
    var retouchTool by remember { mutableStateOf(RetouchTool.BLUR) }
    var retouchRadius by remember { mutableFloatStateOf(20f) }
    var cloneSource by remember { mutableStateOf<Offset?>(null) }
    var cloneOffset by remember { mutableStateOf(Offset.Zero) }
    var isSettingCloneSource by remember { mutableStateOf(true) }
    var retouchBitmap by remember { mutableStateOf<Bitmap?>(null) }
    // Track retouch changes for undo
    val retouchHistory = remember { mutableStateListOf<Bitmap>() }

    // Crop state
    var cropLeft by remember { mutableFloatStateOf(0.05f) }
    var cropTop by remember { mutableFloatStateOf(0.05f) }
    var cropRight by remember { mutableFloatStateOf(0.95f) }
    var cropBottom by remember { mutableFloatStateOf(0.95f) }
    var isCropActive by remember { mutableStateOf(false) }

    LaunchedEffect(uri) {
        withContext(Dispatchers.IO) {
            val bmp = loadBitmap(context, uri)
            withContext(Dispatchers.Main) {
                originalBitmap = bmp
                retouchBitmap = bmp?.copy(Bitmap.Config.ARGB_8888, true)
            }
        }
    }

    // Build combined color matrix
    val combinedMatrix = remember(brightness, contrast, saturation, selectedFilter) {
        val cm = ColorMatrix()
        // Contrast
        val c = contrast
        val t = (1f - c) * 128f
        cm.timesAssign(ColorMatrix(floatArrayOf(
            c, 0f, 0f, 0f, t,
            0f, c, 0f, 0f, t,
            0f, 0f, c, 0f, 0f,
            0f, 0f, 0f, 1f, 0f
        )))
        // Brightness
        cm.timesAssign(ColorMatrix(floatArrayOf(
            1f, 0f, 0f, 0f, brightness,
            0f, 1f, 0f, 0f, brightness,
            0f, 0f, 1f, 0f, brightness,
            0f, 0f, 0f, 1f, 0f
        )))
        // Saturation
        val s = saturation
        val sr = 0.2126f * (1 - s)
        val sg = 0.7152f * (1 - s)
        val sb = 0.0722f * (1 - s)
        cm.timesAssign(ColorMatrix(floatArrayOf(
            sr + s, sg, sb, 0f, 0f,
            sr, sg + s, sb, 0f, 0f,
            sr, sg, sb + s, 0f, 0f,
            0f, 0f, 0f, 1f, 0f
        )))
        // Filter
        if (selectedFilter > 0 && selectedFilter < filterPresets.size) {
            cm.timesAssign(filterPresets[selectedFilter].matrix)
        }
        cm
    }

    androidx.compose.ui.window.Dialog(
        onDismissRequest = { if (!isSaving) onDismiss() },
        properties = androidx.compose.ui.window.DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = Color.Black
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Top bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss, enabled = !isSaving) {
                        Text("Cancel", color = Color.White)
                    }
                    Text("Edit", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 18.sp)
                    TextButton(
                        onClick = {
                            val bmp = retouchBitmap ?: originalBitmap ?: return@TextButton
                            isSaving = true
                            scope.launch(Dispatchers.IO) {
                                val result = applyEdits(
                                    context, bmp, uri, rotation, flipH, flipV,
                                    brightness, contrast, saturation,
                                    if (selectedFilter > 0) filterPresets[selectedFilter].matrix else null,
                                    if (isCropActive) floatArrayOf(cropLeft, cropTop, cropRight, cropBottom) else null,
                                    drawPaths.toList(),
                                    imgDisplayOffsetX, imgDisplayOffsetY, imgDisplayW, imgDisplayH
                                )
                                withContext(Dispatchers.Main) {
                                    isSaving = false
                                    if (result != null) {
                                        Toast.makeText(context, "Saved", Toast.LENGTH_SHORT).show()
                                        onSaved(result)
                                        onDismiss()
                                    } else {
                                        Toast.makeText(context, "Save failed", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }
                        },
                        enabled = !isSaving
                    ) {
                        if (isSaving) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
                        } else {
                            Text("Save", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                // Preview area
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    val workingBmp = retouchBitmap ?: originalBitmap
                    if (workingBmp != null) {
                        val bmp = workingBmp.asImageBitmap()
                        var canvasSize by remember { mutableStateOf(IntSize.Zero) }

                        Canvas(
                            modifier = Modifier
                                .fillMaxSize()
                                .onGloballyPositioned { canvasSize = it.size }
                                .then(
                                    if (selectedTab == EditorTab.CROP) {
                                        Modifier.pointerInput(Unit) {
                                            detectDragGestures { change, dragAmount ->
                                                change.consume()
                                                val w = size.width.toFloat()
                                                val h = size.height.toFloat()
                                                val dx = dragAmount.x / w
                                                val dy = dragAmount.y / h
                                                val pos = change.position
                                                val fx = pos.x / w
                                                val fy = pos.y / h
                                                val nearLeft = kotlin.math.abs(fx - cropLeft) < 0.08f
                                                val nearRight = kotlin.math.abs(fx - cropRight) < 0.08f
                                                val nearTop = kotlin.math.abs(fy - cropTop) < 0.08f
                                                val nearBottom = kotlin.math.abs(fy - cropBottom) < 0.08f
                                                when {
                                                    nearLeft && nearTop -> { cropLeft = (cropLeft + dx).coerceIn(0f, cropRight - 0.1f); cropTop = (cropTop + dy).coerceIn(0f, cropBottom - 0.1f) }
                                                    nearRight && nearTop -> { cropRight = (cropRight + dx).coerceIn(cropLeft + 0.1f, 1f); cropTop = (cropTop + dy).coerceIn(0f, cropBottom - 0.1f) }
                                                    nearLeft && nearBottom -> { cropLeft = (cropLeft + dx).coerceIn(0f, cropRight - 0.1f); cropBottom = (cropBottom + dy).coerceIn(cropTop + 0.1f, 1f) }
                                                    nearRight && nearBottom -> { cropRight = (cropRight + dx).coerceIn(cropLeft + 0.1f, 1f); cropBottom = (cropBottom + dy).coerceIn(cropTop + 0.1f, 1f) }
                                                    nearLeft -> cropLeft = (cropLeft + dx).coerceIn(0f, cropRight - 0.1f)
                                                    nearRight -> cropRight = (cropRight + dx).coerceIn(cropLeft + 0.1f, 1f)
                                                    nearTop -> cropTop = (cropTop + dy).coerceIn(0f, cropBottom - 0.1f)
                                                    nearBottom -> cropBottom = (cropBottom + dy).coerceIn(cropTop + 0.1f, 1f)
                                                    else -> {
                                                        val w2 = cropRight - cropLeft
                                                        val h2 = cropBottom - cropTop
                                                        val newL = (cropLeft + dx).coerceIn(0f, 1f - w2)
                                                        val newT = (cropTop + dy).coerceIn(0f, 1f - h2)
                                                        cropLeft = newL; cropTop = newT; cropRight = newL + w2; cropBottom = newT + h2
                                                    }
                                                }
                                                isCropActive = true
                                            }
                                        }
                                    } else if (selectedTab == EditorTab.RETOUCH) {
                                        Modifier.pointerInput(retouchTool, retouchRadius, isSettingCloneSource) {
                                            detectDragGestures(
                                                onDragStart = { offset ->
                                                    val rb = retouchBitmap ?: return@detectDragGestures
                                                    if (retouchTool == RetouchTool.CLONE && isSettingCloneSource) {
                                                        cloneSource = offset
                                                        isSettingCloneSource = false
                                                        return@detectDragGestures
                                                    }
                                                    // Save state for undo
                                                    retouchHistory.add(rb.copy(Bitmap.Config.ARGB_8888, true))
                                                    if (retouchHistory.size > 15) retouchHistory.removeAt(0)
                                                    // Apply at start position
                                                    if (retouchTool == RetouchTool.CLONE && cloneSource != null) {
                                                        cloneOffset = Offset(offset.x - cloneSource!!.x, offset.y - cloneSource!!.y)
                                                    }
                                                    applyRetouchAt(rb, offset, retouchTool, retouchRadius, cloneOffset, imgDisplayOffsetX, imgDisplayOffsetY, imgDisplayW, imgDisplayH)
                                                    retouchBitmap = rb // trigger recompose
                                                },
                                                onDrag = { change, _ ->
                                                    change.consume()
                                                    val rb = retouchBitmap ?: return@detectDragGestures
                                                    applyRetouchAt(rb, change.position, retouchTool, retouchRadius, cloneOffset, imgDisplayOffsetX, imgDisplayOffsetY, imgDisplayW, imgDisplayH)
                                                    retouchBitmap = rb
                                                },
                                                onDragEnd = {},
                                                onDragCancel = {}
                                            )
                                        }
                                    } else if (selectedTab == EditorTab.DRAW) {
                                        Modifier.pointerInput(drawColor, drawWidth) {
                                            detectDragGestures(
                                                onDragStart = { offset ->
                                                    currentDrawPoints.clear()
                                                    currentDrawPoints.add(offset)
                                                },
                                                onDrag = { change, _ ->
                                                    change.consume()
                                                    currentDrawPoints.add(change.position)
                                                },
                                                onDragEnd = {
                                                    if (currentDrawPoints.size > 1) {
                                                        drawPaths.add(DrawPath(currentDrawPoints.toList(), drawColor, drawWidth))
                                                    }
                                                    currentDrawPoints.clear()
                                                },
                                                onDragCancel = { currentDrawPoints.clear() }
                                            )
                                        }
                                    } else Modifier
                                )
                        ) {
                            val cw = size.width
                            val ch = size.height
                            val bw = bmp.width.toFloat()
                            val bh = bmp.height.toFloat()

                            // Calculate fit scale
                            val scale = min(cw / bw, ch / bh)
                            val drawW = bw * scale
                            val drawH = bh * scale
                            val offsetX = (cw - drawW) / 2f
                            val offsetY = (ch - drawH) / 2f

                            // Store for draw coordinate mapping
                            imgDisplayOffsetX = offsetX
                            imgDisplayOffsetY = offsetY
                            imgDisplayW = drawW
                            imgDisplayH = drawH

                            drawIntoCanvas { canvas ->
                                canvas.save()
                                canvas.translate(cw / 2f, ch / 2f)
                                canvas.rotate(rotation)
                                canvas.scale(if (flipH) -1f else 1f, if (flipV) -1f else 1f)
                                canvas.translate(-cw / 2f, -ch / 2f)

                                val paint = Paint().apply {
                                    colorFilter = ColorFilter.colorMatrix(combinedMatrix)
                                }
                                canvas.drawImageRect(
                                    bmp,
                                    srcOffset = androidx.compose.ui.unit.IntOffset.Zero,
                                    srcSize = androidx.compose.ui.unit.IntSize(bmp.width, bmp.height),
                                    dstOffset = androidx.compose.ui.unit.IntOffset(offsetX.toInt(), offsetY.toInt()),
                                    dstSize = androidx.compose.ui.unit.IntSize(drawW.toInt(), drawH.toInt()),
                                    paint = paint
                                )
                                canvas.restore()
                            }

                            // Draw paths
                            fun drawPathLines(path: DrawPath) {
                                if (path.points.size < 2) return
                                for (i in 0 until path.points.size - 1) {
                                    drawLine(
                                        color = path.color,
                                        start = path.points[i],
                                        end = path.points[i + 1],
                                        strokeWidth = path.width,
                                        cap = StrokeCap.Round
                                    )
                                }
                            }
                            drawPaths.forEach { drawPathLines(it) }
                            // Current stroke being drawn
                            if (currentDrawPoints.size > 1) {
                                for (i in 0 until currentDrawPoints.size - 1) {
                                    drawLine(
                                        color = drawColor,
                                        start = currentDrawPoints[i],
                                        end = currentDrawPoints[i + 1],
                                        strokeWidth = drawWidth,
                                        cap = StrokeCap.Round
                                    )
                                }
                            }

                            // Clone source indicator
                            if (selectedTab == EditorTab.RETOUCH && retouchTool == RetouchTool.CLONE && cloneSource != null) {
                                val accentColor = Color(0xFF00E5FF)
                                drawCircle(accentColor.copy(0.7f), radius = retouchRadius, center = cloneSource!!, style = androidx.compose.ui.graphics.drawscope.Stroke(2f))
                                val cs = cloneSource!!
                                drawLine(accentColor, Offset(cs.x - 12f, cs.y), Offset(cs.x + 12f, cs.y), strokeWidth = 1.5f)
                                drawLine(accentColor, Offset(cs.x, cs.y - 12f), Offset(cs.x, cs.y + 12f), strokeWidth = 1.5f)
                            }

                            // Crop overlay
                            if (selectedTab == EditorTab.CROP) {
                                val cl = offsetX + cropLeft * drawW
                                val ct = offsetY + cropTop * drawH
                                val cr = offsetX + cropRight * drawW
                                val cb = offsetY + cropBottom * drawH
                                // Dim outside
                                drawRect(Color.Black.copy(0.5f), topLeft = Offset(offsetX, offsetY), size = androidx.compose.ui.geometry.Size(drawW, ct - offsetY))
                                drawRect(Color.Black.copy(0.5f), topLeft = Offset(offsetX, cb), size = androidx.compose.ui.geometry.Size(drawW, offsetY + drawH - cb))
                                drawRect(Color.Black.copy(0.5f), topLeft = Offset(offsetX, ct), size = androidx.compose.ui.geometry.Size(cl - offsetX, cb - ct))
                                drawRect(Color.Black.copy(0.5f), topLeft = Offset(cr, ct), size = androidx.compose.ui.geometry.Size(offsetX + drawW - cr, cb - ct))
                                // Border
                                drawRect(Color.White, topLeft = Offset(cl, ct), size = androidx.compose.ui.geometry.Size(cr - cl, cb - ct), style = androidx.compose.ui.graphics.drawscope.Stroke(2f))
                                // Grid lines (rule of thirds)
                                val thirdW = (cr - cl) / 3f
                                val thirdH = (cb - ct) / 3f
                                for (i in 1..2) {
                                    drawLine(Color.White.copy(0.4f), Offset(cl + thirdW * i, ct), Offset(cl + thirdW * i, cb), strokeWidth = 1f)
                                    drawLine(Color.White.copy(0.4f), Offset(cl, ct + thirdH * i), Offset(cr, ct + thirdH * i), strokeWidth = 1f)
                                }
                                // Corner handles
                                val hs = 16f
                                val hw = 3f
                                listOf(Offset(cl, ct), Offset(cr, ct), Offset(cl, cb), Offset(cr, cb)).forEach { corner ->
                                    val sx = if (corner.x == cl) 1f else -1f
                                    val sy = if (corner.y == ct) 1f else -1f
                                    drawLine(Color.White, corner, Offset(corner.x + hs * sx, corner.y), strokeWidth = hw)
                                    drawLine(Color.White, corner, Offset(corner.x, corner.y + hs * sy), strokeWidth = hw)
                                }
                            }
                        }
                    } else {
                        CircularProgressIndicator(color = Color.White)
                    }
                }

                // Tab bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF1A1A1A))
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    EditorTab.entries.forEach { tab ->
                        val selected = selectedTab == tab
                        val color by animateColorAsState(if (selected) MaterialTheme.colorScheme.primary else Color.White.copy(0.5f))
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .clickable { selectedTab = tab }
                                .padding(horizontal = 16.dp, vertical = 8.dp)
                        ) {
                            Icon(
                                when (tab) {
                                    EditorTab.TRANSFORM -> Icons.Default.Transform
                                    EditorTab.ADJUST -> Icons.Default.Tune
                                    EditorTab.FILTER -> Icons.Default.FilterVintage
                                    EditorTab.CROP -> Icons.Default.Crop
                                    EditorTab.DRAW -> Icons.Default.Brush
                                    EditorTab.RETOUCH -> Icons.Default.AutoFixHigh
                                },
                                null, tint = color, modifier = Modifier.size(24.dp)
                            )
                            Text(tab.name.lowercase().replaceFirstChar { it.uppercase() },
                                fontSize = 11.sp, color = color, modifier = Modifier.padding(top = 2.dp))
                        }
                    }
                }

                // Controls area
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = Color(0xFF1A1A1A)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .padding(horizontal = 16.dp, vertical = 12.dp)
                    ) {
                        when (selectedTab) {
                            EditorTab.TRANSFORM -> {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceEvenly
                                ) {
                                    TransformButton(Icons.Default.RotateLeft, "90° Left") { rotation = (rotation - 90f) % 360f }
                                    TransformButton(Icons.Default.RotateRight, "90° Right") { rotation = (rotation + 90f) % 360f }
                                    TransformButton(Icons.Default.Flip, "Flip H") { flipH = !flipH }
                                    TransformButton(Icons.Default.SwapVert, "Flip V") { flipV = !flipV }
                                    TransformButton(Icons.Default.Refresh, "Reset") {
                                        rotation = 0f; flipH = false; flipV = false
                                    }
                                }
                            }
                            EditorTab.ADJUST -> {
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    AdjustSlider("Brightness", brightness, -100f, 100f) { brightness = it }
                                    AdjustSlider("Contrast", contrast, 0.5f, 2f) { contrast = it }
                                    AdjustSlider("Saturation", saturation, 0f, 2f) { saturation = it }
                                    TextButton(
                                        onClick = { brightness = 0f; contrast = 1f; saturation = 1f },
                                        modifier = Modifier.align(Alignment.CenterHorizontally)
                                    ) { Text("Reset", color = Color.White.copy(0.6f), fontSize = 12.sp) }
                                }
                            }
                            EditorTab.FILTER -> {
                                LazyRow(
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    contentPadding = PaddingValues(horizontal = 4.dp)
                                ) {
                                    items(filterPresets.size) { idx ->
                                        val preset = filterPresets[idx]
                                        val isSelected = selectedFilter == idx
                                        Column(
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            modifier = Modifier.clickable { selectedFilter = idx }
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(64.dp)
                                                    .clip(RoundedCornerShape(12.dp))
                                                    .border(
                                                        2.dp,
                                                        if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                                                        RoundedCornerShape(12.dp)
                                                    )
                                            ) {
                                                if (originalBitmap != null) {
                                                    val filterBmp = originalBitmap!!.asImageBitmap()
                                                    Canvas(modifier = Modifier.fillMaxSize()) {
                                                        drawIntoCanvas { canvas ->
                                                            val paint = Paint().apply {
                                                                colorFilter = ColorFilter.colorMatrix(preset.matrix)
                                                            }
                                                            canvas.drawImageRect(
                                                                filterBmp,
                                                                dstSize = androidx.compose.ui.unit.IntSize(size.width.toInt(), size.height.toInt()),
                                                                paint = paint
                                                            )
                                                        }
                                                    }
                                                }
                                            }
                                            Text(
                                                preset.name, fontSize = 10.sp,
                                                color = if (isSelected) MaterialTheme.colorScheme.primary else Color.White.copy(0.6f),
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                                modifier = Modifier.padding(top = 4.dp)
                                            )
                                        }
                                    }
                                }
                            }
                            EditorTab.DRAW -> {
                                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    // Color picker
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceEvenly,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        drawColors.forEach { color ->
                                            val isSelected = drawColor == color
                                            Box(
                                                modifier = Modifier
                                                    .size(if (isSelected) 36.dp else 28.dp)
                                                    .clip(CircleShape)
                                                    .background(color)
                                                    .then(
                                                        if (isSelected) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, CircleShape)
                                                        else Modifier.border(1.dp, Color.White.copy(0.3f), CircleShape)
                                                    )
                                                    .clickable { drawColor = color }
                                            )
                                        }
                                    }
                                    // Brush size
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.Circle, null, tint = Color.White.copy(0.5f), modifier = Modifier.size(12.dp))
                                        Slider(
                                            value = drawWidth,
                                            onValueChange = { drawWidth = it },
                                            valueRange = 2f..30f,
                                            modifier = Modifier.weight(1f),
                                            colors = SliderDefaults.colors(
                                                thumbColor = MaterialTheme.colorScheme.primary,
                                                activeTrackColor = MaterialTheme.colorScheme.primary,
                                                inactiveTrackColor = Color.White.copy(0.15f)
                                            )
                                        )
                                        Icon(Icons.Default.Circle, null, tint = Color.White.copy(0.5f), modifier = Modifier.size(24.dp))
                                    }
                                    // Undo + Clear
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.Center,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        TextButton(
                                            onClick = { if (drawPaths.isNotEmpty()) drawPaths.removeAt(drawPaths.lastIndex) },
                                            enabled = drawPaths.isNotEmpty()
                                        ) {
                                            Icon(Icons.Default.Undo, null, tint = if (drawPaths.isNotEmpty()) Color.White else Color.White.copy(0.3f), modifier = Modifier.size(20.dp))
                                            Spacer(Modifier.width(4.dp))
                                            Text("Undo", color = if (drawPaths.isNotEmpty()) Color.White else Color.White.copy(0.3f), fontSize = 12.sp)
                                        }
                                        Spacer(Modifier.width(24.dp))
                                        TextButton(
                                            onClick = { drawPaths.clear() },
                                            enabled = drawPaths.isNotEmpty()
                                        ) {
                                            Icon(Icons.Default.Delete, null, tint = if (drawPaths.isNotEmpty()) Color(0xFFFF5252) else Color.White.copy(0.3f), modifier = Modifier.size(20.dp))
                                            Spacer(Modifier.width(4.dp))
                                            Text("Clear All", color = if (drawPaths.isNotEmpty()) Color(0xFFFF5252) else Color.White.copy(0.3f), fontSize = 12.sp)
                                        }
                                    }
                                }
                            }
                            EditorTab.RETOUCH -> {
                                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    // Tool selector
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceEvenly
                                    ) {
                                        RetouchTool.entries.forEach { tool ->
                                            val isSelected = retouchTool == tool
                                            val tColor by animateColorAsState(if (isSelected) MaterialTheme.colorScheme.primary else Color.White.copy(0.5f))
                                            Surface(
                                                onClick = {
                                                    retouchTool = tool
                                                    if (tool == RetouchTool.CLONE) isSettingCloneSource = true
                                                },
                                                shape = RoundedCornerShape(12.dp),
                                                color = if (isSelected) MaterialTheme.colorScheme.primary.copy(0.15f) else Color.Transparent,
                                                modifier = Modifier.weight(1f).padding(horizontal = 4.dp)
                                            ) {
                                                Column(
                                                    horizontalAlignment = Alignment.CenterHorizontally,
                                                    modifier = Modifier.padding(vertical = 8.dp)
                                                ) {
                                                    Icon(
                                                        when (tool) {
                                                            RetouchTool.CLONE -> Icons.Default.ContentCopy
                                                            RetouchTool.BLUR -> Icons.Default.BlurOn
                                                            RetouchTool.PIXELATE -> Icons.Default.GridOn
                                                        },
                                                        null, tint = tColor, modifier = Modifier.size(22.dp)
                                                    )
                                                    Text(
                                                        when (tool) {
                                                            RetouchTool.CLONE -> "Clone"
                                                            RetouchTool.BLUR -> "Blur"
                                                            RetouchTool.PIXELATE -> "Pixelate"
                                                        },
                                                        fontSize = 11.sp, color = tColor, modifier = Modifier.padding(top = 2.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                    // Clone source hint
                                    if (retouchTool == RetouchTool.CLONE && isSettingCloneSource) {
                                        Text("Tap on the area to clone FROM", color = MaterialTheme.colorScheme.primary, fontSize = 12.sp,
                                            modifier = Modifier.fillMaxWidth(), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                                    }
                                    // Brush size
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text("Size", color = Color.White.copy(0.6f), fontSize = 12.sp, modifier = Modifier.width(40.dp))
                                        Slider(
                                            value = retouchRadius,
                                            onValueChange = { retouchRadius = it },
                                            valueRange = 8f..60f,
                                            modifier = Modifier.weight(1f),
                                            colors = SliderDefaults.colors(
                                                thumbColor = MaterialTheme.colorScheme.primary,
                                                activeTrackColor = MaterialTheme.colorScheme.primary,
                                                inactiveTrackColor = Color.White.copy(0.15f)
                                            )
                                        )
                                        Text("${retouchRadius.toInt()}", color = Color.White.copy(0.5f), fontSize = 11.sp, modifier = Modifier.width(30.dp))
                                    }
                                    // Undo + Reset source
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                                        TextButton(
                                            onClick = {
                                                if (retouchHistory.isNotEmpty()) {
                                                    retouchBitmap = retouchHistory.removeAt(retouchHistory.lastIndex)
                                                }
                                            },
                                            enabled = retouchHistory.isNotEmpty()
                                        ) {
                                            Icon(Icons.Default.Undo, null, tint = if (retouchHistory.isNotEmpty()) Color.White else Color.White.copy(0.3f), modifier = Modifier.size(18.dp))
                                            Spacer(Modifier.width(4.dp))
                                            Text("Undo", color = if (retouchHistory.isNotEmpty()) Color.White else Color.White.copy(0.3f), fontSize = 12.sp)
                                        }
                                        if (retouchTool == RetouchTool.CLONE) {
                                            Spacer(Modifier.width(16.dp))
                                            TextButton(onClick = { isSettingCloneSource = true; cloneSource = null }) {
                                                Icon(Icons.Default.MyLocation, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                                                Spacer(Modifier.width(4.dp))
                                                Text("New Source", color = MaterialTheme.colorScheme.primary, fontSize = 12.sp)
                                            }
                                        }
                                        Spacer(Modifier.width(16.dp))
                                        TextButton(onClick = {
                                            retouchBitmap = originalBitmap?.copy(Bitmap.Config.ARGB_8888, true)
                                            retouchHistory.clear()
                                        }) {
                                            Icon(Icons.Default.Refresh, null, tint = Color.White.copy(0.6f), modifier = Modifier.size(18.dp))
                                            Spacer(Modifier.width(4.dp))
                                            Text("Reset", color = Color.White.copy(0.6f), fontSize = 12.sp)
                                        }
                                    }
                                }
                            }
                            EditorTab.CROP -> {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceEvenly
                                ) {
                                    TransformButton(Icons.Default.CropFree, "Free") {
                                        cropLeft = 0.05f; cropTop = 0.05f; cropRight = 0.95f; cropBottom = 0.95f; isCropActive = true
                                    }
                                    TransformButton(Icons.Default.CropSquare, "1:1") {
                                        val cx = 0.5f; val cy = 0.5f; val s = 0.4f
                                        cropLeft = cx - s; cropTop = cy - s; cropRight = cx + s; cropBottom = cy + s; isCropActive = true
                                    }
                                    TransformButton(Icons.Default.Crop169, "16:9") {
                                        cropLeft = 0.05f; cropTop = 0.2f; cropRight = 0.95f; cropBottom = 0.8f; isCropActive = true
                                    }
                                    TransformButton(Icons.Default.CropPortrait, "4:3") {
                                        cropLeft = 0.1f; cropTop = 0.05f; cropRight = 0.9f; cropBottom = 0.95f; isCropActive = true
                                    }
                                    TransformButton(Icons.Default.Refresh, "Reset") {
                                        cropLeft = 0.05f; cropTop = 0.05f; cropRight = 0.95f; cropBottom = 0.95f; isCropActive = false
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TransformButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(8.dp)
    ) {
        Surface(
            shape = CircleShape,
            color = Color.White.copy(0.1f),
            modifier = Modifier.size(48.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(icon, label, tint = Color.White, modifier = Modifier.size(24.dp))
            }
        }
        Text(label, fontSize = 10.sp, color = Color.White.copy(0.7f), modifier = Modifier.padding(top = 4.dp))
    }
}

@Composable
private fun AdjustSlider(label: String, value: Float, min: Float, max: Float, onChange: (Float) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(label, color = Color.White.copy(0.7f), fontSize = 12.sp, modifier = Modifier.width(80.dp))
        Slider(
            value = value,
            onValueChange = onChange,
            valueRange = min..max,
            modifier = Modifier.weight(1f),
            colors = SliderDefaults.colors(
                thumbColor = MaterialTheme.colorScheme.primary,
                activeTrackColor = MaterialTheme.colorScheme.primary,
                inactiveTrackColor = Color.White.copy(0.15f)
            )
        )
        Text(
            when {
                max <= 2f && min >= 0f -> String.format("%.1f", value)
                else -> value.toInt().toString()
            },
            color = Color.White.copy(0.5f), fontSize = 11.sp, modifier = Modifier.width(36.dp)
        )
    }
}

private fun loadBitmap(context: Context, uri: Uri): Bitmap? {
    return try {
        val options = BitmapFactory.Options().apply { inSampleSize = 1 }
        when (uri.scheme) {
            "file" -> BitmapFactory.decodeFile(uri.path, options)
            else -> context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
        }
    } catch (e: Exception) {
        android.util.Log.e("MediaEditor", "Load failed", e)
        null
    }
}

private fun applyEdits(
    context: Context,
    source: Bitmap,
    originalUri: Uri,
    rotation: Float,
    flipH: Boolean,
    flipV: Boolean,
    brightness: Float,
    contrast: Float,
    saturation: Float,
    filterMatrix: ColorMatrix?,
    cropFractions: FloatArray?,
    drawings: List<DrawPath> = emptyList(),
    dispOffX: Float = 0f, dispOffY: Float = 0f, dispW: Float = 1f, dispH: Float = 1f
): Uri? {
    return try {
        var bmp = source.copy(Bitmap.Config.ARGB_8888, true)

        // Apply rotation and flip
        if (rotation != 0f || flipH || flipV) {
            val matrix = Matrix()
            matrix.postRotate(rotation)
            if (flipH) matrix.postScale(-1f, 1f, bmp.width / 2f, bmp.height / 2f)
            if (flipV) matrix.postScale(1f, -1f, bmp.width / 2f, bmp.height / 2f)
            bmp = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, matrix, true)
        }

        // Apply color adjustments + filter
        val cm = android.graphics.ColorMatrix()
        // Contrast
        val c = contrast
        val t = (1f - c) * 128f
        cm.postConcat(android.graphics.ColorMatrix(floatArrayOf(
            c, 0f, 0f, 0f, t,
            0f, c, 0f, 0f, t,
            0f, 0f, c, 0f, 0f,
            0f, 0f, 0f, 1f, 0f
        )))
        // Brightness
        cm.postConcat(android.graphics.ColorMatrix(floatArrayOf(
            1f, 0f, 0f, 0f, brightness,
            0f, 1f, 0f, 0f, brightness,
            0f, 0f, 1f, 0f, brightness,
            0f, 0f, 0f, 1f, 0f
        )))
        // Saturation
        val satMatrix = android.graphics.ColorMatrix()
        satMatrix.setSaturation(saturation)
        cm.postConcat(satMatrix)
        // Filter
        if (filterMatrix != null) {
            cm.postConcat(android.graphics.ColorMatrix(filterMatrix.values))
        }

        val canvas = android.graphics.Canvas(bmp)
        val paint = android.graphics.Paint()
        paint.colorFilter = ColorMatrixColorFilter(cm)
        canvas.drawBitmap(bmp.copy(Bitmap.Config.ARGB_8888, false), 0f, 0f, paint)

        // Draw annotations - map from canvas coordinates to bitmap coordinates
        if (drawings.isNotEmpty() && dispW > 0 && dispH > 0) {
            val drawCanvas = android.graphics.Canvas(bmp)
            val drawPaint = android.graphics.Paint().apply {
                style = android.graphics.Paint.Style.STROKE
                strokeCap = android.graphics.Paint.Cap.ROUND
                strokeJoin = android.graphics.Paint.Join.ROUND
                isAntiAlias = true
            }
            val scaleX = bmp.width.toFloat() / dispW
            val scaleY = bmp.height.toFloat() / dispH
            drawings.forEach { path ->
                if (path.points.size < 2) return@forEach
                drawPaint.color = android.graphics.Color.argb(
                    (path.color.alpha * 255).toInt(),
                    (path.color.red * 255).toInt(),
                    (path.color.green * 255).toInt(),
                    (path.color.blue * 255).toInt()
                )
                drawPaint.strokeWidth = path.width * scaleX
                val androidPath = android.graphics.Path()
                androidPath.moveTo(
                    (path.points[0].x - dispOffX) * scaleX,
                    (path.points[0].y - dispOffY) * scaleY
                )
                for (i in 1 until path.points.size) {
                    androidPath.lineTo(
                        (path.points[i].x - dispOffX) * scaleX,
                        (path.points[i].y - dispOffY) * scaleY
                    )
                }
                drawCanvas.drawPath(androidPath, drawPaint)
            }
        }

        // Crop
        if (cropFractions != null) {
            val cl = (cropFractions[0] * bmp.width).toInt().coerceIn(0, bmp.width)
            val ct = (cropFractions[1] * bmp.height).toInt().coerceIn(0, bmp.height)
            val cr = (cropFractions[2] * bmp.width).toInt().coerceIn(cl + 1, bmp.width)
            val cb = (cropFractions[3] * bmp.height).toInt().coerceIn(ct + 1, bmp.height)
            bmp = Bitmap.createBitmap(bmp, cl, ct, cr - cl, cb - ct)
        }

        // Save
        val origName = originalUri.lastPathSegment?.substringAfterLast('/') ?: "edited"
        val baseName = origName.substringBeforeLast('.')
        val ext = origName.substringAfterLast('.', "jpg")
        val dir = File(android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_PICTURES), "Edited")
        dir.mkdirs()
        var destFile = File(dir, "${baseName}_edited.$ext")
        var counter = 1
        while (destFile.exists()) {
            destFile = File(dir, "${baseName}_edited_$counter.$ext")
            counter++
        }

        val format = if (ext.lowercase() == "png") Bitmap.CompressFormat.PNG else Bitmap.CompressFormat.JPEG
        FileOutputStream(destFile).use { out ->
            bmp.compress(format, 95, out)
        }

        MediaScannerConnection.scanFile(context, arrayOf(destFile.absolutePath), null, null)
        Uri.fromFile(destFile)
    } catch (e: Exception) {
        android.util.Log.e("MediaEditor", "Save failed", e)
        null
    }
}

/** Apply retouch tool at a canvas position, mapping to bitmap coordinates */
private fun applyRetouchAt(
    bmp: Bitmap,
    canvasPos: Offset,
    tool: RetouchTool,
    radius: Float,
    cloneOffset: Offset,
    dispOffX: Float, dispOffY: Float, dispW: Float, dispH: Float
) {
    if (dispW <= 0 || dispH <= 0) return
    val scaleX = bmp.width.toFloat() / dispW
    val scaleY = bmp.height.toFloat() / dispH
    val bx = ((canvasPos.x - dispOffX) * scaleX).toInt()
    val by = ((canvasPos.y - dispOffY) * scaleY).toInt()
    val br = (radius * scaleX).toInt().coerceAtLeast(2)

    when (tool) {
        RetouchTool.CLONE -> {
            // Copy pixels from source (offset) to destination
            val srcBx = bx - (cloneOffset.x * scaleX).toInt()
            val srcBy = by - (cloneOffset.y * scaleY).toInt()
            for (dy in -br..br) {
                for (dx in -br..br) {
                    if (dx * dx + dy * dy > br * br) continue
                    val destX = bx + dx
                    val destY = by + dy
                    val srcX = srcBx + dx
                    val srcY = srcBy + dy
                    if (destX in 0 until bmp.width && destY in 0 until bmp.height &&
                        srcX in 0 until bmp.width && srcY in 0 until bmp.height) {
                        bmp.setPixel(destX, destY, bmp.getPixel(srcX, srcY))
                    }
                }
            }
        }
        RetouchTool.BLUR -> {
            // Box blur in the brush area
            val kernelSize = (br / 3).coerceAtLeast(2)
            val left = (bx - br).coerceAtLeast(0)
            val top = (by - br).coerceAtLeast(0)
            val right = (bx + br).coerceAtMost(bmp.width - 1)
            val bottom = (by + br).coerceAtMost(bmp.height - 1)
            val w = right - left + 1
            val h = bottom - top + 1
            if (w <= 0 || h <= 0) return
            val pixels = IntArray(w * h)
            bmp.getPixels(pixels, 0, w, left, top, w, h)
            val blurred = IntArray(w * h)

            for (py in 0 until h) {
                for (px in 0 until w) {
                    // Check if within circle
                    val rx = left + px - bx
                    val ry = top + py - by
                    if (rx * rx + ry * ry > br * br) {
                        blurred[py * w + px] = pixels[py * w + px]
                        continue
                    }
                    var r = 0; var g = 0; var b = 0; var a = 0; var count = 0
                    for (ky in -kernelSize..kernelSize) {
                        for (kx in -kernelSize..kernelSize) {
                            val sx = px + kx; val sy = py + ky
                            if (sx in 0 until w && sy in 0 until h) {
                                val c = pixels[sy * w + sx]
                                a += (c shr 24) and 0xFF
                                r += (c shr 16) and 0xFF
                                g += (c shr 8) and 0xFF
                                b += c and 0xFF
                                count++
                            }
                        }
                    }
                    if (count > 0) {
                        blurred[py * w + px] = ((a / count) shl 24) or ((r / count) shl 16) or ((g / count) shl 8) or (b / count)
                    }
                }
            }
            bmp.setPixels(blurred, 0, w, left, top, w, h)
        }
        RetouchTool.PIXELATE -> {
            // Pixelate in the brush area
            val blockSize = (br / 2).coerceAtLeast(4)
            val left = (bx - br).coerceAtLeast(0)
            val top = (by - br).coerceAtLeast(0)
            val right = (bx + br).coerceAtMost(bmp.width - 1)
            val bottom = (by + br).coerceAtMost(bmp.height - 1)

            var blockY = top
            while (blockY <= bottom) {
                var blockX = left
                while (blockX <= right) {
                    // Check center of block is within circle
                    val cx = blockX + blockSize / 2 - bx
                    val cy = blockY + blockSize / 2 - by
                    if (cx * cx + cy * cy <= br * br) {
                        // Average the block
                        var r = 0; var g = 0; var b = 0; var count = 0
                        for (py in blockY until min(blockY + blockSize, bottom + 1)) {
                            for (px in blockX until min(blockX + blockSize, right + 1)) {
                                val c = bmp.getPixel(px, py)
                                r += (c shr 16) and 0xFF
                                g += (c shr 8) and 0xFF
                                b += c and 0xFF
                                count++
                            }
                        }
                        if (count > 0) {
                            val avg = (0xFF shl 24) or ((r / count) shl 16) or ((g / count) shl 8) or (b / count)
                            for (py in blockY until min(blockY + blockSize, bottom + 1)) {
                                for (px in blockX until min(blockX + blockSize, right + 1)) {
                                    bmp.setPixel(px, py, avg)
                                }
                            }
                        }
                    }
                    blockX += blockSize
                }
                blockY += blockSize
            }
        }
    }
}

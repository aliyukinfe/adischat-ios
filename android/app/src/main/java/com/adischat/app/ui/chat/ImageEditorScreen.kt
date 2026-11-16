package com.adischat.app.ui.chat

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.Image
import androidx.compose.ui.graphics.ImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

private enum class EditTool { None, Crop, Text, Brush, Sticker }

private data class DrawStroke(val points: List<Offset>, val color: Color, val width: Float, val eraser: Boolean)

private class EditOverlay(
    val id: Long,
    val kind: String,      // "text" | "sticker"
    val content: String,
    val color: Color = Color.White,
    val bold: Boolean = false,
    val pill: Boolean = false,
) {
    var offset by mutableStateOf(Offset.Zero)
    var scale by mutableStateOf(1f)
    var rotation by mutableStateOf(0f)
}

private val EditorPalette = listOf(
    Color.White, Color.Black, Color(0xFFEF4444), Color(0xFFF59E0B), Color(0xFF22C55E),
    Color(0xFF3B82F6), Color(0xFFA855F7), Color(0xFFEC4899),
)
private val StickerEmojis = listOf(
    "-", "-", "-", "-", "-", "-", "-", "-", "-", "-", "-", "-",
    "-", "-", "-", "-", "-", "-", "-", "-", "-", "-",
)

/**
 * Full-screen image editor: crop (rotate/flip/aspect), brush, text, stickers.
 * Flattens image + edits to a clean generated JPEG via GraphicsLayer capture.
 */
@Composable
fun ImageEditorScreen(uri: Uri, onCancel: () -> Unit, onDone: (Uri) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val graphicsLayer = rememberGraphicsLayer()

    var base by remember { mutableStateOf<ImageBitmap?>(null) }
    var imgAspect by remember { mutableFloatStateOf(1f) }
    LaunchedEffect(uri) {
        val bmp = withContext(Dispatchers.IO) {
        runCatching {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        val maxDim = 2048
        val sample = maxOf(1, maxOf(bounds.outWidth, bounds.outHeight) / maxDim)
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
        }.getOrNull()
        }
        if (bmp != null) { imgAspect = bmp.width.toFloat() / bmp.height.toFloat(); base = bmp.asImageBitmap() }
    }

    var tool by remember { mutableStateOf(EditTool.None) }
    var rotation by remember { mutableFloatStateOf(0f) }
    var flipH by remember { mutableStateOf(false) }
    var aspect by remember { mutableStateOf<Float?>(null) }   // null = free

    val strokes = remember { mutableStateListOf<DrawStroke>() }
    val redo = remember { mutableStateListOf<DrawStroke>() }
    var current by remember { mutableStateOf<List<Offset>>(emptyList()) }
    var brushColor by remember { mutableStateOf(Color.White) }
    var brushSize by remember { mutableFloatStateOf(14f) }
    var eraser by remember { mutableStateOf(false) }

    val overlays = remember { mutableStateListOf<EditOverlay>() }
    var selectedId by remember { mutableStateOf<Long?>(null) }
    var textColor by remember { mutableStateOf(Color.White) }
    var textBold by remember { mutableStateOf(true) }
    var textPill by remember { mutableStateOf(false) }
    var showTextInput by remember { mutableStateOf(false) }

    var rendering by remember { mutableStateOf(false) }

    // View-only pinch-zoom / pan (does NOT affect the captured output).
    var viewScale by remember { mutableFloatStateOf(1f) }
    var viewOffset by remember { mutableStateOf(Offset.Zero) }

    fun reset() {
        rotation = 0f; flipH = false; aspect = null; strokes.clear(); redo.clear(); overlays.clear(); selectedId = null
        viewScale = 1f; viewOffset = Offset.Zero
    }

    Box(Modifier.fillMaxSize().background(Color.Black).statusBarsPadding().navigationBarsPadding()) {
        Column(Modifier.fillMaxSize()) {
        // - Top action bar (X / Reset / Done) -
        Row(
        Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        ) {
        EditorIconButton(Icons.Default.Close, "Cancel", onCancel)
        Spacer(Modifier.weight(1f))
        EditorIconButton(Icons.Default.Refresh, "Reset", ::reset)
        Spacer(Modifier.width(6.dp))
        Box(
        Modifier.clip(RoundedCornerShape(20.dp)).background(Color(0xFF22C55E))
        .clickable(enabled = base != null && !rendering) {
        rendering = true
        scope.launch {
        val out = runCatching {
        val img = graphicsLayer.toImageBitmap().asAndroidBitmap()
        val file = File.createTempFile("adis_edit_", ".jpg", context.cacheDir)
        withContext(Dispatchers.IO) {
        file.outputStream().use { img.compress(Bitmap.CompressFormat.JPEG, 92, it) }
        }
        Uri.fromFile(file)
        }.getOrNull()
        rendering = false
        if (out != null) onDone(out) else onCancel()
        }
        }
        .padding(horizontal = 18.dp, vertical = 9.dp),
        ) {
        if (rendering) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = Color.Black)
        else Text("Done", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 15.sp)
        }
        }

        // - Tool selector - kept at the TOP so it is always visible -
        ToolSelectorBar(tool) { tool = if (tool == it) EditTool.None else it }

        // - Canvas (recorded for flattening) - fills the remaining space -
        Box(Modifier.weight(1f).fillMaxWidth().padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
        val b = base
        if (b == null) {
        CircularProgressIndicator(color = Color(0xFF22C55E), strokeWidth = 2.dp)
        } else {
        Box(
        Modifier.fillMaxWidth().aspectRatio(aspect ?: imgAspect)
        // Outer view transform (zoom/pan) - applied on screen only;
        // the recorded layer below captures the untransformed content.
        .graphicsLayer {
        scaleX = viewScale; scaleY = viewScale
        translationX = viewOffset.x; translationY = viewOffset.y
        }
        .then(
        if (tool == EditTool.None) Modifier.pointerInput(Unit) {
        detectTransformGestures { _, pan, zoom, _ ->
        viewScale = (viewScale * zoom).coerceIn(1f, 4f)
        viewOffset = if (viewScale <= 1.01f) Offset.Zero else viewOffset + pan
        }
        } else Modifier,
        )
        .clip(RoundedCornerShape(6.dp))
        .drawWithContent {
        graphicsLayer.record { this@drawWithContent.drawContent() }
        drawLayer(graphicsLayer)
        },
        ) {
        Image(
        bitmap = b, contentDescription = null,
        modifier = Modifier.fillMaxSize().graphicsLayer {
        rotationZ = rotation
        scaleX = if (flipH) -1f else 1f
        },
        contentScale = if (aspect != null) ContentScale.Crop else ContentScale.Fit,
        )

        // Drawing layer (offscreen so the eraser clears only strokes).
        Canvas(
        Modifier.fillMaxSize().graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
        .then(if (tool == EditTool.Brush) Modifier.pointerInputBrush(
        onStart = { current = listOf(it) },
        onMove = { current = current + it },
        onEnd = {
        if (current.size > 1) { strokes.add(DrawStroke(current, brushColor, brushSize, eraser)); redo.clear() }
        current = emptyList()
        },
        ) else Modifier),
        ) {
        (strokes + if (current.isNotEmpty()) listOf(DrawStroke(current, brushColor, brushSize, eraser)) else emptyList())
        .forEach { s ->
        val path = Path().apply {
        s.points.firstOrNull()?.let { moveTo(it.x, it.y) }
        s.points.drop(1).forEach { lineTo(it.x, it.y) }
        }
        drawPath(
        path = path,
        color = if (s.eraser) Color.Transparent else s.color,
        style = Stroke(width = s.width, cap = StrokeCap.Round, join = StrokeJoin.Round),
        blendMode = if (s.eraser) BlendMode.Clear else BlendMode.SrcOver,
        )
        }
        }

        // Text + sticker overlays
        overlays.forEach { ov -> OverlayItem(ov, selectedId == ov.id, onSelect = { selectedId = ov.id }) }
        }

        // Delete selected overlay (floats over the canvas, top-center)
        if (selectedId != null) {
        EditorIconButton(
        Icons.Default.Delete, "Delete",
        onClick = { overlays.removeAll { it.id == selectedId }; selectedId = null },
        bg = Color(0xFFEF4444),
        modifier = Modifier.align(Alignment.TopCenter),
        )
        }
        }
        }

        // - Contextual panel for the active tool (bottom) -
        when (tool) {
        EditTool.Crop -> CropPanel(
        onRotateL = { rotation -= 90f }, onRotateR = { rotation += 90f },
        onFlip = { flipH = !flipH }, aspect = aspect, onAspect = { aspect = it },
        )
        EditTool.Brush -> BrushPanel(
        color = brushColor, onColor = { brushColor = it; eraser = false },
        size = brushSize, onSize = { brushSize = it },
        eraser = eraser, onEraser = { eraser = !eraser },
        canUndo = strokes.isNotEmpty(), onUndo = { if (strokes.isNotEmpty()) redo.add(strokes.removeAt(strokes.lastIndex)) },
        canRedo = redo.isNotEmpty(), onRedo = { if (redo.isNotEmpty()) strokes.add(redo.removeAt(redo.lastIndex)) },
        onClear = { strokes.clear(); redo.clear() },
        )
        EditTool.Text -> TextPanel(
        color = textColor, onColor = { textColor = it },
        bold = textBold, onBold = { textBold = !textBold },
        pill = textPill, onPill = { textPill = !textPill },
        onAdd = { showTextInput = true },
        )
        EditTool.Sticker -> StickerPanel(onPick = { e ->
        overlays.add(EditOverlay(System.nanoTime(), "sticker", e)); tool = EditTool.None
        })
        EditTool.None -> {}
        }
        }
    }

    if (showTextInput) {
        TextInputDialog(
        color = textColor, bold = textBold,
        onDismiss = { showTextInput = false },
        onConfirm = { t ->
        showTextInput = false
        if (t.isNotBlank()) overlays.add(EditOverlay(System.nanoTime(), "text", t, textColor, textBold, textPill))
        },
        )
    }
}

private fun Modifier.pointerInputBrush(
    onStart: (Offset) -> Unit, onMove: (Offset) -> Unit, onEnd: () -> Unit,
): Modifier = this.then(
    pointerInput(Unit) {
        detectDragGestures(
        onDragStart = { onStart(it) },
        onDrag = { change, _ -> onMove(change.position) },
        onDragEnd = { onEnd() },
        onDragCancel = { onEnd() },
        )
    },
)

@Composable
private fun OverlayItem(ov: EditOverlay, selected: Boolean, onSelect: () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Box(
        Modifier
        .graphicsLayer {
        translationX = ov.offset.x; translationY = ov.offset.y
        scaleX = ov.scale; scaleY = ov.scale; rotationZ = ov.rotation
        }
        .pointerInputTransform(ov, onSelect)
        .then(if (selected) Modifier.border(1.dp, Color.White, RoundedCornerShape(8.dp)) else Modifier)
        .padding(6.dp),
        contentAlignment = Alignment.Center,
        ) {
        if (ov.kind == "sticker") {
        Text(ov.content, fontSize = 56.sp)
        } else {
        Text(
        ov.content, color = ov.color,
        fontSize = 30.sp, fontWeight = if (ov.bold) FontWeight.Bold else FontWeight.Normal,
        modifier = if (ov.pill) Modifier.clip(RoundedCornerShape(999.dp)).background(Color.Black.copy(alpha = 0.45f)).padding(horizontal = 12.dp, vertical = 6.dp) else Modifier,
        )
        }
        }
    }
}

private fun Modifier.pointerInputTransform(ov: EditOverlay, onSelect: () -> Unit): Modifier = this
    .then(
        pointerInput(ov.id) {
        detectTransformGestures { _, pan, zoom, rot ->
        onSelect()
        ov.offset += pan
        ov.scale = (ov.scale * zoom).coerceIn(0.3f, 6f)
        ov.rotation += rot
        }
        },
    )
    .then(
        pointerInput(ov.id) { detectTapGestures { onSelect() } },
    )

// - Panels -
@Composable
private fun CropPanel(onRotateL: () -> Unit, onRotateR: () -> Unit, onFlip: () -> Unit, aspect: Float?, onAspect: (Float?) -> Unit) {
    PanelSurface {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
        ToolChip(Icons.Default.RotateLeft, "Left", onRotateL)
        ToolChip(Icons.Default.RotateRight, "Right", onRotateR)
        ToolChip(Icons.Default.Flip, "Flip", onFlip)
        Spacer(Modifier.width(4.dp))
        AspectChip("Free", aspect == null) { onAspect(null) }
        AspectChip("1:1", aspect == 1f) { onAspect(1f) }
        AspectChip("4:5", aspect == 0.8f) { onAspect(0.8f) }
        AspectChip("16:9", aspect == 16f / 9f) { onAspect(16f / 9f) }
        }
    }
}

@Composable
private fun BrushPanel(
    color: Color, onColor: (Color) -> Unit, size: Float, onSize: (Float) -> Unit,
    eraser: Boolean, onEraser: () -> Unit, canUndo: Boolean, onUndo: () -> Unit,
    canRedo: Boolean, onRedo: () -> Unit, onClear: () -> Unit,
) {
    PanelSurface {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        EditorPalette.forEach { c -> ColorDot(c, color == c && !eraser) { onColor(c) } }
        Spacer(Modifier.weight(1f))
        ToolChip(if (eraser) Icons.Default.AutoFixOff else Icons.Default.AutoFixHigh, if (eraser) "Erasing" else "Eraser", onEraser, active = eraser)
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Size", color = Color.White, fontSize = 12.sp)
        listOf(6f, 14f, 26f, 40f).forEach { s ->
        Box(Modifier.size(34.dp).clip(CircleShape).background(if (size == s) Color(0x3322C55E) else Color(0x14FFFFFF)).clickable { onSize(s) }, contentAlignment = Alignment.Center) {
        Box(Modifier.size((s / 3f + 4f).dp).clip(CircleShape).background(Color.White))
        }
        }
        Spacer(Modifier.weight(1f))
        ToolChip(Icons.AutoMirrored.Filled.Undo, "Undo", onUndo, enabled = canUndo)
        ToolChip(Icons.AutoMirrored.Filled.Redo, "Redo", onRedo, enabled = canRedo)
        ToolChip(Icons.Default.DeleteSweep, "Clear", onClear)
        }
        }
    }
}

@Composable
private fun TextPanel(color: Color, onColor: (Color) -> Unit, bold: Boolean, onBold: () -> Unit, pill: Boolean, onPill: () -> Unit, onAdd: () -> Unit) {
    PanelSurface {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        EditorPalette.forEach { c -> ColorDot(c, color == c) { onColor(c) } }
        Spacer(Modifier.weight(1f))
        ToolChip(Icons.Default.FormatBold, "Bold", onBold, active = bold)
        ToolChip(Icons.Default.Label, "Pill", onPill, active = pill)
        ToolChip(Icons.Default.Add, "Add", onAdd)
        }
    }
}

@Composable
private fun StickerPanel(onPick: (String) -> Unit) {
    PanelSurface {
        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        items(StickerEmojis) { e ->
        Box(Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)).background(Color(0x14FFFFFF)).clickable { onPick(e) }, contentAlignment = Alignment.Center) {
        Text(e, fontSize = 26.sp)
        }
        }
        }
    }
}

@Composable
private fun ToolSelectorBar(active: EditTool, onSelect: (EditTool) -> Unit) {
    Row(
        Modifier.fillMaxWidth().background(Color(0xFF0A0C10)).padding(horizontal = 8.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceAround, verticalAlignment = Alignment.CenterVertically,
    ) {
        ToolbarItem(Icons.Default.Crop, "Crop", active == EditTool.Crop) { onSelect(EditTool.Crop) }
        ToolbarItem(Icons.Default.TextFields, "Text", active == EditTool.Text) { onSelect(EditTool.Text) }
        ToolbarItem(Icons.Default.Brush, "Brush", active == EditTool.Brush) { onSelect(EditTool.Brush) }
        ToolbarItem(Icons.Default.EmojiEmotions, "Sticker", active == EditTool.Sticker) { onSelect(EditTool.Sticker) }
    }
}

// - Small UI atoms -
@Composable
private fun PanelSurface(content: @Composable () -> Unit) {
    Box(Modifier.fillMaxWidth().background(Color(0xF20A0C10)).padding(horizontal = 12.dp, vertical = 10.dp)) { content() }
}

@Composable
private fun ToolbarItem(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, active: Boolean, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clip(RoundedCornerShape(14.dp)).clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 4.dp)) {
        Icon(icon, label, tint = if (active) Color(0xFF22C55E) else Color.White, modifier = Modifier.size(26.dp))
        Spacer(Modifier.height(3.dp))
        Text(label, color = if (active) Color(0xFF22C55E) else Color(0xFF9AA4B2), fontSize = 11.sp)
    }
}

@Composable
private fun ToolChip(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit, active: Boolean = false, enabled: Boolean = true) {
    Box(
        Modifier.clip(RoundedCornerShape(12.dp)).background(if (active) Color(0x3322C55E) else Color(0x14FFFFFF))
        .clickable(enabled = enabled, onClick = onClick).padding(horizontal = 10.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, label, tint = if (!enabled) Color(0x55FFFFFF) else if (active) Color(0xFF22C55E) else Color.White, modifier = Modifier.size(20.dp)) }
}

@Composable
private fun AspectChip(label: String, active: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.clip(RoundedCornerShape(10.dp)).background(if (active) Color(0xFF22C55E) else Color(0x14FFFFFF)).clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 8.dp),
    ) { Text(label, color = if (active) Color.Black else Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold) }
}

@Composable
private fun ColorDot(color: Color, active: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.size(if (active) 30.dp else 26.dp).clip(CircleShape).background(color)
        .border(2.dp, if (active) Color(0xFF22C55E) else Color.White.copy(alpha = 0.4f), CircleShape).clickable(onClick = onClick),
    )
}

@Composable
private fun EditorIconButton(icon: androidx.compose.ui.graphics.vector.ImageVector, desc: String, onClick: () -> Unit, bg: Color = Color(0x33FFFFFF), modifier: Modifier = Modifier) {
    Box(modifier.size(42.dp).clip(CircleShape).background(bg).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        Icon(icon, desc, tint = Color.White, modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun TextInputDialog(color: Color, bold: Boolean, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var value by remember { mutableStateOf("") }
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Color(0xFF12151C)).padding(18.dp)) {
        Column {
        Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Color(0x14FFFFFF)).padding(14.dp)) {
        if (value.isBlank()) Text("Type text-", color = Color(0xFF7F8A94), fontSize = 18.sp)
        BasicTextField(
        value = value, onValueChange = { value = it },
        textStyle = TextStyle(color = color, fontSize = 18.sp, fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal),
        cursorBrush = SolidColor(Color(0xFF22C55E)), modifier = Modifier.fillMaxWidth(),
        )
        }
        Spacer(Modifier.height(14.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        Text("Cancel", color = Color(0xFF9AA4B2), fontWeight = FontWeight.Bold, modifier = Modifier.clip(RoundedCornerShape(10.dp)).clickable(onClick = onDismiss).padding(horizontal = 14.dp, vertical = 8.dp))
        Spacer(Modifier.width(8.dp))
        Text("Add", color = Color(0xFF22C55E), fontWeight = FontWeight.Bold, modifier = Modifier.clip(RoundedCornerShape(10.dp)).clickable { onConfirm(value) }.padding(horizontal = 16.dp, vertical = 8.dp))
        }
        }
        }
    }
}

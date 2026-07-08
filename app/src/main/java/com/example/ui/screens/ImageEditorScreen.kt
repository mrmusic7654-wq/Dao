package com.example.ui.screens

import android.content.*
import android.graphics.Bitmap
import android.graphics.Canvas as AndroidCanvas
import android.graphics.Paint as AndroidPaint
import android.graphics.Color as AndroidColor
import android.graphics.Path as AndroidPath
import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import android.graphics.Paint.Style
import android.graphics.Paint.Cap
import android.graphics.Paint.Join
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.*
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.example.ui.theme.*
import kotlinx.coroutines.*
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

// ==================== DATA MODELS ====================

data class TextLayer(
    val id: String = UUID.randomUUID().toString(),
    var text: String = "Text",
    var fontSize: Float = 28f,
    var color: Color = Color.White,
    var offsetX: Float = 0f,
    var offsetY: Float = 0f,
    var rotation: Float = 0f,
    var isBold: Boolean = false,
    var isItalic: Boolean = false,
    var fontFamily: String = "default"
)

data class Sticker(
    val id: String = UUID.randomUUID().toString(),
    val emoji: String = "⭐",
    var offsetX: Float = 0f,
    var offsetY: Float = 0f,
    var scale: Float = 1f,
    var rotation: Float = 0f
)

data class DrawPath(
    val id: String = UUID.randomUUID().toString(),
    val path: AndroidPath = AndroidPath(),
    val color: Color = Color.White,
    val strokeWidth: Float = 4f,
    val points: List<Offset> = emptyList()
)

enum class EditorTool { NONE, CROP, FILTERS, ADJUST, TEXT, STICKERS, DRAW, FRAME, AI_TOOLS }
enum class ImageFilter(val label: String, val icon: String) {
    ORIGINAL("Original", "🎨"), VINTAGE("Vintage", "📷"), BLACK_WHITE("B&W", "⬛"),
    WARM("Warm", "🔆"), COOL("Cool", "❄️"), DRAMATIC("Dramatic", "🌑"),
    FADE("Fade", "🌫️"), VIBRANT("Vibrant", "✨"), NOIR("Noir", "🖤"),
    SEPIA("Sepia", "📜"), AUTUMN("Autumn", "🍂"), CYBER("Cyber", "💜")
}

data class AdjustmentValues(
    var brightness: Float = 0f,
    var contrast: Float = 1f,
    var saturation: Float = 1f,
    var warmth: Float = 0f,
    var sharpness: Float = 0f,
    var vignette: Float = 0f
)

// ==================== IMAGE UTILS ====================

object ImageUtils {
    fun applyFilter(bitmap: Bitmap, filter: ImageFilter): Bitmap {
        val result = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = android.graphics.Canvas(result)
        val paint = android.graphics.Paint()

        when (filter) {
            ImageFilter.BLACK_WHITE -> {
                val cm = android.graphics.ColorMatrix().apply { setSaturation(0f) }
                paint.colorFilter = android.graphics.ColorMatrixColorFilter(cm)
            }
            ImageFilter.VINTAGE -> {
                val cm = android.graphics.ColorMatrix(floatArrayOf(
                    0.9f, 0.1f, 0.1f, 0f, 10f,
                    0.1f, 0.7f, 0.1f, 0f, 5f,
                    0.1f, 0.1f, 0.6f, 0f, -5f,
                    0f, 0f, 0f, 1f, 0f
                ))
                paint.colorFilter = android.graphics.ColorMatrixColorFilter(cm)
            }
            ImageFilter.SEPIA -> {
                val cm = android.graphics.ColorMatrix().apply { setScale(1.2f, 1f, 0.8f, 1f) }
                paint.colorFilter = android.graphics.ColorMatrixColorFilter(cm)
            }
            ImageFilter.WARM -> {
                val cm = android.graphics.ColorMatrix().apply { setScale(1.15f, 1.05f, 0.9f, 1f) }
                paint.colorFilter = android.graphics.ColorMatrixColorFilter(cm)
            }
            ImageFilter.COOL -> {
                val cm = android.graphics.ColorMatrix().apply { setScale(0.9f, 1f, 1.2f, 1f) }
                paint.colorFilter = android.graphics.ColorMatrixColorFilter(cm)
            }
            ImageFilter.VIBRANT -> {
                val cm = android.graphics.ColorMatrix().apply { setSaturation(1.8f) }
                paint.colorFilter = android.graphics.ColorMatrixColorFilter(cm)
            }
            else -> {}
        }
        canvas.drawBitmap(bitmap, 0f, 0f, paint)
        return result
    }

    fun applyAdjustments(bitmap: Bitmap, adjustments: AdjustmentValues): Bitmap {
        val result = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = android.graphics.Canvas(result)
        val cm = android.graphics.ColorMatrix()
        cm.setScale(1f + adjustments.brightness, 1f + adjustments.brightness, 1f + adjustments.brightness, 1f)
        val contrastMatrix = android.graphics.ColorMatrix(floatArrayOf(
            adjustments.contrast, 0f, 0f, 0f, 128f * (1f - adjustments.contrast),
            0f, adjustments.contrast, 0f, 0f, 128f * (1f - adjustments.contrast),
            0f, 0f, adjustments.contrast, 0f, 128f * (1f - adjustments.contrast),
            0f, 0f, 0f, 1f, 0f
        ))
        val saturationMatrix = android.graphics.ColorMatrix().apply { setSaturation(adjustments.saturation) }
        cm.set(contrastMatrix)
        val combined = android.graphics.ColorMatrix()
        combined.set(cm)
        combined.postConcat(saturationMatrix)
        android.graphics.Paint().apply {
            colorFilter = android.graphics.ColorMatrixColorFilter(combined)
            canvas.drawBitmap(bitmap, 0f, 0f, this)
        }
        return result
    }

    fun cropBitmap(bitmap: Bitmap, x: Int, y: Int, width: Int, height: Int): Bitmap {
        return Bitmap.createBitmap(bitmap, x.coerceIn(0, bitmap.width), y.coerceIn(0, bitmap.height),
            width.coerceIn(1, bitmap.width), height.coerceIn(1, bitmap.height))
    }

    fun renderWithLayers(
        baseImage: Bitmap,
        textLayers: List<TextLayer>,
        stickers: List<Sticker>,
        drawPaths: List<DrawPath>,
        canvasSize: IntSize,
        filter: ImageFilter,
        adjustments: AdjustmentValues
    ): Bitmap {
        val scaledBase = Bitmap.createScaledBitmap(baseImage, canvasSize.width, canvasSize.height, true)
        val filtered = applyFilter(scaledBase, filter)
        val adjusted = applyAdjustments(filtered, adjustments)
        val result = Bitmap.createBitmap(canvasSize.width, canvasSize.height, Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(result)
        canvas.drawBitmap(adjusted, 0f, 0f, null)
        return result
    }

    fun saveToGallery(context: Context, bitmap: Bitmap): File {
        val dir = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            File(context.getExternalFilesDir(Environment.DIRECTORY_PICTURES), "Dao")
        } else {
            File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "Dao")
        }
        if (!dir.exists()) dir.mkdirs()
        val file = File(dir, "dao_image_${System.currentTimeMillis()}.png")
        java.io.FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        context.sendBroadcast(Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE, Uri.fromFile(file)))
        return file
    }
}

// ==================== MAIN SCREEN ====================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImageEditorScreen(isDark: Boolean, onMenuClick: () -> Unit) {
    val context = LocalContext.current
    var sourceBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var displayBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var canvasSize by remember { mutableStateOf(IntSize(1080, 1080)) }

    // Editor state
    var activeTool by remember { mutableStateOf(EditorTool.NONE) }
    var selectedFilter by remember { mutableStateOf(ImageFilter.ORIGINAL) }
    var adjustments by remember { mutableStateOf(AdjustmentValues()) }
    var textLayers by remember { mutableStateOf(listOf<TextLayer>()) }
    var stickers by remember { mutableStateOf(listOf<Sticker>()) }
    var drawPaths by remember { mutableStateOf(listOf<DrawPath>()) }
    var currentDrawPath by remember { mutableStateOf<DrawPath?>(null) }
    var currentDrawColor by remember { mutableStateOf(Color.White) }
    var currentDrawWidth by remember { mutableStateOf(4f) }

    // UI state
    var showTextDialog by remember { mutableStateOf(false) }
    var showStickerDialog by remember { mutableStateOf(false) }
    var showExportDialog by remember { mutableStateOf(false) }
    var showUndoRedo by remember { mutableStateOf(false) }
    var undoStack by remember { mutableStateOf(listOf<Bitmap?>()) }
    var redoStack by remember { mutableStateOf(listOf<Bitmap?>()) }

    // Crop state
    var isCropping by remember { mutableStateOf(false) }
    var cropRect by remember { mutableStateOf(RectF()) }

    // Image picker
    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let {
            try {
                val bmp = MediaStore.Images.Media.getBitmap(context.contentResolver, it)
                sourceBitmap = bmp
                displayBitmap = bmp.copy(Bitmap.Config.ARGB_8888, true)
                undoStack = listOf(bmp.copy(Bitmap.Config.ARGB_8888, true))
                redoStack = emptyList()
                textLayers = emptyList(); stickers = emptyList(); drawPaths = emptyList()
                selectedFilter = ImageFilter.ORIGINAL; adjustments = AdjustmentValues()
            } catch (e: Exception) {
                Toast.makeText(context, "Failed to load image", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // Camera capture
    val cameraCapture = rememberLauncherForActivityResult(ActivityResultContracts.TakePicturePreview()) { bitmap ->
        bitmap?.let {
            sourceBitmap = it; displayBitmap = it.copy(Bitmap.Config.ARGB_8888, true)
            undoStack = listOf(it.copy(Bitmap.Config.ARGB_8888, true)); redoStack = emptyList()
            textLayers = emptyList(); stickers = emptyList(); drawPaths = emptyList()
            selectedFilter = ImageFilter.ORIGINAL; adjustments = AdjustmentValues()
        }
    }

    fun refreshDisplay() {
        sourceBitmap?.let { src ->
            displayBitmap = ImageUtils.renderWithLayers(src, textLayers, stickers, drawPaths, canvasSize, selectedFilter, adjustments)
        }
    }

    fun pushUndo() {
        displayBitmap?.let { undoStack = (undoStack + it.copy(Bitmap.Config.ARGB_8888, true)).takeLast(20) }
    }

    fun undo() {
        if (undoStack.size > 1) {
            redoStack = (redoStack + undoStack.last()).takeLast(20)
            undoStack = undoStack.dropLast(1)
            displayBitmap = undoStack.last()
        }
    }

    fun redo() {
        if (redoStack.isNotEmpty()) {
            undoStack = (undoStack + redoStack.last()).takeLast(20)
            displayBitmap = redoStack.last()
            redoStack = redoStack.dropLast(1)
        }
    }

    LaunchedEffect(selectedFilter, adjustments) { refreshDisplay() }

    // ==================== DIALOGS ====================

    if (showTextDialog) {
        var txt by remember { mutableStateOf("Text") }
        var fontSize by remember { mutableStateOf(28f) }
        var txtColor by remember { mutableStateOf(Color.White) }
        var isBold by remember { mutableStateOf(false) }

        AlertDialog(
            onDismissRequest = { showTextDialog = false },
            title = { Text("Add Text", color = ZenGold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(value = txt, onValueChange = { txt = it }, label = { Text("Text") }, modifier = Modifier.fillMaxWidth())
                    Text("Size: ${fontSize.toInt()}", color = YinTextSecondary, fontSize = 12.sp)
                    Slider(value = fontSize, onValueChange = { fontSize = it }, valueRange = 12f..72f, colors = SliderDefaults.colors(thumbColor = ZenGold, activeTrackColor = ZenGold))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(Color.White, Color.Black, ZenGold, ZenRed, ZenBlue, Color.Magenta, Color.Cyan, Color.Yellow).forEach { c ->
                            Box(modifier = Modifier.size(28.dp).clip(CircleShape).background(c).border(2.dp, if (txtColor == c) ZenGold else Color.Transparent, CircleShape).clickable(onClick = { txtColor = c }))
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) { Text("Bold", color = YinTextSecondary, fontSize = 12.sp); Switch(checked = isBold, onCheckedChange = { isBold = it }) }
                }
            },
            confirmButton = {
                Button(onClick = {
                    pushUndo()
                    textLayers = textLayers + TextLayer(text = txt, fontSize = fontSize, color = txtColor, isBold = isBold)
                    refreshDisplay(); showTextDialog = false
                }, colors = ButtonDefaults.buttonColors(containerColor = ZenGold)) { Text("Add", color = Color.Black) }
            },
            dismissButton = { TextButton(onClick = { showTextDialog = false }) { Text("Cancel", color = ZenGold) } },
            containerColor = if (isDark) Color(0xFF1A1A24) else Color(0xFFF7F4EE)
        )
    }

    if (showStickerDialog) {
        val stickers = listOf("⭐", "❤️", "🔥", "💎", "🌟", "✨", "🎯", "💡", "🌈", "🦋", "🌸", "💀", "👑", "🎨", "📸", "🎬")
        AlertDialog(
            onDismissRequest = { showStickerDialog = false },
            title = { Text("Add Sticker", color = ZenGold) },
            text = {
                Column(modifier = Modifier.height(250.dp).verticalScroll(rememberScrollState())) {
                    val rows = stickers.chunked(6)
                    rows.forEach { row ->
                        Row(horizontalArrangement = Arrangement.SpaceEvenly, modifier = Modifier.fillMaxWidth()) {
                            row.forEach { emoji ->
                                Box(modifier = Modifier.size(44.dp).clip(RoundedCornerShape(8.dp)).clickable(onClick = {
                                    pushUndo()
                                    stickers = stickers + Sticker(emoji = emoji)
                                    refreshDisplay(); showStickerDialog = false
                                }), contentAlignment = Alignment.Center) { Text(emoji, fontSize = 28.sp) }
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showStickerDialog = false }) { Text("Close", color = ZenGold) } },
            containerColor = if (isDark) Color(0xFF1A1A24) else Color(0xFFF7F4EE)
        )
    }

    if (showExportDialog) {
        AlertDialog(
            onDismissRequest = { showExportDialog = false },
            title = { Text("Export Image", color = ZenGold) },
            text = { Text("Save edited image to gallery?", color = YinTextSecondary) },
            confirmButton = {
                Button(onClick = {
                    displayBitmap?.let {
                        val file = ImageUtils.saveToGallery(context, it)
                        Toast.makeText(context, "Saved: ${file.name}", Toast.LENGTH_LONG).show()
                    }
                    showExportDialog = false
                }, colors = ButtonDefaults.buttonColors(containerColor = ZenGold)) { Text("Save", color = Color.Black) }
            },
            dismissButton = { TextButton(onClick = { showExportDialog = false }) { Text("Cancel", color = ZenGold) } }
        )
    }

    // ==================== MAIN UI ====================
    Column(modifier = Modifier.fillMaxSize().background(if (isDark) Color(0xFF0A0A10) else Color(0xFFF5F5F5))) {
        // Header
        Surface(color = if (isDark) Color(0xFF14141E) else Color.White, shadowElevation = 4.dp) {
            Row(modifier = Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onMenuClick) { Icon(Icons.Default.Menu, null, tint = if (isDark) Color.White else Color.Black) }
                Text("🎨 IMAGE EDITOR", fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold, color = ZenGold, fontSize = 16.sp)
                Spacer(Modifier.weight(1f))
                IconButton(onClick = { undo() }) { Icon(Icons.Default.Undo, null, tint = if (undoStack.size > 1) YinText else Color.Gray, modifier = Modifier.size(20.dp)) }
                IconButton(onClick = { redo() }) { Icon(Icons.Default.Redo, null, tint = if (redoStack.isNotEmpty()) YinText else Color.Gray, modifier = Modifier.size(20.dp)) }
                IconButton(onClick = { showExportDialog = true }) { Icon(Icons.Default.Save, null, tint = ZenGold, modifier = Modifier.size(20.dp)) }
            }
        }

        // Canvas area
        Box(modifier = Modifier.weight(1f).padding(8.dp).clip(RoundedCornerShape(12.dp)).background(Color(0xFF1A1A22)).onSizeChanged { canvasSize = it }) {
            if (displayBitmap != null) {
                Image(bitmap = displayBitmap!!.asImageBitmap(), contentDescription = "Image",
                    modifier = Modifier.fillMaxSize().clipToBounds(),
                    contentScale = ContentScale.Fit)

                // Draw layer — for drawing tool
                if (activeTool == EditorTool.DRAW) {
                    Canvas(modifier = Modifier.fillMaxSize().pointerInput(Unit) {
                        detectDragGestures { change, dragAmount ->
                            change.consume()
                            val point = Offset(change.position.x, change.position.y)
                            val path = currentDrawPath?.path ?: AndroidPath().apply {
                                moveTo(point.x, point.y)
                                currentDrawPath = DrawPath(path = this, color = currentDrawColor, strokeWidth = currentDrawWidth)
                            }
                            path.lineTo(point.x, point.y)
                            currentDrawPath = currentDrawPath?.copy(path = path)
                        }
                    }.pointerInput(Unit) {
                        awaitPointerEventScope {
                            while (true) {
                                val event = awaitPointerEvent()
                                when {
                                    event.changes.any { it.pressed } -> {}
                                    else -> {
                                        currentDrawPath?.let { drawPaths = drawPaths + it; pushUndo() }
                                        currentDrawPath = null
                                    }
                                }
                            }
                        }
                    }) {
                        currentDrawPath?.let { dp ->
                            val pathPaint = AndroidPaint().apply {
                                color = dp.color.toArgb(); isAntiAlias = true
                                style = AndroidPaint.Style.STROKE; strokeWidth = dp.strokeWidth
                                strokeCap = AndroidPaint.Cap.ROUND; strokeJoin = AndroidPaint.Join.ROUND
                            }
                            drawPath(dp.path.toComposePath(), dp.color, style = Stroke(width = dp.strokeWidth, cap = StrokeCap.Round, join = StrokeJoin.Round))
                        }
                    }
                }
            } else {
                // Empty state
                Column(modifier = Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                    Icon(Icons.Default.Image, null, modifier = Modifier.size(72.dp), tint = Color.Gray.copy(alpha = 0.4f))
                    Spacer(Modifier.height(16.dp))
                    Text("No Image Selected", color = Color.Gray, fontSize = 16.sp)
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Button(onClick = { imagePicker.launch("image/*") }, colors = ButtonDefaults.buttonColors(containerColor = ZenGold)) {
                            Icon(Icons.Default.PhotoLibrary, null, modifier = Modifier.size(16.dp)); Text(" Gallery", color = Color.Black, fontSize = 13.sp)
                        }
                        Button(onClick = { cameraCapture.launch(null) }, colors = ButtonDefaults.buttonColors(containerColor = ZenRed)) {
                            Icon(Icons.Default.Camera, null, modifier = Modifier.size(16.dp)); Text(" Camera", color = Color.White, fontSize = 13.sp)
                        }
                    }
                }
            }
        }

        // Toolbar
        Surface(color = if (isDark) Color(0xFF14141E) else Color.White, shadowElevation = 8.dp) {
            Column {
                // Tool chips row
                Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp).horizontalScroll(rememberScrollState())) {
                    ToolChip("Filters", Icons.Default.Filter, activeTool == EditorTool.FILTERS) { activeTool = if (activeTool == EditorTool.FILTERS) EditorTool.NONE else EditorTool.FILTERS }
                    ToolChip("Adjust", Icons.Default.Tune, activeTool == EditorTool.ADJUST) { activeTool = if (activeTool == EditorTool.ADJUST) EditorTool.NONE else EditorTool.ADJUST }
                    ToolChip("Crop", Icons.Default.Crop, activeTool == EditorTool.CROP) { activeTool = if (activeTool == EditorTool.CROP) EditorTool.NONE else EditorTool.CROP }
                    ToolChip("Text", Icons.Default.TextFields, activeTool == EditorTool.TEXT) { showTextDialog = true; activeTool = EditorTool.NONE }
                    ToolChip("Stickers", Icons.Default.EmojiEmotions, activeTool == EditorTool.STICKERS) { showStickerDialog = true; activeTool = EditorTool.NONE }
                    ToolChip("Draw", Icons.Default.Draw, activeTool == EditorTool.DRAW) { activeTool = if (activeTool == EditorTool.DRAW) EditorTool.NONE else EditorTool.DRAW }
                    ToolChip("AI Tools", Icons.Default.AutoAwesome, activeTool == EditorTool.AI_TOOLS) { activeTool = if (activeTool == EditorTool.AI_TOOLS) EditorTool.NONE else EditorTool.AI_TOOLS }
                }

                // Active tool panel
                AnimatedVisibility(visible = activeTool != EditorTool.NONE, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                    when (activeTool) {
                        EditorTool.FILTERS -> {
                            LazyRow(modifier = Modifier.padding(8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                items(ImageFilter.entries.toList()) { filter ->
                                    val isSelected = selectedFilter == filter
                                    Column(horizontalAlignment = Alignment.CenterHorizontally,
                                        modifier = Modifier.width(64.dp).clip(RoundedCornerShape(10.dp)).background(if (isSelected) ZenGold.copy(alpha = 0.2f) else Color.Transparent).border(1.dp, if (isSelected) ZenGold else Color.Transparent, RoundedCornerShape(10.dp)).clickable(onClick = { pushUndo(); selectedFilter = filter }).padding(8.dp)) {
                                        Text(filter.icon, fontSize = 24.sp); Text(filter.label, fontSize = 9.sp, color = if (isSelected) ZenGold else YinTextSecondary, textAlign = TextAlign.Center, maxLines = 1)
                                    }
                                }
                            }
                        }
                        EditorTool.ADJUST -> {
                            Column(modifier = Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                listOf(
                                    "Brightness" to adjustments.brightness to (-0.5f..0.5f),
                                    "Contrast" to adjustments.contrast to (0.5f..2f),
                                    "Saturation" to adjustments.saturation to (0f..2f),
                                    "Warmth" to adjustments.warmth to (-1f..1f),
                                    "Sharpness" to adjustments.sharpness to (0f..1f),
                                    "Vignette" to adjustments.vignette to (0f..1f)
                                ).forEach { (label, range) ->
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(label.first, color = YinTextSecondary, fontSize = 11.sp, modifier = Modifier.width(70.dp))
                                        Slider(value = label.second, onValueChange = { v ->
                                            pushUndo()
                                            adjustments = when (label.first) {
                                                "Brightness" -> adjustments.copy(brightness = v)
                                                "Contrast" -> adjustments.copy(contrast = v)
                                                "Saturation" -> adjustments.copy(saturation = v)
                                                "Warmth" -> adjustments.copy(warmth = v)
                                                "Sharpness" -> adjustments.copy(sharpness = v)
                                                "Vignette" -> adjustments.copy(vignette = v)
                                                else -> adjustments
                                            }
                                        }, valueRange = range.second, modifier = Modifier.weight(1f), colors = SliderDefaults.colors(thumbColor = ZenGold, activeTrackColor = ZenGold, inactiveTrackColor = Color(0xFF333340)))
                                        Text("${"%.1f".format(label.second)}", color = ZenGold, fontSize = 10.sp, modifier = Modifier.width(35.dp))
                                    }
                                }
                            }
                        }
                        EditorTool.CROP -> {
                            Row(modifier = Modifier.padding(8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                listOf("1:1" to 1f, "4:3" to 1.33f, "16:9" to 1.78f, "9:16" to 0.56f, "Original" to 0f).forEach { (label, ratio) ->
                                    Button(onClick = {
                                        sourceBitmap?.let { bmp ->
                                            pushUndo()
                                            val w = bmp.width; val h = if (ratio > 0) (w / ratio).toInt() else bmp.height
                                            displayBitmap = ImageUtils.cropBitmap(bmp, 0, 0, w, h.coerceAtMost(bmp.height))
                                        }
                                    }, colors = ButtonDefaults.buttonColors(containerColor = YinCardBg), modifier = Modifier.height(32.dp)) {
                                        Text(label, color = YinText, fontSize = 11.sp)
                                    }
                                }
                            }
                        }
                        EditorTool.DRAW -> {
                            Row(modifier = Modifier.padding(8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                listOf(Color.White, Color.Black, ZenRed, ZenGold, ZenBlue, Color.Magenta, Color.Cyan, Color.Green).forEach { c ->
                                    Box(modifier = Modifier.size(24.dp).clip(CircleShape).background(c).border(2.dp, if (currentDrawColor == c) ZenGold else Color.Transparent, CircleShape).clickable(onClick = { currentDrawColor = c }))
                                }
                                Spacer(Modifier.width(8.dp))
                                Slider(value = currentDrawWidth, onValueChange = { currentDrawWidth = it }, valueRange = 2f..20f, modifier = Modifier.width(100.dp), colors = SliderDefaults.colors(thumbColor = ZenGold, activeTrackColor = ZenGold))
                                Text("${currentDrawWidth.toInt()}px", color = YinTextSecondary, fontSize = 10.sp)
                                Spacer(Modifier.width(8.dp))
                                Button(onClick = { drawPaths = emptyList(); refreshDisplay() }, colors = ButtonDefaults.buttonColors(containerColor = ZenRed), modifier = Modifier.height(32.dp)) {
                                    Text("Clear", color = Color.White, fontSize = 11.sp)
                                }
                            }
                        }
                        EditorTool.AI_TOOLS -> {
                            Row(modifier = Modifier.padding(8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(onClick = {
                                    pushUndo()
                                    adjustments = adjustments.copy(brightness = 0.1f, contrast = 1.1f, saturation = 1.2f)
                                    selectedFilter = ImageFilter.VIBRANT
                                    Toast.makeText(context, "✨ AI Enhance applied", Toast.LENGTH_SHORT).show()
                                }, colors = ButtonDefaults.buttonColors(containerColor = ZenGold), modifier = Modifier.height(36.dp)) {
                                    Icon(Icons.Default.AutoAwesome, null, modifier = Modifier.size(16.dp)); Text(" AI Enhance", color = Color.Black, fontSize = 12.sp)
                                }
                                Button(onClick = {
                                    pushUndo()
                                    selectedFilter = ImageFilter.BLACK_WHITE
                                    adjustments = adjustments.copy(contrast = 1.3f, sharpness = 0.5f)
                                    Toast.makeText(context, "⬛ Smart B&W applied", Toast.LENGTH_SHORT).show()
                                }, colors = ButtonDefaults.buttonColors(containerColor = YinCardBg), modifier = Modifier.height(36.dp)) {
                                    Text("Smart B&W", color = YinText, fontSize = 12.sp)
                                }
                                Button(onClick = {
                                    val randomFilter = ImageFilter.entries.filter { it != ImageFilter.ORIGINAL }.random()
                                    pushUndo(); selectedFilter = randomFilter
                                    adjustments = AdjustmentValues(brightness = Random().nextFloat() * 0.2f, contrast = 0.8f + Random().nextFloat() * 0.4f, saturation = 0.7f + Random().nextFloat() * 0.6f)
                                    Toast.makeText(context, "🎲 Random style: ${randomFilter.label}", Toast.LENGTH_SHORT).show()
                                }, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6E40C9)), modifier = Modifier.height(36.dp)) {
                                    Text("🎲 Random", color = Color.White, fontSize = 12.sp)
                                }
                            }
                        }
                        else -> {}
                    }
                }
            }
        }
    }
}

@Composable
private fun ToolChip(label: String, icon: ImageVector, active: Boolean, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.padding(horizontal = 4.dp).clip(RoundedCornerShape(10.dp)).background(if (active) ZenGold.copy(alpha = 0.15f) else Color.Transparent).border(1.dp, if (active) ZenGold.copy(alpha = 0.4f) else Color.Transparent, RoundedCornerShape(10.dp)).clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 8.dp)) {
        Icon(icon, label, tint = if (active) ZenGold else YinTextSecondary, modifier = Modifier.size(22.dp))
        Text(label, color = if (active) ZenGold else YinTextSecondary, fontSize = 10.sp, fontWeight = if (active) FontWeight.Bold else FontWeight.Normal)
    }
}

private fun AndroidPath.toComposePath(): androidx.compose.ui.graphics.Path {
    return androidx.compose.ui.graphics.Path()
}

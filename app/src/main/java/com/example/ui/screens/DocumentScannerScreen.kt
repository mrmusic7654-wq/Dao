package com.example.ui.screens

import android.content.*
import android.graphics.*
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
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.*
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
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
import java.io.*
import java.text.SimpleDateFormat
import java.util.*

// ==================== DATA MODELS ====================

data class ScannedPage(
    val id: String = UUID.randomUUID().toString(),
    val bitmap: Bitmap,
    val originalBitmap: Bitmap,
    val filterType: DocFilter = DocFilter.ORIGINAL,
    val corners: QuadCorners? = null,
    val fileName: String = "page_${System.currentTimeMillis()}",
    val rotation: Float = 0f
)

data class QuadCorners(
    val topLeft: PointF,
    val topRight: PointF,
    val bottomRight: PointF,
    val bottomLeft: PointF
) {
    fun toFloatArray(): FloatArray = floatArrayOf(
        topLeft.x, topLeft.y, topRight.x, topRight.y,
        bottomRight.x, bottomRight.y, bottomLeft.x, bottomLeft.y
    )
}

enum class DocFilter(val label: String, val icon: String) {
    ORIGINAL("Original", "📄"),
    BNW("B&W", "⬛"),
    GRAYSCALE("Grayscale", "🔘"),
    ENHANCED("Enhanced", "✨"),
    MAGIC_COLOR("Magic Color", "🎨"),
    LIGHTEN("Lighten", "☀️"),
    DARKEN("Darken", "🌙"),
    HIGH_CONTRAST("High Contrast", "◼")
}

data class ScanDocument(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "Untitled Document",
    val pages: List<ScannedPage> = emptyList(),
    val createdAt: Long = System.currentTimeMillis(),
    val pageFormat: PageFormat = PageFormat.A4
)

enum class PageFormat(val label: String, val width: Int, val height: Int) {
    A4("A4", 2480, 3508), LETTER("Letter", 2550, 3300),
    LEGAL("Legal", 2550, 4200), BUSINESS_CARD("Card", 1050, 600)
}

enum class ScannerView { CAMERA, EDITOR, GALLERY, EXPORT }

// ==================== IMAGE PROCESSING ====================

object DocProcessor {
    fun detectEdges(bitmap: Bitmap): QuadCorners? {
        try {
            val mat = bitmap.copy(Bitmap.Config.ARGB_8888, true)
            val width = mat.width; val height = mat.height
            // Simplified edge detection — in production use OpenCV
            val margin = 0.08f
            return QuadCorners(
                PointF(width * margin, height * margin),
                PointF(width * (1f - margin), height * margin),
                PointF(width * (1f - margin), height * (1f - margin)),
                PointF(width * margin, height * (1f - margin))
            )
        } catch (e: Exception) { return null }
    }

    fun applyPerspectiveCorrection(bitmap: Bitmap, corners: QuadCorners, targetW: Int = 2480, targetH: Int = 3508): Bitmap {
        val src = corners.toFloatArray()
        val dst = floatArrayOf(0f, 0f, targetW.toFloat(), 0f, targetW.toFloat(), targetH.toFloat(), 0f, targetH.toFloat())
        val matrix = Matrix()
        matrix.setPolyToPoly(src, 0, dst, 0, 4)
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
            .let { Bitmap.createScaledBitmap(it, targetW, targetH, true) }
    }

    fun applyFilter(bitmap: Bitmap, filter: DocFilter): Bitmap {
        val result = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(result)
        val paint = Paint()

        when (filter) {
            DocFilter.BNW -> {
                val cm = ColorMatrix().apply { setSaturation(0f) }
                paint.colorFilter = ColorMatrixColorFilter(cm)
                canvas.drawBitmap(bitmap, 0f, 0f, paint)
                // Threshold for pure B&W
                val pixels = IntArray(result.width * result.height)
                result.getPixels(pixels, 0, result.width, 0, 0, result.width, result.height)
                for (i in pixels.indices) {
                    val gray = (Color.red(pixels[i]) + Color.green(pixels[i]) + Color.blue(pixels[i])) / 3
                    pixels[i] = if (gray > 128) Color.WHITE else Color.BLACK
                }
                result.setPixels(pixels, 0, result.width, 0, 0, result.width, result.height)
            }
            DocFilter.GRAYSCALE -> {
                val cm = ColorMatrix().apply { setSaturation(0f) }
                paint.colorFilter = ColorMatrixColorFilter(cm)
                canvas.drawBitmap(bitmap, 0f, 0f, paint)
            }
            DocFilter.ENHANCED -> {
                val cm = ColorMatrix().apply { setSaturation(1.2f) }
                paint.colorFilter = ColorMatrixColorFilter(cm)
                canvas.drawBitmap(bitmap, 0f, 0f, paint)
            }
            DocFilter.HIGH_CONTRAST -> {
                val cm = ColorMatrix(floatArrayOf(
                    1.5f, 0f, 0f, 0f, -30f, 0f, 1.5f, 0f, 0f, -30f,
                    0f, 0f, 1.5f, 0f, -30f, 0f, 0f, 0f, 1f, 0f
                ))
                paint.colorFilter = ColorMatrixColorFilter(cm)
                canvas.drawBitmap(bitmap, 0f, 0f, paint)
            }
            DocFilter.LIGHTEN -> {
                val cm = ColorMatrix().apply { setScale(1.2f, 1.2f, 1.2f, 1f) }
                paint.colorFilter = ColorMatrixColorFilter(cm)
                canvas.drawBitmap(bitmap, 0f, 0f, paint)
            }
            DocFilter.DARKEN -> {
                val cm = ColorMatrix().apply { setScale(0.7f, 0.7f, 0.7f, 1f) }
                paint.colorFilter = ColorMatrixColorFilter(cm)
                canvas.drawBitmap(bitmap, 0f, 0f, paint)
            }
            else -> return bitmap
        }
        return result
    }

    fun rotateBitmap(bitmap: Bitmap, degrees: Float): Bitmap {
        val matrix = Matrix().apply { postRotate(degrees) }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    fun saveAsPdf(pages: List<ScannedPage>, outputFile: File, format: PageFormat = PageFormat.A4): Boolean {
        return try {
            val pdfDocument = android.graphics.pdf.PdfDocument()
            pages.forEach { page ->
                val pageInfo = android.graphics.pdf.PdfDocument.PageInfo.Builder(format.width, format.height, pages.indexOf(page) + 1).create()
                val pdfPage = pdfDocument.startPage(pageInfo)
                val canvas = pdfPage.canvas
                val scaledBitmap = Bitmap.createScaledBitmap(page.bitmap, format.width, format.height, true)
                canvas.drawBitmap(scaledBitmap, 0f, 0f, null)
                pdfDocument.finishPage(pdfPage)
            }
            FileOutputStream(outputFile).use { pdfDocument.writeTo(it) }
            pdfDocument.close()
            true
        } catch (e: Exception) { false }
    }

    fun savePageAsImage(bitmap: Bitmap, outputFile: File, format: Bitmap.CompressFormat = Bitmap.CompressFormat.JPEG, quality: Int = 95): Boolean {
        return try {
            FileOutputStream(outputFile).use { bitmap.compress(format, quality, it) }
            true
        } catch (e: Exception) { false }
    }

    fun getOutputDir(context: Context): File {
        val dir = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            File(context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), "Dao/Scans")
        } else {
            File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS), "Dao/Scans")
        }
        if (!dir.exists()) dir.mkdirs()
        return dir
    }
}

// ==================== MAIN SCREEN ====================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DocumentScannerScreen(isDark: Boolean, onMenuClick: () -> Unit) {
    val context = LocalContext.current
    var viewMode by remember { mutableStateOf(ScannerView.CAMERA) }
    var currentDocument by remember { mutableStateOf(ScanDocument()) }
    var selectedPageIndex by remember { mutableIntStateOf(0) }
    var showExportDialog by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var newDocName by remember { mutableStateOf("") }
    var isProcessing by remember { mutableStateOf(false) }
    var processingMsg by remember { mutableStateOf("") }

    val cameraCapture = rememberLauncherForActivityResult(ActivityResultContracts.TakePicturePreview()) { bitmap ->
        bitmap?.let { processCapturedImage(it) }
    }

    val galleryPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let {
            try {
                val bmp = MediaStore.Images.Media.getBitmap(context.contentResolver, it)
                processCapturedImage(bmp)
            } catch (e: Exception) { Toast.makeText(context, "Failed to load image", Toast.LENGTH_SHORT).show() }
        }
    }

    fun processCapturedImage(bitmap: Bitmap) {
        isProcessing = true; processingMsg = "Detecting edges..."
        CoroutineScope(Dispatchers.Default).launch {
            val corners = DocProcessor.detectEdges(bitmap)
            val corrected = if (corners != null) {
                DocProcessor.applyPerspectiveCorrection(bitmap, corners)
            } else bitmap
            val filtered = DocProcessor.applyFilter(corrected, DocFilter.ORIGINAL)
            val page = ScannedPage(bitmap = filtered, originalBitmap = bitmap, corners = corners)
            withContext(Dispatchers.Main) {
                currentDocument = currentDocument.copy(pages = currentDocument.pages + page)
                selectedPageIndex = currentDocument.pages.size - 1
                viewMode = ScannerView.EDITOR; isProcessing = false
            }
        }
    }

    fun applyFilterToPage(filter: DocFilter) {
        if (currentDocument.pages.isEmpty()) return
        val pages = currentDocument.pages.toMutableList()
        pages[selectedPageIndex] = pages[selectedPageIndex].copy(
            bitmap = DocProcessor.applyFilter(pages[selectedPageIndex].originalBitmap, filter),
            filterType = filter
        )
        currentDocument = currentDocument.copy(pages = pages)
    }

    fun rotatePage(degrees: Float) {
        if (currentDocument.pages.isEmpty()) return
        val pages = currentDocument.pages.toMutableList()
        pages[selectedPageIndex] = pages[selectedPageIndex].copy(
            bitmap = DocProcessor.rotateBitmap(pages[selectedPageIndex].bitmap, degrees),
            rotation = (pages[selectedPageIndex].rotation + degrees) % 360f
        )
        currentDocument = currentDocument.copy(pages = pages)
    }

    fun deletePage() {
        if (currentDocument.pages.isEmpty()) return
        val pages = currentDocument.pages.toMutableList(); pages.removeAt(selectedPageIndex)
        currentDocument = currentDocument.copy(pages = pages)
        selectedPageIndex = if (pages.isEmpty()) 0 else selectedPageIndex.coerceAtMost(pages.size - 1)
        if (pages.isEmpty()) viewMode = ScannerView.CAMERA
    }

    fun exportDocument() {
        if (currentDocument.pages.isEmpty()) return
        isProcessing = true; processingMsg = "Creating PDF..."
        CoroutineScope(Dispatchers.IO).launch {
            val dir = DocProcessor.getOutputDir(context)
            val ts = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val pdfFile = File(dir, "${currentDocument.name}_$ts.pdf")
            val success = DocProcessor.saveAsPdf(currentDocument.pages, pdfFile)
            // Also save individual pages as images
            currentDocument.pages.forEachIndexed { idx, page ->
                val imgFile = File(dir, "${currentDocument.name}_${ts}_page${idx + 1}.jpg")
                DocProcessor.savePageAsImage(page.bitmap, imgFile)
            }
            withContext(Dispatchers.Main) {
                isProcessing = false
                if (success) {
                    context.sendBroadcast(Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE, Uri.fromFile(pdfFile)))
                    Toast.makeText(context, "Saved: ${pdfFile.name}", Toast.LENGTH_LONG).show()
                    showExportDialog = false
                } else Toast.makeText(context, "Export failed", Toast.LENGTH_SHORT).show()
            }
        }
    }

    val currentPage = currentDocument.pages.getOrNull(selectedPageIndex)

    // ==================== DIALOGS ====================

    if (showExportDialog) {
        AlertDialog(
            onDismissRequest = { showExportDialog = false },
            title = { Text("Export Document", color = ZenGold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    InfoRow("Document", currentDocument.name)
                    InfoRow("Pages", "${currentDocument.pages.size}")
                    InfoRow("Format", "PDF + JPEG images")
                    InfoRow("Page Size", currentDocument.pageFormat.label)
                    Text("Files will be saved to Documents/Dao/Scans/", color = YinTextSecondary, fontSize = 11.sp)
                }
            },
            confirmButton = {
                Button(onClick = { exportDocument() }, colors = ButtonDefaults.buttonColors(containerColor = ZenGold)) {
                    Text("Export PDF", color = Color.Black)
                }
            },
            dismissButton = { TextButton(onClick = { showExportDialog = false }) { Text("Cancel", color = ZenGold) } },
            containerColor = if (isDark) Color(0xFF1A1A24) else Color(0xFFF7F4EE)
        )
    }

    if (showRenameDialog) {
        AlertDialog(
            onDismissRequest = { showRenameDialog = false },
            title = { Text("Rename Document", color = ZenGold) },
            text = { OutlinedTextField(value = newDocName, onValueChange = { newDocName = it }, label = { Text("Name") }, modifier = Modifier.fillMaxWidth(), singleLine = true) },
            confirmButton = {
                Button(onClick = { currentDocument = currentDocument.copy(name = newDocName.ifBlank { "Untitled" }); showRenameDialog = false },
                    colors = ButtonDefaults.buttonColors(containerColor = ZenGold)) { Text("Rename", color = Color.Black) }
            },
            dismissButton = { TextButton(onClick = { showRenameDialog = false }) { Text("Cancel", color = ZenGold) } },
            containerColor = if (isDark) Color(0xFF1A1A24) else Color(0xFFF7F4EE)
        )
    }

    // Processing overlay
    if (isProcessing) {
        AlertDialog(onDismissRequest = { }, title = { Text("Processing", color = ZenGold) }, text = {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(modifier = Modifier.size(28.dp), color = ZenGold); Text(processingMsg, color = YinText)
            }
        }, confirmButton = { })
    }

    // ==================== MAIN UI ====================

    Column(modifier = Modifier.fillMaxSize().background(if (isDark) Color(0xFF070709) else Color(0xFFF5F5F5))) {
        // Header
        Surface(color = if (isDark) Color(0xFF14141E) else Color.White, shadowElevation = 4.dp) {
            Column(modifier = Modifier.statusBarsPadding()) {
                Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onMenuClick) { Icon(Icons.Default.Menu, null, tint = if (isDark) Color.White else Color.Black) }
                    Column(modifier = Modifier.weight(1f)) {
                        Text("📄 DOCUMENT SCANNER", fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold,
                            color = if (isDark) ZenGold else Color(0xFF9E7E1D), style = MaterialTheme.typography.titleMedium)
                        Text("${currentDocument.pages.size} page(s) • ${currentDocument.name}", color = YinTextSecondary, fontSize = 10.sp)
                    }
                    if (currentDocument.pages.isNotEmpty()) {
                        IconButton(onClick = { showExportDialog = true }) { Icon(Icons.Default.Save, "Export", tint = ZenGold) }
                    }
                    IconButton(onClick = { newDocName = currentDocument.name; showRenameDialog = true }) { Icon(Icons.Default.Edit, null, tint = YinTextSecondary, modifier = Modifier.size(18.dp)) }
                }

                // Mode tabs
                if (currentDocument.pages.isNotEmpty()) {
                    Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 2.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        FilterChip(selected = viewMode == ScannerView.EDITOR, onClick = { viewMode = ScannerView.EDITOR },
                            label = { Text("Edit", fontSize = 10.sp) }, colors = FilterChipDefaults.filterChipColors(selectedContainerColor = ZenGold.copy(alpha = 0.2f), selectedLabelColor = ZenGold))
                        FilterChip(selected = viewMode == ScannerView.GALLERY, onClick = { viewMode = ScannerView.GALLERY },
                            label = { Text("Pages", fontSize = 10.sp) }, colors = FilterChipDefaults.filterChipColors(selectedContainerColor = ZenGold.copy(alpha = 0.2f), selectedLabelColor = ZenGold))
                    }
                }
            }
        }

        // Content
        Box(modifier = Modifier.weight(1f)) {
            when (viewMode) {
                ScannerView.CAMERA -> {
                    Column(modifier = Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                        Icon(Icons.Default.DocumentScanner, null, modifier = Modifier.size(80.dp), tint = Color.Gray.copy(alpha = 0.4f))
                        Spacer(Modifier.height(16.dp))
                        Text("Document Scanner", color = Color.Gray, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        Text("Capture or import a document to scan", color = Color.Gray.copy(alpha = 0.6f), fontSize = 13.sp)
                        Spacer(Modifier.height(32.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            Button(onClick = { cameraCapture.launch(null) }, colors = ButtonDefaults.buttonColors(containerColor = ZenGold),
                                modifier = Modifier.width(140.dp).height(56.dp)) {
                                Icon(Icons.Default.Camera, null, modifier = Modifier.size(22.dp)); Text(" Camera", color = Color.Black, fontSize = 14.sp)
                            }
                            Button(onClick = { galleryPicker.launch("image/*") }, colors = ButtonDefaults.buttonColors(containerColor = ZenBlue),
                                modifier = Modifier.width(140.dp).height(56.dp)) {
                                Icon(Icons.Default.PhotoLibrary, null, modifier = Modifier.size(22.dp)); Text(" Gallery", color = Color.White, fontSize = 14.sp)
                            }
                        }
                    }
                }

                ScannerView.EDITOR -> {
                    if (currentPage != null) {
                        Column(modifier = Modifier.fillMaxSize()) {
                            // Page preview
                            Box(modifier = Modifier.weight(1f).padding(8.dp).clip(RoundedCornerShape(12.dp)).background(Color(0xFF1A1A22)).border(2.dp, ZenGold.copy(alpha = 0.3f), RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
                                Image(bitmap = currentPage.bitmap.asImageBitmap(), contentDescription = "Page ${selectedPageIndex + 1}",
                                    modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Fit)

                                // Corner overlay
                                currentPage.corners?.let { corners ->
                                    Canvas(modifier = Modifier.fillMaxSize()) {
                                        val pts = corners.toFloatArray()
                                        val scaleX = size.width / currentPage.bitmap.width
                                        val scaleY = size.height / currentPage.bitmap.height
                                        val scaledPts = pts.mapIndexed { idx, v -> if (idx % 2 == 0) v * scaleX else v * scaleY }
                                        drawCircle(Color(0xFF4CAF50), 8f, Offset(scaledPts[0], scaledPts[1]))
                                        drawCircle(Color(0xFF4CAF50), 8f, Offset(scaledPts[2], scaledPts[3]))
                                        drawCircle(Color(0xFF4CAF50), 8f, Offset(scaledPts[4], scaledPts[5]))
                                        drawCircle(Color(0xFF4CAF50), 8f, Offset(scaledPts[6], scaledPts[7]))
                                    }
                                }

                                // Page number
                                Surface(modifier = Modifier.align(Alignment.BottomEnd).padding(8.dp), color = Color.Black.copy(alpha = 0.7f), shape = RoundedCornerShape(4.dp)) {
                                    Text("${selectedPageIndex + 1}/${currentDocument.pages.size}", color = Color.White, fontSize = 11.sp, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
                                }
                            }

                            // Filter row
                            Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp), colors = CardDefaults.cardColors(containerColor = YinCardBg)) {
                                Column(modifier = Modifier.padding(8.dp)) {
                                    Text("Filters", color = YinTextSecondary, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        items(DocFilter.entries.toList()) { filter ->
                                            val isActive = currentPage.filterType == filter
                                            Column(horizontalAlignment = Alignment.CenterHorizontally,
                                                modifier = Modifier.width(56.dp).clip(RoundedCornerShape(8.dp)).background(if (isActive) ZenGold.copy(alpha = 0.2f) else Color.Transparent).clickable(onClick = { applyFilterToPage(filter) }).padding(6.dp)) {
                                                Text(filter.icon, fontSize = 18.sp); Text(filter.label, fontSize = 8.sp, color = if (isActive) ZenGold else YinTextSecondary, textAlign = TextAlign.Center, maxLines = 1)
                                            }
                                        }
                                    }
                                }
                            }

                            // Toolbar
                            Surface(color = if (isDark) Color(0xFF14141E) else Color.White, shadowElevation = 4.dp) {
                                Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    ToolChip("Rotate L", Icons.Default.RotateLeft) { rotatePage(-90f) }
                                    ToolChip("Rotate R", Icons.Default.RotateRight) { rotatePage(90f) }
                                    ToolChip("Recrop", Icons.Default.Crop) { /* Re-detect edges */ }
                                    ToolChip("Delete", Icons.Default.Delete, ZenRed) { deletePage() }
                                    ToolChip("Add Page", Icons.Default.Add, ZenGold) { viewMode = ScannerView.CAMERA }
                                    ToolChip("Export", Icons.Default.Save, Color(0xFF4CAF50)) { showExportDialog = true }
                                }
                            }
                        }
                    }
                }

                ScannerView.GALLERY -> {
                    if (currentDocument.pages.isEmpty()) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("No pages", color = Color.Gray) }
                    } else {
                        LazyColumn(contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            item {
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("All Pages (${currentDocument.pages.size})", color = YinTextSecondary, fontSize = 12.sp)
                                    TextButton(onClick = { viewMode = ScannerView.CAMERA }) { Text("+ Add Page", color = ZenGold) }
                                }
                            }
                            itemsIndexed(currentDocument.pages) { index, page ->
                                Card(modifier = Modifier.fillMaxWidth().clickable(onClick = { selectedPageIndex = index; viewMode = ScannerView.EDITOR }),
                                    colors = CardDefaults.cardColors(containerColor = if (index == selectedPageIndex) ZenGold.copy(alpha = 0.1f) else YinCardBg),
                                    border = if (index == selectedPageIndex) BorderStroke(1.5.dp, ZenGold) else null,
                                    shape = RoundedCornerShape(10.dp)) {
                                    Row(modifier = Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Image(bitmap = page.bitmap.asImageBitmap(), contentDescription = "Page ${index + 1}",
                                            modifier = Modifier.size(60.dp).clip(RoundedCornerShape(6.dp)), contentScale = ContentScale.Crop)
                                        Spacer(Modifier.width(10.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text("Page ${index + 1}", color = YinText, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                            Text(page.filterType.label, color = YinTextSecondary, fontSize = 11.sp)
                                        }
                                        IconButton(onClick = {
                                            val pages = currentDocument.pages.toMutableList(); pages.removeAt(index)
                                            currentDocument = currentDocument.copy(pages = pages)
                                        }) { Icon(Icons.Default.Delete, null, tint = ZenRed, modifier = Modifier.size(18.dp)) }
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
private fun ToolChip(label: String, icon: ImageVector, color: Color = YinTextSecondary, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.padding(horizontal = 4.dp).clip(RoundedCornerShape(8.dp)).clickable(onClick = onClick).padding(horizontal = 10.dp, vertical = 6.dp)) {
        Icon(icon, label, tint = color, modifier = Modifier.size(20.dp))
        Text(label, color = color, fontSize = 10.sp)
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = YinTextSecondary, fontSize = 11.sp); Text(value, color = YinText, fontSize = 11.sp)
    }
}

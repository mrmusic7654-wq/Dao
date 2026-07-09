package com.example.ui.screens

import android.content.Context
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.dao_engine.GeminiService
import com.example.ui.theme.*
import kotlinx.coroutines.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PDFReaderScreen(isDark: Boolean, onMenuClick: () -> Unit) {
    val context = LocalContext.current
    var pdfRenderer by remember { mutableStateOf<PdfRenderer?>(null) }
    var currentPage by remember { mutableIntStateOf(0) }
    var totalPages by remember { mutableIntStateOf(0) }
    var pageBitmap by remember { mutableStateOf<android.graphics.Bitmap?>(null) }
    var extractedText by remember { mutableStateOf("") }
    var aiSummary by remember { mutableStateOf("") }
    var showAIPanel by remember { mutableStateOf(false) }
    var showAskDialog by remember { mutableStateOf(false) }
    var question by remember { mutableStateOf("") }
    var aiAnswer by remember { mutableStateOf("") }

    val pdfPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { loadPDF(context, it) { renderer, pages, text ->
            pdfRenderer = renderer
            totalPages = pages
            extractedText = text
            currentPage = 0
            renderPage(renderer, 0) { pageBitmap = it }
        }}
    }

    fun goToPage(page: Int) {
        if (page in 0 until totalPages) {
            currentPage = page
            pdfRenderer?.let { renderPage(it, page) { pageBitmap = it } }
        }
    }

    if (showAskDialog) {
        AlertDialog(
            onDismissRequest = { showAskDialog = false },
            title = { Text("Ask about this PDF", color = ZenGold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = question, onValueChange = { question = it },
                        label = { Text("Your question") })
                    if (aiAnswer.isNotEmpty()) {
                        Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A24))) {
                            Text(aiAnswer, modifier = Modifier.padding(12.dp), color = YinText, fontSize = 13.sp)
                        }
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    CoroutineScope(Dispatchers.IO).launch {
                        val prompt = "Based on this PDF content, answer: $question\n\nPDF Text: ${extractedText.take(10000)}"
                        val response = GeminiService.generateResponse(context, prompt, "Assistant", "Direct")
                        withContext(Dispatchers.Main) { aiAnswer = response.replyText }
                    }
                }) { Text("Ask") }
            },
            dismissButton = { TextButton(onClick = { showAskDialog = false }) { Text("Close") } }
        )
    }

    Column(modifier = Modifier.fillMaxSize().background(if (isDark) Color(0xFF0A0A10) else Color(0xFFF5F5F5))) {
        // Header
        Surface(color = if (isDark) Color(0xFF14141E) else Color.White) {
            Row(modifier = Modifier.padding(12.dp).statusBarsPadding(), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onMenuClick) { Icon(Icons.Default.Menu, null) }
                Text("📄 PDF Reader", color = ZenGold, fontWeight = FontWeight.Bold)
                Spacer(Modifier.weight(1f))
                IconButton(onClick = { pdfPicker.launch("application/pdf") }) { Icon(Icons.Default.OpenInNew, null, tint = ZenGold) }
                if (pdfRenderer != null) {
                    IconButton(onClick = { showAIPanel = !showAIPanel }) { Icon(Icons.Default.AutoAwesome, null, tint = if (showAIPanel) ZenGold else YinTextSecondary) }
                    IconButton(onClick = { showAskDialog = true }) { Icon(Icons.Default.QuestionAnswer, null, tint = ZenGold) }
                }
            }
        }

        if (pdfRenderer == null) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Icon(Icons.Default.PictureAsPdf, null, modifier = Modifier.size(64.dp), tint = Color.Gray)
                    Text("Open a PDF to read", color = Color.Gray)
                    Button(onClick = { pdfPicker.launch("application/pdf") }) { Text("Open PDF") }
                }
            }
        } else {
            Column(modifier = Modifier.fillMaxSize()) {
                // Page content
                Box(modifier = Modifier.weight(1f).padding(8.dp)) {
                    pageBitmap?.let {
                        Image(bitmap = it.asImageBitmap(), contentDescription = "Page ${currentPage + 1}",
                            modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
                    }
                }

                // AI Panel
                if (showAIPanel && aiSummary.isNotEmpty()) {
                    Card(modifier = Modifier.fillMaxWidth().padding(8.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A24))) {
                        Text(aiSummary, modifier = Modifier.padding(12.dp), color = YinText, fontSize = 12.sp)
                    }
                }

                // Page navigation
                Surface(color = if (isDark) Color(0xFF14141E) else Color.White) {
                    Row(modifier = Modifier.fillMaxWidth().padding(8.dp), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { goToPage(0) }) { Icon(Icons.Default.FirstPage, null) }
                        IconButton(onClick = { goToPage(currentPage - 1) }) { Icon(Icons.Default.ChevronLeft, null) }
                        Text("${currentPage + 1} / $totalPages", color = YinText, fontSize = 14.sp)
                        IconButton(onClick = { goToPage(currentPage + 1) }) { Icon(Icons.Default.ChevronRight, null) }
                        IconButton(onClick = { goToPage(totalPages - 1) }) { Icon(Icons.Default.LastPage, null) }
                    }
                }
            }
        }
    }
}

private fun loadPDF(context: Context, uri: Uri, onLoaded: (PdfRenderer, Int, String) -> Unit) {
    try {
        val fd = context.contentResolver.openFileDescriptor(uri, "r") ?: return
        val renderer = PdfRenderer(fd)
        val text = StringBuilder()
        for (i in 0 until renderer.pageCount) {
            val page = renderer.openPage(i)
            // Simple text extraction (limited; for full extraction use a library)
            text.append("Page ${i + 1}\n")
            page.close()
        }
        onLoaded(renderer, renderer.pageCount, text.toString())
    } catch (e: Exception) {}
}

private fun renderPage(renderer: PdfRenderer, pageNum: Int, onRendered: (android.graphics.Bitmap) -> Unit) {
    val page = renderer.openPage(pageNum)
    val bitmap = android.graphics.Bitmap.createBitmap(page.width, page.height, android.graphics.Bitmap.Config.ARGB_8888)
    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
    page.close()
    onRendered(bitmap)
}

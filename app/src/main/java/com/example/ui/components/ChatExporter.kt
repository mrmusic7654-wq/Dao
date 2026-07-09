package com.example.ui.components

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import com.example.data.model.ChatMessage
import com.example.data.model.ChatSession
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class ChatExporter(private val context: Context) {

    fun exportAsMarkdown(session: ChatSession, messages: List<ChatMessage>): File {
        val content = buildString {
            appendLine("# ${session.title}")
            appendLine()
            appendLine("> Exported: ${SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date())}")
            appendLine()
            appendLine("---")
            appendLine()

            messages.forEach { msg ->
                val role = if (msg.isUser) "**You**" else "**Dao ☯**"
                appendLine("### $role")
                appendLine()
                appendLine(msg.content)
                appendLine()
                appendLine("---")
                appendLine()
            }
        }

        return saveFile("${session.title}.md", content)
    }

    fun exportAsJSON(session: ChatSession, messages: List<ChatMessage>): File {
        val json = org.json.JSONObject().apply {
            put("session", session.title)
            put("exported", System.currentTimeMillis())
            put("messages", org.json.JSONArray().apply {
                messages.forEach { msg ->
                    put(org.json.JSONObject().apply {
                        put("role", if (msg.isUser) "user" else "assistant")
                        put("content", msg.content)
                        put("timestamp", msg.timestamp)
                    })
                }
            })
        }

        return saveFile("${session.title}.json", json.toString(2))
    }

    fun exportAsPDF(session: ChatSession, messages: List<ChatMessage>): File {
        val pdfDocument = android.graphics.pdf.PdfDocument()
        val pageWidth = 595 // A4 points
        val pageHeight = 842
        var yPos = 40f

        val pageInfo = android.graphics.pdf.PdfDocument.PageInfo.Builder(pageWidth, pageHeight, 1).create()
        val page = pdfDocument.startPage(pageInfo)
        val canvas = page.canvas
        val titlePaint = android.graphics.Paint().apply { textSize = 18f; isFakeBoldText = true }
        val bodyPaint = android.graphics.Paint().apply { textSize = 11f }
        val smallPaint = android.graphics.Paint().apply { textSize = 9f }

        canvas.drawText(session.title, 40f, yPos, titlePaint)
        yPos += 30f
        canvas.drawText("Exported: ${SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())}", 40f, yPos, smallPaint)
        yPos += 30f

        messages.forEach { msg ->
            if (yPos > pageHeight - 60) {
                pdfDocument.finishPage(page)
                yPos = 40f
            }
            val label = if (msg.isUser) "You:" else "Dao:"
            canvas.drawText(label, 40f, yPos, titlePaint)
            yPos += 20f

            val lines = msg.content.split("\n")
            lines.forEach { line ->
                if (yPos > pageHeight - 20) {
                    pdfDocument.finishPage(page)
                    yPos = 40f
                }
                canvas.drawText(line, 50f, yPos, bodyPaint)
                yPos += 15f
            }
            yPos += 15f
        }

        pdfDocument.finishPage(page)
        val file = File(context.cacheDir, "${session.title}.pdf")
        pdfDocument.writeTo(java.io.FileOutputStream(file))
        pdfDocument.close()
        return file
    }

    fun shareFile(file: File) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = when {
                file.name.endsWith(".pdf") -> "application/pdf"
                file.name.endsWith(".json") -> "application/json"
                else -> "text/markdown"
            }
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Export Chat"))
    }

    private fun saveFile(name: String, content: String): File {
        val file = File(context.cacheDir, name)
        file.writeText(content)
        return file
    }
}

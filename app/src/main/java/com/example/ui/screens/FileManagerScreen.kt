package com.example.ui.screens

import android.Manifest
import android.content.*
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.example.ui.theme.*
import java.io.*
import java.text.SimpleDateFormat
import java.util.*
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

// ==================== DATA MODELS ====================

data class FileItem(
    val file: File,
    val name: String = file.name,
    val isDirectory: Boolean = file.isDirectory,
    val size: Long = if (file.isFile) file.length() else 0L,
    val lastModified: Long = file.lastModified(),
    val extension: String = file.extension.lowercase(),
    val isHidden: Boolean = file.name.startsWith(".")
)

enum class FileSortMode { NAME, DATE, SIZE, TYPE }

enum class CreateType { FILE, FOLDER }

data class StorageInfo(
    val path: String,
    val totalSpace: Long,
    val freeSpace: Long,
    val usedSpace: Long,
    val name: String
)

// ==================== FILE UTILS ====================

object FileUtils {
    fun formatSize(bytes: Long): String = when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> "${"%.1f".format(bytes / 1024.0)} KB"
        bytes < 1024 * 1024 * 1024 -> "${"%.1f".format(bytes / (1024.0 * 1024.0))} MB"
        else -> "${"%.2f".format(bytes / (1024.0 * 1024.0 * 1024.0))} GB"
    }

    fun formatDate(millis: Long): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
        return sdf.format(Date(millis))
    }

    fun getMimeType(extension: String): String = when (extension) {
        "jpg", "jpeg", "png", "gif", "bmp", "webp", "heic" -> "image"
        "mp4", "mkv", "avi", "mov", "flv", "wmv", "webm", "3gp" -> "video"
        "mp3", "wav", "aac", "flac", "ogg", "m4a", "wma" -> "audio"
        "pdf" -> "pdf"
        "doc", "docx" -> "word"
        "xls", "xlsx" -> "excel"
        "ppt", "pptx" -> "powerpoint"
        "zip", "rar", "7z", "tar", "gz", "bz2" -> "archive"
        "apk" -> "apk"
        "kt", "java", "py", "js", "ts", "html", "css", "json", "xml" -> "code"
        "txt", "md", "log" -> "text"
        else -> "other"
    }

    fun getFileIcon(extension: String, isDirectory: Boolean): androidx.compose.ui.graphics.vector.ImageVector = when {
        isDirectory -> Icons.Default.Folder
        getMimeType(extension) == "image" -> Icons.Default.Image
        getMimeType(extension) == "video" -> Icons.Default.VideoFile
        getMimeType(extension) == "audio" -> Icons.Default.MusicNote
        getMimeType(extension) == "pdf" -> Icons.Default.PictureAsPdf
        getMimeType(extension) == "archive" -> Icons.Default.Archive
        getMimeType(extension) == "apk" -> Icons.Default.Android
        getMimeType(extension) == "code" -> Icons.Default.Code
        else -> Icons.Default.Description
    }

    fun getIconColor(extension: String, isDirectory: Boolean): Color = when {
        isDirectory -> ZenSienna
        getMimeType(extension) == "image" -> Color(0xFF4FC3F7)
        getMimeType(extension) == "video" -> ZenRed
        getMimeType(extension) == "audio" -> Color(0xFFCE93D8)
        getMimeType(extension) == "pdf" -> Color(0xFFFF8A65)
        getMimeType(extension) == "archive" -> Color(0xFFFFD54F)
        getMimeType(extension) == "apk" -> Color(0xFF81C784)
        getMimeType(extension) == "code" -> Color(0xFF90CAF9)
        else -> ZenGold
    }

    fun copyFile(source: File, dest: File): Boolean = try {
        source.inputStream().use { input -> dest.outputStream().use { output -> input.copyTo(output) } }
        true
    } catch (e: Exception) { false }

    fun copyDirectory(source: File, dest: File): Boolean = try {
        if (!dest.exists()) dest.mkdirs()
        source.listFiles()?.forEach { child ->
            val childDest = File(dest, child.name)
            if (child.isDirectory) copyDirectory(child, childDest) else copyFile(child, childDest)
        }
        true
    } catch (e: Exception) { false }

    fun deleteRecursive(file: File): Boolean = try {
        if (file.isDirectory) file.listFiles()?.forEach { deleteRecursive(it) }
        file.delete()
    } catch (e: Exception) { false }

    fun createZip(sourceDir: File, outputFile: File): Boolean = try {
        ZipOutputStream(BufferedOutputStream(FileOutputStream(outputFile))).use { zos ->
            sourceDir.listFiles()?.forEach { file -> addToZip(file, file.name, zos) }
        }
        true
    } catch (e: Exception) { false }

    private fun addToZip(file: File, entryName: String, zos: ZipOutputStream) {
        if (file.isDirectory) {
            file.listFiles()?.forEach { child -> addToZip(child, "$entryName/${child.name}", zos) }
        } else {
            zos.putNextEntry(ZipEntry(entryName))
            file.inputStream().use { it.copyTo(zos) }
            zos.closeEntry()
        }
    }

    fun extractZip(zipFile: File, outputDir: File): Boolean = try {
        ZipFile(zipFile).use { zip ->
            zip.entries().asSequence().forEach { entry ->
                val outputFile = File(outputDir, entry.name)
                if (entry.isDirectory) outputFile.mkdirs()
                else {
                    outputFile.parentFile?.mkdirs()
                    zip.getInputStream(entry).use { input -> outputFile.outputStream().use { output -> input.copyTo(output) } }
                }
            }
        }
        true
    } catch (e: Exception) { false }
}

// ==================== FILE MANAGER SCREEN ====================

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun FileManagerScreen(
    isDark: Boolean,
    onMenuClick: () -> Unit
) {
    val context = LocalContext.current
    val internalStorage = Environment.getExternalStorageDirectory()

    var currentPath by remember { mutableStateOf(internalStorage.absolutePath) }
    var filesList by remember { mutableStateOf(listOf<FileItem>()) }
    var searchQuery by remember { mutableStateOf("") }
    var sortMode by remember { mutableStateOf(FileSortMode.NAME) }
    var isAscending by remember { mutableStateOf(true) }
    var showHidden by remember { mutableStateOf(false) }
    var selectedFiles by remember { mutableStateOf(setOf<String>()) }
    var isSelectionMode by remember { mutableStateOf(false) }
    var storageInfo by remember { mutableStateOf<List<StorageInfo>>(emptyList()) }

    var showCreateDialog by remember { mutableStateOf<CreateType?>(null) }
    var showRenameDialog by remember { mutableStateOf<FileItem?>(null) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showSortDialog by remember { mutableStateOf(false) }
    var showFileInfoDialog by remember { mutableStateOf<FileItem?>(null) }
    var showTextEditor by remember { mutableStateOf<FileItem?>(null) }
    var dialogInputText by remember { mutableStateOf("") }
    var editorContent by remember { mutableStateOf("") }
    var isWorking by remember { mutableStateOf(false) }
    var workMessage by remember { mutableStateOf("") }
    val permissionLauncher = rememberLauncherForActivityResult(
    ActivityResultContracts.RequestMultiplePermissions()
) { permissions ->
    if (permissions.values.all { it }) refreshFiles(currentPath)
    else Toast.makeText(context, "Storage permission required", Toast.LENGTH_LONG).show()
}

fun loadStorageInfo() {
    val internal = Environment.getExternalStorageDirectory()
    storageInfo = listOf(StorageInfo(
        path = internal.absolutePath,
        totalSpace = internal.totalSpace,
        freeSpace = internal.freeSpace,
        usedSpace = internal.totalSpace - internal.freeSpace,
        name = "Internal Storage"
    ))
}

fun refreshFiles(path: String) {
    val dir = File(path)
    if (!dir.exists() || !dir.isDirectory) return
    currentPath = path
    val allFiles = dir.listFiles()?.map { FileItem(it) } ?: emptyList()
    val filtered = allFiles.filter { file ->
        (showHidden || !file.isHidden) && (searchQuery.isBlank() || file.name.contains(searchQuery, ignoreCase = true))
    }
    val sorted = when (sortMode) {
        FileSortMode.NAME -> if (isAscending) filtered.sortedBy { it.name.lowercase() } else filtered.sortedByDescending { it.name.lowercase() }
        FileSortMode.DATE -> if (isAscending) filtered.sortedBy { it.lastModified } else filtered.sortedByDescending { it.lastModified }
        FileSortMode.SIZE -> if (isAscending) filtered.sortedBy { it.size } else filtered.sortedByDescending { it.size }
        FileSortMode.TYPE -> if (isAscending) filtered.sortedBy { it.extension } else filtered.sortedByDescending { it.extension }
    }
    filesList = sorted.sortedByDescending { it.isDirectory }
    selectedFiles = emptySet()
    isSelectionMode = false
}

fun navigateTo(path: String) = refreshFiles(path)

fun navigateUp() {
    val parent = File(currentPath).parentFile
    if (parent != null && parent.canRead()) refreshFiles(parent.absolutePath)
}

fun getMimeTypeForFile(extension: String): String = when (extension) {
    "jpg", "jpeg" -> "image/jpeg"; "png" -> "image/png"; "gif" -> "image/gif"; "webp" -> "image/webp"
    "mp4" -> "video/mp4"; "mkv" -> "video/x-matroska"; "mp3" -> "audio/mpeg"
    "pdf" -> "application/pdf"; "apk" -> "application/vnd.android.package-archive"
    "zip" -> "application/zip"; "txt" -> "text/plain"; "html" -> "text/html"
    else -> "*/*"
}

fun handleFileClick(fileItem: FileItem) {
    if (isSelectionMode) {
        val path = fileItem.file.absolutePath
        selectedFiles = if (path in selectedFiles) selectedFiles - path else selectedFiles + path
        return
    }
    if (fileItem.isDirectory) navigateTo(fileItem.file.absolutePath)
    else {
        val mime = FileUtils.getMimeType(fileItem.extension)
        when (mime) {
            "text", "code" -> {
                try { editorContent = fileItem.file.readText(); showTextEditor = fileItem }
                catch (e: Exception) { Toast.makeText(context, "Cannot read file", Toast.LENGTH_SHORT).show() }
            }
            else -> {
                try {
                    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", fileItem.file)
                    val intent = Intent(Intent.ACTION_VIEW).apply {
                        setDataAndType(uri, getMimeTypeForFile(fileItem.extension))
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    context.startActivity(intent)
                } catch (e: Exception) { showFileInfoDialog = fileItem }
            }
        }
    }
}

fun deleteSelected() {
    var success = true
    selectedFiles.forEach { path -> if (!FileUtils.deleteRecursive(File(path))) success = false }
    Toast.makeText(context, if (success) "Deleted successfully" else "Some files couldn't be deleted", Toast.LENGTH_SHORT).show()
    refreshFiles(currentPath)
}

fun compressSelected() {
    val outputFile = File(File(currentPath), "archive_${System.currentTimeMillis()}.zip")
    isWorking = true; workMessage = "Compressing..."
    val success = FileUtils.createZip(File(currentPath), outputFile)
    isWorking = false
    Toast.makeText(context, if (success) "Archive created" else "Compression failed", Toast.LENGTH_SHORT).show()
    if (success) refreshFiles(currentPath)
}

LaunchedEffect(Unit) {
    val permissions = mutableListOf<String>()
    if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.Q) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED)
            permissions.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED)
            permissions.add(Manifest.permission.READ_EXTERNAL_STORAGE)
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_VIDEO) != PackageManager.PERMISSION_GRANTED)
            permissions.add(Manifest.permission.READ_MEDIA_VIDEO)
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_AUDIO) != PackageManager.PERMISSION_GRANTED)
            permissions.add(Manifest.permission.READ_MEDIA_AUDIO)
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_IMAGES) != PackageManager.PERMISSION_GRANTED)
            permissions.add(Manifest.permission.READ_MEDIA_IMAGES)
    }
    if (permissions.isNotEmpty()) permissionLauncher.launch(permissions.toTypedArray())
    refreshFiles(currentPath)
    loadStorageInfo()
}val permissionLauncher = rememberLauncherForActivityResult(
    ActivityResultContracts.RequestMultiplePermissions()
) { permissions ->
    if (permissions.values.all { it }) refreshFiles(currentPath)
    else Toast.makeText(context, "Storage permission required", Toast.LENGTH_LONG).show()
}

fun loadStorageInfo() {
    val internal = Environment.getExternalStorageDirectory()
    storageInfo = listOf(StorageInfo(
        path = internal.absolutePath,
        totalSpace = internal.totalSpace,
        freeSpace = internal.freeSpace,
        usedSpace = internal.totalSpace - internal.freeSpace,
        name = "Internal Storage"
    ))
}

fun refreshFiles(path: String) {
    val dir = File(path)
    if (!dir.exists() || !dir.isDirectory) return
    currentPath = path
    val allFiles = dir.listFiles()?.map { FileItem(it) } ?: emptyList()
    val filtered = allFiles.filter { file ->
        (showHidden || !file.isHidden) && (searchQuery.isBlank() || file.name.contains(searchQuery, ignoreCase = true))
    }
    val sorted = when (sortMode) {
        FileSortMode.NAME -> if (isAscending) filtered.sortedBy { it.name.lowercase() } else filtered.sortedByDescending { it.name.lowercase() }
        FileSortMode.DATE -> if (isAscending) filtered.sortedBy { it.lastModified } else filtered.sortedByDescending { it.lastModified }
        FileSortMode.SIZE -> if (isAscending) filtered.sortedBy { it.size } else filtered.sortedByDescending { it.size }
        FileSortMode.TYPE -> if (isAscending) filtered.sortedBy { it.extension } else filtered.sortedByDescending { it.extension }
    }
    filesList = sorted.sortedByDescending { it.isDirectory }
    selectedFiles = emptySet()
    isSelectionMode = false
}

fun navigateTo(path: String) = refreshFiles(path)

fun navigateUp() {
    val parent = File(currentPath).parentFile
    if (parent != null && parent.canRead()) refreshFiles(parent.absolutePath)
}

fun getMimeTypeForFile(extension: String): String = when (extension) {
    "jpg", "jpeg" -> "image/jpeg"; "png" -> "image/png"; "gif" -> "image/gif"; "webp" -> "image/webp"
    "mp4" -> "video/mp4"; "mkv" -> "video/x-matroska"; "mp3" -> "audio/mpeg"
    "pdf" -> "application/pdf"; "apk" -> "application/vnd.android.package-archive"
    "zip" -> "application/zip"; "txt" -> "text/plain"; "html" -> "text/html"
    else -> "*/*"
}

fun handleFileClick(fileItem: FileItem) {
    if (isSelectionMode) {
        val path = fileItem.file.absolutePath
        selectedFiles = if (path in selectedFiles) selectedFiles - path else selectedFiles + path
        return
    }
    if (fileItem.isDirectory) navigateTo(fileItem.file.absolutePath)
    else {
        val mime = FileUtils.getMimeType(fileItem.extension)
        when (mime) {
            "text", "code" -> {
                try { editorContent = fileItem.file.readText(); showTextEditor = fileItem }
                catch (e: Exception) { Toast.makeText(context, "Cannot read file", Toast.LENGTH_SHORT).show() }
            }
            else -> {
                try {
                    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", fileItem.file)
                    val intent = Intent(Intent.ACTION_VIEW).apply {
                        setDataAndType(uri, getMimeTypeForFile(fileItem.extension))
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    context.startActivity(intent)
                } catch (e: Exception) { showFileInfoDialog = fileItem }
            }
        }
    }
}

fun deleteSelected() {
    var success = true
    selectedFiles.forEach { path -> if (!FileUtils.deleteRecursive(File(path))) success = false }
    Toast.makeText(context, if (success) "Deleted successfully" else "Some files couldn't be deleted", Toast.LENGTH_SHORT).show()
    refreshFiles(currentPath)
}

fun compressSelected() {
    val outputFile = File(File(currentPath), "archive_${System.currentTimeMillis()}.zip")
    isWorking = true; workMessage = "Compressing..."
    val success = FileUtils.createZip(File(currentPath), outputFile)
    isWorking = false
    Toast.makeText(context, if (success) "Archive created" else "Compression failed", Toast.LENGTH_SHORT).show()
    if (success) refreshFiles(currentPath)
}

LaunchedEffect(Unit) {
    val permissions = mutableListOf<String>()
    if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.Q) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED)
            permissions.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED)
            permissions.add(Manifest.permission.READ_EXTERNAL_STORAGE)
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_VIDEO) != PackageManager.PERMISSION_GRANTED)
            permissions.add(Manifest.permission.READ_MEDIA_VIDEO)
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_AUDIO) != PackageManager.PERMISSION_GRANTED)
            permissions.add(Manifest.permission.READ_MEDIA_AUDIO)
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_IMAGES) != PackageManager.PERMISSION_GRANTED)
            permissions.add(Manifest.permission.READ_MEDIA_IMAGES)
    }
    if (permissions.isNotEmpty()) permissionLauncher.launch(permissions.toTypedArray())
    refreshFiles(currentPath)
    loadStorageInfo()
}


    // ==================== DIALOGS ====================

    showCreateDialog?.let { type ->
        AlertDialog(
            onDismissRequest = { showCreateDialog = null; dialogInputText = "" },
            title = { Text(if (type == CreateType.FILE) "New File" else "New Folder", fontFamily = FontFamily.Serif, color = ZenGold) },
            text = { OutlinedTextField(value = dialogInputText, onValueChange = { dialogInputText = it }, label = { Text(if (type == CreateType.FILE) "File name" else "Folder name") }, modifier = Modifier.fillMaxWidth(), singleLine = true) },
            confirmButton = {
                Button(onClick = {
                    if (dialogInputText.isNotBlank()) {
                        val newFile = File(currentPath, dialogInputText)
                        val success = if (type == CreateType.FILE) try { newFile.createNewFile() } catch (e: Exception) { false } else newFile.mkdirs()
                        if (success) refreshFiles(currentPath) else Toast.makeText(context, "Creation failed", Toast.LENGTH_SHORT).show()
                    }
                    showCreateDialog = null; dialogInputText = ""
                }, colors = ButtonDefaults.buttonColors(containerColor = ZenGold)) { Text("Create", color = Color.Black) }
            },
            dismissButton = { TextButton(onClick = { showCreateDialog = null }) { Text("Cancel", color = ZenGold) } },
            containerColor = if (isDark) Color(0xFF14131A) else Color(0xFFF7F4EE)
        )
    }

    showRenameDialog?.let { fileItem ->
        LaunchedEffect(fileItem) { dialogInputText = fileItem.name }
        AlertDialog(
            onDismissRequest = { showRenameDialog = null },
            title = { Text("Rename", fontFamily = FontFamily.Serif, color = ZenGold) },
            text = { OutlinedTextField(value = dialogInputText, onValueChange = { dialogInputText = it }, label = { Text("New name") }, modifier = Modifier.fillMaxWidth(), singleLine = true) },
            confirmButton = {
                Button(onClick = {
                    if (dialogInputText.isNotBlank()) {
                        val newFile = File(fileItem.file.parent, dialogInputText)
                        if (fileItem.file.renameTo(newFile)) refreshFiles(currentPath) else Toast.makeText(context, "Rename failed", Toast.LENGTH_SHORT).show()
                    }
                    showRenameDialog = null
                }, colors = ButtonDefaults.buttonColors(containerColor = ZenGold)) { Text("Rename", color = Color.Black) }
            },
            dismissButton = { TextButton(onClick = { showRenameDialog = null }) { Text("Cancel", color = ZenGold) } },
            containerColor = if (isDark) Color(0xFF14131A) else Color(0xFFF7F4EE)
        )
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete ${selectedFiles.size} item(s)?", color = ZenRed) },
            text = { Text("This cannot be undone.") },
            confirmButton = { Button(onClick = { deleteSelected(); showDeleteDialog = false }, colors = ButtonDefaults.buttonColors(containerColor = ZenRed)) { Text("Delete", color = Color.White) } },
            dismissButton = { TextButton(onClick = { showDeleteDialog = false }) { Text("Cancel", color = ZenGold) } },
            containerColor = if (isDark) Color(0xFF14131A) else Color(0xFFF7F4EE)
        )
    }

    if (showSortDialog) {
        AlertDialog(
            onDismissRequest = { showSortDialog = false },
            title = { Text("Sort by", fontFamily = FontFamily.Serif, color = ZenGold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    listOf(FileSortMode.NAME to "Name", FileSortMode.DATE to "Date", FileSortMode.SIZE to "Size", FileSortMode.TYPE to "Type").forEach { (mode, label) ->
                        Row(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable {
                            if (sortMode == mode) isAscending = !isAscending else { sortMode = mode; isAscending = true }
                            refreshFiles(currentPath); showSortDialog = false
                        }.padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(label, color = YinText)
                            if (sortMode == mode) Icon(if (isAscending) Icons.Default.ArrowUpward else Icons.Default.ArrowDownward, null, tint = ZenGold, modifier = Modifier.size(18.dp))
                        }
                    }
                    Divider(color = Color(0xFF333340))
                    Row(modifier = Modifier.fillMaxWidth().clickable { showHidden = !showHidden; refreshFiles(currentPath) }.padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Show hidden files", color = YinText)
                        Switch(checked = showHidden, onCheckedChange = { showHidden = it; refreshFiles(currentPath) }, colors = SwitchDefaults.colors(checkedThumbColor = ZenGold, checkedTrackColor = ZenGold.copy(alpha = 0.5f)))
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showSortDialog = false }) { Text("Close", color = ZenGold) } },
            containerColor = if (isDark) Color(0xFF14131A) else Color(0xFFF7F4EE)
        )
    }

    showFileInfoDialog?.let { fileItem ->
        AlertDialog(
            onDismissRequest = { showFileInfoDialog = null },
            title = { Text("File Info", fontFamily = FontFamily.Serif, color = ZenGold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    InfoRow("Name", fileItem.name)
                    InfoRow("Path", fileItem.file.absolutePath)
                    InfoRow("Type", if (fileItem.isDirectory) "Folder" else fileItem.extension.uppercase())
                    InfoRow("Size", if (fileItem.isDirectory) "${fileItem.file.listFiles()?.size ?: 0} items" else FileUtils.formatSize(fileItem.size))
                    InfoRow("Modified", FileUtils.formatDate(fileItem.lastModified))
                    InfoRow("Permissions", (if (fileItem.file.canRead()) "Read " else "") + (if (fileItem.file.canWrite()) "Write " else "") + (if (fileItem.file.canExecute()) "Execute" else ""))
                }
            },
            confirmButton = { TextButton(onClick = { showFileInfoDialog = null }) { Text("Close", color = ZenGold) } },
            containerColor = if (isDark) Color(0xFF14131A) else Color(0xFFF7F4EE)
        )
    }

    if (showTextEditor != null) {
        val fileItem = showTextEditor!!
        AlertDialog(
            onDismissRequest = { showTextEditor = null },
            title = { Text("Editing: ${fileItem.name}", color = ZenGold, maxLines = 1) },
            text = {
                Column(modifier = Modifier.fillMaxWidth().heightIn(min = 300.dp)) {
                    OutlinedTextField(
                        value = editorContent, onValueChange = { editorContent = it },
                        modifier = Modifier.fillMaxWidth().weight(1f),
                        textStyle = androidx.compose.ui.text.TextStyle(fontFamily = FontFamily.Monospace, fontSize = 13.sp, color = YinText),
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = ZenGold, unfocusedBorderColor = Color(0xFF333340))
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    try { fileItem.file.writeText(editorContent); Toast.makeText(context, "Saved!", Toast.LENGTH_SHORT).show() }
                    catch (e: Exception) { Toast.makeText(context, "Save failed", Toast.LENGTH_SHORT).show() }
                    showTextEditor = null; refreshFiles(currentPath)
                }, colors = ButtonDefaults.buttonColors(containerColor = ZenGold)) { Text("Save", color = Color.Black) }
            },
            dismissButton = { TextButton(onClick = { showTextEditor = null }) { Text("Close", color = ZenRed) } },
            containerColor = if (isDark) Color(0xFF14131A) else Color(0xFFF7F4EE)
        )
    }

    if (isWorking) {
        AlertDialog(
            onDismissRequest = { },
            title = { Text("Working", color = ZenGold) },
            text = { Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) { CircularProgressIndicator(modifier = Modifier.size(24.dp), color = ZenGold); Text(workMessage, color = YinText) } },
            confirmButton = { },
            containerColor = if (isDark) Color(0xFF14131A) else Color(0xFFF7F4EE)
        )
    }

    // ==================== MAIN UI ====================
    Scaffold(
        topBar = {
            Column(modifier = Modifier.fillMaxWidth().background(if (isDark) YinBlack else YangWhite).statusBarsPadding()) {
                if (isSelectionMode) {
                    Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { isSelectionMode = false; selectedFiles = emptySet() }) { Icon(Icons.Default.Close, "Cancel", tint = YinText) }
                        Text("${selectedFiles.size} selected", color = YinText, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                        IconButton(onClick = { showDeleteDialog = true }) { Icon(Icons.Default.Delete, "Delete", tint = ZenRed) }
                        IconButton(onClick = { compressSelected() }) { Icon(Icons.Default.Archive, "Compress", tint = ZenGold) }
                        IconButton(onClick = {
                            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            cm.setPrimaryClip(ClipData.newPlainText("paths", selectedFiles.joinToString("\n")))
                            isSelectionMode = false; selectedFiles = emptySet()
                            Toast.makeText(context, "Paths copied", Toast.LENGTH_SHORT).show()
                        }) { Icon(Icons.Default.ContentCopy, "Copy", tint = ZenBlue) }
                    }
                } else {
                    Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = onMenuClick) { Icon(Icons.Default.Menu, "Menu", tint = if (isDark) Color.White else Color.Black) }
                            Text("ZEN FILE EXPLORER", fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold, color = if (isDark) ZenGold else Color(0xFF9E7E1D), style = MaterialTheme.typography.titleMedium)
                        }
                        Row {
                            IconButton(onClick = { showCreateDialog = CreateType.FILE; dialogInputText = "" }) { Icon(Icons.Default.NoteAdd, "New File", tint = ZenGold, modifier = Modifier.size(22.dp)) }
                            IconButton(onClick = { showCreateDialog = CreateType.FOLDER; dialogInputText = "" }) { Icon(Icons.Default.CreateNewFolder, "New Folder", tint = ZenSienna, modifier = Modifier.size(22.dp)) }
                            IconButton(onClick = { showSortDialog = true }) { Icon(Icons.Default.Sort, "Sort", tint = YinTextSecondary, modifier = Modifier.size(22.dp)) }
                        }
                    }
                }
                Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { navigateUp() }, enabled = currentPath != "/" && currentPath != internalStorage.absolutePath) { Icon(Icons.Default.ArrowUpward, "Up", tint = YinText, modifier = Modifier.size(20.dp)) }
                    Text(currentPath.replace(internalStorage.absolutePath, "Internal"), color = YinTextSecondary, fontSize = 12.sp, fontFamily = FontFamily.Monospace, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                }
                OutlinedTextField(
                    value = searchQuery, onValueChange = { searchQuery = it; refreshFiles(currentPath) },
                    placeholder = { Text("Search files...", fontSize = 12.sp, color = YinTextSecondary.copy(alpha = 0.5f)) },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp).height(44.dp),
                    leadingIcon = { Icon(Icons.Default.Search, null, tint = Color.Gray, modifier = Modifier.size(18.dp)) },
                    trailingIcon = { if (searchQuery.isNotEmpty()) IconButton(onClick = { searchQuery = ""; refreshFiles(currentPath) }) { Icon(Icons.Default.Close, "Clear", tint = Color.Gray, modifier = Modifier.size(18.dp)) } },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = ZenGold, unfocusedBorderColor = Color(0xFF333340), focusedContainerColor = Color(0xFF1A1A22), unfocusedContainerColor = Color(0xFF1A1A22)),
                    shape = RoundedCornerShape(10.dp)
                )
            }
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding).background(if (isDark) Color(0xFF070709) else Color(0xFFF1F0EC))) {
            if (filesList.isEmpty() && !isWorking) {
                Column(modifier = Modifier.fillMaxSize().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                    Icon(Icons.Default.FolderOpen, null, modifier = Modifier.size(72.dp), tint = Color.Gray.copy(alpha = 0.4f))
                    Spacer(Modifier.height(16.dp))
                    Text("Empty directory", color = Color.Gray, fontFamily = FontFamily.Serif, fontSize = 16.sp)
                    Text("Create a new file or folder to begin", color = Color.Gray.copy(alpha = 0.6f), fontSize = 12.sp)
                }
            } else {
                Column(modifier = Modifier.fillMaxSize()) {
                    if (storageInfo.isNotEmpty() && currentPath == internalStorage.absolutePath) {
                        Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp), colors = CardDefaults.cardColors(containerColor = YinCardBg), shape = RoundedCornerShape(10.dp)) {
                            Row(modifier = Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                                storageInfo.forEach { info ->
                                    val usedPercent = if (info.totalSpace > 0) info.usedSpace.toFloat() / info.totalSpace else 0f
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text(info.name, color = YinTextSecondary, fontSize = 10.sp)
                                        Text(FileUtils.formatSize(info.freeSpace), color = ZenGold, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                        Text("free", color = YinTextSecondary, fontSize = 9.sp)
                                        LinearProgressIndicator(progress = { usedPercent }, modifier = Modifier.width(60.dp).height(3.dp).clip(CircleShape), color = if (usedPercent > 0.9f) ZenRed else ZenGold, trackColor = Color(0xFF222228))
                                    }
                                }
                            }
                        }
                    }
                    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        if (currentPath == internalStorage.absolutePath) {
                            item { ShortcutRow(Icons.Default.Image, "Images", Color(0xFF4FC3F7)) { File(internalStorage, "DCIM").let { if (it.exists()) navigateTo(it.absolutePath) } } }
                            item { ShortcutRow(Icons.Default.VideoFile, "Videos", ZenRed) { File(internalStorage, "Movies").let { if (it.exists()) navigateTo(it.absolutePath) } } }
                            item { ShortcutRow(Icons.Default.MusicNote, "Audio", Color(0xFFCE93D8)) { File(internalStorage, "Music").let { if (it.exists()) navigateTo(it.absolutePath) } } }
                            item { ShortcutRow(Icons.Default.Download, "Downloads", ZenGold) { File(internalStorage, "Download").let { if (it.exists()) navigateTo(it.absolutePath) } } }
                            item { ShortcutRow(Icons.Default.Archive, "Archives", Color(0xFFFFD54F)) { Toast.makeText(context, "Scanning...", Toast.LENGTH_SHORT).show() } }
                            item { Divider(color = Color(0xFF222228), modifier = Modifier.padding(vertical = 6.dp)) }
                        }
                        items(filesList, key = { it.file.absolutePath }) { fileItem ->
                            val isSelected = fileItem.file.absolutePath in selectedFiles
                            Card(
                                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(if (isSelected) ZenGold.copy(alpha = 0.15f) else Color.Transparent)
                                    .combinedClickable(onClick = { handleFileClick(fileItem) }, onLongClick = { if (!isSelectionMode) { isSelectionMode = true; selectedFiles = setOf(fileItem.file.absolutePath) } }),
                                colors = CardDefaults.cardColors(containerColor = Color.Transparent)
                            ) {
                                Row(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                                    if (isSelectionMode) { Checkbox(checked = isSelected, onCheckedChange = { selectedFiles = if (it) selectedFiles + fileItem.file.absolutePath else selectedFiles - fileItem.file.absolutePath }, colors = CheckboxDefaults.colors(checkedColor = ZenGold), modifier = Modifier.size(20.dp)); Spacer(Modifier.width(8.dp)) }
                                    Box(modifier = Modifier.size(36.dp).clip(RoundedCornerShape(8.dp)).background(FileUtils.getIconColor(fileItem.extension, fileItem.isDirectory).copy(alpha = 0.15f)), contentAlignment = Alignment.Center) {
                                        Icon(FileUtils.getFileIcon(fileItem.extension, fileItem.isDirectory), null, tint = FileUtils.getIconColor(fileItem.extension, fileItem.isDirectory), modifier = Modifier.size(20.dp))
                                    }
                                    Spacer(Modifier.width(10.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(fileItem.name, color = YinText, fontWeight = FontWeight.Medium, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        Text("${if (fileItem.isDirectory) "Folder" else FileUtils.formatSize(fileItem.size)} • ${FileUtils.formatDate(fileItem.lastModified)}", color = YinTextSecondary, fontSize = 11.sp, maxLines = 1)
                                    }
                                    if (!isSelectionMode) {
                                        IconButton(onClick = { showRenameDialog = fileItem }, modifier = Modifier.size(28.dp)) { Icon(Icons.Default.Edit, "Rename", tint = YinTextSecondary.copy(alpha = 0.6f), modifier = Modifier.size(16.dp)) }
                                        IconButton(onClick = { showFileInfoDialog = fileItem }, modifier = Modifier.size(28.dp)) { Icon(Icons.Default.Info, "Info", tint = YinTextSecondary.copy(alpha = 0.6f), modifier = Modifier.size(16.dp)) }
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
private fun ShortcutRow(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, color: Color, onClick: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable(onClick).padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(modifier = Modifier.size(32.dp).clip(RoundedCornerShape(8.dp)).background(color.copy(alpha = 0.15f)), contentAlignment = Alignment.Center) { Icon(icon, null, tint = color, modifier = Modifier.size(18.dp)) }
        Text(label, color = YinText, fontSize = 14.sp)
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = YinTextSecondary, fontSize = 12.sp, fontWeight = FontWeight.Medium)
        Text(value, color = YinText, fontSize = 12.sp, maxLines = 3, modifier = Modifier.widthIn(max = 200.dp))
    }
}

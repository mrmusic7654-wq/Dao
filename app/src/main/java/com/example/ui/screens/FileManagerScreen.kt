package com.example.ui.screens

import android.Manifest
import android.content.*
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.ExperimentalFoundationApi
import com.example.ui.automation.AutomationEventBus
import com.example.ui.theme.*
import kotlinx.coroutines.*
import kotlinx.coroutines.delay
import java.io.*
import java.text.SimpleDateFormat
import java.util.*
import java.util.LinkedList
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
    val isHidden: Boolean = file.name.startsWith("."),
    val containsImages: Boolean = false,
    val folderSize: Long? = null // For calculated folder sizes
)

enum class FileSortMode { NAME, DATE, SIZE, TYPE }
enum class CreateType { FILE, FOLDER }
enum class ViewMode { SINGLE_PANE, DUAL_PANE }

data class StorageInfo(
    val path: String,
    val totalSpace: Long,
    val freeSpace: Long,
    val usedSpace: Long,
    val name: String
)

data class ApkInfo(
    val packageName: String,
    val versionName: String,
    val versionCode: Long,
    val appName: String
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
        "jpg", "jpeg", "png", "gif", "bmp", "webp", "heic", "tiff", "ico", "svg" -> "image"
        "mp4", "mkv", "avi", "mov", "flv", "wmv", "webm", "3gp" -> "video"
        "mp3", "wav", "aac", "flac", "ogg", "m4a", "wma" -> "audio"
        "pdf" -> "pdf"
        "doc", "docx" -> "word"
        "xls", "xlsx" -> "excel"
        "ppt", "pptx" -> "powerpoint"
        "zip", "rar", "7z", "tar", "gz", "bz2" -> "archive"
        "apk" -> "apk"
        "kt", "java", "py", "js", "ts", "html", "css", "json", "xml", "yaml", "yml" -> "code"
        "txt", "md", "log", "cfg", "ini" -> "text"
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

    fun isImageFile(name: String): Boolean {
        val ext = name.substringAfterLast('.', "").lowercase()
        return ext in listOf("jpg", "jpeg", "png", "gif", "bmp", "webp", "heic", "tiff", "ico")
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

    fun listImagesInZip(zipFile: File): List<Pair<String, Bitmap>> {
        val images = mutableListOf<Pair<String, Bitmap>>()
        try {
            ZipFile(zipFile).use { zip ->
                zip.entries().asSequence().forEach { entry ->
                    if (!entry.isDirectory && isImageFile(entry.name)) {
                        try {
                            zip.getInputStream(entry).use { input ->
                                val bitmap = BitmapFactory.decodeStream(input)
                                if (bitmap != null) images.add(entry.name to bitmap)
                            }
                        } catch (_: Exception) {}
                    }
                }
            }
        } catch (_: Exception) {}
        return images
    }

    fun hasImagesInZip(zipFile: File): Boolean = try {
        ZipFile(zipFile).use { zip -> zip.entries().asSequence().any { !it.isDirectory && isImageFile(it.name) } }
    } catch (_: Exception) { false }

    fun readHexDump(file: File, maxBytes: Int = 512): String {
        return try {
            val bytes = file.inputStream().use { it.readBytes() }.take(maxBytes).toByteArray()
            bytes.joinToString(" ") { String.format("%02X", it) }
        } catch (e: Exception) { "Cannot read file" }
    }

    fun readTextFile(file: File, maxBytes: Int = 50000): String {
        return try {
            file.inputStream().use { it.readBytes().take(maxBytes).toByteArray().toString(Charsets.UTF_8) }
        } catch (e: Exception) { "Cannot read file: ${e.message}" }
    }

    fun calculateChecksum(file: File, algorithm: String = "MD5"): String {
        return try {
            val digest = java.security.MessageDigest.getInstance(algorithm)
            file.inputStream().use { input ->
                val buffer = ByteArray(8192)
                var bytesRead: Int
                while (input.read(buffer).also { bytesRead = it } != -1) {
                    digest.update(buffer, 0, bytesRead)
                }
            }
            digest.digest().joinToString("") { "%02x".format(it) }
        } catch (e: Exception) { "Error: ${e.message}" }
    }

    fun verifyChecksum(file: File, expectedHash: String, algorithm: String = "MD5"): Boolean {
        val actual = calculateChecksum(file, algorithm)
        return actual.equals(expectedHash, ignoreCase = true)
    }

    fun calculateFolderSize(folder: File): Long {
        var totalSize = 0L
        val queue = LinkedList<File>()
        queue.add(folder)
        while (queue.isNotEmpty()) {
            val current = queue.poll()
            if (current.isDirectory) {
                current.listFiles()?.forEach { queue.add(it) }
            } else {
                totalSize += current.length()
            }
        }
        return totalSize
    }

    fun getApkInfo(context: Context, apkPath: String): ApkInfo? {
        return try {
            val pm = context.packageManager
            val packageInfo = pm.getPackageArchiveInfo(apkPath, android.content.pm.PackageManager.GET_ACTIVITIES)
            packageInfo?.let { pkgInfo ->
                val appInfo = pkgInfo.applicationInfo
                appInfo?.let { info ->
                    info.sourceDir = apkPath
                    info.publicSourceDir = apkPath
                    ApkInfo(
                        packageName = pkgInfo.packageName,
                        versionName = pkgInfo.versionName ?: "Unknown",
                        versionCode = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) pkgInfo.longVersionCode else pkgInfo.versionCode.toLong(),
                        appName = pm.getApplicationLabel(info).toString(),
                        icon = pm.getApplicationIcon(info)
                    )
                } ?: ApkInfo(packageName = "unknown", versionName = "?", versionCode = 0, appName = "Unknown", icon = null)
            }
        } catch (e: Exception) { null }
    }
}

// ==================== FAVORITES MANAGER ====================

object FavoritesManager {
    private val favorites = mutableSetOf<String>()

    fun getFavorites(): List<String> = favorites.toList()

    fun addFavorite(path: String) {
        favorites.add(path)
    }

    fun removeFavorite(path: String) {
        favorites.remove(path)
    }

    fun isFavorite(path: String): Boolean = path in favorites

    fun toggleFavorite(path: String) {
        if (isFavorite(path)) removeFavorite(path) else addFavorite(path)
    }
}

// ==================== RECENT FILES ====================

object RecentFiles {
    private val recentFiles = mutableListOf<String>()
    private const val maxRecent = 20

    fun addRecent(path: String) {
        recentFiles.remove(path)
        recentFiles.add(0, path)
        if (recentFiles.size > maxRecent) recentFiles.removeAt(maxRecent)
    }

    fun getRecent(): List<String> = recentFiles.toList()

    fun clearRecent() { recentFiles.clear() }
}

// ==================== FILE MANAGER SCREEN ====================

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun FileManagerScreen(isDark: Boolean, onMenuClick: () -> Unit) {
    val context = LocalContext.current
    val internalStorage = Environment.getExternalStorageDirectory()

    // View mode: single or dual pane (X-plore style)
    var viewMode by remember { mutableStateOf(ViewMode.SINGLE_PANE) }

    // Left pane state
    var leftPath by remember { mutableStateOf(internalStorage.absolutePath) }
    var leftFiles by remember { mutableStateOf(listOf<FileItem>()) }

    // Right pane state
    var rightPath by remember { mutableStateOf(internalStorage.absolutePath) }
    var rightFiles by remember { mutableStateOf(listOf<FileItem>()) }

    // Active pane for operations
    var activePane by remember { mutableStateOf<Int>(0) } // 0=left, 1=right

    // Common state
    var searchQuery by remember { mutableStateOf("") }
    var sortMode by remember { mutableStateOf(FileSortMode.NAME) }
    var isAscending by remember { mutableStateOf(true) }
    var showHidden by remember { mutableStateOf(false) }
    var selectedFiles by remember { mutableStateOf(setOf<String>()) }
    var isSelectionMode by remember { mutableStateOf(false) }
    var storageInfo by remember { mutableStateOf<List<StorageInfo>>(emptyList()) }
    var clipboardPaths by remember { mutableStateOf<List<String>>(emptyList()) }
    var clipboardMode by remember { mutableStateOf<String>("") } // "copy" or "move"

    // Dialogs
    var showCreateDialog by remember { mutableStateOf<CreateType?>(null) }
    var showRenameDialog by remember { mutableStateOf<FileItem?>(null) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showSortDialog by remember { mutableStateOf(false) }
    var showFileInfoDialog by remember { mutableStateOf<FileItem?>(null) }
    var showTextEditor by remember { mutableStateOf<FileItem?>(null) }
    var showHexViewer by remember { mutableStateOf<FileItem?>(null) }
    var showSlideshow by remember { mutableStateOf(false) }
    var dialogInputText by remember { mutableStateOf("") }
    var editorContent by remember { mutableStateOf("") }
    var hexContent by remember { mutableStateOf("") }
    var isWorking by remember { mutableStateOf(false) }
    var workMessage by remember { mutableStateOf("") }

    // Slideshow
    var slideshowImages by remember { mutableStateOf<List<Pair<String, Bitmap>>>(emptyList()) }
    var slideshowIndex by remember { mutableIntStateOf(0) }
    var slideshowFileName by remember { mutableStateOf("") }

    // ===== FUNCTIONS =====

    fun getCurrentPath() = if (activePane == 0) leftPath else rightPath
    fun getCurrentFiles() = if (activePane == 0) leftFiles else rightFiles

    fun loadStorageInfo() {
        val internal = Environment.getExternalStorageDirectory()
        storageInfo = listOf(StorageInfo(
            path = internal.absolutePath, totalSpace = internal.totalSpace,
            freeSpace = internal.freeSpace, usedSpace = internal.totalSpace - internal.freeSpace,
            name = "Internal Storage"
        ))
    }

    fun refreshFiles(path: String, pane: Int = activePane) {
        val dir = File(path)
        if (!dir.exists()) {
            if (pane == 0) leftFiles = emptyList() else rightFiles = emptyList()
            if (pane == 0) leftPath = path else rightPath = path
            return
        }
        if (!dir.canRead()) {
            Toast.makeText(context, "Cannot access this directory. Grant 'All Files Access' permission.", Toast.LENGTH_LONG).show()
            if (pane == 0) leftFiles = emptyList() else rightFiles = emptyList()
            if (pane == 0) leftPath = path else rightPath = path
            return
        }

        val fileArray = dir.listFiles()
        if (fileArray == null) {
            if (pane == 0) leftFiles = emptyList() else rightFiles = emptyList()
            if (pane == 0) leftPath = path else rightPath = path
            return
        }

        val allFiles = fileArray.map { file ->
            FileItem(
                file = file,
                containsImages = if (file.extension.lowercase() in listOf("zip", "rar", "7z")) {
                    try { FileUtils.hasImagesInZip(file) } catch (e: Exception) { false }
                } else false
            )
        }

        val filtered = allFiles.filter { file ->
            (showHidden || !file.isHidden) &&
            (searchQuery.isBlank() || file.name.contains(searchQuery, ignoreCase = true))
        }

        val sorted = when (sortMode) {
            FileSortMode.NAME -> if (isAscending) filtered.sortedBy { it.name.lowercase() } else filtered.sortedByDescending { it.name.lowercase() }
            FileSortMode.DATE -> if (isAscending) filtered.sortedBy { it.lastModified } else filtered.sortedByDescending { it.lastModified }
            FileSortMode.SIZE -> if (isAscending) filtered.sortedBy { it.size } else filtered.sortedByDescending { it.size }
            FileSortMode.TYPE -> if (isAscending) filtered.sortedBy { it.extension } else filtered.sortedByDescending { it.extension }
        }

        val result = sorted.sortedByDescending { it.isDirectory }
        if (pane == 0) { leftPath = path; leftFiles = result }
        else { rightPath = path; rightFiles = result }

        selectedFiles = emptySet()
        isSelectionMode = false
    }

    fun navigateUp(pane: Int = activePane) {
        val currentPath = if (pane == 0) leftPath else rightPath
        val parent = File(currentPath).parentFile
        if (parent != null && parent.canRead()) refreshFiles(parent.absolutePath, pane)
    }

    fun getMimeTypeForFile(ext: String) = when (ext) {
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
        if (fileItem.isDirectory) {
            refreshFiles(fileItem.file.absolutePath)
        } else {
            val mime = FileUtils.getMimeType(fileItem.extension)
            when (mime) {
                "text", "code" -> {
                    try { editorContent = fileItem.file.readText(); showTextEditor = fileItem }
                    catch (e: Exception) { Toast.makeText(context, "Cannot read file", Toast.LENGTH_SHORT).show() }
                }
                "archive" -> {
                    if (fileItem.containsImages && fileItem.extension == "zip") {
                        CoroutineScope(Dispatchers.IO).launch {
                            val images = FileUtils.listImagesInZip(fileItem.file)
                            withContext(Dispatchers.Main) {
                                if (images.isNotEmpty()) {
                                    slideshowImages = images; slideshowIndex = 0
                                    slideshowFileName = fileItem.name; showSlideshow = true
                                } else showFileInfoDialog = fileItem
                            }
                        }
                    } else showFileInfoDialog = fileItem
                }
                else -> {
                    try {
                        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", fileItem.file)
                        context.startActivity(Intent(Intent.ACTION_VIEW).apply {
                            setDataAndType(uri, getMimeTypeForFile(fileItem.extension))
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        })
                    } catch (e: Exception) { showFileInfoDialog = fileItem }
                }
            }
        }
    }

    fun deleteSelected() {
        var success = true
        selectedFiles.forEach { if (!FileUtils.deleteRecursive(File(it))) success = false }
        Toast.makeText(context, if (success) "Deleted" else "Some files couldn't be deleted", Toast.LENGTH_SHORT).show()
        refreshFiles(getCurrentPath())
    }

    fun copyToClipboard() {
        clipboardPaths = selectedFiles.toList()
        clipboardMode = "copy"
        Toast.makeText(context, "${selectedFiles.size} item(s) copied to clipboard", Toast.LENGTH_SHORT).show()
        isSelectionMode = false; selectedFiles = emptySet()
    }

    fun pasteFromClipboard() {
        if (clipboardPaths.isEmpty()) return
        isWorking = true; workMessage = if (clipboardMode == "move") "Moving..." else "Copying..."
        CoroutineScope(Dispatchers.IO).launch {
            var success = true
            val dest = getCurrentPath()
            clipboardPaths.forEach { srcPath ->
                val src = File(srcPath)
                val destFile = File(dest, src.name)
                if (src.isDirectory) { if (!FileUtils.copyDirectory(src, destFile)) success = false }
                else { if (!FileUtils.copyFile(src, destFile)) success = false }
                if (clipboardMode == "move") FileUtils.deleteRecursive(src)
            }
            withContext(Dispatchers.Main) {
                isWorking = false
                Toast.makeText(context, if (success) "Pasted successfully" else "Some files failed", Toast.LENGTH_SHORT).show()
                if (clipboardMode == "move") clipboardPaths = emptyList()
                refreshFiles(getCurrentPath())
            }
        }
    }

    fun compressSelected() {
        val outputFile = File(File(getCurrentPath()), "archive_${System.currentTimeMillis()}.zip")
        isWorking = true; workMessage = "Compressing..."
        CoroutineScope(Dispatchers.IO).launch {
            val success = FileUtils.createZip(File(getCurrentPath()), outputFile)
            withContext(Dispatchers.Main) {
                isWorking = false
                Toast.makeText(context, if (success) "Archive created" else "Compression failed", Toast.LENGTH_SHORT).show()
                if (success) refreshFiles(getCurrentPath())
            }
        }
    }

    // ===== INIT =====

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions.values.all { it }) { refreshFiles(leftPath, 0); refreshFiles(rightPath, 1) }
        else Toast.makeText(context, "Storage permission required", Toast.LENGTH_LONG).show()
    }

    LaunchedEffect(Unit) {
        // Request MANAGE_EXTERNAL_STORAGE for Android 11+ full file access
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                    data = Uri.parse("package:${context.packageName}")
                }
                context.startActivity(intent)
            }
        }
        
        // Wait a moment for permission, then load
        delay(1000)
        
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
        refreshFiles(leftPath, 0); refreshFiles(rightPath, 1); loadStorageInfo()
    }

    // Process pending automation actions for FileManager screen
    LaunchedEffect(Unit) {
        while (true) {
            val action = com.example.ui.automation.PendingActions.queue.poll()
            if (action != null && action.targetScreen == "FileManager") {
                when (action.action) {
                    "compress" -> {
                        val path = action.parameters["path"] ?: continue
                        // Navigate to the parent directory first
                        val targetFile = File(path)
                        val parentPath = targetFile.parent ?: getCurrentPath()
                        if (activePane == 0) leftPath = parentPath else rightPath = parentPath
                        refreshFiles(parentPath)
                        delay(500)
                        // Then compress (will compress current directory)
                        isWorking = true; workMessage = "Compressing..."
                        CoroutineScope(Dispatchers.IO).launch {
                            val outputFile = File(File(getCurrentPath()), "archive_${System.currentTimeMillis()}.zip")
                            val success = FileUtils.createZip(File(getCurrentPath()), outputFile)
                            withContext(Dispatchers.Main) {
                                isWorking = false
                                Toast.makeText(context, if (success) "Archive created" else "Compression failed", Toast.LENGTH_SHORT).show()
                                if (success) refreshFiles(getCurrentPath())
                            }
                        }
                    }
                }
            }
            delay(300)
        }
    }

    // Listen for automation events - use getCurrentPath() instead of currentPath
    LaunchedEffect(Unit) {
        AutomationEventBus.events.collect { event ->
            if (event.targetScreen == "FileManager") {
                when (event.action) {
                    "compress" -> {
                        val path = event.parameters["path"] ?: return@collect
                        val outputFile = File(File(getCurrentPath()), "archive_${System.currentTimeMillis()}.zip")
                        val success = FileUtils.createZip(File(path), outputFile)
                        AutomationEventBus.sendResult(AutomationEventBus.AutomationResult(event.requestId, success, if (success) outputFile.absolutePath else "Failed"))
                    }
                    "create" -> {
                        val name = event.parameters["name"] ?: return@collect
                        val type = event.parameters["type"] ?: "file"
                        val newFile = File(getCurrentPath(), name)
                        val success = if (type == "folder") newFile.mkdirs() else newFile.createNewFile()
                        AutomationEventBus.sendResult(AutomationEventBus.AutomationResult(event.requestId, success, if (success) newFile.absolutePath else "Failed"))
                    }
                }
            }
        }
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
                        val newFile = File(getCurrentPath(), dialogInputText)
                        val success = if (type == CreateType.FILE) try { newFile.createNewFile() } catch (e: Exception) { false } else newFile.mkdirs()
                        if (success) refreshFiles(getCurrentPath()) else Toast.makeText(context, "Creation failed", Toast.LENGTH_SHORT).show()
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
                        if (fileItem.file.renameTo(newFile)) refreshFiles(getCurrentPath()) else Toast.makeText(context, "Rename failed", Toast.LENGTH_SHORT).show()
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
            title = { Text("Sort by", color = ZenGold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    listOf(FileSortMode.NAME to "Name", FileSortMode.DATE to "Date", FileSortMode.SIZE to "Size", FileSortMode.TYPE to "Type").forEach { (mode, label) ->
                        Row(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable(onClick = {
                            if (sortMode == mode) isAscending = !isAscending else { sortMode = mode; isAscending = true }
                            refreshFiles(leftPath, 0); refreshFiles(rightPath, 1); showSortDialog = false
                        }).padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(label, color = YinText)
                            if (sortMode == mode) Icon(if (isAscending) Icons.Default.ArrowUpward else Icons.Default.ArrowDownward, null, tint = ZenGold, modifier = Modifier.size(18.dp))
                        }
                    }
                    Divider(color = Color(0xFF333340))
                    Row(modifier = Modifier.fillMaxWidth().clickable(onClick = { showHidden = !showHidden; refreshFiles(leftPath, 0); refreshFiles(rightPath, 1) }).padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Show hidden files", color = YinText)
                        Switch(checked = showHidden, onCheckedChange = { showHidden = it; refreshFiles(leftPath, 0); refreshFiles(rightPath, 1) })
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showSortDialog = false }) { Text("Close", color = ZenGold) } },
            containerColor = if (isDark) Color(0xFF14131A) else Color(0xFFF7F4EE)
        )
    }

    showFileInfoDialog?.let { fileItem ->
        var checksumMd5 by remember { mutableStateOf("") }
        var isCalculatingChecksum by remember { mutableStateOf(false) }
        var apkInfo by remember { mutableStateOf<ApkInfo?>(null) }
        
        LaunchedEffect(fileItem) {
            if (!fileItem.isDirectory && fileItem.extension == "apk") {
                apkInfo = FileUtils.getApkInfo(context, fileItem.file.absolutePath)
            }
        }
        
        AlertDialog(
            onDismissRequest = { showFileInfoDialog = null },
            title = { Text("File Info", color = ZenGold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    InfoRow("Name", fileItem.name); InfoRow("Path", fileItem.file.absolutePath)
                    InfoRow("Type", if (fileItem.isDirectory) "Folder" else fileItem.extension.uppercase())
                    InfoRow("Size", if (fileItem.isDirectory) "${fileItem.file.listFiles()?.size ?: 0} items" else FileUtils.formatSize(fileItem.size))
                    InfoRow("Modified", FileUtils.formatDate(fileItem.lastModified))
                    
                    // APK Info section
                    if (fileItem.extension == "apk") {
                        Divider(color = Color(0xFF333340))
                        Text("📱 APK Information", color = ZenGold, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        apkInfo?.let { info ->
                            InfoRow("App Name", info.appName)
                            InfoRow("Package", info.packageName)
                            InfoRow("Version", "${info.versionName} (${info.versionCode})")
                        } ?: Text("Loading APK info...", color = YinTextSecondary, fontSize = 11.sp)
                    }
                    
                    // Checksum section
                    if (!fileItem.isDirectory) {
                        Divider(color = Color(0xFF333340))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text("MD5 Checksum:", color = YinTextSecondary, fontSize = 11.sp)
                            if (isCalculatingChecksum) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), color = ZenGold, strokeWidth = 2.dp)
                            } else if (checksumMd5.isNotEmpty()) {
                                Text(checksumMd5.take(16) + "...", color = ZenGold, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                            }
                        }
                        Button(onClick = {
                            isCalculatingChecksum = true
                            CoroutineScope(Dispatchers.IO).launch {
                                checksumMd5 = FileUtils.calculateChecksum(fileItem.file, "MD5")
                                withContext(Dispatchers.Main) { isCalculatingChecksum = false }
                            }
                        }, colors = ButtonDefaults.buttonColors(containerColor = YinCardBg), modifier = Modifier.fillMaxWidth()) { 
                            Text("🔐 Calculate MD5", color = YinText) 
                        }
                        Button(onClick = { hexContent = FileUtils.readHexDump(fileItem.file); showHexViewer = fileItem; showFileInfoDialog = null },
                            colors = ButtonDefaults.buttonColors(containerColor = YinCardBg), modifier = Modifier.fillMaxWidth()) { Text("🔍 Hex Viewer", color = YinText) }
                    }
                    
                    if (fileItem.extension in listOf("zip", "rar", "7z")) {
                        if (fileItem.containsImages) {
                            Button(onClick = {
                                showFileInfoDialog = null
                                CoroutineScope(Dispatchers.IO).launch {
                                    val images = FileUtils.listImagesInZip(fileItem.file)
                                    withContext(Dispatchers.Main) {
                                        if (images.isNotEmpty()) { slideshowImages = images; slideshowIndex = 0; slideshowFileName = fileItem.name; showSlideshow = true }
                                    }
                                }
                            }, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4FC3F7)), modifier = Modifier.fillMaxWidth()) { Text("🎞 Slideshow", color = Color.White) }
                        }
                        Button(onClick = {
                            CoroutineScope(Dispatchers.IO).launch {
                                FileUtils.extractZip(fileItem.file, File(fileItem.file.parent, fileItem.name.removeSuffix(".${fileItem.extension}")))
                                withContext(Dispatchers.Main) { refreshFiles(getCurrentPath()) }
                            }
                            showFileInfoDialog = null
                        }, colors = ButtonDefaults.buttonColors(containerColor = ZenGold), modifier = Modifier.fillMaxWidth()) { Text("Extract", color = Color.Black) }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showFileInfoDialog = null }) { Text("Close", color = ZenGold) } },
            containerColor = if (isDark) Color(0xFF14131A) else Color(0xFFF7F4EE)
        )
    }

    if (showHexViewer != null) {
        AlertDialog(
            onDismissRequest = { showHexViewer = null },
            title = { Text("Hex Viewer: ${showHexViewer!!.name}", color = ZenGold) },
            text = { Text(hexContent, fontFamily = FontFamily.Monospace, fontSize = 10.sp, color = Color(0xFF8CBE91)) },
            confirmButton = { TextButton(onClick = { showHexViewer = null }) { Text("Close", color = ZenGold) } },
            containerColor = if (isDark) Color(0xFF0F0F12) else Color(0xFFF8F6F2)
        )
    }

    if (showTextEditor != null) {
        val fi = showTextEditor!!
        AlertDialog(
            onDismissRequest = { showTextEditor = null },
            title = { Text("Edit: ${fi.name}", color = ZenGold, maxLines = 1) },
            text = { OutlinedTextField(value = editorContent, onValueChange = { editorContent = it }, modifier = Modifier.fillMaxWidth().heightIn(min = 300.dp), textStyle = androidx.compose.ui.text.TextStyle(fontFamily = FontFamily.Monospace, fontSize = 13.sp, color = YinText)) },
            confirmButton = { Button(onClick = { try { fi.file.writeText(editorContent); refreshFiles(getCurrentPath()) } catch (e: Exception) {}; showTextEditor = null }, colors = ButtonDefaults.buttonColors(containerColor = ZenGold)) { Text("Save", color = Color.Black) } },
            dismissButton = { TextButton(onClick = { showTextEditor = null }) { Text("Close", color = ZenRed) } },
            containerColor = if (isDark) Color(0xFF14131A) else Color(0xFFF7F4EE)
        )
    }

    if (showSlideshow && slideshowImages.isNotEmpty()) {
        AlertDialog(
            onDismissRequest = { showSlideshow = false },
            title = { Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text(slideshowFileName, color = ZenGold); Text("${slideshowIndex + 1}/${slideshowImages.size}", color = YinTextSecondary, fontSize = 12.sp) } },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Card(modifier = Modifier.fillMaxWidth().height(300.dp), shape = RoundedCornerShape(12.dp)) {
                        slideshowImages.getOrNull(slideshowIndex)?.let { Image(bitmap = it.second.asImageBitmap(), contentDescription = it.first, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Fit) }
                    }
                    Row(horizontalArrangement = Arrangement.SpaceEvenly, modifier = Modifier.fillMaxWidth()) {
                        IconButton(onClick = { if (slideshowIndex > 0) slideshowIndex-- else slideshowIndex = slideshowImages.size - 1 }) { Icon(Icons.Default.SkipPrevious, null, tint = ZenGold, modifier = Modifier.size(32.dp)) }
                        IconButton(onClick = { if (slideshowIndex < slideshowImages.size - 1) slideshowIndex++ else slideshowIndex = 0 }) { Icon(Icons.Default.SkipNext, null, tint = ZenGold, modifier = Modifier.size(32.dp)) }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showSlideshow = false }) { Text("Close", color = ZenGold) } },
            containerColor = if (isDark) Color(0xFF14131A) else Color(0xFFF7F4EE)
        )
    }

    if (isWorking) {
        AlertDialog(onDismissRequest = { }, title = { Text("Working", color = ZenGold) }, text = { Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) { CircularProgressIndicator(modifier = Modifier.size(24.dp), color = ZenGold); Text(workMessage, color = YinText) } }, confirmButton = { })
    }

    // ==================== MAIN UI ====================

    Scaffold(
        topBar = {
            Column(modifier = Modifier.fillMaxWidth().background(if (isDark) YinBlack else YangWhite).statusBarsPadding()) {
                // Header
                Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        val currentActivePath = if (activePane == 0) leftPath else rightPath
                        if (currentActivePath != internalStorage.absolutePath) {
                            IconButton(onClick = { navigateUp() }) {
                                Icon(Icons.Default.ArrowBack, "Back", tint = YinText)
                            }
                        } else {
                            IconButton(onClick = onMenuClick) {
                                Icon(Icons.Default.Menu, null, tint = if (isDark) Color.White else Color.Black)
                            }
                        }
                        Text("ZEN FILE EXPLORER", fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold, color = if (isDark) ZenGold else Color(0xFF9E7E1D))
                    }
                    Row {
                        IconButton(onClick = { showCreateDialog = CreateType.FILE }) { Icon(Icons.Default.NoteAdd, null, tint = ZenGold, modifier = Modifier.size(20.dp)) }
                        IconButton(onClick = { showCreateDialog = CreateType.FOLDER }) { Icon(Icons.Default.CreateNewFolder, null, tint = ZenSienna, modifier = Modifier.size(20.dp)) }
                        IconButton(onClick = { showSortDialog = true }) { Icon(Icons.Default.Sort, null, tint = YinTextSecondary, modifier = Modifier.size(20.dp)) }
                        IconButton(onClick = { viewMode = if (viewMode == ViewMode.SINGLE_PANE) ViewMode.DUAL_PANE else ViewMode.SINGLE_PANE }) {
                            Icon(if (viewMode == ViewMode.SINGLE_PANE) Icons.Default.ViewColumn else Icons.Default.ViewAgenda, null, tint = if (viewMode == ViewMode.DUAL_PANE) ZenGold else YinTextSecondary, modifier = Modifier.size(20.dp))
                        }
                    }
                }

                // Selection toolbar
                if (isSelectionMode) {
                    Surface(color = ZenGold.copy(alpha = 0.1f)) {
                        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = { isSelectionMode = false; selectedFiles = emptySet() }) { Icon(Icons.Default.Close, null, tint = YinText) }
                            Text("${selectedFiles.size} selected", color = YinText, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                            IconButton(onClick = { copyToClipboard() }) { Icon(Icons.Default.ContentCopy, null, tint = ZenBlue, modifier = Modifier.size(20.dp)) }
                            IconButton(onClick = { clipboardMode = "move"; copyToClipboard(); clipboardMode = "move" }) { Icon(Icons.Default.ContentCut, null, tint = ZenGold, modifier = Modifier.size(20.dp)) }
                            IconButton(onClick = { showDeleteDialog = true }) { Icon(Icons.Default.Delete, null, tint = ZenRed, modifier = Modifier.size(20.dp)) }
                            IconButton(onClick = { compressSelected() }) { Icon(Icons.Default.Archive, null, tint = Color(0xFFFFD54F), modifier = Modifier.size(20.dp)) }
                        }
                    }
                }

                // Clipboard bar
                if (clipboardPaths.isNotEmpty()) {
                    Surface(color = ZenBlue.copy(alpha = 0.1f)) {
                        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.ContentPaste, null, tint = ZenBlue, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("${clipboardPaths.size} item(s) in clipboard (${clipboardMode})", color = YinText, fontSize = 11.sp, modifier = Modifier.weight(1f))
                            TextButton(onClick = { pasteFromClipboard() }) { Text("Paste", color = ZenGold, fontSize = 11.sp) }
                            TextButton(onClick = { clipboardPaths = emptyList() }) { Text("Clear", color = ZenRed, fontSize = 11.sp) }
                        }
                    }
                }

                // Search
                OutlinedTextField(value = searchQuery, onValueChange = { searchQuery = it; refreshFiles(leftPath, 0); refreshFiles(rightPath, 1) },
                    placeholder = { Text("Search...", fontSize = 12.sp, color = Color.Gray) },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp).height(40.dp),
                    leadingIcon = { Icon(Icons.Default.Search, null, tint = Color.Gray, modifier = Modifier.size(18.dp)) },
                    trailingIcon = { if (searchQuery.isNotEmpty()) IconButton(onClick = { searchQuery = ""; refreshFiles(leftPath, 0); refreshFiles(rightPath, 1) }) { Icon(Icons.Default.Close, null, tint = Color.Gray, modifier = Modifier.size(18.dp)) } },
                    singleLine = true, shape = RoundedCornerShape(10.dp),
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = ZenGold, unfocusedBorderColor = Color(0xFF333340), focusedContainerColor = Color(0xFF1A1A22), unfocusedContainerColor = Color(0xFF1A1A22))
                )
            }
        },
        bottomBar = {
            if (isSelectionMode) {
                Surface(color = YinCardBg, shadowElevation = 8.dp) {
                    Row(
                        modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 8.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        BottomActionButton("Copy", Icons.Default.ContentCopy) { copyToClipboard() }
                        BottomActionButton("Cut", Icons.Default.ContentCut) { clipboardMode = "move"; copyToClipboard() }
                        BottomActionButton("Paste", Icons.Default.ContentPaste) { pasteFromClipboard() }
                        BottomActionButton("Delete", Icons.Default.Delete, ZenRed) { showDeleteDialog = true }
                        BottomActionButton("More", Icons.Default.MoreVert) { compressSelected() }
                    }
                }
            }
        }
    ) { padding ->
        if (viewMode == ViewMode.DUAL_PANE) {
            // DUAL PANE X-plore style
            Row(modifier = Modifier.fillMaxSize().padding(padding)) {
                // Left pane
                PaneColumn(
                    path = leftPath, files = leftFiles, isActive = activePane == 0,
                    isDark = isDark, internalStorage = internalStorage,
                    onActivate = { activePane = 0 },
                    onNavigateUp = { navigateUp(0) },
                    onFileClick = { handleFileClick(it) },
                    onRefresh = { refreshFiles(leftPath, 0) },
                    isSelectionMode = isSelectionMode, selectedFiles = selectedFiles,
                    onToggleSelect = { path -> selectedFiles = if (path in selectedFiles) selectedFiles - path else selectedFiles + path },
                    onLongPress = { if (!isSelectionMode) { isSelectionMode = true; selectedFiles = setOf(it) } },
                    onRename = { showRenameDialog = it },
                    onInfo = { showFileInfoDialog = it },
                    modifier = Modifier.weight(1f)
                )
                // Divider
                Box(modifier = Modifier.fillMaxHeight().width(2.dp).background(ZenGold.copy(alpha = 0.3f)))
                // Right pane
                PaneColumn(
                    path = rightPath, files = rightFiles, isActive = activePane == 1,
                    isDark = isDark, internalStorage = internalStorage,
                    onActivate = { activePane = 1 },
                    onNavigateUp = { navigateUp(1) },
                    onFileClick = { handleFileClick(it) },
                    onRefresh = { refreshFiles(rightPath, 1) },
                    isSelectionMode = isSelectionMode, selectedFiles = selectedFiles,
                    onToggleSelect = { path -> selectedFiles = if (path in selectedFiles) selectedFiles - path else selectedFiles + path },
                    onLongPress = { if (!isSelectionMode) { isSelectionMode = true; selectedFiles = setOf(it) } },
                    onRename = { showRenameDialog = it },
                    onInfo = { showFileInfoDialog = it },
                    modifier = Modifier.weight(1f)
                )
            }
        } else {
            // SINGLE PANE
            PaneColumn(
                path = leftPath, files = leftFiles, isActive = true,
                isDark = isDark, internalStorage = internalStorage,
                onActivate = {},
                onNavigateUp = { navigateUp(0) },
                onFileClick = { handleFileClick(it) },
                onRefresh = { refreshFiles(leftPath, 0) },
                isSelectionMode = isSelectionMode, selectedFiles = selectedFiles,
                onToggleSelect = { path -> selectedFiles = if (path in selectedFiles) selectedFiles - path else selectedFiles + path },
                onLongPress = { if (!isSelectionMode) { isSelectionMode = true; selectedFiles = setOf(it) } },
                onRename = { showRenameDialog = it },
                onInfo = { showFileInfoDialog = it },
                modifier = Modifier.fillMaxSize().padding(padding)
            )
        }
    }
}

// ==================== PANE COLUMN ====================

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PaneColumn(
    path: String,
    files: List<FileItem>,
    isActive: Boolean,
    isDark: Boolean,
    internalStorage: File,
    onActivate: () -> Unit,
    onNavigateUp: () -> Unit,
    onFileClick: (FileItem) -> Unit,
    onRefresh: () -> Unit,
    isSelectionMode: Boolean,
    selectedFiles: Set<String>,
    onToggleSelect: (String) -> Unit,
    onLongPress: (String) -> Unit,
    onRename: (FileItem) -> Unit,
    onInfo: (FileItem) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.background(if (isActive) (if (isDark) Color(0xFF0A0A10) else Color(0xFFF8F6F2)) else (if (isDark) Color(0xFF050508) else Color(0xFFEEECE8))).clickable(onClick = onActivate)) {
        // Breadcrumb
        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onNavigateUp, enabled = path != "/" && path != internalStorage.absolutePath, modifier = Modifier.size(24.dp)) {
                Icon(Icons.Default.ArrowUpward, null, tint = if (isActive) ZenGold else Color.Gray, modifier = Modifier.size(16.dp))
            }
            Text(path.replace(internalStorage.absolutePath, "Int").takeLast(30), color = if (isActive) YinTextSecondary else Color.Gray, fontSize = 10.sp, fontFamily = FontFamily.Monospace, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            if (isActive) {
                Text("${files.size}", color = ZenGold, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            }
        }

        // Storage bar (root only)
        if (path == internalStorage.absolutePath && isActive) {
            val total = internalStorage.totalSpace
            val free = internalStorage.freeSpace
            val used = total - free
            val pct = if (total > 0) used.toFloat() / total else 0f
            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                LinearProgressIndicator(progress = { pct }, modifier = Modifier.weight(1f).height(2.dp).clip(CircleShape), color = if (pct > 0.9f) ZenRed else ZenGold, trackColor = Color(0xFF222228))
                Spacer(Modifier.width(6.dp))
                Text(FileUtils.formatSize(free), color = ZenGold, fontSize = 9.sp, fontWeight = FontWeight.Bold)
            }
        }

        // File list
        if (files.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("Empty", color = Color.Gray, fontSize = 12.sp) }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectHorizontalDragGestures { _, dragAmount ->
                            if (dragAmount > 100) {
                                onNavigateUp()
                            }
                        }
                    },
                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp)
            ) {
                // Shortcuts at root
                if (path == internalStorage.absolutePath && isActive) {
                    item { MiniShortcut(Icons.Default.Image, "Images", Color(0xFF4FC3F7)) { File(internalStorage, "DCIM").let { if (it.exists()) onRefresh() } } }
                    item { MiniShortcut(Icons.Default.VideoFile, "Videos", ZenRed) { File(internalStorage, "Movies").let { if (it.exists()) onRefresh() } } }
                    item { MiniShortcut(Icons.Default.MusicNote, "Audio", Color(0xFFCE93D8)) { File(internalStorage, "Music").let { if (it.exists()) onRefresh() } } }
                    item { Divider(color = Color(0xFF222228), modifier = Modifier.padding(vertical = 4.dp)) }
                    
                    // Storage Analysis Card
                    item {
                        Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                            colors = CardDefaults.cardColors(containerColor = YinCardBg)) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text("📊 Storage Analysis", color = ZenGold, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                Spacer(Modifier.height(4.dp))
                                StorageCategoryRow("Images", 45.2, Color(0xFF4FC3F7))
                                StorageCategoryRow("Videos", 128.7, ZenRed)
                                StorageCategoryRow("Documents", 12.3, ZenGold)
                                StorageCategoryRow("Other", 8.1, YinTextSecondary)
                            }
                        }
                    }
                }
                items(files, key = { it.file.absolutePath }) { fileItem ->
                    val isSelected = fileItem.file.absolutePath in selectedFiles
                    Row(
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(6.dp)).background(if (isSelected) ZenGold.copy(alpha = 0.15f) else Color.Transparent)
                            .combinedClickable(onClick = { if (isSelectionMode) onToggleSelect(fileItem.file.absolutePath) else onFileClick(fileItem) }, onLongClick = { onLongPress(fileItem.file.absolutePath) })
                            .padding(horizontal = 6.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (isSelectionMode) { Checkbox(checked = isSelected, onCheckedChange = { onToggleSelect(fileItem.file.absolutePath) }, colors = CheckboxDefaults.colors(checkedColor = ZenGold), modifier = Modifier.size(18.dp)); Spacer(Modifier.width(4.dp)) }
                        Box(modifier = Modifier.size(28.dp).clip(RoundedCornerShape(6.dp)).background(FileUtils.getIconColor(fileItem.extension, fileItem.isDirectory).copy(alpha = 0.15f)), contentAlignment = Alignment.Center) {
                            Icon(FileUtils.getFileIcon(fileItem.extension, fileItem.isDirectory), null, tint = FileUtils.getIconColor(fileItem.extension, fileItem.isDirectory), modifier = Modifier.size(16.dp))
                        }
                        Spacer(Modifier.width(8.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(fileItem.name, color = YinText, fontSize = 12.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text("${if (fileItem.isDirectory) "Folder" else FileUtils.formatSize(fileItem.size)} • ${FileUtils.formatDate(fileItem.lastModified)}", color = YinTextSecondary, fontSize = 9.sp, maxLines = 1)
                        }
                        if (!isSelectionMode && isActive) {
                            IconButton(onClick = { onRename(fileItem) }, modifier = Modifier.size(22.dp)) { Icon(Icons.Default.Edit, null, tint = YinTextSecondary.copy(alpha = 0.5f), modifier = Modifier.size(12.dp)) }
                            IconButton(onClick = { onInfo(fileItem) }, modifier = Modifier.size(22.dp)) { Icon(Icons.Default.Info, null, tint = YinTextSecondary.copy(alpha = 0.5f), modifier = Modifier.size(12.dp)) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MiniShortcut(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, color: Color, onClick: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(6.dp)).clickable(onClick = onClick).padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(modifier = Modifier.size(24.dp).clip(RoundedCornerShape(4.dp)).background(color.copy(alpha = 0.15f)), contentAlignment = Alignment.Center) { Icon(icon, null, tint = color, modifier = Modifier.size(14.dp)) }
        Spacer(Modifier.width(8.dp))
        Text(label, color = YinTextSecondary, fontSize = 11.sp)
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = YinTextSecondary, fontSize = 11.sp)
        Text(value, color = YinText, fontSize = 11.sp, maxLines = 2, modifier = Modifier.widthIn(max = 180.dp))
    }
}

@Composable
private fun StorageCategoryRow(name: String, sizeMB: Double, color: Color) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(name, color = YinTextSecondary, fontSize = 11.sp)
        Text("${sizeMB} MB", color = color, fontSize = 11.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun BottomActionButton(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, color: Color = YinText, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 4.dp)) {
        Icon(icon, label, tint = color, modifier = Modifier.size(22.dp))
        Text(label, color = color, fontSize = 10.sp)
    }
}

// ==================== CONTEXT MENU (ZArchiver-style long press) ====================

@Composable
fun FileContextMenu(
    file: FileItem,
    onDismiss: () -> Unit,
    onOpen: () -> Unit,
    onCopy: () -> Unit,
    onMove: () -> Unit,
    onDelete: () -> Unit,
    onRename: () -> Unit,
    onProperties: () -> Unit,
    onCompress: () -> Unit,
    onShare: () -> Unit,
    onChecksum: () -> Unit,
    onAddFavorite: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(file.name, color = ZenGold, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        text = {
            Column {
                ContextMenuItem("Open", Icons.Default.OpenInNew, onOpen)
                ContextMenuItem("Copy", Icons.Default.ContentCopy, onCopy)
                ContextMenuItem("Move", Icons.Default.DriveFileMove, onMove)
                ContextMenuItem("Delete", Icons.Default.Delete, onDelete, ZenRed)
                ContextMenuItem("Rename", Icons.Default.Edit, onRename)
                ContextMenuItem("Compress", Icons.Default.Archive, onCompress)
                ContextMenuItem("Share", Icons.Default.Share, onShare)
                ContextMenuItem("Checksum", Icons.Default.Security, onChecksum)
                ContextMenuItem("Properties", Icons.Default.Info, onProperties)
                ContextMenuItem(
                    if (FavoritesManager.isFavorite(file.file.absolutePath)) "Remove Favorite" else "Add to Favorites",
                    Icons.Default.Star, onAddFavorite
                )
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } }
    )
}

@Composable
fun ContextMenuItem(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit, color: Color = YinText) {
    Row(modifier = Modifier.fillMaxWidth().combinedClickable(onClick = onClick, onLongClick = {}).padding(vertical = 10.dp, horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = color, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(12.dp))
        Text(label, color = color, fontSize = 14.sp)
    }
}

// ==================== FAVORITES SIDEBAR ====================

@Composable
fun FavoritesSidebar(
    favorites: List<String>,
    onNavigate: (String) -> Unit,
    onRemove: (String) -> Unit
) {
    Column {
        Text("⭐ Favorites", color = ZenGold, fontWeight = FontWeight.Bold, fontSize = 12.sp,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp))
        favorites.forEach { path ->
            Row(modifier = Modifier.fillMaxWidth().combinedClickable(onClick = { onNavigate(path) }, onLongClick = {}).padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Star, null, tint = ZenGold, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(8.dp))
                Text(File(path).name, color = YinText, fontSize = 13.sp, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                IconButton(onClick = { onRemove(path) }, modifier = Modifier.size(20.dp)) {
                    Icon(Icons.Default.Close, null, tint = YinTextSecondary.copy(alpha = 0.5f), modifier = Modifier.size(12.dp))
                }
            }
        }
    }
}

// ==================== BATCH RENAME DIALOG ====================

@Composable
fun BatchRenameDialog(
    files: List<FileItem>,
    onDismiss: () -> Unit,
    onRename: (String, Int) -> Unit
) {
    var prefix by remember { mutableStateOf("") }
    var startNumber by remember { mutableIntStateOf(1) }
    val extension = files.firstOrNull()?.extension ?: ""

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Batch Rename ${files.size} files", color = ZenGold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = prefix, onValueChange = { prefix = it }, label = { Text("Prefix") })
                OutlinedTextField(value = startNumber.toString(), onValueChange = { startNumber = it.toIntOrNull() ?: 1 },
                    label = { Text("Start number") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                OutlinedTextField(value = extension, onValueChange = { /* extension is auto-detected */ }, label = { Text("Extension (auto)") }, enabled = false)
                Text("Example: ${prefix}${startNumber}.${extension}", color = YinTextSecondary, fontSize = 11.sp)
            }
        },
        confirmButton = {
            Button(onClick = {
                files.forEachIndexed { index, file ->
                    val newName = "$prefix${startNumber + index}.$extension"
                    val newFile = File(file.file.parent, newName)
                    file.file.renameTo(newFile)
                }
                onDismiss()
            }) { Text("Rename All") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

// ==================== DUAL PANE TOGGLE BUTTON ====================

@Composable
fun DualPaneToggle(
    isDualPane: Boolean,
    onToggle: () -> Unit
) {
    IconButton(onClick = onToggle) {
        Icon(
            if (isDualPane) Icons.Default.ViewColumn else Icons.Default.ViewAgenda,
            contentDescription = "Toggle dual pane",
            tint = if (isDualPane) ZenGold else YinTextSecondary
        )
    }
}

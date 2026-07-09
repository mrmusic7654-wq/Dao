package com.example.ui.screens.filemanager

import android.content.Context
import android.os.Build
import android.os.Environment
import android.os.StatFs
import java.io.*
import java.text.SimpleDateFormat
import java.util.*
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

// ==================== DATA MODELS ====================

data class FileItem(
    val name: String,
    val path: String,
    val isDirectory: Boolean,
    val size: Long,
    val lastModified: Long,
    val extension: String = "",
    val permissions: String = "",
    val isHidden: Boolean = false,
    val isSymlink: Boolean = false,
    val mimeType: String = "",
    val iconRes: Int = 0
)

enum class SortMode { NAME, SIZE, DATE, TYPE, EXTENSION }
enum class ViewMode { LIST, GRID, DETAILS }
enum class SelectionMode { NONE, SINGLE, MULTI }

data class StorageInfo(
    val path: String,
    val label: String,
    val totalBytes: Long,
    val freeBytes: Long,
    val usedBytes: Long,
    val isInternal: Boolean
)

data class ArchiveEntry(
    val name: String,
    val path: String,
    val size: Long,
    val compressedSize: Long,
    val isDirectory: Boolean,
    val method: String,
    val crc: Long
)

// ==================== FILE OPERATIONS ENGINE ====================

object FileOperations {
    
    fun listFiles(path: String, showHidden: Boolean = false): List<FileItem> {
        val dir = File(path)
        if (!dir.exists() || !dir.canRead()) return emptyList()
        
        val files = dir.listFiles() ?: return emptyList()
        return files
            .filter { showHidden || !it.name.startsWith(".") }
            .map { file ->
                FileItem(
                    name = file.name,
                    path = file.absolutePath,
                    isDirectory = file.isDirectory,
                    size = if (file.isFile) file.length() else 0,
                    lastModified = file.lastModified(),
                    extension = file.extension.lowercase(),
                    permissions = getPermissions(file),
                    isHidden = file.name.startsWith("."),
                    mimeType = getMimeType(file.name)
                )
            }
    }
    
    fun sortFiles(files: List<FileItem>, mode: SortMode, ascending: Boolean): List<FileItem> {
        val dirs = files.filter { it.isDirectory }
        val fils = files.filter { !it.isDirectory }
        
        val comparator: Comparator<FileItem> = when (mode) {
            SortMode.NAME -> compareBy { it.name.lowercase() }
            SortMode.SIZE -> compareBy { it.size }
            SortMode.DATE -> compareBy { it.lastModified }
            SortMode.TYPE -> compareBy { it.extension }
            SortMode.EXTENSION -> compareBy { it.extension }
        }
        
        val sortedDirs = if (ascending) dirs.sortedWith(comparator) else dirs.sortedWith(comparator.reversed())
        val sortedFiles = if (ascending) fils.sortedWith(comparator) else fils.sortedWith(comparator.reversed())
        
        return sortedDirs + sortedFiles
    }
    
    fun getStorageInfo(context: Context): List<StorageInfo> {
        val list = mutableListOf<StorageInfo>()
        
        // Internal storage
        val internalPath = Environment.getExternalStorageDirectory().absolutePath
        val internalStat = StatFs(internalPath)
        val internalTotal = internalStat.blockCountLong * internalStat.blockSizeLong
        val internalFree = internalStat.availableBlocksLong * internalStat.blockSizeLong
        
        list.add(StorageInfo(internalPath, "Internal Storage", internalTotal, internalFree, internalTotal - internalFree, true))
        
        // SD Card if available
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            context.getExternalFilesDirs(null).forEach { dir ->
                if (dir != null && dir.absolutePath != context.getExternalFilesDir(null)?.absolutePath) {
                    val sdPath = dir.absolutePath.removeSuffix("/Android/data/${context.packageName}/files")
                    try {
                        val sdStat = StatFs(sdPath)
                        val sdTotal = sdStat.blockCountLong * sdStat.blockSizeLong
                        val sdFree = sdStat.availableBlocksLong * sdStat.blockSizeLong
                        list.add(StorageInfo(sdPath, "SD Card", sdTotal, sdFree, sdTotal - sdFree, false))
                    } catch (_: Exception) {}
                }
            }
        }
        
        return list
    }
    
    fun copyFile(source: File, destination: File, onProgress: ((Float) -> Unit)? = null): Boolean {
        return try {
            if (source.isDirectory) {
                destination.mkdirs()
                source.listFiles()?.forEach { child ->
                    copyFile(child, File(destination, child.name), onProgress)
                }
            } else {
                destination.parentFile?.mkdirs()
                source.inputStream().use { input ->
                    destination.outputStream().use { output ->
                        val buffer = ByteArray(8192)
                        var bytesRead: Int
                        var totalRead = 0L
                        val totalSize = source.length()
                        while (input.read(buffer).also { bytesRead = it } != -1) {
                            output.write(buffer, 0, bytesRead)
                            totalRead += bytesRead
                            onProgress?.invoke(totalRead.toFloat() / totalSize)
                        }
                    }
                }
            }
            true
        } catch (e: Exception) { false }
    }
    
    fun moveFile(source: File, destination: File): Boolean {
        return try {
            if (source.renameTo(destination)) return true
            // If rename fails (cross-device), copy then delete
            if (copyFile(source, destination)) {
                deleteRecursive(source)
                true
            } else false
        } catch (e: Exception) { false }
    }
    
    fun deleteRecursive(file: File): Boolean {
        return try {
            if (file.isDirectory) file.listFiles()?.forEach { deleteRecursive(it) }
            file.delete()
        } catch (e: Exception) { false }
    }
    
    fun renameFile(file: File, newName: String): Boolean {
        return try {
            val newFile = File(file.parent, newName)
            if (!newFile.exists()) file.renameTo(newFile) else false
        } catch (e: Exception) { false }
    }
    
    fun createFile(path: String, name: String, isDirectory: Boolean): Boolean {
        return try {
            val file = File(path, name)
            if (isDirectory) file.mkdirs() else file.createNewFile()
        } catch (e: Exception) { false }
    }
    
    // ==================== ARCHIVE OPERATIONS ====================
    
    fun createZip(files: List<File>, outputPath: String, onProgress: ((Float) -> Unit)? = null): Boolean {
        return try {
            ZipOutputStream(BufferedOutputStream(FileOutputStream(outputPath))).use { zos ->
                files.forEach { file -> addToZip(file, file.name, zos, onProgress) }
            }
            true
        } catch (e: Exception) { false }
    }
    
    private fun addToZip(file: File, entryName: String, zos: ZipOutputStream, onProgress: ((Float) -> Unit)?) {
        if (file.isDirectory) {
            zos.putNextEntry(ZipEntry("$entryName/"))
            zos.closeEntry()
            file.listFiles()?.forEach { addToZip(it, "$entryName/${it.name}", zos, onProgress) }
        } else {
            zos.putNextEntry(ZipEntry(entryName))
            file.inputStream().use { it.copyTo(zos) }
            zos.closeEntry()
        }
    }
    
    fun listArchiveContents(archivePath: String): List<ArchiveEntry> {
        val entries = mutableListOf<ArchiveEntry>()
        try {
            ZipFile(archivePath).use { zip ->
                zip.entries().asSequence().forEach { entry ->
                    entries.add(ArchiveEntry(
                        name = entry.name.substringAfterLast("/").ifBlank { entry.name },
                        path = entry.name,
                        size = entry.size,
                        compressedSize = entry.compressedSize,
                        isDirectory = entry.isDirectory,
                        method = entry.method.toString(),
                        crc = entry.crc
                    ))
                }
            }
        } catch (_: Exception) {}
        return entries
    }
    
    fun extractArchive(archivePath: String, outputDir: String, onProgress: ((Float) -> Unit)? = null): Boolean {
        return try {
            ZipFile(archivePath).use { zip ->
                val totalEntries = zip.entries().asSequence().count()
                var processed = 0
                zip.entries().asSequence().forEach { entry ->
                    val outputFile = File(outputDir, entry.name)
                    if (entry.isDirectory) {
                        outputFile.mkdirs()
                    } else {
                        outputFile.parentFile?.mkdirs()
                        zip.getInputStream(entry).use { input ->
                            outputFile.outputStream().use { output -> input.copyTo(output) }
                        }
                    }
                    processed++
                    onProgress?.invoke(processed.toFloat() / totalEntries)
                }
            }
            true
        } catch (e: Exception) { false }
    }
    
    fun createCompressedArchive(files: List<File>, outputPath: String, format: String = "zip", level: Int = 5, password: String? = null, onProgress: ((Float) -> Unit)? = null): Boolean {
        return when (format.lowercase()) {
            "zip" -> createZip(files, outputPath, onProgress)
            // 7z and tar support would need external libraries
            else -> createZip(files, outputPath, onProgress)
        }
    }
    
    // ==================== UTILITIES ====================
    
    fun getPermissions(file: File): String {
        return buildString {
            append(if (file.isDirectory) "d" else "-")
            append(if (file.canRead()) "r" else "-")
            append(if (file.canWrite()) "w" else "-")
            append(if (file.canExecute()) "x" else "-")
            append("r-xr-x") // Simplified
        }
    }
    
    fun getMimeType(fileName: String): String {
        return when (fileName.substringAfterLast('.').lowercase()) {
            "jpg", "jpeg" -> "image/jpeg"
            "png" -> "image/png"
            "gif" -> "image/gif"
            "mp4" -> "video/mp4"
            "mp3" -> "audio/mpeg"
            "pdf" -> "application/pdf"
            "zip" -> "application/zip"
            "apk" -> "application/vnd.android.package-archive"
            "txt" -> "text/plain"
            else -> "*/*"
        }
    }
    
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
    
    fun searchFiles(rootPath: String, query: String, maxResults: Int = 100): List<FileItem> {
        val results = mutableListOf<FileItem>()
        val root = File(rootPath)
        if (!root.exists()) return results
        
        val queue = LinkedList<File>()
        queue.add(root)
        
        while (queue.isNotEmpty() && results.size < maxResults) {
            val current = queue.poll()
            if (current.name.contains(query, ignoreCase = true)) {
                results.add(FileItem(
                    name = current.name, path = current.absolutePath,
                    isDirectory = current.isDirectory,
                    size = if (current.isFile) current.length() else 0,
                    lastModified = current.lastModified(),
                    extension = current.extension.lowercase()
                ))
            }
            if (current.isDirectory) {
                current.listFiles()?.forEach { queue.add(it) }
            }
        }
        return results
    }
    
    fun getFileProperties(file: File): Map<String, String> {
        return mapOf(
            "Name" to file.name,
            "Path" to file.absolutePath,
            "Type" to if (file.isDirectory) "Folder" else "File (${file.extension.uppercase()})",
            "Size" to if (file.isDirectory) "${file.listFiles()?.size ?: 0} items" else formatSize(file.length()),
            "Modified" to formatDate(file.lastModified()),
            "Permissions" to getPermissions(file),
            "Readable" to file.canRead().toString(),
            "Writable" to file.canWrite().toString(),
            "Executable" to file.canExecute().toString(),
            "Hidden" to file.isHidden.toString()
        )
    }
}

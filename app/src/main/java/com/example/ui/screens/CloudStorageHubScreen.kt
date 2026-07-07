package com.example.ui.screens

import android.content.*
import android.net.Uri
import android.os.Environment
import android.provider.OpenableColumns
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.*
import kotlinx.coroutines.*
import java.io.*
import java.text.SimpleDateFormat
import java.util.*

// ==================== DATA MODELS ====================

data class CloudDrive(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val channelId: Long = 0L,
    val channelUsername: String = "",
    val totalFiles: Int = 0,
    val totalSize: Long = 0L,
    val lastSynced: Long = System.currentTimeMillis(),
    val isAutoBackup: Boolean = false,
    val backupFrequency: BackupFrequency = BackupFrequency.DAILY,
    val icon: String = "📁"
)

data class CloudFile(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val folder: String = "/",
    val messageId: Long = 0L,
    val fileId: String = "",
    val size: Long = 0L,
    val mimeType: String = "",
    val uploadedAt: Long = System.currentTimeMillis(),
    val isDownloaded: Boolean = false,
    val localPath: String = "",
    val thumbnailUri: String = ""
)

enum class BackupFrequency { HOURLY, DAILY, WEEKLY, MANUAL }
enum class CloudViewMode { DRIVES, FILES, RECENTS, SETTINGS }
enum class UploadStatus { IDLE, UPLOADING, COMPLETED, FAILED }

data class UploadTask(
    val id: String = UUID.randomUUID().toString(),
    val fileName: String,
    val fileUri: Uri,
    val driveId: String,
    val folder: String = "/",
    val size: Long = 0L,
    var progress: Float = 0f,
    var status: UploadStatus = UploadStatus.IDLE
)

// ==================== CLOUD ENGINE ====================

object CloudEngine {
    val drives = mutableStateListOf<CloudDrive>()
    val files = mutableStateListOf<CloudFile>()
    val uploadQueue = mutableStateListOf<UploadTask>()
    val recentFiles = mutableStateListOf<CloudFile>()
    var totalStorageUsed = 0L
    var isOnline = false

    fun createDrive(name: String): CloudDrive {
        val drive = CloudDrive(name = name, icon = getDriveIcon(name))
        drives.add(drive)
        // In production: TdApi.createNewSecretChat / createChannel
        return drive
    }

    fun deleteDrive(driveId: String) {
        drives.removeAll { it.id == driveId }
        files.removeAll { it.id.startsWith(driveId) }
    }

    fun uploadFile(driveId: String, uri: Uri, fileName: String, folder: String): UploadTask {
        val task = UploadTask(fileName = fileName, fileUri = uri, driveId = driveId, folder = folder, status = UploadStatus.UPLOADING)
        uploadQueue.add(task)
        // Simulate upload progress
        simulateUpload(task)
        return task
    }

    private fun simulateUpload(task: UploadTask) {
        Thread {
            for (i in 1..100) {
                Thread.sleep(50)
                val idx = uploadQueue.indexOf(task)
                if (idx >= 0) uploadQueue[idx] = task.copy(progress = i / 100f)
            }
            val idx = uploadQueue.indexOf(task)
            if (idx >= 0) {
                uploadQueue[idx] = task.copy(progress = 1f, status = UploadStatus.COMPLETED)
                val newFile = CloudFile(
                    id = "${task.driveId}_${System.currentTimeMillis()}",
                    name = task.fileName, folder = task.folder, size = task.size, uploadedAt = System.currentTimeMillis()
                )
                files.add(0, newFile)
                recentFiles.add(0, newFile)
                if (recentFiles.size > 50) recentFiles.removeAt(recentFiles.size - 1)
                totalStorageUsed += task.size
                // Remove completed from queue after delay
                Thread.sleep(2000)
                uploadQueue.remove(task)
            }
        }.start()
    }

    fun getDriveIcon(name: String): String = when {
        name.contains("backup", ignoreCase = true) -> "🗄"
        name.contains("photo", ignoreCase = true) || name.contains("image", ignoreCase = true) -> "🖼"
        name.contains("video", ignoreCase = true) || name.contains("media", ignoreCase = true) -> "🎬"
        name.contains("doc", ignoreCase = true) || name.contains("work", ignoreCase = true) -> "📄"
        name.contains("code", ignoreCase = true) || name.contains("dev", ignoreCase = true) -> "💻"
        else -> "📁"
    }

    fun getFileIcon(name: String, mime: String): String = when {
        mime.startsWith("image/") -> "🖼"
        mime.startsWith("video/") -> "🎬"
        mime.startsWith("audio/") -> "🎵"
        mime == "application/pdf" -> "📕"
        mime.contains("zip") || mime.contains("rar") || mime.contains("tar") -> "🗜"
        name.endsWith(".apk") -> "📱"
        name.endsWith(".kt") || name.endsWith(".java") || name.endsWith(".py") -> "💻"
        else -> "📄"
    }

    fun formatSize(bytes: Long): String = when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> "${"%.1f".format(bytes / 1024.0)} KB"
        bytes < 1024 * 1024 * 1024 -> "${"%.1f".format(bytes / (1024.0 * 1024.0))} MB"
        else -> "${"%.2f".format(bytes / (1024.0 * 1024.0 * 1024.0))} GB"
    }
}

// ==================== MAIN SCREEN ====================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CloudStorageHubScreen(isDark: Boolean, onMenuClick: () -> Unit) {
    val context = LocalContext.current
    var viewMode by remember { mutableStateOf(CloudViewMode.DRIVES) }
    var selectedDrive by remember { mutableStateOf<CloudDrive?>(null) }
    var currentFolder by remember { mutableStateOf("/") }
    var showCreateDriveDialog by remember { mutableStateOf(false) }
    var showUploadDialog by remember { mutableStateOf(false) }
    var showDriveDetailDialog by remember { mutableStateOf<CloudDrive?>(null) }
    var newDriveName by remember { mutableStateOf("") }
    var searchQuery by remember { mutableStateOf("") }
    var showSettings by remember { mutableStateOf(false) }

    val drives = CloudEngine.drives
    val files = CloudEngine.files
    val recentFiles = CloudEngine.recentFiles
    val uploadQueue = CloudEngine.uploadQueue
    val totalStorage = CloudEngine.totalStorageUsed
    val scope = rememberCoroutineScope()

    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let {
            val name = getFileName(context, it)
            val size = getFileSize(context, it)
            selectedDrive?.let { drive ->
                CloudEngine.uploadFile(drive.id, it, name, currentFolder)
                Toast.makeText(context, "Uploading: $name", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // ==================== DIALOGS ====================

    if (showCreateDriveDialog) {
        var driveIcon by remember { mutableStateOf("📁") }
        AlertDialog(
            onDismissRequest = { showCreateDriveDialog = false },
            title = { Text("Create New Drive", fontFamily = FontFamily.Serif, color = ZenGold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(value = newDriveName, onValueChange = { newDriveName = it },
                        label = { Text("Drive Name") }, modifier = Modifier.fillMaxWidth(), singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = ZenGold))
                    Text("Choose Icon", color = YinTextSecondary, fontSize = 12.sp)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("📁", "🗄", "🖼", "🎬", "📄", "💻", "🎵", "📕", "⚙️", "🔒").forEach { icon ->
                            item {
                                Box(modifier = Modifier.size(40.dp).clip(RoundedCornerShape(8.dp))
                                    .background(if (driveIcon == icon) ZenGold.copy(alpha = 0.2f) else Color.Transparent)
                                    .border(1.5.dp, if (driveIcon == icon) ZenGold else Color.Transparent, RoundedCornerShape(8.dp))
                                    .clickable(onClick = { driveIcon = icon }),
                                    contentAlignment = Alignment.Center) { Text(icon, fontSize = 22.sp) }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    if (newDriveName.isNotBlank()) {
                        val drive = CloudEngine.createDrive(newDriveName)
                        CloudEngine.drives.add(drive.copy(icon = driveIcon))
                        showCreateDriveDialog = false; newDriveName = ""
                    }
                }, colors = ButtonDefaults.buttonColors(containerColor = ZenGold)) { Text("Create", color = Color.Black) }
            },
            dismissButton = { TextButton(onClick = { showCreateDriveDialog = false }) { Text("Cancel", color = ZenGold) } },
            containerColor = if (isDark) Color(0xFF14131A) else Color(0xFFF7F4EE)
        )
    }

    if (showUploadDialog && selectedDrive != null) {
        AlertDialog(
            onDismissRequest = { showUploadDialog = false },
            title = { Text("Upload to ${selectedDrive!!.name}", color = ZenGold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Current folder: $currentFolder", color = YinTextSecondary, fontSize = 12.sp)
                    Text("Select file type:", color = YinTextSecondary, fontSize = 12.sp)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(
                            "📄 Any File" to "*/*",
                            "🖼 Images" to "image/*",
                            "🎬 Videos" to "video/*",
                            "🎵 Audio" to "audio/*",
                            "📕 PDF" to "application/pdf"
                        ).forEach { (label, mime) ->
                            item {
                                FilterChip(selected = false, onClick = {
                                    filePicker.launch(mime); showUploadDialog = false
                                }, label = { Text(label, fontSize = 11.sp) })
                            }
                        }
                    }
                }
            },
            confirmButton = { },
            dismissButton = { TextButton(onClick = { showUploadDialog = false }) { Text("Close", color = ZenGold) } },
            containerColor = if (isDark) Color(0xFF14131A) else Color(0xFFF7F4EE)
        )
    }

    showDriveDetailDialog?.let { drive ->
        AlertDialog(
            onDismissRequest = { showDriveDetailDialog = null },
            title = { Text("${drive.icon} ${drive.name}", color = ZenGold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    InfoRow("Files", "${drive.totalFiles}")
                    InfoRow("Size", CloudEngine.formatSize(drive.totalSize))
                    InfoRow("Last Synced", SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(drive.lastSynced)))
                    InfoRow("Auto Backup", if (drive.isAutoBackup) drive.backupFrequency.name else "Off")
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = {
                            CloudEngine.deleteDrive(drive.id); showDriveDetailDialog = null
                            Toast.makeText(context, "Drive deleted", Toast.LENGTH_SHORT).show()
                        }, colors = ButtonDefaults.buttonColors(containerColor = ZenRed), modifier = Modifier.weight(1f)) {
                            Text("Delete", color = Color.White, fontSize = 12.sp)
                        }
                        Button(onClick = {
                            // Toggle auto-backup
                            val updated = drive.copy(isAutoBackup = !drive.isAutoBackup)
                            val idx = CloudEngine.drives.indexOf(drive)
                            if (idx >= 0) CloudEngine.drives[idx] = updated
                            showDriveDetailDialog = null
                        }, colors = ButtonDefaults.buttonColors(containerColor = ZenGold), modifier = Modifier.weight(1f)) {
                            Text(if (drive.isAutoBackup) "Disable Backup" else "Enable Backup", color = Color.Black, fontSize = 12.sp)
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showDriveDetailDialog = null }) { Text("Close", color = ZenGold) } },
            containerColor = if (isDark) Color(0xFF14131A) else Color(0xFFF7F4EE)
        )
    }

    // ==================== MAIN UI ====================

    Column(modifier = Modifier.fillMaxSize().background(if (isDark) Color(0xFF070709) else Color(0xFFF1F0EC))) {
        // Header
        Surface(color = if (isDark) YinBlack else YangWhite, shadowElevation = 4.dp) {
            Column(modifier = Modifier.statusBarsPadding()) {
                Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onMenuClick) { Icon(Icons.Default.Menu, "Menu", tint = if (isDark) Color.White else Color.Black) }
                    Column(modifier = Modifier.weight(1f)) {
                        Text("☁️ CLOUD STORAGE HUB", fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold,
                            color = if (isDark) ZenGold else Color(0xFF9E7E1D), style = MaterialTheme.typography.titleMedium)
                        if (CloudEngine.isOnline) Text("Connected • ${CloudEngine.formatSize(totalStorage)} used", color = Color(0xFF4CAF50), fontSize = 10.sp)
                        else Text("Offline Mode", color = Color.Gray, fontSize = 10.sp)
                    }
                    if (viewMode == CloudViewMode.FILES) {
                        IconButton(onClick = { showUploadDialog = true }) { Icon(Icons.Default.Upload, "Upload", tint = ZenGold) }
                    }
                    IconButton(onClick = {
                        CloudEngine.isOnline = !CloudEngine.isOnline
                        Toast.makeText(context, if (CloudEngine.isOnline) "Connected to Telegram Cloud" else "Offline mode", Toast.LENGTH_SHORT).show()
                    }) { Icon(if (CloudEngine.isOnline) Icons.Default.Cloud else Icons.Default.CloudOff, null, tint = if (CloudEngine.isOnline) Color(0xFF4CAF50) else Color.Gray) }
                }

                // Navigation tabs
                Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    listOf(
                        CloudViewMode.DRIVES to "Drives",
                        CloudViewMode.FILES to "Files",
                        CloudViewMode.RECENTS to "Recents",
                        CloudViewMode.SETTINGS to "Settings"
                    ).forEach { (mode, label) ->
                        FilterChip(selected = viewMode == mode, onClick = { viewMode = mode },
                            label = { Text(label, fontSize = 11.sp) },
                            colors = FilterChipDefaults.filterChipColors(selectedContainerColor = ZenGold.copy(alpha = 0.2f), selectedLabelColor = ZenGold))
                    }
                }
            }
        }

        // Storage bar
        if (totalStorage > 0) {
            val maxStorage = 10L * 1024 * 1024 * 1024 // 10GB visual max
            val pct = (totalStorage.toFloat() / maxStorage).coerceIn(0f, 1f)
            Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp), colors = CardDefaults.cardColors(containerColor = YinCardBg), shape = RoundedCornerShape(10.dp)) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                        Text("Storage", color = YinTextSecondary, fontSize = 11.sp)
                        Text(CloudEngine.formatSize(totalStorage), color = ZenGold, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                    LinearProgressIndicator(progress = { pct }, modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)), color = ZenGold, trackColor = YinBlack)
                    Text("${drives.size} drives • ${files.size} files", color = YinTextSecondary, fontSize = 10.sp)
                }
            }
        }

        // Upload queue
        AnimatedVisibility(visible = uploadQueue.isNotEmpty()) {
            Surface(color = ZenGold.copy(alpha = 0.05f)) {
                Column(modifier = Modifier.padding(8.dp)) {
                    Text("Uploading...", color = ZenGold, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    uploadQueue.take(3).forEach { task ->
                        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(if (task.status == UploadStatus.COMPLETED) Icons.Default.CheckCircle else Icons.Default.CloudUpload, null,
                                tint = if (task.status == UploadStatus.COMPLETED) Color(0xFF4CAF50) else ZenGold, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(task.fileName, color = YinText, fontSize = 11.sp, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            if (task.status == UploadStatus.UPLOADING) {
                                LinearProgressIndicator(progress = { task.progress }, modifier = Modifier.width(60.dp).height(3.dp).clip(RoundedCornerShape(2.dp)), color = ZenGold)
                                Text("${(task.progress * 100).toInt()}%", color = YinTextSecondary, fontSize = 10.sp)
                            } else {
                                Text("Done", color = Color(0xFF4CAF50), fontSize = 10.sp)
                            }
                        }
                    }
                }
            }
        }

        // Content
        Box(modifier = Modifier.weight(1f)) {
            when (viewMode) {
                CloudViewMode.DRIVES -> {
                    if (drives.isEmpty()) {
                        Column(modifier = Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                            Icon(Icons.Default.Cloud, null, modifier = Modifier.size(72.dp), tint = Color.Gray.copy(alpha = 0.4f))
                            Spacer(Modifier.height(12.dp))
                            Text("No Cloud Drives", color = Color.Gray, fontSize = 16.sp)
                            Text("Create your first Telegram-powered drive", color = Color.Gray.copy(alpha = 0.6f), fontSize = 12.sp)
                            Spacer(Modifier.height(16.dp))
                            Button(onClick = { showCreateDriveDialog = true }, colors = ButtonDefaults.buttonColors(containerColor = ZenGold)) {
                                Icon(Icons.Default.Add, null, modifier = Modifier.size(18.dp)); Text(" Create Drive", color = Color.Black)
                            }
                        }
                    } else {
                        LazyColumn(contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            item {
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("${drives.size} drives", color = YinTextSecondary, fontSize = 12.sp)
                                    TextButton(onClick = { showCreateDriveDialog = true }) { Text("+ New Drive", color = ZenGold, fontSize = 12.sp) }
                                }
                            }
                            items(drives, key = { it.id }) { drive ->
                                DriveCard(drive = drive, onClick = {
                                    selectedDrive = drive; currentFolder = "/"; viewMode = CloudViewMode.FILES
                                }, onLongClick = { showDriveDetailDialog = drive })
                            }
                        }
                    }
                }
                CloudViewMode.FILES -> {
                    if (selectedDrive == null) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("Select a drive first", color = Color.Gray) }
                    } else {
                        val driveFiles = files.filter { it.id.startsWith(selectedDrive!!.id) && (searchQuery.isBlank() || it.name.contains(searchQuery, ignoreCase = true)) }
                        Column(modifier = Modifier.fillMaxSize()) {
                            // Breadcrumb
                            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                                IconButton(onClick = { viewMode = CloudViewMode.DRIVES }) { Icon(Icons.Default.ArrowBack, null, tint = YinText, modifier = Modifier.size(20.dp)) }
                                Text("${selectedDrive!!.icon} ${selectedDrive!!.name} / $currentFolder", color = YinText, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                Spacer(Modifier.weight(1f))
                                IconButton(onClick = { showUploadDialog = true }) { Icon(Icons.Default.Add, "Upload", tint = ZenGold, modifier = Modifier.size(20.dp)) }
                            }
                            // Search
                            OutlinedTextField(value = searchQuery, onValueChange = { searchQuery = it },
                                placeholder = { Text("Search files...", fontSize = 12.sp, color = Color.Gray) },
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp).height(40.dp),
                                leadingIcon = { Icon(Icons.Default.Search, null, tint = Color.Gray, modifier = Modifier.size(16.dp)) },
                                singleLine = true, shape = RoundedCornerShape(10.dp),
                                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = ZenGold, unfocusedBorderColor = Color(0xFF333340)))
                            Spacer(Modifier.height(4.dp))

                            if (driveFiles.isEmpty()) {
                                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("No files in this drive", color = Color.Gray) }
                            } else {
                                LazyColumn(contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    items(driveFiles) { file ->
                                        CloudFileCard(file = file, onDownload = {
                                            Toast.makeText(context, "Downloading: ${file.name}", Toast.LENGTH_SHORT).show()
                                        }, onDelete = {
                                            CloudEngine.files.remove(file)
                                            Toast.makeText(context, "Deleted: ${file.name}", Toast.LENGTH_SHORT).show()
                                        }, onShare = {
                                            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                                type = file.mimeType.ifBlank { "*/*" }
                                                putExtra(Intent.EXTRA_TEXT, "Shared via Dao Cloud: ${file.name} (${CloudEngine.formatSize(file.size)})")
                                            }
                                            context.startActivity(Intent.createChooser(shareIntent, "Share file"))
                                        })
                                    }
                                }
                            }
                        }
                    }
                }
                CloudViewMode.RECENTS -> {
                    if (recentFiles.isEmpty()) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("No recent files", color = Color.Gray) }
                    } else {
                        LazyColumn(contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            items(recentFiles.take(20)) { file ->
                                CloudFileCard(file = file, onDownload = {}, onDelete = {}, onShare = {})
                            }
                        }
                    }
                }
                CloudViewMode.SETTINGS -> {
                    LazyColumn(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        item {
                            Card(colors = CardDefaults.cardColors(containerColor = YinCardBg), shape = RoundedCornerShape(12.dp)) {
                                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text("Cloud Settings", color = ZenGold, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                    Divider(color = Color(0xFF333340))
                                    SettingRow("Auto-sync", "Keep files in sync across devices", true)
                                    SettingRow("Wi-Fi only uploads", "Only upload on Wi-Fi connections", true)
                                    SettingRow("Encrypt files", "AES-256 encryption before upload", false)
                                    SettingRow("Thumbnail preview", "Generate thumbnails for images", true)
                                    SettingRow("Notifications", "Notify on upload/download completion", true)
                                }
                            }
                        }
                        item {
                            Card(colors = CardDefaults.cardColors(containerColor = YinCardBg), shape = RoundedCornerShape(12.dp)) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Text("About", color = ZenGold, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                    Divider(color = Color(0xFF333340))
                                    Text("Powered by Telegram MTProto", color = YinTextSecondary, fontSize = 12.sp)
                                    Text("Version 1.0 • Dao Cloud Engine", color = YinTextSecondary, fontSize = 11.sp)
                                    Text("Files are stored in your private Telegram channels", color = YinTextSecondary, fontSize = 10.sp)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ==================== COMPONENT CARDS ====================

@Composable
private fun DriveCard(drive: CloudDrive, onClick: () -> Unit, onLongClick: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).combinedClickable(onClick = onClick, onLongClick = onLongClick),
        colors = CardDefaults.cardColors(containerColor = YinCardBg), shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, Color(0xFF333340))) {
        Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.size(52.dp).clip(RoundedCornerShape(12.dp)).background(ZenGold.copy(alpha = 0.1f)), contentAlignment = Alignment.Center) {
                Text(drive.icon, fontSize = 28.sp)
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(drive.name, color = YinText, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                Text("${drive.totalFiles} files • ${CloudEngine.formatSize(drive.totalSize)}", color = YinTextSecondary, fontSize = 12.sp)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Synced: ${SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault()).format(Date(drive.lastSynced))}", color = YinTextSecondary, fontSize = 10.sp)
                    if (drive.isAutoBackup) { Spacer(Modifier.width(6.dp)); Surface(color = ZenGold.copy(alpha = 0.2f), shape = RoundedCornerShape(4.dp)) { Text("Auto", color = ZenGold, fontSize = 9.sp, modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp)) } }
                }
            }
            Icon(Icons.Default.ChevronRight, null, tint = YinTextSecondary, modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
private fun CloudFileCard(file: CloudFile, onDownload: () -> Unit, onDelete: () -> Unit, onShare: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = YinCardBg), shape = RoundedCornerShape(8.dp)) {
        Row(modifier = Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(CloudEngine.getFileIcon(file.name, file.mimeType), fontSize = 28.sp)
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(file.name, color = YinText, fontWeight = FontWeight.Medium, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("${CloudEngine.formatSize(file.size)} • ${SimpleDateFormat("MMM dd", Locale.getDefault()).format(Date(file.uploadedAt))}", color = YinTextSecondary, fontSize = 11.sp)
            }
            if (file.isDownloaded) Icon(Icons.Default.CheckCircle, null, tint = Color(0xFF4CAF50), modifier = Modifier.size(18.dp))
            else IconButton(onClick = onDownload, modifier = Modifier.size(28.dp)) { Icon(Icons.Default.Download, "Download", tint = ZenGold, modifier = Modifier.size(18.dp)) }
            IconButton(onClick = onShare, modifier = Modifier.size(28.dp)) { Icon(Icons.Default.Share, "Share", tint = YinTextSecondary.copy(alpha = 0.5f), modifier = Modifier.size(16.dp)) }
            IconButton(onClick = onDelete, modifier = Modifier.size(28.dp)) { Icon(Icons.Default.Delete, "Delete", tint = ZenRed.copy(alpha = 0.5f), modifier = Modifier.size(16.dp)) }
        }
    }
}

@Composable
private fun SettingRow(label: String, description: String, enabled: Boolean) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
        Column(modifier = Modifier.weight(1f)) { Text(label, color = YinText, fontSize = 13.sp); Text(description, color = YinTextSecondary, fontSize = 10.sp) }
        Switch(checked = enabled, onCheckedChange = { }, colors = SwitchDefaults.colors(checkedThumbColor = ZenGold, checkedTrackColor = ZenGold.copy(alpha = 0.3f)))
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = YinTextSecondary, fontSize = 11.sp); Text(value, color = YinText, fontSize = 11.sp)
    }
}

private fun getFileName(context: Context, uri: Uri): String {
    var name = "file_${System.currentTimeMillis()}"
    context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
        if (cursor.moveToFirst()) { val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME); if (idx >= 0) name = cursor.getString(idx) }
    }
    return name
}

private fun getFileSize(context: Context, uri: Uri): Long {
    var size = 0L
    context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
        if (cursor.moveToFirst()) { val idx = cursor.getColumnIndex(OpenableColumns.SIZE); if (idx >= 0) size = cursor.getLong(idx) }
    }
    return size
}

@OptIn(ExperimentalFoundationApi::class)
private fun Modifier.combinedClickable(onClick: () -> Unit, onLongClick: () -> Unit): Modifier = this
    .clickable(onClick = onClick)

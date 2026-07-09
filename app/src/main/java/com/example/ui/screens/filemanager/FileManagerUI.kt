package com.example.ui.screens.filemanager

import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.*
import java.io.File

// ==================== FILE ICON MAPPER ====================

object FileIconMapper {
    fun getIcon(extension: String, isDirectory: Boolean): ImageVector = when {
        isDirectory -> Icons.Default.Folder
        extension in listOf("jpg", "jpeg", "png", "gif", "bmp", "webp") -> Icons.Default.Image
        extension in listOf("mp4", "mkv", "avi", "mov", "flv") -> Icons.Default.VideoFile
        extension in listOf("mp3", "wav", "aac", "flac", "ogg") -> Icons.Default.MusicNote
        extension in listOf("pdf") -> Icons.Default.PictureAsPdf
        extension in listOf("zip", "rar", "7z", "tar", "gz") -> Icons.Default.Archive
        extension in listOf("apk") -> Icons.Default.Android
        extension in listOf("kt", "java", "py", "js", "ts") -> Icons.Default.Code
        extension in listOf("txt", "md", "log") -> Icons.Default.Description
        else -> Icons.Default.InsertDriveFile
    }

    fun getColor(extension: String, isDirectory: Boolean): Color = when {
        isDirectory -> ZenSienna
        extension in listOf("jpg", "jpeg", "png", "gif") -> Color(0xFF4FC3F7)
        extension in listOf("mp4", "mkv", "avi") -> ZenRed
        extension in listOf("mp3", "wav") -> Color(0xFFCE93D8)
        extension in listOf("zip", "rar", "7z") -> Color(0xFFFFD54F)
        extension in listOf("apk") -> Color(0xFF81C784)
        extension in listOf("pdf") -> Color(0xFFFF8A65)
        else -> ZenGold
    }
}

// ==================== STORAGE BAR ====================

@Composable
fun StorageBar(info: StorageInfo, modifier: Modifier = Modifier) {
    val usedPercent = if (info.totalBytes > 0) info.usedBytes.toFloat() / info.totalBytes else 0f
    Card(
        modifier = modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
        colors = CardDefaults.cardColors(containerColor = YinCardBg),
        shape = RoundedCornerShape(10.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(horizontalArrangement = Arrangement.SpaceBetween) {
                Text(info.label, color = YinTextSecondary, fontSize = 11.sp)
                Text(
                    FileOperations.formatSize(info.freeBytes) + " free",
                    color = ZenGold,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp
                )
            }
            LinearProgressIndicator(
                progress = { usedPercent },
                modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                color = when {
                    usedPercent > 0.9f -> ZenRed
                    usedPercent > 0.7f -> Color(0xFFFF9800)
                    else -> ZenGold
                },
                trackColor = YinBlack
            )
            Text(
                "${FileOperations.formatSize(info.usedBytes)} / ${FileOperations.formatSize(info.totalBytes)}",
                color = YinTextSecondary,
                fontSize = 10.sp
            )
        }
    }
}

// ==================== FILE ROW ====================

@Composable
fun FileRow(
    file: FileItem,
    isSelected: Boolean,
    selectionMode: Boolean,
    onTap: () -> Unit,
    onLongPress: () -> Unit,
    onMoreOptions: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onTap),
        color = if (isSelected) ZenGold.copy(alpha = 0.1f) else Color.Transparent
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Checkbox for selection
            if (selectionMode) {
                Checkbox(
                    checked = isSelected,
                    onCheckedChange = { onTap() },
                    colors = CheckboxDefaults.colors(checkedColor = ZenGold),
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.width(8.dp))
            }

            // Icon
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(FileIconMapper.getColor(file.extension, file.isDirectory).copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    FileIconMapper.getIcon(file.extension, file.isDirectory),
                    contentDescription = null,
                    tint = FileIconMapper.getColor(file.extension, file.isDirectory),
                    modifier = Modifier.size(22.dp)
                )
            }

            Spacer(Modifier.width(12.dp))

            // File info
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    file.name,
                    color = YinText,
                    fontWeight = FontWeight.Medium,
                    fontSize = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        if (file.isDirectory) "Folder" else FileOperations.formatSize(file.size),
                        color = YinTextSecondary,
                        fontSize = 11.sp
                    )
                    Text("•", color = YinTextSecondary, fontSize = 11.sp)
                    Text(
                        FileOperations.formatDate(file.lastModified),
                        color = YinTextSecondary,
                        fontSize = 11.sp
                    )
                    if (file.permissions.isNotEmpty()) {
                        Text("•", color = YinTextSecondary, fontSize = 11.sp)
                        Text(file.permissions, color = YinTextSecondary, fontSize = 11.sp)
                    }
                }
            }

            // More options
            IconButton(onClick = onMoreOptions, modifier = Modifier.size(32.dp)) {
                Icon(
                    Icons.Default.MoreVert,
                    "Options",
                    tint = YinTextSecondary.copy(alpha = 0.6f),
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

// ==================== ARCHIVE VIEWER DIALOG ====================

@Composable
fun ArchiveViewerDialog(
    archivePath: String,
    onDismiss: () -> Unit,
    onExtract: (String) -> Unit,
    onExtractSelected: (List<String>, String) -> Unit
) {
    val entries = remember { FileOperations.listArchiveContents(archivePath) }
    var selectedEntries by remember { mutableStateOf(setOf<String>()) }
    var extractPath by remember { mutableStateOf(File(archivePath).parent ?: "/sdcard") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("📦 ${File(archivePath).name}", color = ZenGold, fontFamily = FontFamily.Serif)
        },
        text = {
            Column(modifier = Modifier.heightIn(max = 400.dp)) {
                // Extract path selector
                OutlinedTextField(
                    value = extractPath,
                    onValueChange = { extractPath = it },
                    label = { Text("Extract to") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))

                // Select all toggle
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = selectedEntries.size == entries.size,
                        onCheckedChange = {
                            selectedEntries = if (it) entries.map { e -> e.path }.toSet() else emptySet()
                        }
                    )
                    Text("Select All (${entries.size})", color = YinTextSecondary, fontSize = 12.sp)
                }

                // Entry list
                LazyColumn {
                    items(entries) { entry ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    selectedEntries = if (entry.path in selectedEntries)
                                        selectedEntries - entry.path
                                    else selectedEntries + entry.path
                                }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = entry.path in selectedEntries,
                                onCheckedChange = {
                                    selectedEntries = if (it) selectedEntries + entry.path else selectedEntries - entry.path
                                }
                            )
                            Icon(
                                if (entry.isDirectory) Icons.Default.Folder else Icons.Default.Description,
                                null,
                                tint = if (entry.isDirectory) ZenSienna else ZenGold,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Column {
                                Text(
                                    entry.name,
                                    color = YinText,
                                    fontSize = 12.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    "${FileOperations.formatSize(entry.size)} • ${entry.method}",
                                    color = YinTextSecondary,
                                    fontSize = 10.sp
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = {
                    if (selectedEntries.isEmpty()) onExtract(extractPath)
                    else onExtractSelected(selectedEntries.toList(), extractPath)
                    onDismiss()
                }) { Text("Extract") }
                Button(onClick = { onExtract(extractPath); onDismiss() }) { Text("Extract All") }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Close") } }
    )
}

// ==================== COMPRESSION DIALOG ====================

@Composable
fun CompressionDialog(
    files: List<File>,
    onDismiss: () -> Unit,
    onCompress: (String, String, Int, String?) -> Unit
) {
    var archiveName by remember { mutableStateOf("archive_${System.currentTimeMillis()}") }
    var format by remember { mutableStateOf("zip") }
    var level by remember { mutableIntStateOf(5) }
    var password by remember { mutableStateOf("") }
    var outputPath by remember { mutableStateOf(files.firstOrNull()?.parent ?: "/sdcard") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("🗜 Compress ${files.size} items", color = ZenGold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = archiveName,
                    onValueChange = { archiveName = it },
                    label = { Text("Archive name") }
                )
                OutlinedTextField(
                    value = outputPath,
                    onValueChange = { outputPath = it },
                    label = { Text("Output path") }
                )

                Text("Format", color = YinTextSecondary, fontSize = 12.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("zip" to "ZIP", "7z" to "7Z").forEach { (fmt, label) ->
                        FilterChip(
                            selected = format == fmt,
                            onClick = { format = fmt },
                            label = { Text(label) }
                        )
                    }
                }

                Text("Compression Level: $level", color = YinTextSecondary, fontSize = 12.sp)
                Slider(
                    value = level.toFloat(),
                    onValueChange = { level = it.toInt() },
                    valueRange = 1f..9f,
                    steps = 7
                )

                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Password (optional)") },
                    singleLine = true
                )

                Text(
                    "${files.size} files selected • Total: ${FileOperations.formatSize(files.sumOf { it.length() })}",
                    color = YinTextSecondary,
                    fontSize = 10.sp
                )
            }
        },
        confirmButton = {
            Button(onClick = {
                val fullPath = "$outputPath/$archiveName.$format"
                onCompress(fullPath, format, level, password.ifBlank { null })
                onDismiss()
            }) { Text("Compress") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

// ==================== PROPERTIES DIALOG ====================

@Composable
fun PropertiesDialog(file: File, onDismiss: () -> Unit) {
    val props = remember { FileOperations.getFileProperties(file) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("📋 Properties", color = ZenGold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                props.forEach { (key, value) ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(key, color = YinTextSecondary, fontSize = 12.sp)
                        Text(
                            value,
                            color = YinText,
                            fontSize = 12.sp,
                            modifier = Modifier.widthIn(max = 200.dp)
                        )
                    }
                    Divider(color = Color(0xFF333340))
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } }
    )
}

// ==================== SORT DIALOG ====================

@Composable
fun SortDialog(
    currentMode: SortMode,
    currentAscending: Boolean,
    onDismiss: () -> Unit,
    onSortChanged: (SortMode, Boolean) -> Unit
) {
    var selectedMode by remember { mutableStateOf(currentMode) }
    var ascending by remember { mutableStateOf(currentAscending) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("📊 Sort Files", color = ZenGold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Sort By:", color = YinTextSecondary, fontSize = 12.sp)
                SortMode.values().forEach { mode ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selectedMode = mode }
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(mode.name.lowercase().capitalize(), color = YinText, fontSize = 13.sp)
                        RadioButton(
                            selected = selectedMode == mode,
                            onClick = { selectedMode = mode }
                        )
                    }
                }
                Divider(color = Color(0xFF333340), modifier = Modifier.padding(vertical = 8.dp))
                Text("Order:", color = YinTextSecondary, fontSize = 12.sp)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(
                            selected = ascending,
                            onClick = { ascending = true }
                        )
                        Text("Ascending", color = YinText, fontSize = 13.sp)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(
                            selected = !ascending,
                            onClick = { ascending = false }
                        )
                        Text("Descending", color = YinText, fontSize = 13.sp)
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                onSortChanged(selectedMode, ascending)
                onDismiss()
            }) { Text("Apply") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

// ==================== SEARCH RESULTS ====================

@Composable
fun SearchResults(
    results: List<FileItem>,
    onFileTap: (FileItem) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("🔍 Search Results (${results.size})", color = ZenGold) },
        text = {
            if (results.isEmpty()) {
                Text("No files found", color = YinTextSecondary, modifier = Modifier.padding(16.dp))
            } else {
                LazyColumn(modifier = Modifier.heightIn(max = 400.dp)) {
                    items(results) { file ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onFileTap(file) }
                                .padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                FileIconMapper.getIcon(file.extension, file.isDirectory),
                                null,
                                tint = FileIconMapper.getColor(file.extension, file.isDirectory),
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Column {
                                Text(file.name, color = YinText, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(file.path, color = YinTextSecondary, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } }
    )
}

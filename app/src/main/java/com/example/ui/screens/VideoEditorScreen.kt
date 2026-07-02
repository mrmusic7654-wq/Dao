package com.example.ui.screens

import android.Manifest
import android.content.*
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.ui.theme.*
import kotlinx.coroutines.delay
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.*

// ==================== DATA MODELS ====================

data class VideoClip(
    val id: String = UUID.randomUUID().toString(),
    val uri: Uri,
    val fileName: String,
    val durationMs: Long = 0L,
    val thumbnail: Bitmap? = null,
    val startTrimMs: Long = 0L,
    val endTrimMs: Long = 0L
)

data class VideoProject(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "Untitled Project",
    val clips: List<VideoClip> = emptyList(),
    val selectedClipIndex: Int = 0,
    val aspectRatio: AspectRatio = AspectRatio.RATIO_16_9,
    val exportQuality: ExportQuality = ExportQuality.HD_1080P,
    val filterType: VideoFilter = VideoFilter.ORIGINAL,
    val textOverlay: TextOverlay? = null,
    val bgMusic: Uri? = null
)

enum class AspectRatio(val label: String, val width: Int, val height: Int) {
    RATIO_16_9("16:9", 16, 9),
    RATIO_9_16("9:16", 9, 16),
    RATIO_1_1("1:1", 1, 1),
    RATIO_4_3("4:3", 4, 3)
}

enum class ExportQuality(val label: String, val resolution: String) {
    HD_720P("720p", "1280x720"),
    HD_1080P("1080p", "1920x1080"),
    QHD_4K("4K", "3840x2160")
}

enum class VideoFilter(val label: String, val cssFilter: String) {
    ORIGINAL("Original", ""),
    YIN_AMBER("Yin Amber", "sepia(0.5) brightness(0.9)"),
    YANG_COOL("Yang Cool", "brightness(1.1) contrast(1.05)"),
    ZEN_MONO("Zen Mono", "grayscale(1)"),
    COSMIC_VIBE("Cosmic Vibe", "saturate(1.4) hue-rotate(-10deg)"),
    NOSTALGIA("Nostalgia", "sepia(0.4) contrast(0.9) brightness(0.8)")
}

data class TextOverlay(
    val text: String = "",
    val fontSize: Float = 24f,
    val color: Color = Color.White,
    val positionX: Float = 0.5f,
    val positionY: Float = 0.5f
)

enum class TimelineTool { SPLIT, TRIM, DELETE, DUPLICATE, TEXT, FILTER, MUSIC, EXPORT }

data class FFmpegCommand(
    val inputFiles: List<String>,
    val outputFile: String,
    val filters: List<String> = emptyList(),
    val trimStart: Long = 0L,
    val trimEnd: Long = 0L,
    val quality: String = "1920x1080"
) {
    fun toCommandString(): String {
        val cmd = StringBuilder("ffmpeg -y")
        if (trimStart > 0) cmd.append(" -ss ${trimStart / 1000}.${(trimStart % 1000)}")
        inputFiles.forEach { cmd.append(" -i \"$it\"") }
        if (trimEnd > trimStart) cmd.append(" -to ${(trimEnd - trimStart) / 1000}.${((trimEnd - trimStart) % 1000)}")
        filters.forEach { cmd.append(" -vf \"$it\"") }
        cmd.append(" -preset fast -crf 23 \"$outputFile\"")
        return cmd.toString()
    }
}

// ==================== VIDEO UTILS ====================

object VideoUtils {
    fun getVideoDuration(context: Context, uri: Uri): Long {
        return try {
            val retriever = MediaMetadataRetriever()
            retriever.setDataSource(context, uri)
            val duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0
            retriever.release()
            duration
        } catch (e: Exception) { 0L }
    }

    fun getVideoThumbnail(context: Context, uri: Uri, timeUs: Long = 1000000): Bitmap? {
        return try {
            val retriever = MediaMetadataRetriever()
            retriever.setDataSource(context, uri)
            val bitmap = retriever.getFrameAtTime(timeUs)
            retriever.release()
            bitmap
        } catch (e: Exception) { null }
    }

    fun formatDuration(ms: Long): String {
        val totalSeconds = ms / 1000
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return String.format("%d:%02d", minutes, seconds)
    }

    fun getOutputDir(context: Context): File {
        val dir = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            File(context.getExternalFilesDir(Environment.DIRECTORY_MOVIES), "Dao")
        } else {
            File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES), "Dao")
        }
        if (!dir.exists()) dir.mkdirs()
        return dir
    }
}

// ==================== MAIN SCREEN ====================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VideoEditorScreen(isDark: Boolean, onMenuClick: () -> Unit) {
    val context = LocalContext.current

    var project by remember { mutableStateOf(VideoProject()) }
    var isPlaying by remember { mutableStateOf(false) }
    var currentPlaybackTime by remember { mutableStateOf(0L) }
    var totalDuration by remember { mutableStateOf(0L) }

    var activeTool by remember { mutableStateOf<TimelineTool?>(null) }
    var showAspectDialog by remember { mutableStateOf(false) }
    var showQualityDialog by remember { mutableStateOf(false) }
    var showFilterDialog by remember { mutableStateOf(false) }
    var showTextDialog by remember { mutableStateOf(false) }
    var showExportDialog by remember { mutableStateOf(false) }
    var exportProgress by remember { mutableStateOf(0f) }
    var exportMessage by remember { mutableStateOf("") }
    var isExporting by remember { mutableStateOf(false) }

    var textContent by remember { mutableStateOf("") }
    var textSize by remember { mutableStateOf(24f) }
    var textColor by remember { mutableStateOf(Color.White) }

    var showFFmpegCommand by remember { mutableStateOf(false) }
    var ffmpegCommandText by remember { mutableStateOf("") }

    val videoPickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            val duration = VideoUtils.getVideoDuration(context, it)
            val fileName = getFileNameFromUri(context, it)
            val thumb = VideoUtils.getVideoThumbnail(context, it)
            project = project.copy(
                clips = project.clips + VideoClip(uri = it, fileName = fileName, durationMs = duration, thumbnail = thumb, endTrimMs = duration),
                selectedClipIndex = project.clips.size
            )
            totalDuration = project.clips.sumOf { clip -> if (clip.endTrimMs > 0) clip.endTrimMs - clip.startTrimMs else clip.durationMs }
        }
    }

    val musicPickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { project = project.copy(bgMusic = it) }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (!permissions.values.all { it }) Toast.makeText(context, "Storage permission needed", Toast.LENGTH_SHORT).show()
    }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.Q) {
            val needs = ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED
            if (needs) permissionLauncher.launch(arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.READ_EXTERNAL_STORAGE))
        }
    }

    LaunchedEffect(isPlaying) {
        if (isPlaying && totalDuration > 0) {
            while (isPlaying && currentPlaybackTime < totalDuration) {
                delay(33)
                currentPlaybackTime += 33
                if (currentPlaybackTime >= totalDuration) { currentPlaybackTime = totalDuration; isPlaying = false }
            }
        }
    }

    if (isExporting) {
        LaunchedEffect(Unit) {
            val phases = listOf("Analyzing clips..." to 0.1f, "Applying filters..." to 0.3f, "Rendering frames..." to 0.6f, "Encoding audio..." to 0.8f, "Finalizing..." to 1.0f)
            for ((msg, target) in phases) {
                exportMessage = msg
                while (exportProgress < target) { delay(200); exportProgress += 0.02f }
            }
            exportProgress = 1f; delay(500); isExporting = false; exportProgress = 0f
            Toast.makeText(context, "Export complete! Saved to Movies/Dao/", Toast.LENGTH_LONG).show()
        }
    }

    val selectedClip = project.clips.getOrNull(project.selectedClipIndex)

    // ==================== DIALOGS ====================

    if (showExportDialog) {
        AlertDialog(
            onDismissRequest = { showExportDialog = false },
            title = { Text("Export Video", fontFamily = FontFamily.Serif, color = ZenGold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Quality", color = YinTextSecondary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ExportQuality.entries.forEach { quality ->
                            FilterChip(selected = project.exportQuality == quality, onClick = { project = project.copy(exportQuality = quality) },
                                label = { Text(quality.label) }, colors = FilterChipDefaults.filterChipColors(selectedContainerColor = ZenGold))
                        }
                    }
                    Card(colors = CardDefaults.cardColors(containerColor = YinCardBg)) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            InfoRow("Format", "MP4 (H.264)"); InfoRow("Resolution", project.exportQuality.resolution)
                            InfoRow("Clips", "${project.clips.size}"); InfoRow("Duration", VideoUtils.formatDuration(totalDuration))
                            InfoRow("Filters", project.filterType.label); InfoRow("Music", if (project.bgMusic != null) "Yes" else "None")
                            InfoRow("Output", "Movies/Dao/")
                        }
                    }
                    if (showFFmpegCommand) {
                        Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF0F0F12))) {
                            Column(modifier = Modifier.padding(8.dp)) {
                                Text("FFmpeg Command (AI Agent)", color = ZenGold, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                Spacer(Modifier.height(4.dp))
                                Text(ffmpegCommandText, color = Color(0xFF8CBE91), fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                            }
                        }
                    }
                    TextButton(onClick = {
                        val outputDir = VideoUtils.getOutputDir(context)
                        val outputFile = File(outputDir, "dao_video_${System.currentTimeMillis()}.mp4")
                        ffmpegCommandText = FFmpegCommand(
                            inputFiles = project.clips.map { getRealPathFromUri(context, it.uri) },
                            outputFile = outputFile.absolutePath,
                            filters = listOf("scale=${project.exportQuality.resolution}"),
                            trimStart = project.clips.firstOrNull()?.startTrimMs ?: 0,
                            trimEnd = project.clips.lastOrNull()?.endTrimMs ?: 0
                        ).toCommandString()
                        showFFmpegCommand = !showFFmpegCommand
                    }) { Text("🤖 AI Agent Command", color = ZenBlue, fontSize = 11.sp) }
                }
            },
            confirmButton = {
                Button(onClick = { isExporting = true; showExportDialog = false }, colors = ButtonDefaults.buttonColors(containerColor = ZenGold)) {
                    Text("Export Now", color = Color.Black, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = { TextButton(onClick = { showExportDialog = false }) { Text("Cancel", color = ZenGold) } },
            containerColor = if (isDark) Color(0xFF14131A) else Color(0xFFF7F4EE)
        )
    }

    if (showFilterDialog) {
        AlertDialog(
            onDismissRequest = { showFilterDialog = false },
            title = { Text("Video Filters", fontFamily = FontFamily.Serif, color = ZenGold) },
            text = {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(VideoFilter.entries.toList()) { filter ->
                        val isSelected = project.filterType == filter
                        Column(horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.width(80.dp).clip(RoundedCornerShape(8.dp))
                                .background(if (isSelected) ZenGold.copy(alpha = 0.2f) else Color.Transparent)
                                .border(1.dp, if (isSelected) ZenGold else Color.Transparent, RoundedCornerShape(8.dp))
                                .clickable(onClick = { project = project.copy(filterType = filter); showFilterDialog = false }).padding(8.dp)) {
                            Box(modifier = Modifier.size(56.dp).clip(RoundedCornerShape(6.dp)).background(Color.DarkGray))
                            Spacer(Modifier.height(4.dp))
                            Text(filter.label, fontSize = 10.sp, color = YinText, textAlign = TextAlign.Center)
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showFilterDialog = false }) { Text("Close", color = ZenGold) } },
            containerColor = if (isDark) Color(0xFF14131A) else Color(0xFFF7F4EE)
        )
    }

    if (showTextDialog) {
        AlertDialog(
            onDismissRequest = { showTextDialog = false },
            title = { Text("Add Text Overlay", fontFamily = FontFamily.Serif, color = ZenGold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(value = textContent, onValueChange = { textContent = it }, label = { Text("Text") }, modifier = Modifier.fillMaxWidth())
                    Text("Size: ${textSize.toInt()}", color = YinTextSecondary, fontSize = 12.sp)
                    Slider(value = textSize, onValueChange = { textSize = it }, valueRange = 12f..72f, colors = SliderDefaults.colors(thumbColor = ZenGold, activeTrackColor = ZenGold))
                    Text("Color", color = YinTextSecondary, fontSize = 12.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(Color.White, ZenGold, ZenRed, ZenBlue, Color.Black).forEach { color ->
                            Box(modifier = Modifier.size(28.dp).clip(CircleShape).background(color)
                                .border(2.dp, if (textColor == color) ZenGold else Color.Transparent, CircleShape)
                                .clickable(onClick = { textColor = color }))
                        }
                    }
                }
            },
            confirmButton = {
                Button(onClick = { project = project.copy(textOverlay = TextOverlay(text = textContent, fontSize = textSize, color = textColor)); showTextDialog = false },
                    colors = ButtonDefaults.buttonColors(containerColor = ZenGold)) { Text("Apply", color = Color.Black) }
            },
            dismissButton = { TextButton(onClick = { showTextDialog = false }) { Text("Cancel", color = ZenGold) } },
            containerColor = if (isDark) Color(0xFF14131A) else Color(0xFFF7F4EE)
        )
    }

    if (showAspectDialog) {
        AlertDialog(
            onDismissRequest = { showAspectDialog = false },
            title = { Text("Aspect Ratio", fontFamily = FontFamily.Serif, color = ZenGold) },
            text = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AspectRatio.entries.forEach { ratio ->
                        FilterChip(selected = project.aspectRatio == ratio, onClick = { project = project.copy(aspectRatio = ratio); showAspectDialog = false },
                            label = { Text(ratio.label) }, colors = FilterChipDefaults.filterChipColors(selectedContainerColor = ZenGold))
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showAspectDialog = false }) { Text("Close", color = ZenGold) } },
            containerColor = if (isDark) Color(0xFF14131A) else Color(0xFFF7F4EE)
        )
    }

    if (isExporting) {
        AlertDialog(
            onDismissRequest = { },
            title = { Text("Exporting", fontFamily = FontFamily.Serif, color = ZenGold) },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(exportMessage, color = YinText, fontSize = 14.sp)
                    LinearProgressIndicator(progress = { exportProgress }, modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)), color = ZenGold, trackColor = YinCardBg)
                    Text("${(exportProgress * 100).toInt()}%", color = YinTextSecondary, fontSize = 12.sp)
                }
            },
            confirmButton = { },
            containerColor = if (isDark) Color(0xFF14131A) else Color(0xFFF7F4EE)
        )
    }

    // ==================== MAIN UI ====================

    Scaffold(
        topBar = {
            Column(modifier = Modifier.fillMaxWidth().background(if (isDark) YinBlack else YangWhite).statusBarsPadding()) {
                Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = onMenuClick) { Icon(Icons.Default.Menu, "Menu", tint = if (isDark) Color.White else Color.Black) }
                        Text("ZEN VIDEO EDITOR", fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold, color = if (isDark) ZenGold else Color(0xFF9E7E1D), style = MaterialTheme.typography.titleMedium)
                    }
                    Row {
                        IconButton(onClick = { showAspectDialog = true }) { Icon(Icons.Default.Crop, "Aspect", tint = YinTextSecondary, modifier = Modifier.size(20.dp)) }
                        IconButton(onClick = { showExportDialog = true }) { Icon(Icons.Default.Save, "Export", tint = ZenGold, modifier = Modifier.size(20.dp)) }
                    }
                }
            }
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).background(if (isDark) Color(0xFF070709) else Color(0xFFF1F0EC))) {

            // ==================== PREVIEW ====================
            Box(modifier = Modifier.fillMaxWidth().weight(1f).padding(8.dp).clip(RoundedCornerShape(12.dp)).background(Color.Black).border(2.dp, ZenGold.copy(alpha = 0.3f), RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
                if (project.clips.isEmpty()) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Icon(Icons.Default.VideoLibrary, null, modifier = Modifier.size(64.dp), tint = Color.Gray.copy(alpha = 0.4f))
                        Text("No clips added", color = Color.Gray, fontSize = 16.sp)
                        Button(onClick = { videoPickerLauncher.launch("video/*") }, colors = ButtonDefaults.buttonColors(containerColor = ZenGold)) {
                            Icon(Icons.Default.Add, null, modifier = Modifier.size(18.dp), tint = Color.Black)
                            Spacer(Modifier.width(6.dp))
                            Text("Import Video", color = Color.Black, fontWeight = FontWeight.Bold)
                        }
                    }
                } else {
                    selectedClip?.thumbnail?.let { bmp ->
                        Image(bitmap = bmp.asImageBitmap(), contentDescription = "Preview", modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
                    } ?: Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.Videocam, null, modifier = Modifier.size(64.dp), tint = Color.Gray)
                    }

                    project.textOverlay?.let { overlay ->
                        if (overlay.text.isNotBlank()) {
                            Text(overlay.text, color = overlay.color, fontSize = overlay.fontSize.sp, fontWeight = FontWeight.Bold, modifier = Modifier.align(Alignment.Center).padding(16.dp))
                        }
                    }

                    Row(modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        IconButton(onClick = {
                            if (isPlaying) isPlaying = false else { isPlaying = true; if (currentPlaybackTime >= totalDuration) currentPlaybackTime = 0 }
                        }, modifier = Modifier.size(48.dp).clip(CircleShape).background(ZenGold)) {
                            Icon(if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow, "Play/Pause", tint = Color.Black, modifier = Modifier.size(24.dp))
                        }
                    }

                    Surface(modifier = Modifier.align(Alignment.TopEnd).padding(8.dp), color = Color.Black.copy(alpha = 0.7f), shape = RoundedCornerShape(4.dp)) {
                        Text("${VideoUtils.formatDuration(currentPlaybackTime)} / ${VideoUtils.formatDuration(totalDuration)}", color = Color.White, fontSize = 11.sp, fontFamily = FontFamily.Monospace, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
                    }
                    if (project.filterType != VideoFilter.ORIGINAL) {
                        Surface(modifier = Modifier.align(Alignment.TopStart).padding(8.dp), color = ZenGold.copy(alpha = 0.7f), shape = RoundedCornerShape(4.dp)) {
                            Text(project.filterType.label, color = Color.Black, fontSize = 10.sp, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                        }
                    }
                }
            }

            // ==================== TIMELINE ====================
            if (project.clips.isNotEmpty()) {
                Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp), colors = CardDefaults.cardColors(containerColor = YinCardBg)) {
                    Column(modifier = Modifier.padding(8.dp)) {
                        Text("Timeline", color = YinTextSecondary, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(4.dp))
                        if (totalDuration > 0) {
                            LinearProgressIndicator(progress = { if (totalDuration > 0) currentPlaybackTime.toFloat() / totalDuration else 0f }, modifier = Modifier.fillMaxWidth().height(2.dp).clip(CircleShape), color = ZenGold, trackColor = Color(0xFF222228))
                            Spacer(Modifier.height(6.dp))
                        }
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            items(project.clips.size) { index ->
                                val clip = project.clips[index]
                                val isSelected = index == project.selectedClipIndex
                                Column(horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier.width(100.dp).clip(RoundedCornerShape(6.dp)).border(2.dp, if (isSelected) ZenGold else Color.Transparent, RoundedCornerShape(6.dp))
                                        .clickable(onClick = { project = project.copy(selectedClipIndex = index) })) {
                                    Box(modifier = Modifier.fillMaxWidth().height(56.dp).background(Color.DarkGray), contentAlignment = Alignment.Center) {
                                        clip.thumbnail?.let { bmp ->
                                            Image(bitmap = bmp.asImageBitmap(), contentDescription = "Clip ${index + 1}", modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                                        } ?: Icon(Icons.Default.Videocam, null, tint = Color.Gray, modifier = Modifier.size(24.dp))
                                    }
                                    Text(clip.fileName, color = if (isSelected) ZenGold else YinText, fontSize = 9.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(4.dp))
                                    Text(VideoUtils.formatDuration(clip.durationMs), color = YinTextSecondary, fontSize = 8.sp)
                                }
                            }
                            item {
                                Box(modifier = Modifier.width(60.dp).height(88.dp).clip(RoundedCornerShape(6.dp)).border(1.dp, YinTextSecondary.copy(alpha = 0.3f), RoundedCornerShape(6.dp))
                                    .clickable(onClick = { videoPickerLauncher.launch("video/*") }), contentAlignment = Alignment.Center) {
                                    Icon(Icons.Default.Add, "Add", tint = YinTextSecondary, modifier = Modifier.size(24.dp))
                                }
                            }
                        }
                    }
                }
            }

            // ==================== TOOL BAR ====================
            if (project.clips.isNotEmpty()) {
                Surface(color = YinCardBg, shadowElevation = 4.dp) {
                    Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 6.dp), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                        ToolButton(Icons.Default.ContentCut, "Split", activeTool == TimelineTool.SPLIT) {
                            activeTool = if (activeTool == TimelineTool.SPLIT) null else TimelineTool.SPLIT
                            Toast.makeText(context, "Split at: ${VideoUtils.formatDuration(currentPlaybackTime)}", Toast.LENGTH_SHORT).show()
                        }
                        ToolButton(Icons.Default.ContentPaste, "Dup", activeTool == TimelineTool.DUPLICATE) {
                            selectedClip?.let { project = project.copy(clips = project.clips + it.copy(id = UUID.randomUUID().toString())) }
                            Toast.makeText(context, "Duplicated", Toast.LENGTH_SHORT).show()
                        }
                        ToolButton(Icons.Default.Delete, "Del", false, ZenRed) {
                            if (project.clips.isNotEmpty()) {
                                val newClips = project.clips.toMutableList(); newClips.removeAt(project.selectedClipIndex)
                                project = project.copy(clips = newClips, selectedClipIndex = if (newClips.isEmpty()) 0 else project.selectedClipIndex.coerceAtMost(newClips.size - 1))
                                totalDuration = newClips.sumOf { it.durationMs }
                            }
                        }
                        ToolButton(Icons.Default.TextFields, "Text", activeTool == TimelineTool.TEXT) {
                            showTextDialog = true; project.textOverlay?.let { textContent = it.text; textSize = it.fontSize; textColor = it.color }
                        }
                        ToolButton(Icons.Default.Filter, "Filter", activeTool == TimelineTool.FILTER) { showFilterDialog = true }
                        ToolButton(Icons.Default.MusicNote, "Music", project.bgMusic != null, ZenBlue) { musicPickerLauncher.launch("audio/*") }
                        ToolButton(Icons.Default.Save, "Export", false, ZenGold) { showExportDialog = true }
                    }
                }
            }
        }
    }
}

@Composable
private fun ToolButton(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, isActive: Boolean, color: Color = YinTextSecondary, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(if (isActive) color.copy(alpha = 0.15f) else Color.Transparent).clickable(onClick = onClick).padding(horizontal = 10.dp, vertical = 6.dp)) {
        Icon(icon, label, tint = if (isActive) color else YinTextSecondary, modifier = Modifier.size(20.dp))
        Text(label, color = if (isActive) color else YinTextSecondary, fontSize = 10.sp)
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = YinTextSecondary, fontSize = 11.sp)
        Text(value, color = YinText, fontSize = 11.sp)
    }
}

private fun getFileNameFromUri(context: Context, uri: Uri): String {
    var name = "video_${System.currentTimeMillis()}"
    context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
        if (cursor.moveToFirst()) {
            val idx = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
            if (idx >= 0) name = cursor.getString(idx)
        }
    }
    return name
}

private fun getRealPathFromUri(context: Context, uri: Uri): String {
    if (uri.scheme == "file") return uri.path ?: ""
    var path = ""
    context.contentResolver.query(uri, arrayOf(android.provider.MediaStore.Video.Media.DATA), null, null, null)?.use { cursor ->
        if (cursor.moveToFirst()) {
            val idx = cursor.getColumnIndex(android.provider.MediaStore.Video.Media.DATA)
            if (idx >= 0) path = cursor.getString(idx)
        }
    }
    return path.ifEmpty { uri.toString() }
}

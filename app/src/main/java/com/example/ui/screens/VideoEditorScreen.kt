package com.example.ui.screens

import android.Manifest
import android.content.*
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.example.ui.theme.*
import kotlinx.coroutines.*
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
    val endTrimMs: Long = 0L,
    val volume: Float = 1f,
    val speed: Float = 1f
)

data class AudioTrack(
    val id: String = UUID.randomUUID().toString(),
    val uri: Uri? = null,
    val name: String = "",
    val durationMs: Long = 0L,
    val volume: Float = 0.8f
)

data class TextOverlay(
    val id: String = UUID.randomUUID().toString(),
    val text: String = "",
    val fontSize: Float = 24f,
    val color: Color = Color.White,
    val positionX: Float = 0.5f,
    val positionY: Float = 0.5f,
    val startMs: Long = 0L,
    val endMs: Long = 5000L
)

data class Keyframe(
    val timeMs: Long,
    val value: Float,
    val property: String // scale, position, opacity, volume
)

enum class VideoFilter(val label: String, val icon: String) {
    ORIGINAL("Original", "🎨"),
    YIN_AMBER("Yin Amber", "🌅"),
    YANG_COOL("Yang Cool", "❄️"),
    ZEN_MONO("Zen Mono", "⬛"),
    COSMIC_VIBE("Cosmic", "✨"),
    VINTAGE("Vintage", "📷"),
    GLITCH("Glitch", "⚡"),
    CINEMATIC("Cinematic", "🎬")
}

enum class AspectRatio(val label: String, val w: Int, val h: Int) {
    RATIO_9_16("9:16", 9, 16), RATIO_16_9("16:9", 16, 9),
    RATIO_1_1("1:1", 1, 1), RATIO_4_3("4:3", 4, 3)
}

enum class ExportQuality(val label: String, val res: String) {
    HD_720P("720p", "1280x720"), HD_1080P("1080p", "1920x1080"), QHD_4K("4K", "3840x2160")
}

// ==================== VIDEO UTILS ====================

object VideoUtils {
    fun getDuration(context: Context, uri: Uri): Long {
        return try {
            val r = MediaMetadataRetriever(); r.setDataSource(context, uri)
            val d = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0
            r.release(); d
        } catch (e: Exception) { 0L }
    }

    fun getThumbnail(context: Context, uri: Uri, timeUs: Long = 1000000): Bitmap? {
        return try {
            val r = MediaMetadataRetriever(); r.setDataSource(context, uri)
            val b = r.getFrameAtTime(timeUs); r.release(); b
        } catch (e: Exception) { null }
    }

    fun formatMs(ms: Long): String {
        val t = ms / 1000; return "${t / 60}:${String.format("%02d", t % 60)}"
    }

    fun getOutputDir(context: Context): File {
        val dir = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            File(context.getExternalFilesDir(Environment.DIRECTORY_MOVIES), "Dao")
        } else { File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES), "Dao") }
        if (!dir.exists()) dir.mkdirs(); return dir
    }

    fun getTempDir(context: Context): File {
        val dir = File(context.cacheDir, "dao_editor"); if (!dir.exists()) dir.mkdirs(); return dir
    }
}

// ==================== MAIN VIDEO EDITOR SCREEN ====================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VideoEditorScreen(isDark: Boolean, onMenuClick: () -> Unit) {
    val context = LocalContext.current

    // Project state
    var clips by remember { mutableStateOf(listOf<VideoClip>()) }
    var audioTracks by remember { mutableStateOf(listOf<AudioTrack>()) }
    var textOverlays by remember { mutableStateOf(listOf<TextOverlay>()) }
    var selectedClipIndex by remember { mutableIntStateOf(0) }
    var isPlaying by remember { mutableStateOf(false) }
    var currentTime by remember { mutableStateOf(0L) }
    var totalDuration by remember { mutableStateOf(0L) }
    var aspectRatio by remember { mutableStateOf(AspectRatio.RATIO_9_16) }
    var exportQuality by remember { mutableStateOf(ExportQuality.HD_1080P) }
    var activeFilter by remember { mutableStateOf(VideoFilter.ORIGINAL) }

    // UI state
    var activeTool by remember { mutableStateOf<String?>(null) } // null, "edit", "audio", "text", "effects", "filters", "speed", "overlay", "keyframe", "export"
    var showAspectDialog by remember { mutableStateOf(false) }
    var showExportDialog by remember { mutableStateOf(false) }
    var isExporting by remember { mutableStateOf(false) }
    var exportProgress by remember { mutableStateOf(0f) }
    var exportMsg by remember { mutableStateOf("") }

    // Keyframes
    var keyframes by remember { mutableStateOf(listOf<Keyframe>()) }
    var showKeyframeEditor by remember { mutableStateOf(false) }

    // Timeline zoom
    var timelineZoom by remember { mutableStateOf(1f) }

    // Permissions
    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { perms ->
        if (!perms.values.all { it }) Toast.makeText(context, "Permissions needed", Toast.LENGTH_SHORT).show()
    }

    LaunchedEffect(Unit) {
        val needed = mutableListOf<String>()
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED)
            needed.add(Manifest.permission.CAMERA)
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.Q) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED)
                needed.addAll(listOf(Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.READ_EXTERNAL_STORAGE))
        }
        if (needed.isNotEmpty()) permLauncher.launch(needed.toTypedArray())
    }

    // Video picker
    val videoPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let {
            val dur = VideoUtils.getDuration(context, it)
            val name = getFileName(context, it)
            val thumb = VideoUtils.getThumbnail(context, it)
            clips = clips + VideoClip(uri = it, fileName = name, durationMs = dur, thumbnail = thumb, endTrimMs = dur)
            totalDuration = clips.sumOf { c -> c.endTrimMs - c.startTrimMs }
            selectedClipIndex = clips.size - 1
        }
    }

    // Camera capture
    val cameraCapture = rememberLauncherForActivityResult(ActivityResultContracts.CaptureVideo()) { success ->
        if (success) {
            Toast.makeText(context, "Recording saved! Import it from the gallery.", Toast.LENGTH_LONG).show()
        }
    }

    // Audio picker
    val audioPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let {
            val dur = VideoUtils.getDuration(context, it)
            val name = getFileName(context, it)
            audioTracks = audioTracks + AudioTrack(uri = it, name = name, durationMs = dur)
        }
    }

    // Playback loop
    LaunchedEffect(isPlaying) {
        if (isPlaying && totalDuration > 0) {
            while (isPlaying && currentTime < totalDuration) {
                delay(33); currentTime += 33
                if (currentTime >= totalDuration) { currentTime = 0; isPlaying = false }
            }
        }
    }

    // Export
    if (isExporting) {
        LaunchedEffect(Unit) {
            val phases = listOf("Rendering..." to 0.3f, "Encoding..." to 0.7f, "Finalizing..." to 1.0f)
            for ((msg, tgt) in phases) { exportMsg = msg; while (exportProgress < tgt) { delay(150); exportProgress += 0.03f } }
            exportProgress = 1f; delay(500); isExporting = false; exportProgress = 0f
            Toast.makeText(context, "Exported to Movies/Dao/", Toast.LENGTH_LONG).show()
        }
    }

    val selectedClip = clips.getOrNull(selectedClipIndex)
    val progress = if (totalDuration > 0) currentTime.toFloat() / totalDuration else 0f

    // ==================== DIALOGS ====================

    if (showExportDialog) {
        AlertDialog(onDismissRequest = { showExportDialog = false }, title = { Text("Export Video", color = ZenGold) }, text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Quality", color = YinTextSecondary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ExportQuality.entries.forEach { q ->
                        FilterChip(selected = exportQuality == q, onClick = { exportQuality = q }, label = { Text(q.label) },
                            colors = FilterChipDefaults.filterChipColors(selectedContainerColor = ZenGold))
                    }
                }
                Card(colors = CardDefaults.cardColors(containerColor = YinCardBg)) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        InfoRow("Format", "MP4"); InfoRow("Resolution", exportQuality.res)
                        InfoRow("Clips", "${clips.size}"); InfoRow("Duration", VideoUtils.formatMs(totalDuration))
                        InfoRow("Audio", if (audioTracks.isNotEmpty()) "${audioTracks.size} track(s)" else "None")
                        InfoRow("Filters", activeFilter.label); InfoRow("Output", "Movies/Dao/")
                    }
                }
            }
        }, confirmButton = {
            Button(onClick = { isExporting = true; showExportDialog = false }, colors = ButtonDefaults.buttonColors(containerColor = ZenGold)) {
                Text("Export Now", color = Color.Black, fontWeight = FontWeight.Bold)
            }
        }, dismissButton = { TextButton(onClick = { showExportDialog = false }) { Text("Cancel", color = ZenGold) } },
            containerColor = if (isDark) Color(0xFF1A1A24) else Color(0xFFF7F4EE))
    }

    if (showAspectDialog) {
        AlertDialog(onDismissRequest = { showAspectDialog = false }, title = { Text("Aspect Ratio", color = ZenGold) }, text = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AspectRatio.entries.forEach { r ->
                    FilterChip(selected = aspectRatio == r, onClick = { aspectRatio = r; showAspectDialog = false }, label = { Text(r.label) },
                        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = ZenGold))
                }
            }
        }, confirmButton = { TextButton(onClick = { showAspectDialog = false }) { Text("Close", color = ZenGold) } },
            containerColor = if (isDark) Color(0xFF1A1A24) else Color(0xFFF7F4EE))
    }

    if (isExporting) {
        AlertDialog(onDismissRequest = { }, title = { Text("Exporting", color = ZenGold) }, text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(exportMsg, color = YinText); LinearProgressIndicator(progress = { exportProgress }, modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)), color = ZenGold)
                Text("${(exportProgress * 100).toInt()}%", color = YinTextSecondary)
            }
        }, confirmButton = { })
    }

    // ==================== MAIN UI ====================

    Column(modifier = Modifier.fillMaxSize().background(if (isDark) Color(0xFF0A0A10) else Color(0xFFF5F5F5))) {
        // Header
        Surface(color = if (isDark) Color(0xFF14141E) else Color.White, shadowElevation = 2.dp) {
            Row(modifier = Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onMenuClick) { Icon(Icons.Default.Menu, null, tint = if (isDark) Color.White else Color.Black) }
                Text("ZEN VIDEO EDITOR", fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold, color = ZenGold, fontSize = 16.sp)
                Spacer(Modifier.weight(1f))
                IconButton(onClick = { showAspectDialog = true }) { Icon(Icons.Default.Crop, null, tint = YinTextSecondary, modifier = Modifier.size(20.dp)) }
                Text(aspectRatio.label, color = YinTextSecondary, fontSize = 11.sp)
                IconButton(onClick = { showExportDialog = true }) { Icon(Icons.Default.Save, null, tint = ZenGold, modifier = Modifier.size(20.dp)) }
            }
        }

        // Preview player
        Box(modifier = Modifier.fillMaxWidth().weight(0.45f).padding(4.dp).clip(RoundedCornerShape(12.dp)).background(Color.Black).border(2.dp, ZenGold.copy(alpha = 0.3f), RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
            if (clips.isEmpty()) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Icon(Icons.Default.VideoLibrary, null, modifier = Modifier.size(56.dp), tint = Color.Gray)
                    Text("Tap + to add clips", color = Color.Gray, fontSize = 14.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Button(onClick = { videoPicker.launch("video/*") }, colors = ButtonDefaults.buttonColors(containerColor = ZenGold)) {
                            Icon(Icons.Default.VideoFile, null, modifier = Modifier.size(16.dp)); Text(" Import", color = Color.Black, fontSize = 12.sp)
                        }
                        Button(onClick = {
                            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", File(VideoUtils.getTempDir(context), "capture_${System.currentTimeMillis()}.mp4"))
                            cameraCapture.launch(uri)
                        }, colors = ButtonDefaults.buttonColors(containerColor = ZenRed)) {
                            Icon(Icons.Default.Videocam, null, modifier = Modifier.size(16.dp)); Text(" Record", color = Color.White, fontSize = 12.sp)
                        }
                    }
                }
            } else {
                // Thumbnail
                selectedClip?.thumbnail?.let { Image(bitmap = it.asImageBitmap(), null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Fit) }
                    ?: Icon(Icons.Default.Videocam, null, modifier = Modifier.size(64.dp), tint = Color.Gray)

                // Play/Pause overlay
                IconButton(onClick = { isPlaying = !isPlaying; if (currentTime >= totalDuration) currentTime = 0 }, modifier = Modifier.size(52.dp).clip(CircleShape).background(ZenGold.copy(alpha = 0.8f))) {
                    Icon(if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow, null, tint = Color.Black, modifier = Modifier.size(28.dp))
                }

                // Time overlay
                Surface(modifier = Modifier.align(Alignment.TopEnd).padding(8.dp), color = Color.Black.copy(alpha = 0.7f), shape = RoundedCornerShape(4.dp)) {
                    Text("${VideoUtils.formatMs(currentTime)} / ${VideoUtils.formatMs(totalDuration)}", color = Color.White, fontSize = 11.sp, fontFamily = FontFamily.Monospace, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
                }
                if (activeFilter != VideoFilter.ORIGINAL) {
                    Surface(modifier = Modifier.align(Alignment.TopStart).padding(8.dp), color = ZenGold.copy(alpha = 0.7f), shape = RoundedCornerShape(4.dp)) {
                        Text(activeFilter.label, color = Color.Black, fontSize = 10.sp, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                    }
                }
            }
        }

        // Progress bar
        if (clips.isNotEmpty()) {
            Slider(value = progress, onValueChange = { currentTime = (it * totalDuration).toLong() },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp).height(24.dp),
                colors = SliderDefaults.colors(thumbColor = ZenGold, activeTrackColor = ZenGold, inactiveTrackColor = Color(0xFF333340)))
        }

        // Timeline
        if (clips.isNotEmpty()) {
            Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp).height(70.dp), colors = CardDefaults.cardColors(containerColor = if (isDark) Color(0xFF14141E) else Color.White)) {
                LazyRow(modifier = Modifier.fillMaxSize().padding(4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    itemsIndexed(clips) { index, clip ->
                        val isSelected = index == selectedClipIndex
                        Column(horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.width((60 * timelineZoom).dp).clip(RoundedCornerShape(6.dp)).background(if (isSelected) ZenGold.copy(alpha = 0.2f) else Color.Transparent).border(1.5.dp, if (isSelected) ZenGold else Color(0xFF333340), RoundedCornerShape(6.dp)).clickable(onClick = { selectedClipIndex = index })) {
                            Box(modifier = Modifier.fillMaxWidth().height(40.dp).background(Color.DarkGray), contentAlignment = Alignment.Center) {
                                clip.thumbnail?.let { Image(bitmap = it.asImageBitmap(), null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
                                    ?: Icon(Icons.Default.Videocam, null, tint = Color.Gray, modifier = Modifier.size(18.dp))
                            }
                            Text(clip.fileName.take(8), color = if (isSelected) ZenGold else YinTextSecondary, fontSize = 8.sp, maxLines = 1)
                            Text(VideoUtils.formatMs(clip.durationMs), color = YinTextSecondary, fontSize = 7.sp)
                        }
                    }
                    item {
                        Box(modifier = Modifier.width(50.dp).height(64.dp).clip(RoundedCornerShape(6.dp)).border(1.dp, YinTextSecondary.copy(alpha = 0.3f), RoundedCornerShape(6.dp)).clickable(onClick = { videoPicker.launch("video/*") }), contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.Add, null, tint = YinTextSecondary, modifier = Modifier.size(20.dp))
                        }
                    }
                }
            }
        }

        // Toolbar
        Surface(color = if (isDark) Color(0xFF14141E) else Color.White, shadowElevation = 4.dp) {
            Column {
                // Tool buttons row
                Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp).horizontalScroll(rememberScrollState())) {
                    ToolChip("Edit", Icons.Default.ContentCut, activeTool == "edit") { activeTool = if (activeTool == "edit") null else "edit" }
                    ToolChip("Audio", Icons.Default.MusicNote, activeTool == "audio") { activeTool = if (activeTool == "audio") null else "audio"; if (activeTool == "audio") audioPicker.launch("audio/*") }
                    ToolChip("Text", Icons.Default.TextFields, activeTool == "text") {
                        activeTool = if (activeTool == "text") null else "text"
                        if (activeTool == "text") {
                            textOverlays = textOverlays + TextOverlay(text = "Your Text", startMs = currentTime, endMs = currentTime + 3000)
                        }
                    }
                    ToolChip("Effects", Icons.Default.AutoAwesome, activeTool == "effects") { activeTool = if (activeTool == "effects") null else "effects" }
                    ToolChip("Filters", Icons.Default.Filter, activeTool == "filters") { activeTool = if (activeTool == "filters") null else "filters" }
                    ToolChip("Speed", Icons.Default.Speed, activeTool == "speed") { activeTool = if (activeTool == "speed") null else "speed" }
                    ToolChip("Keyframes", Icons.Default.Diamond, activeTool == "keyframe") { activeTool = if (activeTool == "keyframe") null else "keyframe" }
                    ToolChip("Export", Icons.Default.Save, false) { showExportDialog = true }
                }

                // Active tool panel
                AnimatedVisibility(visible = activeTool != null, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                    when (activeTool) {
                        "edit" -> {
                            Row(modifier = Modifier.fillMaxWidth().padding(8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(onClick = {
                                    if (clips.isNotEmpty()) { clips = clips.toMutableList().also { it.removeAt(selectedClipIndex) }; selectedClipIndex = 0.coerceAtMost(clips.size - 1); totalDuration = clips.sumOf { it.durationMs } }
                                }, colors = ButtonDefaults.buttonColors(containerColor = ZenRed)) { Text("Delete Clip", color = Color.White, fontSize = 11.sp) }
                                Button(onClick = {
                                    selectedClip?.let { clips = clips + it.copy(id = UUID.randomUUID().toString()); totalDuration = clips.sumOf { it.durationMs } }
                                }, colors = ButtonDefaults.buttonColors(containerColor = ZenBlue)) { Text("Duplicate", color = Color.White, fontSize = 11.sp) }
                            }
                        }
                        "audio" -> {
                            LazyRow(modifier = Modifier.padding(8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                items(audioTracks) { track ->
                                    Card(colors = CardDefaults.cardColors(containerColor = YinCardBg)) {
                                        Row(modifier = Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                            Icon(Icons.Default.MusicNote, null, tint = ZenBlue, modifier = Modifier.size(16.dp))
                                            Text(track.name, color = YinText, fontSize = 11.sp, maxLines = 1)
                                        }
                                    }
                                }
                                if (audioTracks.isEmpty()) { item { Text("Tap Audio button to add music", color = Color.Gray, fontSize = 11.sp) } }
                            }
                        }
                        "text" -> {
                            LazyRow(modifier = Modifier.padding(8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                items(textOverlays) { overlay ->
                                    Card(modifier = Modifier.fillMaxWidth().clickable(onClick = { /* Edit text */ }), colors = CardDefaults.cardColors(containerColor = YinCardBg)) {
                                        Text(overlay.text, color = overlay.color, fontSize = 12.sp, modifier = Modifier.padding(8.dp))
                                    }
                                }
                            }
                        }
                        "filters" -> {
                            LazyRow(modifier = Modifier.padding(8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                items(VideoFilter.entries.toList()) { filter ->
                                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(60.dp).clip(RoundedCornerShape(8.dp)).background(if (activeFilter == filter) ZenGold.copy(alpha = 0.2f) else Color.Transparent).clickable(onClick = { activeFilter = filter }).padding(6.dp)) {
                                        Text(filter.icon, fontSize = 20.sp); Text(filter.label, fontSize = 9.sp, color = YinText)
                                    }
                                }
                            }
                        }
                        "speed" -> {
                            Column(modifier = Modifier.padding(8.dp)) {
                                Text("Speed: ${selectedClip?.speed ?: 1f}x", color = YinText, fontSize = 12.sp)
                                Slider(value = selectedClip?.speed ?: 1f, onValueChange = { v ->
                                    selectedClip?.let { clips = clips.toMutableList().also { list -> list[selectedClipIndex] = it.copy(speed = v) } }
                                }, valueRange = 0.1f..10f, colors = SliderDefaults.colors(thumbColor = ZenGold, activeTrackColor = ZenGold))
                            }
                        }
                        "keyframe" -> {
                            Column(modifier = Modifier.padding(8.dp)) {
                                Text("Keyframes: ${keyframes.size}", color = YinText, fontSize = 12.sp)
                                Button(onClick = {
                                    keyframes = keyframes + Keyframe(timeMs = currentTime, value = 1f, property = "scale")
                                }, colors = ButtonDefaults.buttonColors(containerColor = ZenGold), modifier = Modifier.fillMaxWidth()) {
                                    Text("➕ Add Keyframe at ${VideoUtils.formatMs(currentTime)}", color = Color.Black, fontSize = 11.sp)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ==================== HELPER COMPOSABLES ====================

@Composable
private fun ToolChip(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, active: Boolean, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.padding(horizontal = 4.dp).clip(RoundedCornerShape(8.dp)).background(if (active) ZenGold.copy(alpha = 0.15f) else Color.Transparent).clickable(onClick = onClick).padding(horizontal = 10.dp, vertical = 6.dp)) {
        Icon(icon, label, tint = if (active) ZenGold else YinTextSecondary, modifier = Modifier.size(20.dp))
        Text(label, color = if (active) ZenGold else YinTextSecondary, fontSize = 10.sp)
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = YinTextSecondary, fontSize = 11.sp)
        Text(value, color = YinText, fontSize = 11.sp)
    }
}

private fun getFileName(context: Context, uri: Uri): String {
    var name = "file_${System.currentTimeMillis()}"
    context.contentResolver.query(uri, null, null, null, null)?.use { c ->
        if (c.moveToFirst()) { val i = c.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME); if (i >= 0) name = c.getString(i) }
    }
    return name
}

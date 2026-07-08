package com.example.ui.screens

import android.Manifest
import android.content.*
import android.content.pm.PackageManager
import android.graphics.*
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.*
import android.media.projection.*
import android.net.Uri
import android.os.*
import android.provider.MediaStore
import android.util.DisplayMetrics
import android.view.Surface
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
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.ui.theme.*
import kotlinx.coroutines.*
import java.io.*
import java.text.SimpleDateFormat
import java.util.*

// ==================== DATA MODELS ====================

data class RecordingConfig(
    val resolution: RecordingResolution = RecordingResolution.HD_1080P,
    val frameRate: Int = 30,
    val bitRate: Int = 8_000_000,
    val audioSource: AudioSource = AudioSource.INTERNAL,
    val showCamera: Boolean = false,
    val cameraPosition: CameraPosition = CameraPosition.TOP_RIGHT,
    val cameraSize: CameraSize = CameraSize.SMALL,
    val showTouches: Boolean = true,
    val countdown: Int = 3,
    val outputFormat: OutputFormat = OutputFormat.MP4,
    val recordDuration: RecordDuration = RecordDuration.UNLIMITED
)

enum class RecordingResolution(val label: String, val width: Int, val height: Int, val bitrate: Int) {
    SD_480P("480p", 720, 480, 2_000_000),
    HD_720P("720p", 1280, 720, 5_000_000),
    HD_1080P("1080p", 1920, 1080, 8_000_000),
    QHD_1440P("1440p", 2560, 1440, 16_000_000),
    NATIVE("Native", 0, 0, 12_000_000)
}

enum class AudioSource(val label: String) { INTERNAL("Internal"), MIC("Microphone"), BOTH("Both"), NONE("None") }
enum class CameraPosition(val label: String) { TOP_RIGHT("Top Right"), TOP_LEFT("Top Left"), BOTTOM_RIGHT("Bottom Right"), BOTTOM_LEFT("Bottom Left") }
enum class CameraSize(val label: String, val size: Float) { SMALL("Small", 0.15f), MEDIUM("Medium", 0.25f), LARGE("Large", 0.35f) }
enum class OutputFormat(val label: String, val extension: String) { MP4("MP4", ".mp4"), GIF("GIF", ".gif"), WEBM("WebM", ".webm") }
enum class RecordDuration(val label: String, val seconds: Int) { UNLIMITED("Unlimited", 0), MIN_1("1 min", 60), MIN_5("5 min", 300), MIN_15("15 min", 900), MIN_30("30 min", 1800) }

data class SavedRecording(
    val id: String = UUID.randomUUID().toString(),
    val fileName: String,
    val filePath: String,
    val durationMs: Long = 0L,
    val sizeBytes: Long = 0L,
    val resolution: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val format: OutputFormat = OutputFormat.MP4
)

data class RecordingState(
    var isRecording: Boolean = false,
    var isPaused: Boolean = false,
    var elapsedMs: Long = 0L,
    var fileSize: Long = 0L,
    var statusText: String = "Ready",
    var countdownValue: Int = 0
)

// ==================== SCREEN RECORDER ENGINE ====================

object ScreenRecorderEngine {
    var mediaProjection: MediaProjection? = null
    var virtualDisplay: VirtualDisplay? = null
    var mediaRecorder: MediaRecorder? = null
    var outputFile: File? = null
    val recordings = mutableStateListOf<SavedRecording>()
    val state = mutableStateOf(RecordingState())

    fun getOutputDir(context: Context): File {
        val dir = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            File(context.getExternalFilesDir(Environment.DIRECTORY_MOVIES), "Dao/Recordings")
        } else {
            File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES), "Dao/Recordings")
        }
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    fun startRecording(context: Context, config: RecordingConfig, projection: MediaProjection) {
        mediaProjection = projection
        val dir = getOutputDir(context)
        val ts = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val fileName = "dao_recording_$ts${config.outputFormat.extension}"
        outputFile = File(dir, fileName)

        val metrics = DisplayMetrics()
        val wm = context.getSystemService(Context.WINDOW_SERVICE) as android.view.WindowManager
        wm.defaultDisplay.getRealMetrics(metrics)

        val width = if (config.resolution == RecordingResolution.NATIVE) metrics.widthPixels else config.resolution.width
        val height = if (config.resolution == RecordingResolution.NATIVE) metrics.heightPixels else config.resolution.height

        mediaRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(context)
        } else {
            @Suppress("DEPRECATION")
            MediaRecorder()
        }.apply {
            setVideoSource(MediaRecorder.VideoSource.SURFACE)
            if (config.audioSource != AudioSource.NONE) setAudioSource(MediaRecorder.AudioSource.MIC)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            setVideoEncoder(MediaRecorder.VideoEncoder.H264)
            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            setVideoSize(width, height)
            setVideoFrameRate(config.frameRate)
            setVideoEncodingBitRate(config.bitRate)
            setOutputFile(outputFile!!.absolutePath)
            prepare()
        }

        virtualDisplay = projection.createVirtualDisplay(
            "DaoScreenRecorder", width, height, metrics.densityDpi,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            mediaRecorder!!.surface, null, null
        )

        mediaRecorder!!.start()
        state.value = RecordingState(isRecording = true, statusText = "Recording...")
        startTimer(config)
    }

    private fun startTimer(config: RecordingConfig) {
        Thread {
            val startTime = System.currentTimeMillis()
            while (state.value.isRecording) {
                Thread.sleep(100)
                val elapsed = System.currentTimeMillis() - startTime
                val fileSize = outputFile?.length() ?: 0L
                state.value = state.value.copy(elapsedMs = elapsed, fileSize = fileSize)

                if (config.recordDuration != RecordDuration.UNLIMITED) {
                    val maxMs = config.recordDuration.seconds * 1000L
                    if (elapsed >= maxMs) {
                        // Auto-stop via handler
                        break
                    }
                }
            }
        }.start()
    }

    fun pauseRecording() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            mediaRecorder?.pause()
        }
        state.value = state.value.copy(isPaused = true, statusText = "Paused")
    }

    fun resumeRecording() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            mediaRecorder?.resume()
        }
        state.value = state.value.copy(isPaused = false, statusText = "Recording...")
    }

    fun stopRecording(): SavedRecording? {
        try {
            mediaRecorder?.stop()
            mediaRecorder?.release()
        } catch (e: Exception) {}

        virtualDisplay?.release()
        mediaProjection?.stop()

        state.value = RecordingState(statusText = "Processing...")

        val file = outputFile
        if (file != null && file.exists()) {
            val recording = SavedRecording(
                fileName = file.name,
                filePath = file.absolutePath,
                durationMs = state.value.elapsedMs,
                sizeBytes = file.length()
            )
            recordings.add(0, recording)
            state.value = RecordingState(statusText = "Saved!")
            return recording
        }

        state.value = RecordingState(statusText = "Failed")
        return null
    }

    fun deleteRecording(recording: SavedRecording) {
        try { File(recording.filePath).delete() } catch (e: Exception) {}
        recordings.remove(recording)
    }

    fun shareRecording(context: Context, recording: SavedRecording) {
        val uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            androidx.core.content.FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", File(recording.filePath))
        } else {
            Uri.fromFile(File(recording.filePath))
        }
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "video/*"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Share Recording"))
    }

    fun formatDuration(ms: Long): String {
        val seconds = ms / 1000
        val minutes = seconds / 60
        val hours = minutes / 60
        return if (hours > 0) String.format("%d:%02d:%02d", hours, minutes % 60, seconds % 60)
        else String.format("%02d:%02d", minutes % 60, seconds % 60)
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
fun ScreenRecorderScreen(isDark: Boolean, onMenuClick: () -> Unit) {
    val context = LocalContext.current
    var config by remember { mutableStateOf(RecordingConfig()) }
    var showConfigDialog by remember { mutableStateOf(false) }
    var showGifConvertDialog by remember { mutableStateOf<SavedRecording?>(null) }
    var showCountdown by remember { mutableStateOf(false) }
    var countdownValue by remember { mutableIntStateOf(3) }
    var currentView by remember { mutableStateOf(0) } // 0=Recorder, 1=Library

    val engine = ScreenRecorderEngine
    val state = engine.state.value
    val recordings = engine.recordings

    val mediaProjectionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK && result.data != null) {
            val projection = (context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager)
                .getMediaProjection(result.resultCode, result.data!!)
            // Countdown then start
            showCountdown = true; countdownValue = config.countdown
            CoroutineScope(Dispatchers.Main).launch {
                for (i in countdownValue downTo 1) { countdownValue = i; delay(1000) }
                showCountdown = false
                if (projection != null) {
                    engine.startRecording(context, config, projection)
                }
            }
        }
    }

    val audioPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            val projectionManager = context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            mediaProjectionLauncher.launch(projectionManager.createScreenCaptureIntent())
        } else { Toast.makeText(context, "Audio permission needed", Toast.LENGTH_SHORT).show() }
    }

    fun startScreenCapture() {
        if (config.audioSource != AudioSource.NONE && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        } else {
            val projectionManager = context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            mediaProjectionLauncher.launch(projectionManager.createScreenCaptureIntent())
        }
    }

    // ==================== CONFIG DIALOG ====================
    if (showConfigDialog) {
        AlertDialog(
            onDismissRequest = { showConfigDialog = false },
            title = { Text("Recording Settings", fontFamily = FontFamily.Serif, color = ZenGold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.heightIn(max = 450.dp).verticalScroll(rememberScrollState())) {
                    Text("Resolution", color = YinTextSecondary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(RecordingResolution.entries.toList()) { res ->
                            FilterChip(selected = config.resolution == res, onClick = { config = config.copy(resolution = res) },
                                label = { Text(res.label, fontSize = 10.sp) })
                        }
                    }
                    Text("Frame Rate: ${config.frameRate} fps", color = YinTextSecondary, fontSize = 12.sp)
                    Slider(value = config.frameRate.toFloat(), onValueChange = { config = config.copy(frameRate = it.toInt()) },
                        valueRange = 15f..60f, steps = 8, colors = SliderDefaults.colors(thumbColor = ZenGold, activeTrackColor = ZenGold))
                    Text("Bitrate: ${config.bitRate / 1_000_000} Mbps", color = YinTextSecondary, fontSize = 12.sp)
                    Slider(value = config.bitRate.toFloat(), onValueChange = { config = config.copy(bitRate = it.toInt()) },
                        valueRange = 1_000_000f..20_000_000f, steps = 18, colors = SliderDefaults.colors(thumbColor = ZenGold, activeTrackColor = ZenGold))
                    Text("Audio", color = YinTextSecondary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(AudioSource.entries.toList()) { src ->
                            FilterChip(selected = config.audioSource == src, onClick = { config = config.copy(audioSource = src) },
                                label = { Text(src.label, fontSize = 10.sp) })
                        }
                    }
                    Text("Duration", color = YinTextSecondary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(RecordDuration.entries.toList()) { dur ->
                            FilterChip(selected = config.recordDuration == dur, onClick = { config = config.copy(recordDuration = dur) },
                                label = { Text(dur.label, fontSize = 10.sp) })
                        }
                    }
                    Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                        Text("Show camera overlay", color = YinText, fontSize = 13.sp)
                        Switch(checked = config.showCamera, onCheckedChange = { config = config.copy(showCamera = it) },
                            colors = SwitchDefaults.colors(checkedThumbColor = ZenGold, checkedTrackColor = ZenGold.copy(alpha = 0.3f)))
                    }
                    Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                        Text("Show touch indicators", color = YinText, fontSize = 13.sp)
                        Switch(checked = config.showTouches, onCheckedChange = { config = config.copy(showTouches = it) },
                            colors = SwitchDefaults.colors(checkedThumbColor = ZenGold, checkedTrackColor = ZenGold.copy(alpha = 0.3f)))
                    }
                    Text("Countdown: ${config.countdown}s", color = YinTextSecondary, fontSize = 12.sp)
                    Slider(value = config.countdown.toFloat(), onValueChange = { config = config.copy(countdown = it.toInt()) },
                        valueRange = 0f..10f, steps = 9, colors = SliderDefaults.colors(thumbColor = ZenGold, activeTrackColor = ZenGold))
                }
            },
            confirmButton = { Button(onClick = { showConfigDialog = false }, colors = ButtonDefaults.buttonColors(containerColor = ZenGold)) { Text("Done", color = Color.Black) } },
            containerColor = if (isDark) Color(0xFF14131A) else Color(0xFFF7F4EE)
        )
    }

    // ==================== COUNTDOWN OVERLAY ====================
    if (showCountdown) {
        Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.85f)).clickable(onClick = { }), contentAlignment = Alignment.Center) {
            Text("$countdownValue", color = ZenGold, fontSize = 120.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
        }
    }

    // ==================== MAIN UI ====================

    Column(modifier = Modifier.fillMaxSize().background(if (isDark) Color(0xFF070709) else Color(0xFFF1F0EC))) {
        // Header
        Surface(color = if (isDark) YinBlack else YangWhite, shadowElevation = 4.dp) {
            Column(modifier = Modifier.statusBarsPadding()) {
                Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onMenuClick) { Icon(Icons.Default.Menu, null, tint = if (isDark) Color.White else Color.Black) }
                    Column(modifier = Modifier.weight(1f)) {
                        Text("📹 SCREEN RECORDER", fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold,
                            color = if (isDark) ZenGold else Color(0xFF9E7E1D), style = MaterialTheme.typography.titleMedium)
                        Text(state.statusText, color = if (state.isRecording) ZenRed else YinTextSecondary, fontSize = 10.sp)
                    }
                    IconButton(onClick = { showConfigDialog = true }) { Icon(Icons.Default.Settings, null, tint = YinTextSecondary) }
                }
                Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 2.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(selected = currentView == 0, onClick = { currentView = 0 },
                        label = { Text("Record", fontSize = 10.sp) }, colors = FilterChipDefaults.filterChipColors(selectedContainerColor = ZenGold.copy(alpha = 0.2f), selectedLabelColor = ZenGold))
                    FilterChip(selected = currentView == 1, onClick = { currentView = 1 },
                        label = { Text("Library (${recordings.size})", fontSize = 10.sp) }, colors = FilterChipDefaults.filterChipColors(selectedContainerColor = ZenGold.copy(alpha = 0.2f), selectedLabelColor = ZenGold))
                }
            }
        }

        // Content
        when (currentView) {
            0 -> {
                // Recorder view
                Column(modifier = Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                    // Recording indicator
                    if (state.isRecording) {
                        val pulseAlpha by rememberInfiniteTransition(label = "rec_pulse").animateFloat(0.3f, 1f, infiniteRepeatable(tween(800), RepeatMode.Reverse))
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Box(modifier = Modifier.size(12.dp).clip(CircleShape).background(ZenRed.copy(alpha = pulseAlpha)))
                            Text("RECORDING", color = ZenRed, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(engine.formatDuration(state.elapsedMs), color = YinText, fontSize = 48.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(4.dp))
                        Text(engine.formatSize(state.fileSize), color = YinTextSecondary, fontSize = 14.sp)
                        Spacer(Modifier.height(32.dp))

                        Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                            // Pause/Resume
                            IconButton(onClick = { if (state.isPaused) engine.resumeRecording() else engine.pauseRecording() },
                                modifier = Modifier.size(64.dp).clip(CircleShape).background(ZenGold)) {
                                Icon(if (state.isPaused) Icons.Default.PlayArrow else Icons.Default.Pause, null, tint = Color.Black, modifier = Modifier.size(32.dp))
                            }
                            // Stop
                            IconButton(onClick = {
                                val recording = engine.stopRecording()
                                if (recording != null) {
                                    context.sendBroadcast(Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE, Uri.fromFile(File(recording.filePath))))
                                    currentView = 1
                                }
                            }, modifier = Modifier.size(64.dp).clip(CircleShape).background(ZenRed)) {
                                Icon(Icons.Default.Stop, null, tint = Color.White, modifier = Modifier.size(32.dp))
                            }
                        }
                    } else {
                        // Ready state
                        Icon(Icons.Default.Videocam, null, modifier = Modifier.size(80.dp), tint = Color.Gray.copy(alpha = 0.4f))
                        Spacer(Modifier.height(16.dp))
                        Text("Ready to Record", color = Color.Gray, fontSize = 18.sp)
                        Text("${config.resolution.label} • ${config.frameRate}fps • ${config.audioSource.label} audio", color = Color.Gray.copy(alpha = 0.6f), fontSize = 12.sp)
                        Spacer(Modifier.height(32.dp))

                        Button(onClick = { startScreenCapture() }, colors = ButtonDefaults.buttonColors(containerColor = ZenRed),
                            modifier = Modifier.width(200.dp).height(56.dp)) {
                            Icon(Icons.Default.FiberManualRecord, null, modifier = Modifier.size(24.dp))
                            Text(" Start Recording", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        }
                        Spacer(Modifier.height(12.dp))
                        TextButton(onClick = { showConfigDialog = true }) { Text("Configure Settings", color = ZenGold) }
                    }
                }
            }

            1 -> {
                // Library view
                if (recordings.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.VideoLibrary, null, modifier = Modifier.size(64.dp), tint = Color.Gray.copy(alpha = 0.4f))
                            Text("No Recordings", color = Color.Gray, fontSize = 16.sp)
                        }
                    }
                } else {
                    LazyColumn(contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(recordings) { recording ->
                            Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = YinCardBg), shape = RoundedCornerShape(10.dp)) {
                                Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Box(modifier = Modifier.size(48.dp).clip(RoundedCornerShape(8.dp)).background(ZenRed.copy(alpha = 0.15f)), contentAlignment = Alignment.Center) {
                                        Icon(Icons.Default.PlayCircle, null, tint = ZenRed, modifier = Modifier.size(24.dp))
                                    }
                                    Spacer(Modifier.width(10.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(recording.fileName, color = YinText, fontWeight = FontWeight.Bold, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        Text("${engine.formatDuration(recording.durationMs)} • ${engine.formatSize(recording.sizeBytes)}", color = YinTextSecondary, fontSize = 11.sp)
                                        Text(SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault()).format(Date(recording.createdAt)), color = YinTextSecondary, fontSize = 10.sp)
                                    }
                                    IconButton(onClick = { engine.shareRecording(context, recording) }, modifier = Modifier.size(28.dp)) { Icon(Icons.Default.Share, null, tint = ZenGold, modifier = Modifier.size(18.dp)) }
                                    IconButton(onClick = { engine.deleteRecording(recording) }, modifier = Modifier.size(28.dp)) { Icon(Icons.Default.Delete, null, tint = ZenRed, modifier = Modifier.size(18.dp)) }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

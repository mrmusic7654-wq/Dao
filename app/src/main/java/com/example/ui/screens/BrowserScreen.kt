package com.example.ui.screens

import android.Manifest
import android.app.DownloadManager as AndroidDownloadManager
import android.content.*
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.webkit.*
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.example.ui.automation.AutomationEventBus
import com.example.ui.theme.*
import kotlinx.coroutines.*
import java.io.File
import java.net.URLDecoder
import java.util.*

// ==================== DATA MODELS ====================

data class DownloadEntry(
    val id: Long = System.nanoTime(),
    val url: String,
    val fileName: String,
    val mimeType: String = "",
    var progress: Int = 0,
    var status: String = "Downloading",
    val downloadId: Long = 0,
    val totalSize: Long = 0,
    val downloadedSize: Long = 0,
    val speed: String = "",
    val thumbnail: Bitmap? = null
)

data class VideoFormat(
    val resolution: String,
    val format: String,
    val sizeMB: String,
    val url: String,
    val quality: String = ""
)

data class BookmarkEntry(
    val id: Long = System.nanoTime(),
    val title: String,
    val url: String,
    val createdAt: Long = System.currentTimeMillis()
)

enum class BrowserTabType { BROWSER, DOWNLOADS, FILES, SETTINGS }

// ==================== STORAGE HELPER ====================

object DaoStorageHelper {
    fun getDownloadsDir(context: Context): File {
        val dir = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), "Dao")
        } else {
            File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "Dao")
        }
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    fun getVideosDir(context: Context): File {
        val dir = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            File(context.getExternalFilesDir(Environment.DIRECTORY_MOVIES), "Dao")
        } else {
            File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES), "Dao")
        }
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    fun extractFileName(url: String, contentDisposition: String?, fallback: String?): String? {
        if (!contentDisposition.isNullOrBlank()) {
            val match = Regex("filename[^;=\n]*=((['\"]).*?\\2|[^;\n]*)").find(contentDisposition)
            if (match != null) {
                var name = match.groupValues[1].replace("\"", "").trim()
                try { name = URLDecoder.decode(name, "UTF-8") } catch (_: Exception) {}
                return name
            }
        }
        try {
            val urlObj = java.net.URL(url)
            val segment = urlObj.path.substringAfterLast("/")
            if (segment.isNotBlank() && segment.contains(".")) return URLDecoder.decode(segment, "UTF-8")
        } catch (_: Exception) {}
        return fallback
    }

    fun formatSize(bytes: Long): String = when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> "${"%.1f".format(bytes / 1024.0)} KB"
        bytes < 1024 * 1024 * 1024 -> "${"%.1f".format(bytes / (1024.0 * 1024.0))} MB"
        else -> "${"%.2f".format(bytes / (1024.0 * 1024.0 * 1024.0))} GB"
    }
}

// ==================== MAIN BROWSER SCREEN ====================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrowserScreen(isDark: Boolean, onMenuClick: () -> Unit) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val clipboardManager = LocalClipboardManager.current
    val scope = rememberCoroutineScope()

    var activeTab by remember { mutableStateOf(BrowserTabType.BROWSER) }
    var urlInput by remember { mutableStateOf("") }
    var currentUrl by remember { mutableStateOf("https://www.google.com") }
    var webView by remember { mutableStateOf<WebView?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    var loadProgress by remember { mutableIntStateOf(0) }
    var canGoBack by remember { mutableStateOf(false) }
    var canGoForward by remember { mutableStateOf(false) }
    var currentTitle by remember { mutableStateOf("Dao Browser") }

    // Downloads
    var downloads by remember { mutableStateOf(listOf<DownloadEntry>()) }
    var activeFilter by remember { mutableStateOf("active") }

    // Video detection
    var detectedVideoUrl by remember { mutableStateOf<String?>(null) }
    var detectedVideoTitle by remember { mutableStateOf("") }
    var detectedThumbnail by remember { mutableStateOf<Bitmap?>(null) }
    var showVideoPopup by remember { mutableStateOf(false) }
    var showFormatDialog by remember { mutableStateOf(false) }
    var availableFormats by remember { mutableStateOf(listOf<VideoFormat>()) }

    // Bookmarks
    var bookmarks by remember { mutableStateOf(listOf<BookmarkEntry>()) }
    val bookmarkPrefs = remember { context.getSharedPreferences("dao_bookmarks", Context.MODE_PRIVATE) }

    fun loadBookmarks() {
        val list = mutableListOf<BookmarkEntry>()
        bookmarkPrefs.all.forEach { (k, v) ->
            if (k.startsWith("bk_")) {
                val parts = (v as String).split("|||")
                if (parts.size >= 2) list.add(BookmarkEntry(k.removePrefix("bk_").toLongOrNull() ?: 0, parts[0], parts[1]))
            }
        }
        bookmarks = list.sortedByDescending { it.createdAt }
    }

    fun addBookmark() {
        if (bookmarks.any { it.url == currentUrl }) return
        val id = System.currentTimeMillis()
        bookmarkPrefs.edit().putString("bk_$id", "$currentTitle|||$currentUrl|||$id").apply()
        loadBookmarks()
        Toast.makeText(context, "Bookmarked!", Toast.LENGTH_SHORT).show()
    }

    fun removeBookmark(id: Long) {
        bookmarkPrefs.edit().remove("bk_$id").apply()
        loadBookmarks()
    }

    fun startDownload(url: String, fileName: String, isVideo: Boolean = false) {
        val dir = if (isVideo) DaoStorageHelper.getVideosDir(context) else DaoStorageHelper.getDownloadsDir(context)
        val destFile = File(dir, fileName)
        val request = AndroidDownloadManager.Request(Uri.parse(url))
            .setTitle(fileName)
            .setDestinationUri(Uri.fromFile(destFile))
            .setNotificationVisibility(AndroidDownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
        val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as AndroidDownloadManager
        val downloadId = dm.enqueue(request)
        downloads = downloads + DownloadEntry(url = url, fileName = fileName, downloadId = downloadId)
    }

    val storagePermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { _ -> }

    LaunchedEffect(Unit) {
        loadBookmarks()
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.Q) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                storagePermissionLauncher.launch(arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.READ_EXTERNAL_STORAGE))
            }
        }
    }

    // Listen for automation events
    LaunchedEffect(Unit) {
        AutomationEventBus.events.collect { event ->
            if (event.targetScreen == "Browser") {
                when (event.action) {
                    "navigate" -> {
                        val url = event.parameters["url"] ?: return@collect
                        webView?.loadUrl(url)
                        urlInput = url
                        currentUrl = url
                        AutomationEventBus.sendResult(AutomationEventBus.AutomationResult(event.requestId, true, "Navigated to $url"))
                    }
                    "load_url" -> {
                        val url = event.parameters["url"] ?: return@collect
                        webView?.loadUrl(url)
                        urlInput = url
                        currentUrl = url
                        AutomationEventBus.sendResult(AutomationEventBus.AutomationResult(event.requestId, true, "Loaded $url"))
                    }
                    "download_videos" -> {
                        // Trigger video detection download using detectedVideoUrl
                        detectedVideoUrl?.let { url ->
                            startDownload(url, "video_${System.currentTimeMillis()}.mp4", isVideo = true)
                            AutomationEventBus.sendResult(AutomationEventBus.AutomationResult(event.requestId, true, "Downloaded video"))
                        } ?: run {
                            AutomationEventBus.sendResult(AutomationEventBus.AutomationResult(event.requestId, false, "No video detected"))
                        }
                    }
                }
            }
        }
    }

    // Monitor clipboard
    LaunchedEffect(activeTab) {
        if (activeTab == BrowserTabType.BROWSER) {
            try {
                val clip = clipboardManager.getText()
                if (clip != null && clip.text.contains("http") && clip.text != currentUrl) {
                    urlInput = clip.text
                }
            } catch (_: Exception) {}
        }
    }

    // Monitor downloads progress
    LaunchedEffect(downloads.size) {
        val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as AndroidDownloadManager
        while (downloads.any { it.status == "Downloading" }) {
            downloads = downloads.map { d ->
                if (d.status != "Downloading") d
                else {
                    val cursor = dm.query(AndroidDownloadManager.Query().setFilterById(d.downloadId))
                    if (cursor.moveToFirst()) {
                        val s = cursor.getInt(cursor.getColumnIndex(AndroidDownloadManager.COLUMN_STATUS))
                        val b = cursor.getLong(cursor.getColumnIndex(AndroidDownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
                        val t = cursor.getLong(cursor.getColumnIndex(AndroidDownloadManager.COLUMN_TOTAL_SIZE_BYTES))
                        cursor.close()
                        when (s) {
                            AndroidDownloadManager.STATUS_SUCCESSFUL -> d.copy(progress = 100, status = "Complete", totalSize = t, downloadedSize = t)
                            AndroidDownloadManager.STATUS_FAILED -> d.copy(status = "Failed")
                            AndroidDownloadManager.STATUS_RUNNING -> d.copy(progress = if (t > 0) ((b * 100) / t).toInt() else 0, downloadedSize = b, totalSize = t)
                            AndroidDownloadManager.STATUS_PAUSED -> d.copy(status = "Paused")
                            else -> d
                        }
                    } else { cursor.close(); d }
                }
            }
            delay(500)
        }
    }

    // ==================== DIALOGS ====================

    // Video detection popup
    if (showVideoPopup && detectedVideoUrl != null) {
        Card(
            modifier = Modifier.fillMaxWidth().padding(12.dp).shadow(8.dp, RoundedCornerShape(12.dp)),
            colors = CardDefaults.cardColors(containerColor = YinCardBg),
            shape = RoundedCornerShape(12.dp)
        ) {
            Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                // Thumbnail
                Box(modifier = Modifier.size(60.dp).clip(RoundedCornerShape(8.dp)).background(Color.DarkGray), contentAlignment = Alignment.Center) {
                    detectedThumbnail?.let { Image(bitmap = it.asImageBitmap(), contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
                        ?: Icon(Icons.Default.Videocam, null, tint = Color.Gray, modifier = Modifier.size(28.dp))
                }
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(detectedVideoTitle.ifBlank { "Video detected" }, color = YinText, fontWeight = FontWeight.Bold, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("Tap to download", color = YinTextSecondary, fontSize = 11.sp)
                }
                Spacer(Modifier.width(8.dp))
                Button(onClick = {
                    // Generate format options
                    availableFormats = listOf(
                        VideoFormat("4K", "MP4", "~45 MB", detectedVideoUrl ?: "", "2160p"),
                        VideoFormat("1080p", "MP4", "~22 MB", detectedVideoUrl ?: "", "1080p"),
                        VideoFormat("720p", "MP4", "~12 MB", detectedVideoUrl ?: "", "720p"),
                        VideoFormat("480p", "MP4", "~7 MB", detectedVideoUrl ?: "", "480p"),
                        VideoFormat("360p", "MP4", "~4 MB", detectedVideoUrl ?: "", "360p"),
                        VideoFormat("Audio", "MP3", "~2 MB", detectedVideoUrl ?: "", "audio")
                    )
                    showFormatDialog = true
                    showVideoPopup = false
                }, colors = ButtonDefaults.buttonColors(containerColor = ZenRed), modifier = Modifier.height(36.dp)) {
                    Text("Download", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.width(4.dp))
                IconButton(onClick = { showVideoPopup = false }, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Default.Close, null, tint = YinTextSecondary, modifier = Modifier.size(16.dp))
                }
            }
        }
    }

    // Format selection dialog
    if (showFormatDialog) {
        var selectedFormat by remember { mutableStateOf(0) }
        AlertDialog(
            onDismissRequest = { showFormatDialog = false },
            title = { Text("Select Quality", fontFamily = FontFamily.Serif, color = ZenGold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(detectedVideoTitle.ifBlank { "Video" }, color = YinText, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Divider(color = Color(0xFF333340))
                    availableFormats.forEachIndexed { index, format ->
                        Row(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(if (index == selectedFormat) ZenGold.copy(alpha = 0.1f) else Color.Transparent).clickable(onClick = { selectedFormat = index }).padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(selected = index == selectedFormat, onClick = { selectedFormat = index }, colors = RadioButtonDefaults.colors(selectedColor = ZenGold))
                            Spacer(Modifier.width(8.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text("${format.resolution} • ${format.format}", color = YinText, fontWeight = FontWeight.Medium, fontSize = 13.sp)
                                Text("Quality: ${format.quality}", color = YinTextSecondary, fontSize = 10.sp)
                            }
                            Text(format.sizeMB, color = ZenGold, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    val fmt = availableFormats[selectedFormat]
                    val ext = if (fmt.format == "MP3") ".mp3" else ".mp4"
                    val name = detectedVideoTitle.ifBlank { "video_${System.currentTimeMillis()}" }.replace(Regex("[^a-zA-Z0-9._-]"), "_") + ext
                    startDownload(fmt.url, name, isVideo = fmt.format != "MP3")
                    showFormatDialog = false
                    activeTab = BrowserTabType.DOWNLOADS
                }, colors = ButtonDefaults.buttonColors(containerColor = ZenGold)) {
                    Text("Download (${availableFormats[selectedFormat].sizeMB})", color = Color.Black, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = { TextButton(onClick = { showFormatDialog = false }) { Text("Cancel", color = ZenGold) } },
            containerColor = if (isDark) Color(0xFF14131A) else Color(0xFFF7F4EE)
        )
    }

    // ==================== MAIN UI ====================

    Column(modifier = Modifier.fillMaxSize().background(YinBlack)) {

        // Content area
        Box(modifier = Modifier.weight(1f)) {
            when (activeTab) {
                BrowserTabType.BROWSER -> {
                    Column(modifier = Modifier.fillMaxSize()) {
                        // Paste link bar
                        Surface(color = YinCardBg, shadowElevation = 2.dp) {
                            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp).statusBarsPadding(), verticalAlignment = Alignment.CenterVertically) {
                                OutlinedTextField(
                                    value = urlInput,
                                    onValueChange = { urlInput = it },
                                    modifier = Modifier.weight(1f).height(48.dp),
                                    singleLine = true,
                                    placeholder = { Text("Paste video URL here...", color = Color.Gray, fontSize = 13.sp) },
                                    leadingIcon = { Icon(Icons.Default.Link, null, tint = ZenGold, modifier = Modifier.size(18.dp)) },
                                    trailingIcon = {
                                        Row {
                                            if (urlInput.isNotEmpty()) {
                                                IconButton(onClick = {
                                                    urlInput = ""; focusManager.clearFocus()
                                                }, modifier = Modifier.size(28.dp)) {
                                                    Icon(Icons.Default.Close, null, tint = Color.Gray, modifier = Modifier.size(16.dp))
                                                }
                                            }
                                            IconButton(onClick = {
                                                clipboardManager.getText()?.let { urlInput = it.text }
                                            }, modifier = Modifier.size(28.dp)) {
                                                Icon(Icons.Default.ContentPaste, null, tint = ZenGold, modifier = Modifier.size(16.dp))
                                            }
                                        }
                                    },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go),
                                    keyboardActions = KeyboardActions(onGo = {
                                        val full = if (!urlInput.startsWith("http")) "https://$urlInput" else urlInput
                                        urlInput = full; currentUrl = full; webView?.loadUrl(full); focusManager.clearFocus()
                                    }),
                                    textStyle = androidx.compose.ui.text.TextStyle(fontSize = 13.sp, color = YinText),
                                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = ZenGold, unfocusedBorderColor = Color(0xFF333340), focusedContainerColor = Color(0xFF1A1A22), unfocusedContainerColor = Color(0xFF1A1A22)),
                                    shape = RoundedCornerShape(24.dp)
                                )
                                Spacer(Modifier.width(8.dp))
                                IconButton(onClick = {
                                    val full = if (!urlInput.startsWith("http")) "https://$urlInput" else urlInput
                                    urlInput = full; currentUrl = full; webView?.loadUrl(full); focusManager.clearFocus()
                                }) {
                                    Icon(Icons.Default.Search, "Go", tint = ZenGold, modifier = Modifier.size(22.dp))
                                }
                            }
                        }

                        // Loading bar
                        if (isLoading) {
                            LinearProgressIndicator(progress = { loadProgress / 100f }, modifier = Modifier.fillMaxWidth().height(2.dp), color = ZenGold, trackColor = YinCardBg)
                        }

                        // Navigation bar
                        Surface(color = YinCardBg.copy(alpha = 0.9f)) {
                            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 2.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                                IconButton(onClick = { webView?.goBack() }, enabled = canGoBack) { Icon(Icons.Default.ArrowBack, null, tint = if (canGoBack) YinText else Color.Gray, modifier = Modifier.size(20.dp)) }
                                IconButton(onClick = { webView?.goForward() }, enabled = canGoForward) { Icon(Icons.Default.ArrowForward, null, tint = if (canGoForward) YinText else Color.Gray, modifier = Modifier.size(20.dp)) }
                                IconButton(onClick = { webView?.reload() }) { Icon(Icons.Default.Refresh, null, tint = YinText, modifier = Modifier.size(20.dp)) }
                                IconButton(onClick = { urlInput = "https://www.google.com"; webView?.loadUrl("https://www.google.com") }) { Icon(Icons.Default.Home, null, tint = ZenGold, modifier = Modifier.size(20.dp)) }
                                IconButton(onClick = { addBookmark() }) { Icon(Icons.Default.Bookmark, null, tint = if (bookmarks.any { it.url == currentUrl }) ZenGold else YinTextSecondary, modifier = Modifier.size(20.dp)) }
                            }
                        }

                        // WebView
                        AndroidView(factory = { ctx ->
                            WebView(ctx).apply {
                                settings.apply {
                                    javaScriptEnabled = true; domStorageEnabled = true; allowFileAccess = true
                                    databaseEnabled = true; setSupportZoom(true); builtInZoomControls = true; displayZoomControls = false
                                    loadWithOverviewMode = true; useWideViewPort = true; mediaPlaybackRequiresUserGesture = false
                                    mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                                    userAgentString = "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/120.0.0.0 Mobile Safari/537.36"
                                }
                                webViewClient = object : WebViewClient() {
                                    override fun onPageStarted(v: WebView?, url: String?, f: Bitmap?) {
                                        url?.let { urlInput = it; currentUrl = it }; isLoading = true; loadProgress = 0
                                    }
                                    override fun onPageFinished(v: WebView?, url: String?) {
                                        isLoading = false; currentTitle = v?.title ?: "Page"
                                        canGoBack = v?.canGoBack() ?: false; canGoForward = v?.canGoForward() ?: false
                                        // Detect videos
                                        v?.evaluateJavascript("""
                                            (function(){
                                                var videos=[];
                                                document.querySelectorAll('video').forEach(function(v){
                                                    var src=v.currentSrc||v.src||v.querySelector('source')?.src;
                                                    if(src) videos.push(JSON.stringify({url:src,title:document.title}));
                                                });
                                                return JSON.stringify(videos);
                                            })();
                                        """.trimIndent()) { result ->
                                            try {
                                                val arr = org.json.JSONArray(result)
                                                if (arr.length() > 0) {
                                                    val first = org.json.JSONObject(arr.getString(0))
                                                    detectedVideoUrl = first.getString("url")
                                                    detectedVideoTitle = first.optString("title", v?.title ?: "")
                                                    showVideoPopup = true
                                                }
                                            } catch (_: Exception) {}
                                        }
                                    }
                                }
                                webChromeClient = object : WebChromeClient() {
                                    override fun onProgressChanged(v: WebView?, p: Int) { loadProgress = p }
                                }
                                setDownloadListener { url, ua, cd, mt, _ ->
                                    val name = DaoStorageHelper.extractFileName(url, cd, null) ?: "download_${System.currentTimeMillis()}"
                                    val isVid = mt?.startsWith("video/") == true
                                    val ext = if (name.contains(".")) "" else if (isVid) ".mp4" else ".bin"
                                    startDownload(url, name + ext, isVid)
                                    Toast.makeText(ctx, "Downloading: $name", Toast.LENGTH_SHORT).show()
                                }
                                loadUrl(currentUrl); webView = this
                            }
                        }, modifier = Modifier.weight(1f))
                    }
                }

                BrowserTabType.DOWNLOADS -> {
                    // Download manager
                    Column(modifier = Modifier.fillMaxSize().padding(12.dp)) {
                        Text("📥 Downloads", color = ZenGold, fontSize = 18.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Serif)
                        Spacer(Modifier.height(8.dp))

                        // Filter tabs
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf("active" to "Active", "completed" to "Completed", "paused" to "Paused").forEach { (key, label) ->
                                FilterChip(selected = activeFilter == key, onClick = { activeFilter = key },
                                    label = { Text(label, fontSize = 12.sp) },
                                    colors = FilterChipDefaults.filterChipColors(selectedContainerColor = ZenGold.copy(alpha = 0.2f), selectedLabelColor = ZenGold))
                            }
                        }
                        Spacer(Modifier.height(8.dp))

                        val filtered = when (activeFilter) {
                            "active" -> downloads.filter { it.status == "Downloading" }
                            "completed" -> downloads.filter { it.status == "Complete" }
                            "paused" -> downloads.filter { it.status == "Paused" }
                            else -> downloads
                        }

                        if (filtered.isEmpty()) {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("No downloads", color = Color.Gray) }
                        } else {
                            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                items(filtered.reversed()) { dl ->
                                    Card(colors = CardDefaults.cardColors(containerColor = YinCardBg), shape = RoundedCornerShape(10.dp)) {
                                        Column(modifier = Modifier.padding(12.dp)) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text(dl.fileName, color = YinText, fontWeight = FontWeight.Bold, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                                    when (dl.status) {
                                                        "Downloading" -> {
                                                            LinearProgressIndicator(progress = { dl.progress / 100f }, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).height(3.dp).clip(RoundedCornerShape(2.dp)), color = ZenGold, trackColor = YinBlack)
                                                            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                                                                Text("${dl.progress}%", color = YinTextSecondary, fontSize = 11.sp)
                                                                Text("${DaoStorageHelper.formatSize(dl.downloadedSize)} / ${if (dl.totalSize > 0) DaoStorageHelper.formatSize(dl.totalSize) else "..."}", color = YinTextSecondary, fontSize = 10.sp)
                                                            }
                                                        }
                                                        "Complete" -> Text("✅ Complete • ${DaoStorageHelper.formatSize(dl.totalSize)}", color = Color(0xFF4CAF50), fontSize = 11.sp)
                                                        "Failed" -> Text("❌ Failed", color = ZenRed, fontSize = 11.sp)
                                                        "Paused" -> Text("⏸ Paused • ${dl.progress}%", color = YinTextSecondary, fontSize = 11.sp)
                                                    }
                                                }
                                                // Action buttons
                                                when (dl.status) {
                                                    "Downloading" -> IconButton(onClick = { /* pause */ }, modifier = Modifier.size(32.dp)) { Icon(Icons.Default.Pause, null, tint = ZenGold, modifier = Modifier.size(20.dp)) }
                                                    "Complete" -> IconButton(onClick = {
                                                        val file = File(DaoStorageHelper.getVideosDir(context), dl.fileName)
                                                        if (file.exists()) {
                                                            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                                                            context.startActivity(Intent(Intent.ACTION_VIEW).apply { setDataAndType(uri, "video/*"); addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION) })
                                                        }
                                                    }, modifier = Modifier.size(32.dp)) { Icon(Icons.Default.PlayArrow, null, tint = Color(0xFF4CAF50), modifier = Modifier.size(20.dp)) }
                                                    "Paused" -> IconButton(onClick = { /* resume */ }, modifier = Modifier.size(32.dp)) { Icon(Icons.Default.PlayArrow, null, tint = ZenGold, modifier = Modifier.size(20.dp)) }
                                                }
                                                IconButton(onClick = { downloads = downloads - dl }, modifier = Modifier.size(32.dp)) { Icon(Icons.Default.Close, null, tint = ZenRed, modifier = Modifier.size(18.dp)) }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                BrowserTabType.FILES -> {
                    // Built-in mini file explorer
                    val filesDir = DaoStorageHelper.getDownloadsDir(context)
                    val videoDir = DaoStorageHelper.getVideosDir(context)
                    var files by remember { mutableStateOf<List<File>>(emptyList()) }

                    LaunchedEffect(Unit) {
                        files = ((filesDir.listFiles()?.toList() ?: emptyList()) + (videoDir.listFiles()?.toList() ?: emptyList()))
                            .sortedByDescending { it.lastModified() }
                    }

                    Column(modifier = Modifier.fillMaxSize().padding(12.dp)) {
                        Text("📁 Downloaded Files", color = ZenGold, fontSize = 18.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Serif)
                        Spacer(Modifier.height(8.dp))
                        if (files.isEmpty()) {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("No downloaded files", color = Color.Gray) }
                        } else {
                            LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                items(files) { file ->
                                    Card(modifier = Modifier.fillMaxWidth().clickable(onClick = {
                                        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                                        val mime = if (file.name.endsWith(".mp4")) "video/*" else "*/*"
                                        context.startActivity(Intent(Intent.ACTION_VIEW).apply { setDataAndType(uri, mime); addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION) })
                                    }), colors = CardDefaults.cardColors(containerColor = YinCardBg), shape = RoundedCornerShape(8.dp)) {
                                        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                            Icon(if (file.name.endsWith(".mp4")) Icons.Default.VideoFile else Icons.Default.Description, null, tint = if (file.name.endsWith(".mp4")) ZenRed else ZenGold, modifier = Modifier.size(20.dp))
                                            Spacer(Modifier.width(10.dp))
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(file.name, color = YinText, fontWeight = FontWeight.Medium, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                                Text(DaoStorageHelper.formatSize(file.length()), color = YinTextSecondary, fontSize = 11.sp)
                                            }
                                            IconButton(onClick = { file.delete(); files = files - file }) { Icon(Icons.Default.Delete, null, tint = ZenRed, modifier = Modifier.size(18.dp)) }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                BrowserTabType.SETTINGS -> {
                    Column(modifier = Modifier.fillMaxSize().padding(12.dp)) {
                        Text("⚙️ Settings", color = ZenGold, fontSize = 18.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Serif)
                        Spacer(Modifier.height(12.dp))
                        Card(colors = CardDefaults.cardColors(containerColor = YinCardBg)) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text("Bookmarks", color = YinText, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                Spacer(Modifier.height(4.dp))
                                if (bookmarks.isEmpty()) Text("No bookmarks", color = Color.Gray, fontSize = 12.sp)
                                else bookmarks.forEach { bk ->
                                    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Column(modifier = Modifier.weight(1f)) { Text(bk.title, color = YinText, fontSize = 12.sp); Text(bk.url, color = YinTextSecondary, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                                        IconButton(onClick = { removeBookmark(bk.id) }, modifier = Modifier.size(24.dp)) { Icon(Icons.Default.Delete, null, tint = ZenRed, modifier = Modifier.size(14.dp)) }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Video detection popup (floating)
        if (showVideoPopup && detectedVideoUrl != null) {
            Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A28)),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, ZenRed.copy(alpha = 0.5f))
                ) {
                    Row(modifier = Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(modifier = Modifier.size(50.dp).clip(RoundedCornerShape(8.dp)).background(Color.DarkGray), contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.Videocam, null, tint = ZenRed, modifier = Modifier.size(24.dp))
                        }
                        Spacer(Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(detectedVideoTitle.ifBlank { "Video Detected" }, color = YinText, fontWeight = FontWeight.Bold, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text("Ready to download", color = YinTextSecondary, fontSize = 10.sp)
                        }
                        Button(onClick = {
                            availableFormats = listOf(
                                VideoFormat("1080p", "MP4", "~22 MB", detectedVideoUrl ?: "", "1080p"),
                                VideoFormat("720p", "MP4", "~12 MB", detectedVideoUrl ?: "", "720p"),
                                VideoFormat("480p", "MP4", "~7 MB", detectedVideoUrl ?: "", "480p"),
                                VideoFormat("360p", "MP4", "~4 MB", detectedVideoUrl ?: "", "360p"),
                                VideoFormat("Audio", "MP3", "~2 MB", detectedVideoUrl ?: "", "audio")
                            )
                            showFormatDialog = true
                            showVideoPopup = false
                        }, colors = ButtonDefaults.buttonColors(containerColor = ZenRed), modifier = Modifier.height(34.dp)) {
                            Text("Download", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                        IconButton(onClick = { showVideoPopup = false }, modifier = Modifier.size(24.dp)) { Icon(Icons.Default.Close, null, tint = YinTextSecondary, modifier = Modifier.size(14.dp)) }
                    }
                }
            }
        }

        // Bottom Navigation Bar
        Surface(color = YinCardBg, shadowElevation = 8.dp) {
            Row(modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                listOf(
                    BrowserTabType.BROWSER to Triple(Icons.Default.Language, "Browser", ZenBlue),
                    BrowserTabType.DOWNLOADS to Triple(Icons.Default.Download, "Downloads", ZenGold),
                    BrowserTabType.FILES to Triple(Icons.Default.Folder, "Files", ZenSienna),
                    BrowserTabType.SETTINGS to Triple(Icons.Default.Settings, "Settings", YinTextSecondary)
                ).forEach { (tab, data) ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = { activeTab = tab }).padding(horizontal = 12.dp, vertical = 4.dp)) {
                        Icon(data.first, data.second, tint = if (activeTab == tab) data.third else YinTextSecondary, modifier = Modifier.size(22.dp))
                        Text(data.second, color = if (activeTab == tab) data.third else YinTextSecondary, fontSize = 10.sp, fontWeight = if (activeTab == tab) FontWeight.Bold else FontWeight.Normal)
                    }
                }
            }
        }
    }
}

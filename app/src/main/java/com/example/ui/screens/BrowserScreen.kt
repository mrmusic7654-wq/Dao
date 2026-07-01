// /workspace/app/src/main/java/com/example/ui/screens/BrowserScreen.kt

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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.example.ui.theme.*
import kotlinx.coroutines.launch
import java.io.File
import java.net.URLDecoder
import java.text.SimpleDateFormat
import java.util.*

// ==================== DATA MODELS ====================

data class BrowserTab(
    val id: String = UUID.randomUUID().toString(),
    var title: String = "New Tab",
    var url: String = "https://www.google.com",
    var webView: WebView? = null
)

data class BookmarkEntry(
    val id: Long = System.nanoTime(),
    val title: String,
    val url: String,
    val favicon: String = "",
    val createdAt: Long = System.currentTimeMillis()
)

data class DownloadEntry(
    val id: Long = System.nanoTime(),
    val url: String,
    val fileName: String,
    val mimeType: String = "",
    val fileSize: Long = 0,
    var progress: Int = 0,
    var status: DownloadStatus = DownloadStatus.DOWNLOADING,
    val downloadId: Long = 0,
    val createdAt: Long = System.currentTimeMillis()
)

enum class DownloadStatus {
    DOWNLOADING, COMPLETE, FAILED, PAUSED
}

// ==================== STORAGE HELPER ====================

object DaoStorageHelper {
    fun getDaoDownloadsDir(context: Context): File {
        val dir = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), "Dao")
        } else {
            File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "Dao")
        }
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    fun getDownloadFilePath(context: Context, fileName: String): File {
        return File(getDaoDownloadsDir(context), sanitizeFileName(fileName))
    }

    private fun sanitizeFileName(name: String): String {
        return name.replace(Regex("[^a-zA-Z0-9._-]"), "_")
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

    fun getVideoFilePath(context: Context, fileName: String): File {
        return File(getVideosDir(context), sanitizeFileName(fileName))
    }
}

// ==================== DOWNLOAD MANAGER ====================

class DaoDownloadManager(private val context: Context) {
    private val androidDownloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as AndroidDownloadManager
    private val preferences = context.getSharedPreferences("dao_downloads", Context.MODE_PRIVATE)
    
    // Track downloads
    val activeDownloads = mutableStateListOf<DownloadEntry>()
    private val completedDownloads = mutableStateListOf<DownloadEntry>()
    
    // Monitor download progress
    private var isMonitoring = false
    
    fun downloadFile(
        url: String,
        fileName: String? = null,
        mimeType: String? = null,
        userAgent: String? = null,
        contentDisposition: String? = null,
        isVideo: Boolean = false
    ): Long {
        // Extract filename from URL or Content-Disposition header
        val extractedName = extractFileName(url, contentDisposition, fileName) ?: "download_${System.currentTimeMillis()}"
        val finalFileName = ensureExtension(extractedName, mimeType, isVideo)
        
        val downloadDir = if (isVideo) {
            DaoStorageHelper.getVideosDir(context)
        } else {
            DaoStorageHelper.getDaoDownloadsDir(context)
        }
        
        val destinationFile = File(downloadDir, finalFileName)
        val destinationUri = Uri.fromFile(destinationFile)
        
        val request = AndroidDownloadManager.Request(Uri.parse(url))
            .setTitle(finalFileName)
            .setDescription("Downloading...")
            .setNotificationVisibility(AndroidDownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationUri(destinationUri)
            .setAllowedOverMetered(true)
            .setAllowedOverRoaming(true)
        
        // Set MIME type if available
        mimeType?.let { request.setMimeType(it) }
        
        // Add request headers
        userAgent?.let { request.addRequestHeader("User-Agent", it) }
        request.addRequestHeader("Accept", "*/*")
        
        val downloadId = androidDownloadManager.enqueue(request)
        
        // Track this download
        val entry = DownloadEntry(
            url = url,
            fileName = finalFileName,
            mimeType = mimeType ?: "",
            downloadId = downloadId,
            status = DownloadStatus.DOWNLOADING
        )
        activeDownloads.add(entry)
        
        // Save download info
        saveDownloadInfo(downloadId, finalFileName, url, destinationFile.absolutePath)
        
        // Start monitoring if not already
        startMonitoring()
        
        return downloadId
    }
    
    private fun extractFileName(url: String, contentDisposition: String?, fallback: String?): String? {
        // Try Content-Disposition header first
        if (!contentDisposition.isNullOrBlank()) {
            val filenameRegex = Regex("filename[^;=\n]*=((['\"]).*?\\2|[^;\n]*)")
            val match = filenameRegex.find(contentDisposition)
            if (match != null) {
                var name = match.groupValues[1].replace("\"", "").trim()
                try {
                    name = URLDecoder.decode(name, "UTF-8")
                } catch (_: Exception) {}
                return name
            }
        }
        
        // Try extracting from URL
        try {
            val urlObj = java.net.URL(url)
            val path = urlObj.path
            val lastSegment = path.substringAfterLast("/")
            if (lastSegment.isNotBlank() && lastSegment.contains(".")) {
                return URLDecoder.decode(lastSegment, "UTF-8")
            }
        } catch (_: Exception) {}
        
        return fallback
    }
    
    private fun ensureExtension(fileName: String, mimeType: String?, isVideo: Boolean): String {
        if (fileName.contains(".")) return fileName
        
        val extension = when {
            isVideo -> ".mp4"
            mimeType != null -> mimeTypeToExtension(mimeType)
            else -> ".bin"
        }
        
        return "$fileName$extension"
    }
    
    private fun mimeTypeToExtension(mimeType: String): String {
        return when {
            mimeType.contains("video/mp4") -> ".mp4"
            mimeType.contains("video/webm") -> ".webm"
            mimeType.contains("video/") -> ".mp4"
            mimeType.contains("audio/") -> ".mp3"
            mimeType.contains("image/jpeg") -> ".jpg"
            mimeType.contains("image/png") -> ".png"
            mimeType.contains("image/gif") -> ".gif"
            mimeType.contains("image/webp") -> ".webp"
            mimeType.contains("application/pdf") -> ".pdf"
            mimeType.contains("application/zip") -> ".zip"
            mimeType.contains("application/vnd.android.package-archive") -> ".apk"
            mimeType.contains("text/html") -> ".html"
            else -> ".bin"
        }
    }
    
    private fun startMonitoring() {
        if (isMonitoring) return
        isMonitoring = true
        
        Thread {
            while (activeDownloads.isNotEmpty()) {
                val toRemove = mutableListOf<DownloadEntry>()
                
                activeDownloads.forEachIndexed { index, download ->
                    if (download.status != DownloadStatus.COMPLETE && download.status != DownloadStatus.FAILED) {
                        val query = AndroidDownloadManager.Query().setFilterById(download.downloadId)
                        val cursor = androidDownloadManager.query(query)
                        
                        if (cursor.moveToFirst()) {
                            val statusIndex = cursor.getColumnIndex(AndroidDownloadManager.COLUMN_STATUS)
                            val progressIndex = cursor.getColumnIndex(AndroidDownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)
                            val totalIndex = cursor.getColumnIndex(AndroidDownloadManager.COLUMN_TOTAL_SIZE_BYTES)
                            
                            val status = cursor.getInt(statusIndex)
                            val bytesDownloaded = cursor.getLong(progressIndex)
                            val totalBytes = cursor.getLong(totalIndex)
                            
                            when (status) {
                                AndroidDownloadManager.STATUS_SUCCESSFUL -> {
                                    activeDownloads[index] = download.copy(
                                        progress = 100,
                                        status = DownloadStatus.COMPLETE,
                                        fileSize = totalBytes
                                    )
                                    toRemove.add(activeDownloads[index])
                                }
                                AndroidDownloadManager.STATUS_FAILED -> {
                                    activeDownloads[index] = download.copy(
                                        status = DownloadStatus.FAILED
                                    )
                                    toRemove.add(activeDownloads[index])
                                }
                                AndroidDownloadManager.STATUS_RUNNING -> {
                                    val progress = if (totalBytes > 0) {
                                        ((bytesDownloaded * 100) / totalBytes).toInt()
                                    } else 0
                                    activeDownloads[index] = download.copy(
                                        progress = progress,
                                        fileSize = totalBytes,
                                        status = DownloadStatus.DOWNLOADING
                                    )
                                }
                                AndroidDownloadManager.STATUS_PAUSED -> {
                                    activeDownloads[index] = download.copy(
                                        status = DownloadStatus.PAUSED
                                    )
                                }
                            }
                        }
                        cursor.close()
                    } else {
                        toRemove.add(download)
                    }
                }
                
                // Move completed downloads to history
                toRemove.forEach { download ->
                    if (download.status == DownloadStatus.COMPLETE || download.status == DownloadStatus.FAILED) {
                        completedDownloads.add(download)
                        activeDownloads.remove(download)
                    }
                }
                
                Thread.sleep(500)
            }
            isMonitoring = false
        }.start()
    }
    
    private fun saveDownloadInfo(downloadId: Long, fileName: String, url: String, path: String) {
        preferences.edit()
            .putString("download_${downloadId}_name", fileName)
            .putString("download_${downloadId}_url", url)
            .putString("download_${downloadId}_path", path)
            .apply()
    }
    
    fun clearCompleted() {
        completedDownloads.clear()
    }
    
    fun getAllCompleted(): List<DownloadEntry> = completedDownloads.toList()
}

// ==================== BOOKMARK MANAGER ====================

class BookmarkManager(context: Context) {
    private val prefs = context.getSharedPreferences("dao_bookmarks", Context.MODE_PRIVATE)
    
    fun getBookmarks(): List<BookmarkEntry> {
        val bookmarks = mutableListOf<BookmarkEntry>()
        val all = prefs.all
        all.forEach { (key, value) ->
            if (key.startsWith("bookmark_")) {
                val parts = (value as String).split("|||")
                if (parts.size >= 2) {
                    bookmarks.add(
                        BookmarkEntry(
                            id = key.removePrefix("bookmark_").toLongOrNull() ?: System.nanoTime(),
                            title = parts[0],
                            url = parts[1],
                            createdAt = parts.getOrNull(2)?.toLongOrNull() ?: System.currentTimeMillis()
                        )
                    )
                }
            }
        }
        return bookmarks.sortedByDescending { it.createdAt }
    }
    
    fun addBookmark(title: String, url: String) {
        val id = System.nanoTime()
        prefs.edit()
            .putString("bookmark_$id", "$title|||$url|||${System.currentTimeMillis()}")
            .apply()
    }
    
    fun removeBookmark(id: Long) {
        prefs.edit().remove("bookmark_$id").apply()
    }
    
    fun isBookmarked(url: String): Boolean {
        return getBookmarks().any { it.url == url }
    }
    
    fun toggleBookmark(title: String, url: String) {
        val existing = getBookmarks().find { it.url == url }
        if (existing != null) {
            removeBookmark(existing.id)
        } else {
            addBookmark(title, url)
        }
    }
}

// ==================== MAIN BROWSER SCREEN ====================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DaoBrowserScreen(
    initialUrl: String = "https://www.google.com",
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val scope = rememberCoroutineScope()
    
    // State
    val downloadManager = remember { DaoDownloadManager(context) }
    val bookmarkManager = remember { BookmarkManager(context) }
    
    var tabs = remember { mutableStateListOf(BrowserTab(url = initialUrl)) }
    var activeTabIndex by remember { mutableIntStateOf(0) }
    var urlInput by remember { mutableStateOf(initialUrl) }
    var showTabs by remember { mutableStateOf(false) }
    var showBookmarks by remember { mutableStateOf(false) }
    var showDownloads by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }
    
    // Active tab state
    val activeTab get() = tabs.getOrNull(activeTabIndex)
    var isLoading by remember { mutableStateOf(false) }
    var loadProgress by remember { mutableIntStateOf(0) }
    var canGoBack by remember { mutableStateOf(false) }
    var canGoForward by remember { mutableStateOf(false) }
    var currentTitle by remember { mutableStateOf("New Tab") }
    
    // WebView reference
    var webView by remember { mutableStateOf<WebView?>(null) }
    
    // Sync URL input when tab changes
    LaunchedEffect(activeTabIndex) {
        activeTab?.let {
            urlInput = it.url
            currentTitle = it.title
        }
    }
    
    // Permission launcher for storage
    val storagePermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = permissions.values.all { it }
        if (!granted) {
            Toast.makeText(context, "Storage permission needed for downloads", Toast.LENGTH_LONG).show()
        }
    }
    
    // Request storage permission if needed
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.Q) {
            val needsPermission = ContextCompat.checkSelfPermission(
                context, Manifest.permission.WRITE_EXTERNAL_STORAGE
            ) != PackageManager.PERMISSION_GRANTED
            
            if (needsPermission) {
                storagePermissionLauncher.launch(
                    arrayOf(
                        Manifest.permission.WRITE_EXTERNAL_STORAGE,
                        Manifest.permission.READ_EXTERNAL_STORAGE
                    )
                )
            }
        }
    }
    
    // ==================== UI ====================
    Column(modifier = modifier.fillMaxSize().background(YinBlack)) {
        
        // --- TOP BAR (Browser Chrome) ---
        BrowserTopBar(
            urlInput = urlInput,
            onUrlInputChange = { urlInput = it },
            onUrlSubmit = { url ->
                val fullUrl = if (!url.startsWith("http://") && !url.startsWith("https://")) {
                    if (url.contains(".") && !url.contains(" ")) "https://$url" 
                    else "https://www.google.com/search?q=${url.replace(" ", "+")}"
                } else url
                urlInput = fullUrl
                webView?.loadUrl(fullUrl)
                focusManager.clearFocus()
            },
            isLoading = isLoading,
            loadProgress = loadProgress,
            canGoBack = canGoBack,
            canGoForward = canGoForward,
            onBack = { webView?.goBack() },
            onForward = { webView?.goForward() },
            onRefresh = { webView?.reload() },
            onStop = { webView?.stopLoading() },
            onHome = {
                urlInput = "https://www.google.com"
                webView?.loadUrl("https://www.google.com")
            },
            onTabs = { showTabs = true },
            onBookmarks = { showBookmarks = true },
            onDownloads = { showDownloads = true },
            onMenu = { showMenu = true },
            tabCount = tabs.size,
            currentTitle = currentTitle
        )
        
        // --- PROGRESS BAR ---
        if (isLoading && loadProgress < 100) {
            LinearProgressIndicator(
                progress = { loadProgress / 100f },
                modifier = Modifier.fillMaxWidth().height(2.dp),
                color = ZenGold,
                trackColor = YinCardBg
            )
        }
        
        // --- WEBVIEW ---
        Box(modifier = Modifier.weight(1f)) {
            AndroidView(
                factory = { ctx ->
                    WebView(ctx).apply {
                        settings.apply {
                            javaScriptEnabled = true
                            domStorageEnabled = true
                            allowFileAccess = true
                            allowContentAccess = true
                            databaseEnabled = true
                            setSupportZoom(true)
                            builtInZoomControls = true
                            displayZoomControls = false
                            loadWithOverviewMode = true
                            useWideViewPort = true
                            mediaPlaybackRequiresUserGesture = false
                            allowFileAccessFromFileURLs = true
                            allowUniversalAccessFromFileURLs = true
                            mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                            userAgentString = "Mozilla/5.0 (Linux; Android 14; Pixel 8 Pro) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
                        }
                        
                        webViewClient = object : WebViewClient() {
                            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                                url?.let { urlInput = it }
                                isLoading = true
                                loadProgress = 0
                            }
                            
                            override fun onPageFinished(view: WebView?, url: String?) {
                                isLoading = false
                                currentTitle = view?.title ?: "Web Page"
                                canGoBack = view?.canGoBack() ?: false
                                canGoForward = view?.canGoForward() ?: false
                                
                                // Update active tab info
                                val tab = tabs.getOrNull(activeTabIndex)
                                if (tab != null && url != null) {
                                    tabs[activeTabIndex] = tab.copy(
                                        url = url,
                                        title = view?.title ?: url,
                                        webView = view
                                    )
                                }
                            }
                            
                            override fun onReceivedError(
                                view: WebView?,
                                request: WebResourceRequest?,
                                error: WebResourceError?
                            ) {
                                isLoading = false
                            }
                        }
                        
                        webChromeClient = object : WebChromeClient() {
                            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                                loadProgress = newProgress
                            }
                            
                            override fun onReceivedTitle(view: WebView?, title: String?) {
                                currentTitle = title ?: "Web Page"
                            }
                        }
                        
                        downloadListener = DownloadListener { downloadUrl, userAgent, contentDisposition, mimeType, contentLength ->
                            val isVideo = mimeType?.startsWith("video/") == true ||
                                    downloadUrl.contains(Regex("\\.(mp4|webm|mkv|avi|mov|flv|3gp)(\\?.*)?$"))
                            
                            val fileName = DaoStorageHelper.extractFileName(downloadUrl, contentDisposition, null)
                            val friendlyName = fileName ?: "download"
                            
                            downloadManager.downloadFile(
                                url = downloadUrl,
                                fileName = friendlyName,
                                mimeType = mimeType,
                                userAgent = userAgent,
                                contentDisposition = contentDisposition,
                                isVideo = isVideo
                            )
                            
                            Toast.makeText(context, "Downloading: $friendlyName", Toast.LENGTH_SHORT).show()
                        }
                        
                        loadUrl(initialUrl)
                        webView = this
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
            
            // Floating video download button (appears on pages with videos)
            FloatingVideoDetector(
                webView = webView,
                downloadManager = downloadManager,
                context = context
            )
        }
        
        // --- BOTTOM BAR (Quick Actions) ---
        BrowserBottomBar(
            onShare = {
                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, activeTab?.url ?: "")
                    putExtra(Intent.EXTRA_SUBJECT, currentTitle)
                }
                context.startActivity(Intent.createChooser(shareIntent, "Share via"))
            },
            onFind = {
                // Trigger find in page
                webView?.findAllAsync("")
                webView?.findNext(true)
            },
            onDesktopMode = {
                webView?.settings?.userAgentString = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
                webView?.settings?.useWideViewPort = true
                webView?.reload()
                Toast.makeText(context, "Desktop mode enabled", Toast.LENGTH_SHORT).show()
            },
            onBookmarkToggle = {
                bookmarkManager.toggleBookmark(currentTitle, activeTab?.url ?: "")
                Toast.makeText(
                    context,
                    if (bookmarkManager.isBookmarked(activeTab?.url ?: "")) "Bookmarked!" else "Removed bookmark",
                    Toast.LENGTH_SHORT
                ).show()
            },
            isBookmarked = bookmarkManager.isBookmarked(activeTab?.url ?: "")
        )
    }
    
    // ==================== DIALOGS ====================
    
    // Tabs Dialog
    if (showTabs) {
        AlertDialog(
            onDismissRequest = { showTabs = false },
            title = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Tabs (${tabs.size})", fontFamily = FontFamily.Serif, color = ZenGold)
                    TextButton(onClick = {
                        tabs.add(BrowserTab(url = "https://www.google.com"))
                        activeTabIndex = tabs.size - 1
                        urlInput = "https://www.google.com"
                    }) {
                        Icon(Icons.Default.Add, contentDescription = null, tint = ZenGold, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("New Tab", color = ZenGold)
                    }
                }
            },
            text = {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().heightIn(max = 400.dp)
                ) {
                    items(tabs.size) { index ->
                        val tab = tabs[index]
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 2.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .clickable {
                                    activeTabIndex = index
                                    urlInput = tab.url
                                    showTabs = false
                                    // Restore or reload
                                    tab.webView?.let { webView = it }
                                },
                            color = if (index == activeTabIndex) YinCardBg else Color.Transparent
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = tab.title,
                                        color = YinText,
                                        fontWeight = if (index == activeTabIndex) FontWeight.Bold else FontWeight.Normal,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = tab.url,
                                        color = YinTextSecondary,
                                        fontSize = 11.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                if (tabs.size > 1) {
                                    IconButton(
                                        onClick = {
                                            if (tabs.size > 1) {
                                                tabs.removeAt(index)
                                                if (activeTabIndex >= tabs.size) {
                                                    activeTabIndex = tabs.size - 1
                                                }
                                            }
                                        }
                                    ) {
                                        Icon(Icons.Default.Close, contentDescription = "Close tab", tint = ZenRed, modifier = Modifier.size(18.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showTabs = false }) {
                    Text("Done", color = ZenGold)
                }
            },
            containerColor = YinBlack
        )
    }
    
    // Bookmarks Dialog
    if (showBookmarks) {
        val bookmarks = remember { mutableStateListOf<BookmarkEntry>().also { it.addAll(bookmarkManager.getBookmarks()) } }
        
        AlertDialog(
            onDismissRequest = { showBookmarks = false },
            title = {
                Text("📑 Bookmarks", fontFamily = FontFamily.Serif, color = ZenGold)
            },
            text = {
                if (bookmarks.isEmpty()) {
                    Text("No bookmarks yet", color = YinTextSecondary)
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth().heightIn(max = 400.dp)
                    ) {
                        items(bookmarks) { bookmark ->
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 2.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable {
                                        urlInput = bookmark.url
                                        webView?.loadUrl(bookmark.url)
                                        showBookmarks = false
                                    },
                                color = Color.Transparent
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(bookmark.title, color = YinText, fontWeight = FontWeight.Medium)
                                        Text(
                                            bookmark.url,
                                            color = YinTextSecondary,
                                            fontSize = 11.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    IconButton(onClick = {
                                        bookmarkManager.removeBookmark(bookmark.id)
                                        bookmarks.remove(bookmark)
                                    }) {
                                        Icon(Icons.Default.Delete, contentDescription = null, tint = ZenRed, modifier = Modifier.size(18.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showBookmarks = false }) {
                    Text("Close", color = ZenGold)
                }
            },
            containerColor = YinBlack
        )
    }
    
    // Downloads Dialog
    if (showDownloads) {
        AlertDialog(
            onDismissRequest = { showDownloads = false },
            title = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("⬇ Downloads", fontFamily = FontFamily.Serif, color = ZenGold)
                    TextButton(onClick = {
                        downloadManager.clearCompleted()
                    }) {
                        Text("Clear", color = ZenRed, fontSize = 12.sp)
                    }
                }
            },
            text = {
                val allDownloads = downloadManager.activeDownloads.toList() + downloadManager.getAllCompleted()
                
                if (allDownloads.isEmpty()) {
                    Text("No downloads yet", color = YinTextSecondary)
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth().heightIn(max = 400.dp)
                    ) {
                        items(allDownloads.reversed()) { download ->
                            Card(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                colors = CardDefaults.cardColors(containerColor = YinCardBg)
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text(
                                        text = download.fileName,
                                        color = YinText,
                                        fontWeight = FontWeight.Medium,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Spacer(Modifier.height(4.dp))
                                    
                                    when (download.status) {
                                        DownloadStatus.DOWNLOADING -> {
                                            LinearProgressIndicator(
                                                progress = { download.progress / 100f },
                                                modifier = Modifier.fillMaxWidth().height(3.dp),
                                                color = ZenGold,
                                                trackColor = YinBlack
                                            )
                                            Text(
                                                "${download.progress}%",
                                                color = YinTextSecondary,
                                                fontSize = 11.sp
                                            )
                                        }
                                        DownloadStatus.COMPLETE -> {
                                            Text("✅ Complete", color = Color(0xFF4CAF50), fontSize = 11.sp)
                                            if (download.fileSize > 0) {
                                                Text(
                                                    formatFileSize(download.fileSize),
                                                    color = YinTextSecondary,
                                                    fontSize = 11.sp
                                                )
                                            }
                                        }
                                        DownloadStatus.FAILED -> {
                                            Text("❌ Failed", color = ZenRed, fontSize = 11.sp)
                                        }
                                        DownloadStatus.PAUSED -> {
                                            Text("⏸ Paused", color = YinTextSecondary, fontSize = 11.sp)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showDownloads = false }) {
                    Text("Close", color = ZenGold)
                }
            },
            containerColor = YinBlack
        )
    }
    
    // Menu Dialog
    if (showMenu) {
        AlertDialog(
            onDismissRequest = { showMenu = false },
            title = { Text("Browser Menu", fontFamily = FontFamily.Serif, color = ZenGold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    BrowserMenuItem("🔍 Find in Page", onClick = {
                        webView?.findAllAsync("")
                        webView?.findNext(true)
                        showMenu = false
                    })
                    BrowserMenuItem("🖥 Request Desktop Site", onClick = {
                        webView?.settings?.userAgentString = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36"
                        webView?.reload()
                        showMenu = false
                    })
                    BrowserMenuItem("📱 Request Mobile Site", onClick = {
                        webView?.settings?.userAgentString = null // Reset to default
                        webView?.reload()
                        showMenu = false
                    })
                    BrowserMenuItem("🗑 Clear Browsing Data", onClick = {
                        WebStorage.getInstance().deleteAllData()
                        CookieManager.getInstance().removeAllCookies(null)
                        Toast.makeText(context, "Browsing data cleared", Toast.LENGTH_SHORT).show()
                        showMenu = false
                    })
                    BrowserMenuItem("🔗 Copy URL", onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText("URL", activeTab?.url ?: ""))
                        Toast.makeText(context, "URL copied", Toast.LENGTH_SHORT).show()
                        showMenu = false
                    })
                }
            },
            confirmButton = {
                TextButton(onClick = { showMenu = false }) {
                    Text("Close", color = ZenGold)
                }
            },
            containerColor = YinBlack
        )
    }
}

// ==================== SUB-COMPOSABLES ====================

@Composable
private fun BrowserTopBar(
    urlInput: String,
    onUrlInputChange: (String) -> Unit,
    onUrlSubmit: (String) -> Unit,
    isLoading: Boolean,
    loadProgress: Int,
    canGoBack: Boolean,
    canGoForward: Boolean,
    onBack: () -> Unit,
    onForward: () -> Unit,
    onRefresh: () -> Unit,
    onStop: () -> Unit,
    onHome: () -> Unit,
    onTabs: () -> Unit,
    onBookmarks: () -> Unit,
    onDownloads: () -> Unit,
    onMenu: () -> Unit,
    tabCount: Int,
    currentTitle: String
) {
    Surface(
        color = YinCardBg,
        shadowElevation = 2.dp
    ) {
        Column {
            // Navigation buttons row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Back
                IconButton(
                    onClick = onBack,
                    enabled = canGoBack
                ) {
                    Icon(
                        Icons.Default.ArrowBack,
                        contentDescription = "Back",
                        tint = if (canGoBack) YinText else YinTextSecondary.copy(alpha = 0.4f),
                        modifier = Modifier.size(22.dp)
                    )
                }
                
                // Forward
                IconButton(
                    onClick = onForward,
                    enabled = canGoForward
                ) {
                    Icon(
                        Icons.Default.ArrowForward,
                        contentDescription = "Forward",
                        tint = if (canGoForward) YinText else YinTextSecondary.copy(alpha = 0.4f),
                        modifier = Modifier.size(22.dp)
                    )
                }
                
                // Refresh / Stop
                IconButton(
                    onClick = if (isLoading) onStop else onRefresh
                ) {
                    Icon(
                        if (isLoading) Icons.Default.Close else Icons.Default.Refresh,
                        contentDescription = if (isLoading) "Stop" else "Refresh",
                        tint = YinText,
                        modifier = Modifier.size(22.dp)
                    )
                }
                
                // Home
                IconButton(onClick = onHome) {
                    Icon(
                        Icons.Default.Home,
                        contentDescription = "Home",
                        tint = ZenGold,
                        modifier = Modifier.size(22.dp)
                    )
                }
                
                Spacer(Modifier.width(4.dp))
                
                // URL Bar                OutlinedTextField(
                    value = urlInput,
                    onValueChange = onUrlInputChange,
                    modifier = Modifier
                        .weight(1f)
                        .height(42.dp),
                    singleLine = true,
                    textStyle = androidx.compose.ui.text.TextStyle(
                        fontSize = 13.sp,
                        color = YinText,
                        fontFamily = FontFamily.Monospace
                    ),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = ZenGold,
                        unfocusedBorderColor = Color(0xFF333340),
                        cursorColor = ZenGold,
                        focusedContainerColor = Color(0xFF1A1A22),
                        unfocusedContainerColor = Color(0xFF1A1A22)
                    ),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Uri,
                        imeAction = ImeAction.Go
                    ),
                    keyboardActions = KeyboardActions(
                        onGo = { onUrlSubmit(urlInput) }
                    ),
                    placeholder = {
                        Text(
                            "Search or enter URL",
                            color = YinTextSecondary.copy(alpha = 0.5f),
                            fontSize = 12.sp
                        )
                    },
                    shape = RoundedCornerShape(8.dp)
                )
                
                Spacer(Modifier.width(4.dp))
                
                // Tabs button
                Box {
                    IconButton(onClick = onTabs) {
                        Icon(
                            Icons.Default.Tab,
                            contentDescription = "Tabs",
                            tint = YinText,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    if (tabCount > 0) {
                        Badge(
                            modifier = Modifier.align(Alignment.TopEnd).offset(x = (-4).dp, y = 4.dp),
                            containerColor = ZenGold
                        ) {
                            Text("$tabCount", fontSize = 9.sp, color = YinBlack)
                        }
                    }
                }
                
                // Bookmarks
                IconButton(onClick = onBookmarks) {
                    Icon(
                        Icons.Default.Bookmark,
                        contentDescription = "Bookmarks",
                        tint = ZenGold,
                        modifier = Modifier.size(22.dp)
                    )
                }
                
                // Downloads
                IconButton(onClick = onDownloads) {
                    Icon(
                        Icons.Default.Download,
                        contentDescription = "Downloads",
                        tint = ZenBlue,
                        modifier = Modifier.size(22.dp)
                    )
                }
                
                // More menu
                IconButton(onClick = onMenu) {
                    Icon(
                        Icons.Default.MoreVert,
                        contentDescription = "Menu",
                        tint = YinText,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun BrowserBottomBar(
    onShare: () -> Unit,
    onFind: () -> Unit,
    onDesktopMode: () -> Unit,
    onBookmarkToggle: () -> Unit,
    isBookmarked: Boolean
) {
    Surface(
        color = YinCardBg,
        shadowElevation = 2.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            BottomBarButton(icon = Icons.Default.Share, label = "Share", onClick = onShare)
            BottomBarButton(icon = Icons.Default.Search, label = "Find", onClick = onFind)
            BottomBarButton(icon = Icons.Default.DesktopWindows, label = "Desktop", onClick = onDesktopMode)
            BottomBarButton(
                icon = if (isBookmarked) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                label = if (isBookmarked) "Saved" else "Bookmark",
                onClick = onBookmarkToggle,
                tint = if (isBookmarked) ZenGold else YinTextSecondary
            )
        }
    }
}

@Composable
private fun BottomBarButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
    tint: Color = YinTextSecondary
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Icon(icon, contentDescription = label, tint = tint, modifier = Modifier.size(18.dp))
        Text(label, color = tint, fontSize = 10.sp)
    }
}

@Composable
private fun BrowserMenuItem(text: String, onClick: () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick),
        color = Color.Transparent
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(12.dp),
            color = YinText,
            fontSize = 14.sp
        )
    }
}

@Composable
private fun FloatingVideoDetector(
    webView: WebView?,
    downloadManager: DaoDownloadManager,
    context: Context
) {
    var showVideoButton by remember { mutableStateOf(false) }
    var videoUrls by remember { mutableStateOf<List<String>>(emptyList()) }
    
    // Inject JavaScript to detect videos
    LaunchedEffect(webView) {
        webView?.let { view ->
            // We can add a periodic check or trigger on page load
            view.webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView, url: String) {
                    super.onPageFinished(view, url)
                    // Inject video detection script
                    view.evaluateJavascript("""
                        (function() {
                            var videos = [];
                            document.querySelectorAll('video source, video').forEach(function(el) {
                                var src = el.src || el.getAttribute('src');
                                if (src && !videos.includes(src)) videos.push(src);
                            });
                            return JSON.stringify(videos);
                        })();
                    """.trimIndent()) { result ->
                        try {
                            val parsed = result?.trim('"')?.replace("\\\"", "\"")
                            if (!parsed.isNullOrBlank() && parsed != "null" && parsed != "[]") {
                                val urls = org.json.JSONArray(parsed)
                                val list = mutableListOf<String>()
                                for (i in 0 until urls.length()) {
                                    list.add(urls.getString(i))
                                }
                                if (list.isNotEmpty()) {
                                    videoUrls = list
                                    showVideoButton = true
                                }
                            }
                        } catch (_: Exception) {}
                    }
                }
            }
        }
    }
    
    // Floating action button for video download
    AnimatedVisibility(
        visible = showVideoButton && videoUrls.isNotEmpty(),
        enter = fadeIn() + scaleIn(),
        exit = fadeOut() + scaleOut(),
        modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp)
    ) {
        FloatingActionButton(
            onClick = {
                videoUrls.forEach { videoUrl ->
                    val fileName = "video_${System.currentTimeMillis()}.mp4"
                    downloadManager.downloadFile(
                        url = videoUrl,
                        fileName = fileName,
                        isVideo = true
                    )
                    Toast.makeText(context, "Downloading video...", Toast.LENGTH_SHORT).show()
                }
                showVideoButton = false
            },
            containerColor = ZenRed,
            contentColor = Color.White
        ) {
            Icon(Icons.Default.VideoFile, contentDescription = "Download Video")
        }
    }
}

// ==================== UTILITY FUNCTIONS ====================

private fun formatFileSize(bytes: Long): String {
    return when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> "${bytes / 1024} KB"
        bytes < 1024 * 1024 * 1024 -> "${"%.1f".format(bytes / (1024.0 * 1024.0))} MB"
        else -> "${"%.2f".format(bytes / (1024.0 * 1024.0 * 1024.0))} GB"
    }
}

// Extension for DaoStorageHelper
private fun DaoStorageHelper.Companion.extractFileName(
    url: String, 
    contentDisposition: String?, 
    fallback: String?
): String? {
    if (!contentDisposition.isNullOrBlank()) {
        val filenameRegex = Regex("filename[^;=\n]*=((['\"]).*?\\2|[^;\n]*)")
        val match = filenameRegex.find(contentDisposition)
        if (match != null) {
            var name = match.groupValues[1].replace("\"", "").trim()
            try {
                name = URLDecoder.decode(name, "UTF-8")
            } catch (_: Exception) {}
            return name
        }
    }
    try {
        val urlObj = java.net.URL(url)
        val path = urlObj.path
        val lastSegment = path.substringAfterLast("/")
        if (lastSegment.isNotBlank() && lastSegment.contains(".")) {
            return URLDecoder.decode(lastSegment, "UTF-8")
        }
    } catch (_: Exception) {}
    return fallback
}

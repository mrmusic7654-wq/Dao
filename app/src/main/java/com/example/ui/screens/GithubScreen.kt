package com.example.ui.screens

import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.repository.UserPreferences
import com.example.ui.theme.*
import kotlinx.coroutines.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

// ==================== DATA MODELS ====================

data class GitHubRepo(
    val name: String,
    val fullName: String,
    val description: String = "",
    val stars: Int = 0,
    val forks: Int = 0,
    val language: String = "",
    val isPrivate: Boolean = false,
    val updatedAt: String = "",
    val cloneUrl: String = "",
    val htmlUrl: String = ""
)

data class GitHubIssue(
    val number: Int,
    val title: String,
    val state: String,
    val author: String,
    val createdAt: String,
    val labels: List<String> = emptyList()
)

data class GitHubPR(
    val number: Int,
    val title: String,
    val state: String,
    val author: String,
    val branch: String
)

data class GitHubNotification(
    val id: String,
    val title: String,
    val type: String,
    val repo: String,
    val updatedAt: String,
    val isUnread: Boolean
)

enum class GitHubTab { REPOS, ISSUES, PULLS, NOTIFICATIONS, ACTIONS, AGENT }

// ==================== GITHUB API SERVICE ====================

object GitHubApiService {
    private const val BASE_URL = "https://api.github.com"

    fun executeRequest(token: String, endpoint: String, method: String = "GET", body: String? = null): String {
        val url = URL("$BASE_URL$endpoint")
        val connection = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = method
            setRequestProperty("Authorization", "token $token")
            setRequestProperty("Accept", "application/vnd.github.v3+json")
            setRequestProperty("User-Agent", "Dao-App")
            if (body != null) { doOutput = true; outputStream.write(body.toByteArray()) }
            connectTimeout = 15000; readTimeout = 15000
        }
        return try {
            if (connection.responseCode in 200..299) BufferedReader(InputStreamReader(connection.inputStream)).readText()
            else "{\"error\": \"HTTP ${connection.responseCode}\"}"
        } catch (e: Exception) { "{\"error\": \"${e.message}\"}" }
        finally { connection.disconnect() }
    }

    fun listRepos(token: String): List<GitHubRepo> {
        val json = executeRequest(token, "/user/repos?sort=updated&per_page=50")
        if (json.contains("\"error\"")) return emptyList()
        val arr = JSONArray(json)
        return (0 until arr.length()).map { i ->
            val obj = arr.getJSONObject(i)
            GitHubRepo(
                name = obj.getString("name"), fullName = obj.getString("full_name"),
                description = obj.optString("description", ""), stars = obj.optInt("stargazers_count", 0),
                forks = obj.optInt("forks_count", 0), language = obj.optString("language", ""),
                isPrivate = obj.optBoolean("private", false), updatedAt = obj.optString("updated_at", ""),
                cloneUrl = obj.optString("clone_url", ""), htmlUrl = obj.optString("html_url", "")
            )
        }
    }

    fun listIssues(token: String, owner: String, repo: String): List<GitHubIssue> {
        val json = executeRequest(token, "/repos/$owner/$repo/issues?state=all&per_page=30")
        if (json.contains("\"error\"")) return emptyList()
        val arr = JSONArray(json)
        return (0 until arr.length()).mapNotNull { i ->
            val obj = arr.getJSONObject(i)
            if (obj.has("pull_request")) return@mapNotNull null
            val labelsArr = obj.optJSONArray("labels")
            val labels = mutableListOf<String>()
            if (labelsArr != null) for (j in 0 until labelsArr.length()) labels.add(labelsArr.getJSONObject(j).getString("name"))
            GitHubIssue(
                number = obj.getInt("number"), title = obj.getString("title"),
                state = obj.getString("state"), author = obj.getJSONObject("user").getString("login"),
                createdAt = obj.getString("created_at"), labels = labels
            )
        }
    }

    fun listPRs(token: String, owner: String, repo: String): List<GitHubPR> {
        val json = executeRequest(token, "/repos/$owner/$repo/pulls?state=all&per_page=30")
        if (json.contains("\"error\"")) return emptyList()
        val arr = JSONArray(json)
        return (0 until arr.length()).map { i ->
            val obj = arr.getJSONObject(i)
            GitHubPR(number = obj.getInt("number"), title = obj.getString("title"),
                state = obj.getString("state"), author = obj.getJSONObject("user").getString("login"),
                branch = obj.getJSONObject("head").getString("ref"))
        }
    }

    fun listNotifications(token: String): List<GitHubNotification> {
        val json = executeRequest(token, "/notifications?per_page=30")
        if (json.contains("\"error\"")) return emptyList()
        val arr = JSONArray(json)
        return (0 until arr.length()).map { i ->
            val obj = arr.getJSONObject(i)
            GitHubNotification(id = obj.getString("id"), title = obj.getJSONObject("subject").getString("title"),
                type = obj.getJSONObject("subject").optString("type", "unknown"),
                repo = obj.getJSONObject("repository").getString("full_name"),
                updatedAt = obj.getString("updated_at"), isUnread = obj.optBoolean("unread", true))
        }
    }

    fun createRepo(token: String, name: String, description: String, isPrivate: Boolean): String {
        val body = JSONObject().apply { put("name", name); put("description", description); put("private", isPrivate); put("auto_init", true) }.toString()
        return executeRequest(token, "/user/repos", "POST", body)
    }

    fun createIssue(token: String, owner: String, repo: String, title: String, body: String): String {
        val json = JSONObject().apply { put("title", title); put("body", body) }.toString()
        return executeRequest(token, "/repos/$owner/$repo/issues", "POST", json)
    }

    fun deleteRepo(token: String, owner: String, repo: String): String {
        return executeRequest(token, "/repos/$owner/$repo", "DELETE")
    }
}

// ==================== GITHUB SCREEN ====================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GitHubScreen(isDark: Boolean, onMenuClick: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember { UserPreferences(context) }
    var token by remember { mutableStateOf(prefs.githubApiKey) }
    var showTokenDialog by remember { mutableStateOf(false) }
    var tokenInput by remember { mutableStateOf(token) }

    var activeTab by remember { mutableStateOf(GitHubTab.REPOS) }
    var isLoading by remember { mutableStateOf(false) }

    var repos by remember { mutableStateOf(listOf<GitHubRepo>()) }
    var issues by remember { mutableStateOf(listOf<GitHubIssue>()) }
    var pulls by remember { mutableStateOf(listOf<GitHubPR>()) }
    var notifications by remember { mutableStateOf(listOf<GitHubNotification>()) }
    var selectedRepo by remember { mutableStateOf<GitHubRepo?>(null) }

    var showCreateRepoDialog by remember { mutableStateOf(false) }
    var showCreateIssueDialog by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf<GitHubRepo?>(null) }
    var newRepoName by remember { mutableStateOf("") }
    var newRepoDesc by remember { mutableStateOf("") }
    var newRepoPrivate by remember { mutableStateOf(false) }
    var newIssueTitle by remember { mutableStateOf("") }
    var newIssueBody by remember { mutableStateOf("") }
    var statusMessage by remember { mutableStateOf("") }
    var showStatus by remember { mutableStateOf(false) }

    val scope = rememberCoroutineScope()

    fun loadRepos() {
        if (token.isBlank()) { showTokenDialog = true; return }
        isLoading = true
        scope.launch(Dispatchers.IO) { repos = GitHubApiService.listRepos(token); isLoading = false }
    }

    fun loadIssues(owner: String, repo: String) {
        isLoading = true
        scope.launch(Dispatchers.IO) { issues = GitHubApiService.listIssues(token, owner, repo); pulls = GitHubApiService.listPRs(token, owner, repo); isLoading = false }
    }

    fun loadNotifications() {
        isLoading = true
        scope.launch(Dispatchers.IO) { notifications = GitHubApiService.listNotifications(token); isLoading = false }
    }

    LaunchedEffect(activeTab) {
        when (activeTab) {
            GitHubTab.REPOS -> loadRepos()
            GitHubTab.NOTIFICATIONS -> loadNotifications()
            else -> {}
        }
    }

    LaunchedEffect(selectedRepo) {
        selectedRepo?.let {
            if (activeTab == GitHubTab.ISSUES || activeTab == GitHubTab.PULLS) {
                val parts = it.fullName.split("/")
                if (parts.size == 2) loadIssues(parts[0], parts[1])
            }
        }
    }

    // ==================== DIALOGS ====================

    if (showTokenDialog) {
        AlertDialog(
            onDismissRequest = { showTokenDialog = false },
            title = { Text("GitHub Token", fontFamily = FontFamily.Serif, color = ZenGold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Enter your GitHub Personal Access Token", color = YinTextSecondary, fontSize = 12.sp)
                    OutlinedTextField(value = tokenInput, onValueChange = { tokenInput = it }, placeholder = { Text("ghp_...") },
                        modifier = Modifier.fillMaxWidth(), singleLine = true, colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = ZenGold))
                    Text("Token needs: repo, issues, notifications scopes", color = YinTextSecondary.copy(alpha = 0.6f), fontSize = 10.sp)
                }
            },
            confirmButton = {
                Button(onClick = { token = tokenInput; prefs.githubApiKey = tokenInput; showTokenDialog = false; loadRepos() },
                    colors = ButtonDefaults.buttonColors(containerColor = ZenGold)) { Text("Save", color = Color.Black) }
            },
            dismissButton = { TextButton(onClick = { showTokenDialog = false }) { Text("Cancel", color = ZenGold) } },
            containerColor = if (isDark) Color(0xFF14131A) else Color(0xFFF7F4EE)
        )
    }

    if (showCreateRepoDialog) {
        AlertDialog(
            onDismissRequest = { showCreateRepoDialog = false },
            title = { Text("New Repository", fontFamily = FontFamily.Serif, color = ZenGold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = newRepoName, onValueChange = { newRepoName = it }, label = { Text("Name") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                    OutlinedTextField(value = newRepoDesc, onValueChange = { newRepoDesc = it }, label = { Text("Description") }, modifier = Modifier.fillMaxWidth(), maxLines = 3)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Switch(checked = newRepoPrivate, onCheckedChange = { newRepoPrivate = it })
                        Spacer(Modifier.width(8.dp)); Text("Private", color = YinText)
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    scope.launch(Dispatchers.IO) {
                        val result = GitHubApiService.createRepo(token, newRepoName, newRepoDesc, newRepoPrivate)
                        statusMessage = if (result.contains("\"error\"")) "Failed to create" else "Repo created!"; showStatus = true
                    }
                    showCreateRepoDialog = false
                }, colors = ButtonDefaults.buttonColors(containerColor = ZenGold)) { Text("Create", color = Color.Black) }
            },
            dismissButton = { TextButton(onClick = { showCreateRepoDialog = false }) { Text("Cancel", color = ZenGold) } },
            containerColor = if (isDark) Color(0xFF14131A) else Color(0xFFF7F4EE)
        )
    }

    if (showCreateIssueDialog && selectedRepo != null) {
        AlertDialog(
            onDismissRequest = { showCreateIssueDialog = false },
            title = { Text("New Issue", fontFamily = FontFamily.Serif, color = ZenGold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Repo: ${selectedRepo!!.fullName}", color = YinTextSecondary, fontSize = 12.sp)
                    OutlinedTextField(value = newIssueTitle, onValueChange = { newIssueTitle = it }, label = { Text("Title") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                    OutlinedTextField(value = newIssueBody, onValueChange = { newIssueBody = it }, label = { Text("Description") }, modifier = Modifier.fillMaxWidth(), maxLines = 4)
                }
            },
            confirmButton = {
                Button(onClick = {
                    val parts = selectedRepo!!.fullName.split("/")
                    scope.launch(Dispatchers.IO) { GitHubApiService.createIssue(token, parts[0], parts[1], newIssueTitle, newIssueBody); statusMessage = "Issue created!"; showStatus = true }
                    showCreateIssueDialog = false
                }, colors = ButtonDefaults.buttonColors(containerColor = ZenGold)) { Text("Create", color = Color.Black) }
            },
            dismissButton = { TextButton(onClick = { showCreateIssueDialog = false }) { Text("Cancel", color = ZenGold) } },
            containerColor = if (isDark) Color(0xFF14131A) else Color(0xFFF7F4EE)
        )
    }

    showDeleteConfirm?.let { repo ->
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = null },
            title = { Text("Delete ${repo.name}?", color = ZenRed) },
            text = { Text("This cannot be undone.", color = YinTextSecondary) },
            confirmButton = {
                Button(onClick = {
                    val parts = repo.fullName.split("/")
                    scope.launch(Dispatchers.IO) { GitHubApiService.deleteRepo(token, parts[0], parts[1]) }
                    showDeleteConfirm = null; loadRepos()
                }, colors = ButtonDefaults.buttonColors(containerColor = ZenRed)) { Text("Delete", color = Color.White) }
            },
            dismissButton = { TextButton(onClick = { showDeleteConfirm = null }) { Text("Cancel", color = ZenGold) } },
            containerColor = if (isDark) Color(0xFF14131A) else Color(0xFFF7F4EE)
        )
    }

    LaunchedEffect(showStatus) {
        if (showStatus) { Toast.makeText(context, statusMessage, Toast.LENGTH_SHORT).show(); showStatus = false; if (activeTab == GitHubTab.REPOS) loadRepos() }
    }

    // ==================== MAIN UI ====================
    Column(modifier = Modifier.fillMaxSize().background(if (isDark) Color(0xFF070709) else Color(0xFFF1F0EC))) {
        Surface(color = if (isDark) YinBlack else YangWhite) {
            Column(modifier = Modifier.statusBarsPadding()) {
                Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onMenuClick) { Icon(Icons.Default.Menu, "Menu", tint = if (isDark) Color.White else Color.Black) }
                    Icon(Icons.Default.Hub, null, tint = Color(0xFF6E40C9), modifier = Modifier.size(24.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("GITHUB MANAGER", fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold, color = Color(0xFF6E40C9), style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.weight(1f))
                    IconButton(onClick = { showTokenDialog = true }) { Icon(Icons.Default.Key, "Token", tint = if (token.isNotBlank()) Color.Green else Color.Gray, modifier = Modifier.size(20.dp)) }
                }
                Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 8.dp, vertical = 2.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    GitHubTab.entries.forEach { tab ->
                        val label = when (tab) {
                            GitHubTab.REPOS -> "Repos"; GitHubTab.ISSUES -> "Issues"; GitHubTab.PULLS -> "PRs"
                            GitHubTab.NOTIFICATIONS -> "Notifs"; GitHubTab.ACTIONS -> "Actions"; GitHubTab.AGENT -> "🤖 Agent"
                        }
                        FilterChip(selected = activeTab == tab, onClick = { activeTab = tab; if (tab != GitHubTab.ISSUES && tab != GitHubTab.PULLS) selectedRepo = null },
                            label = { Text(label, fontSize = 11.sp) },
                            colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Color(0xFF6E40C9).copy(alpha = 0.2f), selectedLabelColor = Color(0xFF6E40C9)))
                    }
                }
            }
        }

        if (isLoading) LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), color = Color(0xFF6E40C9))

        Box(modifier = Modifier.fillMaxSize()) {
            when (activeTab) {
                GitHubTab.REPOS -> {
                    if (repos.isEmpty() && !isLoading) {
                        Column(modifier = Modifier.fillMaxSize().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                            Icon(Icons.Default.Folder, null, modifier = Modifier.size(64.dp), tint = Color.Gray.copy(alpha = 0.4f))
                            Text("No repos", color = Color.Gray, fontSize = 16.sp); Text("Add a token to get started", color = Color.Gray.copy(alpha = 0.6f), fontSize = 12.sp)
                        }
                    } else {
                        LazyColumn(contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            item {
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                    Text("${repos.size} repositories", color = YinTextSecondary, fontSize = 12.sp)
                                    Button(onClick = { newRepoName = ""; newRepoDesc = ""; newRepoPrivate = false; showCreateRepoDialog = true },
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6E40C9)), modifier = Modifier.height(32.dp)) {
                                        Icon(Icons.Default.Add, null, modifier = Modifier.size(14.dp), tint = Color.White); Text("New", color = Color.White, fontSize = 12.sp)
                                    }
                                }
                            }
                            items(repos) { repo ->
                                RepoCard(repo = repo, isSelected = selectedRepo == repo, onClick = {
                                    selectedRepo = if (selectedRepo == repo) null else repo
                                    if (selectedRepo != null) { activeTab = GitHubTab.ISSUES; val parts = repo.fullName.split("/"); if (parts.size == 2) loadIssues(parts[0], parts[1]) }
                                }, onDelete = { showDeleteConfirm = repo }, onCopyUrl = {
                                    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    cm.setPrimaryClip(android.content.ClipData.newPlainText("url", repo.cloneUrl))
                                    Toast.makeText(context, "Clone URL copied", Toast.LENGTH_SHORT).show()
                                })
                            }
                        }
                    }
                }
                GitHubTab.ISSUES -> {
                    if (selectedRepo == null) {
                        Column(modifier = Modifier.fillMaxSize().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) { Text("Select a repo first", color = Color.Gray, fontSize = 14.sp) }
                    } else {
                        Column {
                            Row(modifier = Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Text(selectedRepo!!.fullName, color = YinText, fontWeight = FontWeight.Bold)
                                Button(onClick = { newIssueTitle = ""; newIssueBody = ""; showCreateIssueDialog = true },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6E40C9)), modifier = Modifier.height(32.dp)) { Text("New Issue", color = Color.White, fontSize = 12.sp) }
                            }
                            LazyColumn(contentPadding = PaddingValues(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                items(issues) { issue -> IssueCard(issue) }
                                if (issues.isEmpty()) item { Text("No issues", color = Color.Gray, modifier = Modifier.padding(16.dp)) }
                            }
                        }
                    }
                }
                GitHubTab.PULLS -> {
                    if (selectedRepo == null) {
                        Column(modifier = Modifier.fillMaxSize().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) { Text("Select a repo first", color = Color.Gray, fontSize = 14.sp) }
                    } else {
                        Column {
                            Text(selectedRepo!!.fullName, color = YinText, fontWeight = FontWeight.Bold, modifier = Modifier.padding(12.dp))
                            LazyColumn(contentPadding = PaddingValues(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                items(pulls) { pr -> PRCard(pr) }
                                if (pulls.isEmpty()) item { Text("No pull requests", color = Color.Gray, modifier = Modifier.padding(16.dp)) }
                            }
                        }
                    }
                }
                GitHubTab.NOTIFICATIONS -> {
                    if (notifications.isEmpty() && !isLoading) {
                        Column(modifier = Modifier.fillMaxSize().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) { Text("No notifications", color = Color.Gray, fontSize = 14.sp) }
                    } else {
                        LazyColumn(contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) { items(notifications) { notif -> NotificationCard(notif) } }
                    }
                }
                GitHubTab.ACTIONS -> {
                    Column(modifier = Modifier.fillMaxSize().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                        Text("🚀 GitHub Actions", color = ZenGold, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        Text("Workflow runs and CI/CD status", color = Color.Gray, fontSize = 14.sp)
                        Text("Coming soon", color = Color.Gray.copy(alpha = 0.6f), fontSize = 12.sp)
                    }
                }
                GitHubTab.AGENT -> { GitHubAgentPanel(token, isDark) }
            }
        }
    }
}

// ==================== COMPONENT CARDS ====================

@Composable
private fun RepoCard(repo: GitHubRepo, isSelected: Boolean, onClick: () -> Unit, onDelete: () -> Unit, onCopyUrl: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = if (isSelected) Color(0xFF6E40C9).copy(alpha = 0.1f) else YinCardBg),
        border = if (isSelected) BorderStroke(1.dp, Color(0xFF6E40C9)) else null, shape = RoundedCornerShape(10.dp)) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(if (repo.isPrivate) Icons.Default.Lock else Icons.Default.Public, null, tint = if (repo.isPrivate) ZenGold else Color(0xFF4CAF50), modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(repo.name, color = YinText, fontWeight = FontWeight.Bold, fontSize = 15.sp, modifier = Modifier.weight(1f))
                IconButton(onClick = onCopyUrl, modifier = Modifier.size(28.dp)) { Icon(Icons.Default.ContentCopy, "Copy URL", tint = YinTextSecondary.copy(alpha = 0.6f), modifier = Modifier.size(14.dp)) }
                IconButton(onClick = onDelete, modifier = Modifier.size(28.dp)) { Icon(Icons.Default.Delete, "Delete", tint = ZenRed.copy(alpha = 0.6f), modifier = Modifier.size(14.dp)) }
            }
            if (repo.description.isNotBlank()) Text(repo.description, color = YinTextSecondary, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.padding(top = 6.dp)) {
                if (repo.language.isNotBlank()) {
                    Row(verticalAlignment = Alignment.CenterVertically) { Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(ZenGold)); Spacer(Modifier.width(4.dp)); Text(repo.language, color = YinTextSecondary, fontSize = 11.sp) }
                }
                Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.Star, null, tint = ZenGold, modifier = Modifier.size(14.dp)); Text("${repo.stars}", color = YinTextSecondary, fontSize = 11.sp) }
                Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.ForkRight, null, tint = YinTextSecondary, modifier = Modifier.size(14.dp)); Text("${repo.forks}", color = YinTextSecondary, fontSize = 11.sp) }
            }
        }
    }
}

@Composable
private fun IssueCard(issue: GitHubIssue) {
    Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = YinCardBg), shape = RoundedCornerShape(8.dp)) {
        Column(modifier = Modifier.padding(10.dp)) {
            Row {
                Surface(color = if (issue.state == "open") Color(0xFF4CAF50).copy(alpha = 0.2f) else Color(0xFF9E9E9E).copy(alpha = 0.2f), shape = RoundedCornerShape(4.dp)) {
                    Text(if (issue.state == "open") "Open" else "Closed", color = if (issue.state == "open") Color(0xFF4CAF50) else Color(0xFF9E9E9E), fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                }
                Spacer(Modifier.width(8.dp)); Text("#${issue.number}", color = YinTextSecondary, fontSize = 12.sp, modifier = Modifier.weight(1f))
                Text(issue.author, color = Color(0xFF6E40C9), fontSize = 11.sp)
            }
            Text(issue.title, color = YinText, fontWeight = FontWeight.Medium, fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp))
            if (issue.labels.isNotEmpty()) {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.padding(top = 4.dp)) {
                    issue.labels.take(3).forEach { label -> Surface(color = ZenGold.copy(alpha = 0.15f), shape = RoundedCornerShape(4.dp)) { Text(label, color = ZenGold, fontSize = 9.sp, modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)) } }
                }
            }
        }
    }
}

@Composable
private fun PRCard(pr: GitHubPR) {
    Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = YinCardBg), shape = RoundedCornerShape(8.dp)) {
        Column(modifier = Modifier.padding(10.dp)) {
            Row {
                Surface(color = if (pr.state == "open") Color(0xFF4CAF50).copy(alpha = 0.2f) else Color(0xFFCE93D8).copy(alpha = 0.2f), shape = RoundedCornerShape(4.dp)) {
                    Text(pr.state.uppercase(), color = if (pr.state == "open") Color(0xFF4CAF50) else Color(0xFFCE93D8), fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                }
                Spacer(Modifier.width(8.dp)); Text("#${pr.number}", color = YinTextSecondary, fontSize = 12.sp, modifier = Modifier.weight(1f))
                Text(pr.author, color = Color(0xFF6E40C9), fontSize = 11.sp)
            }
            Text(pr.title, color = YinText, fontWeight = FontWeight.Medium, fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp))
            Text("Branch: ${pr.branch}", color = YinTextSecondary, fontSize = 11.sp)
        }
    }
}

@Composable
private fun NotificationCard(notification: GitHubNotification) {
    Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = if (notification.isUnread) Color(0xFF6E40C9).copy(alpha = 0.08f) else YinCardBg), shape = RoundedCornerShape(8.dp)) {
        Row(modifier = Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            if (notification.isUnread) { Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(Color(0xFF6E40C9))); Spacer(Modifier.width(8.dp)) }
            Column(modifier = Modifier.weight(1f)) {
                Text(notification.title, color = YinText, fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text("${notification.repo} • ${notification.type}", color = YinTextSecondary, fontSize = 11.sp)
            }
        }
    }
}

@Composable
private fun GitHubAgentPanel(token: String, isDark: Boolean) {
    val context = LocalContext.current
    var commandInput by remember { mutableStateOf("") }
    var outputText by remember { mutableStateOf("🤖 AI Agent ready.\n\nCommands:\n- list repos\n- list issues <owner/repo>\n- create repo <name>\n- create issue <owner/repo> <title>\n- delete repo <owner/repo>") }
    val scope = rememberCoroutineScope()

    Column(modifier = Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("🤖 AI Agent Control", color = Color(0xFF6E40C9), fontWeight = FontWeight.Bold, fontFamily = FontFamily.Serif, fontSize = 18.sp)
        Text("Use natural language or direct API commands", color = YinTextSecondary, fontSize = 12.sp)

        Card(modifier = Modifier.weight(1f).fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color(0xFF0F0F12))) {
            LazyColumn(modifier = Modifier.fillMaxSize().padding(8.dp)) {
                item { Text(outputText, color = Color(0xFF8CBE91), fontSize = 12.sp, fontFamily = FontFamily.Monospace) }
            }
        }

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(value = commandInput, onValueChange = { commandInput = it },
                modifier = Modifier.weight(1f).height(48.dp), placeholder = { Text("Agent command...", fontSize = 12.sp) },
                singleLine = true, colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Color(0xFF6E40C9)), shape = RoundedCornerShape(10.dp))
            Button(onClick = {
                if (commandInput.isBlank() || token.isBlank()) return@Button
                val cmd = commandInput.lowercase(); outputText = "> $commandInput\n"
                scope.launch(Dispatchers.IO) {
                    outputText += when {
                        cmd.contains("list repos") -> GitHubApiService.executeRequest(token, "/user/repos?per_page=10")
                        cmd.contains("create repo") -> GitHubApiService.createRepo(token, commandInput.removePrefix("create repo").trim(), "", false)
                        cmd.contains("delete repo") -> {
                            val parts = commandInput.removePrefix("delete repo").trim().split("/")
                            if (parts.size == 2) GitHubApiService.deleteRepo(token, parts[0], parts[1]) else "Usage: delete repo <owner/repo>"
                        }
                        cmd.contains("list issues") -> {
                            val parts = commandInput.removePrefix("list issues").trim().split("/")
                            if (parts.size == 2) GitHubApiService.executeRequest(token, "/repos/$parts[0]/$parts[1]/issues?per_page=10") else "Usage: list issues <owner/repo>"
                        }
                        cmd.contains("create issue") -> {
                            val parts = commandInput.removePrefix("create issue").trim().split(" ", limit = 3)
                            if (parts.size >= 3) GitHubApiService.createIssue(token, parts[0], parts[1], parts[2], "") else "Usage: create issue <owner/repo> <title>"
                        }
                        else -> "Unknown. Try: list repos, create repo <name>, list issues <owner/repo>, delete repo <owner/repo>"
                    }
                }
                commandInput = ""
            }, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6E40C9))) { Text("Run", color = Color.White) }
        }
    }
}

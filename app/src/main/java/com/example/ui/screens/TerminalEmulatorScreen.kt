package com.example.ui.screens

import android.content.*
import android.content.pm.PackageManager
import android.os.*
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
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
import androidx.compose.ui.graphics.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.*
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.*
import com.example.ui.screens.GitHubApiService
import kotlinx.coroutines.*
import java.io.*
import java.text.SimpleDateFormat
import java.util.*
import java.util.Base64

// ==================== DATA MODELS ====================

data class TerminalLine(
    val id: String = UUID.randomUUID().toString(),
    val text: String,
    val type: LineType = LineType.OUTPUT,
    val timestamp: Long = System.currentTimeMillis()
)

enum class LineType { INPUT, OUTPUT, ERROR, SUCCESS, WARNING, SYSTEM, AI_SUGGESTION }

data class CommandHistory(
    val command: String,
    val timestamp: Long = System.currentTimeMillis(),
    val directory: String = "/"
)

data class CommandSuggestion(
    val command: String,
    val description: String,
    val category: String = "general"
)

// ==================== TERMINAL ENGINE ====================

object TerminalEngine {
    private val shell = if (File("/system/bin/sh").exists()) "/system/bin/sh" else "sh"
    var currentDirectory = "/sdcard"
    var isRootMode = false
    var isAiAssistEnabled = true
    var codespaceOwner = ""
    var codespaceRepo = ""
    var currentCodespaceDirectory = "/"
    val aliases = mutableMapOf<String, String>(
        "ll" to "ls -la",
        "la" to "ls -a",
        "cls" to "clear",
        "cp" to "cp -i",
        "mv" to "mv -i",
        "rm" to "rm -i",
        ".." to "cd ..",
        "..." to "cd ../..",
        "df" to "df -h",
        "du" to "du -h",
        "free" to "cat /proc/meminfo | grep Mem",
        "ip" to "ifconfig 2>/dev/null || ip addr"
    )

    val commandSuggestions = listOf(
        CommandSuggestion("ls -la", "List all files with details", "files"),
        CommandSuggestion("find . -name \"*.txt\"", "Find all text files", "files"),
        CommandSuggestion("df -h", "Show disk usage", "system"),
        CommandSuggestion("ps aux", "List running processes", "system"),
        CommandSuggestion("ping -c 4 google.com", "Test network connectivity", "network"),
        CommandSuggestion("curl -I https://google.com", "Check HTTP headers", "network"),
        CommandSuggestion("pm list packages", "List installed apps", "apps"),
        CommandSuggestion("dumpsys battery", "Battery information", "system"),
        CommandSuggestion("tar -czf backup.tar.gz /path", "Create compressed archive", "files"),
        CommandSuggestion("grep -r \"text\" .", "Search files for text", "files"),
        CommandSuggestion("chmod +x script.sh", "Make file executable", "files"),
        CommandSuggestion("md5sum file.apk", "Calculate file hash", "security"),
        CommandSuggestion("openssl enc -aes-256-cbc -in file", "Encrypt file", "security"),
        CommandSuggestion("netstat -tulpn", "Show open ports", "network"),
        CommandSuggestion("logcat -d | tail -50", "Last 50 log entries", "system")
    )

    fun executeCommand(command: String, context: Context): TerminalLine {
        val trimmed = command.trim()
        if (trimmed.isEmpty()) return TerminalLine(text = "", type = LineType.INPUT)

        // Handle built-in commands
        return when {
            trimmed == "clear" || trimmed == "cls" -> {
                TerminalLine(text = "CLEAR_SCREEN", type = LineType.SYSTEM)
            }
            trimmed == "help" || trimmed == "?" -> {
                val help = buildString {
                    appendLine("╔══════════════════════════════════════╗")
                    appendLine("║     DAO TERMINAL - COMMAND HELP     ║")
                    appendLine("╠══════════════════════════════════════╣")
                    appendLine("║ Built-in Commands:                   ║")
                    appendLine("║  help, ?     - Show this help        ║")
                    appendLine("║  clear, cls  - Clear screen          ║")
                    appendLine("║  pwd         - Print working dir     ║")
                    appendLine("║  cd <dir>    - Change directory      ║")
                    appendLine("║  ls [dir]    - List files            ║")
                    appendLine("║  cat <file>  - Read file contents    ║")
                    appendLine("║  mkdir <dir> - Create directory      ║")
                    appendLine("║  rm <file>   - Delete file           ║")
                    appendLine("║  cp <src> <dst> - Copy file          ║")
                    appendLine("║  mv <src> <dst> - Move file          ║")
                    appendLine("║  whoami      - Show current user     ║")
                    appendLine("║  date        - Show date/time        ║")
                    appendLine("║  alias       - Show aliases          ║")
                    appendLine("║  history     - Command history       ║")
                    appendLine("║  ai          - Toggle AI assist      ║")
                    appendLine("║  root        - Toggle root mode      ║")
                    appendLine("║  suggest     - AI command suggestion ║")
                    appendLine("╚══════════════════════════════════════╝")
                }
                TerminalLine(text = help, type = LineType.OUTPUT)
            }
            trimmed == "pwd" -> {
                TerminalLine(text = currentDirectory, type = LineType.SUCCESS)
            }
            trimmed.startsWith("cd ") -> {
                val target = trimmed.removePrefix("cd ").trim()
                val newDir = when (target) {
                    "~", "/home" -> Environment.getExternalStorageDirectory().absolutePath
                    "-" -> "/"
                    ".." -> File(currentDirectory).parent ?: currentDirectory
                    else -> if (target.startsWith("/")) target else File(currentDirectory, target).absolutePath
                }
                val dir = File(newDir)
                if (dir.exists() && dir.isDirectory) {
                    currentDirectory = dir.absolutePath
                    TerminalLine(text = "", type = LineType.SYSTEM)
                } else {
                    TerminalLine(text = "cd: no such directory: $target", type = LineType.ERROR)
                }
            }
            trimmed.startsWith("ls") -> {
                val args = trimmed.removePrefix("ls").trim()
                val showHidden = args.contains("-a")
                val showDetails = args.contains("-l")
                val path = args.replace(Regex("-[al]+"), "").trim().ifBlank { currentDirectory }
                val dir = File(if (path.startsWith("/")) path else File(currentDirectory, path).absolutePath)
                if (!dir.exists()) {
                    TerminalLine(text = "ls: cannot access '$path': No such file", type = LineType.ERROR)
                } else if (dir.isFile) {
                    TerminalLine(text = dir.name, type = LineType.OUTPUT)
                } else {
                    val files = dir.listFiles() ?: arrayOf()
                    val sorted = files.sortedBy { !it.isDirectory }
                    val output = buildString {
                        for (file in sorted) {
                            if (!showHidden && file.name.startsWith(".")) continue
                            if (showDetails) {
                                val perms = (if (file.isDirectory) "d" else "-") + "rwxr-xr-x"
                                val size = formatSize(file.length())
                                val date = SimpleDateFormat("MMM dd HH:mm", Locale.getDefault()).format(Date(file.lastModified()))
                                appendLine("$perms dao dao ${size.padStart(6)} $date ${file.name}")
                            } else {
                                append("${file.name}  ")
                            }
                        }
                    }
                    TerminalLine(text = output.trim(), type = LineType.OUTPUT)
                }
            }
            trimmed.startsWith("cat ") -> {
                val path = trimmed.removePrefix("cat ").trim()
                val file = File(if (path.startsWith("/")) path else File(currentDirectory, path).absolutePath)
                if (!file.exists()) {
                    TerminalLine(text = "cat: $path: No such file", type = LineType.ERROR)
                } else if (file.isDirectory) {
                    TerminalLine(text = "cat: $path: Is a directory", type = LineType.ERROR)
                } else {
                    try {
                        val content = file.readText().take(10000)
                        TerminalLine(text = content, type = LineType.OUTPUT)
                    } catch (e: Exception) {
                        TerminalLine(text = "cat: ${e.message}", type = LineType.ERROR)
                    }
                }
            }
            trimmed == "whoami" -> {
                TerminalLine(text = if (isRootMode) "root" else "dao", type = LineType.SUCCESS)
            }
            trimmed == "date" -> {
                TerminalLine(text = SimpleDateFormat("EEE MMM dd HH:mm:ss zzz yyyy", Locale.getDefault()).format(Date()), type = LineType.OUTPUT)
            }
            trimmed == "alias" -> {
                val output = aliases.entries.joinToString("\n") { "${it.key}='${it.value}'" }
                TerminalLine(text = output.ifBlank { "No aliases defined" }, type = LineType.OUTPUT)
            }
            trimmed == "history" -> {
                val output = commandHistory.takeLast(20).joinToString("\n") { "  ${commandHistory.indexOf(it) + 1}  ${it.command}" }
                TerminalLine(text = output, type = LineType.OUTPUT)
            }
            trimmed == "ai" -> {
                isAiAssistEnabled = !isAiAssistEnabled
                TerminalLine(text = "AI assist ${if (isAiAssistEnabled) "enabled" else "disabled"}", type = LineType.SYSTEM)
            }
            trimmed == "root" -> {
                isRootMode = !isRootMode
                TerminalLine(text = "Root mode ${if (isRootMode) "activated ⚡" else "deactivated"}", type = if (isRootMode) LineType.WARNING else LineType.SYSTEM)
            }
            trimmed.startsWith("github ") -> {
                val githubCmd = trimmed.removePrefix("github ").trim()
                val token = context.getSharedPreferences("dao_settings", Context.MODE_PRIVATE)
                    .getString("github_api_key", "") ?: ""
                if (token.isBlank()) {
                    TerminalLine(
                        text = "⚠️ GitHub token not set. Add it in Settings > API Integrations.",
                        type = LineType.ERROR
                    )
                } else {
                    handleGithubCommand(githubCmd, token, context)
                }
            }
            trimmed.startsWith("nano ") -> {
                val filePath = trimmed.removePrefix("nano ").trim()
                TerminalLine(text = "EDITOR_OPEN:$filePath", type = LineType.SYSTEM)
            }
            trimmed == "suggest" || trimmed == "ai suggest" -> {
                val suggestions = commandSuggestions.shuffled().take(5)
                val output = buildString {
                    appendLine("🤖 AI Command Suggestions:")
                    suggestions.forEach { s ->
                        appendLine("  ${s.command.padEnd(35)} — ${s.description}")
                    }
                }
                TerminalLine(text = output, type = LineType.AI_SUGGESTION)
            }
            trimmed.startsWith("echo ") -> {
                TerminalLine(text = trimmed.removePrefix("echo ").trim(), type = LineType.OUTPUT)
            }
            trimmed == "uname" || trimmed == "uname -a" -> {
                TerminalLine(text = "DaoOS 1.0.0 (Android ${Build.VERSION.SDK_INT}) ${Build.CPU_ABI} Dao/Terminal", type = LineType.OUTPUT)
            }
            trimmed == "env" -> {
                val output = buildString {
                    appendLine("USER=${if (isRootMode) "root" else "dao"}")
                    appendLine("HOME=/sdcard")
                    appendLine("PWD=$currentDirectory")
                    appendLine("SHELL=$shell")
                    appendLine("ANDROID_SDK=${Build.VERSION.SDK_INT}")
                    appendLine("MODEL=${Build.MODEL}")
                    appendLine("TERM=dao-terminal")
                }
                TerminalLine(text = output, type = LineType.OUTPUT)
            }
            else -> {
                // Execute as shell command
                executeShellCommand(trimmed)
            }
        }
    }

    private val commandHistory = mutableListOf<CommandHistory>()

    fun addToHistory(command: String) {
        commandHistory.add(CommandHistory(command = command, directory = currentDirectory))
        if (commandHistory.size > 500) commandHistory.removeAt(0)
    }

    fun getAiSuggestions(input: String): List<CommandSuggestion> {
        if (input.isBlank()) return commandSuggestions.shuffled().take(3)
        return commandSuggestions.filter {
            it.command.contains(input, ignoreCase = true) || it.description.contains(input, ignoreCase = true)
        }.take(5)
    }

    private fun executeShellCommand(command: String): TerminalLine {
        return try {
            val process = if (isRootMode) {
                Runtime.getRuntime().exec(arrayOf("su", "-c", command))
            } else {
                Runtime.getRuntime().exec(arrayOf(shell, "-c", "cd \"$currentDirectory\" && $command"))
            }

            val stdout = process.inputStream.bufferedReader().readText()
            val stderr = process.errorStream.bufferedReader().readText()
            process.waitFor()

            when {
                stderr.isNotBlank() -> TerminalLine(text = stderr.trim(), type = LineType.ERROR)
                stdout.isNotBlank() -> TerminalLine(text = stdout.trim(), type = LineType.OUTPUT)
                else -> TerminalLine(text = "(no output)", type = LineType.OUTPUT)
            }
        } catch (e: Exception) {
            TerminalLine(text = "Error: ${e.message}", type = LineType.ERROR)
        }
    }

    private fun handleGithubCommand(cmd: String, token: String, context: Context): TerminalLine {
        val parts = cmd.split(" ", limit = 2)
        val action = parts[0]
        val args = if (parts.size > 1) parts[1] else ""

        return try {
            when (action) {
                "create-repo" -> {
                    val name = args.trim()
                    if (name.isBlank()) {
                        TerminalLine(text = "Usage: github create-repo <name>", type = LineType.ERROR)
                    } else {
                        val result = GitHubApiService.createRepo(token, name, "", true)
                        if (result.contains("\"error\"")) {
                            TerminalLine(text = "Failed: $result", type = LineType.ERROR)
                        } else {
                            codespaceRepo = name
                            TerminalLine(text = "Repo created: $name (private). Use 'github ls' to explore.", type = LineType.SUCCESS)
                        }
                    }
                }
                "clone" -> {
                    if (args.isBlank()) {
                        TerminalLine(text = "Usage: github clone <owner/repo>", type = LineType.ERROR)
                    } else {
                        val repoParts = args.split("/")
                        if (repoParts.size != 2) {
                            TerminalLine(text = "Usage: github clone <owner/repo>", type = LineType.ERROR)
                        } else {
                            codespaceOwner = repoParts[0]
                            codespaceRepo = repoParts[1]
                            currentCodespaceDirectory = "/"
                            TerminalLine(text = "Cloned $args. Use 'github ls' to explore.", type = LineType.SUCCESS)
                        }
                    }
                }
                "ls" -> {
                    if (codespaceOwner.isBlank() || codespaceRepo.isBlank()) {
                        TerminalLine(text = "No repo connected. Use 'github clone <owner/repo>'", type = LineType.ERROR)
                    } else {
                        val path = args.ifBlank { currentCodespaceDirectory.removePrefix("/") }
                        val result = GitHubApiService.listDirectory(token, codespaceOwner, codespaceRepo, path)
                        if (result.contains("\"error\"")) {
                            TerminalLine(text = "Failed to list directory", type = LineType.ERROR)
                        } else {
                            val arr = org.json.JSONArray(result)
                            val output = StringBuilder()
                            for (i in 0 until arr.length()) {
                                val obj = arr.getJSONObject(i)
                                val name = obj.getString("name")
                                val type = if (obj.getString("type") == "dir") "/" else ""
                                val size = if (obj.getString("type") != "dir") formatSize(obj.optLong("size", 0)) else ""
                                output.appendLine("$type$name $size")
                            }
                            TerminalLine(text = output.toString().ifBlank { "(empty)" }, type = LineType.OUTPUT)
                        }
                    }
                }
                "cat" -> {
                    if (codespaceOwner.isBlank()) {
                        TerminalLine(text = "No repo connected.", type = LineType.ERROR)
                    } else if (args.isBlank()) {
                        TerminalLine(text = "Usage: github cat <file>", type = LineType.ERROR)
                    } else {
                        val result = GitHubApiService.getFileContent(token, codespaceOwner, codespaceRepo, args)
                        if (result.contains("\"error\"")) {
                            TerminalLine(text = "File not found: $args", type = LineType.ERROR)
                        } else {
                            val json = org.json.JSONObject(result)
                            val content = String(Base64.getDecoder().decode(json.getString("content").replace("\n", "")))
                            TerminalLine(text = content.take(5000), type = LineType.OUTPUT)
                        }
                    }
                }
                "rm" -> {
                    if (codespaceOwner.isBlank()) {
                        TerminalLine(text = "No repo connected.", type = LineType.ERROR)
                    } else if (args.isBlank()) {
                        TerminalLine(text = "Usage: github rm <file>", type = LineType.ERROR)
                    } else {
                        val sha = GitHubApiService.getFileSha(token, codespaceOwner, codespaceRepo, args)
                        if (sha == null) {
                            TerminalLine(text = "File not found: $args", type = LineType.ERROR)
                        } else {
                            GitHubApiService.deleteFile(token, codespaceOwner, codespaceRepo, args, "Deleted $args", sha)
                            TerminalLine(text = "Deleted: $args", type = LineType.SUCCESS)
                        }
                    }
                }
                "help" -> TerminalLine(
                    text = """
GitHub Codespace Commands:
  github create-repo <name>     Create new private repo
  github clone <owner/repo>     Connect to a repo
  github ls [path]              List files in directory
  github cat <file>             View file contents
  github rm <file>              Delete a file
  github help                   Show this help
  nano <file>                   Edit/create a file
                    """.trimIndent(), type = LineType.OUTPUT
                )
                else -> TerminalLine(text = "Unknown github command. Try 'github help'", type = LineType.ERROR)
            }
        } catch (e: Exception) {
            TerminalLine(text = "Error: ${e.message}", type = LineType.ERROR)
        }
    }

    private fun formatSize(bytes: Long): String = when {
        bytes < 1024 -> "$bytes"
        bytes < 1024 * 1024 -> "${"%.1f".format(bytes / 1024.0)}K"
        bytes < 1024 * 1024 * 1024 -> "${"%.1f".format(bytes / (1024.0 * 1024.0))}M"
        else -> "${"%.2f".format(bytes / (1024.0 * 1024.0 * 1024.0))}G"
    }
}

// ==================== MAIN SCREEN ====================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TerminalEmulatorScreen(isDark: Boolean, onMenuClick: () -> Unit) {
    val context = LocalContext.current
    var terminalLines by remember { mutableStateOf(listOf<TerminalLine>()) }
    var currentInput by remember { mutableStateOf("") }
    var showSuggestions by remember { mutableStateOf(false) }
    var showAiPanel by remember { mutableStateOf(false) }
    var aiSuggestions by remember { mutableStateOf(listOf<CommandSuggestion>()) }
    var showAliasDialog by remember { mutableStateOf(false) }
    var newAliasName by remember { mutableStateOf("") }
    var newAliasCommand by remember { mutableStateOf("") }
    var showHelpPanel by remember { mutableStateOf(false) }
    var showFileEditor by remember { mutableStateOf(false) }
    var editingFilePath by remember { mutableStateOf("") }
    var editingFileContent by remember { mutableStateOf("") }
    var editingFileSha by remember { mutableStateOf<String?>(null) }

    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val engine = TerminalEngine

    // Welcome message
    LaunchedEffect(Unit) {
        terminalLines = listOf(
            TerminalLine(text = "╔══════════════════════════════════════════════╗", type = LineType.SYSTEM),
            TerminalLine(text = "║           🖥  DAO TERMINAL EMULATOR          ║", type = LineType.SYSTEM),
            TerminalLine(text = "║           DaoOS 1.0 • ${Build.MODEL.take(25).padEnd(25)}║", type = LineType.SYSTEM),
            TerminalLine(text = "╠══════════════════════════════════════════════╣", type = LineType.SYSTEM),
            TerminalLine(text = "║  Type 'help' for commands                   ║", type = LineType.SYSTEM),
            TerminalLine(text = "║  Type 'suggest' for AI recommendations      ║", type = LineType.SYSTEM),
            TerminalLine(text = "║  Type 'ai' to toggle AI assist              ║", type = LineType.SYSTEM),
            TerminalLine(text = "║  Type 'root' to toggle root mode            ║", type = LineType.SYSTEM),
            TerminalLine(text = "╚══════════════════════════════════════════════╝", type = LineType.SYSTEM),
            TerminalLine(text = "", type = LineType.OUTPUT)
        )
    }

    // Auto-scroll
    LaunchedEffect(terminalLines.size) {
        if (terminalLines.isNotEmpty()) {
            listState.animateScrollToItem(terminalLines.size - 1)
        }
    }

    // AI suggestions update
    LaunchedEffect(currentInput) {
        if (engine.isAiAssistEnabled && currentInput.isNotBlank()) {
            aiSuggestions = engine.getAiSuggestions(currentInput)
            showSuggestions = aiSuggestions.isNotEmpty()
        } else {
            showSuggestions = false
        }
    }

    fun executeInput() {
        if (currentInput.isBlank()) return

        // Resolve alias
        val parts = currentInput.trim().split(" ", limit = 2)
        val resolvedCommand = engine.aliases[parts[0]]?.let { alias ->
            if (parts.size > 1) "$alias ${parts[1]}" else alias
        } ?: currentInput.trim()

        // Add input line
        val inputLine = TerminalLine(
            text = "${if (engine.isRootMode) "🔴" else "🟢"} ${engine.currentDirectory.takeLast(30)} $ ${currentInput.trim()}",
            type = LineType.INPUT
        )
        terminalLines = terminalLines + inputLine
        engine.addToHistory(currentInput.trim())

        // Handle clear screen
        if (resolvedCommand == "clear" || resolvedCommand == "cls") {
            terminalLines = listOf(TerminalLine(text = "", type = LineType.SYSTEM))
            currentInput = ""
            return
        }

        // Execute
        val result = engine.executeCommand(resolvedCommand, context)
        if (result.text == "CLEAR_SCREEN") {
            terminalLines = listOf(TerminalLine(text = "", type = LineType.SYSTEM))
        } else if (result.text.isNotBlank()) {
            terminalLines = terminalLines + result
        }

        // AI follow-up suggestion
        if (engine.isAiAssistEnabled && result.type == LineType.ERROR) {
            terminalLines = terminalLines + TerminalLine(
                text = "💡 Tip: Try 'suggest' for related commands",
                type = LineType.AI_SUGGESTION
            )
        }

        if (result.text.startsWith("EDITOR_OPEN:")) {
            editingFilePath = result.text.removePrefix("EDITOR_OPEN:")
            val token = context.getSharedPreferences("dao_settings", Context.MODE_PRIVATE)
                .getString("github_api_key", "") ?: ""
            if (token.isNotBlank() && engine.codespaceOwner.isNotBlank()) {
                scope.launch(Dispatchers.IO) {
                    val ghResult = GitHubApiService.getFileContent(
                        token, engine.codespaceOwner, engine.codespaceRepo, editingFilePath
                    )
                    withContext(Dispatchers.Main) {
                        try {
                            val json = org.json.JSONObject(ghResult)
                            editingFileContent = String(
                                Base64.getDecoder().decode(json.getString("content").replace("\n", ""))
                            )
                            editingFileSha = json.optString("sha")
                        } catch (e: Exception) {
                            editingFileContent = ""
                            editingFileSha = null
                        }
                        showFileEditor = true
                    }
                }
            } else {
                editingFileContent = ""
                editingFileSha = null
                showFileEditor = true
                terminalLines = terminalLines + TerminalLine(
                    text = "⚠️ Connect a GitHub repo first: github clone <owner/repo>",
                    type = LineType.WARNING
                )
            }
        }

        currentInput = ""
        showSuggestions = false
    }

    // ==================== ALIAS DIALOG ====================
    if (showAliasDialog) {
        AlertDialog(
            onDismissRequest = { showAliasDialog = false },
            title = { Text("Create Alias", color = ZenGold, fontFamily = FontFamily.Monospace) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = newAliasName, onValueChange = { newAliasName = it },
                        label = { Text("Alias (e.g., ll)") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = ZenGold))
                    OutlinedTextField(value = newAliasCommand, onValueChange = { newAliasCommand = it },
                        label = { Text("Command (e.g., ls -la)") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = ZenGold))
                }
            },
            confirmButton = {
                Button(onClick = {
                    if (newAliasName.isNotBlank() && newAliasCommand.isNotBlank()) {
                        engine.aliases[newAliasName.trim()] = newAliasCommand.trim()
                        Toast.makeText(context, "Alias created: ${newAliasName.trim()}", Toast.LENGTH_SHORT).show()
                    }
                    showAliasDialog = false; newAliasName = ""; newAliasCommand = ""
                }, colors = ButtonDefaults.buttonColors(containerColor = ZenGold)) { Text("Save", color = Color.Black) }
            },
            dismissButton = { TextButton(onClick = { showAliasDialog = false }) { Text("Cancel", color = ZenGold) } },
            containerColor = if (isDark) Color(0xFF0F0F12) else Color(0xFFF7F4EE)
        )
    }

    // ==================== MAIN UI ====================
    Column(modifier = Modifier.fillMaxSize().background(if (isDark) Color(0xFF0A0A0E) else Color(0xFF1A1A1A))) {
        // Header
        Surface(color = Color(0xFF0F0F14), shadowElevation = 2.dp) {
            Column(modifier = Modifier.statusBarsPadding()) {
                Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onMenuClick) { Icon(Icons.Default.Menu, null, tint = YinText, modifier = Modifier.size(20.dp)) }
                    Text("🖥 TERMINAL", fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, color = Color(0xFF00FF00), fontSize = 14.sp)
                    if (engine.isRootMode) {
                        Surface(color = ZenRed.copy(alpha = 0.2f), shape = RoundedCornerShape(4.dp)) {
                            Text(" ROOT ", color = ZenRed, fontSize = 9.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp))
                        }
                    }
                    Spacer(Modifier.weight(1f))
                    if (engine.isAiAssistEnabled) {
                        Surface(color = Color(0xFF6E40C9).copy(alpha = 0.2f), shape = RoundedCornerShape(4.dp)) {
                            Text(" AI ON ", color = Color(0xFF6E40C9), fontSize = 9.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp))
                        }
                    }
                    IconButton(onClick = { showAiPanel = !showAiPanel }) { Icon(Icons.Default.AutoAwesome, "AI", tint = if (showAiPanel) Color(0xFF6E40C9) else YinTextSecondary, modifier = Modifier.size(18.dp)) }
                    IconButton(onClick = { showAliasDialog = true }) { Icon(Icons.Default.AddLink, "Alias", tint = YinTextSecondary, modifier = Modifier.size(18.dp)) }
                    IconButton(onClick = {
                        terminalLines = listOf(TerminalLine(text = "", type = LineType.SYSTEM))
                        terminalLines = listOf(
                            TerminalLine(text = "╔══════════════════════════════════════════════╗", type = LineType.SYSTEM),
                            TerminalLine(text = "║           🖥  TERMINAL CLEARED               ║", type = LineType.SYSTEM),
                            TerminalLine(text = "╚══════════════════════════════════════════════╝", type = LineType.SYSTEM),
                            TerminalLine(text = "", type = LineType.OUTPUT)
                        )
                    }) { Icon(Icons.Default.DeleteSweep, "Clear", tint = YinTextSecondary, modifier = Modifier.size(18.dp)) }
                }
            }
        }

        // AI Suggestion panel
        AnimatedVisibility(visible = showAiPanel, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
            Surface(color = Color(0xFF0F0F14)) {
                Column(modifier = Modifier.padding(8.dp)) {
                    Text("🤖 AI COMMAND SUGGESTIONS", color = Color(0xFF6E40C9), fontSize = 10.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                    Spacer(Modifier.height(4.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(commandSuggestionsForCategory()) { suggestion ->
                            Card(modifier = Modifier.clickable(onClick = { currentInput = suggestion.command; showAiPanel = false }),
                                colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A24)), shape = RoundedCornerShape(6.dp)) {
                                Column(modifier = Modifier.padding(8.dp)) {
                                    Text(suggestion.command, color = Color(0xFF8CBE91), fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                                    Text(suggestion.description, color = YinTextSecondary, fontSize = 8.sp, maxLines = 1)
                                }
                            }
                        }
                    }
                }
            }
        }

        // Terminal output
        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 8.dp),
            verticalArrangement = Arrangement.spacedBy(1.dp)
        ) {
            items(terminalLines) { line ->
                when (line.type) {
                    LineType.INPUT -> {
                        Text(
                            text = line.text,
                            color = Color(0xFF00FF00),
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 1.dp)
                        )
                    }
                    LineType.ERROR -> {
                        Text(
                            text = line.text,
                            color = Color(0xFFFF5555),
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 1.dp)
                        )
                    }
                    LineType.SUCCESS -> {
                        Text(
                            text = line.text,
                            color = Color(0xFF55FF55),
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 1.dp)
                        )
                    }
                    LineType.WARNING -> {
                        Text(
                            text = line.text,
                            color = Color(0xFFFFAA00),
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 1.dp)
                        )
                    }
                    LineType.AI_SUGGESTION -> {
                        Text(
                            text = line.text,
                            color = Color(0xFF6E40C9),
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 1.dp)
                        )
                    }
                    LineType.SYSTEM -> {
                        Text(
                            text = line.text,
                            color = Color(0xFF888888),
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 1.dp)
                        )
                    }
                    else -> {
                        Text(
                            text = line.text,
                            color = Color(0xFFCCCCCC),
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 1.dp)
                        )
                    }
                }
            }
        }

        // AI suggestion bar (real-time)
        AnimatedVisibility(visible = showSuggestions && engine.isAiAssistEnabled) {
            Surface(color = Color(0xFF0F0F14)) {
                LazyRow(modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(aiSuggestions.take(4)) { suggestion ->
                        AssistChip(onClick = { currentInput = suggestion.command; showSuggestions = false },
                            label = { Text(suggestion.command, fontSize = 10.sp, fontFamily = FontFamily.Monospace) },
                            colors = AssistChipDefaults.assistChipColors(containerColor = Color(0xFF1A1A24), labelColor = Color(0xFF8CBE91)))
                    }
                }
            }
        }

        // Input bar
        Surface(color = Color(0xFF0F0F14), shadowElevation = 4.dp) {
            Row(modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                // Prompt
                Text(
                    "${if (engine.isRootMode) "🔴" else "🟢"} ${engine.currentDirectory.takeLast(20)} $",
                    color = Color(0xFF00FF00), fontSize = 11.sp, fontFamily = FontFamily.Monospace
                )
                Spacer(Modifier.width(4.dp))
                // Input field
                BasicTextField(
                    value = currentInput,
                    onValueChange = { currentInput = it },
                    modifier = Modifier.weight(1f),
                    textStyle = TextStyle(color = Color(0xFFCCCCCC), fontSize = 11.sp, fontFamily = FontFamily.Monospace),
                    cursorBrush = SolidColor(Color(0xFF00FF00)),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = { executeInput() })
                )
                Spacer(Modifier.width(8.dp))
                // Send button
                IconButton(onClick = { executeInput() }, modifier = Modifier.size(32.dp).clip(CircleShape).background(Color(0xFF00FF00).copy(alpha = 0.15f))) {
                    Icon(Icons.Default.Send, "Execute", tint = Color(0xFF00FF00), modifier = Modifier.size(16.dp))
                }
            }
        }
    }

    // ==================== FILE EDITOR DIALOG ====================
    if (showFileEditor) {
        AlertDialog(
            onDismissRequest = { showFileEditor = false },
            title = {
                Text("📝 Editing: $editingFilePath",
                    color = Color(0xFF00FF00), fontFamily = FontFamily.Monospace, fontSize = 14.sp)
            },
            text = {
                OutlinedTextField(
                    value = editingFileContent,
                    onValueChange = { editingFileContent = it },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 350.dp),
                    textStyle = TextStyle(
                        color = Color(0xFFCCCCCC), fontSize = 12.sp, fontFamily = FontFamily.Monospace
                    ),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFF00FF00),
                        unfocusedBorderColor = Color(0xFF333340),
                        cursorColor = Color(0xFF00FF00)
                    )
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val token = context.getSharedPreferences("dao_settings", Context.MODE_PRIVATE)
                            .getString("github_api_key", "") ?: ""
                        if (token.isNotBlank() && engine.codespaceOwner.isNotBlank()) {
                            scope.launch(Dispatchers.IO) {
                                GitHubApiService.createOrUpdateFile(
                                    token, engine.codespaceOwner, engine.codespaceRepo,
                                    editingFilePath, editingFileContent,
                                    "Updated $editingFilePath from Dao Terminal",
                                    "main", editingFileSha
                                )
                                withContext(Dispatchers.Main) {
                                    terminalLines = terminalLines + TerminalLine(
                                        text = "✅ Saved & committed: $editingFilePath",
                                        type = LineType.SUCCESS
                                    )
                                }
                            }
                        } else {
                            terminalLines = terminalLines + TerminalLine(
                                text = "⚠️ Cannot save: No GitHub repo connected",
                                type = LineType.ERROR
                            )
                        }
                        showFileEditor = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00FF00))
                ) {
                    Text("Save & Commit", color = Color.Black, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showFileEditor = false }) {
                    Text("Cancel", color = Color(0xFF00FF00))
                }
            },
            containerColor = Color(0xFF0F0F12)
        )
    }
}

private fun commandSuggestionsForCategory(category: String = "all"): List<CommandSuggestion> {
    val all = TerminalEngine.commandSuggestions
    return if (category == "all") all.shuffled().take(8) else all.filter { it.category == category }.take(8)
}

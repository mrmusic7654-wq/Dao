package com.example.ui.automation

import android.content.Context
import com.example.Screen
import kotlinx.coroutines.*
import org.json.JSONObject

object AutomationEngine {

    var mainActivity: android.app.Activity? = null

    fun getSystemPrompt(userRequest: String): String {
        return """
You are Dao Agent, an autonomous AI that controls an Android device.

YOU HAVE FULL CONTROL OVER THE DEVICE. You can:
- Open any screen in the Dao app
- Tap buttons by their visible text
- Type text into input fields
- Scroll screens up and down
- Read text visible on screen
- Wait for elements to appear
- Take screenshots to see what's happening
- Navigate back

OUTPUT FORMAT — You MUST use EXACTLY this format for every action:
[ACTION:action_name]{"param1":"value1","param2":"value2"}[/ACTION]

AVAILABLE ACTIONS:

Screen Control:
- open_screen: screen — Open any screen (Browser, FileManager, VideoEditor, CodeEditor, GitHub, Telegram, NotesManager, Terminal, ImageEditor, CloudStorageHub, PasswordVault, DocumentScanner, ScreenRecorder, AutomationDashboard, DaoChat)
- browser_go: url — Navigate browser to URL
- go_back: (none) — Go back to previous screen

UI Interaction:
- tap: text — Tap a button or element by its visible text
- long_press: text — Long press an element
- type: text — Type text into the currently focused field
- scroll_down: (none) — Scroll down
- scroll_up: (none) — Scroll up
- swipe_left: (none) — Swipe left
- swipe_right: (none) — Swipe right

Reading & Analysis:
- read_screen: (none) — Read all visible text on the current screen
- screenshot: (none) — Take a screenshot to see what's on screen
- wait_for_text: text, timeout — Wait for specific text to appear (timeout in ms)

External Tools:
- web_search: query — Search the web
- github_create_repo: name — Create a GitHub repository
- telegram_send: chat_id, text — Send a Telegram message
- notes_create: title, content — Create a note
- terminal_run: command — Run a terminal command
- file_read: path — Read a file
- file_list: path — List files in a directory

RULES:
1. Think step by step — plan before acting
2. Execute ONE action at a time
3. After each action, you'll get the result — use it to decide next step
4. If an action fails, try an alternative approach
5. Use screenshot or read_screen when you're unsure of the current state
6. Use wait_for_text before tapping to ensure the element exists
7. When the task is complete, respond with a clear summary

USER REQUEST: $userRequest

What is your first action?
        """.trimIndent()
    }

    suspend fun executeAction(
        action: String,
        params: Map<String, String>,
        context: Context
    ): String = withContext(Dispatchers.IO) {
        when (action) {
            // ==================== SCREEN CONTROL ====================
            "open_screen" -> {
                val screenName = params["screen"] ?: return@withContext "Missing screen name"
                withContext(Dispatchers.Main) {
                    val screen = when (screenName.lowercase()) {
                        "browser" -> Screen.Browser
                        "filemanager" -> Screen.FileManager
                        "videoeditor" -> Screen.VideoEditor
                        "codeeditor" -> Screen.CodeEditor
                        "github" -> Screen.GitHub
                        "telegram" -> Screen.Telegram
                        "notesmanager" -> Screen.NotesManager
                        "terminal" -> Screen.TerminalEmulator
                        "imageeditor" -> Screen.ImageEditor
                        "cloudstoragehub" -> Screen.CloudStorageHub
                        "passwordvault" -> Screen.PasswordVault
                        "documentscanner" -> Screen.DocumentScanner
                        "screenrecorder" -> Screen.ScreenRecorder
                        "automationdashboard" -> Screen.AutomationDashboard
                        "daochat" -> Screen.DaoChat
                        else -> null
                    }
                    screen?.let { ScreenNavigator.navigateTo(it) }
                }
                delay(800)
                "Opened screen: $screenName"
            }

            "browser_go" -> {
                val url = params["url"] ?: return@withContext "Missing URL"
                withContext(Dispatchers.Main) {
                    ScreenNavigator.navigateTo(Screen.Browser)
                }
                delay(1000)
                PendingActions.queue.add(PendingAction("Browser", "load_url", mapOf("url" to url)))
                "Browser navigating to: $url"
            }

            "go_back" -> {
                withContext(Dispatchers.Main) {
                    DaoAccessibilityService.instance?.pressBack()
                }
                delay(500)
                "Pressed back"
            }

            // ==================== UI INTERACTION ====================
            "tap" -> {
                val text = params["text"] ?: return@withContext "Missing text to tap"
                val result = withContext(Dispatchers.Main) {
                    val service = DaoAccessibilityService.instance
                    if (service == null) {
                        "Accessibility Service not enabled. Please enable Dao in Settings > Accessibility."
                    } else {
                        val success = service.findAndTap(text)
                        if (success) "Tapped: $text"
                        else "Could not find element: '$text'. Use read_screen to see what's visible."
                    }
                }
                delay(500)
                result
            }

            "long_press" -> {
                val text = params["text"] ?: return@withContext "Missing text to long press"
                val result = withContext(Dispatchers.Main) {
                    val service = DaoAccessibilityService.instance
                    if (service == null) "Accessibility Service not enabled."
                    else {
                        val success = service.findAndLongPress(text)
                        if (success) "Long pressed: $text" else "Could not find: $text"
                    }
                }
                delay(500)
                result
            }

            "type" -> {
                val text = params["text"] ?: return@withContext "Missing text to type"
                val result = withContext(Dispatchers.Main) {
                    val service = DaoAccessibilityService.instance
                    if (service == null) "Accessibility Service not enabled."
                    else {
                        val success = service.typeText(text)
                        if (success) "Typed: $text" else "Could not type — no focused input field"
                    }
                }
                delay(300)
                result
            }

            "scroll_down" -> {
                val result = withContext(Dispatchers.Main) {
                    DaoAccessibilityService.instance?.scrollForward()
                }
                delay(500)
                if (result == true) "Scrolled down" else "Could not scroll"
            }

            "scroll_up" -> {
                val result = withContext(Dispatchers.Main) {
                    DaoAccessibilityService.instance?.scrollBackward()
                }
                delay(500)
                if (result == true) "Scrolled up" else "Could not scroll"
            }

            "swipe_left" -> {
                withContext(Dispatchers.Main) {
                    DaoAccessibilityService.instance?.swipeLeft()
                }
                delay(500)
                "Swiped left"
            }

            "swipe_right" -> {
                withContext(Dispatchers.Main) {
                    DaoAccessibilityService.instance?.swipeRight()
                }
                delay(500)
                "Swiped right"
            }

            // ==================== READING & ANALYSIS ====================
            "read_screen" -> {
                val text = withContext(Dispatchers.Main) {
                    DaoAccessibilityService.instance?.getScreenText() ?: "Accessibility Service not enabled"
                }
                if (text.length > 3000) text.take(3000) + "...(truncated)" else text
            }

            "screenshot" -> {
                val activity = mainActivity
                if (activity == null) "Cannot capture — no activity reference"
                else {
                    val bitmap = withContext(Dispatchers.Main) {
                        try {
                            val view = activity.window?.decorView?.rootView
                            val bmp = android.graphics.Bitmap.createBitmap(view!!.width, view.height, android.graphics.Bitmap.Config.ARGB_8888)
                            val canvas = android.graphics.Canvas(bmp)
                            view.draw(canvas)
                            bmp
                        } catch (e: Exception) { null }
                    }
                    if (bitmap != null) {
                        val baos = java.io.ByteArrayOutputStream()
                        bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 70, baos)
                        val base64 = android.util.Base64.encodeToString(baos.toByteArray(), android.util.Base64.NO_WRAP)
                        "[SCREENSHOT:data:image/jpeg;base64,$base64]"
                    } else "Screenshot failed"
                }
            }

            "wait_for_text" -> {
                val text = params["text"] ?: return@withContext "Missing text"
                val timeout = params["timeout"]?.toLongOrNull() ?: 10000L
                val start = System.currentTimeMillis()
                while (System.currentTimeMillis() - start < timeout) {
                    val screenText = DaoAccessibilityService.instance?.getScreenText() ?: ""
                    if (screenText.contains(text, ignoreCase = true)) {
                        return@withContext "Found: '$text' on screen"
                    }
                    delay(500)
                }
                "Timed out waiting for: '$text'"
            }

            // ==================== EXTERNAL TOOLS ====================
            "web_search" -> {
                val query = params["query"] ?: return@withContext "Missing query"
                try {
                    val url = java.net.URL("https://api.duckduckgo.com/?q=${java.net.URLEncoder.encode(query, "UTF-8")}&format=json&no_html=1")
                    val result = url.readText()
                    val json = JSONObject(result)
                    val abstract = json.optString("Abstract", "")
                    if (abstract.isNotBlank()) "Search results: $abstract"
                    else {
                        val topics = json.optJSONArray("RelatedTopics")
                        if (topics != null && topics.length() > 0) {
                            topics.getJSONObject(0).optString("Text", "No results")
                        } else "No results found"
                    }
                } catch (e: Exception) { "Search failed: ${e.message}" }
            }

            "github_create_repo" -> {
                val name = params["name"] ?: return@withContext "Missing repo name"
                val prefs = context.getSharedPreferences("dao_settings", Context.MODE_PRIVATE)
                val token = prefs.getString("github_api_key", "") ?: ""
                if (token.isBlank()) return@withContext "GitHub token not set"
                try {
                    com.example.ui.screens.GitHubApiService.createRepo(token, name, "", true)
                    "GitHub repo created: $name"
                } catch (e: Exception) { "GitHub failed: ${e.message}" }
            }

            "telegram_send" -> {
                val chatId = params["chat_id"]?.toLongOrNull() ?: return@withContext "Invalid chat ID"
                val text = params["text"] ?: return@withContext "Missing text"
                val prefs = context.getSharedPreferences("dao_settings", Context.MODE_PRIVATE)
                val token = prefs.getString("telegram_api_key", "") ?: ""
                if (token.isBlank()) return@withContext "Telegram token not set"
                try {
                    com.example.ui.screens.TelegramApiService.sendMessage(token, chatId, text)
                    "Telegram message sent"
                } catch (e: Exception) { "Telegram failed: ${e.message}" }
            }

            "notes_create" -> {
                val title = params["title"] ?: "Untitled"
                val content = params["content"] ?: ""
                com.example.ui.screens.NotesEngine.createNote(title = title, content = content)
                "Note created: $title"
            }

            "terminal_run" -> {
                val command = params["command"] ?: return@withContext "Missing command"
                try {
                    val process = Runtime.getRuntime().exec(arrayOf("sh", "-c", command))
                    val output = process.inputStream.bufferedReader().readText()
                    val error = process.errorStream.bufferedReader().readText()
                    process.waitFor()
                    if (error.isNotBlank()) "Error: $error" else output.ifBlank { "Command executed (no output)" }
                } catch (e: Exception) { "Terminal error: ${e.message}" }
            }

            "file_read" -> {
                val path = params["path"] ?: return@withContext "Missing path"
                try {
                    java.io.File(path).readText().take(5000)
                } catch (e: Exception) { "File read error: ${e.message}" }
            }

            "file_list" -> {
                val path = params["path"] ?: return@withContext "Missing path"
                try {
                    val dir = java.io.File(path)
                    if (!dir.exists()) "Directory not found: $path"
                    else dir.listFiles()?.joinToString("\n") { "${if (it.isDirectory) "📁" else "📄"} ${it.name}" } ?: "Empty"
                } catch (e: Exception) { "File list error: ${e.message}" }
            }

            else -> "Unknown action: $action. Available: open_screen, browser_go, tap, type, scroll_down, scroll_up, read_screen, screenshot, wait_for_text, web_search, github_create_repo, telegram_send, notes_create, terminal_run, file_read, file_list, go_back"
        }
    }

    fun parseActions(responseText: String): List<Pair<String, Map<String, String>>> {
        val regex = Regex("""\[ACTION:\s*(\w+)\]\s*(\{[^}]*\})\s*\[/ACTION\]""", RegexOption.DOT_MATCHES_ALL)
        return regex.findAll(responseText).map { match ->
            val action = match.groupValues[1].trim()
            val paramsJson = match.groupValues[2].trim()
            val params = try {
                val json = JSONObject(paramsJson)
                json.keys().asSequence().associate { it to json.optString(it, "") }
            } catch (e: Exception) { emptyMap() }
            action to params
        }.toList()
    }
}

object ScreenNavigator {
    var onNavigate: ((Screen) -> Unit)? = null
    fun navigateTo(screen: Screen) { onNavigate?.invoke(screen) }
}

data class PendingAction(
    val targetScreen: String,
    val action: String,
    val parameters: Map<String, String> = emptyMap()
)

object PendingActions {
    val queue = java.util.concurrent.ConcurrentLinkedQueue<PendingAction>()
}

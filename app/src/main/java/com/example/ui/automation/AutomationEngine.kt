package com.example.ui.automation

import android.content.Context
import android.graphics.Bitmap
import android.util.Base64
import com.example.data.repository.UserPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.ByteArrayOutputStream

object AutomationEngine {

    /**
     * Executes an action parsed from AI response.
     * Returns a result string to feed back into the conversation.
     */
    suspend fun executeAction(
        action: String,
        parameters: Map<String, String>,
        context: Context
    ): String = withContext(Dispatchers.IO) {
        when (action) {
            "web_search" -> {
                val query = parameters["query"] ?: return@withContext "Missing query"
                // Use a simple DuckDuckGo Instant Answer API (no key needed)
                try {
                    val url = java.net.URL("https://api.duckduckgo.com/?q=${java.net.URLEncoder.encode(query, "UTF-8")}&format=json&no_html=1")
                    val result = url.readText()
                    val json = JSONObject(result)
                    val abstract = json.optString("Abstract", "")
                    val answer = abstract.ifBlank {
                        // Fallback to related topics
                        val topics = json.optJSONArray("RelatedTopics")
                        if (topics != null && topics.length() > 0) {
                            topics.getJSONObject(0).optString("Text", "No results")
                        } else "No results"
                    }
                    "Web search results for '$query':\n$answer"
                } catch (e: Exception) {
                    "Web search failed: ${e.message}"
                }
            }
            "github_create_repo" -> {
                val token = UserPreferences(context).githubApiKey
                if (token.isBlank()) return@withContext "GitHub token not set"
                val repoName = parameters["name"] ?: return@withContext "Missing repo name"
                val result = com.example.ui.screens.GitHubApiService.createRepo(token, repoName, "", false)
                "GitHub result: $result"
            }
            "telegram_send" -> {
                val token = UserPreferences(context).telegramApiKey
                if (token.isBlank()) return@withContext "Telegram token not set"
                val chatId = parameters["chat_id"]?.toLongOrNull() ?: return@withContext "Invalid chat ID"
                val text = parameters["text"] ?: ""
                val result = com.example.ui.screens.TelegramApiService.sendMessage(token, chatId, text)
                "Telegram result: $result"
            }
            "file_read" -> {
                val path = parameters["path"] ?: return@withContext "Missing file path"
                try {
                    java.io.File(path).readText().take(2000)
                } catch (e: Exception) {
                    "File read error: ${e.message}"
                }
            }
            "screen_capture" -> {
                val activity = context as? android.app.Activity
                val bitmap = captureScreen(activity)
                if (bitmap != null) {
                    val baos = ByteArrayOutputStream()
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 70, baos)
                    val base64 = Base64.getEncoder().encodeToString(baos.toByteArray())
                    "[IMAGE:data:image/jpeg;base64,$base64]"
                } else "Screen capture failed"
            }
            // Add more actions as needed
            else -> "Unknown action: $action"
        }
    }

    /**
     * Captures a screenshot of the current screen.
     */
    private suspend fun captureScreen(activity: android.app.Activity?): Bitmap? = withContext(Dispatchers.Main) {
        activity?.window?.decorView?.rootView?.let { view ->
            val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            val canvas = android.graphics.Canvas(bitmap)
            view.draw(canvas)
            bitmap
        }
    }

    /**
     * Parses the AI's response to extract actions.
     * Expected format: [ACTION:action_name]{"key":"value"}[/ACTION]
     */
    fun parseActions(responseText: String): List<Pair<String, Map<String, String>>> {
        val regex = Regex("""\[ACTION:\s*(\w+)\](.*?)\[/ACTION\]""", RegexOption.DOT_MATCHES_ALL)
        return regex.findAll(responseText).map { match ->
            val action = match.groupValues[1].trim()
            val paramsJson = match.groupValues[2].trim()
            val params = try {
                val json = JSONObject(paramsJson)
                json.keys().asSequence().associate { it to json.getString(it) }
            } catch (e: Exception) { emptyMap() }
            action to params
        }.toList()
    }

    /**
     * Generates a system prompt that instructs the AI to use the tool format.
     */
    fun getAutomationSystemPrompt(availableTools: List<String>): String {
        return """
You are Dao, an automation AI. You have access to the following tools:
${availableTools.joinToString("\n") { "- $it" }}

When you need to perform an action, output exactly:
[ACTION:tool_name]{"param1":"value1","param2":"value2"}[/ACTION]

After executing an action, I will give you the result. Continue until the task is complete, then respond naturally.

Available tools:
- web_search: query (string)
- github_create_repo: name (string)
- telegram_send: chat_id (number), text (string)
- file_read: path (string)
- screen_capture: (none) — Capture a screenshot of the current screen for analysis

Do not output anything else besides the action block when performing an action. When the task is finished, respond with a summary.
        """.trimIndent()
    }
}

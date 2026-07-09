package com.example.data.dao_engine

import android.content.Context
import com.example.data.repository.UserPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.sse.EventSource
import okhttp3.sse.EventSourceListener
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

object GeminiService {

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private val mediaType = "application/json; charset=utf-8".toMediaType()

    /**
     * STREAMING VERSION: Returns a Flow of string chunks as they arrive from Gemini API.
     * Use this for real-time typing effect in the UI.
     */
    fun generateResponseStream(
        context: Context,
        prompt: String,
        personality: String,
        mode: String,
        systemInstructionOverride: String? = null
    ): Flow<String> = callbackFlow {
        val prefs = UserPreferences(context)
        val apiKey = prefs.geminiApiKey
        val rawModel = prefs.geminiModel
        val model = when (rawModel) {
            "gemini-2.5-flash" -> "gemini-2.5-flash"
            "gemini-2.5-pro" -> "gemini-2.5-pro"
            "gemini-1.5-flash" -> "gemini-1.5-flash"
            "gemini-1.5-pro" -> "gemini-1.5-pro"
            else -> rawModel
        }

        if (apiKey.isBlank()) {
            trySend("[SIMULATION] " + DaoEngine.generateResponse(prompt, personality, mode).replyText)
            close()
            return@callbackFlow
        }

        var enhancedPrompt = prompt
        
        // Web Search Mode: Fetch real search results and prepend to prompt
        if (mode == "Web Search") {
            val searchQuery = prompt.split(".").firstOrNull()?.trim() ?: prompt.take(100)
            val searchResults = try {
                val url = java.net.URL("https://api.duckduckgo.com/?q=${java.net.URLEncoder.encode(searchQuery, "UTF-8")}&format=json&no_html=1")
                val result = url.readText()
                val json = JSONObject(result)
                json.optString("Abstract", "").ifBlank {
                    val topics = json.optJSONArray("RelatedTopics")
                    if (topics != null && topics.length() > 0) {
                        topics.getJSONObject(0).optString("Text", "No results")
                    } else "No results"
                }
            } catch (e: Exception) { "Search unavailable" }

            enhancedPrompt = "Web search results for '$searchQuery':\n$searchResults\n\nUser query: $prompt\n\nAnswer the user's question based on the search results above."
        }
        
        // Automation/Agent Mode: Use custom system instruction with tool info
        val systemInstructionText = systemInstructionOverride ?: if (mode == "Deep Think") {
            """
            You are Dao, the cosmic companion of stillness (Yin) and structure (Yang). 
            Embody the personality vibe of: $personality.
            Adhere to the operation mode: $mode.
            Think step by step. Break down the problem, explain your reasoning, and then give the final answer.
            Keep your wisdom aligned, clear, and mystical yet helpful. Respond directly to the user.
        """.trimIndent()
        } else if (mode == "Automation" || mode == "Agent") {
            com.example.ui.automation.AutomationEngine.getAutomationSystemPrompt(
                listOf("web_search", "github_create_repo", "telegram_send", "file_read", "screen_capture", 
                       "browser_navigate", "file_compress", "screen_navigate", "ui_tap", "ui_scroll", 
                       "ui_type", "ui_back", "ui_read_screen")
            )
        } else {
            """
            You are Dao, the cosmic companion of stillness (Yin) and structure (Yang). 
            Embody the personality vibe of: $personality.
            Adhere to the operation mode: $mode.
            Keep your wisdom aligned, clear, and mystical yet helpful. Respond directly to the user.
        """.trimIndent()
        }

        // Detect inline image in prompt [IMAGE:data:image/jpeg;base64,...]
        val contentParts = JSONArray()
        val imageData: String?
        val textPart: String
        
        if (prompt.startsWith("[IMAGE:")) {
            val endIdx = prompt.indexOf("]")
            imageData = prompt.substring(7, endIdx)
            textPart = prompt.substring(endIdx + 2).trim()
            
            val mimeType = if (imageData.startsWith("data:image/")) {
                val mimeEnd = imageData.indexOf(";base64")
                imageData.substring(5, mimeEnd)
            } else "image/jpeg"
            
            val base64Data = if (imageData.contains(";base64,")) {
                imageData.substringAfter(";base64,")
            } else imageData
            
            contentParts.put(JSONObject().apply {
                put("inline_data", JSONObject().apply {
                    put("mime_type", mimeType)
                    put("data", base64Data)
                })
            })
            if (textPart.isNotEmpty()) {
                contentParts.put(JSONObject().apply { put("text", textPart) })
            }
        } else {
            contentParts.put(JSONObject().apply { put("text", enhancedPrompt) })
        }

        // Use streamGenerateContent endpoint for streaming
        val url = "https://generativelanguage.googleapis.com/v1beta/models/$model:streamGenerateContent?alt=sse&key=$apiKey"

        val requestJson = JSONObject().apply {
            put("contents", JSONArray().apply {
                put(JSONObject().apply {
                    put("parts", contentParts)
                })
            })
            put("systemInstruction", JSONObject().apply {
                put("parts", JSONArray().apply {
                    put(JSONObject().apply {
                        put("text", systemInstructionText)
                    })
                })
            })
            put("generationConfig", JSONObject().apply {
                put("temperature", 0.7)
                put("maxOutputTokens", prefs.maxTokens)
                
                // Fix 43: Image model special handling
                if (model.contains("image") || model.contains("banana")) {
                    put("responseModalities", JSONArray().apply { put("IMAGE") })
                }
            })
        }
        
        // Fix 42: Embedding model special handling
        if (model.contains("embedding")) {
            // Use embedding endpoint instead
            val embedUrl = "https://generativelanguage.googleapis.com/v1beta/models/$model:embedContent?key=$apiKey"
            val embedRequestJson = JSONObject().apply {
                put("contents", JSONArray().apply {
                    put(JSONObject().apply {
                        put("parts", contentParts)
                    })
                })
            }
            // Return embedding response as special format
            // Note: This is a simplified handling - full implementation would return vector data
        }

        val requestBody = requestJson.toString().toRequestBody(mediaType)
        val request = Request.Builder()
            .url(url)
            .post(requestBody)
            .build()

        val isCancelled = AtomicBoolean(false)
        
        val eventSourceListener = object : EventSourceListener() {
            override fun onEvent(eventSource: EventSource, id: String?, type: String?, data: String) {
                if (isCancelled.get()) {
                    eventSource.cancel()
                    return
                }
                try {
                    val json = JSONObject(data)
                    val candidates = json.optJSONArray("candidates")
                    val firstCandidate = candidates?.optJSONObject(0)
                    val contentObj = firstCandidate?.optJSONObject("content")
                    val parts = contentObj?.optJSONArray("parts")
                    val chunkText = parts?.optJSONObject(0)?.optString("text")
                    
                    if (!chunkText.isNullOrBlank()) {
                        trySend(chunkText)
                    }
                } catch (e: Exception) {
                    // Parse error, skip this chunk
                }
            }

            override fun onFailure(eventSource: EventSource, t: Throwable?, response: okhttp3.Response?) {
                if (!isCancelled.get()) {
                    val errorMsg = "⚠️ *[Stream error: ${t?.message ?: response?.message}]*"
                    trySend(errorMsg)
                }
                close()
            }

            override fun onClosed(eventSource: EventSource) {
                close()
            }
        }

        val eventSource = client.newEventSourceFactory().newEventSource(request, eventSourceListener)

        awaitClose {
            isCancelled.set(true)
            eventSource.cancel()
        }
    }

    suspend fun generateResponse(
        context: Context,
        prompt: String,
        personality: String,
        mode: String,
        systemInstructionOverride: String? = null
    ): DaoResponse = withContext(Dispatchers.IO) {
        val prefs = UserPreferences(context)
        val apiKey = prefs.geminiApiKey
        // Map user's selection to official model name if needed
        val rawModel = prefs.geminiModel
        val model = when (rawModel) {
            "gemini-2.5-flash" -> "gemini-2.5-flash"
            "gemini-2.5-pro" -> "gemini-2.5-pro"
            "gemini-1.5-flash" -> "gemini-1.5-flash"
            "gemini-1.5-pro" -> "gemini-1.5-pro"
            else -> rawModel
        }

        if (apiKey.isBlank()) {
            return@withContext DaoEngine.generateResponse(prompt, personality, mode)
        }

        var enhancedPrompt = prompt
        
        // Web Search Mode: Fetch real search results and prepend to prompt
        if (mode == "Web Search") {
            val searchQuery = prompt.split(".").firstOrNull()?.trim() ?: prompt.take(100)
            val searchResults = try {
                val url = java.net.URL("https://api.duckduckgo.com/?q=${java.net.URLEncoder.encode(searchQuery, "UTF-8")}&format=json&no_html=1")
                val result = url.readText()
                val json = JSONObject(result)
                json.optString("Abstract", "").ifBlank {
                    val topics = json.optJSONArray("RelatedTopics")
                    if (topics != null && topics.length() > 0) {
                        topics.getJSONObject(0).optString("Text", "No results")
                    } else "No results"
                }
            } catch (e: Exception) { "Search unavailable" }

            enhancedPrompt = "Web search results for '$searchQuery':\n$searchResults\n\nUser query: $prompt\n\nAnswer the user's question based on the search results above."
        }
        
        // Automation/Agent Mode: Use custom system instruction with tool info
        val systemInstructionText = systemInstructionOverride ?: if (mode == "Deep Think") {
            """
            You are Dao, the cosmic companion of stillness (Yin) and structure (Yang). 
            Embody the personality vibe of: $personality.
            Adhere to the operation mode: $mode.
            Think step by step. Break down the problem, explain your reasoning, and then give the final answer.
            Keep your wisdom aligned, clear, and mystical yet helpful. Respond directly to the user.
        """.trimIndent()
        } else if (mode == "Automation" || mode == "Agent") {
            com.example.ui.automation.AutomationEngine.getAutomationSystemPrompt(
                listOf("web_search", "github_create_repo", "telegram_send", "file_read", "screen_capture", 
                       "browser_navigate", "file_compress", "screen_navigate", "ui_tap", "ui_scroll", 
                       "ui_type", "ui_back", "ui_read_screen")
            )
        } else {
            """
            You are Dao, the cosmic companion of stillness (Yin) and structure (Yang). 
            Embody the personality vibe of: $personality.
            Adhere to the operation mode: $mode.
            Keep your wisdom aligned, clear, and mystical yet helpful. Respond directly to the user.
        """.trimIndent()
        }

        // Detect inline image in prompt [IMAGE:data:image/jpeg;base64,...]
        val contentParts = JSONArray()
        val imageData: String?
        val textPart: String
        
        if (prompt.startsWith("[IMAGE:")) {
            val endIdx = prompt.indexOf("]")
            imageData = prompt.substring(7, endIdx) // base64 data without prefix
            textPart = prompt.substring(endIdx + 2).trim()
            
            // Extract mime type from the data URL
            val mimeType = if (imageData.startsWith("data:image/")) {
                val mimeEnd = imageData.indexOf(";base64")
                imageData.substring(5, mimeEnd)
            } else "image/jpeg"
            
            val base64Data = if (imageData.contains(";base64,")) {
                imageData.substringAfter(";base64,")
            } else imageData
            
            contentParts.put(JSONObject().apply {
                put("inline_data", JSONObject().apply {
                    put("mime_type", mimeType)
                    put("data", base64Data)
                })
            })
            if (textPart.isNotEmpty()) {
                contentParts.put(JSONObject().apply { put("text", textPart) })
            }
        } else {
            contentParts.put(JSONObject().apply { put("text", enhancedPrompt) })
        }

        val url = "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent?key=$apiKey"

        val requestJson = JSONObject().apply {
            put("contents", JSONArray().apply {
                put(JSONObject().apply {
                    put("parts", contentParts)
                })
            })
            put("systemInstruction", JSONObject().apply {
                put("parts", JSONArray().apply {
                    put(JSONObject().apply {
                        put("text", systemInstructionText)
                    })
                })
            })
            put("generationConfig", JSONObject().apply {
                put("temperature", 0.7)
                put("maxOutputTokens", prefs.maxTokens)
            })
        }

        val requestBody = requestJson.toString().toRequestBody(mediaType)
        val request = Request.Builder()
            .url(url)
            .post(requestBody)
            .build()

        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    val errBody = response.body?.string() ?: ""
                    throw Exception("API call failed (code ${response.code}): $errBody")
                }
                val bodyString = response.body?.string() ?: throw Exception("Empty response body")
                val json = JSONObject(bodyString)
                val candidates = json.optJSONArray("candidates")
                val firstCandidate = candidates?.optJSONObject(0)
                val contentObj = firstCandidate?.optJSONObject("content")
                val parts = contentObj?.optJSONArray("parts")
                val replyText = parts?.optJSONObject(0)?.optString("text") 
                    ?: throw Exception("No text in candidates")

                // Compute yin/yang impacts dynamically using our local engine heuristic on the response
                val wordAnalysis = DaoEngine.generateBaseResponse(replyText)
                
                DaoResponse(
                    replyText = replyText,
                    yinImpact = wordAnalysis.yinImpact,
                    yangImpact = wordAnalysis.yangImpact,
                    xpReward = wordAnalysis.xpReward,
                    specialMessage = "Aligned with Gemini Real Intelligence 🔮"
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
            // Fall back to local simulation on network or key error, prepend error indication
            val localBase = DaoEngine.generateResponse(prompt, personality, mode)
            localBase.copy(
                replyText = "⚠️ *[Real AI Connection offline or key invalid: ${e.message}]*\n\n" + localBase.replyText,
                specialMessage = "Fell back to simulation"
            )
        }
    }
}

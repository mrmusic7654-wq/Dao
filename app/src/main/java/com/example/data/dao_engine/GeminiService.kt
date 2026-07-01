package com.example.data.dao_engine

import android.content.Context
import com.example.data.repository.UserPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

object GeminiService {

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private val mediaType = "application/json; charset=utf-8".toMediaType()

    suspend fun generateResponse(
        context: Context,
        prompt: String,
        personality: String,
        mode: String
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

        val systemInstructionText = """
            You are Dao, the cosmic companion of stillness (Yin) and structure (Yang). 
            Embody the personality vibe of: $personality.
            Adhere to the operation mode: $mode.
            Keep your wisdom aligned, clear, and mystical yet helpful. Respond directly to the user.
        """.trimIndent()

        val url = "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent?key=$apiKey"

        val requestJson = JSONObject().apply {
            put("contents", JSONArray().apply {
                put(JSONObject().apply {
                    put("parts", JSONArray().apply {
                        put(JSONObject().apply {
                            put("text", prompt)
                        })
                    })
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

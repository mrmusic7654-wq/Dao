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

object OpenAIService {

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
        val apiKey = prefs.openAiApiKey
        
        if (apiKey.isBlank()) {
            return@withContext GeminiService.generateResponse(context, prompt, personality, mode)
        }

        var enhancedPrompt = prompt
        
        // Deep Think Mode: Add chain-of-thought instruction
        val systemContent = if (mode == "Deep Think") {
            "You are Dao, a wise assistant. Personality: $personality. Mode: $mode. Think step by step. Break down the problem, explain your reasoning, and then give the final answer."
        } else {
            "You are Dao, a wise assistant. Personality: $personality. Mode: $mode."
        }

        val json = JSONObject().apply {
            put("model", "gpt-4o-mini")
            put("messages", JSONArray().apply {
                put(JSONObject().apply {
                    put("role", "system")
                    put("content", systemContent)
                })
                put(JSONObject().apply {
                    put("role", "user")
                    put("content", enhancedPrompt)
                })
            })
            put("max_tokens", prefs.maxTokens.coerceAtMost(16384))
        }

        val requestBody = json.toString().toRequestBody(mediaType)
        val request = Request.Builder()
            .url("https://api.openai.com/v1/chat/completions")
            .header("Authorization", "Bearer $apiKey")
            .post(requestBody)
            .build()

        try {
            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                throw Exception("OpenAI API call failed: ${response.code}")
            }
            val body = response.body?.string() ?: throw Exception("Empty body")
            val replyJson = JSONObject(body)
            val choices = replyJson.getJSONArray("choices")
            val replyText = choices.getJSONObject(0).getJSONObject("message").getString("content")
            
            DaoResponse(
                replyText = replyText,
                yinImpact = 0.5f,
                yangImpact = 0.5f,
                xpReward = 15,
                specialMessage = "OpenAI Response"
            )
        } catch (e: Exception) {
            e.printStackTrace()
            GeminiService.generateResponse(context, prompt, personality, mode)
        }
    }
}

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

object HuggingFaceService {

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
        mode: String,
        systemInstructionOverride: String? = null
    ): DaoResponse = withContext(Dispatchers.IO) {
        val prefs = UserPreferences(context)
        val apiKey = prefs.huggingFaceApiKey
        
        if (apiKey.isBlank()) {
            return@withContext GeminiService.generateResponse(context, prompt, personality, mode, systemInstructionOverride)
        }

        var enhancedPrompt = prompt
        
        // Deep Think Mode: Add chain-of-thought instruction
        val fullPrompt = systemInstructionOverride ?: if (mode == "Deep Think") {
            "Think step by step. Break down the problem, explain your reasoning, and then give the final answer.\n\nUser: $prompt"
        } else {
            "You are Dao, a wise assistant with personality: $personality. Mode: $mode.\n\nUser: $prompt"
        }

        val json = JSONObject().apply {
            put("inputs", fullPrompt)
            put("parameters", JSONObject().apply {
                put("max_new_tokens", prefs.maxTokens.coerceAtMost(4096))
                put("temperature", 0.7)
                put("return_full_text", false)
            })
        }

        val requestBody = json.toString().toRequestBody(mediaType)
        val request = Request.Builder()
            .url("https://api-inference.huggingface.co/models/mistralai/Mistral-7B-Instruct-v0.2")
            .header("Authorization", "Bearer $apiKey")
            .post(requestBody)
            .build()

        try {
            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                throw Exception("HuggingFace API call failed: ${response.code}")
            }
            val body = response.body?.string() ?: throw Exception("Empty body")
            val jsonArray = JSONArray(body)
            val replyText = jsonArray.getJSONObject(0).getString("generated_text").trim()
            
            DaoResponse(
                replyText = replyText,
                yinImpact = 0.5f,
                yangImpact = 0.5f,
                xpReward = 15,
                specialMessage = "HuggingFace Response"
            )
        } catch (e: Exception) {
            e.printStackTrace()
            GeminiService.generateResponse(context, prompt, personality, mode)
        }
    }
}

package com.example.data.repository

import android.content.Context

class UserPreferences(context: Context) {
    private val prefs = context.getSharedPreferences("dao_settings", Context.MODE_PRIVATE)

    var geminiApiKey: String
        get() = prefs.getString("gemini_api_key", "") ?: ""
        set(value) = prefs.edit().putString("gemini_api_key", value).apply()

    var geminiModel: String
        get() = prefs.getString("gemini_model", "gemini-2.5-flash") ?: "gemini-2.5-flash"
        set(value) = prefs.edit().putString("gemini_model", value).apply()

    var userName: String
        get() = prefs.getString("user_name", "Novice Seeker") ?: "Novice Seeker"
        set(value) = prefs.edit().putString("user_name", value).apply()

    var userEmail: String
        get() = prefs.getString("user_email", "mr.music7654@gmail.com") ?: "mr.music7654@gmail.com"
        set(value) = prefs.edit().putString("user_email", value).apply()

    var voiceVibe: String
        get() = prefs.getString("voice_vibe", "Sage Whisper") ?: "Sage Whisper"
        set(value) = prefs.edit().putString("voice_vibe", value).apply()

    var hapticEnabled: Boolean
        get() = prefs.getBoolean("haptic_enabled", true)
        set(value) = prefs.edit().putBoolean("haptic_enabled", value).apply()

    var ambientEnabled: Boolean
        get() = prefs.getBoolean("ambient_enabled", true)
        set(value) = prefs.edit().putBoolean("ambient_enabled", value).apply()

    var primaryLanguage: String
        get() = prefs.getString("primary_language", "English") ?: "English"
        set(value) = prefs.edit().putString("primary_language", value).apply()

    var githubApiKey: String
        get() = prefs.getString("github_api_key", "") ?: ""
        set(value) = prefs.edit().putString("github_api_key", value).apply()

    var huggingFaceApiKey: String
        get() = prefs.getString("huggingface_api_key", "") ?: ""
        set(value) = prefs.edit().putString("huggingface_api_key", value).apply()

    var telegramApiKey: String
        get() = prefs.getString("telegram_api_key", "") ?: ""
        set(value) = prefs.edit().putString("telegram_api_key", value).apply()

    var openAiApiKey: String
        get() = prefs.getString("openai_api_key", "") ?: ""
        set(value) = prefs.edit().putString("openai_api_key", value).apply()

    var customInstructions: String
        get() = prefs.getString("custom_instructions", "Provide wise, poetic guidance influenced by classical Taoism. Keep responses concise and insightful.") ?: ""
        set(value) = prefs.edit().putString("custom_instructions", value).apply()

    var aiTemperature: Float
        get() = prefs.getFloat("ai_temperature", 0.7f)
        set(value) = prefs.edit().putFloat("ai_temperature", value).apply()

    var maxTokens: Int
        get() = prefs.getInt("max_tokens", 16384)
        set(value) = prefs.edit().putInt("max_tokens", value).apply()

    var aiProvider: String
        get() = prefs.getString("ai_provider", "gemini") ?: "gemini"
        set(value) = prefs.edit().putString("ai_provider", value).apply()

    var appLockEnabled: Boolean
        get() = prefs.getBoolean("app_lock_enabled", false)
        set(value) = prefs.edit().putBoolean("app_lock_enabled", value).apply()

    var masterPassword: String
        get() = prefs.getString("master_password", "") ?: ""
        set(value) = prefs.edit().putString("master_password", value).apply()
}

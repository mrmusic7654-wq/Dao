package com.example.data.repository

import com.example.data.db.ChatDao
import com.example.data.model.ChatSession
import com.example.data.model.ChatMessage
import com.example.data.model.UserProfile
import com.example.data.dao_engine.DaoEngine
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlin.math.abs

// RPM Rate Limiter (Fix 29)
object RateLimiter {
    private val requestTimestamps = mutableListOf<Long>()
    private var maxRPM = 15 // Default for most Gemini models
    
    fun setLimit(rpm: Int) { maxRPM = rpm }
    
    fun canMakeRequest(): Boolean {
        val now = System.currentTimeMillis()
        val oneMinuteAgo = now - 60000
        synchronized(requestTimestamps) {
            requestTimestamps.removeAll { it < oneMinuteAgo }
            return requestTimestamps.size < maxRPM
        }
    }
    
    fun recordRequest() {
        synchronized(requestTimestamps) {
            requestTimestamps.add(System.currentTimeMillis())
        }
    }
    
    fun getRemainingRequests(): Int {
        val now = System.currentTimeMillis()
        val oneMinuteAgo = now - 60000
        synchronized(requestTimestamps) {
            requestTimestamps.removeAll { it < oneMinuteAgo }
            return maxRPM - requestTimestamps.size
        }
    }
}

class ChatRepository(private val chatDao: ChatDao) {

    val allSessions: Flow<List<ChatSession>> = chatDao.getAllSessions()
    
    fun getMessagesForSession(sessionId: Long): Flow<List<ChatMessage>> {
        return chatDao.getMessagesForSession(sessionId)
    }

    val userProfile: Flow<UserProfile?> = chatDao.getUserProfile()

    suspend fun createNewSession(title: String): Long {
        val session = ChatSession(title = title)
        return chatDao.insertSession(session)
    }

    suspend fun deleteSession(sessionId: Long) {
        chatDao.deleteSessionAndMessages(sessionId)
    }

    suspend fun updateSessionTitle(sessionId: Long, newTitle: String) {
        val session = ChatSession(id = sessionId, title = newTitle)
        chatDao.updateSession(session)
    }

    suspend fun insertUserMessage(sessionId: Long, content: String): ChatMessage {
        // Analyze content roughly for user scoring
        val responseAnalysis = DaoEngine.generateResponse(content)
        val userMsg = ChatMessage(
            sessionId = sessionId,
            content = content,
            isUser = true,
            yinScore = responseAnalysis.yinImpact,
            yangScore = responseAnalysis.yangImpact
        )
        chatDao.insertMessage(userMsg)
        
        // Update session last active time
        val session = ChatSession(id = sessionId, title = getSessionTitle(sessionId) ?: "Dao Discourse", lastActive = System.currentTimeMillis())
        chatDao.updateSession(session)
        
        return userMsg
    }

    suspend fun insertMessagePlaceholder(message: ChatMessage): Long {
        return chatDao.insertMessage(message)
    }

    suspend fun updateMessageContent(messageId: Long, content: String) {
        chatDao.updateMessageContent(messageId, content)
    }

    suspend fun generateAndSaveDaoResponse(context: android.content.Context, sessionId: Long, userMessageContent: String, personality: String = "Zen Sage", mode: String = "Direct", systemInstructionOverride: String? = null): ChatMessage {
        val prefs = UserPreferences(context)
        val response = when (prefs.aiProvider) {
            "openai" -> com.example.data.dao_engine.OpenAIService.generateResponse(context, userMessageContent, personality, mode, systemInstructionOverride)
            "huggingface" -> com.example.data.dao_engine.HuggingFaceService.generateResponse(context, userMessageContent, personality, mode, systemInstructionOverride)
            else -> com.example.data.dao_engine.GeminiService.generateResponse(context, userMessageContent, personality, mode, systemInstructionOverride)
        }
        
        val daoMsg = ChatMessage(
            sessionId = sessionId,
            content = response.replyText,
            isUser = false,
            yinScore = response.yinImpact,
            yangScore = response.yangImpact
        )
        chatDao.insertMessage(daoMsg)
        
        // Auto-update conversation title if it's currently a default
        val currentTitle = getSessionTitle(sessionId)
        if (currentTitle == null || currentTitle == "New Discourse" || currentTitle == "Dao Discourse") {
            val words = userMessageContent.split(" ")
            val titleText = if (words.size > 3) {
                words.take(3).joinToString(" ") + "..."
            } else {
                userMessageContent
            }
            updateSessionTitle(sessionId, titleText)
        }

        // Calculate simulated tokens consumption for dynamic reminder
        val baseTokens = when (mode) {
            "Deep Think" -> 150_000L + (userMessageContent.length * 100L) + (response.replyText.length * 300L)
            "Agent" -> 100_000L + (userMessageContent.length * 150L) + (response.replyText.length * 200L)
            "Web Search" -> 75_000L + (userMessageContent.length * 200L) + (response.replyText.length * 150L)
            "Automation" -> 50_000L + (userMessageContent.length * 100L) + (response.replyText.length * 100L)
            "Translate" -> 30_000L + (userMessageContent.length * 80L) + (response.replyText.length * 80L)
            else -> 15_000L + (userMessageContent.length * 50L) + (response.replyText.length * 50L)
        }

        // Update User Profile (XP, Level, Balance)
        updateProfile(response.yinImpact, response.yangImpact, baseTokens)

        return daoMsg
    }

    private suspend fun getSessionTitle(sessionId: Long): String? {
        val sessions = allSessions.firstOrNull() ?: emptyList()
        return sessions.find { it.id == sessionId }?.title
    }

    private suspend fun updateProfile(yinImpact: Float, yangImpact: Float, tokenUsage: Long) {
        val currentProfile = userProfile.firstOrNull() ?: UserProfile(
            xp = 0L,
            yinBalance = 0.5f,
            levelName = "Zen Neophyte",
            harmonyStreak = 0,
            tokenTotal = 0L,
            tokenLimit = 1_000_000L
        )

        val newXp = currentProfile.xp + tokenUsage
        val newTokenTotal = currentProfile.tokenTotal + tokenUsage
        
        // Running average of Yin balance
        // If we get an impact, we blend it into the running balance (0f to 1f, where 0.5f is perfect harmony)
        val targetYinRatio = yinImpact / (yinImpact + yangImpact).coerceAtLeast(0.01f)
        val newYinBalance = (currentProfile.yinBalance * 0.85f) + (targetYinRatio * 0.15f)

        // Calculate level name based on total tokens (xp)
        val newLevelName = getLevelName(newXp)

        // Streak: if balance is very close to 0.5 (perfect harmony between 0.45 and 0.55), increment streak
        val diff = abs(newYinBalance - 0.5f)
        val newStreak = if (diff < 0.08f) {
            currentProfile.harmonyStreak + 1
        } else {
            0
        }

        val updatedProfile = UserProfile(
            id = 1,
            xp = newXp,
            yinBalance = newYinBalance,
            levelName = newLevelName,
            harmonyStreak = newStreak,
            tokenTotal = newTokenTotal,
            tokenLimit = currentProfile.tokenLimit
        )
        chatDao.insertOrUpdateProfile(updatedProfile)
    }

    private fun getLevelName(tokens: Long): String {
        var level = 1L
        var nextLevelStartTokens = 1_000_000L
        while (tokens >= nextLevelStartTokens) {
            level++
            val neededForNext = level * 1_000_000L
            nextLevelStartTokens += neededForNext
        }
        
        return when (level) {
            1L -> "Zen Neophyte"
            2L -> "Curious Seeker"
            3L -> "Mindful Wayfarer"
            4L -> "Silent Observer"
            5L -> "Entropy Balancer"
            6L -> "Yin-Yang Adept"
            7L -> "Spiritual Architect"
            8L -> "Cosmic Harmonizer"
            9L -> "Dao Sentinel"
            10L -> "Transcendent Sage"
            else -> "Dao Master Lvl $level ☯"
        }
    }

    suspend fun resetProfile() {
        val initialProfile = UserProfile(
            id = 1,
            xp = 0L,
            yinBalance = 0.5f,
            levelName = "Zen Neophyte",
            harmonyStreak = 0
        )
        chatDao.insertOrUpdateProfile(initialProfile)
    }
}

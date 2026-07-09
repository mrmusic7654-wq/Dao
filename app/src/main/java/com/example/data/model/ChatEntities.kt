package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "chat_sessions")
data class ChatSession(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val createdAt: Long = System.currentTimeMillis(),
    val lastActive: Long = System.currentTimeMillis()
)

@Entity(tableName = "chat_messages")
data class ChatMessage(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: Long,
    val content: String,
    val isUser: Boolean,
    val timestamp: Long = System.currentTimeMillis(),
    val yinScore: Float = 0.5f,
    val yangScore: Float = 0.5f
)

@Entity(tableName = "user_profile")
data class UserProfile(
    @PrimaryKey val id: Int = 1,
    val xp: Long = 0L,
    val yinBalance: Float = 0.5f,
    val levelName: String = "Curious Mind",
    val harmonyStreak: Int = 0,
    val tokenTotal: Long = 0L,
    val tokenLimit: Long = 1_000_000L // 1M TPM default
)

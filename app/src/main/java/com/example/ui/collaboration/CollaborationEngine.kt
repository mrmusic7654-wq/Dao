package com.example.ui.collaboration

import android.content.Context
import com.example.data.model.ChatMessage
import kotlinx.coroutines.*
import org.json.JSONObject
import java.net.URI

class CollaborationEngine(private val context: Context) {
    private var webSocket: okhttp3.WebSocket? = null
    private var sessionId: String = ""
    private var isConnected = false
    private var onMessageReceived: ((ChatMessage) -> Unit)? = null
    private var onUserJoined: ((String) -> Unit)? = null
    private var onUserLeft: ((String) -> Unit)? = null

    fun connectToSession(sessionId: String, username: String) {
        this.sessionId = sessionId
        val serverUrl = "wss://dao-collab.example.com/ws?session=$sessionId&user=$username"
        
        val client = okhttp3.OkHttpClient()
        val request = okhttp3.Request.Builder().url(serverUrl).build()
        
        webSocket = client.newWebSocket(request, object : okhttp3.WebSocketListener() {
            override fun onOpen(webSocket: okhttp3.WebSocket, response: okhttp3.Response) {
                isConnected = true
            }

            override fun onMessage(webSocket: okhttp3.WebSocket, text: String) {
                try {
                    val json = JSONObject(text)
                    when (json.getString("type")) {
                        "message" -> {
                            val msg = ChatMessage(
                                sessionId = sessionId.toLongOrNull() ?: 0,
                                content = json.getString("content"),
                                isUser = json.getString("sender") != "dao"
                            )
                            onMessageReceived?.invoke(msg)
                        }
                        "user_joined" -> {
                            onUserJoined?.invoke(json.getString("username"))
                        }
                        "user_left" -> {
                            onUserLeft?.invoke(json.getString("username"))
                        }
                    }
                } catch (_: Exception) {}
            }

            override fun onFailure(webSocket: okhttp3.WebSocket, t: Throwable, response: okhttp3.Response?) {
                isConnected = false
            }
        })
    }

    fun sendMessage(content: String) {
        val json = JSONObject().apply {
            put("type", "message")
            put("content", content)
            put("session", sessionId)
        }
        webSocket?.send(json.toString())
    }

    fun setCallbacks(
        onMessage: (ChatMessage) -> Unit,
        onJoin: (String) -> Unit,
        onLeave: (String) -> Unit
    ) {
        onMessageReceived = onMessage
        onUserJoined = onJoin
        onUserLeft = onLeave
    }

    fun disconnect() {
        webSocket?.close(1000, "User disconnected")
        isConnected = false
    }

    fun isActive(): Boolean = isConnected
}

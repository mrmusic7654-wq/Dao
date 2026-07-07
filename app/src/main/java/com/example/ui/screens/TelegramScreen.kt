package com.example.ui.screens

import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.repository.UserPreferences
import com.example.ui.theme.*
import kotlinx.coroutines.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.*

// ==================== DATA MODELS ====================

data class TelegramChat(
    val id: Long,
    val title: String,
    val type: String = "private",
    val lastMessage: String = "",
    val lastMessageTime: String = "",
    val unreadCount: Int = 0,
    val isOnline: Boolean = false,
    val isPinned: Boolean = false,
    val isMuted: Boolean = false,
    val avatar: String = "",
    val lastMessageType: String = "text" // text, photo, voice, video, sticker
)

data class TelegramMessage(
    val messageId: Long,
    val chatId: Long,
    val text: String = "",
    val fromMe: Boolean = false,
    val senderName: String = "",
    val timestamp: Long = System.currentTimeMillis(),
    val type: String = "text",
    val isRead: Boolean = false,
    val replyTo: String? = null
)

data class TelegramContact(
    val userId: Long,
    val name: String,
    val phone: String = "",
    val isOnline: Boolean = false,
    val lastSeen: String = ""
)

enum class TelegramScreen { CHAT_LIST, CONVERSATION, CONTACTS, SETTINGS }

// ==================== TELEGRAM API SERVICE ====================

object TelegramApiService {
    private const val BASE_URL = "https://api.telegram.org"

    fun executeRequest(botToken: String, method: String, params: Map<String, String> = emptyMap()): String {
        val urlStr = "$BASE_URL/bot$botToken/$method"
        val url = if (params.isNotEmpty()) {
            URL("$urlStr?${params.map { "${it.key}=${URLEncoder.encode(it.value, "UTF-8")}" }.joinToString("&")}")
        } else { URL(urlStr) }
        val connection = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = if (params.isNotEmpty()) "POST" else "GET"
            connectTimeout = 15000; readTimeout = 15000
            if (params.isNotEmpty()) {
                doOutput = true; setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
                OutputStreamWriter(outputStream).use { it.write(params.map { "${it.key}=${URLEncoder.encode(it.value, "UTF-8")}" }.joinToString("&")) }
            }
        }
        return try {
            if (connection.responseCode in 200..299) BufferedReader(InputStreamReader(connection.inputStream)).readText()
            else "{\"ok\":false,\"description\":\"HTTP ${connection.responseCode}\"}"
        } catch (e: Exception) { "{\"ok\":false,\"description\":\"${e.message}\"}" }
        finally { connection.disconnect() }
    }

    fun getMe(botToken: String) = executeRequest(botToken, "getMe")
    fun getUpdates(botToken: String, offset: Long = 0) = executeRequest(botToken, "getUpdates", mapOf("offset" to offset.toString(), "limit" to "50"))
    fun sendMessage(botToken: String, chatId: Long, text: String) = executeRequest(botToken, "sendMessage", mapOf("chat_id" to chatId.toString(), "text" to text))
    fun setWebhook(botToken: String, url: String) = executeRequest(botToken, "setWebhook", mapOf("url" to url))
    fun deleteWebhook(botToken: String) = executeRequest(botToken, "deleteWebhook")
    fun getWebhookInfo(botToken: String) = executeRequest(botToken, "getWebhookInfo")
}

// ==================== MAIN TELEGRAM SCREEN ====================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TelegramScreen(isDark: Boolean, onMenuClick: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember { UserPreferences(context) }
    var botToken by remember { mutableStateOf(prefs.telegramApiKey) }
    var showTokenDialog by remember { mutableStateOf(false) }
    var tokenInput by remember { mutableStateOf(botToken) }

    var currentScreen by remember { mutableStateOf(TelegramScreen.CHAT_LIST) }
    var isLoading by remember { mutableStateOf(false) }
    var isBotConnected by remember { mutableStateOf(false) }
    var botUsername by remember { mutableStateOf("") }
    var botName by remember { mutableStateOf("") }

    var chats by remember { mutableStateOf(listOf<TelegramChat>()) }
    var contacts by remember { mutableStateOf(listOf<TelegramContact>()) }
    var selectedChat by remember { mutableStateOf<TelegramChat?>(null) }
    var conversationMessages by remember { mutableStateOf(listOf<TelegramMessage>()) }

    // Message input
    var messageText by remember { mutableStateOf("") }
    var isRecording by remember { mutableStateOf(false) }

    // Drawer
    var drawerOpen by remember { mutableStateOf(false) }
    var showThemeDialog by remember { mutableStateOf(false) }

    // Send animation
    var showSendAnimation by remember { mutableStateOf(false) }

    val scope = rememberCoroutineScope()
    val telegramBlue = Color(0xFF0088CC)
    val telegramDarkBg = Color(0xFF17212B)
    val telegramCardBg = Color(0xFF242F3D)

    fun connectBot() {
        if (botToken.isBlank()) return
        isLoading = true
        scope.launch(Dispatchers.IO) {
            val result = TelegramApiService.getMe(botToken)
            try {
                val json = JSONObject(result)
                if (json.optBoolean("ok", false)) {
                    val user = json.getJSONObject("result")
                    botUsername = user.optString("username", ""); botName = user.optString("first_name", "")
                    isBotConnected = true
                } else { isBotConnected = false }
            } catch (e: Exception) { isBotConnected = false }
            isLoading = false
        }
    }

    fun loadChats() {
        if (!isBotConnected) return
        isLoading = true
        scope.launch(Dispatchers.IO) {
            val result = TelegramApiService.getUpdates(botToken)
            try {
                val json = JSONObject(result)
                if (json.optBoolean("ok", false)) {
                    val updates = json.optJSONArray("result") ?: JSONArray()
                    val chatMap = mutableMapOf<Long, TelegramChat>()
                    val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
                    for (i in 0 until updates.length()) {
                        val update = updates.getJSONObject(i)
                        val message = update.optJSONObject("message") ?: continue
                        val chat = message.getJSONObject("chat")
                        val chatId = chat.getLong("id")
                        val title = chat.optString("title", chat.optString("first_name", "Unknown"))
                        val type = chat.optString("type", "private")
                        val text = message.optString("text", message.optString("caption", ""))
                        val date = message.optLong("date", 0) * 1000
                        val msgType = if (message.has("photo")) "photo" else if (message.has("voice")) "voice" else if (message.has("video")) "video" else "text"
                        chatMap[chatId] = TelegramChat(id = chatId, title = title, type = type,
                            lastMessage = text.take(100), lastMessageTime = if (date > 0) sdf.format(Date(date)) else "",
                            lastMessageType = msgType, unreadCount = if (i < 3) i + 1 else 0, isPinned = i == 0)
                    }
                    chats = chatMap.values.toList().sortedByDescending { it.lastMessageTime }
                }
            } catch (e: Exception) { chats = emptyList() }
            isLoading = false
        }
    }

    fun loadContacts() {
        if (!isBotConnected) return
        scope.launch(Dispatchers.IO) {
            val result = TelegramApiService.getUpdates(botToken)
            try {
                val json = JSONObject(result)
                if (json.optBoolean("ok", false)) {
                    val updates = json.optJSONArray("result") ?: JSONArray()
                    val userMap = mutableMapOf<Long, TelegramContact>()
                    for (i in 0 until updates.length()) {
                        val update = updates.getJSONObject(i)
                        val message = update.optJSONObject("message") ?: continue
                        val from = message.optJSONObject("from") ?: continue
                        val userId = from.getLong("id")
                        val name = "${from.optString("first_name", "")} ${from.optString("last_name", "")}".trim()
                        userMap[userId] = TelegramContact(userId = userId, name = name.ifBlank { from.optString("username", "User") })
                    }
                    contacts = userMap.values.toList()
                }
            } catch (e: Exception) { contacts = emptyList() }
        }
    }

    fun sendMessage() {
        if (messageText.isBlank() || selectedChat == null) return
        val chat = selectedChat!!
        scope.launch(Dispatchers.IO) {
            TelegramApiService.sendMessage(botToken, chat.id, messageText)
            withContext(Dispatchers.Main) {
                conversationMessages = conversationMessages + TelegramMessage(
                    messageId = System.currentTimeMillis(), chatId = chat.id,
                    text = messageText, fromMe = true, senderName = "You",
                    timestamp = System.currentTimeMillis(), type = "text", isRead = true
                )
                messageText = ""
                showSendAnimation = true
                delay(600); showSendAnimation = false
            }
        }
    }

    LaunchedEffect(isBotConnected) {
        if (isBotConnected) { loadChats(); loadContacts() }
    }

    // ==================== TOKEN DIALOG ====================

    if (showTokenDialog) {
        AlertDialog(
            onDismissRequest = { showTokenDialog = false },
            title = { Text("Connect Telegram Bot", fontFamily = FontFamily.Serif, color = telegramBlue) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Enter Bot Token from @BotFather", color = YinTextSecondary, fontSize = 12.sp)
                    OutlinedTextField(value = tokenInput, onValueChange = { tokenInput = it },
                        placeholder = { Text("123456:ABC-DEF...") }, modifier = Modifier.fillMaxWidth(), singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = telegramBlue))
                }
            },
            confirmButton = {
                Button(onClick = { botToken = tokenInput; prefs.telegramApiKey = tokenInput; showTokenDialog = false; connectBot() },
                    colors = ButtonDefaults.buttonColors(containerColor = telegramBlue)) { Text("Connect", color = Color.White) }
            },
            dismissButton = { TextButton(onClick = { showTokenDialog = false }) { Text("Cancel", color = telegramBlue) } },
            containerColor = if (isDark) Color(0xFF17212B) else Color(0xFFF7F4EE)
        )
    }

    // ==================== SLIDE-OUT DRAWER ====================

    if (drawerOpen) {
        Box(modifier = Modifier.fillMaxSize()) {
            // Backdrop
            Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.5f)).clickable(onClick = { drawerOpen = false }))
            // Drawer panel
            AnimatedVisibility(visible = drawerOpen, enter = slideInHorizontally(initialOffsetX = { -it }), exit = slideOutHorizontally(targetOffsetX = { -it })) {
                Column(
                    modifier = Modifier.fillMaxHeight().width(300.dp).background(if (isDark) Color(0xFF17212B) else Color(0xFFF0F0F0))
                        .clickable(onClick = { /* consume */ })
                ) {
                    // Gradient header
                    Box(modifier = Modifier.fillMaxWidth().height(180.dp).background(Brush.linearGradient(listOf(telegramBlue, Color(0xFF00B0FF)))), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Box(modifier = Modifier.size(72.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.2f)), contentAlignment = Alignment.Center) {
                                Text(botName.take(1).uppercase(), color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold)
                            }
                            Text(botName.ifBlank { "Bot" }, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                            if (isBotConnected) Text("@$botUsername", color = Color.White.copy(alpha = 0.8f), fontSize = 13.sp)
                        }
                    }

                    // Saved Messages
                    DrawerItem(Icons.Default.Bookmark, "Saved Messages", Color(0xFF4FC3F7)) { drawerOpen = false }

                    // Menu items
                    DrawerItem(Icons.Default.Call, "Calls", Color(0xFF4CAF50)) { drawerOpen = false }
                    DrawerItem(Icons.Default.People, "Contacts", Color(0xFFFF9800)) { currentScreen = TelegramScreen.CONTACTS; drawerOpen = false }
                    DrawerItem(Icons.Default.Devices, "Devices", Color(0xFF9C27B0)) { drawerOpen = false }

                    Spacer(Modifier.weight(1f))

                    // Theme switcher
                    Surface(modifier = Modifier.fillMaxWidth().padding(16.dp).clip(RoundedCornerShape(12.dp)), color = Color.White.copy(alpha = 0.1f)) {
                        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Dark Mode", color = Color.White, fontSize = 14.sp)
                            Switch(checked = isDark, onCheckedChange = { showThemeDialog = true }, colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = telegramBlue))
                        }
                    }
                }
            }
        }
    }

    // ==================== MAIN CONTENT ====================

    when (currentScreen) {
        TelegramScreen.CHAT_LIST -> {
            Column(modifier = Modifier.fillMaxSize().background(if (isDark) telegramDarkBg else Color(0xFFF5F5F5))) {
                // Header
                Surface(color = if (isDark) Color(0xFF1F2C38) else telegramBlue, shadowElevation = 4.dp) {
                    Row(modifier = Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { drawerOpen = true }) { Icon(Icons.Default.Menu, "Menu", tint = Color.White) }
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Telegram", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                            if (isBotConnected) Text("@$botUsername", color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp)
                            else Text("Connecting...", color = Color.White.copy(alpha = 0.5f), fontSize = 11.sp)
                        }
                        IconButton(onClick = { /* Search */ }) { Icon(Icons.Default.Search, "Search", tint = Color.White) }
                        if (!isBotConnected) IconButton(onClick = { showTokenDialog = true }) { Icon(Icons.Default.Key, "Connect", tint = Color.White) }
                    }
                }

                if (isLoading) LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), color = telegramBlue)

                if (!isBotConnected) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Icon(Icons.Default.Send, null, modifier = Modifier.size(64.dp), tint = telegramBlue.copy(alpha = 0.4f))
                            Text("Connect a bot to start", color = Color.Gray, fontSize = 16.sp)
                            Button(onClick = { showTokenDialog = true }, colors = ButtonDefaults.buttonColors(containerColor = telegramBlue)) { Text("Connect Bot", color = Color.White) }
                        }
                    }
                } else {
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        // Pinned section
                        val pinned = chats.filter { it.isPinned }
                        if (pinned.isNotEmpty()) {
                            item { Text("Pinned", color = Color(0xFF8899AA), fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) }
                            items(pinned) { chat -> ChatListItem(chat, telegramBlue, isDark) { selectedChat = chat; currentScreen = TelegramScreen.CONVERSATION } }
                            item { Divider(color = Color(0xFF334455), modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) }
                        }

                        item { Text("All Chats", color = Color(0xFF8899AA), fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) }
                        items(chats) { chat -> ChatListItem(chat, telegramBlue, isDark) { selectedChat = chat; currentScreen = TelegramScreen.CONVERSATION } }
                        if (chats.isEmpty()) { item { Text("No chats yet", color = Color.Gray, modifier = Modifier.padding(32.dp)) } }
                    }
                }

                // Bottom tabs
                Surface(color = if (isDark) Color(0xFF1F2C38) else Color.White, shadowElevation = 8.dp) {
                    Row(modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(vertical = 6.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                        listOf(
                            TelegramScreen.CHAT_LIST to Triple(Icons.Default.Chat, "Chats", telegramBlue),
                            TelegramScreen.CONTACTS to Triple(Icons.Default.People, "Contacts", Color(0xFFFF9800)),
                            TelegramScreen.SETTINGS to Triple(Icons.Default.Settings, "Settings", Color.Gray)
                        ).forEach { (screen, data) ->
                            Column(horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = { currentScreen = screen }).padding(horizontal = 16.dp, vertical = 4.dp)) {
                                Icon(data.first, data.second, tint = if (currentScreen == screen) data.third else Color.Gray, modifier = Modifier.size(22.dp))
                                Text(data.second, color = if (currentScreen == screen) data.third else Color.Gray, fontSize = 10.sp, fontWeight = if (currentScreen == screen) FontWeight.Bold else FontWeight.Normal)
                            }
                        }
                    }
                }
            }
        }

        TelegramScreen.CONVERSATION -> {
            ConversationScreen(
                chat = selectedChat,
                messages = conversationMessages,
                messageText = messageText,
                onMessageChange = { messageText = it },
                onSend = { sendMessage() },
                onBack = { currentScreen = TelegramScreen.CHAT_LIST },
                isRecording = isRecording,
                onRecordToggle = { isRecording = !isRecording },
                showSendAnimation = showSendAnimation,
                isDark = isDark,
                telegramBlue = telegramBlue,
                telegramDarkBg = telegramDarkBg
            )
        }

        TelegramScreen.CONTACTS -> {
            Column(modifier = Modifier.fillMaxSize().background(if (isDark) telegramDarkBg else Color(0xFFF5F5F5))) {
                Surface(color = if (isDark) Color(0xFF1F2C38) else telegramBlue, shadowElevation = 4.dp) {
                    Row(modifier = Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { currentScreen = TelegramScreen.CHAT_LIST }) { Icon(Icons.Default.ArrowBack, null, tint = Color.White) }
                        Text("Contacts", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                    }
                }
                if (contacts.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("No contacts", color = Color.Gray) }
                } else {
                    LazyColumn {
                        items(contacts) { contact ->
                            Row(modifier = Modifier.fillMaxWidth().clickable(onClick = {
                                selectedChat = TelegramChat(id = contact.userId, title = contact.name)
                                currentScreen = TelegramScreen.CONVERSATION
                            }).padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Box(modifier = Modifier.size(48.dp).clip(CircleShape).background(telegramBlue.copy(alpha = 0.2f)), contentAlignment = Alignment.Center) {
                                    Text(contact.name.take(1).uppercase(), color = telegramBlue, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                                }
                                Spacer(Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) { Text(contact.name, color = if (isDark) Color.White else Color.Black, fontWeight = FontWeight.Medium); Text("ID: ${contact.userId}", color = Color.Gray, fontSize = 12.sp) }
                            }
                        }
                    }
                }
            }
        }

        TelegramScreen.SETTINGS -> {
            Column(modifier = Modifier.fillMaxSize().background(if (isDark) telegramDarkBg else Color(0xFFF5F5F5))) {
                Surface(color = if (isDark) Color(0xFF1F2C38) else telegramBlue, shadowElevation = 4.dp) {
                    Row(modifier = Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { currentScreen = TelegramScreen.CHAT_LIST }) { Icon(Icons.Default.ArrowBack, null, tint = Color.White) }
                        Text("Settings", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                    }
                }
                LazyColumn(modifier = Modifier.padding(16.dp)) {
                    item {
                        Card(colors = CardDefaults.cardColors(containerColor = if (isDark) Color(0xFF242F3D) else Color.White)) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text("Bot Configuration", color = telegramBlue, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                Spacer(Modifier.height(8.dp))
                                Text("Token: ${if (botToken.isNotBlank()) "Connected ✓" else "Not set"}", color = Color.Gray, fontSize = 12.sp)
                                Text("Bot: @${botUsername.ifBlank { "unknown" }}", color = Color.Gray, fontSize = 12.sp)
                                Spacer(Modifier.height(8.dp))
                                Button(onClick = { showTokenDialog = true }, colors = ButtonDefaults.buttonColors(containerColor = telegramBlue), modifier = Modifier.fillMaxWidth()) { Text("Change Token", color = Color.White) }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ==================== CHAT LIST ITEM ====================

@Composable
private fun ChatListItem(chat: TelegramChat, accentColor: Color, isDark: Boolean, onClick: () -> Unit) {
    val lastMsgIcon = when (chat.lastMessageType) {
        "photo" -> "📷 Photo"
        "voice" -> "🎤 Voice"
        "video" -> "🎬 Video"
        "sticker" -> "🎯 Sticker"
        else -> chat.lastMessage
    }

    Row(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        // Avatar
        Box(modifier = Modifier.size(54.dp).clip(CircleShape).background(Brush.linearGradient(listOf(accentColor, Color(0xFF00B0FF)))), contentAlignment = Alignment.Center) {
            Text(chat.title.take(1).uppercase(), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 20.sp)
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(chat.title, color = if (isDark) Color.White else Color.Black, fontWeight = FontWeight.Bold, fontSize = 15.sp, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(chat.lastMessageTime, color = Color(0xFF8899AA), fontSize = 11.sp)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(lastMsgIcon, color = Color(0xFF8899AA), fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                if (chat.unreadCount > 0) {
                    Spacer(Modifier.width(6.dp))
                    Surface(color = if (chat.isMuted) Color.Gray.copy(alpha = 0.3f) else accentColor, shape = CircleShape) {
                        Text("${chat.unreadCount}", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp))
                    }
                } else if (chat.isMuted) {
                    Spacer(Modifier.width(6.dp))
                    Icon(Icons.Default.NotificationsOff, null, tint = Color.Gray, modifier = Modifier.size(14.dp))
                }
            }
        }
    }
}

// ==================== CONVERSATION SCREEN ====================

@Composable
private fun ConversationScreen(
    chat: TelegramChat?,
    messages: List<TelegramMessage>,
    messageText: String,
    onMessageChange: (String) -> Unit,
    onSend: () -> Unit,
    onBack: () -> Unit,
    isRecording: Boolean,
    onRecordToggle: () -> Unit,
    showSendAnimation: Boolean,
    isDark: Boolean,
    telegramBlue: Color,
    telegramDarkBg: Color
) {
    if (chat == null) return

    Column(modifier = Modifier.fillMaxSize().background(if (isDark) telegramDarkBg else Color(0xFFF5F5F5))) {
        // Chat header
        Surface(color = if (isDark) Color(0xFF1F2C38) else telegramBlue, shadowElevation = 4.dp) {
            Row(modifier = Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, null, tint = Color.White) }
                Box(modifier = Modifier.size(40.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.2f)), contentAlignment = Alignment.Center) {
                    Text(chat.title.take(1).uppercase(), color = Color.White, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(chat.title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Text(if (chat.isOnline) "online" else "last seen recently", color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp)
                }
                IconButton(onClick = { /* Search chat */ }) { Icon(Icons.Default.Search, null, tint = Color.White) }
                IconButton(onClick = { /* Menu */ }) { Icon(Icons.Default.MoreVert, null, tint = Color.White) }
            }
        }

        // Messages
        LazyColumn(modifier = Modifier.weight(1f).padding(horizontal = 8.dp, vertical = 4.dp), reverseLayout = true) {
            items(messages.reversed()) { msg ->
                MessageBubble(msg = msg, telegramBlue = telegramBlue, isDark = isDark)
            }
            if (messages.isEmpty()) {
                item {
                    Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                        Text("No messages yet", color = Color.Gray)
                    }
                }
            }
        }

        // Input bar
        Surface(color = if (isDark) Color(0xFF1F2C38) else Color.White, shadowElevation = 4.dp) {
            Row(modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                // Attachment
                IconButton(onClick = { /* Attachment sheet */ }) { Icon(Icons.Default.AttachFile, null, tint = Color(0xFF8899AA), modifier = Modifier.size(24.dp)) }

                // Message field
                OutlinedTextField(value = messageText, onValueChange = onMessageChange, placeholder = { Text("Message", color = Color.Gray) },
                    modifier = Modifier.weight(1f).height(44.dp), singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = telegramBlue, unfocusedBorderColor = Color.Transparent,
                        focusedContainerColor = if (isDark) Color(0xFF2B3E4F) else Color(0xFFF0F0F0),
                        unfocusedContainerColor = if (isDark) Color(0xFF2B3E4F) else Color(0xFFF0F0F0)),
                    textStyle = androidx.compose.ui.text.TextStyle(color = if (isDark) Color.White else Color.Black),
                    shape = RoundedCornerShape(22.dp))

                Spacer(Modifier.width(4.dp))

                // Send / Mic button
                if (messageText.isNotEmpty()) {
                    Box(modifier = Modifier.scale(if (showSendAnimation) 1.3f else 1f)) {
                        IconButton(onClick = onSend) { Icon(Icons.Default.Send, "Send", tint = telegramBlue, modifier = Modifier.size(24.dp)) }
                    }
                } else {
                    IconButton(onClick = onRecordToggle) {
                        Icon(if (isRecording) Icons.Default.Stop else Icons.Default.Mic, "Record", tint = if (isRecording) Color.Red else Color(0xFF8899AA), modifier = Modifier.size(24.dp))
                    }
                }
            }
        }
    }
}

// ==================== MESSAGE BUBBLE ====================

@Composable
private fun MessageBubble(msg: TelegramMessage, telegramBlue: Color, isDark: Boolean) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        horizontalAlignment = if (msg.fromMe) Alignment.End else Alignment.Start
    ) {
        // Reply preview
        msg.replyTo?.let {
            Surface(color = telegramBlue.copy(alpha = 0.1f), shape = RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp, bottomStart = 0.dp, bottomEnd = 0.dp)) {
                Text(it, color = telegramBlue, fontSize = 11.sp, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }

        Surface(
            color = if (msg.fromMe) telegramBlue else if (isDark) Color(0xFF2B3E4F) else Color(0xFFE8E8E8),
            shape = RoundedCornerShape(
                topStart = 16.dp, topEnd = 16.dp,
                bottomStart = if (msg.fromMe) 16.dp else 4.dp,
                bottomEnd = if (msg.fromMe) 4.dp else 16.dp
            ),
            modifier = Modifier.widthIn(max = 280.dp)
        ) {
            Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                Text(msg.text, color = if (msg.fromMe) Color.White else if (isDark) Color.White else Color.Black, fontSize = 14.sp)
                Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(msg.timestamp)),
                        color = if (msg.fromMe) Color.White.copy(alpha = 0.7f) else Color.Gray, fontSize = 10.sp
                    )
                    if (msg.fromMe) {
                        Spacer(Modifier.width(2.dp))
                        Icon(if (msg.isRead) Icons.Default.DoneAll else Icons.Default.Done, null,
                            tint = if (msg.isRead) Color(0xFF4FC3F7) else Color.White.copy(alpha = 0.5f), modifier = Modifier.size(14.dp))
                    }
                }
            }
        }
    }
}

// ==================== DRAWER ITEM ====================

@Composable
private fun DrawerItem(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, color: Color, onClick: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = color, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(16.dp))
        Text(label, color = Color.White, fontSize = 15.sp)
    }
}

package com.example.ui.screens

import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
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
    val type: String = "private", // private, group, supergroup, channel
    val lastMessage: String = "",
    val lastMessageTime: String = "",
    val unreadCount: Int = 0,
    val isOnline: Boolean = false,
    val avatar: String = ""
)

data class TelegramMessage(
    val messageId: Long,
    val chatId: Long,
    val text: String = "",
    val fromMe: Boolean = false,
    val senderName: String = "",
    val timestamp: Long = System.currentTimeMillis(),
    val type: String = "text" // text, photo, video, document, sticker
)

data class TelegramContact(
    val userId: Long,
    val name: String,
    val phone: String = "",
    val isOnline: Boolean = false,
    val lastSeen: String = ""
)

data class TelegramBot(
    val username: String,
    val description: String = "",
    val commands: List<String> = emptyList()
)

enum class TelegramTab { CHATS, CONTACTS, BOTS, SETTINGS, AGENT }

// ==================== TELEGRAM API SERVICE ====================

object TelegramApiService {
    private const val BASE_URL = "https://api.telegram.org"

    fun executeRequest(botToken: String, method: String, params: Map<String, String> = emptyMap()): String {
        val urlStr = "$BASE_URL/bot$botToken/$method"
        val url = if (params.isNotEmpty()) {
            URL("$urlStr?${params.map { "${it.key}=${URLEncoder.encode(it.value, "UTF-8")}" }.joinToString("&")}")
        } else {
            URL(urlStr)
        }
        val connection = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = if (params.isNotEmpty()) "POST" else "GET"
            connectTimeout = 15000
            readTimeout = 15000
            if (params.isNotEmpty()) {
                doOutput = true
                setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
                val postData = params.map { "${it.key}=${URLEncoder.encode(it.value, "UTF-8")}" }.joinToString("&")
                OutputStreamWriter(outputStream).use { it.write(postData) }
            }
        }
        return try {
            if (connection.responseCode in 200..299) {
                BufferedReader(InputStreamReader(connection.inputStream)).readText()
            } else {
                val errorBody = try { BufferedReader(InputStreamReader(connection.errorStream)).readText() } catch (e: Exception) { "" }
                "{\"ok\":false,\"description\":\"HTTP ${connection.responseCode}: $errorBody\"}"
            }
        } catch (e: Exception) {
            "{\"ok\":false,\"description\":\"${e.message}\"}"
        } finally {
            connection.disconnect()
        }
    }

    fun getMe(botToken: String): String = executeRequest(botToken, "getMe")

    fun getUpdates(botToken: String, offset: Long = 0): String =
        executeRequest(botToken, "getUpdates", mapOf("offset" to offset.toString(), "limit" to "50"))

    fun sendMessage(botToken: String, chatId: Long, text: String): String =
        executeRequest(botToken, "sendMessage", mapOf("chat_id" to chatId.toString(), "text" to text))

    fun sendPhoto(botToken: String, chatId: Long, photoUrl: String, caption: String = ""): String =
        executeRequest(botToken, "sendPhoto", mapOf("chat_id" to chatId.toString(), "photo" to photoUrl, "caption" to caption))

    fun sendDocument(botToken: String, chatId: Long, documentUrl: String, caption: String = ""): String =
        executeRequest(botToken, "sendDocument", mapOf("chat_id" to chatId.toString(), "document" to documentUrl, "caption" to caption))

    fun forwardMessage(botToken: String, chatId: Long, fromChatId: Long, messageId: Long): String =
        executeRequest(botToken, "forwardMessage", mapOf("chat_id" to chatId.toString(), "from_chat_id" to fromChatId.toString(), "message_id" to messageId.toString()))

    fun getChat(botToken: String, chatId: Long): String =
        executeRequest(botToken, "getChat", mapOf("chat_id" to chatId.toString()))

    fun getChatAdministrators(botToken: String, chatId: Long): String =
        executeRequest(botToken, "getChatAdministrators", mapOf("chat_id" to chatId.toString()))

    fun setWebhook(botToken: String, url: String): String =
        executeRequest(botToken, "setWebhook", mapOf("url" to url))

    fun deleteWebhook(botToken: String): String =
        executeRequest(botToken, "deleteWebhook")

    fun getWebhookInfo(botToken: String): String =
        executeRequest(botToken, "getWebhookInfo")

    fun pinMessage(botToken: String, chatId: Long, messageId: Long): String =
        executeRequest(botToken, "pinChatMessage", mapOf("chat_id" to chatId.toString(), "message_id" to messageId.toString()))

    fun unpinMessage(botToken: String, chatId: Long, messageId: Long): String =
        executeRequest(botToken, "unpinChatMessage", mapOf("chat_id" to chatId.toString(), "message_id" to messageId.toString()))

    fun deleteMessage(botToken: String, chatId: Long, messageId: Long): String =
        executeRequest(botToken, "deleteMessage", mapOf("chat_id" to chatId.toString(), "message_id" to messageId.toString()))

    fun getFile(botToken: String, fileId: String): String =
        executeRequest(botToken, "getFile", mapOf("file_id" to fileId))

    fun getFileUrl(botToken: String, filePath: String): String =
        "$BASE_URL/file/bot$botToken/$filePath"
}

// ==================== TELEGRAM SCREEN ====================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TelegramScreen(
    isDark: Boolean,
    onMenuClick: () -> Unit
) {
    val context = LocalContext.current
    val prefs = remember { UserPreferences(context) }
    var botToken by remember { mutableStateOf(prefs.telegramApiKey) }
    var showTokenDialog by remember { mutableStateOf(false) }
    var tokenInput by remember { mutableStateOf(botToken) }

    // Tabs
    var activeTab by remember { mutableStateOf(TelegramTab.CHATS) }
    var isLoading by remember { mutableStateOf(false) }

    // Bot info
    var botUsername by remember { mutableStateOf("") }
    var botName by remember { mutableStateOf("") }
    var isBotConnected by remember { mutableStateOf(false) }

    // Data
    var chats by remember { mutableStateOf(listOf<TelegramChat>()) }
    var messages by remember { mutableStateOf(listOf<TelegramMessage>()) }
    var contacts by remember { mutableStateOf(listOf<TelegramContact>()) }
    var selectedChat by remember { mutableStateOf<TelegramChat?>(null) }

    // Compose message
    var messageText by remember { mutableStateOf("") }
    var showSendDialog by remember { mutableStateOf(false) }
    var sendTarget by remember { mutableStateOf<Long?>(null) }
    var statusMessage by remember { mutableStateOf("") }
    var showStatus by remember { mutableStateOf(false) }

    val scope = rememberCoroutineScope()
    val telegramBlue = Color(0xFF0088CC)

    fun connectBot() {
        if (botToken.isBlank()) return
        isLoading = true
        scope.launch(Dispatchers.IO) {
            val result = TelegramApiService.getMe(botToken)
            try {
                val json = JSONObject(result)
                if (json.optBoolean("ok", false)) {
                    val user = json.getJSONObject("result")
                    botUsername = user.optString("username", "")
                    botName = user.optString("first_name", "")
                    isBotConnected = true
                } else {
                    isBotConnected = false
                    botUsername = ""
                    botName = ""
                }
            } catch (e: Exception) {
                isBotConnected = false
            }
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
                    for (i in 0 until updates.length()) {
                        val update = updates.getJSONObject(i)
                        val message = update.optJSONObject("message") ?: update.optJSONObject("channel_post") ?: continue
                        val chat = message.getJSONObject("chat")
                        val chatId = chat.getLong("id")
                        val title = chat.optString("title", chat.optString("first_name", "Unknown"))
                        val type = chat.optString("type", "private")
                        val text = message.optString("text", message.optString("caption", ""))
                        val date = message.optLong("date", 0) * 1000
                        val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
                        chatMap[chatId] = TelegramChat(
                            id = chatId,
                            title = title,
                            type = type,
                            lastMessage = text.take(100),
                            lastMessageTime = if (date > 0) sdf.format(Date(date)) else ""
                        )
                    }
                    chats = chatMap.values.toList()
                }
            } catch (e: Exception) {
                chats = emptyList()
            }
            isLoading = false
        }
    }

    fun loadContacts() {
        if (!isBotConnected) return
        isLoading = true
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
                        userMap[userId] = TelegramContact(
                            userId = userId,
                            name = name.ifBlank { from.optString("username", "User") },
                            phone = "",
                            isOnline = false
                        )
                    }
                    contacts = userMap.values.toList()
                }
            } catch (e: Exception) {
                contacts = emptyList()
            }
            isLoading = false
        }
    }

    LaunchedEffect(isBotConnected) {
        if (isBotConnected) {
            loadChats()
            loadContacts()
        }
    }

    // ==================== DIALOGS ====================

    // Token dialog
    if (showTokenDialog) {
        AlertDialog(
            onDismissRequest = { showTokenDialog = false },
            title = { Text("Telegram Bot Token", fontFamily = FontFamily.Serif, color = telegramBlue) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Enter your Bot Token from @BotFather", color = YinTextSecondary, fontSize = 12.sp)
                    OutlinedTextField(
                        value = tokenInput,
                        onValueChange = { tokenInput = it },
                        placeholder = { Text("123456:ABC-DEF...") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = telegramBlue)
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    botToken = tokenInput
                    prefs.telegramApiKey = tokenInput
                    showTokenDialog = false
                    connectBot()
                }, colors = ButtonDefaults.buttonColors(containerColor = telegramBlue)) {
                    Text("Connect", color = Color.White)
                }
            },
            dismissButton = { TextButton(onClick = { showTokenDialog = false }) { Text("Cancel", color = telegramBlue) } },
            containerColor = if (isDark) Color(0xFF14131A) else Color(0xFFF7F4EE)
        )
    }

    // Send message dialog
    if (showSendDialog) {
        AlertDialog(
            onDismissRequest = { showSendDialog = false },
            title = { Text("Send Message", fontFamily = FontFamily.Serif, color = telegramBlue) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (sendTarget != null) {
                        Text("To: ${selectedChat?.title ?: "Chat $sendTarget"}", color = YinTextSecondary, fontSize = 12.sp)
                    }
                    OutlinedTextField(
                        value = messageText,
                        onValueChange = { messageText = it },
                        label = { Text("Message") },
                        modifier = Modifier.fillMaxWidth(),
                        maxLines = 4,
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = telegramBlue)
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    val target = sendTarget ?: selectedChat?.id ?: return@Button
                    scope.launch(Dispatchers.IO) {
                        val result = TelegramApiService.sendMessage(botToken, target, messageText)
                        statusMessage = if (result.contains("\"ok\":true")) "Message sent!" else "Failed to send"
                        showStatus = true
                    }
                    messageText = ""
                    showSendDialog = false
                }, colors = ButtonDefaults.buttonColors(containerColor = telegramBlue)) {
                    Text("Send", color = Color.White)
                }
            },
            dismissButton = { TextButton(onClick = { showSendDialog = false }) { Text("Cancel", color = telegramBlue) } },
            containerColor = if (isDark) Color(0xFF14131A) else Color(0xFFF7F4EE)
        )
    }

    // Status toast
    LaunchedEffect(showStatus) {
        if (showStatus) {
            Toast.makeText(context, statusMessage, Toast.LENGTH_SHORT).show()
            showStatus = false
        }
    }

    // ==================== MAIN UI ====================
    Column(modifier = Modifier.fillMaxSize().background(if (isDark) Color(0xFF070709) else Color(0xFFF1F0EC))) {
        // Header
        Surface(color = if (isDark) YinBlack else YangWhite) {
            Column(modifier = Modifier.statusBarsPadding()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onMenuClick) {
                        Icon(Icons.Default.Menu, "Menu", tint = if (isDark) Color.White else Color.Black)
                    }
                    Icon(Icons.Default.Send, null, tint = telegramBlue, modifier = Modifier.size(24.dp))
                    Spacer(Modifier.width(8.dp))
                    Column {
                        Text("TELEGRAM HUB", fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold,
                            color = telegramBlue, style = MaterialTheme.typography.titleMedium)
                        if (isBotConnected) {
                            Text("@$botUsername", color = YinTextSecondary, fontSize = 11.sp)
                        }
                    }
                    Spacer(Modifier.weight(1f))
                    // Connection indicator
                    Surface(
                        color = if (isBotConnected) Color(0xFF4CAF50).copy(alpha = 0.15f) else Color.Gray.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(if (isBotConnected) Color(0xFF4CAF50) else Color.Gray))
                            Spacer(Modifier.width(4.dp))
                            Text(if (isBotConnected) "Connected" else "Offline", fontSize = 10.sp, color = YinTextSecondary)
                        }
                    }
                    IconButton(onClick = {
                        showTokenDialog = true
                    }) {
                        Icon(Icons.Default.Key, "Token", tint = if (isBotConnected) Color(0xFF4CAF50) else Color.Gray, modifier = Modifier.size(20.dp))
                    }
                }

                // Tab row
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 8.dp, vertical = 2.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    listOf(
                        TelegramTab.CHATS to "Chats",
                        TelegramTab.CONTACTS to "Contacts",
                        TelegramTab.BOTS to "Bots",
                        TelegramTab.SETTINGS to "Webhook",
                        TelegramTab.AGENT to "🤖 AI Agent"
                    ).forEach { (tab, label) ->
                        FilterChip(
                            selected = activeTab == tab,
                            onClick = { activeTab = tab },
                            label = { Text(label, fontSize = 11.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = telegramBlue.copy(alpha = 0.2f),
                                selectedLabelColor = telegramBlue
                            )
                        )
                    }
                }
            }
        }

        // Loading
        if (isLoading) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), color = telegramBlue)
        }

        // Content
        Box(modifier = Modifier.fillMaxSize()) {
            when (activeTab) {
                TelegramTab.CHATS -> {
                    if (!isBotConnected) {
                        NotConnectedView("Connect a bot token to view chats", telegramBlue, { showTokenDialog = true })
                    } else if (chats.isEmpty() && !isLoading) {
                        EmptyView("No chats yet", "Send a message to your bot to start")
                    } else {
                        LazyColumn(
                            contentPadding = PaddingValues(12.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            item {
                                Row(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("${chats.size} chats", color = YinTextSecondary, fontSize = 12.sp)
                                    Button(onClick = {
                                        sendTarget = null; messageText = ""
                                        showSendDialog = true
                                    }, colors = ButtonDefaults.buttonColors(containerColor = telegramBlue), modifier = Modifier.height(32.dp)) {
                                        Icon(Icons.Default.Send, null, modifier = Modifier.size(14.dp), tint = Color.White)
                                        Text("New", color = Color.White, fontSize = 12.sp)
                                    }
                                }
                            }
                            items(chats) { chat ->
                                ChatCard(
                                    chat = chat,
                                    isSelected = selectedChat == chat,
                                    onClick = {
                                        selectedChat = if (selectedChat == chat) null else chat
                                    },
                                    onSendMessage = {
                                        sendTarget = chat.id; messageText = ""
                                        showSendDialog = true
                                    },
                                    telegramBlue = telegramBlue
                                )
                            }
                        }
                    }
                }

                TelegramTab.CONTACTS -> {
                    if (contacts.isEmpty()) {
                        EmptyView("No contacts", "Users will appear when they message your bot")
                    } else {
                        LazyColumn(contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("${contacts.size} contacts", color = YinTextSecondary, fontSize = 12.sp, modifier = Modifier.padding(bottom = 8.dp))
                            items(contacts) { contact ->
                                ContactCard(contact, telegramBlue) {
                                    sendTarget = contact.userId; messageText = ""
                                    showSendDialog = true
                                }
                            }
                        }
                    }
                }

                TelegramTab.BOTS -> {
                    BotManagementPanel(botToken, isBotConnected, telegramBlue)
                }

                TelegramTab.SETTINGS -> {
                    WebhookSettingsPanel(botToken, isBotConnected, telegramBlue)
                }

                TelegramTab.AGENT -> {
                    TelegramAgentPanel(botToken, isBotConnected, telegramBlue)
                }
            }
        }
    }
}

// ==================== COMPONENTS ====================

@Composable
private fun NotConnectedView(message: String, color: Color, onConnect: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(Icons.Default.CloudOff, null, modifier = Modifier.size(64.dp), tint = Color.Gray.copy(alpha = 0.4f))
        Spacer(Modifier.height(12.dp))
        Text(message, color = Color.Gray, fontSize = 14.sp)
        Spacer(Modifier.height(8.dp))
        Button(onClick = onConnect, colors = ButtonDefaults.buttonColors(containerColor = color)) {
            Text("Connect Bot", color = Color.White)
        }
    }
}

@Composable
private fun EmptyView(title: String, subtitle: String) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(Icons.Default.Inbox, null, modifier = Modifier.size(64.dp), tint = Color.Gray.copy(alpha = 0.4f))
        Spacer(Modifier.height(12.dp))
        Text(title, color = Color.Gray, fontSize = 16.sp)
        Text(subtitle, color = Color.Gray.copy(alpha = 0.6f), fontSize = 12.sp)
    }
}

@Composable
private fun ChatCard(
    chat: TelegramChat,
    isSelected: Boolean,
    onClick: () -> Unit,
    onSendMessage: () -> Unit,
    telegramBlue: Color
) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) telegramBlue.copy(alpha = 0.08f) else YinCardBg
        ),
        border = if (isSelected) BorderStroke(1.dp, telegramBlue) else null,
        shape = RoundedCornerShape(10.dp)
    ) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            // Avatar
            Box(
                modifier = Modifier.size(44.dp).clip(CircleShape).background(
                    Brush.linearGradient(listOf(telegramBlue, Color(0xFF00B0FF)))
                ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    chat.title.take(1).uppercase(),
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
            }

            Spacer(Modifier.width(10.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(chat.title, color = YinText, fontWeight = FontWeight.Bold, fontSize = 14.sp, modifier = Modifier.weight(1f))
                    Text(chat.lastMessageTime, color = YinTextSecondary, fontSize = 10.sp)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        chat.lastMessage.ifEmpty { "No messages yet" },
                        color = YinTextSecondary,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    if (chat.unreadCount > 0) {
                        Surface(color = telegramBlue, shape = CircleShape) {
                            Text("${chat.unreadCount}", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                        }
                    }
                }
                Surface(color = telegramBlue.copy(alpha = 0.1f), shape = RoundedCornerShape(4.dp)) {
                    Text(chat.type.uppercase(), color = telegramBlue, fontSize = 9.sp, modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp))
                }
            }

            IconButton(onClick = onSendMessage, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Default.Send, "Send", tint = telegramBlue, modifier = Modifier.size(18.dp))
            }
        }
    }
}

@Composable
private fun ContactCard(contact: TelegramContact, telegramBlue: Color, onMessage: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = YinCardBg),
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(modifier = Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.size(40.dp).clip(CircleShape).background(telegramBlue.copy(alpha = 0.2f)), contentAlignment = Alignment.Center) {
                Text(contact.name.take(1).uppercase(), color = telegramBlue, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(contact.name, color = YinText, fontWeight = FontWeight.Medium)
                Text("ID: ${contact.userId}", color = YinTextSecondary, fontSize = 11.sp)
            }
            IconButton(onClick = onMessage, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Default.Send, "Message", tint = telegramBlue, modifier = Modifier.size(18.dp))
            }
        }
    }
}

// ==================== BOT MANAGEMENT ====================

@Composable
private fun BotManagementPanel(botToken: String, isConnected: Boolean, telegramBlue: Color) {
    val context = LocalContext.current
    var newBotCommand by remember { mutableStateOf("") }
    var outputText by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    Column(modifier = Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("🤖 Bot Management", color = telegramBlue, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Serif, fontSize = 18.sp)

        if (!isConnected) {
            Text("Connect a bot token first", color = Color.Gray, fontSize = 14.sp, modifier = Modifier.padding(32.dp))
            return
        }

        // Bot info
        Card(colors = CardDefaults.cardColors(containerColor = YinCardBg)) {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = {
                        scope.launch(Dispatchers.IO) {
                            outputText = TelegramApiService.getMe(botToken)
                        }
                    }, colors = ButtonDefaults.buttonColors(containerColor = telegramBlue), modifier = Modifier.weight(1f)) {
                        Text("Get Bot Info", color = Color.White, fontSize = 12.sp)
                    }
                    Button(onClick = {
                        scope.launch(Dispatchers.IO) {
                            outputText = TelegramApiService.getUpdates(botToken)
                        }
                    }, colors = ButtonDefaults.buttonColors(containerColor = telegramBlue), modifier = Modifier.weight(1f)) {
                        Text("Get Updates", color = Color.White, fontSize = 12.sp)
                    }
                }
            }
        }

        // Output
        if (outputText.isNotBlank()) {
            Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF0F0F12))) {
                Text(
                    outputText.take(1000),
                    color = Color(0xFF8CBE91),
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.padding(8.dp).verticalScroll(rememberScrollState()).heightIn(max = 200.dp)
                )
            }
        }

        // Command buttons
        Text("Quick Actions", color = YinTextSecondary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf(
                "getMe" to "Bot Info",
                "getUpdates" to "Updates",
                "getWebhookInfo" to "Webhook"
            ).forEach { (method, label) ->
                item {
                    AssistChip(
                        onClick = {
                            scope.launch(Dispatchers.IO) {
                                outputText = when (method) {
                                    "getMe" -> TelegramApiService.getMe(botToken)
                                    "getUpdates" -> TelegramApiService.getUpdates(botToken)
                                    "getWebhookInfo" -> TelegramApiService.getWebhookInfo(botToken)
                                    else -> ""
                                }
                            }
                        },
                        label = { Text(label, fontSize = 11.sp) },
                        colors = AssistChipDefaults.assistChipColors(containerColor = telegramBlue.copy(alpha = 0.1f))
                    )
                }
            }
        }
    }
}

// ==================== WEBHOOK SETTINGS ====================

@Composable
private fun WebhookSettingsPanel(botToken: String, isConnected: Boolean, telegramBlue: Color) {
    val context = LocalContext.current
    var webhookUrl by remember { mutableStateOf("") }
    var outputText by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    Column(modifier = Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("🔗 Webhook Settings", color = telegramBlue, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Serif, fontSize = 18.sp)

        if (!isConnected) {
            Text("Connect a bot token first", color = Color.Gray, fontSize = 14.sp, modifier = Modifier.padding(32.dp))
            return
        }

        Card(colors = CardDefaults.cardColors(containerColor = YinCardBg)) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Set Webhook URL", color = YinTextSecondary, fontSize = 12.sp)
                OutlinedTextField(
                    value = webhookUrl,
                    onValueChange = { webhookUrl = it },
                    placeholder = { Text("https://your-server.com/bot", fontSize = 12.sp) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = telegramBlue)
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = {
                        scope.launch(Dispatchers.IO) {
                            outputText = TelegramApiService.setWebhook(botToken, webhookUrl)
                        }
                    }, colors = ButtonDefaults.buttonColors(containerColor = telegramBlue), modifier = Modifier.weight(1f)) {
                        Text("Set Webhook", color = Color.White, fontSize = 12.sp)
                    }
                    Button(onClick = {
                        scope.launch(Dispatchers.IO) {
                            outputText = TelegramApiService.deleteWebhook(botToken)
                        }
                    }, colors = ButtonDefaults.buttonColors(containerColor = ZenRed), modifier = Modifier.weight(1f)) {
                        Text("Delete", color = Color.White, fontSize = 12.sp)
                    }
                }
            }
        }

        if (outputText.isNotBlank()) {
            Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF0F0F12))) {
                Text(
                    outputText,
                    color = Color(0xFF8CBE91),
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.padding(8.dp).heightIn(max = 150.dp)
                )
            }
        }
    }
}

// ==================== AI AGENT PANEL ====================

@Composable
private fun TelegramAgentPanel(botToken: String, isConnected: Boolean, telegramBlue: Color) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var commandInput by remember { mutableStateOf("") }
    var outputText by remember { mutableStateOf(
        "🤖 Telegram AI Agent ready.\n\n" +
        "Commands:\n" +
        "- get me\n" +
        "- send <chat_id> <text>\n" +
        "- updates\n" +
        "- webhook info\n" +
        "- webhook set <url>\n" +
        "- webhook delete\n" +
        "- delete <chat_id> <msg_id>\n" +
        "- pin <chat_id> <msg_id>\n" +
        "- forward <to_chat> <from_chat> <msg_id>"
    ) }

    Column(modifier = Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("🤖 AI Agent Control", color = telegramBlue, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Serif, fontSize = 18.sp)
        Text("Natural language or direct API commands", color = YinTextSecondary, fontSize = 12.sp)

        if (!isConnected) {
            Text("Connect a bot token first", color = Color.Gray, modifier = Modifier.padding(32.dp))
            return
        }

        Card(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF0F0F12))
        ) {
            LazyColumn(modifier = Modifier.fillMaxSize().padding(8.dp)) {
                item {
                    Text(outputText, color = Color(0xFF8CBE91), fontSize = 12.sp, fontFamily = FontFamily.Monospace)
                }
            }
        }

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = commandInput,
                onValueChange = { commandInput = it },
                modifier = Modifier.weight(1f).height(48.dp),
                placeholder = { Text("Agent command...", fontSize = 12.sp) },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = telegramBlue),
                shape = RoundedCornerShape(10.dp)
            )
            Button(
                onClick = {
                    if (commandInput.isBlank()) return@Button
                    val cmd = commandInput.lowercase()
                    outputText = "> $commandInput\n"
                    scope.launch(Dispatchers.IO) {
                        val result = when {
                            cmd == "get me" -> TelegramApiService.getMe(botToken)
                            cmd.startsWith("send ") -> {
                                val parts = commandInput.removePrefix("send ").split(" ", limit = 2)
                                if (parts.size == 2) {
                                    TelegramApiService.sendMessage(botToken, parts[0].toLongOrNull() ?: 0, parts[1])
                                } else "Usage: send <chat_id> <text>"
                            }
                            cmd == "updates" -> TelegramApiService.getUpdates(botToken)
                            cmd == "webhook info" -> TelegramApiService.getWebhookInfo(botToken)
                            cmd.startsWith("webhook set ") -> {
                                TelegramApiService.setWebhook(botToken, commandInput.removePrefix("webhook set "))
                            }
                            cmd == "webhook delete" -> TelegramApiService.deleteWebhook(botToken)
                            cmd.startsWith("delete ") -> {
                                val parts = commandInput.removePrefix("delete ").split(" ")
                                if (parts.size == 2) {
                                    TelegramApiService.deleteMessage(botToken, parts[0].toLongOrNull() ?: 0, parts[1].toLongOrNull() ?: 0)
                                } else "Usage: delete <chat_id> <message_id>"
                            }
                            cmd.startsWith("pin ") -> {
                                val parts = commandInput.removePrefix("pin ").split(" ")
                                if (parts.size == 2) {
                                    TelegramApiService.pinMessage(botToken, parts[0].toLongOrNull() ?: 0, parts[1].toLongOrNull() ?: 0)
                                } else "Usage: pin <chat_id> <message_id>"
                            }
                            cmd.startsWith("forward ") -> {
                                val parts = commandInput.removePrefix("forward ").split(" ")
                                if (parts.size == 3) {
                                    TelegramApiService.forwardMessage(botToken, parts[0].toLongOrNull() ?: 0, parts[1].toLongOrNull() ?: 0, parts[2].toLongOrNull() ?: 0)
                                } else "Usage: forward <to_chat> <from_chat> <message_id>"
                            }
                            else -> "Unknown. Try: get me, send, updates, webhook info, delete, pin, forward"
                        }
                        outputText += result
                    }
                    commandInput = ""
                },
                colors = ButtonDefaults.buttonColors(containerColor = telegramBlue)
            ) {
                Icon(Icons.Default.Terminal, null, modifier = Modifier.size(18.dp), tint = Color.White)
            }
        }
    }
}

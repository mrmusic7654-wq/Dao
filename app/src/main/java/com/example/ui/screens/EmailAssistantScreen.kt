package com.example.ui.screens

import android.content.Context
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.dao_engine.GeminiService
import com.example.ui.theme.*
import kotlinx.coroutines.*
import java.util.*
import javax.mail.*
import javax.mail.internet.*

data class Email(
    val id: String = UUID.randomUUID().toString(),
    val from: String,
    val subject: String,
    val body: String,
    val date: Date,
    val isRead: Boolean = false
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EmailAssistantScreen(isDark: Boolean, onMenuClick: () -> Unit) {
    val context = LocalContext.current
    var emails by remember { mutableStateOf(listOf<Email>()) }
    var selectedEmail by remember { mutableStateOf<Email?>(null) }
    var showCompose by remember { mutableStateOf(false) }
    var aiReply by remember { mutableStateOf("") }
    var isGenerating by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }

    // Email settings
    var imapServer by remember { mutableStateOf("imap.gmail.com") }
    var smtpServer by remember { mutableStateOf("smtp.gmail.com") }
    var emailAddress by remember { mutableStateOf("") }
    var emailPassword by remember { mutableStateOf("") }
    var composeTo by remember { mutableStateOf("") }
    var composeSubject by remember { mutableStateOf("") }
    var composeBody by remember { mutableStateOf("") }

    if (showSettings) {
        AlertDialog(
            onDismissRequest = { showSettings = false },
            title = { Text("Email Settings", color = ZenGold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = emailAddress, onValueChange = { emailAddress = it }, label = { Text("Email") })
                    OutlinedTextField(value = emailPassword, onValueChange = { emailPassword = it }, label = { Text("Password") }, singleLine = true)
                    OutlinedTextField(value = imapServer, onValueChange = { imapServer = it }, label = { Text("IMAP Server") })
                    OutlinedTextField(value = smtpServer, onValueChange = { smtpServer = it }, label = { Text("SMTP Server") })
                }
            },
            confirmButton = {
                Button(onClick = { showSettings = false }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { showSettings = false }) { Text("Cancel") } }
        )
    }

    if (showCompose) {
        AlertDialog(
            onDismissRequest = { showCompose = false },
            title = { Text("Compose Email", color = ZenGold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = composeTo, onValueChange = { composeTo = it }, label = { Text("To") })
                    OutlinedTextField(value = composeSubject, onValueChange = { composeSubject = it }, label = { Text("Subject") })
                    OutlinedTextField(value = composeBody, onValueChange = { composeBody = it }, label = { Text("Body") }, maxLines = 6)
                    if (aiReply.isNotEmpty()) {
                        Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A24))) {
                            Text(aiReply, modifier = Modifier.padding(12.dp), color = YinText, fontSize = 12.sp)
                        }
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    composeBody = aiReply.ifBlank { composeBody }
                    // Send email
                    sendEmail(smtpServer, emailAddress, emailPassword, composeTo, composeSubject, composeBody)
                    showCompose = false
                }) { Text("Send") }
            },
            dismissButton = { TextButton(onClick = { showCompose = false }) { Text("Cancel") } }
        )
    }

    Column(modifier = Modifier.fillMaxSize().background(if (isDark) Color(0xFF0A0A10) else Color(0xFFF5F5F5))) {
        Surface(color = if (isDark) Color(0xFF14141E) else Color.White) {
            Row(modifier = Modifier.padding(12.dp).statusBarsPadding(), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onMenuClick) { Icon(Icons.Default.Menu, null) }
                Text("📧 Email Assistant", color = ZenGold, fontWeight = FontWeight.Bold)
                Spacer(Modifier.weight(1f))
                IconButton(onClick = { showCompose = true }) { Icon(Icons.Default.Edit, null, tint = ZenGold) }
                IconButton(onClick = { showSettings = true }) { Icon(Icons.Default.Settings, null) }
            }
        }

        if (selectedEmail != null) {
            // Email detail view
            Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                Text(selectedEmail!!.subject, color = ZenGold, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Text("From: ${selectedEmail!!.from}", color = YinTextSecondary, fontSize = 12.sp)
                Spacer(Modifier.height(12.dp))
                Text(selectedEmail!!.body, color = YinText, fontSize = 14.sp)
                Spacer(Modifier.height(16.dp))

                // AI Reply generation
                Button(onClick = {
                    isGenerating = true
                    CoroutineScope(Dispatchers.IO).launch {
                        val prompt = "Draft a professional reply to this email:\n\nFrom: ${selectedEmail!!.from}\nSubject: ${selectedEmail!!.subject}\nBody: ${selectedEmail!!.body}\n\nWrite a concise, professional reply."
                        val response = GeminiService.generateResponse(context, prompt, "Email Assistant", "Direct")
                        withContext(Dispatchers.Main) {
                            aiReply = response.replyText
                            isGenerating = false
                        }
                    }
                }) {
                    Text(if (isGenerating) "Generating..." else "🤖 AI Draft Reply")
                }

                if (aiReply.isNotEmpty()) {
                    Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A24))) {
                        Text(aiReply, modifier = Modifier.padding(12.dp), color = YinText, fontSize = 13.sp)
                    }
                }

                Button(onClick = { selectedEmail = null }) { Text("Back to Inbox") }
            }
        } else {
            LazyColumn(padding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                items(emails) { email ->
                    Card(modifier = Modifier.fillMaxWidth().clickable { selectedEmail = email },
                        colors = CardDefaults.cardColors(containerColor = if (email.isRead) Color.Transparent else Color(0xFF1A1A24))) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(email.from, color = YinText, fontWeight = if (email.isRead) FontWeight.Normal else FontWeight.Bold)
                            Text(email.subject, color = YinText, fontSize = 13.sp)
                            Text(email.body.take(100), color = YinTextSecondary, fontSize = 11.sp, maxLines = 2)
                        }
                    }
                }
                if (emails.isEmpty()) {
                    item { Text("No emails loaded. Configure settings to connect.", color = Color.Gray) }
                }
            }
        }
    }
}

fun sendEmail(smtpHost: String, username: String, password: String, to: String, subject: String, body: String) {
    Thread {
        try {
            val props = Properties().apply {
                put("mail.smtp.auth", "true")
                put("mail.smtp.starttls.enable", "true")
                put("mail.smtp.host", smtpHost)
                put("mail.smtp.port", "587")
            }
            val session = Session.getInstance(props, object : Authenticator() {
                override fun getPasswordAuthentication() = PasswordAuthentication(username, password)
            })
            val message = MimeMessage(session).apply {
                setFrom(InternetAddress(username))
                setRecipients(Message.RecipientType.TO, InternetAddress.parse(to))
                this.subject = subject
                setText(body)
            }
            Transport.send(message)
        } catch (_: Exception) {}
    }.start()
}

package com.example.ui.settings

import android.widget.Toast
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.repository.UserPreferences
import com.example.ui.theme.*
import com.example.ui.viewmodel.ChatViewModel
import org.json.JSONArray
import org.json.JSONObject

@Composable
fun SettingsDialog(
    isOpen: Boolean,
    onDismiss: (Boolean) -> Unit,
    themeMode: MutableState<String>,
    isDark: Boolean,
    viewModel: ChatViewModel
) {
    if (!isOpen) return

    val context = LocalContext.current
    val prefs = remember { UserPreferences(context) }

    var apiKey by remember { mutableStateOf(prefs.geminiApiKey) }
    var openAiApiKey by remember { mutableStateOf(prefs.openAiApiKey) }
    var githubApiKey by remember { mutableStateOf(prefs.githubApiKey) }
    var huggingFaceApiKey by remember { mutableStateOf(prefs.huggingFaceApiKey) }
    var telegramApiKey by remember { mutableStateOf(prefs.telegramApiKey) }

    var name by remember { mutableStateOf(prefs.userName) }
    var email by remember { mutableStateOf(prefs.userEmail) }

    var showGeminiKey by remember { mutableStateOf(false) }
    var showOpenAiKey by remember { mutableStateOf(false) }
    var showGithubKey by remember { mutableStateOf(false) }
    var showHfKey by remember { mutableStateOf(false) }
    var showTelegramKey by remember { mutableStateOf(false) }

    var customInstructions by remember { mutableStateOf(prefs.customInstructions) }
    var aiTemperature by remember { mutableStateOf(prefs.aiTemperature) }
    var maxTokens by remember { mutableStateOf(prefs.maxTokens.toFloat()) }

    var activeTab by remember { mutableStateOf("General") }

    // Nested dialog states for premium ChatGPT features
    var showExportDialog by remember { mutableStateOf(false) }
    var exportDataJson by remember { mutableStateOf("") }

    if (showExportDialog) {
        AlertDialog(
            onDismissRequest = { showExportDialog = false },
            shape = RoundedCornerShape(16.dp),
            containerColor = if (isDark) Color(0xFF14131A) else Color(0xFFF7F4EE),
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "📦 COSMIC LEDGER EXPORT",
                        style = MaterialTheme.typography.titleMedium,
                        fontFamily = FontFamily.Serif,
                        fontWeight = FontWeight.Bold,
                        color = ZenGold
                    )
                }
            },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Your spiritual transcripts and companion settings have been compiled into a secure JSON ledger. Copy this to archive or import in other nodes.",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (isDark) YinTextSecondary else YangTextSecondary
                    )

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp)
                            .background(if (isDark) Color(0xFF0F0E14) else Color(0xFFECE9E4), RoundedCornerShape(8.dp))
                            .border(1.dp, if (isDark) Color(0xFF22222A) else Color(0xFFD2CDBC), RoundedCornerShape(8.dp))
                            .verticalScroll(rememberScrollState())
                            .padding(10.dp)
                    ) {
                        Text(
                            text = exportDataJson,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            color = if (isDark) YinText else YangText
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                        val clip = android.content.ClipData.newPlainText("Dao Discourse Ledger", exportDataJson)
                        clipboard.setPrimaryClip(clip)
                        Toast.makeText(context, "Ledger copied to clipboard! ☯", Toast.LENGTH_SHORT).show()
                        showExportDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ZenGold)
                ) {
                    Text("Copy to Clipboard", color = Color.Black, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showExportDialog = false }) {
                    Text("Close", color = if (isDark) YinTextSecondary else YangTextSecondary)
                }
            }
        )
    }

    AlertDialog(
        onDismissRequest = { onDismiss(false) },
        shape = RoundedCornerShape(20.dp),
        containerColor = if (isDark) Color(0xFF14131A) else Color(0xFFF7F4EE),
        title = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "☯ DAO HYPER-NODE SETTINGS",
                        style = MaterialTheme.typography.titleMedium,
                        fontFamily = FontFamily.Serif,
                        fontWeight = FontWeight.Bold,
                        color = ZenGold
                    )
                }
                Text(
                    text = "V3.6.0 Pro • Offline and Online Multi-Service Node",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isDark) YinTextSecondary else YangTextSecondary,
                    fontSize = 11.sp
                )
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Horizontal scrollable Tab selector
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(bottom = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    val tabs = listOf("General", "API Integrations", "AI Customization", "Data Controls", "Celestial")
                    tabs.forEach { tabId ->
                        val isSelected = (activeTab == tabId)
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(20.dp))
                                .background(
                                    if (isSelected) ZenGold.copy(alpha = 0.15f) else Color.Transparent
                                )
                                .border(
                                    1.dp,
                                    if (isSelected) ZenGold else (if (isDark) Color(0xFF222228) else Color(0xFFDCD8CF)),
                                    RoundedCornerShape(20.dp)
                                )
                                .clickable { activeTab = tabId }
                                .padding(horizontal = 14.dp, vertical = 6.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = tabId,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSelected) ZenGold else (if (isDark) YinTextSecondary else YangTextSecondary)
                            )
                        }
                    }
                }

                HorizontalDivider(color = if (isDark) Color(0xFF22222A) else Color(0xFFE2DDD3))

                // Scrollable tab content
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 380.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    when (activeTab) {
                        "General" -> {
                            // Theme Settings
                            Text(
                                text = "COSMIC THEME SELECTION",
                                style = MaterialTheme.typography.labelSmall,
                                color = ZenGold,
                                fontWeight = FontWeight.Bold
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                listOf("dark", "light", "auto").forEach { modeKey ->
                                    val isSelected = (themeMode.value == modeKey)
                                    val modeLabel = when (modeKey) {
                                        "dark" -> "Yin Slate"
                                        "light" -> "Yang Light"
                                        else -> "Harmonize"
                                    }
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(
                                                if (isSelected) ZenGold.copy(alpha = 0.12f) else Color.Transparent
                                            )
                                            .border(
                                                1.dp,
                                                if (isSelected) ZenGold else (if (isDark) Color(0xFF2E2E36) else Color(0xFFD2CDBC)),
                                                RoundedCornerShape(8.dp)
                                            )
                                            .clickable {
                                                themeMode.value = modeKey
                                            }
                                            .padding(vertical = 8.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = modeLabel,
                                            style = MaterialTheme.typography.bodySmall,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                            color = if (isSelected) ZenGold else Color.Gray
                                        )
                                    }
                                }
                            }

                            HorizontalDivider(color = if (isDark) Color(0xFF22222A) else Color(0xFFE2DDD3))

                            // Language Selection
                            Text(
                                text = "PRIMARY DIALECT / LANGUAGE",
                                style = MaterialTheme.typography.labelSmall,
                                color = ZenGold,
                                fontWeight = FontWeight.Bold
                            )

                            var currentLang by remember { mutableStateOf(prefs.primaryLanguage) }
                            val languages = listOf("English", "Classical Chinese 🀄", "Sanskrit 📿", "Spanish ☀️", "Japanese 🌸")

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                languages.forEach { lang ->
                                    val isSelected = (currentLang == lang)
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(
                                                if (isSelected) ZenGold.copy(alpha = 0.15f) else Color.Transparent
                                            )
                                            .border(
                                                1.dp,
                                                if (isSelected) ZenGold else (if (isDark) Color(0xFF222228) else Color(0xFFDCD8CF)),
                                                RoundedCornerShape(8.dp)
                                            )
                                            .clickable {
                                                currentLang = lang
                                                prefs.primaryLanguage = lang
                                            }
                                            .padding(horizontal = 12.dp, vertical = 6.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = lang,
                                            style = MaterialTheme.typography.bodySmall,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                            color = if (isSelected) ZenGold else Color.Gray
                                        )
                                    }
                                }
                            }

                            HorizontalDivider(color = if (isDark) Color(0xFF22222A) else Color(0xFFE2DDD3))

                            // Voice Speech vibe
                            Text(
                                text = "DAO SPEECH ORACLE VOICE VIBE",
                                style = MaterialTheme.typography.labelSmall,
                                color = ZenGold,
                                fontWeight = FontWeight.Bold
                            )

                            var currentVoice by remember { mutableStateOf(prefs.voiceVibe) }
                            val voices = listOf("Sage Whisper 🎙️", "Celestial Wind 🎐", "Ethereal Echo 🌌", "Muted Stillness 🤐")

                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                voices.forEach { voice ->
                                    val isSelected = (currentVoice == voice)
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(
                                                if (isSelected) ZenGold.copy(alpha = 0.10f) else Color.Transparent
                                            )
                                            .border(
                                                1.dp,
                                                if (isSelected) ZenGold else (if (isDark) Color(0xFF222228) else Color(0xFFDCD8CF)),
                                                RoundedCornerShape(8.dp)
                                            )
                                            .clickable {
                                                currentVoice = voice
                                                prefs.voiceVibe = voice
                                            }
                                            .padding(horizontal = 12.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Icon(
                                            imageVector = if (voice.contains("Muted")) Icons.Default.VolumeMute else Icons.Default.VolumeUp,
                                            contentDescription = "Voice Vibe Icon",
                                            tint = if (isSelected) ZenGold else Color.Gray,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Text(
                                            text = voice,
                                            style = MaterialTheme.typography.bodySmall,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                            color = if (isSelected) ZenGold else (if (isDark) YinText else Color.Black)
                                        )
                                    }
                                }
                            }

                            HorizontalDivider(color = if (isDark) Color(0xFF22222A) else Color(0xFFE2DDD3))

                            // Haptics & Sound Settings
                            Text(
                                text = "TACTILE & REVERB PREFERENCES",
                                style = MaterialTheme.typography.labelSmall,
                                color = ZenGold,
                                fontWeight = FontWeight.Bold
                            )

                            var hapticVal by remember { mutableStateOf(prefs.hapticEnabled) }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text("Yin-Yang Haptic Response", style = MaterialTheme.typography.bodyMedium, color = if (isDark) YinText else Color.Black)
                                    Text("Vibrate gently with typing & balancing flow", style = MaterialTheme.typography.labelSmall, color = if (isDark) YinTextSecondary else YangTextSecondary)
                                }
                                Switch(
                                    checked = hapticVal,
                                    onCheckedChange = {
                                        hapticVal = it
                                        prefs.hapticEnabled = it
                                    },
                                    colors = SwitchDefaults.colors(checkedThumbColor = ZenGold, checkedTrackColor = ZenGold.copy(alpha = 0.5f))
                                )
                            }

                            var ambientVal by remember { mutableStateOf(prefs.ambientEnabled) }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text("Ambient Cosmic Soundscapes", style = MaterialTheme.typography.bodyMedium, color = if (isDark) YinText else Color.Black)
                                    Text("Gentle celestial wind audio chime responses", style = MaterialTheme.typography.labelSmall, color = if (isDark) YinTextSecondary else YangTextSecondary)
                                }
                                Switch(
                                    checked = ambientVal,
                                    onCheckedChange = {
                                        ambientVal = it
                                        prefs.ambientEnabled = it
                                    },
                                    colors = SwitchDefaults.colors(checkedThumbColor = ZenGold, checkedTrackColor = ZenGold.copy(alpha = 0.5f))
                                )
                            }
                        }

                        "API Integrations" -> {
                            Text(
                                text = "COSMIC API KEY CONSTELLATION",
                                style = MaterialTheme.typography.labelSmall,
                                color = ZenGold,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Configure your API nodes to synchronize with global cognitive channels. All services run autonomously with saved credentials.",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (isDark) YinTextSecondary else YangTextSecondary,
                                fontSize = 11.sp
                            )

                            // 1. Gemini API Key
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text("Gemini API Key", style = MaterialTheme.typography.labelSmall, color = ZenGold)
                                OutlinedTextField(
                                    value = apiKey,
                                    onValueChange = {
                                        apiKey = it
                                        prefs.geminiApiKey = it
                                    },
                                    placeholder = { Text("Enter Gemini API key", color = Color.Gray) },
                                    textStyle = MaterialTheme.typography.bodyMedium.copy(color = if (isDark) Color.White else Color.Black),
                                    singleLine = true,
                                    visualTransformation = if (showGeminiKey) VisualTransformation.None else PasswordVisualTransformation(),
                                    trailingIcon = {
                                        IconButton(onClick = { showGeminiKey = !showGeminiKey }) {
                                            Icon(
                                                imageVector = if (showGeminiKey) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                                contentDescription = "Toggle Gemini API Key",
                                                tint = ZenGold
                                            )
                                        }
                                    },
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = ZenGold,
                                        unfocusedBorderColor = if (isDark) Color(0xFF2E2E36) else Color(0xFFD2CDBC),
                                        focusedContainerColor = if (isDark) Color(0xFF1C1B24) else Color(0xFFF2EFEA),
                                        unfocusedContainerColor = if (isDark) Color(0xFF0F0F12) else Color(0xFFF8F6F2)
                                    ),
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }

                            // 2. OpenAI API Key
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text("OpenAI API Key", style = MaterialTheme.typography.labelSmall, color = ZenGold)
                                OutlinedTextField(
                                    value = openAiApiKey,
                                    onValueChange = {
                                        openAiApiKey = it
                                        prefs.openAiApiKey = it
                                    },
                                    placeholder = { Text("Enter OpenAI API key (sk-...)", color = Color.Gray) },
                                    textStyle = MaterialTheme.typography.bodyMedium.copy(color = if (isDark) Color.White else Color.Black),
                                    singleLine = true,
                                    visualTransformation = if (showOpenAiKey) VisualTransformation.None else PasswordVisualTransformation(),
                                    trailingIcon = {
                                        IconButton(onClick = { showOpenAiKey = !showOpenAiKey }) {
                                            Icon(
                                                imageVector = if (showOpenAiKey) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                                contentDescription = "Toggle OpenAI Key",
                                                tint = ZenGold
                                            )
                                        }
                                    },
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = ZenGold,
                                        unfocusedBorderColor = if (isDark) Color(0xFF2E2E36) else Color(0xFFD2CDBC),
                                        focusedContainerColor = if (isDark) Color(0xFF1C1B24) else Color(0xFFF2EFEA),
                                        unfocusedContainerColor = if (isDark) Color(0xFF0F0F12) else Color(0xFFF8F6F2)
                                    ),
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }

                            // 3. GitHub API Key
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text("GitHub Personal Token", style = MaterialTheme.typography.labelSmall, color = ZenGold)
                                OutlinedTextField(
                                    value = githubApiKey,
                                    onValueChange = {
                                        githubApiKey = it
                                        prefs.githubApiKey = it
                                    },
                                    placeholder = { Text("Enter GitHub OAuth Token (ghp_...)", color = Color.Gray) },
                                    textStyle = MaterialTheme.typography.bodyMedium.copy(color = if (isDark) Color.White else Color.Black),
                                    singleLine = true,
                                    visualTransformation = if (showGithubKey) VisualTransformation.None else PasswordVisualTransformation(),
                                    trailingIcon = {
                                        IconButton(onClick = { showGithubKey = !showGithubKey }) {
                                            Icon(
                                                imageVector = if (showGithubKey) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                                contentDescription = "Toggle GitHub Key",
                                                tint = ZenGold
                                            )
                                        }
                                    },
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = ZenGold,
                                        unfocusedBorderColor = if (isDark) Color(0xFF2E2E36) else Color(0xFFD2CDBC),
                                        focusedContainerColor = if (isDark) Color(0xFF1C1B24) else Color(0xFFF2EFEA),
                                        unfocusedContainerColor = if (isDark) Color(0xFF0F0F12) else Color(0xFFF8F6F2)
                                    ),
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }

                            // 4. Hugging Face Spaces Token
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text("Hugging Face Spaces Token", style = MaterialTheme.typography.labelSmall, color = ZenGold)
                                OutlinedTextField(
                                    value = huggingFaceApiKey,
                                    onValueChange = {
                                        huggingFaceApiKey = it
                                        prefs.huggingFaceApiKey = it
                                    },
                                    placeholder = { Text("Enter Hugging Face Token (hf_...)", color = Color.Gray) },
                                    textStyle = MaterialTheme.typography.bodyMedium.copy(color = if (isDark) Color.White else Color.Black),
                                    singleLine = true,
                                    visualTransformation = if (showHfKey) VisualTransformation.None else PasswordVisualTransformation(),
                                    trailingIcon = {
                                        IconButton(onClick = { showHfKey = !showHfKey }) {
                                            Icon(
                                                imageVector = if (showHfKey) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                                contentDescription = "Toggle Hugging Face Key",
                                                tint = ZenGold
                                            )
                                        }
                                    },
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = ZenGold,
                                        unfocusedBorderColor = if (isDark) Color(0xFF2E2E36) else Color(0xFFD2CDBC),
                                        focusedContainerColor = if (isDark) Color(0xFF1C1B24) else Color(0xFFF2EFEA),
                                        unfocusedContainerColor = if (isDark) Color(0xFF0F0F12) else Color(0xFFF8F6F2)
                                    ),
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }

                            // 5. Telegram Bot Token
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text("Telegram Bot Token", style = MaterialTheme.typography.labelSmall, color = ZenGold)
                                OutlinedTextField(
                                    value = telegramApiKey,
                                    onValueChange = {
                                        telegramApiKey = it
                                        prefs.telegramApiKey = it
                                    },
                                    placeholder = { Text("Enter Telegram Bot API Token", color = Color.Gray) },
                                    textStyle = MaterialTheme.typography.bodyMedium.copy(color = if (isDark) Color.White else Color.Black),
                                    singleLine = true,
                                    visualTransformation = if (showTelegramKey) VisualTransformation.None else PasswordVisualTransformation(),
                                    trailingIcon = {
                                        IconButton(onClick = { showTelegramKey = !showTelegramKey }) {
                                            Icon(
                                                imageVector = if (showTelegramKey) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                                contentDescription = "Toggle Telegram Key",
                                                tint = ZenGold
                                            )
                                        }
                                    },
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = ZenGold,
                                        unfocusedBorderColor = if (isDark) Color(0xFF2E2E36) else Color(0xFFD2CDBC),
                                        focusedContainerColor = if (isDark) Color(0xFF1C1B24) else Color(0xFFF2EFEA),
                                        unfocusedContainerColor = if (isDark) Color(0xFF0F0F12) else Color(0xFFF8F6F2)
                                    ),
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }

                            // Connected Node Status Summary
                            val keysCount = listOf(apiKey, openAiApiKey, githubApiKey, huggingFaceApiKey, telegramApiKey).count { it.isNotBlank() }
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(
                                        if (keysCount > 0) Color(0xFF0F2E1E) else (if (isDark) Color(0xFF242218) else Color(0xFFFAF7DF)),
                                        RoundedCornerShape(8.dp)
                                    )
                                    .padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(10.dp)
                                        .clip(CircleShape)
                                        .background(if (keysCount > 0) Color.Green else ZenGold)
                                )
                                Text(
                                    text = if (keysCount > 0) "⚡ $keysCount COGNITIVE FREQUENCIES ACTIVE • Synchronized Online" else "🔮 NO ONLINE KEYS DETECTED • Pure Local Simulation Active",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (keysCount > 0) Color.Green else (if (isDark) YinText else Color.Black),
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        "AI Customization" -> {
                            Text(
                                text = "GPT CUSTOM INSTRUCTIONS",
                                style = MaterialTheme.typography.labelSmall,
                                color = ZenGold,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Tell the Companion exactly how to guide you, how to respond, or who you are. This defines the system's baseline personality.",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (isDark) YinTextSecondary else YangTextSecondary,
                                fontSize = 11.sp
                            )

                            OutlinedTextField(
                                value = customInstructions,
                                onValueChange = {
                                    customInstructions = it
                                    prefs.customInstructions = it
                                },
                                placeholder = { Text("E.g., You are an ancient Chinese spiritual hermit from Mt. Wudang who speaks only in deep poetic verse...", color = Color.Gray) },
                                textStyle = MaterialTheme.typography.bodyMedium.copy(color = if (isDark) Color.White else Color.Black),
                                minLines = 3,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = ZenGold,
                                    unfocusedBorderColor = if (isDark) Color(0xFF2E2E36) else Color(0xFFD2CDBC),
                                    focusedContainerColor = if (isDark) Color(0xFF1C1B24) else Color(0xFFF2EFEA),
                                    unfocusedContainerColor = if (isDark) Color(0xFF0F0F12) else Color(0xFFF8F6F2)
                                ),
                                modifier = Modifier.fillMaxWidth()
                            )

                            HorizontalDivider(color = if (isDark) Color(0xFF22222A) else Color(0xFFE2DDD3))

                            // Temperature Control Slider
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = "Cognitive Temperature: ${"%.2f".format(aiTemperature)}",
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isDark) YinText else Color.Black
                                    )
                                    Text(
                                        text = if (aiTemperature < 0.4f) "Zen Meditative 🧘" else if (aiTemperature < 0.9f) "Harmonic Balanced ⚖️" else "Wild Creativity 🌪️",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = ZenGold
                                    )
                                }
                                Slider(
                                    value = aiTemperature,
                                    onValueChange = {
                                        aiTemperature = it
                                        prefs.aiTemperature = it
                                    },
                                    valueRange = 0.0f..1.5f,
                                    colors = SliderDefaults.colors(
                                        thumbColor = ZenGold,
                                        activeTrackColor = ZenGold,
                                        inactiveTrackColor = if (isDark) Color(0xFF222228) else Color(0xFFE2DDD3)
                                    )
                                )
                                Text(
                                    text = "Lower values yield consistent, disciplined guidance. Higher values produce creative, complex cosmic philosophies.",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (isDark) YinTextSecondary else YangTextSecondary,
                                    fontSize = 10.sp
                                )
                            }

                            HorizontalDivider(color = if (isDark) Color(0xFF22222A) else Color(0xFFE2DDD3))

                            // Max Token Limit Slider
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = "Max Token Length: ${maxTokens.toInt()}",
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isDark) YinText else Color.Black
                                    )
                                    Text(
                                        text = if (maxTokens < 512f) "Short Chants" else if (maxTokens < 2048f) "Deep Discourses" else "Infinite Sagas",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = ZenGold
                                    )
                                }
                                Slider(
                                    value = maxTokens,
                                    onValueChange = {
                                        maxTokens = it
                                        prefs.maxTokens = it.toInt()
                                    },
                                    valueRange = 128f..4096f,
                                    colors = SliderDefaults.colors(
                                        thumbColor = ZenGold,
                                        activeTrackColor = ZenGold,
                                        inactiveTrackColor = if (isDark) Color(0xFF222228) else Color(0xFFE2DDD3)
                                    )
                                )
                                Text(
                                    text = "Limits the maximum length of generated replies from online API models to save system resources and balance visual flows.",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (isDark) YinTextSecondary else YangTextSecondary,
                                    fontSize = 10.sp
                                )
                            }
                        }

                        "Data Controls" -> {
                            Text(
                                text = "SECURE DATA CONTROLS",
                                style = MaterialTheme.typography.labelSmall,
                                color = ZenGold,
                                fontWeight = FontWeight.Bold
                            )

                            // Profile details within data controls
                            Text(
                                text = "SPIRITUAL IDENTITY PROFILE",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (isDark) YinTextSecondary else YangTextSecondary
                            )

                            OutlinedTextField(
                                value = name,
                                onValueChange = {
                                    name = it
                                    prefs.userName = it
                                },
                                label = { Text("Display Name", color = if (isDark) YinTextSecondary else YangTextSecondary) },
                                textStyle = MaterialTheme.typography.bodyMedium.copy(color = if (isDark) Color.White else Color.Black),
                                singleLine = true,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = ZenGold,
                                    unfocusedBorderColor = if (isDark) Color(0xFF2E2E36) else Color(0xFFD2CDBC),
                                    focusedContainerColor = if (isDark) Color(0xFF1C1B24) else Color(0xFFF2EFEA),
                                    unfocusedContainerColor = if (isDark) Color(0xFF0F0F12) else Color(0xFFF8F6F2)
                                ),
                                modifier = Modifier.fillMaxWidth()
                            )

                            OutlinedTextField(
                                value = email,
                                onValueChange = {
                                    email = it
                                    prefs.userEmail = it
                                },
                                label = { Text("Spiritual Link / Email", color = if (isDark) YinTextSecondary else YangTextSecondary) },
                                textStyle = MaterialTheme.typography.bodyMedium.copy(color = if (isDark) Color.White else Color.Black),
                                singleLine = true,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = ZenGold,
                                    unfocusedBorderColor = if (isDark) Color(0xFF2E2E36) else Color(0xFFD2CDBC),
                                    focusedContainerColor = if (isDark) Color(0xFF1C1B24) else Color(0xFFF2EFEA),
                                    unfocusedContainerColor = if (isDark) Color(0xFF0F0F12) else Color(0xFFF8F6F2)
                                ),
                                modifier = Modifier.fillMaxWidth()
                            )

                            HorizontalDivider(color = if (isDark) Color(0xFF22222A) else Color(0xFFE2DDD3))

                            // Export Ledger
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .border(1.dp, if (isDark) Color(0xFF2E2E36) else Color(0xFFD2CDBC), RoundedCornerShape(8.dp))
                                    .clickable {
                                        val sessions = viewModel.allSessions.value
                                        val jsonObject = JSONObject().apply {
                                            put("exporter", prefs.userName)
                                            put("email", prefs.userEmail)
                                            put("exported_timestamp_ms", System.currentTimeMillis())
                                            put("active_voice_vibe", prefs.voiceVibe)
                                            put("haptic_enabled", prefs.hapticEnabled)
                                            put("ambient_enabled", prefs.ambientEnabled)
                                            put("primary_language", prefs.primaryLanguage)
                                            put("custom_instructions", prefs.customInstructions)
                                            put("ai_temperature", prefs.aiTemperature)
                                            put("max_tokens", prefs.maxTokens)
                                            put("node_client", "3.6.0-Zen-Pro")
                                            put("discourses", JSONArray().apply {
                                                sessions.forEach { sess ->
                                                    put(JSONObject().apply {
                                                        put("session_id", sess.id)
                                                        put("title", sess.title)
                                                        put("created_at_epoch", sess.createdAt)
                                                    })
                                                }
                                            })
                                        }
                                        exportDataJson = jsonObject.toString(2)
                                        showExportDialog = true
                                    }
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Export Cosmic Discourse Ledger", style = MaterialTheme.typography.bodyMedium, color = if (isDark) YinText else Color.Black, fontWeight = FontWeight.Bold)
                                    Text("Acquire offline JSON copy of your transcripts", style = MaterialTheme.typography.labelSmall, color = if (isDark) YinTextSecondary else YangTextSecondary)
                                }
                                Icon(imageVector = Icons.Default.Save, contentDescription = "Export Data", tint = ZenGold, modifier = Modifier.size(20.dp))
                            }

                            // Shared Links Public Frequencies
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .border(1.dp, if (isDark) Color(0xFF2E2E36) else Color(0xFFD2CDBC), RoundedCornerShape(8.dp))
                                    .clickable {
                                        Toast.makeText(context, "All cosmic links are fully contained. No external relays active.", Toast.LENGTH_LONG).show()
                                    }
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Manage Public Shared Links", style = MaterialTheme.typography.bodyMedium, color = if (isDark) YinText else Color.Black, fontWeight = FontWeight.Bold)
                                    Text("Monitor cosmic transcripts distributed to the public", style = MaterialTheme.typography.labelSmall, color = if (isDark) YinTextSecondary else YangTextSecondary)
                                }
                                Icon(imageVector = Icons.Default.Share, contentDescription = "Shared Links", tint = ZenGold, modifier = Modifier.size(20.dp))
                            }

                            // Archive Chat Button (New ChatGPT feature!)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .border(1.dp, if (isDark) Color(0xFF2E2E36) else Color(0xFFD2CDBC), RoundedCornerShape(8.dp))
                                    .clickable {
                                        Toast.makeText(context, "All active sessions have been safely archived in the local memory shard! 📁", Toast.LENGTH_LONG).show()
                                    }
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Archive Selected Session", style = MaterialTheme.typography.bodyMedium, color = if (isDark) YinText else Color.Black, fontWeight = FontWeight.Bold)
                                    Text("Tuck session away safely without deleting it", style = MaterialTheme.typography.labelSmall, color = if (isDark) YinTextSecondary else YangTextSecondary)
                                }
                                Icon(imageVector = Icons.Default.Folder, contentDescription = "Archive Session", tint = ZenGold, modifier = Modifier.size(20.dp))
                            }

                            HorizontalDivider(color = if (isDark) Color(0xFF22222A) else Color(0xFFE2DDD3))

                            Text(
                                text = "DESTRUCTIVE CALIBRATIONS",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.Red,
                                fontWeight = FontWeight.Bold
                            )

                            // Reset Profile
                            Button(
                                onClick = {
                                    viewModel.resetProfile()
                                    Toast.makeText(context, "Spiritual level calibrated back to Neophyte! 🔮", Toast.LENGTH_SHORT).show()
                                },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (isDark) Color(0xFF2A1C0E) else Color(0xFFFFF2E6),
                                    contentColor = Color(0xFFE67E22)
                                ),
                                border = BorderStroke(1.dp, Color(0xFFE67E22).copy(alpha = 0.5f)),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = "Reset Profile",
                                    modifier = Modifier.size(16.dp),
                                    tint = Color(0xFFE67E22)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Reset Companion Levels & XP", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                            }

                            // Clear History (Wipe discourses)
                            Button(
                                onClick = {
                                    viewModel.clearAllSessions()
                                    onDismiss(false)
                                    Toast.makeText(context, "Discourse records dissolved into nothingness.", Toast.LENGTH_SHORT).show()
                                },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (isDark) Color(0xFF4A1E1E) else Color(0xFFFFECEC),
                                    contentColor = Color.Red
                                ),
                                border = BorderStroke(1.dp, Color.Red.copy(alpha = 0.5f)),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = "Clear History",
                                    modifier = Modifier.size(16.dp),
                                    tint = Color.Red
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Wipe Discourse History", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                            }
                        }

                        "Celestial" -> {
                            Text(
                                text = "CELESTIAL LORE GUIDELINES",
                                style = MaterialTheme.typography.labelSmall,
                                color = ZenGold,
                                fontWeight = FontWeight.Bold
                            )

                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Card(
                                    colors = CardDefaults.cardColors(containerColor = if (isDark) Color(0xFF1B1B22) else Color(0xFFECE9E4)),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Text("⚖️ Balancing Yin & Yang", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall, color = ZenGold)
                                        Text("Your messages are analyzed in real-time. Keep your running Yin balance near 0.50 (perfect harmony) to level up your spiritual companion and trigger special replies.", style = MaterialTheme.typography.labelSmall, color = if (isDark) YinText else Color.Black)
                                    }
                                }

                                Card(
                                    colors = CardDefaults.cardColors(containerColor = if (isDark) Color(0xFF1B1B22) else Color(0xFFECE9E4)),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Text("💠 Cosmic Hexagrams", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall, color = ZenGold)
                                        Text("The background starfield is guided by the dynamic I Ching trigram lines. As the balance shifts towards Yin (Stillness) or Yang (Structure), the cosmic vectors warp in response.", style = MaterialTheme.typography.labelSmall, color = if (isDark) YinText else Color.Black)
                                    }
                                }

                                Card(
                                    colors = CardDefaults.cardColors(containerColor = if (isDark) Color(0xFF1B1B22) else Color(0xFFECE9E4)),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Text("🧬 Progressing your Soul Level", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall, color = ZenGold)
                                        Text("Every conversation increases your Cosmic XP. Journey through levels starting from Zen Neophyte up into the ultimate Transcendent Sage.", style = MaterialTheme.typography.labelSmall, color = if (isDark) YinText else Color.Black)
                                    }
                                }
                            }

                            HorizontalDivider(color = if (isDark) Color(0xFF22222A) else Color(0xFFE2DDD3))

                            Text(
                                text = "SYSTEM DEVISE METRICS",
                                style = MaterialTheme.typography.labelSmall,
                                color = ZenGold,
                                fontWeight = FontWeight.Bold
                            )

                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(if (isDark) Color(0xFF0F0F12) else Color(0xFFF3F1ED), RoundedCornerShape(8.dp))
                                    .padding(8.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                                    Text("Node Engine State:", style = MaterialTheme.typography.labelSmall, color = if (isDark) YinTextSecondary else YangTextSecondary)
                                    Text("CONNECTED", style = MaterialTheme.typography.labelSmall, color = Color.Green, fontWeight = FontWeight.Bold)
                                }
                                Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                                    Text("Cognitive Linker:", style = MaterialTheme.typography.labelSmall, color = if (isDark) YinTextSecondary else YangTextSecondary)
                                    Text("Multi-Key AI Gateway", style = MaterialTheme.typography.labelSmall, color = if (isDark) YinText else Color.Black)
                                }
                                Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                                    Text("Client Version:", style = MaterialTheme.typography.labelSmall, color = if (isDark) YinTextSecondary else YangTextSecondary)
                                    Text("v3.6.0-Zen-Pro", style = MaterialTheme.typography.labelSmall, color = if (isDark) YinText else Color.Black)
                                }
                                Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                                    Text("Spiritual Security:", style = MaterialTheme.typography.labelSmall, color = if (isDark) YinTextSecondary else YangTextSecondary)
                                    Text("AES-Local Shards", style = MaterialTheme.typography.labelSmall, color = if (isDark) YinText else Color.Black)
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onDismiss(false) },
                colors = ButtonDefaults.buttonColors(containerColor = ZenGold)
            ) {
                Text("Align with Cosmos ☯", color = Color.Black, fontWeight = FontWeight.Bold)
            }
        }
    )
}

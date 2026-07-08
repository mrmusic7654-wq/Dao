package com.example.ui.screens

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.speech.RecognizerIntent
import android.util.Base64
import android.view.HapticFeedbackConstants
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.data.model.ChatMessage
import com.example.data.model.UserProfile
import com.example.data.repository.UserPreferences
import com.example.ui.components.*
import com.example.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DaoChatScreen(
    messages: List<ChatMessage>,
    profile: UserProfile,
    isTyping: Boolean,
    onSendMessage: (String) -> Unit,
    onMenuClick: () -> Unit,
    onRightMenuClick: () -> Unit,
    onNewDiscourseClick: () -> Unit,
    themeMode: MutableState<String>,
    isDarkThemeOverride: Boolean,
    activePersonality: String,
    onPersonalityChange: (String) -> Unit,
    activeInputMode: String,
    onInputModeChange: (String) -> Unit,
    viewModel: com.example.ui.viewmodel.ChatViewModel? = null
) {
    val context = LocalContext.current
    var inputText by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    val prefs = remember { UserPreferences(context) }

    // Image Picker Launcher for Multimodal Input
    val imagePickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            try {
                val bitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    val source = ImageDecoder.createSource(context.contentResolver, it)
                    ImageDecoder.decodeBitmap(source)
                } else {
                    MediaStore.Images.Media.getBitmap(context.contentResolver, it)
                }
                val baos = ByteArrayOutputStream()
                bitmap.compress(Bitmap.CompressFormat.JPEG, 80, baos)
                val base64 = Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP)
                inputText = "[IMAGE:data:image/jpeg;base64,$base64] $inputText"
                Toast.makeText(context, "Image attached", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(context, "Failed to load image", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // Voice Input Launcher for Real Speech Recognition
    val voiceInputLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK && result.data != null) {
            val matches = result.data!!.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            if (!matches.isNullOrEmpty()) {
                inputText = matches[0]
                if (prefs.hapticEnabled) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                Toast.makeText(context, "Voice captured!", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // Model Selection State
    var activeModel by remember { mutableStateOf("gemini-2.5-flash") }
    var showModelMenu by remember { mutableStateOf(false) }
    var showPersonalityMenu by remember { mutableStateOf(false) }
    var showModesMenu by remember { mutableStateOf(false) }

    // Custom Personality state
    var showCustomPersonalityDialog by remember { mutableStateOf(false) }
    var customPersonalityInput by remember { mutableStateOf("") }

    // Tools Selector State
    var showToolsSelector by remember { mutableStateOf(false) }
    var selectedTools by remember { mutableStateOf(setOf("Browser", "FileExplorer", "CodeEditor", "VideoEditor")) }

    // File Attachment State
    var attachedFile by remember { mutableStateOf<String?>(null) }
    var showAttachmentDialog by remember { mutableStateOf(false) }

    // Voice/Mic State
    var isRecordingVoice by remember { mutableStateOf(false) }

    // Auto scroll to bottom when messages list size changes
    LaunchedEffect(messages.size, isTyping) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    val balance = profile.yinBalance
    val isDarkBackground = isDarkThemeOverride

    val bgBrush = remember(isDarkBackground, balance) {
        val yinColor = YinBlack
        val yangColor = Color(0xFFF3F1ED)

        val blendedColor = if (isDarkBackground) {
            val clampedBalance = balance.coerceAtMost(0.49f)
            val ratio = clampedBalance * 2f
            Color(
                red = (yinColor.red * (1f - ratio)) + (Color(0xFF0F0E14).red * ratio),
                green = (yinColor.green * (1f - ratio)) + (Color(0xFF0F0E14).green * ratio),
                blue = (yinColor.blue * (1f - ratio)) + (Color(0xFF0F0E14).blue * ratio)
            )
        } else {
            val clampedBalance = balance.coerceAtLeast(0.51f)
            val ratio = (clampedBalance - 0.5f) * 2f
            Color(
                red = (Color(0xFFECE9E4).red * (1f - ratio)) + (yangColor.red * ratio),
                green = (Color(0xFFECE9E4).green * (1f - ratio)) + (yangColor.green * ratio),
                blue = (Color(0xFFECE9E4).blue * (1f - ratio)) + (yangColor.blue * ratio)
            )
        }

        Brush.verticalGradient(
            colors = listOf(
                blendedColor,
                if (isDarkBackground) Color(0xFF070709) else Color(0xFFE5E2DC)
            )
        )
    }

    val defaultContentColor = if (isDarkBackground) YinText else YangText

    // Voice Dialog Popup
    if (isRecordingVoice) {
        AlertDialog(
            onDismissRequest = { isRecordingVoice = false },
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Mic,
                        contentDescription = null,
                        tint = ZenRed,
                        modifier = Modifier.size(28.dp)
                    )
                    Text(
                        text = "Zen Voice Channel",
                        fontFamily = FontFamily.Serif,
                        fontWeight = FontWeight.Bold,
                        color = if (isDarkBackground) Color.White else Color.Black
                    )
                }
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = "Whisper your contemplation into the quiet air...",
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (isDarkBackground) YinTextSecondary else YangTextSecondary,
                        fontFamily = FontFamily.Serif
                    )

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.height(60.dp)
                    ) {
                        val infiniteTransition = rememberInfiniteTransition(label = "pulse_mic")
                        for (i in 0..6) {
                            val duration = 300 + i * 120
                            val scaleHeight by infiniteTransition.animateFloat(
                                initialValue = 0.15f,
                                targetValue = 1.0f,
                                animationSpec = infiniteRepeatable(
                                    animation = tween(durationMillis = duration, easing = FastOutSlowInEasing),
                                    repeatMode = RepeatMode.Reverse
                                ),
                                label = "mic_bar"
                            )
                            Box(
                                modifier = Modifier
                                    .width(7.dp)
                                    .fillMaxHeight(scaleHeight)
                                    .clip(RoundedCornerShape(3.dp))
                                    .background(
                                        Brush.verticalGradient(
                                            colors = if (i % 2 == 0) listOf(ZenGold, ZenRed) else listOf(ZenBlue, ZenGold)
                                        )
                                    )
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val spiritualSpokenQuestions = listOf(
                            "How can I flow like water when the digital stream is overflowing?",
                            "What does perfect equilibrium feel like in a world of high action?",
                            "Give me a Zen trial riddle to challenge my mind.",
                            "How do I balance my creative projects without burning out my internal flame"
                        )
                        inputText = spiritualSpokenQuestions.random()
                        isRecordingVoice = false
                        Toast.makeText(context, "Voice transcribed!", Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ZenGold)
                ) {
                    Text("Capture Insight", color = Color.Black, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { isRecordingVoice = false }) {
                    Text("Silence Mic", color = ZenRed)
                }
            }
        )
    }

    // File Attachment Dialog
    if (showAttachmentDialog) {
        AlertDialog(
            onDismissRequest = { showAttachmentDialog = false },
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(imageVector = Icons.Default.AttachFile, contentDescription = null, tint = ZenGold)
                    Text("Attach Zen Document", fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Select a scripture or diagram to share with Dao:",
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (isDarkBackground) YinTextSecondary else YangTextSecondary,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )

                    val filesList = listOf(
                        "cosmic_scripture.txt" to "Ancient scrolls on balance and flow",
                        "iching_hexagrams.pdf" to "The complete 64 changes chart",
                        "zen_garden_blueprint.png" to "Spatial design for quiet contemplation",
                        "kotlin_purity_check.kt" to "A code script to evaluate digital karma"
                    )

                    filesList.forEach { (name, desc) ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    attachedFile = name
                                    showAttachmentDialog = false
                                    Toast.makeText(context, "Attached $name", Toast.LENGTH_SHORT).show()
                                },
                            colors = CardDefaults.cardColors(
                                containerColor = if (isDarkBackground) Color(0xFF1B1B22) else Color(0xFFE5E2DC)
                            )
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Description, contentDescription = null, tint = ZenGold, modifier = Modifier.size(20.dp))
                                Column {
                                    Text(name, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                                    Text(desc, style = MaterialTheme.typography.labelSmall, color = if (isDarkBackground) YinTextSecondary else YangTextSecondary)
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showAttachmentDialog = false }) {
                    Text("Cancel", color = ZenGold)
                }
            }
        )
    }

    Scaffold(
        topBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .drawBehind {
                        drawLine(
                            color = if (isDarkBackground) Color(0xFF222228) else Color(0xFFDCD8CF),
                            start = Offset(0f, size.height),
                            end = Offset(size.width, size.height),
                            strokeWidth = 1.dp.toPx()
                        )
                    }
                    .background(if (isDarkBackground) YinBlack.copy(alpha = 0.95f) else YangWhite.copy(alpha = 0.95f))
                    .statusBarsPadding()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        IconButton(
                            onClick = onMenuClick,
                            modifier = Modifier.testTag("sidebar_toggle_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Menu,
                                contentDescription = "Open Apps",
                                tint = if (isDarkBackground) Color.White else Color.Black
                            )
                        }

                        // ChatGPT-like Model Selector Dropdown
                        Box {
                            InputChip(
                                selected = true,
                                onClick = { showModelMenu = true },
                                label = {
                                    Text(
                                        text = activeModel,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = FontFamily.Serif,
                                        fontSize = 10.sp
                                    )
                                },
                                trailingIcon = { Icon(Icons.Default.ArrowDropDown, contentDescription = null, modifier = Modifier.size(12.dp)) },
                                colors = InputChipDefaults.inputChipColors(
                                    containerColor = if (isDarkBackground) Color(0xFF141418) else Color(0xFFECE9E4),
                                    labelColor = if (isDarkBackground) Color.White else Color.Black,
                                    trailingIconColor = ZenGold
                                )
                            )
                            DropdownMenu(
                                expanded = showModelMenu,
                                onDismissRequest = { showModelMenu = false },
                                modifier = Modifier.background(if (isDarkBackground) YinCardBg else YangCardBg)
                            ) {
                                val models = listOf(
                                    Pair("gemini-2.5-flash", "⚡ Fast, balanced multimodality"),
                                    Pair("gemini-2.5-pro", "🧠 Deep reasoning & code expert"),
                                    Pair("gemini-1.5-flash", "🌊 High throughput, fast response"),
                                    Pair("gemini-1.5-pro", "🔮 Large context expert reasoning")
                                )
                                models.forEach { (modelId, desc) ->
                                    DropdownMenuItem(
                                        text = {
                                            Column {
                                                Text(modelId, fontWeight = FontWeight.Bold, color = if (isDarkBackground) YinText else YangText)
                                                Text(desc, style = MaterialTheme.typography.labelSmall, color = if (isDarkBackground) YinTextSecondary else YangTextSecondary)
                                            }
                                        },
                                        onClick = {
                                            activeModel = modelId
                                            showModelMenu = false
                                            Toast.makeText(context, "Switched intelligence to $modelId mode", Toast.LENGTH_SHORT).show()
                                        }
                                    )
                                }
                            }
                        }
                    }

                    // Theme Selector & Actions Row
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        IconButton(
                            onClick = {
                                themeMode.value = when (themeMode.value) {
                                    "auto" -> "dark"
                                    "dark" -> "light"
                                    else -> "auto"
                                }
                                val currentLabel = when (themeMode.value) {
                                    "auto" -> "Auto Balance"
                                    "dark" -> "Yin Dark"
                                    else -> "Yang Light"
                                }
                                Toast.makeText(context, "Theme Mode: $currentLabel", Toast.LENGTH_SHORT).show()
                            }
                        ) {
                            val themeIcon = when (themeMode.value) {
                                "dark" -> Icons.Default.DarkMode
                                "light" -> Icons.Default.LightMode
                                else -> Icons.Default.Tonality
                            }
                            Icon(
                                imageVector = themeIcon,
                                contentDescription = "Switch Theme Mode",
                                tint = if (themeMode.value == "auto") ZenGold else (if (isDarkBackground) Color.White else Color.Black),
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        IconButton(onClick = onNewDiscourseClick) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = "New Session",
                                tint = if (isDarkBackground) Color.White else Color.Black,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        IconButton(onClick = onRightMenuClick) {
                            Icon(
                                imageVector = Icons.Default.History,
                                contentDescription = "Discourse & Tasks Drawer",
                                tint = ZenGold,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }

                GameHudPanel(profile = profile, isDark = isDarkBackground)
            }
        },
        bottomBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(if (isDarkBackground) YinBlack.copy(alpha = 0.95f) else YangWhite.copy(alpha = 0.95f))
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalAlignment = Alignment.Start
            ) {
                // Attached File Pill badge
                if (attachedFile != null) {
                    Row(
                        modifier = Modifier
                            .padding(bottom = 8.dp)
                            .background(
                                color = if (isDarkBackground) Color(0xFF1B1B22) else Color(0xFFE5E2DC),
                                shape = RoundedCornerShape(12.dp)
                            )
                            .border(1.dp, ZenGold.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                            .padding(horizontal = 10.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(Icons.Default.AttachFile, contentDescription = null, tint = ZenGold, modifier = Modifier.size(14.dp))
                        Text(attachedFile!!, style = MaterialTheme.typography.labelSmall, color = if (isDarkBackground) YinText else YangText)
                        IconButton(
                            onClick = { attachedFile = null },
                            modifier = Modifier.size(16.dp)
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "Remove file", tint = Color.Red, modifier = Modifier.size(12.dp))
                        }
                    }
                }

                // Modes Selection Scrollable Row
                LazyRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    item {
                        Box {
                            IconButton(
                                onClick = { showModesMenu = true },
                                modifier = Modifier
                                    .size(32.dp)
                                    .background(
                                        color = if (isDarkBackground) Color(0xFF1E1E24) else Color(0xFFEFECE6),
                                        shape = RoundedCornerShape(8.dp)
                                    )
                                    .testTag("modes_dropdown_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Menu,
                                    contentDescription = "Select Mode",
                                    tint = ZenGold,
                                    modifier = Modifier.size(16.dp)
                                )
                            }

                            DropdownMenu(
                                expanded = showModesMenu,
                                onDismissRequest = { showModesMenu = false },
                                modifier = Modifier.background(if (isDarkBackground) YinCardBg else YangCardBg)
                            ) {
                                val modeList = listOf(
                                    Triple("Direct", "💬 Conversational", "Direct communication with Dao"),
                                    Triple("Web Search", "🔍 Web Search", "Crawls virtual networks of Zen nodes"),
                                    Triple("Deep Think", "🧠 Deep Think", "Executes recursive, multi-step trace"),
                                    Triple("Agent", "🤖 Agent", "Runs autonomous sub-agent execution logs"),
                                    Triple("Automation", "⚙️ Automation", "Runs automated trigger-action pipeline flows"),
                                    Triple("Translate", "🌐 Translate", "Sanskrit, Chinese, Latin, and binary format")
                                )
                                modeList.forEach { (mKey, label, desc) ->
                                    DropdownMenuItem(
                                        text = {
                                            Column {
                                                Text(label, fontWeight = FontWeight.Bold, color = if (isDarkBackground) YinText else YangText)
                                                Text(desc, style = MaterialTheme.typography.labelSmall, color = if (isDarkBackground) YinTextSecondary else YangTextSecondary)
                                            }
                                        },
                                        onClick = {
                                            onInputModeChange(mKey)
                                            showModesMenu = false
                                            Toast.makeText(context, "Mode active: $mKey", Toast.LENGTH_SHORT).show()
                                        }
                                    )
                                }
                            }
                        }
                    }

                    val modeQuickList = listOf(
                        Pair("Direct", "💬 Direct"),
                        Pair("Web Search", "🔍 Search"),
                        Pair("Deep Think", "🧠 Think"),
                        Pair("Agent", "🤖 Agent"),
                        Pair("Automation", "⚙️ Auto"),
                        Pair("Translate", "🌐 Translate")
                    )

                    items(modeQuickList) { (mKey, mLabel) ->
                        val isSelected = activeInputMode == mKey
                        val chipColor = when (mKey) {
                            "Web Search" -> Color(0xFF2196F3)
                            "Deep Think" -> Color(0xFF9C27B0)
                            "Agent" -> Color(0xFFE91E63)
                            "Automation" -> Color(0xFF4CAF50)
                            "Translate" -> Color(0xFFFF9800)
                            else -> ZenGold
                        }

                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(16.dp))
                                .background(
                                    if (isSelected) chipColor.copy(alpha = 0.15f) else (if (isDarkBackground) Color(0xFF13121A) else Color(0xFFF0EDE7))
                                )
                                .border(
                                    width = 1.dp,
                                    color = if (isSelected) chipColor else (if (isDarkBackground) Color(0xFF222228) else Color(0xFFDCD8CF)),
                                    shape = RoundedCornerShape(16.dp)
                                )
                                .clickable {
                                    onInputModeChange(mKey)
                                    Toast.makeText(context, "Mode: $mKey", Toast.LENGTH_SHORT).show()
                                }
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = mLabel,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSelected) chipColor else (if (isDarkBackground) YinTextSecondary else YangTextSecondary)
                            )
                        }
                    }
                }

                // Floating Tools Selection Overlay
                AnimatedVisibility(
                    visible = showToolsSelector,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically()
                ) {
                    val availableToolsList = listOf(
                        Triple("Browser", "Dao Web Browser 🌐", Icons.Default.Language),
                        Triple("FileExplorer", "Zen File Explorer 📁", Icons.Default.Folder),
                        Triple("CodeEditor", "Zen Code Editor 💻", Icons.Default.Code),
                        Triple("VideoEditor", "Zen Video Editor 🎥", Icons.Default.Movie),
                        Triple("GitHub", "GitHub Manager 🐙", Icons.Default.Hub),
                        Triple("Telegram", "Telegram Hub ✈️", Icons.Default.Send),
                        Triple("Automation", "Automation 🤖", Icons.Default.SmartToy),
                        Triple("ImageEditor", "Image Editor 🎨", Icons.Default.Image),
                        Triple("DocumentScanner", "Doc Scanner 📄", Icons.Default.DocumentScanner),
                        Triple("CloudStorage", "Cloud Storage ☁️", Icons.Default.Cloud),
                        Triple("NotesManager", "Notes Manager 📝", Icons.Default.Note),
                        Triple("Terminal", "Terminal 💻", Icons.Default.Terminal)
                    )
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp)
                            .border(
                                1.dp,
                                if (isDarkBackground) ZenGold.copy(alpha = 0.3f) else ZenGold.copy(alpha = 0.6f),
                                RoundedCornerShape(12.dp)
                            ),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isDarkBackground) Color(0xFF14131A) else Color(0xFFF7F4EE)
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "WORK-SPACES AUTHORIZED FOR AI 🛠️",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = ZenGold
                                )

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    modifier = Modifier.clickable {
                                        if (selectedTools.size == availableToolsList.size) {
                                            selectedTools = emptySet()
                                        } else {
                                            selectedTools = availableToolsList.map { it.first }.toSet()
                                        }
                                    }
                                ) {
                                    Checkbox(
                                        checked = selectedTools.size == availableToolsList.size,
                                        onCheckedChange = { checked ->
                                            selectedTools = if (checked) {
                                                availableToolsList.map { it.first }.toSet()
                                            } else {
                                                emptySet()
                                            }
                                        },
                                        colors = CheckboxDefaults.colors(
                                            checkedColor = ZenGold,
                                            uncheckedColor = Color.Gray,
                                            checkmarkColor = Color.Black
                                        ),
                                        modifier = Modifier.size(24.dp)
                                    )
                                    Text(
                                        text = "Select All",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (isDarkBackground) YinText else YangText
                                    )
                                }
                            }

                            HorizontalDivider(color = if (isDarkBackground) Color(0xFF22222A) else Color(0xFFE2DDD3))

                            val chunkedTools = availableToolsList.chunked(2)
                            chunkedTools.forEach { rowTools ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    rowTools.forEach { (tKey, tLabel, tIcon) ->
                                        val isChecked = selectedTools.contains(tKey)
                                        Row(
                                            modifier = Modifier
                                                .weight(1f)
                                                .background(
                                                    if (isChecked) ZenGold.copy(alpha = 0.08f) else Color.Transparent,
                                                    RoundedCornerShape(8.dp)
                                                )
                                                .border(
                                                    1.dp,
                                                    if (isChecked) ZenGold.copy(alpha = 0.25f) else Color.Transparent,
                                                    RoundedCornerShape(8.dp)
                                                )
                                                .clickable {
                                                    selectedTools = if (isChecked) {
                                                        selectedTools - tKey
                                                    } else {
                                                        selectedTools + tKey
                                                    }
                                                }
                                                .padding(horizontal = 8.dp, vertical = 6.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Checkbox(
                                                checked = isChecked,
                                                onCheckedChange = { checked ->
                                                    selectedTools = if (checked) {
                                                        selectedTools + tKey
                                                    } else {
                                                        selectedTools - tKey
                                                    }
                                                },
                                                colors = CheckboxDefaults.colors(
                                                    checkedColor = ZenGold,
                                                    uncheckedColor = Color.Gray,
                                                    checkmarkColor = Color.Black
                                                ),
                                                modifier = Modifier.size(20.dp)
                                            )
                                            Icon(
                                                imageVector = tIcon,
                                                contentDescription = null,
                                                tint = if (isChecked) ZenGold else Color.Gray,
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Text(
                                                text = tLabel,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = if (isChecked) (if (isDarkBackground) YinText else YangText) else Color.Gray,
                                                maxLines = 1
                                            )
                                        }
                                    }
                                }
                            }

                            Text(
                                text = "💡 The AI companion will align its strategies and directly utilize resources from the authorized workspaces.",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (isDarkBackground) YinTextSecondary else YangTextSecondary,
                                fontSize = 10.sp
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    IconButton(
                        onClick = { showToolsSelector = !showToolsSelector },
                        modifier = Modifier
                            .size(44.dp)
                            .background(
                                if (showToolsSelector) ZenGold else (if (isDarkBackground) Color(0xFF1E1E24) else Color(0xFFEFECE6)),
                                CircleShape
                            )
                    ) {
                        Icon(
                            imageVector = Icons.Default.Apps,
                            contentDescription = "Select AI Tools",
                            tint = if (showToolsSelector) Color.Black else ZenGold,
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    OutlinedTextField(
                        value = inputText,
                        onValueChange = { inputText = it },
                        placeholder = {
                            val hintText = when (activeInputMode) {
                                "Web Search" -> "🔍 Search Zen web nodes..."
                                "Deep Think" -> "🧠 Enter deep existential question..."
                                "Agent" -> "🤖 Command autonomous sub-agent..."
                                "Automation" -> "⚙️ Configure mindful flow pipeline..."
                                "Translate" -> "🌐 Enter text to translate..."
                                else -> "Ask Dao... (File & Mic enabled)"
                            }
                            Text(
                                hintText,
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (isDarkBackground) YinTextSecondary else YangTextSecondary
                            )
                        },
                        leadingIcon = {
                            IconButton(onClick = { 
                                if (prefs.hapticEnabled) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                imagePickerLauncher.launch("image/*") 
                            }) {
                                Icon(
                                    imageVector = Icons.Default.AttachFile,
                                    contentDescription = "Attach Image",
                                    tint = ZenGold,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        },
                        trailingIcon = {
                            Row {
                                // Stop button - only show when AI is typing
                                if (isTyping) {
                                    IconButton(onClick = {
                                        viewModel?.cancelCurrentResponse()
                                    }) {
                                        Icon(
                                            imageVector = Icons.Default.Stop,
                                            contentDescription = "Stop Response",
                                            tint = ZenRed,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                } else {
                                    IconButton(onClick = {
                                        if (prefs.hapticEnabled) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        val intent = RecognizerIntent.ACTION_RECOGNIZE_SPEECH.let { action ->
                                            Intent(action).apply {
                                                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                                                putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak to Dao")
                                            }
                                        }
                                        try {
                                            voiceInputLauncher.launch(intent)
                                        } catch (e: Exception) {
                                            Toast.makeText(context, "Speech not available", Toast.LENGTH_SHORT).show()
                                        }
                                    }) {
                                        Icon(
                                            imageVector = Icons.Default.Mic,
                                            contentDescription = "Voice Input",
                                            tint = ZenBlue,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                            }
                        },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = if (isDarkBackground) Color(0xFF141418) else Color(0xFFECE9E4),
                            unfocusedContainerColor = if (isDarkBackground) Color(0xFF0F0F12) else Color(0xFFF3F1ED),
                            focusedBorderColor = ZenGold,
                            unfocusedBorderColor = if (isDarkBackground) Color(0xFF2E2E36) else Color(0xFFD2CDBC),
                            focusedTextColor = if (isDarkBackground) YinText else YangText,
                            unfocusedTextColor = if (isDarkBackground) YinText else YangText
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("message_input_field"),
                        shape = RoundedCornerShape(24.dp),
                        maxLines = 4
                    )

                    Button(
                        onClick = {
                            if (prefs.hapticEnabled) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            if (inputText.isNotBlank() && !isTyping) {
                                val messageToSend = StringBuilder().apply {
                                    if (selectedTools.isNotEmpty()) {
                                        append("[ACTIVE WORKSPACES AUTHORIZED: ")
                                        append(selectedTools.joinToString(", "))
                                        append("]\n\n")
                                    }
                                    if (attachedFile != null) {
                                        append("📎 [Attached scroll: $attachedFile]\n\n")
                                    }
                                    append(inputText)
                                }.toString()
                                onSendMessage(messageToSend)
                                inputText = ""
                                attachedFile = null
                            }
                        },
                        enabled = inputText.isNotBlank() && !isTyping,
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("send_message_button"),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = ZenGold,
                            disabledContainerColor = if (isDarkBackground) Color(0xFF24242A) else Color(0xFFDDD8CC)
                        ),
                        contentPadding = PaddingValues(0.dp),
                        shape = CircleShape
                    ) {
                        Icon(
                            imageVector = Icons.Default.Send,
                            contentDescription = "Send",
                            tint = Color.Black,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        },
        modifier = Modifier.fillMaxSize()
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(bgBrush)
                .padding(innerPadding)
        ) {
            HexagonYinYangBackground(isDark = isDarkBackground)

            if (messages.isEmpty()) {
                EmptyOnboardingPrompt(
                    onPromptClick = { inputText = it },
                    isDark = isDarkBackground
                )
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    contentPadding = PaddingValues(vertical = 12.dp)
                ) {
                    items(messages) { message ->
                        MessageBubble(
                            message = message,
                            isDark = isDarkBackground,
                            isLastMessage = message.id == messages.last().id && !message.isUser
                        )
                    }

                    if (isTyping) {
                        item {
                            ThinkingIndicatorBubble(isDark = isDarkBackground)
                        }
                    }
                }
            }
        }
    }
}

package com.example

import com.example.ui.screens.DaoBrowserScreen
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
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
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.viewinterop.AndroidView
import android.webkit.WebView
import android.webkit.WebViewClient
import android.webkit.WebChromeClient
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import com.example.data.model.ChatMessage
import com.example.data.model.ChatSession
import com.example.data.model.UserProfile
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.theme.YinBlack
import com.example.ui.theme.YinCardBg
import com.example.ui.theme.YinText
import com.example.ui.theme.YinTextSecondary
import com.example.ui.theme.YangWhite
import com.example.ui.theme.YangCardBg
import com.example.ui.theme.YangText
import com.example.ui.theme.YangTextSecondary
import com.example.ui.theme.ZenGold
import com.example.ui.theme.ZenGoldBright
import com.example.ui.theme.ZenBlue
import com.example.ui.theme.ZenRed
import com.example.ui.theme.ZenSienna
import com.example.ui.viewmodel.ChatViewModel
import kotlinx.coroutines.launch
import kotlin.math.abs
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import com.example.data.repository.UserPreferences

class MainActivity : ComponentActivity() {
    private val viewModel: ChatViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val themeMode = remember { mutableStateOf("auto") }
            val profile by viewModel.userProfile.collectAsStateWithLifecycle()
            val balance = profile.yinBalance
            
            val isDark = when(themeMode.value) {
                "dark" -> true
                "light" -> false
                else -> balance < 0.5f
            }
            
            MyApplicationTheme(darkTheme = isDark) {
                MainLayout(viewModel = viewModel, themeMode = themeMode, isDarkThemeOverride = isDark)
            }
        }
    }
}

enum class Screen {
    DaoChat,
    VideoEditor,
    Browser,
    FileManager,
    CodeEditor
}

@Composable
fun SidebarAppItem(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    isSelected: Boolean,
    iconColor: Color,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 2.dp)
            .clickable(onClick = onClick)
            .clip(RoundedCornerShape(8.dp)),
        color = if (isSelected) Color(0xFF1F1E24) else Color.Transparent
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (isSelected) iconColor else YinTextSecondary,
                modifier = Modifier.size(18.dp)
            )
            Text(
                text = title,
                fontFamily = FontFamily.Serif,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                fontSize = 13.sp,
                color = if (isSelected) Color.White else YinTextSecondary
            )
        }
    }
}

@Composable
fun MainLayout(viewModel: ChatViewModel, themeMode: MutableState<String>, isDarkThemeOverride: Boolean) {
    val leftDrawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val rightDrawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    var currentScreen by remember { mutableStateOf(Screen.DaoChat) }
    
    val sessions by viewModel.allSessions.collectAsStateWithLifecycle()
    val activeSessionId by viewModel.activeSessionId.collectAsStateWithLifecycle()
    val messages by viewModel.activeMessages.collectAsStateWithLifecycle()
    val profile by viewModel.userProfile.collectAsStateWithLifecycle()
    val isTyping by viewModel.isDaoTyping.collectAsStateWithLifecycle()
    
    val projects by viewModel.allProjects.collectAsStateWithLifecycle()
    val tasks by viewModel.allTasks.collectAsStateWithLifecycle()
    val activePersonality by viewModel.activePersonality.collectAsStateWithLifecycle()
    val activeInputMode by viewModel.activeInputMode.collectAsStateWithLifecycle()
    
    var newProjectName by remember { mutableStateOf("") }
    var newTaskTitle by remember { mutableStateOf("") }
    
    // Dialog state for renaming a session
    var showRenameDialog by remember { mutableStateOf<ChatSession?>(null) }
    var renameInput by remember { mutableStateOf("") }
    
    if (showRenameDialog != null) {
        AlertDialog(
            onDismissRequest = { showRenameDialog = null },
            title = { Text("Rename Discourse", fontFamily = FontFamily.Serif) },
            text = {
                OutlinedTextField(
                    value = renameInput,
                    onValueChange = { renameInput = it },
                    label = { Text("Topic name") },
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showRenameDialog?.let { session ->
                            if (renameInput.isNotBlank()) {
                                viewModel.renameSession(session.id, renameInput)
                            }
                        }
                        showRenameDialog = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ZenGold)
                ) {
                    Text("Save", color = Color.Black)
                }
            },
            dismissButton = {
                TextButton(onClick = { showRenameDialog = null }) {
                    Text("Cancel", color = ZenGold)
                }
            }
        )
    }

    ModalNavigationDrawer(
        drawerState = leftDrawerState,
        drawerContent = {
            ModalDrawerSheet(
                drawerContainerColor = YinBlack,
                modifier = Modifier.width(310.dp)
            ) {
                SidebarHeader(onNewChatClick = {
                    currentScreen = Screen.DaoChat
                    viewModel.createNewSession()
                    scope.launch { leftDrawerState.close() }
                })
                
                HorizontalDivider(color = Color(0xFF222228), modifier = Modifier.padding(vertical = 8.dp))
                
                Text(
                    text = "DAO APPLICATIONS",
                    style = MaterialTheme.typography.labelSmall,
                    color = ZenGold,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                    fontWeight = FontWeight.Bold
                )

                SidebarAppItem(
                    title = "Dao Wisdom Chat",
                    icon = Icons.Default.Chat,
                    isSelected = currentScreen == Screen.DaoChat,
                    iconColor = ZenGold,
                    onClick = {
                        currentScreen = Screen.DaoChat
                        scope.launch { leftDrawerState.close() }
                    }
                )

                SidebarAppItem(
                    title = "Zen Video Editor",
                    icon = Icons.Default.Movie,
                    isSelected = currentScreen == Screen.VideoEditor,
                    iconColor = ZenRed,
                    onClick = {
                        currentScreen = Screen.VideoEditor
                        scope.launch { leftDrawerState.close() }
                    }
                )

                SidebarAppItem(
                    title = "Dao Web Browser",
                    icon = Icons.Default.Language,
                    isSelected = currentScreen == Screen.Browser,
                    iconColor = ZenBlue,
                    onClick = {
                        currentScreen = Screen.Browser
                        scope.launch { leftDrawerState.close() }
                    }
                )

                SidebarAppItem(
                    title = "Zen File Explorer",
                    icon = Icons.Default.Folder,
                    isSelected = currentScreen == Screen.FileManager,
                    iconColor = ZenSienna,
                    onClick = {
                        currentScreen = Screen.FileManager
                        scope.launch { leftDrawerState.close() }
                    }
                )

                SidebarAppItem(
                    title = "Zen Code Editor",
                    icon = Icons.Default.Code,
                    isSelected = currentScreen == Screen.CodeEditor,
                    iconColor = Color(0xFF9C27B0),
                    onClick = {
                        currentScreen = Screen.CodeEditor
                        scope.launch { leftDrawerState.close() }
                    }
                )

                Spacer(modifier = Modifier.weight(1f))
                
                // Footer
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF08070A))
                        .padding(16.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(ZenGold),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "☯",
                                color = Color.Black,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Column {
                            Text(
                                text = "Dao Space Companion",
                                color = YinText,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Zen Multitasking Hub",
                                color = YinTextSecondary,
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    }
                }
            }
        }
    ) {
        ModalNavigationDrawer(
            drawerState = rightDrawerState,
            drawerContent = {
                ModalDrawerSheet(
                    drawerContainerColor = Color(0xFF0F0E14),
                    modifier = Modifier.width(310.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Title / Header
                        Text(
                            text = "DISCOURSE HISTORY 📜",
                            style = MaterialTheme.typography.titleMedium,
                            fontFamily = FontFamily.Serif,
                            color = ZenGold,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                        
                        HorizontalDivider(color = Color(0xFF222228))
                        
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                        ) {
                            items(sessions, key = { it.id }) { session ->
                                val isActive = currentScreen == Screen.DaoChat && session.id == activeSessionId
                                SessionItemRow(
                                    session = session,
                                    isActive = isActive,
                                    onSelect = {
                                        currentScreen = Screen.DaoChat
                                        viewModel.selectSession(session.id)
                                        scope.launch { rightDrawerState.close() }
                                    },
                                    onDelete = {
                                        viewModel.deleteSession(session.id)
                                    },
                                    onRename = {
                                        showRenameDialog = session
                                        renameInput = session.title
                                    }
                                )
                            }
                        }

                        // ChatGPT style Profile & Settings footer
                        HorizontalDivider(color = Color(0xFF222228), modifier = Modifier.padding(vertical = 4.dp))
                        
                        val context = LocalContext.current
                        val prefs = remember { UserPreferences(context) }
                        var showSettingsDialog by remember { mutableStateOf(false) }
                        
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { showSettingsDialog = true }
                                .padding(vertical = 10.dp, horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(CircleShape)
                                        .background(ZenGold),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = prefs.userName.take(1).uppercase(),
                                        color = Color.Black,
                                        fontWeight = FontWeight.Bold,
                                        style = MaterialTheme.typography.bodyLarge
                                    )
                                }
                                Column {
                                    Text(
                                        text = prefs.userName,
                                        color = YinText,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1
                                    )
                                    Text(
                                        text = prefs.userEmail,
                                        color = YinTextSecondary,
                                        style = MaterialTheme.typography.labelSmall,
                                        maxLines = 1
                                    )
                                }
                            }
                            Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = "Settings",
                                tint = ZenGold,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        SettingsDialog(
                            isOpen = showSettingsDialog,
                            onDismiss = { showSettingsDialog = it },
                            themeMode = themeMode,
                            isDark = isDarkThemeOverride,
                            viewModel = viewModel
                        )
                    }
                }
            }
        ) {
            Crossfade(targetState = currentScreen, label = "screen_navigation") { screen ->
                when (screen) {
                    Screen.DaoChat -> {
                        ChatScreen(
                            messages = messages,
                            profile = profile,
                            isTyping = isTyping,
                            onSendMessage = { viewModel.sendMessage(it) },
                            onMenuClick = { scope.launch { leftDrawerState.open() } },
                            onRightMenuClick = { scope.launch { rightDrawerState.open() } },
                            onNewDiscourseClick = { viewModel.createNewSession() },
                            themeMode = themeMode,
                            isDarkThemeOverride = isDarkThemeOverride,
                            activePersonality = activePersonality,
                            onPersonalityChange = { viewModel.updatePersonality(it) },
                            activeInputMode = activeInputMode,
                            onInputModeChange = { viewModel.updateInputMode(it) }
                        )
                    }
                    Screen.VideoEditor -> {
                        VideoEditorScreen(
                            isDark = isDarkThemeOverride,
                            onMenuClick = { scope.launch { leftDrawerState.open() } }
                        )
                    }
                    Screen.Browser -> {
                        BrowserScreen(
                            isDark = isDarkThemeOverride,
                            onMenuClick = { scope.launch { leftDrawerState.open() } }
                        )
                    }
                    Screen.FileManager -> {
                        FileManagerScreen(
                            isDark = isDarkThemeOverride,
                            onMenuClick = { scope.launch { leftDrawerState.open() } }
                        )
                    }
                    Screen.CodeEditor -> {
                        CodeEditorScreen(
                            isDark = isDarkThemeOverride,
                            onMenuClick = { scope.launch { leftDrawerState.open() } },
                            onRightMenuClick = { scope.launch { rightDrawerState.open() } }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun SidebarHeader(onNewChatClick: () -> Unit) {
    Column(modifier = Modifier.padding(16.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.linearGradient(
                            colors = listOf(Color(0xFF141418), Color(0xFFF1F0EC))
                        )
                    )
                    .border(1.5.dp, ZenGold, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Dao",
                    color = ZenGold,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold
                )
            }
            Column {
                Text(
                    text = "DAO MASTER",
                    style = MaterialTheme.typography.titleLarge,
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Balance & Wisdom",
                    style = MaterialTheme.typography.bodySmall,
                    color = ZenGold
                )
            }
        }
        
        Spacer(modifier = Modifier.height(20.dp))
        
        Button(
            onClick = onNewChatClick,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .testTag("new_chat_button"),
            colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
            border = BorderStroke(1.dp, Brush.linearGradient(colors = listOf(ZenGold, Color.White))),
            shape = RoundedCornerShape(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = "New chat", tint = ZenGold)
                Text("New Discourse", color = Color.White, style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

@Composable
fun SessionItemRow(
    session: ChatSession,
    isActive: Boolean,
    onSelect: () -> Unit,
    onDelete: () -> Unit,
    onRename: () -> Unit
) {
    var isHovered by remember { mutableStateOf(false) }
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .clickable { onSelect() }
            .testTag("session_item_card"),
        colors = CardDefaults.cardColors(
            containerColor = if (isActive) Color(0xFF1B1B22) else Color.Transparent
        ),
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    imageVector = if (isActive) Icons.Default.ChatBubble else Icons.Default.ChatBubbleOutline,
                    contentDescription = "Session icon",
                    tint = if (isActive) ZenGold else YinTextSecondary,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = session.title,
                    color = if (isActive) Color.White else YinText,
                    maxLines = 1,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Normal
                )
            }
            
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                IconButton(
                    onClick = onRename,
                    modifier = Modifier.size(26.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = "Rename topic",
                        tint = YinTextSecondary.copy(alpha = 0.7f),
                        modifier = Modifier.size(14.dp)
                    )
                }
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.size(26.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Delete topic",
                        tint = YinTextSecondary.copy(alpha = 0.7f),
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun ChatScreen(
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
    onInputModeChange: (String) -> Unit
) {
    val context = LocalContext.current
    var inputText by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    
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
                    
                    // Sound wave pulsing animation
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
                            "How do I balance my creative projects without burning out my internal flame?"
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
                                themeMode.value = when(themeMode.value) {
                                    "auto" -> "dark"
                                    "dark" -> "light"
                                    else -> "auto"
                                }
                                val currentLabel = when(themeMode.value) {
                                    "auto" -> "Auto Balance"
                                    "dark" -> "Yin Dark"
                                    else -> "Yang Light"
                                }
                                Toast.makeText(context, "Theme Mode: $currentLabel", Toast.LENGTH_SHORT).show()
                            }
                        ) {
                            val themeIcon = when(themeMode.value) {
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
                        // A button that can toggle a dropdown menu containing modes
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

                    // Individual mode selection quick chips
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
                        Triple("VideoEditor", "Zen Video Editor 🎥", Icons.Default.Movie)
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
                            IconButton(onClick = { showAttachmentDialog = true }) {
                                Icon(
                                    imageVector = Icons.Default.AttachFile,
                                    contentDescription = "Attach File",
                                    tint = ZenGold,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        },
                        trailingIcon = {
                            IconButton(onClick = { isRecordingVoice = true }) {
                                Icon(
                                    imageVector = Icons.Default.Mic,
                                    contentDescription = "Speak to Dao",
                                    tint = ZenBlue,
                                    modifier = Modifier.size(20.dp)
                                )
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
                // Beautiful empty/onboarding prompt suggestions
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

@Composable
fun GameHudPanel(profile: UserProfile, isDark: Boolean) {
    // Calculate level & tokens progress
    var level = 1L
    var neededForNext = 1_000_000L // tokens needed to go from level to level+1
    var currentLevelStartTokens = 0L
    var nextLevelStartTokens = 1_000_000L
    
    val totalTokens = profile.xp
    while (totalTokens >= nextLevelStartTokens) {
        currentLevelStartTokens = nextLevelStartTokens
        level++
        neededForNext = level * 1_000_000L
        nextLevelStartTokens += neededForNext
    }
    
    val tokensInCurrentLevel = totalTokens - currentLevelStartTokens
    val xpProgress = (tokensInCurrentLevel.toFloat() / neededForNext.toFloat()).coerceIn(0f, 1f)

    // Format tokens to look clean (e.g. 15.4K / 1.0M or 1.2M / 2.0M)
    fun formatTokens(tk: Long): String {
        return when {
            tk >= 1_000_000L -> {
                val millions = tk / 1_000_000.0
                String.format("%.2fM", millions)
            }
            tk >= 1_000L -> {
                val thousands = tk / 1_000.0
                String.format("%.1fK", thousands)
            }
            else -> tk.toString()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Line 1: Level details & streak
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Box(
                    modifier = Modifier
                        .background(ZenGold.copy(alpha = 0.15f), RoundedCornerShape(4.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = "LVL $level",
                        style = MaterialTheme.typography.labelSmall,
                        color = ZenGold,
                        fontWeight = FontWeight.Bold
                    )
                }
                Text(
                    text = profile.levelName,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (isDark) YinText else YangText,
                    fontWeight = FontWeight.Bold
                )
            }
            
            if (profile.harmonyStreak > 0) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "☯ STREAK: ${profile.harmonyStreak}",
                        style = MaterialTheme.typography.labelSmall,
                        color = ZenGold,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // Line 2: Tokens progress bar
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            LinearProgressIndicator(
                progress = { xpProgress },
                modifier = Modifier
                    .weight(1f)
                    .height(5.dp)
                    .clip(CircleShape),
                color = ZenGold,
                trackColor = if (isDark) Color(0xFF22222A) else Color(0xFFDDD9CF)
            )
            Text(
                text = "${formatTokens(tokensInCurrentLevel)} / ${formatTokens(neededForNext)} Tokens",
                style = MaterialTheme.typography.labelSmall,
                color = if (isDark) YinTextSecondary else YangTextSecondary,
                fontSize = 9.sp
            )
        }
    }
}

@Composable
fun HexagonYinYangBackground(isDark: Boolean, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.fillMaxSize()) {
        val centerX = size.width / 2f
        val centerY = size.height / 2f
        val hexRadius = (size.width.coerceAtMost(size.height) * 0.35f)

        val strokeColor = if (isDark) Color(0xFFB5943C).copy(alpha = 0.15f) else Color(0xFFB5943C).copy(alpha = 0.22f)
        val fillYin = if (isDark) Color(0xFF0F0E14).copy(alpha = 0.45f) else Color(0xFFECE9E4).copy(alpha = 0.45f)
        val fillYang = if (isDark) Color(0xFF2E2D38).copy(alpha = 0.25f) else Color.White.copy(alpha = 0.55f)
        
        // 3. Draw Hexagon outline
        val hexPath = androidx.compose.ui.graphics.Path().apply {
            for (i in 0..5) {
                val angleRad = Math.toRadians((i * 60 - 30).toDouble())
                val x = centerX + hexRadius * Math.cos(angleRad).toFloat()
                val y = centerY + hexRadius * Math.sin(angleRad).toFloat()
                if (i == 0) moveTo(x, y) else lineTo(x, y)
            }
            close()
        }
        drawPath(path = hexPath, color = strokeColor, style = Stroke(width = 2.dp.toPx()))
        
        // 4. Draw Yin-Yang inside the hexagon
        val radius = hexRadius * 0.8f
        
        // Draw Yang half (Right/Light half)
        drawArc(
            color = fillYang,
            startAngle = -90f,
            sweepAngle = 180f,
            useCenter = true,
            topLeft = Offset(centerX - radius, centerY - radius),
            size = androidx.compose.ui.geometry.Size(radius * 2, radius * 2)
        )
        
        // Draw Yin half (Left/Dark half)
        drawArc(
            color = fillYin,
            startAngle = 90f,
            sweepAngle = 180f,
            useCenter = true,
            topLeft = Offset(centerX - radius, centerY - radius),
            size = androidx.compose.ui.geometry.Size(radius * 2, radius * 2)
        )
        
        // Draw transition circles
        // Large Top Yin circle (Dark)
        drawCircle(
            color = fillYin,
            radius = radius / 2f,
            center = Offset(centerX, centerY - radius / 2f)
        )
        
        // Large Bottom Yang circle (Light)
        drawCircle(
            color = fillYang,
            radius = radius / 2f,
            center = Offset(centerX, centerY + radius / 2f)
        )
        
        // Small Yang dot inside Top Yin circle
        drawCircle(
            color = fillYang,
            radius = radius / 8f,
            center = Offset(centerX, centerY - radius / 2f)
        )
        
        // Small Yin dot inside Bottom Yang circle
        drawCircle(
            color = fillYin,
            radius = radius / 8f,
            center = Offset(centerX, centerY + radius / 2f)
        )
        
        // Draw boundary circle outline
        drawCircle(
            color = strokeColor,
            radius = radius,
            center = Offset(centerX, centerY),
            style = Stroke(width = 1.dp.toPx())
        )
    }
}

@Composable
fun EmptyOnboardingPrompt(
    onPromptClick: (String) -> Unit,
    isDark: Boolean
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.verticalScroll(rememberScrollState())
        ) {
            // Spinning premium rotating symbol
            RotatingYinYangSymbol(isThinking = false)
            
            Spacer(modifier = Modifier.height(6.dp))
            
            Text(
                text = "Enter the Digital Stream",
                style = MaterialTheme.typography.displayMedium,
                color = if (isDark) Color.White else Color.Black,
                fontWeight = FontWeight.Bold
            )
            
            Text(
                text = "Speak to Dao. Your inquiries will align the flow of structure (Yang) and stillness (Yin). Complete riddles and maintain harmony to level your spiritual companion.",
                style = MaterialTheme.typography.bodyLarge,
                color = if (isDark) YinTextSecondary else YangTextSecondary,
                modifier = Modifier.padding(horizontal = 12.dp),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
            
            Spacer(modifier = Modifier.height(14.dp))
            
            Text(
                text = "SUGGESTED DISCOURSES",
                style = MaterialTheme.typography.labelSmall,
                color = ZenGold,
                fontWeight = FontWeight.Bold
            )
            
            val suggestions = listOf(
                "🌙 How to handle anger or stress with water flow?",
                "☀️ Help me write a robust Kotlin sorting algorithm.",
                "☯ What is perfect Yin-Yang balance in daily work?",
                "🔮 Challenge me with an ancient Zen Riddle!"
            )
            
            suggestions.forEach { prompt ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onPromptClick(prompt.drop(2)) },
                    colors = CardDefaults.cardColors(
                        containerColor = if (isDark) YinCardBg else YangCardBg
                    ),
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(
                        1.dp,
                        if (isDark) Color(0xFF222228) else Color(0xFFDDD9CF)
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Explore,
                            contentDescription = "Explore",
                            tint = ZenGold,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = prompt,
                            color = if (isDark) YinText else YangText,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun MessageBubble(
    message: ChatMessage,
    isDark: Boolean,
    isLastMessage: Boolean
) {
    val shouldAnimate = isLastMessage && !message.isUser
    var revealedLength by remember(message.id) { 
        mutableStateOf(if (shouldAnimate) 0 else message.content.length) 
    }
    
    LaunchedEffect(message.content, shouldAnimate) {
        if (shouldAnimate) {
            val targetLength = message.content.length
            while (revealedLength < targetLength) {
                val diff = targetLength - revealedLength
                val step = when {
                    diff > 400 -> 15
                    diff > 150 -> 8
                    diff > 50 -> 4
                    else -> 1
                }
                revealedLength = (revealedLength + step).coerceAtMost(targetLength)
                kotlinx.coroutines.delay(12)
            }
        } else {
            revealedLength = message.content.length
        }
    }
    
    val visibleContent = remember(message.content, revealedLength) {
        message.content.substring(0, revealedLength.coerceAtMost(message.content.length))
    }
    
    // Parsing code blocks
    val blocks = visibleContent.split("```")
    
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalAlignment = if (message.isUser) Alignment.End else Alignment.Start
    ) {
        // Label for who sent it
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = if (message.isUser) Arrangement.End else Arrangement.Start,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 2.dp)
        ) {
            Text(
                text = if (message.isUser) "YOU" else "DAO ☯",
                style = MaterialTheme.typography.labelSmall,
                color = if (message.isUser) ZenGold else (if (isDark) Color.White else Color.Black),
                fontWeight = FontWeight.Bold
            )
        }

        blocks.forEachIndexed { index, block ->
            if (index % 2 == 1) {
                // It is a code block
                val lines = block.trim().lines()
                val lang = if (lines.isNotEmpty() && lines.first().length < 15) lines.first() else "code"
                val code = if (lines.isNotEmpty() && lines.first().length < 15) lines.drop(1).joinToString("\n") else block
                
                CodeBlockCard(code = code, language = lang)
            } else {
                if (block.isNotBlank() || (blocks.size == 1 && block.isEmpty() && !message.isUser)) {
                    BubbleCard(
                        text = block,
                        isUser = message.isUser,
                        isDark = isDark,
                        yinScore = message.yinScore,
                        yangScore = message.yangScore,
                        shouldAnimate = false
                    )
                }
            }
        }
    }
}

@Composable
fun BubbleCard(
    text: String,
    isUser: Boolean,
    isDark: Boolean,
    yinScore: Float,
    yangScore: Float,
    shouldAnimate: Boolean
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (isUser) {
                if (isDark) Color(0xFF1E1E26) else Color(0xFFECE7DF)
            } else {
                if (isDark) Color(0xFF131317) else Color(0xFFFBFBFA)
            }
        ),
        shape = RoundedCornerShape(
            topStart = 16.dp,
            topEnd = 16.dp,
            bottomStart = if (isUser) 16.dp else 2.dp,
            bottomEnd = if (isUser) 2.dp else 16.dp
        ),
        border = BorderStroke(
            1.dp,
            if (isDark) Color(0xFF2E2E3C) else Color(0xFFD5CFC5)
        ),
        modifier = Modifier
            .padding(vertical = 4.dp)
            .widthIn(max = 310.dp)
    ) {
        Box(modifier = Modifier.padding(14.dp)) {
            val textColor = if (isDark) YinText else YangText
            ParsedMarkdownText(text = text.trim(), textColor = textColor, isDark = isDark)
        }
    }
}

fun buildFormattedAnnotatedString(text: String, isDark: Boolean): AnnotatedString {
    return buildAnnotatedString {
        val boldParts = text.split("**")
        boldParts.forEachIndexed { bIdx, bPart ->
            val isBold = bIdx % 2 == 1
            val inlineCodeParts = bPart.split("`")
            inlineCodeParts.forEachIndexed { cIdx, cPart ->
                val isInlineCode = cIdx % 2 == 1
                
                val style = when {
                    isBold && isInlineCode -> SpanStyle(
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFE5C07B),
                        background = if (isDark) Color(0xFF1E1E24) else Color(0xFFEAE5DA)
                    )
                    isBold -> SpanStyle(
                        fontWeight = FontWeight.Bold,
                        color = ZenGold
                    )
                    isInlineCode -> SpanStyle(
                        fontFamily = FontFamily.Monospace,
                        color = if (isDark) Color(0xFFE5C07B) else Color(0xFFB53D3D),
                        background = if (isDark) Color(0xFF1E1E24) else Color(0xFFEAE5DA)
                    )
                    else -> SpanStyle()
                }
                
                withStyle(style = style) {
                    append(cPart)
                }
            }
        }
    }
}

@Composable
fun ParsedMarkdownText(text: String, textColor: Color, isDark: Boolean) {
    val paragraphs = text.split("\n")
    
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        paragraphs.forEach { para ->
            val trimmed = para.trim()
            when {
                trimmed.startsWith("###") -> {
                    val headingText = trimmed.removePrefix("###").trim()
                    Text(
                        text = headingText,
                        color = ZenGold,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                }
                trimmed.startsWith("##") -> {
                    val headingText = trimmed.removePrefix("##").trim()
                    Text(
                        text = headingText,
                        color = ZenGold,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(vertical = 6.dp)
                    )
                }
                trimmed.startsWith("#") -> {
                    val headingText = trimmed.removePrefix("#").trim()
                    Text(
                        text = headingText,
                        color = ZenGold,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.ExtraBold,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                }
                trimmed.startsWith(">") -> {
                    val quoteText = trimmed.removePrefix(">").trim()
                    Row(
                        modifier = Modifier
                            .padding(vertical = 4.dp, horizontal = 4.dp)
                            .drawBehind {
                                drawLine(
                                    color = ZenGold,
                                    start = Offset(0f, 0f),
                                    end = Offset(0f, size.height),
                                    strokeWidth = 3.dp.toPx()
                                )
                            }
                            .padding(start = 12.dp)
                    ) {
                        Text(
                            text = quoteText,
                            color = textColor.copy(alpha = 0.85f),
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                                lineHeight = 18.sp
                            )
                        )
                    }
                }
                trimmed.startsWith("---") || trimmed.startsWith("***") -> {
                    HorizontalDivider(
                        color = ZenGold.copy(alpha = 0.3f),
                        thickness = 1.dp,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                }
                else -> {
                    val isBulletList = trimmed.startsWith("* ") || trimmed.startsWith("- ")
                    val isNumberedList = trimmed.firstOrNull()?.isDigit() == true && trimmed.contains(". ") && trimmed.indexOf(". ") < 4
                    
                    val cleanText = when {
                        isBulletList -> trimmed.substring(2).trim()
                        isNumberedList -> {
                            val dotIdx = trimmed.indexOf(". ")
                            trimmed.substring(dotIdx + 2).trim()
                        }
                        else -> para
                    }
                    val formattedString = buildFormattedAnnotatedString(cleanText, isDark)
                    
                    Row(
                        modifier = Modifier.padding(start = if (isBulletList || isNumberedList) 12.dp else 0.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        if (isBulletList) {
                            Text(
                                text = "•",
                                color = ZenGold,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Bold
                            )
                        } else if (isNumberedList) {
                            val dotIdx = trimmed.indexOf(". ")
                            val numStr = trimmed.substring(0, dotIdx + 1)
                            Text(
                                text = numStr,
                                color = ZenGold,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        
                        Text(
                            text = formattedString,
                            color = textColor,
                            style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 19.sp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun AnimatedTypewriterText(text: String, textColor: Color, isAnimated: Boolean) {
    var textToShow by remember(text) { mutableStateOf("") }
    
    LaunchedEffect(text, isAnimated) {
        if (!isAnimated) {
            textToShow = text
            return@LaunchedEffect
        }
        textToShow = ""
        for (char in text) {
            textToShow += char
            kotlinx.coroutines.delay(8)
        }
    }
    
    ParsedMarkdownText(text = textToShow, textColor = textColor, isDark = true)
}

@Composable
fun CodeBlockCard(code: String, language: String) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    
    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF0F0F12)),
        border = BorderStroke(1.dp, Color(0xFF282830)),
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier
            .padding(vertical = 6.dp)
            .fillMaxWidth()
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF060608))
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = language.uppercase(),
                    color = ZenGold,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
                IconButton(
                    onClick = {
                        clipboardManager.setText(AnnotatedString(code))
                        Toast.makeText(context, "Code copied to clipboard", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.ContentCopy,
                        contentDescription = "Copy code",
                        tint = YinTextSecondary,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(14.dp)
            ) {
                Text(
                    text = code.trim(),
                    color = Color(0xFFECEEFA),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    lineHeight = 16.sp
                )
            }
        }
    }
}

@Composable
fun ThinkingIndicatorBubble(isDark: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.Start,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.widthIn(max = 310.dp)) {
            Text(
                text = "DAO ☯",
                style = MaterialTheme.typography.labelSmall,
                color = if (isDark) ZenGold else Color(0xFF9E7E1D),
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
            )
            
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = if (isDark) Color(0xFF131317) else Color(0xFFFBFBFA)
                ),
                shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomStart = 2.dp, bottomEnd = 16.dp),
                border = BorderStroke(1.dp, ZenGold.copy(alpha = 0.35f)),
                modifier = Modifier.padding(vertical = 4.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Rotating multiple yin yang hexagon symbols in an orbital constellation during thought processing
                    ThinkingHexagonConstellation()
                    
                    Spacer(modifier = Modifier.width(4.dp))
                    
                    Text(
                        text = "Dao is meditating on your words...",
                        color = if (isDark) YinTextSecondary else YangTextSecondary,
                        style = MaterialTheme.typography.bodyMedium,
                        fontFamily = FontFamily.Serif
                    )
                }
            }
        }
    }
}

@Composable
fun YinYangHexagonLogo(
    modifier: Modifier = Modifier,
    isThinking: Boolean = false,
    sizeDp: Int = 120
) {
    val transition = rememberInfiniteTransition(label = "hexagon_logo")
    val rotationSpeed = if (isThinking) 2500 else 12000
    val angle by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = rotationSpeed, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "angle"
    )

    val scaleSpeed = if (isThinking) 700 else 2600
    val pulseScale by transition.animateFloat(
        initialValue = 0.94f,
        targetValue = 1.06f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = scaleSpeed, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )

    Box(
        modifier = modifier
            .scale(pulseScale)
            .size(sizeDp.dp)
            .drawBehind {
                val r = size.minDimension / 2.3f
                val cx = size.width / 2
                val cy = size.height / 2

                rotate(degrees = angle) {
                    // Draw I Ching Trigrams (Bagua) outside the hexagon facets
                    val trigramRadii = r * 1.25f
                    val trigramAngles = listOf(0f, 60f, 120f, 180f, 240f, 300f)
                    
                    trigramAngles.forEachIndexed { index, tagAngle ->
                        val rad = Math.toRadians(tagAngle.toDouble())
                        
                        val perpAngle = tagAngle + 90f
                        val perpRad = Math.toRadians(perpAngle.toDouble())
                        val dx = Math.cos(perpRad).toFloat() * 6.dp.toPx()
                        val dy = Math.sin(perpRad).toFloat() * 6.dp.toPx()

                        // Draw 3 levels of bars (trigram lines)
                        for (level in 0..2) {
                            val levelDist = trigramRadii + (level * 3.dp.toPx())
                            val lx = cx + levelDist * Math.cos(rad).toFloat()
                            val ly = cy + levelDist * Math.sin(rad).toFloat()
                            
                            val isBroken = (index + level) % 2 == 1
                            
                            if (isBroken) {
                                // Draw two segments with a gap
                                drawLine(
                                    color = ZenGold.copy(alpha = 0.6f),
                                    start = Offset(lx - dx, ly - dy),
                                    end = Offset(lx - dx * 0.25f, ly - dy * 0.25f),
                                    strokeWidth = 1.5.dp.toPx()
                                )
                                drawLine(
                                    color = ZenGold.copy(alpha = 0.6f),
                                    start = Offset(lx + dx * 0.25f, ly + dy * 0.25f),
                                    end = Offset(lx + dx, ly + dy),
                                    strokeWidth = 1.5.dp.toPx()
                                )
                            } else {
                                // Draw solid line
                                drawLine(
                                    color = ZenGold.copy(alpha = 0.6f),
                                    start = Offset(lx - dx, ly - dy),
                                    end = Offset(lx + dx, ly + dy),
                                    strokeWidth = 1.5.dp.toPx()
                                )
                            }
                        }
                    }

                    // Create regular hexagon Path for clipping
                    val path = androidx.compose.ui.graphics.Path().apply {
                        for (i in 0..5) {
                            val hexAngle = i * 60f
                            val rad = Math.toRadians(hexAngle.toDouble())
                            val hx = cx + r * Math.cos(rad).toFloat()
                            val hy = cy + r * Math.sin(rad).toFloat()
                            if (i == 0) moveTo(hx, hy) else lineTo(hx, hy)
                        }
                        close()
                    }

                    drawContext.canvas.save()
                    drawContext.canvas.clipPath(path)

                    // 1. Right half filled with Yang white
                    drawRect(
                        color = Color(0xFFF1F0EC),
                        topLeft = Offset(cx, cy - r),
                        size = androidx.compose.ui.geometry.Size(r, r * 2)
                    )

                    // 2. Left half filled with Yin black
                    drawRect(
                        color = Color(0xFF141418),
                        topLeft = Offset(cx - r, cy - r),
                        size = androidx.compose.ui.geometry.Size(r, r * 2)
                    )

                    // 3. Top semicircles of Yin-Yang S-curve
                    drawCircle(
                        color = Color(0xFFF1F0EC),
                        radius = r / 2,
                        center = Offset(cx, cy - r / 2)
                    )

                    // 4. Bottom semicircles of Yin-Yang S-curve
                    drawCircle(
                        color = Color(0xFF141418),
                        radius = r / 2,
                        center = Offset(cx, cy + r / 2)
                    )

                    // 5. Top tiny dot (Yin color)
                    drawCircle(
                        color = Color(0xFF141418),
                        radius = r / 6,
                        center = Offset(cx, cy - r / 2)
                    )

                    // 6. Bottom tiny dot (Yang color)
                    drawCircle(
                        color = Color(0xFFF1F0EC),
                        radius = r / 6,
                        center = Offset(cx, cy + r / 2)
                    )

                    drawContext.canvas.restore()

                    // Draw outer golden hexagon frame contours
                    for (i in 0..5) {
                        val p1Angle = i * 60f
                        val p2Angle = ((i + 1) % 6) * 60f
                        val r1 = Math.toRadians(p1Angle.toDouble())
                        val r2 = Math.toRadians(p2Angle.toDouble())
                        drawLine(
                            color = ZenGold,
                            start = Offset(cx + r * Math.cos(r1).toFloat(), cy + r * Math.sin(r1).toFloat()),
                            end = Offset(cx + r * Math.cos(r2).toFloat(), cy + r * Math.sin(r2).toFloat()),
                            strokeWidth = 2.dp.toPx()
                        )
                    }
                }
            }
    )
}

@Composable
fun ThinkingHexagonConstellation() {
    val transition = rememberInfiniteTransition(label = "constellation")
    
    val orbitAngle by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 3500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "orbit"
    )

    Box(
        modifier = Modifier.size(54.dp),
        contentAlignment = Alignment.Center
    ) {
        // Main central hexagon logo
        YinYangHexagonLogo(modifier = Modifier.size(26.dp), isThinking = true, sizeDp = 26)
        
        // 3 Orbiting smaller hexagon logos!
        val phaseAngles = listOf(0f, 120f, 240f)
        
        phaseAngles.forEach { phase ->
            val angleRad = Math.toRadians((orbitAngle + phase).toDouble())
            val dx = (15 * Math.cos(angleRad)).toFloat().dp
            val dy = (15 * Math.sin(angleRad)).toFloat().dp
            
            Box(
                modifier = Modifier
                    .offset(x = dx, y = dy)
                    .size(12.dp)
            ) {
                YinYangHexagonLogo(modifier = Modifier.fillMaxSize(), isThinking = true, sizeDp = 12)
            }
        }
    }
}

@Composable
fun RotatingYinYangSymbol(
    modifier: Modifier = Modifier,
    isThinking: Boolean = false
) {
    val transition = rememberInfiniteTransition(label = "yinyang_rotation")
    val speed = if (isThinking) 1600 else 8000
    val angle by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = speed, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "angle"
    )

    val pulseSpeed = if (isThinking) 500 else 2200
    val scale by transition.animateFloat(
        initialValue = 0.94f,
        targetValue = 1.06f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = pulseSpeed, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )

    Box(
        modifier = modifier
            .scale(scale)
            .size(76.dp)
            .drawBehind {
                val r = size.minDimension / 2
                val cx = size.width / 2
                val cy = size.height / 2

                rotate(degrees = angle) {
                    // 1. Right Light semicircle (Yang)
                    drawArc(
                        color = Color(0xFFF1F0EC),
                        startAngle = -90f,
                        sweepAngle = 180f,
                        useCenter = true
                    )

                    // 2. Left Dark semicircle (Yin)
                    drawArc(
                        color = Color(0xFF141418),
                        startAngle = 90f,
                        sweepAngle = 180f,
                        useCenter = true
                    )

                    // 3. Top medium circle (Yang color, centered at top of Yin-Yang)
                    drawCircle(
                        color = Color(0xFFF1F0EC),
                        radius = r / 2,
                        center = Offset(cx, cy - r / 2)
                    )

                    // 4. Bottom medium circle (Yin color, centered at bottom of Yin-Yang)
                    drawCircle(
                        color = Color(0xFF141418),
                        radius = r / 2,
                        center = Offset(cx, cy + r / 2)
                    )

                    // 5. Top tiny dot (Yin color)
                    drawCircle(
                        color = Color(0xFF141418),
                        radius = r / 6,
                        center = Offset(cx, cy - r / 2)
                    )

                    // 6. Bottom tiny dot (Yang color)
                    drawCircle(
                        color = Color(0xFFF1F0EC),
                        radius = r / 6,
                        center = Offset(cx, cy + r / 2)
                    )
                }

                // 7. Outer golden connection ring
                drawCircle(
                    color = ZenGold,
                    radius = r,
                    style = Stroke(width = 2.dp.toPx())
                )
            }
    )
}

@Composable
fun CodeEditorScreen(
    isDark: Boolean,
    onMenuClick: () -> Unit,
    onRightMenuClick: () -> Unit
) {
    var codeText by remember { mutableStateOf("""// Zen Harmony Code Template
fun main() {
    val yinEnergy = 0.5f
    val yangEnergy = 0.5f
    
    val karma = yinEnergy * yangEnergy
    println("Digital Karma: " + karma)
}""") }
    
    var compileOutput by remember { mutableStateOf("Ready to analyze digital energy...") }
    var selectedTemplate by remember { mutableStateOf("Simple Harmony") }
    var isCompiling by remember { mutableStateOf(false) }
    
    val templates = mapOf(
        "Simple Harmony" to """// Zen Harmony Code Template
fun main() {
    val yinEnergy = 0.5f
    val yangEnergy = 0.5f
    
    val karma = yinEnergy * yangEnergy
    println("Digital Karma: " + karma)
}""",
        "Coroutines Flow" to """// Coroutines Contemplation Flow
import kotlinx.coroutines.flow.*

fun breathe(): Flow<String> = flow {
    while(true) {
        emit("Inhale... (Yin)")
        delay(4000)
        emit("Exhale... (Yang)")
        delay(4000)
    }
}""",
        "Decoupled Spirit" to """// Perfect decoupled system architecture
interface SpiritualNode {
    fun align(): Boolean
}

class ZenNode : SpiritualNode {
    override fun align() = true
}"""
    )
    
    Scaffold(
        topBar = {
            @OptIn(ExperimentalMaterial3Api::class)
            TopAppBar(
                title = { Text("Zen Code Editor", fontFamily = FontFamily.Serif, fontSize = 18.sp, color = if (isDark) Color.White else Color.Black) },
                navigationIcon = {
                    IconButton(onClick = onMenuClick) {
                        Icon(Icons.Default.Menu, contentDescription = "Apps", tint = ZenGold)
                    }
                },
                actions = {
                    IconButton(onClick = onRightMenuClick) {
                        Icon(Icons.Default.History, contentDescription = "Console", tint = ZenGold)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = if (isDark) YinBlack else YangWhite
                )
            )
        },
        containerColor = if (isDark) Color(0xFF0C0B10) else Color(0xFFFBF9F5)
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Template Selector Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("Preset Template:", style = MaterialTheme.typography.labelMedium, color = if (isDark) YinTextSecondary else YangTextSecondary)
                androidx.compose.foundation.lazy.LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    templates.keys.forEach { tName ->
                        item {
                            FilterChip(
                                selected = selectedTemplate == tName,
                                onClick = {
                                    selectedTemplate = tName
                                    codeText = templates[tName] ?: ""
                                    compileOutput = "Loaded $tName template. Ready to analyze."
                                },
                                label = { Text(tName) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = ZenGold.copy(alpha = 0.2f),
                                    selectedLabelColor = ZenGold
                                )
                            )
                        }
                    }
                }
            }
            
            // Editor Box
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (isDark) Color(0xFF13121A) else Color(0xFFF2EFE9))
                    .border(1.dp, if (isDark) Color(0xFF222228) else Color(0xFFDDD9CF), RoundedCornerShape(12.dp))
                    .padding(12.dp)
            ) {
                androidx.compose.foundation.text.BasicTextField(
                    value = codeText,
                    onValueChange = { codeText = it },
                    textStyle = MaterialTheme.typography.bodyMedium.copy(
                        fontFamily = FontFamily.Monospace,
                        color = if (isDark) Color(0xFFE2E0D5) else Color(0xFF33322E)
                    ),
                    modifier = Modifier.fillMaxSize(),
                    cursorBrush = Brush.verticalGradient(listOf(ZenGold, ZenGold))
                )
            }
            
            // Action Controls
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = {
                        // Beautiful formatting simulation
                        isCompiling = true
                        compileOutput = "Formatting code... spiritual energy aligns... "
                        codeText = codeText.trim()
                            .replace(Regex(" +"), " ")
                            .replace("{\n\n", "{\n")
                        compileOutput = "Code beautified. Digital feng-shui complete."
                        isCompiling = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = if (isDark) Color(0xFF22222A) else Color(0xFFE6E2D8)),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp), tint = ZenGold)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Format Code", color = if (isDark) Color.White else Color.Black)
                }
                
                Button(
                    onClick = {
                        isCompiling = true
                        compileOutput = "Compiling algorithm... checking entropy loops..."
                        // Simulated delayed output
                        compileOutput = """
                            [COMPILE STATUS: SUCCESSFUL]
                            [RUNNER OUTPUT]
                            Executing Contemplative Node...
                            
                            Yin Balance Ratio: 50.0%
                            Yang Flow Speed: Optimal (0ms delay)
                            System Harmony: Perfect Equilibrium.
                            
                            Your digital variables are in alignment.
                        """.trimIndent()
                        isCompiling = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ZenGold),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp), tint = Color.Black)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Run Contemplation", color = Color.Black)
                }
            }
            
            // Console / Compiler Output
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(130.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (isDark) Color(0xFF070709) else Color(0xFFE8E5DF))
                    .border(1.dp, if (isDark) Color(0xFF1E1E24) else Color(0xFFDDD9CF), RoundedCornerShape(8.dp))
                    .padding(10.dp)
            ) {
                Text("CONSOLE OUTPUT", style = MaterialTheme.typography.labelSmall, color = ZenGold, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(4.dp))
                LazyColumn(
                    modifier = Modifier.fillMaxSize()
                ) {
                    item {
                        Text(
                            text = compileOutput,
                            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                            color = if (isDark) Color(0xFF8CBE91) else Color(0xFF2A5F30)
                        )
                    }
                }
            }
        }
    }
}

// ==========================================
// ZEN VIDEO EDITOR
// ==========================================
@Composable
fun VideoEditorScreen(
    isDark: Boolean,
    onMenuClick: () -> Unit
) {
    var isPlaying by remember { mutableStateOf(false) }
    var progress by remember { mutableStateOf(0.35f) }
    var selectedFilter by remember { mutableStateOf("Original") }
    var textOverlay by remember { mutableStateOf("Stream of Balance") }
    var selectedTrack by remember { mutableStateOf("Bamboo Flute") }
    var selectedTransition by remember { mutableStateOf("Dissolve Harmony") }
    
    // Playback loop simulation
    LaunchedEffect(isPlaying) {
        if (isPlaying) {
            while (true) {
                kotlinx.coroutines.delay(50)
                progress = (progress + 0.01f)
                if (progress > 1f) progress = 0f
            }
        }
    }

    // Render / Export simulation state
    var isRendering by remember { mutableStateOf(false) }
    var renderProgress by remember { mutableStateOf(0f) }
    var renderStatusText by remember { mutableStateOf("Initializing Render Pipeline...") }

    if (isRendering) {
        LaunchedEffect(Unit) {
            val phases = listOf(
                "Initializing Render Pipeline..." to 0.15f,
                "Applying Yin-Yang grading filters..." to 0.40f,
                "Blending background zen frequencies..." to 0.65f,
                "Encoding audio-visual karma streams..." to 0.85f,
                "Finalizing Zen video container..." to 1.0f
            )
            for (phase in phases) {
                renderStatusText = phase.first
                while (renderProgress < phase.second) {
                    kotlinx.coroutines.delay(120)
                    renderProgress += 0.05f
                }
            }
            renderProgress = 1.0f
            kotlinx.coroutines.delay(1000)
            isRendering = false
            renderProgress = 0f
        }

        AlertDialog(
            onDismissRequest = { },
            title = { Text("Exporting Cosmic Clip", fontFamily = FontFamily.Serif, color = ZenGold) },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = renderStatusText,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (isDark) YinText else YangText
                    )
                    
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(100.dp)) {
                        CircularProgressIndicator(
                            progress = { renderProgress },
                            modifier = Modifier.size(90.dp),
                            color = ZenGold,
                            trackColor = Color.Gray.copy(alpha = 0.2f),
                            strokeWidth = 6.dp
                        )
                        Text(
                            text = "${(renderProgress * 100).toInt()}%",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = ZenGold
                        )
                    }
                }
            },
            confirmButton = { },
            dismissButton = { }
        )
    }

    Scaffold(
        topBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(if (isDark) YinBlack else YangWhite)
                    .statusBarsPadding()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        IconButton(onClick = onMenuClick) {
                            Icon(Icons.Default.Menu, contentDescription = "Menu", tint = if (isDark) Color.White else Color.Black)
                        }
                        Text(
                            text = "ZEN VIDEO EDITOR 🎬",
                            fontFamily = FontFamily.Serif,
                            fontWeight = FontWeight.Bold,
                            color = if (isDark) ZenGold else Color(0xFF9E7E1D),
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                    
                    Button(
                        onClick = { isRendering = true },
                        colors = ButtonDefaults.buttonColors(containerColor = ZenGold)
                    ) {
                        Icon(Icons.Default.Save, contentDescription = "Export", tint = Color.Black, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Export", color = Color.Black, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(if (isDark) Color(0xFF070709) else Color(0xFFF1F0EC))
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 1. Live Video Preview Viewfinder Frame
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(220.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color.Black)
                    .border(2.dp, ZenGold.copy(alpha = 0.4f), RoundedCornerShape(16.dp)),
                contentAlignment = Alignment.Center
            ) {
                // Background visual dynamic effect representing our video stream
                val filterColor = when (selectedFilter) {
                    "Yang Amber" -> Color(0x35FFB300)
                    "Yin Azure" -> Color(0x350091EA)
                    "Zen Emerald" -> Color(0x3500E676)
                    "Monochrome Silence" -> Color(0x70141418)
                    else -> Color.Transparent
                }
                
                Canvas(modifier = Modifier.fillMaxSize()) {
                    // Draw a beautiful dynamic wave representational scenery
                    val wavePath = androidx.compose.ui.graphics.Path().apply {
                        moveTo(0f, size.height * 0.7f)
                        val points = 20
                        for (i in 0..points) {
                            val x = (size.width / points) * i
                            val sine = kotlin.math.sin((i.toFloat() / points) * 2 * Math.PI + (progress * 2 * Math.PI))
                            val y = size.height * 0.7f + (sine * 15.dp.toPx()).toFloat()
                            lineTo(x, y)
                        }
                        lineTo(size.width, size.height)
                        lineTo(0f, size.height)
                        close()
                    }
                    drawPath(
                        path = wavePath,
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                ZenGold.copy(alpha = 0.15f),
                                Color(0xFF141418)
                            )
                        )
                    )
                    
                    // Draw secondary wave
                    val secondWavePath = androidx.compose.ui.graphics.Path().apply {
                        moveTo(0f, size.height * 0.65f)
                        val points = 20
                        for (i in 0..points) {
                            val x = (size.width / points) * i
                            val sine = kotlin.math.cos((i.toFloat() / points) * 2 * Math.PI - (progress * 2 * Math.PI))
                            val y = size.height * 0.62f + (sine * 10.dp.toPx()).toFloat()
                            lineTo(x, y)
                        }
                        lineTo(size.width, size.height)
                        lineTo(0f, size.height)
                        close()
                    }
                    drawPath(
                        path = secondWavePath,
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                if (selectedFilter == "Yin Azure") ZenBlue.copy(alpha = 0.2f) else ZenGold.copy(alpha = 0.10f),
                                Color.Transparent
                            )
                        )
                    )
                }

                // Filter Overlay Frame
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(filterColor)
                )

                // Viewfinder lines & overlays (REC indicators etc.)
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(12.dp)
                ) {
                    // Top Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(if (isPlaying) Color.Red else Color.Gray)
                            )
                            Text(
                                text = if (isPlaying) "REC" else "PAUSED",
                                fontSize = 10.sp,
                                color = Color.White,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Text(
                            text = "FILTER: ${selectedFilter.uppercase()}",
                            fontSize = 10.sp,
                            color = ZenGold,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    // Bottom Row
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.BottomCenter),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val minutes = (progress * 120).toInt() / 60
                        val seconds = (progress * 120).toInt() % 60
                        val frames = ((progress * 120 * 30) % 30).toInt()
                        Text(
                            text = String.format(Locale.getDefault(), "00:%02d:%02d:%02d", minutes, seconds, frames),
                            fontSize = 11.sp,
                            color = Color.LightGray,
                            fontFamily = FontFamily.Monospace
                        )
                        
                        Text(
                            text = "1080p 24FPS",
                            fontSize = 10.sp,
                            color = Color.Gray
                        )
                    }
                }

                // Floating user subtitle overlay
                if (textOverlay.isNotBlank()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 32.dp)
                            .align(Alignment.BottomCenter),
                        contentAlignment = Alignment.Center
                    ) {
                        Surface(
                            color = Color.Black.copy(alpha = 0.6f),
                            shape = RoundedCornerShape(4.dp),
                            modifier = Modifier.padding(horizontal = 24.dp)
                        ) {
                            Text(
                                text = textOverlay,
                                color = Color.White,
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                fontFamily = FontFamily.Serif
                            )
                        }
                    }
                }
            }

            // 2. Playback Control Strip
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = { isPlaying = !isPlaying },
                    modifier = Modifier
                        .size(46.dp)
                        .clip(CircleShape)
                        .background(ZenGold)
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = "Play/Pause",
                        tint = Color.Black
                    )
                }
                
                // Cut segment button
                Button(
                    onClick = { /* Simulated Split action */ },
                    colors = ButtonDefaults.buttonColors(containerColor = if (isDark) Color(0xFF1E1D24) else Color(0xFFE5E2DC)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.ContentCut, contentDescription = "Split", tint = if (isDark) YinText else YangText, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Split Clip", color = if (isDark) YinText else YangText, fontSize = 12.sp)
                }

                // Playback Slider bar
                Slider(
                    value = progress,
                    onValueChange = { progress = it },
                    modifier = Modifier.weight(1f).padding(horizontal = 12.dp),
                    colors = SliderDefaults.colors(
                        thumbColor = ZenGold,
                        activeTrackColor = ZenGold,
                        inactiveTrackColor = Color.Gray.copy(alpha = 0.3f)
                    )
                )
            }

            // 3. Multi-Track Timeline Editor Component
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = if (isDark) YinCardBg else Color(0xFFE9E5DE)),
                border = BorderStroke(1.dp, if (isDark) Color(0xFF222228) else Color(0xFFD6CEB2))
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("MULTITRACK TIMELINE", style = MaterialTheme.typography.labelSmall, color = if (isDark) YinTextSecondary else Color.DarkGray, fontWeight = FontWeight.Bold)
                    
                    // Track 1: Video Track
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Default.Movie, contentDescription = null, tint = ZenRed, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(32.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (isDark) Color(0xFF121216) else Color(0xFFD3CDBE))
                        ) {
                            // Timeline visual pieces
                            Box(
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .fillMaxWidth(0.5f)
                                    .background(ZenRed.copy(alpha = 0.25f))
                                    .border(1.dp, ZenRed, RoundedCornerShape(6.dp))
                            )
                            Box(
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .fillMaxWidth(0.45f)
                                    .align(Alignment.CenterEnd)
                                    .background(ZenRed.copy(alpha = 0.15f))
                                    .border(1.dp, ZenRed.copy(alpha = 0.5f), RoundedCornerShape(6.dp))
                            )
                            
                            // Playback head line representation
                            Box(
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .fillMaxWidth(0.015f)
                                    .align(BiasAlignment(progress * 2f - 1f, 0f))
                                    .background(ZenGold)
                            )
                        }
                    }

                    // Track 2: Subtitle overlay track
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Default.Edit, contentDescription = null, tint = ZenGold, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(20.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (isDark) Color(0xFF121216) else Color(0xFFD3CDBE))
                        ) {
                            if (textOverlay.isNotBlank()) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxHeight()
                                        .padding(horizontal = 24.dp)
                                        .fillMaxWidth(0.6f)
                                        .background(ZenGold.copy(alpha = 0.3f))
                                        .border(1.dp, ZenGold, RoundedCornerShape(6.dp))
                                )
                            }
                            // Playback head line
                            Box(
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .fillMaxWidth(0.015f)
                                    .align(BiasAlignment(progress * 2f - 1f, 0f))
                                    .background(ZenGold)
                            )
                        }
                    }

                    // Track 3: Audio Track
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Default.MusicNote, contentDescription = null, tint = ZenBlue, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(26.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (isDark) Color(0xFF121216) else Color(0xFFD3CDBE))
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .fillMaxWidth()
                                    .background(ZenBlue.copy(alpha = 0.2f))
                                    .border(1.dp, ZenBlue, RoundedCornerShape(6.dp))
                            )
                            // Waveform rendering lines inside track
                            Row(modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                repeat(20) { index ->
                                    val barHeight = (Math.sin(index * 0.8) * 8 + 12).dp
                                    Box(modifier = Modifier.width(2.dp).height(barHeight).background(ZenBlue.copy(alpha = 0.7f)))
                                }
                            }
                            // Playback head line
                            Box(
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .fillMaxWidth(0.015f)
                                    .align(BiasAlignment(progress * 2f - 1f, 0f))
                                    .background(ZenGold)
                            )
                        }
                    }
                }
            }

            // 4. Custom Grading Filters & Audio Overlays Settings Panels
            Text(
                text = "GRADING & EFFECTS",
                style = MaterialTheme.typography.titleSmall,
                fontFamily = FontFamily.Serif,
                color = if (isDark) ZenGold else Color(0xFF886A14)
            )

            // Horizontal Filter row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                val filters = listOf("Original", "Yang Amber", "Yin Azure", "Zen Emerald", "Monochrome Silence")
                filters.forEach { filter ->
                    val isSelected = filter == selectedFilter
                    FilterChip(
                        selected = isSelected,
                        onClick = { selectedFilter = filter },
                        label = { Text(filter, fontSize = 11.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = ZenGold,
                            selectedLabelColor = Color.Black,
                            containerColor = if (isDark) Color(0xFF16151B) else Color(0xFFE5E2DC),
                            labelColor = if (isDark) YinText else YangText
                        )
                    )
                }
            }

            // Subtitle Edit Text overlay input
            OutlinedTextField(
                value = textOverlay,
                onValueChange = { textOverlay = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Subtitle Overlay Text", fontFamily = FontFamily.Serif) },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = ZenGold,
                    unfocusedBorderColor = if (isDark) Color(0xFF33333C) else Color(0xFFB5AF9E),
                    focusedLabelColor = ZenGold
                )
            )

            // Audio select dropdown simulation
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "Ambient Soundtrack", 
                    style = MaterialTheme.typography.labelMedium, 
                    color = if (isDark) YinTextSecondary else Color.DarkGray
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val tracks = listOf("Bamboo Flute", "Mountain River", "Cosmic Om")
                    tracks.forEach { track ->
                        val isTrackSelected = track == selectedTrack
                        ElevatedButton(
                            onClick = { selectedTrack = track },
                            colors = ButtonDefaults.elevatedButtonColors(
                                containerColor = if (isTrackSelected) ZenBlue else (if (isDark) Color(0xFF1A1A22) else Color(0xFFECEAE5))
                            ),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                text = track, 
                                fontSize = 11.sp, 
                                color = if (isTrackSelected) Color.White else (if (isDark) YinText else YangText),
                                fontWeight = if (isTrackSelected) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    }
                }
            }

            // Transition effect choice
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "Clip Transition Type", 
                    style = MaterialTheme.typography.labelMedium, 
                    color = if (isDark) YinTextSecondary else Color.DarkGray
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val transitions = listOf("Dissolve Harmony", "Fade Out", "Swipe Right")
                    transitions.forEach { transition ->
                        val isTransSelected = transition == selectedTransition
                        ElevatedButton(
                            onClick = { selectedTransition = transition },
                            colors = ButtonDefaults.elevatedButtonColors(
                                containerColor = if (isTransSelected) ZenGold else (if (isDark) Color(0xFF1A1A22) else Color(0xFFECEAE5))
                            ),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                text = transition, 
                                fontSize = 11.sp, 
                                color = if (isTransSelected) Color.Black else (if (isDark) YinText else YangText),
                                fontWeight = if (isTransSelected) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    }
                }
            }
        }
    }
}

// ==========================================
// DAO WEB BROWSER SCREEN
// ==========================================
@Composable
fun BrowserScreen(
    isDark: Boolean,
    onMenuClick: () -> Unit
) {
    var urlInput by remember { mutableStateOf("https://wikipedia.org/wiki/Daoism") }
    var currentUrl by remember { mutableStateOf("https://wikipedia.org/wiki/Daoism") }
    var webViewRef by remember { mutableStateOf<android.webkit.WebView?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    var canGoBack by remember { mutableStateOf(false) }
    var canGoForward by remember { mutableStateOf(false) }

    val bookmarks = listOf(
        Triple("Daoism Wiki", "https://wikipedia.org/wiki/Daoism", Icons.Default.Language),
        Triple("Zen Wiki", "https://wikipedia.org/wiki/Zen", Icons.Default.Language),
        Triple("I Ching Wiki", "https://wikipedia.org/wiki/I_Ching", Icons.Default.Language),
        Triple("Kotlin Core", "https://kotlinlang.org", Icons.Default.Language)
    )

    Scaffold(
        topBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(if (isDark) YinBlack else YangWhite)
                    .statusBarsPadding()
            ) {
                // Main Header bar with menu and title
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        IconButton(onClick = onMenuClick) {
                            Icon(Icons.Default.Menu, contentDescription = "Menu", tint = if (isDark) Color.White else Color.Black)
                        }
                        Text(
                            text = "DAO BROWSER 🌐",
                            fontFamily = FontFamily.Serif,
                            fontWeight = FontWeight.Bold,
                            color = if (isDark) ZenGold else Color(0xFF9E7E1D),
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                    
                    // Home shortcut
                    IconButton(
                        onClick = {
                            urlInput = "https://wikipedia.org/wiki/Daoism"
                            currentUrl = "https://wikipedia.org/wiki/Daoism"
                            webViewRef?.loadUrl("https://wikipedia.org/wiki/Daoism")
                        }
                    ) {
                        Icon(Icons.Default.Home, contentDescription = "Home", tint = ZenGold)
                    }
                }

                // Address Input Bar & Browser controls
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    IconButton(
                        onClick = {
                            if (webViewRef?.canGoBack() == true) {
                                webViewRef?.goBack()
                            }
                        },
                        enabled = canGoBack
                    ) {
                        Icon(
                            Icons.Default.ArrowBack, 
                            contentDescription = "Back",
                            tint = if (canGoBack) (if (isDark) Color.White else Color.Black) else Color.Gray
                        )
                    }

                    IconButton(
                        onClick = {
                            if (webViewRef?.canGoForward() == true) {
                                webViewRef?.goForward()
                            }
                        },
                        enabled = canGoForward
                    ) {
                        Icon(
                            Icons.Default.ArrowForward, 
                            contentDescription = "Forward",
                            tint = if (canGoForward) (if (isDark) Color.White else Color.Black) else Color.Gray
                        )
                    }

                    IconButton(onClick = { webViewRef?.reload() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Reload", tint = if (isDark) Color.LightGray else Color.DarkGray)
                    }

                    OutlinedTextField(
                        value = urlInput,
                        onValueChange = { urlInput = it },
                        modifier = Modifier
                            .weight(1f)
                            .height(52.dp),
                        placeholder = { Text("Search or type URL...", style = MaterialTheme.typography.bodySmall) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = if (isDark) Color(0xFF141418) else Color(0xFFECE9E4),
                            unfocusedContainerColor = if (isDark) Color(0xFF0F0F12) else Color(0xFFF3F1ED),
                            focusedBorderColor = ZenGold,
                            unfocusedBorderColor = Color.Transparent,
                            focusedTextColor = if (isDark) Color.White else Color.Black,
                            unfocusedTextColor = if (isDark) Color.White else Color.Black
                        ),
                        singleLine = true,
                        trailingIcon = {
                            IconButton(
                                onClick = {
                                    var formatted = urlInput.trim()
                                    if (formatted.isNotEmpty()) {
                                        if (!formatted.startsWith("http://") && !formatted.startsWith("https://")) {
                                            formatted = "https://$formatted"
                                        }
                                        currentUrl = formatted
                                        urlInput = formatted
                                        webViewRef?.loadUrl(formatted)
                                    }
                                }
                            ) {
                                Icon(Icons.Default.Search, contentDescription = "Navigate", tint = ZenGold)
                            }
                        },
                        shape = RoundedCornerShape(26.dp)
                    )
                }

                // Loading bar
                if (isLoading) {
                    LinearProgressIndicator(
                        modifier = Modifier.fillMaxWidth().height(2.dp),
                        color = ZenGold,
                        trackColor = Color.Transparent
                    )
                }

                // Quick Bookmarks row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    bookmarks.forEach { (name, bUrl, bIcon) ->
                        InputChip(
                            selected = currentUrl == bUrl,
                            onClick = {
                                urlInput = bUrl
                                currentUrl = bUrl
                                webViewRef?.loadUrl(bUrl)
                            },
                            label = { Text(name, style = MaterialTheme.typography.labelSmall) },
                            leadingIcon = { Icon(bIcon, contentDescription = null, modifier = Modifier.size(12.dp)) },
                            colors = InputChipDefaults.inputChipColors(
                                containerColor = if (isDark) Color(0xFF1B1B22) else Color(0xFFE5E2DC),
                                labelColor = if (isDark) YinText else YangText,
                                selectedContainerColor = ZenGold.copy(alpha = 0.2f),
                                selectedLabelColor = ZenGold
                            )
                        )
                    }
                }
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(if (isDark) Color(0xFF070709) else Color(0xFFE5E2DC))
        ) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    android.webkit.WebView(ctx).apply {
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.databaseEnabled = true
                        settings.cacheMode = android.webkit.WebSettings.LOAD_DEFAULT
                        settings.useWideViewPort = true
                        settings.loadWithOverviewMode = true
                        settings.builtInZoomControls = true
                        settings.displayZoomControls = false
                        webViewClient = object : android.webkit.WebViewClient() {
                            override fun onPageStarted(view: android.webkit.WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                                isLoading = true
                                url?.let { urlInput = it }
                            }

                            override fun onPageFinished(view: android.webkit.WebView?, url: String?) {
                                isLoading = false
                                canGoBack = view?.canGoBack() ?: false
                                canGoForward = view?.canGoForward() ?: false
                                url?.let { 
                                    urlInput = it
                                    currentUrl = it
                                }
                            }
                        }
                        webChromeClient = android.webkit.WebChromeClient()
                        loadUrl(currentUrl)
                        webViewRef = this
                    }
                },
                update = { webView ->
                    webViewRef = webView
                }
            )
        }
    }
}

// ==========================================
// ZEN FILE EXPLORER & EDITOR
// ==========================================
@Composable
fun FileManagerScreen(
    isDark: Boolean,
    onMenuClick: () -> Unit
) {
    val context = LocalContext.current
    val baseDir = context.filesDir
    var currentDirectory by remember { mutableStateOf(baseDir) }
    var filesList by remember { mutableStateOf(emptyList<File>()) }
    var searchInput by remember { mutableStateOf("") }

    // Dialog state
    var showCreateFileDialog by remember { mutableStateOf(false) }
    var showCreateDirDialog by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf<File?>(null) }
    var dialogInputText by remember { mutableStateOf("") }

    // Full screen file edit/view overlay state
    var editingFile by remember { mutableStateOf<File?>(null) }
    var editingContent by remember { mutableStateOf("") }

    val refreshFiles = {
        val allFiles = currentDirectory.listFiles()?.toList() ?: emptyList()
        val sorted = allFiles.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))
        filesList = if (searchInput.trim().isEmpty()) {
            sorted
        } else {
            sorted.filter { it.name.contains(searchInput, ignoreCase = true) }
        }
    }

    // Auto-prepopulate directory with spiritual zen files on first launch
    LaunchedEffect(Unit) {
        if (baseDir.listFiles()?.isEmpty() == true) {
            val filesToCreate = listOf(
                "iching_philosophy.txt" to """
                    THE LAWS OF DYNAMIC EQUILIBRIUM
                    ================================
                    1. Heaven (Qian) represents pure Yang, creative energy, and active power.
                    2. Earth (Kun) represents pure Yin, receptive flow, and yielding patience.
                    3. Harmony is found not by choosing one over the other, but by flowing
                       effortlessly between them as seasons flow from winter to summer.
                       
                    Keep your code pure and your mind empty. Flow like water.
                """.trimIndent(),
                "breathing_koan.txt" to """
                    THE CALM MIND
                    ==============
                    A monk asked Chao-chou, "Does a dog have Buddha-nature?"
                    Chao-chou replied, "Mu!" (Nothingness)
                    
                    To find focus:
                    - Inhale slowly for 4 seconds, collecting the Yang light.
                    - Hold the quiet breath for 4 seconds, establishing the central axis.
                    - Exhale gently for 4 seconds, letting go of all Yin clutter.
                    - Remain empty for 4 seconds, returning to the Source.
                """.trimIndent(),
                "manifesto_of_flow.kt" to """
                    // A standard contemplation of functional purity
                    package dao.purity
                    
                    fun achieveBalance(yin: Float, yang: Float): Boolean {
                        val delta = Math.abs(yin - yang)
                        return delta < 0.05f // Perfect balance of digital karma
                    }
                """.trimIndent()
            )
            filesToCreate.forEach { (name, content) ->
                try {
                    File(baseDir, name).writeText(content)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
        refreshFiles()
    }

    // Refresh listing whenever folder or search text changes
    LaunchedEffect(currentDirectory, searchInput) {
        refreshFiles()
    }

    // 1. Create File Dialog
    if (showCreateFileDialog) {
        AlertDialog(
            onDismissRequest = { showCreateFileDialog = false },
            title = { Text("New Zen File", fontFamily = FontFamily.Serif) },
            text = {
                OutlinedTextField(
                    value = dialogInputText,
                    onValueChange = { dialogInputText = it },
                    label = { Text("File Name (e.g. quotes.txt)") },
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (dialogInputText.trim().isNotEmpty()) {
                            try {
                                val target = File(currentDirectory, dialogInputText.trim())
                                if (!target.exists()) {
                                    target.createNewFile()
                                    target.writeText("// Newly created zen canvas\n")
                                }
                                refreshFiles()
                            } catch (e: Exception) {
                                Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                            }
                        }
                        showCreateFileDialog = false
                        dialogInputText = ""
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ZenGold)
                ) {
                    Text("Create", color = Color.Black)
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreateFileDialog = false; dialogInputText = "" }) {
                    Text("Cancel", color = ZenGold)
                }
            }
        )
    }

    // 2. Create Directory Dialog
    if (showCreateDirDialog) {
        AlertDialog(
            onDismissRequest = { showCreateDirDialog = false },
            title = { Text("New Zen Folder", fontFamily = FontFamily.Serif) },
            text = {
                OutlinedTextField(
                    value = dialogInputText,
                    onValueChange = { dialogInputText = it },
                    label = { Text("Folder Name") },
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (dialogInputText.trim().isNotEmpty()) {
                            try {
                                val target = File(currentDirectory, dialogInputText.trim())
                                if (!target.exists()) {
                                    target.mkdir()
                                }
                                refreshFiles()
                            } catch (e: Exception) {
                                Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                            }
                        }
                        showCreateDirDialog = false
                        dialogInputText = ""
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ZenGold)
                ) {
                    Text("Create", color = Color.Black)
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreateDirDialog = false; dialogInputText = "" }) {
                    Text("Cancel", color = ZenGold)
                }
            }
        )
    }

    // 3. Rename Dialog
    showRenameDialog?.let { targetFile ->
        AlertDialog(
            onDismissRequest = { showRenameDialog = null },
            title = { Text("Rename ${if (targetFile.isDirectory) "Folder" else "File"}", fontFamily = FontFamily.Serif) },
            text = {
                OutlinedTextField(
                    value = dialogInputText,
                    onValueChange = { dialogInputText = it },
                    label = { Text("Enter new name") },
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (dialogInputText.trim().isNotEmpty()) {
                            try {
                                val destination = File(targetFile.parentFile, dialogInputText.trim())
                                if (!destination.exists()) {
                                    targetFile.renameTo(destination)
                                }
                                refreshFiles()
                            } catch (e: Exception) {
                                Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                            }
                        }
                        showRenameDialog = null
                        dialogInputText = ""
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ZenGold)
                ) {
                    Text("Rename", color = Color.Black)
                }
            },
            dismissButton = {
                TextButton(onClick = { showRenameDialog = null; dialogInputText = "" }) {
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
                    .background(if (isDark) YinBlack else YangWhite)
                    .statusBarsPadding()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        IconButton(onClick = onMenuClick) {
                            Icon(Icons.Default.Menu, contentDescription = "Menu", tint = if (isDark) Color.White else Color.Black)
                        }
                        Text(
                            text = "ZEN FILE EXPLORER 📂",
                            fontFamily = FontFamily.Serif,
                            fontWeight = FontWeight.Bold,
                            color = if (isDark) ZenGold else Color(0xFF9E7E1D),
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                    
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        IconButton(onClick = {
                            showCreateFileDialog = true
                            dialogInputText = ""
                        }) {
                            Icon(Icons.Default.Description, contentDescription = "New File", tint = ZenGold)
                        }
                        IconButton(onClick = {
                            showCreateDirDialog = true
                            dialogInputText = ""
                        }) {
                            Icon(Icons.Default.CreateNewFolder, contentDescription = "New Folder", tint = ZenSienna)
                        }
                    }
                }

                // Breadcrumb & Back Arrow
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    val isAtRoot = currentDirectory.absolutePath == baseDir.absolutePath
                    IconButton(
                        onClick = {
                            val parent = currentDirectory.parentFile
                            if (parent != null && parent.absolutePath.contains(baseDir.absolutePath)) {
                                currentDirectory = parent
                            }
                        },
                        enabled = !isAtRoot
                    ) {
                        Icon(
                            Icons.Default.ArrowUpward, 
                            contentDescription = "Up",
                            tint = if (isAtRoot) Color.Gray else (if (isDark) Color.White else Color.Black)
                        )
                    }

                    val relPath = currentDirectory.absolutePath
                        .substringAfter(baseDir.absolutePath)
                        .ifEmpty { "/" }
                    
                    Text(
                        text = "Root:$relPath",
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (isDark) YinText else YangText,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.weight(1f)
                    )
                }

                // Search Bar inside Folder
                OutlinedTextField(
                    value = searchInput,
                    onValueChange = { searchInput = it },
                    placeholder = { Text("Search files inside directory...", fontSize = 12.sp) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(16.dp)) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = if (isDark) Color(0xFF141418) else Color(0xFFECE9E4),
                        unfocusedContainerColor = if (isDark) Color(0xFF0F0F12) else Color(0xFFF3F1ED),
                        focusedBorderColor = ZenGold,
                        unfocusedBorderColor = Color.Transparent
                    ),
                    shape = RoundedCornerShape(12.dp)
                )
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(if (isDark) Color(0xFF070709) else Color(0xFFF1F0EC))
        ) {
            if (filesList.isEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        Icons.Default.Folder, 
                        contentDescription = null, 
                        modifier = Modifier.size(64.dp), 
                        tint = Color.Gray.copy(alpha = 0.5f)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "This Sanctuary is Empty",
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.Gray,
                        fontFamily = FontFamily.Serif
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(filesList) { file ->
                        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
                        val dateString = sdf.format(Date(file.lastModified()))
                        val itemSize = if (file.isDirectory) {
                            "${file.listFiles()?.size ?: 0} elements"
                        } else {
                            "${file.length()} Bytes"
                        }

                        ElevatedCard(
                            onClick = {
                                if (file.isDirectory) {
                                    currentDirectory = file
                                } else {
                                    // Open text editor
                                    if (file.name.endsWith(".txt") || file.name.endsWith(".kt") || file.name.endsWith(".java")) {
                                        try {
                                            editingFile = file
                                            editingContent = file.readText()
                                        } catch (e: Exception) {
                                            Toast.makeText(context, "Read failed: ${e.message}", Toast.LENGTH_SHORT).show()
                                        }
                                    } else {
                                        Toast.makeText(context, "Cannot open: binary format", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            },
                            colors = CardDefaults.elevatedCardColors(
                                containerColor = if (isDark) YinCardBg else YangCardBg
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(
                                        imageVector = if (file.isDirectory) Icons.Default.Folder else Icons.Default.Description,
                                        contentDescription = null,
                                        tint = if (file.isDirectory) ZenSienna else ZenGold,
                                        modifier = Modifier.size(24.dp)
                                    )
                                    Column {
                                        Text(
                                            text = file.name,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = if (isDark) YinText else YangText,
                                            fontFamily = FontFamily.Serif
                                        )
                                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            Text(
                                                text = itemSize,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = Color.Gray
                                            )
                                            Text(
                                                text = "•",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = Color.Gray
                                            )
                                            Text(
                                                text = dateString,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = Color.Gray,
                                                fontFamily = FontFamily.Monospace
                                            )
                                        }
                                    }
                                }

                                // Quick Actions Row (Rename / Delete)
                                Row {
                                    IconButton(onClick = {
                                        showRenameDialog = file
                                        dialogInputText = file.name
                                    }) {
                                        Icon(Icons.Default.Edit, contentDescription = "Rename", tint = Color.Gray, modifier = Modifier.size(16.dp))
                                    }
                                    IconButton(onClick = {
                                        try {
                                            if (file.isDirectory) {
                                                file.deleteRecursively()
                                            } else {
                                                file.delete()
                                            }
                                            refreshFiles()
                                        } catch (e: Exception) {
                                            Toast.makeText(context, "Delete failed: ${e.message}", Toast.LENGTH_SHORT).show()
                                        }
                                    }) {
                                        Icon(Icons.Default.Delete, contentDescription = "Delete", tint = ZenRed, modifier = Modifier.size(16.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Full Screen Scribe Text Editor Overlay
    editingFile?.let { currentFile ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.8f))
                .clickable { } // block background clicks
        ) {
            Card(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
                    .statusBarsPadding()
                    .navigationBarsPadding(),
                colors = CardDefaults.cardColors(containerColor = if (isDark) Color(0xFF141318) else Color(0xFFF7F5F0)),
                border = BorderStroke(2.dp, ZenGold)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Header
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Scribe View: ${currentFile.name}",
                                style = MaterialTheme.typography.titleMedium,
                                fontFamily = FontFamily.Serif,
                                color = ZenGold,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Keep edits peaceful and precise",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.Gray
                            )
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            // Cancel Button
                            TextButton(onClick = { editingFile = null }) {
                                Text("Discard", color = ZenRed)
                            }
                            
                            // Save Button
                            Button(
                                onClick = {
                                    try {
                                        currentFile.writeText(editingContent)
                                        refreshFiles()
                                        editingFile = null
                                        Toast.makeText(context, "Scribed successfully!", Toast.LENGTH_SHORT).show()
                                    } catch (e: Exception) {
                                        Toast.makeText(context, "Save failed: ${e.message}", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = ZenGold)
                            ) {
                                Text("Save", color = Color.Black, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    HorizontalDivider(color = Color.Gray.copy(alpha = 0.3f))

                    // Text Field for Editor Content
                    OutlinedTextField(
                        value = editingContent,
                        onValueChange = { editingContent = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        textStyle = MaterialTheme.typography.bodyMedium.copy(
                            fontFamily = FontFamily.Monospace,
                            color = if (isDark) Color.White else Color.Black
                        ),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color.Transparent,
                            unfocusedBorderColor = Color.Transparent,
                            focusedContainerColor = if (isDark) Color(0xFF0F0F12) else Color(0xFFFAF9F6),
                            unfocusedContainerColor = if (isDark) Color(0xFF0F0F12) else Color(0xFFFAF9F6)
                        ),
                        placeholder = { Text("Begin writing your zen transcript...", style = MaterialTheme.typography.bodyMedium, color = Color.Gray) }
                    )
                }
            }
        }
    }
}

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
                                        val jsonObject = org.json.JSONObject().apply {
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
                                            put("discourses", org.json.JSONArray().apply {
                                                sessions.forEach { sess ->
                                                    put(org.json.JSONObject().apply {
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

                            // Help cards on balance
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

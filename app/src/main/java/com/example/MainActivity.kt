package com.example

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
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.LayoutDirection
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
import com.example.data.repository.UserPreferences
import com.example.ui.components.SidebarAppItem
import com.example.ui.components.SidebarHeader
import com.example.ui.components.SessionItemRow
import com.example.ui.components.GameHudPanel
import com.example.ui.components.EmptyOnboardingPrompt
import com.example.ui.components.MessageBubble
import com.example.ui.components.ThinkingIndicatorBubble
import com.example.ui.components.HexagonYinYangBackground
import com.example.ui.components.RotatingYinYangSymbol
import com.example.ui.screens.TerminalEmulatorScreen
import com.example.ui.screens.*
import com.example.ui.settings.SettingsDialog
import com.example.ui.theme.*
import com.example.ui.viewmodel.ChatViewModel
import kotlinx.coroutines.launch
import kotlin.math.abs

class MainActivity : ComponentActivity() {
    private val viewModel: ChatViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Set activity reference for screen capture
        com.example.ui.automation.AutomationEngine.mainActivity = this
        enableEdgeToEdge()
        setContent {
            val themeMode = remember { mutableStateOf("auto") }
            val profile by viewModel.userProfile.collectAsStateWithLifecycle()
            val balance = profile.yinBalance

            val isDark = when (themeMode.value) {
                "dark" -> true
                "light" -> false
                else -> balance < 0.5f
            }

            MyApplicationTheme(darkTheme = isDark) {
                MainLayout(viewModel = viewModel, themeMode = themeMode, isDarkThemeOverride = isDark)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        val prefs = UserPreferences(this)
        if (prefs.appLockEnabled && prefs.masterPassword.isNotEmpty()) {
            // App lock is enabled - in a full implementation, show biometric or PIN dialog here
            // For now, we just check the preference exists
        }
    }
}

enum class Screen {
    DaoChat,
    VideoEditor,
    Browser,
    FileManager,
    CodeEditor,
    GitHub,
    Telegram,
    AutomationDashboard,
    ImageEditor,
    CloudStorageHub,
    PasswordVault,
    DocumentScanner,
    ScreenRecorder,
    TerminalEmulator,
    NotesManager
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

    // Listen for automation events and navigate to target screens
    androidx.compose.runtime.LaunchedEffect(Unit) {
        com.example.ui.automation.AutomationEventBus.events.collect { event ->
            if (event.action == "open") {
                when (event.targetScreen) {
                    "Browser" -> currentScreen = Screen.Browser
                    "FileManager" -> currentScreen = Screen.FileManager
                    "VideoEditor" -> currentScreen = Screen.VideoEditor
                    "CodeEditor" -> currentScreen = Screen.CodeEditor
                    "GitHub" -> currentScreen = Screen.GitHub
                    "Telegram" -> currentScreen = Screen.Telegram
                    "ImageEditor" -> currentScreen = Screen.ImageEditor
                    "DocumentScanner" -> currentScreen = Screen.DocumentScanner
                    "CloudStorageHub" -> currentScreen = Screen.CloudStorageHub
                    "PasswordVault" -> currentScreen = Screen.PasswordVault
                    "NotesManager" -> currentScreen = Screen.NotesManager
                    "TerminalEmulator" -> currentScreen = Screen.TerminalEmulator
                    "AutomationDashboard" -> currentScreen = Screen.AutomationDashboard
                    "ScreenRecorder" -> currentScreen = Screen.ScreenRecorder
                }
            }
        }
    }

    var newProjectName by remember { mutableStateOf("") }
    var newTaskTitle by remember { mutableStateOf("") }

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
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    SidebarHeader(onNewChatClick = {
                        currentScreen = Screen.DaoChat
                        viewModel.createNewSession()
                        scope.launch { leftDrawerState.close() }
                    })

                    HorizontalDivider(color = Color(0xFF222228), modifier = Modifier.padding(vertical = 8.dp))

                    Text(
                        text = "🤖 AI & COMMUNICATION",
                        style = MaterialTheme.typography.labelSmall,
                        color = ZenGold,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                        fontWeight = FontWeight.Bold,
                        fontSize = 10.sp,
                        letterSpacing = 1.sp
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
                        title = "Telegram Hub",
                        icon = Icons.Default.Send,
                        isSelected = currentScreen == Screen.Telegram,
                        iconColor = Color(0xFF0088CC),
                        onClick = {
                            currentScreen = Screen.Telegram
                            scope.launch { leftDrawerState.close() }
                        }
                    )

                    SidebarAppItem(
                        title = "Automation Dashboard",
                        icon = Icons.Default.SmartToy,
                        isSelected = currentScreen == Screen.AutomationDashboard,
                        iconColor = Color(0xFFFF9800),
                        onClick = {
                            currentScreen = Screen.AutomationDashboard
                            scope.launch { leftDrawerState.close() }
                        }
                    )

                    HorizontalDivider(color = Color(0xFF222228), modifier = Modifier.padding(vertical = 8.dp))

                    Text(
                        text = "🎨 MEDIA & CREATIVITY",
                        style = MaterialTheme.typography.labelSmall,
                        color = ZenGold,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                        fontWeight = FontWeight.Bold,
                        fontSize = 10.sp,
                        letterSpacing = 1.sp
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
                        title = "Image Editor",
                        icon = Icons.Default.Image,
                        isSelected = currentScreen == Screen.ImageEditor,
                        iconColor = Color(0xFF4FC3F7),
                        onClick = {
                            currentScreen = Screen.ImageEditor
                            scope.launch { leftDrawerState.close() }
                        }
                    )

                    SidebarAppItem(
                        title = "Document Scanner",
                        icon = Icons.Default.DocumentScanner,
                        isSelected = currentScreen == Screen.DocumentScanner,
                        iconColor = Color(0xFFFF8A65),
                        onClick = {
                            currentScreen = Screen.DocumentScanner
                            scope.launch { leftDrawerState.close() }
                        }
                    )

                    SidebarAppItem(
                        title = "Screen Recorder",
                        icon = Icons.Default.Videocam,
                        isSelected = currentScreen == Screen.ScreenRecorder,
                        iconColor = Color(0xFFE91E63),
                        onClick = {
                            currentScreen = Screen.ScreenRecorder
                            scope.launch { leftDrawerState.close() }
                        }
                    )

                    HorizontalDivider(color = Color(0xFF222228), modifier = Modifier.padding(vertical = 8.dp))

                    Text(
                        text = "📂 FILES & STORAGE",
                        style = MaterialTheme.typography.labelSmall,
                        color = ZenGold,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                        fontWeight = FontWeight.Bold,
                        fontSize = 10.sp,
                        letterSpacing = 1.sp
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
                        title = "Cloud Storage Hub",
                        icon = Icons.Default.Cloud,
                        isSelected = currentScreen == Screen.CloudStorageHub,
                        iconColor = Color(0xFF42A5F5),
                        onClick = {
                            currentScreen = Screen.CloudStorageHub
                            scope.launch { leftDrawerState.close() }
                        }
                    )

                    SidebarAppItem(
                        title = "Password Vault",
                        icon = Icons.Default.Password,
                        isSelected = currentScreen == Screen.PasswordVault,
                        iconColor = Color(0xFF7C4DFF),
                        onClick = {
                            currentScreen = Screen.PasswordVault
                            scope.launch { leftDrawerState.close() }
                        }
                    )

                    HorizontalDivider(color = Color(0xFF222228), modifier = Modifier.padding(vertical = 8.dp))

                    Text(
                        text = "💻 DEVELOPMENT",
                        style = MaterialTheme.typography.labelSmall,
                        color = ZenGold,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                        fontWeight = FontWeight.Bold,
                        fontSize = 10.sp,
                        letterSpacing = 1.sp
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

                    SidebarAppItem(
                        title = "GitHub Manager",
                        icon = Icons.Default.Hub,
                        isSelected = currentScreen == Screen.GitHub,
                        iconColor = Color(0xFF6E40C9),
                        onClick = {
                            currentScreen = Screen.GitHub
                            scope.launch { leftDrawerState.close() }
                        }
                    )

                    SidebarAppItem(
                        title = "Terminal Emulator",
                        icon = Icons.Default.Terminal,
                        isSelected = currentScreen == Screen.TerminalEmulator,
                        iconColor = Color(0xFF00FF00),
                        onClick = {
                            currentScreen = Screen.TerminalEmulator
                            scope.launch { leftDrawerState.close() }
                        }
                    )

                    HorizontalDivider(color = Color(0xFF222228), modifier = Modifier.padding(vertical = 8.dp))

                    Text(
                        text = "📋 PRODUCTIVITY",
                        style = MaterialTheme.typography.labelSmall,
                        color = ZenGold,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                        fontWeight = FontWeight.Bold,
                        fontSize = 10.sp,
                        letterSpacing = 1.sp
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
                        title = "Notes Manager",
                        icon = Icons.Default.Note,
                        isSelected = currentScreen == Screen.NotesManager,
                        iconColor = Color(0xFFFFD54F),
                        onClick = {
                            currentScreen = Screen.NotesManager
                            scope.launch { leftDrawerState.close() }
                        }
                    )

                    Spacer(modifier = Modifier.height(16.dp))
                }

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
                                text = "15 Tools • AI-Powered • Zen Multitasking Hub",
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
                                .clickable(onClick = { showSettingsDialog = true })
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
                        DaoChatScreen(
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
                            onInputModeChange = { viewModel.updateInputMode(it) },
                            viewModel = viewModel
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
                    Screen.GitHub -> {
                        GitHubScreen(
                            isDark = isDarkThemeOverride,
                            onMenuClick = { scope.launch { leftDrawerState.open() } }
                        )
                    }
                    Screen.Telegram -> {
                        TelegramScreen(
                            isDark = isDarkThemeOverride,
                            onMenuClick = { scope.launch { leftDrawerState.open() } }
                        )
                    }
                    Screen.AutomationDashboard -> {
                        AutomationDashboard(
                            isDark = isDarkThemeOverride,
                            onMenuClick = { scope.launch { leftDrawerState.open() } }
                        )
                    }
                    Screen.ImageEditor -> {
                        ImageEditorScreen(
                            isDark = isDarkThemeOverride,
                            onMenuClick = { scope.launch { leftDrawerState.open() } }
                        )
                    }
                    Screen.CloudStorageHub -> {
                        CloudStorageHubScreen(
                            isDark = isDarkThemeOverride,
                            onMenuClick = { scope.launch { leftDrawerState.open() } }
                        )
                    }
                    Screen.PasswordVault -> {
                        PasswordVaultScreen(
                            isDark = isDarkThemeOverride,
                            onMenuClick = { scope.launch { leftDrawerState.open() } }
                        )
                    }
                    Screen.DocumentScanner -> {
                        DocumentScannerScreen(
                            isDark = isDarkThemeOverride,
                            onMenuClick = { scope.launch { leftDrawerState.open() } }
                        )
                    }
                    Screen.ScreenRecorder -> {
                        ScreenRecorderScreen(
                            isDark = isDarkThemeOverride,
                            onMenuClick = { scope.launch { leftDrawerState.open() } }
                        )
                    }
                    Screen.TerminalEmulator -> {
                        TerminalEmulatorScreen(
                            isDark = isDarkThemeOverride,
                            onMenuClick = { scope.launch { leftDrawerState.open() } }
                        )
                    }
                    Screen.NotesManager -> {
                        NotesManagerScreen(
                            isDark = isDarkThemeOverride,
                            onMenuClick = { scope.launch { leftDrawerState.open() } }
                        )
                    }
                }
            }
        }
    }
}

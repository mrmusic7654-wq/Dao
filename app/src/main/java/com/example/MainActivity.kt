package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.ChatSession
import com.example.data.repository.UserPreferences
import com.example.ui.components.SidebarAppItem
import com.example.ui.components.SidebarHeader
import com.example.ui.components.SessionItemRow
import com.example.ui.screens.*
import com.example.ui.settings.SettingsDialog
import com.example.ui.theme.*
import com.example.ui.viewmodel.ChatViewModel
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val viewModel: ChatViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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
}

enum class Screen {
    DaoChat,
    VideoEditor,
    Browser,
    FileManager,
    CodeEditor,
    GitHub,
    Telegram
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
                            if (renameInput.isNotBlank()) viewModel.renameSession(session.id, renameInput)
                        }
                        showRenameDialog = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ZenGold)
                ) { Text("Save", color = Color.Black) }
            },
            dismissButton = {
                TextButton(onClick = { showRenameDialog = null }) { Text("Cancel", color = ZenGold) }
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

                SidebarAppItem(title = "Dao Wisdom Chat", icon = Icons.Default.Chat, isSelected = currentScreen == Screen.DaoChat, iconColor = ZenGold,
                    onClick = { currentScreen = Screen.DaoChat; scope.launch { leftDrawerState.close() } })
                SidebarAppItem(title = "Zen Video Editor", icon = Icons.Default.Movie, isSelected = currentScreen == Screen.VideoEditor, iconColor = ZenRed,
                    onClick = { currentScreen = Screen.VideoEditor; scope.launch { leftDrawerState.close() } })
                SidebarAppItem(title = "Dao Web Browser", icon = Icons.Default.Language, isSelected = currentScreen == Screen.Browser, iconColor = ZenBlue,
                    onClick = { currentScreen = Screen.Browser; scope.launch { leftDrawerState.close() } })
                SidebarAppItem(title = "Zen File Explorer", icon = Icons.Default.Folder, isSelected = currentScreen == Screen.FileManager, iconColor = ZenSienna,
                    onClick = { currentScreen = Screen.FileManager; scope.launch { leftDrawerState.close() } })
                SidebarAppItem(title = "Zen Code Editor", icon = Icons.Default.Code, isSelected = currentScreen == Screen.CodeEditor, iconColor = Color(0xFF9C27B0),
                    onClick = { currentScreen = Screen.CodeEditor; scope.launch { leftDrawerState.close() } })
                SidebarAppItem(title = "GitHub Manager", icon = Icons.Default.Hub, isSelected = currentScreen == Screen.GitHub, iconColor = Color(0xFF6E40C9),
                    onClick = { currentScreen = Screen.GitHub; scope.launch { leftDrawerState.close() } })
                SidebarAppItem(title = "Telegram Hub", icon = Icons.Default.Send, isSelected = currentScreen == Screen.Telegram, iconColor = Color(0xFF0088CC),
                    onClick = { currentScreen = Screen.Telegram; scope.launch { leftDrawerState.close() } })

                Spacer(modifier = Modifier.weight(1f))

                // Footer
                Box(
                    modifier = Modifier.fillMaxWidth().background(Color(0xFF08070A)).padding(16.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Box(modifier = Modifier.size(32.dp).clip(CircleShape).background(ZenGold), contentAlignment = Alignment.Center) {
                            Text("☯", color = Color.Black, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        }
                        Column {
                            Text("Dao Space Companion", color = YinText, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                            Text("Zen Multitasking Hub", color = YinTextSecondary, style = MaterialTheme.typography.labelSmall)
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
                    Column(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("DISCOURSE HISTORY 📜", style = MaterialTheme.typography.titleMedium, fontFamily = FontFamily.Serif,
                            color = ZenGold, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 8.dp))

                        HorizontalDivider(color = Color(0xFF222228))

                        LazyColumn(modifier = Modifier.fillMaxWidth().weight(1f)) {
                            items(sessions, key = { it.id }) { session ->
                                val isActive = currentScreen == Screen.DaoChat && session.id == activeSessionId
                                SessionItemRow(
                                    session = session, isActive = isActive,
                                    onSelect = {
                                        currentScreen = Screen.DaoChat
                                        viewModel.selectSession(session.id)
                                        scope.launch { rightDrawerState.close() }
                                    },
                                    onDelete = { viewModel.deleteSession(session.id) },
                                    onRename = { showRenameDialog = session; renameInput = session.title }
                                )
                            }
                        }

                        HorizontalDivider(color = Color(0xFF222228), modifier = Modifier.padding(vertical = 4.dp))

                        val context = LocalContext.current
                        val prefs = remember { UserPreferences(context) }
                        var showSettingsDialog by remember { mutableStateOf(false) }

                        Row(
                            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable { showSettingsDialog = true }
                                .padding(vertical = 10.dp, horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Box(modifier = Modifier.size(36.dp).clip(CircleShape).background(ZenGold), contentAlignment = Alignment.Center) {
                                    Text(prefs.userName.take(1).uppercase(), color = Color.Black, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyLarge)
                                }
                                Column {
                                    Text(prefs.userName, color = YinText, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, maxLines = 1)
                                    Text(prefs.userEmail, color = YinTextSecondary, style = MaterialTheme.typography.labelSmall, maxLines = 1)
                                }
                            }
                            Icon(Icons.Default.Settings, contentDescription = "Settings", tint = ZenGold, modifier = Modifier.size(20.dp))
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
                            messages = messages, profile = profile, isTyping = isTyping,
                            onSendMessage = { viewModel.sendMessage(it) },
                            onMenuClick = { scope.launch { leftDrawerState.open() } },
                            onRightMenuClick = { scope.launch { rightDrawerState.open() } },
                            onNewDiscourseClick = { viewModel.createNewSession() },
                            themeMode = themeMode, isDarkThemeOverride = isDarkThemeOverride,
                            activePersonality = activePersonality, onPersonalityChange = { viewModel.updatePersonality(it) },
                            activeInputMode = activeInputMode, onInputModeChange = { viewModel.updateInputMode(it) }
                        )
                    }
                    Screen.VideoEditor -> {
                        VideoEditorScreen(isDark = isDarkThemeOverride, onMenuClick = { scope.launch { leftDrawerState.open() } })
                    }
                    Screen.Browser -> {
                        BrowserScreen(isDark = isDarkThemeOverride, onMenuClick = { scope.launch { leftDrawerState.open() } })
                    }
                    Screen.FileManager -> {
                        FileManagerScreen(isDark = isDarkThemeOverride, onMenuClick = { scope.launch { leftDrawerState.open() } })
                    }
                    Screen.CodeEditor -> {
                        CodeEditorScreen(
                            isDark = isDarkThemeOverride,
                            onMenuClick = { scope.launch { leftDrawerState.open() } },
                            onRightMenuClick = { scope.launch { rightDrawerState.open() } }
                        )
                    }
                    Screen.GitHub -> {
                        GitHubScreen(isDark = isDarkThemeOverride, onMenuClick = { scope.launch { leftDrawerState.open() } })
                    }
                    Screen.Telegram -> {
                        TelegramScreen(isDark = isDarkThemeOverride, onMenuClick = { scope.launch { leftDrawerState.open() } })
                    }
                }
            }
        }
    }
}

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
import androidx.compose.foundation.lazy.itemsIndexed
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
import com.example.ui.theme.*
import kotlinx.coroutines.*
import java.text.SimpleDateFormat
import java.util.*

// ==================== DATA MODELS ====================

data class AutomationTask(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val description: String = "",
    val status: TaskStatus = TaskStatus.PENDING,
    val progress: Float = 0f,
    val currentStep: String = "",
    val totalSteps: Int = 0,
    val completedSteps: Int = 0,
    val steps: List<TaskStep> = emptyList(),
    val createdAt: Long = System.currentTimeMillis(),
    val completedAt: Long = 0L,
    val errorMessage: String = "",
    val category: TaskCategory = TaskCategory.GENERAL
)

data class TaskStep(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val description: String = "",
    val status: StepStatus = StepStatus.PENDING,
    val toolUsed: String = "",
    val startedAt: Long = 0L,
    val completedAt: Long = 0L,
    val result: String = ""
)

enum class TaskStatus { PENDING, RUNNING, PAUSED, COMPLETED, FAILED, CANCELLED }
enum class StepStatus { PENDING, IN_PROGRESS, COMPLETED, FAILED, SKIPPED }
enum class TaskCategory { GENERAL, FILE_OPERATION, WEB_SCRAPING, CODE_GENERATION, MEDIA_PROCESSING, API_INTEGRATION, SCHEDULED }

data class AgentLog(
    val id: String = UUID.randomUUID().toString(),
    val message: String,
    val level: LogLevel = LogLevel.INFO,
    val timestamp: Long = System.currentTimeMillis(),
    val taskId: String? = null
)

enum class LogLevel { INFO, SUCCESS, WARNING, ERROR, DEBUG }

// ==================== AUTOMATION ENGINE ====================

object AutomationEngine {
    val activeTasks = mutableStateListOf<AutomationTask>()
    val completedTasks = mutableStateListOf<AutomationTask>()
    val logs = mutableStateListOf<AgentLog>()
    var isAgentRunning = false

    fun createTask(title: String, description: String, steps: List<TaskStep>, category: TaskCategory = TaskCategory.GENERAL): AutomationTask {
        val task = AutomationTask(
            title = title, description = description, steps = steps,
            totalSteps = steps.size, category = category
        )
        activeTasks.add(task)
        addLog("Task created: $title", LogLevel.INFO, task.id)
        return task
    }

    fun startTask(taskId: String) {
        val index = activeTasks.indexOfFirst { it.id == taskId }
        if (index == -1) return
        activeTasks[index] = activeTasks[index].copy(status = TaskStatus.RUNNING, currentStep = "Initializing...")
        addLog("Task started: ${activeTasks[index].title}", LogLevel.INFO, taskId)
        isAgentRunning = true
    }

    fun updateStep(taskId: String, stepIndex: Int, status: StepStatus, result: String = "") {
        val taskIdx = activeTasks.indexOfFirst { it.id == taskId }
        if (taskIdx == -1) return
        val task = activeTasks[taskIdx]
        val steps = task.steps.toMutableList()
        steps[stepIndex] = steps[stepIndex].copy(
            status = status, result = result,
            completedAt = if (status == StepStatus.COMPLETED) System.currentTimeMillis() else 0L
        )
        val completed = steps.count { it.status == StepStatus.COMPLETED }
        activeTasks[taskIdx] = task.copy(
            steps = steps,
            completedSteps = completed,
            progress = if (task.totalSteps > 0) completed.toFloat() / task.totalSteps else 0f,
            currentStep = if (stepIndex + 1 < steps.size) steps[stepIndex + 1].name else "Finalizing..."
        )
        addLog("Step ${stepIndex + 1}/${task.totalSteps}: ${steps[stepIndex].name} - $status", if (status == StepStatus.FAILED) LogLevel.ERROR else LogLevel.SUCCESS, taskId)
    }

    fun completeTask(taskId: String, success: Boolean = true, message: String = "") {
        val index = activeTasks.indexOfFirst { it.id == taskId }
        if (index == -1) return
        val task = activeTasks[index].copy(
            status = if (success) TaskStatus.COMPLETED else TaskStatus.FAILED,
            progress = 1f,
            completedAt = System.currentTimeMillis(),
            errorMessage = if (!success) message else ""
        )
        activeTasks.removeAt(index)
        completedTasks.add(0, task)
        isAgentRunning = activeTasks.none { it.status == TaskStatus.RUNNING }
        addLog("Task ${if (success) "completed" else "failed"}: ${task.title}", if (success) LogLevel.SUCCESS else LogLevel.ERROR, taskId)
    }

    fun pauseTask(taskId: String) {
        val index = activeTasks.indexOfFirst { it.id == taskId }
        if (index == -1) return
        activeTasks[index] = activeTasks[index].copy(status = TaskStatus.PAUSED)
        addLog("Task paused: ${activeTasks[index].title}", LogLevel.WARNING, taskId)
    }

    fun cancelTask(taskId: String) {
        val index = activeTasks.indexOfFirst { it.id == taskId }
        if (index == -1) return
        val task = activeTasks[index].copy(status = TaskStatus.CANCELLED)
        activeTasks.removeAt(index)
        completedTasks.add(0, task)
        isAgentRunning = activeTasks.none { it.status == TaskStatus.RUNNING }
        addLog("Task cancelled: ${task.title}", LogLevel.WARNING, taskId)
    }

    fun addLog(message: String, level: LogLevel = LogLevel.INFO, taskId: String? = null) {
        logs.add(0, AgentLog(message = message, level = level, taskId = taskId))
        if (logs.size > 500) logs.removeAt(logs.size - 1)
    }

    fun clearHistory() {
        completedTasks.clear()
        logs.clear()
        addLog("History cleared", LogLevel.INFO)
    }

    fun getQuickActions(): List<Pair<String, String>> = listOf(
        "📥 Download all images from a webpage" to "web_scrape_images",
        "🗜 Compress files in a folder" to "compress_folder",
        "📤 Upload file to GitHub" to "github_upload",
        "📨 Send Telegram broadcast" to "telegram_broadcast",
        "📹 Convert video to GIF" to "video_to_gif",
        "📷 Scan document to PDF" to "scan_to_pdf",
        "🔍 Search and save results" to "web_search_save",
        "📋 Backup files to cloud" to "cloud_backup"
    )
}

// ==================== MAIN SCREEN ====================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AutomationDashboard(isDark: Boolean, onMenuClick: () -> Unit) {
    val context = LocalContext.current
    var activeTab by remember { mutableStateOf(0) } // 0=Active, 1=History, 2=Logs, 3=Agent
    var showCreateDialog by remember { mutableStateOf(false) }
    var showTaskDetail by remember { mutableStateOf<AutomationTask?>(null) }
    var newTaskTitle by remember { mutableStateOf("") }
    var newTaskDesc by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf(TaskCategory.GENERAL) }

    val activeTasks = AutomationEngine.activeTasks
    val completedTasks = AutomationEngine.completedTasks
    val logs = AutomationEngine.logs
    val isAgentRunning = AutomationEngine.isAgentRunning

    // ==================== CREATE TASK DIALOG ====================
    if (showCreateDialog) {
        AlertDialog(
            onDismissRequest = { showCreateDialog = false },
            title = { Text("New Automation Task", fontFamily = FontFamily.Serif, color = ZenGold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = newTaskTitle, onValueChange = { newTaskTitle = it },
                        label = { Text("Task Name") }, modifier = Modifier.fillMaxWidth(), singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = ZenGold)
                    )
                    OutlinedTextField(
                        value = newTaskDesc, onValueChange = { newTaskDesc = it },
                        label = { Text("Description (optional)") }, modifier = Modifier.fillMaxWidth(), maxLines = 3,
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = ZenGold)
                    )
                    Text("Category", color = YinTextSecondary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TaskCategory.entries.forEach { cat ->
                            FilterChip(
                                selected = selectedCategory == cat,
                                onClick = { selectedCategory = cat },
                                label = { Text(cat.name.replace("_", " "), fontSize = 10.sp) },
                                colors = FilterChipDefaults.filterChipColors(selectedContainerColor = ZenGold.copy(alpha = 0.2f), selectedLabelColor = ZenGold)
                            )
                        }
                    }

                    // Quick actions
                    Text("Quick Templates", color = YinTextSecondary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    AutomationEngine.getQuickActions().take(4).forEach { (label, _) ->
                        Surface(
                            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable(onClick = {
                                newTaskTitle = label; showCreateDialog = false
                                AutomationEngine.createTask(label, label, listOf(
                                    TaskStep(name = "Analyze request", description = "Understand the task"),
                                    TaskStep(name = "Execute operation", description = "Perform the action", toolUsed = "ai_agent"),
                                    TaskStep(name = "Verify result", description = "Confirm completion")
                                ), selectedCategory)
                            }),
                            color = Color.Transparent
                        ) {
                            Text(label, color = YinText, fontSize = 13.sp, modifier = Modifier.padding(12.dp))
                        }
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    if (newTaskTitle.isNotBlank()) {
                        AutomationEngine.createTask(newTaskTitle, newTaskDesc, listOf(
                            TaskStep(name = "Initialize", description = "Setting up task environment"),
                            TaskStep(name = "Execute", description = newTaskDesc.ifBlank { newTaskTitle }, toolUsed = "automation_engine"),
                            TaskStep(name = "Finalize", description = "Cleanup and report")
                        ), selectedCategory)
                    }
                    showCreateDialog = false; newTaskTitle = ""; newTaskDesc = ""
                }, colors = ButtonDefaults.buttonColors(containerColor = ZenGold)) {
                    Text("Create Task", color = Color.Black, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = { TextButton(onClick = { showCreateDialog = false }) { Text("Cancel", color = ZenGold) } },
            containerColor = if (isDark) Color(0xFF14131A) else Color(0xFFF7F4EE)
        )
    }

    // ==================== TASK DETAIL DIALOG ====================
    showTaskDetail?.let { task ->
        AlertDialog(
            onDismissRequest = { showTaskDetail = null },
            title = { Text(task.title, fontFamily = FontFamily.Serif, color = ZenGold, maxLines = 2) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    // Progress
                    Card(colors = CardDefaults.cardColors(containerColor = YinCardBg)) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                                Text("Progress", color = YinTextSecondary, fontSize = 12.sp)
                                Text("${(task.progress * 100).toInt()}%", color = ZenGold, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            }
                            LinearProgressIndicator(progress = { task.progress }, modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)), color = ZenGold, trackColor = YinBlack)
                            Text("${task.completedSteps}/${task.totalSteps} steps", color = YinTextSecondary, fontSize = 11.sp)
                        }
                    }

                    // Steps list
                    Text("Steps", color = YinTextSecondary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    task.steps.forEachIndexed { index, step ->
                        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(modifier = Modifier.size(24.dp).clip(CircleShape).background(
                                when (step.status) {
                                    StepStatus.COMPLETED -> Color(0xFF4CAF50)
                                    StepStatus.IN_PROGRESS -> ZenGold
                                    StepStatus.FAILED -> ZenRed
                                    StepStatus.SKIPPED -> Color.Gray
                                    else -> YinCardBg
                                }
                            ), contentAlignment = Alignment.Center) {
                                when (step.status) {
                                    StepStatus.COMPLETED -> Icon(Icons.Default.Check, null, tint = Color.White, modifier = Modifier.size(14.dp))
                                    StepStatus.IN_PROGRESS -> CircularProgressIndicator(modifier = Modifier.size(14.dp), color = Color.White, strokeWidth = 2.dp)
                                    StepStatus.FAILED -> Icon(Icons.Default.Close, null, tint = Color.White, modifier = Modifier.size(14.dp))
                                    else -> Text("${index + 1}", color = YinTextSecondary, fontSize = 10.sp)
                                }
                            }
                            Spacer(Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(step.name, color = YinText, fontWeight = FontWeight.Medium, fontSize = 13.sp)
                                if (step.toolUsed.isNotBlank()) Text("Tool: ${step.toolUsed}", color = ZenGold, fontSize = 10.sp)
                                if (step.result.isNotBlank()) Text(step.result, color = YinTextSecondary, fontSize = 11.sp, maxLines = 2)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    when (task.status) {
                        TaskStatus.PENDING -> Button(onClick = { AutomationEngine.startTask(task.id); showTaskDetail = null }, colors = ButtonDefaults.buttonColors(containerColor = ZenGold)) { Text("Start", color = Color.Black) }
                        TaskStatus.RUNNING -> Button(onClick = { AutomationEngine.pauseTask(task.id); showTaskDetail = null }, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF9800))) { Text("Pause", color = Color.White) }
                        TaskStatus.PAUSED -> Button(onClick = { AutomationEngine.startTask(task.id); showTaskDetail = null }, colors = ButtonDefaults.buttonColors(containerColor = ZenGold)) { Text("Resume", color = Color.Black) }
                        else -> {}
                    }
                    TextButton(onClick = { AutomationEngine.cancelTask(task.id); showTaskDetail = null }) { Text("Cancel Task", color = ZenRed) }
                    TextButton(onClick = { showTaskDetail = null }) { Text("Close", color = ZenGold) }
                }
            },
            containerColor = if (isDark) Color(0xFF14131A) else Color(0xFFF7F4EE)
        )
    }

    // ==================== MAIN UI ====================
    Column(modifier = Modifier.fillMaxSize().background(if (isDark) Color(0xFF070709) else Color(0xFFF1F0EC))) {
        // Header
        Surface(color = if (isDark) YinBlack else YangWhite, shadowElevation = 4.dp) {
            Column(modifier = Modifier.statusBarsPadding()) {
                Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onMenuClick) { Icon(Icons.Default.Menu, "Menu", tint = if (isDark) Color.White else Color.Black) }
                    Column(modifier = Modifier.weight(1f)) {
                        Text("🤖 AUTOMATION DASHBOARD", fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold,
                            color = if (isDark) ZenGold else Color(0xFF9E7E1D), style = MaterialTheme.typography.titleMedium)
                        if (isAgentRunning) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(Color(0xFF4CAF50)))
                                Spacer(Modifier.width(4.dp))
                                Text("Agent Active", color = Color(0xFF4CAF50), fontSize = 11.sp)
                            }
                        }
                    }
                    IconButton(onClick = { showCreateDialog = true }) { Icon(Icons.Default.Add, "New Task", tint = ZenGold) }
                }

                // Tab row
                Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    listOf("Active" to 0, "History" to 1, "Logs" to 2, "Agent" to 3).forEach { (label, idx) ->
                        FilterChip(selected = activeTab == idx, onClick = { activeTab = idx },
                            label = { Text(label, fontSize = 11.sp) },
                            colors = FilterChipDefaults.filterChipColors(selectedContainerColor = ZenGold.copy(alpha = 0.2f), selectedLabelColor = ZenGold))
                    }
                }
            }
        }

        // Content
        when (activeTab) {
            0 -> { // Active Tasks
                if (activeTasks.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Icon(Icons.Default.Schedule, null, modifier = Modifier.size(64.dp), tint = Color.Gray.copy(alpha = 0.4f))
                            Text("No Active Tasks", color = Color.Gray, fontSize = 16.sp)
                            Text("Create a new automation task to get started", color = Color.Gray.copy(alpha = 0.6f), fontSize = 12.sp)
                            Button(onClick = { showCreateDialog = true }, colors = ButtonDefaults.buttonColors(containerColor = ZenGold)) {
                                Text("Create Task", color = Color.Black)
                            }
                        }
                    }
                } else {
                    LazyColumn(contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(activeTasks, key = { it.id }) { task ->
                            TaskCard(task = task, onClick = { showTaskDetail = task }, onPause = { AutomationEngine.pauseTask(task.id) },
                                onCancel = { AutomationEngine.cancelTask(task.id) }, onStart = { AutomationEngine.startTask(task.id) })
                        }
                    }
                }
            }

            1 -> { // History
                if (completedTasks.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.History, null, modifier = Modifier.size(64.dp), tint = Color.Gray.copy(alpha = 0.4f))
                            Text("No History", color = Color.Gray, fontSize = 16.sp)
                        }
                    }
                } else {
                    LazyColumn(contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        item {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("${completedTasks.size} completed tasks", color = YinTextSecondary, fontSize = 12.sp)
                                TextButton(onClick = { AutomationEngine.clearHistory() }) { Text("Clear All", color = ZenRed, fontSize = 11.sp) }
                            }
                        }
                        items(completedTasks, key = { it.id }) { task ->
                            TaskCard(task = task, onClick = { showTaskDetail = task }, isHistory = true)
                        }
                    }
                }
            }

            2 -> { // Logs
                if (logs.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("No logs yet", color = Color.Gray) }
                } else {
                    LazyColumn(contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        item {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("Agent Logs (${logs.size})", color = YinTextSecondary, fontSize = 12.sp)
                                IconButton(onClick = {
                                    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    cm.setPrimaryClip(android.content.ClipData.newPlainText("logs", logs.joinToString("\n") { "[${it.level}] ${it.message}" }))
                                    Toast.makeText(context, "Logs copied", Toast.LENGTH_SHORT).show()
                                }, modifier = Modifier.size(28.dp)) { Icon(Icons.Default.ContentCopy, null, tint = YinTextSecondary.copy(alpha = 0.5f), modifier = Modifier.size(16.dp)) }
                            }
                        }
                        items(logs.take(200)) { log ->
                            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                                Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(
                                    when (log.level) { LogLevel.SUCCESS -> Color(0xFF4CAF50); LogLevel.ERROR -> ZenRed; LogLevel.WARNING -> Color(0xFFFF9800); else -> YinTextSecondary }
                                ))
                                Spacer(Modifier.width(8.dp))
                                Text(log.message, color = YinText, fontSize = 11.sp, fontFamily = FontFamily.Monospace, modifier = Modifier.weight(1f))
                                Text(SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(log.timestamp)), color = YinTextSecondary, fontSize = 9.sp)
                            }
                        }
                    }
                }
            }

            3 -> { // Agent Control
                AgentControlPanel(isDark = isDark)
            }
        }
    }
}

// ==================== TASK CARD ====================

@Composable
private fun TaskCard(
    task: AutomationTask,
    onClick: () -> Unit,
    onPause: (() -> Unit)? = null,
    onCancel: (() -> Unit)? = null,
    onStart: (() -> Unit)? = null,
    isHistory: Boolean = false
) {
    val statusColor = when (task.status) {
        TaskStatus.RUNNING -> ZenGold
        TaskStatus.COMPLETED -> Color(0xFF4CAF50)
        TaskStatus.FAILED -> ZenRed
        TaskStatus.PAUSED -> Color(0xFFFF9800)
        TaskStatus.CANCELLED -> Color.Gray
        else -> YinTextSecondary
    }

    val statusIcon = when (task.status) {
        TaskStatus.RUNNING -> Icons.Default.PlayArrow
        TaskStatus.COMPLETED -> Icons.Default.CheckCircle
        TaskStatus.FAILED -> Icons.Default.Error
        TaskStatus.PAUSED -> Icons.Default.Pause
        TaskStatus.CANCELLED -> Icons.Default.Cancel
        else -> Icons.Default.Schedule
    }

    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = YinCardBg),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, statusColor.copy(alpha = 0.3f))
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Header
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(statusIcon, null, tint = statusColor, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(task.title, color = YinText, fontWeight = FontWeight.Bold, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(task.description.ifBlank { "No description" }, color = YinTextSecondary, fontSize = 11.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                // Category badge
                Surface(color = statusColor.copy(alpha = 0.15f), shape = RoundedCornerShape(4.dp)) {
                    Text(task.category.name.replace("_", " "), color = statusColor, fontSize = 9.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                }
            }

            Spacer(Modifier.height(8.dp))

            // Progress
            if (!isHistory) {
                LinearProgressIndicator(progress = { task.progress }, modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)), color = statusColor, trackColor = YinBlack)
                Spacer(Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                    Text("${task.completedSteps}/${task.totalSteps} steps", color = YinTextSecondary, fontSize = 10.sp)
                    Text("${(task.progress * 100).toInt()}%", color = statusColor, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                }
                if (task.currentStep.isNotBlank()) {
                    Text(task.currentStep, color = YinTextSecondary, fontSize = 10.sp, fontFamily = FontFamily.Monospace, maxLines = 1)
                }
            }

            // Action buttons
            if (!isHistory && task.status != TaskStatus.COMPLETED && task.status != TaskStatus.FAILED && task.status != TaskStatus.CANCELLED) {
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    if (task.status == TaskStatus.PENDING && onStart != null) {
                        Button(onClick = onStart, colors = ButtonDefaults.buttonColors(containerColor = ZenGold), modifier = Modifier.weight(1f).height(34.dp)) {
                            Icon(Icons.Default.PlayArrow, null, modifier = Modifier.size(16.dp)); Text(" Start", fontSize = 11.sp)
                        }
                    }
                    if (task.status == TaskStatus.RUNNING && onPause != null) {
                        Button(onClick = onPause, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF9800)), modifier = Modifier.weight(1f).height(34.dp)) {
                            Icon(Icons.Default.Pause, null, modifier = Modifier.size(16.dp)); Text(" Pause", fontSize = 11.sp)
                        }
                    }
                    if (task.status == TaskStatus.PAUSED && onStart != null) {
                        Button(onClick = onStart, colors = ButtonDefaults.buttonColors(containerColor = ZenGold), modifier = Modifier.weight(1f).height(34.dp)) {
                            Icon(Icons.Default.PlayArrow, null, modifier = Modifier.size(16.dp)); Text(" Resume", fontSize = 11.sp)
                        }
                    }
                    if (onCancel != null) {
                        Button(onClick = onCancel, colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent), border = BorderStroke(1.dp, ZenRed), modifier = Modifier.weight(1f).height(34.dp)) {
                            Text("Cancel", color = ZenRed, fontSize = 11.sp)
                        }
                    }
                }
            }
        }
    }
}

// ==================== AGENT CONTROL PANEL ====================

@Composable
private fun AgentControlPanel(isDark: Boolean) {
    val context = LocalContext.current
    var commandInput by remember { mutableStateOf("") }
    var outputText by remember { mutableStateOf("🤖 Automation Agent Ready\n\nI can execute tasks using:\n• Web Browser — navigate, search, download\n• File Manager — browse, copy, compress, extract\n• Video Editor — process, trim, export\n• GitHub API — repos, issues, PRs\n• Telegram API — messages, bots\n• Code Editor — generate, format, run\n\nType a command to begin...") }
    val scope = rememberCoroutineScope()

    Column(modifier = Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // Agent status
        Card(colors = CardDefaults.cardColors(containerColor = YinCardBg), shape = RoundedCornerShape(12.dp)) {
            Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(if (AutomationEngine.isAgentRunning) Color(0xFF4CAF50) else Color.Gray))
                Spacer(Modifier.width(8.dp))
                Text(if (AutomationEngine.isAgentRunning) "Agent is running" else "Agent idle", color = YinText, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Spacer(Modifier.weight(1f))
                Text("Tasks: ${AutomationEngine.activeTasks.size} active, ${AutomationEngine.completedTasks.size} done", color = YinTextSecondary, fontSize = 10.sp)
            }
        }

        // Output console
        Card(modifier = Modifier.weight(1f).fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color(0xFF0F0F12)), shape = RoundedCornerShape(12.dp)) {
            LazyColumn(modifier = Modifier.fillMaxSize().padding(10.dp)) {
                item { Text(outputText, color = Color(0xFF8CBE91), fontSize = 12.sp, fontFamily = FontFamily.Monospace) }
            }
        }

        // Command input
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = commandInput, onValueChange = { commandInput = it },
                modifier = Modifier.weight(1f).height(48.dp),
                placeholder = { Text("Agent command...", fontSize = 12.sp, color = Color.Gray) },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = ZenGold, unfocusedBorderColor = Color(0xFF333340)),
                shape = RoundedCornerShape(10.dp)
            )
            Button(onClick = {
                if (commandInput.isBlank()) return@Button
                outputText = "> $commandInput\n"
                val cmd = commandInput.lowercase()

                // Parse command and create task
                val task = when {
                    cmd.contains("download") && cmd.contains("image") -> AutomationEngine.createTask(
                        "Download Images", commandInput, listOf(
                            TaskStep("Navigate to source", "Open browser to find images", "browser"),
                            TaskStep("Detect images", "Scan page for image elements", "ai_agent"),
                            TaskStep("Download all", "Save images to storage", "download_manager"),
                            TaskStep("Verify", "Confirm all files downloaded", "file_manager")
                        ), TaskCategory.WEB_SCRAPING
                    )
                    cmd.contains("compress") || cmd.contains("zip") -> AutomationEngine.createTask(
                        "Compress Files", commandInput, listOf(
                            TaskStep("Select files", "Choose files to compress", "file_manager"),
                            TaskStep("Create archive", "Build ZIP file", "file_manager"),
                            TaskStep("Verify size", "Check compressed size", "file_manager")
                        ), TaskCategory.FILE_OPERATION
                    )
                    cmd.contains("github") || cmd.contains("repo") -> AutomationEngine.createTask(
                        "GitHub Operation", commandInput, listOf(
                            TaskStep("Authenticate", "Verify GitHub token", "github_api"),
                            TaskStep("Execute", "Perform GitHub operation", "github_api"),
                            TaskStep("Confirm", "Verify operation success", "github_api")
                        ), TaskCategory.API_INTEGRATION
                    )
                    cmd.contains("telegram") || cmd.contains("send") -> AutomationEngine.createTask(
                        "Telegram Operation", commandInput, listOf(
                            TaskStep("Connect", "Verify bot connection", "telegram_api"),
                            TaskStep("Send", "Send message/broadcast", "telegram_api"),
                            TaskStep("Confirm", "Check delivery status", "telegram_api")
                        ), TaskCategory.API_INTEGRATION
                    )
                    else -> AutomationEngine.createTask(
                        "Custom Task", commandInput, listOf(
                            TaskStep("Analyze", "Understanding your request", "ai_agent"),
                            TaskStep("Plan", "Creating execution plan", "ai_agent"),
                            TaskStep("Execute", "Running the task", "automation_engine"),
                            TaskStep("Report", "Generating completion report", "ai_agent")
                        ), TaskCategory.GENERAL
                    )
                }
                AutomationEngine.startTask(task.id)
                outputText += "✅ Task created and started: ${task.title}\nTask ID: ${task.id.take(8)}..."
                commandInput = ""
            }, colors = ButtonDefaults.buttonColors(containerColor = ZenGold), modifier = Modifier.height(48.dp)) {
                Icon(Icons.Default.Terminal, null, modifier = Modifier.size(20.dp), tint = Color.Black)
            }
        }
    }
}

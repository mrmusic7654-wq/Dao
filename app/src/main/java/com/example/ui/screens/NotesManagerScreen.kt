package com.example.ui.screens

import android.content.*
import android.content.pm.PackageManager
import android.Manifest
import android.os.*
import android.speech.RecognizerIntent
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.*
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.*
import kotlinx.coroutines.*
import java.io.*
import java.text.SimpleDateFormat
import java.util.*

// ==================== DATA MODELS ====================

data class Note(
    val id: String = UUID.randomUUID().toString(),
    val title: String = "Untitled",
    val content: String = "",
    val folder: String = "General",
    val tags: List<String> = emptyList(),
    val isPinned: Boolean = false,
    val isChecklist: Boolean = false,
    val checklistItems: List<ChecklistItem> = emptyList(),
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val color: NoteColor = NoteColor.DEFAULT,
    val wordCount: Int = 0,
    val isVoiceNote: Boolean = false
)

data class ChecklistItem(
    val id: String = UUID.randomUUID().toString(),
    val text: String,
    var isChecked: Boolean = false
)

enum class NoteColor(val label: String, val bgColor: Color, val borderColor: Color) {
    DEFAULT("Default", Color(0xFF1A1A24), Color(0xFF333340)),
    RED("Red", Color(0xFF2A1A1A), Color(0xFF553333)),
    BLUE("Blue", Color(0xFF1A1A2A), Color(0xFF335555)),
    GREEN("Green", Color(0xFF1A2A1A), Color(0xFF335533)),
    PURPLE("Purple", Color(0xFF2A1A2A), Color(0xFF553355)),
    GOLD("Gold", Color(0xFF2A2A1A), Color(0xFF555533))
}

data class NoteFolder(
    val name: String,
    val icon: String = "📁",
    val noteCount: Int = 0,
    val color: Color = ZenSienna
)

enum class NotesViewMode { ALL, FOLDERS, TAGS, SEARCH, EDITOR, ARCHIVE }

// ==================== NOTES ENGINE ====================

object NotesEngine {
    val notes = mutableStateListOf<Note>()
    val folders = mutableStateListOf(
        NoteFolder("General", "📁", 0, ZenSienna),
        NoteFolder("Work", "💼", 0, ZenBlue),
        NoteFolder("Personal", "👤", 0, ZenGold),
        NoteFolder("Ideas", "💡", 0, Color(0xFFCE93D8)),
        NoteFolder("Code Snippets", "💻", 0, Color(0xFF90CAF9)),
        NoteFolder("Meeting Notes", "📋", 0, Color(0xFFFF8A65)),
        NoteFolder("Journal", "📔", 0, Color(0xFF81C784))
    )

    fun createNote(title: String = "Untitled", content: String = "", folder: String = "General", tags: List<String> = emptyList()): Note {
        val note = Note(title = title, content = content, folder = folder, tags = tags, wordCount = content.split(" ").size)
        notes.add(0, note)
        updateFolderCounts()
        return note
    }

    fun updateNote(id: String, title: String? = null, content: String? = null, folder: String? = null, tags: List<String>? = null) {
        val index = notes.indexOfFirst { it.id == id }
        if (index == -1) return
        notes[index] = notes[index].copy(
            title = title ?: notes[index].title,
            content = content ?: notes[index].content,
            folder = folder ?: notes[index].folder,
            tags = tags ?: notes[index].tags,
            updatedAt = System.currentTimeMillis(),
            wordCount = (content ?: notes[index].content).split(" ").size
        )
        updateFolderCounts()
    }

    fun deleteNote(id: String) {
        notes.removeAll { it.id == id }
        updateFolderCounts()
    }

    fun togglePin(id: String) {
        val index = notes.indexOfFirst { it.id == id }
        if (index == -1) return
        notes[index] = notes[index].copy(isPinned = !notes[index].isPinned)
    }

    fun toggleChecklistItem(noteId: String, itemId: String) {
        val noteIdx = notes.indexOfFirst { it.id == noteId }
        if (noteIdx == -1) return
        val note = notes[noteIdx]
        val items = note.checklistItems.map { if (it.id == itemId) it.copy(isChecked = !it.isChecked) else it }
        notes[noteIdx] = note.copy(checklistItems = items, updatedAt = System.currentTimeMillis())
    }

    fun addChecklistItem(noteId: String, text: String) {
        val noteIdx = notes.indexOfFirst { it.id == noteId }
        if (noteIdx == -1) return
        val note = notes[noteIdx]
        notes[noteIdx] = note.copy(
            checklistItems = note.checklistItems + ChecklistItem(text = text),
            updatedAt = System.currentTimeMillis()
        )
    }

    fun getAllTags(): List<String> {
        return notes.flatMap { it.tags }.distinct().sorted()
    }

    fun getWordCount(): Int = notes.sumOf { it.wordCount }
    fun getCharacterCount(): Int = notes.sumOf { it.content.length }

    fun getStats(): Triple<Int, Int, Int> {
        val totalNotes = notes.size
        val totalWords = getWordCount()
        val totalChars = getCharacterCount()
        return Triple(totalNotes, totalWords, totalChars)
    }

    fun exportAsMarkdown(note: Note): String {
        return buildString {
            appendLine("# ${note.title}")
            appendLine()
            appendLine("> Folder: ${note.folder}  ")
            if (note.tags.isNotEmpty()) appendLine("> Tags: ${note.tags.joinToString(", ")}  ")
            appendLine("> Created: ${SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(note.createdAt))}  ")
            appendLine("> Updated: ${SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(note.updatedAt))}  ")
            appendLine()
            appendLine("---")
            appendLine()
            appendLine(note.content)
            if (note.checklistItems.isNotEmpty()) {
                appendLine()
                appendLine("## Checklist")
                note.checklistItems.forEach { item ->
                    appendLine("- [${if (item.isChecked) "x" else " "}] ${item.text}")
                }
            }
        }
    }

    fun shareNote(context: Context, note: Note) {
        val markdown = exportAsMarkdown(note)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, note.title)
            putExtra(Intent.EXTRA_TEXT, markdown)
        }
        context.startActivity(Intent.createChooser(intent, "Share Note"))
    }

    fun getAiPromptForNote(note: Note): String {
        return buildString {
            appendLine("Here is a note titled '${note.title}':")
            appendLine("---")
            appendLine(note.content.take(2000))
            appendLine("---")
            appendLine("Please provide: 1) A brief summary 2) Key points 3) Action items if any")
        }
    }

    private fun updateFolderCounts() {
        val counts = notes.groupBy { it.folder }.mapValues { it.value.size }
        folders.forEachIndexed { index, folder ->
            folders[index] = folder.copy(noteCount = counts[folder.name] ?: 0)
        }
    }

    fun importMarkdownFile(content: String, fileName: String = "Imported Note") {
        val title = if (content.startsWith("# ")) content.lines().first().removePrefix("# ").trim() else fileName
        val body = if (content.startsWith("# ")) content.lines().drop(1).joinToString("\n").trim() else content
        createNote(title = title, content = body, folder = "General")
    }
}

// ==================== MAIN SCREEN ====================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotesManagerScreen(isDark: Boolean, onMenuClick: () -> Unit) {
    val context = LocalContext.current
    var viewMode by remember { mutableStateOf(NotesViewMode.ALL) }
    var selectedFolder by remember { mutableStateOf<String?>(null) }
    var selectedTag by remember { mutableStateOf<String?>(null) }
    var searchQuery by remember { mutableStateOf("") }
    var editingNote by remember { mutableStateOf<Note?>(null) }
    var showNewNoteDialog by remember { mutableStateOf(false) }
    var showFolderDialog by remember { mutableStateOf(false) }
    var showAIPanel by remember { mutableStateOf(false) }
    var showStatsDialog by remember { mutableStateOf(false) }
    var showExportDialog by remember { mutableStateOf<Note?>(null) }
    var newNoteTitle by remember { mutableStateOf("") }
    var newNoteContent by remember { mutableStateOf("") }
    var newNoteFolder by remember { mutableStateOf("General") }
    var newNoteTags by remember { mutableStateOf("") }
    var editorContent by remember { mutableStateOf("") }
    var editorTitle by remember { mutableStateOf("") }
    var isVoiceRecording by remember { mutableStateOf(false) }
    var aiResponse by remember { mutableStateOf("") }
    var isAILoading by remember { mutableStateOf(false) }

    val engine = NotesEngine
    val allNotes = engine.notes
    val allFolders = engine.folders
    val allTags = remember { derivedStateOf { engine.getAllTags() } }

    // Filter notes
    val filteredNotes = remember(searchQuery, selectedFolder, selectedTag, allNotes.toList()) {
        var result = allNotes.toList()
        if (searchQuery.isNotBlank()) {
            result = result.filter { it.title.contains(searchQuery, ignoreCase = true) || it.content.contains(searchQuery, ignoreCase = true) }
        }
        if (selectedFolder != null) {
            result = result.filter { it.folder == selectedFolder }
        }
        if (selectedTag != null) {
            result = result.filter { it.tags.contains(selectedTag) }
        }
        result.sortedWith(compareByDescending<Note> { it.isPinned }.thenByDescending { it.updatedAt })
    }

    val stats = remember { derivedStateOf { engine.getStats() } }

    // Import file launcher
    val fileImporter = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let {
            try {
                val content = context.contentResolver.openInputStream(it)?.bufferedReader()?.readText() ?: ""
                engine.importMarkdownFile(content, uri.lastPathSegment ?: "Imported Note")
                Toast.makeText(context, "Note imported!", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(context, "Import failed: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // Voice recognition
    val voiceLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK && result.data != null) {
            val matches = result.data!!.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            if (!matches.isNullOrEmpty()) {
                editorContent += "\n${matches[0]}"
                isVoiceRecording = false
            }
        }
    }

    fun startVoiceInput() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak your note...")
        }
        try {
            isVoiceRecording = true
            voiceLauncher.launch(intent)
        } catch (e: Exception) {
            Toast.makeText(context, "Voice input not available", Toast.LENGTH_SHORT).show()
        }
    }

    // ==================== DIALOGS ====================

    if (showNewNoteDialog) {
        AlertDialog(
            onDismissRequest = { showNewNoteDialog = false },
            title = { Text("New Note", fontFamily = FontFamily.Serif, color = ZenGold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = newNoteTitle, onValueChange = { newNoteTitle = it },
                        label = { Text("Title") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = ZenGold))
                    OutlinedTextField(value = newNoteContent, onValueChange = { newNoteContent = it },
                        label = { Text("Content (optional)") }, maxLines = 3, modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = ZenGold))
                    Text("Folder", color = YinTextSecondary, fontSize = 12.sp)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(allFolders) { folder ->
                            FilterChip(selected = newNoteFolder == folder.name, onClick = { newNoteFolder = folder.name },
                                label = { Text("${folder.icon} ${folder.name}", fontSize = 10.sp) },
                                colors = FilterChipDefaults.filterChipColors(selectedContainerColor = ZenGold.copy(alpha = 0.2f), selectedLabelColor = ZenGold))
                        }
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    val tags = newNoteTags.split(",").map { it.trim() }.filter { it.isNotBlank() }
                    engine.createNote(newNoteTitle.ifBlank { "Untitled" }, newNoteContent, newNoteFolder, tags)
                    showNewNoteDialog = false; newNoteTitle = ""; newNoteContent = ""; newNoteTags = ""
                }, colors = ButtonDefaults.buttonColors(containerColor = ZenGold)) { Text("Create", color = Color.Black) }
            },
            dismissButton = { TextButton(onClick = { showNewNoteDialog = false }) { Text("Cancel", color = ZenGold) } },
            containerColor = if (isDark) Color(0xFF14131A) else Color(0xFFF7F4EE)
        )
    }

    if (showStatsDialog) {
        AlertDialog(
            onDismissRequest = { showStatsDialog = false },
            title = { Text("📊 Notes Statistics", fontFamily = FontFamily.Serif, color = ZenGold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    InfoRow("Total Notes", "${stats.value.first}")
                    InfoRow("Total Words", "${stats.value.second}")
                    InfoRow("Total Characters", "${stats.value.third}")
                    InfoRow("Folders", "${allFolders.size}")
                    InfoRow("Tags", "${allTags.value.size}")
                    Divider(color = Color(0xFF333340))
                    allFolders.forEach { folder ->
                        InfoRow("${folder.icon} ${folder.name}", "${folder.noteCount} notes")
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showStatsDialog = false }) { Text("Close", color = ZenGold) } },
            containerColor = if (isDark) Color(0xFF14131A) else Color(0xFFF7F4EE)
        )
    }

    // ==================== MAIN UI ====================

    if (editingNote != null) {
        // ==================== EDITOR VIEW ====================
        val note = editingNote!!
        LaunchedEffect(note) { editorTitle = note.title; editorContent = note.content }

        Column(modifier = Modifier.fillMaxSize().background(if (isDark) Color(0xFF070709) else Color(0xFFF1F0EC))) {
            // Editor header
            Surface(color = if (isDark) YinBlack else YangWhite, shadowElevation = 4.dp) {
                Column(modifier = Modifier.statusBarsPadding()) {
                    Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = {
                            engine.updateNote(note.id, title = editorTitle, content = editorContent)
                            editingNote = null
                        }) { Icon(Icons.Default.ArrowBack, null, tint = YinText) }
                        Column(modifier = Modifier.weight(1f)) {
                            BasicTextField(value = editorTitle, onValueChange = { editorTitle = it },
                                textStyle = TextStyle(color = ZenGold, fontSize = 16.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Serif),
                                singleLine = true, cursorBrush = SolidColor(ZenGold),
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp))
                            Text("${note.folder} • ${note.wordCount} words", color = YinTextSecondary, fontSize = 10.sp)
                        }
                        IconButton(onClick = { isVoiceRecording = true; startVoiceInput() }) { Icon(Icons.Default.Mic, null, tint = if (isVoiceRecording) ZenRed else YinTextSecondary) }
                        IconButton(onClick = {
                            val tags = engine.getAllTags().joinToString(", ")
                            editorContent += "\n\n#tags: $tags"
                        }) { Icon(Icons.Default.Label, null, tint = YinTextSecondary) }
                        IconButton(onClick = {
                            engine.togglePin(note.id)
                            Toast.makeText(context, if (note.isPinned) "Unpinned" else "Pinned!", Toast.LENGTH_SHORT).show()
                        }) { Icon(if (note.isPinned) Icons.Default.PushPin else Icons.Outlined.PushPin, null, tint = if (note.isPinned) ZenGold else YinTextSecondary) }
                        IconButton(onClick = { engine.shareNote(context, note) }) { Icon(Icons.Default.Share, null, tint = YinTextSecondary) }
                    }
                }
            }

            // Checklist section
            if (note.isChecklist || note.checklistItems.isNotEmpty()) {
                Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                    colors = CardDefaults.cardColors(containerColor = note.color.bgColor), shape = RoundedCornerShape(8.dp)) {
                    Column(modifier = Modifier.padding(8.dp)) {
                        Text("✅ Checklist", color = ZenGold, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        note.checklistItems.forEach { item ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(checked = item.isChecked, onCheckedChange = { engine.toggleChecklistItem(note.id, item.id) },
                                    colors = CheckboxDefaults.colors(checkedColor = Color(0xFF4CAF50)), modifier = Modifier.size(20.dp))
                                Text(item.text, color = if (item.isChecked) YinTextSecondary else YinText, fontSize = 12.sp)
                            }
                        }
                    }
                }
            }

            // Editor content
            Box(modifier = Modifier.weight(1f).padding(8.dp).clip(RoundedCornerShape(8.dp)).background(note.color.bgColor).border(1.dp, note.color.borderColor, RoundedCornerShape(8.dp))) {
                BasicTextField(value = editorContent, onValueChange = { editorContent = it },
                    modifier = Modifier.fillMaxSize().padding(12.dp),
                    textStyle = TextStyle(color = YinText, fontSize = 13.sp, fontFamily = FontFamily.Monospace, lineHeight = 20.sp),
                    cursorBrush = SolidColor(ZenGold),
                    decorationBox = { innerTextField ->
                        if (editorContent.isEmpty()) {
                            Text("Start writing in markdown...\n\n# Heading\n**Bold** *Italic*\n- List item\n```\ncode block\n```", color = YinTextSecondary.copy(alpha = 0.4f), fontSize = 13.sp, fontFamily = FontFamily.Monospace)
                        }
                        innerTextField()
                    })
            }

            // Editor toolbar
            Surface(color = if (isDark) Color(0xFF14141E) else Color.White, shadowElevation = 4.dp) {
                Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    EditorTool("B", "Bold") { editorContent += "****" }
                    EditorTool("I", "Italic") { editorContent += "**" }
                    EditorTool("H1", "# ") { editorContent += "\n# " }
                    EditorTool("H2", "## ") { editorContent += "\n## " }
                    EditorTool("•", "- ") { editorContent += "\n- " }
                    EditorTool("☐", "- [ ] ") { editorContent += "\n- [ ] " }
                    EditorTool("`", "code") { editorContent += "``" }
                    EditorTool(">", "quote") { editorContent += "\n> " }
                    EditorTool("---", "line") { editorContent += "\n---\n" }
                }
            }
        }
    } else {
        // ==================== LIST VIEW ====================
        Column(modifier = Modifier.fillMaxSize().background(if (isDark) Color(0xFF070709) else Color(0xFFF1F0EC))) {
            // Header
            Surface(color = if (isDark) YinBlack else YangWhite, shadowElevation = 4.dp) {
                Column(modifier = Modifier.statusBarsPadding()) {
                    Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = onMenuClick) { Icon(Icons.Default.Menu, null, tint = if (isDark) Color.White else Color.Black) }
                        Text("📝 NOTES", fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold,
                            color = if (isDark) ZenGold else Color(0xFF9E7E1D), style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.weight(1f))
                        IconButton(onClick = { showStatsDialog = true }) { Icon(Icons.Default.BarChart, null, tint = YinTextSecondary, modifier = Modifier.size(20.dp)) }
                        IconButton(onClick = { fileImporter.launch("text/*") }) { Icon(Icons.Default.FileUpload, "Import", tint = YinTextSecondary, modifier = Modifier.size(20.dp)) }
                        IconButton(onClick = { showNewNoteDialog = true }) { Icon(Icons.Default.Add, null, tint = ZenGold) }
                    }
                    // Search
                    OutlinedTextField(value = searchQuery, onValueChange = { searchQuery = it },
                        placeholder = { Text("Search notes...", fontSize = 12.sp, color = Color.Gray) },
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp).height(40.dp),
                        leadingIcon = { Icon(Icons.Default.Search, null, tint = Color.Gray, modifier = Modifier.size(16.dp)) },
                        singleLine = true, shape = RoundedCornerShape(10.dp),
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = ZenGold, unfocusedBorderColor = Color(0xFF333340)))
                    // Folder chips
                    LazyRow(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        item {
                            FilterChip(selected = selectedFolder == null && selectedTag == null, onClick = { selectedFolder = null; selectedTag = null },
                                label = { Text("All (${allNotes.size})", fontSize = 10.sp) },
                                colors = FilterChipDefaults.filterChipColors(selectedContainerColor = ZenGold.copy(alpha = 0.2f), selectedLabelColor = ZenGold))
                        }
                        items(allFolders) { folder ->
                            FilterChip(selected = selectedFolder == folder.name, onClick = { selectedFolder = if (selectedFolder == folder.name) null else folder.name; selectedTag = null },
                                label = { Text("${folder.icon} ${folder.name} (${folder.noteCount})", fontSize = 10.sp) },
                                colors = FilterChipDefaults.filterChipColors(selectedContainerColor = ZenGold.copy(alpha = 0.2f), selectedLabelColor = ZenGold))
                        }
                    }
                }
            }

            // Notes list
            if (filteredNotes.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Default.Note, null, modifier = Modifier.size(64.dp), tint = Color.Gray.copy(alpha = 0.4f))
                        Text("No Notes", color = Color.Gray, fontSize = 16.sp)
                        Text("Tap + to create your first note", color = Color.Gray.copy(alpha = 0.6f), fontSize = 12.sp)
                    }
                }
            } else {
                LazyColumn(contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(filteredNotes, key = { it.id }) { note ->
                        Card(modifier = Modifier.fillMaxWidth().clickable(onClick = { editingNote = note }),
                            colors = CardDefaults.cardColors(containerColor = note.color.bgColor),
                            border = BorderStroke(1.dp, note.color.borderColor), shape = RoundedCornerShape(10.dp)) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    if (note.isPinned) { Icon(Icons.Default.PushPin, null, tint = ZenGold, modifier = Modifier.size(14.dp)); Spacer(Modifier.width(4.dp)) }
                                    Text(note.title.ifBlank { "Untitled" }, color = YinText, fontWeight = FontWeight.Bold, fontSize = 14.sp, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(SimpleDateFormat("MMM dd", Locale.getDefault()).format(Date(note.updatedAt)), color = YinTextSecondary, fontSize = 10.sp)
                                }
                                if (note.content.isNotBlank()) {
                                    Text(note.content.take(120).replace("\n", " "), color = YinTextSecondary, fontSize = 11.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 2.dp))
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 4.dp)) {
                                    Text("${note.folder}", color = ZenGold, fontSize = 9.sp)
                                    Text("${note.wordCount} words", color = YinTextSecondary, fontSize = 9.sp)
                                    if (note.checklistItems.isNotEmpty()) {
                                        val done = note.checklistItems.count { it.isChecked }
                                        Text("☐ $done/${note.checklistItems.size}", color = if (done == note.checklistItems.size) Color(0xFF4CAF50) else YinTextSecondary, fontSize = 9.sp)
                                    }
                                }
                                if (note.tags.isNotEmpty()) {
                                    LazyRow(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.padding(top = 4.dp)) {
                                        items(note.tags) { tag ->
                                            Surface(color = ZenGold.copy(alpha = 0.1f), shape = RoundedCornerShape(4.dp)) {
                                                Text(tag, color = ZenGold, fontSize = 8.sp, modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp))
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EditorTool(label: String, insert: String, onClick: () -> Unit) {
    Surface(modifier = Modifier.clip(RoundedCornerShape(6.dp)).clickable(onClick = onClick).padding(horizontal = 10.dp, vertical = 6.dp),
        color = Color(0xFF1A1A24)) {
        Text(label, color = YinTextSecondary, fontSize = 12.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = YinTextSecondary, fontSize = 11.sp)
        Text(value, color = YinText, fontSize = 11.sp)
    }
}

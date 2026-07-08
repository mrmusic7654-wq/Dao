package com.example.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.db.AppDatabase
import com.example.data.model.ChatSession
import com.example.data.model.ChatMessage
import com.example.data.model.UserProfile
import com.example.data.repository.ChatRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class TaskItem(val id: Long, val title: String, val isCompleted: Boolean)

@OptIn(ExperimentalCoroutinesApi::class)
class ChatViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: ChatRepository

    // Sessions stream
    val allSessions: StateFlow<List<ChatSession>>

    // Current active session ID
    private val _activeSessionId = MutableStateFlow<Long?>(null)
    val activeSessionId: StateFlow<Long?> = _activeSessionId.asStateFlow()

    // Current messages stream (reactively switches based on activeSessionId)
    val activeMessages: StateFlow<List<ChatMessage>>

    // User Profile stats
    val userProfile: StateFlow<UserProfile>

    // Dao is processing/typing indicator
    private val _isDaoTyping = MutableStateFlow(false)
    val isDaoTyping: StateFlow<Boolean> = _isDaoTyping.asStateFlow()

    // Active Personality State
    private val _activePersonality = MutableStateFlow("Zen Sage")
    val activePersonality: StateFlow<String> = _activePersonality.asStateFlow()

    fun updatePersonality(newPersonality: String) {
        _activePersonality.value = newPersonality
    }

    // Active Input Mode State
    private val _activeInputMode = MutableStateFlow("Direct")
    val activeInputMode: StateFlow<String> = _activeInputMode.asStateFlow()

    fun updateInputMode(newMode: String) {
        _activeInputMode.value = newMode
    }

    // Projects list
    private val _allProjects = MutableStateFlow(listOf("Wisdom Quest 🌸", "Inner Calm App 🧘", "Cosmic Code Editor 💻", "Entropy Balance ⚖️"))
    val allProjects: StateFlow<List<String>> = _allProjects.asStateFlow()

    fun addProject(name: String) {
        if (name.isNotBlank() && !_allProjects.value.contains(name)) {
            _allProjects.value = _allProjects.value + name
        }
    }

    // Tasks list
    private val _allTasks = MutableStateFlow(listOf(
        TaskItem(1, "Inhale the quiet light", true),
        TaskItem(2, "Refactor the digital karma", false),
        TaskItem(3, "Contemplate the emptiness (Mu)", false)
    ))
    val allTasks: StateFlow<List<TaskItem>> = _allTasks.asStateFlow()

    fun addTask(title: String) {
        if (title.isNotBlank()) {
            val newId = (_allTasks.value.maxOfOrNull { it.id } ?: 0L) + 1
            _allTasks.value = _allTasks.value + TaskItem(newId, title, false)
        }
    }

    fun toggleTask(taskId: Long) {
        _allTasks.value = _allTasks.value.map {
            if (it.id == taskId) it.copy(isCompleted = !it.isCompleted) else it
        }
    }

    fun deleteTask(taskId: Long) {
        _allTasks.value = _allTasks.value.filter { it.id != taskId }
    }

    init {
        val chatDao = AppDatabase.getDatabase(application).chatDao()
        repository = ChatRepository(chatDao)

        allSessions = repository.allSessions
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = emptyList()
            )

        // Expose user profile, supply a starting profile if none is saved in Room yet
        userProfile = repository.userProfile
            .map { it ?: UserProfile(id = 1, xp = 0, yinBalance = 0.5f, levelName = "Novice Seeker", harmonyStreak = 0) }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = UserProfile(id = 1, xp = 0, yinBalance = 0.5f, levelName = "Novice Seeker", harmonyStreak = 0)
            )

        // Reactively fetch messages for the active session
        activeMessages = _activeSessionId
            .flatMapLatest { sessionId ->
                if (sessionId != null) {
                    repository.getMessagesForSession(sessionId)
                } else {
                    flowOf(emptyList())
                }
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = emptyList()
            )

        // Auto-create initial session if database is empty on start
        viewModelScope.launch {
            allSessions.first { it.isNotEmpty() || allSessions.value.isNotEmpty() || true } // wait until flow is resolved
            val currentSessions = allSessions.value
            if (currentSessions.isEmpty()) {
                val newId = repository.createNewSession("Intro to Dao ☯")
                _activeSessionId.value = newId
                
                // Add a welcome message from Dao
                repository.generateAndSaveDaoResponse(getApplication(), newId, "hello", _activePersonality.value)
            } else if (_activeSessionId.value == null) {
                _activeSessionId.value = currentSessions.first().id
            }
        }
    }

    fun selectSession(sessionId: Long) {
        _activeSessionId.value = sessionId
    }

    fun createNewSession(title: String = "New Discourse") {
        viewModelScope.launch {
            val newId = repository.createNewSession(title)
            _activeSessionId.value = newId
            
            // Dao introduces himself in the new session
            _isDaoTyping.value = true
            repository.generateAndSaveDaoResponse(getApplication(), newId, "hello", _activePersonality.value)
            _isDaoTyping.value = false
        }
    }

    fun deleteSession(sessionId: Long) {
        viewModelScope.launch {
            repository.deleteSession(sessionId)
            if (_activeSessionId.value == sessionId) {
                val remaining = allSessions.value.filter { it.id != sessionId }
                if (remaining.isNotEmpty()) {
                    _activeSessionId.value = remaining.first().id
                } else {
                    // Database is now empty, create a new session
                    val newId = repository.createNewSession("New Discourse")
                    _activeSessionId.value = newId
                    repository.generateAndSaveDaoResponse(getApplication(), newId, "hello", _activePersonality.value)
                }
            }
        }
    }

    fun renameSession(sessionId: Long, newTitle: String) {
        viewModelScope.launch {
            repository.updateSessionTitle(sessionId, newTitle)
        }
    }

    fun clearAllSessions() {
        viewModelScope.launch {
            allSessions.value.forEach {
                repository.deleteSession(it.id)
            }
            val newId = repository.createNewSession("Intro to Dao ☯")
            _activeSessionId.value = newId
            repository.generateAndSaveDaoResponse(getApplication(), newId, "hello", _activePersonality.value)
        }
    }

    fun resetProfile() {
        viewModelScope.launch {
            repository.resetProfile()
        }
    }

    fun sendMessage(content: String) {
        val sessionId = _activeSessionId.value ?: return
        if (content.isBlank() || _isDaoTyping.value) return

        viewModelScope.launch {
            // Save user message
            repository.insertUserMessage(sessionId, content)
            
            // Trigger Dao thinking & typing
            _isDaoTyping.value = true
            
            val mode = _activeInputMode.value
            if (mode == "Automation" || mode == "Agent") {
                // Multistep automation loop
                var currentPrompt = content
                var maxSteps = 10  // Safety limit
                while (maxSteps-- > 0) {
                    val response = repository.generateAndSaveDaoResponse(
                        getApplication(), sessionId, currentPrompt,
                        _activePersonality.value, mode
                    )
                    // Check if response contains actions
                    val actions = com.example.ui.automation.AutomationEngine.parseActions(response.replyText)
                    if (actions.isEmpty()) {
                        // No actions, task complete
                        break
                    }

                    // Execute each action and collect results
                    val results = mutableListOf<String>()
                    for ((action, params) in actions) {
                        val result = com.example.ui.automation.AutomationEngine.executeAction(action, params, getApplication())
                        results.add(result)
                    }

                    // Feed results back as the next prompt
                    currentPrompt = results.joinToString("\n\n") + 
                        "\n\nContinue the task or respond if done."
                }
            } else {
                // Normal mode: just generate a single response
                // Simulate natural spiritual typing delay (e.g. 1.2 seconds) to build dramatic, game-like anticipation
                kotlinx.coroutines.delay(1200)
                
                repository.generateAndSaveDaoResponse(getApplication(), sessionId, content, _activePersonality.value, _activeInputMode.value)
            }
            
            _isDaoTyping.value = false
        }
    }
}

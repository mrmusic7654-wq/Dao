package com.example.ui.screens

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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.*
import java.util.*
import kotlin.math.pow

data class Flashcard(
    val id: String = UUID.randomUUID().toString(),
    val question: String,
    val answer: String,
    val topic: String = "General",
    val interval: Int = 1, // Days until next review
    val ease: Float = 2.5f,
    val repetitions: Int = 0,
    val nextReview: Long = System.currentTimeMillis(),
    val createdAt: Long = System.currentTimeMillis()
)

object SpacedRepetitionEngine {
    fun calculateNextReview(card: Flashcard, quality: Int): Flashcard {
        // SM-2 algorithm
        val newEase = (card.ease + (0.1 - (5 - quality) * (0.08 + (5 - quality) * 0.02))).coerceAtLeast(1.3f)
        val newInterval = when {
            card.repetitions == 0 -> 1
            card.repetitions == 1 -> 6
            else -> (card.interval * newEase).toInt()
        }
        val nextReview = System.currentTimeMillis() + (newInterval * 24 * 60 * 60 * 1000L)
        return card.copy(
            ease = newEase,
            interval = newInterval,
            repetitions = card.repetitions + 1,
            nextReview = nextReview
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LearningModeScreen(isDark: Boolean, onMenuClick: () -> Unit) {
    var flashcards by remember { mutableStateOf(generateSampleFlashcards()) }
    var currentIndex by remember { mutableIntStateOf(0) }
    var showAnswer by remember { mutableStateOf(false) }
    var showAddDialog by remember { mutableStateOf(false) }
    var selectedTopic by remember { mutableStateOf("All") }
    var newQuestion by remember { mutableStateOf("") }
    var newAnswer by remember { mutableStateOf("") }
    var newTopic by remember { mutableStateOf("General") }

    val currentCard = flashcards.getOrNull(currentIndex)
    val dueCards = flashcards.filter { it.nextReview <= System.currentTimeMillis() }
    val topics = flashcards.map { it.topic }.distinct()

    if (showAddDialog) {
        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            title = { Text("Add Flashcard", color = ZenGold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = newQuestion, onValueChange = { newQuestion = it }, label = { Text("Question") })
                    OutlinedTextField(value = newAnswer, onValueChange = { newAnswer = it }, label = { Text("Answer") })
                    OutlinedTextField(value = newTopic, onValueChange = { newTopic = it }, label = { Text("Topic") })
                }
            },
            confirmButton = {
                Button(onClick = {
                    flashcards = flashcards + Flashcard(question = newQuestion, answer = newAnswer, topic = newTopic)
                    showAddDialog = false; newQuestion = ""; newAnswer = ""
                }) { Text("Add") }
            },
            dismissButton = { TextButton(onClick = { showAddDialog = false }) { Text("Cancel") } }
        )
    }

    Column(modifier = Modifier.fillMaxSize().background(if (isDark) Color(0xFF0A0A10) else Color(0xFFF5F5F5))) {
        // Header
        Surface(color = if (isDark) Color(0xFF14141E) else Color.White) {
            Row(modifier = Modifier.padding(12.dp).statusBarsPadding(), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onMenuClick) { Icon(Icons.Default.Menu, null) }
                Text("📚 Learning Mode", color = ZenGold, fontWeight = FontWeight.Bold)
                Spacer(Modifier.weight(1f))
                Text("Due: ${dueCards.size}", color = Color(0xFF4CAF50))
                IconButton(onClick = { showAddDialog = true }) { Icon(Icons.Default.Add, null, tint = ZenGold) }
            }
        }

        // Topic filter
        LazyRow(modifier = Modifier.padding(8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            item {
                FilterChip(selected = selectedTopic == "All", onClick = { selectedTopic = "All" },
                    label = { Text("All (${flashcards.size})") })
            }
            items(topics) { topic ->
                FilterChip(selected = selectedTopic == topic, onClick = { selectedTopic = topic },
                    label = { Text("$topic (${flashcards.count { it.topic == topic }})") })
            }
        }

        // Flashcard display
        if (currentCard != null) {
            Card(
                modifier = Modifier.fillMaxWidth().padding(16.dp).weight(1f)
                    .clickable { showAnswer = !showAnswer },
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A24)),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(
                    modifier = Modifier.fillMaxSize().padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text("${currentCard.topic} • Card ${currentIndex + 1}/${flashcards.size}",
                        color = YinTextSecondary, fontSize = 12.sp)
                    Spacer(Modifier.height(16.dp))
                    Text(
                        if (!showAnswer) currentCard.question else currentCard.answer,
                        color = YinText,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(Modifier.height(24.dp))
                    if (!showAnswer) {
                        Text("Tap to reveal answer", color = YinTextSecondary.copy(alpha = 0.5f), fontSize = 11.sp)
                    }
                }
            }

            // Rating buttons (show after answer revealed)
            if (showAnswer) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    listOf("Again" to 0, "Hard" to 2, "Good" to 3, "Easy" to 5).forEach { (label, quality) ->
                        Button(onClick = {
                            flashcards = flashcards.toMutableList().also {
                                it[currentIndex] = SpacedRepetitionEngine.calculateNextReview(currentCard, quality)
                            }
                            showAnswer = false
                            currentIndex = if (currentIndex < flashcards.size - 1) currentIndex + 1 else 0
                        }, colors = ButtonDefaults.buttonColors(
                            containerColor = when (quality) {
                                0 -> ZenRed; 2 -> Color(0xFFFF9800)
                                3 -> Color(0xFF4CAF50); else -> ZenGold
                            }
                        )) {
                            Text(label, fontSize = 13.sp)
                        }
                    }
                }
            }
        } else {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No flashcards! Add some to start learning.", color = Color.Gray)
            }
        }
    }
}

fun generateSampleFlashcards(): List<Flashcard> = listOf(
    Flashcard(question = "What is MVVM?", answer = "Model-View-ViewModel architecture pattern", topic = "Android"),
    Flashcard(question = "What is a Coroutine?", answer = "Lightweight thread for async operations in Kotlin", topic = "Kotlin"),
    Flashcard(question = "What does SOLID stand for?", answer = "Single Responsibility, Open-Closed, Liskov Substitution, Interface Segregation, Dependency Inversion", topic = "Programming")
)

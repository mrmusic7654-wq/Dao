package com.example.ui.screens

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.*

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

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = {
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
                LazyColumn(modifier = Modifier.fillMaxSize()) {
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

package com.example.ui.components

import android.widget.Toast
import androidx.compose.animation.core.*
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.ChatMessage
import com.example.data.model.UserProfile
import com.example.ui.theme.*
import com.example.ui.util.buildFormattedAnnotatedString

@Composable
fun GameHudPanel(profile: UserProfile, isDark: Boolean) {
    var level = 1L
    var neededForNext = 1_000_000L
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

    val blocks = visibleContent.split("```")

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalAlignment = if (message.isUser) Alignment.End else Alignment.Start
    ) {
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

package com.example.ui.components

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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.ChatSession
import com.example.ui.theme.*

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

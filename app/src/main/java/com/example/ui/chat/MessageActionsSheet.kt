package com.example.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Forward
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.Message
import com.example.ui.theme.HeartRed
import com.example.ui.theme.RoseGoldPrimary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MessageActionsSheet(
    message: Message,
    isFromMe: Boolean,
    onDismiss: () -> Unit,
    onReaction: (String) -> Unit,
    onReply: () -> Unit,
    onCopy: () -> Unit,
    onStar: () -> Unit,
    onPin: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onForward: () -> Unit = {},
    onSearch: () -> Unit = {},
    onSaveToMemories: () -> Unit = {}
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp)
                .navigationBarsPadding()
        ) {
            // Emoji reaction row (Scrollable like WhatsApp/Telegram)
            val reactionEmojis = listOf(
                "❤️", "🥰", "😘", "🔥", "🥺", "👍", "🌹", "😂", "🤣", 
                "😊", "😍", "😒", "😎", "😔", "😜", "😡", "😭", "😤", "🤫", 
                "💑", "👩‍❤️‍👨", "👨‍❤️‍👨", "👩‍❤️‍👩", "💍", "💌", "💖", "💘"
            )
            androidx.compose.foundation.lazy.LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(horizontal = 4.dp)
            ) {
                items(reactionEmojis.size) { index ->
                    val emoji = reactionEmojis[index]
                    Box(
                        modifier = Modifier
                            .size(46.dp)
                            .clip(CircleShape)
                            .background(Color.Transparent)
                            .clickable {
                                onReaction(emoji)
                                onDismiss()
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(text = emoji, fontSize = 28.sp)
                    }
                }
            }

            HorizontalDivider(
                modifier = Modifier.padding(vertical = 6.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
            )

            // Primary Actions Grid / List
            ListItem(
                headlineContent = { Text("Reply", fontWeight = FontWeight.Medium) },
                leadingContent = { Icon(Icons.AutoMirrored.Filled.Reply, contentDescription = null, tint = RoseGoldPrimary) },
                modifier = Modifier
                    .clickable {
                        onReply()
                        onDismiss()
                    }
                    .testTag("action_reply")
            )

            ListItem(
                headlineContent = { Text("Save to Our Memories ❤️", fontWeight = FontWeight.Medium) },
                supportingContent = { Text("Preserve in couple's keepsake timeline", fontSize = 11.sp) },
                leadingContent = { Icon(Icons.Filled.Favorite, contentDescription = null, tint = Color(0xFFE91E63)) },
                modifier = Modifier
                    .clickable {
                        onSaveToMemories()
                        onDismiss()
                    }
                    .testTag("action_save_memories")
            )

            ListItem(
                headlineContent = { Text("Copy Text", fontWeight = FontWeight.Medium) },
                leadingContent = { Icon(Icons.Default.ContentCopy, contentDescription = null) },
                modifier = Modifier
                    .clickable {
                        onCopy()
                        onDismiss()
                    }
                    .testTag("action_copy")
            )

            ListItem(
                headlineContent = { Text("Forward Message", fontWeight = FontWeight.Medium) },
                leadingContent = { Icon(Icons.AutoMirrored.Filled.Forward, contentDescription = null) },
                modifier = Modifier
                    .clickable {
                        onForward()
                        onDismiss()
                    }
                    .testTag("action_forward")
            )

            ListItem(
                headlineContent = { Text(if (message.isStarred) "Remove from Starred" else "Star Message", fontWeight = FontWeight.Medium) },
                leadingContent = {
                    Icon(
                        imageVector = if (message.isStarred) Icons.Filled.Star else Icons.Outlined.StarOutline,
                        contentDescription = null,
                        tint = if (message.isStarred) Color(0xFFFFB300) else MaterialTheme.colorScheme.onSurface
                    )
                },
                modifier = Modifier
                    .clickable {
                        onStar()
                        onDismiss()
                    }
                    .testTag("action_star")
            )

            ListItem(
                headlineContent = { Text(if (message.isPinned) "Unpin Message" else "Pin Message (Telegram style)", fontWeight = FontWeight.Medium) },
                leadingContent = {
                    Icon(
                        imageVector = if (message.isPinned) Icons.Filled.PushPin else Icons.Outlined.PushPin,
                        contentDescription = null,
                        tint = if (message.isPinned) RoseGoldPrimary else MaterialTheme.colorScheme.onSurface
                    )
                },
                modifier = Modifier
                    .clickable {
                        onPin()
                        onDismiss()
                    }
                    .testTag("action_pin")
            )

            ListItem(
                headlineContent = { Text("Search in Chat", fontWeight = FontWeight.Medium) },
                leadingContent = { Icon(Icons.Default.Search, contentDescription = null) },
                modifier = Modifier
                    .clickable {
                        onSearch()
                        onDismiss()
                    }
                    .testTag("action_search")
            )

            if (isFromMe && !message.isDeleted) {
                ListItem(
                    headlineContent = { Text("Edit Message", fontWeight = FontWeight.Medium) },
                    leadingContent = { Icon(Icons.Default.Edit, contentDescription = null) },
                    modifier = Modifier
                        .clickable {
                            onEdit()
                            onDismiss()
                        }
                        .testTag("action_edit")
                )
            }

            ListItem(
                headlineContent = { Text("Delete Message", color = HeartRed, fontWeight = FontWeight.Medium) },
                leadingContent = { Icon(Icons.Default.Delete, contentDescription = null, tint = HeartRed) },
                modifier = Modifier
                    .clickable {
                        onDelete()
                        onDismiss()
                    }
                    .testTag("action_delete")
            )

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

package com.example.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.Message
import com.example.ui.theme.HeartRed

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
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp)
                .navigationBarsPadding()
        ) {
            // Emoji reaction row
            val reactionEmojis = listOf("❤️", "🥰", "😘", "🔥", "🥺", "👍", "🌹")
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceAround
            ) {
                reactionEmojis.forEach { emoji ->
                    Box(
                        modifier = Modifier
                            .size(46.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .clickable {
                                onReaction(emoji)
                                onDismiss()
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(text = emoji, fontSize = 24.sp)
                    }
                }
            }

            HorizontalDivider(
                modifier = Modifier.padding(vertical = 8.dp),
                color = MaterialTheme.colorScheme.outlineVariant
            )

            // Action Items
            ListItem(
                headlineContent = { Text("Reply") },
                leadingContent = { Icon(Icons.Default.Reply, contentDescription = null) },
                modifier = Modifier
                    .clickable {
                        onReply()
                        onDismiss()
                    }
                    .testTag("action_reply")
            )

            ListItem(
                headlineContent = { Text("Copy Text") },
                leadingContent = { Icon(Icons.Default.ContentCopy, contentDescription = null) },
                modifier = Modifier
                    .clickable {
                        onCopy()
                        onDismiss()
                    }
                    .testTag("action_copy")
            )

            ListItem(
                headlineContent = { Text(if (message.isStarred) "Remove from Starred" else "Star Message") },
                leadingContent = {
                    Icon(
                        imageVector = if (message.isStarred) Icons.Filled.Star else Icons.Outlined.StarOutline,
                        contentDescription = null,
                        tint = if (message.isStarred) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                    )
                },
                modifier = Modifier
                    .clickable {
                        onStar()
                        onDismiss()
                    }
                    .testTag("action_star")
            )

            if (isFromMe && !message.isDeleted) {
                ListItem(
                    headlineContent = { Text("Edit Message") },
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
                headlineContent = { Text("Delete Message", color = HeartRed) },
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

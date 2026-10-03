package com.example.ui.disguise

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.notes.ChecklistItem
import com.example.data.local.notes.NoteEntity
import com.example.ui.theme.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun NoteCard(
    note: NoteEntity,
    onClick: () -> Unit,
    onTogglePin: () -> Unit,
    onToggleChecklistItem: (itemId: String, isDone: Boolean) -> Unit,
    onDuplicate: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit,
    isSelectionMode: Boolean = false,
    isSelected: Boolean = false,
    onToggleSelect: () -> Unit = {},
    onLongClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var showMenu by remember { mutableStateOf(false) }
    val checklist = remember(note.checklistJson) { note.getChecklist() }
    val isDark = isAppInDark()

    val cardColor = remember(isDark) {
        if (isDark) {
            Color(0xFF0F172A)
        } else {
            Color.White
        }
    }

    val totalItems = checklist.size
    val doneItems = checklist.count { it.isDone }
    val progress = if (totalItems > 0) doneItems.toFloat() / totalItems.toFloat() else 0f

    val formattedDate = remember(note.updatedAt) {
        val sdf = SimpleDateFormat("MMM d, h:mm a", Locale.getDefault())
        sdf.format(Date(note.updatedAt))
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .combinedClickable(
                onClick = {
                    if (isSelectionMode) {
                        onToggleSelect()
                    } else {
                        onClick()
                    }
                },
                onLongClick = {
                    onLongClick()
                }
            )
            .testTag("note_card_${note.id}"),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = cardColor),
        border = BorderStroke(
            width = if (isSelected) 2.5.dp else if (note.isPinned) 1.5.dp else 1.dp,
            color = if (isSelected) RoseGoldPrimary else if (note.isPinned) RoseGoldPrimary.copy(alpha = 0.5f) else if (isDark) Color(0xFF1E293B) else MaterialTheme.colorScheme.outline
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isSelected) 6.dp else if (note.isPinned) 3.dp else 1.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            // Header Row: Category Badge & Selection Checkbox + Pin & More Menu
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Category Tag
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = if (isDark) Color(0xFF0F172A) else MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                    border = BorderStroke(0.5.dp, if (isDark) Color(0xFF1E293B) else MaterialTheme.colorScheme.outline)
                ) {
                    Text(
                        text = note.category,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (isDark) Color(0xFF93C5FD) else RoseGoldPrimary,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (isSelectionMode) {
                        // Multi-selection checkbox
                        Box(
                            modifier = Modifier
                                .size(26.dp)
                                .clip(CircleShape)
                                .background(if (isSelected) RoseGoldPrimary else Color.White)
                                .border(
                                    width = if (isSelected) 0.dp else 2.dp,
                                    color = if (isSelected) RoseGoldPrimary else MaterialTheme.colorScheme.outline,
                                    shape = CircleShape
                                )
                                .clickable { onToggleSelect() },
                            contentAlignment = Alignment.Center
                        ) {
                            if (isSelected) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = "Selected",
                                    tint = Color.White,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    } else {
                        if (note.isPinned) {
                            Surface(
                                shape = CircleShape,
                                color = RoseGoldPrimary.copy(alpha = 0.15f),
                                modifier = Modifier.size(24.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Filled.PushPin,
                                        contentDescription = "Pinned Note",
                                        tint = RoseGoldPrimary,
                                        modifier = Modifier.size(13.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(4.dp))
                        }

                        Box {
                            IconButton(
                                onClick = { showMenu = true },
                                modifier = Modifier
                                    .size(36.dp)
                                    .minimumInteractiveComponentSize()
                            ) {
                                Icon(
                                    imageVector = Icons.Default.MoreVert,
                                    contentDescription = "Note actions",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(20.dp)
                                )
                            }

                            DropdownMenu(
                                expanded = showMenu,
                                onDismissRequest = { showMenu = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text(if (note.isPinned) "Unpin Note" else "Pin to Top") },
                                    onClick = {
                                        showMenu = false
                                        onTogglePin()
                                    },
                                    leadingIcon = {
                                        Icon(
                                            if (note.isPinned) Icons.Outlined.PushPin else Icons.Filled.PushPin,
                                            contentDescription = null
                                        )
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Select Note") },
                                    onClick = {
                                        showMenu = false
                                        onLongClick()
                                    },
                                    leadingIcon = {
                                        Icon(Icons.Default.CheckCircleOutline, contentDescription = null)
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Duplicate") },
                                    onClick = {
                                        showMenu = false
                                        onDuplicate()
                                    },
                                    leadingIcon = {
                                        Icon(Icons.Default.ContentCopy, contentDescription = null)
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Share Note") },
                                    onClick = {
                                        showMenu = false
                                        onShare()
                                    },
                                    leadingIcon = {
                                        Icon(Icons.Default.Share, contentDescription = null)
                                    }
                                )
                                HorizontalDivider()
                                DropdownMenuItem(
                                    text = { Text("Delete", color = MaterialTheme.colorScheme.error) },
                                    onClick = {
                                        showMenu = false
                                        onDelete()
                                    },
                                    leadingIcon = {
                                        Icon(
                                            Icons.Default.Delete,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.error
                                        )
                                    }
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Title
            if (note.title.isNotBlank()) {
                Text(
                    text = note.title,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isDark) Color(0xFFF8FAFC) else Color(0xFF0F172A),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // Content preview
            if (note.content.isNotBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = note.content,
                    fontSize = 13.sp,
                    color = if (isDark) Color(0xFFCBD5E1) else Color(0xFF334155),
                    maxLines = if (checklist.isNotEmpty()) 2 else 4,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = 18.sp
                )
            }

            // Interactive Checklist Preview (if any)
            if (checklist.isNotEmpty()) {
                Spacer(modifier = Modifier.height(10.dp))

                // Checklist Progress Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "$doneItems of $totalItems completed",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = if (isDark) Color(0xFF94A3B8) else Color(0xFF64748B)
                    )
                    Text(
                        text = "${(progress * 100).toInt()}%",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = RoseGoldPrimary
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .clip(CircleShape),
                    color = RoseGoldPrimary,
                    trackColor = if (isDark) Color(0xFF1E293B) else Color.White.copy(alpha = 0.6f)
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Show top 3-4 checklist items
                checklist.take(4).forEach { item ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .clickable(enabled = !isSelectionMode) {
                                onToggleChecklistItem(item.id, !item.isDone)
                            },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(18.dp)
                                .clip(RoundedCornerShape(5.dp))
                                .background(
                                    if (item.isDone) RoseGoldPrimary else if (isDark) Color(0xFF0F172A) else Color.White
                                )
                                .border(
                                    width = 1.dp,
                                    color = if (item.isDone) RoseGoldPrimary else if (isDark) Color(0xFF334155) else MaterialTheme.colorScheme.outline,
                                    shape = RoundedCornerShape(5.dp)
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            if (item.isDone) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = "Done",
                                    tint = Color.White,
                                    modifier = Modifier.size(12.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        Text(
                            text = item.text,
                            fontSize = 13.sp,
                            color = if (item.isDone) {
                                if (isDark) Color(0xFF64748B) else Color(0xFF94A3B8)
                            } else {
                                if (isDark) Color(0xFFF1F5F9) else Color(0xFF0F172A)
                            },
                            textDecoration = if (item.isDone) TextDecoration.LineThrough else null,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                if (checklist.size > 4) {
                    Text(
                        text = "+ ${checklist.size - 4} more items",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = RoseGoldPrimary,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }

            // Reminder Badge Chip
            if (note.reminderTime != null) {
                val isPast = note.isReminderPast()
                val reminderFormatted = remember(note.reminderTime) {
                    val sdf = SimpleDateFormat("MMM d • h:mm a", Locale.getDefault())
                    sdf.format(Date(note.reminderTime))
                }
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = if (isPast) {
                        if (isDark) Color(0xFF1E293B) else Color(0xFFF1F5F9)
                    } else {
                        if (isDark) RoseGoldPrimary.copy(alpha = 0.2f) else RoseGoldPrimary.copy(alpha = 0.12f)
                    },
                    modifier = Modifier.padding(top = 6.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (isPast) Icons.Default.NotificationsOff else Icons.Filled.NotificationsActive,
                            contentDescription = null,
                            tint = if (isPast) {
                                if (isDark) Color(0xFF94A3B8) else Color(0xFF64748B)
                            } else {
                                RoseGoldPrimary
                            },
                            modifier = Modifier.size(12.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = reminderFormatted + if (isPast) " (Passed)" else "",
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.Medium,
                            color = if (isPast) {
                                if (isDark) Color(0xFF94A3B8) else Color(0xFF64748B)
                            } else {
                                RoseGoldPrimary
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Footer: Timestamp
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = formattedDate,
                    fontSize = 11.sp,
                    color = if (isDark) Color(0xFF94A3B8) else Color(0xFF64748B)
                )

                if (checklist.isNotEmpty()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Checklist,
                            contentDescription = null,
                            tint = if (isDark) Color(0xFF94A3B8) else Color(0xFF64748B),
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(3.dp))
                        Text(
                            text = "$totalItems",
                            fontSize = 11.sp,
                            color = if (isDark) Color(0xFF94A3B8) else Color(0xFF64748B)
                        )
                    }
                }
            }
        }
    }
}




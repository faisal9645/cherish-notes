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
import androidx.compose.ui.graphics.compositeOver
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

data class NoteCardThemeColors(
    val containerColor: Color,
    val borderColor: Color,
    val accentPrimary: Color,
    val accentContainer: Color,
    val categoryBadgeText: Color,
    val titleColor: Color,
    val contentColor: Color,
    val secondaryTextColor: Color
)

/** [amoledBlack]: the Black theme, where every card sits on near-black instead of a tinted navy. */
fun resolveNoteCardColors(colorHex: String?, isDark: Boolean, amoledBlack: Boolean = false): NoteCardThemeColors {
    val colors = noteCardColors(colorHex, isDark)
    if (!(isDark && amoledBlack)) return colors
    // Black theme: pure AMOLED black card, every blue border/accent/badge turned neutral grey
    return NoteCardThemeColors(
        containerColor = Color(0xFF000000),
        borderColor = colors.borderColor.withoutBlue(),
        accentPrimary = colors.accentPrimary.withoutBlue(),
        accentContainer = colors.accentContainer.withoutBlue(),
        categoryBadgeText = colors.categoryBadgeText.withoutBlue(),
        titleColor = colors.titleColor.withoutBlue(),
        contentColor = colors.contentColor.withoutBlue(),
        secondaryTextColor = colors.secondaryTextColor.withoutBlue()
    )
}

private fun noteCardColors(colorHex: String?, isDark: Boolean): NoteCardThemeColors {
    val cleanHex = (colorHex ?: "#FFF9C4").trim().uppercase()
    return if (isDark) {
        when (cleanHex) {
            // ColorNote Yellow (Signature sticky note)
            "#FFF9C4", "#FFF59D", "#FFF176", "#FEF08A" -> NoteCardThemeColors(
                containerColor = Color(0xFF231E0C),
                borderColor = Color(0xFFF59E0B).copy(alpha = 0.5f),
                accentPrimary = Color(0xFFFBBF24),
                accentContainer = Color(0xFF78350F).copy(alpha = 0.45f),
                categoryBadgeText = Color(0xFFFDE68A),
                titleColor = Color(0xFFF8FAFC),
                contentColor = Color(0xFFCBD5E1),
                secondaryTextColor = Color(0xFF94A3B8)
            )
            // ColorNote Red / Coral
            "#FFCDD2", "#EF5350", "#F87171" -> NoteCardThemeColors(
                containerColor = Color(0xFF261013),
                borderColor = Color(0xFFEF4444).copy(alpha = 0.5f),
                accentPrimary = Color(0xFFF87171),
                accentContainer = Color(0xFF7F1D1D).copy(alpha = 0.45f),
                categoryBadgeText = Color(0xFFFECACA),
                titleColor = Color(0xFFF8FAFC),
                contentColor = Color(0xFFCBD5E1),
                secondaryTextColor = Color(0xFF94A3B8)
            )
            // ColorNote Orange / Amber
            "#FFE0B2", "#FFA726", "#FB923C" -> NoteCardThemeColors(
                containerColor = Color(0xFF28150B),
                borderColor = Color(0xFFF97316).copy(alpha = 0.5f),
                accentPrimary = Color(0xFFFB923C),
                accentContainer = Color(0xFF7C2D12).copy(alpha = 0.45f),
                categoryBadgeText = Color(0xFFFED7AA),
                titleColor = Color(0xFFF8FAFC),
                contentColor = Color(0xFFCBD5E1),
                secondaryTextColor = Color(0xFF94A3B8)
            )
            // ColorNote Green / Mint
            "#C8E6C9", "#E6F4EA", "#66BB6A", "#34D399" -> NoteCardThemeColors(
                containerColor = Color(0xFF0F261D),
                borderColor = Color(0xFF10B981).copy(alpha = 0.5f),
                accentPrimary = Color(0xFF34D399),
                accentContainer = Color(0xFF064E3B).copy(alpha = 0.45f),
                categoryBadgeText = Color(0xFFA7F3D0),
                titleColor = Color(0xFFF8FAFC),
                contentColor = Color(0xFFCBD5E1),
                secondaryTextColor = Color(0xFF94A3B8)
            )
            // ColorNote Blue / Sky
            "#BBDEFB", "#EFF5FF", "#EBF4FF", "#EBF6FD", "#42A5F5" -> NoteCardThemeColors(
                containerColor = Color(0xFF102338),
                borderColor = Color(0xFF0284C7).copy(alpha = 0.5f),
                accentPrimary = Color(0xFF38BDF8),
                accentContainer = Color(0xFF0C4A6E).copy(alpha = 0.45f),
                categoryBadgeText = Color(0xFFBAE6FD),
                titleColor = Color(0xFFF8FAFC),
                contentColor = Color(0xFFCBD5E1),
                secondaryTextColor = Color(0xFF94A3B8)
            )
            // ColorNote Purple / Lavender
            "#E1BEE7", "#F4EBF7", "#ECEBFF", "#F1F0FF", "#AB47BC" -> NoteCardThemeColors(
                containerColor = Color(0xFF221634),
                borderColor = Color(0xFFA855F7).copy(alpha = 0.5f),
                accentPrimary = Color(0xFFC084FC),
                accentContainer = Color(0xFF581C87).copy(alpha = 0.45f),
                categoryBadgeText = Color(0xFFE9D5FF),
                titleColor = Color(0xFFF8FAFC),
                contentColor = Color(0xFFCBD5E1),
                secondaryTextColor = Color(0xFF94A3B8)
            )
            // ColorNote Pink
            "#F8BBD0", "#EC407A", "#F472B6" -> NoteCardThemeColors(
                containerColor = Color(0xFF2B101D),
                borderColor = Color(0xFFEC4899).copy(alpha = 0.5f),
                accentPrimary = Color(0xFFF472B6),
                accentContainer = Color(0xFF831843).copy(alpha = 0.45f),
                categoryBadgeText = Color(0xFFFBCFE8),
                titleColor = Color(0xFFF8FAFC),
                contentColor = Color(0xFFCBD5E1),
                secondaryTextColor = Color(0xFF94A3B8)
            )
            // ColorNote Teal / Aqua
            "#B2DFDB", "#26A69A", "#2DD4BF" -> NoteCardThemeColors(
                containerColor = Color(0xFF0B2321),
                borderColor = Color(0xFF14B8A6).copy(alpha = 0.5f),
                accentPrimary = Color(0xFF2DD4BF),
                accentContainer = Color(0xFF134E4A).copy(alpha = 0.45f),
                categoryBadgeText = Color(0xFF99F6E4),
                titleColor = Color(0xFFF8FAFC),
                contentColor = Color(0xFFCBD5E1),
                secondaryTextColor = Color(0xFF94A3B8)
            )
            // ColorNote Brown / Sand
            "#D7CCC8", "#8D6E63", "#A1887F" -> NoteCardThemeColors(
                containerColor = Color(0xFF241C18),
                borderColor = Color(0xFF8D6E63).copy(alpha = 0.5f),
                accentPrimary = Color(0xFFA1887F),
                accentContainer = Color(0xFF3E2723).copy(alpha = 0.45f),
                categoryBadgeText = Color(0xFFD7CCC8),
                titleColor = Color(0xFFF8FAFC),
                contentColor = Color(0xFFCBD5E1),
                secondaryTextColor = Color(0xFF94A3B8)
            )
            // ColorNote Slate / Gray
            "#CFD8DC", "#F1F5F9", "#F4F6FB" -> NoteCardThemeColors(
                containerColor = Color(0xFF151C28),
                borderColor = Color(0xFF64748B).copy(alpha = 0.5f),
                accentPrimary = Color(0xFF94A3B8),
                accentContainer = Color(0xFF334155).copy(alpha = 0.45f),
                categoryBadgeText = Color(0xFFCBD5E1),
                titleColor = Color(0xFFF8FAFC),
                contentColor = Color(0xFFCBD5E1),
                secondaryTextColor = Color(0xFF94A3B8)
            )
            // ColorNote Charcoal / Dark
            "#374151", "#263238", "#1E293B" -> NoteCardThemeColors(
                containerColor = Color(0xFF18181B),
                borderColor = Color(0xFF52525B).copy(alpha = 0.6f),
                accentPrimary = Color(0xFFA1A1AA),
                accentContainer = Color(0xFF27272A),
                categoryBadgeText = Color(0xFFE4E4E7),
                titleColor = Color(0xFFF8FAFC),
                contentColor = Color(0xFFCBD5E1),
                secondaryTextColor = Color(0xFF94A3B8)
            )
            // ColorNote Clean / White (pure AMOLED black in dark mode)
            "#F8FAFC", "#FFFFFF" -> NoteCardThemeColors(
                containerColor = Color(0xFF000000),
                borderColor = Color(0xFF334155).copy(alpha = 0.85f),
                accentPrimary = DayBluePrimary,
                accentContainer = DayBluePrimary.copy(alpha = 0.2f),
                categoryBadgeText = DayBlueSecondary,
                titleColor = Color(0xFFF8FAFC),
                contentColor = Color(0xFFCBD5E1),
                secondaryTextColor = Color(0xFF94A3B8)
            )
            else -> { // Dynamic Fallback for any other hex in dark mode
                val parsed = try { Color(android.graphics.Color.parseColor(cleanHex)) } catch (_: Exception) { DayBluePrimary }
                val accent = if (cleanHex == "#EFF5FF" || cleanHex == "#3B82F6") DayBluePrimary else parsed
                NoteCardThemeColors(
                    containerColor = accent.copy(alpha = 0.16f).compositeOver(Color(0xFF0B1324)),
                    borderColor = accent.copy(alpha = 0.35f),
                    accentPrimary = accent,
                    accentContainer = accent.copy(alpha = 0.25f),
                    categoryBadgeText = accent,
                    titleColor = Color(0xFFF8FAFC),
                    contentColor = Color(0xFFCBD5E1),
                    secondaryTextColor = Color(0xFF94A3B8)
                )
            }
        }
    } else {
        // DAY MODE (LIGHT MODE)
        when (cleanHex) {
            // ColorNote Yellow (Signature sticky note)
            "#FFF9C4", "#FFF59D", "#FFF176", "#FEF08A" -> NoteCardThemeColors(
                containerColor = Color(0xFFFFFDE7),
                borderColor = Color(0xFFFFF59D),
                accentPrimary = Color(0xFFFBC02D),
                accentContainer = Color(0xFFFFF9C4),
                categoryBadgeText = Color(0xFF854D0E),
                titleColor = Color(0xFF0F172A),
                contentColor = Color(0xFF334155),
                secondaryTextColor = Color(0xFF713F12)
            )
            // ColorNote Red / Coral
            "#FFCDD2", "#EF5350", "#F87171" -> NoteCardThemeColors(
                containerColor = Color(0xFFFFEBEE),
                borderColor = Color(0xFFFFCDD2),
                accentPrimary = Color(0xFFE53935),
                accentContainer = Color(0xFFFFCDD2),
                categoryBadgeText = Color(0xFF991B1B),
                titleColor = Color(0xFF0F172A),
                contentColor = Color(0xFF334155),
                secondaryTextColor = Color(0xFF991B1B)
            )
            // ColorNote Orange / Amber
            "#FFE0B2", "#FFA726", "#FB923C" -> NoteCardThemeColors(
                containerColor = Color(0xFFFFF3E0),
                borderColor = Color(0xFFFFE0B2),
                accentPrimary = Color(0xFFFB8C00),
                accentContainer = Color(0xFFFFE0B2),
                categoryBadgeText = Color(0xFF9A3412),
                titleColor = Color(0xFF0F172A),
                contentColor = Color(0xFF334155),
                secondaryTextColor = Color(0xFF9A3412)
            )
            // ColorNote Green / Mint
            "#C8E6C9", "#E6F4EA", "#66BB6A", "#34D399" -> NoteCardThemeColors(
                containerColor = Color(0xFFE8F5E9),
                borderColor = Color(0xFFC8E6C9),
                accentPrimary = Color(0xFF43A047),
                accentContainer = Color(0xFFC8E6C9),
                categoryBadgeText = Color(0xFF166534),
                titleColor = Color(0xFF0F172A),
                contentColor = Color(0xFF334155),
                secondaryTextColor = Color(0xFF166534)
            )
            // ColorNote Blue / Sky
            "#BBDEFB", "#EFF5FF", "#EBF4FF", "#EBF6FD", "#42A5F5" -> NoteCardThemeColors(
                containerColor = Color(0xFFE3F2FD),
                borderColor = Color(0xFFBBDEFB),
                accentPrimary = Color(0xFF1E88E5),
                accentContainer = Color(0xFFBBDEFB),
                categoryBadgeText = Color(0xFF075985),
                titleColor = Color(0xFF0F172A),
                contentColor = Color(0xFF334155),
                secondaryTextColor = Color(0xFF0369A1)
            )
            // ColorNote Purple / Lavender
            "#E1BEE7", "#F4EBF7", "#ECEBFF", "#F1F0FF", "#AB47BC" -> NoteCardThemeColors(
                containerColor = Color(0xFFF3E5F5),
                borderColor = Color(0xFFE1BEE7),
                accentPrimary = Color(0xFF8E24AA),
                accentContainer = Color(0xFFE1BEE7),
                categoryBadgeText = Color(0xFF6B21A8),
                titleColor = Color(0xFF0F172A),
                contentColor = Color(0xFF334155),
                secondaryTextColor = Color(0xFF6B21A8)
            )
            // ColorNote Pink
            "#F8BBD0", "#EC407A", "#F472B6" -> NoteCardThemeColors(
                containerColor = Color(0xFFFCE4EC),
                borderColor = Color(0xFFF8BBD0),
                accentPrimary = Color(0xFFD81B60),
                accentContainer = Color(0xFFF8BBD0),
                categoryBadgeText = Color(0xFF9D174D),
                titleColor = Color(0xFF0F172A),
                contentColor = Color(0xFF334155),
                secondaryTextColor = Color(0xFF9D174D)
            )
            // ColorNote Teal / Aqua
            "#B2DFDB", "#26A69A", "#2DD4BF" -> NoteCardThemeColors(
                containerColor = Color(0xFFE0F2F1),
                borderColor = Color(0xFFB2DFDB),
                accentPrimary = Color(0xFF00897B),
                accentContainer = Color(0xFFB2DFDB),
                categoryBadgeText = Color(0xFF115E59),
                titleColor = Color(0xFF0F172A),
                contentColor = Color(0xFF334155),
                secondaryTextColor = Color(0xFF115E59)
            )
            // ColorNote Brown / Sand
            "#D7CCC8", "#8D6E63", "#A1887F" -> NoteCardThemeColors(
                containerColor = Color(0xFFEFEBE9),
                borderColor = Color(0xFFD7CCC8),
                accentPrimary = Color(0xFF6D4C41),
                accentContainer = Color(0xFFD7CCC8),
                categoryBadgeText = Color(0xFF4E342E),
                titleColor = Color(0xFF0F172A),
                contentColor = Color(0xFF334155),
                secondaryTextColor = Color(0xFF4E342E)
            )
            // ColorNote Slate / Gray
            "#CFD8DC", "#F1F5F9", "#F4F6FB" -> NoteCardThemeColors(
                containerColor = Color(0xFFECEFF1),
                borderColor = Color(0xFFCFD8DC),
                accentPrimary = Color(0xFF546E7A),
                accentContainer = Color(0xFFCFD8DC),
                categoryBadgeText = Color(0xFF37474F),
                titleColor = Color(0xFF0F172A),
                contentColor = Color(0xFF334155),
                secondaryTextColor = Color(0xFF455A64)
            )
            // ColorNote Charcoal / Dark
            "#374151", "#263238", "#1E293B" -> NoteCardThemeColors(
                containerColor = Color(0xFFF1F5F9),
                borderColor = Color(0xFF94A3B8),
                accentPrimary = Color(0xFF334155),
                accentContainer = Color(0xFFE2E8F0),
                categoryBadgeText = Color(0xFF1E293B),
                titleColor = Color(0xFF0F172A),
                contentColor = Color(0xFF334155),
                secondaryTextColor = Color(0xFF475569)
            )
            // ColorNote Clean / White
            "#F8FAFC", "#FFFFFF" -> NoteCardThemeColors(
                containerColor = Color.White,
                borderColor = Color(0xFFE2E8F0),
                accentPrimary = DayBluePrimary,
                accentContainer = Color(0xFFEFF6FF),
                categoryBadgeText = DayBluePrimary,
                titleColor = Color(0xFF0F172A),
                contentColor = Color(0xFF334155),
                secondaryTextColor = Color(0xFF64748B)
            )
            else -> { // Dynamic Fallback for any other hex in day mode
                val parsed = try { Color(android.graphics.Color.parseColor(cleanHex)) } catch (_: Exception) { DayBluePrimary }
                NoteCardThemeColors(
                    containerColor = parsed,
                    borderColor = Color(0xFF000000).copy(alpha = 0.12f),
                    accentPrimary = DayBluePrimary,
                    accentContainer = parsed.copy(alpha = 0.5f),
                    categoryBadgeText = DayBluePrimary,
                    titleColor = Color(0xFF0F172A),
                    contentColor = Color(0xFF334155),
                    secondaryTextColor = Color(0xFF64748B)
                )
            }
        }
    }
}

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
    val amoledBlack = com.example.ui.theme.LocalAmoledBlack.current
    val themeColors = remember(note.colorHex, isDark, amoledBlack) {
        resolveNoteCardColors(note.colorHex, isDark, amoledBlack)
    }

    val totalItems = checklist.size
    val doneItems = checklist.count { it.isDone }
    val progress = if (totalItems > 0) doneItems.toFloat() / totalItems.toFloat() else 0f

    val formattedDate = remember(note.updatedAt) {
        val datePart = SimpleDateFormat("MMM d", Locale.US).format(Date(note.updatedAt))
        val timePart = com.example.util.ChatTimeFormatter.formatMessageTime(note.updatedAt)
        "$datePart, $timePart"
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
        colors = CardDefaults.cardColors(containerColor = themeColors.containerColor),
        border = BorderStroke(
            width = if (isSelected) 2.5.dp else if (note.isPinned) 1.5.dp else 1.dp,
            color = if (isSelected) themeColors.accentPrimary else if (note.isPinned) themeColors.accentPrimary.copy(alpha = 0.6f) else themeColors.borderColor
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isSelected) 6.dp else if (note.isPinned) 3.dp else 1.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth()
        ) {
            // ColorNote top accent color strip
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .background(themeColors.accentPrimary)
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp)
            ) {
                // Header Row: Category Badge & Selection Checkbox + Pin & More Menu
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 36.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Category Tag
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = themeColors.accentContainer,
                    border = BorderStroke(0.5.dp, themeColors.borderColor)
                ) {
                    Text(
                        text = note.category,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = themeColors.categoryBadgeText,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }

                Row(
                    modifier = Modifier.height(36.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (isSelectionMode) {
                        // Multi-selection checkbox wrapped in matching 36dp container
                        Box(
                            modifier = Modifier.size(36.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(26.dp)
                                    .clip(CircleShape)
                                    .background(if (isSelected) themeColors.accentPrimary else if (isDark) darkTone(Color(0xFF0F172A)) else Color.White)
                                    .border(
                                        width = if (isSelected) 0.dp else 2.dp,
                                        color = if (isSelected) themeColors.accentPrimary else themeColors.borderColor,
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
                        }
                    } else {
                        if (note.isPinned) {
                            Surface(
                                shape = CircleShape,
                                color = themeColors.accentContainer,
                                modifier = Modifier.size(24.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Filled.PushPin,
                                        contentDescription = "Pinned Note",
                                        tint = themeColors.accentPrimary,
                                        modifier = Modifier.size(13.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(4.dp))
                        }

                        Box {
                            IconButton(
                                onClick = { showMenu = true },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.MoreVert,
                                    contentDescription = "Note actions",
                                    tint = themeColors.secondaryTextColor,
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
                    color = themeColors.titleColor,
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
                    color = themeColors.contentColor,
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
                        color = themeColors.secondaryTextColor
                    )
                    Text(
                        text = "${(progress * 100).toInt()}%",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = themeColors.accentPrimary
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .clip(CircleShape),
                    color = themeColors.accentPrimary,
                    trackColor = themeColors.accentContainer.copy(alpha = 0.5f)
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
                                    if (item.isDone) themeColors.accentPrimary else if (isDark) darkTone(Color(0xFF0F172A)) else Color.White
                                )
                                .border(
                                    width = 1.dp,
                                    color = if (item.isDone) themeColors.accentPrimary else themeColors.borderColor,
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
                                themeColors.secondaryTextColor
                            } else {
                                themeColors.titleColor
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
                        color = themeColors.accentPrimary,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }

            // Reminder Badge Chip
            if (note.reminderTime != null) {
                val isPast = note.isReminderPast()
                val reminderFormatted = remember(note.reminderTime) {
                    val datePart = SimpleDateFormat("MMM d", Locale.US).format(Date(note.reminderTime))
                    val timePart = com.example.util.ChatTimeFormatter.formatMessageTime(note.reminderTime)
                    "$datePart • $timePart"
                }
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = if (isPast) {
                        if (isDark) darkTone(Color(0xFF1E293B)) else Color(0xFFF1F5F9)
                    } else {
                        themeColors.accentContainer
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
                                themeColors.secondaryTextColor
                            } else {
                                themeColors.accentPrimary
                            },
                            modifier = Modifier.size(12.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = reminderFormatted + if (isPast) " (Passed)" else "",
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.Medium,
                            color = if (isPast) {
                                themeColors.secondaryTextColor
                            } else {
                                themeColors.accentPrimary
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
                    color = themeColors.secondaryTextColor
                )

                if (checklist.isNotEmpty()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Checklist,
                            contentDescription = null,
                            tint = themeColors.secondaryTextColor,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(3.dp))
                        Text(
                            text = "$totalItems",
                            fontSize = 11.sp,
                            color = themeColors.secondaryTextColor
                        )
                    }
                }
            }
        }
    }
}
}




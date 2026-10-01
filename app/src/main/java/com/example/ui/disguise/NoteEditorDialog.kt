package com.example.ui.disguise

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.notes.ChecklistItem
import com.example.data.local.notes.NoteEntity
import com.example.ui.theme.*
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NoteEditorScreen(
    initialNote: NoteEntity? = null,
    onDismiss: () -> Unit,
    onSave: (
        id: String?,
        title: String,
        content: String,
        category: String,
        colorHex: String,
        checklist: List<ChecklistItem>,
        isPinned: Boolean
    ) -> Unit,
    onDelete: ((String) -> Unit)? = null,
    onShare: ((NoteEntity) -> Unit)? = null,
    onToast: (String) -> Unit
) {
    BackHandler(onBack = onDismiss)
    val context = LocalContext.current

    var title by remember { mutableStateOf(initialNote?.title ?: "") }
    var content by remember { mutableStateOf(initialNote?.content ?: "") }
    var category by remember { mutableStateOf(initialNote?.category ?: "Personal") }
    var colorHex by remember { mutableStateOf(initialNote?.colorHex ?: "#EFF5FF") }
    var isPinned by remember { mutableStateOf(initialNote?.isPinned ?: false) }

    var isChecklistMode by remember {
        mutableStateOf(initialNote?.getChecklist()?.isNotEmpty() == true)
    }

    var checklistItems by remember {
        mutableStateOf(initialNote?.getChecklist() ?: emptyList())
    }
    var newChecklistInput by remember { mutableStateOf("") }

    val categories = listOf("Personal", "Work", "Lists", "Ideas", "Journal", "Urgent")
    val colorPalettes = listOf(
        "#EFF5FF" to "Ice Blue",
        "#F1F0FF" to "Periwinkle",
        "#EBF6FD" to "Sky Aqua",
        "#E8F7F0" to "Mint Sage",
        "#FFF5EB" to "Warm Peach",
        "#F4F6FB" to "Clean Slate"
    )

    val wordCount = remember(content) {
        if (content.isBlank()) 0 else content.trim().split("\\s+".toRegex()).size
    }
    val charCount = remember(content) { content.length }

    Scaffold(
        topBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface)
                    .statusBarsPadding()
            ) {
                // Top App Bar / Actions
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }

                    Text(
                        text = if (initialNote == null) "New Note" else "Edit Note",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // Pin Toggle
                        IconButton(onClick = { isPinned = !isPinned }) {
                            Icon(
                                imageVector = if (isPinned) Icons.Filled.PushPin else Icons.Outlined.PushPin,
                                contentDescription = if (isPinned) "Unpin" else "Pin",
                                tint = if (isPinned) RoseGoldPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        // Checklist Mode Toggle
                        IconButton(onClick = { isChecklistMode = !isChecklistMode }) {
                            Icon(
                                imageVector = if (isChecklistMode) Icons.Filled.Checklist else Icons.Outlined.Checklist,
                                contentDescription = "Toggle Checklist",
                                tint = if (isChecklistMode) RoseGoldPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        // Delete action (for existing note)
                        if (initialNote != null && onDelete != null) {
                            IconButton(onClick = {
                                onDelete(initialNote.id)
                                onDismiss()
                            }) {
                                Icon(
                                    Icons.Default.Delete,
                                    contentDescription = "Delete",
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, thickness = 1.dp)
            }
        },
        bottomBar = {
            Surface(
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 0.dp,
                shadowElevation = 16.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .imePadding()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surface)
                        .navigationBarsPadding()
                ) {
                    HorizontalDivider(color = Color(0xFFEEEEF0), thickness = 1.dp)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 18.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Cancel Button: Modern Soft Rounded Pill
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = Color(0xFFF2F4F8),
                            modifier = Modifier
                                .weight(1f)
                                .height(50.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .clickable { onDismiss() }
                        ) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier.fillMaxSize()
                            ) {
                                Text(
                                    text = "Cancel",
                                    color = Color(0xFF4A4E5A),
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 15.sp
                                )
                            }
                        }

                        // Save Note Button: Glowing Signature Electric Gradient Pill
                        Box(
                            modifier = Modifier
                                .weight(1.5f)
                                .height(50.dp)
                                .appGradientShadow(RoundedCornerShape(16.dp))
                                .clip(RoundedCornerShape(16.dp))
                                .background(appHorizontalGradient())
                                .clickable {
                                    onSave(
                                        initialNote?.id,
                                        title,
                                        content,
                                        category,
                                        colorHex,
                                        checklistItems,
                                        isPinned
                                    )
                                    onDismiss()
                                }
                                .testTag("note_editor_save_button"),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Save Note",
                                    color = MaterialTheme.colorScheme.surface,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp
                                )
                            }
                        }
                    }
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.surface
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 14.dp)
        ) {
            // Title Input
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                placeholder = {
                    Text(
                        "Title",
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                },
                textStyle = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 22.sp
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("note_editor_title_input"),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color.Transparent,
                    unfocusedBorderColor = Color.Transparent
                )
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Category Selector Chips
            Text(
                text = "Category",
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(6.dp))
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(categories) { cat ->
                    val isSelected = category == cat
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (isSelected) RoseGoldPrimary else MaterialTheme.colorScheme.surfaceVariant,
                        border = if (isSelected) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { category = cat }
                    ) {
                        Text(
                            text = cat,
                            color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 12.sp,
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Color Palette Selector
            Text(
                text = "Note Theme Color",
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                colorPalettes.forEach { (hex, label) ->
                    val isSelected = colorHex == hex
                    val parsed = try {
                        Color(android.graphics.Color.parseColor(hex))
                    } catch (_: Exception) {
                        Color(0xFFEFF5FF)
                    }
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(parsed)
                            .border(
                                width = if (isSelected) 2.5.dp else 1.dp,
                                color = if (isSelected) RoseGoldPrimary else MaterialTheme.colorScheme.outline,
                                shape = CircleShape
                            )
                            .clickable { colorHex = hex },
                        contentAlignment = Alignment.Center
                    ) {
                        if (isSelected) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = label,
                                tint = RoseGoldPrimary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, thickness = 0.5.dp)
            Spacer(modifier = Modifier.height(14.dp))

            // Checklist Section (if checklist mode is active)
            AnimatedVisibility(visible = isChecklistMode) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "Checklist Items",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    checklistItems.forEachIndexed { index, item ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = item.isDone,
                                onCheckedChange = { isChecked ->
                                    checklistItems = checklistItems.toMutableList().also {
                                        it[index] = item.copy(isDone = isChecked)
                                    }
                                },
                                colors = CheckboxDefaults.colors(checkedColor = RoseGoldPrimary)
                            )

                            Text(
                                text = item.text,
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.onBackground,
                                modifier = Modifier.weight(1f)
                            )

                            IconButton(
                                onClick = {
                                    checklistItems = checklistItems.toMutableList().also {
                                        it.removeAt(index)
                                    }
                                },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Remove item",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }

                    // Add New Checklist Item Input
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = newChecklistInput,
                            onValueChange = { newChecklistInput = it },
                            placeholder = { Text("Add checklist item...") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(onDone = {
                                if (newChecklistInput.isNotBlank()) {
                                    checklistItems = checklistItems + ChecklistItem(
                                        id = UUID.randomUUID().toString(),
                                        text = newChecklistInput.trim(),
                                        isDone = false
                                    )
                                    newChecklistInput = ""
                                }
                            }),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f)
                        )

                        Spacer(modifier = Modifier.width(8.dp))

                        IconButton(
                            onClick = {
                                if (newChecklistInput.isNotBlank()) {
                                    checklistItems = checklistItems + ChecklistItem(
                                        id = UUID.randomUUID().toString(),
                                        text = newChecklistInput.trim(),
                                        isDone = false
                                    )
                                    newChecklistInput = ""
                                }
                            },
                            modifier = Modifier
                                .size(48.dp)
                                .background(RoseGoldContainer, RoundedCornerShape(12.dp))
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = "Add",
                                tint = RoseGoldPrimary
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                }
            }

            // Content Field
            Text(
                text = "Note Content",
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(4.dp))
            OutlinedTextField(
                value = content,
                onValueChange = { content = it },
                placeholder = {
                    Text(
                        "Type your thoughts, ideas, or meeting notes here...",
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                },
                minLines = 8,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("note_editor_content_input"),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = RoseGoldPrimary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline
                )
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Word count & stats bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "$wordCount words • $charCount characters",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )

                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Copy to clipboard button
                    TextButton(
                        onClick = {
                            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            val clip = ClipData.newPlainText(title.ifBlank { "Note" }, "$title\n\n$content")
                            cm.setPrimaryClip(clip)
                            onToast("Copied to clipboard 📋")
                        }
                    ) {
                        Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Copy", fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

// Backward-compatible alias for NoteEditorDialog
@Composable
fun NoteEditorDialog(
    initialNote: NoteEntity? = null,
    onDismiss: () -> Unit,
    onSave: (
        id: String?,
        title: String,
        content: String,
        category: String,
        colorHex: String,
        checklist: List<ChecklistItem>,
        isPinned: Boolean
    ) -> Unit,
    onDelete: ((String) -> Unit)? = null,
    onShare: ((NoteEntity) -> Unit)? = null,
    onToast: (String) -> Unit
) {
    NoteEditorScreen(
        initialNote = initialNote,
        onDismiss = onDismiss,
        onSave = onSave,
        onDelete = onDelete,
        onShare = onShare,
        onToast = onToast
    )
}




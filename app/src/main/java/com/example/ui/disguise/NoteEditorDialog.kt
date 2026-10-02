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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
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
    val isDark = MaterialTheme.colorScheme.background == Color.Black ||
            MaterialTheme.colorScheme.background == Color(0xFF0B0F19) ||
            MaterialTheme.colorScheme.surface == Color.Black

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
    var showFormattingBar by remember { mutableStateOf(false) }

    val categories = remember { listOf("Personal", "Work", "Lists", "Ideas", "Journal", "Urgent") }
    val colorPalettes = remember {
        listOf(
            "#EFF5FF" to "Blue Tint",
            "#F1F5F9" to "Slate Tint",
            "#F8FAFC" to "Clean Tint",
            "#EBF4FF" to "Sky Tint",
            "#E6F4EA" to "Mint Tint",
            "#F4EBF7" to "Lavender Tint"
        )
    }

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
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }

                    Text(
                        text = if (initialNote == null) "New Note" else "Edit Note",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // Pin Toggle
                        IconButton(onClick = { isPinned = !isPinned }) {
                            Icon(
                                imageVector = if (isPinned) Icons.Filled.PushPin else Icons.Outlined.PushPin,
                                contentDescription = if (isPinned) "Unpin" else "Pin",
                                tint = if (isPinned) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        // Checklist Mode Toggle
                        IconButton(onClick = { isChecklistMode = !isChecklistMode }) {
                            Icon(
                                imageVector = if (isChecklistMode) Icons.Filled.Checklist else Icons.Outlined.Checklist,
                                contentDescription = "Toggle Checklist",
                                tint = if (isChecklistMode) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        // Category & Color formatting toggle
                        IconButton(onClick = { showFormattingBar = !showFormattingBar }) {
                            Icon(
                                imageVector = Icons.Outlined.Palette,
                                contentDescription = "Style note",
                                tint = if (showFormattingBar) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
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
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, thickness = 0.8.dp)
            }
        },
        bottomBar = {
            Surface(
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 0.dp,
                shadowElevation = 8.dp,
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
                    // Expandable Styling & Category Bar
                    AnimatedVisibility(visible = showFormattingBar) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                                .padding(horizontal = 16.dp, vertical = 10.dp)
                        ) {
                            // Category chips
                            Text(
                                text = "Category",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                items(categories, key = { it }) { cat ->
                                    val isSelected = category == cat
                                    Surface(
                                        shape = RoundedCornerShape(10.dp),
                                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
                                        border = if (isSelected) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(10.dp))
                                            .clickable { category = cat }
                                    ) {
                                        Text(
                                            text = cat,
                                            color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurface,
                                            fontSize = 11.sp,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            // Color chips
                            Text(
                                text = "Card Accent Tint",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
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
                                            .size(28.dp)
                                            .clip(CircleShape)
                                            .background(parsed)
                                            .border(
                                                width = if (isSelected) 2.dp else 1.dp,
                                                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                                                shape = CircleShape
                                            )
                                            .clickable { colorHex = hex },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (isSelected) {
                                            Icon(
                                                imageVector = Icons.Default.Check,
                                                contentDescription = label,
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(14.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant,
                        thickness = 0.8.dp
                    )

                    // Bottom action & stats bar
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Word Count / Stats pill
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier.padding(end = 8.dp)
                        ) {
                            Text(
                                text = "$wordCount words • $charCount chars • $category",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Copy to clipboard
                            IconButton(
                                onClick = {
                                    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    val clip = ClipData.newPlainText(title.ifBlank { "Note" }, "$title\n\n$content")
                                    cm.setPrimaryClip(clip)
                                    onToast("Copied note 📋")
                                },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(Icons.Default.ContentCopy, contentDescription = "Copy", modifier = Modifier.size(18.dp))
                            }

                            // Save Button: Modern Gradient Pill
                            Box(
                                modifier = Modifier
                                    .height(42.dp)
                                    .appGradientShadow(RoundedCornerShape(14.dp))
                                    .clip(RoundedCornerShape(14.dp))
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
                                    .testTag("note_editor_save_button")
                                    .padding(horizontal = 20.dp),
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
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "Save",
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.surface
    ) { paddingValues ->
        // Full-Height Expansive Note Content Textbox
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 20.dp, vertical = 8.dp)
        ) {
            // Note Title Input
            BasicTextField(
                value = title,
                onValueChange = { title = it },
                textStyle = TextStyle(
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                ),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
                    .testTag("note_editor_title_input"),
                decorationBox = { innerTextField ->
                    if (title.isEmpty()) {
                        Text(
                            text = "Title",
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                        )
                    }
                    innerTextField()
                }
            )

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f), thickness = 0.5.dp)
            Spacer(modifier = Modifier.height(10.dp))

            if (isChecklistMode) {
                // Checklist Mode Items
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false)
                        .verticalScroll(rememberScrollState())
                ) {
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
                                colors = CheckboxDefaults.colors(checkedColor = MaterialTheme.colorScheme.primary)
                            )

                            Text(
                                text = item.text,
                                fontSize = 15.sp,
                                color = MaterialTheme.colorScheme.onSurface,
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
                            modifier = Modifier.weight(1f),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = MaterialTheme.colorScheme.onSurface,
                                unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                unfocusedBorderColor = MaterialTheme.colorScheme.outline
                            )
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
                                .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(12.dp))
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = "Add",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
            
            // Note Body Text Box (Always visible)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(top = if (isChecklistMode) 16.dp else 0.dp)
            ) {
                BasicTextField(
                        value = content,
                        onValueChange = { content = it },
                        textStyle = TextStyle(
                            fontSize = 16.sp,
                            lineHeight = 24.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        ),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        modifier = Modifier
                            .fillMaxSize()
                            .testTag("note_editor_content_input"),
                        decorationBox = { innerTextField ->
                            if (content.isEmpty()) {
                                Text(
                                    text = "Start typing your note here...",
                                    fontSize = 16.sp,
                                    lineHeight = 24.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                )
                            }
                            innerTextField()
                        }
                    )
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

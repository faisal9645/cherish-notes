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
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
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
        isPinned: Boolean,
        reminderTime: Long?
    ) -> Unit,
    onDelete: ((String) -> Unit)? = null,
    onShare: ((NoteEntity) -> Unit)? = null,
    onToast: (String) -> Unit
) {
    BackHandler(onBack = onDismiss)
    val context = LocalContext.current
    val isDark = isAppInDark()

    var title by remember { mutableStateOf(initialNote?.title ?: "") }
    var content by remember { mutableStateOf(initialNote?.content ?: "") }
    var category by remember { mutableStateOf(initialNote?.category ?: "Personal") }
    var colorHex by remember { mutableStateOf(initialNote?.colorHex ?: "#EFF5FF") }
    var isPinned by remember { mutableStateOf(initialNote?.isPinned ?: false) }
    var reminderTime by remember { mutableStateOf(initialNote?.reminderTime) }
    var showReminderDialog by remember { mutableStateOf(false) }

    val editorTheme = remember(colorHex, isDark) {
        resolveNoteCardColors(colorHex, isDark)
    }

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
                    .background(editorTheme.containerColor)
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
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = editorTheme.titleColor
                        )
                    }

                    Text(
                        text = if (initialNote == null) "New Note" else "Edit Note",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = editorTheme.titleColor
                    )

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // Pin Toggle
                        IconButton(onClick = { isPinned = !isPinned }) {
                            Icon(
                                imageVector = if (isPinned) Icons.Filled.PushPin else Icons.Outlined.PushPin,
                                contentDescription = if (isPinned) "Unpin" else "Pin",
                                tint = if (isPinned) editorTheme.accentPrimary else editorTheme.secondaryTextColor
                            )
                        }

                        // Reminder Toggle / Picker
                        IconButton(onClick = { showReminderDialog = true }) {
                            Icon(
                                imageVector = if (reminderTime != null) Icons.Filled.NotificationsActive else Icons.Outlined.Notifications,
                                contentDescription = "Set Reminder",
                                tint = if (reminderTime != null) editorTheme.accentPrimary else editorTheme.secondaryTextColor
                            )
                        }

                        // Checklist Mode Toggle
                        IconButton(onClick = { isChecklistMode = !isChecklistMode }) {
                            Icon(
                                imageVector = if (isChecklistMode) Icons.Filled.Checklist else Icons.Outlined.Checklist,
                                contentDescription = "Toggle Checklist",
                                tint = if (isChecklistMode) editorTheme.accentPrimary else editorTheme.secondaryTextColor
                            )
                        }

                        // Category & Color formatting toggle
                        IconButton(onClick = { showFormattingBar = !showFormattingBar }) {
                            Icon(
                                imageVector = Icons.Outlined.Palette,
                                contentDescription = "Style note",
                                tint = if (showFormattingBar) editorTheme.accentPrimary else editorTheme.secondaryTextColor
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
                HorizontalDivider(color = editorTheme.borderColor, thickness = 0.8.dp)
            }
        },
        bottomBar = {
            Surface(
                color = editorTheme.containerColor,
                tonalElevation = 0.dp,
                shadowElevation = 8.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .imePadding()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(editorTheme.containerColor)
                        .navigationBarsPadding()
                ) {
                    // Expandable Styling & Category Bar
                    AnimatedVisibility(visible = showFormattingBar) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(editorTheme.accentContainer.copy(alpha = 0.35f))
                                .padding(horizontal = 16.dp, vertical = 10.dp)
                        ) {
                            // Category chips
                            Text(
                                text = "Category",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = editorTheme.secondaryTextColor
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
                                        color = if (isSelected) editorTheme.accentPrimary else editorTheme.containerColor,
                                        border = if (isSelected) null else BorderStroke(1.dp, editorTheme.borderColor),
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(10.dp))
                                            .clickable { category = cat }
                                    ) {
                                        Text(
                                            text = cat,
                                            color = if (isSelected) Color.White else editorTheme.titleColor,
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
                                color = editorTheme.secondaryTextColor
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                colorPalettes.forEach { (hex, label) ->
                                    val isSelected = colorHex.equals(hex, ignoreCase = true)
                                    val chipTheme = resolveNoteCardColors(hex, isDark)
                                    Box(
                                        modifier = Modifier
                                            .size(28.dp)
                                            .clip(CircleShape)
                                            .background(chipTheme.containerColor)
                                            .border(
                                                width = if (isSelected) 2.dp else 1.dp,
                                                color = if (isSelected) chipTheme.accentPrimary else chipTheme.borderColor,
                                                shape = CircleShape
                                            )
                                            .clickable { colorHex = hex },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(10.dp)
                                                .background(chipTheme.accentPrimary, CircleShape)
                                        )
                                        if (isSelected) {
                                            Icon(
                                                imageVector = Icons.Default.Check,
                                                contentDescription = label,
                                                tint = Color.White,
                                                modifier = Modifier.size(14.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    HorizontalDivider(
                        color = editorTheme.borderColor,
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
                            color = editorTheme.accentContainer,
                            border = BorderStroke(0.5.dp, editorTheme.borderColor),
                            modifier = Modifier.padding(end = 8.dp)
                        ) {
                            Text(
                                text = "$wordCount words • $charCount chars • $category",
                                fontSize = 11.sp,
                                color = editorTheme.categoryBadgeText,
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
                                Icon(
                                    Icons.Default.ContentCopy,
                                    contentDescription = "Copy",
                                    tint = editorTheme.secondaryTextColor,
                                    modifier = Modifier.size(18.dp)
                                )
                            }

                            // Save Button: Styled with Note Accent Primary
                            Box(
                                modifier = Modifier
                                    .height(42.dp)
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(editorTheme.accentPrimary)
                                    .clickable {
                                        val finalTitle = title.trim()
                                        val finalContent = content.trim()
                                        if (finalTitle.isEmpty() && finalContent.isEmpty() && checklistItems.isEmpty()) {
                                            onToast("Empty note discarded")
                                            onDismiss()
                                            return@clickable
                                        }
                                        onSave(
                                            initialNote?.id,
                                            if (finalTitle.isEmpty()) "Untitled" else finalTitle,
                                            content,
                                            category,
                                            colorHex,
                                            checklistItems,
                                            isPinned,
                                            reminderTime
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
        containerColor = editorTheme.containerColor
    ) { paddingValues ->
        // Full-Height Expansive Note Content Textbox
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 20.dp, vertical = 8.dp)
        ) {
            // Active Reminder Chip Banner
            if (reminderTime != null) {
                val isPast = reminderTime!! <= System.currentTimeMillis()
                val reminderFormatted = remember(reminderTime) {
                    val sdf = SimpleDateFormat("EEE, MMM d • h:mm a", Locale.getDefault())
                    sdf.format(Date(reminderTime!!))
                }
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = if (isPast) editorTheme.accentContainer.copy(alpha = 0.5f) else editorTheme.accentContainer,
                    border = BorderStroke(0.5.dp, editorTheme.borderColor),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { showReminderDialog = true }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (isPast) Icons.Default.NotificationsOff else Icons.Filled.NotificationsActive,
                            contentDescription = null,
                            tint = if (isPast) editorTheme.secondaryTextColor else editorTheme.accentPrimary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Reminder: $reminderFormatted" + if (isPast) " (Passed)" else "",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = if (isPast) editorTheme.secondaryTextColor else editorTheme.accentPrimary,
                            modifier = Modifier.weight(1f)
                        )
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Remove reminder",
                            tint = editorTheme.secondaryTextColor,
                            modifier = Modifier
                                .size(20.dp)
                                .clip(CircleShape)
                                .clickable {
                                    reminderTime = null
                                    onToast("Reminder removed")
                                }
                                .padding(2.dp)
                        )
                    }
                }
            }

            // Note Title Input
            BasicTextField(
                value = title,
                onValueChange = { title = it },
                textStyle = TextStyle(
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = editorTheme.titleColor
                ),
                cursorBrush = SolidColor(editorTheme.accentPrimary),
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
                            color = editorTheme.secondaryTextColor.copy(alpha = 0.5f)
                        )
                    }
                    innerTextField()
                }
            )

            HorizontalDivider(color = editorTheme.borderColor.copy(alpha = 0.5f), thickness = 0.5.dp)
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
                                colors = CheckboxDefaults.colors(
                                    checkedColor = editorTheme.accentPrimary,
                                    uncheckedColor = editorTheme.borderColor
                                )
                            )

                            Text(
                                text = item.text,
                                fontSize = 15.sp,
                                color = if (item.isDone) editorTheme.secondaryTextColor else editorTheme.titleColor,
                                textDecoration = if (item.isDone) androidx.compose.ui.text.style.TextDecoration.LineThrough else null,
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
                                    tint = editorTheme.secondaryTextColor,
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
                            placeholder = { Text("Add checklist item...", color = editorTheme.secondaryTextColor.copy(alpha = 0.6f)) },
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
                                focusedTextColor = editorTheme.titleColor,
                                unfocusedTextColor = editorTheme.titleColor,
                                focusedBorderColor = editorTheme.accentPrimary,
                                unfocusedBorderColor = editorTheme.borderColor
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
                                .background(editorTheme.accentContainer, RoundedCornerShape(12.dp))
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = "Add",
                                tint = editorTheme.accentPrimary
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
                        color = editorTheme.contentColor
                    ),
                    cursorBrush = SolidColor(editorTheme.accentPrimary),
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("note_editor_content_input"),
                    decorationBox = { innerTextField ->
                        if (content.isEmpty()) {
                            Text(
                                text = "Start typing your note here...",
                                fontSize = 16.sp,
                                lineHeight = 24.sp,
                                color = editorTheme.secondaryTextColor.copy(alpha = 0.5f)
                            )
                        }
                        innerTextField()
                    }
                )
            }
        }

        if (showReminderDialog) {
        NoteReminderPickerDialog(
            currentReminderTime = reminderTime,
            onReminderSelected = { selectedTime ->
                reminderTime = selectedTime
                onToast("Reminder set ⏰")
            },
            onClearReminder = {
                reminderTime = null
                onToast("Reminder removed")
            },
            onDismiss = { showReminderDialog = false }
        )
    }
}
}

@Composable
fun NoteReminderPickerDialog(
    currentReminderTime: Long?,
    onReminderSelected: (Long) -> Unit,
    onClearReminder: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val calendar = remember { Calendar.getInstance() }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Alarm,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Set Reminder",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Get notified at the chosen time for this note.",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(4.dp))

                // Option 1: In 3 hours (Later Today)
                ReminderPresetOption(
                    title = "Later Today (+3 hours)",
                    subtitle = remember {
                        val cal = Calendar.getInstance().apply { add(Calendar.HOUR_OF_DAY, 3) }
                        SimpleDateFormat("h:mm a", Locale.getDefault()).format(cal.time)
                    },
                    icon = Icons.Outlined.AccessTime,
                    onClick = {
                        val cal = Calendar.getInstance().apply { add(Calendar.HOUR_OF_DAY, 3) }
                        onReminderSelected(cal.timeInMillis)
                        onDismiss()
                    }
                )

                // Option 2: Tonight (8:00 PM)
                ReminderPresetOption(
                    title = "Tonight (8:00 PM)",
                    subtitle = remember {
                        val cal = Calendar.getInstance().apply {
                            set(Calendar.HOUR_OF_DAY, 20)
                            set(Calendar.MINUTE, 0)
                            set(Calendar.SECOND, 0)
                            if (timeInMillis <= System.currentTimeMillis()) {
                                add(Calendar.DAY_OF_YEAR, 1)
                            }
                        }
                        val sdf = SimpleDateFormat("EEE, h:mm a", Locale.getDefault())
                        sdf.format(cal.time)
                    },
                    icon = Icons.Outlined.Nightlight,
                    onClick = {
                        val cal = Calendar.getInstance().apply {
                            set(Calendar.HOUR_OF_DAY, 20)
                            set(Calendar.MINUTE, 0)
                            set(Calendar.SECOND, 0)
                            if (timeInMillis <= System.currentTimeMillis()) {
                                add(Calendar.DAY_OF_YEAR, 1)
                            }
                        }
                        onReminderSelected(cal.timeInMillis)
                        onDismiss()
                    }
                )

                // Option 3: Tomorrow Morning (9:00 AM)
                ReminderPresetOption(
                    title = "Tomorrow Morning (9:00 AM)",
                    subtitle = remember {
                        val cal = Calendar.getInstance().apply {
                            add(Calendar.DAY_OF_YEAR, 1)
                            set(Calendar.HOUR_OF_DAY, 9)
                            set(Calendar.MINUTE, 0)
                            set(Calendar.SECOND, 0)
                        }
                        val sdf = SimpleDateFormat("EEE, MMM d • 9:00 AM", Locale.getDefault())
                        sdf.format(cal.time)
                    },
                    icon = Icons.Outlined.WbSunny,
                    onClick = {
                        val cal = Calendar.getInstance().apply {
                            add(Calendar.DAY_OF_YEAR, 1)
                            set(Calendar.HOUR_OF_DAY, 9)
                            set(Calendar.MINUTE, 0)
                            set(Calendar.SECOND, 0)
                        }
                        onReminderSelected(cal.timeInMillis)
                        onDismiss()
                    }
                )

                // Option 4: Custom Date & Time
                ReminderPresetOption(
                    title = "Pick Custom Date & Time",
                    subtitle = "Select exact date & time",
                    icon = Icons.Outlined.CalendarMonth,
                    onClick = {
                        onDismiss()
                        val currentYear = calendar.get(Calendar.YEAR)
                        val currentMonth = calendar.get(Calendar.MONTH)
                        val currentDay = calendar.get(Calendar.DAY_OF_MONTH)
                        val currentHour = calendar.get(Calendar.HOUR_OF_DAY)
                        val currentMinute = calendar.get(Calendar.MINUTE)

                        android.app.DatePickerDialog(
                            context,
                            { _, year, month, dayOfMonth ->
                                android.app.TimePickerDialog(
                                    context,
                                    { _, hourOfDay, minute ->
                                        val pickedCal = Calendar.getInstance().apply {
                                            set(Calendar.YEAR, year)
                                            set(Calendar.MONTH, month)
                                            set(Calendar.DAY_OF_MONTH, dayOfMonth)
                                            set(Calendar.HOUR_OF_DAY, hourOfDay)
                                            set(Calendar.MINUTE, minute)
                                            set(Calendar.SECOND, 0)
                                        }
                                        if (pickedCal.timeInMillis > System.currentTimeMillis()) {
                                            onReminderSelected(pickedCal.timeInMillis)
                                        }
                                    },
                                    currentHour,
                                    currentMinute,
                                    false
                                ).show()
                            },
                            currentYear,
                            currentMonth,
                            currentDay
                        ).show()
                    }
                )

                if (currentReminderTime != null) {
                    Spacer(modifier = Modifier.height(4.dp))
                    OutlinedButton(
                        onClick = {
                            onClearReminder()
                            onDismiss()
                        },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f)),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.DeleteOutline, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Clear Reminder", fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        shape = RoundedCornerShape(20.dp),
        containerColor = MaterialTheme.colorScheme.surface
    )
}

@Composable
private fun ReminderPresetOption(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = subtitle,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.size(18.dp)
            )
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
        isPinned: Boolean,
        reminderTime: Long?
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
        onSave = { id, title, content, category, colorHex, checklist, isPinned, _ ->
            onSave(id, title, content, category, colorHex, checklist, isPinned)
        },
        onDelete = onDelete,
        onShare = onShare,
        onToast = onToast
    )
}


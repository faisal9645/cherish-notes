package com.example.ui.disguise

import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.security.SecurityPreferences
import com.example.ui.theme.DarkOnBackground
import com.example.ui.theme.DarkOnSurfaceVariant
import com.example.ui.theme.OnRoseGoldContainer
import com.example.ui.theme.RoseGoldContainer
import com.example.ui.theme.RoseGoldOnPrimary
import com.example.ui.theme.RoseGoldPrimary
import com.example.ui.theme.SoftBorderOutline
import com.example.ui.theme.SoftPinkSurfaceVariant
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout

data class DecoyNote(
    val id: String = java.util.UUID.randomUUID().toString(),
    val title: String,
    val content: String,
    val date: String,
    val category: String = "All",
    val color: Color = Color(0xFFFFE8EC),
    val isPinned: Boolean = false,
    val checklist: List<String> = emptyList()
)

/**
 * Custom 3-second long press gesture modifier for intentional secret unlock triggers.
 * Requires continuous press for durationMillis (default 3000ms = 3 seconds).
 * If released earlier, triggers standard onClick.
 */
fun Modifier.secretLongPressGesture(
    durationMillis: Long = 3000L,
    onClick: (() -> Unit)? = null,
    onLongPress: () -> Unit
): Modifier = this.pointerInput(durationMillis) {
    awaitEachGesture {
        awaitFirstDown(requireUnconsumed = false)
        try {
            withTimeout(durationMillis) {
                val up = waitForUpOrCancellation()
                if (up != null) {
                    onClick?.invoke()
                }
            }
        } catch (_: TimeoutCancellationException) {
            // Held continuously for full 3 seconds!
            onLongPress()
            waitForUpOrCancellation()
        }
    }
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun NotesDisguiseScreen(
    securityPreferences: SecurityPreferences,
    onSecretGestureTriggered: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current

    var searchQuery by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf("All") }
    var showAddNoteDialog by remember { mutableStateOf(false) }
    var viewingNote by remember { mutableStateOf<DecoyNote?>(null) }

    var newNoteTitle by remember { mutableStateOf("") }
    var newNoteContent by remember { mutableStateOf("") }
    var newNoteCategory by remember { mutableStateOf("Personal") }

    // Persistent in-memory decoy notes list matching app color layout
    var notesList by remember {
        mutableStateOf(
            listOf(
                DecoyNote(
                    id = "1",
                    title = "Grocery & Pantry Checklist",
                    content = "Weekly grocery replenishment for home",
                    date = "Today, 10:15 AM",
                    category = "Lists",
                    color = Color(0xFFFFE8EC), // Soft Blush
                    isPinned = true,
                    checklist = listOf("Almond milk & Greek yogurt", "Whole grain sourdough", "Avocados (3)", "Olive oil", "Cold brew coffee")
                ),
                DecoyNote(
                    id = "2",
                    title = "Work Meeting Summary",
                    content = "Reviewed Q3 roadmap and feature timeline. Next sprint starts Tuesday. Submit pull requests by 4 PM.",
                    date = "Yesterday",
                    category = "Work",
                    color = Color(0xFFFFDEC9) // Soft Champagne
                ),
                DecoyNote(
                    id = "3",
                    title = "Book & Article Recommendations",
                    content = "1. Designing Data-Intensive Applications\n2. Atomic Habits\n3. Thinking in Systems by Donella Meadows",
                    date = "Sep 25",
                    category = "Ideas",
                    color = Color(0xFFF3E2FF) // Soft Lavender
                ),
                DecoyNote(
                    id = "4",
                    title = "Apartment Wishlist",
                    content = "Ergonomic standing desk lamp, monstera plant pot, linen bedsheets, coffee beans grinder.",
                    date = "Sep 22",
                    category = "Personal",
                    color = Color(0xFFE6F7F0) // Soft Sage
                ),
                DecoyNote(
                    id = "5",
                    title = "Weekly Workout Schedule",
                    content = "Mon: Push (Chest & Shoulders)\nWed: Pull (Back & Biceps)\nFri: Legs & Core\nSun: 5km light jog",
                    date = "Sep 18",
                    category = "Personal",
                    color = Color(0xFFFFF3E0) // Soft Cream
                )
            )
        )
    }

    var checkedItems by remember { mutableStateOf(setOf("Almond milk & Greek yogurt")) }

    fun triggerSecretUnlock() {
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        try {
            val vibrator = context.getSystemService(Vibrator::class.java)
            if (vibrator != null && vibrator.hasVibrator()) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator.vibrate(
                        VibrationEffect.createWaveform(
                            longArrayOf(0, 100, 100, 200),
                            -1
                        )
                    )
                } else {
                    @Suppress("DEPRECATION")
                    vibrator.vibrate(250)
                }
            }
        } catch (_: Exception) {
        }
        onSecretGestureTriggered()
    }

    fun checkSecretUnlockAttempt(text: String): Boolean {
        if (securityPreferences.verifyDisguisePasscode(text.trim())) {
            triggerSecretUnlock()
            return true
        }
        return false
    }

    val categories = listOf("All", "Lists", "Personal", "Work", "Ideas")

    val filteredNotes = notesList.filter { note ->
        val matchesCategory = selectedCategory == "All" || note.category == selectedCategory
        val matchesQuery = searchQuery.isBlank() ||
                note.title.contains(searchQuery, ignoreCase = true) ||
                note.content.contains(searchQuery, ignoreCase = true)
        matchesCategory && matchesQuery
    }

    Surface(
        modifier = modifier.fillMaxSize(),
        color = Color.White
    ) {
        Scaffold(
            topBar = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.White)
                        .statusBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    // Header Bar (Normal header without gestures)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .padding(horizontal = 4.dp, vertical = 4.dp)
                                .testTag("notes_header")
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = RoseGoldContainer,
                                modifier = Modifier.size(36.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Filled.EditNote,
                                        contentDescription = "Notes",
                                        tint = RoseGoldPrimary,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "Notes",
                                fontSize = 24.sp,
                                fontWeight = FontWeight.Bold,
                                color = DarkOnBackground
                            )
                        }

                        // Notes count pill and folders icon (without gestures)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = RoseGoldContainer.copy(alpha = 0.6f),
                                modifier = Modifier
                                    .padding(horizontal = 10.dp, vertical = 5.dp)
                            ) {
                                Text(
                                    text = "${notesList.size} notes",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = OnRoseGoldContainer
                                )
                            }

                            Spacer(modifier = Modifier.width(6.dp))

                            IconButton(
                                onClick = {
                                    if (searchQuery.isNotBlank()) {
                                        checkSecretUnlockAttempt(searchQuery)
                                    }
                                }
                            ) {
                                Icon(
                                    Icons.Outlined.Folder,
                                    contentDescription = "Folders",
                                    tint = DarkOnSurfaceVariant
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Search Bar: typing 'love' unlocks!
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = {
                            searchQuery = it
                            if (checkSecretUnlockAttempt(it)) {
                                searchQuery = ""
                            }
                        },
                        placeholder = {
                            Text(
                                "Search notes...",
                                color = DarkOnSurfaceVariant.copy(alpha = 0.7f),
                                fontSize = 14.sp
                            )
                        },
                        leadingIcon = {
                            Icon(
                                Icons.Outlined.Search,
                                contentDescription = "Search",
                                tint = DarkOnSurfaceVariant
                            )
                        },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { searchQuery = "" }) {
                                    Icon(
                                        Icons.Default.Close,
                                        contentDescription = "Clear",
                                        tint = DarkOnSurfaceVariant
                                    )
                                }
                            }
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(16.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = SoftPinkSurfaceVariant,
                            unfocusedContainerColor = SoftPinkSurfaceVariant,
                            focusedBorderColor = RoseGoldPrimary,
                            unfocusedBorderColor = SoftBorderOutline
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .testTag("notes_search_input")
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Category Filter Chips matching app layout
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(categories) { cat ->
                            val isSelected = selectedCategory == cat
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = if (isSelected) RoseGoldPrimary else SoftPinkSurfaceVariant,
                                border = if (isSelected) null else BorderStroke(1.dp, SoftBorderOutline),
                                modifier = Modifier
                                    .clip(RoundedCornerShape(14.dp))
                                    .clickable { selectedCategory = cat }
                            ) {
                                Text(
                                    text = cat,
                                    color = if (isSelected) RoseGoldOnPrimary else DarkOnSurfaceVariant,
                                    fontSize = 13.sp,
                                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 7.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))
                    HorizontalDivider(
                        color = Color(0xFFF0F0F2),
                        thickness = 1.dp,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            },
            floatingActionButton = {
                // Floating Action Button with RoseGoldPrimary color and SECRET GESTURE 4: 3-second Long-press '+' button!
                FloatingActionButton(
                    onClick = { },
                    containerColor = RoseGoldPrimary,
                    contentColor = RoseGoldOnPrimary,
                    shape = CircleShape,
                    modifier = Modifier
                        .testTag("add_note_fab")
                        .secretLongPressGesture(
                            durationMillis = 3000L,
                            onClick = { showAddNoteDialog = true },
                            onLongPress = {
                                // 3-second Long-press FAB to unlock secret app!
                                triggerSecretUnlock()
                            }
                        )
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Add Note")
                }
            },
            containerColor = Color.White
        ) { paddingValues ->
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(filteredNotes, key = { it.id }) { note ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { viewingNote = note },
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        border = BorderStroke(1.dp, SoftBorderOutline),
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(10.dp)
                                            .clip(CircleShape)
                                            .background(note.color)
                                            .border(1.dp, Color(0x33000000), CircleShape)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = note.title,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 16.sp,
                                        color = DarkOnBackground,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }

                                if (note.isPinned) {
                                    Icon(
                                        Icons.Outlined.PushPin,
                                        contentDescription = "Pinned",
                                        tint = RoseGoldPrimary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }

                            if (note.checklist.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(10.dp))
                                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    note.checklist.take(4).forEach { item ->
                                        val isChecked = checkedItems.contains(item)
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(6.dp))
                                                .clickable {
                                                    checkedItems = if (isChecked) {
                                                        checkedItems - item
                                                    } else {
                                                        checkedItems + item
                                                    }
                                                }
                                                .padding(vertical = 2.dp)
                                        ) {
                                            Icon(
                                                imageVector = if (isChecked) Icons.Filled.CheckCircle else Icons.Outlined.RadioButtonUnchecked,
                                                contentDescription = null,
                                                tint = if (isChecked) RoseGoldPrimary else DarkOnSurfaceVariant.copy(alpha = 0.5f),
                                                modifier = Modifier.size(18.dp)
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(
                                                text = item,
                                                fontSize = 13.sp,
                                                color = if (isChecked) DarkOnSurfaceVariant.copy(alpha = 0.6f) else DarkOnBackground,
                                                textDecoration = if (isChecked) TextDecoration.LineThrough else null
                                            )
                                        }
                                    }
                                }
                            } else {
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = note.content,
                                    fontSize = 13.sp,
                                    color = DarkOnSurfaceVariant,
                                    maxLines = 3,
                                    overflow = TextOverflow.Ellipsis,
                                    lineHeight = 18.sp
                                )
                            }

                            Spacer(modifier = Modifier.height(10.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = note.date,
                                    fontSize = 11.sp,
                                    color = DarkOnSurfaceVariant.copy(alpha = 0.6f)
                                )
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = SoftPinkSurfaceVariant
                                ) {
                                    Text(
                                        text = note.category,
                                        fontSize = 11.sp,
                                        color = DarkOnSurfaceVariant,
                                        fontWeight = FontWeight.Medium,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                    )
                                }
                            }
                        }
                    }
                }
                item {
                    Spacer(modifier = Modifier.height(72.dp))
                }
            }
        }
    }

    // Add Note Dialog
    if (showAddNoteDialog) {
        AlertDialog(
            onDismissRequest = { showAddNoteDialog = false },
            title = {
                Text(
                    "New Note",
                    fontWeight = FontWeight.Bold,
                    color = DarkOnBackground
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = newNoteTitle,
                        onValueChange = { newNoteTitle = it },
                        label = { Text("Title") },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = RoseGoldPrimary,
                            unfocusedBorderColor = SoftBorderOutline
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = newNoteContent,
                        onValueChange = { newNoteContent = it },
                        label = { Text("Note Content") },
                        minLines = 3,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = RoseGoldPrimary,
                            unfocusedBorderColor = SoftBorderOutline
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    // Category Selection Chips
                    Text(
                        "Category",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = DarkOnSurfaceVariant
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf("Personal", "Work", "Lists", "Ideas").forEach { cat ->
                            val isSel = newNoteCategory == cat
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = if (isSel) RoseGoldPrimary else SoftPinkSurfaceVariant,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(10.dp))
                                    .clickable { newNoteCategory = cat }
                            ) {
                                Text(
                                    text = cat,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = if (isSel) Color.White else DarkOnSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newNoteTitle.isNotBlank()) {
                            notesList = listOf(
                                DecoyNote(
                                    title = newNoteTitle,
                                    content = newNoteContent,
                                    date = "Just now",
                                    category = newNoteCategory,
                                    color = Color(0xFFFFE8EC)
                                )
                            ) + notesList
                        }
                        newNoteTitle = ""
                        newNoteContent = ""
                        showAddNoteDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = RoseGoldPrimary)
                ) {
                    Text("Save Note", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddNoteDialog = false }) {
                    Text("Cancel", color = DarkOnSurfaceVariant)
                }
            }
        )
    }

    // View / Edit Note Dialog
    viewingNote?.let { note ->
        var editTitle by remember(note) { mutableStateOf(note.title) }
        var editContent by remember(note) { mutableStateOf(note.content) }

        AlertDialog(
            onDismissRequest = { viewingNote = null },
            title = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Note Details",
                        fontWeight = FontWeight.Bold,
                        color = DarkOnBackground
                    )
                    IconButton(
                        onClick = {
                            notesList = notesList.filter { it.id != note.id }
                            viewingNote = null
                        }
                    ) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = "Delete Note",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = editTitle,
                        onValueChange = { editTitle = it },
                        label = { Text("Title") },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = RoseGoldPrimary,
                            unfocusedBorderColor = SoftBorderOutline
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = editContent,
                        onValueChange = { editContent = it },
                        label = { Text("Content") },
                        minLines = 4,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = RoseGoldPrimary,
                            unfocusedBorderColor = SoftBorderOutline
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (note.checklist.isNotEmpty()) {
                        Text(
                            "Checklist items:",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 12.sp,
                            color = DarkOnSurfaceVariant
                        )
                        note.checklist.forEach { item ->
                            val isChecked = checkedItems.contains(item)
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(6.dp))
                                    .clickable {
                                        checkedItems = if (isChecked) checkedItems - item else checkedItems + item
                                    }
                                    .padding(vertical = 2.dp)
                            ) {
                                Icon(
                                    imageVector = if (isChecked) Icons.Filled.CheckCircle else Icons.Outlined.RadioButtonUnchecked,
                                    contentDescription = null,
                                    tint = if (isChecked) RoseGoldPrimary else DarkOnSurfaceVariant.copy(alpha = 0.5f),
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = item,
                                    fontSize = 13.sp,
                                    color = if (isChecked) DarkOnSurfaceVariant.copy(alpha = 0.6f) else DarkOnBackground,
                                    textDecoration = if (isChecked) TextDecoration.LineThrough else null
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        notesList = notesList.map {
                            if (it.id == note.id) {
                                it.copy(title = editTitle, content = editContent)
                            } else it
                        }
                        viewingNote = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = RoseGoldPrimary)
                ) {
                    Text("Done", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { viewingNote = null }) {
                    Text("Close", color = DarkOnSurfaceVariant)
                }
            }
        )
    }
}

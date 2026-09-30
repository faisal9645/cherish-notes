package com.example.ui.disguise

import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.ui.graphics.graphicsLayer
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.lazy.staggeredgrid.items
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.CherishApplication
import com.example.data.local.notes.NoteEntity
import com.example.security.SecurityPreferences
import com.example.ui.theme.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun NotesDisguiseScreen(
    viewModel: NotesDisguiseViewModel = remember {
        val app = CherishApplication.instance
        NotesDisguiseViewModel(app.notesRepository)
    },
    securityPreferences: SecurityPreferences,
    onSecretGestureTriggered: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val coroutineScope = rememberCoroutineScope()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    var showEditorDialog by remember { mutableStateOf(false) }
    var selectedNoteForEdit by remember { mutableStateOf<NoteEntity?>(null) }
    var showSortMenu by remember { mutableStateOf(false) }
    var showBatchDeleteDialog by remember { mutableStateOf(false) }

    // State for the 2-second hold on the '+' FAB
    val holdProgress = remember { Animatable(0f) }
    var isHoldingFab by remember { mutableStateOf(false) }
    val fabScale by animateFloatAsState(
        targetValue = if (isHoldingFab) 0.90f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "fab_press_scale"
    )

    val categories = listOf("All", "📌 Pinned", "Lists", "Personal", "Work", "Ideas", "Journal")

    // Handle Back press in Selection Mode
    BackHandler(enabled = uiState.isSelectionMode) {
        viewModel.clearSelection()
    }

    // Secret unlock transition state
    var isUnlockingSecret by remember { mutableStateOf(false) }

    fun triggerSecretUnlock() {
        if (isUnlockingSecret) return
        isUnlockingSecret = true
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        try {
            val vibrator = context.getSystemService(Vibrator::class.java)
            if (vibrator != null && vibrator.hasVibrator()) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator.vibrate(
                        VibrationEffect.createWaveform(
                            longArrayOf(0, 35, 70, 50),
                            -1
                        )
                    )
                } else {
                    @Suppress("DEPRECATION")
                    vibrator.vibrate(100)
                }
            }
        } catch (_: Exception) {}

        onSecretGestureTriggered()
        coroutineScope.launch {
            kotlinx.coroutines.delay(500)
            isUnlockingSecret = false
        }
    }

    // Full-screen Note Editor Screen (fixes overlapping Save Note button issue by using real window insets)
    if (showEditorDialog) {
        NoteEditorScreen(
            initialNote = selectedNoteForEdit,
            onDismiss = {
                showEditorDialog = false
                selectedNoteForEdit = null
            },
            onSave = { id, title, content, cat, hex, checklist, pinned ->
                viewModel.saveNote(
                    id = id,
                    title = title,
                    content = content,
                    category = cat,
                    colorHex = hex,
                    checklist = checklist,
                    isPinned = pinned
                )
            },
            onDelete = { noteId ->
                viewModel.deleteNote(noteId)
            },
            onShare = { note ->
                viewModel.shareNote(context, note)
            },
            onToast = { msg ->
                viewModel.showToastMessage(msg)
            }
        )
        return
    }

    // Snackbar Host State
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(uiState.snackbarMessage) {
        uiState.snackbarMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.clearSnackbar()
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = Color.White
        ) {
        Scaffold(
            snackbarHost = { SnackbarHost(snackbarHostState) },
            topBar = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.White)
                        .statusBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    if (uiState.isSelectionMode) {
                        // Contextual Multi-Selection Action Bar
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconButton(onClick = { viewModel.clearSelection() }) {
                                    Icon(Icons.Default.Close, contentDescription = "Cancel Selection")
                                }
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "${uiState.selectedNoteIds.size} selected",
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = DarkOnBackground
                                )
                            }

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                val allSelected = uiState.notes.isNotEmpty() && uiState.selectedNoteIds.size == uiState.notes.size
                                TextButton(
                                    onClick = {
                                        if (allSelected) {
                                            viewModel.clearSelection()
                                        } else {
                                            viewModel.selectAll(uiState.notes.map { it.id })
                                        }
                                    }
                                ) {
                                    Text(
                                        text = if (allSelected) "Deselect All" else "Select All",
                                        color = RoseGoldPrimary,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }

                                IconButton(
                                    onClick = { showBatchDeleteDialog = true },
                                    enabled = uiState.selectedNoteIds.isNotEmpty(),
                                    modifier = Modifier.testTag("delete_selected_notes_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = "Delete Selected Notes",
                                        tint = if (uiState.selectedNoteIds.isNotEmpty()) MaterialTheme.colorScheme.error else Color.Gray
                                    )
                                }
                            }
                        }
                    } else {
                        // Standard Header Bar
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Brand Icon + Title
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .padding(horizontal = 4.dp, vertical = 4.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .testTag("notes_header")
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(38.dp)
                                        .appGradientShadow(CircleShape)
                                        .clip(CircleShape)
                                        .background(appHorizontalGradient()),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.EditNote,
                                        contentDescription = "Notes",
                                        tint = Color.White,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = "Notes",
                                    fontSize = 24.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = DarkOnBackground
                                )
                            }

                            // Right actions: Select, Count pill, Grid/List toggle, and Sort menu
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                // Multi-select button
                                IconButton(
                                    onClick = { viewModel.startSelectionMode() },
                                    modifier = Modifier.testTag("start_select_mode_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.CheckCircle,
                                        contentDescription = "Select notes to delete",
                                        tint = DarkOnSurfaceVariant
                                    )
                                }

                                // View Mode Toggle (Grid vs List)
                                IconButton(onClick = { viewModel.toggleViewMode() }) {
                                    Icon(
                                        imageVector = if (uiState.isGridView) Icons.Default.ViewAgenda else Icons.Default.GridView,
                                        contentDescription = if (uiState.isGridView) "Switch to List View" else "Switch to Grid View",
                                        tint = DarkOnSurfaceVariant
                                    )
                                }

                                // Sort Menu
                                Box {
                                    IconButton(onClick = { showSortMenu = true }) {
                                        Icon(
                                            imageVector = Icons.Outlined.Sort,
                                            contentDescription = "Sort notes",
                                            tint = DarkOnSurfaceVariant
                                        )
                                    }

                                    DropdownMenu(
                                        expanded = showSortMenu,
                                        onDismissRequest = { showSortMenu = false }
                                    ) {
                                        DropdownMenuItem(
                                            text = { Text("Recently Modified") },
                                            onClick = {
                                                viewModel.setSortOrder(NotesSortOrder.RECENT)
                                                showSortMenu = false
                                            },
                                            trailingIcon = {
                                                if (uiState.sortOrder == NotesSortOrder.RECENT) {
                                                    Icon(Icons.Default.Check, contentDescription = null, tint = RoseGoldPrimary)
                                                }
                                            }
                                        )
                                        DropdownMenuItem(
                                            text = { Text("Alphabetical (A-Z)") },
                                            onClick = {
                                                viewModel.setSortOrder(NotesSortOrder.ALPHABETICAL)
                                                showSortMenu = false
                                            },
                                            trailingIcon = {
                                                if (uiState.sortOrder == NotesSortOrder.ALPHABETICAL) {
                                                    Icon(Icons.Default.Check, contentDescription = null, tint = RoseGoldPrimary)
                                                }
                                            }
                                        )
                                        DropdownMenuItem(
                                            text = { Text("By Category") },
                                            onClick = {
                                                viewModel.setSortOrder(NotesSortOrder.CATEGORY)
                                                showSortMenu = false
                                            },
                                            trailingIcon = {
                                                if (uiState.sortOrder == NotesSortOrder.CATEGORY) {
                                                    Icon(Icons.Default.Check, contentDescription = null, tint = RoseGoldPrimary)
                                                }
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Search Bar
                    OutlinedTextField(
                        value = uiState.searchQuery,
                        onValueChange = { input ->
                            // Check secret keyword trigger if enabled in Settings
                            if (securityPreferences.isKeywordTriggerEnabled()) {
                                if (securityPreferences.verifyDisguisePasscode(input)) {
                                    // Clear query immediately so trigger word is never visible or stored
                                    viewModel.setSearchQuery("")
                                    triggerSecretUnlock()
                                    return@OutlinedTextField
                                }
                            }
                            // Normal search continues smoothly
                            viewModel.setSearchQuery(input)
                        },
                        placeholder = {
                            Text(
                                "Search notes, checklists, ideas...",
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
                            if (uiState.searchQuery.isNotEmpty()) {
                                IconButton(onClick = { viewModel.setSearchQuery("") }) {
                                    Icon(
                                        Icons.Default.Close,
                                        contentDescription = "Clear search",
                                        tint = DarkOnSurfaceVariant
                                    )
                                }
                            }
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(16.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = RoseGoldPrimary,
                            unfocusedBorderColor = SoftBorderOutline,
                            focusedContainerColor = SoftPinkSurfaceVariant,
                            unfocusedContainerColor = SoftPinkSurfaceVariant
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .testTag("notes_search_input")
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Category Filter Chips
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(categories) { cat ->
                            val isSelected = uiState.selectedCategory == cat
                            Box(
                                modifier = Modifier
                                    .then(if (isSelected) Modifier.appGradientShadow(RoundedCornerShape(14.dp)) else Modifier)
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(
                                        if (isSelected) appHorizontalGradient() else androidx.compose.ui.graphics.SolidColor(SoftPinkSurfaceVariant)
                                    )
                                    .then(if (!isSelected) Modifier.border(1.dp, SoftBorderOutline, RoundedCornerShape(14.dp)) else Modifier)
                                    .clickable { viewModel.setSelectedCategory(cat) }
                                    .padding(horizontal = 16.dp, vertical = 7.dp)
                            ) {
                                Text(
                                    text = cat,
                                    color = if (isSelected) Color.White else DarkOnSurfaceVariant,
                                    fontSize = 13.sp,
                                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
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
                if (!uiState.isSelectionMode) {
                    // Floating Action Button: Tap to add note, hold to unlock secret chat (no visible progress ring)
                    Box(
                        modifier = Modifier
                            .testTag("add_note_fab")
                            .size(56.dp)
                            .scale(fabScale)
                            .appGradientShadow(CircleShape)
                            .clip(CircleShape)
                            .background(appHorizontalGradient())
                            .pointerInput(Unit) {
                                awaitEachGesture {
                                    val down = awaitFirstDown(requireUnconsumed = false)
                                    down.consume()
                                    isHoldingFab = true
                                    val startTime = System.currentTimeMillis()
                                    var unlocked = false

                                    val holdDurationSec = securityPreferences.getPlusIconHoldDuration()
                                    val timerJob = if (holdDurationSec > 0) {
                                        val totalDurationMillis = holdDurationSec * 1000L
                                        coroutineScope.launch {
                                            holdProgress.snapTo(0f)
                                            var lastTick = 0
                                            while (isActive) {
                                                val elapsed = System.currentTimeMillis() - startTime
                                                val progress = (elapsed.toFloat() / totalDurationMillis).coerceIn(0f, 1f)
                                                holdProgress.snapTo(progress)

                                                // Progressive subtle haptic feedback as user holds
                                                val currentTick = (progress * 4).toInt()
                                                if (currentTick > lastTick && currentTick < 4) {
                                                    lastTick = currentTick
                                                    try {
                                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                    } catch (_: Exception) {}
                                                }

                                                if (progress >= 1f) {
                                                    unlocked = true
                                                    triggerSecretUnlock()
                                                    break
                                                }
                                                kotlinx.coroutines.delay(16)
                                            }
                                        }
                                    } else null

                                    val up = waitForUpOrCancellation()
                                    timerJob?.cancel()
                                    isHoldingFab = false
                                    val elapsed = System.currentTimeMillis() - startTime
                                    coroutineScope.launch {
                                        holdProgress.animateTo(0f, tween(150))
                                    }

                                    if (!unlocked) {
                                        if (up != null && elapsed < 350L) {
                                            selectedNoteForEdit = null
                                            showEditorDialog = true
                                        }
                                    }
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Add Note",
                            tint = Color.White,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }
            },
            containerColor = Color.White,
            contentWindowInsets = WindowInsets.navigationBars
        ) { paddingValues ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                if (uiState.notes.isEmpty()) {
                    // Empty State
                    Box(
                        modifier = Modifier
                            .fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(32.dp)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = RoseGoldContainer,
                            modifier = Modifier.size(72.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Outlined.NoteAdd,
                                    contentDescription = null,
                                    tint = RoseGoldPrimary,
                                    modifier = Modifier.size(36.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Text(
                            text = if (uiState.searchQuery.isNotBlank()) "No notes match '${uiState.searchQuery}'" else "No Notes in ${uiState.selectedCategory}",
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            color = DarkOnBackground
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        Text(
                            text = "Tap the + button below to create your first note or checklist.",
                            fontSize = 13.sp,
                            color = DarkOnSurfaceVariant,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )

                        Spacer(modifier = Modifier.height(20.dp))

                        Button(
                            onClick = {
                                selectedNoteForEdit = null
                                showEditorDialog = true
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = RoseGoldPrimary),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Create Note")
                        }
                    }
                }
            } else {
                if (uiState.isGridView) {
                    // Optimized Virtualized Staggered 2-column Grid for 120Hz/144Hz high-refresh displays
                    LazyVerticalStaggeredGrid(
                        columns = StaggeredGridCells.Fixed(2),
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalItemSpacing = 10.dp
                    ) {
                        items(uiState.notes, key = { it.id }, contentType = { "note" }) { note ->
                            NoteCard(
                                note = note,
                                isSelectionMode = uiState.isSelectionMode,
                                isSelected = uiState.selectedNoteIds.contains(note.id),
                                onToggleSelect = { viewModel.toggleNoteSelection(note.id) },
                                onLongClick = {
                                    if (!uiState.isSelectionMode) {
                                        viewModel.startSelectionMode(note.id)
                                    }
                                },
                                onClick = {
                                    selectedNoteForEdit = note
                                    showEditorDialog = true
                                },
                                onTogglePin = { viewModel.togglePin(note) },
                                onToggleChecklistItem = { itemId, isDone ->
                                    viewModel.toggleChecklistItem(note.id, itemId, isDone)
                                },
                                onDuplicate = { viewModel.duplicateNote(note) },
                                onShare = { viewModel.shareNote(context, note) },
                                onDelete = { viewModel.deleteNote(note.id) }
                            )
                        }
                        item(span = StaggeredGridItemSpan.FullLine) {
                            Spacer(modifier = Modifier.height(72.dp))
                        }
                    }
                } else {
                    // Standard Spacious List View
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(uiState.notes, key = { it.id }, contentType = { "note" }) { note ->
                            NoteCard(
                                note = note,
                                isSelectionMode = uiState.isSelectionMode,
                                isSelected = uiState.selectedNoteIds.contains(note.id),
                                onToggleSelect = { viewModel.toggleNoteSelection(note.id) },
                                onLongClick = {
                                    if (!uiState.isSelectionMode) {
                                        viewModel.startSelectionMode(note.id)
                                    }
                                },
                                onClick = {
                                    selectedNoteForEdit = note
                                    showEditorDialog = true
                                },
                                onTogglePin = { viewModel.togglePin(note) },
                                onToggleChecklistItem = { itemId, isDone ->
                                    viewModel.toggleChecklistItem(note.id, itemId, isDone)
                                },
                                onDuplicate = { viewModel.duplicateNote(note) },
                                onShare = { viewModel.shareNote(context, note) },
                                onDelete = { viewModel.deleteNote(note.id) }
                            )
                        }
                        item {
                            Spacer(modifier = Modifier.height(72.dp))
                        }
                    }
                }
            }
        }
    }

    // Confirmation Dialog for Multiple / Batch Delete
    if (showBatchDeleteDialog) {
        val count = uiState.selectedNoteIds.size
        AlertDialog(
            onDismissRequest = { showBatchDeleteDialog = false },
            title = {
                Text(
                    text = "Delete $count Note${if (count > 1) "s" else ""}?",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
            },
            text = {
                Text(
                    text = "Are you sure you want to delete the selected $count note${if (count > 1) "s" else ""}? This action cannot be undone.",
                    fontSize = 14.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteSelectedNotes()
                        showBatchDeleteDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showBatchDeleteDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}
}
}

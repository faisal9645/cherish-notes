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
import androidx.compose.ui.graphics.luminance
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.ui.res.painterResource
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
import androidx.compose.ui.draw.shadow
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
    var showNotesSettingsDialog by remember { mutableStateOf(false) }

    val pendingNoteId by CherishApplication.instance.pendingNoteIdFlow.collectAsStateWithLifecycle()
    LaunchedEffect(pendingNoteId) {
        val noteId = pendingNoteId ?: return@LaunchedEffect
        CherishApplication.instance.pendingNoteIdFlow.value = null
        viewModel.loadNoteForEdit(noteId) { note ->
            selectedNoteForEdit = note
            showEditorDialog = true
        }
    }

    // State for the 2-second hold on the '+' FAB
    val holdProgress = remember { Animatable(0f) }
    var isHoldingFab by remember { mutableStateOf(false) }
    val fabScale by animateFloatAsState(
        targetValue = if (isHoldingFab) 0.90f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "fab_press_scale"
    )

    val categories = listOf("All", "📌 Pinned", "Lists", "Personal", "Work", "Ideas")

    // Handle Back press in Selection Mode
    BackHandler(enabled = uiState.isSelectionMode) {
        viewModel.clearSelection()
    }

    // Secret unlock transition state
    var isUnlockingSecret by remember { mutableStateOf(false) }
    var isUnlockingAnimationPlaying by remember { mutableStateOf(false) }
    val unlockAnimProgress = remember { Animatable(0f) }

    fun triggerSecretUnlock() {
        if (isUnlockingSecret) return
        isUnlockingSecret = true
        isUnlockingAnimationPlaying = true

        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        try {
            val vibrator = context.getSystemService(Vibrator::class.java)
            if (vibrator != null && vibrator.hasVibrator()) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator.vibrate(
                        VibrationEffect.createWaveform(
                            longArrayOf(0, 45, 80, 70),
                            -1
                        )
                    )
                } else {
                    @Suppress("DEPRECATION")
                    vibrator.vibrate(120)
                }
            }
        } catch (_: Exception) {}

        coroutineScope.launch {
            unlockAnimProgress.snapTo(0f)
            unlockAnimProgress.animateTo(
                targetValue = 1f,
                animationSpec = tween(650, easing = FastOutSlowInEasing)
            )
        }

        // If user enabled device lock (password / PIN / pattern / fingerprint) after hold:
        if (securityPreferences.isRequirePhoneLockAfterHold()) {
            val activity = context as? androidx.fragment.app.FragmentActivity
            if (activity != null && com.example.security.BiometricHelper.isDeviceLockOrBiometricAvailable(context)) {
                com.example.security.BiometricHelper.showDeviceLockOrBiometricPrompt(
                    activity = activity,
                    title = "Confirm Phone Lock / Fingerprint",
                    subtitle = "Verify device credentials to open secret space",
                    onSuccess = {
                        coroutineScope.launch {
                            delay(250)
                            onSecretGestureTriggered()
                            delay(400)
                            isUnlockingSecret = false
                            isUnlockingAnimationPlaying = false
                        }
                    },
                    onError = {
                        isUnlockingSecret = false
                        isUnlockingAnimationPlaying = false
                    }
                )
                return
            }
        }

        coroutineScope.launch {
            delay(580)
            onSecretGestureTriggered()
            delay(400)
            isUnlockingSecret = false
            isUnlockingAnimationPlaying = false
        }
    }

    val isDark = isAppInDark()
    val currentColorScheme = MaterialTheme.colorScheme
    val notesColorScheme = if (isDark) {
        currentColorScheme.copy(
            primary = DayBluePrimary,
            secondary = DayBlueSecondary,
            tertiary = DayBlueTertiary,
            primaryContainer = Color(0xFF172554),
            onPrimaryContainer = Color(0xFFDBEAFE)
        )
    } else {
        currentColorScheme
    }

    val notesHorizontalGradient = androidx.compose.ui.graphics.Brush.horizontalGradient(
        listOf(DayBlueSecondary, DayBluePrimary, DayBlueTertiary)
    )

    MaterialTheme(
        colorScheme = notesColorScheme,
        typography = MaterialTheme.typography
    ) {
        // Full-screen Note Editor Screen (fixes overlapping Save Note button issue by using real window insets)
        if (showEditorDialog) {
            NoteEditorScreen(
                initialNote = selectedNoteForEdit,
                onDismiss = {
                    showEditorDialog = false
                    selectedNoteForEdit = null
                },
                onSave = { id, title, content, cat, hex, checklist, pinned, reminder ->
                    viewModel.saveNote(
                        id = id,
                        title = title,
                        content = content,
                        category = cat,
                        colorHex = hex,
                        checklist = checklist,
                        isPinned = pinned,
                        reminderTime = reminder
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
        } else {
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
                    color = MaterialTheme.colorScheme.surface
                ) {
                Scaffold(
                    snackbarHost = { SnackbarHost(snackbarHostState) },
                    topBar = {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.surface)
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
                                    color = MaterialTheme.colorScheme.onBackground
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
                                        color = MaterialTheme.colorScheme.primary,
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
                                        .clip(CircleShape)
                                        .background(notesHorizontalGradient),
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
                                    color = MaterialTheme.colorScheme.onBackground
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
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                // View Mode Toggle (Grid vs List)
                                IconButton(onClick = { viewModel.toggleViewMode() }) {
                                    Icon(
                                        imageVector = if (uiState.isGridView) Icons.Default.ViewAgenda else Icons.Default.GridView,
                                        contentDescription = if (uiState.isGridView) "Switch to List View" else "Switch to Grid View",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                // Sort Menu
                                Box {
                                    IconButton(onClick = { showSortMenu = true }) {
                                        Icon(
                                            imageVector = Icons.Outlined.Sort,
                                            contentDescription = "Sort notes",
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant
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
                                                    Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
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
                                                    Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
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
                                                    Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                                }
                                            }
                                        )
                                    }
                                }

                                // Notes Settings Button
                                IconButton(
                                    onClick = { showNotesSettingsDialog = true },
                                    modifier = Modifier.testTag("notes_settings_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.Settings,
                                        contentDescription = "Notes Settings",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
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
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                fontSize = 14.sp
                            )
                        },
                        leadingIcon = {
                            Icon(
                                Icons.Outlined.Search,
                                contentDescription = "Search",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        },
                        trailingIcon = {
                            if (uiState.searchQuery.isNotEmpty()) {
                                IconButton(onClick = { viewModel.setSearchQuery("") }) {
                                    Icon(
                                        Icons.Default.Close,
                                        contentDescription = "Clear search",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(16.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = RoseGoldPrimary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                            focusedTextColor = MaterialTheme.colorScheme.onBackground,
                            unfocusedTextColor = MaterialTheme.colorScheme.onBackground
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
                        items(categories, key = { it }) { cat ->
                            val isSelected = uiState.selectedCategory == cat
                            val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
                            Box(
                                modifier = Modifier
                                    .then(if (isSelected) Modifier.shadow(8.dp, RoundedCornerShape(14.dp), ambientColor = Color(0x353048F5), spotColor = Color(0x353048F5)) else Modifier)
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(
                                        if (isSelected) notesHorizontalGradient
                                        else if (isDark) androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.surfaceVariant)
                                        else androidx.compose.ui.graphics.SolidColor(Color.White)
                                    )
                                    .then(
                                        if (!isSelected) Modifier.border(
                                            1.dp,
                                            if (isDark) MaterialTheme.colorScheme.outline else Color(0xFFE2E8F0),
                                            RoundedCornerShape(14.dp)
                                        ) else Modifier
                                    )
                                    .clickable { viewModel.setSelectedCategory(cat) }
                                    .padding(horizontal = 16.dp, vertical = 7.dp)
                            ) {
                                Text(
                                    text = cat,
                                    color = if (isSelected) Color.White else if (isDark) MaterialTheme.colorScheme.onSurfaceVariant else Color(0xFF475569),
                                    fontSize = 13.sp,
                                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant,
                        thickness = 1.dp,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            },
            floatingActionButton = {
                if (!uiState.isSelectionMode) {
                    // Floating Action Button: Tap to add note, hold to unlock secret chat
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.padding(bottom = 20.dp, end = 6.dp)
                    ) {
                        // Immediately once selected seconds complete, circular animation loads
                        if (isUnlockingAnimationPlaying) {
                            // Expanding bloom circular ripple
                            Box(
                                modifier = Modifier
                                    .size(66.dp + (48 * unlockAnimProgress.value).dp)
                                    .graphicsLayer {
                                        alpha = (1f - unlockAnimProgress.value).coerceIn(0f, 1f)
                                    }
                                    .border(
                                        width = 3.dp,
                                        brush = androidx.compose.ui.graphics.Brush.sweepGradient(
                                            listOf(DayBlueSecondary, DayBluePrimary, DayBlueTertiary, DayBlueSecondary)
                                        ),
                                        shape = CircleShape
                                    )
                            )

                            // Circular loader ring
                            CircularProgressIndicator(
                                modifier = Modifier.size(66.dp),
                                color = DayBluePrimary,
                                strokeWidth = 3.5.dp,
                                trackColor = DayBluePrimary.copy(alpha = 0.2f)
                            )
                        }

                        Box(
                            modifier = Modifier
                                .testTag("add_note_fab")
                                .size(56.dp)
                                .scale(fabScale)
                                .shadow(6.dp, CircleShape, ambientColor = Color(0x353048F5), spotColor = Color(0x353048F5))
                                .clip(CircleShape)
                                .background(notesHorizontalGradient)
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
                                                // Stealth hold: No progressive ring shown during hold
                                                delay(totalDurationMillis)
                                                if (isActive) {
                                                    unlocked = true
                                                    triggerSecretUnlock()
                                                }
                                            }
                                        } else null

                                        val up = waitForUpOrCancellation()
                                        timerJob?.cancel()
                                        isHoldingFab = false
                                        val elapsed = System.currentTimeMillis() - startTime

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
                            if (isUnlockingAnimationPlaying) {
                                Icon(
                                    imageVector = Icons.Default.LockOpen,
                                    contentDescription = "Secret Unlocked",
                                    tint = Color.White,
                                    modifier = Modifier
                                        .size(28.dp)
                                        .scale(0.85f + (unlockAnimProgress.value * 0.25f))
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Default.Add,
                                    contentDescription = "Add Note",
                                    tint = Color.White,
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                        }
                    }
                }
            },
            containerColor = MaterialTheme.colorScheme.surface,
            contentWindowInsets = WindowInsets.navigationBars
        ) { paddingValues ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                if (uiState.isLoading) {
                    Box(modifier = Modifier.fillMaxSize())
                } else if (uiState.notes.isEmpty()) {
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
                            color = MaterialTheme.colorScheme.onBackground
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        Text(
                            text = "Tap the + button below to create your first note or checklist.",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
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

    // Satisfying secret opening transition overlay once hold completes
    if (isUnlockingAnimationPlaying) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.onBackground.copy(alpha = unlockAnimProgress.value * 0.22f)),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surface,
                    shadowElevation = 14.dp,
                    modifier = Modifier
                        .size(72.dp)
                        .scale(0.82f + (unlockAnimProgress.value * 0.28f))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(notesHorizontalGradient),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.LockOpen,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(34.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
                    shadowElevation = 6.dp,
                    modifier = Modifier.graphicsLayer {
                        alpha = unlockAnimProgress.value
                    }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            color = DayBluePrimary,
                            strokeWidth = 2.2.dp
                        )
                        Text(
                            text = "Opening Cherish...",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }
    }
}
}
}

        if (showNotesSettingsDialog) {
            NotesSettingsDialog(
                isReminderEnabled = uiState.isNoteRemindersEnabled,
                upcomingRemindersCount = uiState.upcomingRemindersCount,
                onToggleReminder = { enabled ->
                    viewModel.setNoteRemindersEnabled(enabled)
                },
                onDismiss = { showNotesSettingsDialog = false }
            )
        }
    }
}

@Composable
fun NotesSettingsDialog(
    isReminderEnabled: Boolean,
    upcomingRemindersCount: Int,
    onToggleReminder: (Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    val isDark = isAppInDark()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "Notes Settings",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    text = "Notification & Reminder Preferences",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // Setting Item: Reminder Notifications
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = if (isDark) Color(0xFF1E293B) else Color(0xFFF8FAFC),
                    border = BorderStroke(1.dp, if (isDark) Color(0xFF334155) else MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            modifier = Modifier.weight(1f),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (isReminderEnabled) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = if (isReminderEnabled) Icons.Default.NotificationsActive else Icons.Default.NotificationsOff,
                                    contentDescription = null,
                                    tint = if (isReminderEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.padding(end = 8.dp)) {
                                Text(
                                    text = "Reminder Notifications",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = if (isReminderEnabled) "Receive alert notifications when scheduled note reminders are due" else "Notifications muted. Scheduled note reminders will not ring.",
                                    fontSize = 11.5.sp,
                                    lineHeight = 16.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Switch(
                            checked = isReminderEnabled,
                            onCheckedChange = onToggleReminder,
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = MaterialTheme.colorScheme.primary
                            )
                        )
                    }
                }

                // Summary info card
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Alarm,
                            contentDescription = null,
                            tint = if (upcomingRemindersCount > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (upcomingRemindersCount > 0) {
                                "$upcomingRemindersCount upcoming reminder(s) scheduled"
                            } else {
                                "No active scheduled reminders"
                            },
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Done", fontWeight = FontWeight.Bold)
            }
        },
        shape = RoundedCornerShape(24.dp),
        containerColor = MaterialTheme.colorScheme.surface
    )
}





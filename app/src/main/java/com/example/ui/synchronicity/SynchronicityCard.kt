package com.example.ui.synchronicity

import android.text.format.DateFormat
import android.view.HapticFeedbackConstants
import android.widget.Toast
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.SyncMoment
import com.example.data.model.SyncPatterns
import com.example.data.model.SyncStatus
import com.example.data.repository.SynchronicityRepository
import com.example.ui.home.HeartbeatBlue
import com.example.ui.home.HeartbeatPink
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

private val TimeTile = Brush.linearGradient(listOf(HeartbeatPink, HeartbeatBlue))
private val NumberTile = Brush.linearGradient(listOf(Color(0xFF7C5CFF), HeartbeatBlue))
private val OwnTile = Brush.linearGradient(listOf(Color(0xFFFF8A65), HeartbeatPink))

internal fun tileBrush(pattern: String, isCustom: Boolean): Brush = when {
    isCustom -> OwnTile
    SyncPatterns.isTime(pattern) -> TimeTile
    else -> NumberTile
}

/** Who saw it: "You", the partner's name, or "Partner" in Private Mode. */
internal fun whoSaw(moment: SyncMoment, myId: String, partnerName: String, privateMode: Boolean): String = when {
    moment.createdBy == myId -> "You"
    privateMode -> "Partner"
    else -> partnerName
}

internal fun timeLabel(millis: Long): String = SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(millis))

/** "Today", "Yesterday" or "Friday, October 3" for a 6 AM day. */
internal fun dayLabel(periodDate: String, today: String): String = when (periodDate) {
    today -> "Today"
    SynchronicityRepository.previousDay(today) -> "Yesterday"
    else -> try {
        val date = SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(periodDate)
        SimpleDateFormat("EEEE, MMMM d", Locale.getDefault()).format(date ?: Date())
    } catch (_: Exception) {
        periodDate
    }
}

/** "Today, 11:11 AM", "Yesterday, 11:11 PM" or "Oct 3, 4:44 PM" for when it was seen. */
internal fun seenLabel(seenAt: Long, today: String): String {
    val day = SynchronicityRepository.dayKey(seenAt)
    val time = timeLabel(seenAt)
    return when (day) {
        today -> "Today, $time"
        SynchronicityRepository.previousDay(today) -> "Yesterday, $time"
        else -> SimpleDateFormat("MMM d", Locale.getDefault()).format(Date(seenAt)) + ", $time"
    }
}

/** The last two times a clock showed this time pattern (11:11 AM and PM), for "I saw it then". */
internal fun recentOccurrences(pattern: String, now: Long = System.currentTimeMillis()): List<Long> {
    if (!SyncPatterns.isTime(pattern)) return emptyList()
    val parts = pattern.split(":").mapNotNull { it.toIntOrNull() }
    if (parts.size != 2) return emptyList()
    val (hour, minute) = parts
    // A 12-hour clock shows 11:11 twice a day, 12:12 at noon and just after midnight
    val hours = when (hour) {
        12 -> listOf(12, 0)
        in 1..11 -> listOf(hour, hour + 12)
        else -> listOf(hour)
    }
    val times = mutableListOf<Long>()
    for (dayOffset in 0 downTo -1) {
        for (h in hours) {
            val cal = Calendar.getInstance().apply {
                timeInMillis = now
                add(Calendar.DAY_OF_YEAR, dayOffset)
                set(Calendar.HOUR_OF_DAY, h)
                set(Calendar.MINUTE, minute)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            if (cal.timeInMillis <= now && now - cal.timeInMillis < 24L * 60 * 60 * 1000) times += cal.timeInMillis
        }
    }
    return times.sortedDescending().take(2)
}

/**
 * Love Synchronicity on Love & Us: today's, this month's and all-time counts, a tile for each
 * number (a tap records it), our latest moments and the way to the history.
 */
@Composable
fun SynchronicityCard(
    viewModel: SynchronicityViewModel,
    partnerName: String,
    isOnScreen: Boolean,
    onOpenHistory: () -> Unit
) {
    val state by viewModel.uiState.collectAsState()
    // Follows this month only while Love & Us is on screen, and notices a new 6 AM day
    LaunchedEffect(isOnScreen) {
        if (!isOnScreen) return@LaunchedEffect
        viewModel.onScreenVisible()
        while (true) {
            delay(60_000)
            viewModel.onTick()
        }
    }
    var recordPattern by remember { mutableStateOf<String?>(null) }
    var showRecord by remember { mutableStateOf(false) }
    var showAddPattern by remember { mutableStateOf(false) }
    var removePattern by remember { mutableStateOf<String?>(null) }
    var editing by remember { mutableStateOf<SyncMoment?>(null) }
    val privateMode = remember { viewModel.isPrivateMode() }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("synchronicity_card"),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                // A soft pink and blue glow in the corners
                .drawBehind {
                    drawCircle(
                        Brush.radialGradient(
                            listOf(HeartbeatPink.copy(alpha = 0.10f), Color.Transparent),
                            center = Offset(size.width, 0f),
                            radius = size.width * 0.6f
                        ),
                        radius = size.width * 0.6f,
                        center = Offset(size.width, 0f)
                    )
                    drawCircle(
                        Brush.radialGradient(
                            listOf(HeartbeatBlue.copy(alpha = 0.08f), Color.Transparent),
                            center = Offset(0f, size.height),
                            radius = size.width * 0.55f
                        ),
                        radius = size.width * 0.55f,
                        center = Offset(0f, size.height)
                    )
                }
                .padding(16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(TimeTile)
                ) {
                    Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("Love Synchronicity", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                    Text("Numbers that find us", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (state.streak > 0) {
                    Surface(shape = RoundedCornerShape(50), color = HeartbeatPink.copy(alpha = 0.12f)) {
                        Text(
                            "🔥 ${state.streak} ${if (state.streak == 1) "day" else "days"}",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = HeartbeatPink,
                            modifier = Modifier
                                .padding(horizontal = 10.dp, vertical = 5.dp)
                                .semantics { contentDescription = "Synchronicity streak: ${state.streak} days in a row" }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                SyncStat("Today", state.todayCount, Modifier.weight(1f))
                SyncStat("This month", state.monthCount, Modifier.weight(1f))
                SyncStat("All time", state.allTimeCount, Modifier.weight(1f))
            }

            Spacer(modifier = Modifier.height(14.dp))
            Text(
                "Saw one? Tap it",
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.testTag("synchronicity_tiles")
            ) {
                items(state.patterns, key = { it }) { pattern ->
                    val isCustom = pattern in state.customPatterns
                    PatternTile(
                        pattern = pattern,
                        count = state.monthByPattern[pattern] ?: 0,
                        brush = tileBrush(pattern, isCustom),
                        onClick = {
                            recordPattern = pattern
                            showRecord = true
                        },
                        onLongClick = if (isCustom) ({ removePattern = pattern }) else null
                    )
                }
                if (state.customPatterns.size < SyncPatterns.MAX_CUSTOM) {
                    item(key = "add") {
                        Surface(
                            shape = RoundedCornerShape(18.dp),
                            color = Color.Transparent,
                            border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.outlineVariant),
                            onClick = { showAddPattern = true },
                            modifier = Modifier
                                .size(width = 74.dp, height = 86.dp)
                                .semantics { contentDescription = "Add our own number" }
                                .testTag("synchronicity_add_pattern")
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                                Icon(Icons.Default.Add, contentDescription = null, tint = HeartbeatPink)
                                Text("Ours", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }

            when (state.status) {
                SyncStatus.NOT_ALLOWED -> {
                    Spacer(modifier = Modifier.height(12.dp))
                    Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.errorContainer) {
                        Text(
                            SynchronicityRepository.NOT_ALLOWED_MESSAGE,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                }
                SyncStatus.NO_COUPLE -> {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("Log in together to share your moments.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                else -> Unit
            }

            // Our latest moments this month
            val recent = state.monthMoments.take(3)
            Spacer(modifier = Modifier.height(12.dp))
            if (recent.isEmpty()) {
                Text(
                    if (state.status == SyncStatus.LOADING) "Gathering our moments…"
                    else "Noticed 11:11 or 444 together? Tap the number to keep the moment ✨",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 4.dp)
                )
            } else {
                recent.forEach { moment ->
                    MomentRow(
                        moment = moment,
                        who = whoSaw(moment, state.myId, partnerName, privateMode),
                        whenLabel = seenLabel(moment.seenAt, state.today),
                        canEdit = moment.createdBy == state.myId,
                        onClick = { editing = moment }
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = {
                        recordPattern = null
                        showRecord = true
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = HeartbeatPink, contentColor = Color.White),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("synchronicity_record")
                ) {
                    Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Record moment", fontWeight = FontWeight.SemiBold)
                }
                OutlinedButton(
                    onClick = onOpenHistory,
                    modifier = Modifier.testTag("synchronicity_history")
                ) {
                    Icon(Icons.Default.History, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("History")
                }
            }
        }
    }

    if (showRecord) {
        RecordMomentSheet(
            viewModel = viewModel,
            initialPattern = recordPattern,
            editing = null,
            onDismiss = { showRecord = false }
        )
    }
    editing?.let { moment ->
        RecordMomentSheet(
            viewModel = viewModel,
            initialPattern = moment.pattern,
            editing = moment,
            onDismiss = { editing = null }
        )
    }
    if (showAddPattern) {
        AddPatternDialog(viewModel = viewModel, onDismiss = { showAddPattern = false })
    }
    removePattern?.let { pattern ->
        AlertDialog(
            onDismissRequest = { removePattern = null },
            title = { Text("Remove $pattern?") },
            text = { Text("It leaves the tiles for both of you. Moments already recorded with it stay.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.removePattern(pattern)
                    removePattern = null
                }) { Text("Remove") }
            },
            dismissButton = { TextButton(onClick = { removePattern = null }) { Text("Keep") } }
        )
    }
}

@Composable
internal fun SyncStat(label: String, value: Int, modifier: Modifier = Modifier) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.07f),
        modifier = modifier.semantics(mergeDescendants = true) { contentDescription = "$label: $value" }
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(vertical = 10.dp, horizontal = 6.dp)
        ) {
            AnimatedContent(
                targetState = value,
                transitionSpec = {
                    (slideInVertically(tween(250)) { it } + fadeIn(tween(250))) togetherWith
                        (slideOutVertically(tween(200)) { -it } + fadeOut(tween(200)))
                },
                label = "sync_stat"
            ) { shown ->
                Text("$shown", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
            }
            Text(label, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun PatternTile(
    pattern: String,
    count: Int,
    brush: Brush,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)?
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(width = 74.dp, height = 86.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(brush)
            .combinedClickable(
                role = Role.Button,
                onClickLabel = "Record $pattern",
                onClick = onClick,
                onLongClick = onLongClick
            )
            .semantics(mergeDescendants = true) {
                contentDescription = "$pattern, seen $count ${if (count == 1) "time" else "times"} this month"
            }
            .testTag("synchronicity_tile_$pattern")
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                pattern,
                fontSize = if (pattern.length > 5) 15.sp else 19.sp,
                fontWeight = FontWeight.Black,
                color = Color.White,
                maxLines = 1
            )
            Spacer(modifier = Modifier.height(6.dp))
            Surface(shape = RoundedCornerShape(50), color = Color.White.copy(alpha = 0.22f)) {
                Text(
                    if (count > 0) "×$count" else "tap",
                    fontSize = 10.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                )
            }
        }
    }
}

@Composable
internal fun MomentRow(
    moment: SyncMoment,
    who: String,
    whenLabel: String,
    canEdit: Boolean,
    onClick: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .then(if (canEdit) Modifier.combinedClickableCompat(onClick) else Modifier)
            .padding(vertical = 6.dp, horizontal = 2.dp)
            .semantics(mergeDescendants = true) {
                contentDescription = "${moment.pattern}, seen by $who, $whenLabel" +
                    (if (moment.note.isNotBlank()) ". ${moment.note}" else "")
            }
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .clip(RoundedCornerShape(10.dp))
                .background(tileBrush(moment.pattern, isCustom = moment.pattern !in SyncPatterns.defaults))
                .padding(horizontal = 9.dp, vertical = 6.dp)
                .widthIn(min = 40.dp)
        ) {
            Text(moment.pattern, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White)
        }
        Spacer(modifier = Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                "$who · $whenLabel",
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1
            )
            if (moment.note.isNotBlank()) {
                Text(
                    moment.note,
                    fontSize = 12.5.sp,
                    fontStyle = FontStyle.Italic,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2
                )
            }
        }
        if (moment.isPending) {
            Icon(
                Icons.Default.CloudUpload,
                contentDescription = "Waiting to sync",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp)
            )
        }
        if (canEdit) {
            Spacer(modifier = Modifier.width(4.dp))
            Icon(
                Icons.Default.Edit,
                contentDescription = "Edit",
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                modifier = Modifier.size(15.dp)
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
private fun Modifier.combinedClickableCompat(onClick: () -> Unit): Modifier =
    combinedClickable(role = Role.Button, onClickLabel = "Edit", onClick = onClick)

/**
 * Records a moment (or changes one of mine): the number, when it was seen (just now, the last time
 * a clock showed it, or any earlier day and time) and an optional note.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun RecordMomentSheet(
    viewModel: SynchronicityViewModel,
    initialPattern: String?,
    editing: SyncMoment?,
    onDismiss: () -> Unit,
    onChanged: () -> Unit = {}
) {
    val context = LocalContext.current
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val state by viewModel.uiState.collectAsState()
    // One id for this sheet: Save pressed twice, or again after an error, writes the same moment
    val momentId = remember { editing?.id ?: viewModel.newMomentId() }
    var pattern by remember { mutableStateOf(editing?.pattern ?: initialPattern) }
    var note by remember { mutableStateOf(editing?.note.orEmpty()) }
    // Null means "just now" (taken when Save is pressed)
    var seenAt by remember { mutableStateOf(editing?.seenAt) }
    var isSaving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var showDatePicker by remember { mutableStateOf(false) }
    var pickedDateUtc by remember { mutableStateOf<Long?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(start = 20.dp, end = 20.dp, bottom = 24.dp)
                .testTag("synchronicity_record_sheet")
        ) {
            Text(
                if (editing == null) "Record a moment" else "Edit moment",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                if (editing == null) "When a number finds you, keep it here for both of you."
                else "Change the number, when you saw it or the note.",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(16.dp))
            Text("Which number?", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Spacer(modifier = Modifier.height(8.dp))
            val patterns = remember(state.patterns, editing) {
                // A moment with a number we've since removed keeps it on the list while editing
                if (editing != null && editing.pattern !in state.patterns) state.patterns + editing.pattern else state.patterns
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                patterns.forEach { option ->
                    val selected = option == pattern
                    FilterChip(
                        selected = selected,
                        onClick = { pattern = option },
                        label = { Text(option, fontWeight = FontWeight.Bold) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = HeartbeatPink,
                            selectedLabelColor = Color.White
                        ),
                        modifier = Modifier.testTag("synchronicity_pick_$option")
                    )
                }
            }

            pattern?.let { chosen ->
                Spacer(modifier = Modifier.height(12.dp))
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = HeartbeatPink.copy(alpha = 0.08f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text("✨ $chosen · ${SyncPatterns.meaning(chosen)}", fontSize = 14.sp, fontWeight = FontWeight.Medium)
                        Text(
                            "A sweet meaning, just for fun — not a prediction.",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            Text("When did you see it?", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Spacer(modifier = Modifier.height(8.dp))
            val quickTimes = remember(pattern) { pattern?.let { recentOccurrences(it) }.orEmpty() }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = seenAt == null,
                    onClick = { seenAt = null },
                    label = { Text("Just now") },
                    modifier = Modifier.testTag("synchronicity_when_now")
                )
                quickTimes.forEach { time ->
                    FilterChip(
                        selected = seenAt == time,
                        onClick = { seenAt = time },
                        label = { Text(seenLabel(time, state.today)) }
                    )
                }
                // A time picked earlier (or the one being edited) that isn't a quick choice
                val picked = seenAt
                if (picked != null && picked !in quickTimes) {
                    FilterChip(selected = true, onClick = { showDatePicker = true }, label = { Text(seenLabel(picked, state.today)) })
                }
                AssistChip(
                    onClick = { showDatePicker = true },
                    label = { Text("Earlier…") },
                    modifier = Modifier.testTag("synchronicity_when_pick")
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
            OutlinedTextField(
                value = note,
                onValueChange = { note = it.take(SyncPatterns.NOTE_MAX) },
                label = { Text("Note (optional)") },
                placeholder = { Text("Where were you? Who were you thinking of?") },
                supportingText = { Text("${note.length}/${SyncPatterns.NOTE_MAX}") },
                minLines = 2,
                maxLines = 5,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("synchronicity_note")
            )

            error?.let {
                Spacer(modifier = Modifier.height(6.dp))
                Text(it, color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
            }

            Spacer(modifier = Modifier.height(12.dp))
            Button(
                onClick = {
                    val chosen = pattern ?: return@Button
                    if (isSaving) return@Button
                    isSaving = true
                    error = null
                    scope.launch {
                        val time = seenAt ?: System.currentTimeMillis()
                        if (editing == null) {
                            when (val result = viewModel.record(momentId, chosen, note, time)) {
                                SynchronicityRepository.SaveResult.Saved -> {
                                    view.performHapticFeedback(
                                        if (android.os.Build.VERSION.SDK_INT >= 30) HapticFeedbackConstants.CONFIRM
                                        else HapticFeedbackConstants.VIRTUAL_KEY
                                    )
                                    onChanged()
                                    onDismiss()
                                }
                                SynchronicityRepository.SaveResult.SavedOffline -> {
                                    Toast.makeText(context, "Saved on this phone. It syncs when you're back online.", Toast.LENGTH_LONG).show()
                                    onChanged()
                                    onDismiss()
                                }
                                is SynchronicityRepository.SaveResult.Failed -> {
                                    error = result.message
                                    isSaving = false
                                }
                            }
                        } else if (viewModel.update(editing, chosen, note, time)) {
                            onChanged()
                            onDismiss()
                        } else {
                            error = "Couldn't save the change. Try again."
                            isSaving = false
                        }
                    }
                },
                enabled = pattern != null && !isSaving,
                colors = ButtonDefaults.buttonColors(containerColor = HeartbeatPink, contentColor = Color.White),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .testTag("synchronicity_save")
            ) {
                if (isSaving) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = Color.White)
                } else {
                    Text(
                        when {
                            pattern == null -> "Pick a number"
                            editing == null -> "Save $pattern"
                            else -> "Save changes"
                        },
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
            if (editing != null) {
                TextButton(
                    onClick = { confirmDelete = true },
                    enabled = !isSaving,
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .testTag("synchronicity_delete")
                ) { Text("Delete this moment", color = MaterialTheme.colorScheme.error) }
            }
        }
    }

    if (showDatePicker) {
        // Today (by the calendar) and earlier only
        val todayUtc = remember { utcMidnight(Calendar.getInstance()) }
        val dateState = rememberDatePickerState(
            initialSelectedDateMillis = seenAt?.let { utcMidnight(Calendar.getInstance().apply { timeInMillis = it }) } ?: todayUtc,
            selectableDates = object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long) = utcTimeMillis <= todayUtc
                override fun isSelectableYear(year: Int) = year <= Calendar.getInstance().get(Calendar.YEAR)
            }
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    pickedDateUtc = dateState.selectedDateMillis
                    showDatePicker = false
                }) { Text("Next") }
            },
            dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text("Cancel") } }
        ) {
            DatePicker(state = dateState, title = { Text("The day you saw it", modifier = Modifier.padding(start = 24.dp, top = 16.dp)) })
        }
    }
    pickedDateUtc?.let { dateUtc ->
        val start = Calendar.getInstance().apply { timeInMillis = seenAt ?: System.currentTimeMillis() }
        val timeState = rememberTimePickerState(
            initialHour = start.get(Calendar.HOUR_OF_DAY),
            initialMinute = start.get(Calendar.MINUTE),
            is24Hour = DateFormat.is24HourFormat(context)
        )
        AlertDialog(
            onDismissRequest = { pickedDateUtc = null },
            title = { Text("The time you saw it") },
            text = { TimePicker(state = timeState) },
            confirmButton = {
                TextButton(onClick = {
                    val utc = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply { timeInMillis = dateUtc }
                    val chosen = Calendar.getInstance().apply {
                        set(utc.get(Calendar.YEAR), utc.get(Calendar.MONTH), utc.get(Calendar.DAY_OF_MONTH), timeState.hour, timeState.minute, 0)
                        set(Calendar.MILLISECOND, 0)
                    }.timeInMillis
                    if (chosen > System.currentTimeMillis()) {
                        error = "That time hasn't come yet. Pick an earlier one."
                    } else {
                        seenAt = chosen
                        error = null
                    }
                    pickedDateUtc = null
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { pickedDateUtc = null }) { Text("Cancel") } }
        )
    }
    if (confirmDelete && editing != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete this moment?") },
            text = { Text("${editing.pattern} will be removed for both of you.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    isSaving = true
                    scope.launch {
                        if (viewModel.delete(editing)) {
                            onChanged()
                            onDismiss()
                        } else {
                            error = "Couldn't delete it. Try again."
                            isSaving = false
                        }
                    }
                }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Keep") } }
        )
    }
}

/** The picker's way of naming a calendar day: its midnight in UTC. */
private fun utcMidnight(local: Calendar): Long =
    Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
        clear()
        set(local.get(Calendar.YEAR), local.get(Calendar.MONTH), local.get(Calendar.DAY_OF_MONTH))
    }.timeInMillis

@Composable
internal fun AddPatternDialog(viewModel: SynchronicityViewModel, onDismiss: () -> Unit) {
    var input by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add our own number") },
        text = {
            Column {
                Text(
                    "A number that means something to you two: 2 to 6 digits (like 1234) or a time (like 07:07).",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(10.dp))
                OutlinedTextField(
                    value = input,
                    onValueChange = {
                        input = it.filter { c -> c.isDigit() || c == ':' || c == '.' }.take(6)
                        error = null
                    },
                    singleLine = true,
                    isError = error != null,
                    supportingText = { error?.let { Text(it) } },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
                    textStyle = LocalTextStyle.current.copy(textAlign = TextAlign.Center, fontWeight = FontWeight.Bold, fontSize = 22.sp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("synchronicity_new_pattern")
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                error = viewModel.addPattern(input)
                if (error == null) onDismiss()
            }) { Text("Add") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

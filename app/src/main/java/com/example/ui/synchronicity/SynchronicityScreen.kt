package com.example.ui.synchronicity

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.SyncMoment
import com.example.data.model.SyncPatterns
import com.example.data.repository.SynchronicityRepository
import java.text.DateFormatSymbols
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/** "yyyy-MM" moved by [offset] months. */
private fun shiftMonth(month: String, offset: Int): String {
    val cal = Calendar.getInstance().apply {
        clear()
        set(month.take(4).toInt(), month.substring(5, 7).toInt() - 1, 1)
        add(Calendar.MONTH, offset)
    }
    return String.format(Locale.US, "%04d-%02d", cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1)
}

private fun monthTitle(month: String): String {
    val cal = Calendar.getInstance().apply {
        clear()
        set(month.take(4).toInt(), month.substring(5, 7).toInt() - 1, 1)
    }
    return SimpleDateFormat("MMMM yyyy", Locale.getDefault()).format(cal.time)
}

/**
 * Love Synchronicity history: our counts and streak, a calendar for each month (back to our first
 * moment), the month's numbers and every moment grouped by day. One month is read at a time.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SynchronicityScreen(
    viewModel: SynchronicityViewModel,
    partnerName: String,
    onNavigateBack: () -> Unit
) {
    val state by viewModel.uiState.collectAsState()
    LaunchedEffect(Unit) { viewModel.onScreenVisible() }
    val privateMode = remember { viewModel.isPrivateMode() }
    val currentMonth = state.today.take(7).ifBlank { SynchronicityRepository.currentMonth() }

    var month by rememberSaveable { mutableStateOf(SynchronicityRepository.currentMonth()) }
    var firstMonth by remember { mutableStateOf<String?>(null) }
    var selectedDay by remember { mutableStateOf<String?>(null) }
    var editing by remember { mutableStateOf<SyncMoment?>(null) }
    var showRecord by remember { mutableStateOf(false) }
    // Earlier months, read once each while this screen is open
    val loadedMonths = remember { mutableStateMapOf<String, List<SyncMoment>>() }
    var isLoading by remember { mutableStateOf(false) }
    var loadFailed by remember { mutableStateOf(false) }
    var reload by remember { mutableIntStateOf(0) }

    LaunchedEffect(reload) { firstMonth = viewModel.firstMonth() }
    val isCurrent = month == currentMonth
    LaunchedEffect(month, reload) {
        selectedDay = null
        if (isCurrent || month in loadedMonths) return@LaunchedEffect
        isLoading = true
        loadFailed = false
        val items = viewModel.loadMonth(month)
        if (items != null) loadedMonths[month] = items else loadFailed = true
        isLoading = false
    }
    val moments = if (isCurrent) state.monthMoments else loadedMonths[month].orEmpty()
    val byDay = remember(moments) { moments.groupBy { it.periodDate } }
    val canGoBack = month > (firstMonth ?: currentMonth)
    val canGoForward = month < currentMonth
    // A change can move a moment between months: earlier months are read again
    val onChanged: () -> Unit = {
        loadedMonths.clear()
        reload++
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Love Synchronicity", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showRecord = true },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                icon = { Icon(Icons.Default.AutoAwesome, contentDescription = null) },
                text = { Text("Record moment") },
                modifier = Modifier.testTag("synchronicity_history_record")
            )
        }
    ) { padding ->
        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .testTag("synchronicity_history_list")
        ) {
            item(key = "stats") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    SyncStat("Today", state.todayCount, Modifier.weight(1f))
                    SyncStat("This month", state.monthCount, Modifier.weight(1f))
                    SyncStat("All time", state.allTimeCount, Modifier.weight(1f))
                    SyncStat("Streak", state.streak, Modifier.weight(1f))
                }
            }

            item(key = "calendar") {
                Card(
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = { month = shiftMonth(month, -1) }, enabled = canGoBack) {
                                Icon(Icons.Default.ChevronLeft, contentDescription = "Previous month")
                            }
                            Text(
                                monthTitle(month),
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.weight(1f),
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                            IconButton(onClick = { month = shiftMonth(month, 1) }, enabled = canGoForward) {
                                Icon(Icons.Default.ChevronRight, contentDescription = "Next month")
                            }
                        }
                        MonthCalendar(
                            month = month,
                            countsByDay = remember(byDay) { byDay.mapValues { it.value.size } },
                            today = state.today,
                            selectedDay = selectedDay,
                            onSelectDay = { selectedDay = it }
                        )
                    }
                }
            }

            // This month's numbers, most seen first
            if (moments.isNotEmpty()) {
                item(key = "breakdown") {
                    val counts = remember(moments) {
                        moments.groupingBy { it.pattern }.eachCount().entries.sortedByDescending { it.value }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                        counts.take(4).forEach { (pattern, count) ->
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(tileBrush(pattern, isCustom = pattern !in SyncPatterns.defaults))
                                    .padding(horizontal = 10.dp, vertical = 6.dp)
                            ) {
                                Text("$pattern ×$count", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            when {
                isLoading -> item(key = "loading") {
                    Box(modifier = Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                    }
                }
                loadFailed -> item(key = "failed") {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                        Text("Couldn't load ${monthTitle(month)}.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        TextButton(onClick = { reload++ }) { Text("Try again") }
                    }
                }
                moments.isEmpty() -> item(key = "empty") {
                    Text(
                        if (isCurrent) "No moments yet this month. When a number finds you, record it."
                        else "No moments in ${monthTitle(month)}.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 14.sp,
                        modifier = Modifier.padding(vertical = 12.dp)
                    )
                }
                else -> {
                    val days = byDay.keys.sortedDescending().filter { selectedDay == null || it == selectedDay }
                    days.forEach { day ->
                        item(key = "day_$day") {
                            Text(
                                dayLabel(day, state.today),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                        items(byDay[day].orEmpty(), key = { it.id }) { moment ->
                            MomentRow(
                                moment = moment,
                                who = whoSaw(moment, state.myId, partnerName, privateMode),
                                whenLabel = timeLabel(moment.seenAt),
                                canEdit = moment.createdBy == state.myId,
                                onClick = { editing = moment }
                            )
                        }
                    }
                }
            }

            item(key = "meanings") {
                Card(
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.06f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("What the numbers might whisper", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Text(
                            "Sweet meanings, just for fun — not predictions.",
                            fontSize = 11.5.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        state.patterns.forEach { pattern ->
                            Row {
                                Text(pattern, fontWeight = FontWeight.Bold, fontSize = 13.sp, modifier = Modifier.width(58.dp))
                                Text(SyncPatterns.meaning(pattern), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }
    }

    if (showRecord) {
        RecordMomentSheet(
            viewModel = viewModel,
            initialPattern = null,
            editing = null,
            onDismiss = { showRecord = false },
            onChanged = onChanged
        )
    }
    editing?.let { moment ->
        RecordMomentSheet(
            viewModel = viewModel,
            initialPattern = moment.pattern,
            editing = moment,
            onDismiss = { editing = null },
            onChanged = onChanged
        )
    }
}

/** A month grid: days with moments glow blue (deeper with more), today has a ring; tap to see that day. */
@Composable
private fun MonthCalendar(
    month: String,
    countsByDay: Map<String, Int>,
    today: String,
    selectedDay: String?,
    onSelectDay: (String?) -> Unit
) {
    val first = remember(month) {
        Calendar.getInstance().apply {
            clear()
            set(month.take(4).toInt(), month.substring(5, 7).toInt() - 1, 1)
        }
    }
    val daysInMonth = first.getActualMaximum(Calendar.DAY_OF_MONTH)
    val firstDayOfWeek = first.firstDayOfWeek
    val leading = (first.get(Calendar.DAY_OF_WEEK) - firstDayOfWeek + 7) % 7
    val weekdayNames = remember {
        val names = DateFormatSymbols.getInstance(Locale.getDefault()).shortWeekdays
        List(7) { i -> names[(firstDayOfWeek - 1 + i) % 7 + 1].take(2) }
    }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(modifier = Modifier.fillMaxWidth()) {
            weekdayNames.forEach { name ->
                Text(
                    name,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    modifier = Modifier.weight(1f)
                )
            }
        }
        val cells: List<Int?> = List(leading) { null } + (1..daysInMonth).toList()
        cells.chunked(7).forEach { week ->
            Row(modifier = Modifier.fillMaxWidth()) {
                week.forEach { day ->
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .weight(1f)
                            .aspectRatio(1f)
                            .padding(2.dp)
                    ) {
                        if (day != null) {
                            val key = String.format(Locale.US, "%s-%02d", month, day)
                            val count = countsByDay[key] ?: 0
                            val isToday = key == today
                            val isSelected = key == selectedDay
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clip(CircleShape)
                                    .background(
                                        if (count > 0) MaterialTheme.colorScheme.primary.copy(alpha = (0.22f + 0.16f * minOf(count, 4)).coerceAtMost(0.86f))
                                        else Color.Transparent
                                    )
                                    .then(
                                        when {
                                            isSelected -> Modifier.border(2.5.dp, MaterialTheme.colorScheme.primary, CircleShape)
                                            isToday -> Modifier.border(1.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.45f), CircleShape)
                                            else -> Modifier
                                        }
                                    )
                                    .then(
                                        if (count > 0) Modifier.clickable { onSelectDay(if (isSelected) null else key) }
                                        else Modifier
                                    )
                                    .semantics {
                                        contentDescription = "$day: $count ${if (count == 1) "moment" else "moments"}"
                                    }
                            ) {
                                Text(
                                    "$day",
                                    fontSize = 12.sp,
                                    fontWeight = if (count > 0 || isToday) FontWeight.Bold else FontWeight.Normal,
                                    color = if (count >= 2) Color.White else MaterialTheme.colorScheme.onSurface
                                )
                            }
                        } else {
                            Spacer(modifier = Modifier.fillMaxSize())
                        }
                    }
                }
                // Keep the last week's days the same width as the others
                repeat(7 - week.size) { Spacer(modifier = Modifier.weight(1f)) }
            }
        }
    }
}

package com.example.ui.dates

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.DateCategory
import com.example.data.model.ImportantDate
import com.example.ui.theme.ChampagneSecondary
import com.example.ui.theme.RoseGoldPrimary
import com.example.ui.theme.appGradientShadow
import com.example.ui.theme.appHorizontalGradient
import java.util.Calendar
import java.util.TimeZone

/** Categories in the order they're offered when adding a date. */
private val CATEGORY_ORDER = listOf(
    DateCategory.ANNIVERSARY,
    DateCategory.BIRTHDAY,
    DateCategory.FIRST_DATE,
    DateCategory.PLACE,
    DateCategory.FAMILY,
    DateCategory.MILESTONE,
    DateCategory.CUSTOM
)

/** Quick starts for an empty list. */
private val SUGGESTIONS = listOf(
    "Our anniversary" to DateCategory.ANNIVERSARY,
    "Our meeting" to DateCategory.FIRST_DATE,
    "Birthday" to DateCategory.BIRTHDAY,
    "Family birthday" to DateCategory.BIRTHDAY,
    "A place we went" to DateCategory.PLACE
)

/**
 * Our dates: birthdays (ours and family's), anniversaries, meetings, places we went...
 * Shared by both phones. Coming-up dates are shown when the app is opened (a week ahead).
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ImportantDatesScreen(
    viewModel: ImportantDatesViewModel,
    onNavigateBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    var addDialogFor by remember { mutableStateOf<Pair<String, DateCategory>?>(null) }
    var pendingDelete by remember { mutableStateOf<ImportantDate?>(null) }

    // Coming up (soonest first), then one-time dates that have passed
    val (upcoming, past) = remember(uiState.dates) {
        val next = uiState.dates.mapNotNull { DateReminders.next(it) }.sortedBy { it.daysUntil }
        val nextIds = next.map { it.date.id }.toSet()
        next to uiState.dates.filter { it.id !in nextIds }.sortedByDescending { it.dateMillis }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Our Dates",
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier.testTag("dates_back_button")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        floatingActionButton = {
            Box(
                modifier = Modifier
                    .appGradientShadow(CircleShape)
                    .clip(CircleShape)
                    .background(appHorizontalGradient())
                    .size(56.dp)
                    .clickable { addDialogFor = "" to DateCategory.ANNIVERSARY }
                    .testTag("add_date_fab"),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Add a date",
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
                )
            }
        },
        containerColor = MaterialTheme.colorScheme.surface
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Text(
                    text = "Birthdays, anniversaries and our special days. When one is a week away, you'll see it as soon as you open the app.",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp, bottom = 4.dp)
                )
            }

            if (uiState.dates.isEmpty()) {
                item {
                    EmptyDates(onPick = { title, category -> addDialogFor = title to category })
                }
            }

            items(upcoming, key = { it.date.id }) { next ->
                DateCard(
                    title = next.date.title,
                    category = next.date.getTypedCategory(),
                    dateMillis = next.date.dateMillis,
                    repeats = next.date.repeatAnnually,
                    notes = next.date.notes,
                    pill = DateReminders.whenLabel(next.daysUntil),
                    pillHighlighted = next.daysUntil <= 1,
                    extra = DateReminders.yearsLabel(next),
                    onDelete = { pendingDelete = next.date }
                )
            }

            if (past.isNotEmpty()) {
                item {
                    Text(
                        text = "Memories",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 10.dp)
                    )
                }
                items(past, key = { it.id }) { date ->
                    val daysAgo = ((System.currentTimeMillis() - date.dateMillis) / 86_400_000L).toInt()
                    DateCard(
                        title = date.title,
                        category = date.getTypedCategory(),
                        dateMillis = date.dateMillis,
                        repeats = false,
                        notes = date.notes,
                        pill = if (daysAgo <= 1) "Yesterday" else "$daysAgo days ago",
                        pillHighlighted = false,
                        extra = null,
                        onDelete = { pendingDelete = date }
                    )
                }
            }

            item { Spacer(modifier = Modifier.height(88.dp)) }
        }
    }

    addDialogFor?.let { (title, category) ->
        AddDateDialog(
            initialTitle = title,
            initialCategory = category,
            onDismiss = { addDialogFor = null },
            onAdd = { newTitle, millis, newCategory, notes, repeats ->
                viewModel.addDate(newTitle, millis, newCategory, notes, repeats)
                addDialogFor = null
            }
        )
    }

    pendingDelete?.let { date ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Delete this date?") },
            text = { Text("\"${date.title}\" will be removed for both of you.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteDate(date.id)
                    pendingDelete = null
                }) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text("Cancel") }
            }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EmptyDates(onPick: (String, DateCategory) -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = RoseGoldPrimary.copy(alpha = 0.08f))
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Text(text = "📅", fontSize = 30.sp)
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "No dates yet",
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "Start with one of these:",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(12.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SUGGESTIONS.forEach { (title, category) ->
                    AssistChip(
                        onClick = { onPick(title, category) },
                        label = { Text("${DateReminders.emoji(category)}  $title", fontSize = 13.sp) }
                    )
                }
            }
        }
    }
}

@Composable
private fun DateCard(
    title: String,
    category: DateCategory,
    dateMillis: Long,
    repeats: Boolean,
    notes: String?,
    pill: String,
    pillHighlighted: Boolean,
    extra: String?,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, top = 14.dp, bottom = 14.dp, end = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(
                        Brush.radialGradient(listOf(RoseGoldPrimary.copy(alpha = 0.2f), ChampagneSecondary.copy(alpha = 0.2f))),
                        CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(text = DateReminders.emoji(category), fontSize = 24.sp)
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = DateReminders.formatDate(dateMillis) + if (repeats) "  ·  every year" else "",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (!notes.isNullOrBlank()) {
                    Text(
                        text = notes,
                        fontSize = 12.sp,
                        color = RoseGoldPrimary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Column(horizontalAlignment = Alignment.End) {
                Surface(
                    shape = RoundedCornerShape(50),
                    color = if (pillHighlighted) RoseGoldPrimary else RoseGoldPrimary.copy(alpha = 0.13f)
                ) {
                    Text(
                        text = pill,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (pillHighlighted) Color.White else RoseGoldPrimary,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                    )
                }
                if (extra != null) {
                    Text(
                        text = extra,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 3.dp, end = 4.dp)
                    )
                }
            }

            IconButton(onClick = onDelete, modifier = Modifier.size(36.dp)) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "Delete",
                    tint = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

/** The picker works in UTC days; dates are stored as the local midnight of that day. */
private fun utcDayToLocalMidnight(utcMillis: Long): Long {
    val utc = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply { timeInMillis = utcMillis }
    return Calendar.getInstance().apply {
        clear()
        set(utc.get(Calendar.YEAR), utc.get(Calendar.MONTH), utc.get(Calendar.DAY_OF_MONTH))
    }.timeInMillis
}

private fun localDayToUtcMidnight(localMillis: Long): Long {
    val local = Calendar.getInstance().apply { timeInMillis = localMillis }
    return Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
        clear()
        set(local.get(Calendar.YEAR), local.get(Calendar.MONTH), local.get(Calendar.DAY_OF_MONTH))
    }.timeInMillis
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun AddDateDialog(
    initialTitle: String,
    initialCategory: DateCategory,
    onDismiss: () -> Unit,
    onAdd: (String, Long, DateCategory, String?, Boolean) -> Unit
) {
    var title by remember { mutableStateOf(initialTitle) }
    var notes by remember { mutableStateOf("") }
    var category by remember { mutableStateOf(initialCategory) }
    var dateMillis by remember { mutableStateOf<Long?>(null) }
    var repeats by remember { mutableStateOf(true) }
    var showPicker by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add a date") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("What's the day?") },
                    placeholder = { Text("e.g. Mom's birthday") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("date_title_field")
                )

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    CATEGORY_ORDER.forEach { option ->
                        FilterChip(
                            selected = category == option,
                            onClick = { category = option },
                            label = {
                                Text("${DateReminders.emoji(option)} ${DateReminders.label(option)}", fontSize = 12.sp)
                            }
                        )
                    }
                }

                OutlinedButton(
                    onClick = { showPicker = true },
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("date_pick_button")
                ) {
                    Icon(Icons.Default.CalendarMonth, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(dateMillis?.let { DateReminders.formatDate(it) } ?: "Choose the date")
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { repeats = !repeats }
                        .padding(vertical = 2.dp)
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Every year", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                        Text(
                            "Reminds you every year on this day",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(checked = repeats, onCheckedChange = { repeats = it })
                }

                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Note (optional)") },
                    placeholder = { Text("A plan, a gift idea, a memory...") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val day = dateMillis ?: return@Button
                    onAdd(title.trim(), day, category, notes.trim().ifBlank { null }, repeats)
                },
                enabled = title.isNotBlank() && dateMillis != null,
                modifier = Modifier.testTag("date_save_button")
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )

    if (showPicker) {
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = localDayToUtcMidnight(dateMillis ?: System.currentTimeMillis())
        )
        DatePickerDialog(
            onDismissRequest = { showPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let { dateMillis = utcDayToLocalMidnight(it) }
                    showPicker = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showPicker = false }) { Text("Cancel") }
            }
        ) {
            DatePicker(state = pickerState)
        }
    }
}

package com.example.ui.dates

import androidx.compose.foundation.background
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.DateCategory
import com.example.data.model.ImportantDate
import com.example.ui.theme.ChampagneSecondary
import com.example.ui.theme.GoldMilestone
import com.example.ui.theme.HeartRed
import com.example.ui.theme.RoseGoldPrimary
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImportantDatesScreen(
    viewModel: ImportantDatesViewModel,
    onNavigateBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    var showAddDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Milestones & Dates",
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
                    containerColor = Color.White
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddDialog = true },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                shape = CircleShape,
                modifier = Modifier.testTag("add_date_fab")
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add Important Date")
            }
        },
        containerColor = Color.White
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            items(uiState.dates, key = { it.id }) { dateItem ->
                DateCard(
                    dateItem = dateItem,
                    onDelete = { viewModel.deleteDate(dateItem.id) }
                )
            }
            item {
                Spacer(modifier = Modifier.height(72.dp))
            }
        }
    }

    if (showAddDialog) {
        AddDateDialog(
            onDismiss = { showAddDialog = false },
            onAdd = { title, timeMillis, cat, notes ->
                viewModel.addDate(title, timeMillis, cat, notes)
                showAddDialog = false
            }
        )
    }
}

@Composable
fun DateCard(
    dateItem: ImportantDate,
    onDelete: () -> Unit
) {
    val now = System.currentTimeMillis()
    val diffDays = ((dateItem.dateMillis - now) / (1000 * 60 * 60 * 24)).toInt()
    val isFuture = diffDays >= 0

    val icon = when (dateItem.getTypedCategory()) {
        DateCategory.ANNIVERSARY -> "💍"
        DateCategory.BIRTHDAY -> "🎂"
        DateCategory.FIRST_DATE -> "☕"
        DateCategory.MILESTONE -> "🌟"
        DateCategory.CUSTOM -> "❤️"
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Category Icon Badge
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .background(
                        Brush.radialGradient(listOf(RoseGoldPrimary.copy(alpha = 0.2f), ChampagneSecondary.copy(alpha = 0.2f))),
                        CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(text = icon, fontSize = 26.sp)
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = dateItem.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                val sdf = SimpleDateFormat("MMMM d, yyyy", Locale.getDefault())
                Text(
                    text = sdf.format(Date(dateItem.dateMillis)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                if (!dateItem.notes.isNullOrEmpty()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = dateItem.notes,
                        style = MaterialTheme.typography.bodySmall,
                        color = RoseGoldPrimary,
                        maxLines = 1
                    )
                }
            }

            // Days counter pill
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.Center
            ) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (isFuture) RoseGoldPrimary.copy(alpha = 0.15f) else ChampagneSecondary.copy(alpha = 0.15f)
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = if (isFuture) "$diffDays" else "${kotlin.math.abs(diffDays)}",
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = if (isFuture) RoseGoldPrimary else ChampagneSecondary
                        )
                        Text(
                            text = if (isFuture) "days to go" else "days ago",
                            fontSize = 9.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.size(28.dp).padding(top = 4.dp)
                ) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "Delete",
                        tint = MaterialTheme.colorScheme.outlineVariant,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun AddDateDialog(
    onDismiss: () -> Unit,
    onAdd: (String, Long, DateCategory, String?) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf(DateCategory.ANNIVERSARY) }
    var daysOffset by remember { mutableStateOf("30") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Milestone Date") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Title (e.g. Next Anniversary)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = daysOffset,
                    onValueChange = { daysOffset = it.filter { ch -> ch.isDigit() } },
                    label = { Text("Days from today (or past days)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Love note or plan") },
                    modifier = Modifier.fillMaxWidth()
                )

                // Category chips
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    DateCategory.values().forEach { cat ->
                        FilterChip(
                            selected = selectedCategory == cat,
                            onClick = { selectedCategory = cat },
                            label = { Text(cat.name.take(4), fontSize = 11.sp) }
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (title.isNotBlank()) {
                        val offset = daysOffset.toLongOrNull() ?: 30L
                        val targetMillis = System.currentTimeMillis() + (offset * 24L * 3600L * 1000L)
                        onAdd(title, targetMillis, selectedCategory, notes.ifBlank { null })
                    }
                },
                enabled = title.isNotBlank()
            ) {
                Text("Save Date")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

package com.example.ui.notes

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
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.NoteCategory
import com.example.data.model.SharedNote
import com.example.ui.theme.AmethystTertiary
import com.example.ui.theme.HeartRed
import com.example.ui.theme.RoseGoldPrimary
import com.example.ui.theme.appGradientShadow
import com.example.ui.theme.appHorizontalGradient
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SharedNotesScreen(
    viewModel: SharedNotesViewModel,
    onNavigateBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    var editingNote by remember { mutableStateOf<SharedNote?>(null) }
    var showAddDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Our Shared Notes",
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier.testTag("notes_back_button")
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
                    .clickable {
                        editingNote = null
                        showAddDialog = true
                    }
                    .testTag("add_note_fab"),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "New Shared Note",
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
                )
            }
        },
        containerColor = MaterialTheme.colorScheme.surface
    ) { paddingValues ->
        val sortedNotes = remember(uiState.notes) {
            uiState.notes.sortedWith(compareByDescending<SharedNote> { it.isPinned }.thenByDescending { it.updatedAt })
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            items(sortedNotes, key = { it.id }) { note ->
                SharedNoteCard(
                    note = note,
                    onClick = {
                        editingNote = note
                        showAddDialog = true
                    },
                    onTogglePin = { viewModel.togglePin(note.id) },
                    onDelete = { viewModel.deleteNote(note.id) }
                )
            }
            item {
                Spacer(modifier = Modifier.height(72.dp))
            }
        }
    }

    if (showAddDialog) {
        NoteEditDialog(
            initialNote = editingNote,
            onDismiss = { showAddDialog = false },
            onSave = { id, title, content, cat ->
                viewModel.saveNote(id, title, content, cat)
                showAddDialog = false
            }
        )
    }
}

@Composable
fun SharedNoteCard(
    note: SharedNote,
    onClick: () -> Unit,
    onTogglePin: () -> Unit,
    onDelete: () -> Unit
) {
    val categoryBadge = when (note.getTypedCategory()) {
        NoteCategory.LOVE_LETTER -> "💌 Love Letter"
        NoteCategory.BUCKET_LIST -> "✈️ Bucket List"
        NoteCategory.WISHLIST -> "🎁 Wishlist"
        NoteCategory.NOTE -> "📝 Shared Note"
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (note.isPinned) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = if (note.isPinned) 3.dp else 1.dp)
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                ) {
                    Text(
                        text = categoryBadge,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onTogglePin, modifier = Modifier.size(32.dp)) {
                        Icon(
                            imageVector = if (note.isPinned) Icons.Filled.PushPin else Icons.Outlined.PushPin,
                            contentDescription = "Pin Note",
                            tint = if (note.isPinned) RoseGoldPrimary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Delete Note",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = note.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = note.content,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 5
            )

            Spacer(modifier = Modifier.height(10.dp))

            val sdf = SimpleDateFormat("MMM d, h:mm a", Locale.getDefault())
            Text(
                text = "Updated ${sdf.format(Date(note.updatedAt))}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
            )
        }
    }
}

@Composable
fun NoteEditDialog(
    initialNote: SharedNote?,
    onDismiss: () -> Unit,
    onSave: (String?, String, String, NoteCategory) -> Unit
) {
    var title by remember { mutableStateOf(initialNote?.title ?: "") }
    var content by remember { mutableStateOf(initialNote?.content ?: "") }
    var category by remember { mutableStateOf(initialNote?.getTypedCategory() ?: NoteCategory.NOTE) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initialNote != null) "Edit Note" else "New Shared Note") },
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
                    label = { Text("Title") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = content,
                    onValueChange = { content = it },
                    label = { Text("Write your thoughts or letter...") },
                    minLines = 4,
                    maxLines = 8,
                    modifier = Modifier.fillMaxWidth()
                )

                // Category selection
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    NoteCategory.values().forEach { cat ->
                        FilterChip(
                            selected = category == cat,
                            onClick = { category = cat },
                            label = { Text(cat.name.replace("_", " "), fontSize = 10.sp) }
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (title.isNotBlank() || content.isNotBlank()) {
                        onSave(initialNote?.id, title.ifBlank { "Untitled" }, content, category)
                    }
                },
                enabled = title.isNotBlank() || content.isNotBlank()
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}


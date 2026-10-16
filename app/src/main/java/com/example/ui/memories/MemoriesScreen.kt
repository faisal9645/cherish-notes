package com.example.ui.memories

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.PhotoAlbum
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.Place
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.model.Memory
import com.example.ui.theme.HeartRed
import com.example.ui.theme.RoseGoldPrimary
import com.example.ui.theme.appGradientShadow
import com.example.ui.theme.appHorizontalGradient
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import com.example.ui.components.CherishAlertDialog
import com.example.ui.components.CherishButton
import com.example.ui.components.SkeletonCards
import com.example.ui.components.pressScaleTarget
import com.example.ui.components.springClickable
import com.example.ui.components.softShadow
import com.example.ui.components.lazyFadingEdges
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.animation.core.tween

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MemoriesScreen(
    viewModel: MemoriesViewModel,
    onNavigateBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    var showAddDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Our Memories",
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier.testTag("memories_back_button")
                    ) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
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
                    .pressScaleTarget()
                    .appGradientShadow(CircleShape)
                    .clip(CircleShape)
                    .background(appHorizontalGradient())
                    .size(56.dp)
                    .springClickable { showAddDialog = true }
                    .testTag("add_memory_fab"),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.Add,
                    contentDescription = "Add Memory",
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
                )
            }
        },
        containerColor = MaterialTheme.colorScheme.surface
    ) { paddingValues ->
        // Grey cards until the memories first arrive; if they haven't after a few seconds (offline,
        // or none could be read), the empty state shows instead
        var waitedLong by remember { mutableStateOf(false) }
        LaunchedEffect(Unit) {
            kotlinx.coroutines.delay(2_500)
            waitedLong = true
        }
        // The placeholders melt into the memories (or the empty state) instead of the list popping in;
        // each side of the crossfade keeps showing what it was
        val shown = when {
            !uiState.isLoaded && !waitedLong -> 0
            uiState.memories.isEmpty() -> 1
            else -> 2
        }
        Crossfade(targetState = shown, animationSpec = tween(280), label = "memories_content") { phase ->
        if (phase == 0) {
            SkeletonCards(
                count = 3,
                modifier = Modifier
                    .padding(paddingValues)
                    .padding(16.dp)
            )
        } else if (phase == 1) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(32.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .background(RoseGoldPrimary.copy(alpha = 0.15f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Rounded.PhotoAlbum,
                            contentDescription = null,
                            tint = RoseGoldPrimary,
                            modifier = Modifier.size(36.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "No Memories Yet",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Capture your unforgettable trips, dates, and sweet moments together!",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            }
        } else {
            val memoriesList = rememberLazyListState()
            LazyColumn(
                state = memoriesList,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    // Cards fade softly at the top and bottom edges as they scroll
                    .lazyFadingEdges(memoriesList)
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                items(uiState.memories, key = { it.id }) { memory ->
                    MemoryCard(
                        memory = memory,
                        onDelete = { viewModel.deleteMemory(memory.id) },
                        // Memories slide into place, and fade in and out, as they're added or deleted
                        modifier = Modifier.animateItem()
                    )
                }
                item {
                    Spacer(modifier = Modifier.height(72.dp))
                }
            }
        }
        }
    }

    // The dialog stays open while the photo uploads, and closes once the memory is saved
    val savedCount = uiState.savedCount
    var savedCountWhenOpened by remember { mutableIntStateOf(savedCount) }
    LaunchedEffect(savedCount) {
        if (showAddDialog && savedCount > savedCountWhenOpened) showAddDialog = false
    }
    if (showAddDialog) {
        AddMemoryDialog(
            isUploading = uiState.isUploadingPhoto,
            error = uiState.addError,
            onDismiss = {
                if (!uiState.isUploadingPhoto) {
                    viewModel.clearAddError()
                    showAddDialog = false
                }
            },
            onAdd = { title, desc, date, uri, loc ->
                savedCountWhenOpened = savedCount
                viewModel.addMemory(title, desc, date, uri, loc)
            }
        )
    }
}

@Composable
fun MemoryCard(
    memory: Memory,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        // A soft, wide shadow instead of the card's own tight one, so it floats
        modifier = modifier
            .fillMaxWidth()
            .softShadow(6.dp, RoundedCornerShape(20.dp), clip = false),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            if (!memory.photoUrl.isNullOrEmpty()) {
                AsyncImage(
                    model = memory.photoUrl,
                    contentDescription = memory.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                )
            }

            Column(modifier = Modifier.padding(20.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = memory.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                        Icon(
                            Icons.Rounded.Delete,
                            contentDescription = "Delete memory",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(top = 2.dp, bottom = 8.dp)
                ) {
                    val sdf = SimpleDateFormat("MMMM d, yyyy", Locale.getDefault())
                    Text(
                        text = sdf.format(Date(memory.dateMillis)),
                        style = MaterialTheme.typography.labelSmall,
                        color = RoseGoldPrimary,
                        fontWeight = FontWeight.SemiBold
                    )

                    if (!memory.location.isNullOrEmpty()) {
                        Text(" • ", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Icon(
                            Icons.Rounded.Place,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(12.dp)
                        )
                        Spacer(modifier = Modifier.width(2.dp))
                        Text(
                            text = memory.location,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                if (memory.description.isNotEmpty()) {
                    Text(
                        text = memory.description,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
fun AddMemoryDialog(
    isUploading: Boolean,
    error: String? = null,
    onDismiss: () -> Unit,
    onAdd: (String, String, Long, Uri?, String?) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var location by remember { mutableStateOf("") }
    var selectedImageUri by remember { mutableStateOf<Uri?>(null) }
    val context = androidx.compose.ui.platform.LocalContext.current

    val photoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri -> 
        val prefs = com.example.security.SecurityPreferences.getInstance(context)
        prefs.isExternalPickerActive = false
        prefs.ignoreNextPause = false
        selectedImageUri = uri 
    }

    CherishAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Shared Memory") },
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
                    label = { Text("Memory Title") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("What made this moment special?") },
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = location,
                    onValueChange = { location = it },
                    label = { Text("Location (Optional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                CherishButton(
                    onClick = {
                        val prefs = com.example.security.SecurityPreferences.getInstance(context)
                        prefs.isExternalPickerActive = true
                        prefs.ignoreNextPause = true
                        photoPicker.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Rounded.PhotoCamera, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(if (selectedImageUri != null) "Photo Selected ✓" else "Choose Photo")
                }

                if (isUploading) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    Text(
                        text = "Uploading the photo\u2026",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (error != null && !isUploading) {
                    Text(
                        text = error,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        },
        confirmButton = {
            CherishButton(
                onClick = {
                    if (title.isNotBlank()) {
                        onAdd(title, description, System.currentTimeMillis(), selectedImageUri, location.ifBlank { null })
                    }
                },
                enabled = title.isNotBlank() && !isUploading
            ) {
                Text("Save Moment")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}


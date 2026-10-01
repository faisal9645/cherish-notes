package com.example.ui.chat

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.paint
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.data.model.Message
import com.example.data.model.MessageType
import com.example.ui.components.AvatarView
import androidx.compose.foundation.BorderStroke
import com.example.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.core.content.FileProvider
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    viewModel: ChatViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToGallery: () -> Unit,
    onQuickDisguise: () -> Unit = {},
    onNavigateToProfile: () -> Unit = {},
    onLoggedOut: () -> Unit = {}
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val uiState by viewModel.uiState.collectAsState()
    val listState = rememberLazyListState()

    var composerText by remember { mutableStateOf("") }
    var editingMessage by remember { mutableStateOf<Message?>(null) }
    var editDialogText by remember { mutableStateOf("") }
    var showDeleteConfirmDialog by remember { mutableStateOf<Message?>(null) }
    var showMutualDeleteRequestDialog by remember { mutableStateOf(false) }
    var showChatMenu by remember { mutableStateOf(false) }

    val currentPlayingId by viewModel.voicePlayerHelper.currentlyPlayingId.collectAsState()
    val isAudioPlaying by viewModel.voicePlayerHelper.isPlaying.collectAsState()
    val audioProgress by viewModel.voicePlayerHelper.playbackProgress.collectAsState()
    val isRecordingVoice by viewModel.voiceRecorderHelper.isRecording.collectAsState()
    val recordingDurationSec by viewModel.voiceRecorderHelper.recordingDurationSec.collectAsState()
    val recordingAmplitudes by viewModel.voiceRecorderHelper.amplitudes.collectAsState()

    // Activity result launchers for media selection (supports multiple photo selection)
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(maxItems = 10)
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            if (uris.size == 1) {
                viewModel.sendMediaFile(uris[0], MessageType.IMAGE)
            } else {
                viewModel.sendMultipleImages(uris)
            }
        }
    }

    val docPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { viewModel.sendMediaFile(it, MessageType.DOCUMENT) }
    }

    // Mic permission launcher
    val micPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            Toast.makeText(context, "Microphone enabled! Hold mic to record", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(context, "Microphone permission is required for voice notes", Toast.LENGTH_LONG).show()
        }
    }

    // Real-time camera snap launcher
    var tempCameraUri by remember { mutableStateOf<Uri?>(null) }
    var showAttachmentSheet by remember { mutableStateOf(false) }

    val cameraSnapLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success: Boolean ->
        if (success) {
            tempCameraUri?.let { uri ->
                viewModel.sendMediaFile(uri, MessageType.IMAGE)
            }
        }
    }

    fun launchRealtimeCameraSnap() {
        try {
            val imagesDir = File(context.cacheDir, "camera_snaps").apply { mkdirs() }
            val photoFile = File(imagesDir, "snap_${System.currentTimeMillis()}.jpg").apply {
                createNewFile()
            }
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                photoFile
            )
            tempCameraUri = uri
            com.example.security.SecurityPreferences.getInstance(context).ignoreNextPause = true
            cameraSnapLauncher.launch(uri)
        } catch (e: Exception) {
            Toast.makeText(context, "Could not open camera: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            launchRealtimeCameraSnap()
        } else {
            Toast.makeText(context, "Camera permission needed to snap photos", Toast.LENGTH_SHORT).show()
        }
    }

    val triggerCameraSnap: () -> Unit = {
        val hasCamera = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
        if (hasCamera) {
            launchRealtimeCameraSnap()
        } else {
            com.example.security.SecurityPreferences.getInstance(context).ignoreNextPause = true
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    // Scroll to bottom (index 0) when new messages arrive
    LaunchedEffect(uiState.messages.size) {
        if (uiState.messages.isNotEmpty()) {
            listState.animateScrollToItem(0)
        }
    }

    // Clear disguised notifications upon entering chat
    LaunchedEffect(Unit) {
        com.example.notifications.NotificationHelper.clearNotifications(context)
    }

    // Partner info
    val partner = uiState.partnerUser
    val myUser = uiState.currentUser
    val partnerName = partner?.displayName?.ifBlank { null }
        ?: myUser?.partnerEmail?.substringBefore("@")?.replaceFirstChar { it.uppercase() }
        ?: myUser?.partnerId?.removePrefix("user_")?.replaceFirstChar { it.uppercase() }
        ?: "My Partner"
    val isPartnerOnline = partner?.isEffectivelyOnline() ?: false
    val currentUserId = viewModel.uiState.value.currentUser?.id ?: "user_me"

    val partnerHasCheckAfter = partner?.hasActiveCheckAfter() == true
    val partnerCheckAfterTarget = partner?.checkAfterTimeMillis ?: 0L
    val iHaveCheckAfter = myUser?.hasActiveCheckAfter() == true
    val myCheckAfterTarget = myUser?.checkAfterTimeMillis ?: 0L

    var headerTicker by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(partnerCheckAfterTarget, partnerHasCheckAfter) {
        if (partnerHasCheckAfter) {
            while (true) {
                headerTicker = System.currentTimeMillis()
                kotlinx.coroutines.delay(1000)
            }
        }
    }
    val headerRemaining = remember(headerTicker, partnerCheckAfterTarget) {
        if (partnerHasCheckAfter) CheckAfterHelper.calculateRemaining(partnerCheckAfterTarget).first else ""
    }

    // Filter messages for search query, starred filter, and privacy shield
    val displayedMessages = remember(uiState.messages, uiState.searchQuery, uiState.filterStarredOnly, uiState.isSecretHistoryRevealed) {
        var list = uiState.messages
        if (!uiState.isSecretHistoryRevealed) {
            val cal = java.util.Calendar.getInstance()
            if (cal.get(java.util.Calendar.HOUR_OF_DAY) < 6) {
                cal.add(java.util.Calendar.DAY_OF_YEAR, -1)
            }
            cal.set(java.util.Calendar.HOUR_OF_DAY, 6)
            cal.set(java.util.Calendar.MINUTE, 0)
            cal.set(java.util.Calendar.SECOND, 0)
            cal.set(java.util.Calendar.MILLISECOND, 0)
            val todayStart = cal.timeInMillis
            list = list.filter { it.timestamp >= todayStart }
        }
        
        if (uiState.filterStarredOnly) {
            list = list.filter { it.isStarred }
        }
        if (uiState.searchQuery.isNotBlank()) {
            list = list.filter { it.text.contains(uiState.searchQuery, ignoreCase = true) }
        }
        list
    }

    var highlightedMessageId by remember { mutableStateOf<String?>(null) }

    // Scroll to target message when navigating from gallery ("Show in chat")
    LaunchedEffect(uiState.targetScrollMessageId, displayedMessages.size) {
        val targetId = uiState.targetScrollMessageId
        if (targetId != null && displayedMessages.isNotEmpty()) {
            val idx = displayedMessages.indexOfFirst { it.id == targetId }
            if (idx >= 0) {
                listState.animateScrollToItem(idx)
                highlightedMessageId = targetId
                kotlinx.coroutines.delay(2500)
                highlightedMessageId = null
                viewModel.clearTargetScrollMessageId()
            }
        }
    }

    // Panic Protection: Accelerometer shake listener to trigger instant disguise
    DisposableEffect(Unit) {
        val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? android.hardware.SensorManager
        val accelerometer = sensorManager?.getDefaultSensor(android.hardware.Sensor.TYPE_ACCELEROMETER)
        var lastShakeTime = 0L
        var lastX = 0f
        var lastY = 0f
        var lastZ = 0f

        val listener = object : android.hardware.SensorEventListener {
            override fun onSensorChanged(event: android.hardware.SensorEvent?) {
                if (event == null) return
                val x = event.values[0]
                val y = event.values[1]
                val z = event.values[2]
                val delta = kotlin.math.abs(x + y + z - lastX - lastY - lastZ)
                val now = System.currentTimeMillis()
                if (delta > 25f && now - lastShakeTime > 1500L) {
                    lastShakeTime = now
                    viewModel.hideSecretHistory()
                    onQuickDisguise()
                }
                lastX = x
                lastY = y
                lastZ = z
            }
            override fun onAccuracyChanged(sensor: android.hardware.Sensor?, accuracy: Int) {}
        }

        accelerometer?.let {
            sensorManager.registerListener(listener, it, android.hardware.SensorManager.SENSOR_DELAY_UI)
        }

        onDispose {
            sensorManager?.unregisterListener(listener)
            viewModel.cancelVoiceRecording()
            viewModel.hideSecretHistory()
        }
    }

    var dragAccumulator by remember { mutableFloatStateOf(0f) }

    Scaffold(
        modifier = Modifier.pointerInput(Unit) {
            detectHorizontalDragGestures(
                onDragStart = { _ -> dragAccumulator = 0f },
                onDragEnd = {
                    if (dragAccumulator > 80f) {
                        // Swipe Right -> Home
                        onNavigateBack()
                    } else if (dragAccumulator < -80f) {
                        // Swipe Left -> Profile
                        onNavigateToProfile()
                    }
                    dragAccumulator = 0f
                },
                onHorizontalDrag = { _, dragAmount ->
                    dragAccumulator += dragAmount
                }
            )
        },
        topBar = {
            TopAppBar(
                title = {
                    if (uiState.isSearching) {
                        OutlinedTextField(
                            value = uiState.searchQuery,
                            onValueChange = { viewModel.setSearchQuery(it) },
                            placeholder = { Text("Search our chat...") },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color.Transparent,
                                unfocusedBorderColor = Color.Transparent
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("chat_search_input")
                        )
                    } else {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.clickable {
                                if (partnerHasCheckAfter || iHaveCheckAfter) {
                                    viewModel.openCheckAfterSheet()
                                } else {
                                    onNavigateToGallery()
                                }
                            }
                        ) {
                            AvatarView(
                                photoUrl = partner?.photoUrl,
                                name = partnerName,
                                size = 40.dp,
                                isOnline = isPartnerOnline,
                                showOnlineBadge = !partnerHasCheckAfter
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = partnerName,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = if (uiState.isPartnerRecordingAudio) {
                                        "recording voice note... 🎙️"
                                    } else if (uiState.isPartnerTyping) {
                                        "typing sweet words..."
                                    } else if (partnerHasCheckAfter) {
                                        if (headerRemaining.startsWith("✨")) "✨ Reconnecting now"
                                        else "🌙 Quiet time until ${CheckAfterHelper.formatTargetTime(partnerCheckAfterTarget)}"
                                    } else if (isPartnerOnline) {
                                        "Online"
                                    } else {
                                        val lastSeen = partner?.lastSeen ?: 0L
                                        if (lastSeen > 0L) {
                                            val diffSec = ((System.currentTimeMillis() - lastSeen) / 1000).coerceAtLeast(0)
                                            when {
                                                diffSec < 60 -> "Last seen just now"
                                                diffSec < 3600 -> "Last seen ${diffSec / 60}m ago"
                                                diffSec < 86400 -> "Last seen ${diffSec / 3600}h ago"
                                                else -> "Offline"
                                            }
                                        } else {
                                            "Offline"
                                        }
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (uiState.isPartnerRecordingAudio || uiState.isPartnerTyping || partnerHasCheckAfter) {
                                        RoseGoldPrimary
                                    } else if (isPartnerOnline) {
                                        Color(0xFF2E7D32)
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f)
                                    },
                                    fontWeight = if (partnerHasCheckAfter || uiState.isPartnerRecordingAudio || uiState.isPartnerTyping || isPartnerOnline) {
                                        FontWeight.SemiBold
                                    } else {
                                        FontWeight.Normal
                                    }
                                )
                            }
                        }
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = {
                            if (uiState.isSearching) {
                                viewModel.setSearching(false)
                            } else {
                                onNavigateBack()
                            }
                        },
                        modifier = Modifier.testTag("chat_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                actions = {
                    if (uiState.isSearching) {
                        IconButton(onClick = { viewModel.setSearching(false) }) {
                            Icon(Icons.Default.Close, contentDescription = "Close search")
                        }
                    } else {
                        IconButton(
                            onClick = { viewModel.setSearching(true) },
                            modifier = Modifier.testTag("chat_search_button")
                        ) {
                            Icon(Icons.Default.Search, contentDescription = "Search messages")
                        }
                        IconButton(
                            onClick = onNavigateToGallery,
                            modifier = Modifier.testTag("chat_gallery_button")
                        ) {
                            Icon(Icons.Outlined.PhotoLibrary, contentDescription = "Couple Media Gallery")
                        }
                        IconButton(
                            onClick = { viewModel.openCheckAfterSheet() },
                            modifier = Modifier.testTag("chat_check_after_button")
                        ) {
                            Icon(
                                imageVector = if (partnerHasCheckAfter || iHaveCheckAfter) Icons.Filled.HourglassTop else Icons.Outlined.HourglassTop,
                                contentDescription = "Check After Timer",
                                tint = if (partnerHasCheckAfter || iHaveCheckAfter) RoseGoldPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (uiState.isSecretHistoryRevealed) {
                            IconButton(
                                onClick = {
                                    viewModel.hideSecretHistory()
                                    Toast.makeText(context, "Chat history shielded for privacy", Toast.LENGTH_SHORT).show()
                                },
                                modifier = Modifier.testTag("chat_shield_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Shield,
                                    contentDescription = "Shield Chat History",
                                    tint = RoseGoldPrimary
                                )
                            }
                        }
                        Box {
                            IconButton(
                                onClick = { showChatMenu = true },
                                modifier = Modifier.testTag("chat_more_menu_button")
                            ) {
                                Icon(Icons.Default.MoreVert, contentDescription = "More options")
                            }
                            DropdownMenu(
                                expanded = showChatMenu,
                                onDismissRequest = { showChatMenu = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Check After Timer") },
                                    onClick = {
                                        showChatMenu = false
                                        viewModel.openCheckAfterSheet()
                                    },
                                    leadingIcon = {
                                        Icon(Icons.Filled.HourglassTop, null, tint = RoseGoldPrimary)
                                    }
                                )
                                if (!uiState.isSecretHistoryRevealed) {
                                    DropdownMenuItem(
                                        text = { Text("Recover & Show Everything") },
                                        onClick = {
                                            showChatMenu = false
                                            viewModel.revealSecretHistory()
                                        },
                                        leadingIcon = {
                                            Icon(
                                                Icons.Outlined.Visibility,
                                                null,
                                                tint = GoldMilestone
                                            )
                                        }
                                    )
                                }
                                DropdownMenuItem(
                                    text = { Text(if (uiState.filterStarredOnly) "Show All Messages" else "Starred Messages ⭐") },
                                    onClick = {
                                        showChatMenu = false
                                        viewModel.toggleFilterStarred()
                                    },
                                    leadingIcon = {
                                        Icon(if (uiState.filterStarredOnly) Icons.Filled.Star else Icons.Outlined.StarOutline, null, tint = GoldMilestone)
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Stealth Mode (Open Notes App)") },
                                    onClick = {
                                        showChatMenu = false
                                        onQuickDisguise()
                                    },
                                    leadingIcon = {
                                        Icon(
                                            Icons.Outlined.EditNote,
                                            null,
                                            tint = RoseGoldPrimary
                                        )
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Profile & Partner Settings") },
                                    onClick = {
                                        showChatMenu = false
                                        onNavigateToProfile()
                                    },
                                    leadingIcon = {
                                        Icon(
                                            Icons.Default.Person,
                                            null,
                                            tint = RoseGoldPrimary
                                        )
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Log Out / Switch Partner", color = MaterialTheme.colorScheme.error) },
                                    onClick = {
                                        showChatMenu = false
                                        viewModel.logout()
                                        onLoggedOut()
                                    },
                                    leadingIcon = {
                                        Icon(
                                            Icons.AutoMirrored.Filled.Logout,
                                            null,
                                            tint = MaterialTheme.colorScheme.error
                                        )
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Clear Chat (Both Must Accept)", color = MaterialTheme.colorScheme.error) },
                                    onClick = {
                                        showChatMenu = false
                                        showMutualDeleteRequestDialog = true
                                    },
                                    leadingIcon = {
                                        Icon(Icons.Default.DeleteSweep, null, tint = MaterialTheme.colorScheme.error)
                                    }
                                )
                            }
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                windowInsets = WindowInsets.statusBars
            )
        },
        bottomBar = {
            Surface(
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 2.dp,
                shadowElevation = 8.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .imePadding()
                ) {
                    HorizontalDivider(color = Color(0xFFF0F0F2), thickness = 1.dp)
                    // Partner typing / recording animated banner
                AnimatedVisibility(visible = uiState.isPartnerRecordingAudio || uiState.isPartnerTyping) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                            .padding(horizontal = 16.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (uiState.isPartnerRecordingAudio) {
                            Icon(
                                imageVector = Icons.Default.Mic,
                                contentDescription = null,
                                tint = RoseGoldPrimary,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "$partnerName is recording a voice note... 🎙️",
                                fontSize = 12.sp,
                                color = RoseGoldPrimary,
                                fontWeight = FontWeight.Medium
                            )
                        } else {
                            BouncingDots()
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "$partnerName is typing...",
                                fontSize = 12.sp,
                                color = RoseGoldPrimary,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }

                // Media upload progress bar
                if (uiState.isUploadingMedia) {
                    LinearProgressIndicator(
                        progress = { uiState.uploadProgress },
                        modifier = Modifier.fillMaxWidth(),
                        color = RoseGoldPrimary
                    )
                }

                MessageComposer(
                    text = composerText,
                    onTextChanged = {
                        composerText = it
                        viewModel.onTypingChanged(it.isNotBlank())
                    },
                    onSendText = {
                        viewModel.sendTextMessage(composerText)
                        composerText = ""
                        viewModel.onTypingChanged(false)
                    },
                    replyingTo = uiState.replyingToMessage,
                    onDismissReply = { viewModel.setReplyingTo(null) },
                    isRecordingVoice = isRecordingVoice,
                    recordingDurationSec = recordingDurationSec,
                    recordingAmplitudes = recordingAmplitudes,
                    onStartVoiceRecord = {
                        val hasMic = ContextCompat.checkSelfPermission(
                            context,
                            Manifest.permission.RECORD_AUDIO
                        ) == PackageManager.PERMISSION_GRANTED
                        if (hasMic) {
                            viewModel.startVoiceRecording()
                        } else {
                            com.example.security.SecurityPreferences.getInstance(context).ignoreNextPause = true
                            micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                        }
                    },
                    onStopAndSendVoiceRecord = { viewModel.stopAndSendVoiceRecording() },
                    onCancelVoiceRecord = { viewModel.cancelVoiceRecording() },
                    onTakePhoto = triggerCameraSnap,
                    onPickAttachment = { showAttachmentSheet = true },
                    placeholder = if (uiState.isStealthCurtainActive) "Add a note..." else "Message your love..."
                )
            }
        }
    },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Starred Messages Filter Header Banner
            AnimatedVisibility(visible = uiState.filterStarredOnly) {
                Surface(
                    color = GoldMilestone.copy(alpha = 0.15f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Star,
                            contentDescription = null,
                            tint = GoldMilestone,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Showing starred messages only (${displayedMessages.size})",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(onClick = { viewModel.toggleFilterStarred() }) {
                            Text("Show All", fontSize = 12.sp, color = RoseGoldPrimary)
                        }
                    }
                }
            }

            // Telegram-style Pinned Message Banner
            AnimatedVisibility(visible = uiState.pinnedMessage != null && !uiState.isStealthCurtainActive) {
                uiState.pinnedMessage?.let { pinned ->
                    TelegramPinnedBanner(
                        message = pinned,
                        onClick = {
                            val index = displayedMessages.indexOfFirst { it.id == pinned.id }
                            if (index >= 0) {
                                scope.launch { listState.animateScrollToItem(index) }
                            }
                        },
                        onUnpin = { viewModel.togglePin(pinned.id) }
                    )
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                if (!uiState.isStealthCurtainActive) {
                    ChatWallpaper(
                        chatBgTheme = uiState.chatBgTheme,
                        modifier = Modifier.fillMaxSize()
                    )
                }

                if (uiState.isStealthCurtainActive) {
                    // Emergency Privacy Shield: Harmless daily notes / tasks view hiding all previous chat
                    StealthDisguiseNotesView(
                        onRestore = onQuickDisguise
                    )
            } else {
                val reversedMessages = displayedMessages.reversed()
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(Unit) {
                            detectTapGestures(
                                onDoubleTap = {
                                    onQuickDisguise()
                                }
                            )
                        },
                    reverseLayout = true,
                    contentPadding = PaddingValues(vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    itemsIndexed(
                        items = reversedMessages,
                        key = { _, msg -> msg.id },
                        contentType = { _, _ -> "message" }
                    ) { index, message ->
                        val isFromMe = message.senderId == currentUserId
                        val isFirstOfDay = index == reversedMessages.lastIndex || !isSameDay(reversedMessages[index + 1].timestamp, message.timestamp)

                        Column {
                            if (isFirstOfDay) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 12.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Surface(
                                        shape = CircleShape,
                                        color = MaterialTheme.colorScheme.surfaceVariant,
                                        tonalElevation = 1.dp
                                    ) {
                                        Text(
                                            text = formatDateSeparator(message.timestamp),
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                                        )
                                    }
                                }
                            }

                            LaunchedEffect(message.id) {
                                if (!isFromMe && message.getTypedStatus() != com.example.data.model.MessageStatus.READ) {
                                    viewModel.markMessageAsRead(message.id)
                                }
                            }

                            val isHighlighted = (highlightedMessageId == message.id)
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .then(
                                        if (isHighlighted) {
                                            Modifier
                                                .background(RoseGoldPrimary.copy(alpha = 0.15f), RoundedCornerShape(16.dp))
                                                .border(2.dp, RoseGoldPrimary, RoundedCornerShape(16.dp))
                                                .padding(4.dp)
                                        } else Modifier
                                    )
                            ) {
                                MessageBubble(
                                    message = message,
                                    isFromMe = isFromMe,
                                    isPlayingAudio = (currentPlayingId == message.id && isAudioPlaying),
                                    audioProgress = { if (currentPlayingId == message.id) audioProgress else 0f },
                                    gallerySize = uiState.gallerySize,
                                    onPlayAudio = {
                                        message.mediaUrl?.let { url ->
                                            viewModel.playAudio(message.id, url)
                                        }
                                    },
                                    onImageClick = { url ->
                                        viewModel.openFullScreenMedia(url, MessageType.IMAGE, message.getAllMediaUrls())
                                    },
                                    onImageClickWithList = { url, allUrls ->
                                        viewModel.openFullScreenMedia(url, MessageType.IMAGE, allUrls)
                                    },
                                    onSwipeToReply = {
                                        viewModel.setReplyingTo(message)
                                    },
                                    onLongClick = {
                                        viewModel.setSelectedMessageForActions(message)
                                    },
                                    onReactionClick = { emoji ->
                                        viewModel.toggleReaction(message.id, emoji)
                                    },
                                    onOpenTheaterVideo = { videoId ->
                                        viewModel.openTheaterVideo(videoId)
                                    },
                                    voicePlaybackSpeed = uiState.voicePlaybackSpeed,
                                    onToggleVoiceSpeed = {
                                        viewModel.toggleVoiceSpeed()
                                    },
                                    isHighlighted = isHighlighted,
                                    onReplyQuoteClick = { replyId ->
                                        if (!replyId.isNullOrBlank()) {
                                            val targetIndex = reversedMessages.indexOfFirst { it.id == replyId }
                                            if (targetIndex >= 0) {
                                                scope.launch {
                                                    listState.animateScrollToItem(targetIndex)
                                                    highlightedMessageId = replyId
                                                    kotlinx.coroutines.delay(1400)
                                                    highlightedMessageId = null
                                                }
                                            }
                                        }
                                    }
                                )
                            }
                        }
                    }

                    item {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp, vertical = 10.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.85f),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(12.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = Icons.Default.Lock,
                                            contentDescription = null,
                                            tint = RoseGoldPrimary,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "Strictly 2-Person Private Channel",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 12.sp,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "Messages, voice notes, and photos are strictly between you and $partnerName. No third parties can ever join or view this chat.",
                                        fontSize = 11.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                        lineHeight = 15.sp
                                    )
                                }
                            }
                        }
                    }

                    if (partnerHasCheckAfter) {
                        item {
                            CheckAfterChatBanner(
                                targetMillis = partnerCheckAfterTarget,
                                note = partner?.checkAfterNote ?: "",
                                isSetByMe = false,
                                partnerName = partnerName,
                                isReminderEnabled = uiState.isCheckAfterReminderEnabled,
                                onToggleReminder = { viewModel.toggleCheckAfterReminder(!uiState.isCheckAfterReminderEnabled) },
                                onExtend30m = {},
                                onExtend1h = {},
                                onChangeTime = { viewModel.openCheckAfterSheet() },
                                onCancel = {},
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                            )
                        }
                    } else if (iHaveCheckAfter) {
                        item {
                            CheckAfterChatBanner(
                                targetMillis = myCheckAfterTarget,
                                note = myUser?.checkAfterNote ?: "",
                                isSetByMe = true,
                                partnerName = partnerName,
                                isReminderEnabled = false,
                                onToggleReminder = {},
                                onExtend30m = { viewModel.extendCheckAfter(30 * 60 * 1000L) },
                                onExtend1h = { viewModel.extendCheckAfter(60 * 60 * 1000L) },
                                onChangeTime = { viewModel.openCheckAfterSheet() },
                                onCancel = { viewModel.cancelCheckAfter() },
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                            )
                        }
                    }

                    if (uiState.deletionRequest != null) {
                        item {
                            MutualConsentDeletionBanner(
                                request = uiState.deletionRequest!!,
                                isFromMe = (uiState.deletionRequest!!.requestedByUserId == currentUserId),
                                partnerName = partnerName,
                                onAccept = { viewModel.acceptMutualChatDeletion() },
                                onDecline = { viewModel.declineMutualChatDeletion() },
                                onCancel = { viewModel.cancelMutualChatDeletion() }
                            )
                        }
                    }
                }

            // Floating scroll to bottom button
            val showScrollButton by remember {
                derivedStateOf {
                    listState.firstVisibleItemIndex > 3
                }
            }

            androidx.compose.animation.AnimatedVisibility(
                visible = showScrollButton,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp)
            ) {
                SmallFloatingActionButton(
                    onClick = {
                        scope.launch {
                            if (displayedMessages.isNotEmpty()) {
                                listState.animateScrollToItem(0)
                            }
                        }
                    },
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    shape = CircleShape
                ) {
                    Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Scroll to bottom")
                }
            }
            } // Close else branch of if (uiState.isStealthCurtainActive)

            // Stealth mode floating status feedback badge (first-time use only)
            LaunchedEffect(uiState.stealthToastMessage) {
                if (uiState.stealthToastMessage != null) {
                    delay(3500)
                    viewModel.clearStealthToast()
                }
            }

            androidx.compose.animation.AnimatedVisibility(
                visible = uiState.stealthToastMessage != null,
                enter = fadeIn() + slideInVertically(initialOffsetY = { -it }),
                exit = fadeOut() + slideOutVertically(targetOffsetY = { -it }),
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 10.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = Color(0xFF222228).copy(alpha = 0.95f),
                    shadowElevation = 8.dp,
                    modifier = Modifier.clickable { viewModel.clearStealthToast() }
                ) {
                    Text(
                        text = uiState.stealthToastMessage ?: "",
                        color = MaterialTheme.colorScheme.surface,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }
            }
        }
    }
}

    // Message Actions Bottom Sheet
    uiState.selectedMessageForActions?.let { msg ->
        MessageActionsSheet(
            message = msg,
            isFromMe = msg.senderId == currentUserId,
            onDismiss = { viewModel.setSelectedMessageForActions(null) },
            onReaction = { emoji -> viewModel.toggleReaction(msg.id, emoji) },
            onReply = { viewModel.setReplyingTo(msg) },
            onCopy = {
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val clip = ClipData.newPlainText("Cherish Message", msg.text)
                clipboard.setPrimaryClip(clip)
            },
            onStar = { viewModel.toggleStar(msg.id) },
            onPin = { viewModel.togglePin(msg.id) },
            onEdit = {
                editingMessage = msg
                editDialogText = msg.text
            },
            onDelete = {
                showDeleteConfirmDialog = msg
            },
            onForward = {
                composerText = "Fwd: ${msg.text}"
                Toast.makeText(context, "Loaded into composer to forward", Toast.LENGTH_SHORT).show()
            },
            onSearch = {
                viewModel.setSearching(true)
            },
            onSaveToMemories = {
                Toast.makeText(context, "Saved to Our Memories ❤️", Toast.LENGTH_SHORT).show()
            }
        )
    }

    // Attachment Options Bottom Sheet (Gallery, Camera Snap, Documents)
    if (showAttachmentSheet) {
        ModalBottomSheet(
            onDismissRequest = { showAttachmentSheet = false },
            containerColor = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .padding(bottom = 36.dp, top = 8.dp)
            ) {
                Text(
                    text = "Share with your love",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF222228),
                    modifier = Modifier.padding(bottom = 20.dp)
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceAround
                ) {
                    AttachmentOptionItem(
                        icon = Icons.Outlined.PhotoLibrary,
                        label = "Gallery",
                        color = Color(0xFF6C5CE7),
                        onClick = {
                            showAttachmentSheet = false
                            com.example.security.SecurityPreferences.getInstance(context).ignoreNextPause = true
                            photoPickerLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)
                            )
                        }
                    )

                    AttachmentOptionItem(
                        icon = Icons.Default.CameraAlt,
                        label = "Camera",
                        color = RoseGoldPrimary,
                        onClick = {
                            showAttachmentSheet = false
                            triggerCameraSnap()
                        }
                    )

                    AttachmentOptionItem(
                        icon = Icons.Default.InsertDriveFile,
                        label = "Document",
                        color = Color(0xFF0984E3),
                        onClick = {
                            showAttachmentSheet = false
                            com.example.security.SecurityPreferences.getInstance(context).ignoreNextPause = true
                            docPickerLauncher.launch("*/*")
                        }
                    )
                }
            }
        }
    }

    // Edit Message Dialog
    editingMessage?.let { msg ->
        AlertDialog(
            onDismissRequest = { editingMessage = null },
            title = { Text("Edit Message") },
            text = {
                OutlinedTextField(
                    value = editDialogText,
                    onValueChange = { editDialogText = it },
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.editMessage(msg.id, editDialogText)
                        editingMessage = null
                    }
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { editingMessage = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Delete Confirmation Dialog
    showDeleteConfirmDialog?.let { msg ->
        AlertDialog(
            onDismissRequest = { showDeleteConfirmDialog = null },
            title = { Text("Delete message?") },
            text = { Text("This message will be removed from your private chat.") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteMessage(msg.id)
                        showDeleteConfirmDialog = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmDialog = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Full-screen media viewer
    uiState.fullScreenMediaUrl?.let { url ->
        FullScreenMediaViewer(
            mediaUrl = url,
            allMediaUrls = uiState.allMediaUrlsForViewer,
            onDismiss = { viewModel.closeFullScreenMedia() }
        )
    }

    // In-App YouTube Theater Player Modal (no external redirection, keeps same session)
    uiState.theaterVideoId?.let { videoId ->
        InlineVideoTheaterModal(
            videoId = videoId,
            onDismiss = { viewModel.closeTheaterVideo() }
        )
    }

    // Mutual Consent Chat Clear Request Dialog
    if (showMutualDeleteRequestDialog) {
        AlertDialog(
            onDismissRequest = { showMutualDeleteRequestDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Security, contentDescription = null, tint = RoseGoldPrimary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Mutual Consent Chat Clear", fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Text(
                    text = "Under our Mutual Protection rule, clearing chat history requires BOTH partners to accept so that precious memories are never erased accidentally or unilaterally.\n\nWould you like to send a deletion request to $partnerName?",
                    lineHeight = 20.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.requestMutualChatDeletion("ALL_MESSAGES")
                        showMutualDeleteRequestDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Send Deletion Request")
                }
            },
            dismissButton = {
                TextButton(onClick = { showMutualDeleteRequestDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Check-After Setup & Modification Bottom Sheet
    CheckAfterBottomSheet(
        isOpen = uiState.isCheckAfterSheetOpen,
        onDismiss = { viewModel.closeCheckAfterSheet() },
        currentTargetMillis = if (iHaveCheckAfter) myCheckAfterTarget else if (partnerHasCheckAfter) partnerCheckAfterTarget else null,
        currentNote = if (iHaveCheckAfter) myUser?.checkAfterNote else partner?.checkAfterNote,
        isCurrentlyActive = iHaveCheckAfter || partnerHasCheckAfter,
        isSetByMe = iHaveCheckAfter,
        partnerName = partnerName,
        isReminderEnabled = uiState.isCheckAfterReminderEnabled,
        onToggleReminder = { viewModel.toggleCheckAfterReminder(it) },
        onSaveCheckAfter = { target, note ->
            viewModel.setCheckAfter(target, note)
        },
        onCancelCheckAfter = {
            viewModel.cancelCheckAfter()
        },
        onExtend = { duration ->
            viewModel.extendCheckAfter(duration)
        }
    )
}

@Composable
fun TelegramPinnedBanner(
    message: Message,
    onClick: () -> Unit,
    onUnpin: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.85f),
        tonalElevation = 2.dp,
        modifier = modifier
            .fillMaxWidth()
            .clickable { onClick() }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .height(32.dp)
                    .background(RoseGoldPrimary, CircleShape)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Icon(
                imageVector = Icons.Filled.PushPin,
                contentDescription = null,
                tint = RoseGoldPrimary,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Pinned Message",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = RoseGoldPrimary
                )
                Text(
                    text = if (message.text.isNotBlank()) message.text else if (message.getTypedType() == MessageType.IMAGE) "📷 Photo" else if (message.getTypedType() == MessageType.AUDIO) "🎙️ Voice note" else "Document",
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(
                onClick = onUnpin,
                modifier = Modifier.size(28.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Unpin",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}


@Composable
fun MutualConsentDeletionBanner(
    request: com.example.data.model.ChatDeletionRequest,
    isFromMe: Boolean,
    partnerName: String,
    onAccept: () -> Unit,
    onDecline: () -> Unit,
    onCancel: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = if (isFromMe) Color(0xFFFFF8E1) else Color(0xFFFFEBEE)),
        border = BorderStroke(1.dp, if (isFromMe) Color(0xFFFFE082) else Color(0xFFFFCDD2))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = if (isFromMe) Icons.Default.HourglassTop else Icons.Default.WarningAmber,
                    contentDescription = null,
                    tint = if (isFromMe) Color(0xFFF57F17) else MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Mutual Consent Chat Deletion",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            if (isFromMe) {
                Text(
                    text = "You requested to clear this chat. Waiting for $partnerName to accept. Both of you must agree before messages can be deleted.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 16.sp
                )
                Spacer(modifier = Modifier.height(10.dp))
                OutlinedButton(
                    onClick = onCancel,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.height(36.dp)
                ) {
                    Text("Cancel Request", fontSize = 12.sp)
                }
            } else {
                Text(
                    text = "$partnerName requested to clear the chat history. Under mutual protection, deletion will only happen if you accept.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                    lineHeight = 16.sp
                )
                Spacer(modifier = Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = onAccept,
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.height(36.dp)
                    ) {
                        Text("Accept & Clear Chat", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                    OutlinedButton(
                        onClick = onDecline,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.height(36.dp)
                    ) {
                        Text("Decline & Keep", fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

@Composable
fun StealthDisguiseNotesView(
    onRestore: () -> Unit
) {
    var tapCount by remember { mutableIntStateOf(0) }
    var lastTapTime by remember { mutableLongStateOf(0L) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .padding(24.dp)
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = {
                        val now = System.currentTimeMillis()
                        if (now - lastTapTime < 550) {
                            tapCount++
                        } else {
                            tapCount = 1
                        }
                        lastTapTime = now

                        if (tapCount >= 3) {
                            tapCount = 0
                            onRestore()
                        }
                    }
                )
            }
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Personal Checklist & Notes",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = DarkOnBackground
            )
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = Color(0xFFF0F0F2)
            ) {
                Text(
                    text = "Saved",
                    fontSize = 11.sp,
                    color = Color.Gray,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        val dummyTasks = listOf(
            "Weekly Groceries: Sourdough bread, Oat milk, Avocados, Olive oil" to true,
            "Office: Review quarterly budget presentation by 3:00 PM" to false,
            "Home Chores: Clean kitchen, change air filter" to true,
            "Reminder: Pick up pharmacy prescription on the way home" to false,
            "Car: Check tire pressure and washer fluid next Saturday" to false
        )

        dummyTasks.forEach { (task, checked) ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = if (checked) Icons.Default.CheckBox else Icons.Default.CheckBoxOutlineBlank,
                    contentDescription = null,
                    tint = if (checked) Color(0xFF4CAF50) else Color.LightGray,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = task,
                    fontSize = 13.sp,
                    color = if (checked) Color.Gray else DarkOnBackground
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))
        HorizontalDivider(color = Color(0xFFF0F0F2))
        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Meeting Notes Draft:",
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = DarkOnBackground
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "Team sprint planning completed. Next milestone scheduled for the 15th. Follow up with the project manager regarding the final deliverables.",
            fontSize = 12.sp,
            color = DarkOnSurfaceVariant,
            lineHeight = 18.sp
        )

        Spacer(modifier = Modifier.weight(1f))

        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "• Triple-tap anywhere to restore previous chat •",
                fontSize = 11.sp,
                color = Color.LightGray,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
fun BouncingDots() {
    val infiniteTransition = rememberInfiniteTransition(label = "bouncing_dots")
    val dot1 by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = -6f,
        animationSpec = infiniteRepeatable(
            animation = tween(400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "d1"
    )
    val dot2 by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = -6f,
        animationSpec = infiniteRepeatable(
            animation = tween(400, delayMillis = 150, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "d2"
    )
    val dot3 by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = -6f,
        animationSpec = infiniteRepeatable(
            animation = tween(400, delayMillis = 300, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "d3"
    )

    Row(
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(modifier = Modifier.offset(y = dot1.dp).size(6.dp).background(RoseGoldPrimary, CircleShape))
        Box(modifier = Modifier.offset(y = dot2.dp).size(6.dp).background(RoseGoldPrimary, CircleShape))
        Box(modifier = Modifier.offset(y = dot3.dp).size(6.dp).background(RoseGoldPrimary, CircleShape))
    }
}

private val dateSeparatorFormat = object : ThreadLocal<SimpleDateFormat>() {
    override fun initialValue(): SimpleDateFormat {
        return SimpleDateFormat("EEEE, MMMM d", Locale.getDefault())
    }
}

fun isSameDay(t1: Long, t2: Long): Boolean {
    val offset = 6 * 60 * 60 * 1000L
    val cal1 = java.util.Calendar.getInstance().apply { timeInMillis = t1 - offset }
    val cal2 = java.util.Calendar.getInstance().apply { timeInMillis = t2 - offset }
    return cal1.get(java.util.Calendar.YEAR) == cal2.get(java.util.Calendar.YEAR) &&
           cal1.get(java.util.Calendar.DAY_OF_YEAR) == cal2.get(java.util.Calendar.DAY_OF_YEAR)
}

fun formatDateSeparator(timestamp: Long): String {
    val now = System.currentTimeMillis()
    if (isSameDay(timestamp, now)) return "Today"
    if (isSameDay(timestamp, now - 86400000L)) return "Yesterday"
    
    val offset = 6 * 60 * 60 * 1000L
    return dateSeparatorFormat.get()?.format(Date(timestamp - offset)) ?: ""
}

@Composable
private fun AttachmentOptionItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    color: Color,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(12.dp)
    ) {
        Surface(
            shape = CircleShape,
            color = color.copy(alpha = 0.12f),
            modifier = Modifier.size(56.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    tint = color,
                    modifier = Modifier.size(28.dp)
                )
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = label,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            color = Color(0xFF222228)
        )
    }
}



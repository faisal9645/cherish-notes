package com.example.ui.chat

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.ExperimentalFoundationApi
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
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.positionChange
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
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

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ChatScreen(
    viewModel: ChatViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToGallery: () -> Unit,
    onQuickDisguise: () -> Unit = {},
    onNavigateToHome: () -> Unit = {},
    onNavigateToProfile: () -> Unit = {},
    onLoggedOut: () -> Unit = {}
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val uiState by viewModel.uiState.collectAsState()
    val listState = rememberLazyListState()
    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f

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
    var isHandlingMedia by remember { mutableStateOf(false) }
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(maxItems = 10)
    ) { uris: List<Uri> ->
        if (isHandlingMedia) return@rememberLauncherForActivityResult
        val distinctUris = uris.distinct()
        if (distinctUris.isNotEmpty()) {
            isHandlingMedia = true
            if (distinctUris.size == 1) {
                viewModel.sendMediaFile(distinctUris[0], MessageType.IMAGE)
            } else {
                viewModel.sendMultipleImages(distinctUris)
            }
            // Reset after a brief window
            scope.launch {
                delay(2000)
                isHandlingMedia = false
            }
        }
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

    // Issue 13: Only auto-scroll to bottom when a genuinely new message arrives
    // AND the user is already near the bottom — don't hijack scroll mid-history.
    val prevMessageCount = remember { mutableIntStateOf(uiState.messages.size) }
    LaunchedEffect(uiState.messages.size) {
        val isNearBottom = listState.firstVisibleItemIndex <= 3
        val newestMessage = uiState.messages.lastOrNull()
        val iSentIt = newestMessage?.senderId == uiState.currentUser?.id

        if (uiState.messages.size > prevMessageCount.intValue) {
            if (isNearBottom || iSentIt) {
                listState.animateScrollToItem(0)
            }
        }
        prevMessageCount.intValue = uiState.messages.size
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
    val isPartnerOnline = uiState.isPartnerOnline
    val currentUserId = viewModel.uiState.value.currentUser?.id ?: "user_me"

    // Issue 13: Batch read-marking outside LazyColumn — prevents Firestore writes during scroll
    val unreadIds = remember(uiState.messages, currentUserId) {
        uiState.messages
            .filter { it.senderId != currentUserId && it.getTypedStatus() != com.example.data.model.MessageStatus.READ && !it.isDeleted }
            .map { it.id }
    }
    LaunchedEffect(unreadIds) {
        unreadIds.forEach { viewModel.markMessageAsRead(it) }
    }

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

    // Filter messages for search query and starred filter - all messages preserved
    val displayedMessages = remember(uiState.messages, uiState.searchQuery, uiState.filterStarredOnly, uiState.showPreviousChats) {
        var list = uiState.messages
        if (uiState.filterStarredOnly) {
            list = list.filter { it.isStarred }
        }
        if (!uiState.showPreviousChats) {
            val now = java.util.Calendar.getInstance()
            if (now.get(java.util.Calendar.HOUR_OF_DAY) < 6) {
                now.add(java.util.Calendar.DAY_OF_YEAR, -1)
            }
            now.set(java.util.Calendar.HOUR_OF_DAY, 6)
            now.set(java.util.Calendar.MINUTE, 0)
            now.set(java.util.Calendar.SECOND, 0)
            now.set(java.util.Calendar.MILLISECOND, 0)
            val today6am = now.timeInMillis
            list = list.filter { it.timestamp >= today6am }
        }
        if (uiState.searchQuery.isNotBlank()) {
            val q = uiState.searchQuery.trim()
            list = list.filter { it.text.contains(q, ignoreCase = true) }
        }
        list
    }

    var highlightedMessageId by remember { mutableStateOf<String?>(null) }

    // User presence: Show as online only while actively inside the Chat tab (Issue 5)
    DisposableEffect(Unit) {
        viewModel.setInChatTab(true)
        onDispose {
            viewModel.setInChatTab(false)
        }
    }

    // Android back button & gesture: return directly to normal Notes app (Issue Back Button)
    BackHandler {
        if (uiState.isSearching) {
            viewModel.setSearching(false)
        } else if (showChatMenu) {
            showChatMenu = false
        } else {
            onQuickDisguise()
        }
    }

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
                // Sensor listener kept without auto-switching to Notes (Issue 8 & 11)
            }
            override fun onAccuracyChanged(sensor: android.hardware.Sensor?, accuracy: Int) {}
        }

        accelerometer?.let {
            sensorManager.registerListener(listener, it, android.hardware.SensorManager.SENSOR_DELAY_UI)
        }

        onDispose {
            sensorManager?.unregisterListener(listener)
            viewModel.cancelVoiceRecording()
            viewModel.safeStopRecordingForBackground()
            viewModel.hideSecretHistory()
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            Column {
                TopAppBar(
                    title = {
                    if (uiState.isSearching) {
                        OutlinedTextField(
                            value = uiState.searchQuery,
                            onValueChange = { viewModel.setSearchQuery(it) },
                            placeholder = { 
                                Text(
                                    if (uiState.chatExperienceMode == com.example.ui.chat.ChatExperienceMode.PRIVATE) 
                                        "Search messages..." 
                                    else 
                                        "Search our chat..."
                                ) 
                            },
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
                        val isPrivate = uiState.chatExperienceMode == com.example.ui.chat.ChatExperienceMode.PRIVATE
                        
                        if (isPrivate) {
                            // ChatGPT screen style: Hide title and lock icon completely
                        } else {
                            val isPartnerOnline = uiState.isPartnerOnline
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(10.dp))
                                    .clickable { onNavigateToProfile() }
                                    .padding(start = 4.dp, end = 8.dp, top = 4.dp, bottom = 4.dp)
                            ) {
                                AvatarView(
                                    photoUrl = partner?.photoUrl,
                                    name = partnerName,
                                    size = 46.dp,
                                    isOnline = isPartnerOnline,
                                    showOnlineBadge = !partnerHasCheckAfter
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(
                                    modifier = Modifier.widthIn(max = 220.dp),
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    Text(
                                        text = partnerName,
                                        fontSize = 19.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        color = if (isDark) Color.White else Color(0xFF0F172A)
                                    )
                                    Text(
                                        text = if (partnerHasCheckAfter) {
                                            if (headerRemaining.startsWith("✨")) "✨ Reconnecting now"
                                            else "🌙 Quiet time (${CheckAfterHelper.formatTargetTime(partnerCheckAfterTarget)})"
                                        } else if (isPartnerOnline) {
                                            "Online"
                                        } else {
                                            val lastSeen = partner?.lastSeen ?: 0L
                                            if (lastSeen > 0L) {
                                                val diffSec = ((System.currentTimeMillis() - lastSeen) / 1000).coerceAtLeast(0)
                                                when {
                                                    diffSec < 60 -> "last seen just now"
                                                    diffSec < 3600 -> "last seen ${diffSec / 60}m ago"
                                                    diffSec < 86400 -> "last seen ${diffSec / 3600}h ago"
                                                    else -> "offline"
                                                }
                                            } else {
                                                "offline"
                                            }
                                        },
                                        fontSize = 14.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        color = if (partnerHasCheckAfter) {
                                            Color(0xFF3B82F6)
                                        } else if (isPartnerOnline) {
                                            OnlineGreen
                                        } else {
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                        },
                                        fontWeight = FontWeight.Medium
                                    )
                                }
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
                                onQuickDisguise()
                            }
                        },
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("chat_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = if (uiState.chatExperienceMode == com.example.ui.chat.ChatExperienceMode.PRIVATE)
                                MaterialTheme.colorScheme.onSurface
                            else
                                MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                },
                actions = {
                    if (uiState.isSearching) {
                        IconButton(
                            onClick = { viewModel.setSearching(false) },
                            modifier = Modifier.size(42.dp)
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "Close search", modifier = Modifier.size(24.dp))
                        }
                    } else {
                        val isPrivate = uiState.chatExperienceMode == com.example.ui.chat.ChatExperienceMode.PRIVATE
                        val iconTint = if (isPrivate) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
                        if (!isPrivate) {
                            IconButton(
                                onClick = { viewModel.setSearching(true) },
                                modifier = Modifier
                                    .size(46.dp)
                                    .testTag("chat_search_button")
                            ) {
                                Icon(Icons.Default.Search, contentDescription = "Search messages", tint = iconTint, modifier = Modifier.size(26.dp))
                            }
                            IconButton(
                                onClick = onNavigateToGallery,
                                modifier = Modifier
                                    .size(46.dp)
                                    .testTag("chat_gallery_button")
                            ) {
                                Icon(Icons.Outlined.PhotoLibrary, contentDescription = "Couple Media Gallery", tint = iconTint, modifier = Modifier.size(26.dp))
                            }
                            IconButton(
                                onClick = { viewModel.openCheckAfterSheet() },
                                modifier = Modifier
                                    .size(46.dp)
                                    .testTag("chat_check_after_button")
                            ) {
                                Icon(
                                    imageVector = if (partnerHasCheckAfter || iHaveCheckAfter) Icons.Filled.HourglassTop else Icons.Outlined.HourglassTop,
                                    contentDescription = "Check After Timer",
                                    tint = if (partnerHasCheckAfter || iHaveCheckAfter) MaterialTheme.colorScheme.primary else iconTint,
                                    modifier = Modifier.size(26.dp)
                                )
                            }
                        }

                        Box {
                            IconButton(
                                onClick = { showChatMenu = true },
                                modifier = Modifier
                                    .size(46.dp)
                                    .testTag("chat_more_menu_button")
                            ) {
                                Icon(Icons.Default.MoreVert, contentDescription = "More options", tint = iconTint, modifier = Modifier.size(26.dp))
                            }
                            DropdownMenu(
                                expanded = showChatMenu,
                                onDismissRequest = { showChatMenu = false }
                            ) {
                                if (!isPrivate) {
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
                                    text = { 
                                        Text(
                                            if (uiState.chatExperienceMode == com.example.ui.chat.ChatExperienceMode.PRIVATE) 
                                                "Settings" 
                                            else 
                                                "Profile & Partner Settings"
                                        ) 
                                    },
                                    onClick = {
                                        showChatMenu = false
                                        onNavigateToProfile()
                                    },
                                    leadingIcon = {
                                        Icon(
                                            Icons.Default.Person,
                                            null,
                                            tint = if (isPrivate) MaterialTheme.colorScheme.onSurfaceVariant else RoseGoldPrimary
                                        )
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Log Out", color = MaterialTheme.colorScheme.error) },
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
            HorizontalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
            }
        },
        bottomBar = {
            val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
            val barBg = if (isDark) TrueDarkSurface else Color.White
            Surface(
                color = barBg,
                tonalElevation = 0.dp,
                shadowElevation = 8.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(barBg)
                        .navigationBarsPadding()
                        .imePadding()
                ) {
                // Partner typing / recording animated bubble
                AnimatedVisibility(
                    visible = uiState.isPartnerRecordingAudio || uiState.isPartnerTyping,
                    enter = slideInVertically(initialOffsetY = { it / 2 }) + fadeIn(),
                    exit = slideOutVertically(targetOffsetY = { it / 2 }) + fadeOut()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.Start
                    ) {
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                            shape = RoundedCornerShape(18.dp, 18.dp, 18.dp, 4.dp),
                            shadowElevation = 0.dp
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                val isPrivate = uiState.chatExperienceMode == com.example.ui.chat.ChatExperienceMode.PRIVATE
                                if (uiState.isPartnerRecordingAudio) {
                                    Icon(
                                        imageVector = Icons.Default.Mic,
                                        contentDescription = null,
                                        tint = if (isPrivate) MaterialTheme.colorScheme.onSurfaceVariant else RoseGoldPrimary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = if (isPrivate)
                                            "recording audio..."
                                        else
                                            "$partnerName is recording...",
                                        fontSize = 13.sp,
                                        color = if (isPrivate) MaterialTheme.colorScheme.onSurfaceVariant else RoseGoldPrimary,
                                        fontWeight = FontWeight.Medium
                                    )
                                } else {
                                    if (!isPrivate) {
                                        Text(
                                            text = "$partnerName is typing",
                                            fontSize = 13.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            fontWeight = FontWeight.Medium
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                    }
                                    BouncingDots(color = if (isPrivate) MaterialTheme.colorScheme.onSurfaceVariant else RoseGoldPrimary)
                                }
                            }
                        }
                    }
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
                    myPhotoUrl = myUser?.photoUrl,
                    myName = myUser?.displayName ?: "Me",
                    placeholder = when {
                        uiState.isStealthCurtainActive -> "Add a note..."
                        uiState.chatExperienceMode == com.example.ui.chat.ChatExperienceMode.PRIVATE -> "Type a message..."
                        else -> "Type something sweet to $partnerName... ❤️"
                    },
                    isPrivateMode = (uiState.chatExperienceMode == com.example.ui.chat.ChatExperienceMode.PRIVATE)
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

            // Issue 8: Check-After banner — FIXED position below profile header,
            // NOT inside LazyColumn. The banner stays pinned while messages scroll underneath.
            if (!uiState.isStealthCurtainActive && uiState.chatExperienceMode != com.example.ui.chat.ChatExperienceMode.PRIVATE) {
                if (partnerHasCheckAfter) {
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
                } else if (iHaveCheckAfter) {
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

            // Mutual Consent Deletion Banner — fixed position (not scrolling)
            if (!uiState.isStealthCurtainActive && uiState.deletionRequest != null) {
                MutualConsentDeletionBanner(
                    request = uiState.deletionRequest!!,
                    isFromMe = (uiState.deletionRequest!!.requestedByUserId == currentUserId),
                    partnerName = partnerName,
                    onAccept = { viewModel.acceptMutualChatDeletion() },
                    onDecline = { viewModel.declineMutualChatDeletion() },
                    onCancel = { viewModel.cancelMutualChatDeletion() }
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                if (!uiState.isStealthCurtainActive && uiState.chatExperienceMode != com.example.ui.chat.ChatExperienceMode.PRIVATE) {
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
                val reversedMessages = remember(displayedMessages) { displayedMessages.reversed() }
                
                val hasLoadedPreviousChats = remember(displayedMessages) { displayedMessages.any { !isToday(it.timestamp) } }
                val hasTodayMessages = remember(displayedMessages) { displayedMessages.any { isToday(it.timestamp) } }

                val canLoadMore = uiState.showPreviousChats && !uiState.isPaginationExhausted && !uiState.isLoadingMore && displayedMessages.isNotEmpty()
                val shouldLoadMore by remember(canLoadMore, hasLoadedPreviousChats) {
                    derivedStateOf {
                        if (!canLoadMore) return@derivedStateOf false
                        val totalItems = listState.layoutInfo.totalItemsCount
                        val lastVisibleItem = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
                        if (!hasLoadedPreviousChats) {
                            totalItems > 0 && lastVisibleItem >= totalItems - 1
                        } else {
                            totalItems > 0 && lastVisibleItem >= totalItems - 4
                        }
                    }
                }
                LaunchedEffect(shouldLoadMore) {
                    if (shouldLoadMore) {
                        viewModel.loadMoreMessages()
                    }
                }

                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize().pointerInput(Unit) {
                        detectTapGestures(
                            onDoubleTap = { onQuickDisguise() }
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

                        Column(
                            modifier = Modifier.animateItem(
                                fadeInSpec = null,
                                fadeOutSpec = null,
                                placementSpec = spring(
                                    dampingRatio = Spring.DampingRatioNoBouncy,
                                    stiffness = Spring.StiffnessMediumLow
                                )
                            )
                        ) {
                            val dateSep = formatDateSeparator(message.timestamp)
                            if (isFirstOfDay && dateSep.isNotEmpty()) {
                                DateSeparatorBadge(
                                    dateText = dateSep,
                                    isPrivateMode = (uiState.chatExperienceMode == com.example.ui.chat.ChatExperienceMode.PRIVATE),
                                    isDark = isDark
                                )
                            }

                            // Read marking is handled in the batched LaunchedEffect above the Scaffold.
                            // Do NOT put Firestore writes inside LazyColumn items — they fire on every scroll.

                            val isPrivate = uiState.chatExperienceMode == com.example.ui.chat.ChatExperienceMode.PRIVATE
                            val isHighlighted = (highlightedMessageId == message.id)
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp)
                                    .then(
                                        if (isHighlighted) {
                                            val hColor = if (isPrivate) (if (isDark) Color(0xFF6B7280) else Color(0xFF9CA3AF)) else RoseGoldPrimary
                                            Modifier
                                                .background(hColor.copy(alpha = 0.15f), RoundedCornerShape(16.dp))
                                                .border(2.dp, hColor, RoundedCornerShape(16.dp))
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
                                    },
                                    isPrivateMode = isPrivate,
                                    senderPhotoUrl = if (isFromMe) uiState.currentUser?.photoUrl else uiState.partnerUser?.photoUrl
                                )
                            }
                        }
                    }

                    // 1. Loading older messages indicator (shown at top of list while paginating)
                    if (uiState.isLoadingMore) {
                        item(key = "pagination_loader", contentType = "loader") {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 12.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(14.dp),
                                    color = if (isDark) Color(0xFF1E2430).copy(alpha = 0.9f) else Color(0xFFF1F5F9).copy(alpha = 0.95f),
                                    border = BorderStroke(0.8.dp, if (isDark) Color(0xFF334155).copy(alpha = 0.6f) else Color(0xFFCBD5E1)),
                                    shadowElevation = 2.dp
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(13.dp),
                                            strokeWidth = 2.dp,
                                            color = RoseGoldPrimary
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = "Loading earlier messages...",
                                            fontSize = 11.5.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // 2. Load Previous Chats Entry Button
                    // When previous chats have not been loaded yet, provide a sleek button at the top of today's chat
                    if (!hasLoadedPreviousChats && uiState.hasPreviousChatsAvailable && !uiState.isPaginationExhausted && hasTodayMessages && !uiState.isLoadingMore) {
                        item(key = "load_previous_chats_entry", contentType = "previous_entry") {
                            val isPrivate = uiState.chatExperienceMode == com.example.ui.chat.ChatExperienceMode.PRIVATE
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 24.dp, vertical = 12.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(16.dp),
                                    color = if (isPrivate) {
                                        if (isDark) Color(0xFF232428).copy(alpha = 0.9f) else Color(0xFFF3F4F6)
                                    } else {
                                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = if (isDark) 0.85f else 0.92f)
                                    },
                                    border = BorderStroke(
                                        1.dp,
                                        if (isPrivate) (if (isDark) Color(0xFF374151) else Color(0xFFE5E7EB)) else RoseGoldPrimary.copy(alpha = 0.35f)
                                    ),
                                    shadowElevation = 1.dp,
                                    modifier = Modifier.clickable {
                                        viewModel.loadMoreMessages()
                                    }
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.History,
                                            contentDescription = null,
                                            tint = if (isPrivate) (if (isDark) Color(0xFF9CA3AF) else Color(0xFF4B5563)) else RoseGoldPrimary,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = "Load Previous Chats (Yesterday & earlier)",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = if (isPrivate) (if (isDark) Color(0xFFD1D5DB) else Color(0xFF374151)) else RoseGoldPrimary
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // 3. If earlier chats hidden by user privacy setting
                    if (!uiState.showPreviousChats && displayedMessages.isNotEmpty()) {
                        item(key = "earlier_chats_hidden", contentType = "hidden_notice") {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 20.dp, vertical = 12.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(14.dp),
                                    color = if (isDark) Color(0xFF1E2430).copy(alpha = 0.9f) else Color(0xFFF1F5F9).copy(alpha = 0.95f),
                                    border = BorderStroke(0.8.dp, if (isDark) Color(0xFF334155).copy(alpha = 0.6f) else Color(0xFFCBD5E1)),
                                    modifier = Modifier.clickable {
                                        viewModel.setShowPreviousChats(true)
                                    }
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.History,
                                            contentDescription = null,
                                            tint = RoseGoldPrimary,
                                            modifier = Modifier.size(15.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = "Earlier chats hidden by privacy • Tap to show",
                                            fontSize = 11.5.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = RoseGoldPrimary
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // 4. Strictly 2-Person Beginning of Chat Banner
                    // Shown ONLY when the user has genuinely reached the beginning of all messages
                    // (i.e. isPaginationExhausted is true, or chat is empty)
                    if (uiState.isPaginationExhausted || displayedMessages.isEmpty()) {
                        item(key = "info_card", contentType = "info_card") {
                            StrictlyPrivateChatBeginningBanner(
                                currentUser = uiState.currentUser,
                                partnerUser = uiState.partnerUser,
                                partnerName = partnerName,
                                isPrivateMode = (uiState.chatExperienceMode == com.example.ui.chat.ChatExperienceMode.PRIVATE),
                                isDark = isDark
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
                val isPrivate = uiState.chatExperienceMode == com.example.ui.chat.ChatExperienceMode.PRIVATE
                SmallFloatingActionButton(
                    onClick = {
                        scope.launch {
                            if (displayedMessages.isNotEmpty()) {
                                listState.animateScrollToItem(0)
                            }
                        }
                    },
                    containerColor = if (isPrivate) (if (isDark) Color(0xFF2E2F33) else Color(0xFFE5E7EB)) else MaterialTheme.colorScheme.primaryContainer,
                    contentColor = if (isPrivate) (if (isDark) Color(0xFFECECEC) else Color(0xFF1F2937)) else MaterialTheme.colorScheme.onPrimaryContainer,
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
                    color = MaterialTheme.colorScheme.onSurface,
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
    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = if (isDark) MaterialTheme.colorScheme.surfaceVariant else if (isFromMe) Color(0xFFFFF8E1) else Color(0xFFFFEBEE)),
        border = BorderStroke(1.dp, if (isDark) MaterialTheme.colorScheme.outlineVariant else if (isFromMe) Color(0xFFFFE082) else Color(0xFFFFCDD2))
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
                color = MaterialTheme.colorScheme.onBackground
            )
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.outlineVariant
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
                    color = if (checked) Color.Gray else MaterialTheme.colorScheme.onBackground
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Meeting Notes Draft:",
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "Team sprint planning completed. Next milestone scheduled for the 15th. Follow up with the project manager regarding the final deliverables.",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
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
fun BouncingDots(color: Color = RoseGoldPrimary) {
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
        Box(modifier = Modifier.offset(y = dot1.dp).size(6.dp).background(color, CircleShape))
        Box(modifier = Modifier.offset(y = dot2.dp).size(6.dp).background(color, CircleShape))
        Box(modifier = Modifier.offset(y = dot3.dp).size(6.dp).background(color, CircleShape))
    }
}

fun isSameDay(t1: Long, t2: Long): Boolean {
    if (t1 <= 0L || t2 <= 0L) return false
    val cal1 = java.util.Calendar.getInstance().apply { timeInMillis = t1 }
    val cal2 = java.util.Calendar.getInstance().apply { timeInMillis = t2 }
    return cal1.get(java.util.Calendar.ERA) == cal2.get(java.util.Calendar.ERA) &&
           cal1.get(java.util.Calendar.YEAR) == cal2.get(java.util.Calendar.YEAR) &&
           cal1.get(java.util.Calendar.DAY_OF_YEAR) == cal2.get(java.util.Calendar.DAY_OF_YEAR)
}

fun isToday(timestamp: Long): Boolean {
    if (timestamp <= 0L) return false
    val calMsg = java.util.Calendar.getInstance().apply { timeInMillis = timestamp }
    val calToday = java.util.Calendar.getInstance()
    return calMsg.get(java.util.Calendar.ERA) == calToday.get(java.util.Calendar.ERA) &&
           calMsg.get(java.util.Calendar.YEAR) == calToday.get(java.util.Calendar.YEAR) &&
           calMsg.get(java.util.Calendar.DAY_OF_YEAR) == calToday.get(java.util.Calendar.DAY_OF_YEAR)
}

fun isYesterday(timestamp: Long): Boolean {
    if (timestamp <= 0L) return false
    val calMsg = java.util.Calendar.getInstance().apply { timeInMillis = timestamp }
    val calYesterday = java.util.Calendar.getInstance().apply {
        add(java.util.Calendar.DAY_OF_YEAR, -1)
    }
    return calMsg.get(java.util.Calendar.ERA) == calYesterday.get(java.util.Calendar.ERA) &&
           calMsg.get(java.util.Calendar.YEAR) == calYesterday.get(java.util.Calendar.YEAR) &&
           calMsg.get(java.util.Calendar.DAY_OF_YEAR) == calYesterday.get(java.util.Calendar.DAY_OF_YEAR)
}

fun formatDateSeparator(timestamp: Long): String {
    if (timestamp <= 0L) return ""
    if (isToday(timestamp)) return "Today"
    if (isYesterday(timestamp)) return "Yesterday"
    
    val calMsg = java.util.Calendar.getInstance().apply { timeInMillis = timestamp }
    val calNow = java.util.Calendar.getInstance()
    
    val sameYear = calMsg.get(java.util.Calendar.YEAR) == calNow.get(java.util.Calendar.YEAR)
    val pattern = if (sameYear) "EEEE, MMMM d" else "EEEE, MMMM d, yyyy"
    return SimpleDateFormat(pattern, Locale.getDefault()).format(Date(timestamp))
}

@Composable
fun DateSeparatorBadge(
    dateText: String,
    isPrivateMode: Boolean,
    isDark: Boolean,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        val lineColor = if (isPrivateMode) {
            if (isDark) Color(0xFF2A2B30) else Color(0xFFE5E7EB)
        } else {
            if (isDark) Color(0xFF334155).copy(alpha = 0.5f) else Color(0xFFCBD5E1).copy(alpha = 0.7f)
        }

        Box(
            modifier = Modifier
                .weight(1f)
                .height(0.8.dp)
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(Color.Transparent, lineColor)
                    )
                )
        )

        Surface(
            shape = RoundedCornerShape(12.dp),
            color = if (isPrivateMode) {
                if (isDark) Color(0xFF232428).copy(alpha = 0.95f) else Color(0xFFF3F4F6)
            } else {
                if (isDark) Color(0xFF1E293B).copy(alpha = 0.92f) else Color(0xFFF1F5F9).copy(alpha = 0.95f)
            },
            border = BorderStroke(
                0.8.dp,
                if (isPrivateMode) {
                    if (isDark) Color(0xFF374151).copy(alpha = 0.6f) else Color(0xFFE5E7EB)
                } else {
                    if (isDark) Color(0xFF475569).copy(alpha = 0.4f) else Color(0xFFE2E8F0)
                }
            ),
            shadowElevation = 1.dp,
            modifier = Modifier.padding(horizontal = 10.dp)
        ) {
            Text(
                text = dateText,
                fontSize = 11.5.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (isPrivateMode) {
                    if (isDark) Color(0xFF9CA3AF) else Color(0xFF4B5563)
                } else {
                    if (isDark) Color(0xFF94A3B8) else Color(0xFF475569)
                },
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
            )
        }

        Box(
            modifier = Modifier
                .weight(1f)
                .height(0.8.dp)
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(lineColor, Color.Transparent)
                    )
                )
        )
    }
}

@Composable
fun StrictlyPrivateChatBeginningBanner(
    currentUser: com.example.data.model.User?,
    partnerUser: com.example.data.model.User?,
    partnerName: String,
    isPrivateMode: Boolean,
    isDark: Boolean,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (isPrivateMode) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = if (isDark) Color(0xFF1E2024).copy(alpha = 0.85f) else Color(0xFFF3F4F6),
                border = BorderStroke(1.dp, if (isDark) Color(0xFF2E3036) else Color(0xFFE5E7EB)),
                modifier = Modifier.fillMaxWidth(0.92f)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = null,
                            tint = if (isDark) Color(0xFF9CA3AF) else Color(0xFF4B5563),
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Strictly 2-Person Private Channel",
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.5.sp,
                            color = if (isDark) Color(0xFFE5E7EB) else Color(0xFF1F2937)
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Messages, voice notes, and media are strictly between you and $partnerName. End-to-end encrypted.",
                        fontSize = 11.5.sp,
                        color = if (isDark) Color(0xFF9CA3AF) else Color(0xFF6B7280),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        lineHeight = 16.sp
                    )
                }
            }
        } else {
            Surface(
                shape = RoundedCornerShape(22.dp),
                color = if (isDark) {
                    Color(0xFF151D2A).copy(alpha = 0.88f)
                } else {
                    Color(0xFFFFFFFF).copy(alpha = 0.92f)
                },
                border = BorderStroke(
                    1.dp,
                    if (isDark) Color(0xFF2B3954).copy(alpha = 0.6f) else Color(0xFFE2E8F0)
                ),
                shadowElevation = 2.dp,
                modifier = Modifier.fillMaxWidth(0.95f)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Overlapping connected avatars
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.padding(bottom = 8.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            AvatarView(
                                photoUrl = currentUser?.photoUrl,
                                name = currentUser?.displayName ?: "Me",
                                size = 48.dp,
                                showOnlineBadge = false
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            AvatarView(
                                photoUrl = partnerUser?.photoUrl,
                                name = partnerName,
                                size = 48.dp,
                                showOnlineBadge = false
                            )
                        }

                        // Center glowing lock badge
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.surface,
                            border = BorderStroke(1.5.dp, RoseGoldPrimary),
                            shadowElevation = 3.dp,
                            modifier = Modifier
                                .size(24.dp)
                                .align(Alignment.BottomCenter)
                                .offset(y = 6.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Lock,
                                    contentDescription = null,
                                    tint = RoseGoldPrimary,
                                    modifier = Modifier.size(12.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = "Strictly 2-Person Private Channel",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.5.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = "Everything shared in this conversation is strictly between you and $partnerName. Zero third parties can ever join or view your messages.",
                        fontSize = 11.5.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        lineHeight = 16.sp,
                        modifier = Modifier.padding(horizontal = 6.dp)
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = RoseGoldPrimary.copy(alpha = if (isDark) 0.18f else 0.10f),
                        border = BorderStroke(0.6.dp, RoseGoldPrimary.copy(alpha = 0.35f))
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.5.dp)
                        ) {
                            Text(
                                text = "🔒 End-to-End Private Space",
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = RoseGoldPrimary
                            )
                        }
                    }
                }
            }
        }
    }
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
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}




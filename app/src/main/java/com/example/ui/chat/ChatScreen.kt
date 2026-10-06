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
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
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
import androidx.compose.ui.zIndex
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
    val listState = rememberLazyListState(
        initialFirstVisibleItemIndex = viewModel.savedScrollIndex,
        initialFirstVisibleItemScrollOffset = viewModel.savedScrollOffset
    )

    DisposableEffect(Unit) {
        onDispose {
            viewModel.savedScrollIndex = listState.firstVisibleItemIndex
            viewModel.savedScrollOffset = listState.firstVisibleItemScrollOffset
        }
    }
    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f

    var composerText by remember { mutableStateOf("") }
    var editingMessage by remember { mutableStateOf<Message?>(null) }
    var editDialogText by remember { mutableStateOf("") }
    var showDeleteConfirmDialog by remember { mutableStateOf<Message?>(null) }
    var showClearChatDialog by remember { mutableStateOf(false) }
    var showFullProfilePicViewer by remember { mutableStateOf(false) }
    var showChatMenu by remember { mutableStateOf(false) }
    var showVideoNoteRecorder by remember { mutableStateOf(false) }
    var showHeartbeatTouch by remember { mutableStateOf(false) }
    var showMoodPicker by remember { mutableStateOf(false) }

    val searchFocusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current

    // Automatically open keyboard and focus input when search is opened
    LaunchedEffect(uiState.isSearching) {
        if (uiState.isSearching) {
            kotlinx.coroutines.delay(120)
            try {
                searchFocusRequester.requestFocus()
                keyboardController?.show()
            } catch (_: Exception) {}
        }
    }

    // Automatically open Heartbeat Touch dialog when partner starts heartbeat touching
    LaunchedEffect(uiState.isPartnerHeartTouching) {
        if (uiState.isPartnerHeartTouching && !showHeartbeatTouch) {
            showHeartbeatTouch = true
        }
    }

    val currentPlayingId by viewModel.voicePlayerHelper.currentlyPlayingId.collectAsState()
    val isAudioPlaying by viewModel.voicePlayerHelper.isPlaying.collectAsState()
    val audioProgress by viewModel.voicePlayerHelper.playbackProgress.collectAsState()
    val isRecordingVoice by viewModel.voiceRecorderHelper.isRecording.collectAsState()
    val recordingDurationSec by viewModel.voiceRecorderHelper.recordingDurationSec.collectAsState()
    val recordingAmplitudes by viewModel.voiceRecorderHelper.amplitudes.collectAsState()

    // Activity result launchers for media selection (supports multiple photo selection)
    var isHandlingMedia by remember { mutableStateOf(false) }
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(maxItems = 20)
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




    // Video picker launcher for sharing videos
    val videoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            viewModel.sendMediaFile(uri, MessageType.VIDEO)
        }
    }
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

    // Only auto-scroll to bottom when a genuinely new message arrives while near bottom — never hijack scroll mid-history or on tab switch
    val prevNewestMessageTimestamp = rememberSaveable {
        mutableLongStateOf(uiState.messages.maxOfOrNull { it.timestamp } ?: 0L)
    }
    LaunchedEffect(uiState.messages) {
        val newestMessage = uiState.messages.maxByOrNull { it.timestamp }
        if (newestMessage != null) {
            if (prevNewestMessageTimestamp.longValue == 0L) {
                // Initial message load: record timestamp without hijacking user's scroll position
                prevNewestMessageTimestamp.longValue = newestMessage.timestamp
            } else if (newestMessage.timestamp > prevNewestMessageTimestamp.longValue) {
                val isNearBottom = listState.firstVisibleItemIndex <= 1
                val iSentIt = newestMessage.senderId == uiState.currentUser?.id
                if (isNearBottom || iSentIt) {
                    listState.animateScrollToItem(0)
                }
                prevNewestMessageTimestamp.longValue = newestMessage.timestamp
            }
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
                kotlinx.coroutines.delay(10_000L)
            }
        }
    }
    val headerRemaining = remember(headerTicker, partnerCheckAfterTarget) {
        if (partnerHasCheckAfter) CheckAfterHelper.calculateRemaining(partnerCheckAfterTarget).first else ""
    }

    // Filter messages for search query and starred filter - all messages preserved and displayed based on pagination scroll
    val displayedMessages = remember(
        uiState.messages,
        uiState.searchQuery,
        uiState.filterStarredOnly,
        uiState.temporaryClearTimestamp
    ) {
        var list = uiState.messages.filter { !it.isDeleted }
        if (uiState.filterStarredOnly) {
            list = list.filter { it.isStarred }
        }
        if (uiState.temporaryClearTimestamp > 0L) {
            list = list.filter { it.timestamp > uiState.temporaryClearTimestamp }
        }
        if (uiState.searchQuery.isNotBlank()) {
            val q = uiState.searchQuery.trim()
            list = list.filter { it.text.contains(q, ignoreCase = true) }
        }
        list
    }

    var highlightedMessageId by remember { mutableStateOf<String?>(null) }

    val app = LocalContext.current.applicationContext as com.example.CherishApplication
    val isDisguiseActive by app.securityPreferences.isDisguiseActive.collectAsState()
    val isSideEmergencyExitEnabled by app.securityPreferences.isSideEmergencyExitEnabled.collectAsState()
    val sideEmergencyExitOpacity by app.securityPreferences.sideEmergencyExitOpacity.collectAsState()
    LaunchedEffect(isDisguiseActive) {
        if (isDisguiseActive) {
            viewModel.closeFullScreenMedia()
            viewModel.closeTheaterVideo()
            showFullProfilePicViewer = false
            showClearChatDialog = false
            showChatMenu = false
        }
    }

    // User presence: Show as online only while actively inside the Chat tab (Issue 5)
    DisposableEffect(Unit) {
        viewModel.setInChatTab(true)
        onDispose {
            viewModel.setInChatTab(false)
        }
    }

    // Android back button & gesture: handle in-app dialogs first, else return to disguise Notes
    BackHandler {
        if (uiState.theaterVideoId != null) {
            viewModel.closeTheaterVideo()
        } else if (uiState.fullScreenMediaUrl != null) {
            viewModel.closeFullScreenMedia()
        } else if (uiState.isSearching) {
            viewModel.setSearching(false)
        } else if (showChatMenu) {
            showChatMenu = false
        } else {
            onQuickDisguise()
        }
    }

    // Scroll to exact target message when navigating from gallery ("Show in chat")
    LaunchedEffect(uiState.targetScrollMessageId, displayedMessages.size) {
        val targetId = uiState.targetScrollMessageId
        if (targetId != null && displayedMessages.isNotEmpty()) {
            val reversedMessages = displayedMessages.reversed()
            val targetIndex = reversedMessages.indexOfFirst { it.id == targetId }
            if (targetIndex >= 0) {
                listState.animateScrollToItem(index = targetIndex, scrollOffset = -150)
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
        modifier = Modifier.pointerInput(Unit) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                var accX = 0f
                var accY = 0f
                var directionLocked = false
                var isHorizontal = false

                while (true) {
                    val event = awaitPointerEvent()
                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                    if (!change.pressed) break

                    val delta = change.positionChange()
                    accX += delta.x
                    accY += delta.y

                    if (!directionLocked && (kotlin.math.abs(accX) > 12f || kotlin.math.abs(accY) > 12f)) {
                        isHorizontal = kotlin.math.abs(accX) > kotlin.math.abs(accY) * 1.35f
                        directionLocked = true
                    }

                    if (directionLocked && isHorizontal) {
                        // Swiping right smoothly glides to Love & Us tab
                        if (accX > 55f) {
                            change.consume()
                            onNavigateToHome()
                            break
                        }
                        // Swiping left smoothly glides to Settings / Profile tab
                        if (accX < -55f) {
                            change.consume()
                            onNavigateToProfile()
                            break
                        }
                    }
                }
            }
        },
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
                                .focusRequester(searchFocusRequester)
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
                                    .padding(start = 2.dp, end = 6.dp, top = 2.dp, bottom = 2.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .padding(end = 4.dp, bottom = 2.dp)
                                        .clickable(
                                            interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                                            indication = null
                                        ) { showFullProfilePicViewer = true }
                                ) {
                                    AvatarView(
                                        photoUrl = partner?.photoUrl,
                                        name = partnerName,
                                        size = 42.dp,
                                        isOnline = isPartnerOnline,
                                        showOnlineBadge = !partnerHasCheckAfter
                                    )
                                }
                                Spacer(modifier = Modifier.width(10.dp))
                                Column(
                                    modifier = Modifier.weight(1f, fill = false),
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Text(
                                            text = partnerName,
                                            fontSize = 16.5.sp,
                                            lineHeight = 19.sp,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            color = if (isDark) Color.White else Color(0xFF0F172A)
                                        )

                                        // Partner Mood Pill
                                        val partnerMood = partner?.mood
                                        if (!partnerMood.isNullOrBlank()) {
                                            Surface(
                                                shape = RoundedCornerShape(10.dp),
                                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.75f),
                                                modifier = Modifier.clickable { showMoodPicker = true }
                                            ) {
                                                Text(
                                                    text = partnerMood,
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Medium,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    maxLines = 1,
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                )
                                            }
                                        }

                                        // Partner Battery Status Pill - Only shown when partner is online (no stale offline battery)
                                        val battery = partner?.batteryLevel
                                        if (uiState.isPartnerOnline && battery != null && battery in 0..100) {
                                            val isCharging = partner.isCharging
                                            val isLow = battery <= 20
                                            Surface(
                                                shape = RoundedCornerShape(8.dp),
                                                color = when {
                                                    isCharging -> Color(0xFF10B981).copy(alpha = 0.15f)
                                                    isLow -> Color(0xFFEF4444).copy(alpha = 0.15f)
                                                    else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
                                                }
                                            ) {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    modifier = Modifier.padding(horizontal = 4.5.dp, vertical = 2.dp)
                                                ) {
                                                    val icon = when {
                                                        isCharging -> "⚡"
                                                        isLow -> "🪫"
                                                        else -> "🔋"
                                                    }
                                                    Text(
                                                        text = "$icon $battery%",
                                                        fontSize = 9.5.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = when {
                                                            isCharging -> Color(0xFF10B981)
                                                            isLow -> Color(0xFFEF4444)
                                                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                                                        }
                                                    )
                                                }
                                            }
                                        }
                                    }
                                    val statusText = when {
                                        partnerHasCheckAfter -> {
                                            if (headerRemaining.startsWith("✨")) "✨ Reconnecting now"
                                            else "🌙 Quiet time (${CheckAfterHelper.formatTargetTime(partnerCheckAfterTarget)})"
                                        }
                                        uiState.isPartnerRecordingAudio -> "recording audio..."
                                        uiState.isPartnerTyping -> "typing..."
                                        isPartnerOnline -> "Online"
                                        else -> {
                                            val lastSeen = partner?.lastSeen ?: 0L
                                            if (lastSeen > 0L) {
                                                val diffSec = ((System.currentTimeMillis() - lastSeen) / 1000).coerceAtLeast(0)
                                                when {
                                                    diffSec < 60 -> "last seen just now"
                                                    diffSec < 3600 -> "last seen ${diffSec / 60}m ago"
                                                    diffSec < 86400 -> "last seen ${diffSec / 3600}h ago"
                                                    else -> {
                                                        val sdf = SimpleDateFormat("MMM d, h:mm a", Locale.getDefault())
                                                        "last seen ${sdf.format(Date(lastSeen))}"
                                                    }
                                                }
                                            } else {
                                                "offline"
                                            }
                                        }
                                    }
                                    Text(
                                        text = statusText,
                                        fontSize = 12.sp,
                                        lineHeight = 14.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        color = if (partnerHasCheckAfter) {
                                            Color(0xFF3B82F6)
                                        } else if (uiState.isPartnerRecordingAudio || uiState.isPartnerTyping || isPartnerOnline) {
                                            OnlineGreen
                                        } else {
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                        },
                                        fontWeight = FontWeight.Normal
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
                            .size(40.dp)
                            .testTag("chat_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = if (uiState.chatExperienceMode == com.example.ui.chat.ChatExperienceMode.PRIVATE)
                                MaterialTheme.colorScheme.onSurface
                            else
                                MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                },
                actions = {
                    if (uiState.isSearching) {
                        IconButton(
                            onClick = { viewModel.setSearching(false) },
                            modifier = Modifier.size(40.dp)
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "Close search", modifier = Modifier.size(23.dp))
                        }
                    } else {
                        val isPrivate = uiState.chatExperienceMode == com.example.ui.chat.ChatExperienceMode.PRIVATE
                        val iconTint = if (isPrivate) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
                        if (!isPrivate) {
                            IconButton(
                                onClick = { viewModel.setSearching(true) },
                                modifier = Modifier
                                    .size(40.dp)
                                    .testTag("chat_search_button")
                            ) {
                                Icon(Icons.Default.Search, contentDescription = "Search messages", tint = iconTint, modifier = Modifier.size(23.dp))
                            }
                            IconButton(
                                onClick = onNavigateToGallery,
                                modifier = Modifier
                                    .size(40.dp)
                                    .testTag("chat_gallery_button")
                            ) {
                                Icon(Icons.Outlined.PhotoLibrary, contentDescription = "Couple Media Gallery", tint = iconTint, modifier = Modifier.size(23.dp))
                            }
                            IconButton(
                                onClick = { viewModel.openCheckAfterSheet() },
                                modifier = Modifier
                                    .size(40.dp)
                                    .testTag("chat_check_after_button")
                            ) {
                                Icon(
                                    imageVector = if (partnerHasCheckAfter || iHaveCheckAfter) Icons.Filled.HourglassTop else Icons.Outlined.HourglassTop,
                                    contentDescription = "Check After Timer",
                                    tint = if (partnerHasCheckAfter || iHaveCheckAfter) MaterialTheme.colorScheme.primary else iconTint,
                                    modifier = Modifier.size(23.dp)
                                )
                            }

                            // Heartbeat Touch Icon Button
                            IconButton(
                                onClick = { showHeartbeatTouch = true },
                                modifier = Modifier
                                    .size(40.dp)
                                    .testTag("chat_heartbeat_touch_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Favorite,
                                    contentDescription = "Heartbeat Touch",
                                    tint = if (uiState.isPartnerHeartTouching) HeartRed else iconTint,
                                    modifier = Modifier.size(23.dp)
                                )
                            }
                        }

                        Box {
                            IconButton(
                                onClick = { showChatMenu = true },
                                modifier = Modifier
                                    .size(40.dp)
                                    .testTag("chat_more_menu_button")
                            ) {
                                Icon(Icons.Default.MoreVert, contentDescription = "More options", tint = iconTint, modifier = Modifier.size(23.dp))
                            }
                            DropdownMenu(
                                expanded = showChatMenu,
                                onDismissRequest = { showChatMenu = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Set Mood & Status 🥰") },
                                    onClick = {
                                        showChatMenu = false
                                        showMoodPicker = true
                                    },
                                    leadingIcon = {
                                        Icon(Icons.Outlined.Mood, null, tint = RoseGoldPrimary)
                                    }
                                )

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
                                    text = { Text("Clear Chat", color = MaterialTheme.colorScheme.error) },
                                    onClick = {
                                        showChatMenu = false
                                        showClearChatDialog = true
                                    },
                                    leadingIcon = {
                                        Icon(Icons.Default.DeleteSweep, null, tint = MaterialTheme.colorScheme.error)
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
                    onRecordVideoNote = {
                        val hasCam = ContextCompat.checkSelfPermission(
                            context,
                            Manifest.permission.CAMERA
                        ) == PackageManager.PERMISSION_GRANTED
                        val hasMic = ContextCompat.checkSelfPermission(
                            context,
                            Manifest.permission.RECORD_AUDIO
                        ) == PackageManager.PERMISSION_GRANTED
                        if (hasCam && hasMic) {
                            showVideoNoteRecorder = true
                        } else {
                            com.example.security.SecurityPreferences.getInstance(context).ignoreNextPause = true
                            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                            showVideoNoteRecorder = true
                        }
                    },
                    myPhotoUrl = myUser?.photoUrl,
                    myName = myUser?.displayName ?: "Me",
                    placeholder = when {
                        uiState.isStealthCurtainActive -> "Add a note..."
                        uiState.chatExperienceMode == com.example.ui.chat.ChatExperienceMode.PRIVATE -> "Type a message..."
                        else -> "Message $partnerName... ❤️"
                    },
                    isPrivateMode = (uiState.chatExperienceMode == com.example.ui.chat.ChatExperienceMode.PRIVATE)
                )

            }
        }
    },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        var totalDrag = 0f
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .pointerInput(uiState.isStealthCurtainActive, uiState.isSearching) {
                    if (uiState.isStealthCurtainActive || uiState.isSearching) return@pointerInput
                    detectHorizontalDragGestures(
                        onDragStart = { totalDrag = 0f },
                        onDragEnd = {
                            if (totalDrag > 120f) {
                                onNavigateToHome()
                            } else if (totalDrag < -120f) {
                                onNavigateToProfile()
                            }
                            totalDrag = 0f
                        },
                        onDragCancel = { totalDrag = 0f },
                        onHorizontalDrag = { change, dragAmount ->
                            change.consume()
                            totalDrag += dragAmount
                        }
                    )
                }
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

            // Partner Heart Touching Banner (when partner touches heart while user is in chat)
            AnimatedVisibility(visible = uiState.isPartnerHeartTouching && !showHeartbeatTouch) {
                Surface(
                    color = HeartRed.copy(alpha = 0.15f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, HeartRed.copy(alpha = 0.45f)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 4.dp)
                        .clickable { showHeartbeatTouch = true }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Favorite,
                            contentDescription = null,
                            tint = HeartRed,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "❤️ $partnerName is trying to touch your heart, touch $partnerName",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = HeartRed,
                            modifier = Modifier.weight(1f)
                        )
                        FilledTonalButton(
                            onClick = { showHeartbeatTouch = true },
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("Touch", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            // Pinned Message Banner
            AnimatedVisibility(visible = uiState.pinnedMessage != null && !uiState.isStealthCurtainActive) {
                uiState.pinnedMessage?.let { pinned ->
                    PinnedMessageBanner(
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

                val canLoadMore = !uiState.isPaginationExhausted && !uiState.isLoadingMore && displayedMessages.isNotEmpty()
                val shouldLoadMore by remember(canLoadMore) {
                    derivedStateOf {
                        if (!canLoadMore) return@derivedStateOf false
                        val totalItems = listState.layoutInfo.totalItemsCount
                        val lastVisibleItem = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
                        totalItems > 0 && lastVisibleItem >= totalItems - 4
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
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            val dateSep = formatDateSeparator(message.timestamp)
                            if (isFirstOfDay) {
                                if (dateSep.isNotEmpty()) {
                                    DateSeparatorBadge(
                                        dateText = dateSep,
                                        isPrivateMode = (uiState.chatExperienceMode == com.example.ui.chat.ChatExperienceMode.PRIVATE),
                                        isDark = isDark
                                    )
                                }
                                if (isToday(message.timestamp)) {
                                    StrictlyTwoPersonBanner(
                                        isPrivateMode = (uiState.chatExperienceMode == com.example.ui.chat.ChatExperienceMode.PRIVATE),
                                        isDark = isDark
                                    )
                                }
                            }

                            // Read marking is handled in the batched LaunchedEffect above the Scaffold.
                            // Do NOT put Firestore writes inside LazyColumn items — they fire on every scroll.

                            val isPrivate = uiState.chatExperienceMode == com.example.ui.chat.ChatExperienceMode.PRIVATE
                            val isHighlighted = (highlightedMessageId == message.id)
                            val isYouTube = remember(message.text) {
                                YouTubeHelper.extractVideoId(message.text) != null
                            }
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
                                    onSeekAudio = { progress ->
                                        if (currentPlayingId == message.id) {
                                            viewModel.seekAudio(progress)
                                        } else {
                                            message.mediaUrl?.let { url ->
                                                viewModel.playAudio(message.id, url)
                                                viewModel.seekAudio(progress)
                                            }
                                        }
                                    },
                                    onImageClick = { url ->
                                        val isVideo = message.getTypedType() == MessageType.VIDEO || message.isVideoNote || message.isCircularVideoNote()
                                        val targetType = if (isVideo) MessageType.VIDEO else MessageType.IMAGE
                                        val mediaList = if (isVideo) {
                                            listOf(url)
                                        } else {
                                            val msgUrls = message.getAllMediaUrls()
                                            if (msgUrls.size > 1) {
                                                msgUrls
                                            } else {
                                                val allChatImages = displayedMessages
                                                    .filter { it.getTypedType() == MessageType.IMAGE }
                                                    .flatMap { it.getAllMediaUrls() }
                                                    .distinct()
                                                if (allChatImages.isNotEmpty()) allChatImages else listOf(url)
                                            }
                                        }
                                        viewModel.openFullScreenMedia(url, targetType, mediaList)
                                    },
                                    onImageClickWithList = { url, allUrls ->
                                        val isVideo = message.getTypedType() == MessageType.VIDEO || message.isVideoNote || message.isCircularVideoNote()
                                        val targetType = if (isVideo) MessageType.VIDEO else MessageType.IMAGE
                                        val mediaList = if (isVideo) {
                                            listOf(url)
                                        } else if (allUrls.size > 1) {
                                            allUrls
                                        } else {
                                            val allChatImages = displayedMessages
                                                .filter { it.getTypedType() == MessageType.IMAGE }
                                                .flatMap { it.getAllMediaUrls() }
                                                .distinct()
                                            if (allChatImages.isNotEmpty()) allChatImages else listOf(url)
                                        }
                                        viewModel.openFullScreenMedia(url, targetType, mediaList)
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
                                    .padding(vertical = 14.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(20.dp),
                                    color = if (isDark) Color(0xFF1E2430).copy(alpha = 0.95f) else Color.White.copy(alpha = 0.95f),
                                    border = BorderStroke(
                                        1.dp,
                                        if (isDark) Color.White.copy(alpha = 0.12f) else Color.Black.copy(alpha = 0.08f)
                                    ),
                                    shadowElevation = 2.dp
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(14.dp),
                                            strokeWidth = 2.dp,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Text(
                                            text = "Reliving earlier memories...",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = if (isDark) Color.White else Color(0xFF1E293B)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Strictly 2-Person Beginning of Chat Banner (shown when pagination is exhausted)
                    val showBeginning = (uiState.isPaginationExhausted || displayedMessages.isEmpty())
                    if (showBeginning) {
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

            // Partner typing / recording animated bubble - floats seamlessly over chat wallpaper at bottom-start
            androidx.compose.animation.AnimatedVisibility(
                visible = (uiState.isPartnerRecordingAudio || uiState.isPartnerTyping) && !uiState.isStealthCurtainActive,
                enter = androidx.compose.animation.expandVertically() + fadeIn(),
                exit = androidx.compose.animation.shrinkVertically() + fadeOut(),
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 16.dp, bottom = 8.dp)
            ) {
                val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
                val isPrivate = uiState.chatExperienceMode == com.example.ui.chat.ChatExperienceMode.PRIVATE
                Row(
                    horizontalArrangement = Arrangement.Start
                ) {
                    Surface(
                        color = if (isPrivate) {
                            if (isDark) Color(0xFF26272B) else Color(0xFFE5E7EB)
                        } else {
                            if (isDark) Color(0xFF1E2638) else Color.White
                        },
                        shape = RoundedCornerShape(18.dp, 18.dp, 18.dp, 4.dp),
                        border = BorderStroke(
                            0.8.dp,
                            if (isDark) TrueDarkOutline else Color(0xFFE2E8F0)
                        ),
                        shadowElevation = 4.dp
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
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
                                    fontSize = 12.5.sp,
                                    color = if (isPrivate) MaterialTheme.colorScheme.onSurfaceVariant else RoseGoldPrimary,
                                    fontWeight = FontWeight.Medium
                                )
                            } else {
                                if (!isPrivate) {
                                    Text(
                                        text = "$partnerName is typing",
                                        fontSize = 12.5.sp,
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

            } // Close Box(modifier = Modifier.weight(1f).fillMaxWidth())
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
            onSearch = {
                viewModel.setSearching(true)
            },
            onSaveToMemories = {
                Toast.makeText(context, "Saved to Our Memories ❤️", Toast.LENGTH_SHORT).show()
            }
        )
    }

    // Circular Video Note Recorder Dialog
    if (showVideoNoteRecorder) {
        CircularVideoNoteRecorderDialog(
            onDismiss = { showVideoNoteRecorder = false },
            onSendVideoNote = { file, dur ->
                viewModel.sendVideoNote(file, dur)
            }
        )
    }

    // Simultaneous Heartbeat Touch Dialog (Haptic Sync)
    if (showHeartbeatTouch) {
        HeartbeatTouchDialog(
            partnerName = partnerName,
            isPartnerTouching = uiState.isPartnerHeartTouching,
            isPartnerOnline = uiState.isPartnerOnline,
            onTouchChanged = { viewModel.setHeartbeatTouch(it) },
            onDismiss = { showHeartbeatTouch = false }
        )
    }

    // Partner & My Mood Picker Sheet
    if (showMoodPicker) {
        MoodPickerSheet(
            currentMood = myUser?.mood,
            onSelectMood = { viewModel.updateMood(it) },
            onClearMood = { viewModel.clearMood() },
            onDismiss = { showMoodPicker = false }
        )
    }

    // Attachment Options Bottom Sheet (Gallery, Camera Snap, Documents) - opens full height
    if (showAttachmentSheet) {
        val attachmentSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { showAttachmentSheet = false },
            sheetState = attachmentSheetState,
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
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        }
                    )

                    AttachmentOptionItem(
                        icon = Icons.Default.Videocam,
                        label = "Video",
                        color = RoseGoldPrimary,
                        onClick = {
                            showAttachmentSheet = false
                            com.example.security.SecurityPreferences.getInstance(context).ignoreNextPause = true
                            videoPickerLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)
                            )
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
            onShowInChat = { clickedUrl ->
                val targetMsg = uiState.messages.find { it.mediaUrl == clickedUrl || it.getAllMediaUrls().contains(clickedUrl) }
                if (targetMsg != null) {
                    viewModel.navigateToMessageInChat(targetMsg.id)
                }
            },
            onDeleteMedia = { clickedUrl ->
                val targetMsg = uiState.messages.find { it.mediaUrl == clickedUrl || it.getAllMediaUrls().contains(clickedUrl) }
                if (targetMsg != null) {
                    viewModel.deleteMessage(targetMsg.id)
                }
            },
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

    // Clear Chat Dialog (clears temporary screen only, recoverable from settings)
    if (showClearChatDialog) {
        AlertDialog(
            onDismissRequest = { showClearChatDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.DeleteSweep, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Clear Chat from Screen", fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Text(
                    text = "This will temporarily clear the chat screen. All messages remain completely safe and can be restored anytime by going to Settings and switching on 'Show Previous Chats'.",
                    lineHeight = 20.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.clearChatScreenTemporarily()
                        showClearChatDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Clear Screen")
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearChatDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Full Profile Picture View (opens full picture view without navigating to settings)
    if (showFullProfilePicViewer) {
        val photoUrl = partner?.photoUrl
        if (!photoUrl.isNullOrBlank()) {
            FullScreenMediaViewer(
                mediaUrl = photoUrl,
                allMediaUrls = listOf(photoUrl),
                onDismiss = { showFullProfilePicViewer = false }
            )
        } else {
            androidx.compose.ui.window.Dialog(onDismissRequest = { showFullProfilePicViewer = false }) {
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 6.dp,
                    modifier = Modifier.padding(16.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .padding(24.dp)
                            .fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            modifier = Modifier
                                .size(140.dp)
                                .clip(CircleShape)
                                .background(
                                    Brush.linearGradient(
                                        listOf(
                                            RoseGoldPrimary,
                                            Color(0xFFC04B64)
                                        )
                                    )
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = partnerName.take(1).uppercase(),
                                color = Color.White,
                                fontSize = 54.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = partnerName,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = if (isPartnerOnline) "Online" else "No profile photo uploaded",
                            fontSize = 13.sp,
                            color = if (isPartnerOnline) OnlineGreen else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(20.dp))
                        Button(
                            onClick = { showFullProfilePicViewer = false },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Close")
                        }
                    }
                }
            }
        }
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
fun PinnedMessageBanner(
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

fun isSameDay(t1: Long, t2: Long): Boolean = com.example.util.ChatTimeFormatter.isSameDay(t1, t2)

fun isToday(timestamp: Long): Boolean = com.example.util.ChatTimeFormatter.isToday(timestamp)

fun isYesterday(timestamp: Long): Boolean = com.example.util.ChatTimeFormatter.isYesterday(timestamp)

fun formatDateSeparator(timestamp: Long): String = com.example.util.ChatTimeFormatter.formatDateSeparator(timestamp)

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
fun StrictlyTwoPersonBanner(
    isPrivateMode: Boolean,
    isDark: Boolean,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = if (isPrivateMode) {
                if (isDark) Color(0xFF1E2024).copy(alpha = 0.90f) else Color(0xFFF3F4F6)
            } else {
                if (isDark) Color(0xFF152033).copy(alpha = 0.92f) else Color(0xFFEFF6FF)
            },
            border = BorderStroke(
                1.dp,
                if (isPrivateMode) {
                    if (isDark) Color(0xFF33353C) else Color(0xFFE5E7EB)
                } else {
                    if (isDark) Color(0xFF2563EB).copy(alpha = 0.35f) else Color(0xFF93C5FD).copy(alpha = 0.55f)
                }
            )
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = null,
                    tint = if (isPrivateMode) {
                        if (isDark) Color(0xFF9CA3AF) else Color(0xFF4B5563)
                    } else {
                        if (isDark) Color(0xFF60A5FA) else Color(0xFF2563EB)
                    },
                    modifier = Modifier.size(13.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Strictly 2-Person Private Channel • End-to-End Encrypted",
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (isPrivateMode) {
                        if (isDark) Color(0xFFD1D5DB) else Color(0xFF374151)
                    } else {
                        if (isDark) Color(0xFF93C5FD) else Color(0xFF1E40AF)
                    }
                )
            }
        }
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




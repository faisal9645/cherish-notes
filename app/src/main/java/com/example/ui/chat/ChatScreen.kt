@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
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
import androidx.compose.ui.draw.shadow
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.paint
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.data.model.Message
import com.example.data.model.MessageType
import com.example.ui.components.AvatarView
import com.example.ui.components.SelectionCheckBadge
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

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
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
    var isSelectionMode by remember { mutableStateOf(false) }
    var selectedItemIds by remember { mutableStateOf(setOf<String>()) }
    var showMultiDeleteConfirmDialog by remember { mutableStateOf(false) }
    var showClearChatDialog by remember { mutableStateOf(false) }
    var showFullProfilePicViewer by remember { mutableStateOf(false) }
    var showChatMenu by remember { mutableStateOf(false) }
    var showVideoNoteRecorder by remember { mutableStateOf(false) }
    var showHeartbeatTouch by remember { mutableStateOf(false) }
    var showMoodPicker by remember { mutableStateOf(false) }
    val view = LocalView.current

    // Long-press focus view (message lifted over the dimmed, blurred chat)
    var focusedMessage by remember { mutableStateOf<Message?>(null) }
    var focusedBounds by remember { mutableStateOf(Rect.Zero) }
    val focusProgress = remember { Animatable(0f) }

    // Where the last touch landed (fraction of the screen), so the photo viewer zooms out of it
    val lastTouchFraction = remember { floatArrayOf(0.5f, 0.5f) }

    // Messages that arrive while this screen is open get a landing animation; the ones already
    // there when it opened don't. My own just-sent messages count as arrivals.
    val chatOpenedAt = remember { System.currentTimeMillis() }
    val initialMessageIds = remember { HashSet<String>() }
    val initialSnapshotTaken = remember { booleanArrayOf(false) }
    val landedMessageIds = remember { HashSet<String>() }
    if (!initialSnapshotTaken[0] && uiState.messages.isNotEmpty()) {
        uiState.messages.forEach { if (it.timestamp < chatOpenedAt - 2_000L) initialMessageIds += it.id }
        initialSnapshotTaken[0] = true
    }

    // Incoming messages that arrived while scrolled up, shown on the scroll-down button
    var unseenIncomingCount by remember { mutableIntStateOf(0) }

    fun exitSelection() {
        isSelectionMode = false
        selectedItemIds = emptySet()
    }

    // WhatsApp-style: deselecting the last message leaves selection mode
    fun toggleMessageSelection(messageId: String) {
        view.chatHaptic(ChatHaptic.Tick)
        selectedItemIds = if (messageId in selectedItemIds) selectedItemIds - messageId else selectedItemIds + messageId
        if (selectedItemIds.isEmpty()) isSelectionMode = false
    }

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

    val currentPlayingIdState = viewModel.voicePlayerHelper.currentlyPlayingId.collectAsState()
    val currentPlayingId by currentPlayingIdState
    val isAudioPlaying by viewModel.voicePlayerHelper.isPlaying.collectAsState()
    // Read only inside the playing bubble, so playback progress doesn't recompose the whole screen
    val audioProgressState = viewModel.voicePlayerHelper.playbackProgress.collectAsState()
    val isRecordingVoice by viewModel.voiceRecorderHelper.isRecording.collectAsState()
    val recordingDurationSec by viewModel.voiceRecorderHelper.recordingDurationSec.collectAsState()
    val recordingAmplitudes by viewModel.voiceRecorderHelper.amplitudes.collectAsState()

    // Activity result launchers for media selection (supports multiple photo selection)
    var isHandlingMedia by remember { mutableStateOf(false) }
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(maxItems = 20)
    ) { uris: List<Uri> ->
        val prefs = com.example.security.SecurityPreferences.getInstance(context)
        prefs.isExternalPickerActive = false
        prefs.ignoreNextPause = false
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
        val prefs = com.example.security.SecurityPreferences.getInstance(context)
        prefs.isExternalPickerActive = false
        prefs.ignoreNextPause = false
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
        val prefs = com.example.security.SecurityPreferences.getInstance(context)
        prefs.isExternalPickerActive = false
        prefs.ignoreNextPause = false
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
            val prefs = com.example.security.SecurityPreferences.getInstance(context)
            prefs.isExternalPickerActive = true
            prefs.ignoreNextPause = true
            cameraSnapLauncher.launch(uri)
        } catch (e: Exception) {
            val prefs = com.example.security.SecurityPreferences.getInstance(context)
            prefs.isExternalPickerActive = false
            prefs.ignoreNextPause = false
            Toast.makeText(context, "Could not open camera: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        val prefs = com.example.security.SecurityPreferences.getInstance(context)
        prefs.isExternalPickerActive = false
        prefs.ignoreNextPause = false
        if (isGranted) {
            launchRealtimeCameraSnap()
        } else {
            Toast.makeText(context, "Camera permission needed to snap photos", Toast.LENGTH_SHORT).show()
        }
    }

    // Video notes need both the camera and the microphone; the recorder only opens once both are allowed
    val videoNotePermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        val prefs = com.example.security.SecurityPreferences.getInstance(context)
        prefs.isExternalPickerActive = false
        prefs.ignoreNextPause = false
        if (results.isNotEmpty() && results.values.all { it }) {
            showVideoNoteRecorder = true
        } else {
            Toast.makeText(context, "Camera and microphone are needed for video notes", Toast.LENGTH_LONG).show()
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
            val prefs = com.example.security.SecurityPreferences.getInstance(context)
            prefs.isExternalPickerActive = true
            prefs.ignoreNextPause = true
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    // Follow the conversation only when a NEW message lands at the bottom: always for my own sends,
    // for the partner's only when already near the bottom. Keyed on the newest message rather than
    // the message count, so older pages loading at the top never pull the list back down.
    val newestMessage = uiState.messages.lastOrNull()
    val newestSeen = remember { arrayOfNulls<Message>(1) }
    LaunchedEffect(newestMessage?.id) {
        val newest = newestMessage ?: return@LaunchedEffect
        val previous = newestSeen[0]
        newestSeen[0] = newest
        // First load, or the newest was deleted (an older one is now last): keep the position
        if (previous == null || newest.timestamp <= previous.timestamp) return@LaunchedEffect
        val isNearBottom = listState.firstVisibleItemIndex <= 1
        if (isNearBottom || newest.senderId == uiState.currentUser?.id) {
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
    val isPartnerOnline = uiState.isPartnerOnline
    val currentUserId = viewModel.uiState.value.currentUser?.id ?: "user_me"

    // Issue 13: Batch read-marking outside LazyColumn — prevents Firestore writes during scroll
    val unreadIds = remember(uiState.messages, currentUserId) {
        uiState.messages
            .filter { it.senderId != currentUserId && it.getTypedStatus() != com.example.data.model.MessageStatus.READ && !it.isDeleted }
            .map { it.id }
    }
    LaunchedEffect(unreadIds) {
        viewModel.markMessagesAsRead(unreadIds)
    }

    // Soft tick when a message arrives while the chat is open, and a count on the scroll-down
    // button when scrolled up. Compared with the partner's own earlier timestamps, so a clock
    // difference between the two phones doesn't matter.
    val newestIncoming = remember(uiState.messages, currentUserId) {
        uiState.messages.lastOrNull { it.senderId != currentUserId }
    }
    var newestIncomingSeenAt by remember { mutableLongStateOf(-1L) }
    LaunchedEffect(newestIncoming?.id) {
        val incoming = newestIncoming ?: return@LaunchedEffect
        if (newestIncomingSeenAt < 0L) {
            newestIncomingSeenAt = incoming.timestamp
        } else if (incoming.timestamp > newestIncomingSeenAt) {
            newestIncomingSeenAt = incoming.timestamp
            view.chatHaptic(ChatHaptic.Receive)
            if (listState.firstVisibleItemIndex > 1) unseenIncomingCount++
        }
    }
    LaunchedEffect(listState) {
        snapshotFlow { listState.firstVisibleItemIndex == 0 }.collect { atBottom ->
            if (atBottom) unseenIncomingCount = 0
        }
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

    // What a message row can ask of the screen. Created once, reading the latest state when
    // called, so rows get the same instance every time and can be skipped on recomposition.
    val latestDisplayedMessages = rememberUpdatedState(displayedMessages)
    val latestKeyboardController = rememberUpdatedState(keyboardController)
    val rowActions = remember(viewModel) {
        ChatRowActions(
            playAudio = { message ->
                message.mediaUrl?.let { url -> viewModel.playAudio(message.id, url) }
            },
            seekAudio = { message, progress ->
                if (viewModel.voicePlayerHelper.currentlyPlayingId.value == message.id) {
                    viewModel.seekAudio(progress)
                } else {
                    message.mediaUrl?.let { url ->
                        viewModel.playAudio(message.id, url)
                        viewModel.seekAudio(progress)
                    }
                }
            },
            openMedia = { message, url, siblings ->
                (context.applicationContext as? com.example.CherishApplication)?.securityPreferences?.isMediaViewerActive = true
                val isVideo = message.getTypedType() == MessageType.VIDEO || message.isVideoNote || message.isCircularVideoNote()
                val ownPhotos = siblings ?: message.getAllMediaUrls()
                val mediaList = when {
                    isVideo -> listOf(url)
                    ownPhotos.size > 1 -> ownPhotos
                    else -> latestDisplayedMessages.value
                        .filter { it.getTypedType() == MessageType.IMAGE }
                        .flatMap { it.getAllMediaUrls() }
                        .distinct()
                        .ifEmpty { listOf(url) }
                }
                viewModel.openFullScreenMedia(url, if (isVideo) MessageType.VIDEO else MessageType.IMAGE, mediaList)
            },
            reply = { message -> viewModel.setReplyingTo(message) },
            longPress = { message, bounds ->
                latestKeyboardController.value?.hide()
                focusedBounds = bounds
                focusedMessage = message
            },
            react = { message, emoji -> viewModel.toggleReaction(message.id, emoji) },
            openTheaterVideo = { videoId -> viewModel.openTheaterVideo(videoId) },
            toggleVoiceSpeed = { viewModel.toggleVoiceSpeed() },
            jumpToReply = { replyId ->
                if (!replyId.isNullOrBlank()) {
                    // The list is reversed (newest at index 0)
                    val shown = latestDisplayedMessages.value
                    val index = shown.indexOfFirst { it.id == replyId }
                    if (index >= 0) {
                        scope.launch {
                            listState.animateScrollToItem(shown.lastIndex - index)
                            highlightedMessageId = replyId
                            kotlinx.coroutines.delay(1400)
                            highlightedMessageId = null
                        }
                    }
                }
            },
            toggleSelection = { messageId -> toggleMessageSelection(messageId) }
        )
    }

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
            showMultiDeleteConfirmDialog = false
            exitSelection()
            focusedMessage = null
            focusProgress.snapTo(0f)
        }
    }

    LaunchedEffect(uiState.isStealthCurtainActive) {
        if (uiState.isStealthCurtainActive) {
            showMultiDeleteConfirmDialog = false
            exitSelection()
            focusedMessage = null
            focusProgress.snapTo(0f)
        }
    }

    // Drop selected messages that are no longer shown (deleted on the other phone, filter changed)
    LaunchedEffect(displayedMessages, isSelectionMode) {
        if (isSelectionMode) {
            val shownIds = displayedMessages.mapTo(HashSet()) { it.id }
            if (!shownIds.containsAll(selectedItemIds)) {
                selectedItemIds = selectedItemIds.intersect(shownIds)
                if (selectedItemIds.isEmpty()) isSelectionMode = false
            }
        }
    }

    // User presence: Show as online only while actively inside the Chat tab (Issue 5)
    DisposableEffect(Unit) {
        viewModel.setInChatTab(true)
        onDispose {
            viewModel.setInChatTab(false)
        }
    }

    // Android back button & gesture: handle in-app dialogs first, else return to Home tab
    val isImeVisible = androidx.compose.foundation.layout.WindowInsets.isImeVisible
    BackHandler(enabled = true) {
        if (isImeVisible) {
            keyboardController?.hide()
        } else if (isSelectionMode) {
            exitSelection()
        } else if (uiState.theaterVideoId != null) {
            viewModel.closeTheaterVideo()
        } else if (uiState.fullScreenMediaUrl != null) {
            viewModel.closeFullScreenMedia()
        } else if (showFullProfilePicViewer) {
            showFullProfilePicViewer = false
        } else if (showClearChatDialog) {
            showClearChatDialog = false
        } else if (showDeleteConfirmDialog != null) {
            showDeleteConfirmDialog = null
        } else if (editingMessage != null) {
            editingMessage = null
        } else if (showAttachmentSheet) {
            showAttachmentSheet = false
        } else if (showChatMenu) {
            showChatMenu = false
        } else if (showVideoNoteRecorder) {
            showVideoNoteRecorder = false
        } else if (showHeartbeatTouch) {
            showHeartbeatTouch = false
        } else if (showMoodPicker) {
            showMoodPicker = false
        } else if (uiState.selectedMessageForActions != null) {
            viewModel.setSelectedMessageForActions(null)
        } else if (uiState.replyingToMessage != null) {
            viewModel.setReplyingTo(null)
        } else if (uiState.isSearching) {
            viewModel.setSearching(false)
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

    // Leaving the chat: stop any voice recording and hide the history again
    DisposableEffect(Unit) {
        onDispose {
            viewModel.cancelVoiceRecording()
            viewModel.safeStopRecordingForBackground()
            viewModel.hideSecretHistory()
        }
    }

    Scaffold(
        modifier = Modifier
            .graphicsLayer {
                // The chat blurs behind the long-press focus view (Android 12+)
                if (android.os.Build.VERSION.SDK_INT >= 31) {
                    val radius = 10.dp.toPx() * focusProgress.value.coerceIn(0f, 1f)
                    renderEffect = if (radius > 0.5f) BlurEffect(radius, radius, TileMode.Clamp) else null
                }
            }
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    lastTouchFraction[0] = down.position.x / size.width.coerceAtLeast(1)
                    lastTouchFraction[1] = down.position.y / size.height.coerceAtLeast(1)
                    // No tab switching mid-selection, it would drop the selected messages
                    if (isSelectionMode) return@awaitEachGesture
                    var accX = 0f
                    var accY = 0f
                    var directionLocked = false
                    var isHorizontal = false

                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Main)
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (!change.pressed) break

                        // If a child component (like a message bubble swipe-to-reply) consumed the event,
                        // do not interpret this gesture as a tab switch
                        if (change.isConsumed) {
                            break
                        }

                        val delta = change.positionChange()
                        accX += delta.x
                        accY += delta.y

                        if (!directionLocked && (kotlin.math.abs(accX) > 16f || kotlin.math.abs(accY) > 16f)) {
                            isHorizontal = kotlin.math.abs(accX) > kotlin.math.abs(accY) * 1.8f
                            directionLocked = true
                        }

                        if (directionLocked && isHorizontal) {
                            // Swiping right smoothly glides to Love & Us tab
                            if (accX > 70f) {
                                change.consume()
                                onNavigateToHome()
                                break
                            }
                            // Swiping left smoothly glides to Settings / Profile tab
                            if (accX < -70f) {
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
            AnimatedContent(
                targetState = isSelectionMode,
                transitionSpec = { fadeIn(tween(180)) togetherWith fadeOut(tween(120)) },
                label = "chat_top_bar"
            ) { selecting ->
            if (selecting) {
                ChatSelectionTopBar(
                    selectedCount = selectedItemIds.size,
                    onClose = { exitSelection() },
                    onDelete = { showMultiDeleteConfirmDialog = true }
                )
            } else Column {
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
                                        ) {
                                            val app = context.applicationContext as? com.example.CherishApplication
                                            app?.securityPreferences?.isMediaViewerActive = true
                                            showFullProfilePicViewer = true
                                        }
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
                                                EmojiText(
                                                    text = partnerMood,
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Medium,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    maxLines = 1,
                                                    emojiScale = 1.2f,
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
                                onNavigateToHome()
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
                                modifier = Modifier.size(40.dp)
                            ) {
                                Icon(Icons.Default.MoreVert, contentDescription = "More options", tint = iconTint, modifier = Modifier.size(23.dp))
                            }
                            DropdownMenu(
                                expanded = showChatMenu,
                                onDismissRequest = { showChatMenu = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Set Mood Status") },
                                    onClick = {
                                        showChatMenu = false
                                        showMoodPicker = true
                                    },
                                    leadingIcon = { Icon(Icons.Outlined.Mood, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface) }
                                )
                                DropdownMenuItem(
                                    text = { Text("Starred Messages") },
                                    onClick = {
                                        showChatMenu = false
                                        viewModel.toggleFilterStarred()
                                    },
                                    leadingIcon = { Icon(Icons.Outlined.Star, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface) }
                                )
                                DropdownMenuItem(
                                    text = { Text("Clear Chat") },
                                    onClick = {
                                        showChatMenu = false
                                        showClearChatDialog = true
                                    },
                                    leadingIcon = { Icon(Icons.Outlined.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface) }
                                )
                                DropdownMenuItem(
                                    text = { Text("Logout") },
                                    onClick = {
                                        showChatMenu = false
                                        viewModel.logout()
                                        onLoggedOut()
                                    },
                                    leadingIcon = { Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface) }
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
            }
        },
        bottomBar = {
            val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
            val barBg = if (isDark) TrueDarkSurface else Color.White
            // Not a Surface, which clips its content: the voice-lock hint floats above the mic button
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .shadow(8.dp, RectangleShape, clip = false)
                    .background(barBg)
                    // Touches on the tray stay in the tray
                    .pointerInput(Unit) {}
            ) {
                CompositionLocalProvider(LocalContentColor provides contentColorFor(barBg)) {
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
                        if (composerText.isNotBlank()) view.chatHaptic(ChatHaptic.Send)
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
                        val missing = listOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO).filter {
                            ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
                        }
                        if (missing.isEmpty()) {
                            showVideoNoteRecorder = true
                        } else {
                            val prefs = com.example.security.SecurityPreferences.getInstance(context)
                            prefs.isExternalPickerActive = true
                            prefs.ignoreNextPause = true
                            videoNotePermissionLauncher.launch(missing.toTypedArray())
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
                            // The list is reversed (newest at index 0)
                            val index = displayedMessages.indexOfFirst { it.id == pinned.id }
                            if (index >= 0) {
                                scope.launch {
                                    listState.animateScrollToItem(displayedMessages.lastIndex - index)
                                    highlightedMessageId = pinned.id
                                    delay(1400)
                                    highlightedMessageId = null
                                }
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

                // The typing bubble sits under the list, so as it grows the newest messages move up
                Column(modifier = Modifier.fillMaxSize()) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .pointerInput(Unit) {
                            detectTapGestures(onDoubleTap = { onQuickDisguise() })
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
                        // A message that arrives while the chat is open lands with a spring
                        val isFreshArrival = remember(message.id) {
                            index <= 1 && initialSnapshotTaken[0] &&
                                message.id !in initialMessageIds && landedMessageIds.add(message.id)
                        }
                        ChatMessageRow(
                            message = message,
                            isFromMe = isFromMe,
                            isFirstOfDay = isFirstOfDay,
                            isFreshArrival = isFreshArrival,
                            isPrivate = uiState.chatExperienceMode == com.example.ui.chat.ChatExperienceMode.PRIVATE,
                            isDark = isDark,
                            isHighlighted = highlightedMessageId == message.id,
                            isSelectionMode = isSelectionMode,
                            isSelected = isSelectionMode && message.id in selectedItemIds,
                            isPlayingAudio = currentPlayingId == message.id && isAudioPlaying,
                            audioProgress = { if (currentPlayingIdState.value == message.id) audioProgressState.value else 0f },
                            gallerySize = uiState.gallerySize,
                            voicePlaybackSpeed = uiState.voicePlaybackSpeed,
                            senderPhotoUrl = if (isFromMe) uiState.currentUser?.photoUrl else uiState.partnerUser?.photoUrl,
                            actions = rowActions,
                            // Rows glide aside when messages are added or deleted instead of jumping;
                            // new arrivals get their own landing
                            modifier = Modifier.animateItem(
                                fadeInSpec = null,
                                placementSpec = spring(
                                    dampingRatio = 0.86f,
                                    stiffness = Spring.StiffnessMediumLow,
                                    visibilityThreshold = IntOffset.VisibilityThreshold
                                ),
                                fadeOutSpec = tween(180)
                            )
                        )
                    }

                    // 1. Loading older messages indicator (shown at top of list while paginating)
                    if (uiState.isLoadingMore) {
                        item(key = "pagination_loader", contentType = "loader") {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .animateItem()
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
            } // Box content end

                // Only at the bottom: while reading older messages it would push the list up and down
                // (the header still says "typing…")
                val isAtBottom by remember { derivedStateOf { listState.firstVisibleItemIndex <= 1 } }
                PartnerTypingBubble(
                    visible = (uiState.isPartnerTyping || uiState.isPartnerRecordingAudio) &&
                        uiState.chatExperienceMode != com.example.ui.chat.ChatExperienceMode.PRIVATE &&
                        !uiState.isSearching && isAtBottom,
                    isRecording = uiState.isPartnerRecordingAudio,
                    partnerPhotoUrl = partner?.photoUrl,
                    partnerName = partnerName,
                    isDark = isDark
                )
                } // Column: list + typing bubble

            // WhatsApp-style date chip: the date of the messages at the top, shown while scrolling
            var showDateChip by remember { mutableStateOf(false) }
            val isListScrolling = listState.isScrollInProgress
            LaunchedEffect(isListScrolling) {
                if (isListScrolling) {
                    showDateChip = true
                } else {
                    delay(1200)
                    showDateChip = false
                }
            }
            val topVisibleDate by remember(reversedMessages) {
                derivedStateOf {
                    val topIndex = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: return@derivedStateOf null
                    (reversedMessages.getOrNull(topIndex) ?: reversedMessages.lastOrNull())
                        ?.let { formatDateSeparator(it.timestamp) }
                        ?.takeIf { it.isNotEmpty() }
                }
            }
            val lastShownDate = remember { arrayOf("") }
            topVisibleDate?.let { lastShownDate[0] = it }
            androidx.compose.animation.AnimatedVisibility(
                visible = showDateChip && topVisibleDate != null,
                enter = fadeIn(tween(150)) + slideInVertically(spring(dampingRatio = 0.8f, stiffness = 600f)) { -it / 2 },
                exit = fadeOut(tween(250)),
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 8.dp)
            ) {
                DateChip(
                    dateText = lastShownDate[0],
                    isPrivateMode = (uiState.chatExperienceMode == com.example.ui.chat.ChatExperienceMode.PRIVATE),
                    isDark = isDark,
                    shadowElevation = 3.dp
                )
            }

            // Floating scroll to bottom button
            val showScrollButton by remember {
                derivedStateOf {
                    listState.firstVisibleItemIndex > 3
                }
            }

            androidx.compose.animation.AnimatedVisibility(
                visible = showScrollButton,
                enter = scaleIn(spring(dampingRatio = 0.55f, stiffness = 500f), initialScale = 0.4f) + fadeIn(tween(120)),
                exit = scaleOut(tween(140), targetScale = 0.6f) + fadeOut(tween(140)),
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp)
            ) {
                val isPrivate = uiState.chatExperienceMode == com.example.ui.chat.ChatExperienceMode.PRIVATE
                Box {
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
                    // New messages that arrived while scrolled up; bumps with each one
                    if (unseenIncomingCount > 0) {
                        val badgeBump = remember { Animatable(1f) }
                        LaunchedEffect(unseenIncomingCount) {
                            badgeBump.snapTo(1.35f)
                            badgeBump.animateTo(1f, spring(dampingRatio = 0.4f, stiffness = 600f))
                        }
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .offset(x = 4.dp, y = (-4).dp)
                                .graphicsLayer {
                                    scaleX = badgeBump.value
                                    scaleY = badgeBump.value
                                }
                                .defaultMinSize(minWidth = 18.dp, minHeight = 18.dp)
                                .background(
                                    if (isPrivate) (if (isDark) Color(0xFFECECEC) else Color(0xFF1F2937)) else HeartRed,
                                    CircleShape
                                )
                                .padding(horizontal = 4.dp)
                                .testTag("chat_unseen_badge"),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = if (unseenIncomingCount > 99) "99+" else unseenIncomingCount.toString(),
                                color = if (isPrivate && isDark) Color(0xFF111827) else Color.White,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
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

            // (Partner typing / recording animated bubble moved into LazyColumn)

            } // Close Box(modifier = Modifier.weight(1f).fillMaxWidth())
        }
    }

    // Long-press focus view over the whole chat; "+" opens the full sheet below
    focusedMessage?.let { focused ->
        val live = uiState.messages.find { it.id == focused.id } ?: focused
        val focusedIsFromMe = live.senderId == currentUserId
        val isPrivate = uiState.chatExperienceMode == com.example.ui.chat.ChatExperienceMode.PRIVATE
        val focusActions = buildList {
            add(FocusAction("Reply", Icons.AutoMirrored.Filled.Reply) { viewModel.setReplyingTo(live) })
            add(
                FocusAction(
                    label = if (live.isStarred) "Unstar" else "Star",
                    icon = if (live.isStarred) Icons.Filled.Star else Icons.Outlined.StarOutline
                ) { viewModel.toggleStar(live.id) }
            )
            add(
                FocusAction(
                    label = if (live.isPinned) "Unpin" else "Pin",
                    icon = if (live.isPinned) Icons.Filled.PushPin else Icons.Outlined.PushPin
                ) { viewModel.togglePin(live.id) }
            )
            if (live.getTypedType() == MessageType.TEXT && live.text.isNotBlank()) {
                add(FocusAction("Copy", Icons.Default.ContentCopy) {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText("Cherish Message", live.text))
                })
            }
            if (focusedIsFromMe && !live.isDeleted) {
                add(FocusAction("Edit", Icons.Default.Edit) {
                    editingMessage = live
                    editDialogText = live.text
                })
            }
            add(FocusAction("Select", Icons.Default.CheckCircleOutline) {
                isSelectionMode = true
                selectedItemIds = setOf(live.id)
            })
            add(FocusAction("Save to Memories", Icons.Outlined.FavoriteBorder) {
                Toast.makeText(context, "Saved to Our Memories ❤️", Toast.LENGTH_SHORT).show()
            })
            add(FocusAction("Delete", Icons.Outlined.Delete, isDestructive = true) { showDeleteConfirmDialog = live })
        }
        MessageFocusOverlay(
            isFromMe = focusedIsFromMe,
            anchorBounds = focusedBounds,
            progress = focusProgress,
            myReaction = live.reactions[currentUserId],
            isPrivateMode = isPrivate,
            actions = focusActions,
            onReaction = { emoji -> viewModel.toggleReaction(live.id, emoji) },
            onMoreReactions = { viewModel.setSelectedMessageForActions(live) },
            onDismissed = { focusedMessage = null }
        ) {
            MessageBubble(
                message = live,
                isFromMe = focusedIsFromMe,
                isPlayingAudio = false,
                audioProgress = { 0f },
                onPlayAudio = {},
                onImageClick = {},
                onLongClick = {},
                onReactionClick = { emoji -> viewModel.toggleReaction(live.id, emoji) },
                gallerySize = uiState.gallerySize,
                isPrivateMode = isPrivate,
                senderPhotoUrl = if (focusedIsFromMe) uiState.currentUser?.photoUrl else uiState.partnerUser?.photoUrl
            )
        }
    }

    // Message Actions Bottom Sheet
    uiState.selectedMessageForActions?.let { msg ->
        MessageActionsSheet(
            message = msg,
            isFromMe = msg.senderId == currentUserId,
            onDismiss = { viewModel.setSelectedMessageForActions(null) },
            onReaction = { emoji ->
                view.chatHaptic(ChatHaptic.Tick)
                viewModel.toggleReaction(msg.id, emoji)
            },
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
            onSelect = {
                isSelectionMode = true
                selectedItemIds = setOf(msg.id)
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
            onSyncHeartbeatStreak = { viewModel.syncHeartbeatStreak() },
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
                            val prefs = com.example.security.SecurityPreferences.getInstance(context)
                            prefs.isExternalPickerActive = true
                            prefs.ignoreNextPause = true
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
                            val prefs = com.example.security.SecurityPreferences.getInstance(context)
                            prefs.isExternalPickerActive = true
                            prefs.ignoreNextPause = true
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
                        view.chatHaptic(ChatHaptic.Delete)
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

    // Multi-select Delete Confirmation Dialog
    if (showMultiDeleteConfirmDialog) {
        val count = selectedItemIds.size
        AlertDialog(
            onDismissRequest = { showMultiDeleteConfirmDialog = false },
            title = { Text(if (count == 1) "Delete message?" else "Delete $count messages?") },
            text = {
                Text(if (count == 1) "This message will be removed from your private chat." else "These $count messages will be removed from your private chat.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        view.chatHaptic(ChatHaptic.Delete)
                        viewModel.deleteMessages(selectedItemIds)
                        showMultiDeleteConfirmDialog = false
                        exitSelection()
                    },
                    enabled = count > 0,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.testTag("chat_confirm_delete_selected")
                ) {
                    Text(if (count == 1) "Delete" else "Delete ($count)")
                }
            },
            dismissButton = {
                TextButton(onClick = { showMultiDeleteConfirmDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Full-screen media viewer
    uiState.fullScreenMediaUrl?.let { url ->
        val openFrom = remember(url) { TransformOrigin(lastTouchFraction[0], lastTouchFraction[1]) }
        FullScreenMediaViewer(
            mediaUrl = url,
            allMediaUrls = uiState.allMediaUrlsForViewer,
            openFrom = openFrom,
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
            val openFrom = remember(photoUrl) { TransformOrigin(lastTouchFraction[0], lastTouchFraction[1]) }
            FullScreenMediaViewer(
                mediaUrl = photoUrl,
                allMediaUrls = listOf(photoUrl),
                openFrom = openFrom,
                onDismiss = {
                    val app = context.applicationContext as? com.example.CherishApplication
                    app?.securityPreferences?.isMediaViewerActive = false
                    showFullProfilePicViewer = false
                }
            )
        } else {
            androidx.compose.ui.window.Dialog(onDismissRequest = {
                val app = context.applicationContext as? com.example.CherishApplication
                app?.securityPreferences?.isMediaViewerActive = false
                showFullProfilePicViewer = false
            }) {
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
                            onClick = {
                                val app = context.applicationContext as? com.example.CherishApplication
                                app?.securityPreferences?.isMediaViewerActive = false
                                showFullProfilePicViewer = false
                            },
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChatSelectionTopBar(
    selectedCount: Int,
    onClose: () -> Unit,
    onDelete: () -> Unit
) {
    Column {
        TopAppBar(
            title = {
                Text(
                    text = "$selectedCount selected",
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
            },
            navigationIcon = {
                IconButton(
                    onClick = onClose,
                    modifier = Modifier.testTag("chat_selection_close")
                ) {
                    Icon(Icons.Default.Close, contentDescription = "Cancel selection")
                }
            },
            actions = {
                IconButton(
                    onClick = onDelete,
                    enabled = selectedCount > 0,
                    modifier = Modifier.testTag("chat_delete_selected")
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Delete selected messages",
                        tint = if (selectedCount > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                    )
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
            windowInsets = WindowInsets.statusBars
        )
        HorizontalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
    }
}

/**
 * Everything a message row can ask of the chat screen. One instance per screen, so rows receive
 * the same object every time instead of fresh lambdas.
 */
@Stable
private class ChatRowActions(
    val playAudio: (Message) -> Unit,
    val seekAudio: (Message, Float) -> Unit,
    val openMedia: (message: Message, url: String, siblings: List<String>?) -> Unit,
    val reply: (Message) -> Unit,
    val longPress: (message: Message, bounds: Rect) -> Unit,
    val react: (message: Message, emoji: String) -> Unit,
    val openTheaterVideo: (String) -> Unit,
    val toggleVoiceSpeed: () -> Unit,
    val jumpToReply: (String?) -> Unit,
    val toggleSelection: (String) -> Unit
)

/**
 * One message in the chat list: date separator, landing/selection/highlight animations and the
 * selection overlay. It only takes plain values, the message and the shared [actions], so a row is
 * skipped when unrelated chat state (typing, presence, upload or playback progress) changes.
 */
@Composable
private fun ChatMessageRow(
    message: Message,
    isFromMe: Boolean,
    isFirstOfDay: Boolean,
    isFreshArrival: Boolean,
    isPrivate: Boolean,
    isDark: Boolean,
    isHighlighted: Boolean,
    isSelectionMode: Boolean,
    isSelected: Boolean,
    isPlayingAudio: Boolean,
    audioProgress: () -> Float,
    gallerySize: String,
    voicePlaybackSpeed: Float,
    senderPhotoUrl: String?,
    actions: ChatRowActions,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        if (isFirstOfDay) {
            val dateSep = formatDateSeparator(message.timestamp)
            if (dateSep.isNotEmpty()) {
                DateSeparatorBadge(dateText = dateSep, isPrivateMode = isPrivate, isDark = isDark)
            }
            if (isToday(message.timestamp)) {
                StrictlyTwoPersonBanner(isPrivateMode = isPrivate, isDark = isDark)
            }
        }

        // Read marking is handled in the batched LaunchedEffect above the Scaffold.
        // Do NOT put Firestore writes inside LazyColumn items — they fire on every scroll.

        val selectionAccent = if (isPrivate) (if (isDark) Color(0xFF9CA3AF) else Color(0xFF6B7280)) else RoseGoldPrimary

        // A message that arrives while the chat is open lands with a spring: sent ones rise from
        // the composer, received ones slide in from the left
        val landing = remember(message.id) { Animatable(if (isFreshArrival) 0f else 1f) }
        LaunchedEffect(message.id) {
            if (isFreshArrival) landing.animateTo(1f, spring(dampingRatio = 0.62f, stiffness = 420f))
        }

        // Jump-to-message highlight: a quick bump, and a glow that fades once released
        val glow = remember(message.id) { Animatable(0f) }
        val bump = remember(message.id) { Animatable(0f) }
        LaunchedEffect(isHighlighted) {
            if (isHighlighted) {
                launch {
                    bump.animateTo(1f, spring(dampingRatio = 0.5f, stiffness = 900f))
                    bump.animateTo(0f, spring(dampingRatio = 0.45f, stiffness = 300f))
                }
                glow.animateTo(1f, tween(200))
            } else {
                glow.animateTo(0f, tween(650))
            }
        }
        val glowColor = if (isPrivate) (if (isDark) Color(0xFF6B7280) else Color(0xFF9CA3AF)) else RoseGoldPrimary
        val rowTint by animateColorAsState(
            targetValue = if (isSelected) selectionAccent.copy(alpha = if (isDark) 0.22f else 0.14f) else Color.Transparent,
            animationSpec = tween(160),
            label = "row_tint"
        )
        val bubbleStartPadding by animateDpAsState(
            targetValue = if (isSelectionMode) 6.dp else 16.dp,
            animationSpec = spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessMediumLow),
            label = "row_start_padding"
        )
        // Where the message sits on screen, for the long-press focus view
        val anchor = remember(message.id) { arrayOf(Rect.Zero) }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .drawBehind { if (rowTint.alpha > 0f) drawRect(rowTint) }
                .then(
                    if (isFreshArrival) {
                        Modifier.graphicsLayer {
                            val p = landing.value
                            alpha = p.coerceIn(0f, 1f)
                            val s = 0.86f + 0.14f * p
                            scaleX = s
                            scaleY = s
                            transformOrigin = TransformOrigin(if (isFromMe) 1f else 0f, 1f)
                            translationY = (1f - p) * 28.dp.toPx()
                            if (!isFromMe) translationX = (1f - p) * -20.dp.toPx()
                        }
                    } else Modifier
                )
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AnimatedVisibility(
                    visible = isSelectionMode,
                    enter = expandHorizontally(spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessMediumLow)) +
                        fadeIn(tween(150)) +
                        scaleIn(spring(dampingRatio = 0.5f, stiffness = 600f), initialScale = 0.4f),
                    exit = shrinkHorizontally(tween(160)) + fadeOut(tween(120))
                ) {
                    SelectionCheckBadge(
                        isSelected = isSelected,
                        accentColor = selectionAccent,
                        modifier = Modifier.padding(start = 14.dp)
                    )
                }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = bubbleStartPadding, end = 16.dp)
                        .onGloballyPositioned { anchor[0] = it.boundsInRoot() }
                        .graphicsLayer {
                            val grow = 1f + 0.035f * bump.value
                            scaleX = grow
                            scaleY = grow
                            transformOrigin = TransformOrigin(if (isFromMe) 1f else 0f, 0.5f)
                        }
                        .drawBehind {
                            val g = glow.value
                            if (g > 0f) {
                                val corner = CornerRadius(16.dp.toPx())
                                drawRoundRect(glowColor.copy(alpha = 0.16f * g), cornerRadius = corner)
                                drawRoundRect(glowColor.copy(alpha = g), cornerRadius = corner, style = Stroke(2.dp.toPx()))
                            }
                        }
                ) {
                    MessageBubble(
                        message = message,
                        isFromMe = isFromMe,
                        isPlayingAudio = isPlayingAudio,
                        audioProgress = audioProgress,
                        gallerySize = gallerySize,
                        onPlayAudio = { actions.playAudio(message) },
                        onSeekAudio = { progress -> actions.seekAudio(message, progress) },
                        onImageClick = { url -> actions.openMedia(message, url, null) },
                        onImageClickWithList = { url, allUrls -> actions.openMedia(message, url, allUrls) },
                        onSwipeToReply = { actions.reply(message) },
                        onLongClick = { actions.longPress(message, anchor[0]) },
                        onReactionClick = { emoji -> actions.react(message, emoji) },
                        onOpenTheaterVideo = actions.openTheaterVideo,
                        voicePlaybackSpeed = voicePlaybackSpeed,
                        onToggleVoiceSpeed = actions.toggleVoiceSpeed,
                        isHighlighted = isHighlighted,
                        onReplyQuoteClick = actions.jumpToReply,
                        isPrivateMode = isPrivate,
                        senderPhotoUrl = senderPhotoUrl
                    )
                }
            }
            if (isSelectionMode) {
                // Covers the whole row, so a tap selects the message instead of opening media,
                // playing audio or swiping to reply
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .pointerInput(message.id) {
                            detectTapGestures(
                                onTap = { actions.toggleSelection(message.id) },
                                onLongPress = { actions.toggleSelection(message.id) }
                            )
                        }
                        .testTag("chat_select_${message.id}")
                )
            }
        }
    }
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
            .background(MaterialTheme.colorScheme.background)
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

/**
 * Partner typing / recording indicator under the newest message. It springs in from the bottom-left
 * and, because it sits below the list, pushes the newest messages up as it grows.
 */
@Composable
private fun PartnerTypingBubble(
    visible: Boolean,
    isRecording: Boolean,
    partnerPhotoUrl: String?,
    partnerName: String,
    isDark: Boolean
) {
    AnimatedVisibility(
        visible = visible,
        enter = expandVertically(spring(dampingRatio = 0.8f, stiffness = 500f), expandFrom = Alignment.Top) +
            fadeIn(tween(160)) +
            scaleIn(spring(dampingRatio = 0.55f, stiffness = 500f), initialScale = 0.6f, transformOrigin = TransformOrigin(0f, 1f)),
        exit = shrinkVertically(tween(180), shrinkTowards = Alignment.Top) +
            fadeOut(tween(140)) +
            scaleOut(tween(160), targetScale = 0.7f, transformOrigin = TransformOrigin(0f, 1f))
    ) {
        Row(
            verticalAlignment = Alignment.Bottom,
            modifier = Modifier
                .padding(start = 22.dp, end = 16.dp, top = 2.dp, bottom = 8.dp)
                .testTag("partner_typing_bubble")
        ) {
            AvatarView(
                photoUrl = partnerPhotoUrl,
                name = partnerName,
                size = 26.dp,
                showOnlineBadge = false
            )
            Spacer(modifier = Modifier.width(6.dp))
            Surface(
                shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomEnd = 16.dp, bottomStart = 4.dp),
                color = if (isDark) Color(0xFF1E2638) else Color(0xFFF1F5FB),
                border = BorderStroke(0.5.dp, if (isDark) Color(0xFF2A364F) else Color(0xFFE2E8F0)),
                shadowElevation = 0.8.dp
            ) {
                Box(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 11.dp),
                    contentAlignment = Alignment.Center
                ) {
                    if (isRecording) {
                        RecordingBars(color = HeartRed)
                    } else {
                        BouncingDots(color = if (isDark) Color(0xFFCBD5E1) else Color(0xFF64748B))
                    }
                }
            }
        }
    }
}

@Composable
private fun RecordingBars(color: Color) {
    val transition = rememberInfiniteTransition(label = "recording_bars")
    Row(
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Default.Mic,
            contentDescription = "Recording a voice note",
            tint = color,
            modifier = Modifier.size(14.dp)
        )
        repeat(4) { bar ->
            val level by transition.animateFloat(
                initialValue = 0.3f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(380, easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse,
                    initialStartOffset = StartOffset(bar * 90)
                ),
                label = "recording_bar_$bar"
            )
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .height(14.dp)
                    .graphicsLayer { scaleY = level }
                    .background(color.copy(alpha = 0.85f), RoundedCornerShape(2.dp))
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

        DateChip(
            dateText = dateText,
            isPrivateMode = isPrivateMode,
            isDark = isDark,
            modifier = Modifier.padding(horizontal = 10.dp)
        )

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

/** The date pill used by the in-list date separators and the floating date while scrolling. */
@Composable
fun DateChip(
    dateText: String,
    isPrivateMode: Boolean,
    isDark: Boolean,
    modifier: Modifier = Modifier,
    shadowElevation: Dp = 1.dp
) {
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
        shadowElevation = shadowElevation,
        modifier = modifier
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








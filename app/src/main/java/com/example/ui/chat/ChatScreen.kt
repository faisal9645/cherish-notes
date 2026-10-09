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
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
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
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
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
    onNavigateToDates: () -> Unit = {},
    onNavigateToOurWords: () -> Unit = {},
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
    var showScheduleSheet by remember { mutableStateOf(false) }
    var showScheduledList by remember { mutableStateOf(false) }
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

    // The reply preview floats above the message box; the list keeps this much room for it
    var bottomOverlayHeightPx by remember { mutableIntStateOf(0) }
    // Keeps the preview filled while it animates away after the reply is cleared
    val lastReply = remember { arrayOfNulls<Message>(1) }
    uiState.replyingToMessage?.let { lastReply[0] = it }

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

    // Filter messages for search query and starred filter - all messages preserved and displayed based on pagination scroll.
    // Chat from before today stays hidden until "Recover all" (or "Show in chat") turns previous chats on.
    // Filtered (today only / Recover All, starred, search) in the background by the view model
    val displayedMessages by viewModel.displayedMessages.collectAsState()

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
                            listState.revealMessage(shown.lastIndex - index)
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

    // Read ticks only when the chat is really in front of you: not hidden behind Notes or the
    // stealth curtain, and with the app open. (Arrival while hidden still counts as delivered.)
    val chatLifecycleState by androidx.lifecycle.compose.LocalLifecycleOwner.current.lifecycle.currentStateFlow.collectAsState()
    val isChatInView = !isDisguiseActive && !uiState.isStealthCurtainActive &&
        chatLifecycleState.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED)
    // Moments, one after another when the chat is opened: good morning (7 AM to noon, once a day),
    // the anniversary / monthly date / 100th day (once that day), and from 10 PM the goodnight card
    // (once a night, unless a goodnight was already sent tonight)
    var showGoodMorning by remember { mutableStateOf(false) }
    var celebration by remember { mutableStateOf<String?>(null) }
    var showGoodnightCard by remember { mutableStateOf(false) }
    var showGoodnightStars by remember { mutableStateOf(false) }
    // Our dates (shared by both phones): the earliest anniversary starts the days counter
    val ourDates by app.coupleFeaturesRepository.datesFlow.collectAsState()
    // Reminders also cover both of our birthdays (set on the Love & Us tab)
    val birthdays by app.authRepository.birthdays.collectAsState()
    val datesWithBirthdays = remember(ourDates, birthdays, myUser?.id, partner?.id, partnerName) {
        val titles = buildMap {
            myUser?.id?.ifBlank { null }?.let { put(it, "Your birthday") }
            (partner?.id ?: myUser?.partnerId)?.ifBlank { null }?.let { put(it, "$partnerName's birthday") }
        }
        com.example.ui.dates.DateReminders.withBirthdays(ourDates, birthdays, titles)
    }
    val latestOurDates by rememberUpdatedState(datesWithBirthdays)
    val togetherSinceSetting by app.authRepository.togetherSince.collectAsState()
    val togetherSince = remember(ourDates, togetherSinceSetting) {
        com.example.ui.dates.DateReminders.togetherSince(ourDates, togetherSinceSetting)
    }
    val latestTogetherSince by rememberUpdatedState(togetherSince)
    var datesReminder by remember { mutableStateOf<List<com.example.ui.dates.UpcomingDate>>(emptyList()) }
    // In the first days of a month: last month's story is offered, and plays when asked
    var monthRecapInvite by remember { mutableStateOf<com.example.ui.recap.MonthRecap?>(null) }
    var monthRecapStory by remember { mutableStateOf<com.example.ui.recap.MonthRecap?>(null) }
    val sentGoodnightTonight by rememberUpdatedState(
        uiState.messages.any {
            it.effect == EFFECT_GOODNIGHT && it.senderId == currentUserId && it.timestamp >= tonightStartMillis()
        }
    )
    LaunchedEffect(isChatInView) {
        if (!isChatInView) return@LaunchedEffect
        try {
            delay(450)
            if (isGoodMorningDue(context)) {
                markGoodMorningShown(context)
                showGoodMorning = true
                delay(4_500)
                showGoodMorning = false
                delay(400)
            }
            dueCelebration(context, latestTogetherSince)?.let { title ->
                markCelebrated(context)
                celebration = title
                delay(6_000)
                celebration = null
                delay(400)
            }
            // Birthdays, anniversaries and our days coming up this week: once a day
            val reminders = com.example.ui.dates.DateReminders.dueReminder(context, latestOurDates)
            if (reminders.isNotEmpty()) {
                com.example.ui.dates.DateReminders.markReminderShown(context)
                datesReminder = reminders
                while (datesReminder.isNotEmpty()) delay(250)
                delay(300)
            }
            // A new month: "Our October is ready" (once; it waits a while, then steps aside)
            if (com.example.ui.recap.OurMonthRecap.isInviteDue(context)) {
                val recap = com.example.ui.recap.OurMonthRecap.load(context, viewModel.uiState.value.currentUser?.id.orEmpty())
                if (recap != null && recap.totalMessages > 0) {
                    com.example.ui.recap.OurMonthRecap.markInvited(context)
                    monthRecapInvite = recap
                    var waited = 0
                    while (monthRecapInvite != null && waited < 15_000) {
                        delay(250)
                        waited += 250
                    }
                    monthRecapInvite = null
                    while (monthRecapStory != null) delay(250)
                    delay(300)
                }
            }
            if (isGoodnightDue(context) && !sentGoodnightTonight) {
                while (showGoodnightStars) delay(250)
                markGoodnightShown(context)
                showGoodnightCard = true
            }
        } finally {
            showGoodMorning = false
            celebration = null
            monthRecapInvite = null
        }
    }

    // The partner said goodnight: the first time it's seen (within the night), the screen softly
    // dims with stars
    val latestGoodnight = remember(uiState.messages, currentUserId) {
        uiState.messages
            .filter { it.effect == EFFECT_GOODNIGHT && it.senderId != currentUserId && !it.isDeleted }
            .maxByOrNull { it.timestamp }
    }
    LaunchedEffect(latestGoodnight?.id, isChatInView) {
        val goodnight = latestGoodnight ?: return@LaunchedEffect
        if (!isChatInView || isGoodnightStarsSeen(context, goodnight.id)) return@LaunchedEffect
        if (System.currentTimeMillis() - goodnight.timestamp > 10L * 60 * 60 * 1000) return@LaunchedEffect
        delay(600)
        while (showGoodMorning || celebration != null || showGoodnightCard) delay(250)
        markGoodnightStarsSeen(context, goodnight.id)
        try {
            showGoodnightStars = true
            delay(7_000)
        } finally {
            showGoodnightStars = false
        }
    }

    // A "thinking of you" heartbeat arrived while the chat is open: a heart beats with it
    var heartbeatPulseKey by remember { mutableStateOf(0L) }
    LaunchedEffect(isChatInView) {
        if (!isChatInView) return@LaunchedEffect
        com.example.notifications.ThinkingOfYou.received.collect { heartbeatPulseKey = it }
    }

    // The partner is stressed (a fresh mood): offer to send a hug, once per mood
    val partnerMoodAt = partner?.moodAt ?: 0L
    var hugNudgeHandled by remember { mutableStateOf(hugNudgeHandledFor(context)) }
    val showHugNudge = isChatInView && isStressedMood(partner?.mood) && partnerMoodAt > 0L &&
        System.currentTimeMillis() - partnerMoodAt < MOOD_FRESH_MS && hugNudgeHandled != partnerMoodAt

    // Jump-to-unread: the messages that were unread when the chat came into view (gathered for a
    // few seconds, as they load, before they show as read), so a pill can take you to the first one
    var unreadOnOpen by remember { mutableStateOf<Set<String>>(emptySet()) }
    var unreadJumpDone by remember { mutableStateOf(false) }
    val chatInViewSince = remember { longArrayOf(-1L) }
    if (!isChatInView) {
        chatInViewSince[0] = -1L
    } else if (chatInViewSince[0] < 0L) {
        chatInViewSince[0] = android.os.SystemClock.uptimeMillis()
    }
    LaunchedEffect(isChatInView) {
        if (!isChatInView) {
            unreadOnOpen = emptySet()
            unreadJumpDone = false
        }
    }

    LaunchedEffect(unreadIds, isChatInView) {
        if (isChatInView) {
            // Taken here, just before they're marked read
            val sinceInView = android.os.SystemClock.uptimeMillis() - chatInViewSince[0]
            if (!unreadJumpDone && unreadIds.isNotEmpty() && sinceInView in 0L..4000L) {
                unreadOnOpen = unreadOnOpen + unreadIds
            }
            viewModel.markMessagesAsRead(unreadIds)
        }
    }

    // Behind Notes nothing of the chat stays open: sheets, dialogs, the recorder, selection, the
    // keyboard (viewers, recording and playback are stopped in ChatViewModel.onSecretAppHidden)
    LaunchedEffect(isDisguiseActive) {
        if (!isDisguiseActive) return@LaunchedEffect
        showVideoNoteRecorder = false
        showAttachmentSheet = false
        showChatMenu = false
        showHeartbeatTouch = false
        showMoodPicker = false
        showGoodnightCard = false
        showGoodnightStars = false
        datesReminder = emptyList()
        showScheduleSheet = false
        showScheduledList = false
        showClearChatDialog = false
        showMultiDeleteConfirmDialog = false
        showDeleteConfirmDialog = null
        showFullProfilePicViewer = false
        editingMessage = null
        focusedMessage = null
        exitSelection()
        keyboardController?.hide()
    }
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
                listState.revealMessage(targetIndex)
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

    // The wallpaper sits behind the whole screen, so the top bar can be frosted glass over it
    val showWallpaper = !uiState.isStealthCurtainActive &&
        uiState.chatExperienceMode != com.example.ui.chat.ChatExperienceMode.PRIVATE
    var chatScreenSize by remember { mutableStateOf(IntSize.Zero) }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .onSizeChanged { chatScreenSize = it }
            .graphicsLayer {
                // The chat blurs behind the long-press focus view (Android 12+)
                if (android.os.Build.VERSION.SDK_INT >= 31) {
                    val radius = 10.dp.toPx() * focusProgress.value.coerceIn(0f, 1f)
                    renderEffect = if (radius > 0.5f) BlurEffect(radius, radius, TileMode.Clamp) else null
                }
            }
    ) {
    if (showWallpaper) {
        ChatWallpaper(
            chatBgTheme = uiState.chatBgTheme,
            modifier = Modifier.fillMaxSize()
        )
    }
    Scaffold(
        modifier = Modifier
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
            } else Box {
                // Frosted glass: the wallpaper behind the bar, blurred (Android 12+; tinted before)
                if (showWallpaper && android.os.Build.VERSION.SDK_INT >= 31 && chatScreenSize != IntSize.Zero) {
                    Box(modifier = Modifier.matchParentSize().clipToBounds()) {
                        ChatWallpaper(
                            chatBgTheme = uiState.chatBgTheme,
                            modifier = Modifier
                                .layout { measurable, constraints ->
                                    // Laid out like the real wallpaper (whole screen, from the top), so it lines up
                                    val wallpaper = measurable.measure(Constraints.fixed(chatScreenSize.width, chatScreenSize.height))
                                    layout(constraints.maxWidth, constraints.maxHeight) { wallpaper.place(0, 0) }
                                }
                                .blur(24.dp)
                        )
                    }
                }
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
                                        val partnerMood = partner?.mood?.takeIf {
                                            val moodAt = partner?.moodAt ?: 0L
                                            moodAt == 0L || System.currentTimeMillis() - moodAt < MOOD_FRESH_MS
                                        }
                                        if (!partnerMood.isNullOrBlank()) {
                                            Surface(
                                                shape = RoundedCornerShape(10.dp),
                                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.75f),
                                                modifier = Modifier
                                                    .weight(1f, fill = false)
                                                    .clickable { showMoodPicker = true }
                                            ) {
                                                EmojiText(
                                                    text = partnerMood,
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Medium,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                    emojiScale = 1.2f,
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                )
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
                                            // The exact time, which stays right while the screen sits open
                                            // (a "2m ago" would freeze until something else changed)
                                            val lastSeen = partner?.lastSeen ?: 0L
                                            if (lastSeen > 0L) {
                                                val time = SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(lastSeen))
                                                when {
                                                    android.text.format.DateUtils.isToday(lastSeen) -> "last seen today at $time"
                                                    android.text.format.DateUtils.isToday(lastSeen + android.text.format.DateUtils.DAY_IN_MILLIS) ->
                                                        "last seen yesterday at $time"
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
                                    text = {
                                        val myMood = myUser?.mood?.takeIf {
                                            it.isNotBlank() && (myUser?.moodAt ?: 0L).let { at -> at == 0L || System.currentTimeMillis() - at < MOOD_FRESH_MS }
                                        }
                                        Text(
                                            text = if (myMood != null) "Mood: $myMood" else "Set Mood Status",
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    },
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
                                // The starred messages as a little book
                                DropdownMenuItem(
                                    text = { Text("Our Words") },
                                    onClick = {
                                        showChatMenu = false
                                        onNavigateToOurWords()
                                    },
                                    leadingIcon = { Icon(Icons.AutoMirrored.Filled.MenuBook, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface) },
                                    modifier = Modifier.testTag("chat_menu_our_words")
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
                    containerColor = if (showWallpaper) {
                        MaterialTheme.colorScheme.surface.copy(alpha = if (android.os.Build.VERSION.SDK_INT >= 31) 0.72f else 0.92f)
                    } else {
                        MaterialTheme.colorScheme.surface
                    }
                ),
                windowInsets = WindowInsets.statusBars
            )
            HorizontalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
            }
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
                        unreadJumpDone = true
                        viewModel.sendTextMessage(composerText)
                        composerText = ""
                        viewModel.onTypingChanged(false)
                    },
                    replyingTo = uiState.replyingToMessage,
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
                    onScheduleText = { if (composerText.isNotBlank()) showScheduleSheet = true },
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
        containerColor = if (showWallpaper) Color.Transparent else MaterialTheme.colorScheme.background
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
                                    listState.revealMessage(displayedMessages.lastIndex - index)
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

            // Personal space (a Check-After over 2 days): the partner's request to answer, mine
            // waiting for them, or their answer to mine
            val spaceRequest by viewModel.spaceRequest.collectAsState()
            spaceRequest?.let { request ->
                if (!uiState.isStealthCurtainActive) {
                    var answerSeen by remember(request.id) { mutableStateOf(viewModel.isSpaceAnswerSeen(request.id)) }
                    SpaceRequestCard(
                        request = request,
                        myId = currentUserId,
                        partnerName = partnerName,
                        answerSeen = answerSeen,
                        onAccept = { viewModel.respondToSpaceRequest(accept = true) },
                        onDecline = { viewModel.respondToSpaceRequest(accept = false) },
                        onCancel = { viewModel.cancelSpaceRequest() },
                        onAnswerSeen = {
                            viewModel.markSpaceAnswerSeen(request.id)
                            answerSeen = true
                        },
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
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
                if (uiState.isStealthCurtainActive) {
                    // Emergency Privacy Shield: Harmless daily notes / tasks view hiding all previous chat
                    StealthDisguiseNotesView(
                        onRestore = onQuickDisguise
                    )
            } else {
                val reversedMessages = remember(displayedMessages) { displayedMessages.reversed() }
                
                val hasTodayMessages = remember(displayedMessages) { displayedMessages.any { isToday(it.timestamp) } }
                // An empty day still opens with "Today" and the 2-person banner instead of a blank screen
                val showEmptyTodayHeader = !hasTodayMessages && uiState.searchQuery.isBlank() && !uiState.filterStarredOnly

                // Older chat only pages in once previous chats are recovered, as the list nears the top
                val canLoadMore = uiState.showPreviousChats && !uiState.isPaginationExhausted && !uiState.isLoadingMore
                val shouldLoadMore by remember(canLoadMore) {
                    derivedStateOf {
                        if (!canLoadMore) return@derivedStateOf false
                        val totalItems = listState.layoutInfo.totalItemsCount
                        val lastVisibleItem = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
                        totalItems == 0 || lastVisibleItem >= totalItems - HISTORY_PREFETCH_ITEMS
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
                    // A short chat (e.g. a new day) starts at the top, under the header, instead of
                    // sitting at the bottom by the message box
                    verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.Top)
                ) {
                    // A new day with nothing shown yet: "Today" and the banner open the chat at the top
                    if (showEmptyTodayHeader && reversedMessages.isEmpty()) {
                        item(key = "empty_today_header", contentType = "today_header") {
                            val isPrivate = uiState.chatExperienceMode == com.example.ui.chat.ChatExperienceMode.PRIVATE
                            Column(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                                DateSeparatorBadge(dateText = "Today", isPrivateMode = isPrivate, isDark = isDark)
                                StrictlyTwoPersonBanner(isPrivateMode = isPrivate, isDark = isDark)
                            }
                        }
                    }

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
                                    color = if (isDark) darkTone(Color(0xFF1E2430)).copy(alpha = 0.95f) else Color.White.copy(alpha = 0.95f),
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

                // No message yet today but older ones are shown (Recover all): "Today" and the banner
                // follow them, where the first message will land (once it does, its row shows them)
                if (showEmptyTodayHeader && reversedMessages.isNotEmpty()) {
                    val isPrivate = uiState.chatExperienceMode == com.example.ui.chat.ChatExperienceMode.PRIVATE
                    Column(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                        DateSeparatorBadge(dateText = "Today", isPrivateMode = isPrivate, isDark = isDark)
                        StrictlyTwoPersonBanner(isPrivateMode = isPrivate, isDark = isDark)
                    }
                }

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

                // Room for what floats above the message box (upload status, reply preview), so it
                // never covers the last message on either side; follows its height as it animates
                Spacer(
                    modifier = Modifier
                        .fillMaxWidth()
                        .layout { measurable, constraints ->
                            val overlay = bottomOverlayHeightPx
                            val height = if (overlay > 0) overlay + 12.dp.roundToPx() else 0
                            val placeable = measurable.measure(constraints.copy(minHeight = 0, maxHeight = 0))
                            layout(constraints.maxWidth, height) { placeable.place(0, 0) }
                        }
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
                    val layoutInfo = listState.layoutInfo
                    val topItem = layoutInfo.visibleItemsInfo.lastOrNull() ?: return@derivedStateOf null
                    val topIndex = topItem.index
                    val message = reversedMessages.getOrNull(topIndex)
                    if (message != null) {
                        // The top message opens its day and its separator ("Yesterday"…) is on screen:
                        // the date would show twice. (Offsets run from the bottom: the list is reversed.)
                        val opensDay = topIndex == reversedMessages.lastIndex ||
                            !isSameDay(reversedMessages[topIndex + 1].timestamp, message.timestamp)
                        if (opensDay && topItem.offset + topItem.size <= layoutInfo.viewportEndOffset) {
                            return@derivedStateOf null
                        }
                    }
                    (message ?: reversedMessages.lastOrNull())
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

            // Jump-to-unread pill: "N new messages" while the first of them is off screen; a tap
            // goes straight there. Gone once it has been on screen.
            val unreadJumpCount = remember(reversedMessages, unreadOnOpen) {
                if (unreadOnOpen.isEmpty()) 0 else reversedMessages.count { it.id in unreadOnOpen }
            }
            // The oldest of them (the list runs bottom-up, so the highest index)
            val unreadJumpIndex = remember(reversedMessages, unreadOnOpen) {
                if (unreadOnOpen.isEmpty()) -1 else reversedMessages.indexOfLast { it.id in unreadOnOpen }
            }
            // 1 = above the screen, -1 = below it, 0 = on screen, null = not known yet
            val unreadJumpWhere by remember(unreadJumpIndex) {
                derivedStateOf {
                    val visible = listState.layoutInfo.visibleItemsInfo
                    when {
                        unreadJumpIndex < 0 || visible.isEmpty() -> null
                        unreadJumpIndex > visible.last().index -> 1
                        unreadJumpIndex < visible.first().index -> -1
                        else -> 0
                    }
                }
            }
            LaunchedEffect(unreadJumpWhere) {
                if (unreadJumpWhere == 0) unreadJumpDone = true
            }
            val unreadJumpUp = remember { booleanArrayOf(true) }
            unreadJumpWhere?.takeIf { it != 0 }?.let { unreadJumpUp[0] = it == 1 }
            val showUnreadJump = !unreadJumpDone && unreadJumpCount > 0 && (unreadJumpWhere == 1 || unreadJumpWhere == -1)
            val unreadJumpDrop by animateDpAsState(
                targetValue = if (showDateChip && topVisibleDate != null) 40.dp else 0.dp,
                animationSpec = spring(dampingRatio = 0.85f, stiffness = 500f),
                label = "unread_jump_drop"
            )
            androidx.compose.animation.AnimatedVisibility(
                visible = showUnreadJump,
                enter = fadeIn(tween(180)) + slideInVertically(spring(dampingRatio = 0.75f, stiffness = 500f)) { -it },
                exit = fadeOut(tween(200)) + scaleOut(tween(200), targetScale = 0.85f),
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 10.dp)
                    .offset { IntOffset(0, unreadJumpDrop.roundToPx()) }
            ) {
                val isPrivate = uiState.chatExperienceMode == com.example.ui.chat.ChatExperienceMode.PRIVATE
                Surface(
                    onClick = {
                        val index = unreadJumpIndex
                        unreadJumpDone = true
                        val target = reversedMessages.getOrNull(index)
                        if (target != null) {
                            view.chatHaptic(ChatHaptic.Tick)
                            scope.launch {
                                listState.revealMessage(index)
                                highlightedMessageId = target.id
                                delay(2200)
                                if (highlightedMessageId == target.id) highlightedMessageId = null
                            }
                        }
                    },
                    shape = CircleShape,
                    color = if (isPrivate) (if (isDark) darkTone(Color(0xFF2E2F33)) else Color(0xFFE5E7EB)) else MaterialTheme.colorScheme.primary,
                    contentColor = if (isPrivate) (if (isDark) darkTone(Color(0xFFECECEC)) else Color(0xFF1F2937)) else MaterialTheme.colorScheme.onPrimary,
                    shadowElevation = 4.dp,
                    modifier = Modifier.testTag("jump_to_unread")
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(start = 10.dp, end = 14.dp, top = 7.dp, bottom = 7.dp)
                    ) {
                        Icon(
                            imageVector = if (unreadJumpUp[0]) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (unreadJumpCount == 1) "1 new message" else "$unreadJumpCount new messages",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
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
                        containerColor = if (isPrivate) (if (isDark) darkTone(Color(0xFF2E2F33)) else Color(0xFFE5E7EB)) else MaterialTheme.colorScheme.primaryContainer,
                        contentColor = if (isPrivate) (if (isDark) darkTone(Color(0xFFECECEC)) else Color(0xFF1F2937)) else MaterialTheme.colorScheme.onPrimaryContainer,
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
                                    if (isPrivate) (if (isDark) darkTone(Color(0xFFECECEC)) else Color(0xFF1F2937)) else HeartRed,
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

            // Floating above the message box, over the chat: the upload in progress (or the one that
            // didn't go out, with Retry) and the reply preview. Lined up with the box, clear of the
            // mic/send button.
            Column(
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .widthIn(max = 600.dp)
                    .fillMaxWidth()
                    .padding(start = 12.dp, end = 70.dp, bottom = 6.dp)
                    .onSizeChanged { bottomOverlayHeightPx = it.height }
            ) {
                val isPrivate = uiState.chatExperienceMode == com.example.ui.chat.ChatExperienceMode.PRIVATE
                val scheduled by viewModel.scheduledMessages.collectAsState()
                androidx.compose.animation.AnimatedVisibility(
                    visible = scheduled.isNotEmpty(),
                    enter = slideInVertically(spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMediumLow)) { it / 2 } + fadeIn(tween(160)),
                    exit = slideOutVertically(tween(160)) { it / 2 } + fadeOut(tween(120))
                ) {
                    scheduled.firstOrNull()?.let { next ->
                        ScheduledMessagesPill(next = next, count = scheduled.size, onClick = { showScheduledList = true })
                    }
                }
                val failedUpload = uiState.failedUpload
                androidx.compose.animation.AnimatedVisibility(
                    visible = uiState.isUploadingMedia || failedUpload != null,
                    enter = slideInVertically(spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMediumLow)) { it / 2 } + fadeIn(tween(160)),
                    exit = slideOutVertically(tween(160)) { it / 2 } + fadeOut(tween(120))
                ) {
                    UploadStatusPill(
                        isUploading = uiState.isUploadingMedia,
                        progress = uiState.uploadProgress,
                        label = if (uiState.isUploadingMedia) uiState.uploadLabel else failedUpload?.label.orEmpty(),
                        isPrivateMode = isPrivate,
                        onCancel = { viewModel.cancelUpload() },
                        onRetry = { viewModel.retryFailedUpload() },
                        onDismiss = { viewModel.dismissFailedUpload() }
                    )
                }
                androidx.compose.animation.AnimatedVisibility(
                    visible = uiState.replyingToMessage != null,
                    enter = slideInVertically(spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMediumLow)) { it / 2 } + fadeIn(tween(160)),
                    exit = slideOutVertically(tween(160)) { it / 2 } + fadeOut(tween(120))
                ) {
                    val reply = uiState.replyingToMessage ?: lastReply[0]
                    if (reply != null) {
                        ReplyPreviewCard(
                            reply = reply,
                            isPrivateMode = isPrivate,
                            onDismiss = { viewModel.setReplyingTo(null) },
                            modifier = Modifier.fillMaxWidth()
                        )
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
    } // Box: wallpaper + Scaffold

    androidx.compose.animation.AnimatedVisibility(
        visible = showGoodMorning,
        enter = fadeIn(tween(350)),
        exit = fadeOut(tween(500))
    ) {
        GoodMorningGreeting(
            myName = myUser?.displayName?.ifBlank { null } ?: "love",
            partnerName = partnerName,
            onDismiss = { showGoodMorning = false }
        )
    }

    androidx.compose.animation.AnimatedVisibility(
        visible = celebration != null,
        enter = fadeIn(tween(350)),
        exit = fadeOut(tween(600))
    ) {
        // Kept while fading out
        val title = remember { celebration } ?: return@AnimatedVisibility
        LoveCelebration(
            title = "$title \uD83D\uDC9B",
            subtitle = LoveDates.formatLong(togetherSince)?.let { "Together since $it" },
            onDismiss = { celebration = null }
        )
    }

    androidx.compose.animation.AnimatedVisibility(
        visible = datesReminder.isNotEmpty(),
        enter = fadeIn(tween(300)),
        exit = fadeOut(tween(300))
    ) {
        // Kept while fading out
        val shown = remember { datesReminder }
        com.example.ui.dates.DatesReminderCard(
            reminders = shown,
            onSeeAll = {
                datesReminder = emptyList()
                onNavigateToDates()
            },
            onDismiss = { datesReminder = emptyList() }
        )
    }

    androidx.compose.animation.AnimatedVisibility(
        visible = showGoodnightCard,
        enter = fadeIn(tween(400)),
        exit = fadeOut(tween(500))
    ) {
        GoodnightCard(
            partnerName = partnerName,
            onSendGoodnight = { viewModel.sendGoodnight() },
            onDismiss = { showGoodnightCard = false }
        )
    }

    androidx.compose.animation.AnimatedVisibility(
        visible = showGoodnightStars,
        enter = fadeIn(tween(900)),
        exit = fadeOut(tween(1_200))
    ) {
        GoodnightStarsOverlay(partnerName = partnerName, onDismiss = { showGoodnightStars = false })
    }

    if (heartbeatPulseKey != 0L) {
        key(heartbeatPulseKey) {
            HeartbeatReceivedPulse(onDone = { heartbeatPulseKey = 0L })
        }
    }

    androidx.compose.animation.AnimatedVisibility(
        visible = monthRecapInvite != null,
        enter = fadeIn(tween(300)) + slideInVertically(tween(350)) { -it / 2 },
        exit = fadeOut(tween(250)) + slideOutVertically(tween(250)) { -it / 2 }
    ) {
        // Kept while fading out
        val shown = remember { monthRecapInvite } ?: return@AnimatedVisibility
        com.example.ui.recap.OurMonthInviteCard(
            recap = shown,
            onWatch = {
                monthRecapStory = shown
                monthRecapInvite = null
            },
            onDismiss = { monthRecapInvite = null }
        )
    }
    monthRecapStory?.let { recap ->
        com.example.ui.recap.OurMonthStory(
            recap = recap,
            myName = myUser?.displayName?.ifBlank { null } ?: "Me",
            partnerName = partnerName,
            onDismiss = { monthRecapStory = null }
        )
    }

    androidx.compose.animation.AnimatedVisibility(
        visible = showHugNudge,
        enter = fadeIn(tween(300)) + slideInVertically(tween(350)) { -it / 2 },
        exit = fadeOut(tween(250)) + slideOutVertically(tween(250)) { -it / 2 }
    ) {
        HugNudgeCard(
            partnerName = partnerName,
            mood = partner?.mood.orEmpty(),
            onSendHug = {
                viewModel.sendHug()
                viewModel.sendThinkingOfYou()
                markHugNudgeHandled(context, partnerMoodAt)
                hugNudgeHandled = partnerMoodAt
            },
            onDismiss = {
                markHugNudgeHandled(context, partnerMoodAt)
                hugNudgeHandled = partnerMoodAt
            }
        )
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

    // Send later: pick when (long-press on send)
    if (showScheduleSheet) {
        ScheduleMessageDialog(
            text = composerText,
            onSchedule = { sendAt ->
                viewModel.scheduleMessage(composerText, sendAt)
                composerText = ""
                viewModel.onTypingChanged(false)
                showScheduleSheet = false
                Toast.makeText(context, "Scheduled for " + formatScheduleTime(sendAt), Toast.LENGTH_SHORT).show()
            },
            onDismiss = { showScheduleSheet = false }
        )
    }

    // Scheduled messages waiting to go out, each can be cancelled
    if (showScheduledList) {
        val scheduled by viewModel.scheduledMessages.collectAsState()
        ScheduledMessagesDialog(
            messages = scheduled,
            onCancel = { id -> viewModel.cancelScheduledMessage(id) },
            onDismiss = { showScheduledList = false }
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
            onDismiss = { showMoodPicker = false },
            currentMoodAt = myUser?.moodAt ?: 0L,
            partnerName = partnerName
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
                if (targetMsg != null && !viewModel.navigateToMessageInChat(targetMsg.id)) {
                    Toast.makeText(context, "Older chat is hidden. Use Recover All in settings to see it.", Toast.LENGTH_LONG).show()
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
        },
        onRequestSpace = { target, note ->
            if (viewModel.requestSpace(target, note)) {
                Toast.makeText(context, "Asked $partnerName. It starts when they accept.", Toast.LENGTH_LONG).show()
            } else {
                Toast.makeText(context, "Couldn't send the request. Try again.", Toast.LENGTH_SHORT).show()
            }
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
 * A photo/video/voice upload in progress (progress ring, cancel), or one that didn't go out
 * (Retry, dismiss). Shown above the message box.
 */
@Composable
private fun UploadStatusPill(
    isUploading: Boolean,
    progress: Float,
    label: String,
    isPrivateMode: Boolean,
    onCancel: () -> Unit,
    onRetry: () -> Unit,
    onDismiss: () -> Unit
) {
    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val accent = if (isPrivateMode) (if (isDark) darkTone(Color(0xFF9CA3AF)) else Color(0xFF6B7280)) else MaterialTheme.colorScheme.primary
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = if (isDark) darkSurface(Color(0xFF263049), Color(0xFF181818)) else Color.White,
        border = BorderStroke(1.dp, accent.copy(alpha = if (isDark) 0.35f else 0.22f)),
        shadowElevation = 6.dp
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(start = 12.dp)
        ) {
            if (isUploading) {
                CircularProgressIndicator(
                    progress = { progress.coerceIn(0.02f, 1f) },
                    color = accent,
                    trackColor = accent.copy(alpha = 0.18f),
                    strokeWidth = 2.dp,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Sending $label… ${(progress * 100).toInt().coerceIn(0, 100)}%",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                IconButton(onClick = onCancel, modifier = Modifier.size(36.dp).testTag("upload_cancel")) {
                    Icon(Icons.Default.Close, contentDescription = "Cancel sending", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
                }
            } else {
                Icon(Icons.Default.ErrorOutline, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = label.replaceFirstChar { it.uppercase() } + " not sent",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                TextButton(
                    onClick = onRetry,
                    contentPadding = PaddingValues(horizontal = 10.dp),
                    modifier = Modifier.height(36.dp).testTag("upload_retry")
                ) {
                    Text("Retry", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = accent)
                }
                IconButton(onClick = onDismiss, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Default.Close, contentDescription = "Dismiss", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
                }
            }
        }
    }
}

private const val GOOD_MORNING_FROM_HOUR = 7
private const val GOOD_MORNING_UNTIL_HOUR = 12

private fun greetingPrefs(context: android.content.Context) =
    context.getSharedPreferences("cherish_greetings", android.content.Context.MODE_PRIVATE)

private fun todayKey(): String =
    java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(java.util.Date())

/** Morning (7 AM to noon) and today's greeting hasn't been shown yet. */
private fun isGoodMorningDue(context: android.content.Context): Boolean {
    val hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
    if (hour < GOOD_MORNING_FROM_HOUR || hour >= GOOD_MORNING_UNTIL_HOUR) return false
    return greetingPrefs(context).getString("good_morning_shown", null) != todayKey()
}

private fun markGoodMorningShown(context: android.content.Context) {
    greetingPrefs(context).edit().putString("good_morning_shown", todayKey()).apply()
}

/** A sunrise card over the chat: the sun rises, "Good morning, <name>", the date, a line for two. */
@Composable
private fun GoodMorningGreeting(myName: String, partnerName: String, onDismiss: () -> Unit) {
    val rise = remember { Animatable(0f) }
    val cardIn = remember { Animatable(0.86f) }
    LaunchedEffect(Unit) {
        launch { cardIn.animateTo(1f, spring(dampingRatio = 0.62f, stiffness = 380f)) }
        rise.animateTo(1f, tween(1400, easing = FastOutSlowInEasing))
    }
    val glow = rememberInfiniteTransition(label = "sun_glow")
    val glowScale by glow.animateFloat(
        initialValue = 0.9f,
        targetValue = 1.12f,
        animationSpec = infiniteRepeatable(tween(1600, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "sun_glow_scale"
    )
    val date = remember {
        java.text.SimpleDateFormat("EEEE, MMMM d", java.util.Locale.getDefault()).format(java.util.Date())
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.3f))
            .pointerInput(Unit) { detectTapGestures { onDismiss() } }
            .testTag("good_morning_greeting"),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .padding(horizontal = 32.dp)
                .fillMaxWidth()
                .graphicsLayer {
                    scaleX = cardIn.value
                    scaleY = cardIn.value
                }
                .shadow(18.dp, RoundedCornerShape(28.dp))
                .clip(RoundedCornerShape(28.dp))
                .background(
                    Brush.verticalGradient(
                        listOf(Color(0xFFFFE6B8), Color(0xFFFFC2A1), Color(0xFFFF9EB5))
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 26.dp)
            ) {
                // The sun rising behind a soft glow
                Box(modifier = Modifier.size(110.dp), contentAlignment = Alignment.Center) {
                    Box(
                        modifier = Modifier
                            .size(92.dp)
                            .graphicsLayer {
                                scaleX = glowScale
                                scaleY = glowScale
                                alpha = rise.value * 0.55f
                            }
                            .background(
                                Brush.radialGradient(listOf(Color(0xFFFFF3C4), Color(0x00FFF3C4))),
                                CircleShape
                            )
                    )
                    Text(
                        text = "\u2600\uFE0F",
                        fontSize = 58.sp,
                        modifier = Modifier.graphicsLayer {
                            translationY = (1f - rise.value) * 46.dp.toPx()
                            alpha = rise.value.coerceIn(0f, 1f)
                            rotationZ = rise.value * 25f
                        }
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Good morning, $myName",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF4A2A2E),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(text = date, fontSize = 13.sp, color = Color(0xFF7A4A4E))
                Spacer(modifier = Modifier.height(14.dp))
                Text(
                    text = "A new day with $partnerName \uD83D\uDC9B",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color(0xFF5E3438),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
        }
    }
}

/** The next [hour]:[minute] from now (today if it's still ahead, else tomorrow). */
private fun nextTimeAt(hour: Int, minute: Int = 0): Long {
    val cal = java.util.Calendar.getInstance().apply {
        set(java.util.Calendar.HOUR_OF_DAY, hour)
        set(java.util.Calendar.MINUTE, minute)
        set(java.util.Calendar.SECOND, 0)
        set(java.util.Calendar.MILLISECOND, 0)
    }
    if (cal.timeInMillis <= System.currentTimeMillis() + 60_000L) cal.add(java.util.Calendar.DAY_OF_YEAR, 1)
    return cal.timeInMillis
}

/** "Today 10:00 PM", "Tomorrow 7:00 AM" or "Oct 12, 7:00 AM". */
private fun formatScheduleTime(at: Long): String {
    val time = java.text.SimpleDateFormat("h:mm a", java.util.Locale.getDefault()).format(java.util.Date(at))
    val day = java.util.Calendar.getInstance().apply { timeInMillis = at }
    val today = java.util.Calendar.getInstance()
    val tomorrow = java.util.Calendar.getInstance().apply { add(java.util.Calendar.DAY_OF_YEAR, 1) }
    fun sameDay(a: java.util.Calendar, b: java.util.Calendar) =
        a.get(java.util.Calendar.YEAR) == b.get(java.util.Calendar.YEAR) && a.get(java.util.Calendar.DAY_OF_YEAR) == b.get(java.util.Calendar.DAY_OF_YEAR)
    return when {
        sameDay(day, today) -> "Today $time"
        sameDay(day, tomorrow) -> "Tomorrow $time"
        else -> java.text.SimpleDateFormat("MMM d", java.util.Locale.getDefault()).format(java.util.Date(at)) + ", $time"
    }
}

/** Pick when to send [text]: quick choices, or any time from the clock. */
@Composable
private fun ScheduleMessageDialog(text: String, onSchedule: (Long) -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val choices = remember {
        listOf(
            nextTimeAt(7),
            nextTimeAt(22),
            System.currentTimeMillis() + 60 * 60 * 1000L
        )
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Send later", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "\u201C" + text.trim() + "\u201D",
                    maxLines = 2,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 13.sp
                )
                Spacer(modifier = Modifier.height(4.dp))
                choices.forEachIndexed { i, at ->
                    OutlinedButton(
                        onClick = { onSchedule(at) },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(if (i == 2) "In 1 hour" else formatScheduleTime(at))
                    }
                }
                OutlinedButton(
                    onClick = {
                        val now = java.util.Calendar.getInstance()
                        android.app.TimePickerDialog(
                            context,
                            { _, hour, minute -> onSchedule(nextTimeAt(hour, minute)) },
                            now.get(java.util.Calendar.HOUR_OF_DAY),
                            now.get(java.util.Calendar.MINUTE),
                            false
                        ).show()
                    },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Schedule, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Pick a time")
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

/** Above the message box: the next scheduled message (and how many are waiting). */
@Composable
private fun ScheduledMessagesPill(next: com.example.notifications.ScheduledMessage, count: Int, onClick: () -> Unit) {
    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = if (isDark) darkSurface(Color(0xFF263049), Color(0xFF181818)) else Color.White,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = if (isDark) 0.35f else 0.22f)),
        shadowElevation = 6.dp,
        modifier = Modifier.clickable(onClick = onClick).testTag("scheduled_messages_pill")
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            Icon(Icons.Default.Schedule, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = (if (count > 1) "$count scheduled \u00B7 next " else "Scheduled \u00B7 ") + formatScheduleTime(next.sendAt),
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1
            )
        }
    }
}

/** The scheduled messages waiting to go out, each with a cancel button. */
@Composable
private fun ScheduledMessagesDialog(
    messages: List<com.example.notifications.ScheduledMessage>,
    onCancel: (String) -> Unit,
    onDismiss: () -> Unit
) {
    LaunchedEffect(messages.isEmpty()) { if (messages.isEmpty()) onDismiss() }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Scheduled messages", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                messages.forEach { message ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(formatScheduleTime(message.sendAt), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                            Text(message.text, fontSize = 13.sp, maxLines = 2, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                        }
                        IconButton(onClick = { onCancel(message.id) }) {
                            Icon(Icons.Default.Close, contentDescription = "Cancel scheduled message", tint = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } }
    )
}

/**
 * Scrolls the (bottom-up) chat list to a message and lifts it about a third of the way up the
 * screen, so a jumped-to message isn't left sitting under the typing bar.
 */
private suspend fun androidx.compose.foundation.lazy.LazyListState.revealMessage(index: Int) {
    val viewport = layoutInfo.viewportEndOffset - layoutInfo.viewportStartOffset
    animateScrollToItem(index, scrollOffset = -(viewport * 0.3f).toInt())
}

/**
 * Older chat starts loading this many messages before the top, so the next page is usually there
 * before the user reaches it and scrolling up doesn't stall.
 */
private const val HISTORY_PREFETCH_ITEMS = 15

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

        val selectionAccent = if (isPrivate) (if (isDark) darkTone(Color(0xFF9CA3AF)) else Color(0xFF6B7280)) else RoseGoldPrimary

        // A message that arrives while the chat is open lands with a spring: sent ones rise from
        // the composer, received ones slide in from the left
        val landing = remember(message.id) { Animatable(if (isFreshArrival) 0f else 1f) }
        LaunchedEffect(message.id) {
            if (isFreshArrival) landing.animateTo(1f, spring(dampingRatio = 0.62f, stiffness = 420f))
        }

        // Jump-to-message highlight: a soft wash of colour across the row (no outlines) and a
        // quick bump of the bubble; the wash fades slowly once released
        val glow = remember(message.id) { Animatable(0f) }
        val bump = remember(message.id) { Animatable(0f) }
        LaunchedEffect(isHighlighted) {
            if (isHighlighted) {
                launch {
                    delay(120)
                    bump.animateTo(1f, spring(dampingRatio = 0.5f, stiffness = 900f))
                    bump.animateTo(0f, spring(dampingRatio = 0.45f, stiffness = 300f))
                }
                glow.animateTo(1f, tween(260, easing = FastOutSlowInEasing))
            } else {
                glow.animateTo(0f, tween(900, easing = FastOutSlowInEasing))
            }
        }
        val glowColor = if (isPrivate) (if (isDark) darkTone(Color(0xFF6B7280)) else Color(0xFF9CA3AF)) else RoseGoldPrimary
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
                .drawBehind {
                    if (rowTint.alpha > 0f) drawRect(rowTint)
                    val g = glow.value
                    if (g > 0f) {
                        // Edge to edge, soft at the top and bottom
                        val wash = glowColor.copy(alpha = (if (isDark) 0.26f else 0.18f) * g)
                        drawRect(
                            Brush.verticalGradient(
                                0f to Color.Transparent,
                                0.22f to wash,
                                0.78f to wash,
                                1f to Color.Transparent
                            )
                        )
                    }
                }
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
                color = if (isDark) darkSurface(Color(0xFF1E2638)) else Color(0xFFF1F5FB),
                border = BorderStroke(0.5.dp, if (isDark) darkSurface(Color(0xFF2A364F), Color(0xFF262626)) else Color(0xFFE2E8F0)),
                shadowElevation = 0.8.dp
            ) {
                Box(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 11.dp),
                    contentAlignment = Alignment.Center
                ) {
                    if (isRecording) {
                        RecordingBars(color = HeartRed)
                    } else {
                        BouncingDots(color = if (isDark) darkTone(Color(0xFFCBD5E1)) else Color(0xFF64748B))
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
            if (isDark) darkTone(Color(0xFF2A2B30)) else Color(0xFFE5E7EB)
        } else {
            if (isDark) darkTone(Color(0xFF334155)).copy(alpha = 0.5f) else Color(0xFFCBD5E1).copy(alpha = 0.7f)
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
            if (isDark) darkTone(Color(0xFF232428)).copy(alpha = 0.95f) else Color(0xFFF3F4F6)
        } else {
            if (isDark) darkSurface(Color(0xFF1E293B)).copy(alpha = 0.92f) else Color(0xFFF1F5F9).copy(alpha = 0.95f)
        },
        border = BorderStroke(
            0.8.dp,
            if (isPrivateMode) {
                if (isDark) darkTone(Color(0xFF374151)).copy(alpha = 0.6f) else Color(0xFFE5E7EB)
            } else {
                if (isDark) darkTone(Color(0xFF475569)).copy(alpha = 0.4f) else Color(0xFFE2E8F0)
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
                if (isDark) darkTone(Color(0xFF9CA3AF)) else Color(0xFF4B5563)
            } else {
                if (isDark) darkTone(Color(0xFF94A3B8)) else Color(0xFF475569)
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
                if (isDark) darkTone(Color(0xFF1E2024)).copy(alpha = 0.90f) else Color(0xFFF3F4F6)
            } else {
                if (isDark) darkTone(Color(0xFF152033)).copy(alpha = 0.92f) else Color(0xFFEFF6FF)
            },
            border = BorderStroke(
                1.dp,
                if (isPrivateMode) {
                    if (isDark) darkTone(Color(0xFF33353C)) else Color(0xFFE5E7EB)
                } else {
                    if (isDark) darkTone(Color(0xFF2563EB)).copy(alpha = 0.35f) else Color(0xFF93C5FD).copy(alpha = 0.55f)
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
                        if (isDark) darkTone(Color(0xFF9CA3AF)) else Color(0xFF4B5563)
                    } else {
                        if (isDark) darkTone(Color(0xFF60A5FA)) else Color(0xFF2563EB)
                    },
                    modifier = Modifier.size(13.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Strictly 2-Person Private Channel • End-to-End Encrypted",
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (isPrivateMode) {
                        if (isDark) darkTone(Color(0xFFD1D5DB)) else Color(0xFF374151)
                    } else {
                        if (isDark) darkTone(Color(0xFF93C5FD)) else Color(0xFF1E40AF)
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
                color = if (isDark) darkTone(Color(0xFF1E2024)).copy(alpha = 0.85f) else Color(0xFFF3F4F6),
                border = BorderStroke(1.dp, if (isDark) darkTone(Color(0xFF2E3036)) else Color(0xFFE5E7EB)),
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
                            tint = if (isDark) darkTone(Color(0xFF9CA3AF)) else Color(0xFF4B5563),
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Strictly 2-Person Private Channel",
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.5.sp,
                            color = if (isDark) darkTone(Color(0xFFE5E7EB)) else Color(0xFF1F2937)
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Messages, voice notes, and media are strictly between you and $partnerName. End-to-end encrypted.",
                        fontSize = 11.5.sp,
                        color = if (isDark) darkTone(Color(0xFF9CA3AF)) else Color(0xFF6B7280),
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
                    if (isDark) darkTone(Color(0xFF2B3954)).copy(alpha = 0.6f) else Color(0xFFE2E8F0)
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








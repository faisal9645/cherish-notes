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
import androidx.compose.ui.res.painterResource
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
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.draw.shadow
import com.example.ui.components.fluidSheetInertia
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.automirrored.rounded.Reply
import androidx.compose.material.icons.outlined.HourglassTop
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.CheckBox
import androidx.compose.material.icons.rounded.CheckBoxOutlineBlank
import androidx.compose.material.icons.rounded.CheckCircleOutline
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.HourglassTop
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Mood
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarOutline
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material.icons.rounded.WarningAmber
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
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
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
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
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.StateFlow
import androidx.core.content.FileProvider
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import com.example.ui.components.CherishToast
import com.example.ui.components.CherishAlertDialog
import com.example.ui.components.CherishButton
import com.example.ui.components.CherishOutlinedButton
import com.example.ui.components.CherishTonalButton
import com.example.ui.components.pressScaleTarget
import com.example.ui.components.springClickable
import androidx.compose.material.icons.automirrored.rounded.InsertDriveFile
import com.example.ui.components.softShadow

@Stable
class ComposerState(initialText: String = "") {
    var text by mutableStateOf(initialText)
}

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
    // Presence changes often (its beat, typing pace); this screen only shows two things from it, so
    // only those recompose it
    val partnerPresenceState = viewModel.partnerPresence.collectAsState()
    val isPartnerHeartTouchingNow by remember { derivedStateOf { partnerPresenceState.value.isHeartTouching } }
    val isPartnerOnlineNow by remember { derivedStateOf { partnerPresenceState.value.isOnline } }
    val isPartnerTouching = isPartnerHeartTouchingNow || uiState.isPartnerHeartTouching
    // My partner playing one of my voice notes (or a video note) right now: shown on it, its
    // waveform moving as they hear it (timed by listeningClock, which only that message reads), and
    // in the header. Not once it should have ended, nor while they're out of Cherish
    val partnerListeningNow by viewModel.partnerListening.collectAsState()
    val listeningClock = remember { mutableLongStateOf(0L) }
    var listeningOver by remember { mutableStateOf<com.example.data.model.PartnerListening?>(null) }
    LaunchedEffect(partnerListeningNow) {
        val heard = partnerListeningNow ?: return@LaunchedEffect
        while (heard.isCurrent()) {
            listeningClock.longValue = android.os.SystemClock.elapsedRealtime()
            delay(if (heard.playing && !heard.isVideo) 60L else 1_000L)
        }
        listeningOver = heard
    }
    val partnerListening = partnerListeningNow?.takeIf {
        it != listeningOver && isPartnerOnlineNow &&
            uiState.chatExperienceMode != com.example.ui.chat.ChatExperienceMode.PRIVATE
    }
    val hearingStatus = partnerListening?.let { listeningStatus(it.isVideo, it.playing) }
    val listState = rememberLazyListState(
        initialFirstVisibleItemIndex = viewModel.savedScrollIndex,
        initialFirstVisibleItemScrollOffset = viewModel.savedScrollOffset
    )
    // How scrolling feels: the bubbles move exactly with the finger (no lean or spring while it
    // scrolls, which cost frames; see 1817b6f); a fling ticks lightly in the hand as messages race
    // past; pulled on past the newest message, the bubbles bunch up like an accordion and spring
    // back open on release (see AccordionPull)
    val accordion = remember(listState) { AccordionPull(listState) }
    val accordionMaxPx = with(androidx.compose.ui.platform.LocalDensity.current) { 96.dp.toPx() }
    val flingTickPx = with(androidx.compose.ui.platform.LocalDensity.current) { 72.dp.toPx() }
    val scrollView = androidx.compose.ui.platform.LocalView.current
    val chatScrollConnection = remember(accordion, accordionMaxPx, flingTickPx, scrollView) {
        object : NestedScrollConnection {
            private var flingTravel = 0f
            private var lastTickAt = 0L
            private var pullSign = 0f

            /** The list can't go on that way because it's at the newest message. */
            private fun isPastNewest(deltaY: Float): Boolean =
                !listState.canScrollBackward && (listState.canScrollForward || deltaY < 0f)

            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                // Pulling back while bunched up: the accordion opens first
                if (source == NestedScrollSource.UserInput && accordion.value > 0f && pullSign != 0f &&
                    kotlin.math.sign(available.y) == -pullSign
                ) {
                    val taken = accordion.releaseBy(kotlin.math.abs(available.y))
                    return Offset(0f, taken * -pullSign)
                }
                return Offset.Zero
            }

            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                // A fling: a tiny tick every few messages' worth, never faster than about 30 a second
                if (source == NestedScrollSource.SideEffect && consumed.y != 0f) {
                    flingTravel += kotlin.math.abs(consumed.y)
                    if (flingTravel >= flingTickPx) {
                        flingTravel = 0f
                        val now = android.os.SystemClock.uptimeMillis()
                        if (now - lastTickAt >= 32L &&
                            com.example.security.SecurityPreferences.getInstance(scrollView.context).isHapticFeedbackEnabled()
                        ) {
                            lastTickAt = now
                            scrollView.performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK)
                        }
                    }
                }
                // Past the newest message: the bubbles bunch up instead of the list stretching
                if (source == NestedScrollSource.UserInput && available.y != 0f && isPastNewest(available.y)) {
                    pullSign = kotlin.math.sign(available.y)
                    accordion.dragBy(kotlin.math.abs(available.y), accordionMaxPx)
                    return Offset(0f, available.y)
                }
                return Offset.Zero
            }

            override suspend fun onPreFling(available: androidx.compose.ui.unit.Velocity): androidx.compose.ui.unit.Velocity {
                // Let go: it springs back open with a bounce
                flingTravel = 0f
                if (accordion.value > 0f) accordion.settle(scope)
                return androidx.compose.ui.unit.Velocity.Zero
            }

            override suspend fun onPostFling(
                consumed: androidx.compose.ui.unit.Velocity,
                available: androidx.compose.ui.unit.Velocity
            ): androidx.compose.ui.unit.Velocity {
                // A fling that runs into the newest message: a quick bunch-up and rebound with what's left of it
                if (available.y != 0f && isPastNewest(available.y)) {
                    accordion.settle(scope, velocityPxPerSec = kotlin.math.abs(available.y) * 0.12f)
                    return available
                }
                return androidx.compose.ui.unit.Velocity.Zero
            }
        }
    }

    val composerState = remember { ComposerState(viewModel.draftMessageText) }

    DisposableEffect(Unit) {
        onDispose {
            viewModel.draftMessageText = composerState.text
            viewModel.savedScrollIndex = listState.firstVisibleItemIndex
            viewModel.savedScrollOffset = listState.firstVisibleItemScrollOffset
            // The message there too: new ones arriving meanwhile shift the index, not the message
            viewModel.savedScrollKey = listState.layoutInfo.visibleItemsInfo.firstOrNull()?.key as? String
        }
    }
    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f

    var textToSchedule by remember { mutableStateOf("") }
    var editingMessage by remember { mutableStateOf<Message?>(null) }
    var editDialogText by remember { mutableStateOf("") }
    var showDeleteConfirmDialog by remember { mutableStateOf<Message?>(null) }
    var isSelectionMode by remember { mutableStateOf(false) }
    var selectedItemIds by remember { mutableStateOf(setOf<String>()) }
    var showMultiDeleteConfirmDialog by remember { mutableStateOf(false) }
    var showClearChatDialog by remember { mutableStateOf(false) }
    var showFullProfilePicViewer by remember { mutableStateOf(false) }
    var showChatMenu by remember { mutableStateOf(false) }
    val startVoiceCall: () -> Unit = {
        com.example.call.CallActivity.startOutgoing(context)
    }
    val callState by com.example.call.CallManager.state.collectAsState()
    var showVideoNoteRecorder by remember { mutableStateOf(false) }
    var showHeartbeatTouch by remember { mutableStateOf(false) }
    var userDismissedHeartbeatTouch by remember { mutableStateOf(false) }
    // Kisses (see KissTouch): the ones blooming on the chat now, and the kiss pad
    val kissBlooms = remember { mutableStateListOf<KissBloom>() }
    var showKissPad by remember { mutableStateOf(false) }
    // Where the message list and the whole chat are, to place a kiss on the chat
    val kissListCoords = remember { arrayOfNulls<androidx.compose.ui.layout.LayoutCoordinates>(1) }
    val kissRootCoords = remember { arrayOfNulls<androidx.compose.ui.layout.LayoutCoordinates>(1) }
    var showMoodPicker by remember { mutableStateOf(false) }
    var showScheduleSheet by remember { mutableStateOf(false) }
    var showScheduledList by remember { mutableStateOf(false) }
    // The calendar in chat search
    var showJumpToDate by remember { mutableStateOf(false) }
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
    val composerFocusRequester = remember { FocusRequester() }
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

    // Automatically open Heartbeat Touch dialog when partner starts heartbeat touching: never over
    // Notes, and never over a kiss (each keeps its own screen and its own feel)
    LaunchedEffect(Unit) {
        viewModel.partnerPresence
            .map { it.isHeartTouching }
            .distinctUntilChanged()
            .collect { touching ->
                if (!touching) {
                    userDismissedHeartbeatTouch = false
                }
                val behindNotes = com.example.CherishApplication.instance.securityPreferences.isDisguiseActive.value
                if (touching && !showHeartbeatTouch && !showKissPad && !behindNotes && !userDismissedHeartbeatTouch) {
                    showHeartbeatTouch = true
                }
            }
    }

    val currentPlayingIdState = viewModel.voicePlayerHelper.currentlyPlayingId.collectAsState()
    val currentPlayingId by currentPlayingIdState
    val isAudioPlaying by viewModel.voicePlayerHelper.isPlaying.collectAsState()
    // Read only inside the playing bubble, so playback progress doesn't recompose the whole screen
    val audioProgressState = viewModel.voicePlayerHelper.playbackProgress.collectAsState()
    val isRecordingVoice by viewModel.voiceRecorderHelper.isRecording.collectAsState()
    // (The recording's seconds and levels are read by the message box alone, see ChatBottomComposerBar:
    // they change many times a second)

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

    val documentPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        val prefs = com.example.security.SecurityPreferences.getInstance(context)
        prefs.isExternalPickerActive = false
        prefs.ignoreNextPause = false
        if (uri != null) {
            val fileName = com.example.util.DocumentHelper.getDisplayName(context, uri)
            viewModel.sendMediaFile(uri, MessageType.DOCUMENT, fileName)
        }
    }
    val micPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            CherishToast.show("Microphone enabled! Hold mic to record")
        } else {
            CherishToast.show("Microphone permission is required for voice notes", long = true)
        }
    }

    // Real-time camera snap launcher
    var tempCameraUri by remember { mutableStateOf<Uri?>(null) }
    var showAttachmentSheet by remember { mutableStateOf(false) }
    var showWatchPartySetup by remember { mutableStateOf(false) }

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
            CherishToast.show("Could not open camera: ${e.message}")
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
            CherishToast.show("Camera permission needed to snap photos")
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
            CherishToast.show("Camera and microphone are needed for video notes", long = true)
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

    // Holding the camera button records a video with the phone's own camera, sent full size but
    // compressed (720p, like a picked video, see sendMediaFile)
    var tempCameraVideoUri by remember { mutableStateOf<Uri?>(null) }
    var tempCameraVideoFile by remember { mutableStateOf<File?>(null) }
    val cameraVideoLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CaptureVideo()
    ) { success: Boolean ->
        val prefs = com.example.security.SecurityPreferences.getInstance(context)
        prefs.isExternalPickerActive = false
        prefs.ignoreNextPause = false
        val uri = tempCameraVideoUri
        val clip = tempCameraVideoFile
        when {
            !success || uri == null -> clip?.delete()
            // A camera app that kept the video somewhere else leaves this file empty
            clip != null && clip.length() == 0L -> {
                clip.delete()
                CherishToast.show("The camera didn't hand the video back. Try sending it from the gallery")
            }
            else -> viewModel.sendMediaFile(
                uri, MessageType.VIDEO,
                mediaName = "video_${System.currentTimeMillis()}.mp4",
                compactVideo = true
            )
        }
    }

    fun launchCameraVideo() {
        val prefs = com.example.security.SecurityPreferences.getInstance(context)
        try {
            val clipsDir = File(context.cacheDir, "camera_snaps").apply { mkdirs() }
            val clipFile = File(clipsDir, "clip_${System.currentTimeMillis()}.mp4").apply { createNewFile() }
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", clipFile)
            tempCameraVideoUri = uri
            tempCameraVideoFile = clipFile
            prefs.isExternalPickerActive = true
            prefs.ignoreNextPause = true
            cameraVideoLauncher.launch(uri)
        } catch (e: Exception) {
            prefs.isExternalPickerActive = false
            prefs.ignoreNextPause = false
            CherishToast.show("Could not open camera: ${e.message}")
        }
    }

    val cameraVideoPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        val prefs = com.example.security.SecurityPreferences.getInstance(context)
        prefs.isExternalPickerActive = false
        prefs.ignoreNextPause = false
        if (isGranted) {
            launchCameraVideo()
        } else {
            CherishToast.show("Camera permission needed to record videos")
        }
    }

    val triggerCameraVideo: () -> Unit = {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            launchCameraVideo()
        } else {
            val prefs = com.example.security.SecurityPreferences.getInstance(context)
            prefs.isExternalPickerActive = true
            prefs.ignoreNextPause = true
            cameraVideoPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    // Filter messages for search query and starred filter - all messages preserved and displayed based on pagination scroll.
    val displayedMessages by viewModel.displayedMessages.collectAsState()
    val reversedChatRowItems by viewModel.reversedChatRowItems.collectAsState()

    val density = LocalDensity.current
    val isImeVisible = WindowInsets.isImeVisible
    val imeBottom = WindowInsets.ime.getBottom(density)

    // Keyboard open / close scrolling:
    // When keyboard opens (user taps text composer), smoothly anchor the chat list to the newest message.
    LaunchedEffect(isImeVisible) {
        if (isImeVisible && reversedChatRowItems.isNotEmpty() && listState.firstVisibleItemIndex <= 1 &&
            viewModel.uiState.value.targetScrollMessageId == null
        ) {
            listState.animateScrollToItem(0, scrollOffset = 0)
        }
    }

    // Follow the conversation when a new message lands:
    // Smoothly scroll to the newest message when sent by user or when already reading latest messages.
    val latestRowItemId = reversedChatRowItems.firstOrNull()?.message?.id
    val latestRawMessageId = uiState.messages.lastOrNull { !it.isDeleted }?.id
    val lastScrolledMessageId = remember { mutableStateOf<String?>(null) }
    LaunchedEffect(latestRowItemId, latestRawMessageId) {
        val currentId = latestRowItemId ?: latestRawMessageId ?: return@LaunchedEffect
        val previousId = lastScrolledMessageId.value
        lastScrolledMessageId.value = currentId
        // A "Show in chat" jump is on its way: it decides where the chat is
        if (viewModel.uiState.value.targetScrollMessageId != null) return@LaunchedEffect
        if (previousId == null) {
            val savedKey = viewModel.savedScrollKey
            val savedIndex = if (viewModel.savedScrollIndex > 0 && savedKey != null) {
                reversedChatRowItems.indexOfFirst { it.message.id == savedKey }
            } else -1
            when {
                savedIndex > 0 -> listState.scrollToItem(savedIndex, viewModel.savedScrollOffset)
                viewModel.savedScrollIndex == 0 -> listState.scrollToItem(0, scrollOffset = 0)
            }
            return@LaunchedEffect
        }
        if (currentId == previousId) return@LaunchedEffect

        val state = viewModel.uiState.value
        val sentByMe = state.currentUser?.id != null &&
            state.messages.lastOrNull { !it.isDeleted }?.senderId == state.currentUser.id
        if (!sentByMe && listState.firstVisibleItemIndex > 1) return@LaunchedEffect
        listState.animateScrollToItem(0, scrollOffset = 0)
    }

    // When partner starts typing/recording, gently keep latest message in view if already at bottom
    LaunchedEffect(Unit) {
        viewModel.partnerPresence
            .map { it.isTyping to it.isRecordingAudio }
            .distinctUntilChanged()
            .collect { (typing, recording) ->
                if ((typing || recording) && listState.firstVisibleItemIndex == 0 && reversedChatRowItems.isNotEmpty()) {
                    listState.animateScrollToItem(0, scrollOffset = 0)
                }
            }
    }

    // Save draft text without interrupting scrolling or snapping scroll position on every character.
    // Read in a snapshotFlow, not as the effect's key: a key read here recomposed this whole screen
    // (and every visible row's call) on each key typed
    LaunchedEffect(composerState) {
        snapshotFlow { composerState.text }.collect { viewModel.draftMessageText = it }
    }

    // When bottom overlays appear (upload status, reply preview), keep the newest message clear of them
    LaunchedEffect(bottomOverlayHeightPx) {
        if (bottomOverlayHeightPx > 0 && listState.firstVisibleItemIndex == 0 && reversedChatRowItems.isNotEmpty()) {
            listState.animateScrollToItem(0, scrollOffset = 0)
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
            val isHug = incoming.bigEmojis()?.let { isHugScene(it) } == true
            if (isHug) {
                // A hug is felt only inside Cherish: behind Notes (or with the app away) it waits, and
                // plays with its squeezes when Cherish is open again (see BigEmoji)
                val inCherish = com.example.CherishApplication.isAppInForeground &&
                    !com.example.CherishApplication.instance.securityPreferences.isDisguiseActive()
                if (inCherish) {
                    val lvl = incoming.hugSqueeze() ?: PLAIN_HUG_LEVEL
                    val peaks = hugSqueezePeaks(lvl).map { (it * hugSceneMs(lvl)).toLong() }
                    context.vibrateHugSqueezes(lvl, peaks)
                }
            } else {
                view.chatHaptic(ChatHaptic.Receive)
            }
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

    // Chat from before today stays hidden until "Recover all" (or "Show in chat") turns previous chats on.
    // Filtered (today only / Recover All, starred, search) in the background by the view model

    var highlightedMessageId by remember { mutableStateOf<String?>(null) }
    // Messages whose reply quote was tapped, to come back to (the last one first), like WhatsApp and
    // Telegram: the down arrow returns there before it goes to the newest messages
    val replyReturns = remember { mutableStateListOf<String>() }

    // What a message row can ask of the screen. Created once, reading the latest state when
    // called, so rows get the same instance every time and can be skipped on recomposition.
    val latestDisplayedMessages = rememberUpdatedState(displayedMessages)
    val latestKeyboardController = rememberUpdatedState(keyboardController)
    val rowActions = remember(viewModel) {
        ChatRowActions(
            playAudio = { message ->
                val audioUrl = message.mediaUrl ?: message.recordingPath
                audioUrl?.let { url -> viewModel.playAudio(message.id, url) }
            },
            seekAudio = { message, progress ->
                val audioUrl = message.mediaUrl ?: message.recordingPath
                if (viewModel.voicePlayerHelper.currentlyPlayingId.value == message.id) {
                    viewModel.seekAudio(progress)
                } else {
                    audioUrl?.let { url ->
                        viewModel.playAudio(message.id, url)
                        viewModel.seekAudio(progress)
                    }
                }
            },
            openMedia = { message, url, siblings ->
                (context.applicationContext as? com.example.CherishApplication)?.securityPreferences?.isMediaViewerActive = true
                val isVideoNote = message.isCircularVideoNote() || message.isVideoNote
                val isVideo = message.getTypedType() == MessageType.VIDEO || isVideoNote
                val ownPhotos = siblings ?: message.getAllMediaUrls()
                // A photo opens among all the chat's photos (an album's in their order), so the
                // viewer counts them all ("88 of 100") and swipes on through them
                val chatPhotos = if (isVideo) emptyList() else latestDisplayedMessages.value
                    .filter { it.getTypedType() == MessageType.IMAGE }
                    .flatMap { it.getAllMediaUrls() }
                    .distinct()
                val mediaList = when {
                    isVideo -> listOf(url)
                    url in chatPhotos -> chatPhotos
                    else -> ownPhotos.ifEmpty { listOf(url) }
                }
                viewModel.openFullScreenMedia(url, if (isVideo) MessageType.VIDEO else MessageType.IMAGE, mediaList, isVideoNote = isVideoNote)
            },
            reply = { message -> viewModel.setReplyingTo(message) },
            longPress = { message, bounds ->
                // Its menu, not a kiss on the chat (see kissOnHold)
                ChatLongPress.onMessageAt = android.os.SystemClock.uptimeMillis()
                latestKeyboardController.value?.hide()
                focusedBounds = bounds
                focusedMessage = message
            },
            react = { message, emoji -> viewModel.toggleReaction(message.id, emoji) },
            openTheaterVideo = { videoId -> viewModel.openTheaterVideo(videoId) },
            toggleVoiceSpeed = { viewModel.toggleVoiceSpeed() },
            jumpToReply = { fromMessageId, replyId ->
                if (!replyId.isNullOrBlank()) {
                    // The list is reversed (newest at index 0)
                    val shown = latestDisplayedMessages.value
                    val index = shown.indexOfFirst { it.id == replyId }
                    if (index >= 0) {
                        replyReturns.remove(fromMessageId)
                        replyReturns.add(fromMessageId)
                        if (replyReturns.size > 20) replyReturns.removeAt(0)
                        scope.launch {
                            highlightedMessageId = replyId
                            listState.revealMessage(shown.lastIndex - index)
                            try {
                                val vib = (context.getSystemService(android.content.Context.VIBRATOR_SERVICE) as? android.os.Vibrator)
                                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                                    vib?.vibrate(android.os.VibrationEffect.createOneShot(35, android.os.VibrationEffect.DEFAULT_AMPLITUDE))
                                } else {
                                    @Suppress("DEPRECATION")
                                    vib?.vibrate(35)
                                }
                            } catch (_: Exception) {}
                            kotlinx.coroutines.delay(800)
                            if (highlightedMessageId == replyId) {
                                highlightedMessageId = null
                            }
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
    // the anniversary / monthly date / 100th day (once that day), and from 11:30 PM the goodnight card
    // (once a night, unless a goodnight was already sent tonight)
    var showGoodMorning by remember { mutableStateOf(false) }
    var celebration by remember { mutableStateOf<String?>(null) }
    var showGoodnightCard by remember { mutableStateOf(false) }
    var showGoodnightStars by remember { mutableStateOf(false) }
    var skyClock by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(60_000L)
            skyClock = System.currentTimeMillis()
        }
    }
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
    val sentGoodMorningToday by rememberUpdatedState(
        uiState.messages.any {
            it.senderId == currentUserId &&
            it.timestamp >= todayStartMillis() &&
            (it.effect == EFFECT_GOODMORNING || it.text.contains("good morning", ignoreCase = true)) &&
            !it.isDeleted
        }
    )
    val sentGoodnightTonight by rememberUpdatedState(
        uiState.messages.any {
            it.effect == EFFECT_GOODNIGHT && it.senderId == currentUserId && it.timestamp >= tonightStartMillis()
        }
    )
    LaunchedEffect(isChatInView) {
        if (!isChatInView) return@LaunchedEffect
        try {
            delay(450)
            val alreadySentGm = sentGoodMorningToday
            if (isGoodMorningDue(context) && !alreadySentGm) {
                markGoodMorningShown(context)
                showGoodMorning = true
                delay(8_500)
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
        } finally {
            showGoodMorning = false
            celebration = null
            monthRecapInvite = null
        }
    }

    // From 11:00 PM (until 5 AM), the goodnight card prompts once a night unless goodnight was already sent
    LaunchedEffect(isChatInView, skyClock) {
        if (!isChatInView) return@LaunchedEffect
        if (isGoodnightDue(context) && !sentGoodnightTonight) {
            while (showGoodMorning || celebration != null || showGoodnightStars) delay(250)
            markGoodnightShown(context)
            showGoodnightCard = true
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

    // Once we've both said goodnight, a starry night behind the messages until morning (on both
    // phones, from the same messages); the first message of the morning brings a small sunrise
    val bothSaidGoodnightTonight = remember(uiState.messages, currentUserId, skyClock) {
        bothSaidGoodnight(uiState.messages, currentUserId, skyClock)
    }
    val morningFirstMessage = remember(uiState.messages, skyClock) { firstMorningMessage(uiState.messages, skyClock) }
    var showSunrise by remember { mutableStateOf(false) }
    LaunchedEffect(morningFirstMessage?.id, isChatInView) {
        if (morningFirstMessage == null || !isChatInView || isSunriseShown(context)) return@LaunchedEffect
        delay(500)
        markSunriseShown(context)
        showSunrise = true
    }

    // My partner kissed the chat: it blooms here at the same spot (once; one sent while the chat was
    // closed still blooms when it's opened a little later). Never over Private mode or the curtain
    val partnerKissStamp by viewModel.partnerKiss.collectAsState()
    val kissesAllowed = uiState.chatExperienceMode != com.example.ui.chat.ChatExperienceMode.PRIVATE &&
        !uiState.isStealthCurtainActive
    val isMyMale = remember(uiState) { com.example.security.SecurityPreferences.getInstance(context).getUserGender() == com.example.security.SecurityPreferences.GENDER_MALE }
    val isPartnerMale = remember(uiState) { com.example.security.SecurityPreferences.getInstance(context).getPartnerGender() == com.example.security.SecurityPreferences.GENDER_MALE }
    LaunchedEffect(partnerKissStamp?.id, isChatInView, kissesAllowed) {
        val kiss = partnerKissStamp ?: return@LaunchedEffect
        if (!isChatInView || !kissesAllowed || isKissSeen(context, kiss.id)) return@LaunchedEffect
        if (System.currentTimeMillis() - kiss.atMs > KISS_WAITS_MS) return@LaunchedEffect
        markKissSeen(context, kiss.id)
        delay(250)
        kissBlooms += KissBloom(kiss.id, kiss.x, kiss.y, fromMe = false, isMale = isPartnerMale)
        context.vibrateKiss()
    }
    // Partner's live kiss (lips held on the chat or on their kiss pad)
    val partnerKissTouch by viewModel.partnerKissTouch.collectAsState()
    // They started kissing: the kiss pad opens here too, to kiss them back face to face. Only with
    // the chat in front of me (never over Notes, Private mode or the curtain), never over a heartbeat
    // touch, and once per kiss (closed, it stays closed until their next one)
    val partnerStartedKissing = partnerKissTouch != null
    LaunchedEffect(partnerStartedKissing, isChatInView) {
        if (partnerStartedKissing && isChatInView && kissesAllowed && !showHeartbeatTouch) showKissPad = true
    }
    var currentGlassTouch by remember { mutableStateOf<Offset?>(null) }
    // Where my held kiss is, sent as it moves (not on every finger move, see KissSpotSender)
    val kissSpot = remember { KissSpotSender { x, y -> viewModel.setKissTouch(x, y) } }
    // My lips (or thumb) held on the chat's background: a kiss there, blooming on theirs too
    val onKissHold = remember {
        { local: Offset ->
            val list = kissListCoords[0]
            val root = kissRootCoords[0]
            if (list != null && root != null && list.isAttached && root.isAttached && root.size.width > 0 && root.size.height > 0) {
                val at = root.localPositionOf(list, local)
                val x = (at.x / root.size.width).coerceIn(0f, 1f)
                val y = (at.y / root.size.height).coerceIn(0f, 1f)
                kissBlooms += KissBloom(java.util.UUID.randomUUID().toString(), x, y, fromMe = true, isMale = isMyMale)
                context.vibrateKiss()
                viewModel.sendKiss(x, y)
            }
        }
    }

    // A "thinking of you" heartbeat arrived while the chat is open: a heart beats with it
    var heartbeatPulseKey by remember { mutableStateOf(0L) }
    var partnerFeltKey by remember { mutableStateOf(0L) }
    LaunchedEffect(isChatInView) {
        if (!isChatInView) return@LaunchedEffect
        com.example.notifications.ThinkingOfYou.received.collect { heartbeatPulseKey = it }
    }
    LaunchedEffect(isChatInView) {
        if (!isChatInView) return@LaunchedEffect
        com.example.notifications.ThinkingOfYou.partnerFelt.collect { partnerFeltKey = it.timestamp }
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
        showKissPad = false
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
    BackHandler(enabled = !isDisguiseActive) {
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
            userDismissedHeartbeatTouch = true
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
            onNavigateBack()
        }
    }

    // Scroll to exact target message when navigating from gallery ("Show in chat")
    // Found in the rows the list actually shows (worked out a moment after the messages), so the
    // index is the list's own
    LaunchedEffect(uiState.targetScrollMessageId, reversedChatRowItems.size) {
        val targetId = uiState.targetScrollMessageId
        if (targetId != null && reversedChatRowItems.isNotEmpty()) {
            val targetIndex = reversedChatRowItems.indexOfFirst { it.message.id == targetId }
            if (targetIndex >= 0) {
                // Coming back from the gallery or calendar search, the list lays out first
                kotlinx.coroutines.withTimeoutOrNull(1_500) {
                    snapshotFlow { listState.layoutInfo.totalItemsCount }.first { it > targetIndex }
                }
                delay(100)
                highlightedMessageId = targetId
                try {
                    listState.scrollToItem(targetIndex)
                } catch (_: Exception) {
                    listState.revealMessage(targetIndex)
                }
                kotlinx.coroutines.delay(1200)
                highlightedMessageId = null
                viewModel.clearTargetScrollMessageId()
            }
        }
    }
    // Never found (say, older than the chat shows): given up once older chat has stopped coming in
    // for a while, so the chat follows new messages again
    LaunchedEffect(uiState.targetScrollMessageId, reversedChatRowItems.size) {
        val targetId = uiState.targetScrollMessageId ?: return@LaunchedEffect
        delay(12_000)
        if (viewModel.uiState.value.targetScrollMessageId == targetId) viewModel.clearTargetScrollMessageId()
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
    // Settings > Appearance: the messages' text size and font, here and in the message box
    val chatTextStyle = remember(uiState.chatTextSize, uiState.chatFont) {
        ChatTextStyle(uiState.chatTextSize, ChatFont.of(uiState.chatFont))
    }
    // Which of us is him and which her (told the same way as on the couple card), for the 🫂 hug
    val hugCouple = remember(myUser?.id, myUser?.displayName, myUser?.email, myUser?.partnerId, myUser?.partnerEmail, partner?.id, partner?.displayName, partner?.email, partnerName) {
        val me = myUser
        val iAmHer = me != null && com.example.ui.home.herUserId(
            com.example.ui.home.LovePerson(
                id = me.id, name = me.displayName, photoUrl = null, birthday = null,
                isOnline = false, lastSeen = 0L, email = me.email
            ),
            com.example.ui.home.LovePerson(
                id = partner?.id ?: me.partnerId.orEmpty(), name = partner?.displayName.orEmpty(), photoUrl = null,
                birthday = null, isOnline = false, lastSeen = 0L, email = partner?.email ?: me.partnerEmail
            )
        ) == me.id
        HugCouple(iAmHim = !iAmHer, partnerName = partnerName.takeUnless { it == "My Partner" }.orEmpty())
    }
    // From 11 PM: the same colours, but slower, softer motion and quieter sounds (see NightCalm)
    val isNight = rememberIsNight()
    CompositionLocalProvider(LocalChatTextStyle provides chatTextStyle, LocalHugCouple provides hugCouple, LocalChatNight provides isNight, LocalAccordionPull provides accordion) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .onSizeChanged { chatScreenSize = it }
            .onGloballyPositioned { kissRootCoords[0] = it }
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
        NightSky(
            starry = bothSaidGoodnightTonight && !showSunrise,
            sunrise = showSunrise,
            onSunriseDone = { showSunrise = false },
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
                            // Swiping right glides to Love & Us, left to Settings (private chat too)
                            if (accX > 70f) {
                                change.consume()
                                onNavigateToHome()
                                break
                            }
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
                val isPrivate = uiState.chatExperienceMode == com.example.ui.chat.ChatExperienceMode.PRIVATE
                val topBarBackgroundModifier = if (isPrivate) {
                    val topBg = if (isDark) Color.Black else Color.White
                    Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.verticalGradient(
                                colorStops = arrayOf(
                                    0.0f to topBg,
                                    0.8f to topBg,
                                    1.0f to topBg.copy(alpha = 0.92f)
                                )
                            )
                        )
                } else if (showWallpaper) {
                    Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    (if (isDark) Color(0xD9050A18) else Color(0xCCF8FAFC)),
                                    (if (isDark) Color(0x66050A18) else Color(0x66F8FAFC)),
                                    Color.Transparent
                                )
                            )
                        )
                } else {
                    Modifier.fillMaxWidth()
                }
                val screenWidthDp = androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp
                val fontScale = androidx.compose.ui.platform.LocalDensity.current.fontScale

                // Responsive TopBar metrics: dynamically optimizes width & touch targets so
                // compact 6.2" screens (e.g., Galaxy S24 at 360-384dp) showcase the full last seen
                // timestamp and partner local time without ellipsis or clipping, while scaling gracefully
                // on medium and larger 6.7"+ devices.
                val isCompactTopBar = screenWidthDp < 370 || (screenWidthDp < 390 && fontScale > 1.05f)
                val isMediumTopBar = screenWidthDp in 370..405 && fontScale <= 1.05f

                val topBarActionBtnSize = when {
                    isCompactTopBar -> 29.5.dp
                    isMediumTopBar -> 32.dp
                    else -> 36.dp
                }
                val topBarActionIconSize = when {
                    isCompactTopBar -> 19.5.dp
                    isMediumTopBar -> 21.dp
                    else -> 23.dp
                }
                val topBarActionSpacing = when {
                    isCompactTopBar -> 0.dp
                    isMediumTopBar -> 0.5.dp
                    else -> 2.dp
                }
                val topBarNavBtnSize = when {
                    isCompactTopBar -> 31.dp
                    isMediumTopBar -> 34.dp
                    else -> 38.dp
                }
                val topBarNavIconSize = when {
                    isCompactTopBar -> 20.dp
                    isMediumTopBar -> 22.dp
                    else -> 24.dp
                }

                Column(modifier = topBarBackgroundModifier) {
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
                        if (isPrivate) {
                            // Like the ChatGPT app: no title, only the round buttons
                        } else {
                            ChatPartnerHeaderContent(
                                presenceFlow = viewModel.partnerPresence,
                                hearingStatus = hearingStatus,
                                fallbackPartnerName = partnerName,
                                myTimeZone = uiState.currentUser?.timeZone,
                                isDark = isDark,
                                onAvatarClick = {
                                    val app = context.applicationContext as? com.example.CherishApplication
                                    app?.securityPreferences?.isMediaViewerActive = true
                                    showFullProfilePicViewer = true
                                },
                                onAvatarLongPress = {
                                    if (kissesAllowed) {
                                        view.chatHaptic(ChatHaptic.LongPress)
                                        showKissPad = true
                                    }
                                },
                                onMoodClick = { showMoodPicker = true }
                            )
                        }
                    }
                },
                navigationIcon = {
                    if (uiState.chatExperienceMode == com.example.ui.chat.ChatExperienceMode.PRIVATE && !uiState.isSearching) {
                        // ChatGPT's clean menu button: opens Settings, as sliding right does
                        IconButton(
                            onClick = onNavigateToProfile,
                            modifier = Modifier
                                .padding(start = 6.dp)
                                .size(42.dp)
                                .testTag("chat_back_button")
                        ) {
                            TwoLineMenuIcon(tint = if (isDark) Gray400 else Color(0xFF71717A))
                        }
                    } else {
                        CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides androidx.compose.ui.unit.Dp.Unspecified) {
                            IconButton(
                                onClick = {
                                    if (uiState.isSearching) {
                                        viewModel.setSearching(false)
                                    } else {
                                        onNavigateToHome()
                                    }
                                },
                                modifier = Modifier
                                    .padding(start = 0.dp)
                                    .size(topBarNavBtnSize)
                                    .testTag("chat_back_button")
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                                    contentDescription = "Back",
                                    tint = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.size(topBarNavIconSize)
                                )
                            }
                        }
                    }
                },
                actions = {
                    if (uiState.isSearching) {
                        // Jump to a day: the chat's first message that day
                        IconButton(
                            onClick = { showJumpToDate = true },
                            modifier = Modifier
                                .size(40.dp)
                                .testTag("chat_search_calendar")
                        ) {
                            Icon(Icons.Rounded.CalendarMonth, contentDescription = "Jump to date", modifier = Modifier.size(22.dp))
                        }
                        IconButton(
                            onClick = { viewModel.setSearching(false) },
                            modifier = Modifier.size(40.dp)
                        ) {
                            Icon(Icons.Rounded.Close, contentDescription = "Close search", modifier = Modifier.size(24.dp))
                        }
                    } else {
                        val isPrivateChat = uiState.chatExperienceMode == com.example.ui.chat.ChatExperienceMode.PRIVATE
                        val iconTint = if (isPrivateChat) (if (isDark) Gray400 else Color(0xFF71717A)) else MaterialTheme.colorScheme.onSurface
                        if (isPrivateChat) {
                            // ChatGPT style: clean new chat and overflow icons without distracting heavy borders
                            Box(modifier = Modifier.padding(end = 6.dp)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    IconButton(
                                        onClick = { showClearChatDialog = true },
                                        modifier = Modifier
                                            .size(42.dp)
                                            .testTag("chat_new_chat_button")
                                    ) {
                                        NewChatIcon(tint = iconTint)
                                    }
                                    IconButton(
                                        onClick = { showChatMenu = true },
                                        modifier = Modifier.size(44.dp)
                                    ) {
                                        Icon(Icons.Rounded.MoreVert, contentDescription = "More options", tint = iconTint, modifier = Modifier.size(26.dp))
                                    }
                                }
                                DropdownMenu(
                                    expanded = showChatMenu,
                                    onDismissRequest = { showChatMenu = false }
                                ) {
                                    DropdownMenuItem(
                                        text = { Text("Voice call") },
                                        onClick = {
                                            showChatMenu = false
                                            startVoiceCall()
                                        },
                                        leadingIcon = { Icon(Icons.Rounded.Call, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface) },
                                        modifier = Modifier.testTag("chat_menu_voice_call")
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Search") },
                                        onClick = {
                                            showChatMenu = false
                                            viewModel.setSearching(true)
                                        },
                                        leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface) }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Search by Date") },
                                        onClick = {
                                            showChatMenu = false
                                            showJumpToDate = true
                                        },
                                        leadingIcon = { Icon(Icons.Rounded.CalendarMonth, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface) }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Starred messages") },
                                        onClick = {
                                            showChatMenu = false
                                            viewModel.toggleFilterStarred()
                                        },
                                        leadingIcon = { Icon(Icons.Rounded.Star, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface) }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Our Words") },
                                        onClick = {
                                            showChatMenu = false
                                            onNavigateToOurWords()
                                        },
                                        leadingIcon = { Icon(Icons.AutoMirrored.Rounded.MenuBook, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface) }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Normal mode") },
                                        onClick = {
                                            showChatMenu = false
                                            viewModel.toggleChatExperienceMode()
                                        },
                                        leadingIcon = { Icon(Icons.Rounded.FavoriteBorder, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface) }
                                    )
                                }
                            }
                    } else {
                        CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides androidx.compose.ui.unit.Dp.Unspecified) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(topBarActionSpacing),
                                modifier = Modifier.padding(end = if (isCompactTopBar) 0.dp else 1.dp)
                            ) {
                                IconButton(
                                    onClick = startVoiceCall,
                                    modifier = Modifier
                                        .size(topBarActionBtnSize)
                                        .testTag("chat_call_button")
                                ) {
                                    Icon(Icons.Rounded.Call, contentDescription = "Voice call", tint = iconTint, modifier = Modifier.size(topBarActionIconSize))
                                }
                                IconButton(
                                    onClick = { viewModel.setSearching(true) },
                                    modifier = Modifier
                                        .size(topBarActionBtnSize)
                                        .testTag("chat_search_button")
                                ) {
                                    Icon(Icons.Rounded.Search, contentDescription = "Search messages", tint = iconTint, modifier = Modifier.size(topBarActionIconSize))
                                }
                                IconButton(
                                    onClick = onNavigateToGallery,
                                    modifier = Modifier
                                        .size(topBarActionBtnSize)
                                        .testTag("chat_gallery_button")
                                ) {
                                    Icon(Icons.Rounded.PhotoLibrary, contentDescription = "Couple Media Gallery", tint = iconTint, modifier = Modifier.size(topBarActionIconSize))
                                }
                                IconButton(
                                    onClick = { viewModel.openCheckAfterSheet() },
                                    modifier = Modifier
                                        .size(topBarActionBtnSize)
                                        .testTag("chat_check_after_button")
                                ) {
                                    Icon(
                                        imageVector = if (partnerHasCheckAfter || iHaveCheckAfter) Icons.Rounded.HourglassTop else Icons.Outlined.HourglassTop,
                                        contentDescription = "Check After Timer",
                                        tint = if (partnerHasCheckAfter || iHaveCheckAfter) MaterialTheme.colorScheme.primary else iconTint,
                                        modifier = Modifier.size(topBarActionIconSize)
                                    )
                                }

                                Box {
                                    IconButton(
                                        onClick = { showChatMenu = true },
                                        modifier = Modifier.size(topBarActionBtnSize)
                                    ) {
                                        Icon(Icons.Rounded.MoreVert, contentDescription = "More options", tint = iconTint, modifier = Modifier.size(topBarActionIconSize + 1.dp))
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
                                        leadingIcon = { Icon(Icons.Rounded.Mood, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface) }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Search by Date") },
                                        onClick = {
                                            showChatMenu = false
                                            showJumpToDate = true
                                        },
                                        leadingIcon = { Icon(Icons.Rounded.CalendarMonth, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface) }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Starred Messages") },
                                        onClick = {
                                            showChatMenu = false
                                            viewModel.toggleFilterStarred()
                                        },
                                        leadingIcon = { Icon(Icons.Rounded.Star, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface) }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Our Words") },
                                        onClick = {
                                            showChatMenu = false
                                            onNavigateToOurWords()
                                        },
                                        leadingIcon = { Icon(Icons.AutoMirrored.Rounded.MenuBook, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface) },
                                        modifier = Modifier.testTag("chat_menu_our_words")
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Clear Chat") },
                                        onClick = {
                                            showChatMenu = false
                                            showClearChatDialog = true
                                        },
                                        leadingIcon = { Icon(Icons.Rounded.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface) }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Logout") },
                                        onClick = {
                                            showChatMenu = false
                                            viewModel.logout()
                                            onLoggedOut()
                                        },
                                        leadingIcon = { Icon(Icons.AutoMirrored.Rounded.Logout, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface) }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = if (showWallpaper) {
                        Color.Transparent
                    } else if (isDark) {
                        Color.Black
                    } else if (uiState.chatExperienceMode == com.example.ui.chat.ChatExperienceMode.PRIVATE) {
                        Color.Transparent
                    } else {
                        MaterialTheme.colorScheme.surface
                    }
                ),
                windowInsets = WindowInsets.statusBars
            )
            // No line under the bar in private chat or when wallpaper is shown
            if (uiState.chatExperienceMode != com.example.ui.chat.ChatExperienceMode.PRIVATE && !showWallpaper) {
                HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
            }
            if (com.example.call.CallManager.hasActiveCall() && callState.phase != com.example.call.CallPhase.INCOMING_RINGING) {
                Surface(
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { com.example.call.CallActivity.startOutgoing(context) }
                        .testTag("chat_return_to_call")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Rounded.Call, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Call in progress · tap to return", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                    }
                }
            }
            }
            }
            }
        },
        bottomBar = {
            val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f || com.example.ui.theme.isAppInDark()
            val isPrivate = uiState.chatExperienceMode == com.example.ui.chat.ChatExperienceMode.PRIVATE
            val barBg = if (showWallpaper) {
                if (isDark) Color(0x99050A18) else Color(0x99FFFFFF)
            } else if (isDark) {
                Color.Black
            } else {
                Color.White
            }
            // In private mode (ChatGPT style): flat background with no harsh shadow or border
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(if (!isPrivate && !isDark) Modifier.shadow(8.dp, RectangleShape, clip = false) else Modifier)
                    .background(barBg)
                    // Touches on the tray stay in the tray
                    .pointerInput(Unit) {}
            ) {
                CompositionLocalProvider(LocalContentColor provides contentColorFor(barBg)) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(barBg)
                        .pointerInput(isImeVisible) {
                            if (!isImeVisible) {
                                detectVerticalDragGestures { change, dragAmount ->
                                    if (dragAmount < -10f) {
                                        change.consume()
                                        try {
                                            composerFocusRequester.requestFocus()
                                            keyboardController?.show()
                                        } catch (_: Exception) {}
                                    }
                                }
                            }
                        }
                        .windowInsetsPadding(WindowInsets.navigationBars.union(WindowInsets.ime))
                ) {
                // Shared to "Notes" from other apps: waits here to be sent or dropped
                PendingSharesBar()
                ChatBottomComposerBar(
                    composerState = composerState,
                    viewModel = viewModel,
                    uiState = uiState,
                    isRecordingVoice = isRecordingVoice,
                    partnerName = partnerName,
                    myUser = myUser,
                    onScheduleText = {
                        if (composerState.text.isNotBlank()) {
                            textToSchedule = composerState.text
                            showScheduleSheet = true
                        }
                    },
                    triggerCameraSnap = triggerCameraSnap,
                    // Not in private chat, which only sends photos
                    triggerCameraVideo = if (isPrivate) null else triggerCameraVideo,
                    onPickAttachment = {
                        if (isPrivate) {
                            val prefs = com.example.security.SecurityPreferences.getInstance(context)
                            prefs.isExternalPickerActive = true
                            prefs.ignoreNextPause = true
                            photoPickerLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        } else {
                            showAttachmentSheet = true
                        }
                    },
                    onPickVideo = {
                        val prefs = com.example.security.SecurityPreferences.getInstance(context)
                        prefs.isExternalPickerActive = true
                        prefs.ignoreNextPause = true
                        videoPickerLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)
                        )
                    },
                    onPickDocument = {
                        val prefs = com.example.security.SecurityPreferences.getInstance(context)
                        prefs.isExternalPickerActive = true
                        prefs.ignoreNextPause = true
                        documentPickerLauncher.launch(arrayOf("*/*"))
                    },
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
                    onMicPermissionRequired = {
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
                    onSendHaptic = { view.chatHaptic(ChatHaptic.Send) },
                    onUnreadJumpDone = { unreadJumpDone = true },
                    // Tapping the message box keeps the newest messages in view, but only when they
                    // already are: someone reading back stays where they are
                    onInputFocused = {
                        if (reversedChatRowItems.isNotEmpty() && listState.firstVisibleItemIndex <= 2) {
                            scope.launch {
                                listState.scrollToItem(0, scrollOffset = 0)
                                delay(320)
                                if (listState.firstVisibleItemIndex <= 2) listState.scrollToItem(0, scrollOffset = 0)
                            }
                        }
                    },
                    focusRequester = composerFocusRequester
                )

            }
                }
            }
    },
        containerColor = if (showWallpaper) {
            Color.Transparent
        } else if (isDark) {
            Color.Black
        } else if (uiState.chatExperienceMode == com.example.ui.chat.ChatExperienceMode.PRIVATE) {
            Color.White
        } else MaterialTheme.colorScheme.background
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Watch Party: the bar while watching here, or the invite to join one that's on (not in
            // private chat or under the curtain)
            com.example.ui.watchparty.ChatWatchPartySlot(
                blocked = uiState.isStealthCurtainActive ||
                    uiState.chatExperienceMode == com.example.ui.chat.ChatExperienceMode.PRIVATE
            )

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
                            imageVector = Icons.Rounded.Star,
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
            AnimatedVisibility(visible = isPartnerTouching && !showHeartbeatTouch) {
                Surface(
                    color = HeartRed.copy(alpha = 0.15f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, HeartRed.copy(alpha = 0.45f)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 4.dp)
                        .clickable {
                            userDismissedHeartbeatTouch = false
                            showHeartbeatTouch = true
                        }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Favorite,
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
                        CherishTonalButton(
                            onClick = {
                                userDismissedHeartbeatTouch = false
                                showHeartbeatTouch = true
                            },
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
                                    highlightedMessageId = pinned.id
                                    listState.revealMessage(displayedMessages.lastIndex - index)
                                    delay(800)
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
                val hasTodayMessages = remember(displayedMessages) { displayedMessages.any { isToday(it.timestamp) } }
                val isTodayChatOnly = remember(displayedMessages, uiState.showPreviousChats, uiState.searchQuery, uiState.filterStarredOnly) {
                    !uiState.showPreviousChats &&
                        uiState.searchQuery.isBlank() &&
                        !uiState.filterStarredOnly &&
                        displayedMessages.all { isToday(it.timestamp) }
                }
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

                // The newest of my messages she has read: her small photo sits under it ("seen")
                val lastPartnerReadMessageId = remember(uiState.messages, currentUserId) {
                    uiState.messages
                        .filter { it.senderId == currentUserId && (it.getTypedStatus() == com.example.data.model.MessageStatus.READ || it.readTimestamp != null) }
                        .maxByOrNull { it.timestamp }
                        ?.id
                }

                // The typing bubble sits under the list, so as it grows the newest messages move up
                Column(modifier = Modifier.fillMaxSize()) {
                // Swiping down on chat list closes keyboard cleanly; swiping up opens keyboard
                val dismissKeyboardOnSwipeConnection = remember(isImeVisible) {
                    object : NestedScrollConnection {
                        override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                            if (source == NestedScrollSource.Drag) {
                                if (isImeVisible && available.y > 12f) {
                                    keyboardController?.hide()
                                } else if (!isImeVisible && available.y < -15f && listState.firstVisibleItemIndex <= 1) {
                                    try {
                                        composerFocusRequester.requestFocus()
                                        keyboardController?.show()
                                    } catch (_: Exception) {}
                                }
                            }
                            return Offset.Zero
                        }

                        override fun onPostScroll(consumed: Offset, unconsumed: Offset, source: NestedScrollSource): Offset {
                            if (source == NestedScrollSource.Drag && !isImeVisible && unconsumed.y < -8f) {
                                try {
                                    composerFocusRequester.requestFocus()
                                    keyboardController?.show()
                                } catch (_: Exception) {}
                            }
                            return Offset.Zero
                        }
                    }
                }

                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        // The scroll feel outside the keyboard's: the keyboard's pull-up still sees
                        // what the list left over before the accordion takes it
                        .nestedScroll(chatScrollConnection)
                        .nestedScroll(dismissKeyboardOnSwipeConnection)
                        // Lips (or a thumb) held on the background: a kiss there (see KissTouch)
                        .glassTouchOnHold(
                            enabled = kissesAllowed && !isSelectionMode,
                            onGlassTouch = { normX, normY ->
                                if (normX != null && normY != null) {
                                    currentGlassTouch = Offset(normX, normY)
                                    kissSpot.at(normX, normY)
                                } else {
                                    currentGlassTouch = null
                                    kissSpot.letGo()
                                }
                            },
                            onKissStamp = onKissHold,
                            rootCoords = { kissRootCoords[0] },
                            listCoords = { kissListCoords[0] }
                        )
                        .pointerInput(Unit) {
                            detectTapGestures(onDoubleTap = { onQuickDisguise() })
                        },
                    reverseLayout = true,
                    contentPadding = PaddingValues(top = 10.dp, bottom = 14.dp),
                    // A short chat (e.g. a new day) starts at the top, under the header, instead of
                    // sitting at the bottom by the message box
                    verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.Top)
                ) {
                    // A new day with nothing shown yet: "Today" and the banner open the chat at the top
                    // (not in private chat, which stays plain)
                    if (reversedChatRowItems.isEmpty()) {
                        if (uiState.chatExperienceMode == com.example.ui.chat.ChatExperienceMode.PRIVATE) {
                            item(key = "chatgpt_empty_state", contentType = "empty_state") {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 80.dp, bottom = 40.dp, start = 24.dp, end = 24.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    // Just the words, like a new ChatGPT chat
                                    Text(
                                        text = "What can I help with?",
                                        fontSize = 24.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = if (isDark) Neutral200 else InkBlack
                                    )
                                }
                            }
                        } else if (showEmptyTodayHeader) {
                            item(key = "empty_today_header", contentType = "today_header") {
                                Column(modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp)) {
                                    DateSeparatorBadge(dateText = "Today", isPrivateMode = false, isDark = isDark)
                                    if (isTodayChatOnly) {
                                        StrictlyTwoPersonBanner(isPrivateMode = false, isDark = isDark)
                                    }
                                }
                            }
                        }
                    }

                    items(
                        items = reversedChatRowItems,
                        key = { it.message.id },
                        contentType = { item ->
                            val dir = if (item.message.senderId == currentUserId) "OUT" else "IN"
                            val type = if (item.message.isCircularVideoNote()) "VIDEO_NOTE" else item.message.getTypedType().name
                            "$dir:$type"
                        }
                    ) { item ->
                        val message = item.message
                        val isFromMe = message.senderId == currentUserId
                        // A message that arrives while the chat is open lands with a spring
                        val isFreshArrival = remember(message.id) {
                            initialSnapshotTaken[0] &&
                                message.timestamp >= chatOpenedAt - 2_000L &&
                                message.id !in initialMessageIds && landedMessageIds.add(message.id)
                        }
                        // Mine, and my partner is hearing it right now
                        val hearing = partnerListening?.takeIf { isFromMe && it.messageId == message.id }
                        Box(
                            modifier = Modifier
                                // A deleted message fades out and its neighbours glide together to
                                // close the gap (a new one lands with its own spring, see landing)
                                .animateItem(
                                    fadeInSpec = null,
                                    placementSpec = spring(
                                        dampingRatio = 0.85f,
                                        stiffness = Spring.StiffnessMediumLow,
                                        visibilityThreshold = IntOffset.VisibilityThreshold
                                    ),
                                    fadeOutSpec = tween(180)
                                )
                                .fillMaxWidth()
                        ) {
                            ChatMessageRow(
                                message = message,
                                isFromMe = isFromMe,
                                isFirstOfDay = item.isFirstOfDay,
                                dateSeparatorText = item.dateSeparatorText,
                                isOldestShown = item.isOldestShown,
                                isFreshArrival = isFreshArrival,
                                isPrivate = uiState.chatExperienceMode == com.example.ui.chat.ChatExperienceMode.PRIVATE,
                                isDark = isDark,
                                isHighlighted = highlightedMessageId == message.id,
                                isSelectionMode = isSelectionMode,
                                isSelected = isSelectionMode && message.id in selectedItemIds,
                                isPlayingAudio = currentPlayingId == message.id && isAudioPlaying,
                                audioProgress = {
                                    when {
                                        currentPlayingIdState.value == message.id -> audioProgressState.value
                                        hearing != null -> hearing.progress(listeningClock.longValue)
                                        else -> 0f
                                    }
                                },
                                partnerHearing = hearing?.playing,
                                gallerySize = uiState.gallerySize,
                                voicePlaybackSpeed = uiState.voicePlaybackSpeed,
                                senderPhotoUrl = if (isFromMe) uiState.currentUser?.photoUrl else uiState.partnerUser?.photoUrl,
                                actions = rowActions,
                                isLastReadMessage = (message.id == lastPartnerReadMessageId),
                                partnerPhotoUrl = uiState.partnerUser?.photoUrl,
                                partnerName = partnerName,
                                isTodayChatOnly = isTodayChatOnly,
                                isFocused = (focusedMessage?.id == message.id)
                            )
                        }
                    }

                    // 1. Loading older messages indicator (shown at top of list while paginating)
                    if (uiState.isLoadingMore) {
                        item(key = "pagination_loader", contentType = "loader") {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 16.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(20.dp),
                                    color = if (isDark) darkTone(Gray800).copy(alpha = 0.95f) else Color.White.copy(alpha = 0.95f),
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
                                            color = if (isDark) Color.White else NavyCard
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Strictly 2-Person Beginning of Chat Banner (shown when pagination is exhausted)
            } // Box content end

                // No message yet today but older ones are shown (Recover all): "Today" badge
                // follows them, where the first message will land (banner removed when older chats are recovered)
                if (showEmptyTodayHeader && reversedChatRowItems.isNotEmpty()) {
                    val isPrivate = uiState.chatExperienceMode == com.example.ui.chat.ChatExperienceMode.PRIVATE
                    if (!isPrivate) {
                        Column(modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp)) {
                            DateSeparatorBadge(dateText = "Today", isPrivateMode = isPrivate, isDark = isDark)
                            if (isTodayChatOnly) {
                                StrictlyTwoPersonBanner(isPrivateMode = false, isDark = isDark)
                            }
                        }
                    }
                }

                // Only at the bottom: while reading older messages it would push the list up and down
                // (the header still says "typing…")
                val isAtBottom by remember { derivedStateOf { listState.firstVisibleItemIndex <= 2 } }
                ChatPartnerTypingBubbleWrapper(
                    presenceFlow = viewModel.partnerPresence,
                    chatExperienceMode = uiState.chatExperienceMode,
                    isSearching = uiState.isSearching,
                    isAtBottom = isAtBottom,
                    partnerPhotoUrl = partner?.photoUrl,
                    fallbackPartnerName = partnerName,
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

            // The scroll position is read in a scope of its own (see OwnScope), so scrolling
            // rebuilds just the date chip and the new-messages pill, not the whole chat
            OwnScope {
                // WhatsApp-style date chip: the date of the messages at the top, shown while scrolling
                var showDateChip by remember { mutableStateOf(false) }
                val isListScrolling = listState.isScrollInProgress
                LaunchedEffect(isListScrolling) {
                    com.example.ui.components.setVideoNotesScrollState(isListScrolling)
                    if (isListScrolling) {
                        showDateChip = true
                    } else {
                        delay(1200)
                        showDateChip = false
                    }
                }
                val topVisibleDate by remember(reversedChatRowItems) {
                    derivedStateOf {
                        val layoutInfo = listState.layoutInfo
                        val topItem = layoutInfo.visibleItemsInfo.lastOrNull() ?: return@derivedStateOf null
                        val topIndex = topItem.index
                        val rowItem = reversedChatRowItems.getOrNull(topIndex)
                        if (rowItem != null) {
                            val opensDay = rowItem.isFirstOfDay
                            if (opensDay && topItem.offset + topItem.size <= layoutInfo.viewportEndOffset) {
                                return@derivedStateOf null
                            }
                        }
                        val msg = rowItem?.message ?: reversedChatRowItems.lastOrNull()?.message
                        msg?.let { formatDateSeparator(it.timestamp) }
                            ?.takeIf { it.isNotEmpty() }
                    }
                }
                val lastShownDate = remember { arrayOf("") }
                topVisibleDate?.let { lastShownDate[0] = it }
                androidx.compose.animation.AnimatedVisibility(
                    visible = showDateChip && topVisibleDate != null && (uiState.chatExperienceMode != com.example.ui.chat.ChatExperienceMode.PRIVATE),
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
                val unreadJumpCount = remember(reversedChatRowItems, unreadOnOpen) {
                    if (unreadOnOpen.isEmpty()) 0 else reversedChatRowItems.count { it.message.id in unreadOnOpen }
                }
                // The oldest of them (the list runs bottom-up, so the highest index)
                val unreadJumpIndex = remember(reversedChatRowItems, unreadOnOpen) {
                    if (unreadOnOpen.isEmpty()) -1 else reversedChatRowItems.indexOfLast { it.message.id in unreadOnOpen }
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
                        .padding(top = 12.dp)
                        .offset { IntOffset(0, unreadJumpDrop.roundToPx()) }
                ) {
                    val isPrivate = uiState.chatExperienceMode == com.example.ui.chat.ChatExperienceMode.PRIVATE
                    Surface(
                        onClick = {
                            val index = unreadJumpIndex
                            unreadJumpDone = true
                            val target = reversedChatRowItems.getOrNull(index)?.message
                            if (target != null) {
                                view.chatHaptic(ChatHaptic.Tick)
                                scope.launch {
                                    highlightedMessageId = target.id
                                    listState.revealMessage(index)
                                    delay(800)
                                    if (highlightedMessageId == target.id) highlightedMessageId = null
                                }
                            }
                        },
                        shape = CircleShape,
                        color = if (isPrivate) (if (isDark) darkTone(Graphite700) else Gray200) else MaterialTheme.colorScheme.primary,
                        contentColor = if (isPrivate) (if (isDark) darkTone(Neutral200) else Gray800) else MaterialTheme.colorScheme.onPrimary,
                        shadowElevation = 4.dp,
                        modifier = Modifier.testTag("jump_to_unread")
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(start = 12.dp, end = 16.dp, top = 8.dp, bottom = 8.dp)
                        ) {
                            Icon(
                                imageVector = if (unreadJumpUp[0]) Icons.Rounded.KeyboardArrowUp else Icons.Rounded.KeyboardArrowDown,
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
            }

            // Floating scroll to bottom button
            val showScrollButton by remember {
                derivedStateOf {
                    listState.firstVisibleItemIndex > 3
                }
            }
            // Scrolled back to the replied message by hand, or all the way down: nothing to return to
            LaunchedEffect(listState) {
                var watched: String? = null
                var leftIt = false
                var leftBottom = false
                snapshotFlow {
                    val top = replyReturns.lastOrNull()
                    Triple(
                        top,
                        top != null && listState.layoutInfo.visibleItemsInfo.any { it.key == top },
                        listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset < 8
                    )
                }.collect { (top, onScreen, atBottom) ->
                    if (top == null) {
                        watched = null
                        return@collect
                    }
                    if (top != watched) {
                        watched = top
                        leftIt = !onScreen
                        leftBottom = !atBottom
                    }
                    if (!onScreen) leftIt = true
                    if (!atBottom) leftBottom = true
                    when {
                        atBottom && leftBottom -> {
                            replyReturns.clear()
                            watched = null
                        }
                        onScreen && leftIt -> {
                            replyReturns.remove(top)
                            watched = null
                        }
                    }
                }
            }

            androidx.compose.animation.AnimatedVisibility(
                visible = showScrollButton,
                enter = scaleIn(spring(dampingRatio = 0.55f, stiffness = 500f), initialScale = 0.4f) + fadeIn(tween(120)),
                exit = scaleOut(tween(140), targetScale = 0.6f) + fadeOut(tween(140)),
                // Private chat: in the middle above the message box, as in ChatGPT
                modifier = Modifier
                    .align(
                        if (uiState.chatExperienceMode == com.example.ui.chat.ChatExperienceMode.PRIVATE) Alignment.BottomCenter
                        else Alignment.BottomEnd
                    )
                    .padding(16.dp)
            ) {
                val isPrivate = uiState.chatExperienceMode == com.example.ui.chat.ChatExperienceMode.PRIVATE
                Box {
                    SmallFloatingActionButton(
                        onClick = {
                            // Back to the message whose reply quote was tapped, if any; then the newest
                            var back: String? = null
                            var backIndex = -1
                            while (backIndex < 0 && replyReturns.isNotEmpty()) {
                                val id = replyReturns.removeAt(replyReturns.lastIndex)
                                back = id
                                backIndex = reversedChatRowItems.indexOfFirst { it.message.id == id }
                            }
                            val returnTo = back.takeIf { backIndex >= 0 }
                            val returnIndex = backIndex
                            scope.launch {
                                if (returnTo != null) {
                                    highlightedMessageId = returnTo
                                    listState.revealMessage(returnIndex)
                                    delay(800)
                                    if (highlightedMessageId == returnTo) highlightedMessageId = null
                                } else if (displayedMessages.isNotEmpty()) {
                                    listState.animateScrollToItem(0)
                                }
                            }
                        },
                        containerColor = if (isPrivate) {
                            if (isDark) {
                                if (com.example.ui.theme.LocalAmoledBlack.current) Neutral900 else PrivateChromeDark
                            } else PrivateChromeLight
                        } else MaterialTheme.colorScheme.primaryContainer,
                        contentColor = if (isPrivate) (if (isDark) Neutral200 else InkBlack) else MaterialTheme.colorScheme.onPrimaryContainer,
                        shape = CircleShape
                    ) {
                        Icon(
                            imageVector = if (isPrivate) Icons.Rounded.ArrowDownward else Icons.Rounded.KeyboardArrowDown,
                            contentDescription = "Scroll to bottom",
                            modifier = Modifier.size(if (isPrivate) 21.dp else 24.dp)
                        )
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
                                    if (isPrivate) (if (isDark) darkTone(Neutral200) else Gray800) else HeartRed,
                                    CircleShape
                                )
                                .padding(horizontal = 4.dp)
                                .testTag("chat_unseen_badge"),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = if (unseenIncomingCount > 99) "99+" else unseenIncomingCount.toString(),
                                color = if (isPrivate && isDark) Slate900 else Color.White,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            val isPrivateChat = uiState.chatExperienceMode == com.example.ui.chat.ChatExperienceMode.PRIVATE
            if (isPrivateChat) {
                val fadeColor = if (isDark) Neutral800 else Color.White
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(28.dp)
                        .align(Alignment.TopCenter)
                        .background(
                            Brush.verticalGradient(
                                colorStops = arrayOf(
                                    0.0f to fadeColor,
                                    0.5f to fadeColor.copy(alpha = 0.65f),
                                    1.0f to Color.Transparent
                                )
                            )
                        )
                )
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
                    .padding(start = 12.dp, end = 72.dp, bottom = 6.dp)
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
                    .padding(top = 12.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = Graphite800.copy(alpha = 0.95f),
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
    } // Chat text style


    androidx.compose.animation.AnimatedVisibility(
        visible = showGoodMorning,
        enter = fadeIn(tween(350)),
        exit = fadeOut(tween(500))
    ) {
        GoodMorningGreeting(
            myName = myUser?.displayName?.ifBlank { null } ?: "love",
            partnerName = partnerName,
            onSendGoodMorning = { viewModel.sendGoodMorning(partnerName) },
            alreadySentToday = sentGoodMorningToday,
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

    // Kisses on the chat, mine and theirs, blooming over the messages (taking no touches)
    KissStampLayer(
        blooms = kissBlooms,
        partnerName = partnerName,
        onDone = { kissBlooms.remove(it) },
        modifier = Modifier.fillMaxSize()
    )

    // A live kiss through the screen: mine, and theirs where they're kissing (only where kisses show)
    GlassWindowTouchLayer(
        partnerName = partnerName,
        myTouch = currentGlassTouch,
        partnerTouch = if (kissesAllowed && isChatInView) partnerKissTouch else null,
        modifier = Modifier.fillMaxSize()
    )

    if (partnerFeltKey != 0L) {
        key(partnerFeltKey) {
            PartnerFeltOverlay(partnerName = partnerName, onDone = { partnerFeltKey = 0L })
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
            add(FocusAction("Reply", Icons.AutoMirrored.Rounded.Reply) { viewModel.setReplyingTo(live) })
            add(
                FocusAction(
                    label = if (live.isStarred) "Unstar" else "Star",
                    icon = if (live.isStarred) Icons.Rounded.Star else Icons.Rounded.StarOutline
                ) { viewModel.toggleStar(live.id) }
            )
            add(
                FocusAction(
                    label = if (live.isPinned) "Unpin" else "Pin",
                    icon = if (live.isPinned) Icons.Rounded.PushPin else Icons.Outlined.PushPin
                ) { viewModel.togglePin(live.id) }
            )
            if (live.getTypedType() == MessageType.TEXT && live.text.isNotBlank()) {
                add(FocusAction("Copy", Icons.Rounded.ContentCopy) {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText("Cherish Message", live.text))
                })
            }
            if (focusedIsFromMe && !live.isDeleted) {
                add(FocusAction("Edit", Icons.Rounded.Edit) {
                    editingMessage = live
                    editDialogText = live.text
                })
            }
            add(FocusAction("Select", Icons.Rounded.CheckCircleOutline) {
                isSelectionMode = true
                selectedItemIds = setOf(live.id)
            })
            add(FocusAction("Save to Memories", Icons.Rounded.FavoriteBorder) {
                CherishToast.show("Saved to Our Memories ❤️")
            })
            add(FocusAction("Delete", Icons.Rounded.Delete, isDestructive = true) { showDeleteConfirmDialog = live })
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
            CompositionLocalProvider(LocalChatTextStyle provides chatTextStyle, LocalHugCouple provides hugCouple, LocalChatNight provides isNight) {
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
                    senderPhotoUrl = if (focusedIsFromMe) uiState.currentUser?.photoUrl else uiState.partnerUser?.photoUrl,
                    isFocusOverlay = true
                )
            }
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
                CherishToast.show("Saved to Our Memories ❤️")
            }
        )
    }

    // Send later: pick when (long-press on send)
    if (showScheduleSheet) {
        ScheduleMessageDialog(
            text = textToSchedule,
            onSchedule = { sendAt ->
                viewModel.scheduleMessage(textToSchedule, sendAt)
                composerState.text = ""
                textToSchedule = ""
                viewModel.onTypingChanged(false)
                showScheduleSheet = false
                CherishToast.show("Scheduled for " + formatScheduleTime(sendAt))
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

    // The kiss pad: lips to lips, felt on both phones when both kiss at once (see KissTouch)
    if (showKissPad) {
        KissPadDialog(
            partnerName = partnerName,
            partnerTouch = partnerKissTouch,
            onTouch = { x, y -> viewModel.setKissTouch(x, y) },
            onDismiss = { showKissPad = false },
            userIsMale = isMyMale,
            partnerIsMale = isPartnerMale
        )
    }

    // Simultaneous Heartbeat Touch Dialog (Haptic Sync)
    if (showHeartbeatTouch) {
        HeartbeatTouchDialog(
            partnerName = partnerName,
            isPartnerTouching = isPartnerTouching,
            isPartnerOnline = isPartnerOnlineNow || uiState.isPartnerOnline,
            onTouchChanged = { viewModel.setHeartbeatTouch(it) },
            onSyncHeartbeatStreak = { viewModel.syncHeartbeatStreak() },
            onDismiss = {
                showHeartbeatTouch = false
                userDismissedHeartbeatTouch = true
            }
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
            containerColor = com.example.ui.components.CherishDialogDefaults.sheetContainerColor,
            shape = com.example.ui.components.CherishDialogDefaults.sheetShape,
            dragHandle = { com.example.ui.components.CherishDialogDefaults.SheetDragHandle() }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .fluidSheetInertia()
                    .padding(horizontal = 24.dp)
                    .padding(bottom = 36.dp, top = 8.dp)
            ) {
                Text(
                    text = "Share with your love",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(bottom = 20.dp)
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceAround
                ) {
                    AttachmentOptionItem(
                        icon = Icons.Rounded.PhotoLibrary,
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
                        icon = Icons.Rounded.Videocam,
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
                    
                    AttachmentOptionItem(
                        icon = Icons.AutoMirrored.Rounded.InsertDriveFile,
                        label = "Document",
                        color = Color(0xFF00BFA5),
                        onClick = {
                            showAttachmentSheet = false
                            val prefs = com.example.security.SecurityPreferences.getInstance(context)
                            prefs.isExternalPickerActive = true
                            prefs.ignoreNextPause = true
                            documentPickerLauncher.launch(arrayOf("*/*"))
                        }
                    )

                    AttachmentOptionItem(
                        icon = Icons.Rounded.PlayCircle,
                        label = "Watch Together",
                        color = Color(0xFFE53935),
                        onClick = {
                            showAttachmentSheet = false
                            showWatchPartySetup = true
                        }
                    )
                }
            }
        }
    }

    if (showWatchPartySetup) {
        val app = context.applicationContext as? com.example.CherishApplication
        val coupleRepo = app?.coupleFeaturesRepository
        com.example.ui.chat.WatchPartySetupDialog(
            onStart = { videoId, title ->
                showWatchPartySetup = false
                coupleRepo?.startWatchParty(videoId, title, "")
                com.example.ui.watchparty.WatchPartyUi.openTheater()
            },
            onDismiss = { showWatchPartySetup = false }
        )
    }

    // Edit Message Dialog
    editingMessage?.let { msg ->
        CherishAlertDialog(
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
                CherishButton(
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
        CherishAlertDialog(
            onDismissRequest = { showDeleteConfirmDialog = null },
            title = { Text("Delete message?") },
            text = { Text("This message will be removed from your private chat.") },
            confirmButton = {
                CherishButton(
                    onClick = {
                        view.chatHaptic(ChatHaptic.Delete)
                        viewModel.deleteMessage(msg.id)
                        showDeleteConfirmDialog = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
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
        CherishAlertDialog(
            onDismissRequest = { showMultiDeleteConfirmDialog = false },
            title = { Text(if (count == 1) "Delete message?" else "Delete $count messages?") },
            text = {
                Text(if (count == 1) "This message will be removed from your private chat." else "These $count messages will be removed from your private chat.")
            },
            confirmButton = {
                CherishButton(
                    onClick = {
                        view.chatHaptic(ChatHaptic.Delete)
                        viewModel.deleteMessages(selectedItemIds)
                        showMultiDeleteConfirmDialog = false
                        exitSelection()
                    },
                    enabled = count > 0,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
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
        val circularUrls = remember(uiState.messages) {
            uiState.messages
                .filter { it.isCircularVideoNote() || it.isVideoNote }
                .flatMap { it.getAllMediaUrls().ifEmpty { listOfNotNull(it.mediaUrl) } }
                .filter { it.isNotBlank() }
                .toSet()
        }
        val mediaTimes = remember(uiState.messages) { mediaTimesOf(uiState.messages) }
        FullScreenMediaViewer(
            mediaUrl = url,
            allMediaUrls = uiState.allMediaUrlsForViewer,
            openFrom = openFrom,
            isCircularVideo = uiState.isFullScreenVideoNote,
            circularMediaUrls = circularUrls,
            mediaTimes = mediaTimes,
            onShowInChat = { clickedUrl ->
                val targetMsg = uiState.messages.find { it.mediaUrl == clickedUrl || it.getAllMediaUrls().contains(clickedUrl) }
                if (targetMsg != null && !viewModel.navigateToMessageInChat(targetMsg.id)) {
                    CherishToast.show("Older chat is hidden. Use Recover All in settings to see it.", long = true)
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

    // The calendar in chat search: pick a day and the chat jumps to it
    if (showJumpToDate) {
        JumpToDateDialog(
            onPick = { year, month, dayOfMonth ->
                showJumpToDate = false
                viewModel.jumpToDate(year, month, dayOfMonth) { result ->
                    when (result) {
                        DateJumpResult.OlderChatHidden ->
                            CherishToast.show("Older chat is hidden. Use Recover All in settings to see it.", long = true)
                        DateJumpResult.NoMessages -> CherishToast.show("No messages from that day")
                        DateJumpResult.NextDayFound -> CherishToast.show("No messages that day, showing next available date")
                        DateJumpResult.Found -> CherishToast.show("Jumped to date")
                    }
                }
            },
            onDismiss = { showJumpToDate = false }
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
        CherishAlertDialog(
            onDismissRequest = { showClearChatDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.DeleteSweep, contentDescription = null, tint = MaterialTheme.colorScheme.error)
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
                CherishButton(
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

    // Every few days while this phone (Xiaomi, Vivo, Samsung...) still holds back pushes to a closed
    // Notes: the switches to turn on, so messages and calls always arrive. Never over Private mode
    var showDeliverySetup by remember { mutableStateOf(false) }
    val canPromptDelivery = uiState.chatExperienceMode != com.example.ui.chat.ChatExperienceMode.PRIVATE &&
        !uiState.isStealthCurtainActive
    LaunchedEffect(canPromptDelivery) {
        if (!canPromptDelivery) return@LaunchedEffect
        kotlinx.coroutines.delay(2_500L)
        if (com.example.notifications.BackgroundDelivery.shouldPrompt(context)) {
            com.example.notifications.BackgroundDelivery.onPrompted(context)
            showDeliverySetup = true
        }
    }
    if (showDeliverySetup) {
        com.example.ui.components.DeliverySetupSheet(onDismiss = { showDeliverySetup = false })
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
                // Always breathes, online or not (their avatar in the top bar only while online)
                breathing = true,
                isProfilePhoto = true,
                partnerName = partnerName,
                onOpenKissScreen = {
                    showKissPad = true
                },
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

                        val partnerDisguiseOn = partner?.isDisguiseEnabled ?: true
                        val partnerIsCalc = partner?.disguiseStyle == "calculator"
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier.padding(top = 8.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            ) {
                                if (partnerIsCalc) {
                                    androidx.compose.foundation.Image(
                                        painter = painterResource(id = com.example.R.drawable.ic_samsung_calculator),
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp).clip(RoundedCornerShape(4.dp))
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.Rounded.EditNote,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = if (!partnerDisguiseOn) "Disguise: Off" else if (partnerIsCalc) "Disguise: Calculator" else "Disguise: Notes App",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        // Romantic Kiss action inside profile view
                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = if (isMyMale) Color(0xFFC76270).copy(alpha = 0.88f) else Color(0xFFE0475F).copy(alpha = 0.88f),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.45f)),
                            modifier = Modifier
                                .padding(top = 14.dp)
                                .pressScaleTarget()
                                .springClickable {
                                    context.vibrateKiss()
                                    viewModel.sendKiss(0.5f, 0.5f)
                                    kissBlooms += KissBloom(java.util.UUID.randomUUID().toString(), 0.5f, 0.5f, fromMe = true, isMale = isMyMale)
                                }
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                            ) {
                                Text(if (isMyMale) "👄" else "💋", fontSize = 16.sp)
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = "Kiss ${partnerName.trim().substringBefore(' ')}",
                                    color = Color.White,
                                    fontSize = 13.5.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        // Romantic Kiss Screen button inside profile view
                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = Color(0xFF6B1832).copy(alpha = 0.88f),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE0475F).copy(alpha = 0.6f)),
                            modifier = Modifier
                                .padding(top = 8.dp)
                                .pressScaleTarget()
                                .springClickable {
                                    val app = context.applicationContext as? com.example.CherishApplication
                                    app?.securityPreferences?.isMediaViewerActive = false
                                    showFullProfilePicViewer = false
                                    showKissPad = true
                                }
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                            ) {
                                Text(if (isPartnerMale) "👄" else "💋", fontSize = 16.sp)
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = "Kiss Screen ${if (isPartnerMale) "👄" else "💋"}",
                                    color = Color.White,
                                    fontSize = 13.5.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))
                        CherishButton(
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
                CherishToast.show("Asked $partnerName. It starts when they accept.", long = true)
            } else {
                CherishToast.show("Couldn't send the request. Try again.")
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
                    fontSize = 16.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
            },
            navigationIcon = {
                IconButton(
                    onClick = onClose,
                    modifier = Modifier.testTag("chat_selection_close")
                ) {
                    Icon(Icons.Rounded.Close, contentDescription = "Cancel selection")
                }
            },
            actions = {
                IconButton(
                    onClick = onDelete,
                    enabled = selectedCount > 0,
                    modifier = Modifier.testTag("chat_delete_selected")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Delete,
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
    val accent = if (isPrivateMode) (if (isDark) darkTone(Gray400) else Gray500) else MaterialTheme.colorScheme.primary
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = if (isDark) darkSurface(NavyRaised, Neutral900) else Color.White,
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
                    Icon(Icons.Rounded.Close, contentDescription = "Cancel sending", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
                }
            } else {
                Icon(Icons.Rounded.ErrorOutline, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
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
                    Icon(Icons.Rounded.Close, contentDescription = "Dismiss", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
                }
            }
        }
    }
}

private const val GOOD_MORNING_FROM_HOUR = 5
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

/** A sunrise card over the chat: the sun rises, sends a good morning message automatically without typing, and shows a loving greeting. */
@Composable
private fun GoodMorningGreeting(
    myName: String,
    partnerName: String,
    onSendGoodMorning: () -> Unit,
    alreadySentToday: Boolean = false,
    onDismiss: () -> Unit
) {
    var sent by remember { mutableStateOf(alreadySentToday) }
    val rise = remember { Animatable(0f) }
    val cardIn = remember { Animatable(0.86f) }
    LaunchedEffect(Unit) {
        launch { cardIn.animateTo(1f, spring(dampingRatio = 0.62f, stiffness = 380f)) }
        rise.animateTo(1f, tween(1400, easing = FastOutSlowInEasing))
        // Automatically send the good morning message without needing to type!
        if (!sent) {
            delay(700)
            if (!sent) {
                sent = true
                onSendGoodMorning()
            }
        }
    }
    val isAppForeground = com.example.ui.components.rememberAppIsResumed()
    val glowScale: Float = if (isAppForeground) {
        val glow = rememberInfiniteTransition(label = "sun_glow")
        val scale by glow.animateFloat(
            initialValue = 0.9f,
            targetValue = 1.12f,
            animationSpec = infiniteRepeatable(tween(1600, easing = FastOutSlowInEasing), RepeatMode.Reverse),
            label = "sun_glow_scale"
        )
        scale
    } else {
        1.0f
    }
    val date = remember {
        java.text.SimpleDateFormat("EEEE, MMMM d", java.util.Locale.getDefault()).format(java.util.Date())
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.35f))
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
                .softShadow(20.dp, RoundedCornerShape(28.dp))
                .clip(RoundedCornerShape(28.dp))
                .background(
                    Brush.verticalGradient(
                        listOf(Color(0xFFFFE6B8), Color(0xFFFFC2A1), Color(0xFFFF9EB5))
                    )
                )
                .pointerInput(Unit) { detectTapGestures { } },
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 28.dp)
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
                                Brush.radialGradient(listOf(Butter, Color(0x00FFF3C4))),
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
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = if (sent) "Good morning sent to $partnerName \u2600\uFE0F\uD83D\uDC9B" else "A new day with $partnerName \uD83D\uDC9B",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color(0xFF5E3438),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
                Spacer(modifier = Modifier.height(16.dp))
                Surface(
                    shape = RoundedCornerShape(50),
                    color = if (sent) Butter.copy(alpha = 0.9f) else Butter,
                    border = BorderStroke(1.dp, Color(0xFFE6A756).copy(alpha = 0.45f)),
                    shadowElevation = 3.dp,
                    modifier = Modifier
                        .pressScaleTarget()
                        .clip(RoundedCornerShape(50))
                        .springClickable {
                            if (!sent) {
                                sent = true
                                onSendGoodMorning()
                            } else {
                                onDismiss()
                            }
                        }
                        .testTag("good_morning_auto_sent")
                ) {
                    Text(
                        text = if (sent) "Sent automatically \u2728" else "Sending good morning \u2600\uFE0F",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF5A3015),
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Tap to close",
                    fontSize = 12.sp,
                    color = Color(0xFF8A5A5E).copy(alpha = 0.8f),
                    modifier = Modifier
                        .clickable { onDismiss() }
                        .padding(4.dp)
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
    // Any time: the app's own (Material) time picker
    var showTimePicker by remember { mutableStateOf(false) }
    if (showTimePicker) {
        val now = remember { java.util.Calendar.getInstance() }
        com.example.ui.components.CherishTimePickerDialog(
            title = "Send at",
            initialHour = now.get(java.util.Calendar.HOUR_OF_DAY),
            initialMinute = now.get(java.util.Calendar.MINUTE),
            confirmText = "Schedule",
            onPick = { hour, minute ->
                showTimePicker = false
                onSchedule(nextTimeAt(hour, minute))
            },
            onDismiss = { showTimePicker = false }
        )
    }
    val choices = remember {
        listOf(
            nextTimeAt(7),
            nextTimeAt(22),
            System.currentTimeMillis() + 60 * 60 * 1000L
        )
    }
    CherishAlertDialog(
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
                    CherishOutlinedButton(
                        onClick = { onSchedule(at) },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(if (i == 2) "In 1 hour" else formatScheduleTime(at))
                    }
                }
                CherishOutlinedButton(
                    onClick = { showTimePicker = true },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Rounded.Schedule, contentDescription = null, modifier = Modifier.size(16.dp))
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
        color = if (isDark) darkSurface(NavyRaised, Neutral900) else Color.White,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = if (isDark) 0.35f else 0.22f)),
        shadowElevation = 6.dp,
        modifier = Modifier.clickable(onClick = onClick).testTag("scheduled_messages_pill")
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            Icon(Icons.Rounded.Schedule, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
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
    CherishAlertDialog(
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
                            Icon(Icons.Rounded.Close, contentDescription = "Cancel scheduled message", tint = MaterialTheme.colorScheme.error)
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
    /** The reply quote in a message ([fromMessageId]) was tapped: go to the message it quotes. */
    val jumpToReply: (fromMessageId: String, replyId: String?) -> Unit,
    val toggleSelection: (String) -> Unit
)

/**
 * Runs [content] in a recompose scope of its own: state it reads (the list's scroll position,
 * say) then rebuilds only it, not the whole chat around it.
 */
@Composable
private fun OwnScope(content: @Composable () -> Unit) {
    content()
}

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
    dateSeparatorText: String = "",
    isOldestShown: Boolean,
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
    modifier: Modifier = Modifier,
    isLastReadMessage: Boolean = false,
    partnerPhotoUrl: String? = null,
    partnerName: String = "Partner",
    isTodayChatOnly: Boolean = false,
    // Mine, and my partner is hearing it right now (false: paused), see ListeningNow
    partnerHearing: Boolean? = null,
    isFocused: Boolean = false
) {
    val accordion = LocalAccordionPull.current
    Column(modifier = modifier.fillMaxWidth()) {
        if (isFirstOfDay) {
            val dateSep = if (dateSeparatorText.isNotEmpty()) dateSeparatorText else formatDateSeparator(message.timestamp)
            val today = isToday(message.timestamp)
            // In private mode (ChatGPT UI), date separator badges (Today, Yesterday, etc.) are never shown
            if (dateSep.isNotEmpty() && !isPrivate) {
                DateSeparatorBadge(dateText = dateSep, isPrivateMode = isPrivate, isDark = isDark)
            }
            if (today && !isPrivate && isTodayChatOnly) {
                StrictlyTwoPersonBanner(isPrivateMode = false, isDark = isDark)
            }
        }

        // Read marking is handled in the batched LaunchedEffect above the Scaffold.
        // Do NOT put Firestore writes inside LazyColumn items — they fire on every scroll.

        val selectionAccent = if (isPrivate) (if (isDark) darkTone(Gray400) else Gray500) else RoseGoldPrimary

        // A message that arrives while the chat is open lands with a spring: sent ones rise from
        // the composer, received ones slide in from the left. A big emoji has its own entrance
        // (it flies out of the message box or pops in, see BigEmoji)
        val isBigEmoji = remember(message, isPrivate) { !isPrivate && message.bigEmojis() != null }
        val landsWithSpring = isFreshArrival && !isBigEmoji
        val landing = remember(message.id) { Animatable(if (landsWithSpring) 0f else 1f) }
        val isNight = LocalChatNight.current
        LaunchedEffect(message.id) {
            if (landsWithSpring) {
                landing.animateTo(
                    1f,
                    if (isNight) spring(dampingRatio = 0.9f, stiffness = 170f) else spring(dampingRatio = 0.62f, stiffness = 420f)
                )
            }
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
        val glowColor = if (isPrivate) (if (isDark) darkTone(Gray500) else Gray400) else RoseGoldPrimary
        val rowTint = if (isSelectionMode && isSelected) {
            selectionAccent.copy(alpha = if (isDark) 0.22f else 0.14f)
        } else {
            Color.Transparent
        }
        // Private chat sits nearer the edges, with more room between messages, like ChatGPT
        val bubbleStartPadding = if (isSelectionMode) 6.dp else if (isPrivate) 14.dp else 16.dp
        val bubbleEndPadding = if (isPrivate) 14.dp else 16.dp
        // Where the message sits on screen, for the long-press focus view: [0] the row, [1] the
        // bubble itself (used once placed, so the lifted copy is exactly the bubble's size)
        val anchor = remember(message.id) { arrayOf(Rect.Zero, Rect.Zero) }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .drawBehind {
                    if (rowTint.alpha > 0f) drawRect(rowTint)
                    val g = glow.value
                    if (g > 0f) {
                        // Edge to edge, soft at top and bottom with accent highlight strip
                        val wash = glowColor.copy(alpha = (if (isDark) 0.32f else 0.22f) * g)
                        drawRect(
                            Brush.verticalGradient(
                                0f to Color.Transparent,
                                0.16f to wash,
                                0.84f to wash,
                                1f to Color.Transparent
                            )
                        )
                        val barWidth = 4.dp.toPx()
                        val barX = if (isFromMe) size.width - barWidth else 0f
                        drawRoundRect(
                            color = glowColor.copy(alpha = 0.85f * g),
                            topLeft = Offset(barX, size.height * 0.12f),
                            size = Size(barWidth, size.height * 0.76f),
                            cornerRadius = CornerRadius(2.dp.toPx(), 2.dp.toPx())
                        )
                    }
                }
                .graphicsLayer {
                    if (isFocused) {
                        alpha = 0f
                    }
                    val b = bump.value
                    val bumpScale = if (b > 0f) 1f + 0.035f * b else 1f
                    // Pulled on past the newest message, it bunches up toward the bottom (see AccordionPull)
                    val bunch = accordion?.shiftFor(message.id) ?: 0f
                    if (bunch != 0f) translationY = -bunch
                    scaleX = bumpScale
                    scaleY = bumpScale
                }
                .then(
                    if (landsWithSpring) {
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
                        modifier = Modifier.padding(start = 16.dp)
                    )
                }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = bubbleStartPadding, end = bubbleEndPadding)
                        .padding(vertical = if (isPrivate) 6.dp else 0.dp)
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
                        partnerHearing = partnerHearing,
                        gallerySize = gallerySize,
                        onPlayAudio = { actions.playAudio(message) },
                        onSeekAudio = { progress -> actions.seekAudio(message, progress) },
                        onImageClick = { url -> actions.openMedia(message, url, null) },
                        onImageClickWithList = { url, allUrls -> actions.openMedia(message, url, allUrls) },
                        // Not in private chat: its messages run the full width, so a sideways swipe on
                        // them is left for moving to Love & Us / Settings (Reply is in the long-press menu)
                        onSwipeToReply = if (isPrivate) null else ({ actions.reply(message) }),
                        onLongClick = { actions.longPress(message, anchor[1].takeIf { it.width > 0f } ?: anchor[0]) },
                        onReactionClick = { emoji -> actions.react(message, emoji) },
                        onOpenTheaterVideo = actions.openTheaterVideo,
                        voicePlaybackSpeed = voicePlaybackSpeed,
                        onToggleVoiceSpeed = actions.toggleVoiceSpeed,
                        isHighlighted = isHighlighted,
                        onReplyQuoteClick = { replyId -> actions.jumpToReply(message.id, replyId) },
                        isPrivateMode = isPrivate,
                        senderPhotoUrl = senderPhotoUrl,
                        isLastReadMessage = isLastReadMessage,
                        partnerPhotoUrl = partnerPhotoUrl,
                        partnerName = partnerName,
                        isFreshArrival = isFreshArrival,
                        onBubblePositioned = { bounds ->
                            anchor[1] = bounds
                        }
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
                .padding(horizontal = 16.dp, vertical = 8.dp),
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
                imageVector = Icons.Rounded.PushPin,
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
                    imageVector = Icons.Rounded.Close,
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
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = if (isDark) MaterialTheme.colorScheme.surfaceVariant else if (isFromMe) Color(0xFFFFF8E1) else Color(0xFFFFEBEE)),
        border = BorderStroke(1.dp, if (isDark) MaterialTheme.colorScheme.outlineVariant else if (isFromMe) Color(0xFFFFE082) else Color(0xFFFFCDD2))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = if (isFromMe) Icons.Rounded.HourglassTop else Icons.Rounded.WarningAmber,
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
                CherishOutlinedButton(
                    onClick = onCancel,
                    shape = RoundedCornerShape(12.dp),
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
                    CherishButton(
                        onClick = onAccept,
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.height(36.dp)
                    ) {
                        Text("Accept & Clear Chat", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                    CherishOutlinedButton(
                        onClick = onDecline,
                        shape = RoundedCornerShape(12.dp),
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
                    imageVector = if (checked) Icons.Rounded.CheckBox else Icons.Rounded.CheckBoxOutlineBlank,
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ChatPartnerHeaderContent(
    presenceFlow: StateFlow<PartnerPresenceState>,
    fallbackPartnerName: String,
    myTimeZone: String?,
    isDark: Boolean,
    onAvatarClick: () -> Unit,
    onMoodClick: () -> Unit,
    // "listening to your voice note…" while they hear one of mine (see ListeningNow)
    hearingStatus: String? = null,
    // A long press on their photo: the kiss pad (see KissTouch)
    onAvatarLongPress: () -> Unit = {}
) {
    val presence by presenceFlow.collectAsState()
    val isPartnerOnline = presence.isOnline
    val partnerHasCheckAfter = presence.checkAfterActive
    val partnerCheckAfterTarget = presence.checkAfterTimeMillis ?: 0L

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

    val partnerName = presence.displayName.ifBlank { fallbackPartnerName }

    val screenWidthDp = androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp
    val fontScale = androidx.compose.ui.platform.LocalDensity.current.fontScale
    val isCompact = screenWidthDp < 370 || (screenWidthDp < 390 && fontScale > 1.05f)
    val isMedium = screenWidthDp in 370..405 && fontScale <= 1.05f

    val avatarSize = when {
        isCompact -> 44.dp
        isMedium -> 48.dp
        else -> 52.dp
    }
    val avatarSpacer = when {
        isCompact -> 6.dp
        isMedium -> 8.dp
        else -> 9.dp
    }
    val nameFontSize = when {
        isCompact -> 15.sp
        isMedium -> 16.sp
        else -> 17.sp
    }
    val nameLineHeight = when {
        isCompact -> 17.5.sp
        isMedium -> 18.5.sp
        else -> 19.5.sp
    }
    val statusFontSize = when {
        isCompact -> 11.sp
        isMedium -> 11.5.sp
        else -> 12.sp
    }
    val statusLineHeight = when {
        isCompact -> 13.sp
        isMedium -> 13.5.sp
        else -> 14.sp
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(start = 0.dp, end = 2.dp, top = 2.dp, bottom = 2.dp)
    ) {
        Box(
            modifier = Modifier
                .combinedClickable(
                    interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                    indication = null,
                    onLongClick = onAvatarLongPress,
                    onClick = onAvatarClick
                )
        ) {
            AvatarView(
                photoUrl = presence.photoUrl,
                name = partnerName,
                size = avatarSize,
                isOnline = isPartnerOnline,
                isTyping = presence.isTyping || presence.isRecordingAudio,
                showOnlineBadge = !partnerHasCheckAfter
            )
        }
        Spacer(modifier = Modifier.width(avatarSpacer))
        Column(
            modifier = Modifier.weight(1f, fill = false),
            verticalArrangement = Arrangement.Center
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = if (partnerName.isNotBlank() && !partnerName.contains("💕")) "$partnerName 💕" else partnerName,
                    fontSize = nameFontSize,
                    lineHeight = nameLineHeight,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = if (isDark) Color.White else Slate900
                )

                val partnerMood = presence.mood?.takeIf {
                    val moodAt = presence.moodAt
                    moodAt == 0L || System.currentTimeMillis() - moodAt < MOOD_FRESH_MS
                }
                if (!partnerMood.isNullOrBlank()) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.75f),
                        modifier = Modifier
                            .weight(1f, fill = false)
                            .clickable { onMoodClick() }
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
                presence.isRecordingAudio -> "recording audio..."
                presence.isTyping -> "typing..."
                hearingStatus != null -> hearingStatus
                isPartnerOnline -> "Online"
                else -> {
                    val lastSeen = presence.lastSeen
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
            val effPartnerTz = presence.timeZone?.takeIf { it.isNotBlank() }
            var chatTimeTicker by remember { mutableLongStateOf(System.currentTimeMillis()) }
            LaunchedEffect(effPartnerTz) {
                while (effPartnerTz != null) {
                    chatTimeTicker = System.currentTimeMillis()
                    kotlinx.coroutines.delay(60_000L - chatTimeTicker % 60_000L)
                }
            }
            val partnerLocalTimeText = remember(effPartnerTz, myTimeZone, chatTimeTicker) {
                com.example.ui.home.formatPartnerLocalTimeIfDifferent(
                    partnerTimeZoneId = effPartnerTz,
                    myTimeZoneId = myTimeZone,
                    now = chatTimeTicker,
                    label = "Her time"
                )
            }
            val fullStatusText = if (partnerLocalTimeText != null) "$statusText · $partnerLocalTimeText" else statusText
            Text(
                text = fullStatusText,
                fontSize = statusFontSize,
                lineHeight = statusLineHeight,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = if (partnerHasCheckAfter) {
                    Blue500
                } else if (presence.isRecordingAudio || presence.isTyping || hearingStatus != null || isPartnerOnline) {
                    OnlineGreen
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                fontWeight = FontWeight.Normal
            )
        }
    }
}

@Composable
private fun ChatPartnerTypingBubbleWrapper(
    presenceFlow: StateFlow<PartnerPresenceState>,
    chatExperienceMode: com.example.ui.chat.ChatExperienceMode,
    isSearching: Boolean,
    isAtBottom: Boolean,
    partnerPhotoUrl: String?,
    fallbackPartnerName: String,
    isDark: Boolean
) {
    val presence by presenceFlow.collectAsState()
    if (chatExperienceMode == com.example.ui.chat.ChatExperienceMode.PRIVATE) {
        val showTyping = (presence.isTyping || presence.isRecordingAudio) && !isSearching && isAtBottom
        AnimatedVisibility(
            visible = showTyping,
            enter = fadeIn(tween(140)) + expandVertically(spring(dampingRatio = 0.8f, stiffness = 500f)),
            exit = fadeOut(tween(140)) + shrinkVertically(tween(160))
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .padding(start = 20.dp, top = 4.dp, bottom = 8.dp)
                    .testTag("chatgpt_typing_indicator")
            ) {
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF10A37F)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.AutoAwesome,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(13.dp)
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                val infiniteTransition = rememberInfiniteTransition(label = "pulse_chatgpt")
                val dotAlpha by infiniteTransition.animateFloat(
                    initialValue = 0.25f,
                    targetValue = 1f,
                    animationSpec = infiniteRepeatable(tween(550), RepeatMode.Reverse),
                    label = "dot_alpha"
                )
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(if (isDark) Neutral200.copy(alpha = dotAlpha) else InkBlack.copy(alpha = dotAlpha))
                )
            }
        }
    } else {
        PartnerTypingBubble(
            visible = (presence.isTyping || presence.isRecordingAudio) &&
                chatExperienceMode != com.example.ui.chat.ChatExperienceMode.PRIVATE &&
                !isSearching && isAtBottom,
            isRecording = presence.isRecordingAudio,
            partnerPhotoUrl = presence.photoUrl ?: partnerPhotoUrl,
            partnerName = presence.displayName.ifBlank { fallbackPartnerName },
            isDark = isDark,
            typingPace = presence.typingPace
        )
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
    isDark: Boolean,
    typingPace: Int = 1
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
                .padding(start = 24.dp, end = 16.dp, top = 2.dp, bottom = 8.dp)
                .testTag("partner_typing_bubble")
        ) {
            AvatarView(
                photoUrl = partnerPhotoUrl,
                name = partnerName,
                size = 26.dp,
                showOnlineBadge = false
            )
            Spacer(modifier = Modifier.width(6.dp))
            // The same liquid glass as my partner's bubbles
            val typingShape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomEnd = 16.dp, bottomStart = 4.dp)
            val glass = rememberBubbleGlass(isFromMe = false, isDark = isDark)
            Surface(
                shape = typingShape,
                color = Color.Transparent,
                shadowElevation = 0.8.dp
            ) {
                Box(
                    modifier = Modifier
                        .background(glass.tint, typingShape)
                        .background(glass.sheen, typingShape)
                        .border(BorderStroke(0.8.dp, glass.rim), typingShape)
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    if (isRecording) {
                        RecordingBars(color = HeartRed)
                    } else {
                        BouncingDots(color = if (isDark) darkTone(Slate300) else Slate500, pace = typingPace)
                    }
                }
            }
        }
    }
}

@Composable
private fun RecordingBars(color: Color) {
    val isAppForeground = com.example.ui.components.rememberAppIsResumed()
    val transition = if (isAppForeground) rememberInfiniteTransition(label = "recording_bars") else null
    Row(
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Rounded.Mic,
            contentDescription = "Recording a voice note",
            tint = color,
            modifier = Modifier.size(14.dp)
        )
        repeat(4) { bar ->
            val level: Float = if (transition != null) {
                val anim by transition.animateFloat(
                    initialValue = 0.3f,
                    targetValue = 1f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(380, easing = FastOutSlowInEasing),
                        repeatMode = RepeatMode.Reverse,
                        initialStartOffset = StartOffset(bar * 90)
                    ),
                    label = "recording_bar_$bar"
                )
                anim
            } else {
                0.5f
            }
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .height(14.dp)
                    .graphicsLayer { scaleY = level }
                    .background(color.copy(alpha = 0.85f), RoundedCornerShape(4.dp))
            )
        }
    }
}

/**
 * Typing dots with rhythm: they bounce faster while the partner types fast, slower while they type
 * slowly, and drift gently while they pause (pace from their phone: 0 paused, 1 slow, 2 fast). The
 * speed eases between paces without restarting, and the dots move in the draw phase only.
 */
@Composable
fun BouncingDots(color: Color = RoseGoldPrimary, pace: Int = 1) {
    val speed by animateFloatAsState(
        targetValue = when (pace) {
            2 -> 1.9f
            1 -> 1.15f
            else -> 0.5f
        },
        animationSpec = tween(600),
        label = "typing_dots_speed"
    )
    val height by animateFloatAsState(if (pace == 0) 3.5f else 6.5f, tween(600), label = "typing_dots_height")
    val phase = remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) {
        var last = 0L
        while (true) {
            withFrameNanos { now ->
                if (last != 0L) phase.floatValue = (phase.floatValue + (now - last) / 1_000_000_000f * speed) % 1_000f
                last = now
            }
        }
    }
    Row(
        horizontalArrangement = Arrangement.spacedBy(3.5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(3) { dot ->
            Box(
                modifier = Modifier
                    .graphicsLayer {
                        val wave = kotlin.math.sin(phase.floatValue * 2f * Math.PI.toFloat() - dot * 0.9f)
                        translationY = if (wave > 0f) -wave * height.dp.toPx() else 0f
                        // Like a little rubber ball: it squashes as it lands and stretches as it leaves
                        // the ground (scaled from its base, so it stays on the line)
                        val squash = if (wave < 0f) -wave * 0.24f else 0f
                        val stretch = if (wave > 0f) wave * (1f - wave) * 0.45f else 0f
                        transformOrigin = TransformOrigin(0.5f, 1f)
                        scaleY = 1f - squash + stretch
                        scaleX = 1f + squash * 0.8f - stretch * 0.5f
                    }
                    .size(6.dp)
                    .background(color, CircleShape)
            )
        }
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
    if (isPrivateMode) {
        return
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        val lineColor = if (isPrivateMode) {
            if (isDark) darkTone(Graphite700) else Gray200
        } else {
            if (isDark) darkTone(Gray700).copy(alpha = 0.5f) else Slate300.copy(alpha = 0.7f)
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
            modifier = Modifier.padding(horizontal = 12.dp)
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
            if (isDark) darkTone(Graphite850).copy(alpha = 0.95f) else Neutral200
        } else {
            if (isDark) darkSurface(NavyCard).copy(alpha = 0.92f) else MistBlue.copy(alpha = 0.95f)
        },
        border = BorderStroke(
            0.8.dp,
            if (isPrivateMode) {
                if (isDark) darkTone(Gray700).copy(alpha = 0.6f) else Gray200
            } else {
                if (isDark) darkTone(Color(0xFF475569)).copy(alpha = 0.4f) else Gray200
            }
        ),
        shadowElevation = shadowElevation,
        modifier = modifier
    ) {
        Text(
            text = dateText,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            color = if (isPrivateMode) {
                if (isDark) darkTone(Gray400) else Gray600
            } else {
                if (isDark) darkTone(Slate400) else Color(0xFF475569)
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
            // A little room under it, so the first message doesn't sit right against it
            .padding(start = 20.dp, end = 20.dp, top = 2.dp, bottom = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = if (isPrivateMode) {
                if (isDark) darkTone(Graphite850).copy(alpha = 0.90f) else Neutral200
            } else {
                if (isDark) darkTone(NavyCard).copy(alpha = 0.92f) else MistBlue
            },
            border = BorderStroke(
                1.dp,
                if (isPrivateMode) {
                    if (isDark) darkTone(Color(0xFF33353C)) else Gray200
                } else {
                    if (isDark) darkTone(Blue600).copy(alpha = 0.35f) else Blue300.copy(alpha = 0.55f)
                }
            )
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.Lock,
                    contentDescription = null,
                    tint = if (isPrivateMode) {
                        if (isDark) darkTone(Gray400) else Gray600
                    } else {
                        if (isDark) darkTone(Color(0xFF60A5FA)) else Blue600
                    },
                    modifier = Modifier.size(13.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Strictly 2-Person Private Channel",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (isPrivateMode) {
                        if (isDark) darkTone(Color(0xFFD1D5DB)) else Gray700
                    } else {
                        if (isDark) darkTone(Blue300) else Color(0xFF1E40AF)
                    }
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "•",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isPrivateMode) {
                        if (isDark) darkTone(Color(0xFF9CA3AF)) else Gray500
                    } else {
                        if (isDark) darkTone(Blue300) else Color(0xFF1E40AF)
                    }
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "End-to-End Encrypted",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (isPrivateMode) {
                        if (isDark) darkTone(Color(0xFFD1D5DB)) else Gray700
                    } else {
                        if (isDark) darkTone(Blue300) else Color(0xFF1E40AF)
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
            .padding(horizontal = 20.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (isPrivateMode) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = if (isDark) darkTone(Graphite850).copy(alpha = 0.85f) else Neutral200,
                border = BorderStroke(1.dp, if (isDark) darkTone(Graphite700) else Gray200),
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
                            imageVector = Icons.Rounded.Lock,
                            contentDescription = null,
                            tint = if (isDark) darkTone(Gray400) else Gray600,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Strictly 2-Person Private Channel",
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            color = if (isDark) darkTone(Gray200) else Gray800
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Messages, voice notes, and media are strictly between you and $partnerName. End-to-end encrypted.",
                        fontSize = 11.sp,
                        color = if (isDark) darkTone(Gray400) else Gray500,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        lineHeight = 16.sp
                    )
                }
            }
        } else {
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = if (isDark) {
                    Color(0xFF131824).copy(alpha = 0.88f)
                } else {
                    Color.White.copy(alpha = 0.92f)
                },
                border = BorderStroke(
                    1.dp,
                    if (isDark) darkTone(Color(0xFF2B3954)).copy(alpha = 0.6f) else Gray200
                ),
                shadowElevation = 2.dp,
                modifier = Modifier.fillMaxWidth(0.95f)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 12.dp),
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
                                    imageVector = Icons.Rounded.Lock,
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
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = "Everything shared in this conversation is strictly between you and $partnerName. Zero third parties can ever join or view your messages.",
                        fontSize = 11.sp,
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
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = "🔒 End-to-End Private Space",
                                fontSize = 10.sp,
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

@Composable
private fun ChatBottomComposerBar(
    composerState: ComposerState,
    viewModel: ChatViewModel,
    uiState: ChatUiState,
    isRecordingVoice: Boolean,
    partnerName: String,
    myUser: com.example.data.model.User?,
    onScheduleText: () -> Unit,
    triggerCameraSnap: () -> Unit,
    triggerCameraVideo: (() -> Unit)? = null,
    onPickAttachment: () -> Unit,
    onPickVideo: () -> Unit = {},
    onPickDocument: () -> Unit = {},
    onRecordVideoNote: () -> Unit,
    onMicPermissionRequired: () -> Unit,
    onSendHaptic: () -> Unit,
    onUnreadJumpDone: () -> Unit,
    onInputFocused: () -> Unit = {},
    focusRequester: FocusRequester = remember { FocusRequester() },
    modifier: Modifier = Modifier
) {
    val recordingDurationSec by viewModel.voiceRecorderHelper.recordingDurationSec.collectAsState()
    val recordingAmplitudes by viewModel.voiceRecorderHelper.amplitudes.collectAsState()
    // A GIF from the keyboard's own GIF search (Gboard, Samsung Keyboard) goes straight into
    // the chat, as a GIF; not in private chat, which is text only
    AcceptKeyboardMedia(
        enabled = uiState.chatExperienceMode != com.example.ui.chat.ChatExperienceMode.PRIVATE,
        onMedia = { uri -> viewModel.sendMediaFile(uri, MessageType.IMAGE) }
    ) {
        MessageComposer(
            text = composerState.text,
            onTextChanged = {
                composerState.text = it
                viewModel.onTypingChanged(it.isNotBlank())
            },
            onSendText = {
                val textToSend = composerState.text
                if (textToSend.isNotBlank()) {
                    onSendHaptic()
                    onUnreadJumpDone()
                    onInputFocused()
                    viewModel.sendTextMessage(textToSend)
                    composerState.text = ""
                    viewModel.onTypingChanged(false)
                }
            },
            replyingTo = uiState.replyingToMessage,
            isRecordingVoice = isRecordingVoice,
            recordingDurationSec = recordingDurationSec,
            recordingAmplitudes = recordingAmplitudes,
            onStartVoiceRecord = onMicPermissionRequired,
            onStopAndSendVoiceRecord = { viewModel.stopAndSendVoiceRecording() },
            onCancelVoiceRecord = { viewModel.cancelVoiceRecording() },
            onTakePhoto = triggerCameraSnap,
            onRecordVideo = triggerCameraVideo,
            onPickAttachment = onPickAttachment,
            onPickVideo = onPickVideo,
            onPickDocument = onPickDocument,
            onScheduleText = onScheduleText,
            // Not behind the stealth curtain (it looks like notes there)
            onSendHug = if (uiState.isStealthCurtainActive) null else { level ->
                onSendHaptic()
                onUnreadJumpDone()
                viewModel.sendHugSqueeze(level)
            },
            onRecordVideoNote = onRecordVideoNote,
            // GIPHY's GIFs: not in private chat (text only) or behind the stealth curtain
            onSendGif = if (
                uiState.isStealthCurtainActive ||
                uiState.chatExperienceMode == com.example.ui.chat.ChatExperienceMode.PRIVATE
            ) null else { gif ->
                onSendHaptic()
                onUnreadJumpDone()
                viewModel.sendGif(gif)
            },
            myPhotoUrl = myUser?.photoUrl,
            myName = myUser?.displayName ?: "Me",
            placeholder = when {
                uiState.isStealthCurtainActive -> "Add a note..."
                uiState.chatExperienceMode == com.example.ui.chat.ChatExperienceMode.PRIVATE -> "Reply to ChatGPT"
                else -> "Message $partnerName... ❤️"
            },
            isPrivateMode = (uiState.chatExperienceMode == com.example.ui.chat.ChatExperienceMode.PRIVATE),
            onInputFocused = onInputFocused,
            focusRequester = focusRequester,
            modifier = modifier
        )
    }
}









/** The round buttons and pill at the top of private chat (ChatGPT's dark grey, with a fine edge). */
private val PrivateChromeDark = Neutral800
private val PrivateChromeLight = Neutral200
private val PrivateChromeEdgeDark = Neutral700
private val PrivateChromeEdgeLight = Neutral300

@Composable
private fun PrivateRoundButton(
    isDark: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val isAmoled = com.example.ui.theme.LocalAmoledBlack.current
    val bg = if (isDark) (if (isAmoled) Neutral900 else PrivateChromeDark) else PrivateChromeLight
    val edge = if (isDark) (if (isAmoled) Neutral800 else PrivateChromeEdgeDark) else PrivateChromeEdgeLight
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .pressScaleTarget()
            .clip(CircleShape)
            .background(bg)
            .border(1.dp, edge, CircleShape)
            .springClickable(onClick = onClick)
    ) { content() }
}

/** ChatGPT's "new chat" mark: a rounded square, open at the top right, with a pen across it. */
@Composable
private fun NewChatIcon(tint: Color) {
    androidx.compose.foundation.Canvas(modifier = Modifier.size(22.dp)) {
        val w = size.width
        val h = size.height
        val frame = androidx.compose.ui.graphics.Path().apply {
            moveTo(w * 0.48f, h * 0.16f)
            lineTo(w * 0.30f, h * 0.16f)
            quadraticTo(w * 0.14f, h * 0.16f, w * 0.14f, h * 0.32f)
            lineTo(w * 0.14f, h * 0.70f)
            quadraticTo(w * 0.14f, h * 0.86f, w * 0.30f, h * 0.86f)
            lineTo(w * 0.68f, h * 0.86f)
            quadraticTo(w * 0.84f, h * 0.86f, w * 0.84f, h * 0.70f)
            lineTo(w * 0.84f, h * 0.52f)
        }
        drawPath(
            path = frame,
            color = tint,
            style = androidx.compose.ui.graphics.drawscope.Stroke(
                width = 1.9.dp.toPx(),
                cap = androidx.compose.ui.graphics.StrokeCap.Round,
                join = androidx.compose.ui.graphics.StrokeJoin.Round
            )
        )
        // The pen, from the middle up to the open corner
        drawLine(
            color = tint,
            start = androidx.compose.ui.geometry.Offset(w * 0.42f, h * 0.58f),
            end = androidx.compose.ui.geometry.Offset(w * 0.86f, h * 0.14f),
            strokeWidth = 2.3.dp.toPx(),
            cap = androidx.compose.ui.graphics.StrokeCap.Round
        )
    }
}

/** ChatGPT's menu mark: two lines, the lower one shorter. */
@Composable
private fun TwoLineMenuIcon(tint: Color) {
    androidx.compose.foundation.Canvas(modifier = Modifier.size(20.dp)) {
        val stroke = 2.2.dp.toPx()
        drawLine(
            color = tint,
            start = androidx.compose.ui.geometry.Offset(size.width * 0.12f, size.height * 0.36f),
            end = androidx.compose.ui.geometry.Offset(size.width * 0.88f, size.height * 0.36f),
            strokeWidth = stroke,
            cap = androidx.compose.ui.graphics.StrokeCap.Round
        )
        drawLine(
            color = tint,
            start = androidx.compose.ui.geometry.Offset(size.width * 0.12f, size.height * 0.66f),
            end = androidx.compose.ui.geometry.Offset(size.width * 0.58f, size.height * 0.66f),
            strokeWidth = stroke,
            cap = androidx.compose.ui.graphics.StrokeCap.Round
        )
    }
}

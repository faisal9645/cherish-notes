package com.example.ui.gallery

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntOffset
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.BorderStroke
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.BookmarkBorder
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.CalendarToday
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.CallEnd
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.automirrored.rounded.Chat
import androidx.compose.material.icons.rounded.CheckCircleOutline
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.EventAvailable
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.OpenInBrowser
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.Phone
import androidx.compose.material.icons.rounded.PhoneInTalk
import androidx.compose.material.icons.rounded.Place
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.UnfoldMore
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material.icons.rounded.ZoomIn
import androidx.compose.material.icons.rounded.ZoomOut
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.model.Message
import com.example.data.model.MessageType
import com.example.ui.chat.ChatViewModel
import com.example.ui.chat.FullScreenMediaViewer
import com.example.ui.chat.mediaTimesOf
import com.example.ui.chat.MediaTileAnchor
import com.example.ui.chat.mediaTileAnchor
import com.example.ui.chat.openableMedia
import androidx.compose.ui.graphics.graphicsLayer
import com.example.ui.components.SelectionCheckBadge
import com.example.ui.components.SkeletonListRows
import com.example.ui.components.SkeletonPhotoGrid
import com.example.ui.components.WaveformView
import com.example.ui.theme.AppGradientStart
import com.example.ui.theme.RoseGoldPrimary
import com.example.ui.theme.appHorizontalGradient
import java.text.SimpleDateFormat
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.positionChanged
import java.util.*
import kotlin.math.roundToInt
import kotlinx.coroutines.launch
import com.example.ui.theme.Amber500
import com.example.ui.theme.Blue600
import com.example.ui.theme.Emerald500
import com.example.ui.theme.Gray400
import com.example.ui.theme.Pink500
import com.example.ui.theme.Red500
import com.example.ui.theme.Slate400
import com.example.ui.components.CherishToast
import com.example.ui.components.CherishAlertDialog
import com.example.ui.components.CherishButton
import com.example.ui.components.CherishOutlinedButton
import com.example.ui.components.CherishTonalButton
import com.example.ui.components.CherishFilterChip
import com.example.ui.components.pressScaleTarget
import com.example.ui.components.springClickable
import androidx.compose.material.icons.automirrored.rounded.PhoneMissed
import com.example.ui.theme.steadyDigits

data class GalleryMediaItem(
    val id: String,
    val messageId: String,
    val mediaUrl: String,
    val isVideo: Boolean,
    val durationSeconds: Int,
    val timestamp: Long,
    val originalMessage: Message,
    // Small preview for the grid; the viewer opens mediaUrl
    val previewUrl: String = mediaUrl
) {
    /** An animated GIF: it keeps its .gif name when it's sent (see MediaRepository.compressAndPrepareImage) */
    val isGif: Boolean
        get() = !isVideo && mediaUrl.substringBefore('?').endsWith(".gif", ignoreCase = true)
}

data class GalleryLinkItem(
    val messageId: String,
    val url: String,
    val host: String,
    val messageText: String,
    val timestamp: Long,
    val senderName: String,
    val isFromMe: Boolean
)

private val URL_REGEX = Regex(
    "(https?://[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}(?:/[^\\s]*)?|www\\.[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}(?:/[^\\s]*)?)",
    RegexOption.IGNORE_CASE
)

private fun formatGalleryDateTime(timestamp: Long): String {
    val sdf = SimpleDateFormat("MMM d, yyyy • h:mm a", Locale.getDefault())
    return sdf.format(Date(timestamp))
}

private fun formatVoiceDuration(currentSec: Int, totalSec: Int): String {
    val curM = currentSec / 60
    val curS = currentSec % 60
    val totM = totalSec / 60
    val totS = totalSec % 60
    return String.format(Locale.getDefault(), "%d:%02d / %d:%02d", curM, curS, totM, totS)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SharedGalleryScreen(
    chatViewModel: ChatViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToMessage: (messageId: String) -> Unit = {}
) {
    val context = LocalContext.current
    val chatState by chatViewModel.uiState.collectAsState()
    val tabs = listOf("Media", "Voice Notes", "Links", "Starred", "Calls")
    com.example.ui.chat.ReportChatMediaActivity("gallery")
    val pagerState = rememberPagerState(initialPage = 0, pageCount = { tabs.size })
    val coroutineScope = rememberCoroutineScope()
    var selectedMediaUrl by remember { mutableStateOf<String?>(null) }
    var selectedMessageIdForViewer by remember { mutableStateOf<String?>(null) }
    var selectedDateMillis by remember { mutableStateOf<Long?>(null) }
    var showDatePicker by remember { mutableStateOf(false) }
    var messageToDelete by remember { mutableStateOf<Message?>(null) }
    var mediaItemToDelete by remember { mutableStateOf<GalleryMediaItem?>(null) }

    // Telegram-style Gallery Zoom: initially ALWAYS 3 columns
    var gridColumnCount by remember { mutableIntStateOf(3) }
    // Multi-Select and Delete
    var isSelectionMode by remember { mutableStateOf(false) }
    var selectedItemIds by remember { mutableStateOf(setOf<String>()) }
    var showMultiDeleteConfirmDialog by remember { mutableStateOf(false) }

    val voicePlayerHelper = chatViewModel.voicePlayerHelper
    val currentTrackId by voicePlayerHelper.currentTrackId.collectAsState()
    val isVoicePlaying by voicePlayerHelper.isPlaying.collectAsState()
    com.example.ui.chat.ReportChatMediaActivity("audio", active = isVoicePlaying)
    val voiceProgress by voicePlayerHelper.playbackProgress.collectAsState()
    val currentPositionSec by voicePlayerHelper.currentPositionSec.collectAsState()
    val playbackSpeed by voicePlayerHelper.playbackSpeed.collectAsState()

    val currentUserId = chatState.currentUser?.id ?: "user_me"

    val app = LocalContext.current.applicationContext as? com.example.CherishApplication
    val isDisguiseActive by (app?.securityPreferences?.isDisguiseActive ?: kotlinx.coroutines.flow.MutableStateFlow(false)).collectAsState()

    LaunchedEffect(isDisguiseActive) {
        if (isDisguiseActive) {
            selectedMediaUrl = null
            selectedMessageIdForViewer = null
            isSelectionMode = false
            selectedItemIds = emptySet()
        }
    }

    val allGalleryItems by chatViewModel.galleryMediaMessages.collectAsState()
    val isGalleryLoading by chatViewModel.isGalleryLoading.collectAsState()
    val isAllGalleryRecovered by (app?.securityPreferences?.isAllGalleryRecovered?.collectAsState() ?: remember { mutableStateOf(false) })
    val startOfToday = remember { com.example.CherishApplication.instance.chatRepository.getStartOfToday() }

    var playedIds by remember { mutableStateOf(GalleryPlayedManager.getPlaybackState(context)) }

    DisposableEffect(context) {
        val prefs = context.getSharedPreferences("cherish_gallery_played_prefs", Context.MODE_PRIVATE)
        val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
            playedIds = GalleryPlayedManager.getPlaybackState(context)
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        playedIds = GalleryPlayedManager.getPlaybackState(context)
        onDispose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }

    val markItemPlayed: (String) -> Unit = remember(context) {
        { id ->
            if (id.isNotBlank()) {
                GalleryPlayedManager.setPlayed(context, id, true)
                playedIds = GalleryPlayedManager.getPlaybackState(context)
            }
        }
    }

    val toggleItemPlayed: (String, Boolean) -> Unit = remember(context) {
        { id, isFromMe ->
            if (id.isNotBlank()) {
                val newState = GalleryPlayedManager.togglePlayed(context, id, isFromMe)
                playedIds = GalleryPlayedManager.getPlaybackState(context)
                if (newState) {
                    CherishToast.show("Marked played ✓")
                } else {
                    CherishToast.show("Marked not yet")
                }
            }
        }
    }

    LaunchedEffect(isAllGalleryRecovered) {
        if (isAllGalleryRecovered) {
            chatViewModel.loadAllGalleryMedia()
        }
    }

    LaunchedEffect(selectedDateMillis) {
        if (selectedDateMillis != null) {
            chatViewModel.loadAllGalleryMedia()
        }
    }

    LaunchedEffect(pagerState.currentPage) {
        if (pagerState.currentPage == 4) {
            chatViewModel.loadAllGalleryMedia()
        }
    }

    val sourceMessages = remember(chatState.messages, allGalleryItems) {
        (chatState.messages + allGalleryItems).distinctBy { it.id }
    }

    val filteredMessages = remember(sourceMessages, selectedDateMillis, isAllGalleryRecovered, chatState.logicalDay) {
        var list = sourceMessages.filter { !it.isDeleted }

        // Filter out any messages containing "today start 6 am" or similar variations
        list = list.filter { msg ->
            val lower = msg.text.lowercase().trim()
            val hasToday = lower.contains("today")
            val hasStart = lower.contains("start") || lower.contains("satrt")
            val has6Am = lower.contains("6 am") || lower.contains("6am") || lower.contains("6:00")
            !(hasToday && (hasStart || has6Am))
        }

        val shiftToLogicalDay = { ts: Long ->
            val cal = Calendar.getInstance().apply { timeInMillis = ts }
            if (cal.get(Calendar.HOUR_OF_DAY) < 6) {
                cal.add(Calendar.DAY_OF_YEAR, -1)
            }
            Pair(cal.get(Calendar.YEAR), cal.get(Calendar.DAY_OF_YEAR))
        }

        // Calendar Date Search rule (searches full selected day: from its 6 AM to the next morning's).
        // The picked date itself, not the day its midnight belongs to (the day before)
        if (selectedDateMillis != null) {
            val picked = Calendar.getInstance().apply { timeInMillis = selectedDateMillis!! }
            val selLogicalDay = Pair(picked.get(Calendar.YEAR), picked.get(Calendar.DAY_OF_YEAR))
            list = list.filter { msg ->
                shiftToLogicalDay(msg.timestamp) == selLogicalDay
            }
        } else if (!isAllGalleryRecovered) {
            // When day starts, show only today's gallery unless Recover All is activated
            val todayLogicalDay = shiftToLogicalDay(System.currentTimeMillis())
            list = list.filter { msg ->
                shiftToLogicalDay(msg.timestamp) == todayLogicalDay
            }
        }

        list
    }

    // Extract links from filtered messages
    val allExtractedLinks = remember(filteredMessages, currentUserId) {
        val list = mutableListOf<GalleryLinkItem>()
        filteredMessages.forEach { msg ->
            if (msg.text.isNotBlank()) {
                val matches = URL_REGEX.findAll(msg.text)
                for (m in matches) {
                    val raw = m.value
                    val full = if (raw.startsWith("www.", ignoreCase = true)) "https://$raw" else raw
                    val host = try {
                        Uri.parse(full).host?.removePrefix("www.") ?: "website"
                    } catch (_: Exception) {
                        "website"
                    }
                    list.add(
                        GalleryLinkItem(
                            messageId = msg.id,
                            url = full,
                            host = host,
                            messageText = msg.text,
                            timestamp = msg.timestamp,
                            senderName = msg.senderName.ifBlank { if (msg.senderId == currentUserId) "You" else "Partner" },
                            isFromMe = (msg.senderId == currentUserId)
                        )
                    )
                }
            }
        }
        // The same URL twice in one message would produce duplicate LazyColumn keys
        list.distinctBy { it.messageId to it.url }.sortedByDescending { it.timestamp }
    }

    // Every media URL from single and multi-photo messages is represented
    val galleryMediaItems = remember(filteredMessages) {
        val list = mutableListOf<GalleryMediaItem>()
        filteredMessages.forEach { msg ->
            if (msg.getTypedType() == MessageType.IMAGE) {
                val urls = msg.getAllMediaUrls().ifEmpty { listOfNotNull(msg.mediaUrl) }
                urls.forEach { url ->
                    if (url.isNotBlank()) {
                        list.add(
                            GalleryMediaItem(
                                // Keyed by URL (not position) so a tile keeps its identity when other
                                // photos of the same message are deleted
                                id = "${msg.id}_${url.hashCode()}",
                                messageId = msg.id,
                                mediaUrl = url,
                                previewUrl = msg.thumbnailFor(url),
                                isVideo = false,
                                durationSeconds = 0,
                                timestamp = msg.timestamp,
                                originalMessage = msg
                            )
                        )
                    }
                }
            } else if (msg.getTypedType() == MessageType.VIDEO || msg.isVideoNote || msg.isCircularVideoNote()) {
                val vUrl = msg.mediaUrl ?: msg.mediaUrls.firstOrNull() ?: ""
                if (vUrl.isNotBlank()) {
                    list.add(
                        GalleryMediaItem(
                            id = msg.id,
                            messageId = msg.id,
                            mediaUrl = vUrl,
                            // Its poster (about 10 KB) when it has one; the video itself is never loaded for a tile
                            previewUrl = msg.thumbnailFor(vUrl),
                            isVideo = true,
                            durationSeconds = msg.durationSeconds,
                            timestamp = msg.timestamp,
                            originalMessage = msg
                        )
                    )
                }
            }
        }
        list.sortedByDescending { it.timestamp }
    }

    // The gallery's filters: what kind (all, photos, videos, video notes; the Media tab) and who sent
    // it (anyone, me, partner; every tab)
    var mediaKind by rememberSaveable { mutableIntStateOf(MEDIA_ALL) }
    var galleryFrom by rememberSaveable { mutableIntStateOf(FROM_ANYONE) }
    val partnerFirstName = remember(chatState.partnerUser?.displayName) {
        chatState.partnerUser?.displayName?.trim()?.substringBefore(' ')?.ifBlank { null } ?: "Partner"
    }
    // A tab with nothing from the one picked in its filter
    val nothingFromText = if (galleryFrom == FROM_ME) "Nothing from you here yet" else "Nothing from $partnerFirstName here yet"
    val extractedLinks = remember(allExtractedLinks, galleryFrom) { allExtractedLinks.sentBy(galleryFrom) { it.isFromMe } }
    // How many of each kind there are, so kinds with none aren't offered
    val mediaKindCounts = remember(galleryMediaItems) {
        IntArray(MEDIA_KIND_LABELS.size).also { counts ->
            counts[MEDIA_ALL] = galleryMediaItems.count { !it.isGif }
            galleryMediaItems.forEach { counts[mediaKindOf(it)]++ }
        }
    }
    val shownMediaItems = remember(galleryMediaItems, mediaKind, galleryFrom, currentUserId) {
        if (mediaKind == MEDIA_ALL && galleryFrom == FROM_ANYONE) {
            galleryMediaItems.filter { !it.isGif }
        } else {
            galleryMediaItems.filter { item ->
                val fromMe = item.originalMessage.senderId == currentUserId
                val matchesKind = if (mediaKind == MEDIA_ALL) !item.isGif else mediaKindOf(item) == mediaKind
                matchesKind &&
                    when (galleryFrom) {
                        FROM_ME -> fromMe
                        FROM_PARTNER -> !fromMe
                        else -> true
                    }
            }
        }
    }

    val mediaMessages = remember(filteredMessages) {
        filteredMessages.filter {
            !it.mediaUrl.isNullOrBlank() || it.mediaUrls.isNotEmpty()
        }.sortedByDescending { it.timestamp }
    }

    val allVoiceMessages = remember(filteredMessages) {
        filteredMessages.filter { it.getTypedType() == MessageType.AUDIO }
            .sortedByDescending { it.timestamp }
    }
    val voiceMessages = remember(allVoiceMessages, galleryFrom, currentUserId) {
        allVoiceMessages.sentBy(galleryFrom) { it.senderId == currentUserId }
    }

    // All calls: recordings, completed, missed, declined, cancelled
    val allCallHistoryItems = remember(filteredMessages) {
        filteredMessages.filter { msg ->
            msg.getTypedType() == MessageType.CALL_RECORDING ||
            !msg.callId.isNullOrBlank() ||
            msg.text.contains("Voice call", ignoreCase = true) ||
            msg.text.contains("Call declined", ignoreCase = true) ||
            msg.text.contains("Cancelled call", ignoreCase = true) ||
            msg.text.contains("Missed voice call", ignoreCase = true) ||
            msg.text.contains("Missed call", ignoreCase = true)
        }.distinctBy { it.callId ?: it.id }.sortedByDescending { it.timestamp }
    }
    // Calls I placed, or my partner did
    val callHistoryItems = remember(allCallHistoryItems, galleryFrom, currentUserId) {
        allCallHistoryItems.sentBy(galleryFrom) { it.senderId == currentUserId }
    }

    val allStarredMessages = remember(filteredMessages) {
        filteredMessages.filter { it.isStarred }
            .sortedByDescending { it.timestamp }
    }
    val starredMessages = remember(allStarredMessages, galleryFrom, currentUserId) {
        allStarredMessages.sentBy(galleryFrom) { it.senderId == currentUserId }
    }

    // Multi-select works per tab: one key per photo/video tile, otherwise one key per message
    val selectableIdsOnPage = remember(pagerState.currentPage, shownMediaItems, voiceMessages, callHistoryItems, extractedLinks, starredMessages) {
        when (pagerState.currentPage) {
            0 -> shownMediaItems.map { it.id }
            1 -> voiceMessages.map { it.id }
            2 -> extractedLinks.map { it.messageId }.distinct()
            3 -> starredMessages.map { it.id }
            else -> callHistoryItems.map { it.id }
        }
    }
    val allOnPageSelected = selectableIdsOnPage.isNotEmpty() && selectedItemIds.containsAll(selectableIdsOnPage)

    fun exitSelection() {
        isSelectionMode = false
        selectedItemIds = emptySet()
    }

    fun startSelection(id: String) {
        isSelectionMode = true
        selectedItemIds = selectedItemIds + id
    }

    fun toggleSelection(id: String) {
        selectedItemIds = if (id in selectedItemIds) selectedItemIds - id else selectedItemIds + id
    }

    fun deleteSelectedItems() {
        if (pagerState.currentPage == 0) {
            val chosen = galleryMediaItems.filter { it.id in selectedItemIds }
            chatViewModel.deleteGalleryMedia(chosen.map { it.originalMessage }, chosen.map { it.mediaUrl }.toSet())
        } else {
            chatViewModel.deleteMessages(selectedItemIds)
        }
        exitSelection()
    }

    // A selection belongs to the tab it was made on
    LaunchedEffect(pagerState.currentPage) { exitSelection() }

    // Drop selections whose items disappeared (deleted on the other phone, filter changed)
    LaunchedEffect(selectableIdsOnPage) {
        val available = selectableIdsOnPage.toSet()
        if (!available.containsAll(selectedItemIds)) {
            selectedItemIds = selectedItemIds.intersect(available)
        }
    }

    BackHandler(enabled = isSelectionMode) { exitSelection() }

    Scaffold(
        topBar = {
            AnimatedContent(
                targetState = isSelectionMode,
                transitionSpec = { fadeIn(tween(180)) togetherWith fadeOut(tween(120)) },
                label = "gallery_top_bar"
            ) { selecting ->
            if (selecting) {
                TopAppBar(
                    title = {
                        Text(
                            "${selectedItemIds.size} Selected",
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    },
                    navigationIcon = {
                        IconButton(
                            onClick = { exitSelection() },
                            modifier = Modifier.testTag("gallery_selection_close")
                        ) {
                            Icon(Icons.Rounded.Close, contentDescription = "Cancel Selection")
                        }
                    },
                    actions = {
                        TextButton(
                            onClick = {
                                selectedItemIds = if (allOnPageSelected) emptySet() else selectableIdsOnPage.toSet()
                            },
                            enabled = selectableIdsOnPage.isNotEmpty(),
                            modifier = Modifier.testTag("gallery_select_all")
                        ) {
                            Text(
                                if (allOnPageSelected) "Deselect All" else "Select All",
                                fontWeight = FontWeight.Bold,
                                color = RoseGoldPrimary
                            )
                        }
                        IconButton(
                            onClick = {
                                if (selectedItemIds.isNotEmpty()) {
                                    showMultiDeleteConfirmDialog = true
                                }
                            },
                            enabled = selectedItemIds.isNotEmpty(),
                            modifier = Modifier.testTag("gallery_delete_selected")
                        ) {
                            Icon(
                                Icons.Rounded.Delete,
                                contentDescription = "Delete Selected",
                                tint = if (selectedItemIds.isNotEmpty()) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
                )
            } else {
                TopAppBar(
                    title = {
                        Column {
                            Text(
                                "Shared Gallery",
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            val countText = when (pagerState.currentPage) {
                                0 -> "${shownMediaItems.size} ${MEDIA_KIND_COUNT_WORDS[mediaKind]}"
                                1 -> "${voiceMessages.size} voice notes"
                                2 -> "${extractedLinks.size} shared links"
                                3 -> "${starredMessages.size} starred items"
                                else -> "${callHistoryItems.size} calls"
                            }
                            val subtitle = if (selectedDateMillis != null) {
                                val sdf = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())
                                "$countText • ${sdf.format(Date(selectedDateMillis!!))}"
                            } else {
                                countText
                            }
                            Text(
                                text = subtitle,
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    },
                    navigationIcon = {
                        IconButton(
                            onClick = onNavigateBack,
                            modifier = Modifier.testTag("gallery_back_button")
                        ) {
                            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                        }
                    },
                    actions = {
                        // Quick Telegram-style thumbnail zoom controls for media tab
                        if (pagerState.currentPage == 0 && shownMediaItems.isNotEmpty()) {
                            IconButton(
                                onClick = {
                                    if (gridColumnCount < 6) gridColumnCount++
                                }
                            ) {
                                Icon(
                                    Icons.Rounded.ZoomOut,
                                    contentDescription = "Zoom Out (Smaller Thumbnails)",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            IconButton(
                                onClick = {
                                    if (gridColumnCount > 2) gridColumnCount--
                                }
                            ) {
                                Icon(
                                    Icons.Rounded.ZoomIn,
                                    contentDescription = "Zoom In (Bigger Thumbnails)",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        if (selectableIdsOnPage.isNotEmpty()) {
                            IconButton(
                                onClick = { isSelectionMode = true },
                                modifier = Modifier.testTag("gallery_select_button")
                            ) {
                                Icon(
                                    Icons.Rounded.CheckCircleOutline,
                                    contentDescription = "Select Items",
                                    tint = RoseGoldPrimary
                                )
                            }
                        }

                        IconButton(
                            onClick = { showDatePicker = true },
                            modifier = Modifier.testTag("gallery_calendar_search_button")
                        ) {
                            Icon(
                                imageVector = if (selectedDateMillis != null) Icons.Rounded.EventAvailable else Icons.Rounded.CalendarMonth,
                                contentDescription = "Search by Date",
                                tint = if (selectedDateMillis != null) RoseGoldPrimary else MaterialTheme.colorScheme.onSurface
                            )
                        }

                        if (selectedDateMillis != null) {
                            IconButton(
                                onClick = { selectedDateMillis = null },
                                modifier = Modifier.testTag("gallery_clear_date_button")
                            ) {
                                Icon(
                                    Icons.Rounded.Close,
                                    contentDescription = "Clear Date Filter",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
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
            // Calendar Date Filter Chip
            if (selectedDateMillis != null) {
                Surface(
                    color = RoseGoldPrimary.copy(alpha = 0.12f),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Rounded.CalendarToday,
                                contentDescription = null,
                                tint = RoseGoldPrimary,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            val sdf = remember { SimpleDateFormat("MMMM d, yyyy", Locale.getDefault()) }
                            Text(
                                text = "Date: ${sdf.format(Date(selectedDateMillis!!))}",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = RoseGoldPrimary
                            )
                        }
                        IconButton(
                            onClick = { selectedDateMillis = null },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Close,
                                contentDescription = "Clear Date Filter",
                                tint = RoseGoldPrimary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }

            // (Recover All moved to settings tab per user request)

            // Primary Tab Row
            PrimaryTabRow(
                selectedTabIndex = pagerState.currentPage,
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = RoseGoldPrimary
            ) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = pagerState.currentPage == index,
                        onClick = {
                            coroutineScope.launch {
                                pagerState.animateScrollToPage(index)
                            }
                        },
                        modifier = Modifier.heightIn(min = 48.dp)
                    ) {
                        Text(
                            title,
                            fontWeight = if (pagerState.currentPage == index) FontWeight.Bold else FontWeight.Normal,
                            fontSize = 13.sp,
                            textAlign = TextAlign.Center,
                            maxLines = 2,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp)
                        )
                    }
                }
            }

            // Whole-history gallery load (Recover All / date search) in progress
            if (isGalleryLoading) {
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(2.dp)
                        .testTag("gallery_loading_indicator"),
                    color = RoseGoldPrimary,
                    trackColor = RoseGoldPrimary.copy(alpha = 0.15f)
                )
            }

            HorizontalPager(
                state = pagerState,
                userScrollEnabled = !isSelectionMode,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) { page ->
                when (page) {
                    // TAB 0: Media (Photos & Videos)
                    0 -> Column(modifier = Modifier.fillMaxSize()) {
                        // What kind, and whose
                        if (galleryMediaItems.isNotEmpty()) {
                            MediaFilterRow(
                                kind = mediaKind,
                                onKind = { mediaKind = it },
                                kindCounts = mediaKindCounts,
                                from = galleryFrom,
                                onFrom = { galleryFrom = it },
                                partnerName = partnerFirstName
                            )
                        }
                        // The placeholders melt into the content instead of it popping in (each side of the
                        // crossfade keeps showing what it was)
                        val shownTab = when { shownMediaItems.isEmpty() && isGalleryLoading -> 0; shownMediaItems.isEmpty() -> 1; else -> 2 }
                        Crossfade(targetState = shownTab, animationSpec = tween(280), label = "gallery_tab", modifier = Modifier.weight(1f)) { phase ->
                        if (phase != 2) {
                            val what = MEDIA_KIND_EMPTY_WORDS[mediaKind] + when (galleryFrom) {
                                FROM_ME -> " from you"
                                FROM_PARTNER -> " from $partnerFirstName"
                                else -> ""
                            }
                            val emptyText = if (selectedDateMillis != null) {
                                val sdf = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())
                                "No $what found on ${sdf.format(Date(selectedDateMillis!!))} 📅"
                            } else if (galleryMediaItems.isEmpty()) {
                                "No shared photos or videos yet 💕"
                            } else {
                                "No $what yet 💕"
                            }
                            EmptyGalleryNotice(emptyText, isLoading = phase == 0, grid = true)
                        } else {
                            val configuration = LocalConfiguration.current
                            val screenWidthDp = configuration.screenWidthDp.toFloat() - 8f
                            val minColumns = 2
                            val maxColumns = 6
                            val minThumbnailSize = screenWidthDp / maxColumns
                            val maxThumbnailSize = screenWidthDp / minColumns

                            var activeThumbnailSize by remember { mutableFloatStateOf(screenWidthDp / gridColumnCount) }

                            LaunchedEffect(gridColumnCount) {
                                activeThumbnailSize = screenWidthDp / gridColumnCount
                            }

                            // The grid fades in as it opens instead of popping in
                            val gridEntrance = remember { androidx.compose.animation.core.Animatable(0f) }
                            LaunchedEffect(Unit) { gridEntrance.animateTo(1f, tween(320)) }
                            val gridState = rememberLazyGridState()
                            val onThisDayItems = remember(shownMediaItems, selectedDateMillis, isSelectionMode) {
                                if (selectedDateMillis == null && !isSelectionMode) galleryOnThisDay(shownMediaItems) else emptyList()
                            }
                            val monthGroups = remember(shownMediaItems) { galleryByMonth(shownMediaItems) }
                            // Each grid entry's date, in grid order (the card and month headers too), for the scrollbar
                            val gridDates = remember(onThisDayItems, monthGroups) {
                                buildList<Long> {
                                    if (onThisDayItems.isNotEmpty()) add(monthGroups.first().second.first().timestamp)
                                    monthGroups.forEach { (_, monthItems) ->
                                        add(monthItems.first().timestamp)
                                        monthItems.forEach { add(it.timestamp) }
                                    }
                                }
                            }
                            Box(modifier = Modifier.fillMaxSize()) {
                            LazyVerticalGrid(
                                columns = GridCells.Fixed(gridColumnCount),
                                state = gridState,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .graphicsLayer { alpha = gridEntrance.value }
                                    .padding(start = 4.dp, end = 4.dp, bottom = 4.dp)
                                    .pointerInput(Unit) {
                                        awaitEachGesture {
                                            awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                                            var zooming = false
                                            var currentZoom = 1f
                                            val startSize = activeThumbnailSize

                                            do {
                                                val event = awaitPointerEvent(pass = PointerEventPass.Initial)
                                                val zoomChange = event.calculateZoom()
                                                if (zoomChange != 1f && event.changes.size > 1) {
                                                    zooming = true
                                                    currentZoom *= zoomChange
                                                    activeThumbnailSize = (startSize * currentZoom).coerceIn(minThumbnailSize, maxThumbnailSize)
                                                    
                                                    val derivedColumns = (screenWidthDp / activeThumbnailSize).roundToInt().coerceIn(minColumns, maxColumns)
                                                    if (derivedColumns != gridColumnCount) {
                                                        gridColumnCount = derivedColumns
                                                    }
                                                    event.changes.forEach { if (it.positionChanged()) it.consume() }
                                                }
                                            } while (event.changes.any { it.pressed })

                                            if (zooming) {
                                                activeThumbnailSize = screenWidthDp / gridColumnCount
                                            }
                                        }
                                    },
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                // A card of photos from this date in earlier years, above the newest month
                                if (onThisDayItems.isNotEmpty()) {
                                    item(key = "on_this_day", span = { GridItemSpan(maxLineSpan) }) {
                                        OnThisDayCard(
                                            items = onThisDayItems,
                                            posterFor = { chatViewModel.videoPoster(it.originalMessage, it.mediaUrl) },
                                            onOpen = { picked ->
                                                selectedMediaUrl = picked.mediaUrl
                                                selectedMessageIdForViewer = picked.messageId
                                            }
                                        )
                                    }
                                }
                                // Month by month, newest first: an "October 2026" header over each month's
                                // photos, which stays at the top while you're in that month
                                monthGroups.forEach { (month, monthItems) ->
                                    stickyHeader(key = "month_$month") {
                                        GalleryMonthHeader(month = month, count = monthItems.size)
                                    }
                                    items(monthItems, key = { it.id }) { item ->
                                    val isSelected = selectedItemIds.contains(item.id)
                                    val tileAnchor = remember(item.id) { MediaTileAnchor() }

                                    Box(
                                        modifier = Modifier
                                            // New photos fade in and the rest glide into place
                                            .animateItem(fadeInSpec = tween(260), fadeOutSpec = tween(180))
                                            .aspectRatio(1f)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f))
                                            .mediaTileAnchor(tileAnchor)
                                            .then(
                                                if (isSelected) Modifier.border(2.5.dp, RoseGoldPrimary, RoundedCornerShape(8.dp))
                                                else Modifier
                                            )
                                            .pointerInput(item.id, isSelectionMode) {
                                                // Selection is read when the gesture fires; a value captured at
                                                // composition would go stale after the first toggle
                                                detectTapGestures(
                                                    onLongPress = {
                                                        if (isSelectionMode) toggleSelection(item.id) else startSelection(item.id)
                                                    },
                                                    onTap = {
                                                        if (isSelectionMode) {
                                                            toggleSelection(item.id)
                                                        } else {
                                                            tileAnchor.open()
                                                            markItemPlayed(item.id)
                                                            markItemPlayed(item.messageId)
                                                            selectedMediaUrl = item.mediaUrl
                                                            selectedMessageIdForViewer = item.messageId
                                                        }
                                                    }
                                                )
                                            }
                                    ) {
                                        val isVideoItem = item.isVideo
                                        // Zoomed out to the smallest tiles: just the photos (Delete and Show in chat
                                        // are a long-press or the photo's viewer away), and a small play mark on videos
                                        val isCompactTile = gridColumnCount >= COMPACT_TILE_COLUMNS

                                        // Small tiles show the ~10 KB thumbnail; two big columns, the full photo.
                                        // In the GIFs filter, each GIF plays (like Telegram's GIFs tab)
                                        GalleryTileImage(
                                            item = item,
                                            full = gridColumnCount <= 2 || (item.isGif && mediaKind == MEDIA_GIFS),
                                            posterFor = { chatViewModel.videoPoster(it.originalMessage, it.mediaUrl) },
                                            modifier = Modifier.fillMaxSize()
                                        )
                                        if (item.isGif && !isCompactTile) {
                                            Surface(
                                                shape = RoundedCornerShape(6.dp),
                                                color = Color.Black.copy(alpha = 0.6f),
                                                modifier = Modifier
                                                    .align(Alignment.BottomStart)
                                                    .padding(4.dp)
                                            ) {
                                                Text(
                                                    text = "GIF",
                                                    color = Color.White,
                                                    fontSize = 9.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                                                )
                                            }
                                        }

                                        if (isVideoItem) {

                                            // Center Play Indicator
                                            Box(
                                                modifier = Modifier
                                                    .align(Alignment.Center)
                                                    .size(if (isCompactTile) 22.dp else 36.dp)
                                                    .background(Color.Black.copy(alpha = 0.55f), CircleShape),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Rounded.PlayArrow,
                                                    contentDescription = "Video Note",
                                                    tint = Color.White,
                                                    modifier = Modifier.size(if (isCompactTile) 14.dp else 22.dp)
                                                )
                                            }

                                            // Video Note Pill
                                            if (!isCompactTile) Surface(
                                                shape = RoundedCornerShape(8.dp),
                                                color = Color.Black.copy(alpha = 0.65f),
                                                modifier = Modifier
                                                    .align(Alignment.BottomStart)
                                                    .padding(4.dp)
                                            ) {
                                                Row(
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Rounded.Videocam,
                                                        contentDescription = null,
                                                        tint = RoseGoldPrimary,
                                                        modifier = Modifier.size(11.dp)
                                                    )
                                                    Spacer(modifier = Modifier.width(3.dp))
                                                    Text(
                                                        text = if (item.originalMessage.isCircularVideoNote() || item.originalMessage.isVideoNote) "Video Note" else "Video",
                                                        color = Color.White,
                                                        fontSize = 9.sp,
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                }
                                            }
                                        }

                                        // Selection Mode Checkbox Badge
                                        if (isSelectionMode) {
                                            SelectionCheckBadge(
                                                isSelected = isSelected,
                                                overMedia = true,
                                                modifier = Modifier
                                                    .align(Alignment.TopStart)
                                                    .padding(4.dp)
                                            )
                                        }

                                        // Overlay "Show in chat" button on bottom edge (when not in multi-selection mode)
                                        if (!isSelectionMode && !isCompactTile) {
                                            Box(
                                                modifier = Modifier
                                                    .align(Alignment.BottomEnd)
                                                    .padding(4.dp)
                                                    .pressScaleTarget()
                                                    .clip(CircleShape)
                                                    .background(Color.Black.copy(alpha = 0.55f))
                                                    .springClickable { onNavigateToMessage(item.messageId) }
                                                    .padding(6.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.AutoMirrored.Rounded.Chat,
                                                    contentDescription = "Chat",
                                                    tint = Color.White,
                                                    modifier = Modifier.size(14.dp)
                                                )
                                            }

                                            // Overlay "Delete" button on top edge
                                            Box(
                                                modifier = Modifier
                                                    .align(Alignment.TopEnd)
                                                    .padding(4.dp)
                                                    .size(32.dp)
                                                    .pressScaleTarget()
                                                    .clip(CircleShape)
                                                    .background(Color.Black.copy(alpha = 0.65f))
                                                    .springClickable { mediaItemToDelete = item }
                                                    .testTag("gallery_item_delete_${item.id}"),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Rounded.Delete,
                                                    contentDescription = "Delete",
                                                    tint = Gray400,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                                }
                            }
                            GalleryScrollbar(
                                position = rememberGalleryScrollPosition(gridState),
                                dateAt = { gridDates.getOrNull(it) },
                                modifier = Modifier.fillMaxSize().padding(vertical = 6.dp)
                            )
                            }
                        }
                        }
                    }

                    // TAB 1: Voice Notes (Playable with audio player and Show in Chat)
                    1 -> Column(modifier = Modifier.fillMaxSize()) {
                        // Whose: from me or from my partner (as on the Media tab)
                        if (allVoiceMessages.isNotEmpty()) {
                            FromFilterRow(from = galleryFrom, onFrom = { galleryFrom = it }, partnerName = partnerFirstName)
                        }
                        // The placeholders melt into the content instead of it popping in (each side of the
                        // crossfade keeps showing what it was)
                        val shownTab = when { voiceMessages.isEmpty() && isGalleryLoading -> 0; voiceMessages.isEmpty() -> 1; else -> 2 }
                        Crossfade(targetState = shownTab, animationSpec = tween(280), label = "gallery_tab", modifier = Modifier.weight(1f)) { phase ->
                        if (phase != 2) {
                            val emptyText = if (galleryFrom != FROM_ANYONE && allVoiceMessages.isNotEmpty()) nothingFromText else if (selectedDateMillis != null) {
                                val sdf = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())
                                "No voice notes found on ${sdf.format(Date(selectedDateMillis!!))} 📅"
                            } else {
                                "No shared voice notes yet 🎙️"
                            }
                            EmptyGalleryNotice(emptyText, isLoading = phase == 0)
                        } else {
                            val voiceSections = remember(voiceMessages) { galleryMonthSections(voiceMessages) { it.timestamp } }
                            GalleryLazyColumn(
                                dateAt = { voiceSections.dateAt(it) },
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                monthSections(voiceSections, key = { it.id }) { msg ->
                                    val isThisPlaying = (currentTrackId == msg.id && isVoicePlaying)
                                    val isThisActive = (currentTrackId == msg.id)
                                    val duration = if (msg.durationSeconds > 0) msg.durationSeconds
                                        else (com.example.audio.VoicePlayerHelper.getTrackDuration(msg.id).takeIf { it > 0 }
                                            ?: Regex("""\((\d+)\s*s\)""").find(msg.text)?.groupValues?.get(1)?.toIntOrNull()
                                            ?: (msg.waveform.size / 10).takeIf { it > 0 }
                                            ?: 5)

                                    Surface(
                                        shape = RoundedCornerShape(16.dp),
                                        color = MaterialTheme.colorScheme.surface,
                                        shadowElevation = 1.5.dp,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .gallerySelectable(
                                                key = msg.id,
                                                isSelectionMode = isSelectionMode,
                                                isSelected = msg.id in selectedItemIds,
                                                accentColor = RoseGoldPrimary,
                                                onToggle = { toggleSelection(msg.id) },
                                                onStartSelection = { startSelection(msg.id) }
                                            )
                                            .testTag("gallery_voice_${msg.id}")
                                    ) {
                                        Column(modifier = Modifier.padding(16.dp)) {
                                            // Top info: Sender + Date & Time
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                if (isSelectionMode) {
                                                    SelectionCheckBadge(isSelected = msg.id in selectedItemIds)
                                                    Spacer(modifier = Modifier.width(10.dp))
                                                }
                                                Box(
                                                    modifier = Modifier
                                                        .size(34.dp)
                                                        .clip(CircleShape)
                                                        .background(RoseGoldPrimary.copy(alpha = 0.12f)),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Rounded.Mic,
                                                        contentDescription = null,
                                                        tint = RoseGoldPrimary,
                                                        modifier = Modifier.size(18.dp)
                                                    )
                                                }
                                                Spacer(modifier = Modifier.width(10.dp))
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text(
                                                        text = if (msg.senderId == currentUserId) "Voice note by You" else "Voice note by ${msg.senderName}",
                                                        fontWeight = FontWeight.SemiBold,
                                                        fontSize = 13.sp,
                                                        color = MaterialTheme.colorScheme.onSurface,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                    Spacer(modifier = Modifier.height(2.dp))
                                                    Text(
                                                        text = formatGalleryDateTime(msg.timestamp),
                                                        fontSize = 11.sp,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                        maxLines = 1
                                                    )
                                                }

                                                // Show in chat button
                                                CherishOutlinedButton(
                                                    onClick = { onNavigateToMessage(msg.id) },
                                                    shape = RoundedCornerShape(20.dp),
                                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                                    modifier = Modifier.height(32.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.AutoMirrored.Rounded.Chat,
                                                        contentDescription = null,
                                                        tint = RoseGoldPrimary,
                                                        modifier = Modifier.size(13.dp)
                                                    )
                                                    Spacer(modifier = Modifier.width(4.dp))
                                                    Text("Chat", fontSize = 11.sp, color = RoseGoldPrimary)
                                                }
                                                
                                                Spacer(modifier = Modifier.width(8.dp))
                                                
                                                // Delete button
                                                IconButton(
                                                    onClick = { messageToDelete = msg },
                                                    modifier = Modifier.size(32.dp)
                                                ) {
                                                    Icon(Icons.Rounded.DeleteOutline, contentDescription = "Delete", tint = Gray400, modifier = Modifier.size(18.dp))
                                                }
                                            }

                                            Spacer(modifier = Modifier.height(10.dp))

                                            // Audio Player Bar
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clip(RoundedCornerShape(12.dp))
                                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                // Play / Pause Button
                                                IconButton(
                                                    onClick = {
                                                        markItemPlayed(msg.id)
                                                        msg.mediaUrl?.let { url ->
                                                            voicePlayerHelper.playAudio(msg.id, url, durationHintSec = duration)
                                                        }
                                                    },
                                                    modifier = Modifier
                                                        .size(38.dp)
                                                        .clip(CircleShape)
                                                        .background(RoseGoldPrimary)
                                                ) {
                                                    Icon(
                                                        imageVector = if (isThisPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                                                        contentDescription = if (isThisPlaying) "Pause" else "Play",
                                                        tint = Color.White,
                                                        modifier = Modifier.size(20.dp)
                                                    )
                                                }

                                                Spacer(modifier = Modifier.width(10.dp))

                                                // Progress & scrubber waveform
                                                Column(modifier = Modifier.weight(1f)) {
                                                    WaveformView(
                                                        amplitudes = msg.waveform.map { it.toFloat() },
                                                        // Read while drawing, so playback doesn't recompose the gallery
                                                        progressProvider = { if (currentTrackId == msg.id) voiceProgress else 0f },
                                                        isPlaying = isThisPlaying,
                                                        isRecording = false,
                                                        activeColor = RoseGoldPrimary,
                                                        inactiveColor = RoseGoldPrimary.copy(alpha = 0.35f),
                                                        onSeek = { progress ->
                                                            if (isThisActive) {
                                                                voicePlayerHelper.seekTo(progress)
                                                            }
                                                        },
                                                        height = 24.dp,
                                                        modifier = Modifier.fillMaxWidth()
                                                    )
                                                    Spacer(modifier = Modifier.height(4.dp))
                                                    Row(
                                                        modifier = Modifier.fillMaxWidth(),
                                                        horizontalArrangement = Arrangement.SpaceBetween
                                                    ) {
                                                        Text(
                                                            text = if (isThisActive && isThisPlaying) {
                                                                formatVoiceDuration(currentPositionSec, duration)
                                                            } else {
                                                                "${duration}s audio"
                                                            },
                                                            fontSize = 11.sp,
                                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                            style = LocalTextStyle.current.steadyDigits
                                                        )
                                                        if (isThisActive) {
                                                            Text(
                                                                text = "${playbackSpeed}x",
                                                                fontSize = 11.sp,
                                                                fontWeight = FontWeight.Bold,
                                                                color = RoseGoldPrimary,
                                                                modifier = Modifier.clickable {
                                                                    voicePlayerHelper.togglePlaybackSpeed()
                                                                }
                                                            )
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        }
                    }

                    // TAB 2: Links (WhatsApp Style with dates, times, and actions)
                    2 -> Column(modifier = Modifier.fillMaxSize()) {
                        // Whose: from me or from my partner (as on the Media tab)
                        if (allExtractedLinks.isNotEmpty()) {
                            FromFilterRow(from = galleryFrom, onFrom = { galleryFrom = it }, partnerName = partnerFirstName)
                        }
                        // The placeholders melt into the content instead of it popping in (each side of the
                        // crossfade keeps showing what it was)
                        val shownTab = when { extractedLinks.isEmpty() && isGalleryLoading -> 0; extractedLinks.isEmpty() -> 1; else -> 2 }
                        Crossfade(targetState = shownTab, animationSpec = tween(280), label = "gallery_tab", modifier = Modifier.weight(1f)) { phase ->
                        if (phase != 2) {
                            val emptyText = if (galleryFrom != FROM_ANYONE && allExtractedLinks.isNotEmpty()) nothingFromText else if (selectedDateMillis != null) {
                                val sdf = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())
                                "No links found on ${sdf.format(Date(selectedDateMillis!!))} 📅"
                            } else {
                                "No shared links yet 🔗\nAny links shared in your secret chat will automatically appear here."
                            }
                            EmptyGalleryNotice(emptyText, isLoading = phase == 0)
                        } else {
                            val linkSections = remember(extractedLinks) { galleryMonthSections(extractedLinks) { it.timestamp } }
                            GalleryLazyColumn(
                                dateAt = { linkSections.dateAt(it) },
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                monthSections(linkSections, key = { "${it.messageId}_${it.url}" }) { item ->
                                    val ytVideoId = remember(item.url) {
                                        com.example.ui.chat.YouTubeHelper.extractVideoId(item.url)
                                    }
                                    var ytTitle by remember(item.url, ytVideoId) {
                                        mutableStateOf(ytVideoId?.let { com.example.ui.chat.YouTubeHelper.getCachedMetadata(context, it)?.title })
                                    }
                                    LaunchedEffect(item.url, ytVideoId) {
                                        if (ytVideoId != null && ytTitle.isNullOrBlank()) {
                                            val meta = com.example.ui.chat.YouTubeHelper.fetchMetadata(context, ytVideoId)
                                            if (meta?.title?.isNotBlank() == true) {
                                                ytTitle = meta.title
                                            }
                                        }
                                    }
                                    val cleanText = remember(item.messageText, item.url) {
                                        item.messageText.replace(item.url, "").trim()
                                    }

                                    Surface(
                                        shape = RoundedCornerShape(16.dp),
                                        color = MaterialTheme.colorScheme.surface,
                                        shadowElevation = 1.5.dp,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .gallerySelectable(
                                                key = item.messageId,
                                                isSelectionMode = isSelectionMode,
                                                isSelected = item.messageId in selectedItemIds,
                                                accentColor = RoseGoldPrimary,
                                                onToggle = { toggleSelection(item.messageId) },
                                                onStartSelection = { startSelection(item.messageId) }
                                            )
                                            .testTag("gallery_link_${item.messageId}")
                                    ) {
                                        Column(modifier = Modifier.padding(16.dp)) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                verticalAlignment = Alignment.Top
                                            ) {
                                                if (isSelectionMode) {
                                                    SelectionCheckBadge(
                                                        isSelected = item.messageId in selectedItemIds,
                                                        modifier = Modifier.padding(top = 8.dp)
                                                    )
                                                    Spacer(modifier = Modifier.width(10.dp))
                                                }
                                                // Domain icon / thumbnail badge (WhatsApp & Telegram style)
                                                Box(
                                                    modifier = Modifier
                                                        .size(46.dp)
                                                        .clip(RoundedCornerShape(12.dp))
                                                        .background((if (ytVideoId != null) Color(0xFFFF0000) else getDomainBadgeColor(item.host)).copy(alpha = 0.12f)),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    if (ytVideoId != null) {
                                                        AsyncImage(
                                                            model = com.example.ui.chat.YouTubeHelper.getThumbnailUrl(ytVideoId),
                                                            contentDescription = "YouTube Thumbnail",
                                                            contentScale = ContentScale.Crop,
                                                            modifier = Modifier.fillMaxSize()
                                                        )
                                                    } else {
                                                        Icon(
                                                            imageVector = getDomainIcon(item.host),
                                                            contentDescription = null,
                                                            tint = getDomainBadgeColor(item.host),
                                                            modifier = Modifier.size(22.dp)
                                                        )
                                                    }
                                                }

                                                Spacer(modifier = Modifier.width(12.dp))

                                                Column(modifier = Modifier.weight(1f)) {
                                                    if (!ytTitle.isNullOrBlank()) {
                                                        Text(
                                                            text = ytTitle!!,
                                                            fontWeight = FontWeight.Bold,
                                                            fontSize = 14.sp,
                                                            color = MaterialTheme.colorScheme.onSurface,
                                                            maxLines = 2,
                                                            overflow = TextOverflow.Ellipsis
                                                        )
                                                        Spacer(modifier = Modifier.height(2.dp))
                                                        Text(
                                                            text = "YouTube • ${item.host.lowercase()}",
                                                            fontSize = 11.sp,
                                                            fontWeight = FontWeight.SemiBold,
                                                            color = Color(0xFFFF0000)
                                                        )
                                                    } else {
                                                        // Domain Host in Bold
                                                        Text(
                                                            text = item.host.lowercase(),
                                                            fontWeight = FontWeight.Bold,
                                                            fontSize = 14.sp,
                                                            color = MaterialTheme.colorScheme.onSurface
                                                        )
                                                    }
                                                    Spacer(modifier = Modifier.height(2.dp))
                                                    // Full URL in accent color (clickable)
                                                    Text(
                                                        text = item.url,
                                                        fontSize = 12.sp,
                                                        color = Blue600,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis,
                                                        modifier = Modifier.clickable {
                                                            val playKey = ytVideoId ?: item.url
                                                            markItemPlayed(playKey)
                                                            markItemPlayed(item.messageId)
                                                            try {
                                                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(item.url)).apply {
                                                                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                                                }
                                                                context.startActivity(intent)
                                                            } catch (e: Exception) {
                                                                CherishToast.show("Cannot open link")
                                                            }
                                                        }
                                                    )

                                                    val isSuppressed = cleanText.lowercase().let { it.contains("today") && (it.contains("start") || it.contains("satrt") || it.contains("6 am") || it.contains("6am") || it.contains("6:00")) }
                                                    if (cleanText.isNotBlank() && !isSuppressed) {
                                                        Spacer(modifier = Modifier.height(4.dp))
                                                        Text(
                                                            text = cleanText,
                                                            fontSize = 12.sp,
                                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                            maxLines = 2,
                                                            overflow = TextOverflow.Ellipsis
                                                        )
                                                    }

                                                    Spacer(modifier = Modifier.height(6.dp))

                                                    // Meta info: Date & Time + Sender and Played Status Badge
                                                    Row(
                                                        modifier = Modifier.fillMaxWidth(),
                                                        horizontalArrangement = Arrangement.SpaceBetween,
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {
                                                        Row(
                                                            verticalAlignment = Alignment.CenterVertically,
                                                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                                                            modifier = Modifier.weight(1f, fill = false)
                                                        ) {
                                                            Icon(
                                                                imageVector = if (item.isFromMe) Icons.Rounded.ArrowUpward else Icons.Rounded.ArrowDownward,
                                                                contentDescription = if (item.isFromMe) "Sent" else "Received",
                                                                tint = if (item.isFromMe) RoseGoldPrimary else Slate400,
                                                                modifier = Modifier.size(11.dp)
                                                            )
                                                            Text(
                                                                text = "${formatGalleryDateTime(item.timestamp)} • ${if (item.isFromMe) "Sent by You" else "Sent by ${item.senderName}"}",
                                                                fontSize = 11.sp,
                                                                color = Slate400,
                                                                maxLines = 1,
                                                                overflow = TextOverflow.Ellipsis
                                                            )
                                                        }

                                                        Spacer(modifier = Modifier.width(8.dp))

                                                        val playKey = ytVideoId ?: item.url
                                                        val isItemPlayed = remember(playedIds, item.messageId, playKey, item.isFromMe) {
                                                            GalleryPlayedManager.isPlayed(context, playKey, item.isFromMe) ||
                                                            GalleryPlayedManager.isPlayed(context, item.messageId, item.isFromMe)
                                                        }

                                                        PlayedStatusBadge(
                                                            isPlayed = isItemPlayed,
                                                            onToggle = {
                                                                toggleItemPlayed(playKey, item.isFromMe)
                                                                GalleryPlayedManager.setPlayed(context, item.messageId,
                                                                    GalleryPlayedManager.isPlayed(context, playKey, item.isFromMe))
                                                            }
                                                        )
                                                    }
                                                }
                                            }

                                            HorizontalDivider(
                                                modifier = Modifier.padding(vertical = 12.dp),
                                                color = MaterialTheme.colorScheme.outlineVariant
                                            )

                                            // Action Buttons (Open, Copy, Show in chat, Delete)
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Row(
                                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    CherishTonalButton(
                                                        onClick = {
                                                            val playKey = ytVideoId ?: item.url
                                                            markItemPlayed(playKey)
                                                            markItemPlayed(item.messageId)
                                                            if (item.url.contains("youtube.com") || item.url.contains("youtu.be")) {
                                                                onNavigateToMessage(item.messageId)
                                                            } else {
                                                                try {
                                                                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(item.url)).apply {
                                                                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                                                    }
                                                                    context.startActivity(intent)
                                                                } catch (_: Exception) {}
                                                            }
                                                        },
                                                        shape = RoundedCornerShape(12.dp),
                                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                                        modifier = Modifier.height(30.dp)
                                                    ) {
                                                        Icon(Icons.Rounded.OpenInBrowser, contentDescription = null, modifier = Modifier.size(13.dp))
                                                        Spacer(modifier = Modifier.width(3.dp))
                                                        Text("Open", fontSize = 11.sp)
                                                    }

                                                    // Copy button
                                                    CherishOutlinedButton(
                                                        onClick = {
                                                            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                                            cm.setPrimaryClip(ClipData.newPlainText("URL", item.url))
                                                            CherishToast.show("Link copied to clipboard")
                                                        },
                                                        shape = RoundedCornerShape(12.dp),
                                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                                        modifier = Modifier.height(30.dp)
                                                    ) {
                                                        Icon(Icons.Rounded.ContentCopy, contentDescription = null, modifier = Modifier.size(13.dp))
                                                        Spacer(modifier = Modifier.width(3.dp))
                                                        Text("Copy", fontSize = 11.sp)
                                                    }
                                                }

                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                                ) {
                                                    // Show in chat button
                                                    CherishButton(
                                                        onClick = { onNavigateToMessage(item.messageId) },
                                                        shape = RoundedCornerShape(12.dp),
                                                        colors = ButtonDefaults.buttonColors(containerColor = RoseGoldPrimary),
                                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                                        modifier = Modifier.height(30.dp)
                                                    ) {
                                                        Icon(Icons.AutoMirrored.Rounded.Chat, contentDescription = null, tint = Color.White, modifier = Modifier.size(13.dp))
                                                        Spacer(modifier = Modifier.width(3.dp))
                                                        Text("Chat", fontSize = 11.sp, color = Color.White, fontWeight = FontWeight.SemiBold)
                                                    }

                                                    // Delete button
                                                    IconButton(
                                                        onClick = {
                                                            messageToDelete = filteredMessages.find { it.id == item.messageId }
                                                                ?: Message(id = item.messageId, senderId = currentUserId, text = item.url)
                                                        },
                                                        modifier = Modifier.size(30.dp)
                                                    ) {
                                                        Icon(Icons.Rounded.DeleteOutline, contentDescription = "Delete", tint = Gray400, modifier = Modifier.size(18.dp))
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        }
                    }

                    // TAB 3: Starred
                    3 -> Column(modifier = Modifier.fillMaxSize()) {
                        // Whose: from me or from my partner (as on the Media tab)
                        if (allStarredMessages.isNotEmpty()) {
                            FromFilterRow(from = galleryFrom, onFrom = { galleryFrom = it }, partnerName = partnerFirstName)
                        }
                        // The placeholders melt into the content instead of it popping in (each side of the
                        // crossfade keeps showing what it was)
                        val shownTab = when { starredMessages.isEmpty() && isGalleryLoading -> 0; starredMessages.isEmpty() -> 1; else -> 2 }
                        Crossfade(targetState = shownTab, animationSpec = tween(280), label = "gallery_tab", modifier = Modifier.weight(1f)) { phase ->
                        if (phase != 2) {
                            val emptyText = if (galleryFrom != FROM_ANYONE && allStarredMessages.isNotEmpty()) nothingFromText else if (selectedDateMillis != null) {
                                val sdf = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())
                                "No starred items found on ${sdf.format(Date(selectedDateMillis!!))} 📅"
                            } else {
                                "No starred messages or media yet ⭐"
                            }
                            EmptyGalleryNotice(emptyText, isLoading = phase == 0)
                        } else {
                            val starredSections = remember(starredMessages) { galleryMonthSections(starredMessages) { it.timestamp } }
                            GalleryLazyColumn(
                                dateAt = { starredSections.dateAt(it) },
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                monthSections(starredSections, key = { it.id }) { msg ->
                                    Surface(
                                        shape = RoundedCornerShape(16.dp),
                                        color = MaterialTheme.colorScheme.surface,
                                        shadowElevation = 1.5.dp,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .gallerySelectable(
                                                key = msg.id,
                                                isSelectionMode = isSelectionMode,
                                                isSelected = msg.id in selectedItemIds,
                                                accentColor = RoseGoldPrimary,
                                                onToggle = { toggleSelection(msg.id) },
                                                onStartSelection = { startSelection(msg.id) }
                                            )
                                            .testTag("gallery_starred_${msg.id}")
                                    ) {
                                        Column(modifier = Modifier.padding(16.dp)) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                if (isSelectionMode) {
                                                    SelectionCheckBadge(isSelected = msg.id in selectedItemIds)
                                                    Spacer(modifier = Modifier.width(10.dp))
                                                }
                                                Icon(
                                                    imageVector = Icons.Rounded.Star,
                                                    contentDescription = null,
                                                    tint = Amber500,
                                                    modifier = Modifier.size(20.dp)
                                                )
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Text(
                                                    text = formatGalleryDateTime(msg.timestamp),
                                                    fontSize = 11.sp,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    modifier = Modifier.weight(1f)
                                                )
                                                CherishOutlinedButton(
                                                    onClick = { onNavigateToMessage(msg.id) },
                                                    shape = RoundedCornerShape(20.dp),
                                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                                    modifier = Modifier.height(30.dp)
                                                ) {
                                                    Icon(Icons.AutoMirrored.Rounded.Chat, contentDescription = null, tint = RoseGoldPrimary, modifier = Modifier.size(13.dp))
                                                    Spacer(modifier = Modifier.width(4.dp))
                                                    Text("Chat", fontSize = 11.sp, color = RoseGoldPrimary)
                                                }
                                            }

                                            Spacer(modifier = Modifier.height(8.dp))

                                            val starredUrls = msg.getAllMediaUrls()
                                            if (starredUrls.isNotEmpty() && msg.getTypedType() == MessageType.IMAGE) {
                                                if (starredUrls.size == 1) {
                                                    // Wide, so the full photo (the small thumbnails are squares)
                                                    AsyncImage(
                                                        model = starredUrls[0],
                                                        contentDescription = null,
                                                        contentScale = ContentScale.Crop,
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .height(140.dp)
                                                            .clip(RoundedCornerShape(12.dp))
                                                            .openableMedia {
                                                                selectedMediaUrl = starredUrls[0]
                                                                selectedMessageIdForViewer = msg.id
                                                            }
                                                    )
                                                } else {
                                                    Row(
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .height(130.dp),
                                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                                    ) {
                                                        starredUrls.forEach { starUrl ->
                                                            AsyncImage(
                                                                model = starUrl,
                                                                contentDescription = null,
                                                                contentScale = ContentScale.Crop,
                                                                modifier = Modifier
                                                                    .weight(1f)
                                                                    .fillMaxHeight()
                                                                    .clip(RoundedCornerShape(12.dp))
                                                                    .openableMedia {
                                                                        selectedMediaUrl = starUrl
                                                                        selectedMessageIdForViewer = msg.id
                                                                    }
                                                            )
                                                        }
                                                    }
                                                }
                                                val clean = msg.text.trim()
                                                val isSuppressed = clean.lowercase().let { it.contains("today") && (it.contains("start") || it.contains("satrt") || it.contains("6 am") || it.contains("6am") || it.contains("6:00")) }
                                                if (clean.isNotBlank() && clean != "Sent a photo" && clean != com.example.ui.chat.GIF_CAPTION && !isSuppressed) {
                                                    Spacer(modifier = Modifier.height(6.dp))
                                                    Text(clean, fontSize = 13.sp)
                                                }
                                            } else if (msg.getTypedType() == MessageType.CALL_RECORDING) {
                                                com.example.ui.chat.CallRecordingPlayerRow(
                                                    message = msg,
                                                    colors = com.example.ui.chat.CallRecordingColors(
                                                        content = MaterialTheme.colorScheme.onSurface,
                                                        button = RoseGoldPrimary,
                                                        buttonIcon = Color.White,
                                                        track = RoseGoldPrimary.copy(alpha = 0.25f),
                                                        progress = RoseGoldPrimary
                                                    ),
                                                    modifier = Modifier.fillMaxWidth()
                                                )
                                            } else {
                                                val clean = msg.text.trim()
                                                val isSuppressed = clean.lowercase().let { it.contains("today") && (it.contains("start") || it.contains("satrt") || it.contains("6 am") || it.contains("6am") || it.contains("6:00")) }
                                                if (clean.isNotBlank() && !isSuppressed) {
                                                    Text(
                                                        text = clean,
                                                        fontSize = 13.sp,
                                                        color = MaterialTheme.colorScheme.onSurface
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        }
                    }

                    // TAB 4: Calls & Recordings
                    4 -> Column(modifier = Modifier.fillMaxSize()) {
                        // Whose: from me or from my partner (as on the Media tab)
                        if (allCallHistoryItems.isNotEmpty()) {
                            FromFilterRow(from = galleryFrom, onFrom = { galleryFrom = it }, partnerName = partnerFirstName)
                        }
                        // The placeholders melt into the content instead of it popping in (each side of the
                        // crossfade keeps showing what it was)
                        val shownTab = when { callHistoryItems.isEmpty() && isGalleryLoading -> 0; callHistoryItems.isEmpty() -> 1; else -> 2 }
                        Crossfade(targetState = shownTab, animationSpec = tween(280), label = "gallery_tab", modifier = Modifier.weight(1f)) { phase ->
                        if (phase != 2) {
                            val emptyText = if (galleryFrom != FROM_ANYONE && allCallHistoryItems.isNotEmpty()) nothingFromText else if (selectedDateMillis != null) {
                                val sdf = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())
                                "No calls or recordings found on ${sdf.format(Date(selectedDateMillis!!))} 📞"
                            } else {
                                "No calls or recordings yet 📞"
                            }
                            EmptyGalleryNotice(emptyText, isLoading = phase == 0)
                        } else {
                            val callSections = remember(callHistoryItems) { galleryMonthSections(callHistoryItems) { it.timestamp } }
                            GalleryLazyColumn(
                                dateAt = { callSections.dateAt(it) },
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                monthSections(callSections, key = { it.id }) { msg ->
                                    val isRecording = msg.getTypedType() == MessageType.CALL_RECORDING ||
                                        !msg.recordingPath.isNullOrBlank() ||
                                        msg.recordingPaths.isNotEmpty() ||
                                        isCallAudioCached(context, msg)

                                    val isDeclined = msg.text.contains("declined", ignoreCase = true)
                                    val isCancelled = msg.text.contains("cancelled", ignoreCase = true)
                                    val isMissed = msg.text.contains("missed", ignoreCase = true)
                                    val isCompleted = !isDeclined && !isCancelled && !isMissed

                                    val callIcon = when {
                                        isRecording -> Icons.Rounded.PhoneInTalk
                                        isDeclined || isCancelled -> Icons.Rounded.CallEnd
                                        isMissed -> Icons.AutoMirrored.Rounded.PhoneMissed
                                        else -> Icons.Rounded.Call
                                    }
                                    val callColor = when {
                                        isRecording -> RoseGoldPrimary
                                        isCompleted -> Emerald500
                                        isCancelled -> Amber500
                                        else -> Red500
                                    }
                                    val titleText = when {
                                        isRecording -> "Voice Call Recording"
                                        isDeclined -> "Call Declined"
                                        isCancelled -> "Cancelled Call"
                                        isMissed -> "Missed Voice Call"
                                        else -> "Voice Call"
                                    }
                                    val durationText = if (msg.durationSeconds > 0) {
                                        " • ${msg.durationSeconds / 60}m ${msg.durationSeconds % 60}s"
                                    } else ""

                                    Surface(
                                        shape = RoundedCornerShape(16.dp),
                                        color = MaterialTheme.colorScheme.surface,
                                        shadowElevation = 1.5.dp,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .gallerySelectable(
                                                key = msg.id,
                                                isSelectionMode = isSelectionMode,
                                                isSelected = msg.id in selectedItemIds,
                                                accentColor = RoseGoldPrimary,
                                                onToggle = { toggleSelection(msg.id) },
                                                onStartSelection = { startSelection(msg.id) }
                                            )
                                            .testTag("gallery_call_${msg.id}")
                                    ) {
                                        Column(modifier = Modifier.padding(16.dp)) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                if (isSelectionMode) {
                                                    SelectionCheckBadge(isSelected = msg.id in selectedItemIds)
                                                    Spacer(modifier = Modifier.width(10.dp))
                                                }
                                                Box(
                                                    modifier = Modifier
                                                        .size(36.dp)
                                                        .clip(CircleShape)
                                                        .background(callColor.copy(alpha = 0.14f)),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Icon(
                                                        imageVector = callIcon,
                                                        contentDescription = null,
                                                        tint = callColor,
                                                        modifier = Modifier.size(18.dp)
                                                    )
                                                }
                                                Spacer(modifier = Modifier.width(10.dp))
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text(
                                                        text = titleText,
                                                        fontSize = 14.sp,
                                                        fontWeight = FontWeight.SemiBold,
                                                        color = MaterialTheme.colorScheme.onSurface
                                                    )
                                                    Text(
                                                        text = "${formatGalleryDateTime(msg.timestamp)}$durationText",
                                                        fontSize = 11.sp,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                }
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    CherishOutlinedButton(
                                                        onClick = { onNavigateToMessage(msg.id) },
                                                        shape = RoundedCornerShape(20.dp),
                                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                                        modifier = Modifier.height(30.dp)
                                                    ) {
                                                        Icon(Icons.AutoMirrored.Rounded.Chat, contentDescription = null, tint = RoseGoldPrimary, modifier = Modifier.size(13.dp))
                                                        Spacer(modifier = Modifier.width(4.dp))
                                                        Text("Chat", fontSize = 11.sp, color = RoseGoldPrimary)
                                                    }
                                                }
                                            }

                                            if (isRecording) {
                                                Spacer(modifier = Modifier.height(10.dp))
                                                com.example.ui.chat.CallRecordingPlayerRow(
                                                    message = msg,
                                                    colors = com.example.ui.chat.CallRecordingColors(
                                                        content = MaterialTheme.colorScheme.onSurface,
                                                        button = RoseGoldPrimary,
                                                        buttonIcon = Color.White,
                                                        track = RoseGoldPrimary.copy(alpha = 0.25f),
                                                        progress = RoseGoldPrimary
                                                    ),
                                                    modifier = Modifier.fillMaxWidth()
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        }
                    }
                }
            }
        }
    }

    // Multi-Select Delete Confirmation Dialog
    if (showMultiDeleteConfirmDialog) {
        val count = selectedItemIds.size
        val itemLabel = when (pagerState.currentPage) {
            0 -> if (count == 1) "photo or video" else "photos & videos"
            1 -> if (count == 1) "voice note" else "voice notes"
            2 -> if (count == 1) "link message" else "link messages"
            3 -> if (count == 1) "starred message" else "starred messages"
            else -> if (count == 1) "call item" else "call items"
        }
        CherishAlertDialog(
            onDismissRequest = { showMultiDeleteConfirmDialog = false },
            title = { Text("Delete $count $itemLabel?") },
            text = { Text("The selected items will be permanently removed from your shared gallery and chat.") },
            confirmButton = {
                CherishButton(
                    onClick = {
                        deleteSelectedItems()
                        showMultiDeleteConfirmDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                    modifier = Modifier.testTag("gallery_confirm_delete_selected")
                ) {
                    Text("Delete ($count)")
                }
            },
            dismissButton = {
                TextButton(onClick = { showMultiDeleteConfirmDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Delete Confirmation Dialog for Single Gallery Item
    messageToDelete?.let { msg ->
        CherishAlertDialog(
            onDismissRequest = { messageToDelete = null },
            title = { Text("Delete gallery item?") },
            text = { Text("This item will be permanently removed from your shared gallery and chat.") },
            confirmButton = {
                CherishButton(
                    onClick = {
                        val id = msg.id
                        chatViewModel.deleteMessage(id)
                        if (selectedMessageIdForViewer == id || selectedMediaUrl == msg.mediaUrl) {
                            selectedMediaUrl = null
                            selectedMessageIdForViewer = null
                        }
                        messageToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { messageToDelete = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Delete Confirmation Dialog for a single photo/video tile (other photos of the same message stay)
    mediaItemToDelete?.let { item ->
        CherishAlertDialog(
            onDismissRequest = { mediaItemToDelete = null },
            title = { Text(if (item.isVideo) "Delete video?" else "Delete photo?") },
            text = { Text("It will be permanently removed from your shared gallery and chat.") },
            confirmButton = {
                CherishButton(
                    onClick = {
                        chatViewModel.deleteGalleryMedia(listOf(item.originalMessage), setOf(item.mediaUrl))
                        if (selectedMediaUrl == item.mediaUrl) {
                            selectedMediaUrl = null
                            selectedMessageIdForViewer = null
                        }
                        mediaItemToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { mediaItemToDelete = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Search by date: the app's own (Material) date picker, as in chat search
    if (showDatePicker) {
        com.example.ui.components.CherishDatePickerDialog(
            title = "Search by date",
            confirmText = "Show",
            initialDateMillis = selectedDateMillis ?: System.currentTimeMillis(),
            onPick = { year, month, dayOfMonth ->
                showDatePicker = false
                selectedDateMillis = Calendar.getInstance().apply {
                    clear()
                    set(year, month, dayOfMonth)
                }.timeInMillis
            },
            onDismiss = { showDatePicker = false }
        )
    }

    selectedMediaUrl?.let { url ->
        // Swiping in the viewer goes through what the Media tab shows (its filter too)
        val galleryAllUrls = remember(shownMediaItems) {
            shownMediaItems.map { it.mediaUrl }.filter { it.isNotBlank() }.distinct()
        }
        val circularUrls = remember(galleryMediaItems, sourceMessages) {
            val fromGallery = galleryMediaItems
                .filter { it.originalMessage.isCircularVideoNote() || it.originalMessage.isVideoNote }
                .map { it.mediaUrl }
            val fromSource = sourceMessages
                .filter { it.isCircularVideoNote() || it.isVideoNote }
                .flatMap { it.getAllMediaUrls().ifEmpty { listOfNotNull(it.mediaUrl) } }
            (fromGallery + fromSource).filter { it.isNotBlank() }.toSet()
        }
        val isCurrentCircular = remember(url, selectedMessageIdForViewer, circularUrls) {
            url in circularUrls ||
            selectedMessageIdForViewer?.let { id ->
                sourceMessages.find { it.id == id }?.let { it.isCircularVideoNote() || it.isVideoNote }
            } == true ||
            url.contains("video_note", ignoreCase = true) ||
            url.contains("videonote", ignoreCase = true)
        }
        val mediaTimes = remember(sourceMessages) { mediaTimesOf(sourceMessages) }
        FullScreenMediaViewer(
            mediaUrl = url,
            allMediaUrls = if (galleryAllUrls.isNotEmpty()) galleryAllUrls else listOf(url),
            isCircularVideo = isCurrentCircular,
            circularMediaUrls = circularUrls,
            mediaTimes = mediaTimes,
            onShowInChat = { clickedUrl ->
                val targetMsg = sourceMessages.find {
                    it.id == selectedMessageIdForViewer &&
                        (it.mediaUrl == clickedUrl || clickedUrl in it.getAllMediaUrls())
                } ?: sourceMessages.find { it.mediaUrl == clickedUrl || it.getAllMediaUrls().contains(clickedUrl) }
                    ?: allGalleryItems.find { it.mediaUrl == clickedUrl || it.getAllMediaUrls().contains(clickedUrl) }
                if (targetMsg != null) {
                    onNavigateToMessage(targetMsg.id)
                } else {
                    CherishToast.show("The original message is no longer available.", long = true)
                }
            },
            onDeleteMedia = { clickedUrl ->
                val targetMsg = sourceMessages.find { it.mediaUrl == clickedUrl || it.getAllMediaUrls().contains(clickedUrl) }
                    ?: allGalleryItems.find { it.mediaUrl == clickedUrl || it.getAllMediaUrls().contains(clickedUrl) }
                if (targetMsg != null) {
                    chatViewModel.deleteGalleryMedia(listOf(targetMsg), setOf(clickedUrl))
                }
            },
            onDismiss = {
                selectedMediaUrl = null
                selectedMessageIdForViewer = null
            }
        )
    }
}

@Composable
private fun EmptyGalleryNotice(message: String, isLoading: Boolean = false, grid: Boolean = false) {
    if (isLoading) {
        // Grey shapes of what's loading: the photo grid, or rows for the other tabs
        if (grid) SkeletonPhotoGrid(modifier = Modifier.padding(4.dp)) else SkeletonListRows()
        return
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            lineHeight = 20.sp
        )
    }
}

/**
 * Multi-select for gallery cards: long-press starts selecting; while selecting, a tap anywhere on the
 * card toggles it. Taps are taken on the Initial pass, before the card's own buttons (play, open,
 * copy, show in chat) see them, so those don't fire in selection mode.
 */
private fun Modifier.gallerySelectable(
    key: Any,
    isSelectionMode: Boolean,
    isSelected: Boolean,
    accentColor: Color,
    onToggle: () -> Unit,
    onStartSelection: () -> Unit,
    shape: Shape = RoundedCornerShape(16.dp)
): Modifier = this
    .then(if (isSelected) Modifier.border(2.dp, accentColor, shape) else Modifier)
    .drawWithContent {
        drawContent()
        if (isSelected) {
            drawOutline(shape.createOutline(size, layoutDirection, this), accentColor.copy(alpha = 0.10f))
        }
    }
    .pointerInput(key, isSelectionMode) {
        if (isSelectionMode) {
            awaitEachGesture {
                awaitFirstDown(pass = PointerEventPass.Initial).consume()
                val up = waitForUpOrCancellation(pass = PointerEventPass.Initial)
                if (up != null) {
                    up.consume()
                    onToggle()
                }
            }
        } else {
            detectTapGestures(onLongPress = { onStartSelection() })
        }
    }

private fun getDomainIcon(host: String): androidx.compose.ui.graphics.vector.ImageVector {
    val h = host.lowercase()
    return when {
        h.contains("youtube") || h.contains("youtu.be") -> Icons.Rounded.PlayCircle
        h.contains("instagram") -> Icons.Rounded.CameraAlt
        h.contains("spotify") || h.contains("music") -> Icons.Rounded.MusicNote
        h.contains("maps") || h.contains("goo.gl") -> Icons.Rounded.Place
        h.contains("drive") || h.contains("doc") -> Icons.Rounded.Description
        else -> Icons.Rounded.Language
    }
}

private fun getDomainBadgeColor(host: String): Color {
    val h = host.lowercase()
    return when {
        h.contains("youtube") || h.contains("youtu.be") -> Red500
        h.contains("instagram") -> Pink500
        h.contains("spotify") -> Emerald500
        h.contains("twitter") || h.contains("x.com") -> Color(0xFF0284C7)
        h.contains("maps") -> Amber500
        else -> Color(0xFF6366F1)
    }
}

/** Photos and videos grouped by month ("October 2026"), in the order given (newest first). */
private fun galleryByMonth(items: List<GalleryMediaItem>): List<Pair<String, List<GalleryMediaItem>>> {
    val format = SimpleDateFormat("MMMM yyyy", Locale.getDefault())
    return items.groupBy { format.format(Date(it.timestamp)) }.toList()
}

/**
 * A list tab's items in month sections ("October 2026"), like the Media tab: each month has a
 * header that stays at the top while you scroll through it. [dateAt] gives the date at a list row,
 * counting the header rows, for the scrollbar.
 */
private class GallerySections<T>(val groups: List<Pair<String, List<T>>>, timestampOf: (T) -> Long) {
    private val rowDates: List<Long> = buildList {
        groups.forEach { (_, monthItems) ->
            add(monthItems.firstOrNull()?.let(timestampOf) ?: 0L)
            monthItems.forEach { add(timestampOf(it)) }
        }
    }

    fun dateAt(index: Int): Long? = rowDates.getOrNull(index)
}

private fun <T> galleryMonthSections(items: List<T>, timestampOf: (T) -> Long): GallerySections<T> {
    val format = SimpleDateFormat("MMMM yyyy", Locale.getDefault())
    return GallerySections(items.groupBy { format.format(Date(timestampOf(it))) }.toList(), timestampOf)
}

/** Emits [sections] month by month: a sticky month header, then that month's items. */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
private fun <T> LazyListScope.monthSections(
    sections: GallerySections<T>,
    key: (T) -> Any,
    itemContent: @Composable androidx.compose.foundation.lazy.LazyItemScope.(T) -> Unit
) {
    sections.groups.forEach { (month, monthItems) ->
        stickyHeader(key = "month_$month") {
            GalleryMonthHeader(month = month, count = monthItems.size)
        }
        items(monthItems, key = key) { item -> itemContent(item) }
    }
}

/** Photos and videos from today's date in earlier years, newest first. */
private fun galleryOnThisDay(items: List<GalleryMediaItem>): List<GalleryMediaItem> {
    val today = Calendar.getInstance()
    val then = Calendar.getInstance()
    return items.filter {
        then.timeInMillis = it.timestamp
        then.get(Calendar.YEAR) < today.get(Calendar.YEAR) &&
            then.get(Calendar.MONTH) == today.get(Calendar.MONTH) &&
            then.get(Calendar.DAY_OF_MONTH) == today.get(Calendar.DAY_OF_MONTH)
    }
}

/** The Media tab's kinds, for its filter. */
private const val MEDIA_ALL = 0
private const val MEDIA_PHOTOS = 1
private const val MEDIA_VIDEOS = 2
private const val MEDIA_VIDEO_NOTES = 3
private const val MEDIA_GIFS = 4
private val MEDIA_KIND_LABELS = listOf("All", "Photos", "Videos", "Video notes", "GIFs")
private val MEDIA_KIND_COUNT_WORDS = listOf("photos & videos", "photos", "videos", "video notes", "GIFs")
private val MEDIA_KIND_EMPTY_WORDS = listOf("photos or videos", "photos", "videos", "video notes", "GIFs")

/** Who sent it, for the gallery's filter (every tab). */
private const val FROM_ANYONE = 0
private const val FROM_ME = 1
private const val FROM_PARTNER = 2

/** These items narrowed to who sent them, for the gallery's From me / From partner filter. */
private fun <T> List<T>.sentBy(from: Int, isMine: (T) -> Boolean): List<T> = when (from) {
    FROM_ME -> filter(isMine)
    FROM_PARTNER -> filterNot(isMine)
    else -> this
}

/** From this many columns (the smallest tiles) the Media tab shows only the photos, no buttons on them. */
private const val COMPACT_TILE_COLUMNS = 5

private fun mediaKindOf(item: GalleryMediaItem): Int = when {
    !item.isVideo && item.isGif -> MEDIA_GIFS
    !item.isVideo -> MEDIA_PHOTOS
    item.originalMessage.isCircularVideoNote() -> MEDIA_VIDEO_NOTES
    else -> MEDIA_VIDEOS
}

/**
 * The Media tab's filter: all, photos, videos or video notes (kinds there are none of aren't
 * offered), and from me or from my partner. Tapping the chosen sender again shows both.
 */
@Composable
private fun MediaFilterRow(
    kind: Int,
    onKind: (Int) -> Unit,
    kindCounts: IntArray,
    from: Int,
    onFrom: (Int) -> Unit,
    partnerName: String
) {
    val chipColors = FilterChipDefaults.filterChipColors(
        selectedContainerColor = RoseGoldPrimary.copy(alpha = 0.12f),
        selectedLabelColor = RoseGoldPrimary
    )
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 4.dp)
    ) {
        MEDIA_KIND_LABELS.forEachIndexed { index, label ->
            if (index == MEDIA_ALL || index == kind || kindCounts[index] > 0) {
                CherishFilterChip(
                    selected = kind == index,
                    onClick = { onKind(index) },
                    label = { Text(label, fontSize = 12.sp) },
                    colors = chipColors,
                    modifier = Modifier.testTag("gallery_filter_kind_$index")
                )
            }
        }
        Box(
            modifier = Modifier
                .width(1.dp)
                .height(20.dp)
                .background(MaterialTheme.colorScheme.outlineVariant)
        )
        FromFilterChips(from = from, onFrom = onFrom, partnerName = partnerName)
    }
}

/** The Voice Notes, Links, Starred and Calls tabs' filter: from me or from my partner. */
@Composable
private fun FromFilterRow(from: Int, onFrom: (Int) -> Unit, partnerName: String) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 4.dp)
    ) {
        FromFilterChips(from = from, onFrom = onFrom, partnerName = partnerName)
    }
}

/** "From me" and "From <partner>": tapping the chosen one again shows both. */
@Composable
private fun FromFilterChips(from: Int, onFrom: (Int) -> Unit, partnerName: String) {
    val chipColors = FilterChipDefaults.filterChipColors(
        selectedContainerColor = RoseGoldPrimary.copy(alpha = 0.12f),
        selectedLabelColor = RoseGoldPrimary
    )
    listOf(FROM_ME to "From me", FROM_PARTNER to "From $partnerName").forEach { (value, label) ->
        CherishFilterChip(
            selected = from == value,
            onClick = { onFrom(if (from == value) FROM_ANYONE else value) },
            label = { Text(label, fontSize = 12.sp, maxLines = 1) },
            colors = chipColors,
            modifier = Modifier.testTag("gallery_filter_from_$value")
        )
    }
}

/**
 * A gallery tile's picture: a photo's small thumbnail ([full]: the full photo, shown over the
 * thumbnail while it loads), or a video's poster, so a tile never downloads a video. A video
 * without a poster yet gets one from [posterFor].
 */
@Composable
private fun GalleryTileImage(
    item: GalleryMediaItem,
    full: Boolean,
    posterFor: suspend (GalleryMediaItem) -> Any?,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val videoPoster by produceState<Any?>(
        initialValue = if (item.isVideo) item.previewUrl.takeIf { it != item.mediaUrl } else null,
        item.id
    ) {
        if (item.isVideo && value == null) value = posterFor(item)
    }
    val showFull = full && !item.isVideo && item.previewUrl != item.mediaUrl
    val shownUrl = if (showFull) item.mediaUrl else item.previewUrl
    val model: Any? = remember(item.isVideo, shownUrl, videoPoster) {
        if (item.isVideo) {
            videoPoster?.let { if (it is String) galleryImageModel(it) else it }
        } else {
            galleryImageModel(shownUrl)
        }
    }
    // The tile's own background shows until the poster is ready
    if (model == null) return
    AsyncImage(
        model = coil.request.ImageRequest.Builder(context)
            .data(model)
            .apply {
                // Going between thumbnail and full photo keeps showing the one it had until the other's in
                if (!item.isVideo) placeholderMemoryCacheKey(if (showFull) item.previewUrl else item.mediaUrl)
            }
            .crossfade(260)
            .build(),
        contentDescription = if (item.isVideo) "Shared video" else "Shared photo",
        contentScale = ContentScale.Crop,
        modifier = modifier
    )
}

/** What the image loader gets for a stored link: an inline picture's bytes, or the link. */
private fun galleryImageModel(url: String): Any = if (url.startsWith("data:image")) {
    runCatching { android.util.Base64.decode(url.substringAfter("base64,"), android.util.Base64.DEFAULT) }
        .getOrDefault(url)
} else {
    Uri.parse(url)
}

@Composable
private fun GalleryMonthHeader(month: String, count: Int) {
    Row(
        verticalAlignment = Alignment.Bottom,
        modifier = Modifier
            .fillMaxWidth()
            // Stays readable while it rests over the photos at the top
            .background(MaterialTheme.colorScheme.background.copy(alpha = 0.94f))
            .padding(start = 4.dp, top = 2.dp, bottom = 4.dp)
    ) {
        Text(
            text = month,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = count.toString(),
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 1.dp)
        )
    }
}

/** "On this day": a row of photos from this date in earlier years; tapping one opens it. */
@Composable
private fun OnThisDayCard(
    items: List<GalleryMediaItem>,
    posterFor: suspend (GalleryMediaItem) -> Any?,
    onOpen: (GalleryMediaItem) -> Unit
) {
    val yearsAgo = remember(items) {
        val now = Calendar.getInstance().get(Calendar.YEAR)
        val then = Calendar.getInstance().apply { timeInMillis = items.first().timestamp }.get(Calendar.YEAR)
        now - then
    }
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp, bottom = 6.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = "On this day",
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = if (yearsAgo <= 1) "1 year ago today" else "$yearsAgo years ago today",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(10.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.horizontalScroll(rememberScrollState())
            ) {
                items.take(10).forEach { memory ->
                    key(memory.id) {
                        Box(
                            modifier = Modifier
                                .size(92.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.6f))
                                .openableMedia { onOpen(memory) }
                        ) {
                            GalleryTileImage(
                                item = memory,
                                full = false,
                                posterFor = posterFor,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun isCallAudioCached(context: Context, msg: Message): Boolean {
    val rawId = msg.callId?.trim()?.takeIf { it.isNotBlank() } ?: msg.id.removePrefix("callrec_").takeIf { it.isNotBlank() } ?: return false
    val id = rawId.filter { it.isLetterOrDigit() || it == '-' || it == '_' }
    val dir = java.io.File(java.io.File(context.cacheDir, "call_recordings"), id)
    val recDir = java.io.File(context.cacheDir, "callrec")
    return listOf(
        java.io.File(dir, "part_0.mp4"),
        java.io.File(dir, "part_0.aac"),
        java.io.File(dir, "part_0.wav"),
        java.io.File(dir, "recording.mp4"),
        java.io.File(dir, "recording.aac"),
        java.io.File(dir, "recording.wav"),
        java.io.File(recDir, "rec_${id}.wav"),
        java.io.File(recDir, "rec_${id}.aac")
    ).any { it.exists() && it.length() > 0 }
}

// ---------- Scrollbar ----------

/** Where a gallery list is, read when needed (so scrolling doesn't recompose the screen), and how to go elsewhere. */
private class GalleryScrollPosition(
    val firstVisible: () -> Int,
    val visibleCount: () -> Int,
    val totalCount: () -> Int,
    val atEnd: () -> Boolean,
    val isScrolling: () -> Boolean,
    val scrollTo: suspend (Int) -> Unit
)

@Composable
private fun rememberGalleryScrollPosition(state: LazyGridState) = remember(state) {
    GalleryScrollPosition(
        firstVisible = { state.firstVisibleItemIndex },
        visibleCount = { state.layoutInfo.visibleItemsInfo.size },
        totalCount = { state.layoutInfo.totalItemsCount },
        atEnd = { !state.canScrollForward },
        isScrolling = { state.isScrollInProgress },
        scrollTo = { state.scrollToItem(it) }
    )
}

@Composable
private fun rememberGalleryScrollPosition(state: LazyListState) = remember(state) {
    GalleryScrollPosition(
        firstVisible = { state.firstVisibleItemIndex },
        visibleCount = { state.layoutInfo.visibleItemsInfo.size },
        totalCount = { state.layoutInfo.totalItemsCount },
        atEnd = { !state.canScrollForward },
        isScrolling = { state.isScrollInProgress },
        scrollTo = { state.scrollToItem(it) }
    )
}

/** A gallery tab's list with its scrollbar (see GalleryScrollbar); [dateAt] is the date of the item at an index. */
@Composable
private fun GalleryLazyColumn(
    dateAt: (Int) -> Long?,
    modifier: Modifier = Modifier,
    verticalArrangement: Arrangement.Vertical = Arrangement.Top,
    content: LazyListScope.() -> Unit
) {
    val state = rememberLazyListState()
    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(state = state, modifier = modifier, verticalArrangement = verticalArrangement, content = content)
        GalleryScrollbar(
            position = rememberGalleryScrollPosition(state),
            dateAt = dateAt,
            modifier = Modifier.fillMaxSize().padding(vertical = 6.dp)
        )
    }
}

/**
 * The drag handle along the right edge of a long gallery list: a pill with arrows up and down that
 * shows where you are, top to bottom, and can be dragged to go anywhere quickly, with the date
 * there beside it (and a tick as each month goes by). Only the handle takes touches; the list
 * scrolls and taps as usual everywhere else.
 */
@Composable
private fun GalleryScrollbar(
    position: GalleryScrollPosition,
    dateAt: (Int) -> Long?,
    modifier: Modifier = Modifier
) {
    val scrollable by remember(position) {
        derivedStateOf { position.visibleCount() in 1 until position.totalCount() }
    }
    if (!scrollable) return

    val scope = rememberCoroutineScope()
    val density = androidx.compose.ui.platform.LocalDensity.current
    val haptics = androidx.compose.ui.platform.LocalHapticFeedback.current
    val currentDateAt by rememberUpdatedState(dateAt)
    val thumbHeight = 46.dp
    // The touch area; the pill inside is narrower
    val thumbWidth = 36.dp
    val thumbHeightPx = with(density) { thumbHeight.toPx() }
    var trackHeightPx by remember { mutableFloatStateOf(0f) }
    var dragging by remember { mutableStateOf(false) }
    /** Where the dragged thumb is: 0 at the top, 1 at the bottom. */
    var dragFraction by remember { mutableFloatStateOf(0f) }
    var lastTarget by remember { mutableIntStateOf(-1) }
    /** The month under the handle while it's dragged (year * 12 + month), for the tick as it changes. */
    var lastMonth by remember { mutableIntStateOf(-1) }
    val monthAt = { index: Int ->
        currentDateAt(index)?.let { time ->
            val calendar = Calendar.getInstance().apply { timeInMillis = time }
            calendar.get(Calendar.YEAR) * 12 + calendar.get(Calendar.MONTH)
        } ?: -1
    }

    // The first index the list can be scrolled to: the thumb's range maps onto 0 to this
    val lastFirstIndex = { (position.totalCount() - position.visibleCount()).coerceAtLeast(1) }
    val listFraction by remember(position) {
        derivedStateOf {
            if (position.atEnd()) 1f else (position.firstVisible().toFloat() / lastFirstIndex()).coerceIn(0f, 1f)
        }
    }
    val active = dragging || position.isScrolling()
    // Always there on a long list; fully solid while you scroll or hold it
    val handleAlpha by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (active) 1f else 0.85f,
        animationSpec = tween(250),
        label = "gallery_scrollbar_alpha"
    )
    // A little bigger while it's held
    val grabScale by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (dragging) 1.12f else 1f,
        animationSpec = androidx.compose.animation.core.spring(dampingRatio = 0.6f, stiffness = 500f),
        label = "gallery_scrollbar_grab"
    )
    val thumbY = { ((trackHeightPx - thumbHeightPx).coerceAtLeast(0f) * (if (dragging) dragFraction else listFraction)).roundToInt() }

    Box(modifier = modifier.onSizeChanged { trackHeightPx = it.height.toFloat() }) {
        // The date where the thumb is, while it's dragged
        if (dragging) {
            val label = remember(lastTarget) {
                dateAt(lastTarget.coerceAtLeast(0))?.let { SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(Date(it)) }
            }
            if (label != null) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = RoseGoldPrimary,
                    shadowElevation = 4.dp,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .offset {
                            IntOffset(
                                x = -(thumbWidth + 2.dp).roundToPx(),
                                y = thumbY() + ((thumbHeight - 32.dp) / 2).roundToPx()
                            )
                        }
                        .height(32.dp)
                ) {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 16.dp)) {
                        Text(label, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                    }
                }
            }
        }
        Box(
            contentAlignment = Alignment.CenterEnd,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .offset { IntOffset(0, thumbY()) }
                .size(width = thumbWidth, height = thumbHeight)
                .testTag("gallery_scrollbar_thumb")
                .pointerInput(position) {
                    detectVerticalDragGestures(
                        onDragStart = {
                            dragFraction = listFraction
                            lastTarget = position.firstVisible()
                            lastMonth = monthAt(lastTarget)
                            dragging = true
                            haptics.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
                        },
                        onDragEnd = { dragging = false },
                        onDragCancel = { dragging = false },
                        onVerticalDrag = { change, dragAmount ->
                            change.consume()
                            val range = (trackHeightPx - thumbHeightPx).coerceAtLeast(1f)
                            dragFraction = (dragFraction + dragAmount / range).coerceIn(0f, 1f)
                            val target = (dragFraction * lastFirstIndex()).roundToInt()
                                .coerceIn(0, (position.totalCount() - 1).coerceAtLeast(0))
                            if (target != lastTarget) {
                                lastTarget = target
                                // A newer jump cancels the one still going
                                scope.launch { position.scrollTo(target) }
                                val month = monthAt(target)
                                if (month != lastMonth) {
                                    lastMonth = month
                                    haptics.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
                                }
                            }
                        }
                    )
                }
        ) {
            // A pill to grab, with arrows up and down, so it's plain it can be dragged
            Surface(
                shape = RoundedCornerShape(percent = 50),
                color = RoseGoldPrimary,
                shadowElevation = 3.dp,
                modifier = Modifier
                    .padding(end = 4.dp)
                    .graphicsLayer {
                        alpha = handleAlpha
                        scaleX = grabScale
                        scaleY = grabScale
                        transformOrigin = androidx.compose.ui.graphics.TransformOrigin(1f, 0.5f)
                    }
                    .size(width = 24.dp, height = thumbHeight)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Rounded.UnfoldMore,
                        contentDescription = "Drag to scroll",
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

/**
 * SharedPreferences-backed tracking for played status of shared gallery items (links, videos, voice).
 */
object GalleryPlayedManager {
    private const val PREFS_NAME = "cherish_gallery_played_prefs"
    private const val KEY_PLAYED_IDS = "played_item_ids"
    private const val KEY_UNPLAYED_IDS = "unplayed_item_ids"

    fun getPlayedIds(context: Context): Set<String> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getStringSet(KEY_PLAYED_IDS, emptySet()) ?: emptySet()
    }

    fun getUnplayedIds(context: Context): Set<String> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getStringSet(KEY_UNPLAYED_IDS, emptySet()) ?: emptySet()
    }

    // Keep played and unplayed distinct: their union does not change when a badge is toggled.
    fun getPlaybackState(context: Context): Pair<Set<String>, Set<String>> =
        getPlayedIds(context).toSet() to getUnplayedIds(context).toSet()

    fun getAllTrackedIds(context: Context): Set<String> {
        return getPlayedIds(context) + getUnplayedIds(context)
    }

    fun isPlayed(context: Context, id: String, isFromMe: Boolean = false): Boolean {
        if (id.isBlank()) return isFromMe
        val unplayed = getUnplayedIds(context)
        if (unplayed.contains(id)) return false
        val played = getPlayedIds(context)
        if (played.contains(id)) return true
        // Links sent by user default to already played; received links default to not yet played
        return isFromMe
    }

    @Synchronized
    fun setPlayed(context: Context, id: String, played: Boolean) {
        if (id.isBlank()) return
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val currentPlayed = (prefs.getStringSet(KEY_PLAYED_IDS, emptySet()) ?: emptySet()).toMutableSet()
        val currentUnplayed = (prefs.getStringSet(KEY_UNPLAYED_IDS, emptySet()) ?: emptySet()).toMutableSet()
        if (played) {
            currentPlayed.add(id)
            currentUnplayed.remove(id)
        } else {
            currentPlayed.remove(id)
            currentUnplayed.add(id)
        }
        prefs.edit()
            .putStringSet(KEY_PLAYED_IDS, currentPlayed)
            .putStringSet(KEY_UNPLAYED_IDS, currentUnplayed)
            .apply()
    }

    fun togglePlayed(context: Context, id: String, isFromMe: Boolean = false): Boolean {
        val newState = !isPlayed(context, id, isFromMe)
        setPlayed(context, id, newState)
        return newState
    }
}

/**
 * Modern pill badge indicating item played status ("Already played" / "Not yet").
 * Tap toggles the played state.
 */
@Composable
private fun PlayedStatusBadge(
    isPlayed: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onToggle,
        shape = RoundedCornerShape(12.dp),
        color = if (isPlayed) Color(0xFF10B981).copy(alpha = 0.12f) else Color(0xFFF59E0B).copy(alpha = 0.12f),
        border = BorderStroke(
            0.8.dp,
            if (isPlayed) Color(0xFF10B981).copy(alpha = 0.40f) else Color(0xFFF59E0B).copy(alpha = 0.40f)
        ),
        modifier = modifier.height(24.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(if (isPlayed) Color(0xFF10B981) else Color(0xFFF59E0B))
            )
            Spacer(modifier = Modifier.width(5.dp))
            Text(
                text = if (isPlayed) "Already played" else "Not yet",
                fontSize = 10.5.sp,
                fontWeight = FontWeight.Bold,
                color = if (isPlayed) Color(0xFF059669) else Color(0xFFD97706),
                maxLines = 1,
                softWrap = false
            )
        }
    }
}


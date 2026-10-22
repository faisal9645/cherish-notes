package com.example.ui.chat

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.audio.ChatSoundEffectsPlayer
import com.example.audio.VoicePlayerHelper
import com.example.audio.VoiceRecorderHelper
import com.example.data.model.Message
import com.example.data.model.MessageType
import com.example.data.model.User
import com.example.data.repository.AuthRepository
import com.example.data.repository.ChatRepository
import com.example.data.repository.MediaRepository
import com.example.CherishApplication
import com.example.security.SecurityPreferences
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.withPermit
import java.io.File

/** A photo, video or voice message that didn't send; shown above the message box with Retry. */
@androidx.compose.runtime.Immutable
data class FailedUpload(val label: String, val retry: () -> Unit)

/** Precomputed message row UI model with stable properties for LazyColumn optimization */
@androidx.compose.runtime.Immutable
data class ChatUiRowItem(
    val message: Message,
    val isFirstOfDay: Boolean,
    val isOldestShown: Boolean,
    val dateSeparatorText: String,
    val formattedTime: String = ""
)

@androidx.compose.runtime.Immutable
data class ChatUiState(
    val messages: List<Message> = emptyList(),
    val currentUser: User? = null,
    val partnerUser: User? = null,
    val isPartnerTyping: Boolean = false,
    val searchQuery: String = "",
    val isSearching: Boolean = false,
    val replyingToMessage: Message? = null,
    val selectedMessageForActions: Message? = null,
    val fullScreenMediaUrl: String? = null,
    val fullScreenMediaType: MessageType? = null,
    val isFullScreenVideoNote: Boolean = false,
    val allMediaUrlsForViewer: List<String> = emptyList(),
    val gallerySize: String = "small",
    val isUploadingMedia: Boolean = false,
    val uploadProgress: Float = 0f,
    // What's uploading ("photo", "3 photos", "voice message"…), and the last send that didn't go out
    val uploadLabel: String = "",
    val failedUpload: FailedUpload? = null,
    val deletionRequest: com.example.data.model.ChatDeletionRequest? = null,
    val isStealthCurtainActive: Boolean = false,
    val stealthToastMessage: String? = null,
    val isCheckAfterSheetOpen: Boolean = false,
    val isCheckAfterReminderEnabled: Boolean = true,
    val isPartnerRecordingAudio: Boolean = false,
    val isPartnerOnline: Boolean = false,
    val isPartnerHeartTouching: Boolean = false,
    val pinnedMessage: Message? = null,
    val theaterVideoId: String? = null,
    val filterStarredOnly: Boolean = false,
    val voicePlaybackSpeed: Float = 1.0f,
    val isSecretHistoryRevealed: Boolean = true,
    val showPreviousChats: Boolean = false,
    val isLoadingMore: Boolean = false,
    val isPaginationExhausted: Boolean = false,
    val hasPreviousChatsAvailable: Boolean = true,
    val targetScrollMessageId: String? = null,
    val temporaryClearTimestamp: Long = 0L,
    val chatBgTheme: Int = 0,
    // Settings > Appearance: the messages' text size (sp) and font (see ChatFont)
    val chatTextSize: Int = SecurityPreferences.DEFAULT_CHAT_TEXT_SIZE,
    val chatFont: Int = 0,
    val chatExperienceMode: ChatExperienceMode = ChatExperienceMode.NORMAL,
    // The current 6 AM day; changes at 6 AM so "today only" filters recompute
    val logicalDay: String = ""
)

/** How many pages "Show in chat" loads at most looking for an older message (60 messages each). */
@androidx.compose.runtime.Immutable
data class PartnerPresenceState(
    val isOnline: Boolean = false,
    val isTyping: Boolean = false,
    val isRecordingAudio: Boolean = false,
    val isHeartTouching: Boolean = false,
    val batteryLevel: Int? = null,
    val isCharging: Boolean = false,
    val lastSeen: Long = 0L,
    val photoUrl: String? = null,
    val displayName: String = "",
    val mood: String? = null,
    val moodAt: Long = 0L,
    val timeZone: String? = null,
    val typingPace: Int = 1,
    val checkAfterActive: Boolean = false,
    val checkAfterTimeMillis: Long? = null,
    val checkAfterCreatedAt: Long? = null
)

class ChatViewModel(
    private val authRepository: AuthRepository,
    private val chatRepository: ChatRepository,
    private val mediaRepository: MediaRepository,
    val voiceRecorderHelper: VoiceRecorderHelper,
    val voicePlayerHelper: VoicePlayerHelper,
    val securityPreferences: SecurityPreferences = CherishApplication.instance.securityPreferences,
    val soundEffectsPlayer: ChatSoundEffectsPlayer = ChatSoundEffectsPlayer.getInstance(CherishApplication.instance)
) : ViewModel() {

    private val _partnerPresence = MutableStateFlow(PartnerPresenceState())
    val partnerPresence: StateFlow<PartnerPresenceState> = _partnerPresence.asStateFlow()

    /** My partner playing one of my voice notes (or watching a video note) right now, see ListeningNow. */
    val partnerListening: StateFlow<com.example.data.model.PartnerListening?> = authRepository.partnerListening

    /** My partner's latest kiss on the chat, and their lips on the kiss pad right now (see KissTouch). */
    val partnerKiss: StateFlow<com.example.data.model.KissStamp?> = authRepository.partnerKiss
    val partnerKissTouch: StateFlow<com.example.data.model.KissTouch?> = authRepository.partnerKissTouch

    private var lastKissSentAt = 0L

    /** A kiss stamped on the chat at [x], [y] (parts of the screen): it blooms on my partner's too. */
    fun sendKiss(x: Float, y: Float) {
        val now = android.os.SystemClock.elapsedRealtime()
        // A flurry of kisses is one write a second at most
        if (now - lastKissSentAt < KISS_EVERY_MS) return
        lastKissSentAt = now
        authRepository.sendKiss(x, y)
    }

    /** My lips held on the kiss pad (parts of it), or let go (null). */
    fun setKissTouch(x: Float?, y: Float?) = authRepository.setKissTouch(x, y)

    private val _uiState = MutableStateFlow(
        ChatUiState(
            gallerySize = securityPreferences.getImageGallerySize(),
            chatBgTheme = securityPreferences.getChatBgTheme(),
            chatTextSize = securityPreferences.getChatTextSize(),
            chatFont = securityPreferences.getChatFont(),
            chatExperienceMode = securityPreferences.getChatExperienceMode(),
            showPreviousChats = securityPreferences.isShowPreviousChatsEnabled(),
            isSecretHistoryRevealed = securityPreferences.isSecretHistoryRevealed.value,
            temporaryClearTimestamp = securityPreferences.getTemporaryClearTimestamp()
        )
    )
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    /** What the chat list shows depends on these (today only / Recover All, starred, search...). */
    private data class MessageFilter(
        val messages: List<Message>,
        val showPreviousChats: Boolean,
        val starredOnly: Boolean,
        val clearedBefore: Long,
        val query: String,
        val logicalDay: String
    )

    private fun ChatUiState.messageFilter() = MessageFilter(
        messages = messages,
        showPreviousChats = showPreviousChats,
        starredOnly = filterStarredOnly,
        clearedBefore = temporaryClearTimestamp,
        query = searchQuery,
        logicalDay = logicalDay
    )

    private fun filterMessages(filter: MessageFilter): List<Message> {
        var list = filter.messages.filter { !it.isDeleted }
        if (!filter.showPreviousChats) {
            list = list.filter { com.example.util.ChatTimeFormatter.isToday(it.timestamp) }
        }
        if (filter.starredOnly) {
            list = list.filter { it.isStarred }
        }
        if (filter.clearedBefore > 0L) {
            list = list.filter { it.timestamp > filter.clearedBefore }
        }
        if (filter.query.isNotBlank()) {
            val q = filter.query.trim()
            list = list.filter { it.text.contains(q, ignoreCase = true) }
        }
        return list
    }

    /**
     * The messages the chat shows, worked out off the main thread: with big pages of history
     * arriving, filtering on the main thread made scrolling stutter. Only recomputed when the
     * messages or a filter change (not for typing or presence updates).
     */
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val displayedMessages: StateFlow<List<Message>> = _uiState
        .map { it.messageFilter() }
        .distinctUntilChanged()
        .mapLatest { filterMessages(it) }
        .flowOn(kotlinx.coroutines.Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.Eagerly, filterMessages(_uiState.value.messageFilter()))

    /**
     * Precomputed row items for the reversed LazyColumn, calculated completely off the main thread.
     * Keeps date separators and oldest-item flags stable so adding new messages does NOT recompose
     * older message rows, keeping chat scrolling and rendering silky-smooth during long sessions.
     */
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val reversedChatRowItems: StateFlow<List<ChatUiRowItem>> = displayedMessages
        .mapLatest { list ->
            val size = list.size
            val items = ArrayList<ChatUiRowItem>(size)
            for (i in list.indices) {
                val msg = list[i]
                val prevMsg = if (i > 0) list[i - 1] else null
                val isFirstOfDay = prevMsg == null || !com.example.util.ChatTimeFormatter.isSameDay(prevMsg.timestamp, msg.timestamp)
                val isOldestShown = (i == 0)
                val dateSep = if (isFirstOfDay) com.example.util.ChatTimeFormatter.formatDateSeparator(msg.timestamp) else ""
                // (The row formats its own time: nothing reads one made here)
                items.add(
                    ChatUiRowItem(
                        message = msg,
                        isFirstOfDay = isFirstOfDay,
                        isOldestShown = isOldestShown,
                        dateSeparatorText = dateSep
                    )
                )
            }
            items.asReversed()
        }
        .flowOn(kotlinx.coroutines.Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    // Preserved scroll position and composer draft when navigating between tabs (Chat, Love & Us, Settings)
    var savedScrollIndex: Int = 0
    var savedScrollOffset: Int = 0
    var savedScrollKey: String? = null
    var draftMessageText: String = ""

    init {
        chatRepository.ensureGlobalMessagesListener()
        authRepository.ensureCoupleRoomListener()
        authRepository.connectPartnerListenerOnce()
        authRepository.updatePresence()
        chatRepository.markPendingDelivered()

        // Collect messages from the single global listener in ChatRepository.
        // No duplicate listener creation — ChatRepository.init manages the Firestore listener
        // and switches it when coupleId changes via its own collectLatest.
        var highestMessageTimestamp = 0L
        viewModelScope.launch {
            chatRepository.messagesFlow.collect { msgList ->
                val pinned = msgList.lastOrNull { it.isPinned && !it.isDeleted }
                val currentUserId = authRepository.getCurrentUserId()
                val newIncoming = msgList.lastOrNull {
                    it.senderId != currentUserId && it.timestamp > highestMessageTimestamp &&
                        it.getTypedType() != MessageType.CALL_RECORDING
                }
                if (highestMessageTimestamp > 0L && newIncoming != null) {
                    val isDisguised = securityPreferences.isDisguiseActive.value
                    val isActivelyInChat = authRepository.isUserActivelyInChat()
                    if (!isDisguised && isActivelyInChat) {
                        soundEffectsPlayer.playSound(ChatSoundEffectsPlayer.SoundType.RECEIVED)
                    }
                }
                highestMessageTimestamp = maxOf(highestMessageTimestamp, msgList.maxOfOrNull { it.timestamp } ?: 0L)

                _uiState.update {
                    it.copy(
                        messages = msgList,
                        pinnedMessage = pinned
                    )
                }
            }
        }

        // Playing one of my partner's voice notes, or watching their video note with its sound:
        // their phone shows it live ("listening…"). Saved only when it starts, pauses, jumps or
        // stops, a moment after it settles (a drag along the waveform is one save)
        viewModelScope.launch {
            combine(voicePlayerHelper.anchor, VideoNoteWatching.now) { anchor, watching -> anchor to watching }
                .distinctUntilChanged()
                .collectLatest { (anchor, watching) ->
                    kotlinx.coroutines.delay(LISTENING_SETTLE_MS)
                    val myId = _uiState.value.currentUser?.id
                    val now = System.currentTimeMillis()
                    val heard = anchor?.let { a -> _uiState.value.messages.find { it.id == a.trackId } }
                    val value = when {
                        anchor != null && heard != null && myId != null && heard.senderId != myId ->
                            com.example.data.model.PartnerListening.toMap(
                                anchor.trackId, isVideo = false,
                                // It played on while this waited
                                positionMs = anchor.positionMs + if (anchor.playing) (LISTENING_SETTLE_MS * anchor.speed).toLong() else 0L,
                                durationMs = anchor.durationMs, speed = anchor.speed, playing = anchor.playing, atMs = now
                            )
                        watching != null ->
                            com.example.data.model.PartnerListening.toMap(
                                watching.messageId, isVideo = true, positionMs = 0L,
                                durationMs = watching.durationMs, speed = 1f, playing = true, atMs = now
                            )
                        else -> null
                    }
                    authRepository.setListening(value)
                }
        }

        viewModelScope.launch {
            authRepository.currentUserState.collect { user ->
                _uiState.update { it.copy(currentUser = user) }
            }
        }

        viewModelScope.launch {
            authRepository.partnerUserState.collect { partner ->
                val partnerTouching = partner?.isEffectivelyHeartTouching() ?: false
                val isCallActive = com.example.call.CallManager.hasActiveCall()
                val partnerOnline = (partner?.isEffectivelyOnline() ?: false) || isCallActive
                _partnerPresence.value = PartnerPresenceState(
                    isOnline = partnerOnline,
                    isTyping = partner?.isEffectivelyTyping() ?: false,
                    isRecordingAudio = partner?.isEffectivelyRecording() ?: false,
                    isHeartTouching = partnerTouching,
                    batteryLevel = partner?.batteryLevel,
                    isCharging = partner?.isCharging ?: false,
                    lastSeen = partner?.lastSeen ?: 0L,
                    photoUrl = partner?.photoUrl,
                    displayName = partner?.displayName.orEmpty(),
                    mood = partner?.mood,
                    moodAt = partner?.moodAt ?: 0L,
                    timeZone = partner?.timeZone?.takeIf { it.isNotBlank() } ?: securityPreferences.getPartnerCustomTimeZone(),
                    typingPace = partner?.typingPace ?: 1,
                    checkAfterActive = partner?.hasActiveCheckAfter() ?: false,
                    checkAfterTimeMillis = partner?.checkAfterTimeMillis,
                    checkAfterCreatedAt = partner?.checkAfterCreatedAt
                )
                // Keep partner heartbeat/online status and identity up-to-date in _uiState
                _uiState.update { currentUiState ->
                    var updated = currentUiState
                    if (updated.isPartnerHeartTouching != partnerTouching || updated.isPartnerOnline != partnerOnline) {
                        updated = updated.copy(
                            isPartnerHeartTouching = partnerTouching,
                            isPartnerOnline = partnerOnline
                        )
                    }
                    if (updated.partnerUser?.id != partner?.id ||
                        updated.partnerUser?.photoUrl != partner?.photoUrl ||
                        updated.partnerUser?.displayName != partner?.displayName
                    ) {
                        updated = updated.copy(partnerUser = partner)
                    }
                    updated
                }
            }
        }

        viewModelScope.launch {
            securityPreferences.chatBgTheme.collect { theme ->
                _uiState.update { it.copy(chatBgTheme = theme) }
            }
        }

        viewModelScope.launch {
            securityPreferences.chatTextSize.collect { size ->
                _uiState.update { it.copy(chatTextSize = size) }
            }
        }

        viewModelScope.launch {
            securityPreferences.chatFont.collect { font ->
                _uiState.update { it.copy(chatFont = font) }
            }
        }

        viewModelScope.launch {
            securityPreferences.isSecretHistoryRevealed.collect { revealed ->
                _uiState.update { it.copy(isSecretHistoryRevealed = revealed) }
            }
        }

        viewModelScope.launch {
            securityPreferences.imageGallerySize.collect { size ->
                _uiState.update { it.copy(gallerySize = size) }
            }
        }

        viewModelScope.launch {
            securityPreferences.showPreviousChats.collect { show ->
                _uiState.update { it.copy(showPreviousChats = show) }
            }
        }

        viewModelScope.launch {
            securityPreferences.temporaryClearTimestamp.collect { ts ->
                _uiState.update { it.copy(temporaryClearTimestamp = ts) }
            }
        }

        viewModelScope.launch {
            voicePlayerHelper.playbackSpeed.collect { speed ->
                _uiState.update { it.copy(voicePlaybackSpeed = speed) }
            }
        }

        viewModelScope.launch {
            chatRepository.deletionRequestFlow.collect { req ->
                _uiState.update { it.copy(deletionRequest = req) }
            }
        }

        viewModelScope.launch {
            securityPreferences.chatExperienceModeFlow.collect { mode ->
                _uiState.update { it.copy(chatExperienceMode = mode) }
            }
        }

        viewModelScope.launch {
            securityPreferences.isDisguiseActive.collect { isDisguised ->
                if (isDisguised) {
                    _uiState.update {
                        it.copy(
                            fullScreenMediaUrl = null,
                            fullScreenMediaType = null,
                            allMediaUrlsForViewer = emptyList(),
                            theaterVideoId = null
                        )
                    }
                }
            }
        }

        viewModelScope.launch {
            chatRepository.isLoadingMoreFlow.collect { loading ->
                _uiState.update { it.copy(isLoadingMore = loading) }
            }
        }

        viewModelScope.launch {
            chatRepository.isQueryExhaustedFlow.collect { exhausted ->
                _uiState.update { it.copy(isPaginationExhausted = exhausted) }
            }
        }

        viewModelScope.launch {
            chatRepository.hasPreviousChatsAvailableFlow.collect { available ->
                _uiState.update { it.copy(hasPreviousChatsAvailable = available) }
            }
        }
        
        com.example.notifications.ScheduledMessageStore.load(CherishApplication.instance)

        // A new day starts at 6 AM: previous chat and media hide again until "Recover all". Checked
        // every minute (a long sleep wouldn't fire on time if the phone dozes through 6 AM).
        viewModelScope.launch {
            _uiState.update { it.copy(logicalDay = securityPreferences.logicalDayKey()) }
            while (true) {
                kotlinx.coroutines.delay(60_000L)
                val day = securityPreferences.logicalDayKey()
                if (day != _uiState.value.logicalDay) {
                    securityPreferences.setShowPreviousChatsEnabled(false)
                    securityPreferences.setAllGalleryRecovered(false)
                    chatRepository.startNewDay()
                    _uiState.update { it.copy(logicalDay = day) }
                }
            }
        }

        // Ticker to evaluate effective online/typing/heartbeat status without refreshing the whole chat screen
        viewModelScope.launch {
            while (true) {
                val delayMs = if (_partnerPresence.value.isHeartTouching) 500L else 2000L
                kotlinx.coroutines.delay(delayMs)
                val partner = authRepository.partnerUserState.value
                val partnerTouching = partner?.isEffectivelyHeartTouching() ?: false
                val newOnline = (partner?.isEffectivelyOnline() ?: false) || com.example.call.CallManager.hasActiveCall()
                val newTyping = partner?.isEffectivelyTyping() ?: false
                val newRecording = partner?.isEffectivelyRecording() ?: false

                _partnerPresence.update { current ->
                    val partnerLastSeen = partner?.lastSeen ?: current.lastSeen
                    if (current.isOnline == newOnline &&
                        current.isTyping == newTyping &&
                        current.isRecordingAudio == newRecording &&
                        current.isHeartTouching == partnerTouching &&
                        current.batteryLevel == partner?.batteryLevel &&
                        current.isCharging == (partner?.isCharging ?: false) &&
                        current.lastSeen == partnerLastSeen
                    ) {
                        current
                    } else {
                        current.copy(
                            isOnline = newOnline,
                            isTyping = newTyping,
                            isRecordingAudio = newRecording,
                            isHeartTouching = partnerTouching,
                            batteryLevel = partner?.batteryLevel,
                            isCharging = partner?.isCharging ?: false,
                            lastSeen = partnerLastSeen
                        )
                    }
                }

                if (_uiState.value.isPartnerHeartTouching != partnerTouching || _uiState.value.isPartnerOnline != newOnline) {
                    _uiState.update {
                        it.copy(
                            isPartnerHeartTouching = partnerTouching,
                            isPartnerOnline = newOnline
                        )
                    }
                }
            }
        }
    }

    fun setInChatTab(inChat: Boolean) {
        if (inChat) {
            _uiState.update { it.copy(chatExperienceMode = securityPreferences.getChatExperienceMode()) }
        }
        authRepository.setInChatTab(inChat)
    }

    fun setChatExperienceMode(mode: ChatExperienceMode) {
        securityPreferences.setChatExperienceMode(mode)
        _uiState.update { it.copy(chatExperienceMode = mode) }
    }

    fun toggleChatExperienceMode() {
        val nextMode = if (_uiState.value.chatExperienceMode == ChatExperienceMode.PRIVATE) {
            ChatExperienceMode.NORMAL
        } else {
            ChatExperienceMode.PRIVATE
        }
        setChatExperienceMode(nextMode)
    }

    fun setShowPreviousChats(enabled: Boolean) {
        securityPreferences.setShowPreviousChatsEnabled(enabled)
    }

    fun clearChatScreenTemporarily() {
        securityPreferences.clearTemporaryScreen()
    }

    fun loadMoreMessages() {
        chatRepository.loadMoreMessages()
    }

    val galleryMediaMessages: kotlinx.coroutines.flow.StateFlow<List<com.example.data.model.Message>> =
        chatRepository.galleryMediaMessages

    val isGalleryLoading: StateFlow<Boolean> = chatRepository.isGalleryLoadingFlow

    fun loadAllGalleryMedia() {
        chatRepository.loadAllGalleryMedia()
    }

    val isQueryExhausted: Boolean
        get() = chatRepository.isQueryExhausted

    private var typingIdleJob: Job? = null
    private var isCurrentlyTyping = false
    private var lastPaceUpdateRealtime = 0L
    private var lastWrittenPace = 0

    /** Called on keystrokes; throttled so typing does not flood Firestore or re-trigger listeners. */
    fun onTypingChanged(isTyping: Boolean) {
        typingIdleJob?.cancel()
        if (isTyping != isCurrentlyTyping) {
            isCurrentlyTyping = isTyping
            authRepository.setTyping(isTyping)
        }
        updateTypingPace(isTyping)
        if (isTyping) {
            // Stop showing "typing…" to the partner once the keyboard has been idle a while
            typingIdleJob = viewModelScope.launch {
                kotlinx.coroutines.delay(TYPING_IDLE_TIMEOUT_MS)
                isCurrentlyTyping = false
                authRepository.setTyping(false)
                authRepository.setTypingPace(0)
                lastWrittenPace = 0
            }
        }
    }

    // Recent keystroke times (last 3 s) and when the pace drops to "paused"
    private val keystrokeTimes = ArrayDeque<Long>()
    private var typingPauseJob: Job? = null
    private var typingPace = 0

    /**
     * My typing pace for the partner's dots: fast (2) from about 3 keys a second, slow (1) below
     * that, paused (0) after 2.5 s without a key. Throttled to at most once per 3.5s to keep Firestore writes light.
     */
    private fun updateTypingPace(isTyping: Boolean) {
        typingPauseJob?.cancel()
        if (!isTyping) {
            keystrokeTimes.clear()
            typingPace = 0
            if (lastWrittenPace != 0) {
                lastWrittenPace = 0
                authRepository.setTypingPace(0)
            }
            return
        }
        val now = android.os.SystemClock.elapsedRealtime()
        keystrokeTimes.addLast(now)
        while (keystrokeTimes.isNotEmpty() && now - keystrokeTimes.first() > 3_000L) keystrokeTimes.removeFirst()
        val calculatedPace = when {
            keystrokeTimes.size >= 9 -> 2
            keystrokeTimes.size <= 5 -> 1
            else -> typingPace.coerceAtLeast(1)
        }
        typingPace = calculatedPace
        if (calculatedPace != lastWrittenPace && (now - lastPaceUpdateRealtime > 3_500L)) {
            lastPaceUpdateRealtime = now
            lastWrittenPace = calculatedPace
            authRepository.setTypingPace(calculatedPace)
        }
        typingPauseJob = viewModelScope.launch {
            kotlinx.coroutines.delay(2_500L)
            typingPace = 0
            keystrokeTimes.clear()
            if (lastWrittenPace != 0) {
                lastWrittenPace = 0
                authRepository.setTypingPace(0)
            }
        }
    }

    fun sendTextMessage(text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return
        val replyTo = _uiState.value.replyingToMessage

        soundEffectsPlayer.playSound(ChatSoundEffectsPlayer.SoundType.SENT)
        viewModelScope.launch {
            _uiState.update { it.copy(replyingToMessage = null) }
            chatRepository.sendMessage(
                text = trimmed,
                type = MessageType.TEXT,
                replyTo = replyTo
            )
        }
    }

    /** A GIF from GIPHY's picker goes out as GIPHY's own link: nothing to upload. */
    fun sendGif(gif: GiphyGif) {
        val replyTo = _uiState.value.replyingToMessage
        soundEffectsPlayer.playSound(ChatSoundEffectsPlayer.SoundType.SENT)
        viewModelScope.launch {
            _uiState.update { it.copy(replyingToMessage = null) }
            chatRepository.sendMessage(
                text = GIF_CAPTION,
                type = MessageType.IMAGE,
                mediaUrl = gif.sendUrl,
                mediaName = "giphy.gif",
                replyTo = replyTo,
                // Its first frame, for the gallery's small tiles
                thumbnailUrls = listOfNotNull(gif.stillUrl)
            )
        }
    }

    private var uploadJob: Job? = null

    /**
     * Runs one send of media with its progress shown above the message box. [block] returns null
     * when everything went out, or what to show (with Retry) for the part that didn't.
     */
    private fun runUpload(label: String, retry: () -> Unit, block: suspend CoroutineScope.() -> FailedUpload?) {
        _uiState.update { it.copy(isUploadingMedia = true, uploadProgress = 0.05f, uploadLabel = label, failedUpload = null) }
        val job = viewModelScope.launch {
            val failed = try {
                coroutineScope { block() }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w("ChatViewModel", "Sending $label failed", e)
                FailedUpload(label, retry)
            }
            if (failed != null) _uiState.update { it.copy(failedUpload = failed) }
        }
        uploadJob = job
        job.invokeOnCompletion {
            if (uploadJob === job) {
                uploadJob = null
                _uiState.update { it.copy(isUploadingMedia = false, uploadProgress = 0f) }
            }
        }
    }

    /** Stops the upload in progress; nothing is sent. */
    fun cancelUpload() {
        uploadJob?.cancel()
    }

    fun retryFailedUpload() {
        val failed = _uiState.value.failedUpload ?: return
        _uiState.update { it.copy(failedUpload = null) }
        failed.retry()
    }

    fun dismissFailedUpload() {
        _uiState.update { it.copy(failedUpload = null) }
    }

    /**
     * Whether the partner can open [url]: a storage link, or a small inline data URI. The upload
     * falls back to a local file path when it fails, which only exists on this phone.
     */
    private fun isDeliverable(url: String?): Boolean =
        url != null && (url.startsWith("https://") || url.startsWith("http://") || url.startsWith("data:") || url.startsWith("file://") || url.startsWith("content://"))

    private fun reportUploadProgress(progress: Float) {
        // Every change of the chat's state rebuilds the chat screen: the bar moves in 2% steps,
        // not on each of the upload's many callbacks
        val shown = _uiState.value.uploadProgress
        if (progress < 1f && kotlin.math.abs(progress - shown) < 0.02f) return
        _uiState.update { it.copy(uploadProgress = progress) }
    }

    /** [compactVideo]: a clip from holding the camera button, made smaller still (480p). */
    fun sendMediaFile(uri: Uri, type: MessageType, mediaName: String? = null, compactVideo: Boolean = false) {
        val label = when (type) {
            MessageType.IMAGE -> "photo"
            MessageType.VIDEO -> "video"
            else -> "file"
        }
        val retry = { sendMediaFile(uri, type, mediaName, compactVideo) }
        runUpload(label, retry) {
            val coupleId = chatRepository.getConversationId()
            // A video goes out smaller (720p) when that helps: the first part of the bar while
            // that's done, the rest while it uploads
            val compressedVideo = if (type == MessageType.VIDEO) {
                com.example.util.MediaCompressor.compressVideo(CherishApplication.instance, uri, compact = compactVideo) { p ->
                    reportUploadProgress(p * VIDEO_COMPRESS_SHARE)
                }
            } else null
            val preparedFile = when {
                type == MessageType.IMAGE -> mediaRepository.compressAndPrepareImage(uri)
                compressedVideo != null -> compressedVideo
                // Picked videos and files arrive as content links: copy them so they can upload
                else -> mediaRepository.copyToCache(uri, mediaName)
            }
            val sentName = if (compressedVideo != null) {
                mediaName?.substringBeforeLast('.')?.ifBlank { null }?.let { "$it.mp4" }
            } else mediaName

            // Its gallery tile's small picture (a photo's thumbnail, a video's poster) goes up alongside
            val tilePicture = async { uploadTilePicture(type, preparedFile, coupleId) }
            val url = mediaRepository.uploadFile(
                file = preparedFile,
                type = type,
                coupleId = coupleId,
                onProgress = { p ->
                    if (compressedVideo != null) {
                        reportUploadProgress(VIDEO_COMPRESS_SHARE + p * (1f - VIDEO_COMPRESS_SHARE))
                    } else {
                        reportUploadProgress(p)
                    }
                }
            ).getOrNull()
            if (url == null || !isDeliverable(url)) {
                tilePicture.cancel()
                return@runUpload FailedUpload(label, retry)
            }

            if (type == MessageType.VIDEO) {
                com.example.util.VideoThumbnailHelper.cacheLocalVideo(com.example.CherishApplication.instance, preparedFile, url)
            }

            chatRepository.sendMessage(
                text = when (type) {
                    MessageType.IMAGE -> "Sent a photo"
                    MessageType.VIDEO -> "Sent a video"
                    else -> "Sent a file"
                },
                type = type,
                mediaUrl = url,
                mediaName = sentName ?: preparedFile.name,
                mediaSize = preparedFile.length(),
                replyTo = _uiState.value.replyingToMessage,
                thumbnailUrls = listOfNotNull(tilePicture.await())
            )
            _uiState.update { it.copy(replyingToMessage = null) }
            runCatching {
                if (preparedFile.parentFile?.name in listOf("compressed_media", "outgoing_media")) {
                    preparedFile.delete()
                }
                if (uri.path?.contains("camera_snaps") == true) {
                    File(uri.path!!).takeIf { it.exists() }?.delete()
                    // A camera photo or clip comes as this app's own content link: its file is in
                    // the cache's camera_snaps (a recorded clip can be hundreds of MB)
                    uri.lastPathSegment?.let { name ->
                        File(File(CherishApplication.instance.cacheDir, "camera_snaps"), name).takeIf { it.exists() }?.delete()
                    }
                }
            }
            null
        }
    }

    /**
     * The small square picture a photo or video shows as in the gallery (about 10 KB: a photo's
     * thumbnail, a video's poster), uploaded. Null when it couldn't be made or sent; the gallery
     * then makes do without it. Chat bubbles always show the full photo.
     */
    private suspend fun uploadTilePicture(type: MessageType, media: File, coupleId: String): String? {
        var tile: File? = null
        return try {
            tile = when (type) {
                MessageType.IMAGE -> mediaRepository.createThumbnail(media)
                MessageType.VIDEO -> mediaRepository.createVideoPoster(
                    media.absolutePath,
                    File(File(CherishApplication.instance.cacheDir, "compressed_media"), "poster_${media.nameWithoutExtension}.webp")
                )
                else -> null
            } ?: return null
            mediaRepository.uploadFile(tile, MessageType.IMAGE, coupleId).getOrNull()
                ?.takeIf { it.startsWith("https://") || it.startsWith("http://") }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w("ChatViewModel", "Couldn't send the gallery picture", e)
            null
        } finally {
            tile?.let { runCatching { it.delete() } }
        }
    }

    /** Video posters being made this session, by message, so each is only made once. */
    private val posterJobs = java.util.concurrent.ConcurrentHashMap<String, Deferred<Any?>>()
    /** Posters are made two at a time: each reads the start of a video. */
    private val posterSlots = kotlinx.coroutines.sync.Semaphore(2)

    /**
     * What a video's gallery tile shows: its poster. A video sent before posters existed gets one
     * the first time a phone shows it: made from the start of the video (not the whole video), kept
     * on this phone, and saved for both phones, so it's only ever made once. Null when no frame can
     * be read.
     */
    suspend fun videoPoster(message: Message, videoUrl: String): Any? {
        message.thumbnailFor(videoUrl).takeIf { it != videoUrl }?.let { return it }
        val job = posterJobs.computeIfAbsent(message.id) {
            viewModelScope.async(kotlinx.coroutines.Dispatchers.IO) { makeVideoPoster(message.id, videoUrl) }
        }
        return job.await()
    }

    private suspend fun makeVideoPoster(messageId: String, videoUrl: String): Any? = try {
        makeVideoPosterNow(messageId, videoUrl)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Log.w("ChatViewModel", "Couldn't make the poster of $messageId", e)
        null
    }

    private suspend fun makeVideoPosterNow(messageId: String, videoUrl: String): Any? = posterSlots.withPermit {
        val app = CherishApplication.instance
        // The other phone may have made it already
        val saved = chatRepository.fetchSavedPoster(messageId)
        if (!saved.isNullOrEmpty()) return@withPermit saved
        val safeId = messageId.replace(Regex("[^A-Za-z0-9_-]"), "_")
        val posterFile = File(File(app.cacheDir, "video_posters"), "poster_$safeId.webp")
        if (!posterFile.exists() || posterFile.length() == 0L) {
            // The video itself when it's on this phone (sent from here, or played here already)
            val localVideo = com.example.util.VideoThumbnailHelper.getVideoCacheFile(app, videoUrl)
                .takeIf { it.exists() && it.length() > 1024 }
            mediaRepository.createVideoPoster(localVideo?.absolutePath ?: videoUrl, posterFile)
                ?: return@withPermit null
        }
        // Saved for both phones when the server could be asked and has none yet; otherwise this
        // phone's copy is shown, and the next session tries again
        if (saved != null) {
            val uploaded = mediaRepository.uploadFile(posterFile, MessageType.IMAGE, chatRepository.getConversationId())
                .getOrNull()
                ?.takeIf { it.startsWith("https://") || it.startsWith("http://") }
            if (uploaded != null) chatRepository.savePoster(messageId, uploaded)
        }
        posterFile
    }

    fun sendMultipleImages(uris: List<Uri>) {
        if (uris.isEmpty()) return
        fun labelFor(count: Int) = if (count == 1) "photo" else "$count photos"
        runUpload(labelFor(uris.size), retry = { sendMultipleImages(uris) }) {
            val coupleId = chatRepository.getConversationId()
            val uploadedUrls = mutableListOf<String>()
            // Each photo's gallery thumbnail, in the same order ("" for one that couldn't be made)
            val thumbnailUrls = mutableListOf<String>()
            val notSent = mutableListOf<Uri>()
            val tempFiles = mutableListOf<File>()
            try {
                for ((index, uri) in uris.withIndex()) {
                    var thumbnail: String? = null
                    val url = try {
                        val preparedFile = mediaRepository.compressAndPrepareImage(uri)
                        tempFiles.add(preparedFile)
                        val tilePicture = async { uploadTilePicture(MessageType.IMAGE, preparedFile, coupleId) }
                        val uploaded = mediaRepository.uploadFile(
                            file = preparedFile,
                            type = MessageType.IMAGE,
                            coupleId = coupleId,
                            onProgress = { p -> reportUploadProgress((index + p) / uris.size.toFloat()) }
                        ).getOrNull()
                        if (uploaded != null && isDeliverable(uploaded)) {
                            thumbnail = tilePicture.await()
                            uploaded
                        } else {
                            tilePicture.cancel()
                            null
                        }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        Log.w("ChatViewModel", "Photo ${index + 1} of ${uris.size} failed", e)
                        null
                    }
                    if (url != null) {
                        uploadedUrls.add(url)
                        thumbnailUrls.add(thumbnail.orEmpty())
                    } else {
                        notSent.add(uri)
                    }
                }
                if (uploadedUrls.isNotEmpty()) {
                    chatRepository.sendMessage(
                        text = if (uploadedUrls.size == 1) "Sent a photo" else "Sent ${uploadedUrls.size} photos",
                        type = MessageType.IMAGE,
                        mediaUrl = uploadedUrls.firstOrNull(),
                        mediaUrls = uploadedUrls,
                        replyTo = _uiState.value.replyingToMessage,
                        thumbnailUrls = if (thumbnailUrls.any { it.isNotBlank() }) thumbnailUrls else emptyList()
                    )
                    _uiState.update { it.copy(replyingToMessage = null) }
                }
            } finally {
                tempFiles.forEach { runCatching { it.delete() } }
            }
            // Only the photos that didn't go out are offered for retry
            if (notSent.isEmpty()) null else FailedUpload(labelFor(notSent.size)) { sendMultipleImages(notSent) }
        }
    }

    fun onRecordingAudioChanged(isRecording: Boolean) {
        authRepository.setRecordingAudio(isRecording)
    }

    fun startVoiceRecording() {
        val file = voiceRecorderHelper.startRecording()
        if (file != null) {
            authRepository.setRecordingAudio(true)
            soundEffectsPlayer.playSound(ChatSoundEffectsPlayer.SoundType.RECORD_START)
        }
    }

    fun stopAndSendVoiceRecording() {
        authRepository.setRecordingAudio(false)
        soundEffectsPlayer.playSound(ChatSoundEffectsPlayer.SoundType.RECORD_STOP)
        val amplitudesSnapshot = voiceRecorderHelper.amplitudes.value.toList()
        val (file, duration) = voiceRecorderHelper.stopRecording()
        if (file != null && duration > 0) {
            soundEffectsPlayer.playSound(ChatSoundEffectsPlayer.SoundType.SENT)
            val waveform = if (amplitudesSnapshot.isNotEmpty()) amplitudesSnapshot else listOf(0.3f, 0.6f, 0.4f, 0.7f, 0.5f)
            sendVoiceMessage(file, duration, waveform)
        }
    }

    private fun sendVoiceMessage(file: File, duration: Int, waveform: List<Float>) {
        val retry = { sendVoiceMessage(file, duration, waveform) }
        runUpload("voice message", retry) {
            val url = mediaRepository.uploadFile(
                file = file,
                type = MessageType.AUDIO,
                coupleId = chatRepository.getConversationId(),
                onProgress = ::reportUploadProgress
            ).getOrNull()
            if (url == null || !isDeliverable(url)) return@runUpload FailedUpload("voice message", retry)
            chatRepository.sendMessage(
                text = "Voice message ($duration s)",
                type = MessageType.AUDIO,
                mediaUrl = url,
                durationSeconds = duration,
                waveform = waveform
            )
            null
        }
    }

    fun cancelVoiceRecording() {
        authRepository.setRecordingAudio(false)
        voiceRecorderHelper.cancelRecording()
    }

    fun playAudio(messageId: String, audioUrl: String) {
        // "Not heard yet" is about the other one listening: my own replay doesn't count
        val message = _uiState.value.messages.find { it.id == messageId }
        val myId = _uiState.value.currentUser?.id
        if (message != null && message.senderId != myId && !message.isAudioPlayed) {
            chatRepository.markAudioPlayed(messageId)
        }
        voicePlayerHelper.playAudio(
            messageId,
            audioUrl,
            onCompletion = { playNextVoiceNote(messageId) },
            // Its recorded length, for when the player can't read one from the stream
            durationHintSec = message?.durationSeconds ?: 0
        )
    }

    /** When a voice note ends, the next message plays on if it's another voice note from the same person. */
    private fun playNextVoiceNote(finishedId: String) {
        val messages = _uiState.value.messages
        val index = messages.indexOfFirst { it.id == finishedId }
        if (index < 0) return
        val next = messages.getOrNull(index + 1) ?: return
        if (next.getTypedType() != MessageType.AUDIO || next.senderId != messages[index].senderId) return
        next.mediaUrl?.takeIf { it.isNotBlank() }?.let { url -> playAudio(next.id, url) }
    }

    fun seekAudio(progress: Float) {
        voicePlayerHelper.seekTo(progress)
    }

    fun sendVideoNote(videoFile: java.io.File, durationSeconds: Int) {
        val retry = { sendVideoNote(videoFile, durationSeconds) }
        runUpload("video note", retry) {
            val coupleId = chatRepository.getConversationId()
            // Its gallery poster goes up alongside
            val poster = async { uploadTilePicture(MessageType.VIDEO, videoFile, coupleId) }
            val url = mediaRepository.uploadFile(
                file = videoFile,
                type = MessageType.VIDEO,
                coupleId = coupleId,
                onProgress = ::reportUploadProgress
            ).getOrNull()
            if (url == null || !isDeliverable(url)) {
                poster.cancel()
                return@runUpload FailedUpload("video note", retry)
            }
            com.example.util.VideoThumbnailHelper.cacheLocalVideo(com.example.CherishApplication.instance, videoFile, url)
            soundEffectsPlayer.playSound(ChatSoundEffectsPlayer.SoundType.SENT)
            chatRepository.sendMessage(
                text = "Video note ($durationSeconds s)",
                type = MessageType.VIDEO,
                mediaUrl = url,
                mediaName = "videonote_${System.currentTimeMillis()}.mp4",
                durationSeconds = durationSeconds,
                isVideoNote = true,
                thumbnailUrls = listOfNotNull(poster.await())
            )
            null
        }
    }

    fun setHeartbeatTouch(active: Boolean) {
        authRepository.setHeartbeatTouch(active)
    }

    fun syncHeartbeatStreak() {
        authRepository.syncHeartbeatStreak()
    }

    fun updateMood(mood: String) {
        viewModelScope.launch {
            authRepository.updateMood(mood)
        }
    }

    /** One tap: a heartbeat on the partner's phone. Rate-limited so taps don't stack up. */
    fun sendThinkingOfYou(): Boolean {
        val now = System.currentTimeMillis()
        if (now - lastThinkingOfYouAt < 3_000L) return false
        lastThinkingOfYouAt = now
        val sent = authRepository.sendThinkingOfYou()
        // The sender feels the same thump-thump as it goes out
        if (sent) com.example.notifications.ThinkingOfYou.playHeartbeat(CherishApplication.instance)
        return sent
    }

    private var lastThinkingOfYouAt = 0L
    private var isSendingGoodnight = false
    private var isSendingGoodMorning = false

    /** The goodnight message; it dims the partner's screen with stars when they see it. */
    fun sendGoodnight() {
        if (isSendingGoodnight) return
        val currentUserId = _uiState.value.currentUser?.id.orEmpty()
        val tonightStart = tonightStartMillis()
        val alreadySentTonight = _uiState.value.messages.any {
            it.senderId == currentUserId &&
            it.timestamp >= tonightStart &&
            it.effect == EFFECT_GOODNIGHT &&
            !it.isDeleted
        }
        if (alreadySentTonight) return

        isSendingGoodnight = true
        viewModelScope.launch {
            try {
                chatRepository.sendMessage(text = "Goodnight, sleep well my love \uD83C\uDF19\u2728", effect = EFFECT_GOODNIGHT)
            } finally {
                kotlinx.coroutines.delay(1000)
                isSendingGoodnight = false
            }
        }
    }

    /** The good morning message; sends automatically when the sunrise banner appears. */
    fun sendGoodMorning(partnerName: String = "") {
        if (isSendingGoodMorning) return
        val currentUserId = _uiState.value.currentUser?.id.orEmpty()
        val todayStart = todayStartMillis()
        val alreadySentToday = _uiState.value.messages.any {
            it.senderId == currentUserId &&
            it.timestamp >= todayStart &&
            (it.effect == EFFECT_GOODMORNING || it.text.contains("good morning", ignoreCase = true)) &&
            !it.isDeleted
        }
        if (alreadySentToday) return

        isSendingGoodMorning = true
        viewModelScope.launch {
            try {
                chatRepository.sendMessage(
                    text = "Good morning! \uD83C\uDF04\u2600\uFE0F\uD83D\uDC9B",
                    effect = EFFECT_GOODMORNING
                )
            } finally {
                kotlinx.coroutines.delay(1000)
                isSendingGoodMorning = false
            }
        }
    }

    /**
     * A hug held on the 🫂 button, [level] tight (0 a light embrace, 1 a deep tight squeeze): a 🫂
     * that plays that tight on both phones (see HugSqueeze).
     */
    fun sendHugSqueeze(level: Float) {
        soundEffectsPlayer.playSound(ChatSoundEffectsPlayer.SoundType.SENT)
        viewModelScope.launch {
            chatRepository.sendMessage(text = "🫂", effect = hugEffect(level))
        }
    }

    fun sendHug() {
        viewModelScope.launch {
            chatRepository.sendMessage(text = "Sending you the biggest hug \uD83E\uDD17\uD83D\uDC9B")
        }
    }

    fun clearMood() {
        viewModelScope.launch {
            authRepository.updateMood("")
        }
    }

    fun markMessagesAsRead(messageIds: List<String>) {
        if (messageIds.isEmpty()) return
        viewModelScope.launch {
            chatRepository.markAsRead(messageIds)
        }
    }

    fun toggleReaction(messageId: String, emoji: String) {
        soundEffectsPlayer.playSound(ChatSoundEffectsPlayer.SoundType.REACTION)
        viewModelScope.launch {
            chatRepository.toggleReaction(messageId, emoji)
        }
    }

    fun toggleStar(messageId: String) {
        soundEffectsPlayer.playSound(ChatSoundEffectsPlayer.SoundType.STAR)
        viewModelScope.launch {
            chatRepository.toggleStar(messageId)
        }
    }

    fun togglePin(messageId: String) {
        viewModelScope.launch {
            chatRepository.togglePin(messageId)
        }
    }

    fun toggleVoiceSpeed() {
        val newSpeed = voicePlayerHelper.togglePlaybackSpeed()
        _uiState.update { it.copy(voicePlaybackSpeed = newSpeed) }
    }

    fun openTheaterVideo(videoId: String) {
        securityPreferences.isTheaterModeActive = true
        _uiState.update { it.copy(theaterVideoId = videoId) }
    }

    fun closeTheaterVideo() {
        securityPreferences.isTheaterModeActive = false
        _uiState.update { it.copy(theaterVideoId = null) }
    }

    fun toggleFilterStarred() {
        _uiState.update { it.copy(filterStarredOnly = !it.filterStarredOnly) }
    }

    fun editMessage(messageId: String, newText: String) {
        viewModelScope.launch {
            chatRepository.editMessage(messageId, newText)
        }
    }

    fun deleteMessage(messageId: String) {
        deleteMessages(listOf(messageId))
    }

    fun deleteMessages(messageIds: Collection<String>) {
        val ids = messageIds.toSet()
        if (ids.isEmpty()) return
        soundEffectsPlayer.playSound(ChatSoundEffectsPlayer.SoundType.DELETE)
        if (voicePlayerHelper.currentTrackId.value in ids) voicePlayerHelper.stop()
        viewModelScope.launch {
            chatRepository.deleteMessages(ids)
        }
    }

    /** Deletes individual photos/videos; multi-photo messages keep their other photos. */
    fun deleteGalleryMedia(messages: List<Message>, mediaUrls: Set<String>) {
        if (messages.isEmpty() || mediaUrls.isEmpty()) return
        soundEffectsPlayer.playSound(ChatSoundEffectsPlayer.SoundType.DELETE)
        viewModelScope.launch {
            chatRepository.removeMediaUrls(messages, mediaUrls)
        }
    }

    fun setReplyingTo(message: Message?) {
        _uiState.update { it.copy(replyingToMessage = message) }
    }

    fun setSelectedMessageForActions(message: Message?) {
        _uiState.update { it.copy(selectedMessageForActions = message) }
    }

    fun setSearchQuery(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
    }

    fun setSearching(searching: Boolean) {
        _uiState.update {
            it.copy(
                isSearching = searching,
                searchQuery = if (!searching) "" else it.searchQuery
            )
        }
    }

    fun openFullScreenMedia(url: String, type: MessageType, allUrls: List<String> = emptyList(), isVideoNote: Boolean = false) {
        _uiState.update {
            it.copy(
                fullScreenMediaUrl = url,
                fullScreenMediaType = type,
                allMediaUrlsForViewer = if (allUrls.isNotEmpty()) allUrls else listOf(url),
                isFullScreenVideoNote = isVideoNote
            )
        }
    }

    fun setGallerySize(size: String) {
        securityPreferences.setImageGallerySize(size)
        _uiState.update { it.copy(gallerySize = size) }
    }

    fun closeFullScreenMedia() {
        _uiState.update { it.copy(fullScreenMediaUrl = null, fullScreenMediaType = null, allMediaUrlsForViewer = emptyList(), isFullScreenVideoNote = false) }
    }

    /**
     * The secret app went behind Notes (emergency exit, or leaving the app): nothing of it may stay
     * open, recording or playing, and the partner shouldn't keep seeing "typing…".
     */
    fun onSecretAppHidden() {
        closeFullScreenMedia()
        closeTheaterVideo()
        if (voiceRecorderHelper.isRecording.value) cancelVoiceRecording()
        voicePlayerHelper.stop()
        setSearching(false)
        setSelectedMessageForActions(null)
        onTypingChanged(false)
    }

    // --- SECRET HISTORY PROTECTION & RECOVERY ---
    fun recoverAllMessagesAndGallery() {
        securityPreferences.setShowPreviousChatsEnabled(true)
        securityPreferences.setTemporaryClearTimestamp(0L)
        securityPreferences.setAllGalleryRecovered(true)
        chatRepository.recoverAllMessages()
    }

    fun revealSecretHistory() {
        securityPreferences.revealSecretHistory()
        viewModelScope.launch {
            chatRepository.loadMoreMessages()
        }
    }

    fun hideSecretHistory() {
        securityPreferences.hideSecretHistory()
    }

    fun toggleSecretHistory() {
        if (_uiState.value.isSecretHistoryRevealed) {
            securityPreferences.hideSecretHistory()
        } else {
            securityPreferences.revealSecretHistory()
        }
    }

    /**
     * "Show in chat": jumps to the message. Today's messages are always there; an older one only
     * after "Recover all", loading earlier pages until it's found. False when it's older and Recover
     * all hasn't been used (nothing older is revealed).
     */
    fun navigateToMessageInChat(messageId: String): Boolean {
        val target = _uiState.value.messages.find { it.id == messageId }
            ?: chatRepository.galleryMediaMessages.value.find { it.id == messageId }
        val isTodays = target != null && com.example.util.ChatTimeFormatter.isToday(target.timestamp)
        if (!isTodays) {
            val recovered = securityPreferences.isAllGalleryRecovered() || securityPreferences.isShowPreviousChatsEnabled()
            if (!recovered) return false
            securityPreferences.revealSecretHistory()
            securityPreferences.setShowPreviousChatsEnabled(true)
            val sentAt = target?.timestamp
            if (sentAt != null) {
                // All of the chat down to it from the phone's archive in one go, else page by page
                chatRepository.showArchivedDownTo(sentAt) { shown -> if (!shown) loadHistoryUntil(messageId) }
            } else {
                loadHistoryUntil(messageId)
            }
        }
        _uiState.update { it.copy(targetScrollMessageId = messageId) }
        return true
    }

    /**
     * The calendar in chat search: closes the search and jumps to the first message of that day,
     * or of the next available day with messages. Loads earlier pages until the chat reaches it.
     */
    fun jumpToDate(year: Int, month: Int, dayOfMonth: Int, onResult: (DateJumpResult) -> Unit) {
        val calStart = java.util.Calendar.getInstance().apply {
            clear()
            set(year, month, dayOfMonth, 0, 0, 0)
            set(java.util.Calendar.MILLISECOND, 0)
        }
        val calEnd = java.util.Calendar.getInstance().apply {
            clear()
            set(year, month, dayOfMonth, 23, 59, 59)
            set(java.util.Calendar.MILLISECOND, 999)
        }
        val dayStart = calStart.timeInMillis
        val dayEnd = calEnd.timeInMillis

        // When user explicitly searches or jumps to a date, reveal history and show older chats
        securityPreferences.revealSecretHistory()
        securityPreferences.setShowPreviousChatsEnabled(true)
        setSearching(false)
        _uiState.update {
            it.copy(
                filterStarredOnly = false,
                showPreviousChats = true,
                isSecretHistoryRevealed = true
            )
        }

        viewModelScope.launch {
            // Earlier pages, until the chat reaches back to that day (or its history ends)
            var pages = 0
            while (pages++ < MAX_PAGES_TO_FIND_MESSAGE && !chatRepository.isQueryExhausted) {
                val oldest = _uiState.value.messages.minOfOrNull { it.timestamp } ?: break
                if (oldest <= dayStart) break
                chatRepository.loadMoreMessages()
                kotlinx.coroutines.delay(180)
                kotlinx.coroutines.withTimeoutOrNull(8_000L) {
                    while (chatRepository.isLoadingMore) kotlinx.coroutines.delay(100)
                }
            }

            val clearedBefore = _uiState.value.temporaryClearTimestamp
            val candidates = _uiState.value.messages
                .filter { !it.isDeleted && it.timestamp > clearedBefore }

            // 1. First look for message on the exact selected day (calendar day or 6 AM logical day)
            val targetOnDay = candidates
                .filter { msg ->
                    (msg.timestamp in dayStart..dayEnd) ||
                    com.example.util.ChatTimeFormatter.isSameDay(msg.timestamp, dayStart + 12 * 3600 * 1000L)
                }
                .minByOrNull { it.timestamp }

            // 2. If no messages on that exact day, find the closest next messages after that day
            val target = targetOnDay ?: candidates
                .filter { it.timestamp >= dayStart }
                .minByOrNull { it.timestamp }

            if (target == null) {
                onResult(DateJumpResult.NoMessages)
                return@launch
            }

            _uiState.update { it.copy(targetScrollMessageId = target.id, showPreviousChats = true) }
            onResult(if (targetOnDay != null) DateJumpResult.Found else DateJumpResult.NextDayFound)
        }
    }

    /** Loads earlier pages of chat until [messageId] is among them (or the history ends). */
    private fun loadHistoryUntil(messageId: String) {
        viewModelScope.launch {
            repeat(MAX_PAGES_TO_FIND_MESSAGE) {
                if (_uiState.value.messages.any { it.id == messageId } || chatRepository.isQueryExhausted) return@launch
                chatRepository.loadMoreMessages()
                kotlinx.coroutines.delay(150)
                kotlinx.coroutines.withTimeoutOrNull(10_000L) {
                    while (chatRepository.isLoadingMore) kotlinx.coroutines.delay(150)
                }
            }
        }
    }

    // --- Scheduled messages (sent by an alarm at their time, even with the app closed) ---

    val scheduledMessages: StateFlow<List<com.example.notifications.ScheduledMessage>> =
        com.example.notifications.ScheduledMessageStore.messages

    fun scheduleMessage(text: String, sendAt: Long) {
        val clean = text.trim()
        if (clean.isEmpty()) return
        val context = CherishApplication.instance
        val message = com.example.notifications.ScheduledMessage(java.util.UUID.randomUUID().toString(), clean, sendAt)
        com.example.notifications.ScheduledMessageStore.add(context, message)
        com.example.notifications.ScheduledMessageScheduler.schedule(context, message)
    }

    fun cancelScheduledMessage(id: String) {
        val context = CherishApplication.instance
        com.example.notifications.ScheduledMessageScheduler.cancel(context, id)
        com.example.notifications.ScheduledMessageStore.remove(context, id)
    }

    fun clearTargetScrollMessageId() {
        _uiState.update { it.copy(targetScrollMessageId = null) }
    }

    // --- STEALTH PRIVACY SHIELD (Hide previous chats with secret gesture) ---
    fun toggleStealthCurtain() {
        val newState = !_uiState.value.isStealthCurtainActive
        val hasSeenTip = securityPreferences.hasSeenStealthShieldTip()
        val toastMessage = if (!hasSeenTip) {
            securityPreferences.setHasSeenStealthShieldTip(true)
            "🛡️ Stealth Shield: Chats hidden behind notes. Triple-tap to restore."
        } else {
            null
        }
        _uiState.update {
            it.copy(
                isStealthCurtainActive = newState,
                stealthToastMessage = toastMessage
            )
        }
    }

    fun setStealthCurtain(active: Boolean) {
        val hasSeenTip = securityPreferences.hasSeenStealthShieldTip()
        val toastMessage = if (!hasSeenTip && active) {
            securityPreferences.setHasSeenStealthShieldTip(true)
            "🛡️ Stealth Shield: Chats hidden behind notes. Triple-tap to restore."
        } else {
            null
        }
        _uiState.update {
            it.copy(
                isStealthCurtainActive = active,
                stealthToastMessage = toastMessage
            )
        }
    }

    fun clearStealthToast() {
        _uiState.update { it.copy(stealthToastMessage = null) }
    }

    // --- DUAL-CONSENT CHAT DELETION (Both must accept before deleting) ---
    fun requestMutualChatDeletion(scope: String = "ALL_MESSAGES", targetMessageId: String? = null) {
        chatRepository.requestChatDeletion(scope, targetMessageId)
    }

    fun acceptMutualChatDeletion() {
        viewModelScope.launch {
            chatRepository.acceptChatDeletion()
        }
    }

    fun declineMutualChatDeletion() {
        chatRepository.declineChatDeletion()
    }

    fun cancelMutualChatDeletion() {
        chatRepository.cancelChatDeletion()
    }

    // --- CHECK-AFTER FEATURE ---
    /** Personal space: a Check-After over 2 days is asked of the partner first. */
    val spaceRequest: StateFlow<com.example.data.model.SpaceRequest?> = authRepository.spaceRequest

    fun requestSpace(targetMillis: Long, note: String): Boolean = authRepository.requestSpace(targetMillis, note)

    fun respondToSpaceRequest(accept: Boolean) = authRepository.respondToSpaceRequest(accept)

    fun cancelSpaceRequest() = authRepository.cancelSpaceRequest()

    fun isSpaceAnswerSeen(requestId: String) = authRepository.isSpaceAnswerSeen(requestId)

    fun markSpaceAnswerSeen(requestId: String) = authRepository.markSpaceAnswerSeen(requestId)

    fun openCheckAfterSheet() {
        _uiState.update { it.copy(isCheckAfterSheetOpen = true) }
    }

    fun closeCheckAfterSheet() {
        _uiState.update { it.copy(isCheckAfterSheetOpen = false) }
    }

    fun setCheckAfter(targetTimeMillis: Long, note: String = "") {
        authRepository.setCheckAfter(targetTimeMillis, note)
    }

    fun cancelCheckAfter() {
        authRepository.cancelCheckAfter()
    }

    fun extendCheckAfter(additionalMillis: Long) {
        authRepository.extendCheckAfter(additionalMillis)
    }

    fun toggleCheckAfterReminder(enabled: Boolean) {
        securityPreferences.setCheckAfterReminderEnabled(enabled)
        _uiState.update { it.copy(isCheckAfterReminderEnabled = enabled) }
    }

    fun logout() {
        authRepository.logout()
    }

    /**
     * Safely stop voice recording when the app goes to background.
     * Prevents the recording UI from remaining stuck on resume.
     */
    fun safeStopRecordingForBackground() {
        if (voiceRecorderHelper.isRecording.value) {
            authRepository.setRecordingAudio(false)
            voiceRecorderHelper.safeStopForBackground()
        }
    }

    override fun onCleared() {
        super.onCleared()
        voicePlayerHelper.stop()
        voiceRecorderHelper.cancelRecording()
        authRepository.setTyping(false)
        authRepository.setRecordingAudio(false)
        authRepository.setListening(null)
        authRepository.setKissTouch(null, null)
    }

    private companion object {
        const val LISTENING_SETTLE_MS = 300L
        const val KISS_EVERY_MS = 1_000L
        const val TYPING_IDLE_TIMEOUT_MS = 5_000L
        const val MAX_PAGES_TO_FIND_MESSAGE = 20
        /** How much of a video's progress bar is making it smaller (the rest is uploading). */
        const val VIDEO_COMPRESS_SHARE = 0.4f
    }
}

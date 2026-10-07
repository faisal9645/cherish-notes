package com.example.ui.chat

import android.content.Context
import android.net.Uri
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
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.io.File

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
    val allMediaUrlsForViewer: List<String> = emptyList(),
    val gallerySize: String = "large",
    val isUploadingMedia: Boolean = false,
    val uploadProgress: Float = 0f,
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
    val chatExperienceMode: ChatExperienceMode = ChatExperienceMode.NORMAL
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

    private val _uiState = MutableStateFlow(
        ChatUiState(
            gallerySize = securityPreferences.getImageGallerySize(),
            chatBgTheme = securityPreferences.getChatBgTheme(),
            chatExperienceMode = securityPreferences.getChatExperienceMode(),
            showPreviousChats = securityPreferences.isShowPreviousChatsEnabled(),
            isSecretHistoryRevealed = securityPreferences.isSecretHistoryRevealed.value,
            temporaryClearTimestamp = securityPreferences.getTemporaryClearTimestamp()
        )
    )
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    // Preserved scroll position when navigating between tabs (Chat, Love & Us, Settings)
    var savedScrollIndex: Int = 0
    var savedScrollOffset: Int = 0

    init {
        authRepository.connectPartnerListenerOnce()
        authRepository.updatePresence()

        // Collect messages from the single global listener in ChatRepository.
        // No duplicate listener creation — ChatRepository.init manages the Firestore listener
        // and switches it when coupleId changes via its own collectLatest.
        var highestMessageTimestamp = 0L
        viewModelScope.launch {
            chatRepository.messagesFlow.collect { msgList ->
                val pinned = msgList.lastOrNull { it.isPinned && !it.isDeleted }
                val currentUserId = authRepository.getCurrentUserId()
                val newIncoming = msgList.lastOrNull { it.senderId != currentUserId && it.timestamp > highestMessageTimestamp }
                if (highestMessageTimestamp > 0L && newIncoming != null) {
                    soundEffectsPlayer.playSound(ChatSoundEffectsPlayer.SoundType.RECEIVED)
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

        viewModelScope.launch {
            authRepository.currentUserState.collect { user ->
                _uiState.update { it.copy(currentUser = user) }
            }
        }

        viewModelScope.launch {
            authRepository.partnerUserState.collect { partner ->
                val partnerTouching = partner?.heartbeatTouchingTimestamp?.let { it > 0L && (System.currentTimeMillis() - it < 4500L) } ?: false
                _uiState.update {
                    it.copy(
                        partnerUser = partner,
                        isPartnerOnline = partner?.isEffectivelyOnline() ?: false,
                        isPartnerTyping = partner?.isEffectivelyTyping() ?: false,
                        isPartnerRecordingAudio = partner?.isEffectivelyRecording() ?: false,
                        isPartnerHeartTouching = partnerTouching
                    )
                }
            }
        }

        viewModelScope.launch {
            securityPreferences.chatBgTheme.collect { theme ->
                _uiState.update { it.copy(chatBgTheme = theme) }
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
        
        // Ticker to evaluate effective online/typing/heartbeat status without unnecessary CPU load
        viewModelScope.launch {
            while (true) {
                val delayMs = if (_uiState.value.isPartnerHeartTouching || (_uiState.value.partnerUser?.heartbeatTouchingTimestamp ?: 0L) > 0L) 500L else 2000L
                kotlinx.coroutines.delay(delayMs)
                _uiState.update { state ->
                    val partner = state.partnerUser
                    val partnerTouching = partner?.heartbeatTouchingTimestamp?.let { it > 0L && (System.currentTimeMillis() - it < 4500L) } ?: false
                    val newOnline = partner?.isEffectivelyOnline() ?: false
                    val newTyping = partner?.isEffectivelyTyping() ?: false
                    val newRecording = partner?.isEffectivelyRecording() ?: false

                    if (state.isPartnerOnline == newOnline &&
                        state.isPartnerTyping == newTyping &&
                        state.isPartnerRecordingAudio == newRecording &&
                        state.isPartnerHeartTouching == partnerTouching
                    ) {
                        state // No change: return identical instance to prevent recomposition cascades
                    } else {
                        state.copy(
                            isPartnerOnline = newOnline,
                            isPartnerTyping = newTyping,
                            isPartnerRecordingAudio = newRecording,
                            isPartnerHeartTouching = partnerTouching
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

    /** Called on every keystroke; the repository only writes when the value changes. */
    fun onTypingChanged(isTyping: Boolean) {
        typingIdleJob?.cancel()
        authRepository.setTyping(isTyping)
        if (isTyping) {
            // Stop showing "typing…" to the partner once the keyboard has been idle a while
            typingIdleJob = viewModelScope.launch {
                kotlinx.coroutines.delay(TYPING_IDLE_TIMEOUT_MS)
                authRepository.setTyping(false)
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

    fun sendMediaFile(uri: Uri, type: MessageType, mediaName: String? = null) {
        viewModelScope.launch {
            _uiState.update { it.copy(isUploadingMedia = true, uploadProgress = 0.1f) }
            try {
                val coupleId = chatRepository.getConversationId()
                val preparedFile = if (type == MessageType.IMAGE) {
                    mediaRepository.compressAndPrepareImage(uri)
                } else {
                    File(uri.path ?: "")
                }

                // The preview uploads alongside the photo, so it doesn't delay the send
                val thumbnail = if (type == MessageType.IMAGE) async { uploadThumbnail(preparedFile, coupleId) } else null
                val uploadResult = mediaRepository.uploadFile(
                    file = preparedFile,
                    type = type,
                    coupleId = coupleId,
                    onProgress = { p -> _uiState.update { it.copy(uploadProgress = p) } }
                )

                uploadResult.fold(
                    onSuccess = { downloadUrl ->
                        val thumbnailUrl = thumbnail?.await().orEmpty()
                        chatRepository.sendMessage(
                            text = if (type == MessageType.IMAGE) "Sent a photo" else "Sent a file",
                            type = type,
                            mediaUrl = downloadUrl,
                            mediaName = mediaName ?: preparedFile.name,
                            mediaSize = preparedFile.length(),
                            replyTo = _uiState.value.replyingToMessage,
                            thumbnailUrls = if (thumbnailUrl.isNotBlank()) listOf(thumbnailUrl) else emptyList()
                        )
                        _uiState.update { it.copy(replyingToMessage = null) }
                    },
                    onFailure = {
                        thumbnail?.cancel()
                    }
                )
            } finally {
                _uiState.update { it.copy(isUploadingMedia = false, uploadProgress = 0f) }
            }
        }
    }

    /**
     * Uploads a small preview of a prepared photo; "" when it can't. Only real storage URLs are
     * used, never the inline data fallback, so previews can't bloat the message document.
     */
    private suspend fun uploadThumbnail(image: File, coupleId: String): String {
        val thumbnail = mediaRepository.createThumbnail(image) ?: return ""
        val url = mediaRepository.uploadFile(file = thumbnail, type = MessageType.IMAGE, coupleId = coupleId)
            .getOrNull()
            .orEmpty()
        return if (url.startsWith("https://") || url.startsWith("http://")) url else ""
    }

    fun sendMultipleImages(uris: List<Uri>) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            _uiState.update { it.copy(isUploadingMedia = true, uploadProgress = 0.05f) }
            val uploadedUrls = mutableListOf<String>()
            // Same order as uploadedUrls; each resolves to "" where a preview couldn't be made
            val thumbnails = mutableListOf<Deferred<String>>()
            val coupleId = chatRepository.getConversationId()
            try {
                for ((index, uri) in uris.withIndex()) {
                    try {
                        val preparedFile = mediaRepository.compressAndPrepareImage(uri)
                        // The preview uploads alongside the photo, so it doesn't delay the send
                        val thumbnail = async { uploadThumbnail(preparedFile, coupleId) }
                        val uploadResult = mediaRepository.uploadFile(
                            file = preparedFile,
                            type = MessageType.IMAGE,
                            coupleId = coupleId,
                            onProgress = { p ->
                                val overall = (index + p) / uris.size.toFloat()
                                _uiState.update { it.copy(uploadProgress = overall) }
                            }
                        )
                        val url = uploadResult.getOrNull()
                        if (url != null) {
                            uploadedUrls.add(url)
                            thumbnails.add(thumbnail)
                        } else {
                            thumbnail.cancel()
                        }
                    } catch (_: Exception) {}
                }
                if (uploadedUrls.isNotEmpty()) {
                    val thumbnailUrls = thumbnails.awaitAll()
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
                _uiState.update { it.copy(isUploadingMedia = false, uploadProgress = 0f) }
            }
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
            viewModelScope.launch {
                val coupleId = chatRepository.getConversationId()
                val uploadResult = mediaRepository.uploadFile(
                    file = file,
                    type = MessageType.AUDIO,
                    coupleId = coupleId
                )

                uploadResult.onSuccess { downloadUrl ->
                    chatRepository.sendMessage(
                        text = "Voice message ($duration s)",
                        type = MessageType.AUDIO,
                        mediaUrl = downloadUrl,
                        durationSeconds = duration,
                        waveform = if (amplitudesSnapshot.isNotEmpty()) amplitudesSnapshot else listOf(0.3f, 0.6f, 0.4f, 0.7f, 0.5f)
                    )
                }
            }
        }
    }

    fun cancelVoiceRecording() {
        authRepository.setRecordingAudio(false)
        voiceRecorderHelper.cancelRecording()
    }

    fun playAudio(messageId: String, audioUrl: String) {
        voicePlayerHelper.playAudio(messageId, audioUrl)
    }

    fun seekAudio(progress: Float) {
        voicePlayerHelper.seekTo(progress)
    }

    fun sendVideoNote(videoFile: java.io.File, durationSeconds: Int) {
        val coupleId = chatRepository.getConversationId()
        viewModelScope.launch {
            _uiState.update { it.copy(isUploadingMedia = true, uploadProgress = 0f) }
            val uploadResult = mediaRepository.uploadFile(
                file = videoFile,
                type = MessageType.VIDEO,
                coupleId = coupleId,
                onProgress = { p -> _uiState.update { it.copy(uploadProgress = p) } }
            )
            _uiState.update { it.copy(isUploadingMedia = false, uploadProgress = 0f) }
            uploadResult.onSuccess { downloadUrl ->
                soundEffectsPlayer.playSound(ChatSoundEffectsPlayer.SoundType.SENT)
                chatRepository.sendMessage(
                    text = "Video note ($durationSeconds s)",
                    type = MessageType.VIDEO,
                    mediaUrl = downloadUrl,
                    mediaName = "videonote_${System.currentTimeMillis()}.mp4",
                    durationSeconds = durationSeconds,
                    isVideoNote = true
                )
            }
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

    fun openFullScreenMedia(url: String, type: MessageType, allUrls: List<String> = emptyList()) {
        _uiState.update {
            it.copy(
                fullScreenMediaUrl = url,
                fullScreenMediaType = type,
                allMediaUrlsForViewer = if (allUrls.isNotEmpty()) allUrls else listOf(url)
            )
        }
    }

    fun setGallerySize(size: String) {
        securityPreferences.setImageGallerySize(size)
        _uiState.update { it.copy(gallerySize = size) }
    }

    fun closeFullScreenMedia() {
        _uiState.update { it.copy(fullScreenMediaUrl = null, fullScreenMediaType = null, allMediaUrlsForViewer = emptyList()) }
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

    fun navigateToMessageInChat(messageId: String) {
        securityPreferences.revealSecretHistory()
        securityPreferences.setShowPreviousChatsEnabled(true)
        chatRepository.expandLimitForSearch()
        _uiState.update {
            it.copy(
                targetScrollMessageId = messageId
            )
        }
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
    }

    private companion object {
        const val TYPING_IDLE_TIMEOUT_MS = 5_000L
    }
}

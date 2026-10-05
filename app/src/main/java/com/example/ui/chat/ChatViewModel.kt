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

    init {
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
                _uiState.update {
                    it.copy(
                        partnerUser = partner,
                        isPartnerOnline = partner?.isEffectivelyOnline() ?: false,
                        isPartnerTyping = partner?.isEffectivelyTyping() ?: false,
                        isPartnerRecordingAudio = partner?.isEffectivelyRecording() ?: false
                    )
                }
            }
        }

        // Periodically re-evaluate presence because time passes even without Firestore updates
        viewModelScope.launch {
            while (true) {
                kotlinx.coroutines.delay(10_000L) // every 10 seconds
                val partner = _uiState.value.partnerUser
                _uiState.update {
                    it.copy(
                        isPartnerOnline = partner?.isEffectivelyOnline() ?: false,
                        isPartnerTyping = partner?.isEffectivelyTyping() ?: false,
                        isPartnerRecordingAudio = partner?.isEffectivelyRecording() ?: false
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
        
        // Ticker to continuously evaluate effective online/typing status
        viewModelScope.launch {
            while (true) {
                kotlinx.coroutines.delay(1000L)
                _uiState.update { state ->
                    val partner = state.partnerUser
                    val partnerTouching = partner?.heartbeatTouchingTimestamp?.let { System.currentTimeMillis() - it < 8000L } ?: false
                    state.copy(
                        isPartnerOnline = partner?.isEffectivelyOnline() ?: false,
                        isPartnerTyping = partner?.isEffectivelyTyping() ?: false,
                        isPartnerRecordingAudio = partner?.isEffectivelyRecording() ?: false,
                        isPartnerHeartTouching = partnerTouching
                    )
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

    fun loadAllGalleryMedia() {
        chatRepository.loadAllGalleryMedia()
    }

    val isQueryExhausted: Boolean
        get() = chatRepository.isQueryExhausted

    fun onTypingChanged(isTyping: Boolean) {
        authRepository.setTyping(isTyping)
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

                val uploadResult = mediaRepository.uploadFile(
                    file = preparedFile,
                    type = type,
                    coupleId = coupleId,
                    onProgress = { p -> _uiState.update { it.copy(uploadProgress = p) } }
                )

                uploadResult.fold(
                    onSuccess = { downloadUrl ->
                        chatRepository.sendMessage(
                            text = if (type == MessageType.IMAGE) "Sent a photo" else "Sent a file",
                            type = type,
                            mediaUrl = downloadUrl,
                            mediaName = mediaName ?: preparedFile.name,
                            mediaSize = preparedFile.length(),
                            replyTo = _uiState.value.replyingToMessage
                        )
                        _uiState.update { it.copy(replyingToMessage = null) }
                    },
                    onFailure = {
                        // ignore or handle error
                    }
                )
            } finally {
                _uiState.update { it.copy(isUploadingMedia = false, uploadProgress = 0f) }
            }
        }
    }

    fun sendMultipleImages(uris: List<Uri>) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            _uiState.update { it.copy(isUploadingMedia = true, uploadProgress = 0.05f) }
            val uploadedUrls = mutableListOf<String>()
            val coupleId = chatRepository.getConversationId()
            try {
                for ((index, uri) in uris.withIndex()) {
                    try {
                        val preparedFile = mediaRepository.compressAndPrepareImage(uri)
                        val uploadResult = mediaRepository.uploadFile(
                            file = preparedFile,
                            type = MessageType.IMAGE,
                            coupleId = coupleId,
                            onProgress = { p ->
                                val overall = (index + p) / uris.size.toFloat()
                                _uiState.update { it.copy(uploadProgress = overall) }
                            }
                        )
                        uploadResult.onSuccess { url ->
                            uploadedUrls.add(url)
                        }
                    } catch (_: Exception) {}
                }
                if (uploadedUrls.isNotEmpty()) {
                    chatRepository.sendMessage(
                        text = if (uploadedUrls.size == 1) "Sent a photo" else "Sent ${uploadedUrls.size} photos",
                        type = MessageType.IMAGE,
                        mediaUrl = uploadedUrls.firstOrNull(),
                        mediaUrls = uploadedUrls,
                        replyTo = _uiState.value.replyingToMessage
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

    fun markMessageAsRead(messageId: String) {
        viewModelScope.launch {
            chatRepository.markAsRead(messageId)
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
        _uiState.update { it.copy(theaterVideoId = videoId) }
    }

    fun closeTheaterVideo() {
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
        soundEffectsPlayer.playSound(ChatSoundEffectsPlayer.SoundType.DELETE)
        viewModelScope.launch {
            chatRepository.deleteMessage(messageId)
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
}

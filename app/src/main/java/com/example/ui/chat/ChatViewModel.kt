package com.example.ui.chat

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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
    val gallerySize: String = "medium",
    val isUploadingMedia: Boolean = false,
    val uploadProgress: Float = 0f,
    val deletionRequest: com.example.data.model.ChatDeletionRequest? = null,
    val isStealthCurtainActive: Boolean = false,
    val stealthToastMessage: String? = null,
    val isCheckAfterSheetOpen: Boolean = false,
    val isCheckAfterReminderEnabled: Boolean = true,
    val isPartnerRecordingAudio: Boolean = false,
    val pinnedMessage: Message? = null,
    val theaterVideoId: String? = null,
    val filterStarredOnly: Boolean = false,
    val voicePlaybackSpeed: Float = 1.0f,
    val isSecretHistoryRevealed: Boolean = false,
    val targetScrollMessageId: String? = null
)

class ChatViewModel(
    private val authRepository: AuthRepository,
    private val chatRepository: ChatRepository,
    private val mediaRepository: MediaRepository,
    val voiceRecorderHelper: VoiceRecorderHelper,
    val voicePlayerHelper: VoicePlayerHelper,
    val securityPreferences: SecurityPreferences = CherishApplication.instance.securityPreferences
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        ChatUiState(gallerySize = securityPreferences.getImageGallerySize())
    )
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            authRepository.currentUserState
                .map { it?.coupleId ?: "couple_cherish_love" }
                .distinctUntilChanged()
                .collectLatest { convId ->
                    chatRepository.listenToMessages(convId).collect { msgList ->
                        val pinned = msgList.lastOrNull { it.isPinned && !it.isDeleted }
                        _uiState.update {
                            it.copy(
                                messages = msgList,
                                pinnedMessage = pinned
                            )
                        }
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
                        isPartnerTyping = partner?.typingInChat ?: false,
                        isPartnerRecordingAudio = partner?.recordingAudioInChat ?: false
                    )
                }
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
    }

    fun onTypingChanged(isTyping: Boolean) {
        authRepository.setTyping(isTyping)
    }

    fun sendTextMessage(text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return
        val replyTo = _uiState.value.replyingToMessage

        viewModelScope.launch {
            _uiState.update { it.copy(replyingToMessage = null, isSecretHistoryRevealed = true) }
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
                        mediaUrl = uploadedUrls.joinToString(","),
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
        }
    }

    fun stopAndSendVoiceRecording() {
        authRepository.setRecordingAudio(false)
        val amplitudesSnapshot = voiceRecorderHelper.amplitudes.value.toList()
        val (file, duration) = voiceRecorderHelper.stopRecording()
        if (file != null && duration > 0) {
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

    fun markMessageAsRead(messageId: String) {
        viewModelScope.launch {
            chatRepository.markAsRead(messageId)
        }
    }

    fun toggleReaction(messageId: String, emoji: String) {
        viewModelScope.launch {
            chatRepository.toggleReaction(messageId, emoji)
        }
    }

    fun toggleStar(messageId: String) {
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
        _uiState.update { it.copy(isSecretHistoryRevealed = true) }
    }

    fun hideSecretHistory() {
        _uiState.update { it.copy(isSecretHistoryRevealed = false) }
    }

    fun toggleSecretHistory() {
        _uiState.update { it.copy(isSecretHistoryRevealed = !it.isSecretHistoryRevealed) }
    }

    fun navigateToMessageInChat(messageId: String) {
        _uiState.update {
            it.copy(
                isSecretHistoryRevealed = true,
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

    override fun onCleared() {
        super.onCleared()
        voicePlayerHelper.stop()
        voiceRecorderHelper.cancelRecording()
        authRepository.setTyping(false)
        authRepository.setRecordingAudio(false)
    }
}

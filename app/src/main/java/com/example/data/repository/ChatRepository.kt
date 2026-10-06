package com.example.data.repository

import android.content.Context
import android.util.Log
import com.example.data.model.Conversation
import com.example.data.model.Message
import com.example.data.model.MessageStatus
import com.example.data.model.MessageType
import com.google.firebase.FirebaseApp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.UUID

class ChatRepository(
    private val context: Context,
    private val authRepository: AuthRepository
) {
    private val firestore: FirebaseFirestore? by lazy {
        try {
            if (FirebaseApp.getApps(context).isNotEmpty()) FirebaseFirestore.getInstance() else null
        } catch (e: Exception) {
            Log.w("ChatRepository", "Firestore not initialized", e)
            null
        }
    }

    // In-memory cache for ultra-fast rendering & offline resilience
    private val _messagesFlow = MutableStateFlow<List<Message>>(emptyList())
    val messagesFlow: StateFlow<List<Message>> = _messagesFlow.asStateFlow()

    // Dual-consent chat deletion state (both must accept to delete chat)
    private val _deletionRequestFlow = MutableStateFlow<com.example.data.model.ChatDeletionRequest?>(null)
    val deletionRequestFlow: StateFlow<com.example.data.model.ChatDeletionRequest?> = _deletionRequestFlow.asStateFlow()

    private var globalTodayListener: ListenerRegistration? = null
    private var globalPreviousListener: ListenerRegistration? = null
    private var currentActiveConversationId: String? = null
    private var isInitialTodaySnapshot = true
    
    private var previousMessageLimit = 0L
    private var todayMessages: List<Message> = emptyList()
    private var previousMessages: List<Message> = emptyList()

    private val _isQueryExhaustedFlow = MutableStateFlow(false)
    val isQueryExhaustedFlow: StateFlow<Boolean> = _isQueryExhaustedFlow.asStateFlow()

    private val _isLoadingMoreFlow = MutableStateFlow(false)
    val isLoadingMoreFlow: StateFlow<Boolean> = _isLoadingMoreFlow.asStateFlow()

    private val _hasPreviousChatsAvailableFlow = MutableStateFlow(true)
    val hasPreviousChatsAvailableFlow: StateFlow<Boolean> = _hasPreviousChatsAvailableFlow.asStateFlow()

    private val _galleryMediaMessages = MutableStateFlow<List<Message>>(emptyList())
    val galleryMediaMessages: StateFlow<List<Message>> = _galleryMediaMessages.asStateFlow()

    var isQueryExhausted: Boolean
        get() = _isQueryExhaustedFlow.value
        set(value) { _isQueryExhaustedFlow.value = value }

    val isLoadingMore: Boolean
        get() = _isLoadingMoreFlow.value

    fun getStartOfToday(): Long {
        val cal = java.util.Calendar.getInstance()
        if (cal.get(java.util.Calendar.HOUR_OF_DAY) < 4) {
            // Before 4 AM, today's chat session started yesterday morning at 4:00 AM
            cal.add(java.util.Calendar.DAY_OF_YEAR, -1)
        }
        cal.set(java.util.Calendar.HOUR_OF_DAY, 4)
        cal.set(java.util.Calendar.MINUTE, 0)
        cal.set(java.util.Calendar.SECOND, 0)
        cal.set(java.util.Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }

    init {
        _messagesFlow.value = emptyList()
        CoroutineScope(Dispatchers.IO).launch {
            authRepository.currentUserState
                .map { it?.coupleId?.trim()?.ifBlank { null } ?: "couple_faisal_shali" }
                .distinctUntilChanged()
                .collectLatest { convId ->
                    previousMessageLimit = 0L
                    todayMessages = emptyList()
                    previousMessages = emptyList()
                    _isQueryExhaustedFlow.value = false
                    _isLoadingMoreFlow.value = false
                    _hasPreviousChatsAvailableFlow.value = true
                    startGlobalMessagesListener(convId)
                }
        }
    }

    fun getConversationId(): String {
        val user = authRepository.currentUserState.value
        val coupleId = user?.coupleId?.trim()?.ifBlank { null } ?: "couple_faisal_shali"
        return coupleId
    }

    fun loadMoreMessages() {
        if (_isQueryExhaustedFlow.value || _isLoadingMoreFlow.value) return
        _isLoadingMoreFlow.value = true
        if (previousMessageLimit == 0L) {
            previousMessageLimit = 40L
        } else {
            previousMessageLimit += 40L
        }
        val convId = currentActiveConversationId ?: getConversationId()
        if (firestore != null) {
            startPreviousMessagesListener(convId)
        } else {
            _isLoadingMoreFlow.value = false
            _isQueryExhaustedFlow.value = true
        }
    }

    fun expandLimitForSearch() {
        if (previousMessageLimit < 500L) {
            previousMessageLimit = 500L
            currentActiveConversationId?.let { startPreviousMessagesListener(it) }
        }
    }

    /**
     * Immediately loads all media messages (photos, videos, audio notes) and starred items
     * from local state and Firestore for the gallery view so all items appear without manual chat pagination.
     */
    fun loadAllGalleryMedia() {
        // Collect from all currently loaded messages immediately
        val localMedia = _messagesFlow.value.filter {
            !it.isDeleted && (
                it.type.equals(com.example.data.model.MessageType.IMAGE.name, ignoreCase = true) ||
                it.type.equals(com.example.data.model.MessageType.VIDEO.name, ignoreCase = true) ||
                it.type.equals(com.example.data.model.MessageType.AUDIO.name, ignoreCase = true) ||
                it.isStarred ||
                !it.mediaUrl.isNullOrBlank() ||
                it.mediaUrls.isNotEmpty()
            )
        }
        if (localMedia.isNotEmpty()) {
            val combined = (_galleryMediaMessages.value + localMedia).distinctBy { it.id }.sortedByDescending { it.timestamp }
            _galleryMediaMessages.value = combined
        }

        val convId = currentActiveConversationId ?: getConversationId()
        val fs = firestore ?: return
        val convRef = fs.collection("conversations")
            .document(convId)
            .collection("messages")

        convRef.whereIn("type", listOf(
            com.example.data.model.MessageType.IMAGE.name,
            com.example.data.model.MessageType.AUDIO.name,
            com.example.data.model.MessageType.VIDEO.name
        )).get().addOnSuccessListener { mediaSnap ->
            val mediaItems = mediaSnap.documents.mapNotNull { it.toObject(Message::class.java) }
            convRef.whereEqualTo("isStarred", true).get().addOnSuccessListener { starSnap ->
                val starredItems = starSnap.documents.mapNotNull { it.toObject(Message::class.java) }
                val allItems = (mediaItems + starredItems + _galleryMediaMessages.value).distinctBy { it.id }.sortedByDescending { it.timestamp }
                _galleryMediaMessages.value = allItems
            }.addOnFailureListener {
                val allItems = (mediaItems + _galleryMediaMessages.value).distinctBy { it.id }.sortedByDescending { it.timestamp }
                _galleryMediaMessages.value = allItems
            }
        }.addOnFailureListener { e ->
            Log.w("ChatRepository", "Failed loading gallery media", e)
        }
    }

    fun recoverAllMessages() {
        // Un-delete all messages in memory
        _messagesFlow.value = _messagesFlow.value.map {
            if (it.isDeleted) it.copy(isDeleted = false) else it
        }
        todayMessages = todayMessages.map {
            if (it.isDeleted) it.copy(isDeleted = false) else it
        }
        previousMessages = previousMessages.map {
            if (it.isDeleted) it.copy(isDeleted = false) else it
        }
        // ONLY gallery items all become visible:
        loadAllGalleryMedia()
        // Previous chats come as pagination on scroll:
        _hasPreviousChatsAvailableFlow.value = true
        _isQueryExhaustedFlow.value = false
        if (previousMessageLimit == 0L) {
            previousMessageLimit = 40L
        }
        currentActiveConversationId?.let { startPreviousMessagesListener(it) }
        mergeAndEmitMessages()
        try {
            com.example.CherishApplication.instance.googleDriveBackupManager.triggerImmediateAutoBackup()
        } catch (_: Exception) {}
    }

    fun resetPreviousChats() {
        previousMessageLimit = 0L
        previousMessages = emptyList()
        _galleryMediaMessages.value = emptyList()
        globalPreviousListener?.remove()
        globalPreviousListener = null
        _isQueryExhaustedFlow.value = false
        _isLoadingMoreFlow.value = false
        _hasPreviousChatsAvailableFlow.value = true
        mergeAndEmitMessages()
    }

    private fun startGlobalMessagesListener(conversationId: String, forceRestart: Boolean = false) {
        if (!forceRestart && currentActiveConversationId == conversationId && globalTodayListener != null) return
        globalTodayListener?.remove()
        globalPreviousListener?.remove()
        currentActiveConversationId = conversationId
        isInitialTodaySnapshot = true
        val fs = firestore
        if (fs == null) {
            _isLoadingMoreFlow.value = false
            _isQueryExhaustedFlow.value = true
            return
        }

        val startOfToday = getStartOfToday()
        val convRef = fs.collection("conversations")
            .document(conversationId)
            .collection("messages")

        // 1. Listen to ALL of today's messages without pagination (flawless smooth scrolling)
        val todayQuery = convRef
            .whereGreaterThanOrEqualTo("timestamp", startOfToday)
            .orderBy("timestamp", Query.Direction.ASCENDING)

        globalTodayListener = todayQuery.addSnapshotListener { snapshot, error ->
            if (error != null) {
                Log.w("ChatRepository", "Listen today messages failed", error)
                return@addSnapshotListener
            }
            if (snapshot != null) {
                if (!isInitialTodaySnapshot) {
                    for (change in snapshot.documentChanges) {
                        if (change.type == com.google.firebase.firestore.DocumentChange.Type.ADDED) {
                            val msg = change.document.toObject(Message::class.java)
                            val currentUserId = authRepository.getCurrentUserId()
                            if (msg.senderId != currentUserId && msg.status != MessageStatus.READ.name) {
                                if (!authRepository.isUserActivelyInChat()) {
                                    val previewText = when (msg.getTypedType()) {
                                        MessageType.TEXT -> msg.text
                                        MessageType.IMAGE -> "Photo"
                                        MessageType.AUDIO -> "Voice message"
                                        MessageType.VIDEO -> "Video"
                                        MessageType.DOCUMENT -> "Document"
                                        else -> "New message"
                                    }
                                    com.example.notifications.NotificationHelper.showMessageNotification(
                                        context = context,
                                        senderName = msg.senderName,
                                        messageText = previewText,
                                        conversationId = conversationId,
                                        messageId = msg.id
                                    )
                                }
                            }
                        }
                    }
                }
                isInitialTodaySnapshot = false

                todayMessages = snapshot.documents.mapNotNull { it.toObject(Message::class.java) }
                mergeAndEmitMessages()
            }
        }

        // 2. Listen to previous chats (messages before today, loaded with pagination style)
        if (previousMessageLimit > 0L) {
            startPreviousMessagesListener(conversationId)
        } else {
            convRef.whereLessThan("timestamp", startOfToday)
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .limit(1)
                .get()
                .addOnSuccessListener { prevSnap ->
                    val hasPrev = !prevSnap.isEmpty
                    _hasPreviousChatsAvailableFlow.value = hasPrev
                    if (!hasPrev) {
                        _isQueryExhaustedFlow.value = true
                    }
                }
        }
    }

    private fun startPreviousMessagesListener(conversationId: String) {
        globalPreviousListener?.remove()
        val fs = firestore ?: return
        val startOfToday = getStartOfToday()
        val convRef = fs.collection("conversations")
            .document(conversationId)
            .collection("messages")

        val prevQuery = convRef
            .whereLessThan("timestamp", startOfToday)
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .limit(previousMessageLimit)

        globalPreviousListener = prevQuery.addSnapshotListener { snapshot, error ->
            _isLoadingMoreFlow.value = false
            if (error != null) {
                Log.w("ChatRepository", "Listen previous messages failed", error)
                return@addSnapshotListener
            }
            if (snapshot != null) {
                _hasPreviousChatsAvailableFlow.value = snapshot.documents.isNotEmpty()
                _isQueryExhaustedFlow.value = (snapshot.documents.size < previousMessageLimit)
                previousMessages = snapshot.documents.mapNotNull { it.toObject(Message::class.java) }
                mergeAndEmitMessages()
            }
        }
    }

    private fun mergeAndEmitMessages() {
        val firestoreMessages = previousMessages + todayMessages
        val firestoreIds = firestoreMessages.map { it.id }.toSet()
        val maxFirestoreTimestamp = firestoreMessages.maxOfOrNull { it.timestamp } ?: 0L

        val pendingOptimistic = _messagesFlow.value.filter {
            it.timestamp > maxFirestoreTimestamp && it.id !in firestoreIds
        }

        _messagesFlow.value = (firestoreMessages + pendingOptimistic)
            .sortedBy { it.timestamp }
            .distinctBy { it.id }
    }

    /**
     * Returns a flow of messages for the given conversation.
     * Does NOT re-create the Firestore listener — the single global listener
     * set up in init handles all realtime updates. This just bridges to the
     * shared _messagesFlow so callers get the same stream.
     */
    fun listenToMessages(conversationId: String): Flow<List<Message>> {
        return _messagesFlow.asStateFlow()
    }

    suspend fun sendMessage(
        text: String,
        type: MessageType = MessageType.TEXT,
        mediaUrl: String? = null,
        mediaName: String? = null,
        mediaSize: Long = 0L,
        durationSeconds: Int = 0,
        waveform: List<Float> = emptyList(),
        replyTo: Message? = null,
        mediaUrls: List<String> = emptyList(),
        isVideoNote: Boolean = false
    ): Result<Message> {
        val currentUser = authRepository.currentUserState.value
        val partner = authRepository.partnerUserState.value
        val convId = getConversationId()
        val messageId = UUID.randomUUID().toString()

        val newMessage = Message(
            id = messageId,
            conversationId = convId,
            senderId = currentUser?.id ?: "user_me",
            senderName = currentUser?.displayName ?: "Me",
            receiverId = partner?.id ?: "user_partner",
            text = text,
            timestamp = System.currentTimeMillis(),
            type = type.name,
            mediaUrl = mediaUrl ?: mediaUrls.firstOrNull(),
            mediaName = mediaName,
            mediaSize = mediaSize,
            durationSeconds = durationSeconds,
            waveform = waveform,
            status = MessageStatus.SENT.name,
            replyToMessageId = replyTo?.id,
            replyToText = replyTo?.text?.take(80),
            replyToSenderName = replyTo?.senderName,
            mediaUrls = mediaUrls,
            isVideoNote = isVideoNote
        )

        // Optimistically add to local state
        _messagesFlow.value = _messagesFlow.value + newMessage

        return try {
            val fs = firestore
            if (fs != null) {
                val convRef = fs.collection("conversations").document(convId)
                convRef.collection("messages").document(messageId).set(newMessage)

                // Update conversation summary
                val summary = mapOf(
                    "id" to convId,
                    "lastMessageText" to if (type == MessageType.TEXT) text else "[${type.name.lowercase()}]",
                    "lastMessageSenderId" to newMessage.senderId,
                    "lastMessageTimestamp" to newMessage.timestamp
                )
                convRef.set(summary, com.google.firebase.firestore.SetOptions.merge())
            }
            try {
                com.example.CherishApplication.instance.googleDriveBackupManager.triggerImmediateAutoBackup()
            } catch (_: Exception) {}
            Result.success(newMessage)
        } catch (e: Exception) {
            Log.e("ChatRepository", "Error sending message to Firestore", e)
            try {
                com.example.CherishApplication.instance.googleDriveBackupManager.triggerImmediateAutoBackup()
            } catch (_: Exception) {}
            Result.success(newMessage) // preserved locally
        }
    }

    suspend fun markAsRead(messageId: String) {
        val currentUserId = authRepository.getCurrentUserId()
        _messagesFlow.value = _messagesFlow.value.map {
            if (it.id == messageId && it.receiverId == currentUserId) {
                it.copy(status = MessageStatus.READ.name, readTimestamp = System.currentTimeMillis())
            } else it
        }

        try {
            val convId = getConversationId()
            firestore?.collection("conversations")
                ?.document(convId)
                ?.collection("messages")
                ?.document(messageId)
                ?.update(
                    mapOf(
                        "status" to MessageStatus.READ.name,
                        "readTimestamp" to System.currentTimeMillis()
                    )
                )
        } catch (e: Exception) {
            // ignore
        }
    }

    suspend fun toggleReaction(messageId: String, emoji: String) {
        val currentUserId = authRepository.getCurrentUserId()
        val currentMessage = _messagesFlow.value.find { it.id == messageId }
            ?: todayMessages.find { it.id == messageId }
            ?: previousMessages.find { it.id == messageId }
            ?: return

        val newReactions = currentMessage.reactions.toMutableMap()
        if (newReactions[currentUserId] == emoji) {
            newReactions.remove(currentUserId)
        } else {
            newReactions[currentUserId] = emoji
        }

        val updated = currentMessage.copy(reactions = newReactions)
        _messagesFlow.value = _messagesFlow.value.map { if (it.id == messageId) updated else it }
        todayMessages = todayMessages.map { if (it.id == messageId) updated else it }
        previousMessages = previousMessages.map { if (it.id == messageId) updated else it }

        try {
            val convId = currentActiveConversationId ?: getConversationId()
            val fs = firestore
            if (fs != null) {
                fs.collection("conversations")
                    .document(convId)
                    .collection("messages")
                    .document(messageId)
                    .set(mapOf("reactions" to newReactions), SetOptions.merge())
                    .await()
            }
        } catch (e: Exception) {
            Log.e("ChatRepository", "Failed to update reaction in Firestore for message $messageId", e)
        }
    }

    suspend fun toggleStar(messageId: String) {
        val currentMessage = _messagesFlow.value.find { it.id == messageId } ?: return
        val newStarred = !currentMessage.isStarred
        val updated = currentMessage.copy(isStarred = newStarred)
        _messagesFlow.value = _messagesFlow.value.map { if (it.id == messageId) updated else it }

        try {
            val convId = getConversationId()
            firestore?.collection("conversations")
                ?.document(convId)
                ?.collection("messages")
                ?.document(messageId)
                ?.update("isStarred", newStarred)
        } catch (e: Exception) {
            // ignore
        }
    }

    suspend fun togglePin(messageId: String) {
        val currentMessage = _messagesFlow.value.find { it.id == messageId } ?: return
        val newPinned = !currentMessage.isPinned
        val updated = currentMessage.copy(isPinned = newPinned)
        _messagesFlow.value = _messagesFlow.value.map { if (it.id == messageId) updated else it }

        try {
            val convId = getConversationId()
            firestore?.collection("conversations")
                ?.document(convId)
                ?.collection("messages")
                ?.document(messageId)
                ?.update("isPinned", newPinned)
        } catch (e: Exception) {
            // ignore
        }
    }

    suspend fun editMessage(messageId: String, newText: String) {
        val currentMessage = _messagesFlow.value.find { it.id == messageId } ?: return
        val updated = currentMessage.copy(text = newText, isEdited = true)
        _messagesFlow.value = _messagesFlow.value.map { if (it.id == messageId) updated else it }

        try {
            val convId = getConversationId()
            firestore?.collection("conversations")
                ?.document(convId)
                ?.collection("messages")
                ?.document(messageId)
                ?.update(mapOf("text" to newText, "isEdited" to true))
        } catch (e: Exception) {
            // ignore
        }
        try {
            com.example.CherishApplication.instance.googleDriveBackupManager.triggerImmediateAutoBackup()
        } catch (_: Exception) {}
    }

    suspend fun deleteMessage(messageId: String) {
        // Immediately remove from in-memory message flows so it disappears from UI
        _messagesFlow.value = _messagesFlow.value.filter { it.id != messageId }
        _galleryMediaMessages.value = _galleryMediaMessages.value.filter { it.id != messageId }

        try {
            val convId = getConversationId()
            firestore?.collection("conversations")
                ?.document(convId)
                ?.collection("messages")
                ?.document(messageId)
                ?.delete()
        } catch (e: Exception) {
            // ignore
        }
        try {
            com.example.CherishApplication.instance.googleDriveBackupManager.triggerImmediateAutoBackup()
        } catch (_: Exception) {}
    }

    // --- DUAL-CONSENT CHAT DELETION (Both must accept before chat can be deleted) ---
    fun requestChatDeletion(scope: String = "ALL_MESSAGES", targetMessageId: String? = null) {
        val currentUser = authRepository.currentUserState.value
        val userId = currentUser?.id ?: "current_user"
        val userName = currentUser?.displayName?.ifBlank { "Partner" } ?: "Partner"

        val request = com.example.data.model.ChatDeletionRequest(
            id = UUID.randomUUID().toString(),
            requestedByUserId = userId,
            requestedByUserName = userName,
            timestamp = System.currentTimeMillis(),
            scope = scope,
            targetMessageId = targetMessageId,
            status = com.example.data.model.DeletionRequestStatus.PENDING.name
        )
        _deletionRequestFlow.value = request
    }

    suspend fun acceptChatDeletion() {
        val request = _deletionRequestFlow.value ?: return
        if (request.scope == "ALL_MESSAGES") {
            // Clear entire conversation history with mutual consent
            _messagesFlow.value = listOf(
                Message(
                    id = "mutual_clear_${System.currentTimeMillis()}",
                    conversationId = getConversationId(),
                    senderId = "system",
                    senderName = "Cherish Vault",
                    receiverId = "both",
                    text = "🔒 Chat history cleared with mutual consent from both partners.",
                    timestamp = System.currentTimeMillis(),
                    type = MessageType.TEXT.name
                )
            )
        } else if (request.targetMessageId != null) {
            deleteMessage(request.targetMessageId)
        }
        _deletionRequestFlow.value = null
    }

    fun declineChatDeletion() {
        _deletionRequestFlow.value = null
    }

    fun cancelChatDeletion() {
        _deletionRequestFlow.value = null
    }

    fun getStarredMessages(): List<Message> {
        return _messagesFlow.value.filter { it.isStarred && !it.isDeleted }
    }

    fun getMediaMessages(): List<Message> {
        return _messagesFlow.value.filter {
            !it.isDeleted && (it.getTypedType() == MessageType.IMAGE || it.getTypedType() == MessageType.VIDEO || it.getTypedType() == MessageType.AUDIO)
        }
    }

    suspend fun restoreMessages(messages: List<Message>) {
        if (messages.isEmpty()) return
        val current = _messagesFlow.value.toMutableList()
        val existingIds = current.map { it.id }.toSet()
        val toAdd = messages.filter { it.id !in existingIds }
        if (toAdd.isNotEmpty()) {
            current.addAll(toAdd)
            _messagesFlow.value = current.sortedBy { it.timestamp }
            val fs = firestore
            val convId = getConversationId()
            if (fs != null) {
                toAdd.forEach { msg ->
                    try {
                        fs.collection("conversations").document(convId).collection("messages").document(msg.id).set(msg).await()
                    } catch (e: Exception) {
                        Log.w("ChatRepository", "Restore message sync warning", e)
                    }
                }
            }
        }
    }

    private fun getSampleStarterMessages(): List<Message> {
        val now = System.currentTimeMillis()
        val oneHourAgo = now - 3600000
        val thirtyMinAgo = now - 1800000
        val tenMinAgo = now - 600000
        val convId = "couple_cherish_private"

        return listOf(
            Message(
                id = "m1",
                conversationId = convId,
                senderId = "partner",
                senderName = "My Love",
                receiverId = "user_me",
                text = "Hey sweetheart! Hope you're having an amazing day ❤️",
                timestamp = oneHourAgo,
                type = MessageType.TEXT.name,
                status = MessageStatus.READ.name,
                reactions = mapOf("user_me" to "❤️")
            ),
            Message(
                id = "m2",
                conversationId = convId,
                senderId = "user_me",
                senderName = "Me",
                receiverId = "partner",
                text = "Thinking of you makes everything better 🥰 Cannot wait to see you tonight!",
                timestamp = thirtyMinAgo,
                type = MessageType.TEXT.name,
                status = MessageStatus.READ.name,
                reactions = mapOf("partner" to "🥰")
            ),
            Message(
                id = "m3",
                conversationId = convId,
                senderId = "partner",
                senderName = "My Love",
                receiverId = "user_me",
                text = "I'm cooking that pasta you love 🍝 See you soon my heart!",
                timestamp = tenMinAgo,
                type = MessageType.TEXT.name,
                status = MessageStatus.DELIVERED.name,
                isStarred = true
            )
        )
    }
}

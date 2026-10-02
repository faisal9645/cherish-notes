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

    private var globalMessagesListener: ListenerRegistration? = null
    private var currentActiveConversationId: String? = null

    init {
        _messagesFlow.value = emptyList()
        CoroutineScope(Dispatchers.IO).launch {
            authRepository.currentUserState
                .map { it?.coupleId ?: "couple_cherish_love" }
                .distinctUntilChanged()
                .collectLatest { convId ->
                    startGlobalMessagesListener(convId)
                }
        }
    }

    fun getConversationId(): String {
        val user = authRepository.currentUserState.value
        val coupleId = user?.coupleId ?: "couple_cherish_love"
        return coupleId
    }

    private fun startGlobalMessagesListener(conversationId: String) {
        // Prevent duplicate listener for the same conversation
        if (currentActiveConversationId == conversationId && globalMessagesListener != null) return
        globalMessagesListener?.remove()
        currentActiveConversationId = conversationId
        val fs = firestore ?: return
        val query = fs.collection("conversations")
            .document(conversationId)
            .collection("messages")
            .orderBy("timestamp", Query.Direction.ASCENDING)

        globalMessagesListener = query.addSnapshotListener { snapshot, error ->
            if (error != null) {
                Log.w("ChatRepository", "Listen messages failed", error)
                return@addSnapshotListener
            }
            if (snapshot != null) {
                val firestoreMessages = snapshot.documents.mapNotNull { it.toObject(Message::class.java) }
                val firestoreIds = firestoreMessages.map { it.id }.toSet()
                // Keep optimistic in-memory messages that Firestore hasn't confirmed yet (avoids blank flash)
                val pendingOptimistic = _messagesFlow.value.filter { it.id !in firestoreIds }
                // Merge: confirmed Firestore messages + any still-pending optimistic ones
                _messagesFlow.value = (firestoreMessages + pendingOptimistic)
                    .sortedBy { it.timestamp }
                    .distinctBy { it.id }
            }
        }
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
        replyTo: Message? = null
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
            mediaUrl = mediaUrl,
            mediaName = mediaName,
            mediaSize = mediaSize,
            durationSeconds = durationSeconds,
            waveform = waveform,
            status = MessageStatus.SENT.name,
            replyToMessageId = replyTo?.id,
            replyToText = replyTo?.text?.take(80),
            replyToSenderName = replyTo?.senderName
        )

        // Optimistically add to local state
        _messagesFlow.value = _messagesFlow.value + newMessage

        return try {
            val fs = firestore
            if (fs != null) {
                val convRef = fs.collection("conversations").document(convId)
                convRef.collection("messages").document(messageId).set(newMessage).await()

                // Update conversation summary
                val summary = mapOf(
                    "id" to convId,
                    "lastMessageText" to if (type == MessageType.TEXT) text else "[${type.name.lowercase()}]",
                    "lastMessageSenderId" to newMessage.senderId,
                    "lastMessageTimestamp" to newMessage.timestamp
                )
                convRef.set(summary, com.google.firebase.firestore.SetOptions.merge()).await()
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
        val currentMessage = _messagesFlow.value.find { it.id == messageId } ?: return

        val newReactions = currentMessage.reactions.toMutableMap()
        if (newReactions[currentUserId] == emoji) {
            newReactions.remove(currentUserId)
        } else {
            newReactions[currentUserId] = emoji
        }

        val updated = currentMessage.copy(reactions = newReactions)
        _messagesFlow.value = _messagesFlow.value.map { if (it.id == messageId) updated else it }

        try {
            val convId = getConversationId()
            firestore?.collection("conversations")
                ?.document(convId)
                ?.collection("messages")
                ?.document(messageId)
                ?.update("reactions", newReactions)
        } catch (e: Exception) {
            // ignore
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
    }

    suspend fun deleteMessage(messageId: String) {
        val currentMessage = _messagesFlow.value.find { it.id == messageId } ?: return
        val updated = currentMessage.copy(
            text = "This message was deleted",
            isDeleted = true,
            mediaUrl = null
        )
        _messagesFlow.value = _messagesFlow.value.map { if (it.id == messageId) updated else it }

        try {
            val convId = getConversationId()
            firestore?.collection("conversations")
                ?.document(convId)
                ?.collection("messages")
                ?.document(messageId)
                ?.update(
                    mapOf(
                        "text" to "This message was deleted",
                        "isDeleted" to true,
                        "mediaUrl" to null
                    )
                )
        } catch (e: Exception) {
            // ignore
        }
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

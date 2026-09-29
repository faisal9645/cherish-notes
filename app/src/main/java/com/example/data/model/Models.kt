package com.example.data.model

import com.google.firebase.firestore.IgnoreExtraProperties

enum class MessageType {
    TEXT,
    IMAGE,
    VIDEO,
    AUDIO,
    DOCUMENT
}

enum class MessageStatus {
    SENDING,
    SENT,
    DELIVERED,
    READ
}

enum class DateCategory {
    ANNIVERSARY,
    BIRTHDAY,
    FIRST_DATE,
    MILESTONE,
    CUSTOM
}

enum class NoteCategory {
    NOTE,
    LOVE_LETTER,
    BUCKET_LIST,
    WISHLIST
}

@IgnoreExtraProperties
data class User(
    val id: String = "",
    val email: String = "",
    val displayName: String = "",
    val photoUrl: String? = null,
    val statusMessage: String = "Loving every moment with you ✨",
    val partnerId: String? = null,
    val partnerEmail: String? = null,
    val coupleId: String? = null,
    val isOnline: Boolean = false,
    val lastSeen: Long = System.currentTimeMillis(),
    val typingInChat: Boolean = false,
    val fcmToken: String? = null,
    val createdAt: Long = System.currentTimeMillis()
)

@IgnoreExtraProperties
data class Message(
    val id: String = "",
    val conversationId: String = "",
    val senderId: String = "",
    val senderName: String = "",
    val receiverId: String = "",
    val text: String = "",
    val timestamp: Long = System.currentTimeMillis(),
    val type: String = MessageType.TEXT.name,
    val mediaUrl: String? = null,
    val mediaName: String? = null,
    val mediaSize: Long = 0L,
    val durationSeconds: Int = 0,
    val waveform: List<Float> = emptyList(),
    val status: String = MessageStatus.SENT.name,
    val readTimestamp: Long? = null,
    val isEdited: Boolean = false,
    val isDeleted: Boolean = false,
    val isStarred: Boolean = false,
    val replyToMessageId: String? = null,
    val replyToText: String? = null,
    val replyToSenderName: String? = null,
    val reactions: Map<String, String> = emptyMap() // userId -> emoji
) {
    fun getTypedType(): MessageType {
        return runCatching { MessageType.valueOf(type) }.getOrDefault(MessageType.TEXT)
    }

    fun getTypedStatus(): MessageStatus {
        return runCatching { MessageStatus.valueOf(status) }.getOrDefault(MessageStatus.SENT)
    }
}

@IgnoreExtraProperties
data class Conversation(
    val id: String = "",
    val participantIds: List<String> = emptyList(),
    val participantEmails: List<String> = emptyList(),
    val lastMessageText: String = "",
    val lastMessageSenderId: String = "",
    val lastMessageTimestamp: Long = 0L,
    val unreadCountForUser: Map<String, Int> = emptyMap(),
    val typingStatus: Map<String, Boolean> = emptyMap()
)

@IgnoreExtraProperties
data class Memory(
    val id: String = "",
    val coupleId: String = "",
    val title: String = "",
    val description: String = "",
    val photoUrl: String? = null,
    val dateMillis: Long = System.currentTimeMillis(),
    val location: String? = null,
    val createdByUserId: String = "",
    val createdAt: Long = System.currentTimeMillis()
)

@IgnoreExtraProperties
data class ImportantDate(
    val id: String = "",
    val coupleId: String = "",
    val title: String = "",
    val dateMillis: Long = System.currentTimeMillis(),
    val category: String = DateCategory.ANNIVERSARY.name,
    val notes: String? = null,
    val repeatAnnually: Boolean = true
) {
    fun getTypedCategory(): DateCategory {
        return runCatching { DateCategory.valueOf(category) }.getOrDefault(DateCategory.CUSTOM)
    }
}

@IgnoreExtraProperties
data class SharedNote(
    val id: String = "",
    val coupleId: String = "",
    val title: String = "",
    val content: String = "",
    val category: String = NoteCategory.NOTE.name,
    val updatedByUserId: String = "",
    val updatedAt: Long = System.currentTimeMillis(),
    val isPinned: Boolean = false
) {
    fun getTypedCategory(): NoteCategory {
        return runCatching { NoteCategory.valueOf(category) }.getOrDefault(NoteCategory.NOTE)
    }
}

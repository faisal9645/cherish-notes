package com.example.data.model

import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.PropertyName

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

enum class PartnerActivityStatus(val displayName: String, val emoji: String) {
    AVAILABLE("I'm available", "💚"),
    RESTING("I'm resting", "💤"),
    WORKING("I'm working", "💻"),
    STUDYING("I'm studying", "📚"),
    TRAVELLING("I'm travelling", "🚗"),
    TALK_LATER("Talk later", "❤️")
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
    @get:PropertyName("isOnline") @set:PropertyName("isOnline")
    var isOnline: Boolean = false,
    val lastSeen: Long = System.currentTimeMillis(),
    val typingInChat: Boolean = false,
    val fcmToken: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val checkAfterTimeMillis: Long? = null,
    val checkAfterNote: String? = null,
    val checkAfterCreatedAt: Long? = null,
    val checkAfterActive: Boolean = false,
    val recordingAudioInChat: Boolean = false,
    val activityStatus: String? = null,
    val activityStatusNote: String? = null,
    val isActivityHidden: Boolean = false
) {
    fun isEffectivelyOnline(): Boolean {
        if (isOnline) return true
        if (typingInChat || recordingAudioInChat) return true
        val diff = System.currentTimeMillis() - lastSeen
        return diff in 0..60_000L
    }

    fun hasActiveCheckAfter(): Boolean {
        val target = checkAfterTimeMillis ?: return false
        return checkAfterActive && target > 0L && System.currentTimeMillis() < target
    }

    fun isCheckAfterExpired(): Boolean {
        val target = checkAfterTimeMillis ?: return false
        return checkAfterActive && target > 0L && System.currentTimeMillis() >= target
    }

    fun getEffectivePresenceStatus(): String {
        return when {
            hasActiveCheckAfter() -> "Check-after active"
            isActivityHidden -> "Activity unavailable"
            isEffectivelyOnline() -> "Online"
            else -> "Offline"
        }
    }
}


@androidx.compose.runtime.Immutable
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
    val isPinned: Boolean = false,
    val replyToMessageId: String? = null,
    val replyToText: String? = null,
    val replyToSenderName: String? = null,
    val reactions: Map<String, String> = emptyMap(), // userId -> emoji
    val mediaUrls: List<String> = emptyList()
) {
    fun getTypedType(): MessageType {
        return runCatching { MessageType.valueOf(type) }.getOrDefault(MessageType.TEXT)
    }

    fun getTypedStatus(): MessageStatus {
        return runCatching { MessageStatus.valueOf(status) }.getOrDefault(MessageStatus.SENT)
    }

    fun getAllMediaUrls(): List<String> {
        if (mediaUrls.isNotEmpty()) return mediaUrls
        if (!mediaUrl.isNullOrBlank()) {
            return if (mediaUrl.contains(",")) {
                mediaUrl.split(",").map { it.trim() }.filter { it.isNotEmpty() }
            } else {
                listOf(mediaUrl)
            }
        }
        return emptyList()
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

@IgnoreExtraProperties
data class DailyQuestion(
    val id: String = "",
    val question: String = "",
    val category: String = "Deep Connection",
    val myAnswer: String? = null,
    val partnerAnswer: String? = null,
    val isMyAnswerSubmitted: Boolean = false,
    val isPartnerAnswerSubmitted: Boolean = false,
    val isLikedByPartner: Boolean = false,
    val streakDays: Int = 12
)

@IgnoreExtraProperties
data class LoveJarNote(
    val id: String = "",
    val text: String = "",
    val author: String = "",
    val emoji: String = "💖",
    val category: String = "ROMANTIC", // COMPLIMENTS, MEMORIES, FUNNY, MOTIVATION, ROMANTIC, SURPRISE
    val isOpened: Boolean = false,
    val openedAt: Long? = null,
    val createdAt: Long = System.currentTimeMillis()
)

@IgnoreExtraProperties
data class OpenWhenLetter(
    val id: String = "",
    val coupleId: String = "",
    val title: String = "", // e.g. "Open when you miss me"
    val category: String = "MISS_YOU", // MISS_YOU, BAD_DAY, CANT_SLEEP, ANNIVERSARY, MOTIVATION, CUSTOM
    val envelopeEmoji: String = "💌",
    val content: String = "",
    val photoUrl: String? = null,
    val voiceUrl: String? = null,
    val authorName: String = "",
    val authorId: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val unlockCondition: String = "Open when you miss me ❤️",
    val unlockDateMillis: Long? = null,
    val isOpened: Boolean = false,
    val openedAt: Long? = null
)

@IgnoreExtraProperties
data class DeviceSession(
    val id: String = "",
    val deviceName: String = "",
    val platform: String = "Android",
    val lastActiveMillis: Long = System.currentTimeMillis(),
    val isCurrent: Boolean = false,
    val ipOrLocation: String = "Secured Mobile Session"
)

data class StorageBreakdown(
    val photosBytes: Long = 0L,
    val videosBytes: Long = 0L,
    val voiceBytes: Long = 0L,
    val cacheBytes: Long = 0L
) {
    fun formatBytes(bytes: Long): String {
        return when {
            bytes >= 1024 * 1024 * 1024 -> "%.1f GB".format(bytes.toDouble() / (1024 * 1024 * 1024))
            bytes >= 1024 * 1024 -> "%.1f MB".format(bytes.toDouble() / (1024 * 1024))
            bytes >= 1024 -> "%.1f KB".format(bytes.toDouble() / 1024)
            else -> "$bytes B"
        }
    }
}

@IgnoreExtraProperties
data class BucketListItem(
    val id: String = "",
    val title: String = "",
    val category: String = "Romantic Dates",
    val isCompleted: Boolean = false,
    val completedDate: String? = null
)

@IgnoreExtraProperties
data class YearlyJourneyEntry(
    val id: String = "",
    val year: Int = 2026,
    val myAge: Int = 34,
    val partnerAge: Int = 31,
    val yearTheme: String = "",
    val placesWent: String = "",
    val howWeEnjoyed: String = "",
    val specialMemory: String = "",
    val songOrQuote: String = "",
    val passionRating: Int = 5,
    val photoUrl: String? = null,
    val createdAt: Long = System.currentTimeMillis()
)

@IgnoreExtraProperties
data class LifetimeAgeProfile(
    val myBirthYear: Int = 1992,
    val partnerBirthYear: Int = 1995,
    val relationshipStartYear: Int = 2024,
    val secretVow: String = "We may not wear rings before the world, but our hearts took a vow that no paper could ever hold. We chose each other freely, and our connection is for our entire lifetime."
)

enum class DeletionRequestStatus {
    PENDING,
    ACCEPTED,
    DECLINED,
    CANCELLED
}

@IgnoreExtraProperties
data class ChatDeletionRequest(
    val id: String = "",
    val requestedByUserId: String = "",
    val requestedByUserName: String = "",
    val timestamp: Long = System.currentTimeMillis(),
    val scope: String = "ALL_MESSAGES",
    val targetMessageId: String? = null,
    val status: String = DeletionRequestStatus.PENDING.name
) {
    fun getTypedStatus(): DeletionRequestStatus {
        return runCatching { DeletionRequestStatus.valueOf(status) }.getOrDefault(DeletionRequestStatus.PENDING)
    }
}


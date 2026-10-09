package com.example.data.model

import com.google.firebase.firestore.Exclude
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.PropertyName
import com.squareup.moshi.JsonClass

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
    CUSTOM,
    // A place we went (remembered every year on its day)
    PLACE,
    // Family days (other family events; family birthdays are BIRTHDAY)
    FAMILY
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
    val isActivityHidden: Boolean = false,
    val mood: String? = null,
    // When the mood was set (0 for moods set before this existed); old moods fade from the header
    val moodAt: Long = 0L,
    val batteryLevel: Int? = null,
    val isCharging: Boolean = false,
    val heartbeatTouchingTimestamp: Long = 0L,
    val heartbeatStreak: Int = 0,
    val lastHeartbeatSync: Long = 0L
) {
    /**
     * When this phone received this user's latest live presence update, on this phone's own clock
     * (0 when unknown, e.g. only a cached or first copy of the document has arrived). Never stored.
     */
    @get:Exclude @set:Exclude
    var presenceReceivedAt: Long = 0L

    fun isEffectivelyOnline(): Boolean {
        if (!isOnline) return false
        val now = System.currentTimeMillis()
        // A live update measured on this phone's clock, so a wrong clock on either phone can't matter
        if (presenceReceivedAt > 0L) return now - presenceReceivedAt <= ONLINE_WINDOW_MS
        // Otherwise compare clocks; either way, no heartbeat for a few beats means the app is gone
        return kotlin.math.abs(now - lastSeen) <= ONLINE_WINDOW_MS
    }

    fun isEffectivelyTyping(): Boolean {
        return typingInChat && isEffectivelyOnline()
    }

    fun isEffectivelyRecording(): Boolean {
        return recordingAudioInChat && isEffectivelyOnline()
    }

    fun hasActiveCheckAfter(): Boolean {
        val target = checkAfterTimeMillis ?: return false
        return checkAfterActive && target > 0L && System.currentTimeMillis() < target
    }

    fun isCheckAfterExpired(): Boolean {
        val target = checkAfterTimeMillis ?: return false
        return checkAfterActive && target > 0L && System.currentTimeMillis() >= target
    }

    companion object {
        /** How often an open app refreshes its "online" heartbeat. */
        const val PRESENCE_HEARTBEAT_MS = 10_000L

        /** Online only while heartbeats keep arriving: three missed beats and it shows offline. */
        const val ONLINE_WINDOW_MS = 32_000L
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
@JsonClass(generateAdapter = true)
data class Message(
    var id: String = "",
    var conversationId: String = "",
    var senderId: String = "",
    var senderName: String = "",
    var receiverId: String = "",
    var text: String = "",
    var timestamp: Long = System.currentTimeMillis(),
    var type: String = MessageType.TEXT.name,
    var mediaUrl: String? = null,
    var mediaName: String? = null,
    var mediaSize: Long = 0L,
    var durationSeconds: Int = 0,
    var waveform: List<Double> = emptyList(),
    var status: String = MessageStatus.SENT.name,
    var readTimestamp: Long? = null,
    var isEdited: Boolean = false,
    var isDeleted: Boolean = false,
    var isStarred: Boolean = false,
    var isPinned: Boolean = false,
    var replyToMessageId: String? = null,
    var replyToText: String? = null,
    var replyToSenderName: String? = null,
    var reactions: Map<String, String> = emptyMap(), // userId -> emoji
    var mediaUrls: List<String> = emptyList(),
    var isVideoNote: Boolean = false,
    // Text contains a web link; lets the gallery query links instead of scanning every message
    var hasLink: Boolean = false,
    // Small previews for chat bubbles and the gallery, same order as getAllMediaUrls()
    var thumbnailUrls: List<String> = emptyList(),
    // A special message: "goodnight" dims the partner's screen with stars when they see it
    var effect: String? = null
) {
    @com.google.firebase.firestore.Exclude
    fun isCircularVideoNote(): Boolean {
        if (isVideoNote) return true
        if (!type.equals("VIDEO", ignoreCase = true)) return false
        // Notes sent before the flag existed: recorder file name or text. Shared videos aren't notes.
        return mediaName?.startsWith("videonote", ignoreCase = true) == true ||
            mediaUrl?.contains("videonote", ignoreCase = true) == true ||
            text.startsWith("Video note", ignoreCase = true)
    }
    @com.google.firebase.firestore.Exclude
    fun getTypedType(): MessageType {
        return runCatching { MessageType.valueOf(type.trim().uppercase()) }.getOrDefault(MessageType.TEXT)
    }

    @com.google.firebase.firestore.Exclude
    fun getTypedStatus(): MessageStatus {
        return runCatching { MessageStatus.valueOf(status) }.getOrDefault(MessageStatus.SENT)
    }

    /** The small preview for one of this message's media URLs, or the URL itself when it has none. */
    @com.google.firebase.firestore.Exclude
    fun thumbnailFor(url: String): String {
        val index = getAllMediaUrls().indexOf(url)
        return thumbnailUrls.getOrNull(index)?.takeIf { it.isNotBlank() } ?: url
    }

    @com.google.firebase.firestore.Exclude
    fun getAllMediaUrls(): List<String> {
        val result = mutableListOf<String>()
        if (mediaUrls.isNotEmpty()) {
            result.addAll(mediaUrls)
        }
        if (!mediaUrl.isNullOrBlank()) {
            if (mediaUrl!!.contains(",") && !mediaUrl!!.startsWith("data:")) {
                result.addAll(mediaUrl!!.split(",").map { it.trim() }.filter { it.isNotEmpty() })
            } else {
                result.add(mediaUrl!!)
            }
        }
        return result.filter { it.isNotBlank() }.distinct()
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
    val typingStatus: Map<String, Boolean> = emptyMap(),
    val heartbeatStreak: Int = 0,
    val lastHeartbeatSyncTimestamp: Long = 0L
)

@IgnoreExtraProperties
@JsonClass(generateAdapter = true)
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
@JsonClass(generateAdapter = true)
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
@JsonClass(generateAdapter = true)
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
    // I loved the partner's answer today
    val isLikedByPartner: Boolean = false,
    // The partner loved my answer today
    val partnerLovedMyAnswer: Boolean = false,
    // Days in a row we've both answered
    val streakDays: Int = 0
)

@IgnoreExtraProperties
@JsonClass(generateAdapter = true)
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
@JsonClass(generateAdapter = true)
data class BucketListItem(
    val id: String = "",
    val title: String = "",
    val category: String = "Romantic Dates",
    val isCompleted: Boolean = false,
    val completedDate: String? = null
)

@IgnoreExtraProperties
@JsonClass(generateAdapter = true)
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
    val createdAt: Long = System.currentTimeMillis(),
    // Who wrote it ("my" age is theirs); blank for older entries
    val authorId: String = ""
)

@IgnoreExtraProperties
@JsonClass(generateAdapter = true)
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


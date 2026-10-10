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
    // How fast they're typing: 0 paused, 1 slow, 2 fast (their typing dots follow it)
    val typingPace: Int = 0,
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
    val lastHeartbeatSync: Long = 0L,
    val timeZone: String? = null
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
    @get:PropertyName("isVideoNote") @set:PropertyName("isVideoNote")
    var isVideoNote: Boolean = false,
    // Text contains a web link; lets the gallery query links instead of scanning every message
    var hasLink: Boolean = false,
    // Small previews for chat bubbles and the gallery, same order as getAllMediaUrls()
    var thumbnailUrls: List<String> = emptyList(),
    // A special message: "goodnight" dims the partner's screen with stars when they see it
    var effect: String? = null,
    // Voice note listened state: shows a small blue dot if unplayed, cleared once listened
    var isAudioPlayed: Boolean = false
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

/**
 * A movie on our list, kept on the couple's record: suggested by one of us, then watched by each
 * of us (when, per person), so both phones agree on who watched it first and second.
 */
data class MovieItem(
    val id: String = "",
    val title: String = "",
    val genre: String = "Romance",
    val emoji: String = "🎬",
    val notes: String = "",
    /** User id of whoever added it. */
    val addedBy: String = "",
    val addedAt: Long = 0L,
    /** User id -> when they watched it. */
    val watchedAt: Map<String, Long> = emptyMap()
) {
    val isWatched: Boolean get() = watchedAt.isNotEmpty()

    /** Who watched it, first to last. */
    val watchOrder: List<String> get() = watchedAt.entries.sortedBy { it.value }.map { it.key }

    /** Both watched it within an hour of each other: together. */
    val watchedTogether: Boolean
        get() = watchedAt.size >= 2 && (watchedAt.values.maxOrNull()!! - watchedAt.values.minOrNull()!!) < 60L * 60 * 1000
}

/**
 * A book on our shelf, kept on the couple's record: each of us has our own page and finish time,
 * so it shows who's reading what right now and who finished first.
 */
data class BookItem(
    val id: String = "",
    val title: String = "",
    val author: String = "",
    val totalPages: Int = 0,
    val genre: String = "",
    val emoji: String = "📖",
    val notes: String = "",
    val addedBy: String = "",
    val addedAt: Long = 0L,
    /** User id -> the page they're on. */
    val pages: Map<String, Int> = emptyMap(),
    /** User id -> when they finished it. */
    val finishedAt: Map<String, Long> = emptyMap()
) {
    fun pageOf(userId: String): Int = pages[userId] ?: 0
    fun isFinishedBy(userId: String): Boolean = userId in finishedAt
    fun isReadingBy(userId: String): Boolean = !isFinishedBy(userId) && pageOf(userId) > 0

    /** Someone has started it (otherwise it's still a suggestion). */
    val isStarted: Boolean get() = finishedAt.isNotEmpty() || pages.values.any { it > 0 }

    /** Who finished it, first to last. */
    val finishOrder: List<String> get() = finishedAt.entries.sortedBy { it.value }.map { it.key }
}

@IgnoreExtraProperties
@JsonClass(generateAdapter = true)
data class WatchPartySession(
    val id: String = "active",
    val videoId: String = "",
    val mediaUrl: String = "",
    val title: String = "Watch Party",
    val isPlaying: Boolean = false,
    val positionSeconds: Float = 0f,
    val playbackRate: Float = 1.0f,
    val playScheduledAt: Long = 0L,
    val updatedAt: Long = 0L,
    val updatedBy: String = "",
    val bufferingBy: String = "",
    val adBy: String = "",
    val startedBy: String = "",
    val isActive: Boolean = false,
    /** The last heartbeat sent by double-tapping the video: when, and by whom. */
    val lastHeartburstAt: Long = 0L,
    val lastHeartburstBy: String = "",
    /** When this party began (changing the video keeps it); a new party is a new invite. */
    val startedAt: Long = 0L,
    /** The latest emoji reaction either of us sent: its id, the emoji, who, when and how many. */
    val reactionId: String = "",
    val reactionEmoji: String = "",
    val reactionBy: String = "",
    val reactionAt: Long = 0L,
    val reactionCount: Int = 0,
    /** A quick voice snippet sent over the video (id, base64 data, and sender). */
    val voiceSnippetId: String = "",
    val voiceSnippetData: String = "",
    val voiceSnippetBy: String = "",
    /** A short line that appears over the partner's video like a subtitle. */
    val whisperId: String = "",
    val whisperText: String = "",
    val whisperBy: String = "",
    /** Who has it open: user id to the last time their phone said so (0 once they left). */
    val watching: Map<String, Long> = emptyMap(),
    /** The Up Next queue: videos to play automatically after this one finishes. */
    val queue: List<WatchLaterVideo> = emptyList()
) {
    /** Where the video is right now on both phones: the saved spot, plus the time since if playing. */
    fun positionAt(now: Long = com.example.data.repository.ServerTime.now()): Float {
        if (!isPlaying) return positionSeconds
        val activeSince = if (playScheduledAt > updatedAt) playScheduledAt else updatedAt
        return positionSeconds + ((now - activeSince).coerceAtLeast(0L) / 1000f) * playbackRate
    }

    /** Whether [userId] has it open (their phone says so about once a minute while it is). */
    fun isWatchedBy(userId: String?, now: Long = com.example.data.repository.ServerTime.now()): Boolean =
        userId != null && now - (watching[userId] ?: 0L) < WATCHING_FRESH_MS

    companion object {
        const val WATCHING_FRESH_MS = 150_000L
    }
}

/** A video saved by the couple to watch together later. */
data class WatchLaterVideo(
    val id: String = "",
    val videoId: String = "",
    val title: String = "",
    val addedBy: String = "",
    val addedAt: Long = 0L
)

@IgnoreExtraProperties
@JsonClass(generateAdapter = true)
data class SleepSyncEvent(
    val timestamp: Long = 0L,
    val senderId: String = "",
    val senderName: String = "",
    val status: String = "Asleep 🌙",
    val isAsleep: Boolean = true
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

/**
 * A Check-After of more than 2 days ("personal space"), asked of the partner. It's kept on the
 * couple record and becomes the asker's Check-After once the partner accepts.
 */
data class SpaceRequest(
    val id: String = "",
    val fromId: String = "",
    val targetMillis: Long = 0L,
    val note: String = "",
    val requestedAt: Long = 0L,
    /** [STATUS_PENDING], [STATUS_ACCEPTED], [STATUS_DECLINED] or [STATUS_CANCELLED]. */
    val status: String = STATUS_PENDING,
    val respondedAt: Long = 0L
) {
    /** Still waiting for an answer (a week-old or already-passed request no longer counts). */
    val isPending: Boolean
        get() {
            val now = System.currentTimeMillis()
            return status == STATUS_PENDING && targetMillis > now && now - requestedAt < PENDING_FOR_MS
        }

    /** How many days it asks for. */
    val days: Int
        get() = ((targetMillis - requestedAt + DAY_MS / 2) / DAY_MS).toInt().coerceAtLeast(1)

    companion object {
        const val STATUS_PENDING = "pending"
        const val STATUS_ACCEPTED = "accepted"
        const val STATUS_DECLINED = "declined"
        const val STATUS_CANCELLED = "cancelled"
        const val DAY_MS = 24L * 60 * 60 * 1000
        /** A Check-After longer than this needs the partner's OK. */
        const val NEEDS_OK_AFTER_MS = 2 * DAY_MS
        private const val PENDING_FOR_MS = 7 * DAY_MS
    }
}

package com.example.data.model

/**
 * A repeating number one of us noticed and chose to record ("11:11", "444"). Stored at
 * couples/{coupleId}/synchronicity/{id}.
 */
data class SyncMoment(
    /** Made once per "Record" sheet, so saving twice or retrying never makes a second moment. */
    val id: String,
    val pattern: String,
    val note: String,
    /** The app user id of whoever recorded it (e.g. "user_faisal"). */
    val createdBy: String,
    /**
     * When it was seen (by the recording phone's clock). Usually the moment it was recorded, but it
     * can be set earlier when it's recorded later.
     */
    val seenAt: Long,
    /** The 6 AM day it was seen ("yyyy-MM-dd"), worked out on the recording phone in its own time zone. */
    val periodDate: String,
    /** Still on this phone only, waiting to reach the server. */
    val isPending: Boolean = false
)

/** Whether the shared moments can be read and saved right now. */
enum class SyncStatus {
    LOADING,
    READY,
    /** Firestore refused: the security rules for synchronicity haven't been published yet. */
    NOT_ALLOWED,
    /** Nobody is logged in or the couple isn't set up. */
    NO_COUPLE
}

/** The numbers we track, and the sweet (just-for-fun) meanings shown with them. */
object SyncPatterns {
    val defaults = listOf("10:10", "11:11", "12:12", "111", "222", "333", "444", "555", "777", "888", "999")

    /** At most this many of our own numbers on top of the defaults. */
    const val MAX_CUSTOM = 8
    const val NOTE_MAX = 280

    private val meanings = mapOf(
        "10:10" to "A fresh start, side by side.",
        "11:11" to "Make a wish together.",
        "12:12" to "Two hearts in balance.",
        "111" to "New beginnings for us.",
        "222" to "Two of us, in harmony.",
        "333" to "Our love is growing.",
        "444" to "Safe, steady, home.",
        "555" to "Sweet changes ahead.",
        "777" to "Lucky in love.",
        "888" to "Endless love, like ∞ on its side.",
        "999" to "One chapter closes, a new one opens."
    )

    fun meaning(pattern: String): String = meanings[pattern] ?: "A number that's ours."

    fun isTime(pattern: String): Boolean = ':' in pattern

    private val TIME = Regex("""^([01]?\d|2[0-3])[:.]([0-5]\d)$""")
    private val DIGITS = Regex("""^\d{2,6}$""")

    /** A clean pattern ("7:07" becomes "07:07", digits stay as they are), or null if it isn't one. */
    fun normalize(input: String): String? {
        val text = input.trim()
        TIME.matchEntire(text)?.let { match ->
            val (hours, minutes) = match.destructured
            return "%02d:%s".format(java.util.Locale.US, hours.toInt(), minutes)
        }
        return text.takeIf { DIGITS.matches(it) }
    }
}

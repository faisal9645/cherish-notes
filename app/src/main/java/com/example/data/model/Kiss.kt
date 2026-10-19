package com.example.data.model

import android.os.SystemClock

/**
 * A kiss stamped on the chat (a long press of lips or a thumb on it): where, as parts of the
 * screen (0-1), saved as "kiss" on the kisser's profile. It blooms on the other phone as it
 * arrives, or when the chat is next opened a little later.
 */
data class KissStamp(val id: String, val x: Float, val y: Float, val atMs: Long) {
    companion object {
        const val FIELD = "kiss"

        fun toMap(id: String, x: Float, y: Float, atMs: Long): Map<String, Any> =
            mapOf("id" to id, "x" to x.toDouble(), "y" to y.toDouble(), "at" to atMs)

        fun fromMap(value: Any?): KissStamp? {
            val map = value as? Map<*, *> ?: return null
            val id = (map["id"] as? String)?.ifBlank { null } ?: return null
            return KissStamp(
                id = id,
                x = ((map["x"] as? Number)?.toFloat() ?: 0.5f).coerceIn(0f, 1f),
                y = ((map["y"] as? Number)?.toFloat() ?: 0.5f).coerceIn(0f, 1f),
                atMs = (map["at"] as? Number)?.toLong() ?: 0L
            )
        }
    }
}

/**
 * Lips (or a thumb) held on the kiss pad right now, and where (0-1). Saved as "kissTouch" while
 * they're held, again every few seconds so the other phone knows they still are, and removed when
 * they let go. Both held at once: a kiss both phones feel.
 */
data class KissTouch(
    val x: Float,
    val y: Float,
    // This phone's clock (elapsed realtime) when it last heard they're still holding
    val heardAt: Long
) {
    /** Still held: heard again within a little more than the refresh. */
    fun isLive(elapsedNow: Long = SystemClock.elapsedRealtime()): Boolean = elapsedNow - heardAt < LIVE_MS

    companion object {
        const val FIELD = "kissTouch"
        /** How often a held kiss is saved again. */
        const val REFRESH_MS = 4_000L
        private const val LIVE_MS = 6_500L
        /** One saved longer ago than this (their app closed mid-kiss) isn't a kiss any more. */
        private const val STALE_MS = 20_000L

        fun toMap(x: Float, y: Float, atMs: Long): Map<String, Any> =
            mapOf("x" to x.toDouble(), "y" to y.toDouble(), "at" to atMs)

        fun fromMap(value: Any?, nowMs: Long = System.currentTimeMillis()): KissTouch? {
            val map = value as? Map<*, *> ?: return null
            val at = (map["at"] as? Number)?.toLong() ?: return null
            if (nowMs - at > STALE_MS) return null
            return KissTouch(
                x = ((map["x"] as? Number)?.toFloat() ?: 0.5f).coerceIn(0f, 1f),
                y = ((map["y"] as? Number)?.toFloat() ?: 0.5f).coerceIn(0f, 1f),
                heardAt = SystemClock.elapsedRealtime()
            )
        }
    }
}

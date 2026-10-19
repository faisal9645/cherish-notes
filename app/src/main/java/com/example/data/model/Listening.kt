package com.example.data.model

import android.os.SystemClock

/**
 * One of us playing the other's voice note (or watching their video note) right now, saved on
 * the listener's profile as "listening" only when it starts, pauses, jumps or stops, so the other
 * phone can show it live: "listening…", with the waveform moving as it's heard.
 */
data class PartnerListening(
    val messageId: String,
    val isVideo: Boolean,
    // Where they were in it when that was saved, and how long it is (ms)
    val positionMs: Long,
    val durationMs: Long,
    val speed: Float,
    val playing: Boolean,
    // This phone's clock (elapsed realtime) for [positionMs]: playing on from there, it's timed here,
    // so the two phones' clocks needn't agree
    val anchorElapsed: Long
) {
    /** Where they are in it now, 0-1. */
    fun progress(elapsedNow: Long = SystemClock.elapsedRealtime()): Float {
        if (durationMs <= 0L) return 0f
        val heard = if (playing) positionMs + ((elapsedNow - anchorElapsed).coerceAtLeast(0L) * speed).toLong() else positionMs
        return (heard.toFloat() / durationMs).coerceIn(0f, 1f)
    }

    /** Still going on: one that would have ended by now (their app closed mid-way), or left paused a while, isn't. */
    fun isCurrent(elapsedNow: Long = SystemClock.elapsedRealtime()): Boolean {
        val since = elapsedNow - anchorElapsed
        return if (playing) {
            durationMs <= 0L || positionMs + since * speed <= durationMs + END_GRACE_MS
        } else {
            since < PAUSED_FOR_MS
        }
    }

    companion object {
        const val FIELD = "listening"
        private const val END_GRACE_MS = 3_000L
        private const val PAUSED_FOR_MS = 2 * 60 * 1000L

        /** How it's saved on the listener's profile ([atMs]: their clock when they saved it). */
        fun toMap(messageId: String, isVideo: Boolean, positionMs: Long, durationMs: Long, speed: Float, playing: Boolean, atMs: Long) =
            mapOf(
                "id" to messageId,
                "video" to isVideo,
                "pos" to positionMs,
                "dur" to durationMs,
                "speed" to speed.toDouble(),
                "playing" to playing,
                "at" to atMs
            )

        /**
         * Read back on the other phone. [nowMs]/[elapsedNow]: this phone's clocks as it arrives.
         * The time since it was saved is taken from the two clocks only up to a few seconds (the
         * time a change takes to arrive), and one saved long ago is over.
         */
        fun fromMap(value: Any?, nowMs: Long = System.currentTimeMillis(), elapsedNow: Long = SystemClock.elapsedRealtime()): PartnerListening? {
            val map = value as? Map<*, *> ?: return null
            val id = (map["id"] as? String)?.ifBlank { null } ?: return null
            val at = (map["at"] as? Number)?.toLong() ?: return null
            val durationMs = (map["dur"] as? Number)?.toLong() ?: 0L
            val speed = ((map["speed"] as? Number)?.toFloat() ?: 1f).coerceIn(0.5f, 3f)
            val age = nowMs - at
            if (age > durationMs / speed + STALE_AFTER_MS) return null
            return PartnerListening(
                messageId = id,
                isVideo = map["video"] as? Boolean ?: false,
                positionMs = (map["pos"] as? Number)?.toLong() ?: 0L,
                durationMs = durationMs,
                speed = speed,
                playing = map["playing"] as? Boolean ?: false,
                anchorElapsed = elapsedNow - age.coerceIn(0L, ARRIVAL_MS)
            )
        }

        private const val STALE_AFTER_MS = 3 * 60 * 1000L
        private const val ARRIVAL_MS = 4_000L
    }
}

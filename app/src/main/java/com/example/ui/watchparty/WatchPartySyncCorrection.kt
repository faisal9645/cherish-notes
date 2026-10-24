package com.example.ui.watchparty

import kotlin.math.abs

/** Local correction policy; survives player buffering and never writes shared playback state. */
internal class WatchPartySyncCorrection {
    private var lastSeekAt: Long? = null
    private var driftSamples = 0
    private var commandPending = false

    fun shouldSeek(
        drift: Float,
        playing: Boolean,
        recovering: Boolean,
        freshSample: Boolean,
        commandChanged: Boolean,
        now: Long
    ): Boolean {
        commandPending = commandPending || commandChanged
        if (recovering || !freshSample || !drift.isFinite()) {
            driftSamples = 0
            return false
        }
        val distance = abs(drift)
        val explicit = commandPending
        commandPending = false
        driftSamples = if (distance > 0.9f) driftSamples + 1 else 0
        val correctionNeeded = when {
            explicit -> distance > 0.35f
            !playing -> distance > 0.3f
            distance > 2f -> true
            else -> driftSamples >= 3
        }
        val cooledDown = lastSeekAt?.let { now - it >= 3_000L } ?: true
        if (!correctionNeeded || (!explicit && !cooledDown)) return false
        lastSeekAt = now
        driftSamples = 0
        return true
    }
}

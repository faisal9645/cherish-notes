package com.example.util

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import kotlinx.coroutines.*

class HeartbeatHapticHelper(private val context: Context) {
    private val vibrator: Vibrator? by lazy {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vm?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            }
        } catch (_: Exception) {
            null
        }
    }

    private var heartbeatJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    /**
     * Start playing synchronized heartbeat vibrations (lub-dub rhythm at ~72 BPM)
     */
    fun startHeartbeat() {
        if (heartbeatJob?.isActive == true) return
        heartbeatJob = scope.launch {
            while (isActive) {
                playSingleHeartbeat()
                delay(820) // Resting interval between heartbeats
            }
        }
    }

    fun stopHeartbeat() {
        heartbeatJob?.cancel()
        heartbeatJob = null
        try {
            vibrator?.cancel()
        } catch (_: Exception) {}
    }

    private fun playSingleHeartbeat() {
        try {
            val vib = vibrator ?: return
            if (!vib.hasVibrator()) return

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                // Timings: 0ms delay, 45ms (lub), 80ms rest, 70ms (dub)
                // Amplitudes: 0, 160 (lub), 0, 255 (dub)
                val timings = longArrayOf(0, 45, 80, 70)
                val amplitudes = intArrayOf(0, 160, 0, 255)
                val effect = VibrationEffect.createWaveform(timings, amplitudes, -1)
                vib.vibrate(effect)
            } else {
                @Suppress("DEPRECATION")
                vib.vibrate(longArrayOf(0, 45, 80, 70), -1)
            }
        } catch (_: Exception) {}
    }
}

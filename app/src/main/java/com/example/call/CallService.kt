package com.example.call

import android.annotation.SuppressLint
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Keeps a call going when the app is in the background or the screen is off: a foreground
 * service (phone call + microphone) with the call's notification. It holds the CPU and Wi-Fi
 * awake for the call, so the audio doesn't stutter with the screen off. All call logic is in
 * [CallManager]; this only lives as long as the call does.
 */
class CallService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var updates: Job? = null
    private var cpuLock: PowerManager.WakeLock? = null
    private var wifiLock: WifiManager.WifiLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = CallNotifications.buildOngoing(this, CallManager.state.value)
        try {
            ServiceCompat.startForeground(this, CallNotifications.ID_ONGOING, notification, serviceTypes())
        } catch (e: Exception) {
            // Not allowed right now (e.g. no microphone permission): the call goes on only while
            // the call screen is open
            Log.w(TAG, "Couldn't keep the call going in the background", e)
            stopSelf()
            return START_NOT_STICKY
        }
        holdLocks()
        if (updates == null) {
            updates = scope.launch {
                CallManager.state
                    .map { Triple(it.phase, it.recordingStatus, it.peerName) to it.connectedAtElapsed }
                    .distinctUntilChanged()
                    .collect {
                        val state = CallManager.state.value
                        if (state.phase == CallPhase.IDLE || state.phase == CallPhase.ENDED) {
                            stopSelf()
                        } else {
                            CallNotifications.updateOngoing(this@CallService, state)
                        }
                    }
            }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        releaseLocks()
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }

    private fun serviceTypes(): Int = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.R ->
            ServiceInfo.FOREGROUND_SERVICE_TYPE_PHONE_CALL or ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q -> ServiceInfo.FOREGROUND_SERVICE_TYPE_PHONE_CALL
        else -> 0
    }

    @SuppressLint("WakelockTimeout")
    private fun holdLocks() {
        try {
            if (cpuLock == null) {
                val power = getSystemService(Context.POWER_SERVICE) as? PowerManager
                cpuLock = power?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Cherish:Call")?.apply {
                    setReferenceCounted(false)
                    acquire(MAX_CALL_MS)
                }
            }
            if (wifiLock == null) {
                val wifi = applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
                val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    WifiManager.WIFI_MODE_FULL_LOW_LATENCY
                } else {
                    @Suppress("DEPRECATION")
                    WifiManager.WIFI_MODE_FULL_HIGH_PERF
                }
                wifiLock = wifi?.createWifiLock(mode, "Cherish:Call")?.apply {
                    setReferenceCounted(false)
                    acquire()
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Couldn't hold the call awake", e)
        }
    }

    private fun releaseLocks() {
        try {
            cpuLock?.takeIf { it.isHeld }?.release()
        } catch (_: Exception) {}
        try {
            wifiLock?.takeIf { it.isHeld }?.release()
        } catch (_: Exception) {}
        cpuLock = null
        wifiLock = null
    }

    companion object {
        private const val TAG = "CallService"
        private const val MAX_CALL_MS = 6L * 60 * 60 * 1000

        /** Starts it from the visible call screen (Android only allows a microphone service then). */
        fun start(context: Context): Boolean = try {
            ContextCompat.startForegroundService(context, Intent(context, CallService::class.java))
            true
        } catch (e: Exception) {
            Log.w(TAG, "Couldn't start the call service", e)
            false
        }

        fun stop(context: Context) {
            try {
                context.stopService(Intent(context, CallService::class.java))
            } catch (_: Exception) {}
        }
    }
}

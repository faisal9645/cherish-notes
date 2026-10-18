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
import androidx.core.app.NotificationManagerCompat
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
 *
 * It also keeps a ringing call ringing (startRinging): a phone call service with the ringing
 * notification as its own, so the app stays awake and running to ring on a locked or idle phone,
 * even when only the call's push started it. Answered, the same service goes on as the call's.
 */
class CallService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var updates: Job? = null
    private var cpuLock: PowerManager.WakeLock? = null
    private var wifiLock: WifiManager.WifiLock? = null
    /** Up for a ringing call (its notification is the ringing one), not for a call that's on. */
    private var ringing = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startPending = false
        val started = if (intent?.action == ACTION_RING) ringInForeground(CallActivity.inviteFrom(intent)) else callInForeground()
        if (!started) return START_NOT_STICKY
        if (stopWhenStarted) {
            // Over before it got going
            stopWhenStarted = false
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
                        } else if (!ringing) {
                            CallNotifications.updateOngoing(this@CallService, state)
                        }
                    }
            }
        }
        return START_NOT_STICKY
    }

    /** Rings [invite]: its ringing notification is this service's own, and the app rings. */
    private fun ringInForeground(invite: CallInvite?): Boolean {
        val state = CallManager.state.value
        val stillRinging = invite != null && state.phase == CallPhase.INCOMING_RINGING && state.callId == invite.callId
        val notification = if (invite != null) {
            CallNotifications.buildIncoming(this, invite, CallNotifications.Ringing.APP)
        } else {
            CallNotifications.buildOngoing(this, state)
        }
        val id = if (invite != null) CallNotifications.ID_INCOMING else CallNotifications.ID_ONGOING
        try {
            ServiceCompat.startForeground(this, id, notification, ringTypes())
        } catch (e: Exception) {
            Log.w(TAG, "Couldn't ring from the app", e)
            ringingCallId = null
            stopSelf()
            // The notification rings by itself instead
            if (stillRinging && invite != null) CallNotifications.showRingingNotification(this, invite)
            return false
        }
        if (!stillRinging || invite == null) {
            // Answered, declined or called off meanwhile
            ringingCallId = null
            stopSelf()
            return false
        }
        ringing = true
        CallNotifications.ringFromApp(this, invite)
        return true
    }

    /** A call that's on (or being placed or answered): its call notification, microphone included. */
    private fun callInForeground(): Boolean {
        val notification = CallNotifications.buildOngoing(this, CallManager.state.value)
        try {
            ServiceCompat.startForeground(this, CallNotifications.ID_ONGOING, notification, serviceTypes())
        } catch (e: Exception) {
            // Not allowed right now (e.g. no microphone permission): the call goes on only while
            // the call screen is open
            Log.w(TAG, "Couldn't keep the call going in the background", e)
            ringing = false
            ringingCallId = null
            stopSelf()
            return false
        }
        ringingCallId = null
        if (ringing) {
            // Answered: the ringing notification goes (it isn't this service's own any more)
            ringing = false
            try {
                NotificationManagerCompat.from(this).cancel(CallNotifications.ID_INCOMING)
            } catch (_: Exception) {}
        }
        return true
    }

    override fun onDestroy() {
        if (ringing) CallRinger.stop(this)
        ringing = false
        ringingCallId = null
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

    /**
     * Ringing is a phone call without the microphone: Android lets a push start that from the
     * background (it doesn't let one start the microphone).
     */
    private fun ringTypes(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) ServiceInfo.FOREGROUND_SERVICE_TYPE_PHONE_CALL else 0

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
        private const val ACTION_RING = "com.cherish.notes.call.RING"

        // Main thread only (CallManager and the service both run there)
        /** The call this service is ringing for (null while it carries a call that's on, or isn't up). */
        @Volatile private var ringingCallId: String? = null
        /** Asked to start and not started yet: Android wants it in the foreground before it may stop. */
        @Volatile private var startPending = false
        @Volatile private var stopWhenStarted = false

        fun isRingingFor(callId: String): Boolean = ringingCallId == callId

        /**
         * Rings [invite] from the app itself, kept awake and running by this service (see the
         * class). False when Android doesn't allow that right now: its notification rings then.
         */
        fun startRinging(context: Context, invite: CallInvite): Boolean = try {
            ContextCompat.startForegroundService(
                context,
                CallActivity.putInvite(Intent(context, CallService::class.java).setAction(ACTION_RING), invite)
            )
            ringingCallId = invite.callId
            startPending = true
            stopWhenStarted = false
            true
        } catch (e: Exception) {
            Log.w(TAG, "Couldn't ring from the app", e)
            false
        }

        /** Starts it from the visible call screen (Android only allows a microphone service then). */
        fun start(context: Context): Boolean = try {
            ContextCompat.startForegroundService(context, Intent(context, CallService::class.java))
            startPending = true
            stopWhenStarted = false
            true
        } catch (e: Exception) {
            Log.w(TAG, "Couldn't start the call service", e)
            false
        }

        fun stop(context: Context) {
            ringingCallId = null
            if (startPending) {
                // Stopped as soon as it's started (stopping it before then would crash the app)
                stopWhenStarted = true
                return
            }
            try {
                context.stopService(Intent(context, CallService::class.java))
            } catch (_: Exception) {}
        }

        /** The call stopped ringing without a call going on: the ringing service goes, if it's up. */
        fun stopRinging(context: Context) {
            if (ringingCallId != null) stop(context)
        }
    }
}

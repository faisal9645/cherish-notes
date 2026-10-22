package com.example.sync

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.example.CherishApplication
import com.example.R
import com.example.call.CallInvite
import com.example.call.CallManager
import com.example.call.CallRecord
import com.example.data.model.Message
import com.example.data.model.MessageType
import com.example.notifications.NotificationHelper
import com.example.notifications.ThinkingOfYou
import com.example.security.SecurityPreferences
import com.google.firebase.FirebaseApp
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Background sync service that keeps the phone connected to the partner's messages,
 * voice calls, and heartbeats even when the app is closed or minimized.
 * Disguised as a silent "Notes Sync" service matching the app's security settings.
 */
class CherishSyncService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var messagesListener: ListenerRegistration? = null
    private var callControlListener: ListenerRegistration? = null
    private var coupleDocListener: ListenerRegistration? = null
    private var isInitialSnapshot = true

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        // Disabled: User requested to remove always-running background service.
        // Push notifications work purely on-demand via FCM (Firebase Messaging).
        stopSelf()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        stopSelf()
        return START_NOT_STICKY
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        // No auto-resurrection: allow app to rest when not in use
    }

    override fun onDestroy() {
        detachListeners()
        scope.cancel()
        try {
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        } catch (_: Throwable) {}
        super.onDestroy()
    }

    private var currentAttachedCoupleId: String? = null
    private var currentAttachedUid: String? = null

    private fun observeAccount() {
        val app = applicationContext as? CherishApplication ?: return

        // 1. Immediately attach from persisted session credentials on startup
        val savedUid = app.authRepository.getCurrentUserId().trim().ifBlank { null }
        val savedCoupleId = app.authRepository.getSavedCoupleId().trim().ifBlank { null }
        if (!savedCoupleId.isNullOrBlank() && !savedUid.isNullOrBlank()) {
            currentAttachedCoupleId = savedCoupleId
            currentAttachedUid = savedUid
            attachListeners(savedCoupleId, savedUid)
        }

        // 2. React to dynamic account switches / updates
        scope.launch {
            app.authRepository.currentUserState.collectLatest { user ->
                val coupleId = user?.coupleId?.trim()?.ifBlank { null }
                    ?: app.authRepository.getSavedCoupleId().trim().ifBlank { null }
                val myUid = user?.id?.trim()?.ifBlank { null }
                    ?: app.authRepository.getCurrentUserId().trim().ifBlank { null }
                if (!coupleId.isNullOrBlank() && !myUid.isNullOrBlank()) {
                    if (coupleId != currentAttachedCoupleId || myUid != currentAttachedUid) {
                        currentAttachedCoupleId = coupleId
                        currentAttachedUid = myUid
                        attachListeners(coupleId, myUid)
                    }
                } else if (app.authRepository.getSavedCoupleId().isBlank()) {
                    currentAttachedCoupleId = null
                    currentAttachedUid = null
                    detachListeners()
                }
            }
        }
    }

    private fun attachListeners(coupleId: String, myUid: String) {
        detachListeners()

        if (FirebaseApp.getApps(this).isEmpty()) return
        val fs = FirebaseFirestore.getInstance()
        val cleanMyUid = myUid.removePrefix("user_").lowercase()

        // 1. Listen for new messages today
        val startOfToday = (applicationContext as? CherishApplication)?.chatRepository?.getStartOfToday()
            ?: (System.currentTimeMillis() - 24 * 3600 * 1000L)

        isInitialSnapshot = true
        val messagesQuery = fs.collection("conversations")
            .document(coupleId)
            .collection("messages")
            .whereGreaterThanOrEqualTo("timestamp", startOfToday)
            .orderBy("timestamp", Query.Direction.ASCENDING)

        messagesListener = messagesQuery.addSnapshotListener { snapshot, error ->
            if (error != null || snapshot == null) return@addSnapshotListener
            if (!isInitialSnapshot) {
                for (change in snapshot.documentChanges) {
                    if (change.type == com.google.firebase.firestore.DocumentChange.Type.ADDED) {
                        val msg = change.document.toMessageOrNull() ?: continue
                        if (msg.id.isBlank()) msg.id = change.document.id
                        if (msg.getTypedType() == MessageType.CALL_RECORDING) continue

                        val senderClean = msg.senderId.removePrefix("user_").lowercase()
                        if (senderClean != cleanMyUid) {
                            // If user is actively viewing chat screen or app is open on screen, skip duplicate notification
                            if (!NotificationHelper.isAppOpenOnScreen(this) && !NotificationHelper.isUserActivelyViewingChat(this)) {
                                val previewText = when (msg.getTypedType()) {
                                    MessageType.TEXT -> msg.text
                                    MessageType.IMAGE -> "Photo"
                                    MessageType.AUDIO -> "Voice message"
                                    MessageType.VIDEO -> "Video"
                                    MessageType.DOCUMENT -> "Document"
                                    else -> "New message"
                                }
                                NotificationHelper.showMessageNotification(
                                    context = this,
                                    senderName = msg.senderName,
                                    messageText = previewText,
                                    conversationId = coupleId,
                                    messageId = msg.id
                                )
                            }
                        }
                    }
                }
            }
            isInitialSnapshot = false
        }

        // 2. Listen for incoming calls
        callControlListener = fs.collection("couples")
            .document(coupleId)
            .collection("callControl")
            .document("active")
            .addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null || !snapshot.exists()) return@addSnapshotListener
                val state = snapshot.getString("state").orEmpty()
                val calleeId = snapshot.getString("calleeId").orEmpty()
                val callerId = snapshot.getString("callerId").orEmpty()
                val callId = snapshot.getString("callId").orEmpty()
                val ringExpiresAt = snapshot.getRingExpiry()

                val cleanCallee = calleeId.removePrefix("user_").lowercase()
                val cleanCaller = callerId.removePrefix("user_").lowercase()
                val isForMe = (cleanCallee == cleanMyUid) || (cleanCaller.isNotBlank() && cleanCaller != cleanMyUid) || cleanCallee.isBlank()
                val now = System.currentTimeMillis()

                if (state == CallRecord.STATE_RINGING && isForMe && (ringExpiresAt > (now - 30_000L) || ringExpiresAt == 0L)) {
                    val partnerName = (applicationContext as? CherishApplication)?.authRepository?.partnerUserState?.value?.displayName.orEmpty()
                    CallManager.onIncomingInvite(
                        CallInvite(
                            callId = callId,
                            coupleId = coupleId,
                            callerId = callerId,
                            callerName = partnerName,
                            ringUntil = if (ringExpiresAt > now) ringExpiresAt else (now + 75_000L)
                        )
                    )
                }
            }

        // 3. Listen for heartbeat ("thinking of you") signals and reciprocal felt acknowledgments
        var isInitialCoupleDoc = true
        coupleDocListener = fs.collection("couples")
            .document(coupleId)
            .addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null || !snapshot.exists()) return@addSnapshotListener

                // A. Partner sent a heartbeat trigger to us
                (snapshot.get("signal") as? Map<*, *>)?.let { signal ->
                    val type = signal["type"] as? String
                    val signalId = signal["id"] as? String ?: ""
                    val senderId = signal["senderId"] as? String ?: ""
                    val cleanSender = senderId.removePrefix("user_").lowercase()
                    if (type == "heartbeat" && cleanSender.isNotBlank() && cleanSender != cleanMyUid) {
                        val sentAt = (signal["at"] as? com.google.firebase.Timestamp)?.toDate()?.time ?: System.currentTimeMillis()
                        if (!isInitialCoupleDoc || (System.currentTimeMillis() - sentAt < 90_000L)) {
                            val played = ThinkingOfYou.onSignal(this, signalId, sentAt)
                            val isScreenOn = ThinkingOfYou.isScreenInteractive(this)
                            val isAppOpen = NotificationHelper.isAppOpenOnScreen(this)
                            if (played && isScreenOn && isAppOpen) {
                                (applicationContext as? CherishApplication)?.authRepository?.acknowledgeHeartbeatFelt(signalId)
                            } else if (played && isScreenOn) {
                                ThinkingOfYou.setPendingHeartbeatFelt(this, signalId, sentAt)
                            }
                        }
                    }
                }

                // B. Partner felt our heartbeat trigger
                (snapshot.get("signalFelt") as? Map<*, *>)?.let { felt ->
                    val type = felt["type"] as? String
                    val signalId = felt["id"] as? String ?: ""
                    val senderId = felt["senderId"] as? String ?: ""
                    val cleanSender = senderId.removePrefix("user_").lowercase()
                    if (type == "heartbeat_felt" && cleanSender.isNotBlank() && cleanSender != cleanMyUid) {
                        val feltAt = (felt["at"] as? com.google.firebase.Timestamp)?.toDate()?.time ?: System.currentTimeMillis()
                        if (!isInitialCoupleDoc || (System.currentTimeMillis() - feltAt < 90_000L)) {
                            ThinkingOfYou.onPartnerFeltHeartbeat(this, signalId, feltAt)
                        }
                    }
                }

                isInitialCoupleDoc = false
            }
    }

    private fun detachListeners() {
        messagesListener?.remove()
        messagesListener = null
        callControlListener?.remove()
        callControlListener = null
        coupleDocListener?.remove()
        coupleDocListener = null
    }

    private fun createSyncChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Background sync",
                NotificationManager.IMPORTANCE_MIN
            ).apply {
                description = "Keeps everything up to date"
                setShowBadge(false)
                enableVibration(false)
                enableLights(false)
                setSound(null, null)
                lockscreenVisibility = android.app.Notification.VISIBILITY_SECRET
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            manager?.createNotificationChannel(channel)
        }
    }

    private fun buildSyncNotification(): android.app.Notification {
        val prefs = SecurityPreferences.getInstance(this)
        val isCalc = prefs.getDisguiseStyle() == SecurityPreferences.DISGUISE_STYLE_CALCULATOR
        val maskedTitle = prefs.getMaskedNotificationTitle()
        val appIcon = com.example.notifications.NotificationHelper.getAppIconBitmap(this)
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(if (isCalc) R.drawable.ic_stat_calculator else R.drawable.ic_stat_notes)
            .apply {
                if (appIcon != null) setLargeIcon(appIcon)
            }
            .setColor(if (isCalc) 0xFF2E7D32.toInt() else 0xFF2563FF.toInt())
            .setContentTitle(maskedTitle)
            .setContentText(if (isCalc) "Updating calculation history" else "Syncing notes & checklists")
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setOngoing(true)
            .setAutoCancel(false)
            .setShowWhen(false)
            .setVisibility(NotificationCompat.VISIBILITY_SECRET)
            .build()
    }

    private fun DocumentSnapshot.toMessageOrNull(): Message? {
        val message = try {
            toObject(Message::class.java)
        } catch (_: Exception) {
            null
        } ?: return null
        if (message.id.isBlank()) message.id = id
        if (message.durationSeconds <= 0) {
            val num = (get("durationSeconds") as? Number)?.toInt()
                ?: (get("duration") as? Number)?.toInt()
            if (num != null && num > 0) {
                message.durationSeconds = num
            } else {
                val parsed = Regex("""\((\d+)\s*s\)""").find(message.text)?.groupValues?.get(1)?.toIntOrNull()
                if (parsed != null && parsed > 0) {
                    message.durationSeconds = parsed
                } else if (message.waveform.isNotEmpty()) {
                    message.durationSeconds = (message.waveform.size / 10).coerceAtLeast(1)
                }
            }
        }
        return message
    }

    private fun DocumentSnapshot.getRingExpiry(): Long {
        val num = try { getLong("ringExpiresAtMs") } catch (_: Exception) { null }
        if (num != null && num > 0L) return num
        val ts = try { getTimestamp("ringExpiresAt")?.toDate()?.time } catch (_: Exception) { null }
        if (ts != null && ts > 0L) return ts
        val directLong = try { getLong("ringExpiresAt") } catch (_: Exception) { null }
        if (directLong != null && directLong > 0L) return directLong
        return System.currentTimeMillis() + 45_000L
    }

    companion object {
        private const val TAG = "CherishSyncService"
        private const val CHANNEL_ID = "notes_sync_service_channel"
        private const val NOTIFICATION_ID = 1004

        fun start(context: Context) {
            // Disabled: Notifications now work purely on-demand via standard FCM push notifications like other modern messaging apps
            stop(context)
        }

        fun stop(context: Context) {
            try {
                // 1. Cancel any existing resurrection alarm
                val restartServiceIntent = Intent(context.applicationContext, CherishSyncService::class.java).also {
                    it.setPackage(context.packageName)
                }
                val restartPendingIntent = PendingIntent.getService(
                    context.applicationContext,
                    1005,
                    restartServiceIntent,
                    PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
                )
                if (restartPendingIntent != null) {
                    val alarmService = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
                    alarmService?.cancel(restartPendingIntent)
                    restartPendingIntent.cancel()
                }
            } catch (_: Exception) {}

            try {
                // 2. Stop running service
                val intent = Intent(context, CherishSyncService::class.java)
                context.stopService(intent)
            } catch (_: Exception) {}

            try {
                // 3. Remove notification
                val nm = androidx.core.app.NotificationManagerCompat.from(context)
                nm.cancel(NOTIFICATION_ID)
            } catch (_: Exception) {}
        }
    }
}

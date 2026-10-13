package com.example.sync

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
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
        createSyncChannel()
        val notification = buildSyncNotification()
        val serviceType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
        } else 0

        try {
            ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, serviceType)
        } catch (e: Exception) {
            Log.w(TAG, "startForeground for CherishSyncService: ${e.message}")
        }

        observeAccount()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    override fun onDestroy() {
        detachListeners()
        scope.cancel()
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }

    private var currentAttachedCoupleId: String? = null
    private var currentAttachedUid: String? = null

    private fun observeAccount() {
        val app = applicationContext as? CherishApplication ?: return
        scope.launch {
            app.authRepository.currentUserState.collectLatest { user ->
                val coupleId = user?.coupleId?.trim()?.ifBlank { null }
                val myUid = user?.id?.trim()?.ifBlank { null }
                if (!coupleId.isNullOrBlank() && !myUid.isNullOrBlank()) {
                    if (coupleId != currentAttachedCoupleId || myUid != currentAttachedUid) {
                        currentAttachedCoupleId = coupleId
                        currentAttachedUid = myUid
                        attachListeners(coupleId, myUid)
                    }
                } else {
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

        // 3. Listen for heartbeat ("thinking of you") signals
        coupleDocListener = fs.collection("couples")
            .document(coupleId)
            .addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null || !snapshot.exists()) return@addSnapshotListener
                (snapshot.get("signal") as? Map<*, *>)?.let { signal ->
                    val type = signal["type"] as? String
                    val signalId = signal["id"] as? String ?: ""
                    val senderId = signal["senderId"] as? String ?: ""
                    val cleanSender = senderId.removePrefix("user_").lowercase()
                    if (type == "heartbeat" && cleanSender.isNotBlank() && cleanSender != cleanMyUid) {
                        val sentAt = (signal["at"] as? com.google.firebase.Timestamp)?.toDate()?.time ?: System.currentTimeMillis()
                        ThinkingOfYou.onSignal(this, signalId, sentAt)
                    }
                }
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
                "Notes Sync",
                NotificationManager.IMPORTANCE_MIN
            ).apply {
                description = "Keeps notes, checklists and reminders up to date"
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
        val maskedTitle = prefs.getMaskedNotificationTitle()
        val appIcon = com.example.notifications.NotificationHelper.getAppIconBitmap(this)
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_notes)
            .apply {
                if (appIcon != null) setLargeIcon(appIcon)
            }
            .setContentTitle(maskedTitle)
            .setContentText("Syncing notes & checklists")
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setOngoing(false)
            .setAutoCancel(true)
            .setShowWhen(false)
            .build()
    }

    private fun DocumentSnapshot.toMessageOrNull(): Message? {
        val message = try {
            toObject(Message::class.java)
        } catch (_: Exception) {
            null
        } ?: return null
        if (message.id.isBlank()) message.id = id
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
            try {
                val intent = Intent(context, CherishSyncService::class.java)
                ContextCompat.startForegroundService(context, intent)
            } catch (e: Exception) {
                Log.w(TAG, "Failed starting CherishSyncService: ${e.message}")
            }
        }

        fun stop(context: Context) {
            try {
                val intent = Intent(context, CherishSyncService::class.java)
                context.stopService(intent)
            } catch (_: Exception) {}
        }
    }
}

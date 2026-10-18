package com.example.notifications

import android.util.Log
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

class CherishFirebaseMessagingService : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        // Saved on this couple account's profile (not the anonymous sign-in id), so pushes for new
        // messages reach this phone
        try {
            (applicationContext as? com.example.CherishApplication)?.authRepository?.saveFcmToken(token)
        } catch (e: Exception) {
            Log.e("FCM", "Failed to save the push token", e)
        }
    }

    /**
     * A push for a new message (sent by the notifyPartner Cloud Function), also when the app is
     * closed. The notification follows this phone's settings: off, hidden content or Notes disguise.
     */
    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)
        Log.d("CherishFCM", "Push received: data=${remoteMessage.data}, notification=${remoteMessage.notification?.title}")

        // Acquire a 3-second wakelock to ensure the CPU stays awake while processing the incoming message
        try {
            val pm = applicationContext.getSystemService(android.content.Context.POWER_SERVICE) as? android.os.PowerManager
            pm?.newWakeLock(android.os.PowerManager.PARTIAL_WAKE_LOCK, "cherish:fcm_receive_wake")?.acquire(3000L)
        } catch (_: Exception) {}

        val app = applicationContext as? com.example.CherishApplication ?: run {
            Log.w("CherishFCM", "applicationContext is not CherishApplication")
            return
        }

        // Calls ring even with the app open or closed (they open their own screen, never the chat or Notes)
        when (remoteMessage.data["type"]) {
            "call_invite" -> {
                // Keep the device awake and wake screen for incoming call even in idle/Doze state
                try {
                    val pm = applicationContext.getSystemService(android.content.Context.POWER_SERVICE) as? android.os.PowerManager
                    @Suppress("DEPRECATION")
                    pm?.newWakeLock(
                        android.os.PowerManager.FULL_WAKE_LOCK or
                        android.os.PowerManager.ACQUIRE_CAUSES_WAKEUP or
                        android.os.PowerManager.ON_AFTER_RELEASE,
                        "cherish:fcm_call_incoming_wake"
                    )?.acquire(25_000L)
                } catch (_: Exception) {
                    try {
                        val pm = applicationContext.getSystemService(android.content.Context.POWER_SERVICE) as? android.os.PowerManager
                        pm?.newWakeLock(android.os.PowerManager.PARTIAL_WAKE_LOCK, "cherish:fcm_call_cpu_wake")?.acquire(60_000L)
                    } catch (_: Exception) {}
                }
                com.example.call.CallManager.onPushInvite(remoteMessage.data)
                return
            }
            "call_cancel" -> {
                com.example.call.CallManager.onPushCancel(remoteMessage.data)
                return
            }
        }

        val hasSession = try {
            app.authRepository.isUserLoggedIn() ||
                app.securityPreferences.getCoupleSecretKey().isNotBlank() ||
                app.authRepository.getSavedCoupleId().isNotBlank()
        } catch (_: Exception) {
            true // Fail open so incoming push is never dropped
        }

        if (!hasSession) {
            Log.w("CherishFCM", "No active user or couple session, skipping message notification")
            return
        }

        val myUid = app.authRepository.getCurrentUserId()
        val senderId = remoteMessage.data["senderId"]
        val cleanMyUid = myUid.removePrefix("user_").trim().lowercase()
        val cleanSenderId = senderId?.removePrefix("user_")?.trim()?.lowercase()
        if (!cleanSenderId.isNullOrBlank() && cleanMyUid.isNotBlank() && cleanSenderId == cleanMyUid) {
            Log.d("CherishFCM", "Message sent by self ($senderId), skipping notification")
            return
        }

        // "Thinking of you": a heartbeat pulse and notification based on settings
        if (remoteMessage.data["type"] == "heartbeat") {
            val signalId = remoteMessage.data["signalId"].orEmpty()
            val sentAt = remoteMessage.data["sentAt"]?.toLongOrNull() ?: System.currentTimeMillis()
            val handled = ThinkingOfYou.onSignal(
                applicationContext,
                signalId,
                sentAt
            )
            val isScreenOn = ThinkingOfYou.isScreenInteractive(applicationContext)
            val isAppOpen = NotificationHelper.isAppOpenOnScreen(applicationContext)
            if (handled && signalId.isNotBlank()) {
                if (isScreenOn && isAppOpen) {
                    app.authRepository.acknowledgeHeartbeatFelt(signalId)
                } else {
                    ThinkingOfYou.setPendingHeartbeatFelt(applicationContext, signalId, sentAt)
                }
            }
            return
        }

        if (remoteMessage.data["type"] == "heartbeat_felt") {
            val signalId = remoteMessage.data["signalId"].orEmpty()
            val sentAt = remoteMessage.data["sentAt"]?.toLongOrNull() ?: System.currentTimeMillis()
            ThinkingOfYou.onPartnerFeltHeartbeat(
                applicationContext,
                signalId,
                sentAt
            )
            return
        }

        val rawSenderName = remoteMessage.data["senderName"]
            ?: remoteMessage.notification?.title
            ?: ""
        val partnerName = app.authRepository.partnerUserState.value?.displayName
        val senderName = when {
            rawSenderName.isNotBlank() && !rawSenderName.startsWith("user_") -> rawSenderName
            !partnerName.isNullOrBlank() -> partnerName
            else -> "Your Partner"
        }
        val messageText = remoteMessage.data["messageText"]
            ?: remoteMessage.notification?.body
            ?: "Sent a message"
        val conversationId = remoteMessage.data["conversationId"]
        val messageId = remoteMessage.data["messageId"] ?: remoteMessage.data["id"] ?: remoteMessage.messageId

        // If user is actively viewing the live chat on screen, suppress status bar notification
        if (NotificationHelper.isUserActivelyViewingChat(applicationContext)) {
            Log.d("CherishFCM", "User is actively viewing chat, suppressing status bar notification")
            return
        }

        Log.d("CherishFCM", "Posting notification from $senderName, messageId=$messageId")
        NotificationHelper.showMessageNotification(
            context = applicationContext,
            senderName = senderName,
            messageText = messageText,
            conversationId = conversationId,
            messageId = messageId
        )
    }
}

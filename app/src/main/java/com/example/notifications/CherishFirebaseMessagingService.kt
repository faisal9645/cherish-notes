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
        val app = applicationContext as? com.example.CherishApplication ?: return
        if (!app.authRepository.isUserLoggedIn()) return
        if (remoteMessage.data["senderId"] == app.authRepository.getCurrentUserId()) return

        // "Thinking of you": a heartbeat pulse and notification based on settings
        if (remoteMessage.data["type"] == "heartbeat") {
            ThinkingOfYou.onSignal(
                applicationContext,
                remoteMessage.data["signalId"].orEmpty(),
                remoteMessage.data["sentAt"]?.toLongOrNull() ?: System.currentTimeMillis()
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

        // If user is inside the Cherish app on screen, never show system notification
        if (NotificationHelper.isAppOpenOnScreen(applicationContext)) {
            return
        }

        NotificationHelper.showMessageNotification(
            context = applicationContext,
            senderName = senderName,
            messageText = messageText,
            conversationId = conversationId,
            messageId = messageId
        )
    }
}

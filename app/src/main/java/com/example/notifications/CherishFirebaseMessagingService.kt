package com.example.notifications

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

class CherishFirebaseMessagingService : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d("FCM", "Refreshed token: $token")
        val currentUserId = FirebaseAuth.getInstance().currentUser?.uid?.trim()?.ifBlank { null }
        if (currentUserId != null) {
            try {
                FirebaseFirestore.getInstance()
                    .collection("users")
                    .document(currentUserId)
                    .update("fcmToken", token)
            } catch (e: Exception) {
                Log.e("FCM", "Failed to update token in Firestore", e)
            }
        }
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)

        val senderName = remoteMessage.data["senderName"]
            ?: remoteMessage.notification?.title
            ?: "Your Partner"
        val messageText = remoteMessage.data["messageText"]
            ?: remoteMessage.notification?.body
            ?: "Sent a message"
        val conversationId = remoteMessage.data["conversationId"]
        val messageId = remoteMessage.data["messageId"] ?: remoteMessage.data["id"] ?: remoteMessage.messageId

        NotificationHelper.showMessageNotification(
            context = applicationContext,
            senderName = senderName,
            messageText = messageText,
            conversationId = conversationId,
            messageId = messageId
        )
    }
}

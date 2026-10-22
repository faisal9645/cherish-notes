package com.example.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.CherishApplication
import com.example.security.SecurityPreferences
import com.example.ui.chat.EFFECT_GOODNIGHT
import com.example.ui.chat.markGoodnightShown
import com.example.ui.chat.tonightStartMillis
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Handles 11:00 PM alarm to say or prompt goodnight, as well as direct notification action clicks.
 */
class GoodnightReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        val pending = goAsync()

        CoroutineScope(SupervisorJob() + Dispatchers.Main).launch {
            try {
                when (action) {
                    ACTION_GOODNIGHT_11PM -> {
                        // Immediately schedule tomorrow's 11 PM alarm
                        GoodnightReminderScheduler.scheduleNext(context)

                        val app = context.applicationContext as? CherishApplication ?: return@launch
                        if (!app.authRepository.isUserLoggedIn()) return@launch

                        val prefs = SecurityPreferences.getInstance(context)
                        val partnerName = app.authRepository.partnerUserState.value?.displayName?.trim()?.ifBlank { null }
                            ?: "My Love"

                        // If user is currently looking at the chat screen, in-app GoodnightCard handles it
                        if (NotificationHelper.isAppOpenOnScreen(context)) return@launch

                        // Check if goodnight was already sent tonight
                        val currentUserId = app.authRepository.getCurrentUserId()
                        val tonightStart = tonightStartMillis()
                        val alreadySent = app.chatRepository.messagesFlow.value.any {
                            it.effect == EFFECT_GOODNIGHT && it.senderId == currentUserId && it.timestamp >= tonightStart
                        }
                        if (alreadySent) return@launch

                        if (prefs.isAutoGoodnightAt11Pm()) {
                            // Automatically say good night at 11 PM
                            app.chatRepository.sendMessage(
                                text = "Goodnight, sleep well my love \uD83C\uDF19\u2728",
                                effect = EFFECT_GOODNIGHT
                            )
                            markGoodnightShown(context)
                            NotificationHelper.showGoodnightSentNotification(context, partnerName)
                            withTimeoutOrNull(8_000L) { FirebaseFirestore.getInstance().waitForPendingWrites().await() }
                        } else {
                            // Prompt with notification and direct "Send Goodnight" action
                            NotificationHelper.showGoodnightPromptNotification(context, partnerName)
                        }
                    }

                    ACTION_SEND_GOODNIGHT_DIRECT -> {
                        val app = context.applicationContext as? CherishApplication ?: return@launch
                        if (!app.authRepository.isUserLoggedIn()) return@launch

                        val partnerName = app.authRepository.partnerUserState.value?.displayName?.trim()?.ifBlank { null }
                            ?: "My Love"

                        app.chatRepository.sendMessage(
                            text = "Goodnight, sleep well my love \uD83C\uDF19\u2728",
                            effect = EFFECT_GOODNIGHT
                        )
                        markGoodnightShown(context)
                        NotificationHelper.cancelGoodnightNotification(context)
                        NotificationHelper.showGoodnightSentNotification(context, partnerName)
                        withTimeoutOrNull(8_000L) { FirebaseFirestore.getInstance().waitForPendingWrites().await() }
                    }
                }
            } catch (e: Exception) {
                Log.w("GoodnightReceiver", "Error in GoodnightReminderReceiver: ${e.message}", e)
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION_GOODNIGHT_11PM = "com.cherish.notes.ACTION_GOODNIGHT_11PM"
        const val ACTION_SEND_GOODNIGHT_DIRECT = "com.cherish.notes.ACTION_SEND_GOODNIGHT_DIRECT"
    }
}

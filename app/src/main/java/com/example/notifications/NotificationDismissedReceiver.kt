package com.example.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * BroadcastReceiver triggered when the user dismisses the consolidated Cherish notification
 * or any note reminder notification from the system status bar shade. Resets alert trigger state
 * and clears launcher badges so subsequent incoming alerts are received cleanly.
 */
class NotificationDismissedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val noteId = intent?.getStringExtra(EXTRA_NOTE_ID)
        NotificationHelper.onNotificationDismissed(context, noteId)
    }

    companion object {
        const val EXTRA_NOTE_ID = "dismissed_note_id"
    }
}

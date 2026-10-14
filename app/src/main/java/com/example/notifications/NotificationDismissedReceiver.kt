package com.example.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * BroadcastReceiver triggered when the user dismisses the consolidated Cherish notification
 * from the system status bar shade. Resets alert trigger state so subsequent incoming alerts
 * are received cleanly.
 */
class NotificationDismissedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        NotificationHelper.onNotificationDismissed(context)
    }
}

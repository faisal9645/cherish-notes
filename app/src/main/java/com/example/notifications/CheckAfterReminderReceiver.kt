package com.example.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class CheckAfterReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val partnerName = intent.getStringExtra(EXTRA_PARTNER_NAME) ?: "Your partner"
        NotificationHelper.showCheckAfterReminderNotification(context, partnerName)
    }

    companion object {
        const val ACTION_CHECK_AFTER_EXPIRED = "com.example.ACTION_CHECK_AFTER_EXPIRED"
        const val EXTRA_PARTNER_NAME = "extra_partner_name"
    }
}

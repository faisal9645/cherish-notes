package com.example.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.CherishApplication

/**
 * Reschedules all upcoming note reminders whenever the device boots up or the application package is updated.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action == Intent.ACTION_BOOT_COMPLETED || action == Intent.ACTION_MY_PACKAGE_REPLACED) {
            val app = context.applicationContext as? CherishApplication ?: return
            NoteReminderScheduler.rescheduleAllUpcomingReminders(context, app.notesRepository)
            ScheduledMessageScheduler.rescheduleAll(context)
            GoodnightReminderScheduler.scheduleNext(context)
            com.example.sync.CherishSyncService.stop(context)
            if (action == Intent.ACTION_MY_PACKAGE_REPLACED) {
                com.example.update.OtaUpdateManager.getInstance(context).cleanupDownloadedInstallers(forceAll = true)
            } else {
                com.example.update.OtaUpdateManager.getInstance(context).cleanupDownloadedInstallers(forceAll = false)
            }
        }
    }
}

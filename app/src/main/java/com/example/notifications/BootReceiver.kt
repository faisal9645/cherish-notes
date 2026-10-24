package com.example.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.CherishApplication
import kotlinx.coroutines.launch

/**
 * Reschedules all upcoming note reminders whenever the device boots up or the application package is updated.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action == Intent.ACTION_BOOT_COMPLETED || action == Intent.ACTION_MY_PACKAGE_REPLACED) {
            val app = context.applicationContext as? CherishApplication ?: return
            // Keep the update/boot receiver alive until both launcher aliases and share identity
            // match the saved disguise, even when the user hasn't opened the updated app yet.
            val pending = goAsync()
            kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                try {
                    app.securityPreferences.syncLauncherIcon().join()
                } finally {
                    pending.finish()
                }
            }
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

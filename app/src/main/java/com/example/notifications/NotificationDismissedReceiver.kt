package com.example.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper

/**
 * BroadcastReceiver triggered when the user dismisses the consolidated Cherish notification
 * or any note reminder notification from the system status bar shade. Resets alert trigger state
 * and clears launcher badges so subsequent incoming alerts are received cleanly.
 */
class NotificationDismissedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val noteId = intent?.getStringExtra(EXTRA_NOTE_ID)
        NotificationHelper.onNotificationDismissed(context, noteId)
        if (noteId != null) return
        // A message alert swiped away was seen: the partner's next message alerts this phone again,
        // even if Cherish wasn't opened. The app may not be running, so it waits (briefly) for the write
        val write = (context.applicationContext as? com.example.CherishApplication)
            ?.authRepository?.markMessageAlertSeen() ?: return
        val pending = goAsync()
        val handler = Handler(Looper.getMainLooper())
        var finished = false
        val finish = Runnable {
            if (!finished) {
                finished = true
                pending.finish()
            }
        }
        write.addOnCompleteListener { handler.post(finish) }
        handler.postDelayed(finish, SEEN_WRITE_WAIT_MS)
    }

    companion object {
        const val EXTRA_NOTE_ID = "dismissed_note_id"
        private const val SEEN_WRITE_WAIT_MS = 8_000L
    }
}

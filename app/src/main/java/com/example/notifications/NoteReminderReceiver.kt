package com.example.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.security.SecurityPreferences

class NoteReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val prefs = SecurityPreferences.getInstance(context)
        if (!prefs.isNoteRemindersEnabled()) return

        val noteId = intent.getStringExtra(EXTRA_NOTE_ID) ?: return
        val title = intent.getStringExtra(EXTRA_NOTE_TITLE) ?: "Note Reminder"
        val content = intent.getStringExtra(EXTRA_NOTE_CONTENT) ?: ""

        NotificationHelper.showNoteReminderNotification(
            context = context,
            noteId = noteId,
            title = title,
            content = content
        )
    }

    companion object {
        const val ACTION_NOTE_REMINDER = "com.example.ACTION_NOTE_REMINDER"
        const val EXTRA_NOTE_ID = "extra_note_id"
        const val EXTRA_NOTE_TITLE = "extra_note_title"
        const val EXTRA_NOTE_CONTENT = "extra_note_content"
    }
}

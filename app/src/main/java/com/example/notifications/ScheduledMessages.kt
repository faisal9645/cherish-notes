package com.example.notifications

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.example.CherishApplication
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONArray
import org.json.JSONObject

/** A text message waiting to be sent at [sendAt] (e.g. "Good morning" at 7 AM). */
data class ScheduledMessage(val id: String, val text: String, val sendAt: Long)

/** Scheduled messages, kept on this phone until they've gone out. */
object ScheduledMessageStore {
    private const val PREFS = "cherish_scheduled_messages"
    private const val KEY = "messages"

    private val _messages = MutableStateFlow<List<ScheduledMessage>>(emptyList())
    /** Waiting messages, soonest first. */
    val messages: StateFlow<List<ScheduledMessage>> = _messages.asStateFlow()
    private var loaded = false

    @Synchronized
    fun load(context: Context): List<ScheduledMessage> {
        if (!loaded) {
            val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null)
            _messages.value = parse(raw)
            loaded = true
        }
        return _messages.value
    }

    @Synchronized
    fun add(context: Context, message: ScheduledMessage) {
        save(context, (load(context) + message).sortedBy { it.sendAt })
    }

    @Synchronized
    fun remove(context: Context, id: String) {
        save(context, load(context).filterNot { it.id == id })
    }

    fun find(context: Context, id: String): ScheduledMessage? = load(context).firstOrNull { it.id == id }

    private fun save(context: Context, list: List<ScheduledMessage>) {
        val json = JSONArray()
        list.forEach { json.put(JSONObject().put("id", it.id).put("text", it.text).put("sendAt", it.sendAt)) }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, json.toString()).apply()
        _messages.value = list
    }

    private fun parse(raw: String?): List<ScheduledMessage> {
        if (raw.isNullOrBlank()) return emptyList()
        return try {
            val json = JSONArray(raw)
            (0 until json.length()).map { i ->
                val o = json.getJSONObject(i)
                ScheduledMessage(o.getString("id"), o.getString("text"), o.getLong("sendAt"))
            }.sortedBy { it.sendAt }
        } catch (e: Exception) {
            Log.w("ScheduledMessages", "Unreadable scheduled messages", e)
            emptyList()
        }
    }
}

/** Wakes the phone at a scheduled message's time (exact where allowed) to send it. */
object ScheduledMessageScheduler {
    fun schedule(context: Context, message: ScheduledMessage) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        // A time already passed (phone was off) sends a few seconds from now
        val at = message.sendAt.coerceAtLeast(System.currentTimeMillis() + 5_000L)
        val pending = pendingIntent(context, message.id, PendingIntent.FLAG_UPDATE_CURRENT) ?: return
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending)
            } else {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending)
            }
        } catch (e: Exception) {
            Log.w("ScheduledMessages", "Exact alarm unavailable, using an inexact one", e)
            try {
                alarmManager.set(AlarmManager.RTC_WAKEUP, at, pending)
            } catch (_: Exception) {}
        }
    }

    fun cancel(context: Context, id: String) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        pendingIntent(context, id, PendingIntent.FLAG_NO_CREATE)?.let {
            alarmManager.cancel(it)
            it.cancel()
        }
    }

    /** After a reboot or an update the phone forgets alarms: set them all again. */
    fun rescheduleAll(context: Context) {
        ScheduledMessageStore.load(context).forEach { schedule(context, it) }
    }

    private fun pendingIntent(context: Context, id: String, flag: Int): PendingIntent? {
        val intent = Intent(context, ScheduledMessageReceiver::class.java).apply {
            action = ScheduledMessageReceiver.ACTION_SEND
            putExtra(ScheduledMessageReceiver.EXTRA_ID, id)
        }
        return PendingIntent.getBroadcast(context, id.hashCode(), intent, flag or PendingIntent.FLAG_IMMUTABLE)
    }
}

/** Sends a scheduled message when its time comes, even with the app closed. */
class ScheduledMessageReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_SEND) return
        val id = intent.getStringExtra(EXTRA_ID) ?: return
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.Main).launch {
            try {
                val message = ScheduledMessageStore.find(context, id) ?: return@launch
                val app = context.applicationContext as? CherishApplication ?: return@launch
                if (!app.authRepository.isUserLoggedIn()) return@launch
                app.chatRepository.sendMessage(text = message.text)
                ScheduledMessageStore.remove(context, id)
                // Give the message a moment to actually reach the server before the app sleeps again
                withTimeoutOrNull(8_000L) { FirebaseFirestore.getInstance().waitForPendingWrites().await() }
            } catch (e: Exception) {
                Log.w("ScheduledMessages", "Sending scheduled message failed", e)
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION_SEND = "com.cherish.notes.SEND_SCHEDULED_MESSAGE"
        const val EXTRA_ID = "scheduled_message_id"
    }
}

package com.example.notifications

import android.content.Context
import android.util.Log
import com.example.data.model.MessageStatus
import com.example.data.repository.AuthRepository
import com.google.android.gms.tasks.Task
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.functions.FirebaseFunctionsException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.TimeUnit

/**
 * Asks the server to push something to the partner's phone, so it arrives even with the app closed
 * there: a message, a call ringing or called off, a heartbeat. Each ask runs a server function, so
 * it asks only when the partner needs one: not while they're in Cherish (it shows there by itself),
 * not with their notifications off, and for messages one alert until they've been back in Cherish
 * (the server checks that too). Nothing waits on it: the message or call itself is already saved,
 * this only makes sure the other phone hears about it.
 */
object PartnerPush {
    private const val TAG = "PartnerPush"
    private const val FUNCTION = "pushPartner"
    private const val TIMEOUT_SECONDS = 20L
    private const val ATTEMPTS = 3
    private const val RETRY_DELAY_MS = 3_000L

    /** How long a message has to show in the partner's open chat (a double tick) before it's pushed after all. */
    private const val LIVE_DELIVERY_WAIT_MS = 5_000L
    private val SEEN_STATUSES = setOf(MessageStatus.DELIVERED.name, MessageStatus.READ.name)

    private const val PREFS = "partner_push"
    /** Saved messages whose push isn't asked for yet: "coupleId|messageId|savedAt", comma separated. */
    private const val KEY_PENDING = "pending_messages"
    /** The server only pushes a message this soon after it was written. */
    private const val PENDING_KEEP_MS = 24 * 60 * 60 * 1000L
    private const val MAX_PENDING = 50

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** When the partner's phone was last alerted about a message (server time, from the server's answer). */
    @Volatile private var partnerAlertedAt = 0L

    fun callInvite(coupleId: String, callId: String) = send("call_invite", coupleId, "callId" to callId)

    fun callCancel(coupleId: String, callId: String) = send("call_cancel", coupleId, "callId" to callId)

    fun heartbeat(coupleId: String, signalId: String) = send("heartbeat", coupleId, "signalId" to signalId)

    fun heartbeatFelt(coupleId: String, signalId: String) = send("heartbeat_felt", coupleId, "signalId" to signalId)

    /**
     * A message is being saved ([write]): once it is, the partner's phone hears about it if it needs
     * to (see shouldPushMessage). Remembered until then, so a message that's saved only after the
     * app closed is still pushed on its next start (resumePending).
     */
    fun messageSaving(context: Context, coupleId: String, messageId: String, write: Task<Void>) {
        val app = context.applicationContext
        rememberPending(app, coupleId, messageId)
        write.addOnSuccessListener { messageSaved(app, coupleId, messageId) }
    }

    /** At app start: messages from before whose push wasn't asked for, once they're on the server. */
    fun resumePending(context: Context) {
        val app = context.applicationContext
        val pending = pendingMessages(app)
        if (pending.isEmpty()) return
        try {
            FirebaseFirestore.getInstance().waitForPendingWrites()
                .addOnSuccessListener { pending.forEach { (coupleId, messageId) -> messageSaved(app, coupleId, messageId) } }
        } catch (e: Exception) {
            Log.w(TAG, "Couldn't wait for unsaved messages", e)
        }
    }

    private fun messageSaved(context: Context, coupleId: String, messageId: String) {
        scope.launch {
            if (shouldPushMessage(context, messageId)) {
                // The server couldn't be reached: asked again on the next start
                val answer = request("message", coupleId, "messageId" to messageId) ?: return@launch
                val notifiedAt = (answer["notifiedAt"] as? Number)?.toLong() ?: 0L
                if (notifiedAt > partnerAlertedAt) partnerAlertedAt = notifiedAt
            }
            forgetPending(context, messageId)
        }
    }

    /**
     * Whether the partner's phone needs a push for this message: not with their notifications off,
     * not while the last alert is unseen (they haven't been in Cherish since), and not when they're
     * in Cherish and the chat shows it within a few seconds (a double tick). When they only look
     * like they're in Cherish (their app was killed a moment ago), there's no double tick, so it
     * pushes after all.
     */
    private suspend fun shouldPushMessage(context: Context, messageId: String): Boolean {
        val app = context as? com.example.CherishApplication ?: return true
        val auth = app.authRepository
        if (auth.isPartnerNotificationsOff() || isAlertUnseen(auth)) return false
        if (auth.isPartnerInCherish()) {
            val shown = withTimeoutOrNull(LIVE_DELIVERY_WAIT_MS) {
                app.chatRepository.listenToMessages("").first { messages ->
                    messages.any { it.id == messageId && it.status in SEEN_STATUSES }
                }
            }
            if (shown != null) return false
        }
        return !isAlertUnseen(auth)
    }

    /** The partner's phone was alerted about a message and they haven't been in Cherish since. */
    private fun isAlertUnseen(auth: AuthRepository): Boolean {
        val alertedAt = partnerAlertedAt
        val seenAt = auth.partnerChatSeenAt()
        // A partner whose app doesn't say when it was in Cherish yet: the server decides
        return alertedAt > 0L && seenAt > 0L && alertedAt > seenAt
    }

    private fun send(kind: String, coupleId: String, item: Pair<String, String>) {
        scope.launch { request(kind, coupleId, item) }
    }

    /**
     * Asks the server for the push. Its answer (the status; for a message, when the alert that
     * counts went out), or null when the server couldn't be reached, after a few tries.
     */
    private suspend fun request(kind: String, coupleId: String, item: Pair<String, String>): Map<*, *>? {
        val couple = coupleId.trim()
        if (couple.isBlank() || couple == "couple_default" || item.second.isBlank()) return emptyMap<String, Any>()
        val data = hashMapOf("kind" to kind, "coupleId" to couple, item.first to item.second)
        repeat(ATTEMPTS) { attempt ->
            try {
                val auth = FirebaseAuth.getInstance()
                if (auth.currentUser == null) auth.signInAnonymously().await()
                val result = FirebaseFunctions.getInstance("us-central1")
                    .getHttpsCallable(FUNCTION)
                    .withTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
                    .call(data)
                    .await()
                return result.data as? Map<*, *> ?: emptyMap<String, Any>()
            } catch (e: FirebaseFunctionsException) {
                // Tried again only when the server couldn't be reached; any answer of its own is final
                val unreachable = e.code == FirebaseFunctionsException.Code.UNAVAILABLE ||
                    e.code == FirebaseFunctionsException.Code.DEADLINE_EXCEEDED ||
                    e.code == FirebaseFunctionsException.Code.INTERNAL
                Log.w(TAG, "Partner push ($kind) failed: ${e.code}")
                if (!unreachable) return emptyMap<String, Any>()
            } catch (e: Exception) {
                Log.w(TAG, "Partner push ($kind) failed", e)
            }
            if (attempt < ATTEMPTS - 1) delay(RETRY_DELAY_MS * (attempt + 1))
        }
        return null
    }

    // ---------- Messages whose push isn't asked for yet ----------

    private data class Pending(val coupleId: String, val messageId: String, val savedAt: Long)

    private fun readPending(context: Context): List<Pending> =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_PENDING, null).orEmpty()
            .split(",").mapNotNull { entry ->
                val parts = entry.split("|")
                val savedAt = parts.getOrNull(2)?.toLongOrNull()
                if (parts.size != 3 || parts[0].isBlank() || parts[1].isBlank() || savedAt == null) null
                else Pending(parts[0], parts[1], savedAt)
            }

    private fun writePending(context: Context, pending: List<Pending>) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY_PENDING, pending.joinToString(",") { "${it.coupleId}|${it.messageId}|${it.savedAt}" })
            .apply()
    }

    @Synchronized
    private fun rememberPending(context: Context, coupleId: String, messageId: String) {
        if (coupleId.isBlank() || messageId.isBlank()) return
        val now = System.currentTimeMillis()
        val kept = readPending(context).filter { it.messageId != messageId && now - it.savedAt < PENDING_KEEP_MS }
        writePending(context, (kept + Pending(coupleId, messageId, now)).takeLast(MAX_PENDING))
    }

    @Synchronized
    private fun forgetPending(context: Context, messageId: String) {
        val pending = readPending(context)
        if (pending.any { it.messageId == messageId }) writePending(context, pending.filter { it.messageId != messageId })
    }

    @Synchronized
    private fun pendingMessages(context: Context): List<Pair<String, String>> {
        val now = System.currentTimeMillis()
        return readPending(context).filter { now - it.savedAt < PENDING_KEEP_MS }.map { it.coupleId to it.messageId }
    }
}

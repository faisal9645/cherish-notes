package com.example.notifications

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.functions.FirebaseFunctionsException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.concurrent.TimeUnit

/**
 * Asks the server to push something to the partner's phone right away, so it arrives even with
 * the app closed there: a message, a call ringing or called off, a heartbeat. The server's database
 * triggers send the same pushes; this direct path (the pushPartner function) doesn't depend on
 * them, and the partner's phone still shows each thing once. Nothing waits on it: the message or
 * call itself is already saved, this only makes sure the other phone hears about it.
 */
object PartnerPush {
    private const val TAG = "PartnerPush"
    private const val FUNCTION = "pushPartner"
    private const val TIMEOUT_SECONDS = 20L
    private const val ATTEMPTS = 3
    private const val RETRY_DELAY_MS = 3_000L

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun message(coupleId: String, messageId: String) = send("message", coupleId, "messageId" to messageId)

    fun callInvite(coupleId: String, callId: String) = send("call_invite", coupleId, "callId" to callId)

    fun callCancel(coupleId: String, callId: String) = send("call_cancel", coupleId, "callId" to callId)

    fun heartbeat(coupleId: String, signalId: String) = send("heartbeat", coupleId, "signalId" to signalId)

    fun heartbeatFelt(coupleId: String, signalId: String) = send("heartbeat_felt", coupleId, "signalId" to signalId)

    private fun send(kind: String, coupleId: String, item: Pair<String, String>) {
        val couple = coupleId.trim()
        if (couple.isBlank() || couple == "couple_default" || item.second.isBlank()) return
        val data = hashMapOf("kind" to kind, "coupleId" to couple, item.first to item.second)
        scope.launch {
            repeat(ATTEMPTS) { attempt ->
                try {
                    val auth = FirebaseAuth.getInstance()
                    if (auth.currentUser == null) auth.signInAnonymously().await()
                    FirebaseFunctions.getInstance("us-central1")
                        .getHttpsCallable(FUNCTION)
                        .withTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
                        .call(data)
                        .await()
                    return@launch
                } catch (e: FirebaseFunctionsException) {
                    // Tried again only when the server couldn't be reached; any answer of its own is final
                    val unreachable = e.code == FirebaseFunctionsException.Code.UNAVAILABLE ||
                        e.code == FirebaseFunctionsException.Code.DEADLINE_EXCEEDED ||
                        e.code == FirebaseFunctionsException.Code.INTERNAL
                    Log.w(TAG, "Partner push ($kind) failed: ${e.code}")
                    if (!unreachable) return@launch
                } catch (e: Exception) {
                    Log.w(TAG, "Partner push ($kind) failed", e)
                }
                if (attempt < ATTEMPTS - 1) delay(RETRY_DELAY_MS * (attempt + 1))
            }
        }
    }
}

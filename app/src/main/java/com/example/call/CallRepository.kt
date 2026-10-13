package com.example.call

import android.content.Context
import android.util.Log
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.functions.FirebaseFunctionsException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.TimeUnit

/**
 * Talks to the call backend (Cloud Functions in functions/calls.js) and reads the call records it
 * keeps. Everything that changes a call goes through the backend; this phone only reads.
 */
class CallRepository(private val context: Context) {

    private val firestore: FirebaseFirestore? by lazy {
        if (FirebaseApp.getApps(context).isNotEmpty()) FirebaseFirestore.getInstance() else null
    }
    private val functions: FirebaseFunctions? by lazy {
        if (FirebaseApp.getApps(context).isNotEmpty()) FirebaseFunctions.getInstance() else null
    }
    private val auth: FirebaseAuth? by lazy {
        if (FirebaseApp.getApps(context).isNotEmpty()) FirebaseAuth.getInstance() else null
    }
    private val prefs by lazy { context.getSharedPreferences(PREFS, Context.MODE_PRIVATE) }

    /** How to join a call's audio channel (from the backend). */
    data class JoinInfo(
        val appId: String,
        val channel: String,
        val uid: Int,
        val token: String,
        val tokenExpiresAt: Long
    )

    /** The backend's answer to a call action. */
    data class CallResponse(
        val status: String,
        val callId: String?,
        val callerId: String?,
        val join: JoinInfo?
    )

    /** A call action the backend refused or couldn't do, sorted for what the screen says. */
    class CallActionException(val kind: Kind, message: String, cause: Throwable? = null) : Exception(message, cause) {
        enum class Kind { NOT_ALLOWED, NOT_PAIRED, NETWORK, SERVICE, OTHER }
    }

    /** Which call is going on for the couple right now (couples/{coupleId}/callControl/active). */
    data class ControlState(
        val callId: String,
        val state: String,
        val callerId: String,
        val calleeId: String,
        val ringExpiresAt: Long
    )

    suspend fun start(coupleId: String, userId: String, callId: String): CallResponse =
        callFunction("callStart", coupleId, mapOf("coupleId" to coupleId, "userId" to userId, "callId" to callId))

    suspend fun answer(coupleId: String, userId: String, callId: String): CallResponse =
        callFunction("callAnswer", coupleId, mapOf("coupleId" to coupleId, "userId" to userId, "callId" to callId))

    suspend fun end(coupleId: String, userId: String, callId: String, reason: String): CallResponse =
        callFunction("callEnd", coupleId, mapOf("coupleId" to coupleId, "userId" to userId, "callId" to callId, "reason" to reason))

    suspend fun joined(coupleId: String, userId: String, callId: String): CallResponse =
        callFunction("callJoined", coupleId, mapOf("coupleId" to coupleId, "userId" to userId, "callId" to callId))

    suspend fun renewToken(coupleId: String, userId: String, callId: String): CallResponse =
        callFunction("callRenewToken", coupleId, mapOf("coupleId" to coupleId, "userId" to userId, "callId" to callId))

    suspend fun finalizeClientRecording(coupleId: String, userId: String, callId: String): CallResponse =
        callFunction("callFinalizeClientRecording", coupleId, mapOf("coupleId" to coupleId, "userId" to userId, "callId" to callId))

    private suspend fun callFunction(name: String, coupleId: String, data: Map<String, Any>): CallResponse {
        val fns = functions ?: throw CallActionException(CallActionException.Kind.SERVICE, "Calls aren't available on this phone")
        if (!ensureMember(coupleId)) {
            throw CallActionException(CallActionException.Kind.NETWORK, "Couldn't reach the server")
        }
        var lastError: Exception? = null
        repeat(2) { attempt ->
            try {
                val result = fns.getHttpsCallable(name)
                    .withTimeout(CALL_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                    .call(data)
                    .await()
                return parseResponse(result.getData() as? Map<*, *>)
            } catch (e: CancellationException) {
                throw e
            } catch (e: FirebaseFunctionsException) {
                lastError = e
                // This sign-in isn't on the couple's member list yet (e.g. a new sign-in): add it once and retry
                if (e.code == FirebaseFunctionsException.Code.PERMISSION_DENIED && attempt == 0) {
                    forgetMember(coupleId)
                    if (ensureMember(coupleId)) return@repeat
                }
                throw toActionException(e)
            } catch (e: Exception) {
                lastError = e
                throw CallActionException(CallActionException.Kind.NETWORK, "Couldn't reach the server", e)
            }
        }
        throw CallActionException(CallActionException.Kind.OTHER, "The call couldn't be set up", lastError)
    }

    private fun toActionException(e: FirebaseFunctionsException): CallActionException {
        val msg = e.message?.takeIf { it.isNotBlank() } ?: "Call failed"
        Log.w(TAG, "Call function failed: ${e.code} ($msg)")
        val kind = when (e.code) {
            FirebaseFunctionsException.Code.PERMISSION_DENIED,
            FirebaseFunctionsException.Code.UNAUTHENTICATED -> CallActionException.Kind.NOT_ALLOWED
            FirebaseFunctionsException.Code.FAILED_PRECONDITION -> {
                if (msg.contains("partner", ignoreCase = true) || msg.contains("join", ignoreCase = true) || msg.contains("pair", ignoreCase = true)) {
                    CallActionException.Kind.NOT_PAIRED
                } else {
                    CallActionException.Kind.SERVICE
                }
            }
            FirebaseFunctionsException.Code.UNAVAILABLE,
            FirebaseFunctionsException.Code.DEADLINE_EXCEEDED -> CallActionException.Kind.NETWORK
            FirebaseFunctionsException.Code.NOT_FOUND,
            FirebaseFunctionsException.Code.INTERNAL,
            FirebaseFunctionsException.Code.UNIMPLEMENTED -> CallActionException.Kind.SERVICE
            else -> CallActionException.Kind.OTHER
        }
        return CallActionException(kind, msg, e)
    }

    private fun parseResponse(map: Map<*, *>?): CallResponse {
        val status = map?.get("status") as? String ?: ""
        val token = map?.get("token") as? String
        val appId = map?.get("appId") as? String
        val channel = map?.get("channel") as? String
        val uid = (map?.get("uid") as? Number)?.toInt()
        val join = if (!token.isNullOrBlank() && !appId.isNullOrBlank() && !channel.isNullOrBlank() && uid != null) {
            JoinInfo(
                appId = appId,
                channel = channel,
                uid = uid,
                token = token,
                tokenExpiresAt = (map["tokenExpiresAt"] as? Number)?.toLong() ?: 0L
            )
        } else null
        return CallResponse(
            status = status,
            callId = map?.get("callId") as? String,
            callerId = map?.get("callerId") as? String,
            join = join
        )
    }

    /** The call's record, live. Nothing is sent while it can't be read (e.g. not yet allowed). */
    fun observeCall(coupleId: String, callId: String): Flow<CallRecord> = callbackFlow {
        val fs = firestore
        if (fs == null) {
            close()
            return@callbackFlow
        }
        val registration = fs.collection("couples").document(coupleId)
            .collection("calls").document(callId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.w(TAG, "Call record can't be read", error)
                    return@addSnapshotListener
                }
                snapshot?.toCallRecord()?.let { trySend(it) }
            }
        awaitClose { registration.remove() }
    }

    /** The couple's current call, live: how this phone sees a call ring while the app is open. */
    fun observeControl(coupleId: String): Flow<ControlState?> = callbackFlow {
        val fs = firestore
        if (fs == null) {
            close()
            return@callbackFlow
        }
        val registration = fs.collection("couples").document(coupleId)
            .collection("callControl").document("active")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.w(TAG, "Call control can't be read", error)
                    return@addSnapshotListener
                }
                trySend(snapshot?.toControlState())
            }
        awaitClose { registration.remove() }
    }

    suspend fun getCall(coupleId: String, callId: String): CallRecord? {
        val fs = firestore ?: return null
        return try {
            fs.collection("couples").document(coupleId).collection("calls").document(callId)
                .get().await().toCallRecord()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Couldn't read call $callId", e)
            null
        }
    }

    /**
     * The rules let only the couple's own sign-ins at calls: this phone's sign-in is added to the
     * couple's member list once, as Love Synchronicity does.
     */
    suspend fun ensureMember(coupleId: String): Boolean {
        val firebaseAuth = auth ?: return false
        var uid = firebaseAuth.currentUser?.uid
        if (uid == null) {
            uid = try {
                firebaseAuth.signInAnonymously().await().user?.uid
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                null
            }
        }
        if (uid == null) return false
        val key = "member_${coupleId}|$uid"
        if (prefs.getBoolean(key, false)) return true
        val coupleRef = firestore?.collection("couples")?.document(coupleId) ?: return false
        return try {
            val done = withTimeoutOrNull(SERVER_WAIT_MS) {
                coupleRef.set(mapOf("memberUids" to FieldValue.arrayUnion(uid)), SetOptions.merge()).await()
                true
            } == true
            if (done) prefs.edit().putBoolean(key, true).apply()
            done
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Couldn't add this sign-in to the couple", e)
            false
        }
    }

    private fun forgetMember(coupleId: String) {
        val uid = auth?.currentUser?.uid ?: return
        prefs.edit().remove("member_${coupleId}|$uid").apply()
    }

    private fun DocumentSnapshot.toCallRecord(): CallRecord? {
        if (!exists()) return null
        val recording = get("recording") as? Map<*, *>
        return CallRecord(
            callId = getString("callId") ?: id,
            coupleId = getString("coupleId").orEmpty(),
            callerId = getString("callerId").orEmpty(),
            calleeId = getString("calleeId").orEmpty(),
            state = getString("state").orEmpty(),
            endReason = getString("endReason"),
            endedBy = getString("endedBy"),
            recordingStatus = RecordingStatus.fromKey(recording?.get("status") as? String),
            recordingFailure = recording?.get("failureCode") as? String,
            recordingPaths = (recording?.get("storagePaths") as? List<*>)?.filterIsInstance<String>().orEmpty(),
            recordingDurationSec = (recording?.get("durationSec") as? Number)?.toInt() ?: 0,
            durationSec = getLong("durationSec")?.toInt() ?: 0,
            endedAtMs = getTimestamp("endedAt")?.toDate()?.time ?: 0L
        )
    }

    private fun DocumentSnapshot.toControlState(): ControlState? {
        if (!exists()) return null
        val callId = getString("callId")?.ifBlank { null } ?: return null
        return ControlState(
            callId = callId,
            state = getString("state").orEmpty(),
            callerId = getString("callerId").orEmpty(),
            calleeId = getString("calleeId").orEmpty(),
            ringExpiresAt = getTimestamp("ringExpiresAt")?.toDate()?.time ?: 0L
        )
    }

    companion object {
        private const val TAG = "CallRepository"
        private const val PREFS = "cherish_calls"
        private const val CALL_TIMEOUT_SECONDS = 20L
        private const val SERVER_WAIT_MS = 6_000L

        /** A new call id: 20 letters and digits, like a Firestore id (also the call's audio channel). */
        fun newCallId(): String {
            val chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789"
            val random = java.security.SecureRandom()
            return buildString(20) { repeat(20) { append(chars[random.nextInt(chars.length)]) } }
        }
    }
}

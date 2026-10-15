package com.example.call

import android.content.Context
import android.util.Log
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Talks to the call backend and reads the call records it keeps.
 */
class CallRepository(private val context: Context) {

    private val firestore: FirebaseFirestore? by lazy {
        if (FirebaseApp.getApps(context).isNotEmpty()) FirebaseFirestore.getInstance() else null
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

    suspend fun start(coupleId: String, userId: String, callId: String): CallResponse {
        return directStart(coupleId, userId, callId)
    }

    suspend fun answer(coupleId: String, userId: String, callId: String): CallResponse {
        return directAnswer(coupleId, userId, callId)
    }

    suspend fun end(coupleId: String, userId: String, callId: String, reason: String): CallResponse {
        return directEnd(coupleId, userId, callId, reason)
    }

    suspend fun joined(coupleId: String, userId: String, callId: String): CallResponse {
        val fs = firestore ?: return CallResponse(status = "ok", callId = callId, callerId = null, join = null)
        try {
            fs.collection("couples").document(coupleId).collection("calls").document(callId).set(
                mapOf(
                    "state" to CallRecord.STATE_CONNECTED,
                    "connectedAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                ),
                SetOptions.merge()
            ).await()
            fs.collection("couples").document(coupleId).collection("callControl").document("active").set(
                mapOf(
                    "state" to CallRecord.STATE_CONNECTED,
                    "updatedAt" to FieldValue.serverTimestamp()
                ),
                SetOptions.merge()
            ).await()
        } catch (e: Exception) {
            Log.w(TAG, "joined writing calls failed: ${e.message}")
        }
        return CallResponse(status = CallRecord.STATE_CONNECTED, callId = callId, callerId = null, join = null)
    }

    /** A fresh token for this phone's own place in the call ([uid]: the caller's or the callee's). */
    fun renewToken(callId: String, uid: Int): CallResponse = directRenewToken(callId, uid)

    private suspend fun directStart(coupleId: String, userId: String, callId: String): CallResponse {
        val fs = firestore ?: throw CallActionException(CallActionException.Kind.SERVICE, "Firestore unavailable")
        val partnerId = resolvePartnerId(coupleId, userId)
        val now = System.currentTimeMillis()

        // Check if there is an active call already
        val controlRef = fs.collection("couples").document(coupleId).collection("callControl").document("active")
        val controlSnap = try { controlRef.get().await() } catch (_: Exception) { null }
        if (controlSnap != null && controlSnap.exists()) {
            val activeState = controlSnap.getString("state").orEmpty()
            val activeCallId = controlSnap.getString("callId").orEmpty()
            val activeCaller = controlSnap.getString("callerId").orEmpty()
            val activeCallee = controlSnap.getString("calleeId").orEmpty()
            val ringExpires = controlSnap.getTimestamp("ringExpiresAt")?.toDate()?.time
                ?: controlSnap.getLong("ringExpiresAtMs")
                ?: 0L

            // If partner called us at the exact same moment (within 7s collision): pick up theirs
            val activeCreatedAt = controlSnap.getTimestamp("createdAt")?.toDate()?.time
                ?: controlSnap.getTimestamp("updatedAt")?.toDate()?.time
                ?: 0L
            val isRecentCollision = (now - activeCreatedAt) in 0..7_000L
            if (activeState == CallRecord.STATE_RINGING && isSameId(activeCallee, userId) && ringExpires > now && isRecentCollision && activeCallId.isNotBlank()) {
                val targetCallDoc = try {
                    fs.collection("couples").document(coupleId).collection("calls").document(activeCallId).get().await()
                } catch (_: Exception) { null }
                val targetState = targetCallDoc?.getString("state")
                if (targetCallDoc != null && targetCallDoc.exists() && targetState == CallRecord.STATE_RINGING) {
                    return CallResponse(
                        status = "incoming",
                        callId = activeCallId,
                        callerId = activeCaller,
                        join = null
                    )
                }
            }
            // Only return BUSY if partner is actively on another ongoing CONNECTED call with someone else
            if (activeState == CallRecord.STATE_CONNECTED && activeCallId.isNotBlank() && activeCallId != callId && !isSameId(activeCaller, userId) && ringExpires > now) {
                return CallResponse(status = CallRecord.STATE_BUSY, callId = null, callerId = null, join = null)
            }
        }

        // Generate caller Agora token (uid = 1001)
        val token = AgoraTokenBuilder.buildToken(
            channelName = callId,
            uid = 1001
        )
        val join = JoinInfo(
            appId = AgoraTokenBuilder.DEFAULT_APP_ID,
            channel = callId,
            uid = 1001,
            token = token,
            tokenExpiresAt = now + 86400_000L
        )

        val ringExpiresAt = com.google.firebase.Timestamp(java.util.Date(now + 75_000L))
        val ringExpiresAtMs = now + 75_000L

        // Set call document
        val callDoc = mapOf(
            "callId" to callId,
            "coupleId" to coupleId,
            "channel" to callId,
            "appId" to AgoraTokenBuilder.DEFAULT_APP_ID,
            "callerId" to userId,
            "calleeId" to partnerId,
            "participants" to listOf(userId, partnerId),
            "state" to CallRecord.STATE_RINGING,
            "createdAt" to FieldValue.serverTimestamp(),
            "ringExpiresAt" to ringExpiresAt,
            "ringExpiresAtMs" to ringExpiresAtMs,
            "durationSec" to 0,
            "recording" to mapOf("status" to "waiting", "updatedAt" to FieldValue.serverTimestamp()),
            "updatedAt" to FieldValue.serverTimestamp()
        )
        try {
            fs.collection("couples").document(coupleId).collection("calls").document(callId)
                .set(callDoc, SetOptions.merge()).await()
        } catch (e: Exception) {
            Log.w(TAG, "directStart writing calls failed: ${e.message}")
        }

        // Set active control document (clean overwrite without merge so stale endedBy/endReason are wiped)
        val controlDoc = mapOf(
            "callId" to callId,
            "channel" to callId,
            "appId" to AgoraTokenBuilder.DEFAULT_APP_ID,
            "state" to CallRecord.STATE_RINGING,
            "callerId" to userId,
            "calleeId" to partnerId,
            "ringExpiresAt" to ringExpiresAt,
            "ringExpiresAtMs" to ringExpiresAtMs,
            "updatedAt" to FieldValue.serverTimestamp()
        )
        try {
            controlRef.set(controlDoc).await()
            // Rings the partner's phone even when the app is closed there
            com.example.notifications.PartnerPush.callInvite(coupleId, callId)
        } catch (e: Exception) {
            Log.w(TAG, "directStart writing callControl failed: ${e.message}")
        }

        return CallResponse(
            status = CallRecord.STATE_RINGING,
            callId = callId,
            callerId = userId,
            join = join
        )
    }

    private suspend fun directAnswer(coupleId: String, userId: String, callId: String): CallResponse {
        val fs = firestore ?: throw CallActionException(CallActionException.Kind.SERVICE, "Firestore unavailable")
        val now = System.currentTimeMillis()

        // Generate callee Agora token (uid = 1002)
        val token = AgoraTokenBuilder.buildToken(
            channelName = callId,
            uid = 1002
        )
        val join = JoinInfo(
            appId = AgoraTokenBuilder.DEFAULT_APP_ID,
            channel = callId,
            uid = 1002,
            token = token,
            tokenExpiresAt = now + 86400_000L
        )

        try {
            fs.collection("couples").document(coupleId).collection("calls").document(callId).set(
                mapOf(
                    "state" to CallRecord.STATE_CONNECTING,
                    "answeredAt" to FieldValue.serverTimestamp(),
                    "connectedAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                ),
                SetOptions.merge()
            ).await()
        } catch (e: Exception) {
            Log.w(TAG, "directAnswer writing calls failed: ${e.message}")
        }

        try {
            fs.collection("couples").document(coupleId).collection("callControl").document("active").set(
                mapOf(
                    "state" to CallRecord.STATE_CONNECTING,
                    "updatedAt" to FieldValue.serverTimestamp()
                ),
                SetOptions.merge()
            ).await()
        } catch (e: Exception) {
            Log.w(TAG, "directAnswer writing callControl failed: ${e.message}")
        }

        return CallResponse(
            status = CallRecord.STATE_CONNECTING,
            callId = callId,
            callerId = null,
            join = join
        )
    }

    private suspend fun directEnd(coupleId: String, userId: String, callId: String, reason: String): CallResponse {
        val fs = firestore ?: return CallResponse(status = CallRecord.STATE_ENDED, callId = callId, callerId = null, join = null)

        val endState = when (reason) {
            "declined" -> CallRecord.STATE_DECLINED
            "no_answer" -> CallRecord.STATE_MISSED
            "cancelled" -> CallRecord.STATE_CANCELLED
            else -> CallRecord.STATE_ENDED
        }

        try {
            fs.collection("couples").document(coupleId).collection("calls").document(callId).set(
                mapOf(
                    "state" to endState,
                    "endReason" to reason,
                    "endedBy" to userId,
                    "endedAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                ),
                SetOptions.merge()
            ).await()
        } catch (_: Exception) {}

        try {
            fs.collection("couples").document(coupleId).collection("callControl").document("active").set(
                mapOf(
                    "state" to endState,
                    "updatedAt" to FieldValue.serverTimestamp()
                ),
                SetOptions.merge()
            ).await()
        } catch (_: Exception) {}

        // Called off or rang out before an answer: the partner's phone stops ringing and shows it as missed
        if (endState == CallRecord.STATE_CANCELLED || endState == CallRecord.STATE_MISSED) {
            com.example.notifications.PartnerPush.callCancel(coupleId, callId)
        }

        return CallResponse(
            status = endState,
            callId = callId,
            callerId = null,
            join = null
        )
    }

    private fun directRenewToken(callId: String, uid: Int): CallResponse {
        val token = AgoraTokenBuilder.buildToken(channelName = callId, uid = uid)
        return CallResponse(
            status = "ok",
            callId = callId,
            callerId = null,
            join = JoinInfo(
                appId = AgoraTokenBuilder.DEFAULT_APP_ID,
                channel = callId,
                uid = uid,
                token = token,
                tokenExpiresAt = System.currentTimeMillis() + 86400_000L
            )
        )
    }

    private suspend fun resolvePartnerId(coupleId: String, userId: String): String {
        val app = context.applicationContext as? com.example.CherishApplication
        val partnerFromState = app?.authRepository?.partnerUserState?.value?.id
        if (!partnerFromState.isNullOrBlank()) return partnerFromState

        val authPartner = app?.authRepository?.currentUserState?.value?.partnerId
        if (!authPartner.isNullOrBlank()) return authPartner

        val coupleSnap = firestore?.collection("couples")?.document(coupleId)?.get()?.await()
        if (coupleSnap != null && coupleSnap.exists()) {
            val p1 = coupleSnap.getString("partner1Id").orEmpty()
            val p2 = coupleSnap.getString("partner2Id").orEmpty()
            val cleanUid = userId.removePrefix("user_").lowercase()
            if (p1.isNotBlank() && p2.isNotBlank()) {
                return if (p1.removePrefix("user_").lowercase() == cleanUid) p2 else p1
            }
            val members = (coupleSnap.get("members") as? List<*>)?.filterIsInstance<String>().orEmpty()
            val otherMember = members.firstOrNull { it.removePrefix("user_").lowercase() != cleanUid }
            if (!otherMember.isNullOrBlank()) return otherMember
        }
        return "partner"
    }

    private fun isSameId(a: String, b: String): Boolean =
        a.removePrefix("user_").trim().equals(b.removePrefix("user_").trim(), ignoreCase = true)


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
            recordingBy = (recording?.get("recorders") as? List<*>)?.filterIsInstance<String>().orEmpty(),
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
        val ringExpires = getTimestamp("ringExpiresAt")?.toDate()?.time
            ?: getLong("ringExpiresAtMs")
            ?: getLong("ringExpiresAt")
            ?: (System.currentTimeMillis() + 45_000L)
        return ControlState(
            callId = callId,
            state = getString("state").orEmpty(),
            callerId = getString("callerId").orEmpty(),
            calleeId = getString("calleeId").orEmpty(),
            ringExpiresAt = ringExpires
        )
    }

    companion object {
        private const val TAG = "CallRepository"
        private const val PREFS = "cherish_calls"
        private const val SERVER_WAIT_MS = 6_000L

        /** A new call id: 20 letters and digits, like a Firestore id (also the call's audio channel). */
        fun newCallId(): String {
            val chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789"
            val random = java.security.SecureRandom()
            return buildString(20) { repeat(20) { append(chars[random.nextInt(chars.length)]) } }
        }
    }
}

package com.example.data.repository

import android.content.Context
import android.util.Log
import com.example.data.model.SpaceRequest
import com.example.data.model.User
import com.example.notifications.NotificationHelper
import com.example.security.SecurityPreferences
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.TimeZone

class AuthRepository(private val context: Context) {
    private val securityPrefs = SecurityPreferences.getInstance(context)
    private val lovePrefs = context.getSharedPreferences("cherish_love", Context.MODE_PRIVATE)

    private val _birthdays = MutableStateFlow(readBirthdays(lovePrefs.getString("birthdays", null)))
    /** Each of us's birthday ("yyyy-MM-dd") by user id, kept on the couple record for both phones. */
    val birthdays: StateFlow<Map<String, String>> = _birthdays.asStateFlow()

    private fun readBirthdays(json: String?): Map<String, String> = try {
        val obj = org.json.JSONObject(json ?: "{}")
        obj.keys().asSequence().associateWith { obj.getString(it) }
    } catch (_: Exception) {
        emptyMap()
    }

    private fun cacheBirthdays(birthdays: Map<String, String>) {
        _birthdays.value = birthdays
        lovePrefs.edit().putString("birthdays", org.json.JSONObject(birthdays).toString()).apply()
    }

    private val _togetherSince = MutableStateFlow(lovePrefs.getString("together_since", null))
    /**
     * The day "days together" counts from ("yyyy-MM-dd"), set in settings and shared by both phones;
     * null until set (then the earliest anniversary in our dates is used).
     */
    val togetherSince: StateFlow<String?> = _togetherSince.asStateFlow()

    private fun cacheTogetherSince(date: String?) {
        _togetherSince.value = date
        lovePrefs.edit().putString("together_since", date).apply()
    }

    private val _syncPatterns = MutableStateFlow(
        lovePrefs.getString("sync_patterns", null)?.split('\n')?.filter { it.isNotBlank() }.orEmpty()
    )
    /** Our own Love Synchronicity numbers (beyond 11:11, 444...), shared by both phones. */
    val syncPatterns: StateFlow<List<String>> = _syncPatterns.asStateFlow()

    private fun cacheSyncPatterns(patterns: List<String>) {
        _syncPatterns.value = patterns
        lovePrefs.edit().putString("sync_patterns", patterns.joinToString("\n")).apply()
    }

    /** Adds one of our own numbers for both of us (already checked by SyncPatterns.normalize). */
    fun addSyncPattern(pattern: String) {
        if (pattern.isBlank() || pattern in _syncPatterns.value) return
        cacheSyncPatterns(_syncPatterns.value + pattern)
        getCoupleDocRef(_currentUserState.value?.coupleId)?.set(
            mapOf("synchronicityPatterns" to com.google.firebase.firestore.FieldValue.arrayUnion(pattern)),
            com.google.firebase.firestore.SetOptions.merge()
        )
    }

    /** Removes one of our own numbers (moments already recorded with it stay). */
    fun removeSyncPattern(pattern: String) {
        cacheSyncPatterns(_syncPatterns.value - pattern)
        getCoupleDocRef(_currentUserState.value?.coupleId)?.set(
            mapOf("synchronicityPatterns" to com.google.firebase.firestore.FieldValue.arrayRemove(pattern)),
            com.google.firebase.firestore.SetOptions.merge()
        )
    }

    private val _spaceRequest = MutableStateFlow<SpaceRequest?>(null)
    /** The latest personal-space request between us (a Check-After over 2 days, see [requestSpace]). */
    val spaceRequest: StateFlow<SpaceRequest?> = _spaceRequest.asStateFlow()

    /**
     * Asks the partner for a Check-After until [targetMillis] (more than 2 days). It starts only
     * when they accept. False when there's no couple to ask.
     */
    fun requestSpace(targetMillis: Long, note: String): Boolean {
        val myId = getCurrentUserId().ifBlank { null } ?: return false
        val ref = getCoupleDocRef(_currentUserState.value?.coupleId) ?: return false
        val now = System.currentTimeMillis()
        val request = SpaceRequest(
            id = java.util.UUID.randomUUID().toString(),
            fromId = myId,
            targetMillis = targetMillis,
            note = note.trim(),
            requestedAt = now
        )
        _spaceRequest.value = request
        ref.set(
            mapOf(
                "spaceRequest" to mapOf(
                    "id" to request.id,
                    "fromId" to request.fromId,
                    "targetMillis" to request.targetMillis,
                    "note" to request.note,
                    "requestedAt" to request.requestedAt,
                    "status" to SpaceRequest.STATUS_PENDING,
                    "respondedAt" to 0L
                )
            ),
            // The whole request is replaced (no answer left over from an earlier one)
            com.google.firebase.firestore.SetOptions.mergeFields("spaceRequest")
        )
        return true
    }

    /** My answer to the partner's request. Accepting starts their Check-After on both phones. */
    fun respondToSpaceRequest(accept: Boolean) {
        val request = _spaceRequest.value ?: return
        val myId = getCurrentUserId()
        if (!request.isPending || request.fromId == myId) return
        val ref = getCoupleDocRef(_currentUserState.value?.coupleId) ?: return
        val now = System.currentTimeMillis()
        val status = if (accept) SpaceRequest.STATUS_ACCEPTED else SpaceRequest.STATUS_DECLINED
        _spaceRequest.value = request.copy(status = status, respondedAt = now)
        ref.set(
            mapOf("spaceRequest" to mapOf("status" to status, "respondedAt" to now, "respondedBy" to myId)),
            com.google.firebase.firestore.SetOptions.merge()
        )
        if (accept) {
            // Their Check-After starts now, even while their phone is off (it also sets it itself
            // when it sees the answer)
            userDocument(request.fromId)?.set(
                mapOf(
                    "checkAfterTimeMillis" to request.targetMillis,
                    "checkAfterNote" to request.note,
                    "checkAfterCreatedAt" to now,
                    "checkAfterActive" to true
                ),
                com.google.firebase.firestore.SetOptions.merge()
            )
        }
    }

    /** Takes back my request while it's still waiting. */
    fun cancelSpaceRequest() {
        val request = _spaceRequest.value ?: return
        if (request.fromId != getCurrentUserId() || request.status != SpaceRequest.STATUS_PENDING) return
        val ref = getCoupleDocRef(_currentUserState.value?.coupleId) ?: return
        val now = System.currentTimeMillis()
        _spaceRequest.value = request.copy(status = SpaceRequest.STATUS_CANCELLED, respondedAt = now)
        ref.set(
            mapOf("spaceRequest" to mapOf("status" to SpaceRequest.STATUS_CANCELLED, "respondedAt" to now)),
            com.google.firebase.firestore.SetOptions.merge()
        )
    }

    /** The partner's answer to my request has been seen (its note isn't shown again). */
    fun isSpaceAnswerSeen(requestId: String): Boolean = lovePrefs.getBoolean("space_answer_seen_$requestId", false)

    fun markSpaceAnswerSeen(requestId: String) {
        lovePrefs.edit().putBoolean("space_answer_seen_$requestId", true).apply()
    }

    /**
     * My request was accepted: it becomes my Check-After, once (cancelling it later isn't undone
     * when the app starts again).
     */
    private fun applyAcceptedSpace(request: SpaceRequest?, myId: String) {
        if (request == null || request.status != SpaceRequest.STATUS_ACCEPTED || request.fromId != myId) return
        val key = "space_applied_${request.id}"
        if (lovePrefs.getBoolean(key, false)) return
        lovePrefs.edit().putBoolean(key, true).apply()
        if (request.targetMillis <= System.currentTimeMillis()) return
        val me = _currentUserState.value ?: return
        if (me.checkAfterActive && me.checkAfterTimeMillis == request.targetMillis) return
        setCheckAfter(request.targetMillis, request.note)
    }

    /** Sets the day we got together for both of us. */
    fun setTogetherSince(date: String) {
        cacheTogetherSince(date)
        getCoupleDocRef(_currentUserState.value?.coupleId)?.set(
            mapOf("togetherSince" to date),
            com.google.firebase.firestore.SetOptions.merge()
        )
    }

    /** Sets [userId]'s birthday ("yyyy-MM-dd"); either of us can set both. */
    fun setBirthday(userId: String, date: String) {
        if (userId.isBlank()) return
        cacheBirthdays(_birthdays.value + (userId to date))
        getCoupleDocRef(_currentUserState.value?.coupleId)?.set(
            mapOf("birthdays" to mapOf(userId to date)),
            com.google.firebase.firestore.SetOptions.merge()
        )
    }

    private val auth: FirebaseAuth? by lazy {
        try {
            if (FirebaseApp.getApps(context).isNotEmpty()) FirebaseAuth.getInstance() else null
        } catch (e: Exception) {
            Log.w("AuthRepository", "Firebase Auth not initialized", e)
            null
        }
    }

    private val firestore: FirebaseFirestore? by lazy {
        try {
            if (FirebaseApp.getApps(context).isNotEmpty()) FirebaseFirestore.getInstance() else null
        } catch (e: Exception) {
            Log.w("AuthRepository", "Firestore not initialized", e)
            null
        }
    }

    private val _currentUserState = MutableStateFlow<User?>(null)
    val currentUserState: StateFlow<User?> = _currentUserState.asStateFlow()

    private val _partnerUserState = MutableStateFlow<User?>(null)
    val partnerUserState: StateFlow<User?> = _partnerUserState.asStateFlow()

    fun getUserDocRef(uid: String?): com.google.firebase.firestore.DocumentReference? {
        val clean = uid?.trim()?.ifBlank { null } ?: return null
        return firestore?.collection("users")?.document(clean)
    }

    fun getCoupleDocRef(coupleId: String?): com.google.firebase.firestore.DocumentReference? {
        val clean = coupleId?.trim()?.ifBlank { null } ?: return null
        return firestore?.collection("couples")?.document(clean)
    }

    private var partnerListener: ListenerRegistration? = null
    private var currentUserListener: ListenerRegistration? = null
    private var coupleListener: ListenerRegistration? = null
    @Volatile private var isAppInForeground: Boolean = false
    private var isActivelyInChatTab: Boolean = false
    private var heartbeatJob: kotlinx.coroutines.Job? = null

    // Track which partner ID we're currently listening to, to prevent duplicate listeners
    private var currentListeningPartnerId: String? = null
    private var currentListeningUserId: String? = null
    private var currentListeningCoupleId: String? = null

    // Track partner discovery listeners to prevent duplicate creation
    private var partnerEmailQueryListener: ListenerRegistration? = null
    private var partnerCoupleQueryListener: ListenerRegistration? = null
    private var discoverySetupForCoupleId: String? = null

    init {
        loadLocalUserSession()
        val localUser = _currentUserState.value
        if (localUser != null && localUser.id.isNotBlank()) {
            // Every start: keep this phone's push address on the profile current, so message
            // notifications arrive even when the app is closed
            ensureFirebaseAuth {
                updateFcmToken(localUser.id)
                listenToCurrentUser(localUser.id)
                connectPartnerListenerOnce()
                if (com.example.CherishApplication.isAppInForeground || wasForegroundThisProcess) {
                    localUser.coupleId?.trim()?.ifBlank { null }?.let { listenToCoupleRoom(it, localUser.id) }
                }
            }
        }

        // When disguise state changes (e.g. entering Notes), update presence immediately
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main).launch {
            securityPrefs.isDisguiseActive.collect {
                updatePresence()
            }
        }
    }

    fun ensureFirebaseAuth(onReady: () -> Unit = {}) {
        val a = auth ?: run {
            onReady()
            return
        }
        if (a.currentUser != null) {
            onReady()
            return
        }
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
            try {
                a.signInAnonymously().await()
                Log.i("AuthRepository", "Firebase anonymous authentication established")
            } catch (e: Exception) {
                Log.w("AuthRepository", "Firebase anonymous authentication failed", e)
            }
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                onReady()
            }
        }
    }

    /**
     * Logged in = a couple login was made on this phone (saved session). The Firebase anonymous
     * sign-in alone doesn't count: it also happens before a login's details are checked.
     */
    fun isUserLoggedIn(): Boolean {
        if (_currentUserState.value != null) return true
        val prefs = context.getSharedPreferences("cherish_user_session", Context.MODE_PRIVATE)
        return !prefs.getString("uid", null).isNullOrBlank()
    }

    /**
     * The logged-in user's id, or "" when nobody is logged in. There's deliberately no stand-in id:
     * one used to send a logged-out phone's status into someone's real profile (and older
     * versions created a stray users/local_user_a that way).
     */
    fun getCurrentUserId(): String {
        val stateId = _currentUserState.value?.id?.trim().orEmpty()
        if (stateId.isNotBlank()) return stateId
        return context.getSharedPreferences("cherish_user_session", Context.MODE_PRIVATE)
            .getString("uid", "")?.trim().orEmpty()
    }

    /** This user's profile document; null when nobody is logged in, so nothing gets written. */
    private fun userDocument(uid: String) =
        if (uid.isBlank()) null else firestore?.collection("users")?.document(uid)

    fun setInChatTab(inChat: Boolean) {
        isActivelyInChatTab = inChat
        updatePresence()
    }

    /** The app is open on screen (the chat, any tab, or Notes). */
    fun isAppOpenOnScreen(): Boolean = com.example.CherishApplication.isAppInForeground || isAppInForeground

    fun isUserActivelyInChat(): Boolean {
        val isDisguised = securityPrefs.isDisguiseActive.value
        return isAppInForeground && isActivelyInChatTab && !isDisguised
    }

    fun onAppForegroundStateChanged(inForeground: Boolean) {
        isAppInForeground = inForeground
        if (inForeground) {
            wasForegroundThisProcess = true
            ensureCoupleRoomListener()
            startHeartbeat()
            kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                updateNotificationStatus()
            }
        } else {
            heartbeatJob?.cancel()
        }
        updatePresence()
    }

    fun ensureCoupleRoomListener() {
        val localUser = _currentUserState.value ?: return
        val coupleId = localUser.coupleId?.trim()?.ifBlank { null } ?: return
        listenToCoupleRoom(coupleId, localUser.id)
    }

    fun updatePresence() {
        val isDisguised = securityPrefs.isDisguiseActive.value
        // Show Online whenever actively in the Cherish app (foreground and not disguised as Notes)
        val shouldBeOnline = isAppInForeground && !isDisguised
        // Started in the background (a message push or an alarm) and never opened: leave the
        // profile alone, or the partner would see "last seen just now" for every message they send
        if (!shouldBeOnline && !wasForegroundThisProcess) return
        setOnline(shouldBeOnline)
    }

    @Volatile private var wasForegroundThisProcess = false

    fun listenToCoupleRoom(coupleId: String, currentUid: String) {
        val cleanCoupleId = coupleId.trim().ifBlank { null } ?: return
        val cleanUid = currentUid.trim().ifBlank { null } ?: return
        // Prevent duplicate listener for same couple
        if (currentListeningCoupleId == cleanCoupleId) return
        coupleListener?.remove()
        currentListeningCoupleId = cleanCoupleId
        val docRef = getCoupleDocRef(cleanCoupleId) ?: return
        coupleListener = docRef.addSnapshotListener { snapshot, e ->
                if (e != null || snapshot == null || !snapshot.exists()) return@addSnapshotListener
                val p1 = snapshot.getString("partner1Id")
                val p2 = snapshot.getString("partner2Id")
                val otherId = if (p1 != null && p1 != currentUid) p1 else if (p2 != null && p2 != currentUid) p2 else null
                if (!otherId.isNullOrBlank() && _currentUserState.value?.partnerId != otherId) {
                    val updated = _currentUserState.value?.copy(partnerId = otherId)
                    if (updated != null) {
                        _currentUserState.value = updated
                        saveLocalUserSession(updated)
                        listenToPartner(otherId)
                    }
                }

                // Both birthdays (ages and days lived on the Love & Us tab)
                val birthdays = (snapshot.get("birthdays") as? Map<*, *>).orEmpty()
                    .mapNotNull { (key, value) -> if (key is String && value is String) key to value else null }
                    .toMap()
                if (birthdays != _birthdays.value) cacheBirthdays(birthdays)
                val since = snapshot.getString("togetherSince")?.trim()?.ifBlank { null }
                if (since != _togetherSince.value) cacheTogetherSince(since)
                // A personal-space request (a Check-After over 2 days) and the partner's answer
                val request = (snapshot.get("spaceRequest") as? Map<*, *>)?.let { m ->
                    SpaceRequest(
                        id = m["id"] as? String ?: "",
                        fromId = m["fromId"] as? String ?: "",
                        targetMillis = (m["targetMillis"] as? Number)?.toLong() ?: 0L,
                        note = m["note"] as? String ?: "",
                        requestedAt = (m["requestedAt"] as? Number)?.toLong() ?: 0L,
                        status = m["status"] as? String ?: SpaceRequest.STATUS_PENDING,
                        respondedAt = (m["respondedAt"] as? Number)?.toLong() ?: 0L
                    )
                }?.takeIf { it.id.isNotBlank() }
                if (request != _spaceRequest.value) _spaceRequest.value = request
                applyAcceptedSpace(request, cleanUid)
                // Our own Love Synchronicity numbers
                val syncPatterns = (snapshot.get("synchronicityPatterns") as? List<*>).orEmpty()
                    .filterIsInstance<String>().filter { it.isNotBlank() }.distinct()
                if (syncPatterns != _syncPatterns.value) cacheSyncPatterns(syncPatterns)

                // A "thinking of you" heartbeat from the partner while this app is running (when
                // it isn't, the push brings it; whichever comes first plays, the other is ignored)
                (snapshot.get("signal") as? Map<*, *>)?.let { signal ->
                    val senderId = signal["senderId"] as? String ?: ""
                    val cleanSender = senderId.removePrefix("user_").lowercase()
                    if (signal["type"] == "heartbeat" && cleanSender.isNotBlank() && cleanSender != cleanUid) {
                        val sentAt = (signal["at"] as? com.google.firebase.Timestamp)?.toDate()?.time ?: System.currentTimeMillis()
                        val signalId = signal["id"] as? String ?: ""
                        val handled = com.example.notifications.ThinkingOfYou.onSignal(context, signalId, sentAt)
                        val isScreenOn = com.example.notifications.ThinkingOfYou.isScreenInteractive(context)
                        val isAppOpen = com.example.notifications.NotificationHelper.isAppOpenOnScreen(context)
                        if (handled && signalId.isNotBlank() && isScreenOn && isAppOpen) {
                            acknowledgeHeartbeatFelt(signalId)
                        } else if (handled && signalId.isNotBlank() && isScreenOn) {
                            com.example.notifications.ThinkingOfYou.setPendingHeartbeatFelt(context, signalId, sentAt)
                        }
                    }
                }

                // Partner received and felt our heartbeat trigger
                (snapshot.get("signalFelt") as? Map<*, *>)?.let { felt ->
                    val senderId = felt["senderId"] as? String ?: ""
                    val cleanSender = senderId.removePrefix("user_").lowercase()
                    if (felt["type"] == "heartbeat_felt" && cleanSender.isNotBlank() && cleanSender != cleanUid) {
                        val feltId = felt["id"] as? String ?: ""
                        val feltAt = (felt["at"] as? com.google.firebase.Timestamp)?.toDate()?.time ?: System.currentTimeMillis()
                        com.example.notifications.ThinkingOfYou.onPartnerFeltHeartbeat(context, feltId, feltAt)
                    }
                }
            }
    }

    /**
     * "Thinking of you": a heartbeat on the partner's phone (only if they're using it right now).
     * False when there's no couple to send it to.
     */
    fun sendThinkingOfYou(): Boolean {
        val uid = getCurrentUserId()
        if (uid.isBlank()) return false
        val coupleId = _currentUserState.value?.coupleId?.trim()?.ifBlank { null } ?: getSavedCoupleId()
        val coupleRef = getCoupleDocRef(coupleId) ?: return false
        val signalId = java.util.UUID.randomUUID().toString()
        // Only this phone is told when it's felt
        com.example.notifications.ThinkingOfYou.rememberSent(context, signalId)
        val signal = mapOf(
            "id" to signalId,
            "type" to "heartbeat",
            "senderId" to uid,
            "at" to com.google.firebase.firestore.FieldValue.serverTimestamp()
        )
        ensureFirebaseAuth {
            try {
                coupleRef.set(mapOf("signal" to signal), com.google.firebase.firestore.SetOptions.merge())
                    // Always dispatch push notification so partner receives heartbeat when locked, off, or app closed
                    // (at normal priority while they look like they're in Cherish: see PartnerPush.heartbeat)
                    .addOnSuccessListener {
                        com.example.notifications.PartnerPush.heartbeat(coupleRef.id, signalId, partnerInCherish = isPartnerInCherish())
                    }
                Log.d("AuthRepository", "Heartbeat signal dispatched for couple $coupleId")
            } catch (e: Exception) {
                Log.w("AuthRepository", "Failed to dispatch heartbeat signal: ${e.message}")
            }
        }
        return true
    }

    /**
     * Acknowledges that a received heartbeat trigger was felt on this phone.
     * This signals the sender's phone to play the reciprocal flutter haptic and display living response.
     */
    fun acknowledgeHeartbeatFelt(signalId: String) {
        if (signalId.isBlank()) return
        val uid = getCurrentUserId()
        if (uid.isBlank()) return
        val coupleId = _currentUserState.value?.coupleId?.trim()?.ifBlank { null } ?: getSavedCoupleId()
        val coupleRef = getCoupleDocRef(coupleId) ?: return
        val feltData = mapOf(
            "id" to signalId,
            "type" to "heartbeat_felt",
            "senderId" to uid,
            "at" to com.google.firebase.firestore.FieldValue.serverTimestamp()
        )
        ensureFirebaseAuth {
            try {
                coupleRef.set(mapOf("signalFelt" to feltData), com.google.firebase.firestore.SetOptions.merge())
                    // Pushed so the sender feels it when locked or closed; still in Cherish, their
                    // phone gets it by itself (each push runs a server function)
                    .addOnSuccessListener {
                        if (!isPartnerInCherish()) com.example.notifications.PartnerPush.heartbeatFelt(coupleRef.id, signalId)
                    }
                Log.d("AuthRepository", "Heartbeat felt acknowledgment dispatched for $signalId")
            } catch (e: Exception) {
                Log.w("AuthRepository", "Failed to dispatch heartbeat felt: ${e.message}")
            }
        }
    }

    suspend fun loginWithCoupleCredentials(
        username: String,
        partnerName: String,
        secretCode: String
    ): Result<User> {
        val cleanUser = username.trim().lowercase().filter { it.isLetterOrDigit() || it == '_' }.ifBlank { "partner_a" }
        val cleanPartner = partnerName.trim().lowercase().filter { it.isLetterOrDigit() || it == '_' }.ifBlank { "partner_b" }
        val cleanCode = secretCode.trim().lowercase().filter { it.isLetterOrDigit() || it == '_' }.ifBlank { "cherish_couple" }

        val coupleId = "couple_$cleanCode"
        val uid = "user_$cleanUser"
        val fallbackPartnerId = "user_$cleanPartner"

        val displayName = username.trim()
        val partnerDisplayName = partnerName.trim()
        val virtualEmail = "$cleanUser@cherish.app"
        val partnerVirtualEmail = "$cleanPartner@cherish.app"

        var finalPartnerId = fallbackPartnerId

        var authError: String? = null
        val a = auth
        if (a != null && a.currentUser == null) {
            try {
                a.signInAnonymously().await()
            } catch (authEx: Exception) {
                Log.w("AuthRepository", "Anonymous sign-in failed: ${authEx.message}")
                authError = authEx.message
            }
        }

        val fs = firestore ?: return Result.failure(Exception(CONNECTION_PROBLEM))
        if (a != null && a.currentUser == null) {
            try {
                a.signInAnonymously().await()
            } catch (authEx: Exception) {
                Log.w("AuthRepository", "Anonymous sign-in retry failed: ${authEx.message}")
            }
        }
        val coupleDocRef = fs.collection("couples").document(coupleId)
        val coupleSnap = try {
            coupleDocRef.get().await()
        } catch (e: Exception) {
            Log.w("AuthRepository", "Couple check failed: ${e.message}", e)
            val msg = e.message.orEmpty()
            if (authError != null && a?.currentUser == null) {
                return Result.failure(Exception("Authentication failed: $authError"))
            }
            if (msg.contains("PERMISSION_DENIED", ignoreCase = true)) {
                return Result.failure(Exception("Authentication error: Permission denied reading couple space."))
            }
            return Result.failure(Exception(CONNECTION_PROBLEM))
        }

        val currentAuthUid = auth?.currentUser?.uid

        if (!coupleSnap.exists()) {
            // Automatic registration for new secret codes: creates a new isolated couple space
            val newCoupleData = mutableMapOf<String, Any?>(
                "id" to coupleId,
                "secretCode" to cleanCode,
                "partner1Id" to uid,
                "partner1Name" to displayName,
                "partner2Name" to partnerDisplayName,
                "createdAt" to System.currentTimeMillis(),
                "updatedAt" to System.currentTimeMillis()
            )
            if (currentAuthUid != null) {
                newCoupleData["memberUids"] = listOf(currentAuthUid)
            }
            try {
                coupleDocRef.set(newCoupleData).await()
            } catch (e: Exception) {
                Log.w("AuthRepository", "Failed to create couple space: ${e.message}")
                return Result.failure(Exception("Failed to create couple space: ${e.localizedMessage}"))
            }
            finalPartnerId = fallbackPartnerId
        } else {
            // Existing couple space: link or reconnect
            val p1Id = coupleSnap.getString("partner1Id")
            val p2Id = coupleSnap.getString("partner2Id")
            val p1Name = coupleSnap.getString("partner1Name")
            val p2Name = coupleSnap.getString("partner2Name")

            val isP1 = p1Id == uid ||
                p1Id?.removePrefix("user_")?.equals(cleanUser, ignoreCase = true) == true ||
                p1Name?.trim()?.equals(cleanUser, ignoreCase = true) == true ||
                p1Name?.trim()?.equals(displayName, ignoreCase = true) == true

            val isP2 = p2Id == uid ||
                p2Id?.removePrefix("user_")?.equals(cleanUser, ignoreCase = true) == true ||
                p2Name?.trim()?.equals(cleanUser, ignoreCase = true) == true ||
                p2Name?.trim()?.equals(displayName, ignoreCase = true) == true

            when {
                // Partner 1 logging back in
                isP1 -> {
                    finalPartnerId = p2Id ?: fallbackPartnerId
                }
                // Partner 2 logging back in
                isP2 -> {
                    finalPartnerId = p1Id ?: fallbackPartnerId
                }
                // Partner 1 slot is open
                p1Id.isNullOrBlank() -> {
                    finalPartnerId = p2Id ?: fallbackPartnerId
                    val updates = mutableMapOf<String, Any>(
                        "partner1Id" to uid,
                        "partner1Name" to displayName,
                        "updatedAt" to System.currentTimeMillis()
                    )
                    if (currentAuthUid != null) {
                        updates["memberUids"] = com.google.firebase.firestore.FieldValue.arrayUnion(currentAuthUid)
                    }
                    try {
                        coupleDocRef.update(updates).await()
                    } catch (e: Exception) {
                        Log.w("AuthRepository", "Couple linking p1: ${e.message}")
                    }
                }
                // Partner 2 entering the secret code for the first time
                p2Id.isNullOrBlank() && !isP1 -> {
                    finalPartnerId = p1Id ?: fallbackPartnerId
                    val updates = mutableMapOf<String, Any>(
                        "partner2Id" to uid,
                        "partner2Name" to displayName,
                        "updatedAt" to System.currentTimeMillis()
                    )
                    if (currentAuthUid != null) {
                        updates["memberUids"] = com.google.firebase.firestore.FieldValue.arrayUnion(currentAuthUid)
                    }
                    try {
                        coupleDocRef.update(updates).await()
                        if (p1Id != null) {
                            fs.collection("users").document(p1Id).update(mapOf("partnerId" to uid))
                        }
                    } catch (e: Exception) {
                        Log.w("AuthRepository", "Couple linking: ${e.message}")
                    }
                }
                // Both slots already taken by other user IDs
                else -> {
                    return Result.failure(Exception("This Couple Secret Code is already in use by two registered partners."))
                }
            }

            // Ensure current auth UID is in memberUids so security rules allow synchronicity & couple documents
            if (currentAuthUid != null) {
                val memberUids = (coupleSnap.get("memberUids") as? List<*>)?.filterIsInstance<String>().orEmpty()
                if (currentAuthUid !in memberUids) {
                    try {
                        coupleDocRef.update("memberUids", com.google.firebase.firestore.FieldValue.arrayUnion(currentAuthUid)).await()
                    } catch (e: Exception) {
                        Log.w("AuthRepository", "Failed to union memberUids: ${e.message}")
                    }
                }
            }
        }

        val currentTz = TimeZone.getDefault().id
        val finalUser = User(
            id = uid,
            email = virtualEmail,
            displayName = displayName,
            partnerId = finalPartnerId,
            partnerEmail = partnerVirtualEmail,
            coupleId = coupleId,
            isOnline = true,
            lastSeen = System.currentTimeMillis(),
            createdAt = System.currentTimeMillis(),
            timeZone = currentTz
        )

        _currentUserState.value = finalUser
        saveLocalUserSession(finalUser)
        securityPrefs.setApprovedPartnerEmail(partnerVirtualEmail)
        securityPrefs.setCoupleSecretKey(coupleId)

        listenToCurrentUser(uid)
        connectPartnerListenerOnce()
        if (finalPartnerId.isNotBlank()) {
            listenToPartner(finalPartnerId)
        }
        listenToCoupleRoom(coupleId, uid)
        setOnline(true)
        startHeartbeat()

        if (fs != null) {
            kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                try {
                    val userDocRef = fs.collection("users").document(uid)
                    val updates = mutableMapOf<String, Any>(
                        "id" to uid,
                        "email" to virtualEmail,
                        "displayName" to displayName,
                        "partnerId" to finalPartnerId,
                        "partnerName" to partnerDisplayName,
                        "partnerEmail" to partnerVirtualEmail,
                        "coupleId" to coupleId,
                        "secretCode" to cleanCode,
                        "isOnline" to true,
                        "online" to true,
                        "lastSeen" to System.currentTimeMillis(),
                        "timeZone" to currentTz
                    )
                    userDocRef.set(updates, com.google.firebase.firestore.SetOptions.merge()).await()
                    updateFcmToken(uid)
                } catch (e: Exception) {
                    Log.w("AuthRepository", "User doc sync: ${e.message}")
                }
            }
        }

        return Result.success(finalUser)
    }

    fun computeCoupleId(userStr: String, partnerStr: String, keyStr: String): String {
        val cleanUser = userStr.trim().lowercase().substringBefore("@").replace(".", "_").replace("+", "_").replace(" ", "_").filter { it.isLetterOrDigit() || it == '_' }
        val cleanPartner = partnerStr.trim().lowercase().substringBefore("@").replace(".", "_").replace("+", "_").replace(" ", "_").filter { it.isLetterOrDigit() || it == '_' }
        val cleanKey = keyStr.trim().lowercase().replace(" ", "_").replace("-", "_")

        // 1. Custom couple key
        if (cleanKey.isNotBlank() && cleanKey != "cherish" && cleanKey != "cherish_forever_2026" && cleanKey != "cherish_love" && cleanKey != "couple_default") {
            return if (cleanKey.startsWith("couple_")) cleanKey else "couple_$cleanKey"
        }

        // 2. Deterministic sorted pairing
        if (cleanPartner.isNotBlank()) {
            val sorted = listOf(cleanUser, cleanPartner).sorted()
            return "couple_${sorted[0]}_${sorted[1]}"
        }

        return "couple_$cleanUser"
    }

    suspend fun loginWithUsernameAndPassword(
        username: String,
        pass: String,
        partnerUsername: String,
        coupleKey: String
    ): Result<User> {
        val cleanUser = username.trim().lowercase().replace("@", "_").replace(" ", "_").filter { it.isLetterOrDigit() || it == '_' }.ifBlank { "me" }
        val cleanPartner = partnerUsername.trim().lowercase().replace("@", "_").replace(" ", "_").filter { it.isLetterOrDigit() || it == '_' }
        val cleanKey = coupleKey.trim().lowercase().replace(" ", "_")

        val coupleId = computeCoupleId(cleanUser, cleanPartner, cleanKey)

        val uid = "user_$cleanUser"
        val partnerId = if (cleanPartner.isNotBlank()) {
            "user_$cleanPartner"
        } else null

        val displayName = cleanUser.replaceFirstChar { it.uppercase() }
        val virtualEmail = "$cleanUser@cherish.app"
        val partnerVirtualEmail = if (cleanPartner.isNotBlank()) {
            if (partnerUsername.contains("@")) partnerUsername.trim().lowercase() else "$cleanPartner@cherish.app"
        } else null

        val finalUser = User(
            id = uid,
            email = virtualEmail,
            displayName = displayName,
            partnerId = partnerId,
            partnerEmail = partnerVirtualEmail,
            coupleId = coupleId,
            isOnline = true,
            lastSeen = System.currentTimeMillis(),
            createdAt = System.currentTimeMillis()
        )

        // Save session locally immediately - zero lag, instant entry!
        _currentUserState.value = finalUser
        saveLocalUserSession(finalUser)
        if (!partnerVirtualEmail.isNullOrBlank()) {
            securityPrefs.setApprovedPartnerEmail(partnerVirtualEmail)
        }
        securityPrefs.setCoupleSecretKey(coupleId)

        // Connect real-time listeners right away
        listenToCurrentUser(uid)
        connectPartnerListenerOnce()
        setOnline(true)
        startHeartbeat()

        // Sync with Firestore in background without blocking login
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
            try {
                try {
                    auth?.signInAnonymously()?.await()
                } catch (_: Exception) {}

                val fs = firestore
                if (fs != null) {
                    val userDocRef = fs.collection("users").document(uid)
                    val updates = mutableMapOf<String, Any>(
                        "id" to uid,
                        "email" to virtualEmail,
                        "displayName" to displayName,
                        "partnerId" to (partnerId ?: ""),
                        "partnerEmail" to (partnerVirtualEmail ?: ""),
                        "coupleId" to coupleId,
                        "isOnline" to true,
                        "online" to true,
                        "lastSeen" to System.currentTimeMillis(),
                        "password" to pass
                    )
                    userDocRef.set(updates, com.google.firebase.firestore.SetOptions.merge())

                    // Also ensure couples document is created/linked for push notifications & heartbeat
                    val coupleDocRef = fs.collection("couples").document(coupleId)
                    val currentAuthUid = auth?.currentUser?.uid
                    val coupleUpdates = mutableMapOf<String, Any>(
                        "id" to coupleId,
                        "updatedAt" to System.currentTimeMillis()
                    )
                    if (partnerId.isNullOrBlank()) {
                        coupleUpdates["partner1Id"] = uid
                        coupleUpdates["partner1Name"] = displayName
                    } else {
                        coupleUpdates["partner1Id"] = uid
                        coupleUpdates["partner1Name"] = displayName
                        coupleUpdates["partner2Id"] = partnerId
                        coupleUpdates["partner2Name"] = cleanPartner.replaceFirstChar { it.uppercase() }
                    }
                    if (currentAuthUid != null) {
                        coupleUpdates["memberUids"] = com.google.firebase.firestore.FieldValue.arrayUnion(currentAuthUid)
                    }
                    coupleDocRef.set(coupleUpdates, com.google.firebase.firestore.SetOptions.merge())

                    updateFcmToken(uid)
                }
            } catch (e: Exception) {
                Log.w("AuthRepository", "Background Firestore sync: ${e.message}")
            }
        }

        return Result.success(finalUser)
    }

    suspend fun loginWithEmail(email: String, pass: String): Result<User> {
        return loginWithUsernameAndPassword(
            username = email.substringBefore("@"),
            pass = pass,
            partnerUsername = "",
            coupleKey = ""
        )
    }

    suspend fun registerWithEmail(email: String, pass: String, displayName: String): Result<User> {
        return loginWithUsernameAndPassword(
            username = email.substringBefore("@"),
            pass = pass,
            partnerUsername = "",
            coupleKey = ""
        )
    }

    fun loginOffline(username: String, partnerUsername: String, coupleKey: String, displayName: String = ""): User {
        val cleanUser = username.trim().lowercase().replace("@", "_").replace(" ", "_").filter { it.isLetterOrDigit() || it == '_' }.ifBlank { "me" }
        val cleanPartner = partnerUsername.trim().lowercase().replace("@", "_").replace(" ", "_").filter { it.isLetterOrDigit() || it == '_' }
        val cleanKey = coupleKey.trim().lowercase().replace(" ", "_")

        val coupleId = if (cleanKey.isNotBlank() && cleanKey != "cherish" && cleanKey != "cherish-forever-2026" && cleanKey != "cherish-love" && cleanKey != "couple_default") {
            if (cleanKey.startsWith("couple_")) cleanKey else "couple_$cleanKey"
        } else if (cleanPartner.isNotBlank()) {
            val sorted = listOf(cleanUser, cleanPartner).sorted()
            "couple_${sorted[0]}_${sorted[1]}"
        } else {
            "couple_$cleanUser"
        }

        val uid = "user_$cleanUser"
        val partnerId = if (cleanPartner.isNotBlank()) "user_$cleanPartner" else null
        val localUser = User(
            id = uid,
            email = "$cleanUser@cherish.app",
            displayName = displayName.ifBlank { cleanUser.replaceFirstChar { it.uppercase() } },
            partnerId = partnerId,
            partnerEmail = if (cleanPartner.isNotBlank()) "$cleanPartner@cherish.app" else null,
            coupleId = coupleId,
            isOnline = true
        )
        if (cleanPartner.isNotBlank()) {
            securityPrefs.setApprovedPartnerEmail("$cleanPartner@cherish.app")
        }
        securityPrefs.setCoupleSecretKey(coupleId)
        _currentUserState.value = localUser
        saveLocalUserSession(localUser)

        try {
            userDocument(uid)?.set(localUser)
        } catch (_: Exception) {}

        listenToCurrentUser(uid)
        connectPartnerListenerOnce()
        if (partnerId != null) {
            listenToPartner(partnerId)
        }
        setOnline(true)
        startHeartbeat()
        return localUser
    }

    suspend fun loginWithGoogleIdToken(
        idToken: String,
        partnerUsernameOrEmail: String = "",
        coupleKey: String = ""
    ): Result<User> {
        return try {
            val authInstance = auth ?: return Result.failure(Exception("Firebase Auth not initialized"))
            val credential = GoogleAuthProvider.getCredential(idToken, null)
            val authResult = authInstance.signInWithCredential(credential).await()
            val firebaseUser = authResult.user ?: return Result.failure(Exception("Google Sign-In returned empty user"))

            val email = firebaseUser.email.orEmpty().trim().lowercase()
            val displayName = firebaseUser.displayName.orEmpty().ifBlank { email.substringBefore("@").replaceFirstChar { it.uppercase() } }
            val photoUrl = firebaseUser.photoUrl?.toString()

            val cleanUser = email.substringBefore("@").replace(".", "_").replace("+", "_").filter { it.isLetterOrDigit() || it == '_' }.ifBlank { "google_user" }
            val cleanPartner = partnerUsernameOrEmail.trim().lowercase().substringBefore("@").replace(".", "_").replace("+", "_").filter { it.isLetterOrDigit() || it == '_' }
            val cleanKey = coupleKey.trim().lowercase().replace(" ", "_")

            val coupleId = computeCoupleId(cleanUser, cleanPartner, cleanKey)

            val uid = firebaseUser.uid
            val partnerId = if (cleanPartner.isNotBlank()) {
                "user_$cleanPartner"
            } else null

            val partnerVirtualEmail = if (cleanPartner.isNotBlank()) {
                if (partnerUsernameOrEmail.contains("@")) partnerUsernameOrEmail.trim().lowercase() else "$cleanPartner@gmail.com"
            } else null

            val finalUser = User(
                id = uid,
                email = email,
                displayName = displayName,
                photoUrl = photoUrl,
                partnerId = partnerId,
                partnerEmail = partnerVirtualEmail,
                coupleId = coupleId,
                isOnline = true,
                lastSeen = System.currentTimeMillis(),
                createdAt = System.currentTimeMillis()
            )

            _currentUserState.value = finalUser
            saveLocalUserSession(finalUser)
            if (!partnerVirtualEmail.isNullOrBlank()) {
                securityPrefs.setApprovedPartnerEmail(partnerVirtualEmail)
            }
            securityPrefs.setCoupleSecretKey(coupleId)

            listenToCurrentUser(uid)
            connectPartnerListenerOnce()
            setOnline(true)
            startHeartbeat()

            val fs = firestore
            if (fs != null) {
                val userDocRef = fs.collection("users").document(uid)
                val updates = mutableMapOf<String, Any>(
                    "id" to uid,
                    "email" to email,
                    "displayName" to displayName,
                    "partnerId" to (partnerId ?: ""),
                    "partnerEmail" to (partnerVirtualEmail ?: ""),
                    "coupleId" to coupleId,
                    "isOnline" to true,
                    "online" to true,
                    "lastSeen" to System.currentTimeMillis()
                )
                photoUrl?.let { updates["photoUrl"] = it }
                userDocRef.set(updates, com.google.firebase.firestore.SetOptions.merge()).await()
                updateFcmToken(uid)
            }

            Result.success(finalUser)
        } catch (e: Exception) {
            Log.e("AuthRepository", "Google sign in error", e)
            Result.failure(e)
        }
    }

    suspend fun sendPasswordReset(email: String): Result<Unit> {
        return try {
            auth?.sendPasswordResetEmail(email.trim())?.await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun updateProfile(
        displayName: String,
        statusMessage: String,
        photoUrl: String? = null,
        removePhoto: Boolean = false
    ): Result<Unit> {
        val uid = getCurrentUserId()
        val finalPhotoUrl = when {
            removePhoto -> null
            photoUrl != null -> photoUrl
            else -> _currentUserState.value?.photoUrl
        }
        val current = _currentUserState.value ?: User(id = uid)
        val updatedUser = current.copy(
            displayName = displayName,
            statusMessage = statusMessage,
            photoUrl = finalPhotoUrl
        )
        _currentUserState.value = updatedUser
        saveLocalUserSession(updatedUser)

        return try {
            val updates = mutableMapOf<String, Any?>()
            updates["displayName"] = displayName
            updates["statusMessage"] = statusMessage
            if (removePhoto) {
                updates["photoUrl"] = com.google.firebase.firestore.FieldValue.delete()
            } else if (photoUrl != null) {
                updates["photoUrl"] = photoUrl
            }
            userDocument(uid)?.set(updates as Map<String, Any>, com.google.firebase.firestore.SetOptions.merge())
            Result.success(Unit)
        } catch (e: Exception) {
            Log.w("AuthRepository", "Failed to update profile in Firestore, local update kept", e)
            Result.success(Unit)
        }
    }

    fun updateStatusMessage(statusMessage: String) {
        val current = _currentUserState.value ?: return
        val updated = current.copy(statusMessage = statusMessage)
        _currentUserState.value = updated
        saveLocalUserSession(updated)
        try {
            val uid = getCurrentUserId()
            if (uid.isNotBlank()) {
                userDocument(uid)?.update("statusMessage", statusMessage)
            }
        } catch (_: Exception) {}
    }

    suspend fun updateMood(mood: String): Result<Unit> {
        val uid = getCurrentUserId()
        val current = _currentUserState.value ?: User(id = uid)
        val moodAt = if (mood.isBlank()) 0L else System.currentTimeMillis()
        val updated = current.copy(mood = mood, moodAt = moodAt)
        _currentUserState.value = updated
        saveLocalUserSession(updated)
        return try {
            userDocument(uid)?.set(
                mapOf("mood" to mood, "moodAt" to moodAt),
                com.google.firebase.firestore.SetOptions.merge()
            )
            Result.success(Unit)
        } catch (e: Exception) {
            Log.w("AuthRepository", "Failed to update mood in Firestore", e)
            Result.success(Unit)
        }
    }

    suspend fun updateBatteryStatus(level: Int, isCharging: Boolean): Result<Unit> {
        val uid = getCurrentUserId()
        val current = _currentUserState.value ?: User(id = uid)
        // Guard against duplicate state mutations and unnecessary writes to save battery & CPU
        if (current.batteryLevel == level && current.isCharging == isCharging) {
            return Result.success(Unit)
        }
        val updated = current.copy(batteryLevel = level, isCharging = isCharging)
        _currentUserState.value = updated
        return try {
            userDocument(uid)?.set(
                mapOf("batteryLevel" to level, "isCharging" to isCharging),
                com.google.firebase.firestore.SetOptions.merge()
            )
            Result.success(Unit)
        } catch (e: Exception) {
            Result.success(Unit)
        }
    }

    /**
     * Computes the current phone's alert status:
     * - "CLOSED" if notifications are turned off in-app or blocked in Android system settings
     * - "SAFE" if notifications are on, but content is disguised as Notes for privacy
     * - "OPEN" if notifications are normal, active, and previewing
     */
    fun getComputedNotificationStatus(): String {
        return try {
            val sysEnabled = androidx.core.app.NotificationManagerCompat.from(context).areNotificationsEnabled()
            val appEnabled = securityPrefs.isNotificationsEnabled()
            val hideContent = securityPrefs.isHideNotificationContent()
            when {
                !sysEnabled || !appEnabled -> "CLOSED"
                hideContent -> "SAFE"
                else -> "OPEN"
            }
        } catch (_: Exception) {
            "OPEN"
        }
    }

    suspend fun updateNotificationStatus(): Result<Unit> {
        val status = getComputedNotificationStatus()
        val sysPerm = try {
            androidx.core.app.NotificationManagerCompat.from(context).areNotificationsEnabled()
        } catch (_: Exception) { true }
        return updateNotificationStatus(status, sysPerm)
    }

    suspend fun updateNotificationStatus(status: String, permissionEnabled: Boolean? = null): Result<Unit> {
        val uid = getCurrentUserId()
        if (uid.isBlank()) return Result.success(Unit)
        val perm = permissionEnabled ?: try {
            androidx.core.app.NotificationManagerCompat.from(context).areNotificationsEnabled()
        } catch (_: Exception) { true }
        val current = _currentUserState.value ?: User(id = uid)
        if (current.notificationStatus == status && current.notificationPermission == perm) {
            return Result.success(Unit)
        }
        val updated = current.copy(notificationStatus = status).apply { notificationPermission = perm }
        _currentUserState.value = updated
        saveLocalUserSession(updated)
        return try {
            userDocument(uid)?.set(
                mapOf("notificationStatus" to status, "notificationPermission" to perm),
                com.google.firebase.firestore.SetOptions.merge()
            )
            Result.success(Unit)
        } catch (e: Exception) {
            Result.success(Unit)
        }
    }

    private var heartbeatTouchJob: kotlinx.coroutines.Job? = null

    fun setHeartbeatTouch(active: Boolean) {
        heartbeatTouchJob?.cancel()
        heartbeatTouchJob = null

        val uid = getCurrentUserId()
        val timestamp = if (active) System.currentTimeMillis() else 0L
        val current = _currentUserState.value ?: User(id = uid)
        _currentUserState.value = current.copy(heartbeatTouchingTimestamp = timestamp)
        try {
            userDocument(uid)?.set(
                mapOf("heartbeatTouchingTimestamp" to timestamp),
                com.google.firebase.firestore.SetOptions.merge()
            )
        } catch (_: Exception) {}

        if (active) {
            heartbeatTouchJob = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                while (isActive) {
                    kotlinx.coroutines.delay(1200L)
                    val liveTs = System.currentTimeMillis()
                    try {
                        userDocument(uid)?.set(
                            mapOf("heartbeatTouchingTimestamp" to liveTs),
                            com.google.firebase.firestore.SetOptions.merge()
                        )
                    } catch (_: Exception) {}
                }
            }
        }
    }

    fun syncHeartbeatStreak() {
        val uid = getCurrentUserId()
        val current = _currentUserState.value ?: return
        val now = System.currentTimeMillis()
        
        // Logical day starts at 6 AM
        val DAY_ROLLOVER_OFFSET_MS = 6 * 60 * 60 * 1000L
        val currentLogicalDay = (now - DAY_ROLLOVER_OFFSET_MS) / (24 * 60 * 60 * 1000L)
        val lastSyncLogicalDay = (current.lastHeartbeatSync - DAY_ROLLOVER_OFFSET_MS) / (24 * 60 * 60 * 1000L)
        
        if (currentLogicalDay > lastSyncLogicalDay) {
            val newStreak = if (currentLogicalDay - lastSyncLogicalDay == 1L) current.heartbeatStreak + 1 else 1
            
            _currentUserState.value = current.copy(heartbeatStreak = newStreak, lastHeartbeatSync = now)
            try {
                userDocument(uid)?.set(
                    mapOf("heartbeatStreak" to newStreak, "lastHeartbeatSync" to now),
                    com.google.firebase.firestore.SetOptions.merge()
                )
            } catch (_: Exception) {}
        }
    }

    suspend fun pairWithPartner(partnerUsername: String, coupleSecretKey: String): Result<User> {
        val uid = getCurrentUserId()
        if (uid.isBlank()) return Result.failure(Exception("Log in first"))
        val cleanPartner = partnerUsername.trim().lowercase().replace("@", "_").replace(" ", "_").filter { it.isLetterOrDigit() || it == '_' }
        val partnerId = "user_$cleanPartner"
        val partnerEmail = "$cleanPartner@cherish.app"
        val cleanKey = coupleSecretKey.trim().lowercase().replace(" ", "_")

        val currentUser = _currentUserState.value
        val cleanUser = currentUser?.displayName?.trim()?.lowercase()?.filter { it.isLetterOrDigit() || it == '_' }
            ?: uid.removePrefix("user_")

        val coupleId = if (cleanKey.isNotBlank() && cleanKey != "cherish" && cleanKey != "cherish-forever-2026" && cleanKey != "cherish-love" && cleanKey != "couple_default") {
            if (cleanKey.startsWith("couple_")) cleanKey else "couple_$cleanKey"
        } else if (cleanPartner.isNotBlank()) {
            val sorted = listOf(cleanUser, cleanPartner).sorted()
            "couple_${sorted[0]}_${sorted[1]}"
        } else {
            "couple_$cleanKey"
        }

        securityPrefs.setApprovedPartnerEmail(partnerEmail)
        securityPrefs.setCoupleSecretKey(coupleId)

        val updatedUser = (_currentUserState.value ?: User(id = uid)).copy(
            partnerId = partnerId,
            partnerEmail = partnerEmail,
            coupleId = coupleId
        )
        _currentUserState.value = updatedUser
        saveLocalUserSession(updatedUser)

        return try {
            val fs = firestore
            if (fs != null) {
                val updates = mapOf<String, Any>(
                    "partnerEmail" to partnerEmail,
                    "partnerId" to partnerId,
                    "coupleId" to coupleId
                )
                fs.collection("users").document(uid).update(updates)
                listenToPartner(partnerId)
            }
            Result.success(updatedUser)
        } catch (e: Exception) {
            Log.w("AuthRepository", "Pair with partner Firestore update warning", e)
            Result.success(updatedUser)
        }
    }

    @Volatile private var lastFirestoreOnlineWritten: Boolean? = null

    private fun startHeartbeat() {
        heartbeatJob?.cancel()
        heartbeatJob = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
            while (isAppInForeground) {
                val isDisguised = securityPrefs.isDisguiseActive.value
                val shouldBeOnline = isAppInForeground && !isDisguised
                if (shouldBeOnline) {
                    _currentUserState.value = _currentUserState.value?.copy(
                        isOnline = true,
                        lastSeen = System.currentTimeMillis()
                    )
                    if (firestore == null) {
                        val partner = _partnerUserState.value
                        if (partner != null && partner.isOnline) {
                            _partnerUserState.value = partner.copy(lastSeen = System.currentTimeMillis())
                        }
                    }
                    // Every beat: the partner's phone shows this one online only while beats keep
                    // coming (User.ONLINE_WINDOW_MS), so an app that's killed or loses its network
                    // shows offline within a minute, last seen at its last beat
                    val uid = getCurrentUserId()
                    if (uid.isNotBlank()) {
                        val currentNotif = getComputedNotificationStatus()
                        val currentSysPerm = try {
                            androidx.core.app.NotificationManagerCompat.from(context).areNotificationsEnabled()
                        } catch (_: Exception) { true }
                        val updates = mapOf<String, Any>(
                            "isOnline" to true,
                            "online" to true,
                            "lastSeen" to System.currentTimeMillis(),
                            "timeZone" to TimeZone.getDefault().id,
                            "notificationStatus" to currentNotif,
                            "notificationPermission" to currentSysPerm,
                            // In Cherish: the partner's next message may alert this phone once it's left
                            "chatSeenAt" to com.google.firebase.firestore.FieldValue.serverTimestamp()
                        )
                        if (_currentUserState.value?.notificationStatus != currentNotif || _currentUserState.value?.notificationPermission != currentSysPerm) {
                            _currentUserState.value = _currentUserState.value?.copy(notificationStatus = currentNotif)?.apply { notificationPermission = currentSysPerm }
                        }
                        lastFirestoreOnlineWritten = true
                        try {
                            userDocument(uid)?.set(
                                updates,
                                com.google.firebase.firestore.SetOptions.merge()
                            )
                        } catch (_: Exception) {}
                    }
                } else if (lastFirestoreOnlineWritten != false) {
                    // Disguised as Notes: offline is written once, not on every beat
                    setOnline(false)
                }
                kotlinx.coroutines.delay(User.PRESENCE_HEARTBEAT_MS)
            }
        }
    }

    fun setOnline(online: Boolean) {
        val uid = getCurrentUserId()
        if (uid.isBlank()) return
        val currentTz = TimeZone.getDefault().id
        val currentNotif = getComputedNotificationStatus()
        val currentSysPerm = try {
            androidx.core.app.NotificationManagerCompat.from(context).areNotificationsEnabled()
        } catch (_: Exception) { true }
        _currentUserState.value = _currentUserState.value?.copy(
            isOnline = online,
            lastSeen = System.currentTimeMillis(),
            timeZone = currentTz,
            notificationStatus = currentNotif
        )?.apply {
            notificationPermission = currentSysPerm
        }
        if (lastFirestoreOnlineWritten == online) return
        val wasInCherish = lastFirestoreOnlineWritten == true
        lastFirestoreOnlineWritten = online
        try {
            val updates = mutableMapOf<String, Any>(
                "isOnline" to online,
                "online" to online,
                "lastSeen" to System.currentTimeMillis(),
                "timeZone" to currentTz,
                "notificationStatus" to currentNotif,
                "notificationPermission" to currentSysPerm
            )
            // Entering Cherish, or leaving it (opening only Notes doesn't count): the partner's next
            // message may alert this phone again (one alert until then, see the pushPartner function)
            if (online || wasInCherish) {
                updates["chatSeenAt"] = com.google.firebase.firestore.FieldValue.serverTimestamp()
            }
            if (!online) {
                updates["typingInChat"] = false
                updates["recordingAudioInChat"] = false
                typingWritten = uid to false
            }
            userDocument(uid)?.set(
                updates,
                com.google.firebase.firestore.SetOptions.merge()
            )
        } catch (e: Exception) {
            Log.w("AuthRepository", "Failed to update online status", e)
        }
    }

    // Last "typing" value written per user, so every keystroke doesn't cost a Firestore write
    @Volatile private var typingWritten: Pair<String, Boolean>? = null

    fun setTyping(typing: Boolean) {
        val uid = getCurrentUserId()
        if (typingWritten == uid to typing) return
        typingWritten = uid to typing
        try {
            userDocument(uid)?.set(
                mapOf("typingInChat" to typing),
                com.google.firebase.firestore.SetOptions.merge()
            )
        } catch (e: Exception) {
            // ignore
        }
    }

    // Last typing pace written per user (only changes are written)
    @Volatile private var typingPaceWritten: Pair<String, Int>? = null

    /** How fast I'm typing: disabled writing to Firestore to minimize billing costs. */
    fun setTypingPace(pace: Int) {
        // No-op: only isTyping boolean is written to Firestore to save costs
    }

    fun setRecordingAudio(recording: Boolean) {
        val uid = getCurrentUserId()
        try {
            userDocument(uid)?.set(
                mapOf("recordingAudioInChat" to recording),
                com.google.firebase.firestore.SetOptions.merge()
            )
        } catch (e: Exception) {
            // ignore
        }
    }

    fun setCheckAfter(targetTimeMillis: Long, note: String = "") {
        val uid = getCurrentUserId()
        val now = System.currentTimeMillis()
        val updates = mapOf(
            "checkAfterTimeMillis" to targetTimeMillis,
            "checkAfterNote" to note,
            "checkAfterCreatedAt" to now,
            "checkAfterActive" to true
        )
        val current = _currentUserState.value ?: User(id = uid)
        val updated = current.copy(
            checkAfterTimeMillis = targetTimeMillis,
            checkAfterNote = note,
            checkAfterCreatedAt = now,
            checkAfterActive = true
        )
        _currentUserState.value = updated
        saveLocalUserSession(updated)
        try {
            userDocument(uid)?.update(updates)
        } catch (e: Exception) {
            Log.w("AuthRepository", "Failed to update check-after in Firestore", e)
        }
    }

    fun cancelCheckAfter() {
        val uid = getCurrentUserId()
        val updates = mapOf(
            "checkAfterActive" to false
        )
        val current = _currentUserState.value
        if (current != null) {
            val updated = current.copy(checkAfterActive = false)
            _currentUserState.value = updated
            saveLocalUserSession(updated)
        }
        try {
            userDocument(uid)?.update(updates)
        } catch (e: Exception) {
            Log.w("AuthRepository", "Failed to cancel check-after in Firestore", e)
        }
    }

    fun extendCheckAfter(additionalMillis: Long) {
        val currentTarget = _currentUserState.value?.checkAfterTimeMillis ?: System.currentTimeMillis()
        val base = if (currentTarget > System.currentTimeMillis()) currentTarget else System.currentTimeMillis()
        val newTarget = base + additionalMillis
        setCheckAfter(newTarget, _currentUserState.value?.checkAfterNote ?: "")
    }

    fun parseUserSnapshot(snapshot: com.google.firebase.firestore.DocumentSnapshot): User? {
        if (!snapshot.exists()) return null
        try {
            val user = snapshot.toObject(User::class.java)
            if (user != null) {
                val docPerm = (snapshot.get("notificationPermission") as? Boolean)
                    ?: try { snapshot.getBoolean("notificationPermission") } catch (_: Throwable) { null }
                if (docPerm != null) user.notificationPermission = docPerm
                return user
            }
        } catch (e: Throwable) {
            Log.w("AuthRepository", "Failed to deserialize User via reflection: ${e.message}")
        }
        return try {
            val id = snapshot.getString("id") ?: snapshot.id
            val email = snapshot.getString("email").orEmpty()
            val displayName = snapshot.getString("displayName").orEmpty()
            val photoUrl = snapshot.getString("photoUrl")
            val statusMessage = snapshot.getString("statusMessage") ?: "Loving every moment with you ✨"
            val partnerId = snapshot.getString("partnerId")
            val partnerEmail = snapshot.getString("partnerEmail")
            val coupleId = snapshot.getString("coupleId")
            val isOnline = (snapshot.get("isOnline") as? Boolean)
                ?: (snapshot.get("online") as? Boolean)
                ?: try { snapshot.getBoolean("isOnline") ?: snapshot.getBoolean("online") } catch (_: Exception) { null }
                ?: false
            val lastSeen = (snapshot.get("lastSeen") as? Number)?.toLong()
                ?: try { snapshot.getTimestamp("lastSeen")?.toDate()?.time } catch (_: Exception) { null }
                ?: try { snapshot.getDate("lastSeen")?.time } catch (_: Exception) { null }
                ?: System.currentTimeMillis()
            val typingInChat = (snapshot.get("typingInChat") as? Boolean) ?: false
            val typingPace = (snapshot.get("typingPace") as? Number)?.toInt() ?: 0
            val fcmToken = snapshot.getString("fcmToken")
            val createdAt = (snapshot.get("createdAt") as? Number)?.toLong() ?: System.currentTimeMillis()
            val checkAfterTimeMillis = (snapshot.get("checkAfterTimeMillis") as? Number)?.toLong()
            val checkAfterNote = snapshot.getString("checkAfterNote")
            val checkAfterCreatedAt = (snapshot.get("checkAfterCreatedAt") as? Number)?.toLong()
            val checkAfterActive = (snapshot.get("checkAfterActive") as? Boolean) ?: false
            val recordingAudioInChat = (snapshot.get("recordingAudioInChat") as? Boolean) ?: false
            val activityStatus = snapshot.getString("activityStatus")
            val activityStatusNote = snapshot.getString("activityStatusNote")
            val isActivityHidden = (snapshot.get("isActivityHidden") as? Boolean) ?: false
            val mood = snapshot.getString("mood")
            val moodAt = (snapshot.get("moodAt") as? Number)?.toLong() ?: 0L
            val batteryLevel = (snapshot.get("batteryLevel") as? Number)?.toInt()
            val isCharging = (snapshot.get("isCharging") as? Boolean) ?: false
            val heartbeatTouchingTimestamp = (snapshot.get("heartbeatTouchingTimestamp") as? Number)?.toLong() ?: 0L
            val heartbeatStreak = (snapshot.get("heartbeatStreak") as? Number)?.toInt() ?: 0
            val lastHeartbeatSync = (snapshot.get("lastHeartbeatSync") as? Number)?.toLong() ?: 0L
            val timeZone = snapshot.getString("timeZone")
            val notificationStatus = snapshot.getString("notificationStatus")
            val notifPerm = (snapshot.get("notificationPermission") as? Boolean)
                ?: try { snapshot.getBoolean("notificationPermission") } catch (_: Exception) { null }
                ?: true

            User(
                id = id,
                email = email,
                displayName = displayName,
                photoUrl = photoUrl,
                statusMessage = statusMessage,
                partnerId = partnerId,
                partnerEmail = partnerEmail,
                coupleId = coupleId,
                isOnline = isOnline,
                lastSeen = lastSeen,
                typingInChat = typingInChat,
                typingPace = typingPace,
                fcmToken = fcmToken,
                createdAt = createdAt,
                checkAfterTimeMillis = checkAfterTimeMillis,
                checkAfterNote = checkAfterNote,
                checkAfterCreatedAt = checkAfterCreatedAt,
                checkAfterActive = checkAfterActive,
                recordingAudioInChat = recordingAudioInChat,
                activityStatus = activityStatus,
                activityStatusNote = activityStatusNote,
                isActivityHidden = isActivityHidden,
                mood = mood,
                moodAt = moodAt,
                batteryLevel = batteryLevel,
                isCharging = isCharging,
                heartbeatTouchingTimestamp = heartbeatTouchingTimestamp,
                heartbeatStreak = heartbeatStreak,
                lastHeartbeatSync = lastHeartbeatSync,
                timeZone = timeZone,
                notificationStatus = notificationStatus
            ).apply {
                notificationPermission = notifPerm
            }
        } catch (e: Throwable) {
            Log.e("AuthRepository", "Fallback manual user parsing error", e)
            null
        }
    }

    private fun listenToCurrentUser(uid: String) {
        val cleanUid = uid.trim().ifBlank { null } ?: return
        // Prevent duplicate listener for same user
        if (currentListeningUserId == cleanUid) return
        currentUserListener?.remove()
        currentListeningUserId = cleanUid
        val docRef = getUserDocRef(cleanUid) ?: return
        currentUserListener = docRef.addSnapshotListener { snapshot, error ->
            try {
                if (error != null) {
                    Log.w("AuthRepository", "Listen to current user failed", error)
                    return@addSnapshotListener
                }
                if (snapshot != null && snapshot.exists()) {
                    var user = parseUserSnapshot(snapshot)
                    if (user != null) {
                        val boolOnline = (snapshot.get("isOnline") as? Boolean)
                            ?: (snapshot.get("online") as? Boolean)
                            ?: try { snapshot.getBoolean("isOnline") ?: snapshot.getBoolean("online") } catch (_: Exception) { null }
                            ?: user.isOnline
                        val docLastSeen = (snapshot.get("lastSeen") as? Number)?.toLong()
                            ?: try { snapshot.getTimestamp("lastSeen")?.toDate()?.time } catch (_: Exception) { null }
                            ?: try { snapshot.getDate("lastSeen")?.time } catch (_: Exception) { null }
                            ?: user.lastSeen
                        if (user.isOnline != boolOnline || user.lastSeen != docLastSeen) {
                            user = user.copy(
                                isOnline = boolOnline,
                                lastSeen = docLastSeen
                            )
                        }
                        val docNotifStatus = snapshot.getString("notificationStatus")
                        val docNotifPerm = (snapshot.get("notificationPermission") as? Boolean)
                            ?: try { snapshot.getBoolean("notificationPermission") } catch (_: Exception) { null }
                        if (!docNotifStatus.isNullOrBlank() && user.notificationStatus != docNotifStatus) {
                            user = user.copy(notificationStatus = docNotifStatus)
                        }
                        if (docNotifPerm != null && user.notificationPermission != docNotifPerm) {
                            user.notificationPermission = docNotifPerm
                        }
                    }
                    _currentUserState.value = user
                    user?.let { saveLocalUserSession(it) }
                    connectPartnerListenerOnce()
                    // Only connect partner listener if partner ID changed
                    val newPartnerId = user?.partnerId
                    if (!newPartnerId.isNullOrBlank() && newPartnerId != currentListeningPartnerId) {
                        listenToPartner(newPartnerId)
                    }
                }
            } catch (e: Throwable) {
                Log.e("AuthRepository", "Unhandled error inside currentUserListener", e)
            }
        }
    }

    /**
     * Connect partner listener exactly once per unique partner/couple configuration.
     * Prevents duplicate listeners during recomposition or tab switching.
     */
    fun connectPartnerListenerOnce() {
        val user = _currentUserState.value ?: return
        val partnerId = user.partnerId
        if (!partnerId.isNullOrBlank() && partnerId != currentListeningPartnerId) {
            listenToPartner(partnerId)
        }
        // Set up discovery listeners only once per couple
        val coupleId = user.coupleId
        if (coupleId != null && coupleId != discoverySetupForCoupleId) {
            discoverySetupForCoupleId = coupleId
            findAndListenToPartner(user.partnerEmail, coupleId, user.id)
        }
    }

    fun listenToPartner(partnerId: String) {
        val cleanPartnerId = partnerId.trim().ifBlank { null } ?: return
        // Skip if already listening to this exact partner
        if (currentListeningPartnerId == cleanPartnerId) return
        partnerListener?.remove()
        currentListeningPartnerId = cleanPartnerId
        val docRef = getUserDocRef(cleanPartnerId) ?: return
        partnerListener = docRef.addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.w("AuthRepository", "Listen to partner failed", error)
                    return@addSnapshotListener
                }
                if (snapshot != null && snapshot.exists()) {
                    publishPartnerSnapshot(snapshot)
                }
            }
    }

    // The partner's lastSeen as last read from the server, and when a live heartbeat last arrived here
    private var partnerServerLastSeen: Pair<String, Long>? = null
    private var partnerPresenceReceivedAt = 0L

    /** When the partner was last in Cherish (server time, their profile's chatSeenAt); 0 when not known. */
    @Volatile private var partnerChatSeenAt = 0L

    fun partnerChatSeenAt(): Long = partnerChatSeenAt

    /** The partner is in Cherish right now: their presence beats keep coming. */
    fun isPartnerInCherish(): Boolean = _partnerUserState.value?.isEffectivelyOnline() == true

    /** The partner switched notifications off (in Cherish or for it in Android): nothing is pushed to them. */
    fun isPartnerNotificationsOff(): Boolean =
        _partnerUserState.value?.notificationStatus?.trim()?.uppercase()?.let { it in NOTIFICATIONS_OFF } == true

    /**
     * Publishes the partner's document from any of the partner listeners. A heartbeat that arrives
     * while listening (a small step on from the previous one) is timed on this phone's own clock, so
     * online/offline doesn't depend on the two phones' clocks agreeing. A first copy, or a big jump
     * after being disconnected, falls back to comparing clocks until the next heartbeat.
     */
    private fun publishPartnerSnapshot(doc: com.google.firebase.firestore.DocumentSnapshot) {
        try {
            var partner = parseUserSnapshot(doc)
            if (partner != null) {
                val boolOnline = (doc.get("isOnline") as? Boolean)
                    ?: (doc.get("online") as? Boolean)
                    ?: try { doc.getBoolean("isOnline") ?: doc.getBoolean("online") } catch (_: Exception) { null }
                    ?: partner.isOnline
                val docLastSeen = (doc.get("lastSeen") as? Number)?.toLong()
                    ?: try { doc.getTimestamp("lastSeen")?.toDate()?.time } catch (_: Exception) { null }
                    ?: try { doc.getDate("lastSeen")?.time } catch (_: Exception) { null }
                    ?: partner.lastSeen
                if (partner.isOnline != boolOnline || partner.lastSeen != docLastSeen) {
                    partner = partner.copy(
                        isOnline = boolOnline,
                        lastSeen = docLastSeen
                    )
                }
                val docNotifStatus = doc.getString("notificationStatus")
                val docNotifPerm = (doc.get("notificationPermission") as? Boolean)
                    ?: try { doc.getBoolean("notificationPermission") } catch (_: Exception) { null }
                if (!docNotifStatus.isNullOrBlank() && partner.notificationStatus != docNotifStatus) {
                    partner = partner.copy(notificationStatus = docNotifStatus)
                }
                if (docNotifPerm != null && partner.notificationPermission != docNotifPerm) {
                    partner.notificationPermission = docNotifPerm
                }
                val now = System.currentTimeMillis()
                if (!doc.metadata.isFromCache) {
                    val previous = partnerServerLastSeen
                    val step = if (previous != null && previous.first == doc.id) docLastSeen - previous.second else -1L
                    val clockDiff = kotlin.math.abs(now - docLastSeen)

                    if (!boolOnline) {
                        partnerPresenceReceivedAt = 0L
                    } else if (step in 1L..LIVE_HEARTBEAT_MAX_STEP_MS) {
                        partnerPresenceReceivedAt = now
                    } else if (step == 0L && partnerPresenceReceivedAt > 0L && (now - partnerPresenceReceivedAt) <= User.ONLINE_WINDOW_MS) {
                        partnerPresenceReceivedAt = now
                    } else if (step != 0L && clockDiff <= 300_000L) {
                        // A first copy, or a new lastSeen after a while (just came online), within 5
                        // minutes of this phone's clock (covers new logins & cross-country clock drift).
                        // Not some other field changing on a profile whose beats stopped: that's no
                        // sign of life
                        partnerPresenceReceivedAt = now
                    } else {
                        partnerPresenceReceivedAt = 0L
                    }
                    partnerServerLastSeen = doc.id to docLastSeen
                }
                if (partnerServerLastSeen?.first == doc.id) partner.presenceReceivedAt = partnerPresenceReceivedAt
                if (partner.heartbeatTouchingTimestamp > 0L) {
                    partner.heartbeatTouchReceivedAt = now
                } else {
                    partner.heartbeatTouchReceivedAt = 0L
                }
                // When they were last in Cherish (one message alert until they're back, see PartnerPush)
                (doc.get("chatSeenAt") as? com.google.firebase.Timestamp)?.toDate()?.time?.let { seenAt ->
                    if (seenAt > partnerChatSeenAt) partnerChatSeenAt = seenAt
                }
            }
            _partnerUserState.value = partner
            handlePartnerCheckAfterReminder(partner)
        } catch (e: Throwable) {
            Log.e("AuthRepository", "Unhandled error inside publishPartnerSnapshot", e)
        }
    }

    private fun findAndListenToPartner(partnerEmail: String?, coupleId: String?, uid: String) {
        val fs = firestore ?: return

        // 1. Prioritize discovery via Couple ID (e.g. couple_secret123)
        if (!coupleId.isNullOrBlank() && coupleId != "couple_default") {
            partnerCoupleQueryListener?.remove()
            partnerCoupleQueryListener = fs.collection("users")
                .whereEqualTo("coupleId", coupleId)
                .limit(10)
                .addSnapshotListener { snapshots, error ->
                    if (error != null) {
                        Log.w("AuthRepository", "Partner query by coupleId failed", error)
                        return@addSnapshotListener
                    }
                    val doc = snapshots?.documents?.firstOrNull { it.id != uid }
                    if (doc != null) {
                        publishPartnerSnapshot(doc)
                        if (_currentUserState.value?.partnerId != doc.id) {
                            val updated = _currentUserState.value?.copy(partnerId = doc.id)
                            if (updated != null) {
                                _currentUserState.value = updated
                                saveLocalUserSession(updated)
                            }
                            try {
                                fs.collection("users").document(uid).update("partnerId", doc.id)
                            } catch (_: Exception) {}
                        }
                        listenToPartner(doc.id)
                    }
                }
        }

        // 2. Also discover via Partner Email
        val cleanEmail = partnerEmail?.trim()?.lowercase()
        if (!cleanEmail.isNullOrBlank()) {
            val partnerUid = if (cleanEmail.contains("@")) "user_${cleanEmail.substringBefore("@")}" else "user_$cleanEmail"
            if (_partnerUserState.value == null && currentListeningPartnerId != partnerUid) {
                listenToPartner(partnerUid)
            }

            partnerEmailQueryListener?.remove()
            partnerEmailQueryListener = fs.collection("users")
                .whereEqualTo("email", cleanEmail)
                .limit(1)
                .addSnapshotListener { snapshots, error ->
                    if (error != null) {
                        Log.w("AuthRepository", "Partner query by email failed", error)
                        return@addSnapshotListener
                    }
                    val doc = snapshots?.documents?.firstOrNull()
                    if (doc != null && doc.id != uid) {
                        publishPartnerSnapshot(doc)
                        if (_currentUserState.value?.partnerId != doc.id) {
                            val updated = _currentUserState.value?.copy(partnerId = doc.id)
                            if (updated != null) {
                                _currentUserState.value = updated
                                saveLocalUserSession(updated)
                            }
                            try {
                                fs.collection("users").document(uid).update("partnerId", doc.id)
                            } catch (_: Exception) {}
                        }
                        listenToPartner(doc.id)
                    }
                }
        }
    }

    private fun handlePartnerCheckAfterReminder(partner: User?) {
        val target = partner?.checkAfterTimeMillis
        val active = partner?.checkAfterActive == true
        if (active && target != null && target > System.currentTimeMillis()) {
            com.example.notifications.CheckAfterReminderScheduler.scheduleReminder(
                context,
                target,
                partner.displayName.ifBlank { "My Partner" }
            )
        } else {
            com.example.notifications.CheckAfterReminderScheduler.cancelReminder(context)
        }
    }


    private suspend fun fetchUserFromFirestore(uid: String): User? {
        return try {
            val doc = userDocument(uid)?.get()?.await()
            if (doc != null && doc.exists()) parseUserSnapshot(doc) else null
        } catch (e: Exception) {
            null
        }
    }

    /** Stores this phone's push token on the logged-in profile (when the token changes). */
    fun saveFcmToken(token: String) {
        val uid = getCurrentUserId()
        if (uid.isBlank() || token.isBlank()) return
        storeFcmToken(uid, token)
    }

    fun updateFcmToken(uid: String) {
        if (uid.isBlank()) return
        try {
            val messaging = FirebaseMessaging.getInstance()
            messaging.isAutoInitEnabled = true
            messaging.token
                .addOnSuccessListener { token -> if (!token.isNullOrBlank()) storeFcmToken(uid, token) }
                .addOnFailureListener { e ->
                    Log.w("AuthRepository", "FCM token registration failed gracefully: ${e.message}")
                }
        } catch (e: Throwable) {
            Log.w("AuthRepository", "FCM token registration skipped", e)
        }
    }

    /**
     * Saves [token] (this phone's push address) everywhere the server looks for [uid]'s: the
     * profile, the same id with or without "user_", and this partner's slot on the couple record.
     */
    private fun storeFcmToken(uid: String, token: String) {
        context.getSharedPreferences("cherish_user_session", Context.MODE_PRIVATE)
            .edit()
            .putString("fcmToken", token)
            .apply()
        ensureFirebaseAuth {
            try {
                val update = mapOf("fcmToken" to token)
                val merge = com.google.firebase.firestore.SetOptions.merge()
                userDocument(uid)?.set(update, merge)
                val altUid = if (uid.startsWith("user_")) uid.removePrefix("user_") else "user_$uid"
                userDocument(altUid)?.set(update, merge)
                val targetCoupleId = _currentUserState.value?.coupleId?.trim()?.ifBlank { null } ?: getSavedCoupleId().trim().ifBlank { null }
                getCoupleDocRef(targetCoupleId)?.get()?.addOnSuccessListener { snap ->
                    if (snap == null || !snap.exists()) return@addOnSuccessListener
                    // Only once the record says which partner this phone is: a guess could put this
                    // address in the partner's slot, and their pushes would come here
                    val field = coupleTokenField(snap, uid) ?: return@addOnSuccessListener
                    if (snap.getString(field) != token) snap.reference.set(mapOf(field to token), merge)
                }
                Log.i("AuthRepository", "FCM token saved for user $uid")
            } catch (e: Exception) {
                Log.w("AuthRepository", "Failed saving FCM token: ${e.message}")
            }
        }
    }

    /** [uid]'s push-address field on the couple record, when the record says which partner they are. */
    private fun coupleTokenField(couple: com.google.firebase.firestore.DocumentSnapshot, uid: String): String? {
        val me = uid.removePrefix("user_").trim().lowercase()
        if (me.isBlank()) return null
        fun isMe(id: String?, name: String?): Boolean =
            id?.removePrefix("user_")?.trim()?.lowercase() == me ||
                name?.trim()?.lowercase()?.replace(Regex("[^a-z0-9_]"), "") == me
        return when {
            isMe(couple.getString("partner1Id"), couple.getString("partner1Name")) -> "partner1FcmToken"
            isMe(couple.getString("partner2Id"), couple.getString("partner2Name")) -> "partner2FcmToken"
            else -> null
        }
    }

    fun logout() {
        val oldUid = getCurrentUserId()
        heartbeatJob?.cancel()
        isAppInForeground = false
        setOnline(false)
        currentUserListener?.remove()
        partnerListener?.remove()
        partnerEmailQueryListener?.remove()
        partnerCoupleQueryListener?.remove()
        coupleListener?.remove()
        // Reset tracking state
        currentListeningUserId = null
        currentListeningPartnerId = null
        currentListeningCoupleId = null
        discoverySetupForCoupleId = null
        partnerServerLastSeen = null
        partnerPresenceReceivedAt = 0L
        partnerChatSeenAt = 0L

        // Clear FCM push token from Firestore and delete locally so next login starts clean
        if (oldUid.isNotBlank()) {
            try {
                userDocument(oldUid)?.update("fcmToken", com.google.firebase.firestore.FieldValue.delete())
            } catch (_: Exception) {}
        }
        try {
            FirebaseMessaging.getInstance().deleteToken()
        } catch (_: Exception) {}

        try {
            auth?.signOut()
        } catch (e: Exception) {
            // ignore
        }
        _currentUserState.value = null
        _partnerUserState.value = null
        clearLocalUserSession()

        // Dismiss any existing notifications and clear launcher badge
        NotificationHelper.clearNotifications(context)
        NotificationHelper.updateLauncherBadge(context, 0)
    }

    private fun saveLocalUserSession(user: User) {
        context.getSharedPreferences("cherish_user_session", Context.MODE_PRIVATE)
            .edit()
            .putString("uid", user.id)
            .putString("email", user.email)
            .putString("displayName", user.displayName)
            .putString("photoUrl", user.photoUrl ?: "")
            .putString("statusMessage", user.statusMessage)
            .putString("partnerId", user.partnerId ?: "")
            .putString("partnerEmail", user.partnerEmail ?: "")
            .putString("coupleId", user.coupleId ?: "")
            .putLong("checkAfterTimeMillis", user.checkAfterTimeMillis ?: 0L)
            .putString("checkAfterNote", user.checkAfterNote ?: "")
            .putBoolean("checkAfterActive", user.checkAfterActive)
            .putString("timeZone", user.timeZone ?: TimeZone.getDefault().id)
            .putString("notificationStatus", user.notificationStatus ?: "OPEN")
            .putBoolean("notificationPermission", user.notificationPermission ?: true)
            .apply()
    }

    private fun loadLocalUserSession() {
        val prefs = context.getSharedPreferences("cherish_user_session", Context.MODE_PRIVATE)
        val rawUid = prefs.getString("uid", null)?.trim()?.ifBlank { null }
        if (rawUid != null) {
            val rawCoupleId = prefs.getString("coupleId", null)?.trim()?.ifBlank { null }
                ?: securityPrefs.getCoupleSecretKey().trim().ifBlank { null }
            val uid = rawUid
            val targetTime = prefs.getLong("checkAfterTimeMillis", 0L)
            val note = prefs.getString("checkAfterNote", "") ?: ""
            val active = prefs.getBoolean("checkAfterActive", false)
            val photo = prefs.getString("photoUrl", null)?.trim()?.ifBlank { null }
            val status = prefs.getString("statusMessage", "Loving every moment with you ✨")?.trim()?.ifBlank { "Loving every moment with you ✨" } ?: "Loving every moment with you ✨"
            val partnerId = prefs.getString("partnerId", null)?.trim()?.ifBlank { null }
            val partnerEmail = prefs.getString("partnerEmail", null)?.trim()?.ifBlank { null }
            val coupleId = rawCoupleId
            val timeZone = prefs.getString("timeZone", null)?.trim()?.ifBlank { null } ?: TimeZone.getDefault().id
            val notificationStatus = prefs.getString("notificationStatus", "OPEN") ?: "OPEN"
            val notificationPermission = prefs.getBoolean("notificationPermission", true)
            val defaultName = uid.removePrefix("user_").replaceFirstChar { it.uppercase() }
            val user = User(
                id = uid,
                email = prefs.getString("email", "") ?: "",
                displayName = prefs.getString("displayName", defaultName)?.ifBlank { defaultName } ?: defaultName,
                photoUrl = photo,
                statusMessage = status,
                partnerId = partnerId,
                partnerEmail = partnerEmail,
                coupleId = coupleId,
                checkAfterTimeMillis = if (targetTime > 0L) targetTime else null,
                checkAfterNote = note.ifBlank { null },
                checkAfterActive = active,
                timeZone = timeZone,
                notificationStatus = notificationStatus
            ).apply {
                this.notificationPermission = notificationPermission
            }
            _currentUserState.value = user
            if (!partnerId.isNullOrBlank()) {
                val partnerName = partnerId.removePrefix("user_").replaceFirstChar { it.uppercase() }
                if (_partnerUserState.value == null) {
                    _partnerUserState.value = User(
                        id = partnerId,
                        email = partnerEmail ?: "",
                        displayName = partnerName,
                        partnerId = user.id,
                        partnerEmail = user.email,
                        coupleId = coupleId,
                        isOnline = false,
                        statusMessage = "Always with you 💕",
                        notificationStatus = "OPEN"
                    )
                }
            }
        }
        // No saved login: stay logged out, so the login screen opens the app.
    }


    fun getSavedCoupleId(): String {
        val current = _currentUserState.value?.coupleId?.trim()?.ifBlank { null }
        if (!current.isNullOrBlank()) return current
        val sessionPrefs = context.getSharedPreferences("cherish_user_session", Context.MODE_PRIVATE)
        val sessionCoupleId = sessionPrefs.getString("coupleId", null)?.trim()?.ifBlank { null }
        if (!sessionCoupleId.isNullOrBlank()) return sessionCoupleId
        val secretKey = securityPrefs.getCoupleSecretKey().trim().ifBlank { null }
        if (!secretKey.isNullOrBlank()) return secretKey
        return "couple_default"
    }

    private fun clearLocalUserSession() {
        context.getSharedPreferences("cherish_user_session", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .apply()
    }

    private companion object {
        /** Login failed; doesn't say which part was wrong. */
        const val WRONG_LOGIN_DETAILS = "Login details are incorrect"
        const val CONNECTION_PROBLEM = "Couldn't check your login. Check your internet connection and try again."

        /** Partner heartbeats ~10 s apart; a bigger jump is a catch-up after a disconnect, not a live beat. */
        const val LIVE_HEARTBEAT_MAX_STEP_MS = 60_000L
        /** notificationStatus values meaning a phone shows no notifications (the Profile screen's list). */
        val NOTIFICATIONS_OFF = setOf("CLOSED", "OFF", "MUTED")
    }
}

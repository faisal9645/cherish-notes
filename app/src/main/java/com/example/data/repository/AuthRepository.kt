package com.example.data.repository

import android.content.Context
import android.util.Log
import com.example.data.model.SpaceRequest
import com.example.data.model.User
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
    private var isAppInForeground: Boolean = false
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
            updateFcmToken(localUser.id)
            listenToCurrentUser(localUser.id)
            connectPartnerListenerOnce()
            localUser.coupleId?.trim()?.ifBlank { null }?.let { listenToCoupleRoom(it, localUser.id) }
        }

        // When disguise state changes (e.g. entering Notes), update presence immediately
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main).launch {
            securityPrefs.isDisguiseActive.collect {
                updatePresence()
            }
        }
    }

    /**
     * Logged in = a couple login was made on this phone (saved session). The Firebase anonymous
     * sign-in alone doesn't count: it also happens before a login's details are checked.
     */
    fun isUserLoggedIn(): Boolean {
        return _currentUserState.value != null
    }

    /**
     * The logged-in user's id, or "" when nobody is logged in. There's deliberately no stand-in id:
     * one used to send a logged-out phone's status into someone's real profile (and older
     * versions created a stray users/local_user_a that way).
     */
    fun getCurrentUserId(): String {
        return _currentUserState.value?.id?.trim().orEmpty()
    }

    /** This user's profile document; null when nobody is logged in, so nothing gets written. */
    private fun userDocument(uid: String) =
        if (uid.isBlank()) null else firestore?.collection("users")?.document(uid)

    fun setInChatTab(inChat: Boolean) {
        isActivelyInChatTab = inChat
        updatePresence()
    }

    /** The app is open on screen (the chat, any tab, or Notes). */
    fun isAppOpenOnScreen(): Boolean = isAppInForeground

    fun isUserActivelyInChat(): Boolean {
        val isDisguised = securityPrefs.isDisguiseActive.value
        return isAppInForeground && isActivelyInChatTab && !isDisguised
    }

    fun onAppForegroundStateChanged(inForeground: Boolean) {
        isAppInForeground = inForeground
        if (inForeground) wasForegroundThisProcess = true
        updatePresence()
        if (inForeground) {
            startHeartbeat()
        } else {
            heartbeatJob?.cancel()
        }
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
                    val senderId = signal["senderId"] as? String
                    if (signal["type"] == "heartbeat" && !senderId.isNullOrBlank() && senderId != cleanUid) {
                        val sentAt = (signal["at"] as? com.google.firebase.Timestamp)?.toDate()?.time ?: 0L
                        com.example.notifications.ThinkingOfYou.onSignal(context, signal["id"] as? String ?: "", sentAt)
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
        val coupleRef = getCoupleDocRef(_currentUserState.value?.coupleId) ?: return false
        if (uid.isBlank()) return false
        val signal = mapOf(
            "id" to java.util.UUID.randomUUID().toString(),
            "type" to "heartbeat",
            "senderId" to uid,
            "at" to com.google.firebase.firestore.FieldValue.serverTimestamp()
        )
        coupleRef.set(mapOf("signal" to signal), com.google.firebase.firestore.SetOptions.merge())
        return true
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

        try {
            auth?.signInAnonymously()?.await()
        } catch (_: Exception) {}

        // Only the existing couple gets in: the secret code must match it, and the two names must
        // be its two members. Nothing new is created from the login screen.
        val fs = firestore ?: return Result.failure(Exception(CONNECTION_PROBLEM))
        val coupleDocRef = fs.collection("couples").document(coupleId)
        val coupleSnap = try {
            coupleDocRef.get().await()
        } catch (e: Exception) {
            Log.w("AuthRepository", "Couple check failed: ${e.message}")
            return Result.failure(Exception(CONNECTION_PROBLEM))
        }
        if (!coupleSnap.exists()) return Result.failure(Exception(WRONG_LOGIN_DETAILS))
        fun memberId(name: String?) = name?.trim()?.lowercase()?.filter { it.isLetterOrDigit() || it == '_' }
            ?.takeIf { it.isNotBlank() }?.let { "user_$it" }
        // By id once someone has joined, otherwise by the name given when the couple was set up
        val members = setOfNotNull(
            coupleSnap.getString("partner1Id") ?: memberId(coupleSnap.getString("partner1Name")),
            coupleSnap.getString("partner2Id") ?: memberId(coupleSnap.getString("partner2Name"))
        )
        if (uid == fallbackPartnerId || uid !in members || fallbackPartnerId !in members) {
            return Result.failure(Exception(WRONG_LOGIN_DETAILS))
        }

        run {
            try {
                run {
                    val p1Id = coupleSnap.getString("partner1Id")
                    val p2Id = coupleSnap.getString("partner2Id")
                    if (p1Id != null && p1Id != uid) {
                        finalPartnerId = p1Id
                        coupleDocRef.update(
                            mapOf(
                                "partner2Id" to uid,
                                "partner2Name" to displayName,
                                "updatedAt" to System.currentTimeMillis()
                            )
                        ).await()
                        try {
                            fs.collection("users").document(p1Id).update(mapOf("partnerId" to uid))
                        } catch (_: Exception) {}
                    } else if (p2Id != null && p2Id != uid) {
                        finalPartnerId = p2Id
                    }
                }
            } catch (e: Exception) {
                Log.w("AuthRepository", "Couple linking: ${e.message}")
            }
        }

        val finalUser = User(
            id = uid,
            email = virtualEmail,
            displayName = displayName,
            partnerId = finalPartnerId,
            partnerEmail = partnerVirtualEmail,
            coupleId = coupleId,
            isOnline = false,
            lastSeen = System.currentTimeMillis(),
            createdAt = System.currentTimeMillis()
        )

        _currentUserState.value = finalUser
        saveLocalUserSession(finalUser)
        securityPrefs.setApprovedPartnerEmail(partnerVirtualEmail)
        securityPrefs.setCoupleSecretKey(coupleId)

        listenToCurrentUser(uid)
        listenToPartner(finalPartnerId)
        listenToCoupleRoom(coupleId, uid)

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
                        "isOnline" to false,
                        "lastSeen" to System.currentTimeMillis()
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

        // 1. Faisal & Shali automatic detection
        val allContext = "$cleanUser $cleanPartner $cleanKey"
        if (allContext.contains("faisal") && allContext.contains("shali")) {
            return "couple_faisal_shali"
        }

        // 2. Custom couple key
        if (cleanKey.isNotBlank() && cleanKey != "cherish" && cleanKey != "cherish_forever_2026" && cleanKey != "cherish_love" && cleanKey != "couple_default") {
            return if (cleanKey.startsWith("couple_")) cleanKey else "couple_$cleanKey"
        }

        // 3. Deterministic sorted pairing
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
        } else if (cleanUser.contains("faisal")) {
            "user_shali"
        } else if (cleanUser.contains("shali")) {
            "user_faisal"
        } else null

        val displayName = cleanUser.replaceFirstChar { it.uppercase() }
        val virtualEmail = "$cleanUser@cherish.app"
        val partnerVirtualEmail = if (cleanPartner.isNotBlank()) {
            if (partnerUsername.contains("@")) partnerUsername.trim().lowercase() else "$cleanPartner@cherish.app"
        } else if (cleanUser.contains("faisal")) {
            "shalihafais36@gmail.com"
        } else if (cleanUser.contains("shali")) {
            "faisallasiaff@gmail.com"
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
        if (partnerId != null) {
            listenToPartner(partnerId)
        }
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
            } else if (cleanUser.contains("faisal")) {
                "user_shali"
            } else if (cleanUser.contains("shali")) {
                "user_faisal"
            } else null

            val partnerVirtualEmail = if (cleanPartner.isNotBlank()) {
                if (partnerUsernameOrEmail.contains("@")) partnerUsernameOrEmail.trim().lowercase() else "$cleanPartner@gmail.com"
            } else if (cleanUser.contains("faisal")) {
                "shalihafais36@gmail.com"
            } else if (cleanUser.contains("shali")) {
                "faisallasiaff@gmail.com"
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
                    kotlinx.coroutines.delay(2000L)
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

    private fun startHeartbeat() {
        heartbeatJob?.cancel()
        heartbeatJob = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
            while (isAppInForeground) {
                val isDisguised = securityPrefs.isDisguiseActive.value
                val shouldBeOnline = isAppInForeground && !isDisguised
                val uid = getCurrentUserId()
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
                    if (uid.isNotBlank()) {
                        val updates = mapOf<String, Any>(
                            "isOnline" to true,
                            "online" to true,
                            "lastSeen" to System.currentTimeMillis()
                        )
                        try {
                            userDocument(uid)?.set(
                                updates,
                                com.google.firebase.firestore.SetOptions.merge()
                            )
                        } catch (_: Exception) {}
                    }
                } else if (_currentUserState.value?.isOnline != false) {
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
        _currentUserState.value = _currentUserState.value?.copy(
            isOnline = online,
            lastSeen = System.currentTimeMillis()
        )
        try {
            val updates = mutableMapOf<String, Any>(
                "isOnline" to online,
                "online" to online,
                "lastSeen" to System.currentTimeMillis()
            )
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

    private fun listenToCurrentUser(uid: String) {
        val cleanUid = uid.trim().ifBlank { null } ?: return
        // Prevent duplicate listener for same user
        if (currentListeningUserId == cleanUid) return
        currentUserListener?.remove()
        currentListeningUserId = cleanUid
        val docRef = getUserDocRef(cleanUid) ?: return
        currentUserListener = docRef.addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.w("AuthRepository", "Listen to current user failed", error)
                    return@addSnapshotListener
                }
                if (snapshot != null && snapshot.exists()) {
                    var user = snapshot.toObject(User::class.java)
                    if (user != null) {
                        val boolOnline = snapshot.getBoolean("isOnline")
                            ?: snapshot.getBoolean("online")
                            ?: user.isOnline
                        val docLastSeen = snapshot.getLong("lastSeen")
                            ?: snapshot.getDate("lastSeen")?.time
                            ?: user.lastSeen
                        if (user.isOnline != boolOnline || user.lastSeen != docLastSeen) {
                            user = user.copy(
                                isOnline = boolOnline,
                                lastSeen = docLastSeen
                            )
                        }
                    }
                    _currentUserState.value = user
                    user?.let { saveLocalUserSession(it) }
                    // Only connect partner listener if partner ID changed
                    val newPartnerId = user?.partnerId
                    if (!newPartnerId.isNullOrBlank() && newPartnerId != currentListeningPartnerId) {
                        listenToPartner(newPartnerId)
                    }
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

    /**
     * Publishes the partner's document from any of the partner listeners. A heartbeat that arrives
     * while listening (a small step on from the previous one) is timed on this phone's own clock, so
     * online/offline doesn't depend on the two phones' clocks agreeing. A first copy, or a big jump
     * after being disconnected, falls back to comparing clocks until the next heartbeat.
     */
    private fun publishPartnerSnapshot(doc: com.google.firebase.firestore.DocumentSnapshot) {
        var partner = doc.toObject(User::class.java)
        if (partner != null) {
            val boolOnline = doc.getBoolean("isOnline")
                ?: doc.getBoolean("online")
                ?: partner.isOnline
            val docLastSeen = doc.getLong("lastSeen")
                ?: doc.getDate("lastSeen")?.time
                ?: partner.lastSeen
            if (partner.isOnline != boolOnline || partner.lastSeen != docLastSeen) {
                partner = partner.copy(
                    isOnline = boolOnline,
                    lastSeen = docLastSeen
                )
            }
            if (!doc.metadata.isFromCache) {
                val previous = partnerServerLastSeen
                val step = if (previous != null && previous.first == doc.id) docLastSeen - previous.second else -1L
                if (step in 1L..LIVE_HEARTBEAT_MAX_STEP_MS) {
                    partnerPresenceReceivedAt = System.currentTimeMillis()
                } else if (step != 0L) {
                    partnerPresenceReceivedAt = 0L
                }
                partnerServerLastSeen = doc.id to docLastSeen
            }
            if (partnerServerLastSeen?.first == doc.id) partner.presenceReceivedAt = partnerPresenceReceivedAt
        }
        _partnerUserState.value = partner
        handlePartnerCheckAfterReminder(partner)
    }

    private fun findAndListenToPartner(partnerEmail: String?, coupleId: String?, uid: String) {
        val fs = firestore ?: return

        // 1. Prioritize discovery via Couple ID (e.g. couple_faisal_shali)
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
            doc?.toObject(User::class.java)
        } catch (e: Exception) {
            null
        }
    }

    /** Stores this phone's push token on the logged-in profile (when the token changes). */
    fun saveFcmToken(token: String) {
        val uid = getCurrentUserId()
        if (uid.isBlank()) return
        userDocument(uid)?.set(mapOf("fcmToken" to token), com.google.firebase.firestore.SetOptions.merge())
    }

    private fun updateFcmToken(uid: String) {
        try {
            val availability = com.google.android.gms.common.GoogleApiAvailability.getInstance()
                .isGooglePlayServicesAvailable(context)
            if (availability != com.google.android.gms.common.ConnectionResult.SUCCESS) {
                Log.d("AuthRepository", "Play Services not available for FCM ($availability)")
                return
            }

            val messaging = FirebaseMessaging.getInstance()
            messaging.token
                .addOnSuccessListener { token ->
                    userDocument(uid)?.set(mapOf("fcmToken" to token), com.google.firebase.firestore.SetOptions.merge())
                }
                .addOnFailureListener { e ->
                    Log.w("AuthRepository", "FCM token registration failed gracefully: ${e.message}")
                }
        } catch (e: Throwable) {
            Log.w("AuthRepository", "FCM token registration skipped", e)
        }
    }

    fun logout() {
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
        try {
            auth?.signOut()
        } catch (e: Exception) {
            // ignore
        }
        _currentUserState.value = null
        _partnerUserState.value = null
        clearLocalUserSession()
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
            .apply()
    }

    private fun loadLocalUserSession() {
        val prefs = context.getSharedPreferences("cherish_user_session", Context.MODE_PRIVATE)
        val rawUid = prefs.getString("uid", null)?.trim()?.ifBlank { null }
        if (rawUid != null) {
            val uid = rawUid
            val targetTime = prefs.getLong("checkAfterTimeMillis", 0L)
            val note = prefs.getString("checkAfterNote", "") ?: ""
            val active = prefs.getBoolean("checkAfterActive", false)
            val photo = prefs.getString("photoUrl", null)?.trim()?.ifBlank { null }
            val status = prefs.getString("statusMessage", "Loving every moment with you ✨")?.trim()?.ifBlank { "Loving every moment with you ✨" } ?: "Loving every moment with you ✨"
            val partnerId = prefs.getString("partnerId", null)?.trim()?.ifBlank { null }
            val partnerEmail = prefs.getString("partnerEmail", null)?.trim()?.ifBlank { null }
            val coupleId = prefs.getString("coupleId", "couple_faisal_shali")?.trim()?.ifBlank { "couple_faisal_shali" } ?: "couple_faisal_shali"
            val defaultName = if (uid == "user_shali") "Shali" else "Faisal"
            val defaultPartnerId = if (uid == "user_faisal") "user_shali" else "user_faisal"
            val defaultPartnerEmail = if (uid == "user_faisal") "shalihafais36@gmail.com" else "faisallasiaff@gmail.com"
            val user = User(
                id = uid,
                email = prefs.getString("email", "") ?: "",
                displayName = prefs.getString("displayName", defaultName)?.ifBlank { defaultName } ?: defaultName,
                photoUrl = photo,
                statusMessage = status,
                partnerId = partnerId ?: defaultPartnerId,
                partnerEmail = partnerEmail ?: defaultPartnerEmail,
                coupleId = coupleId,
                checkAfterTimeMillis = if (targetTime > 0L) targetTime else null,
                checkAfterNote = note.ifBlank { null },
                checkAfterActive = active
            )
            _currentUserState.value = user
            val partnerUid = user.partnerId?.ifBlank { null } ?: defaultPartnerId
            val partnerName = if (partnerUid == "user_shali") "Shali" else "Faisal"
            val partnerEmailAddr = if (partnerUid == "user_shali") "shalihafais36@gmail.com" else "faisallasiaff@gmail.com"
            if (_partnerUserState.value == null) {
                _partnerUserState.value = User(
                    id = partnerUid,
                    email = partnerEmailAddr,
                    displayName = partnerName,
                    partnerId = user.id,
                    partnerEmail = user.email,
                    coupleId = coupleId,
                    isOnline = true,
                    statusMessage = if (partnerUid == "user_shali") "Always with you 💕" else "Forever yours ❤️"
                )
            }
        }
        // No saved login: stay logged out, so the login screen (and only the right details) opens
        // the app. There used to be an automatic login as a default user here.
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
    }
}

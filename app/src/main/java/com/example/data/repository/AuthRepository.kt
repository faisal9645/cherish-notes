package com.example.data.repository

import android.content.Context
import android.util.Log
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
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class AuthRepository(private val context: Context) {
    private val securityPrefs = SecurityPreferences.getInstance(context)

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

    private var partnerListener: ListenerRegistration? = null
    private var currentUserListener: ListenerRegistration? = null

    init {
        loadLocalUserSession()
        val localUser = _currentUserState.value
        if (localUser != null) {
            listenToCurrentUser(localUser.id)
            connectPartnerListener()
            setOnline(true)
        } else {
            val currentFirebaseUser = auth?.currentUser
            if (currentFirebaseUser != null) {
                listenToCurrentUser(currentFirebaseUser.uid)
                connectPartnerListener()
                setOnline(true)
            }
        }
    }

    fun isUserLoggedIn(): Boolean {
        return auth?.currentUser != null || _currentUserState.value != null
    }

    fun getCurrentUserId(): String {
        return _currentUserState.value?.id ?: auth?.currentUser?.uid ?: "local_user_a"
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
        connectPartnerListener()
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
            firestore?.collection("users")?.document(uid)?.set(localUser)
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
            connectPartnerListener()
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
            firestore?.collection("users")?.document(uid)?.update(updates as Map<String, Any>)?.await()
            Result.success(Unit)
        } catch (e: Exception) {
            Log.w("AuthRepository", "Failed to update profile in Firestore, local update kept", e)
            Result.success(Unit)
        }
    }

    suspend fun pairWithPartner(partnerUsername: String, coupleSecretKey: String): Result<User> {
        val uid = getCurrentUserId()
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
                fs.collection("users").document(uid).update(updates).await()
                listenToPartner(partnerId)
            }
            Result.success(updatedUser)
        } catch (e: Exception) {
            Log.w("AuthRepository", "Pair with partner Firestore update warning", e)
            Result.success(updatedUser)
        }
    }

    private var isAppInForeground: Boolean = false
    private var heartbeatJob: kotlinx.coroutines.Job? = null

    fun onAppForegroundStateChanged(inForeground: Boolean) {
        isAppInForeground = inForeground
        setOnline(inForeground)
        if (inForeground) {
            startHeartbeat()
        } else {
            heartbeatJob?.cancel()
        }
    }

    private fun startHeartbeat() {
        heartbeatJob?.cancel()
        heartbeatJob = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
            while (isAppInForeground) {
                kotlinx.coroutines.delay(20_000L)
                if (isAppInForeground) {
                    val uid = getCurrentUserId()
                    if (uid.isNotBlank() && uid != "local_user_a") {
                        val updates = mapOf<String, Any>(
                            "isOnline" to true,
                            "online" to true,
                            "lastSeen" to System.currentTimeMillis()
                        )
                        try {
                            firestore?.collection("users")?.document(uid)?.set(
                                updates,
                                com.google.firebase.firestore.SetOptions.merge()
                            )
                        } catch (_: Exception) {}
                    }
                }
            }
        }
    }

    fun setOnline(online: Boolean) {
        val uid = getCurrentUserId()
        if (uid.isBlank() || uid == "local_user_a") return
        _currentUserState.value = _currentUserState.value?.copy(
            isOnline = online,
            lastSeen = System.currentTimeMillis()
        )
        try {
            val updates = mutableMapOf<String, Any>(
                "isOnline" to online,
                "lastSeen" to System.currentTimeMillis()
            )
            if (!online) {
                updates["typingInChat"] = false
                updates["recordingAudioInChat"] = false
            }
            firestore?.collection("users")?.document(uid)?.set(
                updates,
                com.google.firebase.firestore.SetOptions.merge()
            )
        } catch (e: Exception) {
            Log.w("AuthRepository", "Failed to update online status", e)
        }

        if (online) {
            connectPartnerListener()
        }
    }

    fun setTyping(typing: Boolean) {
        val uid = getCurrentUserId()
        try {
            firestore?.collection("users")?.document(uid)?.set(
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
            firestore?.collection("users")?.document(uid)?.set(
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
            firestore?.collection("users")?.document(uid)?.update(updates)
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
            firestore?.collection("users")?.document(uid)?.update(updates)
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

    private var partnerEmailQueryListener: ListenerRegistration? = null
    private var partnerCoupleQueryListener: ListenerRegistration? = null

    private fun listenToCurrentUser(uid: String) {
        currentUserListener?.remove()
        currentUserListener = firestore?.collection("users")?.document(uid)
            ?.addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.w("AuthRepository", "Listen to current user failed", error)
                    return@addSnapshotListener
                }
                if (snapshot != null && snapshot.exists()) {
                    val user = snapshot.toObject(User::class.java)
                    _currentUserState.value = user
                    user?.let { saveLocalUserSession(it) }
                    connectPartnerListener()
                }
            }
    }

    fun connectPartnerListener() {
        val user = _currentUserState.value ?: return
        val partnerId = user.partnerId
        if (!partnerId.isNullOrBlank() && partnerId != _partnerUserState.value?.id) {
            listenToPartner(partnerId)
        }
        // Always also actively discover partner by coupleId and email in Firestore
        findAndListenToPartner(user.partnerEmail, user.coupleId, user.id)
    }

    fun listenToPartner(partnerId: String) {
        if (partnerId.isBlank()) return
        partnerListener?.remove()
        partnerListener = firestore?.collection("users")?.document(partnerId)
            ?.addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.w("AuthRepository", "Listen to partner failed", error)
                    return@addSnapshotListener
                }
                if (snapshot != null && snapshot.exists()) {
                    val partner = snapshot.toObject(User::class.java)
                    _partnerUserState.value = partner
                    handlePartnerCheckAfterReminder(partner)
                }
            }
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
                        val partner = doc.toObject(User::class.java)
                        _partnerUserState.value = partner
                        handlePartnerCheckAfterReminder(partner)
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
            if (_partnerUserState.value == null) {
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
                        val partner = doc.toObject(User::class.java)
                        _partnerUserState.value = partner
                        handlePartnerCheckAfterReminder(partner)
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
            val doc = firestore?.collection("users")?.document(uid)?.get()?.await()
            doc?.toObject(User::class.java)
        } catch (e: Exception) {
            null
        }
    }

    private fun updateFcmToken(uid: String) {
        try {
            FirebaseMessaging.getInstance().token.addOnSuccessListener { token ->
                firestore?.collection("users")?.document(uid)?.update("fcmToken", token)
            }
        } catch (e: Exception) {
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
        val uid = prefs.getString("uid", null)
        if (uid != null) {
            val targetTime = prefs.getLong("checkAfterTimeMillis", 0L)
            val note = prefs.getString("checkAfterNote", "") ?: ""
            val active = prefs.getBoolean("checkAfterActive", false)
            val photo = prefs.getString("photoUrl", null)?.ifBlank { null }
            val status = prefs.getString("statusMessage", "Loving every moment with you ✨") ?: "Loving every moment with you ✨"
            val partnerId = prefs.getString("partnerId", null)?.ifBlank { null }
            val partnerEmail = prefs.getString("partnerEmail", null)?.ifBlank { null }
            val coupleId = prefs.getString("coupleId", "couple_default") ?: "couple_default"
            _currentUserState.value = User(
                id = uid,
                email = prefs.getString("email", "") ?: "",
                displayName = prefs.getString("displayName", "User") ?: "User",
                photoUrl = photo,
                statusMessage = status,
                partnerId = partnerId,
                partnerEmail = partnerEmail,
                coupleId = coupleId,
                checkAfterTimeMillis = if (targetTime > 0L) targetTime else null,
                checkAfterNote = note.ifBlank { null },
                checkAfterActive = active
            )
        }
    }


    private fun clearLocalUserSession() {
        context.getSharedPreferences("cherish_user_session", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .apply()
    }
}

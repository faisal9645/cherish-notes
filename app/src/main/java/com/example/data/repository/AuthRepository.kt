package com.example.data.repository

import android.content.Context
import android.util.Log
import com.example.data.model.User
import com.example.security.SecurityPreferences
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
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
        val currentFirebaseUser = auth?.currentUser
        if (currentFirebaseUser != null) {
            listenToCurrentUser(currentFirebaseUser.uid)
        }
    }

    fun isUserLoggedIn(): Boolean {
        return auth?.currentUser != null || _currentUserState.value != null
    }

    fun getCurrentUserId(): String {
        return _currentUserState.value?.id ?: auth?.currentUser?.uid ?: "local_user_a"
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
        val displayName = cleanUser.replaceFirstChar { it.uppercase() }
        val virtualEmail = "$cleanUser@cherish.app"
        val partnerVirtualEmail = if (cleanPartner.isNotBlank()) "$cleanPartner@cherish.app" else null

        // Try Firebase Auth in the background (will not block if disabled/offline)
        try {
            val authInst = auth
            if (authInst != null) {
                try {
                    authInst.signInWithEmailAndPassword(virtualEmail, pass).await()
                } catch (_: Exception) {
                    try {
                        authInst.createUserWithEmailAndPassword(virtualEmail, pass).await()
                    } catch (_: Exception) {
                        try {
                            if (authInst.currentUser == null) {
                                authInst.signInAnonymously().await()
                            }
                        } catch (_: Exception) {}
                    }
                }
            }
        } catch (_: Exception) {}

        val fs = firestore
        var loadedUser: User? = null

        if (fs != null) {
            try {
                val userDocRef = fs.collection("users").document(uid)
                val snapshot = userDocRef.get().await()
                if (snapshot.exists()) {
                    val existing = snapshot.toObject(User::class.java)
                    val storedPass = snapshot.getString("password")
                    if (storedPass != null && storedPass != pass) {
                        return Result.failure(IllegalArgumentException("Incorrect password for @$cleanUser"))
                    }
                    val updated = (existing ?: User(id = uid)).copy(
                        displayName = existing?.displayName?.ifBlank { displayName } ?: displayName,
                        isOnline = true,
                        lastSeen = System.currentTimeMillis(),
                        partnerId = partnerId ?: existing?.partnerId,
                        partnerEmail = partnerVirtualEmail ?: existing?.partnerEmail,
                        coupleId = coupleId
                    )
                    userDocRef.update(
                        mapOf(
                            "isOnline" to true,
                            "lastSeen" to System.currentTimeMillis(),
                            "partnerId" to (partnerId ?: existing?.partnerId ?: ""),
                            "partnerEmail" to (partnerVirtualEmail ?: existing?.partnerEmail ?: ""),
                            "coupleId" to coupleId
                        )
                    ).await()
                    loadedUser = updated
                } else {
                    val newUser = User(
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
                    userDocRef.set(newUser).await()
                    try {
                        userDocRef.update("password", pass).await()
                    } catch (_: Exception) {}
                    loadedUser = newUser
                }
            } catch (e: Exception) {
                Log.w("AuthRepository", "Firestore user sync warning, continuing with local session", e)
            }
        }

        val finalUser = loadedUser ?: User(
            id = uid,
            email = virtualEmail,
            displayName = displayName,
            partnerId = partnerId,
            partnerEmail = partnerVirtualEmail,
            coupleId = coupleId,
            isOnline = true,
            lastSeen = System.currentTimeMillis()
        )

        _currentUserState.value = finalUser
        saveLocalUserSession(finalUser)
        if (!partnerVirtualEmail.isNullOrBlank()) {
            securityPrefs.setApprovedPartnerEmail(partnerVirtualEmail)
        }
        securityPrefs.setCoupleSecretKey(coupleId)

        listenToCurrentUser(uid)
        if (partnerId != null) {
            listenToPartner(partnerId)
        } else {
            connectPartnerListener()
        }
        updateFcmToken(uid)
        setOnline(true)

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

    fun setOnline(online: Boolean) {
        val uid = getCurrentUserId()
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
            firestore?.collection("users")?.document(uid)?.update(updates)
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
            firestore?.collection("users")?.document(uid)?.update("typingInChat", typing)
        } catch (e: Exception) {
            // ignore
        }
    }

    fun setRecordingAudio(recording: Boolean) {
        val uid = getCurrentUserId()
        try {
            firestore?.collection("users")?.document(uid)?.update("recordingAudioInChat", recording)
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

    private var partnerQueryListener: ListenerRegistration? = null

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
                    val partnerId = user?.partnerId
                    if (!partnerId.isNullOrBlank()) {
                        if (partnerId != _partnerUserState.value?.id) {
                            listenToPartner(partnerId)
                        }
                    } else {
                        connectPartnerListener()
                    }
                }
            }
    }

    fun connectPartnerListener() {
        val user = _currentUserState.value ?: return
        val partnerId = user.partnerId
        if (!partnerId.isNullOrBlank()) {
            if (partnerId != _partnerUserState.value?.id) {
                listenToPartner(partnerId)
            }
        } else {
            findAndListenToPartner(user.partnerEmail, user.coupleId, user.id)
        }
    }

    fun listenToPartner(partnerId: String) {
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
        val cleanEmail = partnerEmail?.trim()?.lowercase()
        if (!cleanEmail.isNullOrBlank()) {
            val partnerUid = if (cleanEmail.contains("@")) "user_${cleanEmail.substringBefore("@")}" else "user_$cleanEmail"
            listenToPartner(partnerUid)

            partnerQueryListener?.remove()
            partnerQueryListener = fs.collection("users")
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
        } else if (!coupleId.isNullOrBlank() && coupleId != "couple_default") {
            partnerQueryListener?.remove()
            partnerQueryListener = fs.collection("users")
                .whereEqualTo("coupleId", coupleId)
                .limit(5)
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
        setOnline(false)
        currentUserListener?.remove()
        partnerListener?.remove()
        partnerQueryListener?.remove()
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

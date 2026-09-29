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
        val currentFirebaseUser = auth?.currentUser
        if (currentFirebaseUser != null) {
            listenToCurrentUser(currentFirebaseUser.uid)
        } else {
            // Check local fallback session
            loadLocalUserSession()
        }
    }

    fun isUserLoggedIn(): Boolean {
        return auth?.currentUser != null || _currentUserState.value != null
    }

    fun getCurrentUserId(): String {
        return auth?.currentUser?.uid ?: _currentUserState.value?.id ?: "local_user_a"
    }

    suspend fun loginWithEmail(email: String, pass: String): Result<User> {
        return try {
            val authInstance = auth
            if (authInstance != null) {
                val authResult = authInstance.signInWithEmailAndPassword(email.trim(), pass).await()
                val uid = authResult.user?.uid ?: throw IllegalStateException("User ID is null")
                val user = fetchUserFromFirestore(uid) ?: User(
                    id = uid,
                    email = email.trim(),
                    displayName = authResult.user?.displayName ?: email.substringBefore("@")
                )
                listenToCurrentUser(uid)
                updateFcmToken(uid)
                setOnline(true)
                Result.success(user)
            } else {
                // Local fallback mode for offline/preview
                val localUser = User(
                    id = "user_me",
                    email = email.trim(),
                    displayName = email.substringBefore("@").replaceFirstChar { it.uppercase() },
                    partnerEmail = securityPrefs.getApprovedPartnerEmail()
                )
                _currentUserState.value = localUser
                saveLocalUserSession(localUser)
                Result.success(localUser)
            }
        } catch (e: Exception) {
            Log.e("AuthRepository", "Login failed", e)
            Result.failure(e)
        }
    }

    suspend fun registerWithEmail(email: String, pass: String, displayName: String): Result<User> {
        return try {
            val authInstance = auth
            if (authInstance != null) {
                val authResult = authInstance.createUserWithEmailAndPassword(email.trim(), pass).await()
                val uid = authResult.user?.uid ?: throw IllegalStateException("User ID is null")
                val newUser = User(
                    id = uid,
                    email = email.trim(),
                    displayName = displayName.ifBlank { email.substringBefore("@") },
                    createdAt = System.currentTimeMillis()
                )
                firestore?.collection("users")?.document(uid)?.set(newUser)?.await()
                listenToCurrentUser(uid)
                updateFcmToken(uid)
                setOnline(true)
                Result.success(newUser)
            } else {
                val localUser = User(
                    id = "user_me",
                    email = email.trim(),
                    displayName = displayName.ifBlank { email.substringBefore("@") },
                    partnerEmail = securityPrefs.getApprovedPartnerEmail()
                )
                _currentUserState.value = localUser
                saveLocalUserSession(localUser)
                Result.success(localUser)
            }
        } catch (e: Exception) {
            Log.e("AuthRepository", "Registration failed", e)
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

    suspend fun updateProfile(displayName: String, statusMessage: String, photoUrl: String? = null): Result<Unit> {
        val uid = getCurrentUserId()
        return try {
            val updates = mutableMapOf<String, Any>(
                "displayName" to displayName,
                "statusMessage" to statusMessage
            )
            photoUrl?.let { updates["photoUrl"] = it }
            firestore?.collection("users")?.document(uid)?.update(updates)?.await()

            _currentUserState.value = _currentUserState.value?.copy(
                displayName = displayName,
                statusMessage = statusMessage,
                photoUrl = photoUrl ?: _currentUserState.value?.photoUrl
            )
            Result.success(Unit)
        } catch (e: Exception) {
            _currentUserState.value = _currentUserState.value?.copy(
                displayName = displayName,
                statusMessage = statusMessage,
                photoUrl = photoUrl ?: _currentUserState.value?.photoUrl
            )
            Result.success(Unit)
        }
    }

    suspend fun pairWithPartner(partnerEmail: String, coupleSecretKey: String): Result<User> {
        val uid = getCurrentUserId()
        val formattedEmail = partnerEmail.trim().lowercase()
        securityPrefs.setApprovedPartnerEmail(formattedEmail)
        securityPrefs.setCoupleSecretKey(coupleSecretKey)

        val coupleId = "couple_${coupleSecretKey.trim().uppercase().replace(" ", "_")}"

        return try {
            val fs = firestore
            if (fs != null) {
                // Find partner by email in Firestore
                val partnerQuery = fs.collection("users")
                    .whereEqualTo("email", formattedEmail)
                    .limit(1)
                    .get()
                    .await()

                val partnerDoc = partnerQuery.documents.firstOrNull()
                val partnerId = partnerDoc?.id

                // Update current user's document
                val updates = mutableMapOf<String, Any>(
                    "partnerEmail" to formattedEmail,
                    "coupleId" to coupleId
                )
                if (partnerId != null) {
                    updates["partnerId"] = partnerId
                }
                fs.collection("users").document(uid).update(updates).await()

                if (partnerId != null) {
                    listenToPartner(partnerId)
                }

                val updatedUser = _currentUserState.value?.copy(
                    partnerEmail = formattedEmail,
                    partnerId = partnerId,
                    coupleId = coupleId
                ) ?: User(id = uid, partnerEmail = formattedEmail, partnerId = partnerId, coupleId = coupleId)
                _currentUserState.value = updatedUser
                Result.success(updatedUser)
            } else {
                val updatedUser = _currentUserState.value?.copy(
                    partnerEmail = formattedEmail,
                    coupleId = coupleId
                ) ?: User(id = uid, partnerEmail = formattedEmail, coupleId = coupleId)
                _currentUserState.value = updatedUser
                saveLocalUserSession(updatedUser)
                Result.success(updatedUser)
            }
        } catch (e: Exception) {
            val updatedUser = _currentUserState.value?.copy(
                partnerEmail = formattedEmail,
                coupleId = coupleId
            ) ?: User(id = uid, partnerEmail = formattedEmail, coupleId = coupleId)
            _currentUserState.value = updatedUser
            Result.success(updatedUser)
        }
    }

    fun setOnline(online: Boolean) {
        val uid = getCurrentUserId()
        try {
            val updates = mapOf(
                "isOnline" to online,
                "lastSeen" to System.currentTimeMillis()
            )
            firestore?.collection("users")?.document(uid)?.update(updates)
        } catch (e: Exception) {
            Log.w("AuthRepository", "Failed to update online status", e)
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
                    user?.partnerId?.let { partnerId ->
                        if (partnerId != _partnerUserState.value?.id) {
                            listenToPartner(partnerId)
                        }
                    }
                }
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
                    _partnerUserState.value = snapshot.toObject(User::class.java)
                }
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
            .putString("partnerEmail", user.partnerEmail)
            .putString("coupleId", user.coupleId)
            .apply()
    }

    private fun loadLocalUserSession() {
        val prefs = context.getSharedPreferences("cherish_user_session", Context.MODE_PRIVATE)
        val uid = prefs.getString("uid", null)
        if (uid != null) {
            _currentUserState.value = User(
                id = uid,
                email = prefs.getString("email", "") ?: "",
                displayName = prefs.getString("displayName", "User") ?: "User",
                partnerEmail = prefs.getString("partnerEmail", null),
                coupleId = prefs.getString("coupleId", "couple_default")
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

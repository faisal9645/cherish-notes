package com.example.data.repository

import android.content.Context
import android.util.Log
import com.example.data.model.SyncMoment
import com.example.data.model.SyncPatterns
import com.example.data.model.SyncStatus
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.AggregateSource
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.MetadataChanges
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Love Synchronicity: the numbers we noticed and recorded, shared by both phones.
 *
 * One live listener covers only this month (today's and the month's counts, the tiles, the
 * streak). Earlier months are read only when asked for (history, or a streak reaching back), the
 * all-time total is this month plus one count of everything before it, so nothing re-reads the
 * whole history. Days start at 6 AM, like the chat's.
 */
class SynchronicityRepository(
    private val context: Context,
    private val authRepository: AuthRepository
) {
    private val firestore: FirebaseFirestore? by lazy {
        try {
            if (FirebaseApp.getApps(context).isNotEmpty()) FirebaseFirestore.getInstance() else null
        } catch (e: Exception) {
            Log.w(TAG, "Firestore not initialized", e)
            null
        }
    }

    private val auth: FirebaseAuth? by lazy {
        try {
            if (FirebaseApp.getApps(context).isNotEmpty()) FirebaseAuth.getInstance() else null
        } catch (e: Exception) {
            null
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val prefs by lazy { context.getSharedPreferences("cherish_synchronicity", Context.MODE_PRIVATE) }

    // This month's moments, newest first. Built from the documents by id, so the same moment
    // arriving again (pending, then confirmed by the server) is never counted twice.
    private val _monthMoments = MutableStateFlow<List<SyncMoment>>(emptyList())
    val monthMoments: StateFlow<List<SyncMoment>> = _monthMoments.asStateFlow()

    // How many moments came before this month (for the all-time total)
    private val _olderCount = MutableStateFlow(0)
    val olderCount: StateFlow<Int> = _olderCount.asStateFlow()

    // Our synchronicity streak: days in a row with a moment, ending today (or yesterday, until today
    // ends). Its own number; the Daily Heartbeat streak isn't touched.
    private val _streak = MutableStateFlow(0)
    val streak: StateFlow<Int> = _streak.asStateFlow()

    private val _status = MutableStateFlow(SyncStatus.LOADING)
    val status: StateFlow<SyncStatus> = _status.asStateFlow()

    private var listenKey: String? = null
    private var monthListener: ListenerRegistration? = null
    private var streakJob: Job? = null
    /** Days with a moment in earlier months ("coupleId|yyyy-MM"), read once when a streak reaches them. */
    private val olderMonthDays = HashMap<String, Set<String>>()
    private val confirmedMembers = HashSet<String>()

    private fun coupleId(): String? {
        if (!authRepository.isUserLoggedIn()) return null
        return authRepository.currentUserState.value?.coupleId?.trim()?.ifBlank { null } ?: DEFAULT_COUPLE_ID
    }

    private fun moments(coupleId: String): CollectionReference? =
        firestore?.collection("couples")?.document(coupleId)?.collection(COLLECTION)

    /**
     * Starts the live view of this month (once; calling again does nothing unless the month or the
     * couple changed). Called while Love & Us is on screen.
     */
    fun start() {
        val coupleId = coupleId()
        if (coupleId == null) {
            _status.value = SyncStatus.NO_COUPLE
            return
        }
        val month = currentMonth()
        val key = "$coupleId|$month"
        if (key == listenKey) return
        val coupleChanged = listenKey?.substringBefore('|') != coupleId
        monthListener?.remove()
        monthListener = null
        // Until the server says otherwise: the last known count, or (a new month) the old month on top
        val olderGuess = if (coupleChanged) 0 else _olderCount.value + _monthMoments.value.size
        if (coupleChanged) olderMonthDays.clear()
        _olderCount.value = prefs.getInt(olderCountKey(coupleId, month), olderGuess)
        listenKey = key
        _monthMoments.value = emptyList()
        _status.value = SyncStatus.LOADING
        val ref = moments(coupleId) ?: return
        scope.launch {
            ensureMember(coupleId)
            if (listenKey != key) return@launch
            monthListener = ref.whereEqualTo("periodMonth", month)
                .addSnapshotListener(MetadataChanges.INCLUDE) { snapshot, error ->
                    if (listenKey != key) return@addSnapshotListener
                    if (error != null) {
                        Log.w(TAG, "Listen synchronicity failed", error)
                        // Let a later start() try again
                        monthListener?.remove()
                        monthListener = null
                        listenKey = null
                        _status.value = if (error.code == FirebaseFirestoreException.Code.PERMISSION_DENIED) {
                            SyncStatus.NOT_ALLOWED
                        } else {
                            SyncStatus.READY
                        }
                        return@addSnapshotListener
                    }
                    if (snapshot == null) return@addSnapshotListener
                    val items = snapshot.documents
                        .mapNotNull { it.toMoment() }
                        .distinctBy { it.id }
                        .sortedByDescending { it.seenAt }
                    if (items != _monthMoments.value) {
                        _monthMoments.value = items
                        recomputeStreak(coupleId, items)
                    }
                    _status.value = SyncStatus.READY
                }
            refreshOlderCount(coupleId, month)
        }
    }

    /** Called when the 6 AM day may have changed: a new month starts a new live view. */
    fun onDayChanged() {
        if (listenKey != null && listenKey?.substringAfter('|') != currentMonth()) start()
        else coupleId()?.let { recomputeStreak(it, _monthMoments.value) }
    }

    sealed class SaveResult {
        object Saved : SaveResult()
        /** Kept on the phone; it reaches the partner once back online. */
        object SavedOffline : SaveResult()
        data class Failed(val message: String) : SaveResult()
    }

    /**
     * Saves a sighting seen at [seenAt] (now, or earlier when it's recorded later; never in the
     * future). [momentId] is made once per "Record" sheet: pressing Save again or retrying after an
     * error writes the same document, never a second moment.
     */
    suspend fun recordMoment(momentId: String, pattern: String, note: String, seenAt: Long): SaveResult {
        val coupleId = coupleId() ?: return SaveResult.Failed("Log in to record moments")
        val userId = authRepository.getCurrentUserId().ifBlank { null } ?: return SaveResult.Failed("Log in to record moments")
        val clean = SyncPatterns.normalize(pattern) ?: return SaveResult.Failed("Pick a number first")
        val ref = moments(coupleId) ?: return SaveResult.Failed("Can't reach the server right now")
        ensureMember(coupleId)
        val now = System.currentTimeMillis()
        val seen = seenAt.coerceAtMost(now)
        val day = dayKey(seen)
        val data = hashMapOf<String, Any>(
            "id" to momentId,
            "pattern" to clean,
            "note" to note.trim().take(SyncPatterns.NOTE_MAX),
            "createdBy" to userId,
            "authUid" to (auth?.currentUser?.uid ?: ""),
            "createdAt" to FieldValue.serverTimestamp(),
            "clientCreatedAt" to now,
            "seenAt" to seen,
            "periodDate" to day,
            "periodMonth" to day.take(7),
            "timeZone" to TimeZone.getDefault().id
        )
        return try {
            val confirmed = withTimeoutOrNull(SERVER_WAIT_MS) {
                ref.document(momentId).set(data).await()
                true
            }
            onEarlierMonthChanged(coupleId, if (day.take(7) != currentMonth()) 1 else 0, day.take(7))
            if (confirmed == true) SaveResult.Saved else SaveResult.SavedOffline
        } catch (e: CancellationException) {
            throw e
        } catch (e: FirebaseFirestoreException) {
            Log.w(TAG, "Record moment failed", e)
            if (e.code == FirebaseFirestoreException.Code.PERMISSION_DENIED) {
                _status.value = SyncStatus.NOT_ALLOWED
                SaveResult.Failed(NOT_ALLOWED_MESSAGE)
            } else {
                SaveResult.Failed("Couldn't save. Try again.")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Record moment failed", e)
            SaveResult.Failed("Couldn't save. Try again.")
        }
    }

    /**
     * Changes one of my own moments later: the number, when it was seen (its day moves with it) and
     * the note. False if it was refused.
     */
    suspend fun updateMoment(moment: SyncMoment, pattern: String, note: String, seenAt: Long): Boolean {
        val coupleId = coupleId() ?: return false
        if (moment.createdBy != authRepository.getCurrentUserId()) return false
        val clean = SyncPatterns.normalize(pattern) ?: return false
        val ref = moments(coupleId)?.document(moment.id) ?: return false
        val seen = seenAt.coerceAtMost(System.currentTimeMillis())
        val day = dayKey(seen)
        return try {
            withTimeoutOrNull(SERVER_WAIT_MS) {
                ref.update(
                    mapOf(
                        "pattern" to clean,
                        "note" to note.trim().take(SyncPatterns.NOTE_MAX),
                        "seenAt" to seen,
                        "periodDate" to day,
                        "periodMonth" to day.take(7),
                        "editedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
            // It may have moved out of, or into, an earlier month
            val current = currentMonth()
            val oldMonth = moment.periodDate.take(7)
            val newMonth = day.take(7)
            val delta = (if (newMonth != current) 1 else 0) - (if (oldMonth != current) 1 else 0)
            onEarlierMonthChanged(coupleId, delta, oldMonth, newMonth)
            true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Edit note failed", e)
            false
        }
    }

    /** Deletes one of my own moments. False if it was refused. */
    suspend fun deleteMoment(moment: SyncMoment): Boolean {
        val coupleId = coupleId() ?: return false
        if (moment.createdBy != authRepository.getCurrentUserId()) return false
        val ref = moments(coupleId)?.document(moment.id) ?: return false
        return try {
            withTimeoutOrNull(SERVER_WAIT_MS) { ref.delete().await() }
            val oldMonth = moment.periodDate.take(7)
            onEarlierMonthChanged(coupleId, if (oldMonth != currentMonth()) -1 else 0, oldMonth)
            true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Delete moment failed", e)
            false
        }
    }

    /**
     * One month of moments ("yyyy-MM"), newest first, for the history. This month comes from the
     * live view; others are read once each time they're opened. Null if it couldn't be read.
     */
    suspend fun loadMonth(month: String): List<SyncMoment>? {
        if (month == currentMonth() && listenKey != null) return _monthMoments.value
        val coupleId = coupleId() ?: return null
        val ref = moments(coupleId) ?: return null
        ensureMember(coupleId)
        return try {
            val items = ref.whereEqualTo("periodMonth", month).get().await().documents
                .mapNotNull { it.toMoment() }
                .distinctBy { it.id }
                .sortedByDescending { it.seenAt }
            if (month != currentMonth()) olderMonthDays["$coupleId|$month"] = items.mapTo(HashSet()) { it.periodDate }
            items
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Load month $month failed", e)
            null
        }
    }

    /** The month of our first moment ("yyyy-MM"), so the history knows how far back to go. */
    suspend fun firstMonth(): String? {
        val coupleId = coupleId() ?: return null
        val cacheKey = "first_month_$coupleId"
        prefs.getString(cacheKey, null)?.let { return it }
        val ref = moments(coupleId) ?: return null
        ensureMember(coupleId)
        return try {
            val first = ref.orderBy("periodMonth", Query.Direction.ASCENDING).limit(1).get().await()
                .documents.firstOrNull()?.getString("periodMonth")
            // Only kept once there's one: before that, the first moment may still be on its way
            first?.also { prefs.edit().putString(cacheKey, it).apply() }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }
    }

    /**
     * A moment was added to, changed in or removed from [months]: for any earlier than this one, its
     * days are read again when needed, and the count before this month and the streak are redone.
     */
    private suspend fun onEarlierMonthChanged(coupleId: String, olderDelta: Int, vararg months: String) {
        val current = currentMonth()
        val earlier = months.filter { it != current }.distinct()
        if (earlier.isEmpty()) return
        earlier.forEach { olderMonthDays.remove("$coupleId|$it") }
        // A moment dated before our first one: the history now reaches back to it
        val firstKey = "first_month_$coupleId"
        val first = prefs.getString(firstKey, null)
        earlier.minOrNull()?.let { if (first == null || it < first) prefs.edit().putString(firstKey, it).apply() }
        // Shown at once (also offline); the server's count replaces it when it can be read
        _olderCount.value = (_olderCount.value + olderDelta).coerceAtLeast(0)
        refreshOlderCount(coupleId, current)
        recomputeStreak(coupleId, _monthMoments.value)
    }

    /** All moments before this month: one count query (read once per month per app run), kept on the phone. */
    private suspend fun refreshOlderCount(coupleId: String, month: String) {
        val ref = moments(coupleId) ?: return
        try {
            val count = ref.whereLessThan("periodMonth", month).count().get(AggregateSource.SERVER).await().count.toInt()
            if ("$coupleId|$month" == listenKey || listenKey == null) {
                _olderCount.value = count
            }
            prefs.edit().putInt(olderCountKey(coupleId, month), count).apply()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Offline: the last known count stays
            Log.w(TAG, "Count earlier moments failed", e)
        }
    }

    private fun recomputeStreak(coupleId: String, monthItems: List<SyncMoment>) {
        streakJob?.cancel()
        streakJob = scope.launch {
            val month = currentMonth()
            val thisMonthDays = monthItems.mapTo(HashSet()) { it.periodDate }
            val today = dayKey()
            var day = if (today in thisMonthDays) today else previousDay(today)
            var streak = 0
            while (streak < MAX_STREAK_DAYS) {
                val dayMonth = day.take(7)
                val days = if (dayMonth == month) thisMonthDays else olderDays(coupleId, dayMonth) ?: break
                if (day !in days) break
                streak++
                day = previousDay(day)
            }
            _streak.value = streak
        }
    }

    private suspend fun olderDays(coupleId: String, month: String): Set<String>? {
        val key = "$coupleId|$month"
        olderMonthDays[key]?.let { return it }
        val ref = moments(coupleId) ?: return null
        return try {
            val days = ref.whereEqualTo("periodMonth", month).get().await().documents
                .mapNotNullTo(HashSet()) { it.getString("periodDate") }
            olderMonthDays[key] = days
            days
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }
    }

    /**
     * The rules only let the couple's own sign-ins at these moments: this phone's sign-in is added
     * to the couple's member list once (its id, nothing else).
     */
    private suspend fun ensureMember(coupleId: String): Boolean {
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
        val key = "$coupleId|$uid"
        if (key in confirmedMembers || prefs.getBoolean("member_$key", false)) {
            confirmedMembers += key
            return true
        }
        val coupleRef = firestore?.collection("couples")?.document(coupleId) ?: return false
        return try {
            val done = withTimeoutOrNull(SERVER_WAIT_MS) {
                coupleRef.set(mapOf("memberUids" to FieldValue.arrayUnion(uid)), SetOptions.merge()).await()
                true
            } == true
            if (done) {
                confirmedMembers += key
                prefs.edit().putBoolean("member_$key", true).apply()
            }
            done
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Couldn't add this sign-in to the couple", e)
            false
        }
    }

    private fun DocumentSnapshot.toMoment(): SyncMoment? {
        val pattern = getString("pattern")?.ifBlank { null } ?: return null
        val periodDate = getString("periodDate")?.ifBlank { null } ?: return null
        return SyncMoment(
            id = id,
            pattern = pattern,
            note = getString("note").orEmpty(),
            createdBy = getString("createdBy").orEmpty(),
            seenAt = getLong("seenAt") ?: getLong("clientCreatedAt") ?: getTimestamp("createdAt")?.toDate()?.time ?: 0L,
            periodDate = periodDate,
            isPending = metadata.hasPendingWrites()
        )
    }

    private fun olderCountKey(coupleId: String, month: String) = "older_${coupleId}_$month"

    companion object {
        private const val TAG = "SynchronicityRepo"
        const val COLLECTION = "synchronicity"
        private const val DEFAULT_COUPLE_ID = "couple_faisal_shali"
        private const val SERVER_WAIT_MS = 6_000L
        private const val MAX_STREAK_DAYS = 3_660
        private const val DAY_START_MS = 6L * 60 * 60 * 1000
        const val NOT_ALLOWED_MESSAGE = "Syncing isn't switched on yet. The Firebase rules need a one-time update."

        /** The 6 AM day ("yyyy-MM-dd") in this phone's time zone, like the chat's "Today". */
        fun dayKey(now: Long = System.currentTimeMillis()): String =
            SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(now - DAY_START_MS))

        fun currentMonth(): String = dayKey().take(7)

        fun previousDay(day: String): String {
            val format = SimpleDateFormat("yyyy-MM-dd", Locale.US)
            val cal = Calendar.getInstance().apply {
                time = format.parse(day) ?: Date()
                add(Calendar.DAY_OF_MONTH, -1)
            }
            return format.format(cal.time)
        }
    }
}

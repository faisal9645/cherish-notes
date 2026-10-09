package com.example.ui.synchronicity

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.SyncMoment
import com.example.data.model.SyncPatterns
import com.example.data.model.SyncStatus
import com.example.data.repository.AuthRepository
import com.example.data.repository.SynchronicityRepository
import com.example.security.SecurityPreferences
import com.example.ui.chat.ChatExperienceMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.util.UUID

/** Everything the Love Synchronicity card and history show, worked out from the stored moments. */
data class SyncUiState(
    val status: SyncStatus = SyncStatus.LOADING,
    /** The defaults and then our own numbers. */
    val patterns: List<String> = SyncPatterns.defaults,
    val customPatterns: List<String> = emptyList(),
    val today: String = "",
    val todayCount: Int = 0,
    val monthCount: Int = 0,
    val allTimeCount: Int = 0,
    val streak: Int = 0,
    /** This month's count for each number. */
    val monthByPattern: Map<String, Int> = emptyMap(),
    /** This month's moments, newest first. */
    val monthMoments: List<SyncMoment> = emptyList(),
    val myId: String = ""
)

class SynchronicityViewModel(
    private val repository: SynchronicityRepository,
    private val authRepository: AuthRepository,
    private val securityPreferences: SecurityPreferences
) : ViewModel() {

    private val today = MutableStateFlow(SynchronicityRepository.dayKey())

    private val counts = combine(
        repository.monthMoments,
        repository.olderCount,
        repository.streak,
        repository.status,
        today
    ) { month, older, streak, status, day ->
        SyncUiState(
            status = status,
            today = day,
            todayCount = month.count { it.periodDate == day },
            monthCount = month.size,
            allTimeCount = older + month.size,
            streak = streak,
            monthByPattern = month.groupingBy { it.pattern }.eachCount(),
            monthMoments = month
        )
    }

    val uiState: StateFlow<SyncUiState> = combine(
        counts,
        authRepository.syncPatterns,
        authRepository.currentUserState
    ) { state, custom, user ->
        val ours = custom.filter { it !in SyncPatterns.defaults }
        state.copy(
            patterns = SyncPatterns.defaults + ours,
            customPatterns = ours,
            myId = user?.id.orEmpty()
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SyncUiState())

    /** Love & Us (or the history) is on screen: make sure this month is being followed. */
    fun onScreenVisible() {
        repository.start()
        onTick()
    }

    /** Every minute while on screen: a new 6 AM day (or month) updates the counts. */
    fun onTick() {
        val day = SynchronicityRepository.dayKey()
        if (day != today.value) {
            today.value = day
            repository.onDayChanged()
        }
    }

    /** A new id for one "Record" sheet (kept while it's open, so a retry can't make a second moment). */
    fun newMomentId(): String = UUID.randomUUID().toString()

    suspend fun record(momentId: String, pattern: String, note: String, seenAt: Long) =
        repository.recordMoment(momentId, pattern, note, seenAt)

    suspend fun update(moment: SyncMoment, pattern: String, note: String, seenAt: Long) =
        repository.updateMoment(moment, pattern, note, seenAt)

    suspend fun delete(moment: SyncMoment) = repository.deleteMoment(moment)

    suspend fun loadMonth(month: String) = repository.loadMonth(month)

    suspend fun firstMonth() = repository.firstMonth()

    /** Adds one of our own numbers; returns what's wrong with it, or null when it was added. */
    fun addPattern(input: String): String? {
        val clean = SyncPatterns.normalize(input) ?: return "Use 2 to 6 digits (like 1234) or a time (like 07:07)"
        val current = uiState.value
        if (clean in current.patterns) return "$clean is already there"
        if (current.customPatterns.size >= SyncPatterns.MAX_CUSTOM) return "You can keep up to ${SyncPatterns.MAX_CUSTOM} of your own numbers"
        authRepository.addSyncPattern(clean)
        return null
    }

    fun removePattern(pattern: String) = authRepository.removeSyncPattern(pattern)

    /** In Private Mode the partner's name isn't shown; they're "Partner". */
    fun isPrivateMode(): Boolean = securityPreferences.getChatExperienceMode() == ChatExperienceMode.PRIVATE
}

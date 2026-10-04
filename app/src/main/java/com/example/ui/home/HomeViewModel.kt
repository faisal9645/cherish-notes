package com.example.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.BucketListItem
import com.example.data.model.DailyQuestion
import com.example.data.model.LoveJarNote
import com.example.data.model.Message
import com.example.data.model.User
import com.example.data.repository.AuthRepository
import com.example.data.repository.ChatRepository
import com.example.data.repository.CoupleFeaturesRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class HomeUiState(
    val currentUser: User? = null,
    val partnerUser: User? = null,
    val lastMessage: Message? = null,
    val unreadCount: Int = 0,
    val daysTogether: Long = 280L,
    val isPartnerTyping: Boolean = false,
    val isPartnerOnline: Boolean = false,
    val dailyQuestion: DailyQuestion = DailyQuestion(),
    val loveJarNotes: List<LoveJarNote> = emptyList(),
    val bucketList: List<BucketListItem> = emptyList(),
    val lastLoveNudgeSent: String? = null
)

class HomeViewModel(
    private val authRepository: AuthRepository,
    private val chatRepository: ChatRepository,
    private val coupleFeaturesRepository: CoupleFeaturesRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            authRepository.currentUserState.collect { user ->
                _uiState.update { it.copy(currentUser = user) }
            }
        }

        viewModelScope.launch {
            authRepository.partnerUserState.collect { partner ->
                _uiState.update {
                    it.copy(
                        partnerUser = partner,
                        isPartnerOnline = partner?.isEffectivelyOnline() ?: false,
                        isPartnerTyping = partner?.isEffectivelyTyping() ?: false
                    )
                }
            }
        }

        viewModelScope.launch {
            chatRepository.messagesFlow.collect { messages ->
                val currentUserId = authRepository.getCurrentUserId()
                val last = messages.lastOrNull { !it.isDeleted }
                val unread = messages.count {
                    it.receiverId == currentUserId && it.getTypedStatus() != com.example.data.model.MessageStatus.READ
                }
                _uiState.update {
                    it.copy(
                        lastMessage = last,
                        unreadCount = unread
                    )
                }
            }
        }

        viewModelScope.launch {
            coupleFeaturesRepository.dailyQuestionFlow.collect { dq ->
                _uiState.update { it.copy(dailyQuestion = dq) }
            }
        }

        viewModelScope.launch {
            coupleFeaturesRepository.loveJarNotesFlow.collect { notes ->
                _uiState.update { it.copy(loveJarNotes = notes) }
            }
        }

        viewModelScope.launch {
            coupleFeaturesRepository.bucketListFlow.collect { items ->
                _uiState.update { it.copy(bucketList = items) }
            }
        }

        viewModelScope.launch {
            while (true) {
                kotlinx.coroutines.delay(1000L)
                _uiState.update { state ->
                    val partner = state.partnerUser
                    state.copy(
                        isPartnerOnline = partner?.isEffectivelyOnline() ?: false,
                        isPartnerTyping = partner?.isEffectivelyTyping() ?: false
                    )
                }
            }
        }
    }

    fun submitDailyAnswer(answer: String) {
        coupleFeaturesRepository.submitMyDailyAnswer(answer)
    }

    fun toggleLikeDailyAnswer() {
        coupleFeaturesRepository.toggleLikeDailyAnswer()
    }

    fun addLoveJarNote(text: String, emoji: String) {
        coupleFeaturesRepository.addLoveJarNote(text, emoji)
    }

    fun toggleBucketItem(id: String) {
        coupleFeaturesRepository.toggleBucketItem(id)
    }

    fun addBucketItem(title: String, category: String) {
        coupleFeaturesRepository.addBucketItem(title, category)
    }

    fun sendLoveNudge(nudgeName: String, emoji: String, defaultMessage: String) {
        viewModelScope.launch {
            chatRepository.sendMessage(text = "$emoji $defaultMessage")
            _uiState.update { it.copy(lastLoveNudgeSent = "Sent $nudgeName $emoji") }
        }
    }

    fun clearLoveNudgeStatus() {
        _uiState.update { it.copy(lastLoveNudgeSent = null) }
    }

    fun formatLastSeen(timestamp: Long, isOnline: Boolean): String {
        if (isOnline) return "Online right now ❤️"
        if (timestamp <= 0L) return "Offline"

        val diff = System.currentTimeMillis() - timestamp
        val minutes = diff / (1000 * 60)
        val hours = minutes / 60
        val days = hours / 24

        return when {
            minutes < 2 -> "Just stepped away"
            minutes < 60 -> "Active $minutes min ago"
            hours < 24 -> "Active $hours hr ago"
            days < 7 -> "Active $days days ago"
            else -> {
                val sdf = SimpleDateFormat("MMM d, h:mm a", Locale.getDefault())
                "Last seen ${sdf.format(Date(timestamp))}"
            }
        }
    }

    fun formatMessageTime(timestamp: Long): String {
        val sdf = SimpleDateFormat("h:mm a", Locale.getDefault())
        return sdf.format(Date(timestamp))
    }
}

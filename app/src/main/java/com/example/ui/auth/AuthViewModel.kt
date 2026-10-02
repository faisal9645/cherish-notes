package com.example.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.User
import com.example.data.repository.AuthRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed class AuthUiState {
    object Idle : AuthUiState()
    object Loading : AuthUiState()
    data class Success(val user: User) : AuthUiState()
    data class Error(val message: String) : AuthUiState()
}

class AuthViewModel(private val authRepository: AuthRepository) : ViewModel() {

    private val _uiState = MutableStateFlow<AuthUiState>(AuthUiState.Idle)
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    /**
     * The only login flow:
     *   Username + Partner Name + Couple Secret Code → Firebase pairing
     *   Partner A waits → Partner B enters matching details → auto-connect
     */
    fun loginCouple(
        username: String,
        partnerName: String,
        coupleSecretCode: String
    ) {
        val cleanUser = username.trim()
        val cleanPartner = partnerName.trim()
        val cleanCode = coupleSecretCode.trim()
        if (cleanUser.isBlank()) {
            _uiState.value = AuthUiState.Error("Please enter your username")
            return
        }
        if (cleanPartner.isBlank()) {
            _uiState.value = AuthUiState.Error("Please enter your partner's name")
            return
        }
        if (cleanCode.isBlank()) {
            _uiState.value = AuthUiState.Error("Please enter the Couple Secret Code")
            return
        }
        viewModelScope.launch {
            _uiState.value = AuthUiState.Loading
            val result = authRepository.loginWithCoupleCredentials(
                username = cleanUser,
                partnerName = cleanPartner,
                secretCode = cleanCode
            )
            result.fold(
                onSuccess = { _uiState.value = AuthUiState.Success(it) },
                onFailure = { _uiState.value = AuthUiState.Error(it.localizedMessage ?: "Login failed") }
            )
        }
    }

    fun resetState() {
        _uiState.value = AuthUiState.Idle
    }
}

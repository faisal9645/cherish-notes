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

    fun login(
        username: String,
        pass: String,
        partnerUsername: String = "",
        coupleKey: String = ""
    ) {
        val cleanUser = username.trim()
        val cleanPass = pass.trim()
        if (cleanUser.isBlank() || cleanPass.isBlank()) {
            _uiState.value = AuthUiState.Error("Please enter your username and password")
            return
        }
        if (cleanPass.length < 4) {
            _uiState.value = AuthUiState.Error("Password must be at least 4 characters")
            return
        }
        viewModelScope.launch {
            _uiState.value = AuthUiState.Loading
            val result = authRepository.loginWithUsernameAndPassword(
                username = cleanUser,
                pass = cleanPass,
                partnerUsername = partnerUsername.trim(),
                coupleKey = coupleKey.trim()
            )
            result.fold(
                onSuccess = { _uiState.value = AuthUiState.Success(it) },
                onFailure = { _uiState.value = AuthUiState.Error(it.localizedMessage ?: "Login failed") }
            )
        }
    }

    fun continueOffline(
        username: String,
        partnerUsername: String = "",
        coupleKey: String = ""
    ) {
        val cleanUser = username.trim().ifBlank { "me" }
        val user = authRepository.loginOffline(
            username = cleanUser,
            partnerUsername = partnerUsername.trim(),
            coupleKey = coupleKey.trim()
        )
        _uiState.value = AuthUiState.Success(user)
    }

    fun loginWithGoogle(
        idToken: String,
        partnerUsernameOrEmail: String = "",
        coupleKey: String = ""
    ) {
        viewModelScope.launch {
            _uiState.value = AuthUiState.Loading
            val result = authRepository.loginWithGoogleIdToken(
                idToken = idToken,
                partnerUsernameOrEmail = partnerUsernameOrEmail.trim(),
                coupleKey = coupleKey.trim()
            )
            result.fold(
                onSuccess = { _uiState.value = AuthUiState.Success(it) },
                onFailure = { _uiState.value = AuthUiState.Error(it.localizedMessage ?: "Google sign-in failed") }
            )
        }
    }

    fun sendPasswordReset(email: String, onDone: (Boolean, String) -> Unit) {
        if (email.isBlank()) {
            onDone(false, "Please enter your username")
            return
        }
        viewModelScope.launch {
            val res = authRepository.sendPasswordReset(email)
            res.fold(
                onSuccess = { onDone(true, "Reset link sent!") },
                onFailure = { onDone(false, it.localizedMessage ?: "Failed to reset") }
            )
        }
    }

    fun resetState() {
        _uiState.value = AuthUiState.Idle
    }
}

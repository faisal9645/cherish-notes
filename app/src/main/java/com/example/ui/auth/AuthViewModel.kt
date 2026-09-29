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

    fun login(email: String, pass: String) {
        if (email.isBlank() || pass.isBlank()) {
            _uiState.value = AuthUiState.Error("Please enter both email and password")
            return
        }
        viewModelScope.launch {
            _uiState.value = AuthUiState.Loading
            val result = authRepository.loginWithEmail(email, pass)
            result.fold(
                onSuccess = { _uiState.value = AuthUiState.Success(it) },
                onFailure = { _uiState.value = AuthUiState.Error(it.localizedMessage ?: "Login failed") }
            )
        }
    }

    fun register(email: String, pass: String, name: String, partnerEmail: String, coupleKey: String) {
        if (email.isBlank() || pass.isBlank()) {
            _uiState.value = AuthUiState.Error("Please provide all required fields")
            return
        }
        if (pass.length < 6) {
            _uiState.value = AuthUiState.Error("Password must be at least 6 characters")
            return
        }
        viewModelScope.launch {
            _uiState.value = AuthUiState.Loading
            val regResult = authRepository.registerWithEmail(email, pass, name)
            regResult.fold(
                onSuccess = { user ->
                    if (partnerEmail.isNotBlank() || coupleKey.isNotBlank()) {
                        authRepository.pairWithPartner(partnerEmail, coupleKey.ifBlank { "CHERISH-FOREVER" })
                    }
                    _uiState.value = AuthUiState.Success(user)
                },
                onFailure = {
                    _uiState.value = AuthUiState.Error(it.localizedMessage ?: "Registration failed")
                }
            )
        }
    }

    fun loginWithGmail(gmail: String, partnerGmail: String, coupleKey: String, pass: String = "gmail_secure_couple") {
        val cleanGmail = gmail.trim().lowercase().let { if (!it.contains("@")) "$it@gmail.com" else it }
        val cleanPartner = partnerGmail.trim().lowercase().let { if (it.isNotBlank() && !it.contains("@")) "$it@gmail.com" else it }

        if (!cleanGmail.endsWith("@gmail.com") && !cleanGmail.endsWith("@googlemail.com")) {
            _uiState.value = AuthUiState.Error("Please use a valid Gmail address (must end with @gmail.com)")
            return
        }

        if (cleanPartner.isNotBlank() && !cleanPartner.endsWith("@gmail.com") && !cleanPartner.endsWith("@googlemail.com")) {
            _uiState.value = AuthUiState.Error("Partner must also use a valid Gmail address (must end with @gmail.com)")
            return
        }

        viewModelScope.launch {
            _uiState.value = AuthUiState.Loading
            val loginRes = authRepository.loginWithEmail(cleanGmail, pass)
            loginRes.fold(
                onSuccess = { user ->
                    if (cleanPartner.isNotBlank()) {
                        authRepository.pairWithPartner(cleanPartner, coupleKey.ifBlank { "CHERISH-FOREVER" })
                    }
                    _uiState.value = AuthUiState.Success(user)
                },
                onFailure = {
                    val regRes = authRepository.registerWithEmail(
                        email = cleanGmail,
                        pass = pass,
                        displayName = cleanGmail.substringBefore("@").replaceFirstChar { it.uppercase() }
                    )
                    regRes.fold(
                        onSuccess = { user ->
                            if (cleanPartner.isNotBlank()) {
                                authRepository.pairWithPartner(cleanPartner, coupleKey.ifBlank { "CHERISH-FOREVER" })
                            }
                            _uiState.value = AuthUiState.Success(user)
                        },
                        onFailure = { err ->
                            _uiState.value = AuthUiState.Error(err.localizedMessage ?: "Gmail authentication failed")
                        }
                    )
                }
            )
        }
    }

    fun sendPasswordReset(email: String, onDone: (Boolean, String) -> Unit) {
        if (email.isBlank()) {
            onDone(false, "Please enter your email address")
            return
        }
        viewModelScope.launch {
            val res = authRepository.sendPasswordReset(email)
            res.fold(
                onSuccess = { onDone(true, "Reset link sent! Please check your inbox.") },
                onFailure = { onDone(false, it.localizedMessage ?: "Failed to send reset email") }
            )
        }
    }

    fun resetState() {
        _uiState.value = AuthUiState.Idle
    }
}

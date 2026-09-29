package com.example.ui.profile

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.MessageType
import com.example.data.model.User
import com.example.data.repository.AuthRepository
import com.example.data.repository.MediaRepository
import com.example.security.SecurityPreferences
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class ProfileUiState(
    val currentUser: User? = null,
    val partnerUser: User? = null,
    val isAppLockEnabled: Boolean = false,
    val isBiometricEnabled: Boolean = false,
    val isScreenshotProtectionEnabled: Boolean = false,
    val isHideNotificationContent: Boolean = true,
    val coupleKey: String = "",
    val isDisguiseModeEnabled: Boolean = true,
    val disguisePasscode: String = "love",
    val isUpdating: Boolean = false
)

class ProfileViewModel(
    private val authRepository: AuthRepository,
    private val mediaRepository: MediaRepository,
    private val securityPreferences: SecurityPreferences
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        ProfileUiState(
            isAppLockEnabled = securityPreferences.isAppLockEnabled(),
            isBiometricEnabled = securityPreferences.isBiometricEnabled(),
            isScreenshotProtectionEnabled = securityPreferences.isScreenshotProtectionEnabled(),
            isHideNotificationContent = securityPreferences.isHideNotificationContent(),
            coupleKey = securityPreferences.getCoupleSecretKey(),
            isDisguiseModeEnabled = securityPreferences.isDisguiseModeEnabled(),
            disguisePasscode = securityPreferences.getDisguisePasscode()
        )
    )
    val uiState: StateFlow<ProfileUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            authRepository.currentUserState.collect { user ->
                _uiState.update { it.copy(currentUser = user) }
            }
        }

        viewModelScope.launch {
            authRepository.partnerUserState.collect { partner ->
                _uiState.update { it.copy(partnerUser = partner) }
            }
        }
    }

    fun updateProfile(displayName: String, status: String, newPhotoUri: Uri?) {
        viewModelScope.launch {
            _uiState.update { it.copy(isUpdating = true) }
            var photoUrl: String? = null
            if (newPhotoUri != null) {
                try {
                    val comp = mediaRepository.compressAndPrepareImage(newPhotoUri)
                    val upload = mediaRepository.uploadFile(comp, MessageType.IMAGE, "avatars")
                    photoUrl = upload.getOrNull()
                } catch (e: Exception) {
                    // ignore
                }
            }
            authRepository.updateProfile(displayName, status, photoUrl)
            _uiState.update { it.copy(isUpdating = false) }
        }
    }

    fun setAppLock(enabled: Boolean, pin: String? = null) {
        if (pin != null && pin.length == 4) {
            securityPreferences.setPin(pin)
        }
        securityPreferences.setAppLockEnabled(enabled)
        _uiState.update { it.copy(isAppLockEnabled = enabled) }
    }

    fun setBiometric(enabled: Boolean) {
        securityPreferences.setBiometricEnabled(enabled)
        _uiState.update { it.copy(isBiometricEnabled = enabled) }
    }

    fun setScreenshotProtection(enabled: Boolean, onApplyWindowFlag: (Boolean) -> Unit) {
        securityPreferences.setScreenshotProtectionEnabled(enabled)
        onApplyWindowFlag(enabled)
        _uiState.update { it.copy(isScreenshotProtectionEnabled = enabled) }
    }

    fun setHideNotificationContent(hide: Boolean) {
        securityPreferences.setHideNotificationContent(hide)
        _uiState.update { it.copy(isHideNotificationContent = hide) }
    }

    fun setDisguiseMode(enabled: Boolean) {
        securityPreferences.setDisguiseModeEnabled(enabled)
        _uiState.update { it.copy(isDisguiseModeEnabled = enabled) }
    }

    fun setDisguisePasscode(passcode: String) {
        securityPreferences.setDisguisePasscode(passcode)
        _uiState.update { it.copy(disguisePasscode = passcode) }
    }

    fun triggerInstantDisguise() {
        securityPreferences.reDisguise()
    }

    fun updatePartnerEmailAndKey(partnerEmail: String, coupleKey: String) {
        viewModelScope.launch {
            authRepository.pairWithPartner(partnerEmail, coupleKey)
            _uiState.update { it.copy(coupleKey = coupleKey) }
        }
    }

    fun logout() {
        authRepository.logout()
    }
}

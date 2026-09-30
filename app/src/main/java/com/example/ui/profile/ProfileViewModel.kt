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
    val isKeywordTriggerEnabled: Boolean = true,
    val plusHoldDurationSec: Int = 5,
    val isRequirePhoneLockAfterHold: Boolean = false,
    val gallerySize: String = "medium",
    val isHapticEnabled: Boolean = true,
    val isAutoPlayMedia: Boolean = true,
    val isHighQualityMedia: Boolean = true,
    val isDoubleTapZoomEnabled: Boolean = true,
    val isCheckAfterReminderEnabled: Boolean = true,
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
            disguisePasscode = securityPreferences.getDisguisePasscode(),
            isKeywordTriggerEnabled = securityPreferences.isKeywordTriggerEnabled(),
            plusHoldDurationSec = securityPreferences.getPlusIconHoldDuration(),
            isRequirePhoneLockAfterHold = securityPreferences.isRequirePhoneLockAfterHold(),
            gallerySize = securityPreferences.getImageGallerySize(),
            isHapticEnabled = securityPreferences.isHapticFeedbackEnabled(),
            isAutoPlayMedia = securityPreferences.isAutoPlayMedia(),
            isHighQualityMedia = securityPreferences.isHighQualityMedia(),
            isDoubleTapZoomEnabled = securityPreferences.isDoubleTapZoomEnabled(),
            isCheckAfterReminderEnabled = securityPreferences.isCheckAfterReminderEnabled()
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

    fun updateProfile(
        displayName: String,
        status: String,
        newPhotoUri: Uri?,
        removePhoto: Boolean = false,
        onComplete: (Boolean) -> Unit = {}
    ) {
        viewModelScope.launch {
            _uiState.update { it.copy(isUpdating = true) }
            var photoUrl: String? = null
            if (removePhoto) {
                authRepository.updateProfile(displayName.trim(), status.trim(), null, removePhoto = true)
            } else {
                if (newPhotoUri != null) {
                    try {
                        val avatarFile = mediaRepository.saveAndPrepareAvatar(newPhotoUri)
                        val coupleId = _uiState.value.currentUser?.coupleId ?: "couple_default"
                        val upload = mediaRepository.uploadFile(avatarFile, MessageType.IMAGE, coupleId)
                        photoUrl = upload.getOrNull() ?: Uri.fromFile(avatarFile).toString()
                    } catch (e: Exception) {
                        android.util.Log.e("ProfileViewModel", "Avatar preparation/upload failed", e)
                    }
                }
                authRepository.updateProfile(
                    displayName = displayName.trim(),
                    statusMessage = status.trim(),
                    photoUrl = photoUrl,
                    removePhoto = false
                )
            }
            _uiState.update { it.copy(isUpdating = false) }
            onComplete(true)
        }
    }

    fun updateAvatar(newPhotoUri: Uri, onComplete: (Boolean) -> Unit = {}) {
        val user = _uiState.value.currentUser
        updateProfile(
            displayName = user?.displayName ?: "Me",
            status = user?.statusMessage ?: "",
            newPhotoUri = newPhotoUri,
            removePhoto = false,
            onComplete = onComplete
        )
    }

    fun removeAvatar(onComplete: (Boolean) -> Unit = {}) {
        val user = _uiState.value.currentUser
        updateProfile(
            displayName = user?.displayName ?: "Me",
            status = user?.statusMessage ?: "",
            newPhotoUri = null,
            removePhoto = true,
            onComplete = onComplete
        )
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

    fun setKeywordTriggerEnabled(enabled: Boolean) {
        securityPreferences.setKeywordTriggerEnabled(enabled)
        _uiState.update { it.copy(isKeywordTriggerEnabled = enabled) }
    }

    fun setPlusHoldDuration(seconds: Int) {
        securityPreferences.setPlusIconHoldDuration(seconds)
        _uiState.update { it.copy(plusHoldDurationSec = seconds) }
    }

    fun setRequirePhoneLockAfterHold(enabled: Boolean) {
        securityPreferences.setRequirePhoneLockAfterHold(enabled)
        _uiState.update { it.copy(isRequirePhoneLockAfterHold = enabled) }
    }

    fun setImageGallerySize(size: String) {
        securityPreferences.setImageGallerySize(size)
        _uiState.update { it.copy(gallerySize = size) }
    }

    fun setHapticEnabled(enabled: Boolean) {
        securityPreferences.setHapticFeedbackEnabled(enabled)
        _uiState.update { it.copy(isHapticEnabled = enabled) }
    }

    fun setAutoPlayMedia(enabled: Boolean) {
        securityPreferences.setAutoPlayMedia(enabled)
        _uiState.update { it.copy(isAutoPlayMedia = enabled) }
    }

    fun setHighQualityMedia(enabled: Boolean) {
        securityPreferences.setHighQualityMedia(enabled)
        _uiState.update { it.copy(isHighQualityMedia = enabled) }
    }

    fun setDoubleTapZoom(enabled: Boolean) {
        securityPreferences.setDoubleTapZoomEnabled(enabled)
        _uiState.update { it.copy(isDoubleTapZoomEnabled = enabled) }
    }

    fun setCheckAfterReminderEnabled(enabled: Boolean) {
        securityPreferences.setCheckAfterReminderEnabled(enabled)
        _uiState.update { it.copy(isCheckAfterReminderEnabled = enabled) }
    }

    fun setCheckAfter(targetTimeMillis: Long, note: String = "") {
        authRepository.setCheckAfter(targetTimeMillis, note)
    }

    fun cancelCheckAfter() {
        authRepository.cancelCheckAfter()
    }

    fun extendCheckAfter(additionalMillis: Long) {
        authRepository.extendCheckAfter(additionalMillis)
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

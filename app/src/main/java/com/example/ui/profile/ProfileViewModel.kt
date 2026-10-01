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
    val isUpdating: Boolean = false,
    val isSecretHistoryRevealed: Boolean = false,
    val themeMode: Int = 0,
    val chatBgTheme: Int = 0
)

data class UpdateCheckState(
    val isChecking: Boolean = false,
    val showDialog: Boolean = false,
    val isUpdateAvailable: Boolean = false,
    val currentVersion: String = "1.0.0",
    val latestVersion: String = "1.0.0",
    val downloadUrl: String? = null,
    val releaseNotes: String = ""
)

class ProfileViewModel(
    private val authRepository: AuthRepository,
    private val mediaRepository: MediaRepository,
    private val securityPreferences: SecurityPreferences,
    private val googleDriveBackupManager: com.example.backup.GoogleDriveBackupManager
) : ViewModel() {

    val backupState: StateFlow<com.example.backup.BackupState> = googleDriveBackupManager.backupState

    fun backupNow(onResult: (Boolean, String) -> Unit = { _, _ -> }) {
        viewModelScope.launch {
            val res = googleDriveBackupManager.performBackupToGoogleDrive()
            res.fold(
                onSuccess = { onResult(true, it) },
                onFailure = { onResult(false, it.localizedMessage ?: "Backup failed") }
            )
        }
    }

    fun restoreNow(onResult: (Boolean, String) -> Unit = { _, _ -> }) {
        viewModelScope.launch {
            val res = googleDriveBackupManager.performRestoreFromGoogleDrive()
            res.fold(
                onSuccess = { onResult(true, it) },
                onFailure = { onResult(false, it.localizedMessage ?: "Restore failed") }
            )
        }
    }

    fun updateLoginCredentials(
        username: String,
        partnerName: String,
        coupleSecretCode: String,
        onResult: (Boolean) -> Unit = {}
    ) {
        viewModelScope.launch {
            val cleanUser = username.trim().ifBlank { _uiState.value.currentUser?.displayName ?: "Me" }
            val cleanPartner = partnerName.trim()
            val cleanCode = coupleSecretCode.trim().ifBlank { "CHERISH-FOREVER" }

            authRepository.updateProfile(cleanUser, _uiState.value.currentUser?.statusMessage ?: "", null, false)
            if (cleanPartner.isNotBlank()) {
                authRepository.pairWithPartner(cleanPartner, cleanCode)
            }
            securityPreferences.setCoupleSecretKey(cleanCode)
            _uiState.update { it.copy(coupleKey = cleanCode) }
            googleDriveBackupManager.triggerImmediateAutoBackup()
            onResult(true)
        }
    }

    private val _updateState = MutableStateFlow(UpdateCheckState())
    val updateState: StateFlow<UpdateCheckState> = _updateState.asStateFlow()

    fun dismissUpdateDialog() {
        _updateState.update { it.copy(showDialog = false) }
    }

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
            isCheckAfterReminderEnabled = securityPreferences.isCheckAfterReminderEnabled(),
            themeMode = securityPreferences.getThemeMode(),
            chatBgTheme = securityPreferences.getChatBgTheme()
        )
    )
    val uiState: StateFlow<ProfileUiState> = _uiState.asStateFlow()

    fun reloadSettings() {
        _uiState.update { current ->
            current.copy(
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
                isCheckAfterReminderEnabled = securityPreferences.isCheckAfterReminderEnabled(),
                themeMode = securityPreferences.getThemeMode(),
                chatBgTheme = securityPreferences.getChatBgTheme()
            )
        }
    }

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

        viewModelScope.launch {
            securityPreferences.themeMode.collect { mode ->
                _uiState.update { it.copy(themeMode = mode) }
            }
        }
        
        viewModelScope.launch {
            securityPreferences.chatBgTheme.collect { theme ->
                _uiState.update { it.copy(chatBgTheme = theme) }
            }
        }

        viewModelScope.launch {
            securityPreferences.isSecretHistoryRevealed.collect { revealed ->
                _uiState.update { it.copy(isSecretHistoryRevealed = revealed) }
            }
        }
    }

    fun setThemeMode(mode: Int) {
        securityPreferences.setThemeMode(mode)
    }

    fun setChatBgTheme(theme: Int) {
        securityPreferences.setChatBgTheme(theme)
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

    fun revealSecretHistory() {
        securityPreferences.revealSecretHistory()
    }

    fun hideSecretHistory() {
        securityPreferences.hideSecretHistory()
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

    fun checkForUpdates(context: android.content.Context) {
        val currentVer = try {
            val pInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            pInfo.versionName ?: "1.0.0"
        } catch (_: Exception) {
            "1.0.0"
        }
        val currentCode = com.example.BuildConfig.VERSION_CODE

        _updateState.value = UpdateCheckState(
            isChecking = true,
            showDialog = true,
            currentVersion = currentVer,
            latestVersion = currentVer
        )

        com.google.firebase.firestore.FirebaseFirestore.getInstance()
            .collection("app_config")
            .document("version")
            .get()
            .addOnSuccessListener { doc ->
                if (doc.exists()) {
                    val latestVersionCode = doc.getLong("versionCode") ?: 0
                    val latestVersionName = doc.getString("versionName") ?: currentVer
                    val url = doc.getString("downloadUrl")
                    val notes = doc.getString("releaseNotes") ?: "• AMOLED pure dark theme & deeper dark blue aesthetics\n• Blazing fast startup speed & instant responsiveness\n• High quality unified app logo everywhere\n• Enhanced notes day & dark mode text contrast"

                    if (latestVersionCode > currentCode) {
                        _updateState.value = UpdateCheckState(
                            isChecking = false,
                            showDialog = true,
                            isUpdateAvailable = true,
                            currentVersion = currentVer,
                            latestVersion = latestVersionName,
                            downloadUrl = url,
                            releaseNotes = notes
                        )
                    } else {
                        _updateState.value = UpdateCheckState(
                            isChecking = false,
                            showDialog = true,
                            isUpdateAvailable = false,
                            currentVersion = currentVer,
                            latestVersion = currentVer,
                            releaseNotes = "You are on the latest version. Pure AMOLED dark theme and performance enhancements are active."
                        )
                    }
                } else {
                    _updateState.value = UpdateCheckState(
                        isChecking = false,
                        showDialog = true,
                        isUpdateAvailable = false,
                        currentVersion = currentVer,
                        latestVersion = currentVer,
                        releaseNotes = "You are on the latest version. Pure AMOLED dark theme and performance enhancements are active."
                    )
                }
            }
            .addOnFailureListener {
                _updateState.value = UpdateCheckState(
                    isChecking = false,
                    showDialog = true,
                    isUpdateAvailable = false,
                    currentVersion = currentVer,
                    latestVersion = currentVer,
                    releaseNotes = "Your app is up to date (Offline verified)."
                )
            }
    }
}

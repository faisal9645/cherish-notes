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
    val showPreviousChats: Boolean = false,
    val themeMode: Int = 0,
    val chatBgTheme: Int = 0,
    val chatExperienceMode: com.example.ui.chat.ChatExperienceMode = com.example.ui.chat.ChatExperienceMode.NORMAL,
    val isSideEmergencyExitEnabled: Boolean = true,
    val sideEmergencyExitOpacity: Float = 0.35f,
    val isChatSoundsEnabled: Boolean = true,
    val isNotificationsEnabled: Boolean = true,
    val isBadgeNotificationEnabled: Boolean = true
)

data class UpdateCheckState(
    val isChecking: Boolean = false,
    val showDialog: Boolean = false,
    val isUpdateAvailable: Boolean = false,
    val isDownloading: Boolean = false,
    val downloadProgress: Float = 0f,
    val downloadedBytes: Long = 0L,
    val totalBytes: Long = 0L,
    val isReadyToInstall: Boolean = false,
    val apkFile: java.io.File? = null,
    val currentVersion: String = com.example.BuildConfig.VERSION_NAME,
    val latestVersion: String = com.example.BuildConfig.VERSION_NAME,
    val downloadUrl: String? = null,
    val releaseNotes: String = "",
    val errorMessage: String? = null
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
            chatBgTheme = securityPreferences.getChatBgTheme(),
            chatExperienceMode = securityPreferences.getChatExperienceMode(),
            isNotificationsEnabled = securityPreferences.isNotificationsEnabled()
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
                chatBgTheme = securityPreferences.getChatBgTheme(),
                chatExperienceMode = securityPreferences.getChatExperienceMode(),
                isSideEmergencyExitEnabled = securityPreferences.isSideEmergencyExitEnabled(),
                sideEmergencyExitOpacity = securityPreferences.getSideEmergencyExitOpacity(),
                isNotificationsEnabled = securityPreferences.isNotificationsEnabled(),
                isBadgeNotificationEnabled = securityPreferences.isBadgeNotificationEnabled()
            )
        }
    }

    fun setNotificationsEnabled(enabled: Boolean) {
        securityPreferences.setNotificationsEnabled(enabled)
        _uiState.update { it.copy(isNotificationsEnabled = enabled) }
    }

    fun setBadgeNotificationEnabled(enabled: Boolean) {
        securityPreferences.setBadgeNotificationEnabled(enabled)
        _uiState.update { it.copy(isBadgeNotificationEnabled = enabled) }
    }

    fun setSideEmergencyExitEnabled(enabled: Boolean) {
        securityPreferences.setSideEmergencyExitEnabled(enabled)
        _uiState.update { it.copy(isSideEmergencyExitEnabled = enabled) }
    }

    fun setSideEmergencyExitOpacity(opacity: Float) {
        securityPreferences.setSideEmergencyExitOpacity(opacity)
        _uiState.update { it.copy(sideEmergencyExitOpacity = opacity) }
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

        viewModelScope.launch {
            securityPreferences.showPreviousChats.collect { show ->
                _uiState.update { it.copy(showPreviousChats = show) }
            }
        }

        viewModelScope.launch {
            securityPreferences.isChatSoundsEnabled.collect { sounds ->
                _uiState.update { it.copy(isChatSoundsEnabled = sounds) }
            }
        }
    }

    fun setChatSoundsEnabled(enabled: Boolean) {
        securityPreferences.setChatSoundsEnabled(enabled)
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

    fun setShowPreviousChatsEnabled(enabled: Boolean) {
        securityPreferences.setShowPreviousChatsEnabled(enabled)
    }

    fun recoverAllChatsAndGallery() {
        securityPreferences.setShowPreviousChatsEnabled(true)
        securityPreferences.setTemporaryClearTimestamp(0L)
        securityPreferences.setAllGalleryRecovered(true)
        try {
            com.example.CherishApplication.instance.chatRepository.recoverAllMessages()
            com.example.CherishApplication.instance.chatRepository.loadAllGalleryMedia()
            googleDriveBackupManager.triggerImmediateAutoBackup()
        } catch (_: Exception) {}
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
        val secPrefs = com.example.CherishApplication.instance.securityPreferences
        if (secPrefs.isDisguiseActive() || !secPrefs.hasRevealedSecretApp()) return

        val otaManager = com.example.update.OtaUpdateManager.getInstance(context)
        val currentVer = com.example.BuildConfig.VERSION_NAME

        _updateState.value = UpdateCheckState(
            isChecking = true,
            showDialog = true,
            currentVersion = currentVer,
            latestVersion = currentVer
        )

        viewModelScope.launch {
            val info = otaManager.checkForUpdates(silent = false)
            if (secPrefs.isDisguiseActive() || !secPrefs.hasRevealedSecretApp()) {
                _updateState.value = UpdateCheckState(showDialog = false)
                return@launch
            }

            if (info != null) {
                if (info.hasUpdate) {
                    _updateState.value = UpdateCheckState(
                        isChecking = false,
                        showDialog = true,
                        isUpdateAvailable = true,
                        currentVersion = currentVer,
                        latestVersion = info.latestVersionName.ifBlank { "v$currentVer" },
                        downloadUrl = info.downloadUrl,
                        releaseNotes = info.releaseNotes
                    )
                } else {
                    _updateState.value = UpdateCheckState(
                        isChecking = false,
                        showDialog = true,
                        isUpdateAvailable = false,
                        currentVersion = currentVer,
                        latestVersion = currentVer,
                        downloadUrl = info.downloadUrl,
                        releaseNotes = "You are on the latest version of Cherish ($currentVer). Pure white day theme, instant OTA updates, and all chat features are active ✨"
                    )
                }
            } else {
                _updateState.value = UpdateCheckState(
                    isChecking = false,
                    showDialog = true,
                    isUpdateAvailable = false,
                    currentVersion = currentVer,
                    latestVersion = currentVer,
                    releaseNotes = "Your app is up to date (Version $currentVer)."
                )
            }
        }
    }

    fun silentCheckForUpdates(context: android.content.Context) {
        val secPrefs = com.example.CherishApplication.instance.securityPreferences
        if (secPrefs.isDisguiseActive() || !secPrefs.hasRevealedSecretApp()) return

        val otaManager = com.example.update.OtaUpdateManager.getInstance(context)
        val currentVer = com.example.BuildConfig.VERSION_NAME

        viewModelScope.launch {
            val info = otaManager.checkForUpdates(silent = true)
            if (info != null && info.hasUpdate) {
                // Ensure disguise mode did not become active during network call
                if (!secPrefs.isDisguiseActive() && secPrefs.hasRevealedSecretApp()) {
                    _updateState.value = UpdateCheckState(
                        isChecking = false,
                        showDialog = true,
                        isUpdateAvailable = true,
                        currentVersion = currentVer,
                        latestVersion = info.latestVersionName.ifBlank { "v$currentVer" },
                        downloadUrl = info.downloadUrl,
                        releaseNotes = info.releaseNotes
                    )
                }
            }
        }
    }

    fun downloadAndInstallUpdate(context: android.content.Context) {
        val otaManager = com.example.update.OtaUpdateManager.getInstance(context)
        val info = otaManager.latestUpdateInfo.value ?: return

        _updateState.update { it.copy(isDownloading = true, errorMessage = null) }

        viewModelScope.launch {
            val job = launch {
                otaManager.updateState.collect { state ->
                    when (state) {
                        is com.example.update.UpdateState.Downloading -> {
                            _updateState.update {
                                it.copy(
                                    isDownloading = true,
                                    downloadProgress = state.progress,
                                    downloadedBytes = state.downloadedBytes,
                                    totalBytes = state.totalBytes
                                )
                            }
                        }
                        is com.example.update.UpdateState.ReadyToInstall -> {
                            _updateState.update {
                                it.copy(
                                    isDownloading = false,
                                    isReadyToInstall = true,
                                    apkFile = state.apkFile
                                )
                            }
                        }
                        is com.example.update.UpdateState.Error -> {
                            _updateState.update {
                                it.copy(
                                    isDownloading = false,
                                    errorMessage = state.message
                                )
                            }
                        }
                        else -> {}
                    }
                }
            }

            otaManager.downloadAndInstallUpdate(context, info)
            job.cancel()
        }
    }

    fun triggerInstall(context: android.content.Context) {
        val file = _updateState.value.apkFile ?: return
        val otaManager = com.example.update.OtaUpdateManager.getInstance(context)
        otaManager.installApk(context, file)
    }

    fun setChatExperienceMode(mode: com.example.ui.chat.ChatExperienceMode) {
        securityPreferences.setChatExperienceMode(mode)
        _uiState.update { it.copy(chatExperienceMode = mode) }
    }
}

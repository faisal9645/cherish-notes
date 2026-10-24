package com.example.security

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.withLock
import java.security.MessageDigest

class SecurityPreferences(private val context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("cherish_secure_prefs", Context.MODE_PRIVATE)

    private val _isAppLocked = MutableStateFlow(false)
    val isAppLocked: StateFlow<Boolean> = _isAppLocked.asStateFlow()

    var ignoreNextPause: Boolean = false

    /**
     * Set to true before navigating to a Chat sub-screen (Profile, Settings, etc.)
     * to prevent onStop from re-disguising the app mid-navigation.
     * Reset to false when returning from the sub-screen.
     */
    var ignoreChatNavigation: Boolean = false

    /**
     * Set to true while YouTube Theater mode or full-screen video playback is active
     * to ensure orientation changes and fullscreen dialogs never trigger disguise.
     */
    var isTheaterModeActive: Boolean = false

    /**
     * Set to true while full-screen photo/media viewer is active so dialog windows
     * and window focus changes never trigger disguise mode.
     */
    var isMediaViewerActive: Boolean = false

    /**
     * Set to true while system pickers (Photo Picker, Camera, Documents, Biometric) are active
     * so that Activity transitions and pause/stop events never trigger disguise mode.
     */
    var isExternalPickerActive: Boolean = false
        set(value) {
            field = value
            externalPickerSince = if (value) android.os.SystemClock.elapsedRealtime() else 0L
        }

    @Volatile private var externalPickerSince = 0L

    /**
     * A picker, the camera or a chooser opened from the app is on screen: the user is still in the
     * app (no notification pops up over it). Only for a while, in case they left from there.
     */
    fun isInExternalPicker(): Boolean = isExternalPickerActive &&
        android.os.SystemClock.elapsedRealtime() - externalPickerSince < EXTERNAL_PICKER_IN_APP_MS

    /**
     * Timestamp of the most recent call termination. Prevents app from accidentally
     * disguising into Notes immediately after hanging up or cutting a call.
     */
    @Volatile
    var lastCallEndedAt: Long = 0L

    fun wasInCallRecently(): Boolean {
        val diff = android.os.SystemClock.elapsedRealtime() - lastCallEndedAt
        return diff in 0L..30_000L
    }


    private val _isDisguiseActive = MutableStateFlow(prefs.getBoolean(KEY_DISGUISE_ENABLED, true))
    val isDisguiseActive: StateFlow<Boolean> = _isDisguiseActive.asStateFlow()

    private val _isSideEmergencyExitEnabled = MutableStateFlow(prefs.getBoolean(KEY_SIDE_EMERGENCY_EXIT_ENABLED, true))
    val isSideEmergencyExitEnabled: StateFlow<Boolean> = _isSideEmergencyExitEnabled.asStateFlow()

    private val _sideEmergencyExitOpacity = MutableStateFlow(prefs.getFloat(KEY_SIDE_EMERGENCY_EXIT_OPACITY, 0.35f))
    val sideEmergencyExitOpacity: StateFlow<Float> = _sideEmergencyExitOpacity.asStateFlow()

    private val _hasRevealedSecretAppInSession = MutableStateFlow(!prefs.getBoolean(KEY_DISGUISE_ENABLED, true))
    val hasRevealedSecretAppInSession: StateFlow<Boolean> = _hasRevealedSecretAppInSession.asStateFlow()

    private val _partnerCustomTimeZone = MutableStateFlow(prefs.getString(KEY_PARTNER_CUSTOM_TIMEZONE, null))
    val partnerCustomTimeZone: StateFlow<String?> = _partnerCustomTimeZone.asStateFlow()


    fun isDisguiseActive(): Boolean = _isDisguiseActive.value

    fun hasRevealedSecretApp(): Boolean = _hasRevealedSecretAppInSession.value

    fun isDisguiseModeEnabled(): Boolean = prefs.getBoolean(KEY_DISGUISE_ENABLED, true)

    private val _disguiseStyle = MutableStateFlow(getDisguiseStyle())
    val disguiseStyle: StateFlow<String> = _disguiseStyle.asStateFlow()

    fun getDisguiseStyle(): String = prefs.getString(KEY_DISGUISE_STYLE, DISGUISE_STYLE_CALCULATOR) ?: DISGUISE_STYLE_CALCULATOR

    fun setDisguiseStyle(style: String) {
        val valid = if (style == DISGUISE_STYLE_CALCULATOR) DISGUISE_STYLE_CALCULATOR else DISGUISE_STYLE_NOTES
        if (getDisguiseStyle() == valid && _disguiseStyle.value == valid) {
            com.example.share.ShareRotationHelper.syncShareAliases(context)
            syncLauncherIcon()
            return
        }
        prefs.edit().putString(KEY_DISGUISE_STYLE, valid).apply()
        _disguiseStyle.value = valid
        _isCalculatorDecoy.value = isCalculatorDecoy()
        com.example.share.ShareRotationHelper.syncShareAliases(context)
        syncLauncherIcon()
    }

    /**
     * The app is the Calculator right now: its icon, its name in notifications and recent apps, and
     * what hides Cherish. It's Calculator only while Disguise Mode is on with Calculator picked;
     * otherwise the app is Notes (its base identity, and when Notes disguise is picked).
     */
    fun isCalculatorDecoy(): Boolean = isDisguiseModeEnabled() && getDisguiseStyle() == DISGUISE_STYLE_CALCULATOR

    /** The style the app wears right now (see [isCalculatorDecoy]). */
    fun currentDecoyStyle(): String = if (isCalculatorDecoy()) DISGUISE_STYLE_CALCULATOR else DISGUISE_STYLE_NOTES

    private val _isCalculatorDecoy = MutableStateFlow(isCalculatorDecoy())
    val calculatorDecoy: StateFlow<Boolean> = _isCalculatorDecoy.asStateFlow()

    private val launcherSyncScope = kotlinx.coroutines.CoroutineScope(
        kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.IO
    )
    private val launcherSyncLock = kotlinx.coroutines.sync.Mutex()

    /**
     * Shows the one launcher icon that fits now: the Calculator, or Notes (also whenever Disguise
     * Mode is off). Calls queue up and each works from the current settings, so a newer call never
     * cuts an icon switch off halfway.
     *
     * Android 13+ applies both alias changes atomically. Older versions enable the destination
     * first so there is always a working launcher entry. Drawer sorting/cached positions belong
     * to the launcher; switching aliases cannot force an OEM launcher to rebuild its alphabet index.
     */
    fun syncLauncherIcon(): kotlinx.coroutines.Job = launcherSyncScope.launch {
        launcherSyncLock.withLock { applyLauncherIcon() }
    }

    private suspend fun applyLauncherIcon() {
        try {
            val pm = context.packageManager
            val mainComp = android.content.ComponentName(context, "com.example.MainActivity")
            val notesComp = android.content.ComponentName(context, "com.example.NotesAlias")
            val calcComp = android.content.ComponentName(context, "com.example.CalculatorAlias")

            // Both icons open MainActivity, so it must never stay disabled
            setComponentOn(pm, mainComp, true)

            val isCalc = isCalculatorDecoy()
            val incoming = if (isCalc) calcComp else notesComp
            val outgoing = if (isCalc) notesComp else calcComp

            // "Share to Notes" is offered only while the app is Notes (a calculator taking shares
            // would stand out in every share sheet). Android controls the share target ranking.
            com.example.share.ShareRotationHelper.syncShareAliases(context)

            val drawerFixedKey = "drawer_order_fixed_v244"
            val needsDrawerReorder = !prefs.getBoolean(drawerFixedKey, false)

            if (!needsDrawerReorder && isComponentOn(pm, incoming) && !isComponentOn(pm, outgoing)) return

            if (needsDrawerReorder) {
                // To force OEM launchers (e.g. Vivo FuntouchOS/OriginOS) to re-index and sort the icon
                // into its proper alphabetical drawer position on initial upgrade:
                setComponentOn(pm, outgoing, false)
                setComponentOn(pm, incoming, false)
                kotlinx.coroutines.delay(350)
                setComponentOn(pm, incoming, true)
                prefs.edit().putBoolean(drawerFixedKey, true).apply()
            } else {
                // Ensure the incoming launcher alias is always enabled first so system settings
                // and launchers never see a zero-component state when switching disguise.
                setComponentOn(pm, incoming, true)
                setComponentOn(pm, outgoing, false)
            }
        } catch (e: Exception) {
            android.util.Log.w("SecurityPreferences", "Launcher identity switch failed", e)
        }
    }



    /** A component's real state: what it was switched to, or its manifest default if never switched */
    @Suppress("DEPRECATION")
    private fun isComponentOn(pm: android.content.pm.PackageManager, comp: android.content.ComponentName): Boolean =
        when (pm.getComponentEnabledSetting(comp)) {
            android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_ENABLED -> true
            android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_DEFAULT -> try {
                pm.getActivityInfo(comp, android.content.pm.PackageManager.MATCH_DISABLED_COMPONENTS).enabled
            } catch (_: Throwable) {
                false
            }
            else -> false
        }

    private fun setComponentOn(pm: android.content.pm.PackageManager, comp: android.content.ComponentName, on: Boolean) {
        if (isComponentOn(pm, comp) == on) return
        pm.setComponentEnabledSetting(
            comp,
            if (on) android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_ENABLED
            else android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
            android.content.pm.PackageManager.DONT_KILL_APP
        )
    }

    fun setDisguiseModeEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_DISGUISE_ENABLED, enabled).apply()
        _isDisguiseActive.value = enabled
        if (enabled) {
            _hasRevealedSecretAppInSession.value = false
        } else {
            _hasRevealedSecretAppInSession.value = true
        }
        _isCalculatorDecoy.value = isCalculatorDecoy()
        _callMaskingStyle.value = getCallMaskingStyle()
        com.example.share.ShareRotationHelper.syncShareAliases(context)
        syncLauncherIcon()
    }

    fun revealSecretApp() {
        _hasRevealedSecretAppInSession.value = true
        _isDisguiseActive.value = false
        try {
            val app = context.applicationContext as? com.example.CherishApplication
            app?.chatRepository?.markPendingDelivered()
            app?.authRepository?.updatePresence()
        } catch (_: Exception) {}
    }

    fun reDisguise() {
        if (!isDisguiseModeEnabled()) return
        // A full-screen video or Watch Party never leaves the app, so it doesn't hold Cherish open
        // here: going home from it hides Cherish like going home from anywhere else
        if (ignoreNextPause || isExternalPickerActive || wasInCallRecently()) return
        if (com.example.call.CallManager.hasActiveCall() || com.example.call.CallManager.isCallScreenVisible) return
        isMediaViewerActive = false
        _hasRevealedSecretAppInSession.value = false
        _isDisguiseActive.value = true
        try {
            val app = context.applicationContext as? com.example.CherishApplication
            app?.authRepository?.updatePresence()
            kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                com.example.util.StorageOptimizer.pruneStaleCaches(context)
            }
        } catch (_: Exception) {}
    }

    /**
     * For manual emergency triggers only (e.g. Side Emergency Exit button, Panic button in Settings).
     * Bypasses transient view flags to immediately disguise into Notes.
     */
    fun forceDisguise() {
        // An emergency exit always ends on Notes, even with automatic disguise turned off, and
        // leaves no viewer/picker flag behind that would stop the next automatic disguise
        isMediaViewerActive = false
        isTheaterModeActive = false
        isExternalPickerActive = false
        ignoreNextPause = false
        _hasRevealedSecretAppInSession.value = false
        _isDisguiseActive.value = true
        // Hidden during a call: the call ending doesn't bring Cherish back
        try { com.example.call.CallManager.cancelReturnToCherish() } catch (_: Throwable) {}
        try {
            val app = context.applicationContext as? com.example.CherishApplication
            app?.authRepository?.updatePresence()
            kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                com.example.util.StorageOptimizer.pruneStaleCaches(context)
            }
        } catch (_: Exception) {}
    }

    private val _isSecretHistoryRevealed = MutableStateFlow(true)
    val isSecretHistoryRevealed: StateFlow<Boolean> = _isSecretHistoryRevealed.asStateFlow()

    fun revealSecretHistory() {
        _isSecretHistoryRevealed.value = true
        setShowPreviousChatsEnabled(true)
        setTemporaryClearTimestamp(0L)
    }

    fun hideSecretHistory() {
        _isSecretHistoryRevealed.value = false
    }

    /**
     * The day "Recover all" applies to. Days start at 6 AM, like the chat's "Today": recovering at
     * 1 AM lasts until 6 AM, and everything is hidden again from 6 AM.
     */
    fun logicalDayKey(now: Long = System.currentTimeMillis()): String =
        java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(java.util.Date(now - 6 * 60 * 60 * 1000L))

    private val _showPreviousChats = MutableStateFlow(isShowPreviousChatsEnabled())
    val showPreviousChats: StateFlow<Boolean> = _showPreviousChats.asStateFlow()

    fun isShowPreviousChatsEnabled(): Boolean {
        val todayStr = logicalDayKey()
        val savedDate = prefs.getString(KEY_SHOW_PREVIOUS_CHATS_DATE, null)
        return savedDate == todayStr
    }

    fun setShowPreviousChatsEnabled(enabled: Boolean) {
        if (enabled) {
            setTemporaryClearTimestamp(0L)
            val todayStr = logicalDayKey()
            prefs.edit().putString(KEY_SHOW_PREVIOUS_CHATS_DATE, todayStr).apply()
        } else {
            prefs.edit().remove(KEY_SHOW_PREVIOUS_CHATS_DATE).apply()
        }
        _showPreviousChats.value = enabled
    }

    private val _isAllGalleryRecovered = MutableStateFlow(isAllGalleryRecovered())
    val isAllGalleryRecovered: StateFlow<Boolean> = _isAllGalleryRecovered.asStateFlow()

    fun isAllGalleryRecovered(): Boolean {
        val todayStr = logicalDayKey()
        val savedDate = prefs.getString(KEY_ALL_GALLERY_RECOVERED_DATE, null)
        return savedDate == todayStr
    }

    fun setAllGalleryRecovered(recovered: Boolean) {
        val todayStr = logicalDayKey()
        if (recovered) {
            prefs.edit().putString(KEY_ALL_GALLERY_RECOVERED_DATE, todayStr).apply()
        } else {
            prefs.edit().remove(KEY_ALL_GALLERY_RECOVERED_DATE).apply()
        }
        _isAllGalleryRecovered.value = recovered
    }

    private val _temporaryClearTimestamp = MutableStateFlow(getTemporaryClearTimestamp())
    val temporaryClearTimestamp: StateFlow<Long> = _temporaryClearTimestamp.asStateFlow()

    fun getTemporaryClearTimestamp(): Long = prefs.getLong(KEY_TEMPORARY_CLEAR_TIMESTAMP, 0L)

    fun setTemporaryClearTimestamp(timestamp: Long) {
        prefs.edit().putLong(KEY_TEMPORARY_CLEAR_TIMESTAMP, timestamp).apply()
        _temporaryClearTimestamp.value = timestamp
    }

    fun clearTemporaryScreen() {
        val now = System.currentTimeMillis()
        setTemporaryClearTimestamp(now)
        setShowPreviousChatsEnabled(false)
    }

    private val _isNoteRemindersEnabled = MutableStateFlow(isNoteRemindersEnabled())
    val isNoteRemindersEnabled: StateFlow<Boolean> = _isNoteRemindersEnabled.asStateFlow()

    fun isNoteRemindersEnabled(): Boolean = prefs.getBoolean(KEY_NOTE_REMINDERS_ENABLED, true)

    private val _isChatSoundsEnabled = MutableStateFlow(isChatSoundsEnabled())
    val isChatSoundsEnabled: StateFlow<Boolean> = _isChatSoundsEnabled.asStateFlow()

    fun isChatSoundsEnabled(): Boolean = prefs.getBoolean(KEY_CHAT_SOUNDS_ENABLED, true)

    fun setChatSoundsEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_CHAT_SOUNDS_ENABLED, enabled).apply()
        _isChatSoundsEnabled.value = enabled
    }

    fun setNoteRemindersEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_NOTE_REMINDERS_ENABLED, enabled).apply()
        _isNoteRemindersEnabled.value = enabled
    }

    private val _themeMode = MutableStateFlow(getThemeMode())
    val themeMode: StateFlow<Int> = _themeMode.asStateFlow()

    // 0 = System, 1 = Light / Day, 2 = Dark, 3 = Black (pure black for AMOLED screens)
    fun getThemeMode(): Int {
        val mode = prefs.getInt("theme_mode", 0)
        return if (mode > 3) 2 else mode
    }

    fun setThemeMode(mode: Int) {
        prefs.edit().putInt("theme_mode", mode).apply()
        _themeMode.value = mode
    }

    private val _chatBgTheme = MutableStateFlow(getChatBgTheme())
    val chatBgTheme: StateFlow<Int> = _chatBgTheme.asStateFlow()

    // 0 = Normal, 1 = Theme 1
    fun getChatBgTheme(): Int = prefs.getInt("chat_bg_theme", 0)

    fun setChatBgTheme(theme: Int) {
        prefs.edit().putInt("chat_bg_theme", theme).apply()
        _chatBgTheme.value = theme
    }

    private val _customWallpaperPath = MutableStateFlow(getCustomWallpaperPath())
    val customWallpaperPath: StateFlow<String?> = _customWallpaperPath.asStateFlow()

    private val _customWallpaperVersion = MutableStateFlow(System.currentTimeMillis())
    val customWallpaperVersion: StateFlow<Long> = _customWallpaperVersion.asStateFlow()

    fun getCustomWallpaperPath(): String? = prefs.getString("custom_wallpaper_path", null)

    fun setCustomWallpaperPath(path: String?) {
        if (path == null) {
            prefs.edit().remove("custom_wallpaper_path").apply()
        } else {
            prefs.edit().putString("custom_wallpaper_path", path).apply()
        }
        _customWallpaperPath.value = path
        _customWallpaperVersion.value = System.currentTimeMillis()
    }

    private val _chatTextSize = MutableStateFlow(getChatTextSize())
    val chatTextSize: StateFlow<Int> = _chatTextSize.asStateFlow()

    /** The chat's message text size, in sp (14 is the usual size). */
    fun getChatTextSize(): Int =
        prefs.getInt(KEY_CHAT_TEXT_SIZE, DEFAULT_CHAT_TEXT_SIZE).coerceIn(MIN_CHAT_TEXT_SIZE, MAX_CHAT_TEXT_SIZE)

    fun setChatTextSize(sizeSp: Int) {
        val size = sizeSp.coerceIn(MIN_CHAT_TEXT_SIZE, MAX_CHAT_TEXT_SIZE)
        prefs.edit().putInt(KEY_CHAT_TEXT_SIZE, size).apply()
        _chatTextSize.value = size
    }

    private val _chatFont = MutableStateFlow(getChatFont())
    val chatFont: StateFlow<Int> = _chatFont.asStateFlow()

    // The font chat messages are written in: 0 = the app's font, 1 = Sweetie, 2 = Romantic
    fun getChatFont(): Int = prefs.getInt(KEY_CHAT_FONT, 0).coerceIn(0, 2)

    fun setChatFont(font: Int) {
        prefs.edit().putInt(KEY_CHAT_FONT, font).apply()
        _chatFont.value = font
    }

    fun getDisguisePasscode(): String {
        val saved = prefs.getString(KEY_DISGUISE_PASSCODE, null)
        if (saved == null || saved == "1234") {
            return "love"
        }
        return saved
    }

    fun setDisguisePasscode(passcode: String) {
        prefs.edit().putString(KEY_DISGUISE_PASSCODE, passcode.trim()).apply()
    }

    fun verifyDisguisePasscode(code: String): Boolean {
        val trimmed = code.trim()
        val saved = getDisguisePasscode()
        if (trimmed.equals(saved, ignoreCase = true)) return true
        if (hasPin() && verifyPin(trimmed)) return true
        return false
    }

    fun getCalculatorPasscode(): String {
        val saved = prefs.getString(KEY_CALCULATOR_PASSCODE, null)
        if (saved.isNullOrBlank()) {
            return "1234"
        }
        return saved
    }

    fun setCalculatorPasscode(passcode: String) {
        val clean = passcode.trim()
        prefs.edit().putString(KEY_CALCULATOR_PASSCODE, if (clean.isBlank()) "1234" else clean).apply()
    }

    fun verifyCalculatorPasscode(code: String): Boolean {
        val trimmed = code.trim().replace(",", "").replace(" ", "")
        val saved = getCalculatorPasscode().trim()
        return trimmed == saved
    }

    fun isAppLockEnabled(): Boolean = prefs.getBoolean(KEY_APP_LOCK_ENABLED, false)

    fun setAppLockEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_APP_LOCK_ENABLED, enabled).apply()
        if (!enabled) {
            _isAppLocked.value = false
        }
    }

    fun isBiometricEnabled(): Boolean = prefs.getBoolean(KEY_BIOMETRIC_ENABLED, false)

    fun setBiometricEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_BIOMETRIC_ENABLED, enabled).apply()
    }

    fun isScreenshotProtectionEnabled(): Boolean = prefs.getBoolean(KEY_SCREENSHOT_PROTECTION, false)

    fun setScreenshotProtectionEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_SCREENSHOT_PROTECTION, enabled).apply()
    }

    /**
     * Checks if Digital Wellbeing usage access has been blocked/revoked so that
     * screen time and app opens cannot be tracked by system usage monitors.
     */
    fun isDigitalWellbeingBlocked(context: Context): Boolean {
        return try {
            val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as? android.app.AppOpsManager ?: return false
            val pm = context.packageManager
            val packages = listOf(
                "com.google.android.apps.wellbeing",
                "com.samsung.android.forest"
            )
            val installedWellbeing = packages.filter { pkg ->
                try {
                    pm.getPackageInfo(pkg, 0)
                    true
                } catch (_: Exception) {
                    false
                }
            }
            if (installedWellbeing.isEmpty()) return true
            installedWellbeing.none { pkg ->
                try {
                    val uid = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        pm.getPackageUid(pkg, android.content.pm.PackageManager.PackageInfoFlags.of(0))
                    } else {
                        @Suppress("DEPRECATION")
                        pm.getPackageUid(pkg, 0)
                    }
                    val mode = appOps.unsafeCheckOpNoThrow(
                        android.app.AppOpsManager.OPSTR_GET_USAGE_STATS,
                        uid,
                        pkg
                    )
                    mode == android.app.AppOpsManager.MODE_ALLOWED
                } catch (_: Exception) {
                    false
                }
            }
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Directly navigates to Usage Access or Digital Wellbeing settings so the user
     * can revoke its permission to track screen time and app activity.
     */
    fun openDigitalWellbeingSettings(context: Context) {
        val packages = listOf(
            "com.google.android.apps.wellbeing",
            "com.samsung.android.forest"
        )
        for (pkg in packages) {
            try {
                val intent = android.content.Intent(android.provider.Settings.ACTION_USAGE_ACCESS_SETTINGS).apply {
                    data = android.net.Uri.parse("package:$pkg")
                    addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
                return
            } catch (_: Exception) {}
        }
        try {
            val intent = android.content.Intent(android.provider.Settings.ACTION_USAGE_ACCESS_SETTINGS).apply {
                addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            return
        } catch (_: Exception) {}
        try {
            val intent = android.content.Intent("android.settings.DIGITAL_WELLBEING_SETTINGS").apply {
                addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (_: Exception) {}
    }

    fun isHideNotificationContent(): Boolean = prefs.getBoolean(KEY_HIDE_NOTIFICATION_CONTENT, true)

    fun setHideNotificationContent(hide: Boolean) {
        prefs.edit().putBoolean(KEY_HIDE_NOTIFICATION_CONTENT, hide).apply()
        _isHideNotificationContent.value = hide
    }

    private val _isHideNotificationContent = MutableStateFlow(isHideNotificationContent())
    val isHideNotificationContentFlow: StateFlow<Boolean> = _isHideNotificationContent.asStateFlow()

    fun isNotificationsEnabled(): Boolean = prefs.getBoolean(KEY_NOTIFICATIONS_ENABLED, true)

    fun setNotificationsEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_NOTIFICATIONS_ENABLED, enabled).apply()
        _isNotificationsEnabled.value = enabled
    }

    private val _isNotificationsEnabled = MutableStateFlow(isNotificationsEnabled())
    val isNotificationsEnabledFlow: StateFlow<Boolean> = _isNotificationsEnabled.asStateFlow()

    fun isBadgeNotificationEnabled(): Boolean = prefs.getBoolean(KEY_BADGE_NOTIFICATION_ENABLED, true)

    fun setBadgeNotificationEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_BADGE_NOTIFICATION_ENABLED, enabled).apply()
        _isBadgeNotificationEnabled.value = enabled
    }

    private val _isBadgeNotificationEnabled = MutableStateFlow(isBadgeNotificationEnabled())
    val isBadgeNotificationEnabledFlow: StateFlow<Boolean> = _isBadgeNotificationEnabled.asStateFlow()

    fun isCallRingingEnabled(): Boolean = prefs.getBoolean(KEY_CALL_RINGING_ENABLED, true)

    fun setCallRingingEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_CALL_RINGING_ENABLED, enabled).apply()
        _isCallRingingEnabled.value = enabled
    }

    private val _isCallRingingEnabled = MutableStateFlow(isCallRingingEnabled())
    val isCallRingingEnabledFlow: StateFlow<Boolean> = _isCallRingingEnabled.asStateFlow()

    // Voice Call Settings & Optimizations
    fun getCallAiNoiseMode(): String = prefs.getString(KEY_CALL_AI_NOISE_MODE, "balanced") ?: "balanced"
    fun setCallAiNoiseMode(mode: String) {
        prefs.edit().putString(KEY_CALL_AI_NOISE_MODE, mode).apply()
        _callAiNoiseMode.value = mode
    }
    private val _callAiNoiseMode = MutableStateFlow(getCallAiNoiseMode())
    val callAiNoiseModeFlow: StateFlow<String> = _callAiNoiseMode.asStateFlow()

    fun isCallLowDataMode(): Boolean = prefs.getBoolean(KEY_CALL_LOW_DATA_MODE, true)
    fun setCallLowDataMode(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_CALL_LOW_DATA_MODE, enabled).apply()
        _callLowDataMode.value = enabled
    }
    private val _callLowDataMode = MutableStateFlow(isCallLowDataMode())
    val callLowDataModeFlow: StateFlow<Boolean> = _callLowDataMode.asStateFlow()

    fun getCallDefaultAudioOutput(): String = prefs.getString(KEY_CALL_DEFAULT_AUDIO_OUTPUT, "earpiece") ?: "earpiece"
    fun setCallDefaultAudioOutput(output: String) {
        prefs.edit().putString(KEY_CALL_DEFAULT_AUDIO_OUTPUT, output).apply()
        _callDefaultAudioOutput.value = output
    }
    private val _callDefaultAudioOutput = MutableStateFlow(getCallDefaultAudioOutput())
    val callDefaultAudioOutputFlow: StateFlow<String> = _callDefaultAudioOutput.asStateFlow()

    fun isCallAutoRecordEnabled(): Boolean = prefs.getBoolean(KEY_CALL_AUTO_RECORD_ENABLED, true)
    fun setCallAutoRecordEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_CALL_AUTO_RECORD_ENABLED, enabled).apply()
        _callAutoRecordEnabled.value = enabled
    }
    private val _callAutoRecordEnabled = MutableStateFlow(isCallAutoRecordEnabled())
    val callAutoRecordEnabledFlow: StateFlow<Boolean> = _callAutoRecordEnabled.asStateFlow()

    fun getCallMaskingStyle(): String {
        val defaultStyle = if (isDisguiseModeEnabled()) "discreet" else "normal"
        val explicit = prefs.getString(KEY_CALL_MASKING_STYLE, null)
        return explicit ?: defaultStyle
    }
    fun setCallMaskingStyle(style: String) {
        prefs.edit().putString(KEY_CALL_MASKING_STYLE, style).apply()
        _callMaskingStyle.value = style
    }
    private val _callMaskingStyle = MutableStateFlow(getCallMaskingStyle())
    val callMaskingStyleFlow: StateFlow<String> = _callMaskingStyle.asStateFlow()

    fun isCallVibrateConnected(): Boolean = prefs.getBoolean(KEY_CALL_VIBRATE_CONNECTED, true)
    fun setCallVibrateConnected(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_CALL_VIBRATE_CONNECTED, enabled).apply()
        _callVibrateConnected.value = enabled
    }
    private val _callVibrateConnected = MutableStateFlow(isCallVibrateConnected())
    val callVibrateConnectedFlow: StateFlow<Boolean> = _callVibrateConnected.asStateFlow()

    fun isCallVibrateMute(): Boolean = prefs.getBoolean(KEY_CALL_VIBRATE_MUTE, true)
    fun setCallVibrateMute(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_CALL_VIBRATE_MUTE, enabled).apply()
        _callVibrateMute.value = enabled
    }
    private val _callVibrateMute = MutableStateFlow(isCallVibrateMute())
    val callVibrateMuteFlow: StateFlow<Boolean> = _callVibrateMute.asStateFlow()

    fun getUserGender(): String = prefs.getString(KEY_USER_GENDER, GENDER_MALE) ?: GENDER_MALE
    fun setUserGender(gender: String) {
        val clean = gender.lowercase().trim()
        prefs.edit().putString(KEY_USER_GENDER, clean).apply()
        _userGender.value = clean
        if (!prefs.contains(KEY_PARTNER_GENDER)) {
            val opposite = if (clean == GENDER_MALE) GENDER_FEMALE else GENDER_MALE
            prefs.edit().putString(KEY_PARTNER_GENDER, opposite).apply()
            _partnerGender.value = opposite
        }
    }
    private val _userGender = MutableStateFlow(getUserGender())
    val userGenderFlow: StateFlow<String> = _userGender.asStateFlow()

    fun getPartnerGender(): String = prefs.getString(KEY_PARTNER_GENDER, if (getUserGender() == GENDER_MALE) GENDER_FEMALE else GENDER_MALE) ?: GENDER_FEMALE
    fun setPartnerGender(gender: String) {
        val clean = gender.lowercase().trim()
        prefs.edit().putString(KEY_PARTNER_GENDER, clean).apply()
        _partnerGender.value = clean
    }
    private val _partnerGender = MutableStateFlow(getPartnerGender())
    val partnerGenderFlow: StateFlow<String> = _partnerGender.asStateFlow()

    fun setPin(pin: String) {
        val hash = hashPin(pin)
        prefs.edit().putString(KEY_PIN_HASH, hash).apply()
    }

    fun verifyPin(pin: String): Boolean {
        val savedHash = prefs.getString(KEY_PIN_HASH, null) ?: return false
        val matches = savedHash == hashPin(pin)
        if (matches) {
            _isAppLocked.value = false
        }
        return matches
    }

    fun hasPin(): Boolean {
        return !prefs.getString(KEY_PIN_HASH, null).isNullOrEmpty()
    }

    fun unlockViaBiometric() {
        _isAppLocked.value = false
    }

    fun lockApp() {
        if (isAppLockEnabled() && hasPin()) {
            _isAppLocked.value = true
        }
    }

    fun getCoupleSecretKey(): String {
        val raw = prefs.getString(KEY_COUPLE_KEY, "CHERISH-FOREVER") ?: "CHERISH-FOREVER"
        val clean = raw.trim()
            .removePrefix("COUPLE_")
            .removePrefix("couple_")
            .removePrefix("COUPLE-")
            .removePrefix("couple-")
            .ifBlank { "CHERISH-FOREVER" }
        return clean.uppercase()
    }

    fun setCoupleSecretKey(key: String) {
        val clean = key.trim()
            .removePrefix("COUPLE_")
            .removePrefix("couple_")
            .removePrefix("COUPLE-")
            .removePrefix("couple-")
            .ifBlank { "CHERISH-FOREVER" }
            .uppercase()
        prefs.edit().putString(KEY_COUPLE_KEY, clean).apply()
    }

    fun getApprovedPartnerEmail(): String {
        return prefs.getString(KEY_PARTNER_EMAIL, "") ?: ""
    }

    fun setApprovedPartnerEmail(email: String) {
        prefs.edit().putString(KEY_PARTNER_EMAIL, email.trim().lowercase()).apply()
    }

    // --- SECRET CHAT TRIGGER & GESTURE PREFERENCES ---

    fun isSideEmergencyExitEnabled(): Boolean = _isSideEmergencyExitEnabled.value

    fun setSideEmergencyExitEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_SIDE_EMERGENCY_EXIT_ENABLED, enabled).apply()
        _isSideEmergencyExitEnabled.value = enabled
    }

    fun getSideEmergencyExitOpacity(): Float = _sideEmergencyExitOpacity.value

    fun setSideEmergencyExitOpacity(opacity: Float) {
        val clamped = opacity.coerceIn(0.05f, 1.0f)
        prefs.edit().putFloat(KEY_SIDE_EMERGENCY_EXIT_OPACITY, clamped).apply()
        _sideEmergencyExitOpacity.value = clamped
    }

    fun isKeywordTriggerEnabled(): Boolean = prefs.getBoolean(KEY_KEYWORD_TRIGGER_ENABLED, true)

    fun setKeywordTriggerEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_KEYWORD_TRIGGER_ENABLED, enabled).apply()
    }

    fun getPlusIconHoldDuration(): Int = prefs.getInt(KEY_PLUS_HOLD_DURATION, 5)

    fun setPlusIconHoldDuration(seconds: Int) {
        prefs.edit().putInt(KEY_PLUS_HOLD_DURATION, seconds.coerceIn(0, 10)).apply()
    }

    fun getEqualsHoldDuration(): Int = prefs.getInt(KEY_EQUALS_HOLD_DURATION, 2)

    fun setEqualsHoldDuration(seconds: Int) {
        prefs.edit().putInt(KEY_EQUALS_HOLD_DURATION, seconds.coerceIn(1, 10)).apply()
    }

    fun isEqualsHoldUnlockEnabled(): Boolean = prefs.getBoolean(KEY_EQUALS_HOLD_UNLOCK_ENABLED, true)

    fun setEqualsHoldUnlockEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_EQUALS_HOLD_UNLOCK_ENABLED, enabled).apply()
        _isEqualsHoldUnlockEnabled.value = enabled
    }

    private val _isEqualsHoldUnlockEnabled = MutableStateFlow(isEqualsHoldUnlockEnabled())
    val isEqualsHoldUnlockEnabledFlow: StateFlow<Boolean> = _isEqualsHoldUnlockEnabled.asStateFlow()

    // Require phone screen lock (PIN, Password, Pattern) or fingerprint before revealing secret chat
    fun isRequirePhoneLockAfterHold(): Boolean = prefs.getBoolean(KEY_REQUIRE_PHONE_LOCK_AFTER_HOLD, false)

    fun setRequirePhoneLockAfterHold(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_REQUIRE_PHONE_LOCK_AFTER_HOLD, enabled).apply()
    }

    data class SecurityAuditScore(
        val passedCount: Int,
        val totalCount: Int = 8,
        val isDisguiseOn: Boolean,
        val isAppLockOn: Boolean,
        val isBiometricOn: Boolean,
        val isNotificationMaskingOn: Boolean,
        val isScreenshotBlockingOn: Boolean,
        val isRecentAppsCoverOn: Boolean,
        val isKeywordTriggerOn: Boolean,
        val isPhoneLockHoldOn: Boolean
    ) {
        val percentage: Int get() = if (totalCount > 0) (passedCount * 100) / totalCount else 100
        val summaryText: String get() = "$passedCount/$totalCount Active"
    }

    fun getSecurityAuditScore(): SecurityAuditScore {
        val disguise = isDisguiseModeEnabled()
        val appLock = isAppLockEnabled() && hasPin()
        val biometric = isBiometricEnabled()
        val notif = isHideNotificationContent()
        val screenshot = isScreenshotProtectionEnabled()
        val recentApps = true
        val keyword = isKeywordTriggerEnabled()
        val hold = isRequirePhoneLockAfterHold() || getPlusIconHoldDuration() > 0

        val passed = listOf(disguise, appLock, biometric, notif, screenshot, recentApps, keyword, hold).count { it }
        return SecurityAuditScore(
            passedCount = passed,
            totalCount = 8,
            isDisguiseOn = disguise,
            isAppLockOn = appLock,
            isBiometricOn = biometric,
            isNotificationMaskingOn = notif,
            isScreenshotBlockingOn = screenshot,
            isRecentAppsCoverOn = recentApps,
            isKeywordTriggerOn = keyword,
            isPhoneLockHoldOn = hold
        )
    }

    // First run initial permissions requested tracking
    fun hasRequestedInitialPermissions(): Boolean = prefs.getBoolean(KEY_INITIAL_PERMS_REQUESTED, false)

    fun setInitialPermissionsRequested(requested: Boolean) {
        prefs.edit()
            .putBoolean(KEY_INITIAL_PERMS_REQUESTED, requested)
            .putBoolean(KEY_INITIAL_NOTIF_REQUESTED, requested)
            .apply()
    }

    fun hasRequestedInitialNotificationPermission(): Boolean = prefs.getBoolean(KEY_INITIAL_NOTIF_REQUESTED, false)

    fun setInitialNotificationPermissionRequested(requested: Boolean) {
        prefs.edit().putBoolean(KEY_INITIAL_NOTIF_REQUESTED, requested).apply()
    }

    // --- MEDIA & GALLERY PREFERENCES ---

    fun getImageGallerySize(): String = prefs.getString(KEY_IMAGE_GALLERY_SIZE, "small") ?: "small"

    fun setImageGallerySize(size: String) {
        val validSize = when (size.lowercase()) {
            "medium", "large" -> size.lowercase()
            else -> "small"
        }
        prefs.edit().putString(KEY_IMAGE_GALLERY_SIZE, validSize).apply()
        _imageGallerySize.value = validSize
    }

    private val _imageGallerySize = MutableStateFlow(getImageGallerySize())
    val imageGallerySize: StateFlow<String> = _imageGallerySize.asStateFlow()

    // --- DAILY FREE CALL MINUTES ---

    fun getTodayLogicalDate(): String {
        val cal = java.util.Calendar.getInstance()
        if (cal.get(java.util.Calendar.HOUR_OF_DAY) < 6) {
            cal.add(java.util.Calendar.DAY_OF_YEAR, -1)
        }
        return "%04d_%02d_%02d".format(cal.get(java.util.Calendar.YEAR), cal.get(java.util.Calendar.MONTH) + 1, cal.get(java.util.Calendar.DAY_OF_MONTH))
    }

    fun getTodayCallDurationSeconds(): Int {
        val dateKey = "call_secs_" + getTodayLogicalDate()
        return prefs.getInt(dateKey, 0)
    }

    fun getCurrentMonthKey(): String {
        val cal = java.util.Calendar.getInstance()
        return "%04d_%02d".format(cal.get(java.util.Calendar.YEAR), cal.get(java.util.Calendar.MONTH) + 1)
    }

    fun getMonthCallDurationSeconds(): Int {
        val monthKey = "call_month_secs_" + getCurrentMonthKey()
        return prefs.getInt(monthKey, 0)
    }

    fun getMonthCallMinutesUsed(): Int {
        return (getMonthCallDurationSeconds() + 59) / 60
    }

    fun isMonthlyCallLimitReached(): Boolean {
        return getMonthCallMinutesUsed() >= 10_000
    }

    fun getMonthlyCallMinutesRemaining(): Int {
        return (10_000 - getMonthCallMinutesUsed()).coerceAtLeast(0)
    }

    private val _monthlyFreeCallMinutesRemaining = MutableStateFlow(getMonthlyCallMinutesRemaining())
    val monthlyFreeCallMinutesRemaining: StateFlow<Int> = _monthlyFreeCallMinutesRemaining.asStateFlow()

    fun addCallDurationSeconds(seconds: Int) {
        if (seconds <= 0) return
        val dateKey = "call_secs_" + getTodayLogicalDate()
        val current = prefs.getInt(dateKey, 0)
        val monthKey = "call_month_secs_" + getCurrentMonthKey()
        val currentMonthSecs = prefs.getInt(monthKey, 0)
        prefs.edit()
            .putInt(dateKey, current + seconds)
            .putInt(monthKey, currentMonthSecs + seconds)
            .apply()
        _dailyFreeCallMinutesRemaining.value = getDailyFreeCallMinutesRemaining()
        _monthlyFreeCallMinutesRemaining.value = getMonthlyCallMinutesRemaining()
    }

    fun getDailyFreeCallMinutesTotal(): Int = prefs.getInt(KEY_DAILY_FREE_CALL_MINUTES, 60)

    fun getDailyFreeCallMinutesRemaining(): Int {
        val total = getDailyFreeCallMinutesTotal()
        val usedSeconds = getTodayCallDurationSeconds()
        val usedMinutes = (usedSeconds + 59) / 60
        return (total - usedMinutes).coerceAtLeast(0)
    }

    private val _dailyFreeCallMinutesRemaining = MutableStateFlow(getDailyFreeCallMinutesRemaining())
    val dailyFreeCallMinutesRemaining: StateFlow<Int> = _dailyFreeCallMinutesRemaining.asStateFlow()

    fun refreshDailyCallMinutes() {
        _dailyFreeCallMinutesRemaining.value = getDailyFreeCallMinutesRemaining()
        _monthlyFreeCallMinutesRemaining.value = getMonthlyCallMinutesRemaining()
    }

    // --- DAILY VOICE CHANGER FREE QUOTA ---
    // 300 seconds (5 minutes) daily free quota, resets every day at 6:00 AM (logical day)
    fun getDailyVoiceChangerRemainingSeconds(): Int {
        val dateKey = "vc_secs_" + getTodayLogicalDate()
        val used = prefs.getInt(dateKey, 0)
        return (DAILY_VOICE_CHANGER_QUOTA_SECONDS - used).coerceAtLeast(0)
    }

    fun consumeVoiceChangerSeconds(seconds: Int = 1): Int {
        if (seconds <= 0) return getDailyVoiceChangerRemainingSeconds()
        val dateKey = "vc_secs_" + getTodayLogicalDate()
        val current = prefs.getInt(dateKey, 0)
        val newUsed = (current + seconds).coerceAtMost(DAILY_VOICE_CHANGER_QUOTA_SECONDS)
        prefs.edit().putInt(dateKey, newUsed).apply()
        val rem = (DAILY_VOICE_CHANGER_QUOTA_SECONDS - newUsed).coerceAtLeast(0)
        _dailyVoiceChangerRemainingSeconds.value = rem
        return rem
    }

    private val _dailyVoiceChangerRemainingSeconds = MutableStateFlow(getDailyVoiceChangerRemainingSeconds())
    val dailyVoiceChangerRemainingSeconds: StateFlow<Int> = _dailyVoiceChangerRemainingSeconds.asStateFlow()

    fun refreshDailyVoiceChangerQuota(): Int {
        val rem = getDailyVoiceChangerRemainingSeconds()
        _dailyVoiceChangerRemainingSeconds.value = rem
        return rem
    }

    fun ensurePreferencesMigrated(currentVersionCode: Int) {
        val lastSeenVersion = prefs.getInt(KEY_LAST_SEEN_APP_VERSION, 0)
        if (lastSeenVersion < currentVersionCode) {
            // New version APK update: ensure previous settings are preserved safely
            prefs.edit().putInt(KEY_LAST_SEEN_APP_VERSION, currentVersionCode).apply()
        }
        if (!prefs.contains(KEY_DISGUISE_STYLE)) {
            // Fresh install defaults to Calculator
            prefs.edit().putString(KEY_DISGUISE_STYLE, DISGUISE_STYLE_CALCULATOR).apply()
        }
    }

    fun isHapticFeedbackEnabled(): Boolean = prefs.getBoolean(KEY_HAPTIC_FEEDBACK_ENABLED, true)

    fun setHapticFeedbackEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_HAPTIC_FEEDBACK_ENABLED, enabled).apply()
    }

    // Each haptic can be switched off on its own (all only while "Haptic Feedback" is on)
    fun isHapticOnSend(): Boolean = prefs.getBoolean(KEY_HAPTIC_SEND, true)
    fun setHapticOnSend(enabled: Boolean) = prefs.edit().putBoolean(KEY_HAPTIC_SEND, enabled).apply()
    fun isHapticOnReceive(): Boolean = prefs.getBoolean(KEY_HAPTIC_RECEIVE, true)
    fun setHapticOnReceive(enabled: Boolean) = prefs.edit().putBoolean(KEY_HAPTIC_RECEIVE, enabled).apply()
    fun isHeartbeatOnLongPress(): Boolean = prefs.getBoolean(KEY_HAPTIC_HEARTBEAT_LONG_PRESS, true)
    fun setHeartbeatOnLongPress(enabled: Boolean) = prefs.edit().putBoolean(KEY_HAPTIC_HEARTBEAT_LONG_PRESS, enabled).apply()

    fun isHeartbeatTriggerEnabled(): Boolean = prefs.getBoolean(KEY_HEARTBEAT_TRIGGER_ENABLED, true)
    fun setHeartbeatTriggerEnabled(enabled: Boolean) = prefs.edit().putBoolean(KEY_HEARTBEAT_TRIGGER_ENABLED, enabled).apply()

    /** How loud the app's own sounds play, 0 to 100 (25 = 25% initial volume). */
    fun getAppSoundVolume(): Int = prefs.getInt(KEY_APP_SOUND_VOLUME, 25)
    fun setAppSoundVolume(volume: Int) = prefs.edit().putInt(KEY_APP_SOUND_VOLUME, volume.coerceIn(0, 100)).apply()

    /** What a masked (hidden-content / disguised) notification says. */
    fun getMaskedNotificationTitle(style: String = currentDecoyStyle()): String {
        return if (style == DISGUISE_STYLE_CALCULATOR) {
            val custom = prefs.getString(KEY_MASKED_TITLE_CALCULATOR, null)?.ifBlank { null }
            custom ?: "Calculator"
        } else {
            val custom = prefs.getString(KEY_MASKED_NOTIFICATION_TITLE, null)?.ifBlank { null }
            custom ?: DEFAULT_MASKED_TITLE
        }
    }

    fun getMaskedNotificationText(style: String = currentDecoyStyle()): String {
        return if (style == DISGUISE_STYLE_CALCULATOR) {
            val custom = prefs.getString(KEY_MASKED_TEXT_CALCULATOR, null)?.ifBlank { null }
            custom ?: "Calculation history updated"
        } else {
            val custom = prefs.getString(KEY_MASKED_NOTIFICATION_TEXT, null)?.ifBlank { null }
            custom ?: DEFAULT_MASKED_TEXT
        }
    }

    fun setMaskedNotificationText(title: String, text: String, style: String = getDisguiseStyle()) {
        val editor = prefs.edit()
        if (style == DISGUISE_STYLE_CALCULATOR) {
            editor.putString(KEY_MASKED_TITLE_CALCULATOR, title.trim())
                .putString(KEY_MASKED_TEXT_CALCULATOR, text.trim())
        } else {
            editor.putString(KEY_MASKED_NOTIFICATION_TITLE, title.trim())
                .putString(KEY_MASKED_NOTIFICATION_TEXT, text.trim())
        }
        editor.apply()
    }

    fun isAutoPlayMedia(): Boolean = prefs.getBoolean(KEY_AUTOPLAY_MEDIA, true)

    fun setAutoPlayMedia(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_AUTOPLAY_MEDIA, enabled).apply()
    }

    fun isHighQualityMedia(): Boolean = prefs.getBoolean(KEY_HIGH_QUALITY_MEDIA, true)

    fun setHighQualityMedia(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_HIGH_QUALITY_MEDIA, enabled).apply()
    }

    fun isDoubleTapZoomEnabled(): Boolean = prefs.getBoolean(KEY_DOUBLE_TAP_ZOOM, true)

    fun setDoubleTapZoomEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_DOUBLE_TAP_ZOOM, enabled).apply()
    }

    // --- CLEAN REINSTALL (resets the usage counters Android keeps per app) ---
    // Schedule: 0 = off, 1 = weekly (an old stored "daily" (2) folds to weekly)
    fun getReinstallSchedule(): Int = prefs.getInt(KEY_REINSTALL_SCHEDULE, 0).coerceIn(0, 1)

    fun setReinstallSchedule(mode: Int) {
        val m = mode.coerceIn(0, 1)
        prefs.edit().putInt(KEY_REINSTALL_SCHEDULE, m).apply()
        _reinstallSchedule.value = m
    }

    private val _reinstallSchedule = MutableStateFlow(getReinstallSchedule())
    val reinstallScheduleFlow: StateFlow<Int> = _reinstallSchedule.asStateFlow()

    /** When this phone last did a clean reinstall (local clock, ms); 0 if never. */
    fun getLastReinstallAt(): Long = prefs.getLong(KEY_LAST_REINSTALL_AT, 0L)

    fun setLastReinstallAt(timestamp: Long) {
        prefs.edit().putLong(KEY_LAST_REINSTALL_AT, timestamp).apply()
        _lastReinstallAt.value = timestamp
    }

    private val _lastReinstallAt = MutableStateFlow(getLastReinstallAt())
    val lastReinstallAtFlow: StateFlow<Long> = _lastReinstallAt.asStateFlow()

    /** Whether a reinstall is due now, by the chosen schedule. */
    fun isReinstallDue(now: Long = System.currentTimeMillis()): Boolean {
        val schedule = getReinstallSchedule()
        if (schedule == 0) return false
        val last = getLastReinstallAt()
        if (last <= 0L) return true
        val interval = 7L * 24 * 60 * 60 * 1000 // weekly
        return now - last >= interval
    }

    /**
     * True exactly once per install: the first run after a fresh install (an uninstall wipes all
     * prefs, so this key is absent again). Used to clean up the leftover installer and mark the
     * reinstall as done.
     */
    fun consumeFreshInstallFlag(): Boolean {
        if (prefs.contains(KEY_INSTALL_MARKED)) return false
        prefs.edit().putBoolean(KEY_INSTALL_MARKED, true).apply()
        return true
    }

    fun isCheckAfterReminderEnabled(): Boolean = prefs.getBoolean(KEY_CHECK_AFTER_REMINDER, true)

    fun setCheckAfterReminderEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_CHECK_AFTER_REMINDER, enabled).apply()
    }

    fun isAutoGoodnightAt11Pm(): Boolean = prefs.getBoolean(KEY_AUTO_GOODNIGHT_11PM, true)

    fun setAutoGoodnightAt11Pm(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_AUTO_GOODNIGHT_11PM, enabled).apply()
    }

    // --- AUTO-LOCK & INACTIVITY TIMEOUT ---
    // 0 = Immediately, 30 = 30s, 60 = 1m, 300 = 5m, -1 = Never
    fun getAutoLockTimeoutSeconds(): Int = prefs.getInt(KEY_AUTO_LOCK_TIMEOUT_SEC, 60)

    fun setAutoLockTimeoutSeconds(seconds: Int) {
        prefs.edit().putInt(KEY_AUTO_LOCK_TIMEOUT_SEC, seconds).apply()
    }

    // --- PANIC GESTURE PREFERENCES ---
    // "SHAKE", "DOUBLE_TAP_SHIELD", "HARDWARE_BACK", "INSTANT_EXIT"
    fun getPanicGestureType(): String = prefs.getString(KEY_PANIC_GESTURE, "SHAKE") ?: "SHAKE"

    fun setPanicGestureType(gesture: String) {
        prefs.edit().putString(KEY_PANIC_GESTURE, gesture).apply()
    }

    // --- SECRET CHAT APPEARANCE MODE ---
    // "NORMAL", "DARK", "MINIMAL", "NEUTRAL"
    fun getSecretChatThemeMode(): String = prefs.getString(KEY_CHAT_THEME_MODE, "NORMAL") ?: "NORMAL"

    fun setSecretChatThemeMode(mode: String) {
        prefs.edit().putString(KEY_CHAT_THEME_MODE, mode).apply()
    }

    fun getCustomChatTitle(): String = prefs.getString(KEY_CUSTOM_CHAT_TITLE, "") ?: ""

    fun setCustomChatTitle(title: String) {
        prefs.edit().putString(KEY_CUSTOM_CHAT_TITLE, title.trim()).apply()
    }

    // --- NOTIFICATION PRIVACY LEVEL ---
    // "FULL" (Sarah: I miss you), "NEUTRAL" (â¤ï¸ New message), "DISGUISED" (Notes synchronized), "NONE" (New message)
    fun getNotificationPrivacyMode(): String = prefs.getString(KEY_NOTIFICATION_PRIVACY_MODE, "DISGUISED") ?: "DISGUISED"

    fun setNotificationPrivacyMode(mode: String) {
        prefs.edit().putString(KEY_NOTIFICATION_PRIVACY_MODE, mode).apply()
    }

    // --- DECOY / DURESS PIN ---
    fun hasDuressPin(): Boolean = !prefs.getString(KEY_DURESS_PIN_HASH, null).isNullOrEmpty()

    fun setDuressPin(pin: String) {
        val hash = hashPin(pin)
        prefs.edit().putString(KEY_DURESS_PIN_HASH, hash).apply()
    }

    fun verifyDuressPin(pin: String): Boolean {
        val savedHash = prefs.getString(KEY_DURESS_PIN_HASH, null) ?: return false
        return savedHash == hashPin(pin)
    }

    fun getGalleryDensity(): Float = prefs.getFloat(KEY_GALLERY_DENSITY, 0.5f)

    private val _chatExperienceMode = MutableStateFlow(getChatExperienceMode())
    val chatExperienceModeFlow: StateFlow<com.example.ui.chat.ChatExperienceMode> = _chatExperienceMode.asStateFlow()

    fun getChatExperienceMode(): com.example.ui.chat.ChatExperienceMode {
        val key = prefs.getString(KEY_CHAT_EXPERIENCE_MODE, com.example.ui.chat.ChatExperienceMode.NORMAL.key) 
                  ?: com.example.ui.chat.ChatExperienceMode.NORMAL.key
        return com.example.ui.chat.ChatExperienceMode.fromKey(key)
    }

    fun setChatExperienceMode(mode: com.example.ui.chat.ChatExperienceMode) {
        prefs.edit().putString(KEY_CHAT_EXPERIENCE_MODE, mode.key).apply()
        _chatExperienceMode.value = mode
    }

    fun setGalleryDensity(density: Float) {
        prefs.edit().putFloat(KEY_GALLERY_DENSITY, density.coerceIn(0f, 1f)).apply()
    }

    // --- STEALTH SHIELD FIRST-TIME ONBOARDING TIP ---
    fun hasSeenStealthShieldTip(): Boolean = prefs.getBoolean(KEY_HAS_SEEN_STEALTH_SHIELD_TIP, false)

    fun setHasSeenStealthShieldTip(seen: Boolean) {
        prefs.edit().putBoolean(KEY_HAS_SEEN_STEALTH_SHIELD_TIP, seen).apply()
    }

    // --- CALL RECORDING NOTICE (shown once before this phone's first call) ---
    fun hasSeenCallRecordingNotice(): Boolean = prefs.getBoolean(KEY_SEEN_CALL_RECORDING_NOTICE, false)

    fun setSeenCallRecordingNotice() {
        prefs.edit().putBoolean(KEY_SEEN_CALL_RECORDING_NOTICE, true).apply()
    }

    // --- CALL RECORDINGS CLOUD UPLOAD (default true for reliable cloud call recording sync) ---
    fun isAutoUploadCallRecordingsEnabled(): Boolean = prefs.getBoolean(KEY_CALL_RECORDINGS_AUTO_UPLOAD, true)

    fun setAutoUploadCallRecordingsEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_CALL_RECORDINGS_AUTO_UPLOAD, enabled).apply()
    }

    // --- NOTIFICATION ALERT ONCE PER APP SESSION ---
    fun hasAlertedSinceLastAppOpen(): Boolean = prefs.getBoolean(KEY_HAS_ALERTED_SINCE_LAST_OPEN, false)

    fun setAlertedSinceLastAppOpen(alerted: Boolean) {
        prefs.edit().putBoolean(KEY_HAS_ALERTED_SINCE_LAST_OPEN, alerted).apply()
    }

    // --- PARTNER CUSTOM TIME ZONE ---
    fun getPartnerCustomTimeZone(): String? = prefs.getString(KEY_PARTNER_CUSTOM_TIMEZONE, null)?.takeIf { it.isNotBlank() }

    fun setPartnerCustomTimeZone(timeZoneId: String?) {
        val clean = timeZoneId?.trim()?.ifBlank { null }
        if (clean == null) {
            prefs.edit().remove(KEY_PARTNER_CUSTOM_TIMEZONE).apply()
        } else {
            prefs.edit().putString(KEY_PARTNER_CUSTOM_TIMEZONE, clean).apply()
        }
        _partnerCustomTimeZone.value = clean
    }

    private fun hashPin(pin: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(pin.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }

    init {
        try {
            val disguiseEnabled = isDisguiseModeEnabled()
            _isDisguiseActive.value = disguiseEnabled
            if (!disguiseEnabled) {
                _hasRevealedSecretAppInSession.value = true
            }

            // If app lock is enabled and PIN is set, default to locked on cold start
            if (isAppLockEnabled() && hasPin()) {
                _isAppLocked.value = true
            }

            // Whenever an update installs or fresh install, ensure notifications, hide content,
            // app icon badge, heartbeat trigger, and in-chat sound effects are all enabled ON
            val currentVersionCode = try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    context.packageManager.getPackageInfo(context.packageName, 0).longVersionCode.toInt()
                } else {
                    @Suppress("DEPRECATION")
                    context.packageManager.getPackageInfo(context.packageName, 0).versionCode
                }
            } catch (_: Throwable) { 0 }

            val lastSeenVersionCode = prefs.getInt(KEY_LAST_SEEN_APP_VERSION, 0)
            if (currentVersionCode > lastSeenVersionCode) {
                // Preserve user's previous settings when updating to a new version.
                // Only initialize default values for fresh installations or newly added keys.
                if (!prefs.contains(KEY_NOTIFICATIONS_ENABLED)) {
                    setNotificationsEnabled(true)
                }
                if (!prefs.contains(KEY_HIDE_NOTIFICATION_CONTENT)) {
                    setHideNotificationContent(true)
                }
                if (!prefs.contains(KEY_BADGE_NOTIFICATION_ENABLED)) {
                    setBadgeNotificationEnabled(true)
                }
                if (!prefs.contains(KEY_HEARTBEAT_TRIGGER_ENABLED)) {
                    setHeartbeatTriggerEnabled(true)
                }
                if (!prefs.contains(KEY_CHAT_SOUNDS_ENABLED)) {
                    setChatSoundsEnabled(true)
                }
                if (!prefs.contains(KEY_CALL_RINGING_ENABLED)) {
                    setCallRingingEnabled(true)
                }
                prefs.edit().putInt(KEY_LAST_SEEN_APP_VERSION, currentVersionCode).apply()
            }
        } catch (e: Throwable) {
            android.util.Log.e("SecurityPreferences", "Initialization error: ${e.message}", e)
        }
    }

    companion object {
        const val DISGUISE_STYLE_NOTES = "notes"
        const val DISGUISE_STYLE_CALCULATOR = "calculator"
        private const val KEY_DISGUISE_STYLE = "disguise_style"

        private const val EXTERNAL_PICKER_IN_APP_MS = 2L * 60 * 1000
        private const val KEY_DISGUISE_ENABLED = "disguise_enabled"
        private const val KEY_DISGUISE_PASSCODE = "disguise_passcode"
        private const val KEY_CALCULATOR_PASSCODE = "calculator_passcode"
        private const val KEY_APP_LOCK_ENABLED = "app_lock_enabled"
        private const val KEY_BIOMETRIC_ENABLED = "biometric_enabled"
        private const val KEY_SCREENSHOT_PROTECTION = "screenshot_protection"
        private const val KEY_HIDE_NOTIFICATION_CONTENT = "hide_notification_content"
        private const val KEY_NOTIFICATIONS_ENABLED = "notifications_enabled"
        private const val KEY_BADGE_NOTIFICATION_ENABLED = "badge_notification_enabled"
        private const val KEY_CALL_RINGING_ENABLED = "call_ringing_enabled"
        private const val KEY_PIN_HASH = "pin_hash"
        private const val KEY_COUPLE_KEY = "couple_key"
        private const val KEY_PARTNER_EMAIL = "partner_email"
        private const val KEY_USER_GENDER = "user_gender"
        private const val KEY_PARTNER_GENDER = "partner_gender"
        const val GENDER_MALE = "male"
        const val GENDER_FEMALE = "female"

        // Trigger & gallery keys
        private const val KEY_KEYWORD_TRIGGER_ENABLED = "keyword_trigger_enabled"
        private const val KEY_PLUS_HOLD_DURATION = "plus_hold_duration_sec"
        private const val KEY_EQUALS_HOLD_DURATION = "equals_hold_duration_sec"
        private const val KEY_EQUALS_HOLD_UNLOCK_ENABLED = "equals_hold_unlock_enabled"
        private const val KEY_IMAGE_GALLERY_SIZE = "image_gallery_size"
        private const val KEY_HAPTIC_FEEDBACK_ENABLED = "haptic_feedback_enabled"
        private const val KEY_HAPTIC_SEND = "haptic_on_send"
        private const val KEY_HAPTIC_RECEIVE = "haptic_on_receive"
        private const val KEY_HAPTIC_HEARTBEAT_LONG_PRESS = "haptic_heartbeat_long_press"
        private const val KEY_HEARTBEAT_TRIGGER_ENABLED = "heartbeat_trigger_enabled"
        private const val KEY_APP_SOUND_VOLUME = "app_sound_volume"
        private const val KEY_MASKED_NOTIFICATION_TITLE = "masked_notification_title"
        private const val KEY_MASKED_NOTIFICATION_TEXT = "masked_notification_text"
        private const val KEY_MASKED_TITLE_CALCULATOR = "masked_notification_title_calculator"
        private const val KEY_MASKED_TEXT_CALCULATOR = "masked_notification_text_calculator"
        const val DEFAULT_MASKED_TITLE = "Notes"
        const val DEFAULT_MASKED_TEXT = "Checklist reminder updated"
        private const val KEY_AUTOPLAY_MEDIA = "autoplay_media"
        private const val KEY_HIGH_QUALITY_MEDIA = "high_quality_media"
        private const val KEY_DOUBLE_TAP_ZOOM = "double_tap_zoom"
        private const val KEY_CHECK_AFTER_REMINDER = "check_after_reminder_enabled"
        private const val KEY_AUTO_GOODNIGHT_11PM = "auto_goodnight_11pm"

        // New Pillar 1 & 2 Keys
        private const val KEY_AUTO_LOCK_TIMEOUT_SEC = "auto_lock_timeout_sec"
        private const val KEY_PANIC_GESTURE = "panic_gesture_type"
        private const val KEY_CHAT_THEME_MODE = "chat_theme_mode"
        private const val KEY_CHAT_EXPERIENCE_MODE = "chat_experience_mode"
        private const val KEY_CUSTOM_CHAT_TITLE = "custom_chat_title"
        private const val KEY_NOTIFICATION_PRIVACY_MODE = "notification_privacy_mode"
        private const val KEY_DURESS_PIN_HASH = "duress_pin_hash"
        private const val KEY_GALLERY_DENSITY = "gallery_density"
        private const val KEY_HAS_SEEN_STEALTH_SHIELD_TIP = "has_seen_stealth_shield_tip"
        private const val KEY_REQUIRE_PHONE_LOCK_AFTER_HOLD = "require_phone_lock_after_hold"
        private const val KEY_INITIAL_PERMS_REQUESTED = "initial_perms_requested"
        private const val KEY_INITIAL_NOTIF_REQUESTED = "initial_notif_requested"
        private const val KEY_SHOW_PREVIOUS_CHATS = "show_previous_chats"
        private const val KEY_SHOW_PREVIOUS_CHATS_DATE = "show_previous_chats_date"
        private const val KEY_ALL_GALLERY_RECOVERED = "all_gallery_recovered"
        private const val KEY_ALL_GALLERY_RECOVERED_DATE = "all_gallery_recovered_date"
        private const val KEY_TEMPORARY_CLEAR_TIMESTAMP = "temporary_clear_timestamp"
        private const val KEY_NOTE_REMINDERS_ENABLED = "note_reminders_enabled"
        private const val KEY_SIDE_EMERGENCY_EXIT_ENABLED = "side_emergency_exit_enabled"
        private const val KEY_SIDE_EMERGENCY_EXIT_OPACITY = "side_emergency_exit_opacity"
        private const val KEY_CHAT_SOUNDS_ENABLED = "chat_sounds_enabled"
        private const val KEY_CHAT_TEXT_SIZE = "chat_text_size"
        private const val KEY_CHAT_FONT = "chat_font"
        const val DEFAULT_CHAT_TEXT_SIZE = 14
        const val MIN_CHAT_TEXT_SIZE = 12
        const val MAX_CHAT_TEXT_SIZE = 24
        private const val KEY_PARTNER_CUSTOM_TIMEZONE = "partner_custom_timezone"
        private const val KEY_SEEN_CALL_RECORDING_NOTICE = "seen_call_recording_notice"
        private const val KEY_LAST_SEEN_APP_VERSION = "last_seen_app_version_code"
        private const val KEY_REINSTALL_SCHEDULE = "reinstall_schedule"
        private const val KEY_LAST_REINSTALL_AT = "last_reinstall_at"
        private const val KEY_INSTALL_MARKED = "install_marked"
        private const val KEY_DAILY_FREE_CALL_MINUTES = "daily_free_call_minutes"
        const val DAILY_VOICE_CHANGER_QUOTA_SECONDS = 300 // 5 minutes free daily voice effects
        private const val KEY_CALL_AI_NOISE_MODE = "call_ai_noise_mode"
        private const val KEY_CALL_LOW_DATA_MODE = "call_low_data_mode"
        private const val KEY_CALL_DEFAULT_AUDIO_OUTPUT = "call_default_audio_output"
        private const val KEY_CALL_AUTO_RECORD_ENABLED = "call_auto_record_enabled"
        private const val KEY_CALL_MASKING_STYLE = "call_masking_style"
        private const val KEY_CALL_VIBRATE_CONNECTED = "call_vibrate_connected"
        private const val KEY_CALL_VIBRATE_MUTE = "call_vibrate_mute"
        private const val KEY_CALL_RECORDINGS_AUTO_UPLOAD = "call_recordings_auto_upload"
        private const val KEY_HAS_ALERTED_SINCE_LAST_OPEN = "has_alerted_since_last_open"
        // v3: the earlier re-file switched the icon back on too soon to take effect


        @Volatile
        private var INSTANCE: SecurityPreferences? = null

        fun getInstance(context: Context): SecurityPreferences {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: SecurityPreferences(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
}


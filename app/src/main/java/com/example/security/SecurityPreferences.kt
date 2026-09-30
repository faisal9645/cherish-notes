package com.example.security

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.security.MessageDigest

class SecurityPreferences(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("cherish_secure_prefs", Context.MODE_PRIVATE)

    private val _isAppLocked = MutableStateFlow(false)
    val isAppLocked: StateFlow<Boolean> = _isAppLocked.asStateFlow()

    var ignoreNextPause: Boolean = false


    private val _isDisguiseActive = MutableStateFlow(true)
    val isDisguiseActive: StateFlow<Boolean> = _isDisguiseActive.asStateFlow()

    init {
        // If disguise mode is enabled, start disguised
        _isDisguiseActive.value = isDisguiseModeEnabled()

        // If app lock is enabled and PIN is set, default to locked on cold start
        if (isAppLockEnabled() && hasPin()) {
            _isAppLocked.value = true
        }
    }

    fun isDisguiseModeEnabled(): Boolean = prefs.getBoolean(KEY_DISGUISE_ENABLED, true)

    fun setDisguiseModeEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_DISGUISE_ENABLED, enabled).apply()
        _isDisguiseActive.value = enabled
    }

    fun revealSecretApp() {
        _isDisguiseActive.value = false
    }

    fun reDisguise() {
        if (isDisguiseModeEnabled()) {
            _isDisguiseActive.value = true
        }
    }

    private val _themeMode = MutableStateFlow(getThemeMode())
    val themeMode: StateFlow<Int> = _themeMode.asStateFlow()

    // 0 = System, 1 = Light, 2 = Dark
    fun getThemeMode(): Int = prefs.getInt("theme_mode", 0)

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

    fun isHideNotificationContent(): Boolean = prefs.getBoolean(KEY_HIDE_NOTIFICATION_CONTENT, true)

    fun setHideNotificationContent(hide: Boolean) {
        prefs.edit().putBoolean(KEY_HIDE_NOTIFICATION_CONTENT, hide).apply()
    }

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
        return prefs.getString(KEY_COUPLE_KEY, "CHERISH-FOREVER") ?: "CHERISH-FOREVER"
    }

    fun setCoupleSecretKey(key: String) {
        prefs.edit().putString(KEY_COUPLE_KEY, key.trim().uppercase()).apply()
    }

    fun getApprovedPartnerEmail(): String {
        return prefs.getString(KEY_PARTNER_EMAIL, "") ?: ""
    }

    fun setApprovedPartnerEmail(email: String) {
        prefs.edit().putString(KEY_PARTNER_EMAIL, email.trim().lowercase()).apply()
    }

    // --- SECRET CHAT TRIGGER & GESTURE PREFERENCES ---

    fun isKeywordTriggerEnabled(): Boolean = prefs.getBoolean(KEY_KEYWORD_TRIGGER_ENABLED, true)

    fun setKeywordTriggerEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_KEYWORD_TRIGGER_ENABLED, enabled).apply()
    }

    fun getPlusIconHoldDuration(): Int = prefs.getInt(KEY_PLUS_HOLD_DURATION, 5)

    fun setPlusIconHoldDuration(seconds: Int) {
        prefs.edit().putInt(KEY_PLUS_HOLD_DURATION, seconds.coerceIn(0, 10)).apply()
    }

    // Require phone screen lock (PIN, Password, Pattern) or fingerprint before revealing secret chat
    fun isRequirePhoneLockAfterHold(): Boolean = prefs.getBoolean(KEY_REQUIRE_PHONE_LOCK_AFTER_HOLD, false)

    fun setRequirePhoneLockAfterHold(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_REQUIRE_PHONE_LOCK_AFTER_HOLD, enabled).apply()
    }

    // First run initial permissions requested tracking
    fun hasRequestedInitialPermissions(): Boolean = prefs.getBoolean(KEY_INITIAL_PERMS_REQUESTED, false)

    fun setInitialPermissionsRequested(requested: Boolean) {
        prefs.edit().putBoolean(KEY_INITIAL_PERMS_REQUESTED, requested).apply()
    }

    // --- MEDIA & GALLERY PREFERENCES ---

    fun getImageGallerySize(): String = prefs.getString(KEY_IMAGE_GALLERY_SIZE, "medium") ?: "medium"

    fun setImageGallerySize(size: String) {
        val validSize = when (size.lowercase()) {
            "small", "large" -> size.lowercase()
            else -> "medium"
        }
        prefs.edit().putString(KEY_IMAGE_GALLERY_SIZE, validSize).apply()
    }

    fun isHapticFeedbackEnabled(): Boolean = prefs.getBoolean(KEY_HAPTIC_FEEDBACK_ENABLED, true)

    fun setHapticFeedbackEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_HAPTIC_FEEDBACK_ENABLED, enabled).apply()
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

    fun isCheckAfterReminderEnabled(): Boolean = prefs.getBoolean(KEY_CHECK_AFTER_REMINDER, true)

    fun setCheckAfterReminderEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_CHECK_AFTER_REMINDER, enabled).apply()
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
    // "FULL" (Sarah: I miss you), "NEUTRAL" (❤️ New message), "DISGUISED" (Notes synchronized), "NONE" (New message)
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

    fun setGalleryDensity(density: Float) {
        prefs.edit().putFloat(KEY_GALLERY_DENSITY, density.coerceIn(0f, 1f)).apply()
    }

    // --- STEALTH SHIELD FIRST-TIME ONBOARDING TIP ---
    fun hasSeenStealthShieldTip(): Boolean = prefs.getBoolean(KEY_HAS_SEEN_STEALTH_SHIELD_TIP, false)

    fun setHasSeenStealthShieldTip(seen: Boolean) {
        prefs.edit().putBoolean(KEY_HAS_SEEN_STEALTH_SHIELD_TIP, seen).apply()
    }

    private fun hashPin(pin: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(pin.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }

    companion object {
        private const val KEY_DISGUISE_ENABLED = "disguise_enabled"
        private const val KEY_DISGUISE_PASSCODE = "disguise_passcode"
        private const val KEY_APP_LOCK_ENABLED = "app_lock_enabled"
        private const val KEY_BIOMETRIC_ENABLED = "biometric_enabled"
        private const val KEY_SCREENSHOT_PROTECTION = "screenshot_protection"
        private const val KEY_HIDE_NOTIFICATION_CONTENT = "hide_notification_content"
        private const val KEY_PIN_HASH = "pin_hash"
        private const val KEY_COUPLE_KEY = "couple_key"
        private const val KEY_PARTNER_EMAIL = "partner_email"

        // Trigger & gallery keys
        private const val KEY_KEYWORD_TRIGGER_ENABLED = "keyword_trigger_enabled"
        private const val KEY_PLUS_HOLD_DURATION = "plus_hold_duration_sec"
        private const val KEY_IMAGE_GALLERY_SIZE = "image_gallery_size"
        private const val KEY_HAPTIC_FEEDBACK_ENABLED = "haptic_feedback_enabled"
        private const val KEY_AUTOPLAY_MEDIA = "autoplay_media"
        private const val KEY_HIGH_QUALITY_MEDIA = "high_quality_media"
        private const val KEY_DOUBLE_TAP_ZOOM = "double_tap_zoom"
        private const val KEY_CHECK_AFTER_REMINDER = "check_after_reminder_enabled"

        // New Pillar 1 & 2 Keys
        private const val KEY_AUTO_LOCK_TIMEOUT_SEC = "auto_lock_timeout_sec"
        private const val KEY_PANIC_GESTURE = "panic_gesture_type"
        private const val KEY_CHAT_THEME_MODE = "chat_theme_mode"
        private const val KEY_CUSTOM_CHAT_TITLE = "custom_chat_title"
        private const val KEY_NOTIFICATION_PRIVACY_MODE = "notification_privacy_mode"
        private const val KEY_DURESS_PIN_HASH = "duress_pin_hash"
        private const val KEY_GALLERY_DENSITY = "gallery_density"
        private const val KEY_HAS_SEEN_STEALTH_SHIELD_TIP = "has_seen_stealth_shield_tip"
        private const val KEY_REQUIRE_PHONE_LOCK_AFTER_HOLD = "require_phone_lock_after_hold"
        private const val KEY_INITIAL_PERMS_REQUESTED = "initial_perms_requested"

        @Volatile
        private var INSTANCE: SecurityPreferences? = null

        fun getInstance(context: Context): SecurityPreferences {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: SecurityPreferences(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
}

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

        @Volatile
        private var INSTANCE: SecurityPreferences? = null

        fun getInstance(context: Context): SecurityPreferences {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: SecurityPreferences(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
}

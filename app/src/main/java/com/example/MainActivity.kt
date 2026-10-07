package com.example

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.SystemBarStyle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.DisposableEffect
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import com.example.ui.navigation.CherishNavGraph
import com.example.ui.theme.CherishTheme
import com.example.util.BatteryStatusHelper
import kotlinx.coroutines.launch

class MainActivity : FragmentActivity() {

    private val app: CherishApplication
        get() = application as CherishApplication

    private val batteryHelper by lazy { BatteryStatusHelper(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        batteryHelper.start()
        lifecycleScope.launch {
            batteryHelper.batteryInfo.collect { info ->
                if (app.authRepository.isUserLoggedIn()) {
                    app.authRepository.updateBatteryStatus(info.level, info.isCharging)
                }
            }
        }
        androidx.core.view.WindowCompat.setDecorFitsSystemWindows(window, false)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isStatusBarContrastEnforced = false
            window.isNavigationBarContrastEnforced = false
        }
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.auto(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT)
        )
        val initialThemeMode = try { app.securityPreferences.getThemeMode() } catch (_: Exception) { 0 }
        val isSystemDark = (resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES
        val isInitialDark = when (initialThemeMode) {
            1 -> false
            2, 3 -> true
            else -> isSystemDark
        }
        window.decorView.setBackgroundColor(if (isInitialDark) android.graphics.Color.parseColor("#121212") else android.graphics.Color.WHITE)
        setHighRefreshRate()
        // Screenshot protection removed per user request — FLAG_SECURE is never applied
        // Only apply default state on cold start.
        // SecurityPreferences init already sets the default state based on isDisguiseModeEnabled().
        // Do not force re-disguise here, as it overrides the in-memory state during Activity recreation.
        handleNotificationIntent(intent)

        setContent {
            val themeMode by app.securityPreferences.themeMode.collectAsState(initial = 0)
            val isSystemDark = androidx.compose.foundation.isSystemInDarkTheme()
            val useDarkTheme = when (themeMode) {
                1 -> false
                2, 3 -> true
                else -> isSystemDark
            }

            DisposableEffect(useDarkTheme) {
                val insetsController = androidx.core.view.WindowCompat.getInsetsController(window, window.decorView)
                insetsController.isAppearanceLightStatusBars = !useDarkTheme
                insetsController.isAppearanceLightNavigationBars = !useDarkTheme
                insetsController.show(androidx.core.view.WindowInsetsCompat.Type.systemBars())
                window.decorView.setBackgroundColor(if (useDarkTheme) android.graphics.Color.BLACK else android.graphics.Color.WHITE)
                onDispose {}
            }

            CherishTheme(themeMode = themeMode) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = androidx.compose.material3.MaterialTheme.colorScheme.background
                ) {
                    val context = LocalContext.current
                    val permissionsToRequest = remember {
                        buildList {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                add(android.Manifest.permission.POST_NOTIFICATIONS)
                            }
                            add(android.Manifest.permission.RECORD_AUDIO)
                            add(android.Manifest.permission.CAMERA)
                        }
                    }

                    val notifPermissionLauncher = rememberLauncherForActivityResult(
                        contract = ActivityResultContracts.RequestPermission()
                    ) { _ ->
                        app.securityPreferences.setInitialNotificationPermissionRequested(true)
                    }

                    val permissionLauncher = rememberLauncherForActivityResult(
                        contract = ActivityResultContracts.RequestMultiplePermissions()
                    ) { _ ->
                        app.securityPreferences.setInitialPermissionsRequested(true)
                    }

                    LaunchedEffect(Unit) {
                        val ungranted = permissionsToRequest.filter { perm ->
                            ContextCompat.checkSelfPermission(context, perm) != PackageManager.PERMISSION_GRANTED
                        }
                        if (ungranted.isNotEmpty() && !app.securityPreferences.hasRequestedInitialPermissions()) {
                            permissionLauncher.launch(ungranted.toTypedArray())
                        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            val notifUngranted = ContextCompat.checkSelfPermission(
                                context,
                                android.Manifest.permission.POST_NOTIFICATIONS
                            ) != PackageManager.PERMISSION_GRANTED
                            if (notifUngranted && !app.securityPreferences.hasRequestedInitialNotificationPermission()) {
                                notifPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                            }
                        }
                    }

                    // Always default to hiding previous/cleared chats on fresh app launch
                    LaunchedEffect(Unit) {
                        app.securityPreferences.setShowPreviousChatsEnabled(false)
                    }

                    CherishNavGraph(app = app)
                }
            }
        }
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleNotificationIntent(intent)
    }

    private fun handleNotificationIntent(intent: android.content.Intent?) {
        if (intent == null) return
        val openNoteId = intent.getStringExtra("open_note_id")
        if (!openNoteId.isNullOrBlank()) {
            app.securityPreferences.reDisguise()
            app.pendingNoteIdFlow.value = openNoteId
            return
        }
        val isFromNotification = intent.getBooleanExtra("from_notification", false) ||
                intent.getBooleanExtra("open_chat", false) ||
                intent.hasExtra("conversationId")
        if (isFromNotification) {
            // Only reveal in-memory for this session - do NOT persist to SharedPreferences.
            // On next cold launch or minimize, the disguise re-activates correctly.
            app.securityPreferences.revealSecretApp()
        }
    }

    override fun onResume() {
        super.onResume()
        // Do NOT re-call setDecorFitsSystemWindows here — already set in onCreate.
        // Re-calling it causes a layout recalculation that shifts content after minimize/reopen.
        // Screenshot protection removed — no FLAG_SECURE applied
        app.authRepository.onAppForegroundStateChanged(true)
        app.securityPreferences.ignoreNextPause = false
        if (app.authRepository.isUserLoggedIn()) {
            val info = batteryHelper.getCurrentBattery()
            lifecycleScope.launch {
                app.authRepository.updateBatteryStatus(info.level, info.isCharging)
            }
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        // Window focus changes happen during normal in-app interactions (dialogs, dropdowns,
        // photo viewer, keyboard, bottom sheets, permission popups). NEVER trigger re-disguise here.
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (isChangingConfigurations) return
        if (!app.securityPreferences.ignoreNextPause && 
            !app.securityPreferences.isTheaterModeActive &&
            !app.securityPreferences.isMediaViewerActive &&
            !app.securityPreferences.isExternalPickerActive &&
            app.securityPreferences.isDisguiseModeEnabled()) {
            app.securityPreferences.reDisguise()
        }
    }

    override fun onPause() {
        super.onPause()
    }

    override fun onStop() {
        super.onStop()
        if (isChangingConfigurations) return
        app.authRepository.onAppForegroundStateChanged(false)
        // Re-disguise ONLY when the user truly leaves the app (activity stopped without active picker/viewer),
        // and disguise mode is enabled in user settings.
        if (!app.securityPreferences.ignoreNextPause && 
            !app.securityPreferences.isTheaterModeActive &&
            !app.securityPreferences.isMediaViewerActive &&
            !app.securityPreferences.isExternalPickerActive &&
            app.securityPreferences.isDisguiseModeEnabled()) {
            app.securityPreferences.reDisguise()
        }
        if (!app.securityPreferences.isTheaterModeActive && !app.securityPreferences.isExternalPickerActive) {
            app.securityPreferences.ignoreNextPause = false
        }
        app.googleDriveBackupManager.triggerImmediateAutoBackup()
    }

    override fun onDestroy() {
        super.onDestroy()
        batteryHelper.stop()
        app.authRepository.onAppForegroundStateChanged(false)
    }

    // Screenshot protection removed — applyScreenshotProtection() has been deleted.
    // FLAG_SECURE is never set. Users can freely take screenshots and screen recordings.

    private fun setHighRefreshRate() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val currentDisplay = display
                if (currentDisplay != null) {
                    val maxMode = currentDisplay.supportedModes.maxByOrNull { it.refreshRate }
                    if (maxMode != null) {
                        val params = window.attributes
                        params.preferredDisplayModeId = maxMode.modeId
                        params.preferredRefreshRate = maxMode.refreshRate
                        window.attributes = params
                    }
                }
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val wm = getSystemService(Context.WINDOW_SERVICE) as? WindowManager
                @Suppress("DEPRECATION")
                val currentDisplay = wm?.defaultDisplay
                val maxMode = currentDisplay?.supportedModes?.maxByOrNull { it.refreshRate }
                if (maxMode != null) {
                    val params = window.attributes
                    params.preferredDisplayModeId = maxMode.modeId
                    params.preferredRefreshRate = maxMode.refreshRate
                    window.attributes = params
                }
            }
        } catch (_: Exception) {}
    }
}

package com.example

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.security.PrivacyShield
import com.example.ui.navigation.CherishNavGraph
import com.example.ui.theme.CherishTheme
import com.example.util.BatteryStatusHelper
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class MainActivity : FragmentActivity() {

    private val app: CherishApplication
        get() = application as CherishApplication

    private val batteryHelper by lazy { BatteryStatusHelper(this) }

    /** Whether the secret app may be what's on screen; stays true until Notes has been drawn. */
    private var secretAppOnScreen = false

    /** Drawn over everything in the window while PrivacyShield is up, so previews show nothing. */
    private var privacyCover: View? = null
    private var privacyCoverColor = android.graphics.Color.WHITE

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Battery tracking and the auto-backup start once the first screen is on screen, so they
        // never hold up opening the app
        afterFirstFrame {
            batteryHelper.start()
            lifecycleScope.launch {
                batteryHelper.batteryInfo.collect { info ->
                    if (app.authRepository.isUserLoggedIn()) {
                        app.authRepository.updateBatteryStatus(info.level, info.isCharging)
                    }
                }
            }
            app.googleDriveBackupManager.start()
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
        // Screenshots inside the app are allowed; only previews (recent apps) hide the secret chat
        // (see setSecretAppOnScreen and PrivacyShield)
        // Only apply default state on cold start.
        // SecurityPreferences init already sets the default state based on isDisguiseModeEnabled().
        // Do not force re-disguise here, as it overrides the in-memory state during Activity recreation.
        handleNotificationIntent(intent)

        // The recents screen must never show the secret chat: its card goes blank while the chat
        // is open, and shows the Notes screen like any notes app otherwise
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.RESUMED) {
                app.securityPreferences.isDisguiseActive.collectLatest { disguised ->
                    if (disguised) {
                        // Only once Notes has been drawn over the chat
                        delay(300)
                        setSecretAppOnScreen(false)
                    } else {
                        setSecretAppOnScreen(true)
                    }
                }
            }
        }

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
                privacyCoverColor = if (useDarkTheme) android.graphics.Color.BLACK else android.graphics.Color.WHITE
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

                    CherishNavGraph(app = app)
                }
            }
        }
        installPrivacyCover()
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

    /** Adds the privacy cover above all content; PrivacyShield shows and hides it without waiting for a frame. */
    private fun installPrivacyCover() {
        val cover = View(this).apply {
            visibility = View.GONE
            // Nothing underneath reacts while it's up
            isClickable = true
        }
        (window.decorView as ViewGroup).addView(
            cover,
            ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        )
        privacyCover = cover
        PrivacyShield.onChange = { raised ->
            if (raised) cover.setBackgroundColor(privacyCoverColor)
            cover.visibility = if (raised) View.VISIBLE else View.GONE
        }
    }

    /** Covers the chat when the app is on its way out (home, recent apps, another app). */
    private fun coverSecretApp() {
        if (secretAppOnScreen) PrivacyShield.raise()
    }

    private fun setSecretAppOnScreen(onScreen: Boolean) {
        secretAppOnScreen = onScreen
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // Hides only the recents preview; screenshots inside the app keep working
            setRecentsScreenshotEnabled(!onScreen)
        }
    }

    override fun onResume() {
        super.onResume()
        PrivacyShield.lower()
        // Do NOT re-call setDecorFitsSystemWindows here — already set in onCreate.
        // Re-calling it causes a layout recalculation that shifts content after minimize/reopen.
        app.authRepository.onAppForegroundStateChanged(true)
        app.securityPreferences.ignoreNextPause = false
        // Ends an installer trip, or continues an update install that waited for permission
        com.example.update.OtaUpdateManager.getInstance(this).onAppResumed(this)
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
        // Only the privacy cover reacts: the moment focus leaves for the system (recent apps,
        // notification shade) the chat is covered, and uncovered when it's back.
        if (hasFocus) {
            PrivacyShield.lower()
        } else if (!isInMultiWindowMode && PrivacyShield.isLeavingApp()) {
            coverSecretApp()
        }
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        PrivacyShield.onTouch(ev)
        val handled = super.dispatchTouchEvent(ev)
        // The system took this touch for its home/recents swipe after focus had already left
        if (ev.actionMasked == MotionEvent.ACTION_CANCEL && !hasWindowFocus() && !isInMultiWindowMode) {
            coverSecretApp()
        }
        return handled
    }

    override fun onUserLeaveHint() {
        coverSecretApp()
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
        coverSecretApp()
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
        app.googleDriveBackupManager.flushPendingBackup()
    }

    override fun onDestroy() {
        super.onDestroy()
        if (privacyCover != null) PrivacyShield.onChange = null
        batteryHelper.stop()
        app.authRepository.onAppForegroundStateChanged(false)
    }

    // Screenshots and screen recordings inside the app are allowed: FLAG_SECURE is never set.
    // Recent-apps previews are covered instead (PrivacyShield, setRecentsScreenshotEnabled).

    /**
     * Runs [block] once the first frame has been drawn (posted from just before that draw), so
     * work in it can't delay the app appearing. Skipped if the activity is already gone.
     */
    private fun afterFirstFrame(block: () -> Unit) {
        val decor = window.decorView
        androidx.core.view.OneShotPreDrawListener.add(decor) {
            decor.post { if (!isDestroyed && !isFinishing) block() }
        }
    }

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

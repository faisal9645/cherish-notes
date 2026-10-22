package com.example

import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Toast
import com.example.data.model.MessageType
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.SystemBarStyle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.example.call.FloatingCallBubble
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

open class MainActivity : FragmentActivity() {

    private val app: CherishApplication
        get() = application as CherishApplication

    private val batteryHelper by lazy { BatteryStatusHelper(this) }

    /** Whether the secret app may be what's on screen; stays true until Notes has been drawn. */
    private var secretAppOnScreen = false

    /** Cherish (not Notes) was on screen when the activity paused. */
    private var cherishOnScreenAtPause = false

    // The phone locked (or its screen timed out) with Cherish on screen: the app goes to the
    // background, so unlocking shows the home screen, neither Notes nor Cherish
    private val screenOffReceiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: android.content.Intent?) {
            if (intent?.action == android.content.Intent.ACTION_SCREEN_OFF) leaveIfLockedInCherish()
        }
    }

    /** Drawn over everything in the window while PrivacyShield is up, so previews show nothing. */
    private var privacyCover: View? = null
    private var privacyCoverColor = android.graphics.Color.WHITE

    override fun onCreate(savedInstanceState: Bundle?) {
        val isCalc = try {
            val comp = intent?.component?.className ?: ""
            if (comp.endsWith("CalculatorAlias")) {
                true
            } else if (comp.endsWith("NotesAlias")) {
                false
            } else {
                app.securityPreferences.isDisguiseModeEnabled() &&
                    app.securityPreferences.getDisguiseStyle() == com.example.security.SecurityPreferences.DISGUISE_STYLE_CALCULATOR
            }
        } catch (_: Exception) { false }
        if (isCalc) {
            setTheme(R.style.Theme_MyApplication_Calculator)
        }
        super.onCreate(savedInstanceState)
        try {
            ContextCompat.registerReceiver(
                this,
                screenOffReceiver,
                android.content.IntentFilter(android.content.Intent.ACTION_SCREEN_OFF),
                ContextCompat.RECEIVER_NOT_EXPORTED
            )
        } catch (e: Exception) {
            Log.w("MainActivity", "Couldn't watch for the screen locking", e)
        }
        // The auto-backup starts once the first screen is on screen, so it never holds up opening
        // the app. (The battery isn't watched continuously: it's read once when the app opens, in
        // onResume, and refreshed every few minutes only while the Love & Us tab is on screen.)
        afterFirstFrame {
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
        window.decorView.setBackgroundColor(if (isInitialDark) android.graphics.Color.BLACK else android.graphics.Color.WHITE)
        setHighRefreshRate()
        // Screenshots inside the app are allowed; only previews (recent apps) hide the secret chat
        // (see setSecretAppOnScreen and PrivacyShield)
        // Only a fresh launch (the icon, a notification, the call screen) decides what shows. Being
        // rebuilt (dark mode switching on, display size) or reopened from Recents after Android
        // closed the app replays the launch's intent: that must neither reveal Cherish nor hide an
        // open chat (an app started again from scratch is behind Notes anyway)
        val isFreshLaunch = savedInstanceState == null &&
            ((intent?.flags ?: 0) and android.content.Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY) == 0
        if (isFreshLaunch) {
            intent?.let { applyLaunch(it) }
            handleNotificationIntent(intent)
        }

        // The recents screen must never show the secret chat: its card goes blank while the chat
        // is open, and shows the Notes screen like any notes app otherwise
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.RESUMED) {
                kotlinx.coroutines.flow.combine(
                    app.securityPreferences.isDisguiseActive,
                    app.securityPreferences.calculatorDecoy,
                    app.securityPreferences.disguiseStyle
                ) { disguised, calculator, style ->
                    Triple(disguised, calculator, style)
                }.collectLatest { (disguised, calculator, style) ->
                    val isCalc = if (disguised) calculator else (style == com.example.security.SecurityPreferences.DISGUISE_STYLE_CALCULATOR)
                    updateTaskDescription(isCalc)
                    if (disguised) {
                        // Only once Notes/Calculator has been drawn over the chat
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
                            if (notifUngranted) {
                                notifPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                            }
                        }
                    }

                    Box(modifier = Modifier.fillMaxSize()) {
                        CherishNavGraph(app = app)
                        FloatingCallBubble()
                    }
                }
            }
        }
        installPrivacyCover()
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        applyLaunch(intent)
        handleNotificationIntent(intent)
    }

    /**
     * What a launch shows: the icon opens Notes or the Calculator (with Disguise Mode off, Cherish
     * itself, the app lock still asking first); anything else that isn't a notification opens behind
     * the disguise. Notifications decide for themselves (handleNotificationIntent).
     */
    private fun applyLaunch(intent: android.content.Intent) {
        val prefs = app.securityPreferences
        val isFromNotification = intent.getBooleanExtra("from_notification", false) ||
                intent.getBooleanExtra("open_chat", false) ||
                intent.hasExtra("conversationId")
        val targetComponent = intent.component?.className ?: ""
        val fromIcon = targetComponent.endsWith("CalculatorAlias") || targetComponent.endsWith("NotesAlias")
        if (fromIcon) {
            if (prefs.isDisguiseModeEnabled()) {
                prefs.setDisguiseStyle(
                    if (targetComponent.endsWith("CalculatorAlias")) com.example.security.SecurityPreferences.DISGUISE_STYLE_CALCULATOR
                    else com.example.security.SecurityPreferences.DISGUISE_STYLE_NOTES
                )
                prefs.forceDisguise()
            } else {
                // Not the style: with Disguise Mode off the icon is Notes whatever was picked, and
                // the pick is kept for when it's turned back on
                prefs.revealSecretApp()
            }
        } else if (!isFromNotification && prefs.isDisguiseModeEnabled()) {
            prefs.forceDisguise()
        }
    }

    private fun handleNotificationIntent(intent: android.content.Intent?) {
        if (intent == null) return
        val openNoteId = intent.getStringExtra("open_note_id")
        if (!openNoteId.isNullOrBlank()) {
            com.example.notifications.NotificationHelper.cancelNoteReminderNotification(this, openNoteId)
            com.example.notifications.NotificationHelper.clearNotifications(this)
            // A note's reminder always opens Notes with that note: also with Disguise Mode off, or
            // just after a call (nothing that holds Cherish open applies here)
            app.securityPreferences.forceDisguise()
            if (!app.securityPreferences.isCalculatorDecoy()) {
                app.pendingNoteIdFlow.value = openNoteId
            }
            return
        }
        com.example.notifications.NotificationHelper.clearNotifications(this)
        val isFromNotification = intent.getBooleanExtra("from_notification", false) ||
                intent.getBooleanExtra("open_chat", false) ||
                intent.getBooleanExtra("from_call_ended", false) ||
                intent.hasExtra("conversationId")
        if (isFromNotification) {
            // Its alert was seen, even if only Notes opens: the partner's next message alerts again
            app.authRepository.markMessageAlertSeen()
            val isDiscreet = intent.getBooleanExtra("is_discreet", false)
            if (isDiscreet) {
                // Disguised notification: keep or restore safe Notes disguise
                app.securityPreferences.reDisguise()
            } else {
                // Original notification: reveal secret Cherish app and display chat
                app.securityPreferences.revealSecretApp()
            }
            val heartbeatSignalId = intent.getStringExtra("heartbeat_signal_id")
            if (!heartbeatSignalId.isNullOrBlank()) {
                app.authRepository.acknowledgeHeartbeatFelt(heartbeatSignalId)
            }
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
        val callback: (Boolean) -> Unit = { raised ->
            if (raised) cover.setBackgroundColor(privacyCoverColor)
            cover.visibility = if (raised) View.VISIBLE else View.GONE
        }
        privacyCoverCallback = callback
        PrivacyShield.onChange = callback
    }

    /** This screen's own cover hook, so closing another copy of the screen never takes this one's away. */
    private var privacyCoverCallback: ((Boolean) -> Unit)? = null

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
        // Exclude task from recents and usage stats tracking when secret app is on screen
        try {
            val am = getSystemService(ACTIVITY_SERVICE) as? android.app.ActivityManager
            am?.appTasks?.forEach { task ->
                task.setExcludeFromRecents(onScreen)
            }
        } catch (_: Exception) {}
    }

    /** Set when the app comes back, so the next window focus can drop a left-over keyboard. */
    private var hideKeyboardOnFocus = false

    override fun onStart() {
        super.onStart()
        com.example.notifications.NotificationHelper.clearNotifications(this)
    }

    override fun onResume() {
        super.onResume()
        // Clear any notifications from status bar since user is actively inside the Cherish app
        com.example.notifications.NotificationHelper.clearNotifications(this)
        hideKeyboardOnFocus = true
        applyScreenshotProtection()
        PrivacyShield.lower()
        // Do NOT re-call setDecorFitsSystemWindows here — already set in onCreate.
        // Re-calling it causes a layout recalculation that shifts content after minimize/reopen.
        app.authRepository.onAppForegroundStateChanged(true)
        val currentUid = app.authRepository.getCurrentUserId()
        if (currentUid.isNotBlank()) {
            app.authRepository.updateFcmToken(currentUid)
        }
        app.securityPreferences.ignoreNextPause = false
        // (A call ending never reveals Cherish here: only one that began in it goes back to it)
        // Ends an installer trip, or continues an update install that waited for permission
        com.example.update.OtaUpdateManager.getInstance(this).onAppResumed(this)
        // Back from the battery or Auto-start settings
        com.example.notifications.BackgroundDelivery.onAppResumed(this)
        if (!app.securityPreferences.isDisguiseActive()) {
            val callPhase = com.example.call.CallManager.state.value.phase
            val invite = com.example.call.CallManager.currentInvite
            if (callPhase == com.example.call.CallPhase.INCOMING_RINGING && invite != null) {
                try {
                    startActivity(com.example.call.CallActivity.incomingIntent(this, invite).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
                } catch (e: Exception) {
                    Log.w("MainActivity", "Could not open incoming call on resume", e)
                }
            }
        }
        if (app.authRepository.isUserLoggedIn()) {
            val info = batteryHelper.getCurrentBattery()
            lifecycleScope.launch {
                app.authRepository.updateBatteryStatus(info.level, info.isCharging)
                app.authRepository.updateNotificationStatus()
                // Tell the partner about a clean reinstall once we're signed in again (prefs recorded
                // it on the fresh start; the profile may not have it yet)
                val localReinstall = app.securityPreferences.getLastReinstallAt()
                val synced = app.authRepository.currentUserState.value?.lastReinstallAt ?: 0L
                if (localReinstall > 0L && localReinstall > synced) {
                    app.authRepository.updateReinstallStatus(localReinstall)
                }
            }
            // Morning unlock: resets "Asleep 🌙" status to active when partner or user opens phone in morning
            val currentStatus = app.authRepository.currentUserState.value?.statusMessage
            if (currentStatus == "Asleep 🌙") {
                val hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
                if (hour in 5..12) {
                    app.coupleFeaturesRepository.wakeUpFromSleep()
                }
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
            com.example.notifications.NotificationHelper.clearNotifications(this)
            PrivacyShield.lower()
            // Back in the app on Notes: no keyboard left over from the chat's message box underneath
            // (only after coming back from outside, not when an in-app dialog closes)
            val justCameBack = hideKeyboardOnFocus
            hideKeyboardOnFocus = false
            if (justCameBack && app.securityPreferences.isDisguiseActive()) {
                currentFocus?.clearFocus()
                (getSystemService(INPUT_METHOD_SERVICE) as? android.view.inputmethod.InputMethodManager)
                    ?.hideSoftInputFromWindow(window.decorView.windowToken, 0)
            }
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
        // With a call on (or its screen opening, or a call ended recently), do not re-disguise
        if (com.example.call.CallManager.hasActiveCall() ||
            com.example.call.CallManager.isCallScreenVisible ||
            app.securityPreferences.wasInCallRecently()) return
        // A full-screen video or the Watch Party doesn't hold Cherish open: going home from it hides it
        if (!app.securityPreferences.ignoreNextPause &&
            !app.securityPreferences.isExternalPickerActive &&
            app.securityPreferences.isDisguiseModeEnabled()) {
            app.securityPreferences.reDisguise()
        }
    }

    override fun onPause() {
        cherishOnScreenAtPause = !app.securityPreferences.isDisguiseActive()
        coverSecretApp()
        super.onPause()
    }

    /**
     * The phone locked (see screenOffReceiver). Whatever was holding Cherish open behind the app (a
     * photo picker or the camera opened from the chat, a call that just ended), locking hides it and
     * counts as leaving for the app lock. With Cherish on screen the app also goes to the background,
     * so unlocking shows the home screen. Not while a call is on.
     */
    private fun leaveIfLockedInCherish() {
        if (isFinishing || isChangingConfigurations) return
        if (com.example.call.CallManager.hasActiveCall() ||
            com.example.call.CallManager.isCallScreenVisible) return
        val prefs = app.securityPreferences
        val cherishShown = if (lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED)) {
            !prefs.isDisguiseActive()
        } else {
            cherishOnScreenAtPause
        }
        if (!prefs.isDisguiseActive() && prefs.isDisguiseModeEnabled()) prefs.forceDisguise()
        app.onScreenLockedWhileAway()
        if (!cherishShown) return
        cherishOnScreenAtPause = false
        moveTaskToBack(true)
    }

    override fun onStop() {
        super.onStop()
        if (isChangingConfigurations) return
        // Stopped by the screen going off (it may beat the screen-off broadcast)
        val isInteractive = (getSystemService(POWER_SERVICE) as? android.os.PowerManager)?.isInteractive != false
        if (!isInteractive) leaveIfLockedInCherish() else cherishOnScreenAtPause = false
        app.authRepository.onAppForegroundStateChanged(false)
        // Re-disguise ONLY when the user truly leaves the app (activity stopped without active picker/viewer),
        // and disguise mode is enabled in user settings. The call screen covering the chat isn't leaving
        // (going home from the call screen is: the whole app stops then, see CherishApplication).
        if (!app.securityPreferences.ignoreNextPause &&
            !app.securityPreferences.isExternalPickerActive &&
            !app.securityPreferences.wasInCallRecently() &&
            !com.example.call.CallManager.isCallScreenVisible &&
            !com.example.call.CallManager.hasActiveCall() &&
            app.securityPreferences.isDisguiseModeEnabled()) {
            app.securityPreferences.reDisguise()
        }
        if (!app.securityPreferences.isExternalPickerActive) {
            app.securityPreferences.ignoreNextPause = false
        }
        app.googleDriveBackupManager.flushPendingBackup()
        app.coupleFeaturesRepository.stopFeatureListeners()
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            unregisterReceiver(screenOffReceiver)
        } catch (_: Exception) {}
        if (privacyCoverCallback != null && PrivacyShield.onChange === privacyCoverCallback) PrivacyShield.onChange = null
        batteryHelper.stop()
        app.authRepository.onAppForegroundStateChanged(false)
    }

    // Screenshots and screen recordings are allowed unless Settings > Screenshot Protection is on.
    // Recent-apps previews are always covered (PrivacyShield, setRecentsScreenshotEnabled).
    private fun applyScreenshotProtection() {
        val flag = android.view.WindowManager.LayoutParams.FLAG_SECURE
        if (app.securityPreferences.isScreenshotProtectionEnabled()) window.setFlags(flag, flag)
        else window.clearFlags(flag)
    }

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


    private fun updateTaskDescription(isCalc: Boolean) {
        try {
            val label = if (isCalc) "Calculator" else "Notes"
            title = label
            val themeMode = try { app.securityPreferences.getThemeMode() } catch (_: Exception) { 0 }
            val isSystemDark = (resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES
            val isDark = when (themeMode) {
                1 -> false
                2, 3 -> true
                else -> isSystemDark
            }
            val color = if (isDark) android.graphics.Color.BLACK else android.graphics.Color.WHITE
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                val iconRes = if (isCalc) R.mipmap.ic_calculator_launcher else R.mipmap.ic_launcher
                setTaskDescription(
                    android.app.ActivityManager.TaskDescription.Builder()
                        .setLabel(label)
                        .setIcon(iconRes)
                        .setPrimaryColor(color)
                        .build()
                )
            } else {
                val iconRes = if (isCalc) R.drawable.ic_samsung_calculator else R.drawable.ic_notes_app
                val bmp = android.graphics.BitmapFactory.decodeResource(resources, iconRes)
                @Suppress("DEPRECATION")
                setTaskDescription(android.app.ActivityManager.TaskDescription(label, bmp, color))
            }
        } catch (_: Throwable) {}
    }
}

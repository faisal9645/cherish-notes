package com.example

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.graphics.Color as AndroidColor
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.DisposableEffect
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.example.ui.navigation.CherishNavGraph
import com.example.ui.theme.CherishTheme

class MainActivity : FragmentActivity() {

    private val app: CherishApplication
        get() = application as CherishApplication

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        androidx.core.view.WindowCompat.setDecorFitsSystemWindows(window, false)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.auto(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT)
        )
        setHighRefreshRate()
        applyScreenshotProtection()
        if (app.securityPreferences.isDisguiseModeEnabled()) {
            app.securityPreferences.reDisguise()
        } else {
            app.securityPreferences.revealSecretApp()
        }
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
                        }
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
        val isFromNotification = intent.getBooleanExtra("from_notification", false) ||
                intent.getBooleanExtra("open_chat", false) ||
                intent.hasExtra("conversationId")
        if (isFromNotification) {
            app.securityPreferences.revealSecretApp()
        }
    }

    override fun onResume() {
        super.onResume()
        app.securityPreferences.revealSecretApp()
        applyScreenshotProtection()
        app.authRepository.onAppForegroundStateChanged(true)
        app.securityPreferences.ignoreNextPause = false
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        // Keep user strictly inside Chat - do not switch to Notes on focus changes
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        // Do not auto-switch to Notes on leaving hint
    }

    override fun onPause() {
        super.onPause()
        // Stable lifecycle: do not toggle window secure flags or force disguise on pause
    }

    override fun onStop() {
        super.onStop()
        app.authRepository.onAppForegroundStateChanged(false)
    }

    override fun onDestroy() {
        super.onDestroy()
        app.authRepository.onAppForegroundStateChanged(false)
    }

    private fun applyScreenshotProtection() {
        val isProtected = app.securityPreferences.isScreenshotProtectionEnabled()
        val currentFlags = window.attributes.flags
        val hasSecure = (currentFlags and WindowManager.LayoutParams.FLAG_SECURE) != 0
        if (isProtected != hasSecure) {
            if (isProtected) {
                window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
            } else {
                window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
            }
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

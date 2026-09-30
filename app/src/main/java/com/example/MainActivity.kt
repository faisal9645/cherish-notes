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
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.fragment.app.FragmentActivity
import com.example.ui.navigation.CherishNavGraph
import com.example.ui.theme.CherishTheme

class MainActivity : FragmentActivity() {

    private val app: CherishApplication
        get() = application as CherishApplication

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setHighRefreshRate()

        applyScreenshotProtection()

        setContent {
            val themeMode by app.securityPreferences.themeMode.collectAsState(initial = 0)
            val isSystemDark = androidx.compose.foundation.isSystemInDarkTheme()
            val useDarkTheme = when (themeMode) {
                1 -> false
                2 -> true
                else -> isSystemDark
            }

            LaunchedEffect(useDarkTheme) {
                window.decorView.setBackgroundColor(if (useDarkTheme) android.graphics.Color.BLACK else android.graphics.Color.WHITE)
                WindowCompat.getInsetsController(window, window.decorView).apply {
                    isAppearanceLightStatusBars = !useDarkTheme
                    isAppearanceLightNavigationBars = !useDarkTheme
                }
            }

            CherishTheme(darkTheme = useDarkTheme) {
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

    private var lastBackgroundTimestamp: Long = 0L
    private var disguiseView: android.view.View? = null

    private fun showRecentsDisguise() {
        if (disguiseView == null) {
            disguiseView = android.view.LayoutInflater.from(this).inflate(R.layout.layout_recents_disguise, null)
        }
        val decorView = window.decorView as? android.view.ViewGroup
        if (disguiseView?.parent == null) {
            decorView?.addView(disguiseView)
        }
    }

    private fun hideRecentsDisguise() {
        val decorView = window.decorView as? android.view.ViewGroup
        disguiseView?.let { view ->
            decorView?.removeView(view)
        }
    }

    override fun onResume() {
        super.onResume()
        setHighRefreshRate()
        applyScreenshotProtection()
        app.authRepository.onAppForegroundStateChanged(true)
        // Reset the ignore flag when returning to the app
        app.securityPreferences.ignoreNextPause = false
        hideRecentsDisguise()
    }

    override fun onPause() {
        super.onPause()
        lastBackgroundTimestamp = System.currentTimeMillis()

        if (!app.securityPreferences.ignoreNextPause) {
            // Immediate Disguise Protection: Revert to normal notes as soon as the app is minimized
            if (app.securityPreferences.isDisguiseModeEnabled()) {
                app.securityPreferences.reDisguise()
            }
            if (app.securityPreferences.hasPin() && app.securityPreferences.isAppLockEnabled()) {
                app.securityPreferences.lockApp()
            }
            
            // Show Native Notes layout over the screen for the Recents Snapshot
            showRecentsDisguise()
        }

        // Ensure recent-apps preview is redacted when leaving secret mode (unless showing the fake notes overlay)
        if (app.securityPreferences.isScreenshotProtectionEnabled()) {
            window.setFlags(
                WindowManager.LayoutParams.FLAG_SECURE,
                WindowManager.LayoutParams.FLAG_SECURE
            )
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
    }

    override fun onStop() {
        super.onStop()
        app.authRepository.onAppForegroundStateChanged(false)
        if (!app.securityPreferences.ignoreNextPause) {
            if (app.securityPreferences.isDisguiseModeEnabled()) {
                app.securityPreferences.reDisguise()
            }
            if (app.securityPreferences.hasPin() && app.securityPreferences.isAppLockEnabled()) {
                app.securityPreferences.lockApp()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        app.authRepository.onAppForegroundStateChanged(false)
        hideRecentsDisguise()
    }

    private fun applyScreenshotProtection() {
        val isProtected = app.securityPreferences.isScreenshotProtectionEnabled()
        if (isProtected) {
            window.setFlags(
                WindowManager.LayoutParams.FLAG_SECURE,
                WindowManager.LayoutParams.FLAG_SECURE
            )
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
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

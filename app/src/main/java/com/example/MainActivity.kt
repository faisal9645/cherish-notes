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

        // Explicitly set window background to pure white
        window.decorView.setBackgroundColor(AndroidColor.WHITE)
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = true
            isAppearanceLightNavigationBars = true
        }

        applyScreenshotProtection()

        setContent {
            CherishTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = Color.White
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

    override fun onResume() {
        super.onResume()
        setHighRefreshRate()
        applyScreenshotProtection()
        app.authRepository.onAppForegroundStateChanged(true)
        // Reset the ignore flag when returning to the app
        app.securityPreferences.ignoreNextPause = false
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
        }

        // Ensure recent-apps preview is redacted when leaving secret mode
        if (app.securityPreferences.isScreenshotProtectionEnabled() || !app.securityPreferences.isDisguiseActive.value) {
            window.setFlags(
                WindowManager.LayoutParams.FLAG_SECURE,
                WindowManager.LayoutParams.FLAG_SECURE
            )
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

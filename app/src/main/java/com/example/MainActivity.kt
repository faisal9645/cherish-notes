package com.example

import android.content.Context
import android.os.Build
import android.graphics.Color as AndroidColor
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
        app.authRepository.setOnline(true)
    }

    override fun onPause() {
        super.onPause()
        app.authRepository.setOnline(false)
        lastBackgroundTimestamp = System.currentTimeMillis()

        // Immediate Disguise Protection: Revert to normal notes as soon as the app is minimized
        if (app.securityPreferences.isDisguiseModeEnabled()) {
            app.securityPreferences.reDisguise()
        }
        if (app.securityPreferences.hasPin() && app.securityPreferences.isAppLockEnabled()) {
            app.securityPreferences.lockApp()
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
        app.authRepository.setOnline(false)
        if (app.securityPreferences.isDisguiseModeEnabled()) {
            app.securityPreferences.reDisguise()
        }
        if (app.securityPreferences.hasPin() && app.securityPreferences.isAppLockEnabled()) {
            app.securityPreferences.lockApp()
        }
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

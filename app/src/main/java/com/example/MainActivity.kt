package com.example

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

    override fun onResume() {
        super.onResume()
        applyScreenshotProtection()
        app.authRepository.setOnline(true)
    }

    override fun onPause() {
        super.onPause()
        app.authRepository.setOnline(false)
    }

    override fun onStop() {
        super.onStop()
        if (app.securityPreferences.isDisguiseModeEnabled()) {
            app.securityPreferences.reDisguise()
        }
        // Lock app when user leaves if app lock is enabled
        if (app.securityPreferences.isAppLockEnabled() && app.securityPreferences.hasPin()) {
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
}

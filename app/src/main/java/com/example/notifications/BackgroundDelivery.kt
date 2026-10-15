package com.example.notifications

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import com.example.security.SecurityPreferences

/**
 * Messages and calls reach a closed Cherish as push notifications, like WhatsApp and Telegram:
 * nothing of the app runs in the background. Some phones (Xiaomi, Oppo, Vivo, Realme, OnePlus...)
 * still keep a closed app from being woken by its pushes unless its battery use is "Unrestricted",
 * and some also want it allowed to start by itself ("Auto-start"). Only from Settings, when asked.
 */
object BackgroundDelivery {
    private const val TAG = "BackgroundDelivery"

    /** Phone makers that add their own "Auto-start" switch for apps. */
    private val AUTO_START_MAKERS = setOf(
        "xiaomi", "redmi", "poco", "oppo", "realme", "oneplus", "vivo", "iqoo", "huawei", "honor", "asus", "letv"
    )

    /** Where those makers keep it, newest first; the first one this phone has is opened. */
    private val AUTO_START_SCREENS = listOf(
        ComponentName("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity"),
        ComponentName("com.coloros.safecenter", "com.coloros.safecenter.permission.startup.StartupAppListActivity"),
        ComponentName("com.coloros.safecenter", "com.coloros.safecenter.startupapp.StartupAppListActivity"),
        ComponentName("com.oplus.safecenter", "com.oplus.safecenter.permission.startup.StartupAppListActivity"),
        ComponentName("com.oppo.safe", "com.oppo.safe.permission.startup.StartupAppListActivity"),
        ComponentName("com.vivo.permissionmanager", "com.vivo.permissionmanager.activity.BgStartUpManagerActivity"),
        ComponentName("com.iqoo.secure", "com.iqoo.secure.ui.phoneoptimize.AddWhiteListActivity"),
        ComponentName("com.huawei.systemmanager", "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity"),
        ComponentName("com.huawei.systemmanager", "com.huawei.systemmanager.optimize.process.ProtectActivity"),
        ComponentName("com.oneplus.security", "com.oneplus.security.chainlaunch.view.ChainLaunchAppListActivity"),
        ComponentName("com.asus.mobilemanager", "com.asus.mobilemanager.autostart.AutoStartActivity"),
        ComponentName("com.letv.android.letvsafe", "com.letv.android.letvsafe.AutobootManageActivity")
    )

    @Volatile private var awaitingReturn = false

    /** Battery use is "Unrestricted": the phone delivers pushes to the closed app. */
    fun isUnrestricted(context: Context): Boolean = NotificationHelper.isBatteryOptimizationIgnored(context)

    /** This phone's maker has its own Auto-start switch (worth turning on too). */
    fun hasAutoStartSetting(): Boolean = Build.MANUFACTURER.lowercase() in AUTO_START_MAKERS

    /** The system's question (or its battery list where the question isn't offered). */
    fun requestUnrestricted(context: Context) {
        leavingForSettings(context)
        NotificationHelper.requestIgnoreBatteryOptimization(context)
    }

    /** The maker's Auto-start screen, else the app's own settings page (battery is there too). */
    fun openAutoStart(context: Context) {
        leavingForSettings(context)
        for (screen in AUTO_START_SCREENS) {
            try {
                context.startActivity(Intent().setComponent(screen).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                return
            } catch (_: Exception) {
                // Not on this phone (or not allowed): the next one
            }
        }
        try {
            context.startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        } catch (e: Exception) {
            Log.w(TAG, "No settings screen to open", e)
            onAppResumed(context)
        }
    }

    /** Back from those settings: leaving the app afterwards hides it behind Notes again. */
    fun onAppResumed(context: Context) {
        if (!awaitingReturn) return
        awaitingReturn = false
        SecurityPreferences.getInstance(context).isExternalPickerActive = false
    }

    /** A trip to the phone's settings isn't leaving the app: it comes back to the chat, not Notes. */
    private fun leavingForSettings(context: Context) {
        val prefs = SecurityPreferences.getInstance(context)
        prefs.ignoreNextPause = true
        prefs.isExternalPickerActive = true
        awaitingReturn = true
    }
}

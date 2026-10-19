package com.example.notifications

import android.app.AppOpsManager
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
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

    /**
     * Android lets the app bring its call screen up by itself ("Display over other apps"): on a locked
     * phone, as some phones hold back a call notification's full screen. In another app a call is its
     * notification's banner instead (see CallManager.onIncomingInvite).
     */
    fun canOpenCallScreen(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(context)

    /** A ringing call may take over a locked phone's screen (Android 14 lets an app be denied that). */
    fun canUseFullScreen(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) return true
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return true
        return manager.canUseFullScreenIntent()
    }

    /** Both of those: an incoming call shows its full call screen wherever the phone is. */
    fun isCallScreenAllowed(context: Context): Boolean = canOpenCallScreen(context) && canUseFullScreen(context)

    /**
     * An incoming call opens its call screen by itself when the app isn't open: allowed by the
     * phone, and not when Incoming Call Privacy is Discreet (that one shows the disguised alert).
     */
    fun mayOpenCallScreen(context: Context): Boolean =
        !SecurityPreferences.getInstance(context).getCallMaskingStyle().equals("discreet", ignoreCase = true) &&
            canOpenCallScreen(context)

    /** The phone's page for what's still missing for the call screen (see isCallScreenAllowed). */
    fun openCallScreenSettings(context: Context) {
        leavingForSettings(context)
        val app = Uri.parse("package:${context.packageName}")
        val page = when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE && !canUseFullScreen(context) ->
                Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, app)
            !canOpenCallScreen(context) -> Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, app)
            else -> Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, app)
        }
        try {
            context.startActivity(page.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e: Exception) {
            try {
                context.startActivity(
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, app).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            } catch (e2: Exception) {
                Log.w(TAG, "No settings screen to open", e2)
                onAppResumed(context)
            }
        }
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

    // ---------- Every switch this phone needs, for the setup sheet ----------

    /** Who made the phone, for its own battery and start-up switches. */
    enum class Maker { XIAOMI, VIVO, SAMSUNG, OPPO, HUAWEI, OTHER }

    fun maker(): Maker = when (Build.MANUFACTURER.lowercase()) {
        "xiaomi", "redmi", "poco" -> Maker.XIAOMI
        "vivo", "iqoo" -> Maker.VIVO
        "samsung" -> Maker.SAMSUNG
        "oppo", "realme", "oneplus" -> Maker.OPPO
        "huawei", "honor" -> Maker.HUAWEI
        else -> Maker.OTHER
    }

    /**
     * One switch that lets pushes wake the closed app (or a call open its screen). [isOn] is null
     * where the phone doesn't tell apps: the user ticks it once done. [required]: without it,
     * messages or calls can go missing; the others make calls nicer.
     */
    class Step(
        val key: String,
        val title: String,
        val detail: String,
        val isOn: Boolean?,
        val required: Boolean,
        val open: (Context) -> Unit
    )

    fun steps(context: Context): List<Step> {
        val maker = maker()
        val steps = mutableListOf<Step>()
        steps += Step(
            "notifications", "Notifications allowed",
            "So messages and calls can show at all.",
            isOn = androidx.core.app.NotificationManagerCompat.from(context).areNotificationsEnabled(),
            required = true,
            open = ::openNotificationSettings
        )
        steps += Step(
            "battery",
            when (maker) {
                Maker.XIAOMI -> "Allow running in the background"
                Maker.VIVO -> "Allow background power use"
                Maker.OPPO -> "Allow background activity"
                else -> "Battery: Unrestricted"
            },
            "Without it the phone holds back pushes while Notes is closed.",
            isOn = isUnrestricted(context),
            required = true,
            open = ::requestUnrestricted
        )
        when (maker) {
            Maker.XIAOMI -> {
                steps += Step(
                    "autostart", "Autostart",
                    "Security > Permissions > Autostart: turn on Notes. Without it a swiped-away Notes can't wake for a push.",
                    isOn = miuiOpAllowed(context, MIUI_OP_AUTO_START),
                    required = true,
                    open = ::openAutoStart
                )
                steps += Step(
                    "miui_battery", "Battery saver: No restrictions",
                    "Pick \"No restrictions\" for Notes (MIUI and HyperOS keep their own battery list).",
                    isOn = null,
                    required = true,
                    open = ::openMakerBattery
                )
                steps += Step(
                    "miui_popups", "Pop-up windows while in background",
                    "Lets the call screen open by itself.",
                    isOn = miuiOpAllowed(context, MIUI_OP_BACKGROUND_START),
                    required = false,
                    open = ::openMiuiPermissions
                )
                steps += Step(
                    "miui_lock_screen", "Show on lock screen",
                    "Lets a call ring on the locked screen.",
                    isOn = miuiOpAllowed(context, MIUI_OP_SHOW_WHEN_LOCKED),
                    required = false,
                    open = ::openMiuiPermissions
                )
            }
            Maker.VIVO -> {
                steps += Step(
                    "autostart", "Autostart",
                    "i Manager > App manager > Autostart: turn on Notes.",
                    isOn = null,
                    required = true,
                    open = ::openAutoStart
                )
                steps += Step(
                    "vivo_background", "High background power consumption",
                    "Battery > Background power consumption management: allow Notes.",
                    isOn = null,
                    required = true,
                    open = ::openMakerBattery
                )
            }
            Maker.SAMSUNG -> steps += Step(
                "samsung_sleeping", "Never sleeping apps",
                "Battery > Background usage limits: add Notes to \"Never sleeping apps\" (and take it out of Sleeping and Deep sleeping apps).",
                isOn = null,
                required = true,
                open = ::openMakerBattery
            )
            Maker.OPPO, Maker.HUAWEI -> steps += Step(
                "autostart", if (maker == Maker.HUAWEI) "App launch: Manage manually" else "Auto launch",
                "Turn it on for Notes, so a closed Notes can wake for a push.",
                isOn = null,
                required = true,
                open = ::openAutoStart
            )
            Maker.OTHER -> Unit
        }
        if (isBackgroundDataRestricted(context)) {
            steps += Step(
                "background_data", "Background data",
                "Data Saver blocks Notes in the background: allow it, so calls connect on mobile data too.",
                isOn = false,
                required = true,
                open = ::openBackgroundData
            )
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            steps += Step(
                "full_screen", "Full-screen incoming calls",
                "Lets a call take over the locked screen.",
                isOn = canUseFullScreen(context),
                required = true,
                open = ::openCallScreenSettings
            )
        }
        if (maker != Maker.XIAOMI) {
            steps += Step(
                "overlay", "Display over other apps",
                "Lets the call screen open by itself on a locked phone. In another app, a call rings as a banner.",
                isOn = canOpenCallScreen(context),
                required = false,
                open = { openSettingsPage(it, Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, packageUri(it))) }
            )
        }
        return steps
    }

    /** Where [step] stands: on (detected or ticked off by the user), or not yet. */
    fun isDone(context: Context, step: Step): Boolean = step.isOn ?: prefs(context).getBoolean(KEY_DONE + step.key, false)

    /** The user ticked off (or un-ticked) a switch the phone doesn't tell apps about. */
    fun setDone(context: Context, step: Step, done: Boolean) {
        prefs(context).edit().putBoolean(KEY_DONE + step.key, done).apply()
    }

    /** How many needed switches are still off. */
    fun missingCount(context: Context): Int = steps(context).count { it.required && !isDone(context, it) }

    /** In Recents, keeping Notes locked stops the phone clearing it (said the way each maker does). */
    fun lockInRecentsTip(): String = when (maker()) {
        Maker.XIAOMI -> "Also lock Notes in Recents: open Recents, long-press Notes and tap the lock."
        Maker.VIVO -> "Also lock Notes in Recents: open Recents and pull Notes down (or tap its lock)."
        Maker.SAMSUNG -> "Also lock Notes in Recents: open Recents, tap the Notes icon and pick \"Keep open\"."
        Maker.OPPO -> "Also lock Notes in Recents: open Recents, tap the ⋮ on Notes and pick \"Lock\"."
        Maker.HUAWEI -> "Also lock Notes in Recents: open Recents and pull Notes down to lock it."
        Maker.OTHER -> "Swiping Notes away from Recents is fine: pushes still reach it."
    }

    /**
     * Whether to bring up the setup sheet by itself: a needed switch is off, and it wasn't shown in
     * the last few days (it's always in Settings > Notifications too).
     */
    fun shouldPrompt(context: Context): Boolean {
        val last = prefs(context).getLong(KEY_LAST_PROMPT, 0L)
        if (System.currentTimeMillis() - last < PROMPT_EVERY_MS) return false
        return missingCount(context) > 0
    }

    fun onPrompted(context: Context) {
        prefs(context).edit().putLong(KEY_LAST_PROMPT, System.currentTimeMillis()).apply()
    }

    private fun openNotificationSettings(context: Context) {
        val page = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
        } else {
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, packageUri(context))
        }
        openSettingsPage(context, page)
    }

    /** The maker's own battery list for apps (Xiaomi, Vivo, Samsung), else the app's settings page. */
    private fun openMakerBattery(context: Context) {
        val label = try {
            context.applicationInfo.loadLabel(context.packageManager).toString()
        } catch (_: Exception) {
            "Notes"
        }
        val screens = when (maker()) {
            Maker.XIAOMI -> listOf(
                Intent().setComponent(ComponentName("com.miui.powerkeeper", "com.miui.powerkeeper.ui.HiddenAppsConfigActivity"))
                    .putExtra("package_name", context.packageName)
                    .putExtra("package_label", label)
            )
            Maker.VIVO -> listOf(
                Intent().setComponent(ComponentName("com.vivo.abe", "com.vivo.applicationbehaviorengine.ui.ExcessivePowerManagerActivity")),
                Intent().setComponent(ComponentName("com.iqoo.powersaving", "com.iqoo.powersaving.PowerSavingManagerActivity"))
            )
            Maker.SAMSUNG -> listOf(
                Intent().setComponent(ComponentName("com.samsung.android.lool", "com.samsung.android.sm.battery.ui.BatteryActivity")),
                Intent().setComponent(ComponentName("com.samsung.android.lool", "com.samsung.android.sm.ui.battery.BatteryActivity")),
                Intent().setComponent(ComponentName("com.samsung.android.sm", "com.samsung.android.sm.ui.battery.BatteryActivity"))
            )
            else -> emptyList()
        }
        leavingForSettings(context)
        for (screen in screens) {
            try {
                context.startActivity(screen.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                return
            } catch (_: Exception) {
                // Not on this phone's version: the next one
            }
        }
        openSettingsPage(context, Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, packageUri(context)))
    }

    /** MIUI / HyperOS's own permissions for the app (pop-ups in the background, lock screen). */
    private fun openMiuiPermissions(context: Context) {
        leavingForSettings(context)
        val screens = listOf(
            Intent("miui.intent.action.APP_PERM_EDITOR")
                .setClassName("com.miui.securitycenter", "com.miui.permcenter.permissions.PermissionsEditorActivity")
                .putExtra("extra_pkgname", context.packageName),
            Intent("miui.intent.action.APP_PERM_EDITOR")
                .setClassName("com.miui.securitycenter", "com.miui.permcenter.permissions.AppPermissionsEditorActivity")
                .putExtra("extra_pkgname", context.packageName)
        )
        for (screen in screens) {
            try {
                context.startActivity(screen.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                return
            } catch (_: Exception) {
                // Not on this version: the next one
            }
        }
        openSettingsPage(context, Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, packageUri(context)))
    }

    private fun openBackgroundData(context: Context) {
        val page = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            Intent(Settings.ACTION_IGNORE_BACKGROUND_DATA_RESTRICTIONS_SETTINGS, packageUri(context))
        } else {
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, packageUri(context))
        }
        openSettingsPage(context, page)
    }

    /** Data Saver (or the app's own "Background data" switch) keeps it off the network in the background. */
    private fun isBackgroundDataRestricted(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return false
        val connectivity = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
        return try {
            connectivity.restrictBackgroundStatus == ConnectivityManager.RESTRICT_BACKGROUND_STATUS_ENABLED
        } catch (_: Exception) {
            false
        }
    }

    /**
     * One of MIUI / HyperOS's own permissions (Auto-start, pop-ups...): on or off, or null when the
     * phone doesn't say (then the user ticks it off).
     */
    private fun miuiOpAllowed(context: Context, op: Int): Boolean? = try {
        val ops = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val check = AppOpsManager::class.java.getMethod(
            "checkOpNoThrow", Int::class.javaPrimitiveType, Int::class.javaPrimitiveType, String::class.java
        )
        when (check.invoke(ops, op, android.os.Process.myUid(), context.packageName) as Int) {
            AppOpsManager.MODE_ALLOWED -> true
            AppOpsManager.MODE_IGNORED, AppOpsManager.MODE_ERRORED -> false
            else -> null
        }
    } catch (_: Throwable) {
        null
    }

    private fun openSettingsPage(context: Context, page: Intent) {
        leavingForSettings(context)
        try {
            context.startActivity(page.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e: Exception) {
            try {
                context.startActivity(
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, packageUri(context)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            } catch (e2: Exception) {
                Log.w(TAG, "No settings screen to open", e2)
                onAppResumed(context)
            }
        }
    }

    private fun packageUri(context: Context): Uri = Uri.parse("package:${context.packageName}")

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private const val PREFS = "background_delivery"
    private const val KEY_DONE = "done_"
    private const val KEY_LAST_PROMPT = "last_prompt"
    private const val PROMPT_EVERY_MS = 3L * 24 * 60 * 60 * 1000
    private const val MIUI_OP_AUTO_START = 10008
    private const val MIUI_OP_SHOW_WHEN_LOCKED = 10020
    private const val MIUI_OP_BACKGROUND_START = 10021

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

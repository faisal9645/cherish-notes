package com.example.share

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import com.example.security.SecurityPreferences

/**
 * Manages rotating activity-aliases for receiving shares from external apps (Gallery, YouTube, Chrome, etc.).
 *
 * Android OS (ChooserActivity / UsageStatsManager) logs and ranks sharing frequency per exact
 * ComponentName. By rotating between multiple aliases after each share, no single component
 * ever accumulates enough usage history to be ranked as the #1 or #2 "Frequent" app in
 * the system share sheet, while keeping sharing completely functional.
 */
object ShareRotationHelper {
    const val ALIAS_COUNT = 5
    private const val PREFS_NAME = "share_rotation_prefs"
    private const val KEY_ACTIVE_ALIAS_INDEX = "active_share_alias_index"
    private const val KEY_SHARE_SHEET_ENABLED = "share_sheet_enabled"

    fun getAliasName(index: Int): String {
        return "com.example.share.ShareReceiverAlias${(index % ALIAS_COUNT) + 1}"
    }

    fun getAllShareComponents(context: Context): Array<ComponentName> {
        val list = mutableListOf<ComponentName>()
        list.add(ComponentName(context, ShareReceiverActivity::class.java))
        for (i in 0 until ALIAS_COUNT) {
            list.add(ComponentName(context, getAliasName(i)))
        }
        return list.toTypedArray()
    }

    fun isShareSheetEnabled(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_SHARE_SHEET_ENABLED, true)
    }

    fun setShareSheetEnabled(context: Context, enabled: Boolean) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_SHARE_SHEET_ENABLED, enabled).apply()
        syncShareAliases(context)
    }

    /**
     * Rotates to the next alias after a share is handled.
     * This resets Android's per-component frequency ranking so the app does not
     * dominate the top spots in the system share sheet.
     */
    fun rotateToNextAlias(context: Context) {
        try {
            if (!isShareSheetEnabled(context)) {
                disableAllAliases(context)
                return
            }
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val currentIndex = prefs.getInt(KEY_ACTIVE_ALIAS_INDEX, 0)
            val nextIndex = (currentIndex + 1) % ALIAS_COUNT

            val pm = context.packageManager
            val currentComp = ComponentName(context, getAliasName(currentIndex))
            val nextComp = ComponentName(context, getAliasName(nextIndex))

            // Enable next alias first so there is always a valid share target available
            pm.setComponentEnabledSetting(
                nextComp,
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                PackageManager.DONT_KILL_APP
            )

            // Disable the alias that was just used
            pm.setComponentEnabledSetting(
                currentComp,
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                PackageManager.DONT_KILL_APP
            )

            prefs.edit().putInt(KEY_ACTIVE_ALIAS_INDEX, nextIndex).apply()
        } catch (e: Throwable) {}
    }

    /**
     * Synchronizes the active alias with the current disguise style and user settings.
     */
    fun syncShareAliases(context: Context) {
        try {
            val secPrefs = SecurityPreferences.getInstance(context)
            val isCalc = secPrefs.isCalculatorDecoy()
            val isEnabled = isShareSheetEnabled(context) && !isCalc

            val pm = context.packageManager
            val baseComp = ComponentName(context, ShareReceiverActivity::class.java)
            if (!isEnabled) {
                pm.setComponentEnabledSetting(
                    baseComp,
                    PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                    PackageManager.DONT_KILL_APP
                )
                disableAllAliases(context)
                return
            }

            pm.setComponentEnabledSetting(
                baseComp,
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                PackageManager.DONT_KILL_APP
            )

            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val activeIndex = prefs.getInt(KEY_ACTIVE_ALIAS_INDEX, 0)

            for (i in 0 until ALIAS_COUNT) {
                val comp = ComponentName(context, getAliasName(i))
                val shouldBeOn = (i == activeIndex)
                val targetState = if (shouldBeOn) {
                    PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                } else {
                    PackageManager.COMPONENT_ENABLED_STATE_DISABLED
                }
                pm.setComponentEnabledSetting(comp, targetState, PackageManager.DONT_KILL_APP)
            }
        } catch (e: Throwable) {}
    }

    fun disableAllAliases(context: Context) {
        try {
            val pm = context.packageManager
            for (i in 0 until ALIAS_COUNT) {
                val comp = ComponentName(context, getAliasName(i))
                pm.setComponentEnabledSetting(
                    comp,
                    PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                    PackageManager.DONT_KILL_APP
                )
            }
        } catch (e: Throwable) {}
    }
}

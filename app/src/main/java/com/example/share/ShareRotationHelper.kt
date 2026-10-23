package com.example.share

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import com.example.security.SecurityPreferences

/** Maintains one Notes share target. Android owns ranking; aliases cannot guarantee last place. */
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

    /** Rotate only while Notes sharing is enabled; never revive a Calculator share target. */
    @Synchronized
    fun rotateToNextAlias(context: Context) {
        if (isShareSheetEnabled(context) && !SecurityPreferences.getInstance(context).isCalculatorDecoy()) {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val next = (prefs.getInt(KEY_ACTIVE_ALIAS_INDEX, 0).coerceIn(0, ALIAS_COUNT - 1) + 1) % ALIAS_COUNT
            prefs.edit().putInt(KEY_ACTIVE_ALIAS_INDEX, next).apply()
        }
        syncShareAliases(context)
    }
    /**
     * Synchronizes the active alias with the current disguise style and user settings.
     */
    @Synchronized
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
            val activeIndex = prefs.getInt(KEY_ACTIVE_ALIAS_INDEX, 0).coerceIn(0, ALIAS_COUNT - 1)

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

    @Synchronized
    fun disableAllAliases(context: Context) {
        val pm = context.packageManager
        for (i in 0 until ALIAS_COUNT) {
            try {
                pm.setComponentEnabledSetting(
                    ComponentName(context, getAliasName(i)),
                    PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                    PackageManager.DONT_KILL_APP
                )
            } catch (_: Exception) {
                // A stale/missing component must not prevent disabling the remaining aliases.
            }
        }
    }
}
package com.example.util

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class BatteryInfo(
    val level: Int = 100,
    val isCharging: Boolean = false
)

class BatteryStatusHelper(private val context: Context) {
    private val _batteryInfo = MutableStateFlow(getCurrentBattery())
    val batteryInfo: StateFlow<BatteryInfo> = _batteryInfo.asStateFlow()

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context?, intent: Intent?) {
            _batteryInfo.value = getCurrentBattery(intent)
        }
    }

    private var isRegistered = false

    fun start() {
        if (!isRegistered) {
            try {
                val filter = IntentFilter().apply {
                    addAction(Intent.ACTION_BATTERY_CHANGED)
                    addAction(Intent.ACTION_POWER_CONNECTED)
                    addAction(Intent.ACTION_POWER_DISCONNECTED)
                }
                val stickyIntent = context.registerReceiver(receiver, filter)
                _batteryInfo.value = getCurrentBattery(stickyIntent)
                isRegistered = true
            } catch (_: Exception) {}
        }
    }

    fun stop() {
        if (isRegistered) {
            try {
                context.unregisterReceiver(receiver)
            } catch (_: Exception) {}
            isRegistered = false
        }
    }

    fun getCurrentBattery(intent: Intent? = null): BatteryInfo {
        return try {
            val bm = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
            val capacity = bm?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: -1

            val stickyIntent = intent ?: run {
                val ifilter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
                context.registerReceiver(null, ifilter)
            }

            val level = stickyIntent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
            val scale = stickyIntent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
            val status = stickyIntent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
            val plugged = stickyIntent?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0

            val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                             status == BatteryManager.BATTERY_STATUS_FULL ||
                             plugged > 0 ||
                             (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && bm?.getIntProperty(BatteryManager.BATTERY_PROPERTY_STATUS) == BatteryManager.BATTERY_STATUS_CHARGING)

            val batteryPct = when {
                capacity in 0..100 -> capacity
                level >= 0 && scale > 0 -> ((level.toFloat() / scale.toFloat()) * 100).toInt().coerceIn(0, 100)
                else -> 100
            }

            BatteryInfo(level = batteryPct, isCharging = isCharging)
        } catch (_: Exception) {
            BatteryInfo()
        }
    }
}

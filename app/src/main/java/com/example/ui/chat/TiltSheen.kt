package com.example.ui.chat

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlin.math.abs

/**
 * How the phone is tilted away from the way it has been held lately, about -1..1 each way, for
 * [tiltSheen]. One accelerometer listener serves every photo on screen, and only while one is
 * (with the app open).
 */
private object DeviceTilt {
    private const val TILT_STEP = 0.012f

    var tilt by mutableStateOf(Offset.Zero)
        private set
    private var users = 0
    private var manager: SensorManager? = null
    private var baseX = Float.NaN
    private var baseY = Float.NaN
    private var smoothX = 0f
    private var smoothY = 0f

    private val listener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent) {
            val rawX = event.values[0]
            val rawY = event.values[1]
            if (baseX.isNaN()) {
                baseX = rawX
                baseY = rawY
            }
            // The way it's held drifts in slowly; the sheen answers a tilt away from that
            baseX += (rawX - baseX) * 0.02f
            baseY += (rawY - baseY) * 0.02f
            smoothX += ((rawX - baseX) / 3f - smoothX) * 0.2f
            smoothY += ((rawY - baseY) / 3f - smoothY) * 0.2f
            val x = smoothX.coerceIn(-1f, 1f)
            val y = smoothY.coerceIn(-1f, 1f)
            // Only a change that shows: held still, the photos on screen stop redrawing
            if (abs(x - tilt.x) >= TILT_STEP || abs(y - tilt.y) >= TILT_STEP) tilt = Offset(x, y)
        }

        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
    }

    fun acquire(context: Context) {
        users++
        if (users > 1) return
        val sensors = context.applicationContext.getSystemService(SensorManager::class.java) ?: return
        val accelerometer = sensors.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) ?: return
        manager = sensors
        baseX = Float.NaN
        baseY = Float.NaN
        sensors.registerListener(listener, accelerometer, SensorManager.SENSOR_DELAY_UI)
    }

    fun release() {
        users = (users - 1).coerceAtLeast(0)
        if (users > 0) return
        manager?.unregisterListener(listener)
        manager = null
        smoothX = 0f
        smoothY = 0f
        tilt = Offset.Zero
    }
}

/**
 * A soft band of light that slides across this element as the phone tilts, like light on a
 * polaroid. Put it right after the element's clip. Nothing with system animations off.
 */
@Composable
fun Modifier.tiltSheen(): Modifier {
    val context = LocalContext.current
    if (context.areAnimationsDisabled()) return this
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        // Only while the app is on screen
        var held = false
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> if (!held) {
                    held = true
                    DeviceTilt.acquire(context)
                }
                Lifecycle.Event.ON_PAUSE -> if (held) {
                    held = false
                    DeviceTilt.release()
                }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            if (held) DeviceTilt.release()
        }
    }
    // The photo underneath gets a layer of its own, so a tilt redraws only the band of light
    return drawWithContent {
        drawContent()
        val tilt = DeviceTilt.tilt
        val shift = (tilt.x * 0.6f + tilt.y * 0.4f).coerceIn(-1f, 1f)
        if (abs(shift) < 0.02f) return@drawWithContent
        // A diagonal band, slid along it by the tilt, brighter the further it's tilted
        val half = maxOf(size.width, size.height) * 0.45f
        val center = Offset(size.width * (0.5f + 0.6f * shift), size.height * (0.5f - 0.6f * shift))
        drawRect(
            brush = Brush.linearGradient(
                0f to Color.Transparent,
                0.5f to Color.White.copy(alpha = 0.16f * abs(shift).coerceAtLeast(0.35f)),
                1f to Color.Transparent,
                start = Offset(center.x - half, center.y - half),
                end = Offset(center.x + half, center.y + half)
            )
        )
    }.graphicsLayer()
}

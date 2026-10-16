package com.example.ui.chat

import com.example.ui.theme.darkTone

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.luminance
import androidx.compose.animation.animateColor
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.spring
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.consumeAsFlow
import com.example.ui.theme.InkBlack
import com.example.ui.theme.Lavender50

/**
 * Premium Chat Wallpaper Composable that dynamically adapts to Day Mode (Light)
 * and Night Mode (Dark).
 *
 * Supported Themes:
 * - 0: Normal (Minimalist, modern clean canvas with subtle depth)
 * - 1: Theme 1 (Classic Doodle Art Wallpaper)
 * - 2: Theme 2 (Romantic Couple Love Doodles Wallpaper)
 */
@Composable
fun ChatWallpaper(
    chatBgTheme: Int,
    modifier: Modifier = Modifier
) {
    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f || com.example.ui.theme.isAppInDark()

    when (chatBgTheme) {
        1 -> DoodleWallpaper(isDark = isDark, modifier = modifier)
        2 -> LoveImmersiveWallpaper(isDark = isDark, modifier = modifier)
        else -> NormalWallpaper(isDark = isDark, modifier = modifier)
    }
}

/**
 * Clean minimalist wallpaper (Theme 0) - Reverted to solid default
 */
@Composable
fun NormalWallpaper(
    isDark: Boolean,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(if (isDark) Color.Black else Color.White)
    )
}

/**
 * Romantic Couple Doodles Wallpaper (Theme 2)
 * High-definition couple love doodles artwork that seamlessly adapts to Day and Dark modes:
 * - Day Mode: Pure white background (#FFFFFF) with crisp royal theme blue (#1D4ED8) doodles.
 * - Dark Mode: Deep midnight royal blue (#011565) background with crisp luminous doodles.
 * - AMOLED Mode: True pitch black (#000000) background for infinite OLED contrast and battery savings.
 */
@Composable
fun LoveImmersiveWallpaper(
    isDark: Boolean,
    modifier: Modifier = Modifier
) {
    val isAmoled = com.example.ui.theme.LocalAmoledBlack.current
    val drawableRes = when {
        isAmoled -> com.example.R.drawable.chat_bg_theme_2_amoled
        isDark -> com.example.R.drawable.chat_bg_theme_2_dark
        else -> com.example.R.drawable.chat_bg_theme_2_day
    }
    val fallbackBg = when {
        isAmoled || isDark -> Color.Black
        else -> Color.White
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(fallbackBg)
            .clipToBounds()
    ) {
        androidx.compose.foundation.Image(
            painter = androidx.compose.ui.res.painterResource(id = drawableRes),
            contentDescription = "Theme 2 Love Wallpaper",
            contentScale = androidx.compose.ui.layout.ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
                .tiltParallax()
        )
    }
}

/**
 * Iconic Doodle Pattern Wallpaper (Theme 1)
 * High-definition procedural vector doodles that scale smoothly across any DPI.
 * Pure white (#FFFFFF) in Day mode, true AMOLED Black (#000000) in Night mode.
 * Doodle line and dot accents dynamically match the app's theme colors.
 */
@Composable
fun DoodleWallpaper(
    isDark: Boolean,
    modifier: Modifier = Modifier
) {
    val isAmoled = com.example.ui.theme.LocalAmoledBlack.current
    val fallbackBg = when {
        isAmoled || isDark -> Color.Black
        else -> Color.White
    }

    // In dark/amoled mode, invert colors so background is pitch black and doodle artwork is luminous theme blue
    val colorFilter = when {
        isDark || isAmoled -> {
            androidx.compose.ui.graphics.ColorFilter.colorMatrix(
                androidx.compose.ui.graphics.ColorMatrix(
                    floatArrayOf(
                        -0.65f, 0f, 0f, 0f, 175f,
                        -0.78f, 0f, 0f, 0f, 210f,
                        -0.95f, 0f, 0f, 0f, 255f,
                        0f, 0f, 0f, 1f, 0f
                    )
                )
            )
        }
        else -> null
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(fallbackBg)
            .clipToBounds()
    ) {
        androidx.compose.foundation.Image(
            painter = androidx.compose.ui.res.painterResource(id = com.example.R.drawable.chat_bg_theme_1),
            contentDescription = "Theme 1 Doodle Wallpaper",
            contentScale = androidx.compose.ui.layout.ContentScale.Crop,
            colorFilter = colorFilter,
            modifier = Modifier
                .fillMaxSize()
                .tiltParallax()
        )
    }
}

/**
 * Shifts the wallpaper a few dp against the phone's tilt for a sense of depth. Tilt is measured
 * against a slowly drifting baseline, so whatever angle the phone is held at counts as centre.
 * Off when system animations are turned off.
 */
@Composable
private fun Modifier.tiltParallax(maxShift: Dp = 10.dp): Modifier {
    val context = LocalContext.current
    val maxShiftPx = with(LocalDensity.current) { maxShift.toPx() }
    val shift = remember { Animatable(Offset.Zero, Offset.VectorConverter) }
    // The motion sensor only runs while the chat is on screen: not in the background or behind Notes
    val lifecycleState by androidx.lifecycle.compose.LocalLifecycleOwner.current.lifecycle.currentStateFlow
        .collectAsState()
    val isDisguised by com.example.security.SecurityPreferences.getInstance(context).isDisguiseActive.collectAsState()
    val isOnScreen = !isDisguised && lifecycleState.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED)

    LaunchedEffect(maxShiftPx, isOnScreen) {
        if (!isOnScreen) return@LaunchedEffect
        if (context.areAnimationsDisabled()) return@LaunchedEffect
        val sensorManager = context.getSystemService(SensorManager::class.java) ?: return@LaunchedEffect
        val sensor = sensorManager.getDefaultSensor(Sensor.TYPE_GRAVITY)
            ?: sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
            ?: return@LaunchedEffect
        val targets = Channel<Offset>(Channel.CONFLATED)
        var baseX = Float.NaN
        var baseY = Float.NaN
        var lastTarget = Offset.Zero
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                val x = event.values[0]
                val y = event.values[1]
                if (baseX.isNaN()) {
                    baseX = x
                    baseY = y
                }
                baseX += (x - baseX) * 0.02f
                baseY += (y - baseY) * 0.02f
                // About 3 m/s² of change away from the baseline gives the full shift
                val dx = ((x - baseX) / 3f).coerceIn(-1f, 1f)
                val dy = ((y - baseY) / 3f).coerceIn(-1f, 1f)
                val newTarget = Offset(dx * maxShiftPx, -dy * maxShiftPx)
                // Ignore micro-fluctuations smaller than 0.5px so the animation settles when still
                if (kotlin.math.abs(newTarget.x - lastTarget.x) >= 0.5f ||
                    kotlin.math.abs(newTarget.y - lastTarget.y) >= 0.5f
                ) {
                    lastTarget = newTarget
                    targets.trySend(newTarget)
                }
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }
        sensorManager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_UI)
        try {
            targets.consumeAsFlow().collectLatest { target ->
                if ((target - shift.value).getDistance() >= 0.5f) {
                    shift.animateTo(target, spring(dampingRatio = 0.9f, stiffness = Spring.StiffnessVeryLow))
                }
            }
        } finally {
            sensorManager.unregisterListener(listener)
        }
    }

    return graphicsLayer {
        // Slightly enlarged so the shifted edges never show
        val zoom = 1f + 2.2f * maxShiftPx / size.minDimension.coerceAtLeast(1f)
        scaleX = zoom
        scaleY = zoom
        translationX = shift.value.x
        translationY = shift.value.y
    }
}

/**
 * Draws an organic cluster of chat-themed vector doodles inside one tile.
 */
private fun DrawScope.drawDoodleTile(
    originX: Float,
    originY: Float,
    tileSize: Float,
    strokeColor: Color,
    dotColor: Color,
    stroke: Stroke,
    variant: Int
) {
    // 1. Heart motif
    rotate(degrees = if (variant % 2 == 0) -8f else 10f, pivot = Offset(originX + tileSize * 0.22f, originY + tileSize * 0.22f)) {
        val heartPath = Path().apply {
            val cx = originX + tileSize * 0.22f
            val cy = originY + tileSize * 0.22f
            val s = tileSize * 0.09f
            moveTo(cx, cy + s * 0.8f)
            cubicTo(cx - s * 1.2f, cy - s * 0.2f, cx - s * 1.1f, cy - s * 1.2f, cx, cy - s * 0.4f)
            cubicTo(cx + s * 1.1f, cy - s * 1.2f, cx + s * 1.2f, cy - s * 0.2f, cx, cy + s * 0.8f)
            close()
        }
        drawPath(heartPath, strokeColor, style = stroke)
    }

    // 2. Chat bubble motif
    rotate(degrees = if (variant % 2 == 0) 6f else -6f, pivot = Offset(originX + tileSize * 0.76f, originY + tileSize * 0.24f)) {
        val bx = originX + tileSize * 0.65f
        val by = originY + tileSize * 0.16f
        val bw = tileSize * 0.22f
        val bh = tileSize * 0.15f
        val r = 5.dp.toPx()

        val bubblePath = Path().apply {
            addRoundRect(RoundRect(Rect(bx, by, bx + bw, by + bh), androidx.compose.ui.geometry.CornerRadius(r, r)))
            moveTo(bx + bw * 0.3f, by + bh)
            lineTo(bx + bw * 0.2f, by + bh + tileSize * 0.045f)
            lineTo(bx + bw * 0.5f, by + bh)
        }
        drawPath(bubblePath, strokeColor, style = stroke)
    }

    // 3. Paper plane motif
    rotate(degrees = if (variant == 1) -15f else 22f, pivot = Offset(originX + tileSize * 0.32f, originY + tileSize * 0.72f)) {
        val px = originX + tileSize * 0.24f
        val py = originY + tileSize * 0.65f
        val ps = tileSize * 0.16f
        val planePath = Path().apply {
            moveTo(px + ps, py)
            lineTo(px, py + ps * 0.8f)
            lineTo(px + ps * 0.38f, py + ps * 0.52f)
            close()
            moveTo(px + ps, py)
            lineTo(px + ps * 0.38f, py + ps * 0.52f)
            lineTo(px + ps * 0.42f, py + ps * 0.78f)
        }
        drawPath(planePath, strokeColor, style = stroke)
    }

    // 4. Sparkle / 4-Point Star motif
    val sx = originX + tileSize * 0.78f
    val sy = originY + tileSize * 0.74f
    val starR = tileSize * 0.08f
    val starPath = Path().apply {
        moveTo(sx, sy - starR)
        quadraticTo(sx, sy, sx + starR, sy)
        quadraticTo(sx, sy, sx, sy + starR)
        quadraticTo(sx, sy, sx - starR, sy)
        quadraticTo(sx, sy, sx, sy - starR)
        close()
    }
    drawPath(starPath, strokeColor, style = stroke)

    // 5. Center Motif (Music Note, Coffee Cup, Smiley, or Camera based on variant)
    when (variant % 4) {
        0 -> {
            // Music Note
            val mx = originX + tileSize * 0.52f
            val my = originY + tileSize * 0.48f
            val ms = tileSize * 0.12f
            val notePath = Path().apply {
                drawCircle(color = strokeColor, radius = ms * 0.28f, center = Offset(mx, my + ms * 0.4f), style = stroke)
                moveTo(mx + ms * 0.28f, my + ms * 0.4f)
                lineTo(mx + ms * 0.28f, my - ms * 0.35f)
                lineTo(mx + ms * 0.75f, my - ms * 0.15f)
            }
            drawPath(notePath, strokeColor, style = stroke)
        }
        1 -> {
            // Coffee Cup
            val cx = originX + tileSize * 0.48f
            val cy = originY + tileSize * 0.46f
            val cw = tileSize * 0.14f
            val ch = tileSize * 0.11f
            val cupPath = Path().apply {
                addRoundRect(
                    RoundRect(
                        Rect(cx - cw / 2, cy - ch / 2, cx + cw / 2, cy + ch / 2),
                        bottomLeft = androidx.compose.ui.geometry.CornerRadius(4.dp.toPx(), 4.dp.toPx()),
                        bottomRight = androidx.compose.ui.geometry.CornerRadius(4.dp.toPx(), 4.dp.toPx())
                    )
                )
                // handle
                arcTo(
                    rect = Rect(cx + cw / 2 - 2f, cy - ch / 3, cx + cw / 2 + ch * 0.6f, cy + ch / 3),
                    startAngleDegrees = -90f,
                    sweepAngleDegrees = 180f,
                    forceMoveTo = true
                )
            }
            drawPath(cupPath, strokeColor, style = stroke)
        }
        2 -> {
            // Smiley Face
            val smx = originX + tileSize * 0.50f
            val smy = originY + tileSize * 0.48f
            val smr = tileSize * 0.08f
            drawCircle(color = strokeColor, radius = smr, center = Offset(smx, smy), style = stroke)
            drawCircle(color = dotColor, radius = 1.3f, center = Offset(smx - smr * 0.35f, smy - smr * 0.2f))
            drawCircle(color = dotColor, radius = 1.3f, center = Offset(smx + smr * 0.35f, smy - smr * 0.2f))
            val smilePath = Path().apply {
                arcTo(
                    rect = Rect(smx - smr * 0.5f, smy - smr * 0.2f, smx + smr * 0.5f, smy + smr * 0.55f),
                    startAngleDegrees = 20f,
                    sweepAngleDegrees = 140f,
                    forceMoveTo = true
                )
            }
            drawPath(smilePath, strokeColor, style = stroke)
        }
        else -> {
            // Camera
            val camX = originX + tileSize * 0.50f
            val camY = originY + tileSize * 0.48f
            val cw = tileSize * 0.15f
            val ch = tileSize * 0.11f
            val camPath = Path().apply {
                addRoundRect(
                    RoundRect(
                        Rect(camX - cw / 2, camY - ch / 2, camX + cw / 2, camY + ch / 2),
                        androidx.compose.ui.geometry.CornerRadius(3.dp.toPx(), 3.dp.toPx())
                    )
                )
                moveTo(camX - cw * 0.22f, camY - ch / 2)
                lineTo(camX - cw * 0.22f, camY - ch / 2 - 2.5.dp.toPx())
                lineTo(camX - cw * 0.05f, camY - ch / 2 - 2.5.dp.toPx())
                lineTo(camX - cw * 0.05f, camY - ch / 2)
            }
            drawPath(camPath, strokeColor, style = stroke)
            drawCircle(color = strokeColor, radius = ch * 0.28f, center = Offset(camX, camY), style = stroke)
        }
    }

    // 6. Subtle stardust dots for rich texture
    drawCircle(dotColor, radius = 1.4f, center = Offset(originX + tileSize * 0.12f, originY + tileSize * 0.5f))
    drawCircle(dotColor, radius = 1.4f, center = Offset(originX + tileSize * 0.58f, originY + tileSize * 0.12f))
    drawCircle(dotColor, radius = 1.4f, center = Offset(originX + tileSize * 0.9f, originY + tileSize * 0.48f))
    drawCircle(dotColor, radius = 1.4f, center = Offset(originX + tileSize * 0.48f, originY + tileSize * 0.88f))
}

/**
 * Cosmic Stars & Constellations Wallpaper (Theme 2)
 */
@Composable
fun CosmicConstellationWallpaper(
    isDark: Boolean,
    modifier: Modifier = Modifier
) {
    val bgBrush = remember(isDark) {
        if (isDark) {
            androidx.compose.ui.graphics.SolidColor(Color.Black)
        } else {
            Brush.verticalGradient(
                listOf(
                    Lavender50,
                    Color(0xFFE8E3F3)
                )
            )
        }
    }

    val starColor = remember(isDark) {
        if (isDark) {
            Color(0xFF9CAEFF).copy(alpha = 0.11f)
        } else {
            Color(0xFF6B5DA8).copy(alpha = 0.08f)
        }
    }

    val lineColor = remember(isDark) {
        if (isDark) {
            Color(0xFF7A8CD0).copy(alpha = 0.05f)
        } else {
            Color(0xFF5A4D8C).copy(alpha = 0.045f)
        }
    }

    Canvas(
        modifier = modifier
            .fillMaxSize()
            .background(bgBrush)
    ) {
        val tileSize = 120.dp.toPx()
        val cols = (size.width / tileSize).toInt() + 1
        val rows = (size.height / tileSize).toInt() + 1

        val lineStroke = Stroke(width = 1.2f, cap = StrokeCap.Round)

        for (r in 0 until rows) {
            for (c in 0 until cols) {
                val ox = c * tileSize
                val oy = r * tileSize

                val p1 = Offset(ox + tileSize * 0.2f, oy + tileSize * 0.3f)
                val p2 = Offset(ox + tileSize * 0.5f, oy + tileSize * 0.2f)
                val p3 = Offset(ox + tileSize * 0.8f, oy + tileSize * 0.45f)
                val p4 = Offset(ox + tileSize * 0.4f, oy + tileSize * 0.75f)

                // Constellation connections
                drawLine(lineColor, p1, p2, strokeWidth = 1.2f)
                drawLine(lineColor, p2, p3, strokeWidth = 1.2f)
                drawLine(lineColor, p1, p4, strokeWidth = 1.2f)

                // Constellation Stars
                drawCircle(starColor, radius = 2.4f, center = p1)
                drawCircle(starColor, radius = 3.0f, center = p2)
                drawCircle(starColor, radius = 2.2f, center = p3)
                drawCircle(starColor, radius = 2.6f, center = p4)

                // Sparkle star
                val sx = ox + tileSize * 0.75f
                val sy = oy + tileSize * 0.82f
                val sr = tileSize * 0.07f
                drawLine(starColor, Offset(sx - sr, sy), Offset(sx + sr, sy), strokeWidth = 1.2f)
                drawLine(starColor, Offset(sx, sy - sr), Offset(sx, sy + sr), strokeWidth = 1.2f)
            }
        }
    }
}

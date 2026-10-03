package com.example.ui.chat

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
import androidx.compose.runtime.getValue
import androidx.compose.ui.unit.dp

/**
 * Premium Chat Wallpaper Composable that dynamically adapts to Day Mode (Light)
 * and Night Mode (Dark).
 *
 * Supported Themes:
 * - 0: Normal (Minimalist, modern clean canvas with subtle depth)
 * - 1: Theme 1 (Classic Telegram / WhatsApp Doodle Art Wallpaper)
 * - 2: Theme 2 (Cosmic Stars & Constellations Wallpaper)
 */
@Composable
fun ChatWallpaper(
    chatBgTheme: Int,
    modifier: Modifier = Modifier
) {
    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f

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
            .background(if (isDark) Color(0xFF1E1E1E) else Color(0xFFF5F5F5))
    )
}

/**
 * Animated Immersive Love Wallpaper (Theme 2)
 */
@Composable
fun LoveImmersiveWallpaper(
    isDark: Boolean,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = androidx.compose.animation.core.rememberInfiniteTransition(label = "love_bg")
    
    val color1 by infiniteTransition.animateColor(
        initialValue = if (isDark) Color(0xFF180A12) else Color(0xFFFFF0F5),
        targetValue = if (isDark) Color(0xFF28111B) else Color(0xFFFFE4E1),
        animationSpec = androidx.compose.animation.core.infiniteRepeatable(
            animation = androidx.compose.animation.core.tween(6000, easing = androidx.compose.animation.core.FastOutSlowInEasing),
            repeatMode = androidx.compose.animation.core.RepeatMode.Reverse
        ),
        label = "c1"
    )
    
    val color2 by infiniteTransition.animateColor(
        initialValue = if (isDark) Color(0xFF0F060A) else Color(0xFFFFF8F8),
        targetValue = if (isDark) Color(0xFF1C0A11) else Color(0xFFFFEBF0),
        animationSpec = androidx.compose.animation.core.infiniteRepeatable(
            animation = androidx.compose.animation.core.tween(4500, easing = androidx.compose.animation.core.FastOutSlowInEasing),
            repeatMode = androidx.compose.animation.core.RepeatMode.Reverse
        ),
        label = "c2"
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(color1, color2)))
    )
}

/**
 * Iconic Doodle Pattern Wallpaper (Theme 1)
 * High-definition procedural vector doodles that scale smoothly across any DPI.
 */
@Composable
fun DoodleWallpaper(
    isDark: Boolean,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier.fillMaxSize()) {
        androidx.compose.foundation.Image(
            painter = androidx.compose.ui.res.painterResource(id = com.example.R.drawable.chat_bg_theme_1),
            contentDescription = "Chat Background",
            contentScale = androidx.compose.ui.layout.ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
        
        // Overlay for day/night mode adaptation
        if (isDark) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.75f))
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.White.copy(alpha = 0.4f))
            )
        }
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

    // 5. Music Note or Coffee Cup in Center
    if (variant % 2 == 0) {
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
    } else {
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
                    Color(0xFFF1EEF8),
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

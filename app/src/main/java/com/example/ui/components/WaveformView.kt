package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
fun WaveformView(
    amplitudes: List<Float>,
    progress: Float = 0f,
    activeColor: Color = MaterialTheme.colorScheme.primary,
    inactiveColor: Color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f),
    height: Dp = 36.dp,
    modifier: Modifier = Modifier
) {
    val safeAmplitudes = androidx.compose.runtime.remember(amplitudes) {
        runCatching {
            (amplitudes as? List<*>)?.mapNotNull { (it as? Number)?.toFloat() } ?: emptyList()
        }.getOrDefault(emptyList())
    }

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
    ) {
        val centerY = size.height / 2f
        val playedX = (size.width * progress.coerceIn(0f, 1f))

        // Premium compact waveform ridges (no continuous baseline)
        val totalBars = 40
        val barWidth = 3.dp.toPx()
        val step = size.width / totalBars.toFloat()

        for (i in 0 until totalBars) {
            val x = i * step + (step / 2f)
            val amp = if (safeAmplitudes.isNotEmpty()) {
                val idx = ((i.toFloat() / totalBars) * safeAmplitudes.size).toInt().coerceIn(0, safeAmplitudes.lastIndex)
                safeAmplitudes[idx].coerceIn(0.15f, 1f)
            } else {
                (kotlin.math.sin(i * 0.45f) * 0.35f + 0.65f).coerceIn(0.15f, 1f)
            }

            // Make bars taller by using almost full height
            val barH = (size.height * amp * 0.95f).coerceAtLeast(barWidth)
            val color = if (x <= playedX) activeColor else inactiveColor

            drawLine(
                color = color,
                start = Offset(x, centerY - barH / 2f),
                end = Offset(x, centerY + barH / 2f),
                strokeWidth = barWidth,
                cap = StrokeCap.Round
            )
        }
    }
}

package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
fun WaveformView(
    amplitudes: List<Float>,
    progress: Float = 0f,
    activeColor: Color = MaterialTheme.colorScheme.primary,
    inactiveColor: Color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f),
    height: Dp = 32.dp,
    modifier: Modifier = Modifier
) {
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
    ) {
        val totalBars = 32
        val barWidth = 3.dp.toPx()
        val spacing = (size.width - (totalBars * barWidth)) / (totalBars - 1).coerceAtLeast(1)

        val safeAmplitudes = runCatching {
            (amplitudes as? List<*>)?.mapNotNull { (it as? Number)?.toFloat() } ?: emptyList()
        }.getOrDefault(emptyList())

        val samples = if (safeAmplitudes.isEmpty()) {
            List(totalBars) { index ->
                (kotlin.math.sin(index * 0.4f) * 0.4f + 0.5f).coerceIn(0.2f, 0.9f)
            }
        } else {
            // Sample or interpolate amplitudes to totalBars
            List(totalBars) { i ->
                val srcIdx = ((i.toFloat() / totalBars) * safeAmplitudes.size).toInt().coerceIn(0, safeAmplitudes.lastIndex)
                safeAmplitudes[srcIdx]
            }
        }

        val playedBarIndex = (progress * totalBars).toInt()

        for (i in 0 until totalBars) {
            val barAmp = samples[i].coerceIn(0.15f, 1f)
            val barHeight = size.height * barAmp
            val x = i * (barWidth + spacing)
            val y = (size.height - barHeight) / 2f

            val color = if (i <= playedBarIndex) activeColor else inactiveColor

            drawRoundRect(
                color = color,
                topLeft = Offset(x, y),
                size = Size(barWidth, barHeight),
                cornerRadius = CornerRadius(barWidth / 2f, barWidth / 2f)
            )
        }
    }
}

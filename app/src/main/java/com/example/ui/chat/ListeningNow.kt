package com.example.ui.chat

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.Message
import com.example.ui.components.rememberAppIsResumed
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/*
 * "She's listening": while one of us plays the other's voice note (or watches a video note with
 * its sound), the other phone shows it on that message as it happens: "listening…" with the
 * waveform moving as it's heard, "watching…" on a video note, and the same in the chat's header.
 * See PartnerListening for what's saved.
 */

/** The partner's round video note I'm watching with its sound right now (they play in the chat itself). */
internal object VideoNoteWatching {
    data class Watching(val messageId: String, val durationMs: Long)

    private val _now = MutableStateFlow<Watching?>(null)
    val now: StateFlow<Watching?> = _now.asStateFlow()

    fun report(message: Message, playing: Boolean) {
        if (playing) {
            _now.value = Watching(message.id, message.durationSeconds * 1000L)
        } else if (_now.value?.messageId == message.id) {
            _now.value = null
        }
    }
}

/** What the header says while they listen: "listening to your voice note…". */
internal fun listeningStatus(isVideo: Boolean, playing: Boolean): String? = when {
    !playing -> null
    isVideo -> "watching your video note…"
    else -> "listening to your voice note…"
}

/** "listening…" (or "paused", "watching…"), its icon breathing while they play it. */
@Composable
internal fun PartnerHearingLabel(isVideo: Boolean, playing: Boolean, color: Color, modifier: Modifier = Modifier) {
    val isForeground = rememberAppIsResumed()
    val breath = if (playing && isForeground) {
        val transition = rememberInfiniteTransition(label = "hearing")
        val value by transition.animateFloat(
            initialValue = 0.45f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(900, easing = FastOutSlowInEasing), RepeatMode.Reverse),
            label = "hearing_breath"
        )
        value
    } else {
        1f
    }
    Row(verticalAlignment = Alignment.CenterVertically, modifier = modifier) {
        Icon(
            imageVector = if (isVideo) Icons.Rounded.Visibility else Icons.Rounded.Headphones,
            contentDescription = null,
            tint = color,
            modifier = Modifier
                .size(12.dp)
                .graphicsLayer { alpha = breath }
        )
        Spacer(modifier = Modifier.width(3.dp))
        Text(
            text = when {
                !playing -> "paused"
                isVideo -> "watching…"
                else -> "listening…"
            },
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            color = color,
            maxLines = 1
        )
    }
}

/** The same, in a chip like the time's, under a video note. */
@Composable
internal fun PartnerHearingChip(playing: Boolean, modifier: Modifier = Modifier) {
    val chip = timeChipColors()
    Surface(shape = RoundedCornerShape(12.dp), color = chip.background, border = chip.border, modifier = modifier) {
        PartnerHearingLabel(
            isVideo = true,
            playing = playing,
            color = chip.content,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}

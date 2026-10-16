package com.example.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PhoneInTalk
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.Immutable
import com.example.CherishApplication
import androidx.compose.runtime.remember
import com.example.call.CallRecordingPlayer
import com.example.call.CallRecordingWave
import com.example.data.model.Message
import com.example.ui.components.WaveformView
import com.example.util.formatRecordingLength
import com.example.ui.components.pressScaleTarget
import com.example.ui.components.springClickable
import androidx.compose.material3.LocalTextStyle
import com.example.ui.theme.steadyDigits

/** The recording player's colours. */
@Immutable
data class CallRecordingColors(
    val content: Color,
    val button: Color,
    val buttonIcon: Color,
    val track: Color,
    val progress: Color
)

/**
 * A call recording's player: play/pause, how far along it is (tap the bar to jump), its length,
 * and plainly when it's loading, gone, or couldn't load (tap to try again). Used in the chat bubble
 * and on the Shared Gallery's Calls tab, so both play the same recording the same way.
 *
 * Its colours come as one value: with five separate colour arguments R8 once compiled this
 * function into bytecode Android refused to load, and the app crashed at the end of every call.
 */
@Composable
fun CallRecordingPlayerRow(
    message: Message,
    colors: CallRecordingColors,
    modifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null
) {
    val contentColor = colors.content
    val buttonColor = colors.button
    val buttonIconColor = colors.buttonIcon
    val trackColor = colors.track
    val progressColor = colors.progress
    val context = LocalContext.current
    val app = context.applicationContext as? CherishApplication ?: return
    val player = app.voicePlayerHelper
    val currentTrack by player.currentTrackId.collectAsState()
    val speed by player.playbackSpeed.collectAsState()
    val isPlaying by player.isPlaying.collectAsState()
    val progress by player.playbackProgress.collectAsState()
    val positionSec by player.currentPositionSec.collectAsState()
    val items by CallRecordingPlayer.items.collectAsState()
    val item = items[message.id] ?: CallRecordingPlayer.Item()
    val isActive = currentTrack == message.id
    val playingNow = isActive && isPlaying

    Row(verticalAlignment = Alignment.CenterVertically, modifier = modifier) {
        val playActionLabel = when {
            item.status == CallRecordingPlayer.Status.LOADING -> "Loading call recording"
            item.status == CallRecordingPlayer.Status.UNAVAILABLE -> "Call recording unavailable"
            item.status == CallRecordingPlayer.Status.FAILED -> "Retry loading call recording"
            playingNow -> "Pause call recording"
            else -> "Play call recording"
        }
        Box(
            modifier = Modifier
                .size(48.dp)
                .pressScaleTarget()
                .clip(CircleShape)
                .background(buttonColor)
                .springClickable(enabled = item.status != CallRecordingPlayer.Status.LOADING) {
                    CallRecordingPlayer.toggle(context, message)
                }
                .semantics { contentDescription = playActionLabel }
                .testTag("call_recording_play_${message.id}"),
            contentAlignment = Alignment.Center
        ) {
            when {
                item.status == CallRecordingPlayer.Status.LOADING ->
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = buttonIconColor)
                item.status == CallRecordingPlayer.Status.UNAVAILABLE ->
                    Icon(Icons.Rounded.Block, contentDescription = null, tint = buttonIconColor, modifier = Modifier.size(20.dp))
                item.status == CallRecordingPlayer.Status.FAILED ->
                    Icon(Icons.Rounded.Refresh, contentDescription = null, tint = buttonIconColor, modifier = Modifier.size(22.dp))
                playingNow ->
                    Icon(Icons.Rounded.Pause, contentDescription = null, tint = buttonIconColor, modifier = Modifier.size(24.dp))
                else ->
                    Icon(Icons.Rounded.PlayArrow, contentDescription = null, tint = buttonIconColor, modifier = Modifier.size(24.dp))
            }
        }

        Spacer(modifier = Modifier.width(10.dp))

        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.PhoneInTalk, contentDescription = null, tint = contentColor.copy(alpha = 0.85f), modifier = Modifier.size(14.dp))
                Spacer(modifier = Modifier.width(5.dp))
                val lengthFormatted = formatRecordingLength(message.durationSeconds)
                Text("Voice Call ($lengthFormatted)", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = contentColor)
                if (isActive && item.parts > 1) {
                    Text(" · part ${item.part + 1} of ${item.parts}", fontSize = 11.sp, color = contentColor.copy(alpha = 0.75f))
                } else if (!isActive) {
                    Text(" · Play Recording", fontSize = 11.sp, color = contentColor.copy(alpha = 0.75f))
                }
            }
            Spacer(modifier = Modifier.height(6.dp))
            // Its waveform, like a voice note's: tap or drag on it to jump there while it plays
            val wave = remember(message.id, message.waveform) { CallRecordingWave.of(message) }
            WaveformView(
                amplitudes = wave,
                progress = if (isActive) progress.coerceIn(0f, 1f) else 0f,
                isPlaying = playingNow,
                activeColor = progressColor,
                inactiveColor = progressColor.copy(alpha = maxOf(trackColor.alpha, 0.35f)),
                onSeek = if (isActive) { fraction -> player.seekTo(fraction) } else null,
                height = 32.dp,
                modifier = Modifier.testTag("call_recording_wave_${message.id}")
            )
            Spacer(modifier = Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                val total = formatRecordingLength(message.durationSeconds)
                Text(
                    text = if (isActive) "${formatRecordingLength(positionSec)} / $total" else total,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    color = contentColor.copy(alpha = 0.85f),
                    style = LocalTextStyle.current.steadyDigits
                )
                if (isActive) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Box(
                        modifier = Modifier
                            .pressScaleTarget()
                            .clip(RoundedCornerShape(8.dp))
                            .background(buttonColor.copy(alpha = 0.35f))
                            .springClickable { player.togglePlaybackSpeed() }
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        val speedLabel = when (speed) {
                            1.5f -> "1.5x"
                            2.0f -> "2x"
                            else -> "1x"
                        }
                        Text(
                            text = speedLabel,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = contentColor
                        )
                    }
                }
                Spacer(modifier = Modifier.weight(1f))
                trailing?.invoke()
            }
            val note = item.note
            if (note != null && (item.status == CallRecordingPlayer.Status.UNAVAILABLE || item.status == CallRecordingPlayer.Status.FAILED)) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(note, fontSize = 11.sp, color = contentColor.copy(alpha = 0.8f))
            } else if (item.status == CallRecordingPlayer.Status.LOADING) {
                Spacer(modifier = Modifier.height(2.dp))
                Text("Loading…", fontSize = 11.sp, color = contentColor.copy(alpha = 0.8f))
            }
        }
    }
}

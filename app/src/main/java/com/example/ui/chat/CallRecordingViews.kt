package com.example.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PhoneInTalk
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.CherishApplication
import com.example.call.CallRecordingPlayer
import com.example.data.model.Message

/** "12:05" / "1:02:05" for a recording's length. */
fun formatRecordingLength(totalSeconds: Int): String {
    val seconds = totalSeconds.coerceAtLeast(0)
    val hours = seconds / 3600
    val minutes = (seconds % 3600) / 60
    val rest = seconds % 60
    return if (hours > 0) "%d:%02d:%02d".format(hours, minutes, rest) else "%d:%02d".format(minutes, rest)
}

/**
 * A call recording's player: play/pause, how far along it is (tap the bar to jump), its length,
 * and plainly when it's loading, gone, or couldn't load (tap to try again). Used in the chat bubble
 * and on the Shared Gallery's Calls tab, so both play the same recording the same way.
 */
@Composable
fun CallRecordingPlayerRow(
    message: Message,
    contentColor: Color,
    buttonColor: Color,
    buttonIconColor: Color,
    trackColor: Color,
    progressColor: Color,
    modifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null
) {
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
                .clip(CircleShape)
                .background(buttonColor)
                .clickable(enabled = item.status != CallRecordingPlayer.Status.LOADING) {
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
                    Icon(Icons.Default.Block, contentDescription = null, tint = buttonIconColor, modifier = Modifier.size(20.dp))
                item.status == CallRecordingPlayer.Status.FAILED ->
                    Icon(Icons.Default.Refresh, contentDescription = null, tint = buttonIconColor, modifier = Modifier.size(22.dp))
                playingNow ->
                    Icon(Icons.Default.Pause, contentDescription = null, tint = buttonIconColor, modifier = Modifier.size(24.dp))
                else ->
                    Icon(Icons.Default.PlayArrow, contentDescription = null, tint = buttonIconColor, modifier = Modifier.size(24.dp))
            }
        }

        Spacer(modifier = Modifier.width(10.dp))

        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.PhoneInTalk, contentDescription = null, tint = contentColor.copy(alpha = 0.85f), modifier = Modifier.size(14.dp))
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
            // The bar: tap anywhere on it to jump there while it plays (generous 32dp tap target)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(32.dp)
                    .pointerInput(isActive) {
                        detectTapGestures { offset ->
                            if (isActive && size.width > 0) player.seekTo(offset.x / size.width)
                        }
                    },
                contentAlignment = Alignment.CenterStart
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(trackColor)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(if (isActive) progress.coerceIn(0f, 1f) else 0f)
                            .fillMaxHeight()
                            .background(progressColor)
                    )
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                val total = formatRecordingLength(message.durationSeconds)
                Text(
                    text = if (isActive) "${formatRecordingLength(positionSec)} / $total" else total,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    color = contentColor.copy(alpha = 0.85f)
                )
                if (isActive) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(buttonColor.copy(alpha = 0.35f))
                            .clickable { player.togglePlaybackSpeed() }
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

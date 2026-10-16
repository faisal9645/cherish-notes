package com.example.call

import android.os.SystemClock
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.platform.LocalContext
import com.example.security.SecurityPreferences
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.BluetoothAudio
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.CallEnd
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.MicOff
import androidx.compose.material.icons.rounded.PhoneInTalk
import androidx.compose.material.icons.rounded.SignalCellularAlt1Bar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.components.AvatarView
import kotlinx.coroutines.delay
import androidx.compose.material3.LocalTextStyle
import com.example.ui.theme.steadyDigits

private val HangUpRed = Color(0xFFE53935)
private val AnswerGreen = Color(0xFF16A34A)
private val RecordingRed = Color(0xFFDC2626)
private val WarningAmber = Color(0xFFD97706)

@Composable
fun CallScreen(
    state: CallUiState,
    showRecordingNotice: Boolean,
    onAnswer: () -> Unit,
    onDecline: () -> Unit,
    onHangUp: () -> Unit,
    onToggleMute: () -> Unit,
    onToggleSpeaker: () -> Unit,
    onSelectRoute: (AudioRoute) -> Unit,
    onMinimize: () -> Unit,
    onRetry: () -> Unit = {},
    onDismiss: () -> Unit = {}
) {
    val colors = MaterialTheme.colorScheme
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(colors.primary.copy(alpha = 0.16f), colors.background, colors.background)))
            .systemBarsPadding()
            .testTag("call_screen")
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(modifier = Modifier.fillMaxWidth().height(48.dp), verticalAlignment = Alignment.CenterVertically) {
                if (state.phase != CallPhase.INCOMING_RINGING && state.phase != CallPhase.ENDED && state.phase != CallPhase.IDLE) {
                    IconButton(
                        onClick = onMinimize,
                        modifier = Modifier
                            .semantics { contentDescription = "Back to the app" }
                            .testTag("call_minimize")
                    ) {
                        Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = null, tint = colors.onBackground)
                    }
                }
                Spacer(modifier = Modifier.weight(1f))
                if (state.isWeakNetwork && state.phase != CallPhase.ENDED) WeakNetworkChip()
            }

            Spacer(modifier = Modifier.height(36.dp))
            CallAvatar(state)
            Spacer(modifier = Modifier.height(20.dp))
            Text(
                text = state.peerName ?: "Voice call",
                fontSize = 24.sp,
                fontWeight = FontWeight.SemiBold,
                color = colors.onBackground,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(6.dp))
            CallStatusText(state)

            // Daily Call Quota Warning Alert (5m and 1m warnings)
            val context = LocalContext.current
            val secPrefs = remember { SecurityPreferences.getInstance(context) }
            val dailyRemainingMinutes by secPrefs.dailyFreeCallMinutesRemaining.collectAsState()
            val callElapsedMinutes = if (state.connectedAtElapsed > 0L) {
                val now = SystemClock.elapsedRealtime()
                ((now - state.connectedAtElapsed) / 60000L).toInt()
            } else 0
            val effectiveRemaining = (dailyRemainingMinutes - callElapsedMinutes).coerceAtLeast(0)

            if (state.phase == CallPhase.CONNECTED && effectiveRemaining <= 5) {
                Spacer(modifier = Modifier.height(10.dp))
                Surface(
                    shape = RoundedCornerShape(50),
                    color = WarningAmber.copy(alpha = 0.16f),
                    border = BorderStroke(1.dp, WarningAmber.copy(alpha = 0.45f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("⚠️", fontSize = 13.sp)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            if (effectiveRemaining <= 1) "1 min free call left today"
                            else "$effectiveRemaining mins free call left today",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = WarningAmber
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            when {
                state.isOnHold -> StatusBanner("On hold while your phone call is on")
                state.peerReconnecting -> StatusBanner("Waiting for the other phone to reconnect…")
            }

            CallControls(
                state = state,
                onAnswer = onAnswer,
                onDecline = onDecline,
                onHangUp = onHangUp,
                onToggleMute = onToggleMute,
                onToggleSpeaker = onToggleSpeaker,
                onSelectRoute = onSelectRoute,
                onRetry = onRetry,
                onDismiss = onDismiss
            )

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun CallAvatar(state: CallUiState) {
    val ringing = state.phase == CallPhase.INCOMING_RINGING || state.phase == CallPhase.OUTGOING_RINGING ||
        state.phase == CallPhase.STARTING
    val pulse = rememberInfiniteTransition(label = "call_pulse")
    val pulseScale by pulse.animateFloat(
        initialValue = 1f,
        targetValue = if (ringing) 1.18f else 1f,
        animationSpec = infiniteRepeatable(tween(1100, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "call_pulse_scale"
    )

    // Live remote voice activity indicator: concentric audio ripples reflecting amplitude
    val remoteNorm = (state.remoteVolume.coerceIn(0, 255) / 255f)
    val animatedAmp by animateFloatAsState(
        targetValue = if (state.phase == CallPhase.CONNECTED) remoteNorm else 0f,
        animationSpec = spring(stiffness = Spring.StiffnessLow),
        label = "call_voice_amp"
    )

    Box(contentAlignment = Alignment.Center) {
        // Outer concentric audio ripple (visual voice amplitude indication)
        if (state.phase == CallPhase.CONNECTED) {
            Box(
                modifier = Modifier
                    .size(175.dp)
                    .scale(1f + (animatedAmp * 0.32f))
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = (0.05f + animatedAmp * 0.22f).coerceIn(0f, 0.4f)))
            )
        }

        // Inner audio ripple / ringing pulse ring
        Box(
            modifier = Modifier
                .size(145.dp)
                .scale(if (ringing) pulseScale else (1f + (animatedAmp * 0.18f)))
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = if (ringing) 0.12f else (0.09f + animatedAmp * 0.28f).coerceIn(0f, 0.5f)))
        )
        if (state.peerName != null && !state.peerPhotoUrl.isNullOrBlank()) {
            AvatarView(photoUrl = state.peerPhotoUrl, name = state.peerName, size = 116.dp, showOnlineBadge = false)
        } else {
            Box(
                modifier = Modifier
                    .size(116.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Rounded.PhoneInTalk, contentDescription = null, tint = Color.White, modifier = Modifier.size(52.dp))
            }
        }
    }
}

@Composable
private fun CallStatusText(state: CallUiState) {
    val colors = MaterialTheme.colorScheme
    val text = when (state.phase) {
        CallPhase.IDLE -> ""
        CallPhase.STARTING -> "Calling…"
        CallPhase.OUTGOING_RINGING -> "Ringing…"
        CallPhase.INCOMING_RINGING -> "Incoming voice call"
        CallPhase.CONNECTING -> "Connecting…"
        CallPhase.RECONNECTING -> "Reconnecting…"
        CallPhase.CONNECTED -> null
        CallPhase.ENDED -> {
            val base = endText(state.endReason)
            val duration = if (state.connectedAtElapsed > 0L) {
                val now = SystemClock.elapsedRealtime()
                val seconds = ((now - state.connectedAtElapsed) / 1000).coerceAtLeast(0)
                " · " + formatCallDuration(seconds)
            } else ""
            val error = if (!state.errorMessage.isNullOrBlank() &&
                state.endReason != CallEndReason.HANGUP && state.endReason != CallEndReason.REMOTE_HANGUP) {
                "\n${state.errorMessage}"
            } else ""
            "$base$duration$error"
        }
    }
    if (text != null) {
        Text(text, fontSize = 16.sp, color = colors.onSurfaceVariant, textAlign = TextAlign.Center)
    } else {
        val now by produceState(SystemClock.elapsedRealtime(), state.connectedAtElapsed) {
            while (true) {
                value = SystemClock.elapsedRealtime()
                delay(500)
            }
        }
        val seconds = if (state.connectedAtElapsed > 0L) ((now - state.connectedAtElapsed) / 1000).coerceAtLeast(0) else 0
        Text(
            formatCallDuration(seconds),
            fontSize = 18.sp,
            fontWeight = FontWeight.Medium,
            color = colors.onBackground,
            style = LocalTextStyle.current.steadyDigits
        )
    }
}

/** What the screen says when a call ends, by why it ended. */
fun endText(reason: CallEndReason?): String = when (reason) {
    CallEndReason.DECLINED, CallEndReason.REMOTE_DECLINED -> "Call declined"
    CallEndReason.BUSY -> "Busy: already on a call"
    CallEndReason.UNAVAILABLE -> "Unavailable right now"
    CallEndReason.NO_ANSWER -> "No answer"
    CallEndReason.CANCELLED -> "Call cancelled"
    CallEndReason.MISSED -> "Missed call"
    CallEndReason.NETWORK -> "Connection lost"
    CallEndReason.PEER_LOST -> "The other phone lost its connection"
    CallEndReason.PERMISSION_DENIED -> "Calls need microphone access"
    CallEndReason.NOT_PAIRED -> "Your partner hasn't joined yet"
    CallEndReason.NOT_ALLOWED -> "Calls aren't available on this phone right now"
    CallEndReason.FAILED -> "Call failed"
    CallEndReason.HANGUP, CallEndReason.REMOTE_HANGUP, null -> "Call ended"
}

fun formatCallDuration(totalSeconds: Long): String {
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) "%d:%02d:%02d".format(hours, minutes, seconds) else "%02d:%02d".format(minutes, seconds)
}

/**
 * The recording's state, only as the backend reports it: "Recording" appears once Agora has
 * confirmed it started, never before; a failure says so plainly.
 */
@Composable
private fun RecordingIndicator(state: CallUiState) {
    // Hidden per user requirement: do not show recording happening on calling screen
}

@Composable
private fun RecordingNoticeCard() {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = "Calls are recorded automatically. Once a call connects, it's recorded for both of you and " +
                "saved privately to your chat and Shared Gallery. Either of you can delete a recording.",
            fontSize = 13.sp,
            lineHeight = 18.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(16.dp)
        )
    }
}

@Composable
private fun WeakNetworkChip() {
    Surface(shape = RoundedCornerShape(50), color = WarningAmber.copy(alpha = 0.14f)) {
        Row(modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.SignalCellularAlt1Bar, contentDescription = null, tint = WarningAmber, modifier = Modifier.size(14.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text("Weak connection", fontSize = 12.sp, color = WarningAmber)
        }
    }
}

@Composable
private fun StatusBanner(text: String) {
    Text(
        text,
        fontSize = 13.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier.padding(bottom = 16.dp)
    )
}

@Composable
private fun CallControls(
    state: CallUiState,
    onAnswer: () -> Unit,
    onDecline: () -> Unit,
    onHangUp: () -> Unit,
    onToggleMute: () -> Unit,
    onToggleSpeaker: () -> Unit,
    onSelectRoute: (AudioRoute) -> Unit,
    onRetry: () -> Unit,
    onDismiss: () -> Unit
) {
    when (state.phase) {
        CallPhase.INCOMING_RINGING -> Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            RoundAction(Icons.Rounded.CallEnd, "Decline", HangUpRed, onDecline, "call_decline")
            RoundAction(Icons.Rounded.Call, "Answer", AnswerGreen, onAnswer, "call_answer")
        }
        CallPhase.STARTING, CallPhase.OUTGOING_RINGING, CallPhase.CONNECTING,
        CallPhase.CONNECTED, CallPhase.RECONNECTING -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                ToggleControl(
                    icon = if (state.isMuted) Icons.Rounded.MicOff else Icons.Rounded.Mic,
                    label = if (state.isMuted) "Unmute" else "Mute",
                    active = state.isMuted,
                    onClick = onToggleMute,
                    tag = "call_mute"
                )
                AudioRouteControl(state, onToggleSpeaker, onSelectRoute)
            }
            Spacer(modifier = Modifier.height(28.dp))
            RoundAction(Icons.Rounded.CallEnd, "Hang up", HangUpRed, onHangUp, "call_hang_up")
        }
        // Over: the screen closes by itself (no Call again or Close to tap)
        else -> Unit
    }
}

@Composable
private fun RoundAction(
    icon: ImageVector,
    label: String,
    color: Color,
    onClick: () -> Unit,
    tag: String,
    iconTint: Color = Color.White
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        IconButton(
            onClick = onClick,
            modifier = Modifier
                .size(72.dp)
                .clip(CircleShape)
                .background(color)
                .semantics { contentDescription = label }
                .testTag(tag)
        ) {
            Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(32.dp))
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(label, fontSize = 13.sp, color = MaterialTheme.colorScheme.onBackground)
    }
}

@Composable
private fun ToggleControl(icon: ImageVector, label: String, active: Boolean, onClick: () -> Unit, tag: String) {
    val colors = MaterialTheme.colorScheme
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        IconButton(
            onClick = onClick,
            modifier = Modifier
                .size(62.dp)
                .clip(CircleShape)
                .background(if (active) colors.onBackground else colors.surfaceVariant)
                .semantics { contentDescription = label }
                .testTag(tag)
        ) {
            Icon(icon, contentDescription = null, tint = if (active) colors.background else colors.onSurface, modifier = Modifier.size(26.dp))
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(label, fontSize = 13.sp, color = colors.onBackground)
    }
}

/** Speaker on/off; with a headset connected, a choice of where the sound goes. */
@Composable
private fun AudioRouteControl(state: CallUiState, onToggleSpeaker: () -> Unit, onSelectRoute: (AudioRoute) -> Unit) {
    var menuOpen by remember { mutableStateOf(false) }
    val hasChoices = state.bluetoothAvailable || state.wiredAvailable
    Box {
        ToggleControl(
            icon = routeIcon(state.route),
            label = routeLabel(state.route),
            active = state.route != AudioRoute.EARPIECE,
            onClick = { if (hasChoices) menuOpen = true else onToggleSpeaker() },
            tag = "call_audio_route"
        )
        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            val routes = buildList {
                if (state.wiredAvailable) add(AudioRoute.WIRED) else add(AudioRoute.EARPIECE)
                add(AudioRoute.SPEAKER)
                if (state.bluetoothAvailable) add(AudioRoute.BLUETOOTH)
            }
            routes.forEach { route ->
                DropdownMenuItem(
                    text = { Text(routeLabel(route), fontWeight = if (route == state.route) FontWeight.Bold else FontWeight.Normal) },
                    leadingIcon = { Icon(routeIcon(route), contentDescription = null) },
                    onClick = {
                        menuOpen = false
                        onSelectRoute(route)
                    }
                )
            }
        }
    }
}

private fun routeIcon(route: AudioRoute): ImageVector = when (route) {
    AudioRoute.EARPIECE -> Icons.Rounded.PhoneInTalk
    AudioRoute.SPEAKER -> Icons.AutoMirrored.Rounded.VolumeUp
    AudioRoute.BLUETOOTH -> Icons.Rounded.BluetoothAudio
    AudioRoute.WIRED -> Icons.Rounded.Headphones
}

private fun routeLabel(route: AudioRoute): String = when (route) {
    AudioRoute.EARPIECE -> "Phone"
    AudioRoute.SPEAKER -> "Speaker"
    AudioRoute.BLUETOOTH -> "Bluetooth"
    AudioRoute.WIRED -> "Headset"
}

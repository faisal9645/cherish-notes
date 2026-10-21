package com.example.call

import android.os.SystemClock
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.MicOff
import androidx.compose.material.icons.rounded.PhoneInTalk
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.components.AvatarView
import kotlinx.coroutines.delay
import kotlin.math.roundToInt
import com.example.ui.components.pressScaleTarget
import com.example.ui.components.springClickable
import androidx.compose.material3.LocalTextStyle
import com.example.ui.theme.steadyDigits
import com.example.ui.components.softShadow

/**
 * Draggable Floating Mini Call Bubble (Picture-in-Picture)
 * Appears across all screens (Gallery, Notes disguise, Chat, Profile) whenever a call is active
 * and the main CallActivity is minimized.
 */
@Composable
fun FloatingCallBubble(
    modifier: Modifier = Modifier
) {
    val callState by CallManager.state.collectAsState()
    val context = LocalContext.current
    // Never over Notes, the Calculator or the lock: the partner's photo and a call timer would give
    // Cherish away (the call goes on; its notification is in the shade)
    val securityPreferences = remember(context) { com.example.security.SecurityPreferences.getInstance(context) }
    val isDisguised by securityPreferences.isDisguiseActive.collectAsState()
    val isLocked by securityPreferences.isAppLocked.collectAsState()
    val isVisible = CallManager.hasActiveCall() &&
        !CallManager.isCallScreenVisible &&
        !isDisguised &&
        !isLocked &&
        callState.phase != CallPhase.INCOMING_RINGING &&
        callState.phase != CallPhase.ENDED &&
        callState.phase != CallPhase.IDLE

    val density = LocalDensity.current
    val config = LocalConfiguration.current

    val screenWidthPx = with(density) { config.screenWidthDp.dp.toPx() }
    val screenHeightPx = with(density) { config.screenHeightDp.dp.toPx() }

    var offsetX by remember { mutableFloatStateOf(with(density) { 16.dp.toPx() }) }
    var offsetY by remember { mutableFloatStateOf(with(density) { 90.dp.toPx() }) }

    AnimatedVisibility(
        visible = isVisible,
        enter = fadeIn() + scaleIn(initialScale = 0.8f),
        exit = fadeOut() + scaleOut(targetScale = 0.8f),
        modifier = modifier
    ) {
        val now by produceState(SystemClock.elapsedRealtime(), callState.connectedAtElapsed) {
            while (true) {
                value = SystemClock.elapsedRealtime()
                delay(500)
            }
        }

        val durationFormatted = remember(now, callState.connectedAtElapsed, callState.phase) {
            if (callState.phase == CallPhase.CONNECTED && callState.connectedAtElapsed > 0L) {
                val sec = ((now - callState.connectedAtElapsed) / 1000L).coerceAtLeast(0)
                formatCallDuration(sec)
            } else if (callState.phase == CallPhase.CONNECTING) {
                "Connecting…"
            } else if (callState.phase == CallPhase.RECONNECTING) {
                "Reconnecting…"
            } else {
                "Calling…"
            }
        }

        Box(
            modifier = Modifier
                .offset { IntOffset(offsetX.roundToInt(), offsetY.roundToInt()) }
                .pointerInput(Unit) {
                    detectDragGestures { change, dragAmount ->
                        change.consume()
                        offsetX = (offsetX + dragAmount.x).coerceIn(10f, screenWidthPx - 500f)
                        offsetY = (offsetY + dragAmount.y).coerceIn(50f, screenHeightPx - 250f)
                    }
                }
        ) {
            Surface(
                shape = RoundedCornerShape(28.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.95f),
                shadowElevation = 10.dp,
                modifier = Modifier
                    .pressScaleTarget()
                    .softShadow(12.dp, RoundedCornerShape(28.dp))
                    .clip(RoundedCornerShape(28.dp))
                    .springClickable {
                        CallActivity.startOutgoing(context)
                    }
            ) {
                Row(
                    modifier = Modifier.padding(start = 6.dp, end = 6.dp, top = 4.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Avatar or Call Icon
                    val peerPhoto = callState.peerPhotoUrl
                    val peerName = callState.peerName ?: "Partner"
                    if (!peerPhoto.isNullOrBlank()) {
                        AvatarView(
                            photoUrl = peerPhoto,
                            name = peerName,
                            size = 36.dp,
                            showOnlineBadge = false
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Rounded.PhoneInTalk,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    // Duration / Status
                    Text(
                        text = durationFormatted,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = LocalTextStyle.current.steadyDigits
                    )

                    Spacer(modifier = Modifier.width(4.dp))

                    // Quick Mute / Unmute Button
                    IconButton(
                        onClick = { CallManager.toggleMute() },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = if (callState.isMuted) Color(0xFFE53935).copy(alpha = 0.15f)
                            else MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                            modifier = Modifier.size(30.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = if (callState.isMuted) Icons.Rounded.MicOff else Icons.Rounded.Mic,
                                    contentDescription = if (callState.isMuted) "Unmute" else "Mute",
                                    tint = if (callState.isMuted) Color(0xFFE53935) else MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

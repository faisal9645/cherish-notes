package com.example.ui.security

import com.example.ui.theme.darkTone

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.example.security.PrivacyShield
import com.example.security.SecurityPreferences
import com.example.ui.theme.HeartRed
import kotlin.math.roundToInt

/** Where the exit handle is docked; shared, so it stays in the same place in every window. */
private object EmergencyExitDock {
    var onLeft by mutableStateOf(false)
    var offsetY by mutableFloatStateOf(0f)
}

/**
 * The draggable side handle that hides the secret app behind Notes in one tap. It shows while the
 * secret app is open (and the setting is on), and is placed in the main window and inside every
 * full-screen secret window (photo viewer, theatre, video note recorder) that would cover it.
 * Hiding closes everything that was open (see CherishNavGraph and ChatViewModel.onSecretAppHidden).
 */
@Composable
fun EmergencyExitHandle(modifier: Modifier = Modifier) {
    val prefs = SecurityPreferences.getInstance(LocalContext.current)
    val isDisguiseActive by prefs.isDisguiseActive.collectAsState()
    val hasRevealedSecretApp by prefs.hasRevealedSecretAppInSession.collectAsState()
    val isAppLocked by prefs.isAppLocked.collectAsState()
    val isEnabled by prefs.isSideEmergencyExitEnabled.collectAsState()
    val opacitySetting by prefs.sideEmergencyExitOpacity.collectAsState()
    if (isDisguiseActive || !hasRevealedSecretApp || isAppLocked || !isEnabled) return

    val exitOpacity = opacitySetting.coerceIn(0.1f, 1.0f)
    val haptic = LocalHapticFeedback.current
    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val onLeft = EmergencyExitDock.onLeft
    // Sideways drag only while the finger is down; docking happens on release
    var dragOffsetX by remember { mutableFloatStateOf(0f) }

    val handleShape = if (onLeft) {
        RoundedCornerShape(topStart = 0.dp, bottomStart = 0.dp, topEnd = 24.dp, bottomEnd = 24.dp)
    } else {
        RoundedCornerShape(topStart = 24.dp, bottomStart = 24.dp, topEnd = 0.dp, bottomEnd = 0.dp)
    }

    Box(modifier = modifier.fillMaxSize()) {
        Surface(
            shape = handleShape,
            color = (if (isDark) darkTone(Color(0xFF1E2638)) else Color(0xFF1F2937)).copy(alpha = exitOpacity),
            border = BorderStroke(
                1.dp,
                if (isDark) darkTone(Color(0xFF2A364F)).copy(alpha = (exitOpacity * 0.7f).coerceIn(0.1f, 0.9f))
                else Color(0xFF111827).copy(alpha = (exitOpacity * 0.5f).coerceIn(0.1f, 0.8f))
            ),
            modifier = Modifier
                .align(if (onLeft) Alignment.CenterStart else Alignment.CenterEnd)
                .offset { IntOffset(dragOffsetX.roundToInt(), EmergencyExitDock.offsetY.roundToInt()) }
                .width(44.dp)
                .height(88.dp)
                .pointerInput(onLeft) {
                    detectDragGestures(
                        onDragEnd = {
                            if (!onLeft && dragOffsetX < -90f) {
                                EmergencyExitDock.onLeft = true
                            } else if (onLeft && dragOffsetX > 90f) {
                                EmergencyExitDock.onLeft = false
                            }
                            dragOffsetX = 0f
                        },
                        onDragCancel = { dragOffsetX = 0f },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            dragOffsetX += dragAmount.x
                            EmergencyExitDock.offsetY = (EmergencyExitDock.offsetY + dragAmount.y).coerceIn(-500f, 500f)
                            // Switch sides as soon as it's dragged far enough across
                            if (!onLeft && dragOffsetX < -180f) {
                                EmergencyExitDock.onLeft = true
                                dragOffsetX = 0f
                            } else if (onLeft && dragOffsetX > 180f) {
                                EmergencyExitDock.onLeft = false
                                dragOffsetX = 0f
                            }
                        }
                    )
                }
                .clickable {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    prefs.forceDisguise()
                }
                .testTag("side_emergency_exit_toggle")
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(start = if (onLeft) 4.dp else 0.dp, end = if (!onLeft) 4.dp else 0.dp),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ExitToApp,
                    contentDescription = "Emergency Exit to Notes",
                    tint = HeartRed.copy(alpha = exitOpacity.coerceAtLeast(0.55f)),
                    modifier = Modifier
                        .size(24.dp)
                        .graphicsLayer { if (onLeft) scaleX = -1f }
                )
            }
        }
    }
}

/**
 * Goes last in the root of a full-screen secret window (photo viewer, theatre, video note
 * recorder), which sits above the main window: adds the emergency exit handle the window would
 * otherwise cover, and the privacy cover for when the user heads to the recent-apps screen from
 * here. [ownDialogOpen]: the window's own popup (e.g. a delete confirmation) is what took focus.
 */
@Composable
fun SecretWindowGuard(ownDialogOpen: Boolean = false) {
    val windowInfo = LocalWindowInfo.current
    val isOwnDialogOpen by rememberUpdatedState(ownDialogOpen)
    LaunchedEffect(windowInfo) {
        var hadFocus = false
        snapshotFlow { windowInfo.isWindowFocused }.collect { focused ->
            if (focused) {
                hadFocus = true
                PrivacyShield.lower()
            } else if (hadFocus && !isOwnDialogOpen) {
                // Focus left for the system (recent apps, notification shade): cover at once
                PrivacyShield.raise()
            }
        }
    }

    EmergencyExitHandle()

    if (PrivacyShield.isRaised) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                // Nothing underneath reacts while covered
                .pointerInput(Unit) {}
        )
    }
}

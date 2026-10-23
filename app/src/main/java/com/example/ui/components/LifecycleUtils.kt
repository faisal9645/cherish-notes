package com.example.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.security.SecurityPreferences

/** A retained/precomposed chat row must not animate or decode video offscreen. */
val LocalVisualWorkAllowed = androidx.compose.runtime.compositionLocalOf { true }

/**
 * Returns true only when the current lifecycle is at least RESUMED (i.e. visible and interactive)
 * AND the app is not hidden behind the Notes disguise screen.
 * When the app goes to background or the disguise screen is active, this returns false,
 * allowing looping animations (hearts, ripples, shimmers, waves, sun glow) to pause immediately.
 */
@Composable
fun rememberAppIsResumed(): Boolean {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val isDisguised by SecurityPreferences.getInstance(context).isDisguiseActive.collectAsState()
    var isResumed by remember {
        mutableStateOf(lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))
    }
    DisposableEffect(lifecycleOwner) {
        isResumed = lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                isResumed = true
            } else if (event == Lifecycle.Event.ON_PAUSE || event == Lifecycle.Event.ON_STOP) {
                isResumed = false
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }
    return isResumed && !isDisguised && LocalVisualWorkAllowed.current
}

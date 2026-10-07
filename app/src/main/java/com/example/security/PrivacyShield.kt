package com.example.security

import android.os.SystemClock
import android.view.MotionEvent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Covers the secret app the moment it stops being the window in front (the recent-apps screen,
 * the notification shade, another app), so a preview never shows the chat. Nothing here uses
 * FLAG_SECURE, so screenshots keep working everywhere.
 *
 * The app's own dialogs and menus also take focus from the chat, but they open from a touch on
 * the app: focus lost while a finger is down, or just after one lifted, counts as in-app. A touch
 * the system takes over for its own gesture (back, home, recents) ends in a cancel rather than a
 * lift, so it doesn't count.
 */
object PrivacyShield {
    /** Whether the cover is up. Full-screen secret windows cover themselves with it as well. */
    var isRaised by mutableStateOf(false)
        private set

    /** Told at once, without waiting for a frame, whenever the cover goes up or down. */
    var onChange: ((raised: Boolean) -> Unit)? = null

    private var pointerDown = false
    private var lastInAppAction = 0L

    /** Feeds the touches on the app's main window. */
    fun onTouch(event: MotionEvent) {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> pointerDown = true
            MotionEvent.ACTION_UP -> {
                pointerDown = false
                lastInAppAction = SystemClock.uptimeMillis()
            }
            // The system took the gesture over (back, home, recents): not an in-app touch
            MotionEvent.ACTION_CANCEL -> pointerDown = false
        }
    }

    /** One of the app's own windows is about to take focus without a touch (e.g. the update dialog). */
    fun noteInAppWindow() {
        lastInAppAction = SystemClock.uptimeMillis()
    }

    /** Whether losing focus right now most likely means the user is heading out of the app. */
    fun isLeavingApp(): Boolean =
        !pointerDown && SystemClock.uptimeMillis() - lastInAppAction > IN_APP_GRACE_MS

    fun raise() = update(true)

    fun lower() = update(false)

    private fun update(raised: Boolean) {
        if (isRaised == raised) return
        isRaised = raised
        onChange?.invoke(raised)
    }

    /** A dialog opened by a tap takes focus well within this time after the finger lifts. */
    private const val IN_APP_GRACE_MS = 600L
}

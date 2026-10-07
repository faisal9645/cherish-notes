package com.example.baselineprofile

import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Records the code Cherish runs while starting and while scrolling the chat. Release builds ship it
 * as a Baseline Profile, so that code is compiled ahead of time instead of running interpreted
 * after every install or OTA update.
 *
 * With an Android 13+ emulator or device connected:
 *   ./gradlew :app:generateBaselineProfile
 * The profile is written to app/src/release/generated/baselineProfiles; commit it.
 *
 * When the device is signed in, the chat journey opens the chat and scrolls through the history.
 * When it is signed out, only startup is recorded.
 */
@RunWith(AndroidJUnit4::class)
@LargeTest
class BaselineProfileGenerator {

    @get:Rule
    val rule = BaselineProfileRule()

    /** Cold start into the Notes screen, with the chat already composing underneath it. */
    @Test
    fun startup() = rule.collect(
        packageName = PACKAGE_NAME,
        includeInStartupProfile = true
    ) {
        pressHome()
        startActivityAndWait()
        device.waitForIdle(IDLE_TIMEOUT_MS)
    }

    /** Opens the chat the way a message notification does, then scrolls older messages and back. */
    @Test
    fun chatScroll() = rule.collect(packageName = PACKAGE_NAME) {
        pressHome()
        startActivityAndWait { intent -> intent.putExtra("open_chat", true) }
        scrollChat()
    }

    private fun MacrobenchmarkScope.scrollChat() {
        // The message list is the tallest scrollable on screen. It's missing when signed out or
        // behind the app lock, and then there is nothing to scroll.
        val list = device.wait(Until.findObjects(By.scrollable(true)), 5_000)
            ?.maxByOrNull { it.visibleBounds.height() }
            ?: return
        // Keeps the gestures clear of the system navigation bar
        list.setGestureMargin(device.displayWidth / 5)
        // The list is reversed: up shows older messages (and loads more pages), down returns
        repeat(4) {
            list.fling(Direction.UP)
            device.waitForIdle(IDLE_TIMEOUT_MS)
        }
        repeat(4) {
            list.fling(Direction.DOWN)
            device.waitForIdle(IDLE_TIMEOUT_MS)
        }
    }

    private companion object {
        const val PACKAGE_NAME = "com.cherish.notes"
        const val IDLE_TIMEOUT_MS = 1_000L
    }
}

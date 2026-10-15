package com.example.util

/**
 * "12:05" / "1:02:05" for a recording's length. Kept out of the screens' Compose code, so saving a
 * call's recording never loads a screen's classes.
 */
fun formatRecordingLength(totalSeconds: Int): String {
    val seconds = totalSeconds.coerceAtLeast(0)
    val hours = seconds / 3600
    val minutes = (seconds % 3600) / 60
    val rest = seconds % 60
    return if (hours > 0) "%d:%02d:%02d".format(hours, minutes, rest) else "%d:%02d".format(minutes, rest)
}

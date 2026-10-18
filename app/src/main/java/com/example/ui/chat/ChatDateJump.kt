package com.example.ui.chat

import androidx.compose.runtime.Composable
import com.example.ui.components.CherishDatePickerDialog

/** How a jump to a day (the calendar in chat search) went. */
enum class DateJumpResult { Found, NextDayFound, NoMessages, OlderChatHidden }

/**
 * The calendar in chat search: pick a day and the chat jumps to its first message. Days after
 * today can't be picked.
 */
@Composable
internal fun JumpToDateDialog(
    onPick: (year: Int, month: Int, dayOfMonth: Int) -> Unit,
    onDismiss: () -> Unit
) {
    CherishDatePickerDialog(
        title = "Jump to date",
        confirmText = "Go",
        onPick = onPick,
        onDismiss = onDismiss
    )
}

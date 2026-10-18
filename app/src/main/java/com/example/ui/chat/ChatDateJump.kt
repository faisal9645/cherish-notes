package com.example.ui.chat

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import java.util.Calendar
import java.util.TimeZone

/** How a jump to a day (the calendar in chat search) went. */
enum class DateJumpResult { Found, NextDayFound, NoMessages, OlderChatHidden }

/**
 * The calendar in chat search: pick a day and the chat jumps to its first message. Days after
 * today can't be picked.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun JumpToDateDialog(
    onPick: (year: Int, month: Int, dayOfMonth: Int) -> Unit,
    onDismiss: () -> Unit
) {
    // The picker counts in UTC days
    val todayUtc = remember { localDayAsUtc(System.currentTimeMillis()) }
    val thisYear = remember { Calendar.getInstance().get(Calendar.YEAR) }
    val state = rememberDatePickerState(
        initialSelectedDateMillis = todayUtc,
        selectableDates = object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long): Boolean = utcTimeMillis <= todayUtc
            override fun isSelectableYear(year: Int): Boolean = year <= thisYear
        }
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = {
                    val picked = state.selectedDateMillis ?: return@TextButton
                    val day = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply { timeInMillis = picked }
                    onPick(day.get(Calendar.YEAR), day.get(Calendar.MONTH), day.get(Calendar.DAY_OF_MONTH))
                },
                enabled = state.selectedDateMillis != null,
                modifier = Modifier.testTag("chat_jump_to_date_go")
            ) { Text("Go") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    ) {
        DatePicker(
            state = state,
            title = {
                Text("Jump to date", modifier = Modifier.padding(start = 24.dp, end = 12.dp, top = 16.dp))
            }
        )
    }
}

/** [localMillis]'s calendar day, as the picker's UTC midnight. */
private fun localDayAsUtc(localMillis: Long): Long {
    val local = Calendar.getInstance().apply { timeInMillis = localMillis }
    return Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
        clear()
        set(local.get(Calendar.YEAR), local.get(Calendar.MONTH), local.get(Calendar.DAY_OF_MONTH))
    }.timeInMillis
}

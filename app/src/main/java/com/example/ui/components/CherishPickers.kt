package com.example.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialogDefaults
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import java.util.Calendar
import java.util.TimeZone

/*
 * The app's date and time pickers: Material dialogs, so every place that asks for a day or a time
 * looks the same.
 */

/**
 * Pick a day. Days after today can't be picked unless [allowFuture]; [initialDateMillis] is any
 * time on the day shown first.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CherishDatePickerDialog(
    title: String,
    onPick: (year: Int, month: Int, dayOfMonth: Int) -> Unit,
    onDismiss: () -> Unit,
    confirmText: String = "OK",
    initialDateMillis: Long = System.currentTimeMillis(),
    allowFuture: Boolean = false
) {
    // The picker counts in UTC days
    val todayUtc = remember { localDayAsUtc(System.currentTimeMillis()) }
    val thisYear = remember { Calendar.getInstance().get(Calendar.YEAR) }
    val state = rememberDatePickerState(
        initialSelectedDateMillis = remember { localDayAsUtc(initialDateMillis) },
        selectableDates = remember(allowFuture) {
            object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long): Boolean = allowFuture || utcTimeMillis <= todayUtc
                override fun isSelectableYear(year: Int): Boolean = allowFuture || year <= thisYear
            }
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
                modifier = Modifier.testTag("date_picker_confirm")
            ) { Text(confirmText) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    ) {
        DatePicker(
            state = state,
            title = {
                Text(title, modifier = Modifier.padding(start = 24.dp, end = 12.dp, top = 16.dp))
            }
        )
    }
}

/** Pick a time of day, in the 12-hour clock the app shows times in. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CherishTimePickerDialog(
    title: String,
    initialHour: Int,
    initialMinute: Int,
    onPick: (hour: Int, minute: Int) -> Unit,
    onDismiss: () -> Unit,
    confirmText: String = "OK"
) {
    val state = rememberTimePickerState(
        initialHour = initialHour.mod(24),
        initialMinute = initialMinute.coerceIn(0, 59),
        is24Hour = false
    )
    BasicAlertDialog(onDismissRequest = onDismiss) {
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            color = AlertDialogDefaults.containerColor,
            tonalElevation = AlertDialogDefaults.TonalElevation
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 20.dp)
                )
                TimePicker(state = state)
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text("Cancel") }
                    TextButton(
                        onClick = { onPick(state.hour, state.minute) },
                        modifier = Modifier.testTag("time_picker_confirm")
                    ) { Text(confirmText) }
                }
            }
        }
    }
}

/** [localMillis]'s calendar day, as a date picker's UTC midnight. */
private fun localDayAsUtc(localMillis: Long): Long {
    val local = Calendar.getInstance().apply { timeInMillis = localMillis }
    return Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
        clear()
        set(local.get(Calendar.YEAR), local.get(Calendar.MONTH), local.get(Calendar.DAY_OF_MONTH))
    }.timeInMillis
}

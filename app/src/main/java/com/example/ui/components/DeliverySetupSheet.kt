package com.example.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.notifications.BackgroundDelivery
import com.example.ui.theme.OnlineGreen

/**
 * Every switch this phone needs so messages and calls reach a closed (or swiped-away) Notes, with
 * a way straight to each one: Xiaomi, Vivo and Samsung (and others) hold back an app's pushes
 * until their own battery and start-up switches allow it. What the phone tells apps is ticked by
 * itself, and checked again on coming back from its settings; the rest the user ticks off.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeliverySetupSheet(onDismiss: () -> Unit) {
    val context = LocalContext.current
    var refresh by remember { mutableIntStateOf(0) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) refresh++
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    val steps = remember(refresh) { BackgroundDelivery.steps(context) }
    val done = remember(steps) {
        mutableStateMapOf<String, Boolean>().apply { steps.forEach { put(it.key, BackgroundDelivery.isDone(context, it)) } }
    }
    val missing = steps.count { it.required && done[it.key] != true }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = CherishDialogDefaults.sheetShape,
        containerColor = CherishDialogDefaults.sheetContainerColor,
        dragHandle = { CherishDialogDefaults.SheetDragHandle() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(start = 20.dp, end = 20.dp, bottom = 24.dp)
                .testTag("delivery_setup_sheet")
        ) {
            Text("Get every message and call", fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Text(
                if (missing == 0) "All set: they reach you even with Notes closed or swiped away."
                else "This phone holds back pushes while Notes is closed until these are on. Find Notes in each list.",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))
            steps.forEach { step ->
                val isOn = done[step.key] == true
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                ) {
                    if (step.isOn == null) {
                        // The phone doesn't say: ticked off by hand
                        Checkbox(
                            checked = isOn,
                            onCheckedChange = { checked ->
                                BackgroundDelivery.setDone(context, step, checked)
                                done[step.key] = checked
                            }
                        )
                    } else {
                        Box(modifier = Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = when {
                                    isOn -> Icons.Rounded.CheckCircle
                                    step.required -> Icons.Rounded.ErrorOutline
                                    else -> Icons.Rounded.RadioButtonUnchecked
                                },
                                contentDescription = if (isOn) "On" else "Off",
                                tint = when {
                                    isOn -> OnlineGreen
                                    step.required -> MaterialTheme.colorScheme.error
                                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                                },
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                        Text(
                            if (step.required) step.title else "${step.title} (optional)",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(step.detail, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (!isOn || step.isOn == null) {
                        TextButton(
                            onClick = { step.open(context) },
                            modifier = Modifier.testTag("delivery_open_${step.key}")
                        ) {
                            Text(if (step.isOn == null) "Open" else "Turn on", fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(BackgroundDelivery.lockInRecentsTip(), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.height(16.dp))
            CherishButton(
                onClick = onDismiss,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
            ) {
                Text(if (missing == 0) "All set" else "Done for now", fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

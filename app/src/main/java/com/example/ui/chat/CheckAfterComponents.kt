package com.example.ui.chat

import com.example.ui.theme.darkTone

import kotlinx.coroutines.launch
import com.example.ui.components.fluidSheetInertia
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccessTime
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.HourglassEmpty
import androidx.compose.material.icons.rounded.HourglassTop
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.NotificationsNone
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.*
import com.example.ui.theme.Gray200
import com.example.ui.theme.Green800
import com.example.ui.theme.MistBlue
import com.example.ui.theme.Red700
import com.example.ui.components.CherishAlertDialog
import com.example.ui.components.CherishButton
import com.example.ui.components.CherishOutlinedButton
import com.example.ui.components.CherishTonalButton
import com.example.ui.theme.steadyDigits
import com.example.ui.components.CherishSwitch

object CheckAfterHelper {
    fun formatTargetTime(timeMillis: Long): String {
        if (timeMillis <= 0L) return ""
        if (timeMillis == Long.MAX_VALUE) return "Later"
        val calTarget = Calendar.getInstance().apply { timeInMillis = timeMillis }
        val calNow = Calendar.getInstance()
        val formattedTime = com.example.util.ChatTimeFormatter.formatMessageTime(timeMillis)

        val isSameDay = calTarget.get(Calendar.YEAR) == calNow.get(Calendar.YEAR) &&
                calTarget.get(Calendar.DAY_OF_YEAR) == calNow.get(Calendar.DAY_OF_YEAR)

        calNow.add(Calendar.DAY_OF_YEAR, 1)
        val isTomorrow = calTarget.get(Calendar.YEAR) == calNow.get(Calendar.YEAR) &&
                calTarget.get(Calendar.DAY_OF_YEAR) == calNow.get(Calendar.DAY_OF_YEAR)

        return when {
            isSameDay -> formattedTime
            isTomorrow -> "Tomorrow, $formattedTime"
            else -> {
                val datePart = SimpleDateFormat("MMM d", Locale.US).format(Date(timeMillis))
                "$datePart, $formattedTime"
            }
        }
    }

    fun calculateRemaining(targetMillis: Long): Pair<String, Boolean> {
        if (targetMillis == Long.MAX_VALUE) return "Paused until you open" to false
        val now = System.currentTimeMillis()
        val diff = targetMillis - now
        if (diff <= 0) {
            return "✨ You can check now" to true
        }

        val totalSeconds = diff / 1000
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60

        val formatted = when {
            hours >= 24 -> "${hours / 24}d ${hours % 24}h remaining"
            hours > 0 -> "${hours}h ${minutes}m remaining"
            minutes > 0 -> "${minutes}m ${seconds}s remaining"
            else -> "${seconds}s remaining"
        }
        return formatted to false
    }

    fun getTonightMillis(): Long {
        val cal = Calendar.getInstance()
        if (cal.get(Calendar.HOUR_OF_DAY) < 22) {
            cal.set(Calendar.HOUR_OF_DAY, 22)
            cal.set(Calendar.MINUTE, 0)
            cal.set(Calendar.SECOND, 0)
            cal.set(Calendar.MILLISECOND, 0)
        } else if (cal.get(Calendar.HOUR_OF_DAY) == 22 && cal.get(Calendar.MINUTE) < 30) {
            cal.set(Calendar.HOUR_OF_DAY, 23)
            cal.set(Calendar.MINUTE, 30)
            cal.set(Calendar.SECOND, 0)
            cal.set(Calendar.MILLISECOND, 0)
        } else {
            cal.add(Calendar.DAY_OF_YEAR, 1)
            cal.set(Calendar.HOUR_OF_DAY, 8)
            cal.set(Calendar.MINUTE, 0)
            cal.set(Calendar.SECOND, 0)
            cal.set(Calendar.MILLISECOND, 0)
        }
        return cal.timeInMillis
    }
}

/**
 * Compact, elegant Chat Banner for active Check-After status
 */
@Composable
fun CheckAfterChatBanner(
    targetMillis: Long,
    note: String,
    isSetByMe: Boolean,
    partnerName: String,
    isReminderEnabled: Boolean,
    onToggleReminder: () -> Unit,
    onExtend30m: () -> Unit,
    onExtend1h: () -> Unit,
    onChangeTime: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    var currentTime by remember { mutableLongStateOf(System.currentTimeMillis()) }

    LaunchedEffect(targetMillis) {
        while (true) {
            currentTime = System.currentTimeMillis()
            delay(1000)
        }
    }

    val (remainingText, isExpired) = remember(currentTime, targetMillis) {
        CheckAfterHelper.calculateRemaining(targetMillis)
    }

    // The banner's heart beats a few times when it appears, then rests (the banner can stay up
    // for hours)
    val heartBeat = remember { androidx.compose.animation.core.Animatable(1f) }
    LaunchedEffect(Unit) {
        repeat(4) {
            heartBeat.animateTo(1.12f, tween(900, easing = FastOutSlowInEasing))
            heartBeat.animateTo(1f, tween(900, easing = FastOutSlowInEasing))
        }
    }
    val heartScale = heartBeat.value

    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val primaryAccent = MaterialTheme.colorScheme.primary

    val bannerBgGradient = if (isExpired) {
        Brush.horizontalGradient(
            colors = listOf(Green800.copy(alpha = 0.15f), Color(0xFF81C784).copy(alpha = 0.15f))
        )
    } else {
        if (isDark) {
            Brush.horizontalGradient(
                colors = listOf(Color(0xFF0B132B), Color(0xFF1C2541))
            )
        } else {
            Brush.horizontalGradient(
                colors = listOf(MistBlue, Gray200)
            )
        }
    }

    val borderColor = if (isExpired) Color(0xFF4CAF50) else primaryAccent.copy(alpha = 0.35f)

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = Color.Transparent,
        border = BorderStroke(1.dp, borderColor),
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(bannerBgGradient)
            .testTag("check_after_chat_banner")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = if (isExpired) Icons.Rounded.CheckCircle else Icons.Rounded.Favorite,
                        contentDescription = null,
                        tint = if (isExpired) Green800 else primaryAccent,
                        modifier = Modifier
                            .size(18.dp)
                            .scale(if (isExpired) 1f else heartScale)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (isExpired) {
                            "✨ You can check now"
                        } else if (isSetByMe) {
                            "❤️ Check-After is active"
                        } else {
                            "❤️ Check after ${CheckAfterHelper.formatTargetTime(targetMillis)}"
                        },
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = if (isExpired) (if (isDark) darkTone(Color(0xFF86EFAC)) else Color(0xFF1B5E20)) else MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                if (!isSetByMe && !isExpired) {
                    IconButton(
                        onClick = onToggleReminder,
                        modifier = Modifier.size(26.dp)
                    ) {
                        Icon(
                            imageVector = if (isReminderEnabled) Icons.Rounded.NotificationsActive else Icons.Rounded.NotificationsNone,
                            contentDescription = "Toggle reminder",
                            tint = if (isReminderEnabled) primaryAccent else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(2.dp))

            if (!isExpired) {
                Text(
                    text = if (isSetByMe) {
                        "Quiet time until ${CheckAfterHelper.formatTargetTime(targetMillis)}. $partnerName knows you'll be back soon."
                    } else {
                        "$partnerName is taking quiet time until ${CheckAfterHelper.formatTargetTime(targetMillis)}. Leave a sweet note for them."
                    },
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 15.sp
                )

                Spacer(modifier = Modifier.height(4.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = primaryAccent.copy(alpha = 0.12f)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.HourglassEmpty,
                                contentDescription = null,
                                tint = primaryAccent,
                                modifier = Modifier.size(12.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = remainingText,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = primaryAccent,
                                style = LocalTextStyle.current.steadyDigits
                            )
                        }
                    }

                    if (note.isNotBlank()) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                        ) {
                            Text(
                                text = note,
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                if (isSetByMe) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CherishTonalButton(
                            onClick = onExtend30m,
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                            modifier = Modifier.height(28.dp),
                            colors = ButtonDefaults.filledTonalButtonColors(
                                containerColor = primaryAccent.copy(alpha = 0.15f),
                                contentColor = primaryAccent
                            )
                        ) {
                            Text("+30m", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }

                        CherishTonalButton(
                            onClick = onExtend1h,
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                            modifier = Modifier.height(28.dp),
                            colors = ButtonDefaults.filledTonalButtonColors(
                                containerColor = primaryAccent.copy(alpha = 0.15f),
                                contentColor = primaryAccent
                            )
                        ) {
                            Text("+1h", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }

                        CherishOutlinedButton(
                            onClick = onChangeTime,
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                            modifier = Modifier.height(28.dp),
                            border = BorderStroke(1.dp, primaryAccent.copy(alpha = 0.4f))
                        ) {
                            Text("Change", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface)
                        }

                        TextButton(
                            onClick = onCancel,
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                            modifier = Modifier.height(28.dp)
                        ) {
                            Text("Cancel", fontSize = 11.sp, color = Red700)
                        }
                    }
                }
            } else {
                Text(
                    text = "$partnerName's check-after period is done. Feel free to check in!",
                    fontSize = 12.sp,
                    color = Green800
                )
            }
        }
    }
}

/**
 * Compact, streamlined Check-After Bottom Sheet
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CheckAfterBottomSheet(
    isOpen: Boolean,
    onDismiss: () -> Unit,
    currentTargetMillis: Long?,
    currentNote: String?,
    isCurrentlyActive: Boolean,
    isSetByMe: Boolean,
    partnerName: String,
    isReminderEnabled: Boolean,
    onToggleReminder: (Boolean) -> Unit,
    onSaveCheckAfter: (targetMillis: Long, note: String) -> Unit,
    onCancelCheckAfter: () -> Unit,
    onExtend: (Long) -> Unit,
    // More than 2 days: asked of the partner instead of set at once (null: set at once)
    onRequestSpace: ((targetMillis: Long, note: String) -> Unit)? = null
) {
    if (!isOpen) return

    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val primaryAccent = MaterialTheme.colorScheme.primary

    val presetDurations = remember {
        listOf(
            "30m" to (30 * 60 * 1000L),
            "1h" to (1 * 3600 * 1000L),
            "2h" to (2 * 3600 * 1000L),
            "3h" to (3 * 3600 * 1000L),
            "5h" to (5 * 3600 * 1000L),
            "8h" to (8 * 3600 * 1000L),
            "Tonight" to -1L,
            "1 day" to com.example.data.model.SpaceRequest.DAY_MS,
            "2 days" to 2 * com.example.data.model.SpaceRequest.DAY_MS,
            "Days" to -4L,
            "Until Open" to -3L,
            "Custom" to -2L
        )
    }
    // Personal space for some days (more than 2 needs the partner's OK)
    var customDays by remember { mutableIntStateOf(3) }
    var showDaysPicker by remember { mutableStateOf(false) }
    // "Custom": a time of day, from the app's own (Material) time picker
    var showTimePicker by remember { mutableStateOf(false) }

    val quickNotes = remember {
        listOf(
            "Focusing 💻",
            "Napping 😴",
            "Studying 📚",
            "Family 🏡",
            "Quiet time 🧘",
            "Driving 🚗"
        )
    }

    var selectedPreset: String? by remember { mutableStateOf<String?>("2h") }
    var selectedNote by remember { mutableStateOf(currentNote ?: "") }
    var targetMillis by remember {
        mutableLongStateOf(
            if (isCurrentlyActive && currentTargetMillis != null && currentTargetMillis > System.currentTimeMillis()) {
                currentTargetMillis
            } else {
                System.currentTimeMillis() + (2 * 3600 * 1000L)
            }
        )
    }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = com.example.ui.components.CherishDialogDefaults.sheetContainerColor,
        shape = com.example.ui.components.CherishDialogDefaults.sheetShape,
        dragHandle = { com.example.ui.components.CherishDialogDefaults.SheetDragHandle() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fluidSheetInertia()
                .padding(horizontal = 20.dp, vertical = 6.dp)
                .navigationBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Header
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = Icons.Rounded.HourglassTop,
                    contentDescription = null,
                    tint = primaryAccent,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Check After",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "Set a peaceful countdown so your partner knows when you'll be back.",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Currently Active Card (prominent active-state hierarchy)
            if (isCurrentlyActive && currentTargetMillis != null && currentTargetMillis > System.currentTimeMillis()) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    border = BorderStroke(1.dp, primaryAccent.copy(alpha = 0.4f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Rounded.Favorite,
                                    contentDescription = null,
                                    tint = primaryAccent,
                                    modifier = Modifier.size(15.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Active: ${CheckAfterHelper.formatTargetTime(currentTargetMillis)}",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp,
                                    color = primaryAccent
                                )
                            }
                            val (remaining, _) = CheckAfterHelper.calculateRemaining(currentTargetMillis)
                            Text(
                                text = "⏳ $remaining",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            CherishTonalButton(
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    onExtend(15 * 60 * 1000L)
                                    onDismiss()
                                },
                                modifier = Modifier.weight(1f).height(32.dp),
                                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp),
                                colors = ButtonDefaults.filledTonalButtonColors(
                                    containerColor = primaryAccent.copy(alpha = 0.12f),
                                    contentColor = primaryAccent
                                )
                            ) {
                                Text("+15m", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }

                            CherishTonalButton(
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    onExtend(30 * 60 * 1000L)
                                    onDismiss()
                                },
                                modifier = Modifier.weight(1f).height(32.dp),
                                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp),
                                colors = ButtonDefaults.filledTonalButtonColors(
                                    containerColor = primaryAccent.copy(alpha = 0.12f),
                                    contentColor = primaryAccent
                                )
                            ) {
                                Text("+30m", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }

                            CherishTonalButton(
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    onExtend(60 * 60 * 1000L)
                                    onDismiss()
                                },
                                modifier = Modifier.weight(1f).height(32.dp),
                                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp),
                                colors = ButtonDefaults.filledTonalButtonColors(
                                    containerColor = primaryAccent.copy(alpha = 0.12f),
                                    contentColor = primaryAccent
                                )
                            ) {
                                Text("+1h", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }

                            CherishOutlinedButton(
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    onCancelCheckAfter()
                                    onDismiss()
                                },
                                modifier = Modifier.height(32.dp),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                border = BorderStroke(1.dp, Red700.copy(alpha = 0.4f))
                            ) {
                                Text("Cancel", fontSize = 11.sp, color = Red700)
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(10.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, thickness = 0.8.dp)
                Spacer(modifier = Modifier.height(8.dp))
            }

            // Presets
            Text(
                text = "Select Duration",
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(6.dp))

            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(presetDurations, key = { it.first }) { (label, duration) ->
                    val isSelected = selectedPreset == label
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (isSelected) primaryAccent else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                        border = if (isSelected) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                        modifier = Modifier
                            .clickable {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                selectedPreset = label
                                when (label) {
                                    "Tonight" -> {
                                        targetMillis = CheckAfterHelper.getTonightMillis()
                                    }
                                    "Until Open" -> {
                                        targetMillis = Long.MAX_VALUE
                                    }
                                    "Days" -> {
                                        showDaysPicker = true
                                    }
                                    "Custom" -> {
                                        showTimePicker = true
                                    }
                                    else -> {
                                        targetMillis = System.currentTimeMillis() + duration
                                    }
                                }
                            }
                    ) {
                        Text(
                            text = if (label == "Days") (if (isSelected) "$customDays days" else "Days…") else label,
                            fontSize = 12.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Mechanical Watch Crown Rotary Dial for precise timer tuning with angular inertia & tactile micro-clicks
            RotaryCrownTimePicker(
                currentMinutes = remember(targetMillis) {
                    if (targetMillis == Long.MAX_VALUE) 60
                    else ((targetMillis - System.currentTimeMillis()).coerceAtLeast(0L) / 60000L).toInt().coerceIn(5, 720)
                },
                onMinutesChanged = { mins ->
                    selectedPreset = null
                    targetMillis = System.currentTimeMillis() + (mins * 60_000L)
                }
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Calculated Target Time Preview Box
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                border = BorderStroke(1.dp, primaryAccent.copy(alpha = 0.25f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.AccessTime,
                        contentDescription = null,
                        tint = primaryAccent,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Check after: ",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = CheckAfterHelper.formatTargetTime(targetMillis),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Reason Chips
            Text(
                text = "Reason / Mood (Optional)",
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(4.dp))

            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(quickNotes, key = { it }) { noteTag ->
                    val isTagSelected = selectedNote == noteTag
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (isTagSelected) primaryAccent.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceVariant,
                        border = BorderStroke(
                            1.dp,
                            if (isTagSelected) primaryAccent else MaterialTheme.colorScheme.outlineVariant
                        ),
                        modifier = Modifier.clickable {
                            selectedNote = if (isTagSelected) "" else noteTag
                        }
                    ) {
                        Text(
                            text = noteTag,
                            fontSize = 11.sp,
                            color = if (isTagSelected) primaryAccent else MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Custom note input field
            OutlinedTextField(
                value = selectedNote,
                onValueChange = { selectedNote = it },
                placeholder = { Text("Or custom note...", fontSize = 12.sp) },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Compact Reminder Toggle Switch
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Rounded.Notifications,
                        contentDescription = null,
                        tint = primaryAccent,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Discreet Arrival Notification",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                CherishSwitch(
                    checked = isReminderEnabled,
                    onCheckedChange = { onToggleReminder(it) },
                    modifier = Modifier.scale(0.8f),
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = primaryAccent
                    )
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // More than 2 days: personal space, which the partner accepts first
            val needsOk = onRequestSpace != null && targetMillis != Long.MAX_VALUE &&
                targetMillis - System.currentTimeMillis() > com.example.data.model.SpaceRequest.NEEDS_OK_AFTER_MS
            val askDays = ((targetMillis - System.currentTimeMillis() + com.example.data.model.SpaceRequest.DAY_MS / 2) /
                com.example.data.model.SpaceRequest.DAY_MS).toInt().coerceAtLeast(1)
            if (needsOk) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = primaryAccent.copy(alpha = 0.08f),
                    border = BorderStroke(1.dp, primaryAccent.copy(alpha = 0.3f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "More than 2 days needs $partnerName's OK. They'll see your request in the chat, and it starts when they accept.",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                        lineHeight = 15.sp,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
            }

            // Save / Set Button (compact 42dp height)
            CherishButton(
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    if (needsOk) onRequestSpace?.invoke(targetMillis, selectedNote)
                    else onSaveCheckAfter(targetMillis, selectedNote)
                    onDismiss()
                },
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = primaryAccent),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(42.dp)
                    .testTag("set_check_after_button")
            ) {
                Icon(Icons.Rounded.Favorite, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = if (needsOk) "Ask $partnerName for $askDays days" else "Set Check-After",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }

            Spacer(modifier = Modifier.height(4.dp))
        }
    }

    if (showTimePicker) {
        val now = remember { Calendar.getInstance() }
        com.example.ui.components.CherishTimePickerDialog(
            title = "Check after",
            initialHour = now.get(Calendar.HOUR_OF_DAY) + 1,
            initialMinute = now.get(Calendar.MINUTE),
            onPick = { hourOfDay, minute ->
                showTimePicker = false
                // That time today, or tomorrow when it has passed
                targetMillis = Calendar.getInstance().apply {
                    set(Calendar.HOUR_OF_DAY, hourOfDay)
                    set(Calendar.MINUTE, minute)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                    if (timeInMillis <= System.currentTimeMillis()) {
                        add(Calendar.DAY_OF_YEAR, 1)
                    }
                }.timeInMillis
            },
            onDismiss = { showTimePicker = false }
        )
    }

    if (showDaysPicker) {
        val until = System.currentTimeMillis() + customDays * com.example.data.model.SpaceRequest.DAY_MS
        CherishAlertDialog(
            onDismissRequest = { showDaysPicker = false },
            title = { Text("Personal space") },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { customDays = (customDays - 1).coerceAtLeast(1) }, enabled = customDays > 1) {
                            Icon(Icons.Rounded.Remove, contentDescription = "Fewer days")
                        }
                        Text(
                            text = if (customDays == 1) "1 day" else "$customDays days",
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .widthIn(min = 110.dp)
                                .testTag("check_after_days"),
                            textAlign = TextAlign.Center
                        )
                        IconButton(onClick = { customDays = (customDays + 1).coerceAtMost(30) }, enabled = customDays < 30) {
                            Icon(Icons.Rounded.Add, contentDescription = "More days")
                        }
                    }
                    Text(
                        text = "Until ${CheckAfterHelper.formatTargetTime(until)}",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (customDays > 2) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "More than 2 days: $partnerName accepts it first.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.primary,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    targetMillis = System.currentTimeMillis() + customDays * com.example.data.model.SpaceRequest.DAY_MS
                    showDaysPicker = false
                }) { Text("Choose") }
            },
            dismissButton = { TextButton(onClick = { showDaysPicker = false }) { Text("Cancel") } }
        )
    }
}

/**
 * Personal space in the chat: the partner's request (accept or not now), my request waiting for
 * them, or their answer to mine (shown once). Nothing when there's nothing to show.
 */
@Composable
fun SpaceRequestCard(
    request: com.example.data.model.SpaceRequest,
    myId: String,
    partnerName: String,
    answerSeen: Boolean,
    onAccept: () -> Unit,
    onDecline: () -> Unit,
    onCancel: () -> Unit,
    onAnswerSeen: () -> Unit,
    modifier: Modifier = Modifier
) {
    val primaryAccent = MaterialTheme.colorScheme.primary
    val fromMe = request.fromId == myId
    val answered = request.status == com.example.data.model.SpaceRequest.STATUS_ACCEPTED ||
        request.status == com.example.data.model.SpaceRequest.STATUS_DECLINED
    val show = request.isPending || (fromMe && answered && !answerSeen && request.respondedAt > 0L &&
        System.currentTimeMillis() - request.respondedAt < 3 * com.example.data.model.SpaceRequest.DAY_MS)
    if (!show) return
    val until = CheckAfterHelper.formatTargetTime(request.targetMillis)
    val days = if (request.days == 1) "1 day" else "${request.days} days"

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = primaryAccent.copy(alpha = 0.08f),
        border = BorderStroke(1.dp, primaryAccent.copy(alpha = 0.35f)),
        modifier = modifier
            .fillMaxWidth()
            .testTag("space_request_card")
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Rounded.Favorite,
                    contentDescription = null,
                    tint = primaryAccent,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = when {
                        request.isPending && !fromMe -> "$partnerName asks for some personal space"
                        request.isPending -> "Waiting for $partnerName to accept"
                        request.status == com.example.data.model.SpaceRequest.STATUS_ACCEPTED -> "$partnerName accepted ❤️"
                        else -> "$partnerName said not now"
                    },
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = when {
                    request.isPending && !fromMe -> "$days of quiet time, until $until. It starts when you accept."
                    request.isPending -> "Your $days of quiet time, until $until, starts when $partnerName accepts."
                    request.status == com.example.data.model.SpaceRequest.STATUS_ACCEPTED -> "Your $days of quiet time is on, until $until."
                    else -> "Maybe talk it over together, or pick 2 days or less."
                },
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 16.sp
            )
            if (request.note.isNotBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)) {
                    Text(
                        text = request.note,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                when {
                    request.isPending && !fromMe -> {
                        CherishButton(
                            onClick = onAccept,
                            colors = ButtonDefaults.buttonColors(containerColor = primaryAccent),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                            modifier = Modifier
                                .height(34.dp)
                                .testTag("space_request_accept")
                        ) { Text("Accept", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White) }
                        CherishOutlinedButton(
                            onClick = onDecline,
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp),
                            modifier = Modifier
                                .height(34.dp)
                                .testTag("space_request_decline")
                        ) { Text("Not now", fontSize = 13.sp) }
                    }
                    request.isPending -> {
                        TextButton(
                            onClick = onCancel,
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                            modifier = Modifier
                                .height(30.dp)
                                .testTag("space_request_cancel")
                        ) { Text("Cancel request", fontSize = 12.sp, color = MaterialTheme.colorScheme.error) }
                    }
                    else -> {
                        TextButton(
                            onClick = onAnswerSeen,
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                            modifier = Modifier.height(30.dp)
                        ) { Text("OK", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = primaryAccent) }
                    }
                }
            }
        }
    }
}

/**
 * Rotary Dial Physics for "Pick a Time" / Timers:
 * Feels like turning a high-end mechanical watch crown.
 * Each notch has an angular inertia decay with subtle micro-clicks (HapticFeedbackType.TextHandleMove).
 */
@Composable
fun RotaryCrownTimePicker(
    currentMinutes: Int,
    minMinutes: Int = 5,
    maxMinutes: Int = 720,
    stepMinutes: Int = 5,
    onMinutesChanged: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val angularOffset = remember { Animatable(currentMinutes.toFloat()) }
    var lastHapticNotch by remember { mutableIntStateOf(currentMinutes / stepMinutes) }

    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        // Watch crown dial display header
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier.padding(bottom = 6.dp)
        ) {
            val totalMins = angularOffset.value.toInt().coerceIn(minMinutes, maxMinutes)
            val hours = totalMins / 60
            val mins = totalMins % 60
            val timeLabel = when {
                hours > 0 && mins > 0 -> "${hours}h ${mins}m"
                hours > 0 -> "${hours} hour${if (hours > 1) "s" else ""}"
                else -> "$mins minutes"
            }
            Text(
                text = "Crown Dial: ",
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = timeLabel,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        }

        // Mechanical Watch Crown Cylindrical Wheel
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(46.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(
                    if (isDark) Brush.horizontalGradient(
                        listOf(
                            Color(0xFF1E2129),
                            Color(0xFF333842),
                            Color(0xFF4C5362),
                            Color(0xFF333842),
                            Color(0xFF1E2129)
                        )
                    ) else Brush.horizontalGradient(
                        listOf(
                            Color(0xFFD1D5DB),
                            Color(0xFFF3F4F6),
                            Color(0xFFFFFFFF),
                            Color(0xFFF3F4F6),
                            Color(0xFFD1D5DB)
                        )
                    )
                )
                .border(
                    1.dp,
                    if (isDark) Color.White.copy(alpha = 0.15f) else Color.Black.copy(alpha = 0.12f),
                    RoundedCornerShape(12.dp)
                )
                .pointerInput(Unit) {
                    var lastDragTime = 0L
                    detectHorizontalDragGestures(
                        onDragStart = {
                            lastDragTime = System.currentTimeMillis()
                        },
                        onDragEnd = {
                            // Angular inertia decay down to nearest notch
                            scope.launch {
                                val targetSnap = (kotlin.math.round(angularOffset.value / stepMinutes) * stepMinutes)
                                    .coerceIn(minMinutes.toFloat(), maxMinutes.toFloat())
                                angularOffset.animateTo(
                                    targetValue = targetSnap,
                                    animationSpec = spring(
                                        dampingRatio = Spring.DampingRatioMediumBouncy,
                                        stiffness = Spring.StiffnessMediumLow
                                    )
                                )
                                onMinutesChanged(targetSnap.toInt())
                            }
                        },
                        onDragCancel = {
                            scope.launch {
                                val targetSnap = (kotlin.math.round(angularOffset.value / stepMinutes) * stepMinutes)
                                    .coerceIn(minMinutes.toFloat(), maxMinutes.toFloat())
                                angularOffset.animateTo(targetSnap, spring(dampingRatio = Spring.DampingRatioMediumBouncy))
                                onMinutesChanged(targetSnap.toInt())
                            }
                        },
                        onHorizontalDrag = { change, dragAmount ->
                            change.consume()
                            lastDragTime = System.currentTimeMillis()

                            // 1dp drag moves approx 0.4 minutes
                            val deltaMins = -dragAmount * 0.35f
                            val nextVal = (angularOffset.value + deltaMins).coerceIn(minMinutes.toFloat(), maxMinutes.toFloat())
                            val currentNotch = (nextVal / stepMinutes).toInt()

                            if (currentNotch != lastHapticNotch) {
                                lastHapticNotch = currentNotch
                                try {
                                    // Mechanical crown micro-click tactile feedback
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                } catch (_: Exception) {}
                                onMinutesChanged(currentNotch * stepMinutes)
                            }
                            scope.launch { angularOffset.snapTo(nextVal) }
                        }
                    )
                },
            contentAlignment = Alignment.Center
        ) {
            // Milled Crown Grooves/Notches
            androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
                val notchSpacing = 16.dp.toPx()
                val offsetPx = (angularOffset.value * 2.8f) % notchSpacing
                val numNotches = (size.width / notchSpacing).toInt() + 4

                for (i in -2..numNotches) {
                    val x = i * notchSpacing - offsetPx
                    if (x in 0f..size.width) {
                        val distFromCenter = kotlin.math.abs(x - size.width / 2f) / (size.width / 2f)
                        val notchAlpha = (1f - distFromCenter * distFromCenter).coerceIn(0.1f, 0.9f)
                        val isMajor = (i % 3 == 0)
                        val notchHeight = if (isMajor) size.height * 0.65f else size.height * 0.4f
                        val startY = (size.height - notchHeight) / 2f
                        val endY = startY + notchHeight

                        drawLine(
                            color = (if (isDark) Color.White else Color.Black).copy(alpha = notchAlpha * 0.45f),
                            start = Offset(x, startY),
                            end = Offset(x, endY),
                            strokeWidth = if (isMajor) 2.2.dp.toPx() else 1.2.dp.toPx()
                        )
                    }
                }

                // Center jewel / detent alignment marker
                val centerX = size.width / 2f
                drawLine(
                    color = Color(0xFFF43F5E), // Rose-red jewel indicator
                    start = Offset(centerX, 4.dp.toPx()),
                    end = Offset(centerX, size.height - 4.dp.toPx()),
                    strokeWidth = 2.5.dp.toPx()
                )
            }
        }
    }
}

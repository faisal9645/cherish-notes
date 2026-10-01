package com.example.ui.chat

import android.app.TimePickerDialog
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.*
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.*

object CheckAfterHelper {
    fun formatTargetTime(timeMillis: Long): String {
        if (timeMillis <= 0L) return ""
        val calTarget = Calendar.getInstance().apply { timeInMillis = timeMillis }
        val calNow = Calendar.getInstance()
        val timeFormat = SimpleDateFormat("h:mm a", Locale.getDefault())
        val formattedTime = timeFormat.format(Date(timeMillis))

        val isSameDay = calTarget.get(Calendar.YEAR) == calNow.get(Calendar.YEAR) &&
                calTarget.get(Calendar.DAY_OF_YEAR) == calNow.get(Calendar.DAY_OF_YEAR)

        calNow.add(Calendar.DAY_OF_YEAR, 1)
        val isTomorrow = calTarget.get(Calendar.YEAR) == calNow.get(Calendar.YEAR) &&
                calTarget.get(Calendar.DAY_OF_YEAR) == calNow.get(Calendar.DAY_OF_YEAR)

        return when {
            isSameDay -> formattedTime
            isTomorrow -> "Tomorrow, $formattedTime"
            else -> {
                val dateFormat = SimpleDateFormat("MMM d, h:mm a", Locale.getDefault())
                dateFormat.format(Date(timeMillis))
            }
        }
    }

    fun calculateRemaining(targetMillis: Long): Pair<String, Boolean> {
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
            hours > 0 -> "${hours}h ${minutes}m remaining"
            minutes > 0 -> "${minutes}m ${seconds}s remaining"
            else -> "${seconds}s remaining"
        }
        return formatted to false
    }

    fun getTonightMillis(): Long {
        val cal = Calendar.getInstance()
        // If before 10:00 PM, set to 10:00 PM tonight. Else set to 11:30 PM tonight or next day 8:00 AM
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
            // Next morning 8 AM
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
 * Romantic Chat Banner for active Check-After status
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

    val infiniteTransition = rememberInfiniteTransition(label = "banner_pulse")
    val heartScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.14f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "heart_scale"
    )

    val bannerBgGradient = if (isExpired) {
        Brush.horizontalGradient(
            colors = listOf(Color(0xFF2E7D32).copy(alpha = 0.15f), Color(0xFF81C784).copy(alpha = 0.15f))
        )
    } else {
        Brush.horizontalGradient(
            colors = listOf(Color(0xFFFFF0F5), Color(0xFFFDE8E9))
        )
    }

    val borderColor = if (isExpired) Color(0xFF4CAF50) else RoseGoldPrimary.copy(alpha = 0.4f)

    Surface(
        shape = RoundedCornerShape(18.dp),
        color = Color.Transparent,
        border = BorderStroke(1.dp, borderColor),
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(bannerBgGradient)
            .testTag("check_after_chat_banner")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
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
                        imageVector = if (isExpired) Icons.Filled.CheckCircle else Icons.Filled.Favorite,
                        contentDescription = null,
                        tint = if (isExpired) Color(0xFF2E7D32) else RoseGoldPrimary,
                        modifier = Modifier
                            .size(20.dp)
                            .scale(if (isExpired) 1f else heartScale)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (isExpired) {
                            "✨ You can check now"
                        } else if (isSetByMe) {
                            "❤️ Your Check-After is active"
                        } else {
                            "❤️ Check after ${CheckAfterHelper.formatTargetTime(targetMillis)}"
                        },
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = if (isExpired) Color(0xFF1B5E20) else DarkAubergine,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                if (!isSetByMe && !isExpired) {
                    // Quick Reminder Toggle Icon
                    IconButton(
                        onClick = onToggleReminder,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = if (isReminderEnabled) Icons.Filled.NotificationsActive else Icons.Outlined.NotificationsNone,
                            contentDescription = "Toggle reminder",
                            tint = if (isReminderEnabled) RoseGoldPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            if (!isExpired) {
                Text(
                    text = if (isSetByMe) {
                        "You're taking peaceful quiet time until ${CheckAfterHelper.formatTargetTime(targetMillis)}. $partnerName knows you'll be back soon 💕."
                    } else {
                        "$partnerName is taking some quiet time until ${CheckAfterHelper.formatTargetTime(targetMillis)} 🌙. Leave a sweet thought for when they return."
                    },
                    fontSize = 12.sp,
                    color = DarkAubergine.copy(alpha = 0.85f),
                    lineHeight = 16.sp
                )

                Spacer(modifier = Modifier.height(6.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = RoseGoldPrimary.copy(alpha = 0.12f)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.HourglassEmpty,
                                contentDescription = null,
                                tint = RoseGoldPrimary,
                                modifier = Modifier.size(13.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = remainingText,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = RoseGoldPrimary
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

                // If set by me, show quick controls: Extend, Change, Cancel
                if (isSetByMe) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        FilledTonalButton(
                            onClick = onExtend30m,
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            modifier = Modifier.height(30.dp),
                            colors = ButtonDefaults.filledTonalButtonColors(
                                containerColor = RoseGoldPrimary.copy(alpha = 0.15f),
                                contentColor = RoseGoldPrimary
                            )
                        ) {
                            Text("+30m", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }

                        FilledTonalButton(
                            onClick = onExtend1h,
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            modifier = Modifier.height(30.dp),
                            colors = ButtonDefaults.filledTonalButtonColors(
                                containerColor = RoseGoldPrimary.copy(alpha = 0.15f),
                                contentColor = RoseGoldPrimary
                            )
                        ) {
                            Text("+1h", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }

                        OutlinedButton(
                            onClick = onChangeTime,
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            modifier = Modifier.height(30.dp),
                            border = BorderStroke(1.dp, RoseGoldPrimary.copy(alpha = 0.4f))
                        ) {
                            Text("Change", fontSize = 11.sp, color = DarkAubergine)
                        }

                        TextButton(
                            onClick = onCancel,
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                            modifier = Modifier.height(30.dp)
                        ) {
                            Text("Cancel", fontSize = 11.sp, color = Color(0xFFD32F2F))
                        }
                    }
                }
            } else {
                Text(
                    text = "$partnerName's check-after period has completed! You can check in or send a warm message 💕",
                    fontSize = 12.sp,
                    color = Color(0xFF2E7D32)
                )
            }
        }
    }
}

/**
 * Beautiful Check-After Bottom Sheet allowing partners to pick presets, custom time, and notes
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
    onExtend: (Long) -> Unit
) {
    if (!isOpen) return

    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current

    val presetDurations = listOf(
        "1h" to (1 * 3600 * 1000L),
        "2h" to (2 * 3600 * 1000L),
        "3h" to (3 * 3600 * 1000L),
        "5h" to (5 * 3600 * 1000L),
        "8h" to (8 * 3600 * 1000L),
        "Tonight" to -1L,
        "Custom" to -2L
    )

    val quickNotes = listOf(
        "Focusing on work 💻",
        "Taking a nap 😴",
        "Study session 📚",
        "Family time 🏡",
        "Quiet me-time 🧘",
        "Driving / Travel 🚗"
    )

    var selectedPreset by remember { mutableStateOf("2h") }
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
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 22.dp, vertical = 12.dp)
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
                    imageVector = Icons.Filled.HourglassTop,
                    contentDescription = null,
                    tint = RoseGoldPrimary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Check After",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = DarkAubergine
                )
            }

            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Help your partner avoid repeatedly checking your status with a peaceful, shared countdown.",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(18.dp))

            // Currently Active Card (if already active)
            if (isCurrentlyActive && currentTargetMillis != null && currentTargetMillis > System.currentTimeMillis()) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Filled.Favorite,
                                contentDescription = null,
                                tint = RoseGoldPrimary,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Active Check-After",
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                color = DarkAubergine
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Target: ${CheckAfterHelper.formatTargetTime(currentTargetMillis)}",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = RoseGoldPrimary
                        )
                        val (remaining, _) = CheckAfterHelper.calculateRemaining(currentTargetMillis)
                        Text(
                            text = "⏳ $remaining",
                            fontSize = 12.sp,
                            color = DarkAubergine
                        )

                        Spacer(modifier = Modifier.height(10.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            FilledTonalButton(
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    onExtend(30 * 60 * 1000L)
                                    onDismiss()
                                },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.filledTonalButtonColors(
                                    containerColor = RoseGoldPrimary.copy(alpha = 0.12f),
                                    contentColor = RoseGoldPrimary
                                )
                            ) {
                                Text("+30 Mins", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }

                            FilledTonalButton(
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    onExtend(60 * 60 * 1000L)
                                    onDismiss()
                                },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.filledTonalButtonColors(
                                    containerColor = RoseGoldPrimary.copy(alpha = 0.12f),
                                    contentColor = RoseGoldPrimary
                                )
                            ) {
                                Text("+1 Hour", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }

                            OutlinedButton(
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    onCancelCheckAfter()
                                    onDismiss()
                                },
                                border = BorderStroke(1.dp, Color(0xFFD32F2F).copy(alpha = 0.4f))
                            ) {
                                Text("Cancel", fontSize = 12.sp, color = Color(0xFFD32F2F))
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, thickness = 1.dp)
                Spacer(modifier = Modifier.height(14.dp))
                Text(
                    text = "Or Set a New Time",
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = DarkAubergine,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
            }

            // Quick Preset Buttons: 1h | 2h | 3h | 5h | 8h | Tonight | Custom
            Text(
                text = "Quick Presets",
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(8.dp))

            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(presetDurations) { (label, duration) ->
                    val isSelected = selectedPreset == label
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = if (isSelected) RoseGoldPrimary else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                        border = if (isSelected) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                        modifier = Modifier
                            .clickable {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                selectedPreset = label
                                when (label) {
                                    "Tonight" -> {
                                        targetMillis = CheckAfterHelper.getTonightMillis()
                                    }
                                    "Custom" -> {
                                        // Open standard time picker dialog
                                        val cal = Calendar.getInstance()
                                        TimePickerDialog(
                                            context,
                                            { _, hourOfDay, minute ->
                                                val targetCal = Calendar.getInstance().apply {
                                                    set(Calendar.HOUR_OF_DAY, hourOfDay)
                                                    set(Calendar.MINUTE, minute)
                                                    set(Calendar.SECOND, 0)
                                                    set(Calendar.MILLISECOND, 0)
                                                    // If chosen time is already past today, roll to tomorrow
                                                    if (timeInMillis <= System.currentTimeMillis()) {
                                                        add(Calendar.DAY_OF_YEAR, 1)
                                                    }
                                                }
                                                targetMillis = targetCal.timeInMillis
                                            },
                                            cal.get(Calendar.HOUR_OF_DAY) + 1,
                                            cal.get(Calendar.MINUTE),
                                            false
                                        ).show()
                                    }
                                    else -> {
                                        targetMillis = System.currentTimeMillis() + duration
                                    }
                                }
                            }
                    ) {
                        Text(
                            text = label,
                            fontSize = 13.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            color = if (isSelected) Color.White else DarkAubergine,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Calculated Target Time Preview Box
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = Color(0xFFFBF4F6),
                border = BorderStroke(1.dp, RoseGoldPrimary.copy(alpha = 0.3f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.AccessTime,
                        contentDescription = null,
                        tint = RoseGoldPrimary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "Don't check until",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = CheckAfterHelper.formatTargetTime(targetMillis),
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = DarkAubergine
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Quick Activity / Mood Tags
            Text(
                text = "Reason / Mood (Optional)",
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
                items(quickNotes) { noteTag ->
                    val isTagSelected = selectedNote == noteTag
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (isTagSelected) RoseGoldPrimary.copy(alpha = 0.15f) else Color(0xFFF7F7F8),
                        border = BorderStroke(
                            1.dp,
                            if (isTagSelected) RoseGoldPrimary else Color(0xFFE5E5E8)
                        ),
                        modifier = Modifier.clickable {
                            selectedNote = if (isTagSelected) "" else noteTag
                        }
                    ) {
                        Text(
                            text = noteTag,
                            fontSize = 11.sp,
                            color = if (isTagSelected) RoseGoldPrimary else DarkAubergine,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Custom note input field
            OutlinedTextField(
                value = selectedNote,
                onValueChange = { selectedNote = it },
                placeholder = { Text("Or enter a custom sweet note...") },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Reminder Toggle Switch
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Outlined.Notifications,
                        contentDescription = null,
                        tint = RoseGoldPrimary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = "Notification Reminder",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = DarkAubergine
                        )
                        Text(
                            text = "Get a discreet reminder when time arrives",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Switch(
                    checked = isReminderEnabled,
                    onCheckedChange = { onToggleReminder(it) },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = RoseGoldPrimary
                    )
                )
            }

            Spacer(modifier = Modifier.height(18.dp))

            // Save / Set Button
            Button(
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onSaveCheckAfter(targetMillis, selectedNote)
                    onDismiss()
                },
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = RoseGoldPrimary),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .testTag("set_check_after_button")
            ) {
                Icon(Icons.Filled.Favorite, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Set Check-After",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.surface
                )
            }

            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}





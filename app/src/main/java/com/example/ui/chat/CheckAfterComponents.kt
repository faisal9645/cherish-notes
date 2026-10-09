package com.example.ui.chat

import com.example.ui.theme.darkTone

import android.app.TimePickerDialog
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
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
            colors = listOf(Color(0xFF2E7D32).copy(alpha = 0.15f), Color(0xFF81C784).copy(alpha = 0.15f))
        )
    } else {
        if (isDark) {
            Brush.horizontalGradient(
                colors = listOf(Color(0xFF0B132B), Color(0xFF1C2541))
            )
        } else {
            Brush.horizontalGradient(
                colors = listOf(Color(0xFFF1F5FB), Color(0xFFE2E8F0))
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
                        imageVector = if (isExpired) Icons.Filled.CheckCircle else Icons.Filled.Favorite,
                        contentDescription = null,
                        tint = if (isExpired) Color(0xFF2E7D32) else primaryAccent,
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
                            imageVector = if (isReminderEnabled) Icons.Filled.NotificationsActive else Icons.Outlined.NotificationsNone,
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
                        shape = RoundedCornerShape(10.dp),
                        color = primaryAccent.copy(alpha = 0.12f)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.HourglassEmpty,
                                contentDescription = null,
                                tint = primaryAccent,
                                modifier = Modifier.size(12.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = remainingText,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = primaryAccent
                            )
                        }
                    }

                    if (note.isNotBlank()) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                        ) {
                            Text(
                                text = note,
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
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
                        FilledTonalButton(
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

                        FilledTonalButton(
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

                        OutlinedButton(
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
                            Text("Cancel", fontSize = 11.sp, color = Color(0xFFD32F2F))
                        }
                    }
                }
            } else {
                Text(
                    text = "$partnerName's check-after period is done. Feel free to check in!",
                    fontSize = 12.sp,
                    color = Color(0xFF2E7D32)
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
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        dragHandle = { BottomSheetDefaults.DragHandle(height = 4.dp, width = 36.dp) }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
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
                    imageVector = Icons.Filled.HourglassTop,
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
                    shape = RoundedCornerShape(14.dp),
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
                                    imageVector = Icons.Filled.Favorite,
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
                            FilledTonalButton(
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

                            FilledTonalButton(
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

                            FilledTonalButton(
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

                            OutlinedButton(
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    onCancelCheckAfter()
                                    onDismiss()
                                },
                                modifier = Modifier.height(32.dp),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                border = BorderStroke(1.dp, Color(0xFFD32F2F).copy(alpha = 0.4f))
                            ) {
                                Text("Cancel", fontSize = 11.sp, color = Color(0xFFD32F2F))
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
                        shape = RoundedCornerShape(10.dp),
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
                                        val cal = Calendar.getInstance()
                                        TimePickerDialog(
                                            context,
                                            { _, hourOfDay, minute ->
                                                val targetCal = Calendar.getInstance().apply {
                                                    set(Calendar.HOUR_OF_DAY, hourOfDay)
                                                    set(Calendar.MINUTE, minute)
                                                    set(Calendar.SECOND, 0)
                                                    set(Calendar.MILLISECOND, 0)
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
                            text = if (label == "Days") (if (isSelected) "$customDays days" else "Days…") else label,
                            fontSize = 12.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(horizontal = 11.dp, vertical = 6.dp)
                        )
                    }
                }
            }

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
                        imageVector = Icons.Default.AccessTime,
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
                        shape = RoundedCornerShape(10.dp),
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
                            modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp)
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
                        imageVector = Icons.Outlined.Notifications,
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
                Switch(
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
                        fontSize = 11.5.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                        lineHeight = 15.sp,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
            }

            // Save / Set Button (compact 42dp height)
            Button(
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    if (needsOk) onRequestSpace?.invoke(targetMillis, selectedNote)
                    else onSaveCheckAfter(targetMillis, selectedNote)
                    onDismiss()
                },
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = primaryAccent),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(42.dp)
                    .testTag("set_check_after_button")
            ) {
                Icon(Icons.Filled.Favorite, contentDescription = null, modifier = Modifier.size(16.dp))
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

    if (showDaysPicker) {
        val until = System.currentTimeMillis() + customDays * com.example.data.model.SpaceRequest.DAY_MS
        AlertDialog(
            onDismissRequest = { showDaysPicker = false },
            title = { Text("Personal space") },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { customDays = (customDays - 1).coerceAtLeast(1) }, enabled = customDays > 1) {
                            Icon(Icons.Filled.Remove, contentDescription = "Fewer days")
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
                            Icon(Icons.Filled.Add, contentDescription = "More days")
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
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Filled.Favorite,
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
                Surface(shape = RoundedCornerShape(10.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)) {
                    Text(
                        text = request.note,
                        fontSize = 11.5.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                when {
                    request.isPending && !fromMe -> {
                        Button(
                            onClick = onAccept,
                            colors = ButtonDefaults.buttonColors(containerColor = primaryAccent),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                            modifier = Modifier
                                .height(34.dp)
                                .testTag("space_request_accept")
                        ) { Text("Accept", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White) }
                        OutlinedButton(
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

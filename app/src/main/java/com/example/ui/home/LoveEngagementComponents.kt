package com.example.ui.home

import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.BucketListItem
import com.example.data.model.DailyQuestion
import com.example.data.model.LoveJarNote
import com.example.ui.theme.*
import kotlin.random.Random

/**
 * Instant Tactile Love Nudges Bar
 */
@Composable
fun LoveNudgesBar(
    onSendNudge: (name: String, emoji: String, message: String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var activeBurstEmoji by remember { mutableStateOf<String?>(null) }

    fun triggerVibration(pattern: LongArray) {
        try {
            val vibrator = context.getSystemService(Vibrator::class.java)
            if (vibrator != null && vibrator.hasVibrator()) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator.vibrate(VibrationEffect.createWaveform(pattern, -1))
                } else {
                    @Suppress("DEPRECATION")
                    vibrator.vibrate(pattern[1])
                }
            }
        } catch (_: Exception) {}
    }

    LaunchedEffect(activeBurstEmoji) {
        if (activeBurstEmoji != null) {
            kotlinx.coroutines.delay(1200)
            activeBurstEmoji = null
        }
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Favorite,
                        contentDescription = null,
                        tint = HeartRed,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Instant Love Nudges",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                Text(
                    text = "Tap to feel & send",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceAround
            ) {
                // Kiss
                NudgeButton(
                    emoji = "💋",
                    label = "Kiss",
                    onClick = {
                        triggerVibration(longArrayOf(0, 80, 50, 80))
                        activeBurstEmoji = "💋"
                        onSendNudge("Kiss", "💋", "Sending you a sweet, warm kiss right now! 💋")
                    }
                )

                // Heartbeat
                NudgeButton(
                    emoji = "💓",
                    label = "Heartbeat",
                    onClick = {
                        triggerVibration(longArrayOf(0, 100, 100, 150, 80, 200))
                        activeBurstEmoji = "💓"
                        onSendNudge("Heartbeat", "💓", "Feeling your heartbeat pulse in mine right now 💓")
                    }
                )

                // Warm Hug
                NudgeButton(
                    emoji = "🤗",
                    label = "Warm Hug",
                    onClick = {
                        triggerVibration(longArrayOf(0, 200, 100, 250))
                        activeBurstEmoji = "🤗"
                        onSendNudge("Hug", "🤗", "Wrapping you in the biggest, warmest embrace 🤗")
                    }
                )

                // Thinking of You
                NudgeButton(
                    emoji = "💭",
                    label = "Miss You",
                    onClick = {
                        triggerVibration(longArrayOf(0, 120))
                        activeBurstEmoji = "💭"
                        onSendNudge("Thinking of You", "💭", "Just paused my day to think about you... I love you! 💭")
                    }
                )
            }

            AnimatedVisibility(visible = activeBurstEmoji != null) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Sent with all my love $activeBurstEmoji",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = RoseGoldPrimary
                    )
                }
            }
        }
    }
}

@Composable
private fun NudgeButton(
    emoji: String,
    label: String,
    onClick: () -> Unit
) {
    var isPressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.85f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "nudge_scale"
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .scale(scale)
            .clickable {
                isPressed = true
                onClick()
            }
            .padding(4.dp)
    ) {
        LaunchedEffect(isPressed) {
            if (isPressed) {
                kotlinx.coroutines.delay(180)
                isPressed = false
            }
        }

        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(text = emoji, fontSize = 23.sp)
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = label,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * Daily Us — Question of the Day for Deep Connection
 */
@Composable
fun DailyQuestionCard(
    dailyQuestion: DailyQuestion,
    partnerName: String,
    onSubmitAnswer: (String) -> Unit,
    onToggleLike: () -> Unit,
    modifier: Modifier = Modifier
) {
    var myAnswerInput by remember { mutableStateOf("") }
    var isExpanded by remember { mutableStateOf(false) }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
        ) {
            // Header Row: Badge & Streak
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = RoseGoldContainer
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Daily Us • ${dailyQuestion.category}",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = OnRoseGoldContainer
                        )
                    }
                }

                val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = if (isDark) darkTone(Color(0xFF2E1C0C)) else Color(0xFFFFF3E0)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(text = "🔥", fontSize = 11.sp)
                        Spacer(modifier = Modifier.width(3.dp))
                        Text(
                            text = "${dailyQuestion.streakDays} Day Streak",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isDark) darkTone(Color(0xFFFDBA74)) else Color(0xFFE65100)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // The Question
            Text(
                text = dailyQuestion.question,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
                lineHeight = 23.sp
            )

            Spacer(modifier = Modifier.height(14.dp))

            if (!dailyQuestion.isMyAnswerSubmitted) {
                // Input mode: User hasn't answered yet
                OutlinedTextField(
                    value = myAnswerInput,
                    onValueChange = { myAnswerInput = it },
                    placeholder = {
                        Text(
                            "Type your heartfelt answer...",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    minLines = 2,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = RoseGoldPrimary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline
                    )
                )

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (dailyQuestion.isPartnerAnswerSubmitted) "🔒 $partnerName has answered: answer to see it"
                        else "🔒 Answer to reveal $partnerName's answer",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Button(
                        onClick = {
                            if (myAnswerInput.isNotBlank()) {
                                onSubmitAnswer(myAnswerInput)
                                myAnswerInput = ""
                            }
                        },
                        enabled = myAnswerInput.isNotBlank(),
                        colors = ButtonDefaults.buttonColors(containerColor = RoseGoldPrimary),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Lock In Answer 💌", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            } else {
                // Both Answered / Reveal Mode!
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(14.dp)
                ) {
                    // My Answer
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(text = "You", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = RoseGoldPrimary)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(text = "• Today", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = dailyQuestion.myAnswer.orEmpty(),
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onBackground,
                        lineHeight = 20.sp
                    )
                    if (dailyQuestion.partnerLovedMyAnswer) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "❤️ $partnerName loved your answer",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = HeartRed
                        )
                    }

                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outline,
                        thickness = 0.5.dp,
                        modifier = Modifier.padding(vertical = 10.dp)
                    )

                    // Partner's Answer
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(text = partnerName, fontWeight = FontWeight.Bold, fontSize = 12.sp, color = MaterialTheme.colorScheme.onBackground)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(text = "• Today", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }

                        if (dailyQuestion.isPartnerAnswerSubmitted) IconButton(
                            onClick = onToggleLike,
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                imageVector = if (dailyQuestion.isLikedByPartner) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                                contentDescription = "Like answer",
                                tint = if (dailyQuestion.isLikedByPartner) HeartRed else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = dailyQuestion.partnerAnswer ?: "Waiting for $partnerName's answer…",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onBackground,
                        lineHeight = 20.sp
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = if (dailyQuestion.isPartnerAnswerSubmitted) "Both answered today! Connection renewed 💕"
                        else "Your answer is saved. $partnerName sees it once they answer too",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = RoseGoldPrimary
                    )
                }
            }
        }
    }
}

/**
 * "Love Jar" Card (Reasons Why I Love You)
 */
@Composable
fun LoveJarCard(
    notesCount: Int,
    onOpenJar: () -> Unit,
    onAddNote: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                Box(
                    modifier = Modifier
                        .size(50.dp)
                        .clip(CircleShape)
                        .background(RoseGoldContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = "🏺", fontSize = 26.sp)
                }
                Spacer(modifier = Modifier.width(14.dp))
                Column {
                    Text(
                        text = "Love Jar",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = "$notesCount sweet notes & reasons inside",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Row {
                FilledTonalButton(
                    onClick = onOpenJar,
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text("Draw Note 📜", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

/**
 * Shared Couple Bucket List Card
 */
@Composable
fun BucketListCard(
    items: List<BucketListItem>,
    onToggleItem: (String) -> Unit,
    onAddNew: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Stars,
                        contentDescription = null,
                        tint = RoseGoldPrimary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Our Couple Bucket List",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                }

                IconButton(onClick = onAddNew, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.Add, contentDescription = "Add bucket dream", tint = RoseGoldPrimary)
                }
            }

            Text(
                text = "Things we dream of doing together ✈️",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(14.dp))

            items.take(4).forEach { item ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { onToggleItem(item.id) }
                        .padding(vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = item.isCompleted,
                        onCheckedChange = { onToggleItem(item.id) },
                        colors = CheckboxDefaults.colors(checkedColor = RoseGoldPrimary)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = item.title,
                            fontSize = 14.sp,
                            color = if (item.isCompleted) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onBackground,
                            textDecoration = if (item.isCompleted) TextDecoration.LineThrough else null,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (item.isCompleted && item.completedDate != null) {
                            Text(
                                text = "Accomplished together on ${item.completedDate} 🎉",
                                fontSize = 10.sp,
                                color = RoseGoldPrimary,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Draw Note Dialog
 */
@Composable
fun DrawLoveNoteDialog(
    note: LoveJarNote?,
    onDismiss: () -> Unit,
    onDrawAnother: () -> Unit,
    onAddNote: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(text = "From Our Love Jar 🏺", fontWeight = FontWeight.Bold, fontSize = 18.sp)
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(text = note?.emoji ?: "💖", fontSize = 36.sp)
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "\"${note?.text ?: "I love every little moment we share together."}\"",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onBackground,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    lineHeight = 22.sp
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "— ${note?.author ?: "My Love"}",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = RoseGoldPrimary
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onDrawAnother,
                colors = ButtonDefaults.buttonColors(containerColor = RoseGoldPrimary)
            ) {
                Text("Draw Another 📜")
            }
        },
        dismissButton = {
            TextButton(onClick = onAddNote) {
                Text("+ Drop New Note")
            }
        }
    )
}

/**
 * Add Love Note Dialog
 */
@Composable
fun AddLoveNoteDialog(
    onDismiss: () -> Unit,
    onAdd: (text: String, emoji: String) -> Unit
) {
    var text by remember { mutableStateOf("") }
    var selectedEmoji by remember { mutableStateOf("💖") }
    val emojis = listOf("💖", "🌸", "✨", "💌", "🌟", "🥺", "☕")

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("Drop a Note in the Love Jar 🏺", fontWeight = FontWeight.Bold, fontSize = 17.sp)
        },
        text = {
            Column {
                Text(
                    text = "Write a sweet compliment, reason why you love them, or a cherished memory:",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(10.dp))
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    placeholder = { Text("Because you always make me laugh...") },
                    minLines = 3,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(10.dp))
                Text(text = "Choose an emoji:", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                Spacer(modifier = Modifier.height(6.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(emojis) { emoji ->
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(if (selectedEmoji == emoji) RoseGoldContainer else MaterialTheme.colorScheme.surfaceVariant)
                                .clickable { selectedEmoji = emoji },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(text = emoji, fontSize = 18.sp)
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (text.isNotBlank()) {
                        onAdd(text, selectedEmoji)
                        onDismiss()
                    }
                },
                enabled = text.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = RoseGoldPrimary)
            ) {
                Text("Drop in Jar 💌")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

/**
 * Add Bucket Item Dialog
 */
@Composable
fun AddBucketItemDialog(
    onDismiss: () -> Unit,
    onAdd: (title: String, category: String) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("Romantic Dates") }
    val categories = listOf("Romantic Dates", "Travel", "Experiences", "Cozy Fun")

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("Add Couple Dream ✨", fontWeight = FontWeight.Bold, fontSize = 18.sp)
        },
        text = {
            Column {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    placeholder = { Text("e.g., Roadtrip to the coast...") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text("Category:", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                Spacer(modifier = Modifier.height(6.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(categories) { cat ->
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = if (category == cat) RoseGoldPrimary else MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier.clickable { category = cat }
                        ) {
                            Text(
                                text = cat,
                                color = if (category == cat) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 11.sp,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (title.isNotBlank()) {
                        onAdd(title, category)
                        onDismiss()
                    }
                },
                enabled = title.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = RoseGoldPrimary)
            ) {
                Text("Add to List")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

/**
 * Our Shared Movies (Watched List and Suggested Movies)
 */
@Composable
fun CoupleMoviesCard(
    movies: List<com.example.data.model.MovieItem>,
    onToggleWatched: (String, Int) -> Unit,
    onDeleteMovie: (String) -> Unit,
    onAddNew: () -> Unit,
    onPickTonight: () -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedTab by remember { mutableIntStateOf(0) } // 0: Watched, 1: Suggestions
    val watchedList = remember(movies) { movies.filter { it.isWatched } }
    val suggestionsList = remember(movies) { movies.filter { !it.isWatched } }
    val displayList = if (selectedTab == 0) watchedList else suggestionsList

    Card(
        modifier = modifier.fillMaxWidth().testTag("couple_movies_card"),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(RoseGoldContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("🍿", fontSize = 20.sp)
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "Our Movies",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Text(
                            text = "${watchedList.size} watched • ${suggestionsList.size} suggestions",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = onPickTonight,
                        modifier = Modifier.size(36.dp).testTag("pick_tonight_movie_button")
                    ) {
                        Text("🎲", fontSize = 18.sp)
                    }
                    IconButton(
                        onClick = onAddNew,
                        modifier = Modifier.size(36.dp).testTag("add_movie_button")
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "Add movie", tint = RoseGoldPrimary)
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Tabs for Watched vs Suggested
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                    .padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = if (selectedTab == 0) RoseGoldPrimary else Color.Transparent,
                    modifier = Modifier
                        .weight(1f)
                        .clickable { selectedTab = 0 }
                        .padding(vertical = 2.dp),
                    shadowElevation = if (selectedTab == 0) 2.dp else 0.dp
                ) {
                    Text(
                        text = "Watched (${watchedList.size}) 🎬",
                        color = if (selectedTab == 0) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.5.sp,
                        fontWeight = if (selectedTab == 0) FontWeight.Bold else FontWeight.Medium,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        modifier = Modifier.padding(vertical = 6.dp)
                    )
                }

                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = if (selectedTab == 1) RoseGoldPrimary else Color.Transparent,
                    modifier = Modifier
                        .weight(1f)
                        .clickable { selectedTab = 1 }
                        .padding(vertical = 2.dp),
                    shadowElevation = if (selectedTab == 1) 2.dp else 0.dp
                ) {
                    Text(
                        text = "Suggestions (${suggestionsList.size}) ✨",
                        color = if (selectedTab == 1) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.5.sp,
                        fontWeight = if (selectedTab == 1) FontWeight.Bold else FontWeight.Medium,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        modifier = Modifier.padding(vertical = 6.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            if (displayList.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 18.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (selectedTab == 0) "No movies watched yet! Pick a suggestion above 🍿" else "No movie suggestions yet! Add one for your next date night ✨",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    displayList.take(5).forEach { movie ->
                        MovieRowItem(
                            movie = movie,
                            onToggleWatched = { onToggleWatched(movie.id, 5) },
                            onDelete = { onDeleteMovie(movie.id) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MovieRowItem(
    movie: com.example.data.model.MovieItem,
    onToggleWatched: () -> Unit,
    onDelete: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        border = BorderStroke(0.6.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(RoseGoldContainer.copy(alpha = 0.8f)),
                contentAlignment = Alignment.Center
            ) {
                Text(text = movie.emoji, fontSize = 22.sp)
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = movie.title,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.5.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (movie.isWatched) {
                        val first = movie.watchedFirstBy
                        val second = movie.watchedSecondBy
                        val watchBadge = when {
                            first != null && second != null -> "🥇 1st: $first • 🥈 2nd: $second"
                            first != null -> "🥇 1st: $first"
                            else -> "Watched ✓"
                        }
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = HeartbeatPink.copy(alpha = 0.15f)
                        ) {
                            Text(
                                text = watchBadge,
                                color = HeartbeatPink,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(2.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = movie.genre,
                        fontSize = 11.5.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (movie.isWatched && movie.watchedDate != null) {
                        Text(
                            text = "• ${movie.watchedDate}",
                            fontSize = 11.sp,
                            color = RoseGoldPrimary,
                            fontWeight = FontWeight.Medium
                        )
                    } else if (!movie.isWatched && movie.suggestedBy.isNotBlank()) {
                        Text(
                            text = "• Suggested by ${movie.suggestedBy}",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                if (movie.notes.isNotBlank()) {
                    Spacer(modifier = Modifier.height(3.dp))
                    Text(
                        text = "“${movie.notes}”",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(modifier = Modifier.width(6.dp))

            FilledTonalIconButton(
                onClick = onToggleWatched,
                modifier = Modifier.size(34.dp),
                colors = IconButtonDefaults.filledTonalIconButtonColors(
                    containerColor = if (movie.isWatched) RoseGoldPrimary.copy(alpha = 0.15f) else RoseGoldContainer
                )
            ) {
                if (movie.isWatched) {
                    Icon(Icons.Filled.Favorite, contentDescription = "Watched", tint = HeartRed, modifier = Modifier.size(16.dp))
                } else {
                    Icon(Icons.Filled.Check, contentDescription = "Mark as Watched", tint = RoseGoldPrimary, modifier = Modifier.size(16.dp))
                }
            }

            IconButton(
                onClick = onDelete,
                modifier = Modifier.size(28.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Delete movie",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
                    modifier = Modifier.size(14.dp)
                )
            }
        }
    }
}

@Composable
fun AddMovieDialog(
    onDismiss: () -> Unit,
    onAdd: (title: String, genre: String, emoji: String, isWatched: Boolean, notes: String) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var genre by remember { mutableStateOf("Romance") }
    var selectedEmoji by remember { mutableStateOf("🍿") }
    var isWatched by remember { mutableStateOf(false) }
    var notes by remember { mutableStateOf("") }

    val genres = listOf("Romance", "RomCom", "Cozy Anime", "Musical", "Drama", "Mystery", "Adventure")
    val emojis = listOf("🍿", "🎬", "🌆", "⏳", "💌", "✨", "🚂", "💍", "🛋️", "☕")

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("Add Movie to Watchlist 🍿", fontWeight = FontWeight.Bold, fontSize = 18.sp)
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Movie Title") },
                    placeholder = { Text("e.g. About Time, The Holiday...") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(10.dp))

                Text("Genre:", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                Spacer(modifier = Modifier.height(6.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(genres) { g ->
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = if (genre == g) RoseGoldPrimary else MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier.clickable { genre = g }
                        ) {
                            Text(
                                text = g,
                                color = if (genre == g) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 11.5.sp,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))
                Text("Movie Emoji:", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                Spacer(modifier = Modifier.height(6.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(emojis) { emoji ->
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(if (selectedEmoji == emoji) RoseGoldContainer else MaterialTheme.colorScheme.surfaceVariant)
                                .clickable { selectedEmoji = emoji },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(text = emoji, fontSize = 18.sp)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().clickable { isWatched = !isWatched }
                ) {
                    Checkbox(
                        checked = isWatched,
                        onCheckedChange = { isWatched = it },
                        colors = CheckboxDefaults.colors(checkedColor = RoseGoldPrimary)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("We already watched this together 🎬", fontSize = 13.sp)
                }

                Spacer(modifier = Modifier.height(6.dp))
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Couple Note / Why watch? (optional)") },
                    placeholder = { Text("e.g. Recommended for rainy Friday night") },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 2
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (title.isNotBlank()) {
                        onAdd(title, genre, selectedEmoji, isWatched, notes)
                        onDismiss()
                    }
                },
                enabled = title.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = RoseGoldPrimary)
            ) {
                Text("Save Movie 🎬")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun RandomMoviePickerDialog(
    movies: List<com.example.data.model.MovieItem>,
    onDismiss: () -> Unit,
    onMarkWatched: (String) -> Unit
) {
    val suggestions = remember(movies) { movies.filter { !it.isWatched } }
    var pickedMovie by remember { mutableStateOf(if (suggestions.isNotEmpty()) suggestions.random() else movies.randomOrNull()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Tonight's Movie Date! 🍿🎬", fontWeight = FontWeight.Bold, fontSize = 18.sp)
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (pickedMovie != null) {
                    Text(text = pickedMovie!!.emoji, fontSize = 42.sp)
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = pickedMovie!!.title,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = pickedMovie!!.genre,
                        fontSize = 13.sp,
                        color = RoseGoldPrimary,
                        fontWeight = FontWeight.SemiBold
                    )
                    if (pickedMovie!!.notes.isNotBlank()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "“${pickedMovie!!.notes}”",
                            fontSize = 12.5.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Suggested by ${pickedMovie!!.suggestedBy} ✨",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Text(
                        text = "Add some movies to your watchlist first! 🎬",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        confirmButton = {
            if (pickedMovie != null) {
                Button(
                    onClick = {
                        pickedMovie = if (suggestions.size > 1) {
                            suggestions.filter { it.id != pickedMovie!!.id }.random()
                        } else suggestions.randomOrNull() ?: movies.randomOrNull()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = RoseGoldPrimary)
                ) {
                    Text("Spin Again 🎲")
                }
            }
        },
        dismissButton = {
            if (pickedMovie != null && !pickedMovie!!.isWatched) {
                TextButton(
                    onClick = {
                        onMarkWatched(pickedMovie!!.id)
                        onDismiss()
                    }
                ) {
                    Text("Watching Now! 🍿")
                }
            } else {
                TextButton(onClick = onDismiss) {
                    Text("Close")
                }
            }
        }
    )
}

/**
 * Our Books: Book suggestions, count, and page count tracking for both users
 */
@Composable
fun CoupleBooksCard(
    books: List<com.example.data.model.BookItem>,
    myName: String,
    partnerName: String,
    onUpdateProgress: (id: String, myPage: Int?, partnerPage: Int?) -> Unit,
    onDeleteBook: (String) -> Unit,
    onAddNew: () -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedTab by remember { mutableIntStateOf(0) } // 0: Reading & Read, 1: Suggestions
    var editingBookProgress by remember { mutableStateOf<com.example.data.model.BookItem?>(null) }

    val readingAndFinished = remember(books) {
        books.filter { it.myCurrentPage > 0 || it.partnerCurrentPage > 0 || it.isCompletedByMe || it.isCompletedByPartner }
    }
    val suggestionsList = remember(books) {
        books.filter { it !in readingAndFinished }
    }
    val displayList = if (selectedTab == 0) (if (readingAndFinished.isNotEmpty()) readingAndFinished else books) else suggestionsList

    Card(
        modifier = modifier.fillMaxWidth().testTag("couple_books_card"),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(DayBluePrimary.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("📚", fontSize = 20.sp)
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "Our Books",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Text(
                            text = "${books.size} books • ${readingAndFinished.size} active reading",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = onAddNew,
                        modifier = Modifier.size(36.dp).testTag("add_book_button")
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "Add book", tint = DayBluePrimary)
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Tabs for Reading vs Suggestions
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                    .padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = if (selectedTab == 0) DayBluePrimary else Color.Transparent,
                    modifier = Modifier
                        .weight(1f)
                        .clickable { selectedTab = 0 }
                        .padding(vertical = 2.dp),
                    shadowElevation = if (selectedTab == 0) 2.dp else 0.dp
                ) {
                    Text(
                        text = "Reading & Read (${readingAndFinished.size}) 📖",
                        color = if (selectedTab == 0) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.5.sp,
                        fontWeight = if (selectedTab == 0) FontWeight.Bold else FontWeight.Medium,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        modifier = Modifier.padding(vertical = 6.dp)
                    )
                }

                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = if (selectedTab == 1) DayBluePrimary else Color.Transparent,
                    modifier = Modifier
                        .weight(1f)
                        .clickable { selectedTab = 1 }
                        .padding(vertical = 2.dp),
                    shadowElevation = if (selectedTab == 1) 2.dp else 0.dp
                ) {
                    Text(
                        text = "Suggestions (${suggestionsList.size}) ✨",
                        color = if (selectedTab == 1) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.5.sp,
                        fontWeight = if (selectedTab == 1) FontWeight.Bold else FontWeight.Medium,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        modifier = Modifier.padding(vertical = 6.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            if (displayList.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 18.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (selectedTab == 0) "No books reading yet! Pick a suggestion below 📚" else "No book suggestions yet! Add one for each other ✨",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    displayList.take(6).forEach { book ->
                        BookRowItem(
                            book = book,
                            myName = myName,
                            partnerName = partnerName,
                            onEditProgress = { editingBookProgress = book },
                            onDelete = { onDeleteBook(book.id) }
                        )
                    }
                }
            }
        }
    }

    if (editingBookProgress != null) {
        UpdateBookProgressDialog(
            book = editingBookProgress!!,
            myName = myName,
            partnerName = partnerName,
            onDismiss = { editingBookProgress = null },
            onSave = { myPage, partnerPage ->
                onUpdateProgress(editingBookProgress!!.id, myPage, partnerPage)
                editingBookProgress = null
            }
        )
    }
}

@Composable
private fun BookRowItem(
    book: com.example.data.model.BookItem,
    myName: String,
    partnerName: String,
    onEditProgress: () -> Unit,
    onDelete: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        border = BorderStroke(0.6.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(DayBluePrimary.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = book.emoji, fontSize = 22.sp)
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = book.title,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.5.sp,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Spacer(modifier = Modifier.height(2.dp))

                    Text(
                        text = "by ${book.author} • ${book.genre}",
                        fontSize = 11.5.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.width(6.dp))

                FilledTonalButton(
                    onClick = onEditProgress,
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = DayBluePrimary.copy(alpha = 0.15f),
                        contentColor = DayBluePrimary
                    )
                ) {
                    Text("Pages", fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold)
                }

                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Delete book",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
                        modifier = Modifier.size(14.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Dual User Page Tracking
            val myPct = if (book.totalPages > 0) (book.myCurrentPage * 100 / book.totalPages).coerceIn(0, 100) else 0
            val partnerPct = if (book.totalPages > 0) (book.partnerCurrentPage * 100 / book.totalPages).coerceIn(0, 100) else 0

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.6f))
                    .padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // My progress bar
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "$myName: p. ${book.myCurrentPage} of ${book.totalPages}",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = RoseGoldPrimary
                        )
                        Text(
                            text = if (book.isCompletedByMe) "Finished ✓" else "$myPct%",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (book.isCompletedByMe) OnlineGreen else RoseGoldPrimary
                        )
                    }
                    Spacer(modifier = Modifier.height(3.dp))
                    LinearProgressIndicator(
                        progress = { (book.myCurrentPage.toFloat() / book.totalPages.coerceAtLeast(1)).coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth().height(5.dp).clip(RoundedCornerShape(3.dp)),
                        color = RoseGoldPrimary,
                        trackColor = RoseGoldPrimary.copy(alpha = 0.2f)
                    )
                }

                // Partner progress bar
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "$partnerName: p. ${book.partnerCurrentPage} of ${book.totalPages}",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = DayBluePrimary
                        )
                        Text(
                            text = if (book.isCompletedByPartner) "Finished ✓" else "$partnerPct%",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (book.isCompletedByPartner) OnlineGreen else DayBluePrimary
                        )
                    }
                    Spacer(modifier = Modifier.height(3.dp))
                    LinearProgressIndicator(
                        progress = { (book.partnerCurrentPage.toFloat() / book.totalPages.coerceAtLeast(1)).coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth().height(5.dp).clip(RoundedCornerShape(3.dp)),
                        color = DayBluePrimary,
                        trackColor = DayBluePrimary.copy(alpha = 0.2f)
                    )
                }

                // Who finished 1st / 2nd badges
                if (book.firstFinishedBy != null) {
                    val finishBadge = if (book.secondFinishedBy != null) {
                        "🥇 1st: ${book.firstFinishedBy} • 🥈 2nd: ${book.secondFinishedBy}"
                    } else {
                        "🥇 Finished 1st by ${book.firstFinishedBy}"
                    }
                    Text(
                        text = finishBadge,
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = HeartbeatPink
                    )
                }
            }

            if (book.notes.isNotBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "“${book.notes}”",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
fun UpdateBookProgressDialog(
    book: com.example.data.model.BookItem,
    myName: String,
    partnerName: String,
    onDismiss: () -> Unit,
    onSave: (myPage: Int, partnerPage: Int) -> Unit
) {
    var myPage by remember { mutableIntStateOf(book.myCurrentPage) }
    var partnerPage by remember { mutableIntStateOf(book.partnerCurrentPage) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("Track Reading Progress 📖", fontWeight = FontWeight.Bold, fontSize = 18.sp)
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    text = "${book.title} (${book.totalPages} pages)",
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // My page control
                Column {
                    Text(text = "$myName's Current Page: $myPage", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = RoseGoldPrimary)
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedButton(
                            onClick = { myPage = (myPage - 10).coerceAtLeast(0) },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                        ) { Text("-10") }

                        OutlinedButton(
                            onClick = { myPage = (myPage - 1).coerceAtLeast(0) },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                        ) { Text("-1") }

                        OutlinedButton(
                            onClick = { myPage = (myPage + 1).coerceAtMost(book.totalPages) },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                        ) { Text("+1") }

                        Button(
                            onClick = { myPage = (myPage + 10).coerceAtMost(book.totalPages) },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = RoseGoldPrimary)
                        ) { Text("+10") }

                        OutlinedButton(
                            onClick = { myPage = book.totalPages },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                        ) { Text("Finish ✓") }
                    }
                }

                // Partner page control
                Column {
                    Text(text = "$partnerName's Current Page: $partnerPage", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = DayBluePrimary)
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedButton(
                            onClick = { partnerPage = (partnerPage - 10).coerceAtLeast(0) },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                        ) { Text("-10") }

                        OutlinedButton(
                            onClick = { partnerPage = (partnerPage - 1).coerceAtLeast(0) },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                        ) { Text("-1") }

                        OutlinedButton(
                            onClick = { partnerPage = (partnerPage + 1).coerceAtMost(book.totalPages) },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                        ) { Text("+1") }

                        Button(
                            onClick = { partnerPage = (partnerPage + 10).coerceAtMost(book.totalPages) },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = DayBluePrimary)
                        ) { Text("+10") }

                        OutlinedButton(
                            onClick = { partnerPage = book.totalPages },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                        ) { Text("Finish ✓") }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(myPage, partnerPage) },
                colors = ButtonDefaults.buttonColors(containerColor = DayBluePrimary)
            ) {
                Text("Save Progress")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun AddBookDialog(
    onDismiss: () -> Unit,
    onAdd: (title: String, author: String, totalPages: Int, genre: String, emoji: String, notes: String) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var author by remember { mutableStateOf("") }
    var totalPagesText by remember { mutableStateOf("250") }
    var genre by remember { mutableStateOf("Fiction") }
    var selectedEmoji by remember { mutableStateOf("📖") }
    var notes by remember { mutableStateOf("") }

    val genres = listOf("Fiction", "Romance", "Classic", "Self-Growth", "Mystery", "Poetry", "Fantasy")
    val emojis = listOf("📖", "🌹", "☕", "⚡", "✨", "💌", "🌿", "🛋️", "💫")

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("Add Book Suggestion 📚", fontWeight = FontWeight.Bold, fontSize = 18.sp)
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Book Title") },
                    placeholder = { Text("e.g. The Little Prince, Normal People...") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = author,
                    onValueChange = { author = it },
                    label = { Text("Author") },
                    placeholder = { Text("e.g. Sally Rooney") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = totalPagesText,
                    onValueChange = { totalPagesText = it.filter { ch -> ch.isDigit() } },
                    label = { Text("Total Pages") },
                    placeholder = { Text("e.g. 280") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(10.dp))

                Text("Genre:", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                Spacer(modifier = Modifier.height(6.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(genres) { g ->
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = if (genre == g) DayBluePrimary else MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier.clickable { genre = g }
                        ) {
                            Text(
                                text = g,
                                color = if (genre == g) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 11.5.sp,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))
                Text("Book Emoji:", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                Spacer(modifier = Modifier.height(6.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(emojis) { em ->
                        Surface(
                            shape = CircleShape,
                            color = if (selectedEmoji == em) DayBluePrimary.copy(alpha = 0.2f) else Color.Transparent,
                            border = if (selectedEmoji == em) BorderStroke(1.5.dp, DayBluePrimary) else null,
                            modifier = Modifier.clickable { selectedEmoji = em }
                        ) {
                            Text(text = em, fontSize = 24.sp, modifier = Modifier.padding(8.dp))
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Why we should read this (optional)") },
                    placeholder = { Text("e.g. Recommended for our evening reading together...") },
                    maxLines = 3,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val pages = totalPagesText.toIntOrNull() ?: 200
                    onAdd(title, author, pages, genre, selectedEmoji, notes)
                },
                enabled = title.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = DayBluePrimary)
            ) {
                Text("Add Book 📚")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}



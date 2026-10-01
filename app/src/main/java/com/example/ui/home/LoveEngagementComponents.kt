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
                    color = if (isDark) Color(0xFF2E1C0C) else Color(0xFFFFF3E0)
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
                            color = if (isDark) Color(0xFFFDBA74) else Color(0xFFE65100)
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
                        text = "🔒 Answer to reveal $partnerName's answer",
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
                        text = dailyQuestion.myAnswer ?: "Loved every moment.",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onBackground,
                        lineHeight = 20.sp
                    )

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

                        IconButton(
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
                        text = dailyQuestion.partnerAnswer ?: "Waiting for answer...",
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
                        text = "Both answered today! Connection renewed 💕",
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



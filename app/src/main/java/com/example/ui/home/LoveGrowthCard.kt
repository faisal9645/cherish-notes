package com.example.ui.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.Message
import com.example.data.model.MessageType
import java.text.NumberFormat
import java.util.Calendar
import java.util.Locale

/** One level of our love, reached after [fromDays] days together. */
private class LoveLevel(val number: Int, val name: String, val fromDays: Int)

private val LoveLevels = listOf(
    LoveLevel(1, "New Love 🌱", 0),
    LoveLevel(2, "Falling Deeper 💞", 100),
    LoveLevel(3, "Soulmates 💖", 365),
    LoveLevel(4, "Forever Bond 💍", 3 * 365),
    LoveLevel(5, "Eternal ♾️", 5 * 365)
)

/** A small habit for the day, a different one each day. */
private val DailyHabits = listOf(
    "Hug for at least 20 seconds today.",
    "Send a voice note just to say you miss each other.",
    "Tell each other one thing you're grateful for.",
    "Plan your next date, even a tiny one.",
    "Share a song that reminds you of the other.",
    "Compliment something you usually don't say out loud.",
    "Put your phones away for one long talk tonight.",
    "Send a photo of something that made you think of them.",
    "Ask how their day really went, and just listen.",
    "Say goodnight with one thing you loved about today.",
    "Recreate a favourite memory, in a small way.",
    "Write a quick note they'll find later.",
    "Laugh together: share the funniest thing you saw.",
    "Hold hands, even just in a photo or a heartbeat touch."
)

/**
 * Our love growth, from real numbers: the level by days together (with how far to the next one),
 * the Daily Us streak (days in a row we both answered), the Heartbeat Touch streak, and a habit
 * for today. Until the together date is set, it says so.
 */
@Composable
fun LoveGrowthCard(daysTogether: Int?, dailyStreak: Int, heartbeatStreak: Int) {
    val number = NumberFormat.getIntegerInstance(Locale.getDefault())
    val habit = remember {
        DailyHabits[Calendar.getInstance().get(Calendar.DAY_OF_YEAR) % DailyHabits.size]
    }
    val level = daysTogether?.let { days -> LoveLevels.last { days >= it.fromDays } }
    val next = level?.let { current -> LoveLevels.firstOrNull { it.number == current.number + 1 } }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.TrendingUp,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Our Love Growth",
                        fontSize = 15.sp,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                if (level != null) {
                    Surface(shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)) {
                        Text(
                            text = "Level ${level.number}: ${level.name}",
                            fontSize = 12.sp,
                            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            if (daysTogether == null || level == null) {
                Text(
                    text = "Set your days together on the card above to see how your love grows.",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Text(
                    text = if (next != null) {
                        "${number.format(daysTogether.toLong())} days together. Next level in ${number.format((next.fromDays - daysTogether).toLong())} days."
                    } else {
                        "${number.format(daysTogether.toLong())} days together. The highest level, forever ✨"
                    },
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                val progress = if (next == null) 1f
                else ((daysTogether - level.fromDays).toFloat() / (next.fromDays - level.fromDays)).coerceIn(0f, 1f)
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxWidth().height(6.dp).clip(CircleShape),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                GrowthStat(
                    label = "Daily Us streak",
                    value = if (dailyStreak > 0) "🔥 $dailyStreak ${if (dailyStreak == 1) "day" else "days"}" else "Answer today",
                    modifier = Modifier.weight(1f)
                )
                GrowthStat(
                    label = "Heartbeat streak",
                    value = if (heartbeatStreak > 0) "💓 $heartbeatStreak ${if (heartbeatStreak == 1) "day" else "days"}" else "Hold together",
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(10.dp))
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(modifier = Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(text = "💡", fontSize = 16.sp)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Today's habit: $habit",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                        lineHeight = 16.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun GrowthStat(label: String, value: String, modifier: Modifier = Modifier) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.07f),
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
            Text(text = label, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                text = value,
                fontSize = 13.5.sp,
                fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

/** The chat card's one-line preview: the text, or what kind of message it was. */
fun lastMessagePreview(message: Message): String = when (message.getTypedType()) {
    MessageType.IMAGE -> "📷 Photo"
    MessageType.VIDEO -> if (message.isCircularVideoNote()) "🎥 Video note" else "🎥 Video"
    MessageType.AUDIO -> "🎤 Voice message"
    MessageType.DOCUMENT -> "📄 Document"
    else -> message.text.ifBlank { "Message" }
}

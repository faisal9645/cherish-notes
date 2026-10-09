package com.example.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.RoseGoldPrimary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MoodPickerSheet(
    currentMood: String?,
    onSelectMood: (String) -> Unit,
    onClearMood: () -> Unit,
    onDismiss: () -> Unit,
    // When my mood was set (0 = before this was kept) and who sees it
    currentMoodAt: Long = 0L,
    partnerName: String = "Your partner"
) {
    // The quick check-in: one tap, shown softly in the partner's header ("Stressed" offers them
    // to send a hug)
    val quickMoods = listOf("Happy ☀️", "Tired 😴", "Missing you 🥰", "Stressed 😣")
    val presets = listOf(
        "Thinking of you 💭",
        "At work 💼",
        "Studying 📚",
        "Need hugs 🥺",
        "Sleepy 😴",
        "Cozy in bed 🌙",
        "Driving 🚗",
        "Eating 🍕",
        "Gym / Workout 🏋️",
        "Excited 🎉"
    )

    // My own words stay in the box (they're not one of the buttons above)
    val myCustomMood = currentMood?.trim()?.takeIf { it.isNotBlank() && it !in quickMoods && it !in presets }
    var customMoodText by remember { mutableStateOf(myCustomMood.orEmpty()) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp)
                .navigationBarsPadding()
        ) {
            Text(
                text = "How are you feeling?",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Your partner sees it softly next to your name in chat.",
                fontSize = 12.5.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // My mood as it is now, also a custom one (the partner sees it for 12 hours)
            if (!currentMood.isNullOrBlank()) {
                val now = System.currentTimeMillis()
                val showing = currentMoodAt == 0L || now - currentMoodAt < MOOD_FRESH_MS
                Spacer(modifier = Modifier.height(12.dp))
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = RoseGoldPrimary.copy(alpha = 0.10f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, RoseGoldPrimary.copy(alpha = 0.35f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("my_current_mood")
                ) {
                    Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                        Text(
                            text = "Your mood now",
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = RoseGoldPrimary
                        )
                        EmojiText(
                            text = currentMood,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            emojiScale = 1.2f,
                            maxLines = 2
                        )
                        Text(
                            text = when {
                                !showing -> "Not showing anymore (moods show for 12 hours). Set it again to show it."
                                currentMoodAt == 0L -> "$partnerName sees it next to your name"
                                else -> {
                                    val until = java.text.SimpleDateFormat("h:mm a", java.util.Locale.getDefault())
                                        .format(java.util.Date(currentMoodAt + MOOD_FRESH_MS))
                                    "$partnerName sees it until $until"
                                }
                            },
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                quickMoods.forEach { mood ->
                    val isSelected = mood == currentMood
                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = if (isSelected) RoseGoldPrimary.copy(alpha = 0.18f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            if (isSelected) RoseGoldPrimary else Color.Transparent
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .clickable {
                                onSelectMood(mood)
                                onDismiss()
                            }
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(vertical = 12.dp, horizontal = 4.dp)
                        ) {
                            Text(text = mood.substringAfterLast(' '), fontSize = 28.sp)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = mood.substringBeforeLast(' '),
                                fontSize = 11.5.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                color = if (isSelected) RoseGoldPrimary else MaterialTheme.colorScheme.onSurface,
                                maxLines = 1
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Presets grid
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(presets) { mood ->
                    val isSelected = mood == currentMood
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = if (isSelected) RoseGoldPrimary.copy(alpha = 0.18f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            if (isSelected) RoseGoldPrimary else Color.Transparent
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onSelectMood(mood)
                                onDismiss()
                            }
                    ) {
                        EmojiText(
                            text = mood,
                            fontSize = 13.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            color = if (isSelected) RoseGoldPrimary else MaterialTheme.colorScheme.onSurface,
                            emojiScale = 1.2f,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Custom mood text field
            OutlinedTextField(
                value = customMoodText,
                onValueChange = { customMoodText = it },
                placeholder = { Text("Or type a custom status...") },
                label = if (myCustomMood != null && customMoodText.trim() == myCustomMood) {
                    { Text("Your custom mood") }
                } else null,
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("custom_mood_field"),
                trailingIcon = {
                    // Nothing to set when it's already my mood
                    if (customMoodText.isNotBlank() && customMoodText.trim() != currentMood?.trim()) {
                        TextButton(
                            onClick = {
                                onSelectMood(customMoodText.trim())
                                onDismiss()
                            }
                        ) {
                            Text("Set", color = RoseGoldPrimary, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            )

            Spacer(modifier = Modifier.height(12.dp))

            if (!currentMood.isNullOrBlank()) {
                TextButton(
                    onClick = {
                        onClearMood()
                        onDismiss()
                    },
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                ) {
                    Text("Clear Current Mood", color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

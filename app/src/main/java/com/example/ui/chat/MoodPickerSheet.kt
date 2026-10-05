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
    onDismiss: () -> Unit
) {
    val presets = listOf(
        "Missing you 🥰",
        "Thinking of you 💭",
        "At work 💼",
        "Studying 📚",
        "Need hugs 🥺",
        "Happy ☀️",
        "Sleepy 😴",
        "Cozy in bed 🌙",
        "Driving 🚗",
        "Eating 🍕",
        "Gym / Workout 🏋️",
        "Excited 🎉"
    )

    var customMoodText by remember { mutableStateOf("") }

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
                text = "Set Your Mood & Status",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Your partner will see this right next to your name in chat.",
                fontSize = 12.5.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(16.dp))

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
                        Text(
                            text = mood,
                            fontSize = 13.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            color = if (isSelected) RoseGoldPrimary else MaterialTheme.colorScheme.onSurface,
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
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth(),
                trailingIcon = {
                    if (customMoodText.isNotBlank()) {
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

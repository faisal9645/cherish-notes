package com.example.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.RoseGoldPrimary
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
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
        "Loving ❤️",
        "Loving mood 💕",
        "Thinking of you 💭",
        "Need hugs 🥺",
        "Need a call 📞",
        "At work 💼",
        "Studying 📚",
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

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scrollState = rememberScrollState()
    val coroutineScope = rememberCoroutineScope()
    val isImeVisible = WindowInsets.isImeVisible
    val bringIntoViewRequester = remember { BringIntoViewRequester() }

    LaunchedEffect(isImeVisible) {
        if (isImeVisible) {
            delay(120)
            bringIntoViewRequester.bringIntoView()
            scrollState.animateScrollTo(scrollState.maxValue)
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = com.example.ui.components.CherishDialogDefaults.sheetShape,
        containerColor = com.example.ui.components.CherishDialogDefaults.sheetContainerColor,
        dragHandle = { com.example.ui.components.CherishDialogDefaults.SheetDragHandle() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(scrollState)
                .padding(horizontal = 20.dp, vertical = 8.dp)
                .navigationBarsPadding()
                .imePadding()
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
                fontSize = 12.sp,
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
                    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                        Text(
                            text = "Your mood now",
                            fontSize = 11.sp,
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
                        shape = RoundedCornerShape(20.dp),
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
                                fontSize = 11.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                color = if (isSelected) RoseGoldPrimary else MaterialTheme.colorScheme.onSurface,
                                maxLines = 1
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Presets grid in scrollable column
            presets.chunked(2).forEach { rowItems ->
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp)
                ) {
                    rowItems.forEach { mood ->
                        val isSelected = mood == currentMood
                        Surface(
                            shape = RoundedCornerShape(16.dp),
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
                            EmojiText(
                                text = mood,
                                fontSize = 13.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                color = if (isSelected) RoseGoldPrimary else MaterialTheme.colorScheme.onSurface,
                                emojiScale = 1.2f,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp)
                            )
                        }
                    }
                    if (rowItems.size == 1) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Custom mood text field
            OutlinedTextField(
                value = customMoodText,
                onValueChange = { customMoodText = it },
                placeholder = { Text("Or type a custom status...") },
                label = if (myCustomMood != null && customMoodText.trim() == myCustomMood) {
                    { Text("Your custom mood") }
                } else null,
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(
                    onDone = {
                        if (customMoodText.isNotBlank()) {
                            onSelectMood(customMoodText.trim())
                            onDismiss()
                        }
                    }
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("custom_mood_field")
                    .bringIntoViewRequester(bringIntoViewRequester)
                    .onFocusChanged { focusState ->
                        if (focusState.isFocused) {
                            coroutineScope.launch {
                                delay(120)
                                bringIntoViewRequester.bringIntoView()
                                scrollState.animateScrollTo(scrollState.maxValue)
                            }
                        }
                    },
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

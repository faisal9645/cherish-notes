package com.example.ui.home

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.OpenWhenLetter
import com.example.ui.theme.DarkAubergine
import com.example.ui.theme.RoseGoldPrimary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OpenWhenScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Default romantic pre-curated envelopes
    var envelopes by remember {
        mutableStateOf(
            listOf(
                OpenWhenLetter(
                    id = "env_1",
                    title = "Open when you miss me",
                    category = "MISS_YOU",
                    envelopeEmoji = "💌",
                    content = "Whenever you feel a million miles away, close your eyes and remember: every single beat of my heart belongs to you. You are my home, no matter where we are. Call me whenever you need my voice. ❤️",
                    authorName = "Your Love",
                    unlockCondition = "Open when longing for my warmth",
                    isOpened = false
                ),
                OpenWhenLetter(
                    id = "env_2",
                    title = "Open when you have a bad day",
                    category = "BAD_DAY",
                    envelopeEmoji = "🌧️",
                    content = "Take a deep breath, my darling. Today was just one tough chapter, not the entire book. You are stronger, braver, and more resilient than you know. When you get home, everything is going to be okay. I'm right here with you. 🫂",
                    authorName = "Your Love",
                    unlockCondition = "Open when the world feels too heavy",
                    isOpened = false
                ),
                OpenWhenLetter(
                    id = "env_3",
                    title = "Open when you can't sleep",
                    category = "CANT_SLEEP",
                    envelopeEmoji = "🌙",
                    content = "It's late, the night is quiet, and the stars are out. Imagine my arms wrapped around you, whispering sweet things in your ear. Rest your mind, close your eyes, and dream of our next sunrise together. Sweet dreams my angel. ✨",
                    authorName = "Your Love",
                    unlockCondition = "Open past midnight when thoughts wander",
                    isOpened = false
                ),
                OpenWhenLetter(
                    id = "env_4",
                    title = "Open on our anniversary",
                    category = "ANNIVERSARY",
                    envelopeEmoji = "💍",
                    content = "Happy Anniversary to the one who made my life an extraordinary adventure. From the very first glance to this exact second, loving you has been the easiest, truest decision I've ever made. Here is to our entire lifetime together! 🥂❤️",
                    authorName = "Your Love",
                    unlockCondition = "Locked until our milestone day",
                    isOpened = false
                ),
                OpenWhenLetter(
                    id = "env_5",
                    title = "Open when you need motivation",
                    category = "MOTIVATION",
                    envelopeEmoji = "🔥",
                    content = "Look at how far you've come! I believe in your genius, your ambition, and your boundless potential even when you doubt yourself. Go out there and conquer it — I am your biggest cheerleader forever! 🌟",
                    authorName = "Your Love",
                    unlockCondition = "Open before a big challenge",
                    isOpened = false
                ),
                OpenWhenLetter(
                    id = "env_6",
                    title = "Open when you want a silly laugh",
                    category = "FUNNY",
                    envelopeEmoji = "😜",
                    content = "Remember that time we got hopelessly lost and ended up laughing until our stomachs hurt? You have the most infectious, adorable laugh on this planet. Smile right now, you gorgeous human! 😂💕",
                    authorName = "Your Love",
                    unlockCondition = "Open when you need an instant grin",
                    isOpened = false
                )
            )
        )
    }

    var selectedLetter by remember { mutableStateOf<OpenWhenLetter?>(null) }
    var showCreateDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            "Open When... Envelopes",
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = 18.sp
                        )
                        Text(
                            "Locked letters sealed until the right moment",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier.testTag("open_when_back")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { showCreateDialog = true }) {
                        Icon(Icons.Default.AddCircleOutline, contentDescription = "Add Envelope", tint = RoseGoldPrimary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        },
        containerColor = Color(0xFFFAF9FC)
    ) { paddingValues ->
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            items(envelopes, key = { it.id }) { letter ->
                EnvelopeCard(
                    letter = letter,
                    onClick = {
                        selectedLetter = letter
                    }
                )
            }
        }
    }

    // Modal Letter Viewer (Open animation & romantic letter paper)
    selectedLetter?.let { letter ->
        LetterModalDialog(
            letter = letter,
            onDismiss = { selectedLetter = null },
            onMarkOpened = {
                envelopes = envelopes.map {
                    if (it.id == letter.id) it.copy(isOpened = true, openedAt = System.currentTimeMillis()) else it
                }
                selectedLetter = letter.copy(isOpened = true)
            }
        )
    }

    // Create New Envelope Dialog
    if (showCreateDialog) {
        CreateEnvelopeDialog(
            onDismiss = { showCreateDialog = false },
            onCreate = { title, condition, text, emoji ->
                val newLetter = OpenWhenLetter(
                    id = "env_${System.currentTimeMillis()}",
                    title = title,
                    envelopeEmoji = emoji,
                    content = text,
                    authorName = "Me",
                    unlockCondition = condition,
                    isOpened = false
                )
                envelopes = envelopes + newLetter
                showCreateDialog = false
            }
        )
    }
}

@Composable
fun EnvelopeCard(
    letter: OpenWhenLetter,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, if (letter.isOpened) RoseGoldPrimary.copy(alpha = 0.4f) else Color(0xFFE8E5EE)),
        shadowElevation = 2.dp,
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(54.dp)
                    .clip(CircleShape)
                    .background(
                        if (letter.isOpened)
                            Brush.linearGradient(listOf(Color(0xFFFFEBEE), Color(0xFFFFCDD2)))
                        else
                            Brush.linearGradient(listOf(Color(0xFFEDE7F6), Color(0xFFD1C4E9)))
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(letter.envelopeEmoji, fontSize = 28.sp)
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = letter.title,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                textAlign = TextAlign.Center,
                color = DarkAubergine,
                maxLines = 2
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = letter.unlockCondition,
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                maxLines = 1
            )

            Spacer(modifier = Modifier.height(10.dp))

            Surface(
                shape = RoundedCornerShape(12.dp),
                color = if (letter.isOpened) Color(0xFFE8F5E9) else Color(0xFFF3F0F7)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (letter.isOpened) Icons.Filled.Check else Icons.Outlined.Lock,
                        contentDescription = null,
                        modifier = Modifier.size(11.dp),
                        tint = if (letter.isOpened) Color(0xFF2E7D32) else RoseGoldPrimary
                    )
                    Spacer(modifier = Modifier.width(3.dp))
                    Text(
                        text = if (letter.isOpened) "Opened" else "Locked",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (letter.isOpened) Color(0xFF2E7D32) else RoseGoldPrimary
                    )
                }
            }
        }
    }
}

@Composable
fun LetterModalDialog(
    letter: OpenWhenLetter,
    onDismiss: () -> Unit,
    onMarkOpened: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(letter.envelopeEmoji, fontSize = 24.sp)
                Spacer(modifier = Modifier.width(8.dp))
                Text(letter.title, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = DarkAubergine)
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xFFFFFDF7)) // parchment ivory color
                    .padding(16.dp)
            ) {
                Text(
                    text = "Condition: ${letter.unlockCondition}",
                    fontSize = 11.sp,
                    fontStyle = FontStyle.Italic,
                    color = RoseGoldPrimary
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = letter.content,
                    fontSize = 14.sp,
                    lineHeight = 22.sp,
                    color = Color(0xFF2C2416),
                    fontFamily = FontFamily.Serif
                )
                Spacer(modifier = Modifier.height(14.dp))
                Text(
                    text = "— With all my heart, ${letter.authorName} ❤️",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = DarkAubergine,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.End
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onMarkOpened()
                    onDismiss()
                },
                colors = ButtonDefaults.buttonColors(containerColor = RoseGoldPrimary)
            ) {
                Text(if (letter.isOpened) "Keep Sealed" else "Break Seal & Read ❤️")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}

@Composable
fun CreateEnvelopeDialog(
    onDismiss: () -> Unit,
    onCreate: (title: String, condition: String, text: String, emoji: String) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var condition by remember { mutableStateOf("") }
    var text by remember { mutableStateOf("") }
    var emoji by remember { mutableStateOf("💌") }

    val emojis = listOf("💌", "🌙", "🌧️", "✈️", "☕", "💍", "🔥", "🧸")

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Seal a New 'Open When' Envelope", fontWeight = FontWeight.Bold, fontSize = 16.sp) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                // Emoji picker
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceAround
                ) {
                    emojis.forEach { e ->
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(if (emoji == e) RoseGoldPrimary.copy(alpha = 0.2f) else Color.Transparent)
                                .clickable { emoji = e },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(e, fontSize = 18.sp)
                        }
                    }
                }

                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Title (e.g. Open when you feel lonely)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = condition,
                    onValueChange = { condition = it },
                    label = { Text("Unlock condition / moment") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text("Your heartfelt message") },
                    minLines = 4,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (title.isNotBlank() && text.isNotBlank()) {
                        onCreate(title, condition, text, emoji)
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = RoseGoldPrimary)
            ) {
                Text("Seal with Wax ❤️")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}



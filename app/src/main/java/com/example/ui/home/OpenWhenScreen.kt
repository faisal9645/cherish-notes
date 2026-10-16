package com.example.ui.home

import com.example.ui.theme.darkTone

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
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AddCircleOutline
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
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
import com.example.ui.theme.Blue300
import com.example.ui.theme.Blue900
import com.example.ui.theme.Green50
import com.example.ui.theme.Green800
import com.example.ui.theme.Lavender200
import com.example.ui.theme.Lavender50
import com.example.ui.components.CherishAlertDialog
import com.example.ui.components.CherishButton
import com.example.ui.components.pressScale
import com.example.ui.components.pressScaleTarget
import com.example.ui.components.springClickable

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OpenWhenScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Our real letters, kept on the couple's document: the ones written for me and the ones I wrote
    val app = androidx.compose.ui.platform.LocalContext.current.applicationContext as com.example.CherishApplication
    val repository = app.coupleFeaturesRepository
    LaunchedEffect(Unit) { repository.rebuildLoveUs() }
    val envelopes by repository.openWhenFlow.collectAsState()
    val myId = app.authRepository.getCurrentUserId()

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
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { showCreateDialog = true }) {
                        Icon(Icons.Rounded.AddCircleOutline, contentDescription = "Add Envelope", tint = RoseGoldPrimary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        if (envelopes.isEmpty()) {
            // No letters yet
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(32.dp)
            ) {
                Text("\uD83D\uDC8C", fontSize = 44.sp)
                Spacer(modifier = Modifier.height(10.dp))
                Text("No letters yet", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = MaterialTheme.colorScheme.onSurface)
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    "Write a sealed letter for the moments your love will need it most",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(16.dp))
                CherishButton(onClick = { showCreateDialog = true }) { Text("Write a letter") }
            }
        } else LazyVerticalGrid(
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
                    },
                    // Envelopes slide into place, and fade in and out, as they're written or opened
                    modifier = Modifier.animateItem()
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
                // Marked opened only when it's a letter written for me (mine just show)
                repository.markOpenWhenOpened(letter.id)
                selectedLetter = letter.copy(isOpened = true)
            }
        )
    }

    // Create New Envelope Dialog
    if (showCreateDialog) {
        CreateEnvelopeDialog(
            onDismiss = { showCreateDialog = false },
            onCreate = { title, condition, text, emoji ->
                repository.addOpenWhenLetter(title, condition, text, emoji)
                showCreateDialog = false
            }
        )
    }
}

@Composable
fun EnvelopeCard(
    letter: OpenWhenLetter,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Surface's own click keeps the ripple inside the rounded corners (and the shadow intact);
    // the envelope shrinks a touch while held
    val press = remember { MutableInteractionSource() }
    Surface(
        onClick = onClick,
        interactionSource = press,
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, if (letter.isOpened) RoseGoldPrimary.copy(alpha = 0.4f) else Lavender50),
        shadowElevation = 2.dp,
        modifier = modifier
            .fillMaxWidth()
            .pressScale(press)
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
                            Brush.linearGradient(listOf(Color(0xFFE8E3F3), Lavender200))
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
                color = MaterialTheme.colorScheme.onSurface,
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

            val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = if (letter.isOpened) (if (isDark) darkTone(Blue900) else Green50) else (if (isDark) MaterialTheme.colorScheme.surfaceVariant else Lavender50)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (letter.isOpened) Icons.Rounded.Check else Icons.Rounded.Lock,
                        contentDescription = null,
                        modifier = Modifier.size(11.dp),
                        tint = if (letter.isOpened) (if (isDark) darkTone(Blue300) else Green800) else RoseGoldPrimary
                    )
                    Spacer(modifier = Modifier.width(3.dp))
                    Text(
                        text = if (letter.isOpened) "Opened" else "Locked",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (letter.isOpened) (if (isDark) darkTone(Blue300) else Green800) else RoseGoldPrimary
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
    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    CherishAlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(letter.envelopeEmoji, fontSize = 24.sp)
                Spacer(modifier = Modifier.width(8.dp))
                Text(letter.title, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurface)
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(if (isDark) MaterialTheme.colorScheme.surfaceVariant else Color(0xFFFFFDF7))
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
                    color = MaterialTheme.colorScheme.onSurface,
                    fontFamily = FontFamily.Serif
                )
                Spacer(modifier = Modifier.height(14.dp))
                Text(
                    text = "— With all my heart, ${letter.authorName} ❤️",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.End
                )
            }
        },
        confirmButton = {
            CherishButton(
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

    CherishAlertDialog(
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
                                .pressScaleTarget()
                                .clip(CircleShape)
                                .background(if (emoji == e) RoseGoldPrimary.copy(alpha = 0.2f) else Color.Transparent)
                                .springClickable { emoji = e },
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
            CherishButton(
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



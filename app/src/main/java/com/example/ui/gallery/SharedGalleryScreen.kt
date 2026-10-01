package com.example.ui.gallery

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.model.Message
import com.example.data.model.MessageType
import com.example.ui.chat.ChatViewModel
import com.example.ui.chat.FullScreenMediaViewer
import com.example.ui.theme.AppGradientStart
import com.example.ui.theme.RoseGoldPrimary
import com.example.ui.theme.appHorizontalGradient
import java.text.SimpleDateFormat
import java.util.*

data class GalleryLinkItem(
    val messageId: String,
    val url: String,
    val host: String,
    val messageText: String,
    val timestamp: Long,
    val senderName: String,
    val isFromMe: Boolean
)

private val URL_REGEX = Regex(
    "(https?://[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}(?:/[^\\s]*)?|www\\.[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}(?:/[^\\s]*)?)",
    RegexOption.IGNORE_CASE
)

private fun formatGalleryDateTime(timestamp: Long): String {
    val sdf = SimpleDateFormat("MMM d, yyyy • h:mm a", Locale.getDefault())
    return sdf.format(Date(timestamp))
}

private fun formatVoiceDuration(currentSec: Int, totalSec: Int): String {
    val curM = currentSec / 60
    val curS = currentSec % 60
    val totM = totalSec / 60
    val totS = totalSec % 60
    return String.format(Locale.getDefault(), "%d:%02d / %d:%02d", curM, curS, totM, totS)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SharedGalleryScreen(
    chatViewModel: ChatViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToMessage: (messageId: String) -> Unit = {}
) {
    val context = LocalContext.current
    val chatState by chatViewModel.uiState.collectAsState()
    var selectedTab by remember { mutableIntStateOf(0) }
    var selectedMediaUrl by remember { mutableStateOf<String?>(null) }
    var selectedMessageIdForViewer by remember { mutableStateOf<String?>(null) }

    val voicePlayerHelper = chatViewModel.voicePlayerHelper
    val currentTrackId by voicePlayerHelper.currentTrackId.collectAsState()
    val isVoicePlaying by voicePlayerHelper.isPlaying.collectAsState()
    val voiceProgress by voicePlayerHelper.playbackProgress.collectAsState()
    val currentPositionSec by voicePlayerHelper.currentPositionSec.collectAsState()
    val playbackSpeed by voicePlayerHelper.playbackSpeed.collectAsState()

    val currentUserId = chatState.currentUser?.id ?: "user_me"

    val tabs = listOf("Media", "Voice Notes", "Links", "Starred")

    // Extract links from all messages
    val extractedLinks = remember(chatState.messages, currentUserId) {
        val list = mutableListOf<GalleryLinkItem>()
        chatState.messages.forEach { msg ->
            if (!msg.isDeleted && msg.text.isNotBlank()) {
                val matches = URL_REGEX.findAll(msg.text)
                for (m in matches) {
                    val raw = m.value
                    val full = if (raw.startsWith("www.", ignoreCase = true)) "https://$raw" else raw
                    val host = try {
                        Uri.parse(full).host?.removePrefix("www.") ?: "website"
                    } catch (_: Exception) {
                        "website"
                    }
                    list.add(
                        GalleryLinkItem(
                            messageId = msg.id,
                            url = full,
                            host = host,
                            messageText = msg.text,
                            timestamp = msg.timestamp,
                            senderName = msg.senderName.ifBlank { if (msg.senderId == currentUserId) "You" else "Partner" },
                            isFromMe = (msg.senderId == currentUserId)
                        )
                    )
                }
            }
        }
        list.sortedByDescending { it.timestamp }
    }

    val mediaMessages = remember(chatState.messages) {
        chatState.messages.filter { it.mediaUrl != null && !it.isDeleted && it.getTypedType() == MessageType.IMAGE }
            .sortedByDescending { it.timestamp }
    }

    val voiceMessages = remember(chatState.messages) {
        chatState.messages.filter { it.getTypedType() == MessageType.AUDIO && !it.isDeleted }
            .sortedByDescending { it.timestamp }
    }

    val starredMessages = remember(chatState.messages) {
        chatState.messages.filter { it.isStarred && !it.isDeleted }
            .sortedByDescending { it.timestamp }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            "Shared Gallery",
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            when (selectedTab) {
                                0 -> "${mediaMessages.size} photos & videos"
                                1 -> "${voiceMessages.size} voice notes"
                                2 -> "${extractedLinks.size} shared links"
                                else -> "${starredMessages.size} starred items"
                            },
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier.testTag("gallery_back_button")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (chatState.isSecretHistoryRevealed) {
                        IconButton(
                            onClick = { chatViewModel.hideSecretHistory() },
                            modifier = Modifier.testTag("gallery_shield_toggle")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Shield,
                                contentDescription = "Shield Gallery",
                                tint = RoseGoldPrimary
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        },
        containerColor = Color(0xFFF9F9FB)
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Privacy Shield View: When secret history is locked/shielded
            if (!chatState.isSecretHistoryRevealed) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Surface(
                        shape = RoundedCornerShape(24.dp),
                        color = MaterialTheme.colorScheme.surface,
                        shadowElevation = 4.dp,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(28.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(68.dp)
                                    .clip(CircleShape)
                                    .background(appHorizontalGradient()),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Lock,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(32.dp)
                                )
                            }
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "Gallery Shielded for Privacy",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Photos, videos, voice notes, and links are hidden until you unlock the secret space.",
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                lineHeight = 18.sp
                            )
                            Spacer(modifier = Modifier.height(24.dp))
                            Button(
                                onClick = { chatViewModel.revealSecretHistory() },
                                colors = ButtonDefaults.buttonColors(containerColor = RoseGoldPrimary),
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .testTag("gallery_recover_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.LockOpen,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Recover & Show Gallery", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            } else {
                // Primary Tab Row
                PrimaryTabRow(
                    selectedTabIndex = selectedTab,
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = RoseGoldPrimary
                ) {
                    tabs.forEachIndexed { index, title ->
                        Tab(
                            selected = selectedTab == index,
                            onClick = { selectedTab = index },
                            text = {
                                Text(
                                    title,
                                    fontWeight = if (selectedTab == index) FontWeight.Bold else FontWeight.Normal,
                                    fontSize = 13.sp
                                )
                            }
                        )
                    }
                }

                when (selectedTab) {
                    // TAB 0: Media (Photos & Videos)
                    0 -> {
                        if (mediaMessages.isEmpty()) {
                            EmptyGalleryNotice("No shared photos or videos yet 💕")
                        } else {
                            LazyVerticalGrid(
                                columns = GridCells.Fixed(3),
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(4.dp),
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                items(mediaMessages, key = { it.id }) { msg ->
                                    Box(
                                        modifier = Modifier
                                            .aspectRatio(1f)
                                            .clip(RoundedCornerShape(8.dp))
                                            .clickable {
                                                selectedMediaUrl = msg.mediaUrl
                                                selectedMessageIdForViewer = msg.id
                                            }
                                    ) {
                                        AsyncImage(
                                            model = msg.mediaUrl,
                                            contentDescription = "Shared photo",
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.fillMaxSize()
                                        )

                                        // Overlay "Show in chat" button on bottom edge
                                        Box(
                                            modifier = Modifier
                                                .align(Alignment.BottomEnd)
                                                .padding(4.dp)
                                                .clip(CircleShape)
                                                .background(Color.Black.copy(alpha = 0.55f))
                                                .clickable { onNavigateToMessage(msg.id) }
                                                .padding(6.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Chat,
                                                contentDescription = "Show in chat",
                                                tint = Color.White,
                                                modifier = Modifier.size(14.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // TAB 1: Voice Notes (Playable with audio player and Show in Chat)
                    1 -> {
                        if (voiceMessages.isEmpty()) {
                            EmptyGalleryNotice("No shared voice notes yet 🎙️")
                        } else {
                            LazyColumn(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 14.dp, vertical = 10.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                items(voiceMessages, key = { it.id }) { msg ->
                                    val isThisPlaying = (currentTrackId == msg.id && isVoicePlaying)
                                    val isThisActive = (currentTrackId == msg.id)
                                    val duration = if (msg.durationSeconds > 0) msg.durationSeconds else 5

                                    Surface(
                                        shape = RoundedCornerShape(16.dp),
                                        color = MaterialTheme.colorScheme.surface,
                                        shadowElevation = 1.5.dp,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Column(modifier = Modifier.padding(14.dp)) {
                                            // Top info: Sender + Date & Time
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(34.dp)
                                                        .clip(CircleShape)
                                                        .background(RoseGoldPrimary.copy(alpha = 0.12f)),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.Mic,
                                                        contentDescription = null,
                                                        tint = RoseGoldPrimary,
                                                        modifier = Modifier.size(18.dp)
                                                    )
                                                }
                                                Spacer(modifier = Modifier.width(10.dp))
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text(
                                                        text = if (msg.senderId == currentUserId) "Voice note by You" else "Voice note by ${msg.senderName}",
                                                        fontWeight = FontWeight.SemiBold,
                                                        fontSize = 13.sp,
                                                        color = MaterialTheme.colorScheme.onSurface
                                                    )
                                                    Text(
                                                        text = formatGalleryDateTime(msg.timestamp),
                                                        fontSize = 11.sp,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                }

                                                // Show in chat button
                                                OutlinedButton(
                                                    onClick = { onNavigateToMessage(msg.id) },
                                                    shape = RoundedCornerShape(20.dp),
                                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                                    modifier = Modifier.height(32.dp)
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.Chat,
                                                        contentDescription = null,
                                                        tint = RoseGoldPrimary,
                                                        modifier = Modifier.size(13.dp)
                                                    )
                                                    Spacer(modifier = Modifier.width(4.dp))
                                                    Text("Show in chat", fontSize = 11.sp, color = RoseGoldPrimary)
                                                }
                                            }

                                            Spacer(modifier = Modifier.height(10.dp))

                                            // Audio Player Bar
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clip(RoundedCornerShape(12.dp))
                                                    .background(Color(0xFFF7F7FA))
                                                    .padding(horizontal = 10.dp, vertical = 8.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                // Play / Pause Button
                                                IconButton(
                                                    onClick = {
                                                        msg.mediaUrl?.let { url ->
                                                            voicePlayerHelper.playAudio(msg.id, url)
                                                        }
                                                    },
                                                    modifier = Modifier
                                                        .size(38.dp)
                                                        .clip(CircleShape)
                                                        .background(RoseGoldPrimary)
                                                ) {
                                                    Icon(
                                                        imageVector = if (isThisPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                                        contentDescription = if (isThisPlaying) "Pause" else "Play",
                                                        tint = Color.White,
                                                        modifier = Modifier.size(20.dp)
                                                    )
                                                }

                                                Spacer(modifier = Modifier.width(10.dp))

                                                // Progress & scrubber bar
                                                Column(modifier = Modifier.weight(1f)) {
                                                    LinearProgressIndicator(
                                                        progress = { if (isThisActive) voiceProgress else 0f },
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .height(5.dp)
                                                            .clip(RoundedCornerShape(3.dp)),
                                                        color = RoseGoldPrimary,
                                                        trackColor = Color(0xFFE2E2E8)
                                                    )
                                                    Spacer(modifier = Modifier.height(4.dp))
                                                    Row(
                                                        modifier = Modifier.fillMaxWidth(),
                                                        horizontalArrangement = Arrangement.SpaceBetween
                                                    ) {
                                                        Text(
                                                            text = if (isThisActive && isThisPlaying) {
                                                                formatVoiceDuration(currentPositionSec, duration)
                                                            } else {
                                                                "${duration}s audio"
                                                            },
                                                            fontSize = 11.sp,
                                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                                        )
                                                        if (isThisActive) {
                                                            Text(
                                                                text = "${playbackSpeed}x",
                                                                fontSize = 11.sp,
                                                                fontWeight = FontWeight.Bold,
                                                                color = RoseGoldPrimary,
                                                                modifier = Modifier.clickable {
                                                                    voicePlayerHelper.togglePlaybackSpeed()
                                                                }
                                                            )
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // TAB 2: Links (WhatsApp Style with dates, times, and actions)
                    2 -> {
                        if (extractedLinks.isEmpty()) {
                            EmptyGalleryNotice("No shared links yet 🔗\nAny links shared in your secret chat will automatically appear here.")
                        } else {
                            LazyColumn(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 14.dp, vertical = 10.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                items(extractedLinks, key = { "${it.messageId}_${it.url}" }) { item ->
                                    Surface(
                                        shape = RoundedCornerShape(16.dp),
                                        color = MaterialTheme.colorScheme.surface,
                                        shadowElevation = 1.5.dp,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Column(modifier = Modifier.padding(14.dp)) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                verticalAlignment = Alignment.Top
                                            ) {
                                                // Domain icon badge (WhatsApp style)
                                                Box(
                                                    modifier = Modifier
                                                        .size(42.dp)
                                                        .clip(RoundedCornerShape(10.dp))
                                                        .background(getDomainBadgeColor(item.host).copy(alpha = 0.12f)),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Icon(
                                                        imageVector = getDomainIcon(item.host),
                                                        contentDescription = null,
                                                        tint = getDomainBadgeColor(item.host),
                                                        modifier = Modifier.size(22.dp)
                                                    )
                                                }

                                                Spacer(modifier = Modifier.width(12.dp))

                                                Column(modifier = Modifier.weight(1f)) {
                                                    // Domain Host in Bold
                                                    Text(
                                                        text = item.host.lowercase(),
                                                        fontWeight = FontWeight.Bold,
                                                        fontSize = 14.sp,
                                                        color = MaterialTheme.colorScheme.onSurface
                                                    )
                                                    Spacer(modifier = Modifier.height(2.dp))
                                                    // Full URL in accent color (clickable)
                                                    Text(
                                                        text = item.url,
                                                        fontSize = 12.sp,
                                                        color = Color(0xFF2563EB),
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis,
                                                        modifier = Modifier.clickable {
                                                            try {
                                                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(item.url)).apply {
                                                                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                                                }
                                                                context.startActivity(intent)
                                                            } catch (e: Exception) {
                                                                Toast.makeText(context, "Cannot open link", Toast.LENGTH_SHORT).show()
                                                            }
                                                        }
                                                    )

                                                    // Optional context message caption if text has more than just the url
                                                    val cleanText = item.messageText.replace(item.url, "").trim()
                                                    if (cleanText.isNotBlank()) {
                                                        Spacer(modifier = Modifier.height(4.dp))
                                                        Text(
                                                            text = cleanText,
                                                            fontSize = 12.sp,
                                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                            maxLines = 2,
                                                            overflow = TextOverflow.Ellipsis
                                                        )
                                                    }

                                                    Spacer(modifier = Modifier.height(6.dp))

                                                    // Meta info: Date & Time + Sender
                                                    Text(
                                                        text = "${formatGalleryDateTime(item.timestamp)} • Sent by ${item.senderName}",
                                                        fontSize = 11.sp,
                                                        color = Color(0xFF94A3B8)
                                                    )
                                                }
                                            }

                                            HorizontalDivider(
                                                modifier = Modifier.padding(vertical = 10.dp),
                                                color = Color(0xFFF1F5F9)
                                            )

                                            // Action Buttons (Open, Copy, Show in chat)
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                    // Open button
                                                    FilledTonalButton(
                                                        onClick = {
                                                            try {
                                                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(item.url)).apply {
                                                                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                                                }
                                                                context.startActivity(intent)
                                                            } catch (_: Exception) {}
                                                        },
                                                        shape = RoundedCornerShape(12.dp),
                                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                                        modifier = Modifier.height(30.dp)
                                                    ) {
                                                        Icon(Icons.Outlined.OpenInBrowser, contentDescription = null, modifier = Modifier.size(13.dp))
                                                        Spacer(modifier = Modifier.width(4.dp))
                                                        Text("Open", fontSize = 11.sp)
                                                    }

                                                    // Copy button
                                                    OutlinedButton(
                                                        onClick = {
                                                            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                                            cm.setPrimaryClip(ClipData.newPlainText("URL", item.url))
                                                            Toast.makeText(context, "Link copied to clipboard", Toast.LENGTH_SHORT).show()
                                                        },
                                                        shape = RoundedCornerShape(12.dp),
                                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                                        modifier = Modifier.height(30.dp)
                                                    ) {
                                                        Icon(Icons.Outlined.ContentCopy, contentDescription = null, modifier = Modifier.size(13.dp))
                                                        Spacer(modifier = Modifier.width(4.dp))
                                                        Text("Copy", fontSize = 11.sp)
                                                    }
                                                }

                                                // Show in chat button
                                                Button(
                                                    onClick = { onNavigateToMessage(item.messageId) },
                                                    shape = RoundedCornerShape(12.dp),
                                                    colors = ButtonDefaults.buttonColors(containerColor = RoseGoldPrimary),
                                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                                    modifier = Modifier.height(30.dp)
                                                ) {
                                                    Icon(Icons.Default.Chat, contentDescription = null, tint = Color.White, modifier = Modifier.size(13.dp))
                                                    Spacer(modifier = Modifier.width(4.dp))
                                                    Text("Show in chat", fontSize = 11.sp, color = Color.White, fontWeight = FontWeight.SemiBold)
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // TAB 3: Starred
                    3 -> {
                        if (starredMessages.isEmpty()) {
                            EmptyGalleryNotice("No starred messages or media yet ⭐")
                        } else {
                            LazyColumn(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 14.dp, vertical = 10.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                items(starredMessages, key = { it.id }) { msg ->
                                    Surface(
                                        shape = RoundedCornerShape(16.dp),
                                        color = MaterialTheme.colorScheme.surface,
                                        shadowElevation = 1.5.dp,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Column(modifier = Modifier.padding(14.dp)) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Star,
                                                    contentDescription = null,
                                                    tint = Color(0xFFF59E0B),
                                                    modifier = Modifier.size(20.dp)
                                                )
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Text(
                                                    text = formatGalleryDateTime(msg.timestamp),
                                                    fontSize = 11.sp,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    modifier = Modifier.weight(1f)
                                                )
                                                OutlinedButton(
                                                    onClick = { onNavigateToMessage(msg.id) },
                                                    shape = RoundedCornerShape(20.dp),
                                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                                    modifier = Modifier.height(30.dp)
                                                ) {
                                                    Icon(Icons.Default.Chat, contentDescription = null, tint = RoseGoldPrimary, modifier = Modifier.size(13.dp))
                                                    Spacer(modifier = Modifier.width(4.dp))
                                                    Text("Show in chat", fontSize = 11.sp, color = RoseGoldPrimary)
                                                }
                                            }

                                            Spacer(modifier = Modifier.height(8.dp))

                                            if (msg.mediaUrl != null && msg.getTypedType() == MessageType.IMAGE) {
                                                AsyncImage(
                                                    model = msg.mediaUrl,
                                                    contentDescription = null,
                                                    contentScale = ContentScale.Crop,
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .height(140.dp)
                                                        .clip(RoundedCornerShape(10.dp))
                                                        .clickable {
                                                            selectedMediaUrl = msg.mediaUrl
                                                            selectedMessageIdForViewer = msg.id
                                                        }
                                                )
                                                if (msg.text.isNotBlank() && msg.text != "Sent a photo") {
                                                    Spacer(modifier = Modifier.height(6.dp))
                                                    Text(msg.text, fontSize = 13.sp)
                                                }
                                            } else {
                                                Text(
                                                    text = msg.text,
                                                    fontSize = 13.sp,
                                                    color = MaterialTheme.colorScheme.onSurface
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    selectedMediaUrl?.let { url ->
        FullScreenMediaViewer(
            mediaUrl = url,
            allMediaUrls = mediaMessages.mapNotNull { it.mediaUrl },
            onShowInChat = { clickedUrl ->
                val targetMsg = mediaMessages.find { it.mediaUrl == clickedUrl }
                if (targetMsg != null) {
                    onNavigateToMessage(targetMsg.id)
                }
            },
            onDismiss = {
                selectedMediaUrl = null
                selectedMessageIdForViewer = null
            }
        )
    }
}

@Composable
private fun EmptyGalleryNotice(message: String) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            lineHeight = 20.sp
        )
    }
}

private fun getDomainIcon(host: String): androidx.compose.ui.graphics.vector.ImageVector {
    val h = host.lowercase()
    return when {
        h.contains("youtube") || h.contains("youtu.be") -> Icons.Default.PlayCircle
        h.contains("instagram") -> Icons.Default.CameraAlt
        h.contains("spotify") || h.contains("music") -> Icons.Default.MusicNote
        h.contains("maps") || h.contains("goo.gl") -> Icons.Default.Place
        h.contains("drive") || h.contains("doc") -> Icons.Default.Description
        else -> Icons.Default.Language
    }
}

private fun getDomainBadgeColor(host: String): Color {
    val h = host.lowercase()
    return when {
        h.contains("youtube") || h.contains("youtu.be") -> Color(0xFFEF4444)
        h.contains("instagram") -> Color(0xFFEC4899)
        h.contains("spotify") -> Color(0xFF10B981)
        h.contains("twitter") || h.contains("x.com") -> Color(0xFF0284C7)
        h.contains("maps") -> Color(0xFFF59E0B)
        else -> Color(0xFF6366F1)
    }
}




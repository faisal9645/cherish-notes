package com.example.ui.chat

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.model.Message
import com.example.data.model.MessageStatus
import com.example.data.model.MessageType
import com.example.ui.components.WaveformView
import com.example.ui.theme.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MessageBubble(
    message: Message,
    isFromMe: Boolean,
    isPlayingAudio: Boolean,
    audioProgress: Float,
    onPlayAudio: () -> Unit,
    onImageClick: (String) -> Unit,
    onLongClick: () -> Unit,
    onReactionClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val bubbleShape = if (isFromMe) {
        RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp, bottomStart = 18.dp, bottomEnd = 4.dp)
    } else {
        RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp, bottomStart = 4.dp, bottomEnd = 18.dp)
    }

    val bubbleBg = if (isFromMe) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }

    val textColor = if (isFromMe) {
        MaterialTheme.colorScheme.onPrimary
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 3.dp),
        horizontalAlignment = if (isFromMe) Alignment.End else Alignment.Start
    ) {
        Box(
            modifier = Modifier
                .widthIn(min = 80.dp, max = 310.dp)
                .then(
                    if (isFromMe) Modifier.appGradientShadow(bubbleShape)
                    else Modifier
                )
                .clip(bubbleShape)
                .background(
                    if (isFromMe) appHorizontalGradient()
                    else androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.surfaceVariant)
                )
                .combinedClickable(
                    onClick = {
                        if (message.getTypedType() == MessageType.IMAGE && message.mediaUrl != null) {
                            onImageClick(message.mediaUrl)
                        }
                    },
                    onLongClick = onLongClick
                )
                .testTag("message_bubble_${message.id}")
        ) {
            Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                // Reply Quote Preview
                if (!message.replyToText.isNullOrEmpty()) {
                    Surface(
                        color = (if (isFromMe) Color.Black else MaterialTheme.colorScheme.surface).copy(alpha = 0.15f),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 6.dp)
                    ) {
                        Row(modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) {
                            Box(
                                modifier = Modifier
                                    .width(3.dp)
                                    .height(28.dp)
                                    .background(if (isFromMe) Color.White else MaterialTheme.colorScheme.primary, CircleShape)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Column {
                                Text(
                                    text = message.replyToSenderName ?: "Partner",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = textColor.copy(alpha = 0.9f)
                                )
                                Text(
                                    text = message.replyToText,
                                    fontSize = 12.sp,
                                    maxLines = 1,
                                    color = textColor.copy(alpha = 0.75f)
                                )
                            }
                        }
                    }
                }

                // Media Content
                when (message.getTypedType()) {
                    MessageType.IMAGE -> {
                        if (!message.mediaUrl.isNullOrEmpty()) {
                            AsyncImage(
                                model = message.mediaUrl,
                                contentDescription = "Shared photo",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(200.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .padding(bottom = 6.dp)
                            )
                        }
                    }
                    MessageType.AUDIO -> {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                        ) {
                            IconButton(
                                onClick = onPlayAudio,
                                modifier = Modifier
                                    .size(42.dp)
                                    .background(
                                        (if (isFromMe) Color.White else MaterialTheme.colorScheme.primary).copy(alpha = 0.2f),
                                        CircleShape
                                    )
                            ) {
                                Icon(
                                    imageVector = if (isPlayingAudio) Icons.Default.Pause else Icons.Default.PlayArrow,
                                    contentDescription = if (isPlayingAudio) "Pause voice message" else "Play voice message",
                                    tint = textColor,
                                    modifier = Modifier.size(24.dp)
                                )
                            }

                            Spacer(modifier = Modifier.width(8.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                WaveformView(
                                    amplitudes = message.waveform,
                                    progress = if (isPlayingAudio) audioProgress else 0f,
                                    activeColor = if (isFromMe) Color.White else MaterialTheme.colorScheme.primary,
                                    inactiveColor = textColor.copy(alpha = 0.35f),
                                    height = 24.dp
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "${message.durationSeconds}s • Voice note",
                                    fontSize = 10.sp,
                                    color = textColor.copy(alpha = 0.7f)
                                )
                            }
                        }
                    }
                    MessageType.DOCUMENT -> {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(vertical = 4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.InsertDriveFile,
                                contentDescription = null,
                                tint = textColor,
                                modifier = Modifier.size(28.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = message.mediaName ?: "Document",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = textColor
                                )
                                Text(
                                    text = "${(message.mediaSize / 1024)} KB",
                                    fontSize = 10.sp,
                                    color = textColor.copy(alpha = 0.7f)
                                )
                            }
                        }
                    }
                    else -> {}
                }

                // Text Content
                if (message.text.isNotEmpty() && message.getTypedType() != MessageType.AUDIO) {
                    Text(
                        text = message.text,
                        color = textColor,
                        style = MaterialTheme.typography.bodyMedium,
                        fontStyle = if (message.isDeleted) FontStyle.Italic else FontStyle.Normal
                    )
                }

                // Bubble Footer: Time, Status Ticks, Star, Edit Label
                Row(
                    modifier = Modifier
                        .align(Alignment.End)
                        .padding(top = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (message.isStarred) {
                        Icon(
                            imageVector = Icons.Default.Star,
                            contentDescription = "Starred",
                            tint = GoldMilestone,
                            modifier = Modifier
                                .size(12.dp)
                                .padding(end = 4.dp)
                        )
                    }

                    if (message.isEdited) {
                        Text(
                            text = "edited • ",
                            fontSize = 10.sp,
                            color = textColor.copy(alpha = 0.6f)
                        )
                    }

                    Text(
                        text = SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(message.timestamp)),
                        fontSize = 10.sp,
                        color = textColor.copy(alpha = 0.65f)
                    )

                    if (isFromMe) {
                        Spacer(modifier = Modifier.width(4.dp))
                        when (message.getTypedStatus()) {
                            MessageStatus.SENDING -> {
                                Icon(
                                    imageVector = Icons.Default.AccessTime,
                                    contentDescription = "Sending",
                                    tint = textColor.copy(alpha = 0.5f),
                                    modifier = Modifier.size(11.dp)
                                )
                            }
                            MessageStatus.SENT -> {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = "Sent",
                                    tint = textColor.copy(alpha = 0.7f),
                                    modifier = Modifier.size(12.dp)
                                )
                            }
                            MessageStatus.DELIVERED -> {
                                Icon(
                                    imageVector = Icons.Default.DoneAll,
                                    contentDescription = "Delivered",
                                    tint = textColor.copy(alpha = 0.75f),
                                    modifier = Modifier.size(13.dp)
                                )
                            }
                            MessageStatus.READ -> {
                                Icon(
                                    imageVector = Icons.Default.DoneAll,
                                    contentDescription = "Read",
                                    tint = Color.White,
                                    modifier = Modifier.size(13.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        // Emoji reactions pill below bubble
        if (message.reactions.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .offset(y = (-6).dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surface)
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape)
                    .padding(horizontal = 6.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val emojiCounts = message.reactions.values.groupingBy { it }.eachCount()
                for ((emoji, count) in emojiCounts) {
                    Text(
                        text = "$emoji $count",
                        fontSize = 11.sp,
                        modifier = Modifier.combinedClickable(
                            onClick = { onReactionClick(emoji) },
                            onLongClick = onLongClick
                        )
                    )
                }
            }
        }
    }
}

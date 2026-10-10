package com.example.ui.chat

import android.app.Activity
import android.content.Context
import android.content.pm.ActivityInfo
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.model.WatchPartySession
import kotlinx.coroutines.delay

/**
 * Synced Ambient Listening / Watch Party Modal
 *
 * Sync playback for YouTube videos or music links with shared pause/play
 * and synchronized progress between the couple.
 */
@Composable
fun SyncedWatchPartyModal(
    session: WatchPartySession,
    onPlayPause: (Boolean, Float) -> Unit,
    onSeek: (Float) -> Unit,
    onEndSession: () -> Unit,
    onDismiss: () -> Unit
) {
    BackHandler(onBack = onDismiss)
    val context = LocalContext.current
    val app = context.applicationContext as? com.example.CherishApplication

    var currentProgress by remember(session.positionSeconds) { mutableFloatStateOf(session.positionSeconds) }
    var isSeeking by remember { mutableStateOf(false) }

    // Progress tick while playing
    LaunchedEffect(session.isPlaying, isSeeking) {
        if (session.isPlaying && !isSeeking) {
            while (true) {
                delay(1000)
                currentProgress += 1f
            }
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = false
        )
    ) {
        Surface(
            color = Color(0xFF0F0B1E),
            modifier = Modifier.fillMaxSize()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .systemBarsPadding()
            ) {
                // Top Header Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = CircleShape,
                            color = Color(0xFFEC4899).copy(alpha = 0.2f),
                            modifier = Modifier.size(36.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Movie,
                                    contentDescription = null,
                                    tint = Color(0xFFEC4899),
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "Synced Watch Party",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = if (session.isPlaying) Color(0xFF10B981) else Color(0xFFF59E0B)
                                ) {
                                    Text(
                                        text = if (session.isPlaying) "SYNCED • PLAYING" else "PAUSED",
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = Color.White,
                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                    )
                                }
                            }
                            Text(
                                text = session.title.ifBlank { "Ambient Listening with Partner 💕" },
                                fontSize = 12.sp,
                                color = Color.White.copy(alpha = 0.7f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.12f))
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
                    }
                }

                // Video / Ambient Player Area
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .background(Color.Black),
                    contentAlignment = Alignment.Center
                ) {
                    if (session.videoId.isNotBlank()) {
                        YouTubeWebView(
                            videoId = session.videoId,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Headphones,
                                contentDescription = null,
                                tint = Color(0xFFEC4899),
                                modifier = Modifier.size(64.dp)
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "Synced Ambient Audio",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color.White
                            )
                            Text(
                                text = "Listening together in synchronized harmony ✨",
                                fontSize = 13.sp,
                                color = Color.White.copy(alpha = 0.6f)
                            )
                        }
                    }
                }

                // Bottom Synced Control Deck
                Surface(
                    color = Color(0xFF16112C),
                    tonalElevation = 6.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 16.dp)
                    ) {
                        // Synced Progress Slider
                        val minutes = (currentProgress / 60).toInt()
                        val seconds = (currentProgress % 60).toInt()
                        val timeStr = String.format("%02d:%02d", minutes, seconds)

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = timeStr,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color.White.copy(alpha = 0.8f)
                            )
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Default.Sync,
                                    contentDescription = null,
                                    tint = Color(0xFF38BDF8),
                                    modifier = Modifier.size(13.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "Both phones in sync",
                                    fontSize = 11.sp,
                                    color = Color(0xFF38BDF8),
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }

                        Slider(
                            value = currentProgress,
                            onValueChange = {
                                isSeeking = true
                                currentProgress = it
                            },
                            onValueChangeFinished = {
                                isSeeking = false
                                onSeek(currentProgress)
                            },
                            valueRange = 0f..600f,
                            colors = SliderDefaults.colors(
                                thumbColor = Color(0xFFEC4899),
                                activeTrackColor = Color(0xFFEC4899),
                                inactiveTrackColor = Color.White.copy(alpha = 0.2f)
                            ),
                            modifier = Modifier.fillMaxWidth().testTag("watch_party_progress_slider")
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        // Controls Row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Rewind 10s
                            IconButton(
                                onClick = {
                                    val newPos = (currentProgress - 10f).coerceAtLeast(0f)
                                    currentProgress = newPos
                                    onSeek(newPos)
                                }
                            ) {
                                Icon(Icons.Default.Replay10, contentDescription = "Rewind 10s", tint = Color.White)
                            }

                            // Shared Play / Pause Primary Button
                            FilledIconButton(
                                onClick = {
                                    val nextPlayState = !session.isPlaying
                                    onPlayPause(nextPlayState, currentProgress)
                                },
                                colors = IconButtonDefaults.filledIconButtonColors(containerColor = Color(0xFFEC4899)),
                                modifier = Modifier.size(56.dp).testTag("watch_party_play_pause_btn")
                            ) {
                                Icon(
                                    imageVector = if (session.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                    contentDescription = if (session.isPlaying) "Pause" else "Play",
                                    tint = Color.White,
                                    modifier = Modifier.size(32.dp)
                                )
                            }

                            // Forward 10s
                            IconButton(
                                onClick = {
                                    val newPos = currentProgress + 10f
                                    currentProgress = newPos
                                    onSeek(newPos)
                                }
                            ) {
                                Icon(Icons.Default.Forward10, contentDescription = "Forward 10s", tint = Color.White)
                            }

                            // Leave / End Party
                            TextButton(
                                onClick = {
                                    onEndSession()
                                    onDismiss()
                                }
                            ) {
                                Text("End Party", color = Color(0xFFEF4444), fontSize = 13.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}

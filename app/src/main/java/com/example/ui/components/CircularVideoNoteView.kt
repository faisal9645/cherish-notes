package com.example.ui.components

import android.graphics.Bitmap
import android.media.MediaPlayer
import android.net.Uri
import android.widget.VideoView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.ui.theme.RoseGoldPrimary
import com.example.util.VideoThumbnailHelper
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

@Composable
fun CircularVideoNoteView(
    videoUrl: String,
    durationSeconds: Int = 0,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var isPlaying by remember { mutableStateOf(false) }
    var isMuted by remember { mutableStateOf(false) }
    var progress by remember { mutableFloatStateOf(0f) }
    var videoViewRef by remember { mutableStateOf<VideoView?>(null) }
    var mediaPlayerRef by remember { mutableStateOf<MediaPlayer?>(null) }
    var isVideoReady by remember { mutableStateOf(false) }
    var thumbnailBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var videoWidth by remember { mutableIntStateOf(0) }
    var videoHeight by remember { mutableIntStateOf(0) }

    LaunchedEffect(videoUrl) {
        if (videoUrl.isNotBlank()) {
            thumbnailBitmap = VideoThumbnailHelper.getThumbnail(context, videoUrl)
        }
    }

    // Progress tracker
    LaunchedEffect(isPlaying) {
        while (isActive && isPlaying) {
            videoViewRef?.let { vv ->
                val cur = vv.currentPosition
                val dur = vv.duration
                if (dur > 0) {
                    progress = (cur.toFloat() / dur.toFloat()).coerceIn(0f, 1f)
                }
            }
            delay(50)
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            try {
                videoViewRef?.stopPlayback()
            } catch (_: Exception) {}
        }
    }

    val scaleFactor = remember(videoWidth, videoHeight) {
        if (videoWidth > 0 && videoHeight > 0) {
            val aspect = videoWidth.toFloat() / videoHeight.toFloat()
            // To ensure CenterCrop inside a square without ANY stretch:
            val scaleX = if (aspect < 1f) (1f / aspect) else 1.0f
            val scaleY = if (aspect > 1f) aspect else 1.0f
            Pair(scaleX, scaleY)
        } else {
            Pair(1.15f, 1.15f)
        }
    }

    Box(
        modifier = modifier
            .size(260.dp)
            .clip(CircleShape)
            .background(Color.Black)
            .border(3.dp, RoseGoldPrimary.copy(alpha = 0.5f), CircleShape)
            .clickable {
                videoViewRef?.let { vv ->
                    if (vv.isPlaying) {
                        vv.pause()
                        isPlaying = false
                    } else {
                        vv.start()
                        isPlaying = true
                    }
                }
            },
        contentAlignment = Alignment.Center
    ) {
        // Video View surface with center-crop to prevent any stretching
        AndroidView(
            factory = { ctx ->
                VideoView(ctx).apply {
                    setVideoURI(Uri.parse(videoUrl))
                    setOnPreparedListener { mp ->
                        mediaPlayerRef = mp
                        mp.isLooping = true
                        try {
                            mp.setVideoScalingMode(MediaPlayer.VIDEO_SCALING_MODE_SCALE_TO_FIT_WITH_CROPPING)
                        } catch (_: Exception) {}
                        videoWidth = mp.videoWidth
                        videoHeight = mp.videoHeight
                        isVideoReady = true
                        if (isMuted) {
                            mp.setVolume(0f, 0f)
                        } else {
                            mp.setVolume(1f, 1f)
                        }
                    }
                    setOnCompletionListener {
                        progress = 0f
                    }
                    videoViewRef = this
                }
            },
            update = { vv ->
                videoViewRef = vv
            },
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = scaleFactor.first
                    scaleY = scaleFactor.second
                }
                .clip(CircleShape)
        )

        // Thumbnail Poster Frame: Displays immediately so circle video is NEVER blank
        if (!isPlaying || !isVideoReady) {
            val bmp = thumbnailBitmap
            if (bmp != null) {
                Image(
                    bitmap = bmp.asImageBitmap(),
                    contentDescription = "Video note thumbnail",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(CircleShape)
                )
            } else {
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(videoUrl)
                        .crossfade(true)
                        .build(),
                    contentDescription = "Video note thumbnail",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(CircleShape)
                )
            }
        }

        // Circular progress ring around the edge
        val ringColor = RoseGoldPrimary
        Canvas(modifier = Modifier.fillMaxSize().padding(1.5.dp)) {
            val strokeWidth = 3.5.dp.toPx()
            // Background track
            drawCircle(
                color = Color.White.copy(alpha = 0.2f),
                radius = (size.minDimension - strokeWidth) / 2f,
                style = Stroke(width = strokeWidth)
            )
            // Active Progress
            if (progress > 0.005f) {
                drawArc(
                    color = ringColor,
                    startAngle = -90f,
                    sweepAngle = progress * 360f,
                    useCenter = false,
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                )
            }
        }

        // Center Play / Pause Indicator overlay
        AnimatedVisibility(
            visible = !isPlaying,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Surface(
                shape = CircleShape,
                color = Color.Black.copy(alpha = 0.55f),
                modifier = Modifier.size(56.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = "Play circular video note",
                        tint = Color.White,
                        modifier = Modifier.size(36.dp)
                    )
                }
            }
        }

        // Top right mute/unmute toggle
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(12.dp)
        ) {
            Surface(
                shape = CircleShape,
                color = Color.Black.copy(alpha = 0.6f),
                modifier = Modifier
                    .size(32.dp)
                    .clickable {
                        isMuted = !isMuted
                        mediaPlayerRef?.let { mp ->
                            if (isMuted) mp.setVolume(0f, 0f) else mp.setVolume(1f, 1f)
                        }
                    }
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = if (isMuted) Icons.AutoMirrored.Filled.VolumeOff else Icons.AutoMirrored.Filled.VolumeUp,
                        contentDescription = "Mute audio",
                        tint = Color.White,
                        modifier = Modifier.size(17.dp)
                    )
                }
            }
        }

        // Bottom duration pill
        if (durationSeconds > 0) {
            Surface(
                shape = CircleShape,
                color = Color.Black.copy(alpha = 0.65f),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 10.dp)
            ) {
                Text(
                    text = String.format(java.util.Locale.US, "%d:%02d", durationSeconds / 60, durationSeconds % 60),
                    fontSize = 11.sp,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 2.5.dp)
                )
            }
        }
    }
}

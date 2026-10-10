package com.example.ui.components

import android.graphics.Bitmap
import android.graphics.SurfaceTexture
import android.media.MediaPlayer
import android.net.Uri
import android.util.Log
import android.view.Surface
import android.view.TextureView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.OpenInFull
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
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
import com.example.ui.theme.RoseGoldPrimary
import com.example.util.VideoThumbnailHelper
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import java.io.File

/** Only one video note plays at a time: starting one stops (and releases) the others. */
private object VideoNotePlayback {
    var activeKey by mutableStateOf<Any?>(null)
}

/** Stops whichever video note is playing (each goes back to its still frame), e.g. when the chat hides. */
fun stopAllVideoNotes() {
    VideoNotePlayback.activeKey = null
}

@Composable
fun CircularVideoNoteView(
    videoUrl: String,
    durationSeconds: Int = 0,
    autoPlay: Boolean = false,
    onExpandClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    // CircleShape for round video notes; a rounded rectangle for shared videos
    shape: androidx.compose.ui.graphics.Shape = CircleShape,
    // Fill the shape (cropping the edges), or show the whole picture with bars
    cropToFill: Boolean = true
) {
    val isRound = shape == CircleShape
    val context = LocalContext.current
    var playerRequested by remember { mutableStateOf(autoPlay) }
    var isPlaying by remember { mutableStateOf(autoPlay) }
    var shouldPlayWhenReady by remember { mutableStateOf(autoPlay) }
    var isMuted by remember { mutableStateOf(false) }
    var progress by remember { mutableFloatStateOf(0f) }
    var mediaPlayerRef by remember { mutableStateOf<MediaPlayer?>(null) }
    var isVideoReady by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }
    var thumbnailBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var videoWidth by remember { mutableIntStateOf(0) }
    var videoHeight by remember { mutableIntStateOf(0) }
    val playbackKey = remember { Any() }

    fun releasePlayer() {
        try {
            mediaPlayerRef?.pause()
        } catch (_: Exception) {}
        playerRequested = false
        isPlaying = false
        shouldPlayWhenReady = false
        isVideoReady = false
        isLoading = false
        progress = 0f
    }

    fun startPlayback() {
        VideoNotePlayback.activeKey = playbackKey
        playerRequested = true
        shouldPlayWhenReady = true
        isPlaying = true
    }

    // Another note started playing: let this one go back to its still frame
    LaunchedEffect(playbackKey) {
        if (autoPlay) VideoNotePlayback.activeKey = playbackKey
        snapshotFlow { VideoNotePlayback.activeKey === playbackKey }
            .collect { isActiveNote -> if (!isActiveNote && playerRequested) releasePlayer() }
    }
    DisposableEffect(playbackKey) {
        onDispose {
            if (VideoNotePlayback.activeKey === playbackKey) VideoNotePlayback.activeKey = null
        }
    }

    // Extract thumbnail and ensure local caching for smooth instant playback
    LaunchedEffect(videoUrl) {
        if (videoUrl.isNotBlank()) {
            val bitmap = VideoThumbnailHelper.getThumbnail(context, videoUrl)
            if (bitmap != null) {
                thumbnailBitmap = bitmap
            }
        }
    }

    // Toggle play / pause helper
    val togglePlayPause: () -> Unit = {
        if (!playerRequested) {
            startPlayback()
        } else {
            mediaPlayerRef?.let { mp ->
                try {
                    if (mp.isPlaying) {
                        mp.pause()
                        isPlaying = false
                        shouldPlayWhenReady = false
                    } else {
                        VideoNotePlayback.activeKey = playbackKey
                        mp.start()
                        isPlaying = true
                        shouldPlayWhenReady = true
                    }
                } catch (e: Exception) {
                    Log.w("CircularVideoNoteView", "Toggle play error", e)
                    isPlaying = !isPlaying
                    shouldPlayWhenReady = isPlaying
                }
            } ?: run {
                isPlaying = !isPlaying
                shouldPlayWhenReady = isPlaying
            }
        }
    }

    // Progress tracker
    LaunchedEffect(isPlaying, isVideoReady) {
        while (isActive && isPlaying) {
            mediaPlayerRef?.let { mp ->
                try {
                    if (mp.isPlaying) {
                        val cur = mp.currentPosition
                        val dur = mp.duration
                        if (dur > 0) {
                            progress = (cur.toFloat() / dur.toFloat()).coerceIn(0f, 1f)
                        }
                    }
                } catch (_: Exception) {}
            }
            delay(50)
        }
    }

    val cropScale = remember(videoWidth, videoHeight, cropToFill) {
        if (!cropToFill) {
            1f
        } else if (videoWidth > 0 && videoHeight > 0) {
            val aspect = videoWidth.toFloat() / videoHeight.toFloat()
            if (aspect < 1f) 1f / aspect else aspect
        } else {
            1.15f
        }
    }

    Box(
        modifier = modifier
            .then(if (isRound) Modifier.defaultMinSize(minWidth = 220.dp, minHeight = 220.dp) else Modifier)
            .clip(shape)
            .background(Color(0xFF1E1A26))
            .then(if (isRound) Modifier.border(3.5.dp, RoseGoldPrimary.copy(alpha = 0.65f), CircleShape) else Modifier)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) {
                if (onExpandClick != null) {
                    onExpandClick()
                } else {
                    togglePlayPause()
                }
            },
        contentAlignment = Alignment.Center
    ) {
        // TextureView surface with center-crop: works properly with Compose clip(CircleShape)
        if (playerRequested) {
            AndroidView(
                factory = { ctx ->
                    val textureView = TextureView(ctx)
                    var activeSurface: Surface? = null

                    textureView.surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                        override fun onSurfaceTextureAvailable(st: SurfaceTexture, width: Int, height: Int) {
                            try {
                                activeSurface?.release()
                                val surface = Surface(st)
                                activeSurface = surface
                                mediaPlayerRef?.release()

                                val mp = MediaPlayer().apply {
                                    setSurface(surface)
                                    isLooping = true
                                    val cacheFile = VideoThumbnailHelper.getVideoCacheFile(ctx, videoUrl)
                                    if (cacheFile.exists() && cacheFile.length() > 1024) {
                                        setDataSource(cacheFile.absolutePath)
                                    } else if (videoUrl.startsWith("content://")) {
                                        setDataSource(ctx, Uri.parse(videoUrl))
                                    } else if (videoUrl.startsWith("file://") || videoUrl.startsWith("/")) {
                                        val f = File(videoUrl.removePrefix("file://"))
                                        if (f.exists()) setDataSource(f.absolutePath) else setDataSource(ctx, Uri.parse(videoUrl))
                                    } else {
                                        setDataSource(videoUrl)
                                    }

                                    if (isMuted) setVolume(0f, 0f) else setVolume(1f, 1f)
                                    setOnPreparedListener { preparedMp ->
                                        mediaPlayerRef = preparedMp
                                        videoWidth = preparedMp.videoWidth
                                        videoHeight = preparedMp.videoHeight
                                        isVideoReady = true
                                        isLoading = false
                                        if (shouldPlayWhenReady || isPlaying) {
                                            try {
                                                preparedMp.start()
                                                isPlaying = true
                                            } catch (e: Exception) {
                                                Log.w("CircularVideoNoteView", "Error starting MediaPlayer", e)
                                            }
                                        }
                                    }
                                    setOnErrorListener { _, what, extra ->
                                        Log.w("CircularVideoNoteView", "MediaPlayer error: what=$what, extra=$extra")
                                        isVideoReady = false
                                        isPlaying = false
                                        isLoading = false
                                        true
                                    }
                                    setOnCompletionListener {
                                        progress = 0f
                                    }
                                    isLoading = true
                                    prepareAsync()
                                }
                                mediaPlayerRef = mp
                            } catch (e: Exception) {
                                Log.w("CircularVideoNoteView", "Failed setting up TextureView MediaPlayer", e)
                                isLoading = false
                            }
                        }

                        override fun onSurfaceTextureSizeChanged(st: SurfaceTexture, width: Int, height: Int) {}

                        override fun onSurfaceTextureDestroyed(st: SurfaceTexture): Boolean {
                            try {
                                mediaPlayerRef?.stop()
                                mediaPlayerRef?.release()
                            } catch (_: Exception) {}
                            mediaPlayerRef = null
                            activeSurface?.release()
                            activeSurface = null
                            isVideoReady = false
                            return true
                        }

                        override fun onSurfaceTextureUpdated(st: SurfaceTexture) {}
                    }
                    textureView
                },
                update = {
                    mediaPlayerRef?.let { mp ->
                        if (isMuted) mp.setVolume(0f, 0f) else mp.setVolume(1f, 1f)
                    }
                },
                onRelease = { tv ->
                    try {
                        tv.surfaceTextureListener = null
                        mediaPlayerRef?.stop()
                        mediaPlayerRef?.release()
                    } catch (_: Exception) {}
                    mediaPlayerRef = null
                    isVideoReady = false
                    isLoading = false
                },
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = cropScale
                        scaleY = cropScale
                    }
                    .clip(shape)
            )
        }

        // Thumbnail Poster Frame: shown until video is actively ready and playing
        if (!isPlaying || !isVideoReady) {
            val bmp = thumbnailBitmap
            if (bmp != null) {
                Image(
                    bitmap = bmp.asImageBitmap(),
                    contentDescription = "Video note thumbnail",
                    contentScale = if (cropToFill) ContentScale.Crop else ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(shape)
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.radialGradient(
                                colors = listOf(
                                    Color(0xFF2E2638),
                                    Color(0xFF14101A)
                                )
                            )
                        )
                        .clip(shape),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        color = RoseGoldPrimary,
                        strokeWidth = 2.5.dp,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }
        }

        // Circular progress ring around the edge (round notes only)
        val ringColor = RoseGoldPrimary
        if (isRound) Canvas(modifier = Modifier.fillMaxSize().padding(2.dp)) {
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

        // Center Play / Pause / Loading Button
        Surface(
            shape = CircleShape,
            color = Color.Black.copy(alpha = if (isPlaying && isVideoReady) 0.35f else 0.65f),
            modifier = Modifier
                .size(56.dp)
                .clickable {
                    togglePlayPause()
                }
        ) {
            Box(contentAlignment = Alignment.Center) {
                if (isLoading) {
                    CircularProgressIndicator(
                        color = RoseGoldPrimary,
                        strokeWidth = 2.5.dp,
                        modifier = Modifier.size(28.dp)
                    )
                } else {
                    Icon(
                        imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (isPlaying) "Pause video note" else "Play video note",
                        tint = Color.White,
                        modifier = Modifier.size(34.dp)
                    )
                }
            }
        }

        // Top-left "Make Big" / Expand icon button
        if (onExpandClick != null) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(12.dp)
            ) {
                Surface(
                    shape = CircleShape,
                    color = Color.Black.copy(alpha = 0.6f),
                    modifier = Modifier
                        .size(32.dp)
                        .clickable { onExpandClick() }
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.OpenInFull,
                            contentDescription = "Make circle video big",
                            tint = Color.White,
                            modifier = Modifier.size(17.dp)
                        )
                    }
                }
            }
        }

        // Top-right mute/unmute toggle
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
                            try {
                                if (isMuted) mp.setVolume(0f, 0f) else mp.setVolume(1f, 1f)
                            } catch (_: Exception) {}
                        }
                    }
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = if (isMuted) Icons.AutoMirrored.Filled.VolumeOff else Icons.AutoMirrored.Filled.VolumeUp,
                        contentDescription = if (isMuted) "Unmute audio" else "Mute audio",
                        tint = Color.White,
                        modifier = Modifier.size(17.dp)
                    )
                }
            }
        }

        // Bottom duration pill (safely inset so it is never clipped by the circular border)
        if (durationSeconds > 0) {
            Surface(
                shape = CircleShape,
                color = Color.Black.copy(alpha = 0.65f),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 14.dp)
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

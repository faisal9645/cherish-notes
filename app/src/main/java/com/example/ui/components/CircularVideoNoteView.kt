package com.example.ui.components

import android.graphics.Bitmap
import android.graphics.SurfaceTexture
import android.media.MediaPlayer
import android.net.Uri
import android.util.Log
import android.view.Surface
import android.view.TextureView
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeOff
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.OpenInFull
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.example.util.VideoThumbnailHelper
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import java.io.File
import com.example.ui.theme.Blue300
import com.example.ui.theme.Blue500
import com.example.ui.theme.NavyCard
import com.example.ui.theme.Slate900

/** Only one video note plays at a time: starting one stops (and releases) the others. */
private object VideoNotePlayback {
    var activeKey by mutableStateOf<Any?>(null)
}

/** Stops whichever video note is playing (each goes back to its still frame), e.g. when the chat hides. */
fun stopAllVideoNotes() {
    VideoNotePlayback.activeKey = null
}

private val BlueCircularBorder = Blue500

@Composable
fun CircularVideoNoteView(
    videoUrl: String,
    durationSeconds: Int = 0,
    autoPlay: Boolean = false,
    onExpandClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    onDoubleClick: (() -> Unit)? = null,
    onPlayingChanged: ((Boolean) -> Unit)? = null,
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
    var isCompleted by remember { mutableStateOf(false) }
    var isMuted by remember { mutableStateOf(false) }
    var isLooping by remember { mutableStateOf(false) }
    var progress by remember { mutableFloatStateOf(0f) }
    var mediaPlayerRef by remember { mutableStateOf<MediaPlayer?>(null) }
    var isVideoReady by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }
    var hasError by remember { mutableStateOf(false) }
    var thumbnailBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var videoWidth by remember { mutableIntStateOf(0) }
    var videoHeight by remember { mutableIntStateOf(0) }
    var videoAspect by remember { mutableFloatStateOf(1f) }
    var effectiveDurationSeconds by remember { mutableIntStateOf(durationSeconds) }
    var localVideoFile by remember { mutableStateOf<File?>(null) }
    val scope = rememberCoroutineScope()
    val playbackKey = remember { Any() }

    val activelyPlaying = isPlaying && isVideoReady
    LaunchedEffect(activelyPlaying) {
        onPlayingChanged?.invoke(activelyPlaying)
    }

    LaunchedEffect(isLooping) {
        try {
            mediaPlayerRef?.isLooping = isLooping
        } catch (_: Exception) {}
    }

    fun releasePlayer() {
        try {
            mediaPlayerRef?.stop()
            mediaPlayerRef?.release()
        } catch (_: Exception) {}
        mediaPlayerRef = null
        playerRequested = false
        isPlaying = false
        shouldPlayWhenReady = false
        isVideoReady = false
        isLoading = false
        isCompleted = false
        progress = 0f
        onPlayingChanged?.invoke(false)
    }

    fun startPlayback() {
        hasError = false
        isCompleted = false
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

    // Lifecycle observer: pause video when app goes to background
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE || event == Lifecycle.Event.ON_STOP) {
                try {
                    mediaPlayerRef?.let { mp ->
                        if (mp.isPlaying) {
                            mp.pause()
                            isPlaying = false
                            shouldPlayWhenReady = false
                        }
                    }
                } catch (_: Exception) {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    DisposableEffect(playbackKey) {
        onDispose {
            onPlayingChanged?.invoke(false)
            if (VideoNotePlayback.activeKey === playbackKey) {
                VideoNotePlayback.activeKey = null
            }
            try {
                mediaPlayerRef?.stop()
                mediaPlayerRef?.release()
            } catch (_: Exception) {}
            mediaPlayerRef = null
            isVideoReady = false
            isLoading = false
        }
    }

    // Extract thumbnail, metadata, and ensure local caching for smooth instant playback
    LaunchedEffect(videoUrl) {
        if (videoUrl.isNotBlank()) {
            val cachedInfo = VideoThumbnailHelper.getVideoInfo(videoUrl)
            if (cachedInfo != null) {
                videoAspect = cachedInfo.aspect
                if (effectiveDurationSeconds <= 0 && cachedInfo.durationSeconds > 0) {
                    effectiveDurationSeconds = cachedInfo.durationSeconds
                }
            }

            val initialCached = withContext(Dispatchers.IO) {
                val c = VideoThumbnailHelper.getVideoCacheFile(context, videoUrl)
                if (c.exists() && c.length() > 1024) c
                else if (videoUrl.startsWith("file://") || (!videoUrl.startsWith("http") && !videoUrl.startsWith("content:") && !videoUrl.startsWith("data:"))) {
                    val f = File(videoUrl.removePrefix("file://"))
                    if (f.exists() && f.length() > 1024) f else null
                } else null
            }
            if (initialCached != null) {
                localVideoFile = initialCached
            }

            val bitmap = VideoThumbnailHelper.getThumbnail(context, videoUrl)
            if (bitmap != null) {
                thumbnailBitmap = bitmap
                if (bitmap.height > 0) {
                    videoAspect = bitmap.width.toFloat() / bitmap.height.toFloat()
                }
            }

            val postInfo = VideoThumbnailHelper.getVideoInfo(videoUrl)
            if (postInfo != null) {
                videoAspect = postInfo.aspect
                if (effectiveDurationSeconds <= 0 && postInfo.durationSeconds > 0) {
                    effectiveDurationSeconds = postInfo.durationSeconds
                }
            }

            val file = withContext(Dispatchers.IO) {
                VideoThumbnailHelper.getCachedFile(context, videoUrl)
            }
            if (file != null && file.exists()) {
                localVideoFile = file
            }
        }
    }

    // Toggle play / pause helper
    val togglePlayPause: () -> Unit = {
        if (isCompleted) {
            isCompleted = false
            progress = 0f
            mediaPlayerRef?.let { mp ->
                try {
                    VideoNotePlayback.activeKey = playbackKey
                    mp.seekTo(0)
                    mp.start()
                    isPlaying = true
                    shouldPlayWhenReady = true
                } catch (e: Exception) {
                    Log.w("CircularVideoNoteView", "Replay error", e)
                    startPlayback()
                }
            } ?: run {
                startPlayback()
            }
        } else if (!playerRequested || mediaPlayerRef == null) {
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
                    startPlayback()
                }
            } ?: run {
                startPlayback()
            }
        }
    }

    // Progress tracker from actual playback position
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

    // Precise center-crop aspect ratio scaling:
    // TextureView fills the container (mapping pixels to width & height).
    // For a circular/square presentation, applying scaleX / scaleY according to videoAspect
    // perfectly un-squishes the video to its natural aspect ratio, matching ContentScale.Crop on the thumbnail.
    // This eliminates the previous double-scaling that caused extreme face zoom.
    val scaleX = remember(videoAspect, cropToFill) {
        if (!cropToFill) {
            if (videoAspect > 1f) 1f else videoAspect.coerceAtLeast(0.01f)
        } else {
            if (videoAspect > 1f) videoAspect else 1f
        }
    }
    val scaleY = remember(videoAspect, cropToFill) {
        if (!cropToFill) {
            if (videoAspect < 1f) 1f else 1f / videoAspect.coerceAtLeast(0.01f)
        } else {
            if (videoAspect < 1f) 1f / videoAspect.coerceAtLeast(0.01f) else 1f
        }
    }

    val currentOnLongClick by rememberUpdatedState(onLongClick)
    val currentOnDoubleClick by rememberUpdatedState(onDoubleClick)
    val currentTogglePlayPause by rememberUpdatedState(togglePlayPause)

    Box(
        modifier = modifier
            .then(if (isRound) Modifier.defaultMinSize(minWidth = 220.dp, minHeight = 220.dp) else Modifier)
            .clip(shape)
            .background(Slate900)
            .pointerInput(Unit) {
                detectTapGestures(
                    onLongPress = {
                        currentOnLongClick?.invoke()
                    },
                    onDoubleTap = {
                        currentOnDoubleClick?.invoke()
                    },
                    onTap = {
                        currentTogglePlayPause()
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        // TextureView surface with precise aspect preservation matching thumbnail ContentScale.Crop
        if (playerRequested) {
            AndroidView(
                factory = { ctx ->
                    val textureView = TextureView(ctx)
                    var activeSurface: Surface? = null

                    fun setupAndPlay(mp: MediaPlayer, source: String) {
                        try {
                            mp.reset()
                            activeSurface?.let { mp.setSurface(it) }
                            mp.isLooping = isLooping
                            if (source.startsWith("http://") || source.startsWith("https://") || source.startsWith("content://")) {
                                mp.setDataSource(ctx, Uri.parse(source))
                            } else {
                                val path = source.removePrefix("file://")
                                mp.setDataSource(path)
                            }
                            if (isMuted) mp.setVolume(0f, 0f) else mp.setVolume(1f, 1f)
                            mp.setOnPreparedListener { preparedMp ->
                                mediaPlayerRef = preparedMp
                                videoWidth = preparedMp.videoWidth
                                videoHeight = preparedMp.videoHeight
                                if (thumbnailBitmap == null && videoWidth > 0 && videoHeight > 0) {
                                    videoAspect = videoWidth.toFloat() / videoHeight.toFloat()
                                }
                                if (effectiveDurationSeconds <= 0 && preparedMp.duration > 0) {
                                    effectiveDurationSeconds = (preparedMp.duration / 1000).coerceAtLeast(1)
                                }
                                isVideoReady = true
                                isLoading = false
                                hasError = false
                                if (shouldPlayWhenReady || isPlaying) {
                                    try {
                                        preparedMp.start()
                                        isPlaying = true
                                    } catch (e: Exception) {
                                        Log.w("CircularVideoNoteView", "Error starting MediaPlayer", e)
                                    }
                                }
                            }
                            mp.setOnErrorListener { _, what, extra ->
                                Log.w("CircularVideoNoteView", "MediaPlayer error: what=$what, extra=$extra")
                                isVideoReady = false
                                isPlaying = false
                                isLoading = false
                                hasError = true
                                true
                            }
                            mp.setOnCompletionListener {
                                if (isLooping) {
                                    try {
                                        mp.seekTo(0)
                                        mp.start()
                                        isPlaying = true
                                        isCompleted = false
                                    } catch (_: Exception) {
                                        isPlaying = false
                                        isCompleted = true
                                        progress = 1f
                                    }
                                } else {
                                    isPlaying = false
                                    isCompleted = true
                                    progress = 1f
                                }
                            }
                            isLoading = true
                            mp.prepareAsync()
                        } catch (e: Exception) {
                            Log.w("CircularVideoNoteView", "Setup MediaPlayer error", e)
                            isLoading = false
                            hasError = true
                        }
                    }

                    textureView.surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                        override fun onSurfaceTextureAvailable(st: SurfaceTexture, width: Int, height: Int) {
                            try {
                                activeSurface?.release()
                                val surface = Surface(st)
                                activeSurface = surface
                                mediaPlayerRef?.release()

                                val mp = MediaPlayer()
                                mediaPlayerRef = mp

                                val readyFile = localVideoFile
                                if (readyFile != null && readyFile.exists() && readyFile.length() > 1024) {
                                    setupAndPlay(mp, readyFile.absolutePath)
                                } else {
                                    isLoading = true
                                    scope.launch(Dispatchers.IO) {
                                        val f = VideoThumbnailHelper.getCachedFile(ctx, videoUrl)
                                        withContext(Dispatchers.Main) {
                                            if (activeSurface != null) {
                                                if (f != null && f.exists()) {
                                                    localVideoFile = f
                                                    setupAndPlay(mp, f.absolutePath)
                                                } else {
                                                    setupAndPlay(mp, videoUrl)
                                                }
                                            } else {
                                                isLoading = false
                                            }
                                        }
                                    }
                                }
                            } catch (e: Exception) {
                                Log.w("CircularVideoNoteView", "Failed setting up TextureView MediaPlayer", e)
                                isLoading = false
                                hasError = true
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
                        this.scaleX = scaleX
                        this.scaleY = scaleY
                        transformOrigin = TransformOrigin.Center
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
                                    NavyCard,
                                    Slate900
                                )
                            )
                        )
                        .clip(shape),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        color = BlueCircularBorder,
                        strokeWidth = 2.5.dp,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }
        }

        // Blue Circular Outline & Unified Synchronized Progress Ring
        if (isRound) {
            Canvas(modifier = Modifier.fillMaxSize().padding(1.5.dp)) {
                val strokeWidth = 3.dp.toPx()
                val radius = (size.minDimension - strokeWidth) / 2f
                // 1. Prominent blue circular ring outline around circumference by default
                drawCircle(
                    color = BlueCircularBorder,
                    radius = radius,
                    style = Stroke(width = strokeWidth)
                )
                // 2. Synchronized active playback progress ring along the exact same circumference
                if (progress > 0.002f) {
                    drawArc(
                        color = Blue300,
                        startAngle = -90f,
                        sweepAngle = (progress * 360f).coerceIn(0f, 360f),
                        useCenter = false,
                        style = Stroke(width = strokeWidth + 0.8f.dp.toPx(), cap = StrokeCap.Round)
                    )
                }
            }
        }

        // Center Play / Pause / Loading Visual Overlay
        // Hidden when video is actively playing, shown when paused, loading, or retry
        if (!isPlaying || !isVideoReady || isLoading || hasError) {
            Surface(
                shape = CircleShape,
                color = Color.Black.copy(alpha = 0.65f),
                modifier = Modifier.size(56.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            color = BlueCircularBorder,
                            strokeWidth = 2.5.dp,
                            modifier = Modifier.size(28.dp)
                        )
                    } else if (hasError) {
                        Icon(
                            imageVector = Icons.Rounded.Refresh,
                            contentDescription = "Retry playback",
                            tint = Color.White,
                            modifier = Modifier.size(32.dp)
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Rounded.PlayArrow,
                            contentDescription = "Play video note",
                            tint = Color.White,
                            modifier = Modifier.size(34.dp)
                        )
                    }
                }
            }
        }

        // Top-left "Make Big" / Expand icon button (safely inset within circle curve)
        if (onExpandClick != null) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(
                        top = if (isRound) 30.dp else 12.dp,
                        start = if (isRound) 30.dp else 12.dp
                    )
            ) {
                Surface(
                    shape = CircleShape,
                    color = Color.Black.copy(alpha = 0.6f),
                    modifier = Modifier
                        .size(if (isRound) 28.dp else 32.dp)
                        .clickable { onExpandClick() }
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Rounded.OpenInFull,
                            contentDescription = "Make circle video big",
                            tint = Color.White,
                            modifier = Modifier.size(if (isRound) 15.dp else 17.dp)
                        )
                    }
                }
            }
        }

        // Top-right Continuous Loop button (safely inset within circle curve, fully inside boundary)
        Surface(
            shape = CircleShape,
            color = if (isLooping) MaterialTheme.colorScheme.primary else Color.Black.copy(alpha = 0.65f),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.7f)),
            shadowElevation = 3.dp,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(
                    top = if (isRound) 30.dp else 12.dp,
                    end = if (isRound) 30.dp else 12.dp
                )
                .size(if (isRound) 28.dp else 32.dp)
                .clickable {
                    isLooping = !isLooping
                }
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Rounded.Repeat,
                    contentDescription = if (isLooping) "Continuous loop enabled" else "Continuous loop disabled",
                    tint = Color.White,
                    modifier = Modifier.size(if (isRound) 15.dp else 17.dp)
                )
            }
        }

        // Bottom-right Mute/unmute toggle (safely inset within circle curve)
        Surface(
            shape = CircleShape,
            color = Color.Black.copy(alpha = 0.6f),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(
                    bottom = if (isRound) 30.dp else 12.dp,
                    end = if (isRound) 30.dp else 12.dp
                )
                .size(if (isRound) 28.dp else 32.dp)
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
                    imageVector = if (isMuted) Icons.AutoMirrored.Rounded.VolumeOff else Icons.AutoMirrored.Rounded.VolumeUp,
                    contentDescription = if (isMuted) "Unmute audio" else "Mute audio",
                    tint = Color.White,
                    modifier = Modifier.size(if (isRound) 15.dp else 17.dp)
                )
            }
        }

        // Bottom duration pill (safely inset below center play button, never overlapping)
        val displayDuration = if (effectiveDurationSeconds > 0) effectiveDurationSeconds else 0
        if (displayDuration > 0) {
            Surface(
                shape = CircleShape,
                color = Color.Black.copy(alpha = 0.65f),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 12.dp)
            ) {
                Text(
                    text = String.format(java.util.Locale.US, "%d:%02d", displayDuration / 60, displayDuration % 60),
                    fontSize = 11.sp,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 2.5.dp)
                )
            }
        }
    }
}

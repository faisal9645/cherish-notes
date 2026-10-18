package com.example.ui.components

import android.graphics.Bitmap
import android.graphics.SurfaceTexture
import android.media.MediaPlayer
import android.net.Uri
import android.util.Log
import android.view.Surface
import android.view.TextureView
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
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
import com.example.ui.theme.Blue500
import com.example.ui.theme.NavyCard
import com.example.ui.theme.Slate900
import androidx.compose.material3.LocalTextStyle
import com.example.ui.theme.steadyDigits

/**
 * Only one video note plays with sound at a time: starting one stops (and releases) the others.
 * While none does, the chat's round notes play silently where they are (their previews), unless
 * previews are off (the app hidden behind Notes).
 */
private object VideoNotePlayback {
    var activeKey by mutableStateOf<Any?>(null)
    var previewsAllowed by mutableStateOf(true)
}

/** Stops every video note, silent previews too (each goes back to its still frame), e.g. when the app hides. */
fun stopAllVideoNotes() {
    VideoNotePlayback.activeKey = null
    VideoNotePlayback.previewsAllowed = false
}

/** Lets the chat's round notes play their silent previews again (the app is back from Notes). */
fun resumeVideoNotePreviews() {
    VideoNotePlayback.previewsAllowed = true
}

private val BlueCircularBorder = Blue500

// A round note's buttons sit on its diagonals, inside the curve at any size (it grows to the
// screen's width while playing), where fixed corner insets would fall outside the circle
private const val ROUND_BUTTON_BIAS = 0.65f
private val RoundTopStart = BiasAlignment(-ROUND_BUTTON_BIAS, -ROUND_BUTTON_BIAS)
private val RoundTopEnd = BiasAlignment(ROUND_BUTTON_BIAS, -ROUND_BUTTON_BIAS)
private val RoundBottomEnd = BiasAlignment(ROUND_BUTTON_BIAS, ROUND_BUTTON_BIAS)

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
    cropToFill: Boolean = true,
    // In the chat: while no note plays with sound, it plays where it is, muted and looping, at
    // its size, with no ring or buttons; a tap plays it from the start with sound (like Telegram)
    silentPreview: Boolean = false
) {
    val isRound = shape == CircleShape
    val context = LocalContext.current
    // Playing its silent preview (muted, looping, no ring or buttons)
    var isPreview by remember { mutableStateOf(false) }
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

    // Playing with sound (a silent preview doesn't count): it grows and shows its ring
    val activelyPlaying = isPlaying && isVideoReady && !isPreview
    LaunchedEffect(activelyPlaying) {
        onPlayingChanged?.invoke(activelyPlaying)
    }
    // A round note has its ring only while it plays (it fades in and out)
    val ringAlpha = animateFloatAsState(
        targetValue = if (activelyPlaying) 1f else 0f,
        animationSpec = tween(durationMillis = 220),
        label = "video_note_ring"
    )

    // A preview loops muted whatever the loop and sound buttons are set to
    LaunchedEffect(isLooping, isPreview) {
        try {
            mediaPlayerRef?.isLooping = isLooping || isPreview
        } catch (_: Exception) {}
    }

    fun releasePlayer() {
        try {
            mediaPlayerRef?.stop()
            mediaPlayerRef?.release()
        } catch (_: Exception) {}
        mediaPlayerRef = null
        playerRequested = false
        isPreview = false
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
        isPreview = false
        VideoNotePlayback.activeKey = playbackKey
        playerRequested = true
        shouldPlayWhenReady = true
        isPlaying = true
    }

    // Plays where it is, muted and looping (its player kept when it has one)
    fun startPreview() {
        if (hasError) return
        isPreview = true
        isCompleted = false
        val mp = mediaPlayerRef
        if (playerRequested && mp != null && isVideoReady) {
            try {
                mp.setVolume(0f, 0f)
                mp.isLooping = true
                if (!mp.isPlaying) mp.start()
                isPlaying = true
                shouldPlayWhenReady = true
                return
            } catch (e: Exception) {
                Log.w("CircularVideoNoteView", "Preview error", e)
            }
        }
        playerRequested = true
        shouldPlayWhenReady = true
        isPlaying = true
    }

    // From the silent preview: from the start, with sound (and so its ring and buttons)
    fun playWithSound() {
        isPreview = false
        isCompleted = false
        progress = 0f
        VideoNotePlayback.activeKey = playbackKey
        val mp = mediaPlayerRef
        if (mp != null && isVideoReady) {
            try {
                if (isMuted) mp.setVolume(0f, 0f) else mp.setVolume(1f, 1f)
                mp.isLooping = isLooping
                mp.seekTo(0)
                if (!mp.isPlaying) mp.start()
                isPlaying = true
                shouldPlayWhenReady = true
            } catch (e: Exception) {
                Log.w("CircularVideoNoteView", "Play with sound error", e)
                startPlayback()
            }
        } else {
            // Still loading: it starts with sound once ready
            startPlayback()
        }
    }

    // One note plays with sound: while it does, the others go still, silent previews too. While
    // none does, a round note in the chat plays its silent preview
    LaunchedEffect(playbackKey) {
        if (autoPlay) VideoNotePlayback.activeKey = playbackKey
        snapshotFlow { VideoNotePlayback.activeKey to VideoNotePlayback.previewsAllowed }
            .collect { (active, previewsAllowed) ->
                if (active !== playbackKey && playerRequested && (!isPreview || active != null || !previewsAllowed)) {
                    releasePlayer()
                }
                if (silentPreview && previewsAllowed && active == null && !playerRequested) startPreview()
            }
    }

    // Lifecycle observer: pause video when app goes to background; a silent preview goes on when back
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
            } else if (event == Lifecycle.Event.ON_RESUME && isPreview) {
                shouldPlayWhenReady = true
                isPlaying = true
                try {
                    mediaPlayerRef?.let { mp -> if (isVideoReady && !mp.isPlaying) mp.start() }
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

    // Toggle play / pause helper (a tap on a silent preview plays it with sound)
    val togglePlayPause: () -> Unit = {
        if (isPreview) {
            playWithSound()
        } else if (isCompleted) {
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
                            mp.isLooping = isLooping || isPreview
                            if (source.startsWith("http://") || source.startsWith("https://") || source.startsWith("content://")) {
                                mp.setDataSource(ctx, Uri.parse(source))
                            } else {
                                val path = source.removePrefix("file://")
                                mp.setDataSource(path)
                            }
                            if (isMuted || isPreview) mp.setVolume(0f, 0f) else mp.setVolume(1f, 1f)
                            mp.setOnPreparedListener { preparedMp ->
                                mediaPlayerRef = preparedMp
                                // As it is now: a preview muted and looping, or with sound
                                if (isMuted || isPreview) preparedMp.setVolume(0f, 0f) else preparedMp.setVolume(1f, 1f)
                                preparedMp.isLooping = isLooping || isPreview
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
                                // Shows its retry button, even from a preview
                                isPreview = false
                                isVideoReady = false
                                isPlaying = false
                                isLoading = false
                                hasError = true
                                true
                            }
                            mp.setOnCompletionListener {
                                if (!isPreview && !isLooping && silentPreview) {
                                    // Played through with sound: small again, back to its silent preview
                                    progress = 0f
                                    startPreview()
                                    if (VideoNotePlayback.activeKey === playbackKey) VideoNotePlayback.activeKey = null
                                } else if (isLooping || isPreview) {
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
                        if (isMuted || isPreview) mp.setVolume(0f, 0f) else mp.setVolume(1f, 1f)
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
                    // A silent preview shows nothing over its still frame while it loads
                    if (!isPreview) {
                        CircularProgressIndicator(
                            color = BlueCircularBorder,
                            strokeWidth = 2.5.dp,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }
            }
        }

        // One ring, the playback progress running round the edge: only while the note plays
        // with sound, none at rest or on a silent preview
        if (isRound) {
            Canvas(modifier = Modifier.fillMaxSize().padding(2.dp)) {
                val alpha = ringAlpha.value
                if (alpha <= 0f || progress <= 0.002f) return@Canvas
                val strokeWidth = 3.5.dp.toPx()
                drawArc(
                    color = BlueCircularBorder,
                    startAngle = -90f,
                    sweepAngle = (progress * 360f).coerceIn(0f, 360f),
                    useCenter = false,
                    topLeft = Offset(strokeWidth / 2f, strokeWidth / 2f),
                    size = Size(size.width - strokeWidth, size.height - strokeWidth),
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
                    alpha = alpha
                )
            }
        }

        // Center Play / Pause / Loading Visual Overlay
        // Hidden when video is actively playing, shown when paused, loading, or retry; never on a
        // silent preview
        if (!isPreview && (!isPlaying || !isVideoReady || isLoading || hasError)) {
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

        // The buttons and the duration: none on a silent preview
        if (!isPreview) {
        // Top-left "Make Big" / Expand icon button (safely inset within circle curve)
        if (onExpandClick != null) {
            Box(
                modifier = Modifier
                    .align(if (isRound) RoundTopStart else Alignment.TopStart)
                    .padding(if (isRound) 0.dp else 12.dp)
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
                .align(if (isRound) RoundTopEnd else Alignment.TopEnd)
                .padding(if (isRound) 0.dp else 12.dp)
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
                .align(if (isRound) RoundBottomEnd else Alignment.BottomEnd)
                .padding(if (isRound) 0.dp else 12.dp)
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
                    style = LocalTextStyle.current.steadyDigits,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                )
            }
        }
        }
    }
}

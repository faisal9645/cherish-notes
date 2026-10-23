package com.example.ui.components

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.net.Uri
import android.os.PowerManager
import android.util.Log
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
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
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
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.exoplayer.ExoPlayer
import com.example.ui.theme.Blue500
import com.example.ui.theme.NavyCard
import com.example.ui.theme.Slate900
import com.example.ui.theme.steadyDigits
import com.example.util.VideoThumbnailHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.roundToInt

/**
 * Only one video note plays with sound at a time: starting one stops (and releases) the others.
 * While none does, the round note most in view in the chat plays silently where it is (its
 * preview); only that one, so a single video is decoding. No previews while the app hides behind
 * Notes, or in Battery Saver.
 */
private object VideoNotePlayback {
    var activeKey by mutableStateOf<Any?>(null)
    var previewsAllowed by mutableStateOf(true)
    var isListScrolling by mutableStateOf(false)
    // How much of each round note in the chat is on screen
    val inView = mutableStateMapOf<Any, InView>()

    /** Whose turn it is to preview: the note with the most of it on screen (half at least), then the lowest. */
    fun previewChoice(): Any? = inView.entries
        .filter { it.value.shown >= MIN_SHOWN_TO_PREVIEW }
        .maxWithOrNull(compareBy<Map.Entry<Any, InView>>({ it.value.shown }, { it.value.row }))
        ?.key

    fun report(key: Any, coordinates: LayoutCoordinates) {
        val width = coordinates.size.width
        val height = coordinates.size.height
        if (!coordinates.isAttached || width <= 0 || height <= 0) {
            inView.remove(key)
            return
        }
        // The part on screen (the list clips the rest), in tenths, and roughly where: so scrolling
        // changes these only now and then
        val visible = coordinates.boundsInWindow()
        val shown = ((visible.width * visible.height) / (width.toFloat() * height) * 10f).roundToInt() / 10f
        val now = InView(shown, (visible.center.y / ROW_PX).roundToInt())
        if (inView[key] != now) inView[key] = now
    }
}

private data class InView(val shown: Float, val row: Int)

/** Battery Saver: no silent previews while it's on. */
private object BatterySaver {
    var isOn by mutableStateOf(false)
        private set
    private var watching = false

    fun watch(context: Context) {
        if (watching) return
        watching = true
        val app = context.applicationContext
        val power = app.getSystemService(PowerManager::class.java) ?: return
        isOn = power.isPowerSaveMode
        ContextCompat.registerReceiver(
            app,
            object : BroadcastReceiver() {
                override fun onReceive(context: Context, intent: Intent) {
                    isOn = power.isPowerSaveMode
                }
            },
            IntentFilter(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
    }
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

/** Tells video notes whether the chat list is currently scrolling so decoding pauses during fast flings. */
fun setVideoNotesScrollState(isScrolling: Boolean) {
    if (VideoNotePlayback.isListScrolling != isScrolling) {
        VideoNotePlayback.isListScrolling = isScrolling
    }
}

private val BlueCircularBorder = Blue500

// A round note's buttons sit on its diagonals, inside the curve at any size (it grows to the
// screen's width while playing), where fixed corner insets would fall outside the circle
private const val ROUND_BUTTON_BIAS = 0.65f
private val RoundTopStart = BiasAlignment(-ROUND_BUTTON_BIAS, -ROUND_BUTTON_BIAS)
private val RoundTopEnd = BiasAlignment(ROUND_BUTTON_BIAS, -ROUND_BUTTON_BIAS)
private val RoundBottomEnd = BiasAlignment(ROUND_BUTTON_BIAS, ROUND_BUTTON_BIAS)

private const val MIN_SHOWN_TO_PREVIEW = 0.5f
private const val ROW_PX = 96f
/** A note is the one most in view this long before its preview starts: not while scrolling past. */
private const val PREVIEW_SETTLE_MS = 220L
/** How soon a round note's buttons fade once it plays with sound, and how long a tap brings them back */
private const val CONTROLS_HIDE_AT_START_MS = 600L
private const val CONTROLS_SHOWN_MS = 2_500L
private const val TAG = "CircularVideoNoteView"

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
    // In the chat: when it's the one most in view and no note plays with sound, it plays where it
    // is, muted and looping, at its size, with no ring or buttons; a tap plays it from the start
    // with sound (like Telegram)
    silentPreview: Boolean = false,
    // The small poster uploaded with it: shown until it plays, so scrolling past doesn't download
    // the whole video to make a still frame
    posterUrl: String? = null,
    // Whether its buttons are showing (a round note playing with sound hides them): the
    // full-screen viewer hides its own bar and buttons along with them
    onControlsVisibleChanged: ((Boolean) -> Unit)? = null
) {
    val isRound = shape == CircleShape
    val context = LocalContext.current
    // Playing its silent preview (muted, looping, no ring or buttons)
    var isPreview by remember { mutableStateOf(false) }
    // The player exists only while this note plays (with sound or as a preview)
    var playerRequested by remember { mutableStateOf(autoPlay) }
    var player by remember { mutableStateOf<ExoPlayer?>(null) }
    var textureView by remember { mutableStateOf<TextureView?>(null) }
    // Meant to be playing (set before the player is ready)
    var isPlaying by remember { mutableStateOf(autoPlay) }
    var isCompleted by remember { mutableStateOf(false) }
    var isMuted by remember { mutableStateOf(false) }
    var isLooping by remember { mutableStateOf(false) }
    var progress by remember { mutableFloatStateOf(0f) }
    // Its first frame is on screen
    var isVideoReady by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }
    var hasError by remember { mutableStateOf(false) }
    var thumbnailBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var videoAspect by remember { mutableFloatStateOf(1f) }
    var effectiveDurationSeconds by remember { mutableIntStateOf(durationSeconds) }
    var localVideoFile by remember { mutableStateOf<File?>(null) }
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

    // A round note playing with sound shows just the video: its buttons fade away, and a tap
    // brings them back for a moment (paused, finished or loading, they stay)
    var controlsShown by remember { mutableStateOf(true) }
    var controlsTaps by remember { mutableIntStateOf(0) }
    LaunchedEffect(activelyPlaying, controlsTaps) {
        if (!activelyPlaying) {
            controlsShown = true
            controlsTaps = 0
        } else if (controlsShown) {
            delay(if (controlsTaps == 0) CONTROLS_HIDE_AT_START_MS else CONTROLS_SHOWN_MS)
            controlsShown = false
        }
    }
    val controlsVisible = !(isRound && activelyPlaying) || controlsShown
    val controlsAlpha by animateFloatAsState(
        targetValue = if (controlsVisible) 1f else 0f,
        animationSpec = tween(durationMillis = 200),
        label = "video_note_controls"
    )
    val currentOnControlsVisibleChanged by rememberUpdatedState(onControlsVisibleChanged)
    LaunchedEffect(controlsVisible) {
        currentOnControlsVisibleChanged?.invoke(controlsVisible)
    }

    // The player follows: muted and looping as a preview, otherwise as its buttons are set
    LaunchedEffect(player, isMuted, isLooping, isPreview) {
        player?.let { p ->
            p.volume = if (isMuted || isPreview) 0f else 1f
            p.repeatMode = if (isLooping || isPreview) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
        }
    }

    fun releasePlayer() {
        // The player goes with it (see below)
        playerRequested = false
        isPreview = false
        isPlaying = false
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
        isPlaying = true
        player?.play()
        playerRequested = true
    }

    // Plays where it is, muted and looping (on its player, when it has one)
    fun startPreview() {
        if (hasError) return
        isPreview = true
        isCompleted = false
        isPlaying = true
        player?.let { p ->
            p.volume = 0f
            p.repeatMode = Player.REPEAT_MODE_ONE
            if (p.playbackState == Player.STATE_ENDED) p.seekTo(0)
            p.play()
        }
        playerRequested = true
    }

    // From the silent preview: from the start, with sound (and so its ring and buttons)
    fun playWithSound() {
        isPreview = false
        isCompleted = false
        progress = 0f
        VideoNotePlayback.activeKey = playbackKey
        isPlaying = true
        player?.let { p ->
            p.volume = if (isMuted) 0f else 1f
            p.repeatMode = if (isLooping) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
            p.seekTo(0)
            p.play()
        }
        playerRequested = true
    }

    // Played to its end (a loop never ends)
    fun onPlayedThrough() {
        if (silentPreview) {
            // Small again, back to its silent preview (while it's still the one most in view)
            progress = 0f
            startPreview()
            if (VideoNotePlayback.activeKey === playbackKey) VideoNotePlayback.activeKey = null
        } else {
            isPlaying = false
            isCompleted = true
            progress = 1f
        }
    }

    val listener = remember {
        object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                isLoading = playbackState == Player.STATE_BUFFERING
                when (playbackState) {
                    Player.STATE_READY -> {
                        hasError = false
                        val durationMs = player?.duration ?: C.TIME_UNSET
                        if (effectiveDurationSeconds <= 0 && durationMs > 0) {
                            effectiveDurationSeconds = (durationMs / 1000).toInt().coerceAtLeast(1)
                        }
                    }
                    Player.STATE_ENDED -> onPlayedThrough()
                    else -> Unit
                }
            }

            override fun onRenderedFirstFrame() {
                isVideoReady = true
            }

            override fun onVideoSizeChanged(videoSize: VideoSize) {
                if (videoSize.width > 0 && videoSize.height > 0) {
                    val pixelRatio = if (videoSize.pixelWidthHeightRatio > 0f) videoSize.pixelWidthHeightRatio else 1f
                    videoAspect = (videoSize.width.toFloat() * pixelRatio) / videoSize.height.toFloat()
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                Log.w(TAG, "Playback error", error)
                // Shows its retry button, even from a preview
                isPreview = false
                isVideoReady = false
                isPlaying = false
                isLoading = false
                hasError = true
            }
        }
    }

    // The player (ExoPlayer: quick to start, loops without a gap), while this note plays
    DisposableEffect(playerRequested) {
        if (!playerRequested) return@DisposableEffect onDispose { }
        val exo = ExoPlayer.Builder(context).build().apply {
            addListener(listener)
            volume = if (isMuted || isPreview) 0f else 1f
            repeatMode = if (isLooping || isPreview) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
            playWhenReady = isPlaying
        }
        player = exo
        onDispose {
            exo.removeListener(listener)
            exo.release()
            if (player === exo) player = null
            isVideoReady = false
            isLoading = false
        }
    }
    // It plays the downloaded copy (fetched first when there's none yet), else the link itself
    LaunchedEffect(player) {
        val p = player ?: return@LaunchedEffect
        isLoading = true
        val file = localVideoFile?.takeIf { it.exists() && it.length() > 1024 }
            ?: VideoThumbnailHelper.getCachedFile(context, videoUrl)?.takeIf { it.exists() }?.also { localVideoFile = it }
        val uri = when {
            file != null -> Uri.fromFile(file)
            videoUrl.contains("://") -> Uri.parse(videoUrl)
            else -> Uri.fromFile(File(videoUrl))
        }
        p.setMediaItem(MediaItem.fromUri(uri))
        p.prepare()
    }
    // Its picture goes to the TextureView on screen
    DisposableEffect(player, textureView) {
        val p = player
        val tv = textureView
        if (p == null || tv == null) return@DisposableEffect onDispose { }
        p.setVideoTextureView(tv)
        onDispose {
            try {
                p.clearVideoTextureView(tv)
            } catch (_: Exception) {}
        }
    }

    // Tells the chat whether it's at the most-in-view note's turn to preview (and when it can)
    LaunchedEffect(silentPreview) {
        if (silentPreview) BatterySaver.watch(context)
    }

    // React to autoPlay changes (e.g. pagers swiping between media in full-screen viewer)
    LaunchedEffect(autoPlay) {
        if (autoPlay) {
            if (!isPlaying || !playerRequested) {
                startPlayback()
            }
        } else {
            if (isPlaying || playerRequested) {
                player?.pause()
                isPlaying = false
                playerRequested = false
                if (VideoNotePlayback.activeKey === playbackKey) {
                    VideoNotePlayback.activeKey = null
                }
            }
        }
    }

    // One note plays with sound; while it does, the others are still, previews too. While none
    // does, the round note most in view in the chat plays its silent preview
    LaunchedEffect(playbackKey) {
        if (autoPlay) VideoNotePlayback.activeKey = playbackKey
        snapshotFlow {
            val active = VideoNotePlayback.activeKey
            val myTurn = silentPreview && active == null && VideoNotePlayback.previewsAllowed &&
                !VideoNotePlayback.isListScrolling &&
                !BatterySaver.isOn && VideoNotePlayback.previewChoice() === playbackKey
            // Paused with its sound on (by a tap, or the app going to the background) and then
            // scrolled mostly away: it no longer holds the notes in view back from playing
            val pausedAway = active === playbackKey && silentPreview && !isPlaying &&
                (VideoNotePlayback.inView[playbackKey]?.shown ?: 0f) < MIN_SHOWN_TO_PREVIEW
            Triple(active, myTurn, pausedAway)
        }.collectLatest { (active, myTurn, pausedAway) ->
            if (pausedAway) {
                VideoNotePlayback.activeKey = null
                releasePlayer()
                return@collectLatest
            }
            when {
                active === playbackKey -> Unit
                isPreview -> if (!myTurn) releasePlayer()
                // Another note plays with sound, or all were stopped
                playerRequested -> releasePlayer()
            }
            if (myTurn && !playerRequested && !hasError) {
                delay(PREVIEW_SETTLE_MS)
                startPreview()
            }
        }
    }

    // Lifecycle observer: pause video when app goes to background; a silent preview goes on when back
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE, Lifecycle.Event.ON_STOP -> player?.let { p ->
                    if (p.playWhenReady) {
                        p.pause()
                        isPlaying = false
                    }
                }
                Lifecycle.Event.ON_RESUME -> if (isPreview) {
                    isPlaying = true
                    player?.play()
                }
                else -> Unit
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
            VideoNotePlayback.inView.remove(playbackKey)
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

            // Its poster shows instead; the video is fetched only to play
            if (posterUrl != null) return@LaunchedEffect

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
        val p = player
        when {
            isPreview -> playWithSound()
            hasError && p != null -> {
                hasError = false
                VideoNotePlayback.activeKey = playbackKey
                isPlaying = true
                p.prepare()
                p.play()
            }
            isCompleted -> {
                isCompleted = false
                progress = 0f
                if (p != null) {
                    VideoNotePlayback.activeKey = playbackKey
                    isPlaying = true
                    p.seekTo(0)
                    p.play()
                } else {
                    startPlayback()
                }
            }
            !playerRequested || p == null -> startPlayback()
            isPlaying -> {
                p.pause()
                isPlaying = false
            }
            else -> {
                VideoNotePlayback.activeKey = playbackKey
                isPlaying = true
                p.play()
            }
        }
    }

    // Progress tracker from actual playback position
    LaunchedEffect(isPlaying, player) {
        val p = player ?: return@LaunchedEffect
        while (isActive && isPlaying) {
            val durationMs = p.duration
            if (durationMs > 0) {
                progress = (p.currentPosition.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
            }
            delay(50)
        }
    }

    var containerSize by remember { mutableStateOf(IntSize.Zero) }
    val containerAspect = remember(containerSize) {
        if (containerSize.width > 0 && containerSize.height > 0) {
            containerSize.width.toFloat() / containerSize.height.toFloat()
        } else 1f
    }

    // Precise aspect ratio scaling matching ContentScale.Crop / ContentScale.Fit:
    // TextureView fills the container (mapping pixels to container width & height).
    // Comparing videoAspect to containerAspect guarantees that neither crop nor fit
    // ever stretches or squishes the video, regardless of whether the container is square or rectangular.
    val scaleX = remember(videoAspect, containerAspect, cropToFill) {
        val va = videoAspect.coerceAtLeast(0.01f)
        val ca = containerAspect.coerceAtLeast(0.01f)
        val r = va / ca
        if (cropToFill) {
            if (r > 1f) r else 1f
        } else {
            if (r > 1f) 1f else r
        }
    }
    val scaleY = remember(videoAspect, containerAspect, cropToFill) {
        val va = videoAspect.coerceAtLeast(0.01f)
        val ca = containerAspect.coerceAtLeast(0.01f)
        val r = va / ca
        if (cropToFill) {
            if (r < 1f) 1f / r else 1f
        } else {
            if (r < 1f) 1f else 1f / r
        }
    }

    // A tap on a round note playing with sound, while its buttons are hidden, only brings them back
    val onVideoTap: () -> Unit = {
        if (isRound && activelyPlaying && !controlsShown) {
            controlsShown = true
            controlsTaps++
        } else {
            togglePlayPause()
        }
    }

    val currentOnLongClick by rememberUpdatedState(onLongClick)
    val currentOnDoubleClick by rememberUpdatedState(onDoubleClick)
    val currentOnVideoTap by rememberUpdatedState(onVideoTap)

    Box(
        modifier = modifier
            .then(if (isRound) Modifier.defaultMinSize(minWidth = 220.dp, minHeight = 220.dp) else Modifier)
            .onSizeChanged { containerSize = it }
            // How much of it is on screen, for the chat's one silent preview
            .then(
                if (silentPreview) Modifier.onGloballyPositioned { VideoNotePlayback.report(playbackKey, it) }
                else Modifier
            )
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
                        currentOnVideoTap()
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        // TextureView surface with precise aspect preservation matching thumbnail ContentScale.Crop
        if (playerRequested) {
            AndroidView(
                factory = { ctx -> TextureView(ctx).also { textureView = it } },
                onRelease = { tv -> if (textureView === tv) textureView = null },
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
            } else if (posterUrl != null) {
                // The uploaded poster (small, and cached)
                coil.compose.AsyncImage(
                    model = posterUrl,
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

        // The buttons and the duration: none on a silent preview, and they fade away while a
        // round note plays with sound (a tap brings them back, see controlsShown)
        if (!isPreview && controlsAlpha > 0f) {
        Box(modifier = Modifier.matchParentSize().graphicsLayer { alpha = controlsAlpha }) {
        // While it plays: a pause button in the middle, where its play button was (not over
        // the spinner while it buffers)
        if (isRound && activelyPlaying && !isLoading) {
            Surface(
                shape = CircleShape,
                color = Color.Black.copy(alpha = 0.65f),
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(56.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Rounded.Pause,
                        contentDescription = "Pause video note",
                        tint = Color.White,
                        modifier = Modifier.size(34.dp)
                    )
                }
            }
        }

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
}

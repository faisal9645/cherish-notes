package com.example.ui.chat

import android.content.Context
import android.graphics.Matrix
import android.graphics.SurfaceTexture
import android.hardware.Camera
import android.media.CamcorderProfile
import android.media.MediaRecorder
import android.os.Build
import android.util.Log
import android.view.Surface
import android.view.TextureView
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.ui.theme.HeartRed
import com.example.ui.theme.RoseGoldPrimary
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import kotlin.math.roundToInt

/**
 * Telegram-Style Circular Video Note Recorder:
 * - Hold to record live circular video note
 * - Swipe TOP (drag up) to Lock into hands-free recording
 * - Slide LEFT (drag left) to Cancel & discard
 * - Release finger to instantly Send (if not locked)
 * - In Locked mode: Stop & Send button, Red Trash/Cancel button, Flip Camera button
 * - Live 60-second circular progress indicator with timer badge
 */
@Composable
fun CircularVideoNoteRecorderDialog(
    onDismiss: () -> Unit,
    onSendVideoNote: (File, Int) -> Unit
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val primaryColor = RoseGoldPrimary
    val coroutineScope = rememberCoroutineScope()
    var recordingStartTime by remember { mutableLongStateOf(0L) }

    var isRecording by remember { mutableStateOf(false) }
    var isLocked by remember { mutableStateOf(false) }
    var isCancelled by remember { mutableStateOf(false) }
    var recordingDurationSec by remember { mutableIntStateOf(0) }
    var cameraFacing by remember { mutableIntStateOf(Camera.CameraInfo.CAMERA_FACING_FRONT) }

    var dragOffsetX by remember { mutableFloatStateOf(0f) }
    var dragOffsetY by remember { mutableFloatStateOf(0f) }

    var cameraRef by remember { mutableStateOf<Camera?>(null) }
    var cameraId by remember { mutableIntStateOf(0) }
    var mediaRecorderRef by remember { mutableStateOf<MediaRecorder?>(null) }
    var outputFileRef by remember { mutableStateOf<File?>(null) }
    var surfaceTextureRef by remember { mutableStateOf<SurfaceTexture?>(null) }
    var textureViewRef by remember { mutableStateOf<TextureView?>(null) }
    var previewWidth by remember { mutableIntStateOf(0) }
    var previewHeight by remember { mutableIntStateOf(0) }
    // How the camera picture is turned for the screen, and how the recording is marked to play
    var displayOrientation by remember { mutableIntStateOf(90) }
    var recordingOrientation by remember { mutableIntStateOf(270) }
    var recordingProfile by remember { mutableStateOf<CamcorderProfile?>(null) }
    // Bumped for every recording started, so the timer restarts with it
    var recordingSession by remember { mutableIntStateOf(0) }

    /** Centre-crops the camera picture into the square viewfinder, without stretching it. */
    fun adjustTextureTransform(tv: TextureView, viewW: Int, viewH: Int) {
        val pW = previewWidth
        val pH = previewHeight
        if (viewW <= 0 || viewH <= 0 || pW <= 0 || pH <= 0) return
        // The camera turns its picture upright for the screen, so a landscape preview shows as portrait
        val sideways = displayOrientation % 180 != 0
        val pictureRatio = if (sideways) pH.toFloat() / pW else pW.toFloat() / pH
        val viewRatio = viewW.toFloat() / viewH
        // The TextureView squeezes the picture into its own shape; scale one side back out
        val scaleX = if (pictureRatio > viewRatio) pictureRatio / viewRatio else 1f
        val scaleY = if (pictureRatio < viewRatio) viewRatio / pictureRatio else 1f
        val isFront = cameraFacing == Camera.CameraInfo.CAMERA_FACING_FRONT
        // Front camera: shown the way it is recorded (the camera itself mirrors its preview)
        tv.setTransform(Matrix().apply { setScale(if (isFront) -scaleX else scaleX, scaleY, viewW / 2f, viewH / 2f) })
    }

    fun stopCamera() {
        val cam = cameraRef ?: return
        cameraRef = null
        try { cam.stopPreview() } catch (_: Exception) {}
        try { cam.release() } catch (_: Exception) {}
    }

    fun startCamera(facing: Int, texture: SurfaceTexture, viewW: Int, viewH: Int) {
        stopCamera()
        try {
            val id = getCameraId(facing)
            val info = Camera.CameraInfo().also { Camera.getCameraInfo(id, it) }
            val cam = Camera.open(id)
            cameraRef = cam
            cameraId = id

            val screenDegrees = displayRotationDegrees(context)
            val isFront = info.facing == Camera.CameraInfo.CAMERA_FACING_FRONT
            displayOrientation = if (isFront) {
                (360 - (info.orientation + screenDegrees) % 360) % 360
            } else {
                (info.orientation - screenDegrees + 360) % 360
            }
            recordingOrientation = if (isFront) {
                (info.orientation + screenDegrees) % 360
            } else {
                (info.orientation - screenDegrees + 360) % 360
            }
            cam.setDisplayOrientation(displayOrientation)

            val profile = videoNoteProfile(id)
            recordingProfile = profile
            val params = cam.parameters
            val previewSize = choosePreviewSize(
                sizes = params.supportedPreviewSizes.orEmpty(),
                videoW = profile?.videoFrameWidth ?: 1280,
                videoH = profile?.videoFrameHeight ?: 720,
                viewSide = minOf(viewW, viewH)
            )
            previewSize?.let { params.setPreviewSize(it.width, it.height) }
            // Prepares the camera for video, so starting the recording doesn't restart the picture
            params.setRecordingHint(true)
            if (Camera.Parameters.FOCUS_MODE_CONTINUOUS_VIDEO in params.supportedFocusModes.orEmpty()) {
                params.focusMode = Camera.Parameters.FOCUS_MODE_CONTINUOUS_VIDEO
            }
            if (params.isVideoStabilizationSupported) params.videoStabilization = true
            val applied = runCatching { cam.parameters = params }.isSuccess
            if (!applied && previewSize != null) {
                // Some cameras refuse one of the extras; the preview size is the part that matters
                runCatching { cam.parameters = cam.parameters.apply { setPreviewSize(previewSize.width, previewSize.height) } }
            }
            val shown = cam.parameters.previewSize
            previewWidth = shown.width
            previewHeight = shown.height
            cam.setPreviewTexture(texture)
            cam.startPreview()
            textureViewRef?.let { adjustTextureTransform(it, viewW, viewH) }
        } catch (e: Exception) {
            Log.w("VideoNoteRecorder", "Camera failed to start", e)
            stopCamera()
        }
    }

    /** Starts recording from the open camera; false when the camera or recorder isn't available. */
    fun beginRecording(): Boolean {
        if (isRecording) return true
        val cam = cameraRef ?: return false
        val outFile = File(context.cacheDir, "videonote_${System.currentTimeMillis()}.mp4")
        val recorder = startVideoRecording(context, cam, cameraId, recordingProfile, recordingOrientation, outFile)
            ?: return false
        outputFileRef = outFile
        mediaRecorderRef = recorder
        recordingStartTime = System.currentTimeMillis()
        recordingSession++
        isRecording = true
        return true
    }

    /** Stops the recorder; true when it saved a playable video. */
    fun finishRecorder(): Boolean {
        val recorder = mediaRecorderRef ?: return false
        mediaRecorderRef = null
        // stop() throws when nothing usable was recorded
        val saved = try {
            recorder.stop()
            true
        } catch (_: Exception) {
            false
        }
        try { recorder.release() } catch (_: Exception) {}
        return saved
    }

    fun failAndClose() {
        Toast.makeText(context, "Couldn't use the camera, please try again", Toast.LENGTH_SHORT).show()
        stopCamera()
        onDismiss()
    }

    fun stopAndSend() {
        if (!isRecording) return
        isRecording = false
        coroutineScope.launch {
            // A recorder stopped within its first moments has nothing to save
            val elapsed = System.currentTimeMillis() - recordingStartTime
            if (elapsed < 900) delay(900 - elapsed)
            val durationSec = ((System.currentTimeMillis() - recordingStartTime + 500) / 1000).toInt().coerceIn(1, 60)
            val saved = finishRecorder()
            stopCamera()
            val file = outputFileRef
            if (saved && file != null && file.length() > 1024) {
                onSendVideoNote(file, durationSec)
            } else {
                file?.delete()
                Toast.makeText(context, "Couldn't save the video note, please try again", Toast.LENGTH_SHORT).show()
            }
            onDismiss()
        }
    }

    fun cancelAndDiscard(showMessage: Boolean = true) {
        isCancelled = true
        isRecording = false
        finishRecorder()
        outputFileRef?.delete()
        outputFileRef = null
        if (showMessage) Toast.makeText(context, "Video note discarded", Toast.LENGTH_SHORT).show()
        onDismiss()
    }

    /** A recording can't change cameras midway, so it starts again with the other camera. */
    fun flipCamera() {
        val wasRecording = isRecording
        if (wasRecording) {
            isRecording = false
            finishRecorder()
            outputFileRef?.delete()
            outputFileRef = null
        }
        cameraFacing = if (cameraFacing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
            Camera.CameraInfo.CAMERA_FACING_BACK
        } else {
            Camera.CameraInfo.CAMERA_FACING_FRONT
        }
        val st = surfaceTextureRef ?: return
        startCamera(cameraFacing, st, textureViewRef?.width ?: 0, textureViewRef?.height ?: 0)
        if (wasRecording && !beginRecording()) failAndClose()
    }

    // Recording timer & 60-second limit, restarted with every recording
    LaunchedEffect(recordingSession) {
        if (recordingSession == 0) return@LaunchedEffect
        recordingDurationSec = 0
        while (isActive && isRecording) {
            delay(1000)
            if (!isRecording) break
            recordingDurationSec++
            if (recordingDurationSec >= 60) {
                stopAndSend()
                break
            }
        }
    }

    // Leaving the app ends the note: the camera is taken away in the background, and the chat
    // hides behind Notes
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) cancelAndDiscard(showMessage = false)
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    DisposableEffect(Unit) {
        onDispose {
            finishRecorder()
            stopCamera()
        }
    }

    // Pulse animation for recording dot
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_alpha"
    )

    // Bouncing chevron for lock & cancel hints
    val arrowBounce by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = -8f,
        animationSpec = infiniteRepeatable(
            animation = tween(400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "arrow_bounce"
    )

    Dialog(
        onDismissRequest = {
            if (!isRecording || isLocked) {
                cancelAndDiscard()
            }
        },
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.88f))
                .statusBarsPadding()
                .navigationBarsPadding(),
            contentAlignment = Alignment.Center
        ) {
            // As big as the screen allows, leaving room for the controls below
            val circleSize = minOf(maxWidth - 24.dp, maxHeight - 330.dp).coerceAtLeast(220.dp)

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 12.dp, vertical = 12.dp)
            ) {
                // Header status
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier.padding(bottom = 6.dp)
                ) {
                    if (isRecording) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .graphicsLayer { alpha = pulseAlpha }
                                .background(HeartRed, CircleShape)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    Text(
                        text = when {
                            isLocked -> "🔒 Locked • Hands-free recording"
                            isRecording -> "Recording Video Note..."
                            else -> "Circular Video Note"
                        },
                        color = Color.White,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Text(
                    text = when {
                        isLocked -> "Tap check to send • Tap trash to discard"
                        isRecording -> "Swipe up to lock • Slide left to cancel"
                        else -> "Hold button below to record video note"
                    },
                    color = Color.White.copy(alpha = 0.75f),
                    fontSize = 13.sp
                )

                Spacer(modifier = Modifier.height(18.dp))

                // Centered Circular Viewfinder (Enlarged, strictly clipped with NO dark square box around it)
                Box(
                    modifier = Modifier
                        .size(circleSize)
                        .clip(CircleShape)
                        .border(
                            width = 3.5.dp,
                            color = if (isRecording) RoseGoldPrimary else Color.White.copy(alpha = 0.35f),
                            shape = CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    AndroidView(
                        factory = { ctx ->
                            TextureView(ctx).apply {
                                textureViewRef = this
                                clipToOutline = true
                                outlineProvider = object : android.view.ViewOutlineProvider() {
                                    override fun getOutline(view: android.view.View, outline: android.graphics.Outline) {
                                        outline.setOval(0, 0, view.width, view.height)
                                    }
                                }
                                surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                                    override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) {
                                        surfaceTextureRef = surface
                                        startCamera(cameraFacing, surface, width, height)
                                        // Auto-start recording as in Telegram
                                        if (!isCancelled && !beginRecording()) failAndClose()
                                    }

                                    override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) {
                                        adjustTextureTransform(this@apply, width, height)
                                    }

                                    override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
                                        surfaceTextureRef = null
                                        stopCamera()
                                        return true
                                    }

                                    override fun onSurfaceTextureUpdated(surface: SurfaceTexture) {}
                                }
                            }
                        },
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(CircleShape)
                    )

                    // Circular recording progress ring (0 to 60s)
                    Canvas(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(2.dp)
                    ) {
                        if (isRecording) {
                            val strokeW = 4.5.dp.toPx()
                            val pct = (recordingDurationSec / 60f).coerceIn(0f, 1f)
                            drawArc(
                                color = primaryColor,
                                startAngle = -90f,
                                sweepAngle = pct * 360f,
                                useCenter = false,
                                style = Stroke(width = strokeW, cap = StrokeCap.Round)
                            )
                        }
                    }

                    // Recording timer badge inside circle
                    if (isRecording) {
                        Surface(
                            shape = CircleShape,
                            color = Color.Black.copy(alpha = 0.7f),
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .padding(top = 16.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .background(HeartRed, CircleShape)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = String.format(
                                        java.util.Locale.US,
                                        "%d:%02d / 1:00",
                                        recordingDurationSec / 60,
                                        recordingDurationSec % 60
                                    ),
                                    color = Color.White,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(28.dp))

                // Telegram Gesture Controls Area
                if (!isLocked) {
                    // HOLDING MODE: Shows Lock indicator above, Slide to Cancel on left, and Hold button
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        // Swipe Up to Lock Indicator
                        AnimatedVisibility(
                            visible = isRecording,
                            enter = fadeIn() + slideInVertically { it },
                            exit = fadeOut()
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier
                                    .padding(bottom = 12.dp)
                                    .offset { IntOffset(0, (arrowBounce + (dragOffsetY * 0.2f)).roundToInt()) }
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(16.dp),
                                    color = if (dragOffsetY < -45f) RoseGoldPrimary else Color.White.copy(alpha = 0.2f),
                                    border = androidx.compose.foundation.BorderStroke(
                                        1.dp,
                                        if (dragOffsetY < -45f) Color.White else Color.White.copy(alpha = 0.3f)
                                    )
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                    ) {
                                        Icon(
                                            imageVector = if (dragOffsetY < -45f) Icons.Default.LockOpen else Icons.Default.Lock,
                                            contentDescription = "Lock",
                                            tint = Color.White,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = if (dragOffsetY < -45f) "Release to Lock!" else "Swipe UP to lock",
                                            color = Color.White,
                                            fontSize = 12.5.sp,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                }
                                Icon(
                                    imageVector = Icons.Default.KeyboardArrowUp,
                                    contentDescription = null,
                                    tint = if (dragOffsetY < -45f) RoseGoldPrimary else Color.White.copy(alpha = 0.7f),
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }

                        // Bottom Row: [ < < < Slide to cancel ] + [ Central Record Button ]
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            // Slide to cancel hint
                            AnimatedVisibility(
                                visible = isRecording,
                                enter = fadeIn(),
                                exit = fadeOut()
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .padding(end = 16.dp)
                                        .offset { IntOffset((dragOffsetX * 0.4f).roundToInt(), 0) }
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                        contentDescription = null,
                                        tint = if (dragOffsetX < -60f) HeartRed else Color.White.copy(alpha = 0.6f),
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = if (dragOffsetX < -60f) "Release to Cancel!" else "< < Slide to cancel",
                                        color = if (dragOffsetX < -60f) HeartRed else Color.White.copy(alpha = 0.75f),
                                        fontSize = 12.5.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }

                            // Interactive Hold-to-Record Button
                            Box(
                                modifier = Modifier
                                    .size(76.dp)
                                    .pointerInput(Unit) {
                                        awaitEachGesture {
                                            val down = awaitFirstDown(requireUnconsumed = false)
                                            val startTime = System.currentTimeMillis()
                                            dragOffsetX = 0f
                                            dragOffsetY = 0f

                                            if (!isRecording && !beginRecording()) {
                                                failAndClose()
                                                return@awaitEachGesture
                                            }

                                            var hasTriggeredLock = false
                                            var hasTriggeredCancel = false

                                            while (true) {
                                                val event = awaitPointerEvent()
                                                val change = event.changes.firstOrNull { it.id == down.id }
                                                if (change == null || !change.pressed) break
                                                change.consume()

                                                val delta = change.position - down.position
                                                dragOffsetX = delta.x.coerceAtMost(0f)
                                                dragOffsetY = delta.y.coerceAtMost(0f)

                                                // Swipe TOP (up) to lock threshold (-45f)
                                                if (delta.y < -45f && !hasTriggeredLock) {
                                                    hasTriggeredLock = true
                                                    isLocked = true
                                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                    dragOffsetX = 0f
                                                    dragOffsetY = 0f
                                                    break
                                                }

                                                // Slide LEFT to cancel threshold (-55f)
                                                if (delta.x < -55f && !hasTriggeredCancel) {
                                                    hasTriggeredCancel = true
                                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                    cancelAndDiscard()
                                                    break
                                                }
                                            }

                                            // Finger released
                                            dragOffsetX = 0f
                                            dragOffsetY = 0f

                                            if (!hasTriggeredLock && !hasTriggeredCancel) {
                                                val elapsed = System.currentTimeMillis() - startTime
                                                if (elapsed >= 500L || recordingDurationSec >= 1) {
                                                    // Send immediately upon release
                                                    stopAndSend()
                                                } else {
                                                    // Quick tap or short release: lock into hands-free so recording isn't lost
                                                    isLocked = true
                                                    Toast.makeText(context, "Hands-free active • Tap send when ready", Toast.LENGTH_SHORT).show()
                                                }
                                            }
                                        }
                                    }
                                    .graphicsLayer {
                                        translationX = dragOffsetX
                                        translationY = dragOffsetY
                                    }
                                    .clip(CircleShape)
                                    .background(
                                        Brush.radialGradient(
                                            colors = listOf(
                                                HeartRed,
                                                RoseGoldPrimary
                                            )
                                        )
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Videocam,
                                    contentDescription = "Hold to record video note",
                                    tint = Color.White,
                                    modifier = Modifier.size(38.dp)
                                )
                            }
                        }
                    }
                } else {
                    // LOCKED HANDS-FREE CONTROLS: [ 🗑️ Cancel ] [ 🔄 Flip Camera ] [ ✈️ Send ]
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(32.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // 🗑️ Discard / Cancel Button
                        IconButton(
                            onClick = { cancelAndDiscard() },
                            modifier = Modifier
                                .size(56.dp)
                                .background(Color.White.copy(alpha = 0.15f), CircleShape)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "Discard video note",
                                tint = HeartRed,
                                modifier = Modifier.size(26.dp)
                            )
                        }

                        // 🔄 Camera Flip Button (Front / Back)
                        IconButton(
                            onClick = {
                                flipCamera()
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            },
                            modifier = Modifier
                                .size(56.dp)
                                .background(Color.White.copy(alpha = 0.15f), CircleShape)
                        ) {
                            Icon(
                                imageVector = Icons.Default.FlipCameraAndroid,
                                contentDescription = "Switch Camera",
                                tint = Color.White,
                                modifier = Modifier.size(26.dp)
                            )
                        }

                        // ✈️ Stop & Send Action Button
                        Surface(
                            shape = CircleShape,
                            color = RoseGoldPrimary,
                            modifier = Modifier
                                .size(72.dp)
                                .clickable { stopAndSend() }
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.Send,
                                    contentDescription = "Send Video Note",
                                    tint = Color.White,
                                    modifier = Modifier.size(34.dp)
                                )
                            }
                        }
                    }
                }
            }

            // Close button at top right
            IconButton(
                onClick = { cancelAndDiscard() },
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(16.dp)
                    .size(40.dp)
                    .background(Color.Black.copy(alpha = 0.5f), CircleShape)
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Close",
                    tint = Color.White
                )
            }
        }
    }
}

private fun getCameraId(facing: Int): Int {
    val info = Camera.CameraInfo()
    for (i in 0 until Camera.getNumberOfCameras()) {
        Camera.getCameraInfo(i, info)
        if (info.facing == facing) return i
    }
    return 0
}

/** Screen rotation in degrees, for turning the camera picture upright. */
private fun displayRotationDegrees(context: Context): Int =
    when (ContextCompat.getDisplayOrDefault(context).rotation) {
        Surface.ROTATION_90 -> 90
        Surface.ROTATION_180 -> 180
        Surface.ROTATION_270 -> 270
        else -> 0
    }

/** The recording profile for a video note: 720p where the camera has it, else the best below. */
private fun videoNoteProfile(cameraId: Int): CamcorderProfile? {
    val qualities = intArrayOf(
        CamcorderProfile.QUALITY_720P,
        CamcorderProfile.QUALITY_480P,
        CamcorderProfile.QUALITY_HIGH,
        CamcorderProfile.QUALITY_LOW
    )
    for (quality in qualities) {
        if (!CamcorderProfile.hasProfile(cameraId, quality)) continue
        val profile = runCatching { CamcorderProfile.get(cameraId, quality) }.getOrNull()
        if (profile != null) return profile
    }
    return null
}

/**
 * A preview size shaped like the video, so the picture doesn't change when recording starts, and
 * sharp enough for a viewfinder [viewSide] px across (up to 1080p).
 */
private fun choosePreviewSize(sizes: List<Camera.Size>, videoW: Int, videoH: Int, viewSide: Int): Camera.Size? {
    if (sizes.isEmpty()) return null
    val ratio = videoW.toFloat() / videoH
    val sameShape = sizes.filter { kotlin.math.abs(it.width.toFloat() / it.height - ratio) < 0.03f }
    val candidates = sameShape.ifEmpty { sizes }.filter { maxOf(it.width, it.height) <= 1920 }.ifEmpty { sizes }
    val wantedShortSide = maxOf(viewSide, minOf(videoW, videoH))
    return candidates.filter { minOf(it.width, it.height) >= wantedShortSide }.minByOrNull { it.width * it.height }
        ?: candidates.maxByOrNull { it.width * it.height }
}

/** Sharp enough for a round video note while keeping the upload small (about 19 MB a minute). */
private const val VIDEO_NOTE_BIT_RATE = 2_500_000

private fun startVideoRecording(
    context: Context,
    camera: Camera,
    cameraId: Int,
    profile: CamcorderProfile?,
    orientationHint: Int,
    outputFile: File
): MediaRecorder? {
    val recorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) MediaRecorder(context) else @Suppress("DEPRECATION") MediaRecorder()
    return try {
        camera.unlock()
        recorder.setCamera(camera)
        recorder.setAudioSource(MediaRecorder.AudioSource.CAMCORDER)
        recorder.setVideoSource(MediaRecorder.VideoSource.CAMERA)
        if (profile != null) {
            recorder.setProfile(profile)
            recorder.setVideoEncodingBitRate(minOf(profile.videoBitRate, VIDEO_NOTE_BIT_RATE))
        } else {
            recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            recorder.setVideoEncoder(MediaRecorder.VideoEncoder.H264)
            recorder.setVideoSize(640, 480)
            recorder.setVideoFrameRate(30)
            recorder.setVideoEncodingBitRate(VIDEO_NOTE_BIT_RATE)
        }
        recorder.setOutputFile(outputFile.absolutePath)
        recorder.setOrientationHint(orientationHint)
        recorder.prepare()
        recorder.start()
        recorder
    } catch (e: Exception) {
        Log.w("VideoNoteRecorder", "Recording failed to start (camera $cameraId)", e)
        runCatching { recorder.reset() }
        runCatching { recorder.release() }
        // Hand the camera back so the preview keeps working
        runCatching { camera.lock() }
        outputFile.delete()
        null
    }
}

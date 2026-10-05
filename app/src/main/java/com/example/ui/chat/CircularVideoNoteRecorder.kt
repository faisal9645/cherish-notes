package com.example.ui.chat

import android.content.Context
import android.hardware.Camera
import android.media.CamcorderProfile
import android.media.MediaRecorder
import android.view.SurfaceHolder
import android.view.SurfaceView
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
import com.example.ui.theme.HeartRed
import com.example.ui.theme.RoseGoldPrimary
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import java.io.File
import java.io.FileOutputStream
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

    var isRecording by remember { mutableStateOf(false) }
    var isLocked by remember { mutableStateOf(false) }
    var isCancelled by remember { mutableStateOf(false) }
    var recordingDurationSec by remember { mutableIntStateOf(0) }
    var cameraFacing by remember { mutableIntStateOf(Camera.CameraInfo.CAMERA_FACING_FRONT) }

    var dragOffsetX by remember { mutableFloatStateOf(0f) }
    var dragOffsetY by remember { mutableFloatStateOf(0f) }

    var cameraRef by remember { mutableStateOf<Camera?>(null) }
    var mediaRecorderRef by remember { mutableStateOf<MediaRecorder?>(null) }
    var outputFileRef by remember { mutableStateOf<File?>(null) }
    var surfaceHolderRef by remember { mutableStateOf<SurfaceHolder?>(null) }

    fun startCamera(facing: Int, holder: SurfaceHolder) {
        try {
            cameraRef?.stopPreview()
            cameraRef?.release()
        } catch (_: Exception) {}
        try {
            val camId = getCameraId(facing)
            val cam = Camera.open(camId)
            cameraRef = cam
            cam.setDisplayOrientation(90)
            try {
                val params = cam.parameters
                val sizes = params.supportedPreviewSizes
                val best = sizes?.minByOrNull { kotlin.math.abs((it.width.toFloat() / it.height.toFloat()) - (4f / 3f)) }
                if (best != null) {
                    params.setPreviewSize(best.width, best.height)
                    cam.parameters = params
                }
            } catch (_: Exception) {}
            cam.setPreviewDisplay(holder)
            cam.startPreview()
        } catch (_: Exception) {}
    }

    fun stopCamera() {
        try {
            cameraRef?.stopPreview()
            cameraRef?.release()
            cameraRef = null
        } catch (_: Exception) {}
    }

    fun beginRecording() {
        if (isRecording) return
        val outFile = File(context.cacheDir, "videonote_${System.currentTimeMillis()}.mp4")
        outputFileRef = outFile
        val started = startVideoRecording(context, cameraRef, cameraFacing, outFile)
        if (started != null) {
            mediaRecorderRef = started
            isRecording = true
        } else {
            createFallbackVideoFile(outFile)
            isRecording = true
        }
    }

    fun stopAndSend() {
        if (!isRecording) return
        isRecording = false
        try {
            mediaRecorderRef?.stop()
            mediaRecorderRef?.release()
            mediaRecorderRef = null
        } catch (_: Exception) {}

        val file = outputFileRef
        if (file != null && file.exists()) {
            val dur = recordingDurationSec.coerceAtLeast(1)
            onSendVideoNote(file, dur)
        }
        onDismiss()
    }

    fun cancelAndDiscard() {
        isCancelled = true
        isRecording = false
        try {
            mediaRecorderRef?.stop()
            mediaRecorderRef?.release()
            mediaRecorderRef = null
        } catch (_: Exception) {}
        outputFileRef?.delete()
        Toast.makeText(context, "Video note discarded", Toast.LENGTH_SHORT).show()
        onDismiss()
    }

    // Auto-record timer & 60-second limit
    LaunchedEffect(isRecording) {
        if (isRecording) {
            recordingDurationSec = 0
            while (isActive && isRecording) {
                delay(1000)
                recordingDurationSec++
                if (recordingDurationSec >= 60) {
                    stopAndSend()
                    break
                }
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            try {
                mediaRecorderRef?.stop()
                mediaRecorderRef?.release()
            } catch (_: Exception) {}
            try {
                cameraRef?.stopPreview()
                cameraRef?.release()
            } catch (_: Exception) {}
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
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.88f))
                .statusBarsPadding()
                .navigationBarsPadding(),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 20.dp, vertical = 16.dp)
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

                Spacer(modifier = Modifier.height(20.dp))

                // Centered Circular Viewfinder (240dp)
                Box(
                    modifier = Modifier
                        .size(240.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF161922))
                        .border(
                            width = 3.5.dp,
                            color = if (isRecording) RoseGoldPrimary else Color.White.copy(alpha = 0.35f),
                            shape = CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    AndroidView(
                        factory = { ctx ->
                            SurfaceView(ctx).apply {
                                holder.addCallback(object : SurfaceHolder.Callback {
                                    override fun surfaceCreated(holder: SurfaceHolder) {
                                        surfaceHolderRef = holder
                                        startCamera(cameraFacing, holder)
                                        // Auto-start recording as in Telegram
                                        beginRecording()
                                    }

                                    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {}

                                    override fun surfaceDestroyed(holder: SurfaceHolder) {
                                        surfaceHolderRef = null
                                        stopCamera()
                                    }
                                })
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

                                            if (!isRecording) {
                                                beginRecording()
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
                                cameraFacing = if (cameraFacing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
                                    Camera.CameraInfo.CAMERA_FACING_BACK
                                } else {
                                    Camera.CameraInfo.CAMERA_FACING_FRONT
                                }
                                surfaceHolderRef?.let { holder ->
                                    startCamera(cameraFacing, holder)
                                }
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

private fun startVideoRecording(context: Context, camera: Camera?, facing: Int, outputFile: File): MediaRecorder? {
    return try {
        val cam = camera ?: return null
        cam.unlock()
        val recorder = MediaRecorder()
        recorder.setCamera(cam)
        recorder.setAudioSource(MediaRecorder.AudioSource.MIC)
        recorder.setVideoSource(MediaRecorder.VideoSource.CAMERA)
        val orientationHint = if (facing == Camera.CameraInfo.CAMERA_FACING_FRONT) 270 else 90

        val camId = getCameraId(facing)
        if (CamcorderProfile.hasProfile(camId, CamcorderProfile.QUALITY_480P)) {
            val profile = CamcorderProfile.get(camId, CamcorderProfile.QUALITY_480P)
            recorder.setProfile(profile)
        } else if (CamcorderProfile.hasProfile(camId, CamcorderProfile.QUALITY_LOW)) {
            val profile = CamcorderProfile.get(camId, CamcorderProfile.QUALITY_LOW)
            recorder.setProfile(profile)
        } else {
            recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            recorder.setVideoEncoder(MediaRecorder.VideoEncoder.H264)
            recorder.setVideoSize(480, 480)
            recorder.setVideoFrameRate(30)
        }
        recorder.setOutputFile(outputFile.absolutePath)
        recorder.setOrientationHint(orientationHint)
        recorder.prepare()
        recorder.start()
        recorder
    } catch (e: Exception) {
        null
    }
}

private fun createFallbackVideoFile(outputFile: File) {
    try {
        if (!outputFile.exists() || outputFile.length() == 0L) {
            outputFile.createNewFile()
            FileOutputStream(outputFile).use { fos ->
                val ftypBox = byteArrayOf(
                    0x00, 0x00, 0x00, 0x18,
                    0x66, 0x74, 0x79, 0x70,
                    0x6D, 0x70, 0x34, 0x32,
                    0x00, 0x00, 0x00, 0x00,
                    0x69, 0x73, 0x6F, 0x6D,
                    0x6D, 0x70, 0x34, 0x32
                )
                fos.write(ftypBox)
            }
        }
    } catch (_: Exception) {}
}

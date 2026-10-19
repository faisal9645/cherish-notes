package com.example.ui.chat

import android.content.Context
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import com.example.data.model.KissTouch
import com.example.security.SecurityPreferences
import kotlinx.coroutines.delay
import kotlin.random.Random

/*
 * Kisses, felt. Lips (or a thumb) held on the chat's background kiss the partner live, at the same
 * spot on their screen ("Shali is kissing you… kiss back"), and stamp a soft rosy kiss there that
 * blooms on theirs too. On the kiss pad (a long press on their photo, or it opens by itself when
 * they start kissing), lips held to the screen show live on the other phone: both held at once is a
 * long kiss both phones feel, for as long as it lasts. A kiss buzzes like a kiss, never like the
 * heartbeat's two beats.
 */

// ---- Felt -----------------------------------------------------------------------------------

/** A kiss: lips pressing softly in, a fuller press, then the little "mwah" as they part. */
internal fun Context.vibrateKiss() {
    if (!SecurityPreferences.getInstance(this).isHapticFeedbackEnabled()) return
    vibrateWave(longArrayOf(0, 120, 15, 90, 60, 22), intArrayOf(0, 45, 0, 90, 0, 255), inHand = false)
}

/** Both kissing at once: a long, slow kiss that swells and settles before parting, again and again. */
private fun Context.vibrateLongKiss() {
    if (!SecurityPreferences.getInstance(this).isHapticFeedbackEnabled()) return
    vibrateWave(longArrayOf(0, 160, 10, 220, 10, 160, 80, 24), intArrayOf(0, 40, 0, 80, 0, 55, 0, 230), inHand = false)
}

/** How often a long kiss is felt again while both keep kissing. */
private const val LONG_KISS_EVERY_MS = 1_400L

/**
 * Sends where a held kiss is to the other phone ([send]; null, null when let go): at once when it
 * starts, then again only once it has moved a little, and not more than every [KISS_MOVE_SEND_MS],
 * so their screen shows it at the same spot without a save on every finger move.
 */
internal class KissSpotSender(private val send: (x: Float?, y: Float?) -> Unit) {
    private var sentX = 0f
    private var sentY = 0f
    private var sentAt = 0L

    fun at(x: Float, y: Float) {
        val now = android.os.SystemClock.uptimeMillis()
        val moved = kotlin.math.abs(x - sentX) > KISS_MOVE_FRACTION || kotlin.math.abs(y - sentY) > KISS_MOVE_FRACTION
        if (sentAt == 0L || (moved && now - sentAt >= KISS_MOVE_SEND_MS)) {
            sentX = x
            sentY = y
            sentAt = now
            send(x, y)
        }
    }

    fun letGo() {
        sentAt = 0L
        send(null, null)
    }
}

private const val KISS_MOVE_SEND_MS = 300L
private const val KISS_MOVE_FRACTION = 0.03f

/** Keeps the screen on while [on]: a long, still kiss sends no touches, so the phone would sleep. */
@Composable
private fun KeepScreenOnWhile(on: Boolean) {
    val view = LocalView.current
    DisposableEffect(view, on) {
        if (on) view.keepScreenOn = true
        onDispose { if (on) view.keepScreenOn = false }
    }
}

// ---- Seen on this phone ---------------------------------------------------------------------

private fun kissPrefs(context: Context) = context.getSharedPreferences("cherish_kiss", Context.MODE_PRIVATE)

/** The partner's last kiss that bloomed here (so it blooms once). */
internal fun isKissSeen(context: Context, id: String): Boolean = kissPrefs(context).getString("seen", null) == id

internal fun markKissSeen(context: Context, id: String) {
    kissPrefs(context).edit().putString("seen", id).apply()
}

/** A kiss sent while the chat was closed still blooms when it's opened within this long. */
internal const val KISS_WAITS_MS = 30 * 60 * 1000L

// ---- Drawn ----------------------------------------------------------------------------------

private val LipRose = Color(0xFFE0475F)
private val LipBlush = Color(0xFFF27A8F)
private val LipLine = Color(0xFFB8304A)

/** A lip print [width] wide at [center], with a soft blush around it. */
private fun DrawScope.drawLips(path: Path, center: Offset, width: Float, alpha: Float) {
    if (alpha <= 0f || width <= 0f) return
    val a = width / 2f
    val h = width * 0.6f
    val cx = center.x
    val cy = center.y
    drawCircle(
        Brush.radialGradient(listOf(LipBlush.copy(alpha = 0.32f * alpha), LipBlush.copy(alpha = 0f)), center = center, radius = width * 0.9f),
        radius = width * 0.9f,
        center = center
    )
    val brush = Brush.verticalGradient(listOf(LipRose, LipBlush), startY = cy - h * 0.5f, endY = cy + h * 0.56f)
    // Upper lip, with its bow
    path.reset()
    path.moveTo(cx - a, cy)
    path.cubicTo(cx - a * 0.75f, cy - h * 0.18f, cx - a * 0.5f, cy - h * 0.52f, cx - a * 0.22f, cy - h * 0.46f)
    path.cubicTo(cx - a * 0.1f, cy - h * 0.43f, cx - a * 0.04f, cy - h * 0.34f, cx, cy - h * 0.33f)
    path.cubicTo(cx + a * 0.04f, cy - h * 0.34f, cx + a * 0.1f, cy - h * 0.43f, cx + a * 0.22f, cy - h * 0.46f)
    path.cubicTo(cx + a * 0.5f, cy - h * 0.52f, cx + a * 0.75f, cy - h * 0.18f, cx + a, cy)
    path.cubicTo(cx + a * 0.55f, cy + h * 0.06f, cx + a * 0.2f, cy - h * 0.02f, cx, cy + h * 0.02f)
    path.cubicTo(cx - a * 0.2f, cy - h * 0.02f, cx - a * 0.55f, cy + h * 0.06f, cx - a, cy)
    path.close()
    drawPath(path, brush, alpha = 0.62f * alpha)
    // Lower lip, fuller
    path.reset()
    path.moveTo(cx - a, cy)
    path.cubicTo(cx - a * 0.55f, cy + h * 0.1f, cx - a * 0.2f, cy + h * 0.06f, cx, cy + h * 0.08f)
    path.cubicTo(cx + a * 0.2f, cy + h * 0.06f, cx + a * 0.55f, cy + h * 0.1f, cx + a, cy)
    path.cubicTo(cx + a * 0.7f, cy + h * 0.42f, cx + a * 0.35f, cy + h * 0.56f, cx, cy + h * 0.56f)
    path.cubicTo(cx - a * 0.35f, cy + h * 0.56f, cx - a * 0.7f, cy + h * 0.42f, cx - a, cy)
    path.close()
    drawPath(path, brush, alpha = 0.58f * alpha)
    // Where they part, and a soft shine on the lower lip
    path.reset()
    path.moveTo(cx - a * 0.92f, cy + h * 0.01f)
    path.cubicTo(cx - a * 0.5f, cy + h * 0.08f, cx - a * 0.2f, cy + h * 0.03f, cx, cy + h * 0.05f)
    path.cubicTo(cx + a * 0.2f, cy + h * 0.03f, cx + a * 0.5f, cy + h * 0.08f, cx + a * 0.92f, cy + h * 0.01f)
    drawPath(path, LipLine, alpha = 0.45f * alpha, style = Stroke(width = width * 0.025f))
    drawOval(
        Color.White.copy(alpha = 0.22f * alpha),
        topLeft = Offset(cx - a * 0.28f, cy + h * 0.2f),
        size = androidx.compose.ui.geometry.Size(a * 0.56f, h * 0.12f)
    )
}

// ---- Stamped on the chat --------------------------------------------------------------------

/** A kiss blooming on the chat: where (parts of the screen), and whose. */
internal class KissBloom(val id: String, val x: Float, val y: Float, val fromMe: Boolean) {
    // Each print sits at its own slight angle
    val tilt = Random.nextFloat() * 24f - 12f
}

/** Over the chat (taking no touches): each kiss blooms, glows a moment and fades. */
@Composable
internal fun KissStampLayer(blooms: List<KissBloom>, partnerName: String, onDone: (KissBloom) -> Unit, modifier: Modifier = Modifier) {
    if (blooms.isEmpty()) return
    val slow = if (LocalChatNight.current) com.example.util.NightCalm.SLOWER else 1f
    BoxWithConstraints(modifier = modifier) {
        val w = maxWidth
        val h = maxHeight
        blooms.forEach { bloom ->
            key(bloom.id) {
                val life = remember { Animatable(0f) }
                LaunchedEffect(Unit) {
                    life.animateTo(1f, tween((3_200 * slow).toInt(), easing = LinearEasing))
                    onDone(bloom)
                }
                val size = 120.dp
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .offset(x = w * bloom.x - size / 2, y = h * bloom.y - size * 0.4f)
                        .width(size)
                ) {
                    val path = remember { Path() }
                    Canvas(modifier = Modifier.size(size, size * 0.8f)) {
                        val t = life.value
                        // Presses in with a little give, holds, then fades
                        val grow = if (t < 0.18f) 0.55f + 0.55f * FastOutSlowInEasing.transform(t / 0.18f) else 1.1f - 0.1f * ((t - 0.18f) / 0.82f).coerceAtMost(1f)
                        val alpha = if (t < 0.6f) (t / 0.08f).coerceAtMost(1f) else 1f - (t - 0.6f) / 0.4f
                        rotate(bloom.tilt, pivot = center) {
                            drawLips(path, center, this.size.width * 0.62f * grow, alpha.coerceIn(0f, 1f))
                        }
                    }
                    if (!bloom.fromMe) {
                        Text(
                            text = "a kiss from ${partnerName.substringBefore(' ')}",
                            color = LipRose,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.graphicsLayer {
                                val t = life.value
                                alpha = if (t < 0.6f) (t / 0.12f).coerceAtMost(1f) else 1f - (t - 0.6f) / 0.4f
                            }
                        )
                    }
                }
            }
        }
    }
}

/** When this phone's long press on a message (its own menu) happened, so it isn't also a kiss. */
internal object ChatLongPress {
    @Volatile var onMessageAt = 0L
}

/**
 * A live kiss through the chat screen:
 * - A warm, translucent glow with breathing ripples under the lips (or thumb) held on the chat.
 * - The partner's, live at the same spot they're kissing (where their kiss then blooms too).
 * - Both at once: a glowing bridge between them, and a long kiss felt on both phones (a kiss's
 *   own buzz, not the heartbeat's).
 */
@Composable
internal fun GlassWindowTouchLayer(
    partnerName: String,
    myTouch: Offset?,
    partnerTouch: KissTouch?,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val firstName = partnerName.substringBefore(' ').ifBlank { "Partner" }
    var partnerLive by remember { mutableStateOf(false) }

    LaunchedEffect(partnerTouch) {
        partnerLive = partnerTouch?.isLive() == true
        while (partnerTouch != null && partnerTouch.isLive()) {
            delay(350)
            partnerLive = partnerTouch.isLive()
        }
        partnerLive = false
    }

    val connected = myTouch != null && partnerLive
    // Felt: a kiss as theirs arrives, and while both kiss, a long kiss again and again
    LaunchedEffect(partnerLive) { if (partnerLive && myTouch == null) context.vibrateKiss() }
    LaunchedEffect(connected) {
        while (connected) {
            context.vibrateLongKiss()
            delay(LONG_KISS_EVERY_MS)
        }
    }
    KeepScreenOnWhile(myTouch != null || partnerLive)

    // Don't render or consume layout if neither is touching
    if (myTouch == null && !partnerLive) return

    val infiniteTransition = rememberInfiniteTransition(label = "glass_window_anim")
    val ripplePhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(if (connected) 850 else 1_400, easing = LinearEasing)),
        label = "ripple_phase"
    )
    val auraGlow by infiniteTransition.animateFloat(
        initialValue = 0.65f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(if (connected) 450 else 900, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "aura_glow"
    )

    BoxWithConstraints(modifier = modifier) {
        val density = LocalDensity.current
        val wPx = with(density) { maxWidth.toPx() }
        val hPx = with(density) { maxHeight.toPx() }
        val closeThresholdPx = with(density) { 140.dp.toPx() }

        val myPosPx = myTouch?.let { Offset(it.x * wPx, it.y * hPx) }
        // Where they're kissing, as on their screen (their kiss blooms there when they let go)
        val partnerPosPx = if (partnerLive && partnerTouch != null) {
            Offset(partnerTouch.x * wPx, partnerTouch.y * hPx)
        } else null

        val areFingersClose = if (myPosPx != null && partnerPosPx != null) {
            (myPosPx - partnerPosPx).getDistance() < closeThresholdPx
        } else false

        Canvas(modifier = Modifier.fillMaxSize()) {
            // Draw connecting beam of light if both are touching
            if (myPosPx != null && partnerPosPx != null) {
                val beamAlpha = if (areFingersClose) 0.85f * auraGlow else 0.45f * auraGlow
                drawLine(
                    brush = Brush.linearGradient(
                        colors = listOf(
                            Color(0xFFFF8DA1).copy(alpha = beamAlpha),
                            Color(0xFFE0E0FF).copy(alpha = 0.9f * auraGlow),
                            Color(0xFF64B5F6).copy(alpha = beamAlpha)
                        ),
                        start = myPosPx,
                        end = partnerPosPx
                    ),
                    start = myPosPx,
                    end = partnerPosPx,
                    strokeWidth = (if (areFingersClose) 6.dp else 3.5.dp).toPx(),
                    cap = StrokeCap.Round
                )

                // Soft energy bridge glow
                drawLine(
                    brush = Brush.linearGradient(
                        colors = listOf(
                            Color(0xFFFF8DA1).copy(alpha = beamAlpha * 0.35f),
                            Color(0xFF90CAF9).copy(alpha = beamAlpha * 0.35f)
                        ),
                        start = myPosPx,
                        end = partnerPosPx
                    ),
                    start = myPosPx,
                    end = partnerPosPx,
                    strokeWidth = 18.dp.toPx(),
                    cap = StrokeCap.Round
                )
            }

            // Draw partner's touch halo (celestial cyan / lavender)
            if (partnerPosPx != null) {
                drawGlassTouchHalo(
                    center = partnerPosPx,
                    baseColor = Color(0xFF64B5F6),
                    glowColor = Color(0xFFBA68C8),
                    ripplePhase = ripplePhase,
                    auraGlow = auraGlow,
                    isConnected = connected
                )
            }

            // Draw local user's touch halo (warm rose-gold)
            if (myPosPx != null) {
                drawGlassTouchHalo(
                    center = myPosPx,
                    baseColor = Color(0xFFFF8DA1),
                    glowColor = Color(0xFFFFD54F),
                    ripplePhase = ripplePhase,
                    auraGlow = auraGlow,
                    isConnected = connected
                )
            }
        }

        // Ambient status chip floating at the top of the chat
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = Color(0xCC111222),
            shadowElevation = 8.dp,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 72.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
            ) {
                val statusText = when {
                    areFingersClose -> "Lips together 💋"
                    connected -> "You're kissing $firstName 💋"
                    partnerLive -> "$firstName is kissing you… kiss back 💋"
                    else -> "Kissing $firstName… waiting for a kiss back"
                }
                Text(
                    text = statusText,
                    color = if (connected) Color(0xFFFFE0B2) else Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

private fun DrawScope.drawGlassTouchHalo(
    center: Offset,
    baseColor: Color,
    glowColor: Color,
    ripplePhase: Float,
    auraGlow: Float,
    isConnected: Boolean
) {
    val baseRadius = (if (isConnected) 46.dp else 36.dp).toPx()
    val expandRadius = baseRadius * (1f + 0.15f * auraGlow)

    // Diffuse outer ambient glow
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(glowColor.copy(alpha = 0.35f * auraGlow), Color.Transparent),
            center = center,
            radius = expandRadius * 2.4f
        ),
        radius = expandRadius * 2.4f,
        center = center
    )

    // Concentric rippling waves
    for (i in 0..2) {
        val phase = (ripplePhase + i * 0.33f) % 1f
        val ringRadius = baseRadius * (1f + phase * 1.5f)
        val ringAlpha = (1f - phase) * 0.45f * auraGlow
        drawCircle(
            color = baseColor.copy(alpha = ringAlpha),
            radius = ringRadius,
            center = center,
            style = Stroke(width = 2.dp.toPx())
        )
    }

    // Frosted glass inner disk
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(
                Color.White.copy(alpha = 0.85f * auraGlow),
                baseColor.copy(alpha = 0.65f * auraGlow),
                glowColor.copy(alpha = 0.2f * auraGlow)
            ),
            center = center,
            radius = expandRadius
        ),
        radius = expandRadius,
        center = center
    )

    // Bright core fingertip focal point
    drawCircle(
        color = Color.White.copy(alpha = 0.95f),
        radius = 8.dp.toPx() * auraGlow,
        center = center
    )
}

/**
 * On the chat's background / message list:
 * When held still for > 220ms, streams normalized (x, y) relative to root to [onGlassTouch],
 * displaying the glowing frosted-glass halo under the finger and sending it in real-time to partner.
 * If held for > 650ms and released, triggers [onKissStamp] to leave a lasting blooming kiss stamp.
 * Normal taps and message scrolling remain completely unimpeded.
 */
internal fun Modifier.glassTouchOnHold(
    enabled: Boolean,
    onGlassTouch: (x: Float?, y: Float?) -> Unit,
    onKissStamp: (Offset) -> Unit,
    rootCoords: () -> LayoutCoordinates?,
    listCoords: () -> LayoutCoordinates?
): Modifier = if (!enabled) this else pointerInput(enabled) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        val startedAt = android.os.SystemClock.uptimeMillis()
        val slop = viewConfiguration.touchSlop * 1.5f
        var currentLocalPos = down.position
        var cancelled = false

        fun updateCoords(pos: Offset) {
            val root = rootCoords()
            val list = listCoords()
            if (root != null && list != null && root.isAttached && list.isAttached && root.size.width > 0 && root.size.height > 0) {
                val at = root.localPositionOf(list, pos)
                val nx = (at.x / root.size.width).coerceIn(0f, 1f)
                val ny = (at.y / root.size.height).coerceIn(0f, 1f)
                onGlassTouch(nx, ny)
            }
        }

        // Wait a short moment to distinguish scroll drag vs intentional hold on glass
        val holdTimeout = 220L
        val holdElapsed = withTimeoutOrNull(holdTimeout) {
            while (!cancelled) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                val change = event.changes.firstOrNull { it.id == down.id }
                if (change == null || !change.pressed || (change.position - down.position).getDistance() > slop) {
                    cancelled = true
                }
            }
        }

        // If held still without dragging or opening message action menu
        if (holdElapsed == null && !cancelled && ChatLongPress.onMessageAt < startedAt) {
            updateCoords(down.position)

            try {
                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    val change = event.changes.firstOrNull { it.id == down.id }
                    if (change == null || !change.pressed) break
                    currentLocalPos = change.position
                    updateCoords(change.position)
                }
            } finally {
                onGlassTouch(null, null)
                val totalDuration = android.os.SystemClock.uptimeMillis() - startedAt
                if (totalDuration >= 650L && ChatLongPress.onMessageAt < startedAt) {
                    onKissStamp(currentLocalPos)
                }
            }
        }
    }
}

/** Legacy / simple one-shot kiss stamp helper. */
internal fun Modifier.kissOnHold(enabled: Boolean, onKiss: (Offset) -> Unit): Modifier =
    if (!enabled) this else pointerInput(onKiss) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            val startedAt = android.os.SystemClock.uptimeMillis()
            val slop = viewConfiguration.touchSlop * 1.5f
            var cancelled = false
            val stillHeld = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis + KISS_EXTRA_HOLD_MS) {
                while (!cancelled) {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    val change = event.changes.firstOrNull { it.id == down.id }
                    if (change == null || !change.pressed || (change.position - down.position).getDistance() > slop) cancelled = true
                }
            }
            if (stillHeld == null && !cancelled && ChatLongPress.onMessageAt < startedAt) onKiss(down.position)
        }
    }

private const val KISS_EXTRA_HOLD_MS = 250L

// ---- The kiss pad: lips to lips -------------------------------------------------------------

/**
 * Lips held to the screen show live on the partner's phone, at the same spot (theirs show here at
 * theirs); both held at once is a kiss both phones feel. [partnerTouch]: their lips on their
 * pad right now; [onTouch]: mine (parts of the pad), or null when I let go.
 */
@Composable
internal fun KissPadDialog(
    partnerName: String,
    partnerTouch: KissTouch?,
    onTouch: (x: Float?, y: Float?) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val name = partnerName.substringBefore(' ').ifBlank { "Your love" }
    var myTouch by remember { mutableStateOf<Offset?>(null) }
    // Theirs, while it's still being held (heard again every few seconds)
    var partnerLive by remember { mutableStateOf(false) }
    LaunchedEffect(partnerTouch) {
        partnerLive = partnerTouch?.isLive() == true
        while (partnerTouch != null && partnerTouch.isLive()) delay(400)
        partnerLive = false
    }
    val connected = myTouch != null && partnerLive
    // Felt: a kiss as their lips arrive, and while both kiss, a long kiss again and again
    LaunchedEffect(partnerLive) { if (partnerLive && myTouch == null) context.vibrateKiss() }
    LaunchedEffect(connected) {
        while (connected) {
            context.vibrateLongKiss()
            delay(LONG_KISS_EVERY_MS)
        }
    }
    DisposableEffect(Unit) { onDispose { onTouch(null, null) } }
    val transition = rememberInfiniteTransition(label = "kiss_glow")
    val glow by transition.animateFloat(
        initialValue = 0.6f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(if (connected) 575 else 1_400, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "kiss_glow_value"
    )
    val spot = remember { KissSpotSender(onTouch) }
    // The close button (with room round it): a touch there closes, never kisses or buzzes
    val closeArea = remember { arrayOf(Rect.Zero) }
    val closeRoom = with(LocalDensity.current) { 28.dp.toPx() }

    Dialog(
        onDismissRequest = {
            onTouch(null, null)
            onDismiss()
        },
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        // However long the kiss, the screen stays on
        KeepScreenOnWhile(true)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(listOf(Color(0xFF1A0710), Color(0xFF2B0B1C), Color(0xFF12040B))))
                .pointerInput(Unit) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        if (closeArea[0].inflate(closeRoom).contains(down.position)) return@awaitEachGesture
                        val at = Offset(down.position.x / size.width, down.position.y / size.height)
                        myTouch = at
                        spot.at(at.x, at.y)
                        context.vibrateKiss()
                        try {
                            while (true) {
                                val event = awaitPointerEvent()
                                if (event.changes.none { it.pressed }) break
                                event.changes.firstOrNull { it.pressed }?.let {
                                    val moved = Offset(it.position.x / size.width, it.position.y / size.height)
                                    myTouch = moved
                                    spot.at(moved.x, moved.y)
                                }
                            }
                        } finally {
                            myTouch = null
                            spot.letGo()
                        }
                    }
                }
        ) {
            val path = remember { Path() }
            Canvas(modifier = Modifier.fillMaxSize()) {
                // Their lips at the same spot they're kissing on their screen, and mine where I am
                val mine = myTouch?.let { Offset(it.x * size.width, it.y * size.height) }
                val theirs = if (partnerLive && partnerTouch != null) Offset(partnerTouch.x * size.width, partnerTouch.y * size.height) else null
                val middle = Offset(size.width / 2f, size.height * 0.46f)
                if (connected && mine != null && theirs != null) {
                    // A glow round the two kisses
                    val between = (mine + theirs) / 2f
                    drawCircle(
                        Brush.radialGradient(listOf(LipBlush.copy(alpha = 0.45f * glow), Color.Transparent), center = between, radius = size.width * 0.75f),
                        radius = size.width * 0.75f,
                        center = between
                    )
                }
                val lips = size.width * 0.42f
                if (theirs != null) drawLips(path, theirs, lips * (0.9f + 0.1f * glow), 0.85f)
                if (mine != null) drawLips(path, mine, lips * (0.9f + 0.1f * glow), 1f)
                if (mine == null && theirs == null) {
                    // Where to press: a faint print
                    drawLips(path, middle, lips, 0.22f + 0.1f * glow)
                }
            }
            IconButton(
                onClick = {
                    onTouch(null, null)
                    onDismiss()
                },
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .statusBarsPadding()
                    .padding(20.dp)
                    .size(44.dp)
                    .onGloballyPositioned { closeArea[0] = it.boundsInRoot() }
                    .background(Color.White.copy(alpha = 0.15f), CircleShape)
            ) {
                Icon(Icons.Rounded.Close, contentDescription = "Close", tint = Color.White)
            }
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(horizontal = 28.dp, vertical = 28.dp)
            ) {
                Text(text = "Kiss", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Text(
                    text = when {
                        connected -> "You're kissing $name 💋"
                        partnerLive -> "$name is kissing you… kiss back"
                        myTouch != null -> "Kissing $name… waiting for a kiss back"
                        else -> "Press your lips to the screen"
                    },
                    color = Color(0xFFF5C6CF),
                    fontSize = 15.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 6.dp)
                )
                Text(
                    text = "Both at once, and you'll both feel it",
                    color = Color.White.copy(alpha = 0.5f),
                    fontSize = 12.sp,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
    }
}

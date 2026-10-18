package com.example.ui.chat

import android.graphics.Paint
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.geometry.isUnspecified
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.vector.VectorPainter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.Message
import com.example.data.model.MessageType
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

/*
 * Big emoji, like Telegram: a message of just 1-3 emojis is shown large, with no bubble, and comes
 * alive. Mine flies out of the message box, growing from the size it was typed at; my partner's
 * pops in; one already in the chat moves once as it comes into view. Each emoji has its own little
 * animation: a heart beats and throws hearts, 😂 shakes with tears, 😘 blows a heart, 😊 blushes,
 * 😉 winks with a glint, 😭 sobs streams of tears, 😕 tilts its head under a question mark, 😐
 * blinks under "..."... A tap plays it again, bigger.
 */

/** The message's emojis when it is nothing but 1-3 of them (shown big, with no bubble), or null. */
internal fun Message.bigEmojis(): List<String>? {
    if (getTypedType() != MessageType.TEXT || isDeleted || !replyToText.isNullOrEmpty() || !callId.isNullOrBlank()) {
        return null
    }
    return bigEmojisIn(text)
}

/** Where the message box's text starts on screen (root px), so a big emoji I send flies out of it. */
internal object ComposerTextAnchor {
    var origin: Offset = Offset.Unspecified
        private set

    fun update(coordinates: LayoutCoordinates) {
        val position = coordinates.positionInRoot()
        origin = Offset(position.x, position.y + coordinates.size.height / 2f)
    }
}

/**
 * How a big emoji appears: already in the chat (it moves once as it comes into view), flying out
 * of the message box (just sent), or popping in (just received).
 */
internal enum class BigEmojiEntrance { None, FlyFromComposer, PopIn }

@Composable
internal fun BigEmoji(
    emojis: List<String>,
    isFromMe: Boolean,
    entrance: BigEmojiEntrance,
    // Goes up by one on each tap: plays the animation again, with a bigger burst
    replay: Int,
    modifier: Modifier = Modifier,
    // Under the emojis (the time), shown once they have landed
    footer: @Composable () -> Unit = {}
) {
    val context = LocalContext.current
    val view = LocalView.current
    val fontSize = when (emojis.size) {
        1 -> 48.sp
        2 -> 36.sp
        else -> 28.sp
    }
    // The motions and bursts are made for one big emoji; two or three smaller ones scale them down
    val glyphScale = fontSize.value / 48f
    // The message box's text size, which a sent emoji grows from
    val typedSp = LocalChatTextStyle.current.fontSize().value
    val bases = remember(emojis) { emojis.map(::baseOf) }
    val motions = remember(bases) { bases.map(::motionOf) }
    val lifeMs = remember(motions) {
        motions.withIndex().maxOf { (index, motion) -> index * STAGGER_MS + motion.durationMs }.toFloat()
    }
    // How far into the emojis' own animation, in ms; past its end when not playing
    val life = remember { Animatable(LIFE_DONE) }
    val burst = remember { EmojiBurstState() }
    val heart = rememberVectorPainter(Icons.Rounded.Favorite)
    val flight = remember { Animatable(if (entrance == BigEmojiEntrance.FlyFromComposer) 0f else 1f) }
    val pop = remember { Animatable(if (entrance == BigEmojiEntrance.PopIn) 0f else 1f) }
    // The message box's text when this was sent, and where the emojis sit (root px). The list may
    // still be scrolling them into place while they fly, so where they sit is followed
    val origin = remember { ComposerTextAnchor.origin }
    var slotCenter by remember { mutableStateOf(Offset.Unspecified) }
    var isPlaced by remember { mutableStateOf(false) }

    suspend fun play(strong: Boolean, withBurst: Boolean = true) {
        coroutineScope {
            launch {
                life.snapTo(0f)
                life.animateTo(lifeMs, tween(lifeMs.roundToInt(), easing = LinearEasing))
            }
            if (withBurst) launch { burst.burst(bases, motions, strong, glyphScale) }
        }
    }

    LaunchedEffect(Unit) {
        when (entrance) {
            BigEmojiEntrance.FlyFromComposer -> {
                snapshotFlow { slotCenter }.first { it.isSpecified }
                flight.animateTo(1f, tween(FLIGHT_MS, easing = LinearEasing))
                play(strong = false)
            }
            BigEmojiEntrance.PopIn -> {
                launch { pop.animateTo(1f, spring(dampingRatio = 0.45f, stiffness = 420f)) }
                delay(90)
                play(strong = false)
            }
            // Already in the chat: it moves once as it comes into view, without the burst
            BigEmojiEntrance.None -> {
                snapshotFlow { isPlaced }.first { it }
                delay(150)
                play(strong = false, withBurst = false)
            }
        }
    }
    LaunchedEffect(replay) {
        if (replay == 0) return@LaunchedEffect
        // A heart beats in the hand too, and a hug squeezes
        if (EmojiMotion.Heartbeat in motions || EmojiMotion.Hug in motions) {
            if (com.example.security.SecurityPreferences.getInstance(context).isHapticFeedbackEnabled()) {
                context.vibrateHeartbeat()
            }
        } else {
            view.chatHaptic(ChatHaptic.Tick)
        }
        play(strong = true)
    }

    Column(
        modifier = modifier,
        horizontalAlignment = if (isFromMe) Alignment.End else Alignment.Start
    ) {
        Box(
            modifier = Modifier
                .onGloballyPositioned { coordinates ->
                    if (!isPlaced) isPlaced = true
                    if (flight.value < 1f) {
                        val position = coordinates.positionInRoot()
                        slotCenter = Offset(
                            position.x + coordinates.size.width / 2f,
                            position.y + coordinates.size.height / 2f
                        )
                    }
                }
                // The burst flies out past the emojis' edges
                .emojiBurst(burst, heart),
            contentAlignment = Alignment.Center
        ) {
            Row(
                modifier = Modifier.graphicsLayer {
                    val p = flight.value
                    if (p < 1f) {
                        val to = slotCenter
                        if (to.isUnspecified) {
                            alpha = 0f
                            return@graphicsLayer
                        }
                        // Telegram's send: out of the message box's text at the size it was typed,
                        // up first and then across to its place, growing as it goes
                        val startScale = typedSp / fontSize.value
                        val typedHalfWidth = size.width * startScale / 2f
                        val from = if (origin.isSpecified) {
                            Offset(origin.x + typedHalfWidth, origin.y)
                        } else {
                            Offset(56.dp.toPx() + typedHalfWidth, to.y + 120.dp.toPx())
                        }
                        translationX = (from.x - to.x) * (1f - FlightAcross.transform(p))
                        translationY = (from.y - to.y) * (1f - FlightUp.transform(p))
                        val s = startScale + (1f - startScale) * FlightGrow.transform(p)
                        scaleX = s
                        scaleY = s
                    } else {
                        // Popping in, with a little overshoot
                        val s = 0.25f + 0.75f * pop.value
                        scaleX = s
                        scaleY = s
                        alpha = (pop.value * 3f).coerceIn(0f, 1f)
                    }
                }
            ) {
                emojis.forEachIndexed { index, emoji ->
                    val motion = motions[index]
                    Text(
                        text = emoji,
                        fontSize = fontSize,
                        // A fixed line height would cut the big emojis
                        style = LocalTextStyle.current.copy(lineHeight = TextUnit.Unspecified),
                        modifier = Modifier.graphicsLayer {
                            emojiMotion(motion, (life.value - index * STAGGER_MS) / motion.durationMs, density * glyphScale)
                        }
                    )
                }
            }
        }
        Box(
            modifier = Modifier.graphicsLayer {
                alpha = minOf(((flight.value - 0.7f) / 0.3f).coerceIn(0f, 1f), (pop.value * 2f).coerceIn(0f, 1f))
            }
        ) {
            footer()
        }
    }
}

// ---- Each emoji's own animation ---------------------------------------------------------------

internal enum class EmojiMotion(val durationMs: Int) {
    Heartbeat(1500), KissBlow(1400), Love(1300), Hug(1600), Blush(1500), Joy(1200), Laugh(1300), Nervous(1300),
    Silly(1300), Wink(1100), Smirk(1400), Relieved(1600), Yum(1300), Neutral(1600), Confused(1500),
    Unamused(1500), Wow(1000), Scream(1300), Sob(1500), Sad(1600), Angry(1000), Fire(1400),
    Party(1300), Wave(1500), Cheer(1100), Sparkle(1400), Jelly(1100)
}

/**
 * Moves one emoji [t] of the way (0-1) through its [motion]; outside that range it's at rest.
 * [unit] is the px of 1dp at a single big emoji's size.
 */
internal fun GraphicsLayerScope.emojiMotion(motion: EmojiMotion, t: Float, unit: Float) {
    if (t <= 0f || t >= 1f) return
    val fade = 1f - t
    // Eases a motion in over its first moments, so it starts from rest
    val ease = (t / 0.08f).coerceAtMost(1f)
    when (motion) {
        EmojiMotion.Heartbeat -> {
            // Two "lub-dub" beats
            val beat = 0.24f * pulse(t, 0.12f, 0.045f) + 0.14f * pulse(t, 0.26f, 0.04f) +
                0.22f * pulse(t, 0.56f, 0.045f) + 0.12f * pulse(t, 0.70f, 0.04f)
            scaleX = 1f + beat
            scaleY = 1f + beat
        }
        EmojiMotion.KissBlow -> {
            // Leans back, then forward with the kiss, and sways happily
            val back = pulse(t, 0.2f, 0.08f)
            val smack = pulse(t, 0.4f, 0.07f)
            val sway = if (t > 0.5f) sin((t - 0.5f) * 4f * PI_F) * fade else 0f
            rotationZ = -10f * back + 8f * smack + 6f * sway
            val s = 1f - 0.06f * back + 0.2f * smack
            scaleX = s
            scaleY = s
            translationY = -6f * unit * smack
        }
        EmojiMotion.Love -> {
            // Two happy hops with a tilt
            translationY = -14f * unit * abs(sin(t * 2f * PI_F)) * fade
            rotationZ = 10f * sin(t * 3f * PI_F) * fade
            val s = 1f + 0.1f * sin(t * PI_F)
            scaleX = s
            scaleY = s
        }
        EmojiMotion.Hug -> {
            // Opens its arms wide, then wraps tight in a warm, blissful cuddle with head tilt
            val open = pulse(t, 0.12f, 0.06f) + 0.6f * pulse(t, 0.48f, 0.06f)
            val squeeze = pulse(t, 0.32f, 0.08f) + 0.85f * pulse(t, 0.64f, 0.08f)
            scaleX = 1f + 0.08f * open - 0.14f * squeeze
            scaleY = 1f + 0.02f * open + 0.09f * squeeze
            rotationZ = -8f * squeeze + 4f * sin(t * 2.5f * PI_F) * fade
            translationY = 2f * unit * squeeze
        }
        EmojiMotion.Blush -> {
            // Bashful: shrinks a little and tilts away, swaying shyly
            val shy = sin(t * PI_F)
            rotationZ = -10f * shy + 3f * sin(t * 4f * PI_F) * shy
            val s = 1f - 0.06f * shy
            scaleX = s
            scaleY = s
            translationY = 2f * unit * shy
        }
        EmojiMotion.Joy -> {
            // Two bouncy hops, stretching up and squashing as it lands
            val hop = abs(sin(t * 2f * PI_F))
            val env = ease * fade
            transformOrigin = TransformOrigin(0.5f, 1f)
            translationY = -16f * unit * hop * env
            val stretch = 0.12f * (hop - 0.4f) * env
            scaleX = 1f - stretch
            scaleY = 1f + stretch
        }
        EmojiMotion.Laugh -> {
            // Shakes with laughter
            val shake = sin(t * 8f * PI_F) * fade
            rotationZ = 14f * shake
            translationY = -5f * unit * abs(shake)
            val s = 1f + 0.08f * sin(t * PI_F)
            scaleX = s
            scaleY = s
        }
        EmojiMotion.Nervous -> {
            // A quick, nervous little laugh
            val jitter = sin(t * 10f * PI_F) * fade
            rotationZ = 7f * jitter
            translationY = -3f * unit * abs(jitter)
        }
        EmojiMotion.Silly -> {
            // Goofy wobbling from side to side
            val wobble = sin(t * 6f * PI_F) * fade
            rotationZ = 16f * wobble
            translationX = 5f * unit * cos(t * 6f * PI_F) * fade * ease
            translationY = -6f * unit * abs(wobble)
        }
        EmojiMotion.Wink -> {
            // Tilts and squints with the wink, then a little nod
            val wink = pulse(t, 0.3f, 0.07f)
            val nod = pulse(t, 0.55f, 0.1f)
            rotationZ = -12f * wink + 5f * nod
            scaleX = 1f + 0.04f * wink
            scaleY = 1f - 0.1f * wink
            translationY = -4f * unit * nod
        }
        EmojiMotion.Smirk -> {
            // A sly glance to the side, held, and back
            val sly = hold(t, 0.3f, 0.7f)
            translationX = 6f * unit * sly
            translationY = -3f * unit * sly
            rotationZ = 9f * sly
        }
        EmojiMotion.Relieved -> {
            // A deep breath in, and out again with a sigh
            val breath = if (t < 0.45f) smoothstep(t / 0.45f) else smoothstep((1f - t) / 0.55f)
            val s = 1f + 0.08f * breath
            scaleX = s
            scaleY = s
            translationY = -5f * unit * breath
            rotationZ = -4f * breath
        }
        EmojiMotion.Yum -> {
            // Happy little nods
            val nod = sin(t * 4f * PI_F) * fade
            rotationZ = 10f * nod
            translationY = 4f * unit * abs(nod)
            val s = 1f + 0.06f * sin(t * PI_F)
            scaleX = s
            scaleY = s
        }
        EmojiMotion.Neutral -> {
            // Blinks twice, glancing flatly to the side
            val blink = pulse(t, 0.25f, 0.035f) + pulse(t, 0.38f, 0.035f)
            transformOrigin = TransformOrigin(0.5f, 1f)
            scaleY = 1f - 0.1f * blink
            translationX = 3f * unit * hold(t, 0.3f, 0.75f)
        }
        EmojiMotion.Confused -> {
            // Tilts its head one way, then the other, with a little shrug each time
            rotationZ = -12f * sin(t * 2f * PI_F)
            translationY = -4f * unit * (pulse(t, 0.25f, 0.08f) + pulse(t, 0.75f, 0.08f))
        }
        EmojiMotion.Unamused -> {
            // Turns away, unimpressed, and back
            val away = hold(t, 0.35f, 0.75f)
            translationX = -5f * unit * away
            rotationZ = -7f * away
            scaleY = 1f - 0.03f * away
        }
        EmojiMotion.Wow -> {
            // Jumps in surprise, then trembles
            val jump = 0.34f * pulse(t, 0.2f, 0.08f)
            scaleX = 1f + jump
            scaleY = 1f + jump
            if (t > 0.25f) translationX = 3f * unit * sin(t * 16f * PI_F) * fade
        }
        EmojiMotion.Scream -> {
            // Stretches up in fright, trembling
            val fright = hold(t, 0.12f, 0.6f)
            transformOrigin = TransformOrigin(0.5f, 1f)
            scaleX = 1f - 0.05f * fright
            scaleY = 1f + 0.12f * fright
            translationX = 3f * unit * sin(t * 34f * PI_F) * fright
            translationY = 1.5f * unit * sin(t * 26f * PI_F) * fright
        }
        EmojiMotion.Sob -> {
            // Heaving sobs
            val heave = abs(sin(t * 7f * PI_F)) * fade * ease
            transformOrigin = TransformOrigin(0.5f, 1f)
            scaleX = 1f + 0.03f * heave
            scaleY = 1f - 0.06f * heave
            translationY = -3f * unit * heave
        }
        EmojiMotion.Sad -> {
            // Droops and sways
            val droop = sin(t * PI_F)
            translationY = 7f * unit * droop
            scaleX = 1f + 0.04f * droop
            scaleY = 1f - 0.07f * droop
            rotationZ = 7f * sin(t * 2f * PI_F) * fade
        }
        EmojiMotion.Angry -> {
            // Shakes with rage, swelling up
            translationX = 6f * unit * sin(t * 14f * PI_F) * fade
            val s = 1f + 0.14f * sin(t * PI_F)
            scaleX = s
            scaleY = s
        }
        EmojiMotion.Fire -> {
            // Flickers up from its base
            val flicker = sin(t * 10f * PI_F) * fade
            transformOrigin = TransformOrigin(0.5f, 1f)
            scaleX = 1f - 0.06f * flicker
            scaleY = 1f + 0.12f * flicker
            translationY = -3f * unit * abs(flicker)
        }
        EmojiMotion.Party -> {
            // Pops, then rocks
            val popOut = 0.28f * pulse(t, 0.2f, 0.08f)
            scaleX = 1f + popOut
            scaleY = 1f + popOut
            rotationZ = -16f * sin(t * 3f * PI_F) * fade
        }
        EmojiMotion.Wave -> {
            // Waves from the wrist
            transformOrigin = TransformOrigin(0.7f, 0.95f)
            rotationZ = 22f * sin(t * 6f * PI_F) * fade
        }
        EmojiMotion.Cheer -> {
            // Pumps from its base and bounces
            transformOrigin = TransformOrigin(0.5f, 1f)
            translationY = -12f * unit * abs(sin(t * 3f * PI_F)) * fade
            rotationZ = -12f * sin(t * 3f * PI_F) * fade
            val popOut = 0.16f * pulse(t, 0.18f, 0.07f)
            scaleX = 1f + popOut
            scaleY = 1f + popOut
        }
        EmojiMotion.Sparkle -> {
            // Twinkles round
            rotationZ = 24f * sin(t * 2f * PI_F) * fade
            val s = 1f + 0.16f * sin(t * PI_F)
            scaleX = s
            scaleY = s
        }
        EmojiMotion.Jelly -> {
            // Wobbles like jelly
            val wobble = sin(t * 5f * PI_F) * fade * fade
            scaleX = 1f + 0.16f * wobble
            scaleY = 1f - 0.16f * wobble
            rotationZ = 6f * sin(t * 3f * PI_F) * fade
        }
    }
}

/** A smooth bump, 1 at [center] and [width] wide. */
private fun pulse(t: Float, center: Float, width: Float): Float {
    val x = (t - center) / width
    return exp(-x * x)
}

/** Eases from 0 up to 1 by [rise], holds, and eases back to 0 from [fall] to the end. */
private fun hold(t: Float, rise: Float, fall: Float): Float = when {
    t < rise -> smoothstep(t / rise)
    t > fall -> smoothstep((1f - t) / (1f - fall))
    else -> 1f
}

private fun smoothstep(x: Float): Float {
    val c = x.coerceIn(0f, 1f)
    return c * c * (3f - 2f * c)
}

internal fun motionOf(base: String): EmojiMotion = when {
    base in HEARTS -> EmojiMotion.Heartbeat
    base in KISSES -> EmojiMotion.KissBlow
    base in HUG -> EmojiMotion.Hug
    // Couples with a heart between them too
    base in LOVE || '❤' in base -> EmojiMotion.Love
    base in BLUSH -> EmojiMotion.Blush
    base in JOY -> EmojiMotion.Joy
    base in LAUGH -> EmojiMotion.Laugh
    base in NERVOUS -> EmojiMotion.Nervous
    base in SILLY -> EmojiMotion.Silly
    base in WINK -> EmojiMotion.Wink
    base in SMIRK -> EmojiMotion.Smirk
    base in RELIEVED -> EmojiMotion.Relieved
    base in YUM -> EmojiMotion.Yum
    base in NEUTRAL -> EmojiMotion.Neutral
    base in CONFUSED -> EmojiMotion.Confused
    base in UNAMUSED -> EmojiMotion.Unamused
    base in SCREAM -> EmojiMotion.Scream
    base in WOW -> EmojiMotion.Wow
    base in SOB -> EmojiMotion.Sob
    base in SAD -> EmojiMotion.Sad
    base in ANGRY -> EmojiMotion.Angry
    base in FIRE -> EmojiMotion.Fire
    base in PARTY -> EmojiMotion.Party
    base in WAVE -> EmojiMotion.Wave
    base in CHEER -> EmojiMotion.Cheer
    base in SPARKLE -> EmojiMotion.Sparkle
    else -> EmojiMotion.Jelly
}

/** [emoji] without its variation selectors and skin tones, to look it up. */
internal fun baseOf(emoji: String): String {
    val base = StringBuilder(emoji.length)
    var i = 0
    while (i < emoji.length) {
        val cp = emoji.codePointAt(i)
        i += Character.charCount(cp)
        if (cp != 0xFE0F && cp != 0xFE0E && cp !in 0x1F3FB..0x1F3FF) base.appendCodePoint(cp)
    }
    return base.toString()
}

private val HEARTS = setOf(
    "❤", "♥", "❣", "❤‍🔥", "❤‍🩹",
    "🧡", "💛", "💚", "💙", "💜", "🤎", "🖤", "🤍", "🩷", "🩵", "🩶",
    "💖", "💗", "💓", "💞", "💕", "💘", "💝", "💟", "💌"
)
private const val KISS_MARK = "💋"
private val KISSES = setOf("😘", "😚", "😗", "😙", "😽", KISS_MARK)
private val LOVE = setOf("😍", "🥰", "😻", "🫶", "💑", "💏")
private val HUG = setOf("🤗", "🫂")
private val BLUSH = setOf("😊", "☺", "🤭")
private val JOY = setOf("😀", "😃", "😄", "😁", "😸", "😺")
private val LAUGH = setOf("😂", "🤣", "😆", "😹")
private const val SWEAT_SMILE = "😅"
private val NERVOUS = setOf(SWEAT_SMILE, "😬")
private val SILLY = setOf("😜", "😝", "😛", "🤪", "🙃")
private val WINK = setOf("😉")
private val SMIRK = setOf("😏")
private val RELIEVED = setOf("😌")
private val YUM = setOf("😋", "🤤")
private val NEUTRAL = setOf("😐", "😑", "😶")
private val CONFUSED = setOf("😕", "🤔", "🫤")
private val UNAMUSED = setOf("😒", "🙄")
private val SCREAM = setOf("😱", "🙀")
private val WOW = setOf("😮", "😲", "🤯", "😳", "😯", "😦", "😧", "🫢", "👀", "😵")
private val SOB = setOf("😭")
private val SAD = setOf("😢", "🥺", "😿", "😞", "😔", "☹", "🙁", "😟", "😥", "😓", "🥲", "💔", "😩", "😫", "😪", "👎")
private val ANGRY = setOf("😡", "😠", "🤬", "👿", "😤", "💢", "😾")
private val FIRE = setOf("🔥", "💥", "⚡", "☄", "🌶")
private val PARTY = setOf("🎉", "🥳", "🎊", "🎂", "🎁", "🎈", "🍾", "🥂", "🎆", "🎇", "🪅", "🍻", "🏆")
private val WAVE = setOf("👋", "🤚", "✋", "🖐", "🙋")
private val CHEER = setOf(
    "👍", "👏", "🙌", "💪", "✌", "👌", "🤞", "🤟", "🤘", "👊", "✊", "🤙", "🫡", "💯", "✅", "🙏", "🤝", "😎"
)
private val SPARKLE = setOf(
    "✨", "⭐", "🌟", "💫", "🌙", "🌈", "☀", "🌸", "🌹", "💐", "🌺", "🌷", "🌻", "🤩", "😇", "🦋",
    "🌞", "🌛", "🌝", "💎", "👑"
)
/** Laughing till they cry. */
private val LAUGH_TEARS = setOf("😂", "🤣", "😹")
/** Sad faces with a tear to shed. */
private val CRYING = setOf("😢", "🥲", "😿", "🥺")
private val FLUSHED = setOf("😳")

// ---- The burst around them --------------------------------------------------------------------

private enum class BurstStyle(val count: Int, val tapCount: Int) {
    Hearts(9, 14), HeartBeats(12, 20), KissHearts(6, 10), Hug(6, 8), Blush(5, 8), Flushed(2, 2), Confetti(22, 34),
    Sparkles(9, 14), Twinkle(2, 4), LaughTears(6, 10), SobTears(16, 24), CryTears(3, 5), Sweat(2, 3),
    Sigh(3, 5), Dots(3, 3), Question(1, 3), Ripples(3, 4), Embers(12, 18), Rage(9, 13)
}

private enum class ParticleShape {
    Heart, Confetti, Sparkle, Drop, Ember, Puff, Glyph,
    // These stay where they start: a ring spreading out, a dot, a glow on the face, an aura behind it
    Ring, Dot, Glow, Aura
}

private class Particle(
    val shape: ParticleShape,
    // Which way it flies (radians, 0 is right and -PI/2 up) and how far, in dp
    val angle: Float,
    val distance: Float,
    val size: Float,
    // Degrees it turns over its flight (for a glyph, how far it rocks)
    val spin: Float,
    // When it sets off and how long it lasts, as parts of the whole burst
    val start: Float,
    val life: Float,
    // How far it falls (or rises, below 0) by the end, in dp
    val gravity: Float,
    val color: Color,
    // Where it sets off: a part of the emojis' width, and up (below 0) or down from their middle
    val originX: Float,
    val originY: Float,
    // The character drawn for a glyph
    val glyph: String? = null
)

@Stable
private class EmojiBurstState {
    val progress = Animatable(1f)
    var particles by mutableStateOf(emptyList<Particle>())
        private set
    // Reused for drawing
    val path = Path()
    val glyphPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }

    suspend fun burst(bases: List<String>, motions: List<EmojiMotion>, strong: Boolean, scale: Float) {
        val made = ArrayList<Particle>()
        bases.forEachIndexed { index, base ->
            // A tap on one without a burst of its own still sparkles
            val style = burstStyleOf(base, motions[index]) ?: (if (strong) BurstStyle.Sparkles else null)
                ?: return@forEachIndexed
            made += particlesFor(
                style = style,
                base = base,
                strong = strong,
                index = index,
                total = bases.size,
                delay = index * STAGGER_MS / BURST_MS.toFloat(),
                scale = scale
            )
        }
        if (made.isEmpty()) return
        particles = made
        progress.snapTo(0f)
        progress.animateTo(1f, tween(BURST_MS, easing = LinearEasing))
    }
}

private fun burstStyleOf(base: String, motion: EmojiMotion): BurstStyle? = when (motion) {
    EmojiMotion.Heartbeat -> BurstStyle.HeartBeats
    EmojiMotion.KissBlow -> BurstStyle.KissHearts
    EmojiMotion.Love -> BurstStyle.Hearts
    EmojiMotion.Hug -> BurstStyle.Hug
    EmojiMotion.Blush -> BurstStyle.Blush
    EmojiMotion.Laugh -> if (base in LAUGH_TEARS) BurstStyle.LaughTears else null
    EmojiMotion.Nervous -> if (base == SWEAT_SMILE) BurstStyle.Sweat else null
    EmojiMotion.Wink, EmojiMotion.Smirk -> BurstStyle.Twinkle
    EmojiMotion.Relieved, EmojiMotion.Unamused -> BurstStyle.Sigh
    EmojiMotion.Yum, EmojiMotion.Cheer, EmojiMotion.Sparkle -> BurstStyle.Sparkles
    EmojiMotion.Neutral -> BurstStyle.Dots
    EmojiMotion.Confused -> BurstStyle.Question
    EmojiMotion.Wow -> if (base in FLUSHED) BurstStyle.Flushed else BurstStyle.Sparkles
    EmojiMotion.Scream -> BurstStyle.Ripples
    EmojiMotion.Sob -> BurstStyle.SobTears
    EmojiMotion.Sad -> if (base in CRYING) BurstStyle.CryTears else BurstStyle.Sigh
    EmojiMotion.Angry -> BurstStyle.Rage
    EmojiMotion.Fire -> BurstStyle.Embers
    EmojiMotion.Party -> BurstStyle.Confetti
    EmojiMotion.Joy, EmojiMotion.Silly, EmojiMotion.Wave, EmojiMotion.Jelly -> null
}

private fun particlesFor(
    style: BurstStyle,
    base: String,
    strong: Boolean,
    // Which of the message's emojis it comes from, of how many
    index: Int,
    total: Int,
    delay: Float,
    scale: Float
): List<Particle> {
    // A tap throws more of them, farther
    val far = if (strong) 1.35f else 1f
    val colors = colorsOf(style, base)
    fun particle(
        shape: ParticleShape,
        angle: Float = 0f,
        distance: Float = 0f,
        size: Float,
        spin: Float = 0f,
        start: Float,
        life: Float,
        gravity: Float = 0f,
        // Where it sets off: across from the emoji's middle (in its widths), up or down (parts of its height)
        dx: Float = 0f,
        dy: Float = 0f,
        color: Color = colors.random(),
        glyph: String? = null
    ) = Particle(
        shape = shape,
        angle = angle,
        distance = distance * far * scale,
        size = size * scale,
        spin = spin,
        start = delay + start,
        // Done before the burst ends, so none vanishes mid-flight
        life = life.coerceAtMost(0.999f - delay - start),
        gravity = gravity * scale,
        color = color,
        originX = (index + 0.5f + dx) / total,
        originY = dy,
        glyph = glyph
    )
    return List(if (strong) style.tapCount else style.count) { i ->
        when (style) {
            BurstStyle.Hearts -> particle(
                ParticleShape.Heart, angle = UP + rnd(-1.3f, 1.3f), distance = rnd(56f, 120f), size = rnd(12f, 22f),
                spin = rnd(-50f, 50f), start = rnd(0f, 0.12f), life = rnd(0.6f, 0.8f), gravity = -24f
            )
            // In time with the two beats
            BurstStyle.HeartBeats -> particle(
                ParticleShape.Heart, angle = UP + rnd(-1.4f, 1.4f), distance = rnd(48f, 110f), size = rnd(11f, 20f),
                spin = rnd(-40f, 40f), start = (if (i % 2 == 0) 0.1f else 0.54f) + rnd(0f, 0.05f),
                life = rnd(0.38f, 0.44f), gravity = -20f
            )
            // Blown from the lips with the kiss, off to the side and up, the first one big
            BurstStyle.KissHearts -> particle(
                ParticleShape.Heart, angle = -0.7f + rnd(-0.45f, 0.45f), distance = rnd(50f, 105f),
                size = if (i == 0) 24f else rnd(10f, 16f), spin = rnd(-30f, 30f), start = 0.3f + i * 0.035f,
                life = rnd(0.5f, 0.62f), gravity = -22f,
                dx = if (base == KISS_MARK) 0f else 0.2f, dy = if (base == KISS_MARK) 0f else 0.14f
            )
            // A serene embrace: glowing blue aura behind, soft cheeks, blue ripple, and sweet sparkles
            BurstStyle.Hug -> when (i) {
                0 -> particle(ParticleShape.Aura, size = 52f, start = 0.12f, life = 0.75f, color = HugGlowColor)
                1 -> particle(ParticleShape.Glow, size = 17f, start = 0.18f, life = 0.70f, dx = -0.24f, dy = 0.08f, color = HugCheekColor)
                2 -> particle(ParticleShape.Glow, size = 17f, start = 0.18f, life = 0.70f, dx = 0.24f, dy = 0.08f, color = HugCheekColor)
                3 -> particle(ParticleShape.Ring, distance = 58f, size = 2.5f, start = 0.28f, life = 0.50f, color = HugGlowColor)
                4 -> particle(ParticleShape.Sparkle, angle = UP + rnd(-0.7f, 0.7f), distance = rnd(35f, 65f), size = rnd(10f, 14f), spin = rnd(-40f, 40f), start = 0.32f, life = 0.50f, color = HugColors[1])
                else -> particle(ParticleShape.Heart, angle = UP + rnd(-0.6f, 0.6f), distance = rnd(30f, 60f), size = rnd(9f, 13f), spin = rnd(-25f, 25f), start = 0.34f, life = 0.50f, gravity = -14f, color = HugColors[0])
            }
            // Rosy cheeks, and a few tiny hearts
            BurstStyle.Blush -> if (i < 2) {
                particle(
                    ParticleShape.Glow, size = 15f, start = 0.05f, life = 0.85f,
                    dx = if (i == 0) -0.25f else 0.25f, dy = 0.1f
                )
            } else {
                particle(
                    ParticleShape.Heart, angle = UP + rnd(-0.6f, 0.6f), distance = rnd(30f, 55f), size = rnd(7f, 11f),
                    start = 0.3f + (i - 2) * 0.08f, life = 0.45f, gravity = -10f, dx = rnd(-0.2f, 0.2f)
                )
            }
            // Cheeks flushing red
            BurstStyle.Flushed -> particle(
                ParticleShape.Glow, size = 15f, start = 0.12f, life = 0.75f,
                dx = if (i % 2 == 0) -0.25f else 0.25f, dy = 0.1f
            )
            BurstStyle.Confetti -> particle(
                ParticleShape.Confetti, angle = UP + rnd(-1.4f, 1.4f), distance = rnd(70f, 150f), size = rnd(6f, 10f),
                spin = rnd(400f, 800f) * (if (Random.nextBoolean()) 1f else -1f), start = 0.15f + rnd(0f, 0.05f),
                life = rnd(0.7f, 0.82f), gravity = 120f, dy = -0.1f
            )
            BurstStyle.Sparkles -> particle(
                ParticleShape.Sparkle, angle = rnd(0f, 2f * PI_F), distance = rnd(40f, 95f), size = rnd(9f, 17f),
                spin = rnd(-140f, 140f), start = rnd(0f, 0.25f), life = rnd(0.45f, 0.7f)
            )
            // A glint by the eye
            BurstStyle.Twinkle -> particle(
                ParticleShape.Sparkle, angle = -PI_F / 4f + rnd(-0.6f, 0.6f), distance = rnd(6f, 16f), size = rnd(13f, 19f),
                spin = rnd(-90f, 90f), start = 0.24f + i * 0.1f, life = 0.42f, dx = 0.3f + rnd(-0.05f, 0.05f), dy = -0.3f
            )
            // Spurting from both eyes, a few at a time
            BurstStyle.LaughTears -> particle(
                ParticleShape.Drop, angle = if (i % 2 == 0) -PI_F + rnd(0.3f, 0.8f) else -rnd(0.3f, 0.8f),
                distance = rnd(26f, 60f), size = rnd(6f, 10f), start = (i / 2) * 0.09f + rnd(0f, 0.03f),
                life = rnd(0.45f, 0.6f), gravity = 90f, dy = -0.1f
            )
            // Streaming from both eyes
            BurstStyle.SobTears -> particle(
                ParticleShape.Drop, angle = if (i % 2 == 0) -PI_F + rnd(0.15f, 0.65f) else -rnd(0.15f, 0.65f),
                distance = rnd(30f, 70f), size = rnd(5f, 9f), start = (i / 2) * 0.05f + rnd(0f, 0.02f),
                life = rnd(0.38f, 0.48f), gravity = 110f, dx = if (i % 2 == 0) -0.2f else 0.2f, dy = -0.04f
            )
            // Rolling down from one eye
            BurstStyle.CryTears -> particle(
                ParticleShape.Drop, angle = PI_F / 2f + rnd(-0.08f, 0.08f), distance = rnd(8f, 14f), size = rnd(7f, 9f),
                start = 0.12f + i * 0.24f, life = 0.5f, gravity = 70f, dx = 0.17f, dy = 0.02f
            )
            // Down the side of the forehead
            BurstStyle.Sweat -> particle(
                ParticleShape.Drop, angle = PI_F / 2f + rnd(-0.15f, 0.15f), distance = rnd(6f, 12f), size = rnd(8f, 11f),
                start = 0.1f + i * 0.32f, life = 0.5f, gravity = 60f, dx = 0.3f, dy = -0.34f
            )
            // A breath out of the mouth, drifting off
            BurstStyle.Sigh -> particle(
                ParticleShape.Puff, angle = PI_F * 0.88f + rnd(-0.2f, 0.2f), distance = rnd(22f, 38f), size = rnd(8f, 13f),
                start = 0.42f + i * 0.06f, life = rnd(0.42f, 0.5f), gravity = 6f, dx = -0.2f, dy = 0.18f
            )
            // "..." over its head, one dot at a time
            BurstStyle.Dots -> particle(
                ParticleShape.Dot, size = 7f, start = 0.12f + i * 0.14f, life = 0.8f - i * 0.14f,
                dx = (i - 1) * 0.2f, dy = -0.62f
            )
            // Question marks popping up over its head
            BurstStyle.Question -> particle(
                ParticleShape.Glyph, angle = UP + rnd(-0.3f, 0.3f), distance = rnd(8f, 14f),
                size = if (i == 0) 28f else rnd(18f, 22f), spin = rnd(14f, 20f), start = 0.18f + i * 0.12f, life = 0.7f,
                gravity = -6f, dx = 0.28f - i * 0.28f, dy = -0.5f, glyph = QUESTION_MARK
            )
            // Shock waves
            BurstStyle.Ripples -> particle(
                ParticleShape.Ring, distance = rnd(56f, 72f), size = rnd(2f, 3f), start = 0.06f + i * 0.13f, life = 0.45f
            )
            BurstStyle.Embers -> particle(
                ParticleShape.Ember, angle = UP + rnd(-0.7f, 0.7f), distance = rnd(36f, 90f), size = rnd(4f, 9f),
                start = rnd(0f, 0.45f), life = rnd(0.4f, 0.55f), gravity = -26f, dy = -0.22f
            )
            // Red with rage behind it, puffing steam
            BurstStyle.Rage -> if (i == 0) {
                particle(ParticleShape.Aura, size = 54f, start = 0f, life = 0.7f, color = RageColor)
            } else {
                particle(
                    ParticleShape.Ember, angle = UP + rnd(-1f, 1f), distance = rnd(30f, 70f), size = rnd(6f, 11f),
                    start = rnd(0f, 0.35f), life = rnd(0.4f, 0.55f), gravity = -18f, dy = -0.3f
                )
            }
        }
    }
}

private fun rnd(from: Float, until: Float): Float = from + Random.nextFloat() * (until - from)

private fun colorsOf(style: BurstStyle, base: String): List<Color> = when (style) {
    BurstStyle.Hearts, BurstStyle.HeartBeats -> heartColorsOf(base)
    BurstStyle.KissHearts -> HeartColors
    BurstStyle.Hug -> HugColors
    BurstStyle.Blush -> BlushColors
    BurstStyle.Flushed -> FlushColors
    BurstStyle.Confetti -> ConfettiColors
    BurstStyle.Sparkles, BurstStyle.Twinkle -> SparkleColors
    BurstStyle.LaughTears, BurstStyle.SobTears, BurstStyle.CryTears, BurstStyle.Sweat -> TearColors
    BurstStyle.Sigh -> SighColors
    BurstStyle.Dots, BurstStyle.Question -> DotColors
    BurstStyle.Ripples -> RippleColors
    BurstStyle.Embers -> FireColors
    BurstStyle.Rage -> SteamColors
}

/** A coloured heart throws hearts of its own colour. */
private fun heartColorsOf(base: String): List<Color> = when (if (base.isEmpty()) 0 else base.codePointAt(0)) {
    0x1F499 -> listOf(Color(0xFF2196F3), Color(0xFF42A5F5), Color(0xFF64B5F6), Color(0xFF1E88E5)) // 💙
    0x1F49A -> listOf(Color(0xFF43A047), Color(0xFF66BB6A), Color(0xFF81C784), Color(0xFF2E7D32)) // 💚
    0x1F49B -> listOf(Color(0xFFFDD835), Color(0xFFFFEE58), Color(0xFFFFCA28), Color(0xFFFBC02D)) // 💛
    0x1F49C -> listOf(Color(0xFF8E24AA), Color(0xFFAB47BC), Color(0xFFBA68C8), Color(0xFF7B1FA2)) // 💜
    0x1F9E1 -> listOf(Color(0xFFFB8C00), Color(0xFFFFA726), Color(0xFFFFB74D), Color(0xFFF57C00)) // 🧡
    0x1F5A4 -> listOf(Color(0xFF212121), Color(0xFF424242), Color(0xFF616161)) // 🖤
    0x1F90D -> listOf(Color(0xFFF5F5F5), Color(0xFFE0E0E0), Color(0xFFFCE4EC)) // 🤍
    0x1F90E -> listOf(Color(0xFF6D4C41), Color(0xFF8D6E63), Color(0xFFA1887F)) // 🤎
    0x1FA77 -> listOf(Color(0xFFF48FB1), Color(0xFFF06292), Color(0xFFF8BBD0)) // 🩷
    0x1FA75 -> listOf(Color(0xFF81D4FA), Color(0xFF4FC3F7), Color(0xFFB3E5FC)) // 🩵
    0x1FA76 -> listOf(Color(0xFF9E9E9E), Color(0xFFBDBDBD), Color(0xFF757575)) // 🩶
    else -> HeartColors
}

private val ConfettiColors = listOf(
    Color(0xFFFF4D6D), Color(0xFFFFC94D), Color(0xFF4DA3FF), Color(0xFF5BD68A), Color(0xFFB57BFF), Color(0xFFFF8A4D)
)
private val SparkleColors = listOf(Color(0xFFFFD54F), Color(0xFFFFE082), Color(0xFFFFC107), Color(0xFFFFF3B0))
private val TearColors = listOf(Color(0xFF64B5F6), Color(0xFF90CAF9), Color(0xFF42A5F5))
private val FireColors = listOf(Color(0xFFFF9800), Color(0xFFFFC107), Color(0xFFFF5722), Color(0xFFFFEB3B))
private val SteamColors = listOf(Color(0xFFE53935), Color(0xFFFF7043), Color(0xFFEF5350))
private val BlushColors = listOf(Color(0xFFFF7A9A), Color(0xFFFF8FAB), Color(0xFFFF6B8A))
private val FlushColors = listOf(Color(0xFFFF5252), Color(0xFFFF6E6E))
private val SighColors = listOf(Color(0xFFB0BEC5), Color(0xFFCFD8DC), Color(0xFFDCE3E8))
private val DotColors = listOf(Color(0xFF9AA0A6))
private val RippleColors = listOf(Color(0xFF8AB4F8), Color(0xFFA7C7FA))
private val RageColor = Color(0xFFFF3B30)
// The hug in 🫂's own colours: vibrant blue, sky blue, and soft light blue burst
private val HugColors = listOf(Color(0xFF2F6FD6), Color(0xFF6EC1F2), Color(0xFFA9DDFB))
private val HugGlowColor = Color(0xFFBFE6FF)
private val HugCheekColor = Color(0xFF6EC1F2)
private const val QUESTION_MARK = "❓"

/** Draws [state]'s burst around this element; nothing clips it, so it flies past the edges. */
private fun Modifier.emojiBurst(state: EmojiBurstState, heart: VectorPainter): Modifier = drawWithContent {
    val t = state.progress.value
    val bursting = t < 1f
    // An aura glows behind the emojis; everything else is in front of them
    if (bursting) state.particles.forEach { if (it.shape == ParticleShape.Aura) drawParticle(it, t, heart, state) }
    drawContent()
    if (bursting) state.particles.forEach { if (it.shape != ParticleShape.Aura) drawParticle(it, t, heart, state) }
}

private fun DrawScope.drawParticle(p: Particle, t: Float, heart: VectorPainter, state: EmojiBurstState) {
    val local = (t - p.start) / p.life
    if (local <= 0f || local >= 1f) return
    val origin = Offset(size.width * p.originX, size.height * (0.5f + p.originY))
    when (p.shape) {
        ParticleShape.Glow, ParticleShape.Aura -> {
            // Glows up and fades, breathing a little; a cheek's glow is wider than tall
            val isCheek = p.shape == ParticleShape.Glow
            val radius = p.size.dp.toPx() * (1f + 0.06f * sin(local * 6f * PI_F))
            val glow = sin(local * PI_F) * (if (isCheek) 0.7f else 0.55f)
            val brush = Brush.radialGradient(listOf(p.color, p.color.copy(alpha = 0f)), center = origin, radius = radius)
            scale(scaleX = if (isCheek) 1.35f else 1f, scaleY = 1f, pivot = origin) {
                drawCircle(brush, radius = radius, center = origin, alpha = glow)
            }
            return
        }
        ParticleShape.Ring -> {
            val radius = p.distance.dp.toPx() * (1f - (1f - local).pow(2))
            drawCircle(
                p.color, radius = radius, center = origin, alpha = (1f - local) * 0.8f,
                style = Stroke(width = p.size.dp.toPx())
            )
            return
        }
        ParticleShape.Dot -> {
            val pop = if (local < 0.12f) BackOut.transform(local / 0.12f) else 1f
            val alpha = if (local > 0.8f) (1f - local) / 0.2f else 1f
            drawCircle(p.color, radius = p.size.dp.toPx() / 2f * pop, center = origin, alpha = alpha)
            return
        }
        else -> Unit
    }
    val travel = 1f - (1f - local).pow(3)
    val distance = p.distance.dp.toPx() * travel
    val position = Offset(
        origin.x + cos(p.angle) * distance,
        origin.y + sin(p.angle) * distance + p.gravity.dp.toPx() * local * local
    )
    val grow = (local / 0.15f).coerceAtMost(1f)
    val alpha = if (local > 0.6f) 1f - (local - 0.6f) / 0.4f else 1f
    val px = p.size.dp.toPx() * grow
    val rotation = p.spin * local
    when (p.shape) {
        ParticleShape.Heart -> drawHeart(heart, position, px, rotation, p.color, alpha)
        ParticleShape.Confetti -> {
            // A paper strip tumbling: it narrows and widens as it turns over
            val width = px * (0.25f + 0.75f * abs(cos(rotation * PI_F / 90f)))
            rotate(rotation, pivot = position) {
                drawRect(
                    color = p.color,
                    topLeft = Offset(position.x - width / 2f, position.y - px / 4f),
                    size = Size(width, px / 2f),
                    alpha = alpha
                )
            }
        }
        ParticleShape.Sparkle -> drawSparkle(state.path, position, px, rotation, p.color, alpha)
        ParticleShape.Drop -> drawDrop(state.path, position, px, p.color, alpha)
        ParticleShape.Ember -> {
            drawCircle(p.color, radius = px, center = position, alpha = alpha * 0.3f)
            drawCircle(p.color, radius = px * 0.45f, center = position, alpha = alpha)
        }
        ParticleShape.Puff -> {
            // A soft cloud that spreads as it drifts
            val radius = p.size.dp.toPx() * (0.5f + 0.5f * local)
            drawCircle(
                Brush.radialGradient(listOf(p.color, p.color.copy(alpha = 0f)), center = position, radius = radius),
                radius = radius,
                center = position,
                alpha = alpha * 0.85f
            )
        }
        ParticleShape.Glyph -> {
            // Rocks gently as it floats
            val rock = p.spin * sin(local * 4f * PI_F) * (1f - local)
            rotate(rock, pivot = position) {
                drawIntoCanvas { canvas ->
                    val paint = state.glyphPaint
                    paint.textSize = px
                    paint.alpha = (alpha * 255f).roundToInt().coerceIn(0, 255)
                    canvas.nativeCanvas.drawText(p.glyph.orEmpty(), position.x, position.y + px * 0.35f, paint)
                }
            }
        }
        ParticleShape.Ring, ParticleShape.Dot, ParticleShape.Glow, ParticleShape.Aura -> Unit
    }
}

/** A four-pointed twinkle. */
private fun DrawScope.drawSparkle(path: Path, center: Offset, size: Float, rotation: Float, color: Color, alpha: Float) {
    if (size <= 0f || alpha <= 0f) return
    val r = size / 2f
    path.reset()
    path.moveTo(center.x, center.y - r)
    path.quadraticTo(center.x, center.y, center.x + r, center.y)
    path.quadraticTo(center.x, center.y, center.x, center.y + r)
    path.quadraticTo(center.x, center.y, center.x - r, center.y)
    path.quadraticTo(center.x, center.y, center.x, center.y - r)
    path.close()
    rotate(rotation, pivot = center) { drawPath(path, color, alpha = alpha) }
}

/** A teardrop, its point up; [size] is the width of its round end. */
private fun DrawScope.drawDrop(path: Path, center: Offset, size: Float, color: Color, alpha: Float) {
    if (size <= 0f || alpha <= 0f) return
    val r = size / 2f
    path.reset()
    path.moveTo(center.x, center.y - r * 1.8f)
    path.cubicTo(center.x + r * 0.35f, center.y - r * 1.15f, center.x + r, center.y - r * 0.55f, center.x + r, center.y)
    path.arcTo(Rect(center.x - r, center.y - r, center.x + r, center.y + r), 0f, 180f, false)
    path.cubicTo(center.x - r, center.y - r * 0.55f, center.x - r * 0.35f, center.y - r * 1.15f, center.x, center.y - r * 1.8f)
    path.close()
    drawPath(path, color, alpha = alpha)
}

// Telegram's send flight: up out of the message box quickly, across a beat later, growing with a
// slight overshoot as it lands
private val FlightUp = CubicBezierEasing(0.2f, 0.9f, 0.3f, 1f)
private val FlightAcross = CubicBezierEasing(0.5f, 0f, 0.3f, 1f)
private val FlightGrow = CubicBezierEasing(0.3f, 1.4f, 0.6f, 1f)
private const val FLIGHT_MS = 460

/** Pops past its size and settles back. */
private val BackOut = CubicBezierEasing(0.34f, 1.56f, 0.64f, 1f)

/** Each emoji of a message starts its animation this much after the one before. */
private const val STAGGER_MS = 110
private const val BURST_MS = 1500
internal const val LIFE_DONE = 1_000_000f

private val PI_F = PI.toFloat()
private val UP = -PI_F / 2f

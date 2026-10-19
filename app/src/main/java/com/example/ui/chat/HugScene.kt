package com.example.ui.chat

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.lerp
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.VectorPainter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Collections
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.sin

/*
 * A 🫂 sent on its own is a real hug: him in 🫂's deep blue, taller and broader, her in its sky blue
 * (with her hair up in a bun), my partner on the left and me on the right, as in the chat. Whoever
 * sent it walks over with an arm open and wraps it round the other's back; the other steps in and
 * hugs back, a hand on their back. Heads lean together, hers on his shoulder, they squeeze and rock,
 * and stay in each other's arms. Next to the time it says who hugged whom.
 */

/** Which of us is him (the deep blue) and which her (the sky blue), and my partner's name. */
@Immutable
internal data class HugCouple(val iAmHim: Boolean, val partnerName: String)

internal val LocalHugCouple = staticCompositionLocalOf { HugCouple(iAmHim = true, partnerName = "") }

/** Tracks which incoming hugs have already played their entrance so new ones play on receipt automatically. */
internal object HugPlayedTracker {
    private val played = Collections.synchronizedSet(HashSet<String>())
    fun shouldPlay(id: String): Boolean = played.add(id)
}

/** A message of nothing but 🫂 is shown as the hug. */
internal fun isHugScene(emojis: List<String>): Boolean = emojis.size == 1 && baseOf(emojis[0]) == PEOPLE_HUGGING

internal const val HUG_SCENE_MS = 2400

/** Where a hug plays again from (a tap, or coming into view): already in each other's arms. */
internal const val HUG_EMBRACED = 0.5f

/** How long the hearts wait, played from [from], to float up with the first squeeze. */
internal fun hugBurstDelayMs(from: Float): Long =
    ((FIRST_SQUEEZE - from) * HUG_SCENE_MS - HUG_HEARTS_LEAD_MS).toLong().coerceAtLeast(0L)

@Composable
internal fun HugScene(
    // How far through the hug, 0-1 (past 1 at rest, in each other's arms); only drawing reads it
    progress: () -> Float,
    isFromMe: Boolean,
    modifier: Modifier = Modifier,
    // How tight it was sent, held on the 🫂 button (0-1; null for a plain 🫂), see HugSqueeze
    level: Float? = null
) {
    val couple = LocalHugCouple.current
    val heart = rememberVectorPainter(Icons.Rounded.Favorite)
    val path = remember { Path() }
    val partner = if (couple.iAmHim) HugFigure.Her else HugFigure.Him
    val me = if (couple.iAmHim) HugFigure.Him else HugFigure.Her
    Spacer(
        modifier
            .size(HUG_SCENE_WIDTH, HUG_SCENE_HEIGHT)
            .semantics { contentDescription = PEOPLE_HUGGING }
            .drawBehind {
                drawHug(progress().coerceIn(0f, 1f), left = partner, right = me, rightHugs = isFromMe, heart, path, level ?: PLAIN_HUG_LEVEL)
            }
    )
}

/** "You hugged Shali" or "Shali hugged you", in a chip like the time's, once they're hugging. Soft in day mode. */
@Composable
internal fun HugCaption(progress: () -> Float, isFromMe: Boolean, modifier: Modifier = Modifier, level: Float? = null) {
    val couple = LocalHugCouple.current
    val text = remember(couple, isFromMe, level) {
        val name = couple.partnerName.trim().substringBefore(' ')
        // How tight, when it was held: "You hugged Shali tight"
        val how = level?.let { " " + HugStrength.of(it).adverb }.orEmpty()
        when {
            isFromMe -> "You hugged " + name.ifBlank { if (couple.iAmHim) "her" else "him" } + how
            name.isNotBlank() -> "$name hugged you$how"
            couple.iAmHim -> "She hugged you$how"
            else -> "He hugged you$how"
        }
    }
    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val chipBg = if (isDark) {
        Color.Black.copy(alpha = TIME_CHIP_ALPHA)
    } else {
        MaterialTheme.colorScheme.surface.copy(alpha = 0.90f)
    }
    val chipTextColor = if (isDark) Color.White else MaterialTheme.colorScheme.onSurface
    val chipBorder = if (isDark) null else BorderStroke(0.7.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = chipBg,
        border = chipBorder,
        modifier = modifier.graphicsLayer { alpha = ease((progress() - 0.36f) / 0.12f) }
    ) {
        Text(
            text = text,
            fontSize = 10.sp,
            color = chipTextColor,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}

private enum class HugFigure(
    val color: Color,
    val shade: Color,
    // Half the shoulders' width, the head's radius, and the top of the shoulders (scene units)
    val shoulders: Float,
    val head: Float,
    val top: Float,
    // How the shoulders round off: 0 square, 1 soft and sloping
    val softness: Float
) {
    // 🫂's own blues: him the deep blue, taller and broader with square shoulders; her the sky
    // blue, slighter and shorter, with soft shoulders
    Him(Color(0xFF2F6FD6), Color(0xFF1F4FA3), shoulders = 23f, head = 13.5f, top = 46f, softness = 0.15f),
    Her(Color(0xFF6EC1F2), Color(0xFF3F9FDB), shoulders = 18f, head = 12f, top = 51f, softness = 0.85f)
}

/**
 * The hug [t] of the way through (0-1). Drawn in units of a 120 x 100 scene: the figures stand on
 * its bottom edge, the left one facing right and the right one facing left.
 */
private fun DrawScope.drawHug(
    t: Float,
    left: HugFigure,
    right: HugFigure,
    rightHugs: Boolean,
    heart: VectorPainter,
    path: Path,
    // How tight (0-1): harder squeezes, one more for a deep one, and they hold each other closer
    level: Float
) {
    val u = size.width / SCENE_UNITS_WIDE
    // The one who sent it walks over, an arm opening on the way, and wraps it round the other, who
    // steps in to meet them and hugs back; their heads lean together, squeezes, a soft rock
    val walk = ease((t - 0.04f) / 0.32f)
    val reach = ease((t - 0.08f) / 0.24f)
    val meet = ease((t - 0.26f) / 0.14f)
    val wrap = ease((t - 0.30f) / 0.12f)
    val nestle = ease((t - 0.34f) / 0.12f)
    val hugBack = ease((t - 0.40f) / 0.10f)

    // Dynamic squeeze power based on gentle, warm, tight
    val power = when {
        level < 0.35f -> 0.55f + 0.35f * (level / 0.35f) // Gentle
        level < 0.67f -> 0.95f + 0.55f * ((level - 0.35f) / 0.32f) // Warm
        else -> 1.55f + 0.95f * ((level - 0.67f) / 0.33f) // Tight / Deep
    }
    val squeeze = power * hugSqueezePeaks(level).withIndex().sumOf { (i, peak) ->
        val weight = when {
            level < 0.35f -> 0.75f
            level < 0.67f -> if (i == 0) 1f else 0.85f
            else -> if (i == 0) 1.15f else if (i == 1) 1.0f else 0.9f
        }
        (weight * bump(t, peak, if (level < 0.35f) 0.06f else 0.05f)).toDouble()
    }.toFloat()

    // Squeeze proximity matching gentle, warm, tight
    val snug = when {
        level < 0.35f -> 1.2f * nestle
        level < 0.67f -> 2.6f * nestle
        else -> 4.5f * nestle
    }

    val rock = if (t > HUG_EMBRACED) {
        val r = (t - HUG_EMBRACED) / (1f - HUG_EMBRACED)
        val rockAmp = when {
            level < 0.35f -> 1.8f // Gentle slow sway
            level < 0.67f -> 3.2f // Warm comforting rock
            else -> 4.2f // Tight passionate rock
        }
        rockAmp * sin(r * 2f * HugPi) * sin(r * HugPi)
    } else {
        0f
    }

    val hugger = if (rightHugs) right else left
    val hugged = if (rightHugs) left else right
    // Which way each faces: +1 right, -1 left
    val huggerFacing = if (rightHugs) -1f else 1f
    val huggedFacing = -huggerFacing
    // Where each stands: a little into each other once hugging, the hugger far off at first
    val huggerX = 60f - huggerFacing * (14f - snug - 1.5f * squeeze + 24f * (1f - walk))
    val huggedX = 60f - huggedFacing * (14f - snug - 1.5f * squeeze + 5f * (1f - meet))
    // A bounce in each step, leaning into the walk
    val huggerLift = 3f * abs(sin(walk * 3f * HugPi))
    val huggerLean = huggerFacing * (7f * sin(walk * HugPi) + 2f * nestle)
    val huggedLean = huggedFacing * 2f * nestle
    // On each one's back, where the other's hands go
    val huggedBack = huggedX - huggedFacing * hugged.shoulders * 0.8f
    val huggerBack = huggerX - huggerFacing * hugger.shoulders * 0.8f

    rotate(rock, pivot = Offset(60f * u, 100f * u)) {
        // The one being hugged is behind; their arm, hanging by their side at first, goes round
        // the hugger behind the hugger's body. The hugger's other hand holds their back. Arms and
        // hands sit at each one's own height: his arm comes over her shoulder, hers round his back
        rotate(huggedLean, pivot = Offset(huggedX * u, 100f * u)) {
            drawFigure(hugged, huggedX, huggedFacing, lift = 0f, nestle, squeeze, u, path)
            val shoulder = Offset(huggedX + huggedFacing * hugged.shoulders * 0.5f, hugged.top + 10f)
            val down = Offset(shoulder.x + huggedFacing * 3f, hugged.top + 38f)
            val hand = lerp(down, Offset(huggerBack, hugger.top + 16f), hugBack)
            drawArm(path, shoulder, hand, hugged, u)
            drawHand(Offset(huggedBack + huggedFacing * 2f, hugged.top + 28f), hugger, pop(ease((wrap - 0.5f) / 0.5f)), u)
        }
        // The hugger in front, their arm over the other's shoulder and round their back, and the
        // other's hand on their back, hugging back
        rotate(huggerLean, pivot = Offset(huggerX * u, 100f * u)) {
            drawFigure(hugger, huggerX, huggerFacing, huggerLift, nestle, squeeze, u, path)
            val shoulder = Offset(huggerX + huggerFacing * hugger.shoulders * 0.5f, hugger.top + 10f - huggerLift)
            val down = Offset(shoulder.x + huggerFacing * 3f, hugger.top + 38f)
            val open = Offset(shoulder.x + huggerFacing * 17f, hugger.top + 2f - huggerLift)
            val around = Offset(huggedBack, hugged.top + 15f - 2f * squeeze)
            val hand = if (wrap <= 0f) lerp(down, open, reach) else lerp(open, around, wrap)
            drawArm(path, shoulder, hand, hugger, u, sag = 1f + 6f * wrap)
            drawHand(hand, hugger, 1f, u)
            drawHand(Offset(huggerBack, hugger.top + 16f), hugged, pop(ease((hugBack - 0.4f) / 0.6f)), u)
        }
        // A heart over their heads as they come together, beating with the squeezes
        val heartIn = ease((t - 0.44f) / 0.08f)
        val heartOut = ease((t - 0.86f) / 0.14f)
        if (heartIn > 0f && heartOut < 1f) {
            drawHeart(
                heart,
                center = Offset(60f * u, (9f - 6f * heartOut) * u),
                size = (13f + 4f * squeeze) * pop(heartIn) * u,
                rotation = 0f,
                color = HugHeartColor,
                alpha = 1f - heartOut
            )
        }
    }
}

/** One of them, a bust like 🫂's: [x] is their middle, [facing] +1 right or -1 left. */
private fun DrawScope.drawFigure(
    figure: HugFigure,
    x: Float,
    facing: Float,
    lift: Float,
    nestle: Float,
    squeeze: Float,
    u: Float,
    path: Path
) {
    val half = figure.shoulders
    val top = figure.top - lift
    // The body: shoulders (square for him, soft for her) down to the bottom edge, a shade deeper
    // lower down
    val drop = 2f + 6f * figure.softness
    val reach = 0.75f - 0.3f * figure.softness
    path.reset()
    path.moveTo((x - half) * u, 100f * u)
    path.lineTo((x - half) * u, (top + 18f) * u)
    path.cubicTo((x - half) * u, (top + drop) * u, (x - half * reach) * u, top * u, x * u, top * u)
    path.cubicTo((x + half * reach) * u, top * u, (x + half) * u, (top + drop) * u, (x + half) * u, (top + 18f) * u)
    path.lineTo((x + half) * u, 100f * u)
    path.close()
    drawPath(
        path,
        Brush.verticalGradient(
            listOf(figure.color, androidx.compose.ui.graphics.lerp(figure.color, figure.shade, 0.55f)),
            startY = top * u,
            endY = 100f * u
        )
    )

    // The head leans towards the other, and nestles in as they hug: hers further, onto his shoulder
    val r = figure.head
    val lean = if (figure == HugFigure.Her) 3.5f else 2.5f
    val headX = x + facing * (2f + lean * nestle)
    val headY = top - HEAD_GAP - r + 1.5f * nestle + squeeze
    val head = Offset(headX * u, headY * u)
    if (figure == HugFigure.Her) {
        // Her hair: up in a bun, and down over her back
        drawCircle(figure.shade, radius = r * 0.45f * u, center = Offset((headX - facing * r * 0.6f) * u, (headY - r * 0.8f) * u))
        drawOval(
            figure.shade,
            topLeft = Offset((headX - facing * r * 0.55f - r * 0.5f) * u, headY * u),
            size = Size(r * u, r * 1.35f * u)
        )
    }
    drawCircle(figure.color, radius = r * u, center = head)
    // The hair over the top and back of the head: short for him, fuller for her
    path.reset()
    path.addOval(Rect(head, r * u))
    clipPath(path) {
        val cap = if (figure == HugFigure.Her) 1.05f else 0.95f
        drawCircle(
            figure.shade,
            radius = r * cap * u,
            center = Offset((headX - facing * r * 0.35f) * u, (headY - r * 0.6f) * u)
        )
    }
}

/** An arm from [from] to [to], bending down by [sag] at the elbow; outlined so it shows on a body. */
private fun DrawScope.drawArm(path: Path, from: Offset, to: Offset, figure: HugFigure, u: Float, sag: Float = 1f) {
    val elbow = Offset((from.x + to.x) / 2f, max(from.y, to.y) + sag)
    path.reset()
    path.moveTo(from.x * u, from.y * u)
    path.quadraticTo(elbow.x * u, elbow.y * u, to.x * u, to.y * u)
    drawPath(path, figure.shade, style = Stroke(width = 9.5f * u, cap = StrokeCap.Round))
    drawPath(path, figure.color, style = Stroke(width = 7f * u, cap = StrokeCap.Round))
}

/** A hand at [at], [scale] of its size (0 hides it). */
private fun DrawScope.drawHand(at: Offset, figure: HugFigure, scale: Float, u: Float) {
    if (scale <= 0f) return
    val center = Offset(at.x * u, at.y * u)
    drawCircle(figure.shade, radius = 4.8f * scale * u, center = center)
    drawCircle(figure.color, radius = 3.6f * scale * u, center = center)
}

/** Grows from 0 to a little past 1 and settles back. */
private fun pop(x: Float): Float = if (x <= 0f) 0f else x + 0.25f * sin(x * HugPi)

private fun ease(x: Float): Float {
    val c = x.coerceIn(0f, 1f)
    return c * c * (3f - 2f * c)
}

/** A smooth bump, 1 at [center] and [width] wide. */
private fun bump(t: Float, center: Float, width: Float): Float {
    val x = (t - center) / width
    return exp(-x * x)
}

private const val PEOPLE_HUGGING = "🫂"
private const val SCENE_UNITS_WIDE = 120f
// Between the head and the shoulders, in scene units
private const val HEAD_GAP = 2f
// Two people side by side, kept near a lone big emoji's size (48sp)
private val HUG_SCENE_WIDTH = 96.dp
private val HUG_SCENE_HEIGHT = 80.dp
private const val FIRST_SQUEEZE = 0.6f

/** A plain 🫂 (not held) hugs like a warm hold. */
internal const val PLAIN_HUG_LEVEL = 0.5f

/** When each squeeze is tightest: 1 for gentle, 2 for warm, and 3 for deep tight squeeze. */
internal fun hugSqueezePeaks(level: Float?): List<Float> {
    val l = level ?: PLAIN_HUG_LEVEL
    return when {
        l < 0.35f -> listOf(FIRST_SQUEEZE) // Gentle: 1 tender squeeze
        l < 0.67f -> listOf(FIRST_SQUEEZE, 0.76f) // Warm: 2 comforting squeezes
        else -> listOf(FIRST_SQUEEZE, 0.76f, 0.88f) // Tight: 3 deep tight squeezes
    }
}
// The hug's burst hearts set off 0.3 of the way through the burst (1.5 s)
private const val HUG_HEARTS_LEAD_MS = 450f
// Between his blue and her sky blue
private val HugHeartColor = Color(0xFF4A97E8)
private val HugPi = PI.toFloat()

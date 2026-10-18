package com.example.ui.chat

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.isSpecified
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

/**
 * A message's text whose emojis come alive in the line, as a big emoji does on its own: a heart
 * beats, 🤗 hugs, 😂 shakes with laughter... They play just after the message arrives
 * ([playOnArrival]) and again whenever [replay] changes (a tap on the message). Text without
 * emojis, or with very many, is drawn as EmojiText draws it.
 */
@Composable
internal fun AnimatedEmojiText(
    text: String,
    style: TextStyle,
    color: Color,
    fontStyle: FontStyle?,
    playOnArrival: Boolean,
    replay: Int,
    modifier: Modifier = Modifier
) {
    val content = remember(text) { inlineEmojiText(text) }
    if (content == null) {
        EmojiText(text = text, modifier = modifier, color = color, fontStyle = fontStyle, style = style, largeWhenEmojiOnly = true)
        return
    }
    val emojiSize = (if (style.fontSize.isSpecified) style.fontSize else 14.sp) * content.scale
    // The motions are made for a big emoji; these small ones move as far for their size
    val unitScale = emojiSize.value / 72f
    val motions = remember(content) { content.emojis.map { motionOf(baseOf(it)) } }
    val lifeMs = remember(motions) {
        motions.withIndex().maxOf { (index, motion) -> startOf(index) + motion.durationMs }.toFloat()
    }
    // How far into the emojis' animation, in ms; past its end when still
    val life = remember { Animatable(LIFE_DONE) }

    suspend fun play() {
        life.snapTo(0f)
        life.animateTo(lifeMs, tween(lifeMs.roundToInt(), easing = LinearEasing))
    }
    LaunchedEffect(Unit) {
        if (playOnArrival) {
            // Once the message has landed
            delay(380)
            play()
        }
    }
    LaunchedEffect(replay) {
        if (replay > 0) play()
    }

    val inlineContent = remember(content, style, emojiSize) {
        content.emojis.mapIndexed { index, emoji ->
            val motion = motions[index]
            inlineEmojiId(index) to InlineTextContent(
                Placeholder(
                    width = (content.scale * EMOJI_ADVANCE).em,
                    height = (content.scale * EMOJI_ADVANCE).em,
                    placeholderVerticalAlign = PlaceholderVerticalAlign.TextCenter
                )
            ) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = emoji,
                        fontSize = emojiSize,
                        maxLines = 1,
                        softWrap = false,
                        style = style.copy(lineHeight = TextUnit.Unspecified),
                        modifier = Modifier
                            .wrapContentSize(unbounded = true)
                            .graphicsLayer {
                                emojiMotion(motion, (life.value - startOf(index)) / motion.durationMs, density * unitScale)
                            }
                    )
                }
            }
        }.toMap()
    }
    Text(
        text = content.text,
        inlineContent = inlineContent,
        modifier = modifier,
        color = color,
        fontStyle = fontStyle,
        // A fixed line height would make the emojis overlap the next line
        style = style.copy(lineHeight = TextUnit.Unspecified)
    )
}

/** When the emoji at [index] starts, in ms into the run: one after another, the first few. */
private fun startOf(index: Int): Int = minOf(index, 6) * 90

/** An emoji's room in the line, wide and high, as a part of its font size. */
private const val EMOJI_ADVANCE = 1.2f

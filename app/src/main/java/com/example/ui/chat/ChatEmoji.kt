package com.example.ui.chat

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.em
import kotlin.math.max

/*
 * How emojis look in the chat, applied when drawing (stored text is never changed):
 * - hands and people get a fair skin tone unless the sender picked one;
 * - yellow smiley faces, which have no Unicode skin tones, are drawn as images recoloured to the
 *   same fair tone;
 * - emojis are drawn a little bigger than the text around them, emoji-only messages large.
 */

/** Unicode "light skin tone" modifier (Fitzpatrick type 1-2). */
private const val FAIR_SKIN_TONE = 0x1F3FB

/** Hue of the fair tone faces are recoloured to; matches the light skin tone of hands. */
private const val FACE_SKIN_HUE = 25f

/** Inline face images are sized to match a native emoji glyph at the same font size. */
private const val FACE_SIZE_EM = 1.1f

/**
 * Text with chat emoji styling. [emojiScale] enlarges emojis relative to the text; with
 * [largeWhenEmojiOnly], a message of just a few emojis is shown larger still.
 */
@Composable
fun EmojiText(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    fontSize: TextUnit = TextUnit.Unspecified,
    fontWeight: FontWeight? = null,
    fontStyle: FontStyle? = null,
    style: TextStyle = LocalTextStyle.current,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Clip,
    emojiScale: Float = 1.3f,
    largeWhenEmojiOnly: Boolean = false
) {
    val content = remember(text, emojiScale, largeWhenEmojiOnly) {
        buildEmojiContent(text, emojiScale, largeWhenEmojiOnly)
    }
    Text(
        text = content.text,
        modifier = modifier,
        color = color,
        fontSize = fontSize,
        fontWeight = fontWeight,
        fontStyle = fontStyle,
        maxLines = maxLines,
        overflow = overflow,
        inlineContent = content.inlineContent,
        // A fixed line height would make the bigger emojis overlap the next line
        style = if (content.hasEmoji) style.copy(lineHeight = TextUnit.Unspecified) else style
    )
}

/** A single emoji (reactions, pickers) with the chat emoji styling, at [fontSize]. */
@Composable
fun ChatEmoji(emoji: String, fontSize: TextUnit, modifier: Modifier = Modifier) {
    EmojiText(text = emoji, fontSize = fontSize, emojiScale = 1f, modifier = modifier)
}

private class EmojiContent(
    val text: AnnotatedString,
    val inlineContent: Map<String, InlineTextContent>,
    val hasEmoji: Boolean
)

private class EmojiToken(val text: String, val isEmoji: Boolean)

private fun buildEmojiContent(raw: String, emojiScale: Float, largeWhenEmojiOnly: Boolean): EmojiContent {
    val tokens = tokenize(raw)
    val emojiCount = tokens.count { it.isEmoji }
    if (emojiCount == 0) return EmojiContent(AnnotatedString(raw), emptyMap(), hasEmoji = false)

    val emojiOnly = tokens.all { it.isEmoji || it.text.isBlank() }
    val scale = if (largeWhenEmojiOnly && emojiOnly && emojiCount <= 6) {
        when (emojiCount) {
            1 -> 2.4f
            2 -> 2.1f
            3 -> 1.8f
            else -> 1.5f
        }
    } else {
        emojiScale
    }

    val inline = HashMap<String, InlineTextContent>()
    val text = buildAnnotatedString {
        tokens.forEach { token ->
            if (!token.isEmoji) {
                append(token.text)
                return@forEach
            }
            val shown = withFairSkinTone(token.text)
            if (isFace(shown) && FaceEmojiImages.canDraw(shown)) {
                val id = "face:$shown"
                if (id !in inline) {
                    val size = (scale * FACE_SIZE_EM).em
                    inline[id] = InlineTextContent(Placeholder(size, size, PlaceholderVerticalAlign.TextCenter)) {
                        FaceEmojiImage(shown)
                    }
                }
                appendInlineContent(id, shown)
            } else if (scale != 1f) {
                withStyle(SpanStyle(fontSize = scale.em)) { append(shown) }
            } else {
                append(shown)
            }
        }
    }
    return EmojiContent(text, inline, hasEmoji = true)
}

@Composable
private fun FaceEmojiImage(face: String) {
    val bitmap = remember(face) { FaceEmojiImages.get(face) } ?: return
    Image(bitmap = bitmap, contentDescription = face, modifier = Modifier.fillMaxSize())
}

// ---- Emoji detection --------------------------------------------------------------------------

/** BMP symbols shown as emoji even without a variation selector. */
private val DEFAULT_EMOJI_BMP = intArrayOf(
    0x231A, 0x231B, 0x23E9, 0x23EA, 0x23EB, 0x23EC, 0x23F0, 0x23F3, 0x25FD, 0x25FE, 0x2614, 0x2615,
    0x2648, 0x2649, 0x264A, 0x264B, 0x264C, 0x264D, 0x264E, 0x264F, 0x2650, 0x2651, 0x2652, 0x2653,
    0x267F, 0x2693, 0x26A1, 0x26AA, 0x26AB, 0x26BD, 0x26BE, 0x26C4, 0x26C5, 0x26CE, 0x26D4, 0x26EA,
    0x26F2, 0x26F3, 0x26F5, 0x26FA, 0x26FD, 0x2705, 0x270A, 0x270B, 0x2728, 0x274C, 0x274E, 0x2753,
    0x2754, 0x2755, 0x2757, 0x2764, 0x2795, 0x2796, 0x2797, 0x27B0, 0x27BF, 0x2B1B, 0x2B1C, 0x2B50,
    0x2B55
).toHashSet()

private fun isSkinTone(cp: Int) = cp in 0x1F3FB..0x1F3FF

private fun isRegionalIndicator(cp: Int) = cp in 0x1F1E6..0x1F1FF

/** [followedByVs16]: symbols that are text by default count as emoji when followed by U+FE0F. */
private fun startsEmoji(cp: Int, followedByVs16: Boolean): Boolean = when {
    cp in 0x1F000..0x1FAFF -> true
    cp in DEFAULT_EMOJI_BMP -> true
    !followedByVs16 -> false
    else -> cp in 0x2190..0x21FF || cp in 0x2300..0x23FF || cp in 0x25A0..0x27BF ||
        cp in 0x2900..0x297F || cp in 0x2B00..0x2BFF || cp == 0x00A9 || cp == 0x00AE ||
        cp == 0x2122 || cp == 0x3030 || cp == 0x303D || cp == 0x3297 || cp == 0x3299
}

/** End of the emoji sequence (ZWJ, skin tones, flags, keycaps) starting at [start], or [start]. */
private fun emojiEnd(text: String, start: Int): Int {
    val cp = text.codePointAt(start)
    var end = start + Character.charCount(cp)

    if (cp == '#'.code || cp == '*'.code || cp in '0'.code..'9'.code) {
        var k = end
        if (k < text.length && text[k].code == 0xFE0F) k++
        return if (k < text.length && text[k].code == 0x20E3) k + 1 else start
    }
    if (isRegionalIndicator(cp)) {
        if (end < text.length && isRegionalIndicator(text.codePointAt(end))) end += 2
        return end
    }
    if (!startsEmoji(cp, followedByVs16 = end < text.length && text[end].code == 0xFE0F)) return start

    while (end < text.length) {
        val next = text.codePointAt(end)
        end += when {
            next == 0xFE0F || next == 0xFE0E || next == 0x20E3 -> 1
            isSkinTone(next) || next in 0xE0020..0xE007F -> 2
            next == 0x200D && end + 1 < text.length && startsEmoji(text.codePointAt(end + 1), followedByVs16 = true) ->
                1 + Character.charCount(text.codePointAt(end + 1))
            else -> return end
        }
    }
    return end
}

private fun tokenize(text: String): List<EmojiToken> {
    val tokens = ArrayList<EmojiToken>()
    val plain = StringBuilder()
    var i = 0
    while (i < text.length) {
        val end = emojiEnd(text, i)
        if (end > i) {
            if (plain.isNotEmpty()) {
                tokens += EmojiToken(plain.toString(), isEmoji = false)
                plain.setLength(0)
            }
            tokens += EmojiToken(text.substring(i, end), isEmoji = true)
            i = end
        } else {
            val cp = text.codePointAt(i)
            plain.appendCodePoint(cp)
            i += Character.charCount(cp)
        }
    }
    if (plain.isNotEmpty()) tokens += EmojiToken(plain.toString(), isEmoji = false)
    return tokens
}

// ---- Skin tone for hands and people -----------------------------------------------------------

/** Emoji_Modifier_Base ranges (Unicode emoji-data), inclusive pairs. */
private val MODIFIER_BASES = intArrayOf(
    0x261D, 0x261D, 0x26F9, 0x26F9, 0x270A, 0x270D, 0x1F385, 0x1F385, 0x1F3C2, 0x1F3C4,
    0x1F3C7, 0x1F3C7, 0x1F3CA, 0x1F3CC, 0x1F442, 0x1F443, 0x1F446, 0x1F450, 0x1F466, 0x1F469,
    0x1F46B, 0x1F46E, 0x1F470, 0x1F478, 0x1F47C, 0x1F47C, 0x1F481, 0x1F483, 0x1F485, 0x1F487,
    0x1F48F, 0x1F48F, 0x1F491, 0x1F491, 0x1F4AA, 0x1F4AA, 0x1F574, 0x1F575, 0x1F57A, 0x1F57A,
    0x1F590, 0x1F590, 0x1F595, 0x1F596, 0x1F645, 0x1F647, 0x1F64B, 0x1F64F, 0x1F6A3, 0x1F6A3,
    0x1F6B4, 0x1F6B6, 0x1F6C0, 0x1F6C0, 0x1F6CC, 0x1F6CC, 0x1F90C, 0x1F90C, 0x1F90F, 0x1F90F,
    0x1F918, 0x1F91F, 0x1F926, 0x1F926, 0x1F930, 0x1F939, 0x1F93D, 0x1F93E, 0x1F977, 0x1F977,
    0x1F9B5, 0x1F9B6, 0x1F9B8, 0x1F9B9, 0x1F9BB, 0x1F9BB, 0x1F9CD, 0x1F9CF, 0x1F9D1, 0x1F9DD,
    0x1FAC3, 0x1FAC5, 0x1FAF0, 0x1FAF8
)

private fun isModifierBase(cp: Int): Boolean {
    var i = 0
    while (i < MODIFIER_BASES.size) {
        if (cp >= MODIFIER_BASES[i] && cp <= MODIFIER_BASES[i + 1]) return true
        i += 2
    }
    return false
}

private val toneCache = LruCache<String, String>(256)
private val glyphPaint = Paint()

/**
 * Adds the fair skin tone to every hand/person in the emoji sequence that has none. Kept as is
 * when any tone is already set, or when this phone's emoji font has no glyph for the toned form
 * (e.g. family sequences), so it never breaks into separate pictures.
 */
@Synchronized
private fun withFairSkinTone(emoji: String): String {
    toneCache.get(emoji)?.let { return it }
    var hasBase = false
    var i = 0
    while (i < emoji.length) {
        val cp = emoji.codePointAt(i)
        if (isSkinTone(cp)) {
            toneCache.put(emoji, emoji)
            return emoji
        }
        if (isModifierBase(cp)) hasBase = true
        i += Character.charCount(cp)
    }
    var result = emoji
    if (hasBase) {
        val toned = StringBuilder(emoji.length + 4)
        i = 0
        while (i < emoji.length) {
            val cp = emoji.codePointAt(i)
            i += Character.charCount(cp)
            toned.appendCodePoint(cp)
            if (isModifierBase(cp)) {
                // Skin tone sequences don't use the emoji variation selector
                if (i < emoji.length && emoji[i].code == 0xFE0F) i++
                toned.appendCodePoint(FAIR_SKIN_TONE)
            }
        }
        if (glyphPaint.hasGlyph(toned.toString())) result = toned.toString()
    }
    toneCache.put(emoji, result)
    return result
}

// ---- Fair-toned smiley faces ------------------------------------------------------------------

/** Human smiley faces (yellow in emoji fonts); cats, robots, ghosts etc. are left alone. */
private val FACE_RANGES = intArrayOf(
    0x1F600, 0x1F637, 0x1F641, 0x1F644, 0x1F910, 0x1F915, 0x1F917, 0x1F917, 0x1F920, 0x1F925,
    0x1F927, 0x1F92F, 0x1F970, 0x1F976, 0x1F978, 0x1F97A, 0x1F9D0, 0x1F9D0, 0x1FAE0, 0x1FAE5,
    0x1FAE8, 0x1FAE8, 0x263A, 0x263A, 0x2639, 0x2639
)

/** Faces drawn red, green or blue on purpose (angry, sick, hot, cold, exploding): left alone. */
private val COLORED_FACES = setOf(0x1F621, 0x1F92C, 0x1F922, 0x1F92E, 0x1F92F, 0x1F975, 0x1F976)

private fun isFace(emoji: String): Boolean {
    val cp = emoji.codePointAt(0)
    if (cp in COLORED_FACES) return false
    var i = 0
    while (i < FACE_RANGES.size) {
        if (cp >= FACE_RANGES[i] && cp <= FACE_RANGES[i + 1]) return true
        i += 2
    }
    return false
}

private object FaceEmojiImages {
    private const val SIZE_PX = 144
    private val images = LruCache<String, ImageBitmap>(80)
    private val unsupported = HashSet<String>()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val bounds = Rect()

    @Synchronized
    fun canDraw(face: String): Boolean {
        if (face in unsupported) return false
        if (images.get(face) != null) return true
        return paint.hasGlyph(face).also { if (!it) unsupported += face }
    }

    @Synchronized
    fun get(face: String): ImageBitmap? {
        images.get(face)?.let { return it }
        if (!canDraw(face)) return null
        val bitmap = render(face)
        if (bitmap == null) {
            unsupported += face
            return null
        }
        recolorToFairSkin(bitmap)
        return bitmap.asImageBitmap().also { images.put(face, it) }
    }

    /** Draws the system emoji glyph tightly into a square bitmap. */
    private fun render(face: String): Bitmap? {
        paint.textSize = 100f
        paint.getTextBounds(face, 0, face.length, bounds)
        val largest = max(bounds.width(), bounds.height())
        if (largest <= 0) return null
        paint.textSize = 100f * SIZE_PX * 0.96f / largest
        paint.getTextBounds(face, 0, face.length, bounds)
        val bitmap = Bitmap.createBitmap(SIZE_PX, SIZE_PX, Bitmap.Config.ARGB_8888)
        Canvas(bitmap).drawText(face, SIZE_PX / 2f - bounds.exactCenterX(), SIZE_PX / 2f - bounds.exactCenterY(), paint)
        return bitmap
    }

    /**
     * Moves the bright yellow/orange face skin to a fair skin tone, keeping its shading (orange
     * areas of a yellow face are its shadows). Dark features, red blush, hearts, tears and white
     * eyes are outside the hue/brightness window and keep their colours; edges blend smoothly.
     */
    private fun recolorToFairSkin(bitmap: Bitmap) {
        val width = bitmap.width
        val height = bitmap.height
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
        val hsv = FloatArray(3)
        for (i in pixels.indices) {
            val c = pixels[i]
            val alpha = c ushr 24
            if (alpha == 0) continue
            android.graphics.Color.colorToHSV(c, hsv)
            val hue = hsv[0]
            val sat = hsv[1]
            val value = hsv[2]
            val weight = smoothstep(18f, 28f, hue) * (1f - smoothstep(60f, 70f, hue)) *
                smoothstep(0.28f, 0.42f, sat) * smoothstep(0.48f, 0.68f, value)
            if (weight <= 0f) continue
            val shade = ((46f - hue) / 18f).coerceIn(0f, 1f)
            hsv[0] = FACE_SKIN_HUE
            hsv[1] = (sat * 0.29f + shade * 0.08f).coerceIn(0.12f, 0.42f)
            hsv[2] = (0.58f + 0.39f * value - shade * 0.10f).coerceIn(0f, 0.98f)
            val mapped = android.graphics.Color.HSVToColor(hsv)
            pixels[i] = android.graphics.Color.argb(
                alpha,
                mix(android.graphics.Color.red(c), android.graphics.Color.red(mapped), weight),
                mix(android.graphics.Color.green(c), android.graphics.Color.green(mapped), weight),
                mix(android.graphics.Color.blue(c), android.graphics.Color.blue(mapped), weight)
            )
        }
        bitmap.setPixels(pixels, 0, width, 0, 0, width, height)
    }

    private fun smoothstep(edge0: Float, edge1: Float, x: Float): Float {
        val t = ((x - edge0) / (edge1 - edge0)).coerceIn(0f, 1f)
        return t * t * (3f - 2f * t)
    }

    private fun mix(from: Int, to: Int, t: Float): Int = (from + (to - from) * t).toInt().coerceIn(0, 255)
}

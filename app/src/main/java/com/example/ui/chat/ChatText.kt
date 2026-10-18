package com.example.ui.chat

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import com.example.security.SecurityPreferences
import com.example.ui.theme.RomanticFontFamily
import com.example.ui.theme.SweetieFontFamily

/** The fonts chat messages can be written in (Settings > Appearance & Experience). */
enum class ChatFont(
    val title: String,
    val caption: String,
    // null: the app's own font
    val family: FontFamily?,
    // A script reads small for its size, so it's drawn this much bigger
    val scale: Float
) {
    Default("Default", "App font", null, 1f),
    // A sweet, rounded handwriting, like MIUI's Sweetie
    Sweetie("Sweetie", "Font 1", SweetieFontFamily, 1.05f),
    // A flowing romantic cursive
    Romantic("Romantic", "Font 2", RomanticFontFamily, 1.4f);

    companion object {
        fun of(index: Int): ChatFont = entries.getOrElse(index) { Default }
    }
}

/** How the chat's messages read: their text size (sp, in normal chat) and font. */
@Immutable
data class ChatTextStyle(
    val sizeSp: Int = SecurityPreferences.DEFAULT_CHAT_TEXT_SIZE,
    val font: ChatFont = ChatFont.Default
) {
    /** The text size as drawn, [extraSp] bigger (private chat reads 2sp bigger, like ChatGPT). */
    fun fontSize(extraSp: Int = 0): TextUnit = ((sizeSp + extraSp) * font.scale).sp

    /** [base] at this size and in this font, with room between the lines. */
    fun applyTo(base: TextStyle, extraSp: Int = 0): TextStyle {
        val size = fontSize(extraSp)
        return base.copy(
            fontSize = size,
            lineHeight = size * LINE_SPACING,
            fontFamily = font.family ?: base.fontFamily
        )
    }

    private companion object {
        /** Line height for the text size (the chat's 14sp text had 20sp lines). */
        const val LINE_SPACING = 1.43f
    }
}

/** The text style of the chat's messages and message box, from Settings. */
val LocalChatTextStyle = staticCompositionLocalOf { ChatTextStyle() }

package com.example.ui.disguise

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import java.text.DecimalFormatSymbols
import java.util.Locale
import kotlin.reflect.KProperty

/** Canonical expression and real selection, shared by touch editing and the calculator keypad. */
internal class CalculatorEditor {
    var value by mutableStateOf(TextFieldValue("0", TextRange(1)))
        private set

    operator fun getValue(owner: Any?, property: KProperty<*>): String = value.text
    operator fun setValue(owner: Any?, property: KProperty<*>, text: String) {
        value = TextFieldValue(text, TextRange(text.length))
    }

    val editingInside: Boolean
        get() = !value.selection.collapsed || value.selection.end != value.text.length

    fun replaceSelection(text: String) {
        val start = value.selection.min
        val end = value.selection.max
        val result = value.text.replaceRange(start, end, text)
        if (result.length > 512) return
        value = TextFieldValue(result, TextRange(start + text.length))
    }

    fun backspace() {
        if (!value.selection.collapsed) replaceSelection("")
        else if (value.selection.start > 0) {
            val end = value.selection.start
            value = value.copy(selection = TextRange(end - 1, end))
            replaceSelection("")
        }
    }

    fun update(next: TextFieldValue) {
        // Selection-only updates must preserve canonical decimal points (also in comma locales).
        if (next.text == value.text) {
            value = next
            return
        }
        val symbols = DecimalFormatSymbols.getInstance()
        val localizedPaste = symbols.decimalSeparator != '.' && next.text.contains(symbols.decimalSeparator)
        fun normalize(text: String): String = buildString {
            for (char in text) {
                when {
                    char == symbols.decimalSeparator -> append('.')
                    char == symbols.groupingSeparator && (char != '.' || localizedPaste) -> Unit
                    char == ',' -> Unit
                    char.isDigit() -> append(Character.digit(char, 10))
                    char in ".+-−×÷*/^%!()√π " || char in 'a'..'z' || char == 'E' -> append(char)
                }
            }
        }
        val clean = normalize(next.text)
        if (clean.length > 512) return
        value = TextFieldValue(clean, TextRange(
            normalize(next.text.take(next.selection.start)).length,
            normalize(next.text.take(next.selection.end)).length
        ))
    }
}

/** Formatting never changes the expression handed to the parser. */
internal class CalculatorNumberTransformation(
    private val locale: Locale = Locale.getDefault(),
    private val operatorColor: Color = Color.Unspecified
) : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val grouped = groupDigitsForDisplay(text.text)
        val symbols = DecimalFormatSymbols.getInstance(locale)
        val originalToDisplay = IntArray(text.length + 1)
        val displayToOriginal = IntArray(grouped.length + 1)
        var original = 0
        grouped.forEachIndexed { index, char ->
            displayToOriginal[index] = original
            if (char != ',') {
                originalToDisplay[original] = index
                original++
            }
        }
        originalToDisplay[text.length] = grouped.length
        displayToOriginal[grouped.length] = text.length
        val localized = grouped.map {
            when (it) {
                ',' -> symbols.groupingSeparator
                '.' -> symbols.decimalSeparator
                else -> it
            }
        }.joinToString("")
        val styled = AnnotatedString.Builder(localized).apply {
            localized.forEachIndexed { index, char ->
                if (char in "+−×÷%()^√") addStyle(SpanStyle(color = operatorColor), index, index + 1)
            }
        }.toAnnotatedString()
        return TransformedText(styled, object : OffsetMapping {
            override fun originalToTransformed(offset: Int) = originalToDisplay[offset.coerceIn(0, text.length)]
            override fun transformedToOriginal(offset: Int) = displayToOriginal[offset.coerceIn(0, grouped.length)]
        })
    }
}

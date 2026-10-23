package com.example.ui.disguise

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

class CalculatorBehaviorTest {
    @Test fun smallNonzeroResultsArePreserved() {
        assertEquals(1e-14, evaluateSamsungExpression("1 / 100000000000000", true).getOrThrow().toDouble(), 1e-28)
    }

    @Test fun invalidNumbersAndFractionalFactorialsAreRejected() {
        assertTrue(evaluateSamsungExpression("1..2 + 3", true).isFailure)
        assertTrue(evaluateSamsungExpression("0.5!", true).isFailure)
        assertTrue(evaluateSamsungExpression("1e999999999", true).isFailure)
    }

    @Test fun binaryRoundingNoiseDoesNotReachTheDisplay() {
        assertEquals("0.3", evaluateSamsungExpression("0.1 + 0.2", true).getOrThrow())
    }

    @Test fun keypadInsertionAndDeletionRespectSelection() {
        val editor = CalculatorEditor()
        editor.update(TextFieldValue("1234", TextRange(1, 3)))
        editor.replaceSelection("9")
        assertEquals("194", editor.value.text)
        assertEquals(TextRange(2), editor.value.selection)
        editor.backspace()
        assertEquals("14", editor.value.text)
        assertEquals(TextRange(1), editor.value.selection)
    }

    @Test fun groupedDisplayKeepsCursorOffsetsAndLocalizedSeparators() {
        val original = AnnotatedString("12345.6")
        val us = CalculatorNumberTransformation(Locale.US).filter(original)
        val german = CalculatorNumberTransformation(Locale.GERMANY).filter(original)
        assertEquals("12,345.6", us.text.text)
        assertEquals("12.345,6", german.text.text)
        for (offset in 0..original.length) {
            val shown = us.offsetMapping.originalToTransformed(offset)
            assertEquals(offset, us.offsetMapping.transformedToOriginal(shown))
        }
        assertEquals(2, us.offsetMapping.transformedToOriginal(2))
    }
}

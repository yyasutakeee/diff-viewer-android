package com.example.diffviewer.core.diffui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SyntaxHighlightedTextTest {
    @Test
    fun createsDifferentColorSpansForKotlinKeywordAndString() {
        val sourceLine = "fun greeting() = \"Hello\""

        val highlightedText = createSyntaxHighlightedText(
            prefix = "+",
            sourceLine = sourceLine,
            isKotlinSource = true,
            backgroundColor = Color.White,
        )

        val keywordStyle = requireNotNull(findSpanStyleForText(highlightedText, "fun"))
        val stringStyle = requireNotNull(findSpanStyleForText(highlightedText, "\"Hello\""))
        assertEquals("+$sourceLine", highlightedText.text)
        assertNotEquals(keywordStyle.color, stringStyle.color)
        assertNotEquals(Color.Unspecified, keywordStyle.color)
        assertNotEquals(Color.Unspecified, stringStyle.color)
        assertNull(findSpanStyleForText(highlightedText, "greeting"))
    }

    @Test
    fun doesNotCreateColorSpansForNonKotlinSource() {
        val highlightedText = createSyntaxHighlightedText(
            prefix = "+",
            sourceLine = "fun greeting() = \"Hello\"",
            isKotlinSource = false,
            backgroundColor = Color.White,
        )

        assertTrue(highlightedText.spanStyles.isEmpty())
    }

    private fun findSpanStyleForText(
        highlightedText: AnnotatedString,
        expectedText: String,
    ): SpanStyle? {
        return highlightedText.spanStyles.find { spanStyleRange ->
            highlightedText.text.substring(spanStyleRange.start, spanStyleRange.end) == expectedText
        }?.item
    }
}

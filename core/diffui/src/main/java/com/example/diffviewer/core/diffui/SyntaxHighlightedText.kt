package com.example.diffviewer.core.diffui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import com.example.diffviewer.core.syntaxhighlight.KotlinSyntaxLexer
import com.example.diffviewer.core.syntaxhighlight.SyntaxToken
import com.example.diffviewer.core.syntaxhighlight.SyntaxTokenKind

private val kotlinSyntaxLexer = KotlinSyntaxLexer()

@Composable
fun rememberSyntaxHighlightedText(
    prefix: String,
    sourceLine: String,
    isKotlinSource: Boolean,
    baseTextColor: Color,
    backgroundColor: Color,
): AnnotatedString {
    return remember(prefix, sourceLine, isKotlinSource, baseTextColor, backgroundColor) {
        createSyntaxHighlightedText(
            prefix = prefix,
            sourceLine = sourceLine,
            isKotlinSource = isKotlinSource,
            backgroundColor = backgroundColor,
        )
    }
}

internal fun createSyntaxHighlightedText(
    prefix: String,
    sourceLine: String,
    isKotlinSource: Boolean,
    backgroundColor: Color,
): AnnotatedString {
    val syntaxTokens = if (isKotlinSource) kotlinSyntaxLexer.tokenizeLine(sourceLine) else emptyList()
    val syntaxColorScheme = createSyntaxColorScheme(backgroundColor)
    return buildAnnotatedString {
        append(prefix)
        append(sourceLine)
        syntaxTokens.forEach { syntaxToken ->
            addStyle(
                style = syntaxToken.toSpanStyle(syntaxColorScheme),
                start = prefix.length + syntaxToken.startIndex,
                end = prefix.length + syntaxToken.endIndexExclusive,
            )
        }
    }
}

fun isKotlinSourcePath(filePath: String): Boolean {
    val lowercaseFilePath = filePath.lowercase()
    return lowercaseFilePath.endsWith(".kt") || lowercaseFilePath.endsWith(".kts")
}

private data class SyntaxColorScheme(
    val keywordColor: Color,
    val stringColor: Color,
    val commentColor: Color,
    val numberColor: Color,
    val annotationColor: Color,
)

private fun createSyntaxColorScheme(backgroundColor: Color): SyntaxColorScheme {
    return if (backgroundColor.luminance() < 0.4f) {
        SyntaxColorScheme(
            keywordColor = Color(0xFFD0BCFF),
            stringColor = Color(0xFFA8E6CF),
            commentColor = Color(0xFFD0D0D0),
            numberColor = Color(0xFF9CDCFE),
            annotationColor = Color(0xFFFFD580),
        )
    } else {
        SyntaxColorScheme(
            keywordColor = Color(0xFF6F42C1),
            stringColor = Color(0xFF0B6E4F),
            commentColor = Color(0xFF5F6368),
            numberColor = Color(0xFF005CC5),
            annotationColor = Color(0xFF8A5100),
        )
    }
}

private fun SyntaxToken.toSpanStyle(syntaxColorScheme: SyntaxColorScheme): SpanStyle {
    return when (kind) {
        SyntaxTokenKind.KEYWORD -> SpanStyle(
            color = syntaxColorScheme.keywordColor,
            fontWeight = FontWeight.SemiBold,
        )
        SyntaxTokenKind.STRING,
        SyntaxTokenKind.CHARACTER -> SpanStyle(color = syntaxColorScheme.stringColor)
        SyntaxTokenKind.COMMENT -> SpanStyle(
            color = syntaxColorScheme.commentColor,
            fontStyle = FontStyle.Italic,
        )
        SyntaxTokenKind.NUMBER -> SpanStyle(color = syntaxColorScheme.numberColor)
        SyntaxTokenKind.ANNOTATION -> SpanStyle(color = syntaxColorScheme.annotationColor)
    }
}

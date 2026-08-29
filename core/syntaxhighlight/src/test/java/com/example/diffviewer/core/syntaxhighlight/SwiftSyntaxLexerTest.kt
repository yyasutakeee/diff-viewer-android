package com.example.diffviewer.core.syntaxhighlight

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class SwiftSyntaxLexerTest {
    private val swiftSyntaxLexer = SwiftSyntaxLexer()

    @Test
    fun tokenizesRepresentativeSwiftSyntax() {
        val sourceLine = "@MainActor final class Counter { let value = 42 }"

        val syntaxTokens = swiftSyntaxLexer.tokenizeLine(sourceLine)

        assertToken(sourceLine, syntaxTokens, "@MainActor", SyntaxTokenKind.ANNOTATION)
        assertToken(sourceLine, syntaxTokens, "final", SyntaxTokenKind.KEYWORD)
        assertToken(sourceLine, syntaxTokens, "class", SyntaxTokenKind.KEYWORD)
        assertToken(sourceLine, syntaxTokens, "let", SyntaxTokenKind.KEYWORD)
        assertToken(sourceLine, syntaxTokens, "42", SyntaxTokenKind.NUMBER)
    }

    @Test
    fun keepsCommentContentsAsOneCommentToken() {
        val sourceLine = "// func value() -> String"

        val syntaxTokens = swiftSyntaxLexer.tokenizeLine(sourceLine)

        assertEquals(1, syntaxTokens.size)
        assertEquals(SyntaxTokenKind.COMMENT, syntaxTokens.single().kind)
    }

    @Test
    fun doesNotStartCommentInsideAString() {
        val sourceLine = "let endpoint = \"http://127.0.0.1\""

        val syntaxTokens = swiftSyntaxLexer.tokenizeLine(sourceLine)

        assertToken(sourceLine, syntaxTokens, "let", SyntaxTokenKind.KEYWORD)
        assertToken(sourceLine, syntaxTokens, "\"http://127.0.0.1\"", SyntaxTokenKind.STRING)
        assertFalse(syntaxTokens.any { syntaxToken -> syntaxToken.kind == SyntaxTokenKind.COMMENT })
    }

    @Test
    fun tokenizesPoundDirectives() {
        val sourceLine = "#if os(iOS)"

        val syntaxTokens = swiftSyntaxLexer.tokenizeLine(sourceLine)

        assertToken(sourceLine, syntaxTokens, "#if", SyntaxTokenKind.KEYWORD)
    }

    private fun assertToken(
        sourceLine: String,
        syntaxTokens: List<SyntaxToken>,
        expectedText: String,
        expectedKind: SyntaxTokenKind,
    ) {
        val matchingSyntaxToken = syntaxTokens.find { syntaxToken ->
            sourceLine.substring(syntaxToken.startIndex, syntaxToken.endIndexExclusive) == expectedText
        }
        assertEquals(expectedKind, requireNotNull(matchingSyntaxToken).kind)
    }
}

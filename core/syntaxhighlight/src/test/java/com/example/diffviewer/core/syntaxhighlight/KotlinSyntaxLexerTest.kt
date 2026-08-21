package com.example.diffviewer.core.syntaxhighlight

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class KotlinSyntaxLexerTest {
    private val kotlinSyntaxLexer = KotlinSyntaxLexer()

    @Test
    fun tokenizesRepresentativeKotlinSyntax() {
        val sourceLine = "@Composable fun Greeting(count: Int = 42)"

        val syntaxTokens = kotlinSyntaxLexer.tokenizeLine(sourceLine)

        assertToken(sourceLine, syntaxTokens, "@Composable", SyntaxTokenKind.ANNOTATION)
        assertToken(sourceLine, syntaxTokens, "fun", SyntaxTokenKind.KEYWORD)
        assertToken(sourceLine, syntaxTokens, "42", SyntaxTokenKind.NUMBER)
    }

    @Test
    fun keepsCommentContentsAsOneCommentToken() {
        val sourceLine = "// fun value = \"text\""

        val syntaxTokens = kotlinSyntaxLexer.tokenizeLine(sourceLine)

        assertEquals(1, syntaxTokens.size)
        assertEquals(SyntaxTokenKind.COMMENT, syntaxTokens.single().kind)
    }

    @Test
    fun doesNotStartCommentInsideAString() {
        val sourceLine = "val endpoint = \"http://127.0.0.1\""

        val syntaxTokens = kotlinSyntaxLexer.tokenizeLine(sourceLine)

        assertToken(sourceLine, syntaxTokens, "val", SyntaxTokenKind.KEYWORD)
        assertToken(sourceLine, syntaxTokens, "\"http://127.0.0.1\"", SyntaxTokenKind.STRING)
        assertFalse(syntaxTokens.any { syntaxToken -> syntaxToken.kind == SyntaxTokenKind.COMMENT })
    }

    @Test
    fun respectsEscapedStringQuotes() {
        val sourceLine = "val message = \"say \\\"hello\\\"\""

        val syntaxTokens = kotlinSyntaxLexer.tokenizeLine(sourceLine)

        assertToken(sourceLine, syntaxTokens, "\"say \\\"hello\\\"\"", SyntaxTokenKind.STRING)
    }

    @Test
    fun tokenizesCharacterLiterals() {
        val sourceLine = "val slash = '\\\\'"

        val syntaxTokens = kotlinSyntaxLexer.tokenizeLine(sourceLine)

        assertToken(sourceLine, syntaxTokens, "'\\\\'", SyntaxTokenKind.CHARACTER)
    }

    @Test
    fun doesNotTreatKeywordPrefixesAsKeywords() {
        val sourceLine = "val valueClassName = functionValue"

        val syntaxTokens = kotlinSyntaxLexer.tokenizeLine(sourceLine)

        assertEquals(
            listOf("val"),
            syntaxTokens.filter { syntaxToken -> syntaxToken.kind == SyntaxTokenKind.KEYWORD }
                .map { syntaxToken -> sourceLine.substring(syntaxToken.startIndex, syntaxToken.endIndexExclusive) },
        )
    }

    private fun assertToken(
        sourceLine: String,
        syntaxTokens: List<SyntaxToken>,
        expectedText: String,
        expectedKind: SyntaxTokenKind,
    ) {
        val matchingSyntaxToken = findSyntaxToken(sourceLine, syntaxTokens, expectedText)
        assertEquals(expectedKind, matchingSyntaxToken.kind)
    }

    private fun findSyntaxToken(
        sourceLine: String,
        syntaxTokens: List<SyntaxToken>,
        expectedText: String,
    ): SyntaxToken {
        return requireNotNull(
            syntaxTokens.find { syntaxToken ->
                sourceLine.substring(syntaxToken.startIndex, syntaxToken.endIndexExclusive) == expectedText
            }
        )
    }
}

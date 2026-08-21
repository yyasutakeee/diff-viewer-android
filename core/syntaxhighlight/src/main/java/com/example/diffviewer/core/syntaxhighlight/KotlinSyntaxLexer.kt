package com.example.diffviewer.core.syntaxhighlight

class KotlinSyntaxLexer {
    fun tokenizeLine(sourceLine: String): List<SyntaxToken> {
        val syntaxTokens = mutableListOf<SyntaxToken>()
        var currentIndex = 0
        while (currentIndex < sourceLine.length) {
            val tokenEndIndex = when {
                sourceLine.startsWith("//", currentIndex) -> {
                    syntaxTokens += sourceLine.createToken(
                        startIndex = currentIndex,
                        endIndexExclusive = sourceLine.length,
                        kind = SyntaxTokenKind.COMMENT,
                    )
                    sourceLine.length
                }
                sourceLine.startsWith("/*", currentIndex) -> {
                    val endIndexExclusive = findBlockCommentEnd(sourceLine, currentIndex)
                    syntaxTokens += sourceLine.createToken(
                        startIndex = currentIndex,
                        endIndexExclusive = endIndexExclusive,
                        kind = SyntaxTokenKind.COMMENT,
                    )
                    endIndexExclusive
                }
                sourceLine.startsWith("\"\"\"", currentIndex) -> {
                    val endIndexExclusive = findTripleQuotedStringEnd(sourceLine, currentIndex)
                    syntaxTokens += sourceLine.createToken(
                        startIndex = currentIndex,
                        endIndexExclusive = endIndexExclusive,
                        kind = SyntaxTokenKind.STRING,
                    )
                    endIndexExclusive
                }
                sourceLine[currentIndex] == '"' -> {
                    val endIndexExclusive = findQuotedValueEnd(sourceLine, currentIndex, '"')
                    syntaxTokens += sourceLine.createToken(
                        startIndex = currentIndex,
                        endIndexExclusive = endIndexExclusive,
                        kind = SyntaxTokenKind.STRING,
                    )
                    endIndexExclusive
                }
                sourceLine[currentIndex] == '\'' -> {
                    val endIndexExclusive = findQuotedValueEnd(sourceLine, currentIndex, '\'')
                    syntaxTokens += sourceLine.createToken(
                        startIndex = currentIndex,
                        endIndexExclusive = endIndexExclusive,
                        kind = SyntaxTokenKind.CHARACTER,
                    )
                    endIndexExclusive
                }
                sourceLine[currentIndex] == '@' && sourceLine.hasIdentifierStartAt(currentIndex + 1) -> {
                    val endIndexExclusive = findAnnotationEnd(sourceLine, currentIndex)
                    syntaxTokens += sourceLine.createToken(
                        startIndex = currentIndex,
                        endIndexExclusive = endIndexExclusive,
                        kind = SyntaxTokenKind.ANNOTATION,
                    )
                    endIndexExclusive
                }
                sourceLine[currentIndex].isDigit() -> {
                    val endIndexExclusive = findNumberEnd(sourceLine, currentIndex)
                    syntaxTokens += sourceLine.createToken(
                        startIndex = currentIndex,
                        endIndexExclusive = endIndexExclusive,
                        kind = SyntaxTokenKind.NUMBER,
                    )
                    endIndexExclusive
                }
                sourceLine.hasIdentifierStartAt(currentIndex) -> {
                    val endIndexExclusive = findIdentifierEnd(sourceLine, currentIndex)
                    val identifier = sourceLine.substring(currentIndex, endIndexExclusive)
                    if (identifier in KOTLIN_KEYWORDS) {
                        syntaxTokens += sourceLine.createToken(
                            startIndex = currentIndex,
                            endIndexExclusive = endIndexExclusive,
                            kind = SyntaxTokenKind.KEYWORD,
                        )
                    }
                    endIndexExclusive
                }
                else -> currentIndex + 1
            }
            currentIndex = tokenEndIndex
        }
        return syntaxTokens
    }

    private fun findBlockCommentEnd(sourceLine: String, startIndex: Int): Int {
        val closingIndex = sourceLine.indexOf("*/", startIndex = startIndex + 2)
        return if (closingIndex == -1) sourceLine.length else closingIndex + 2
    }

    private fun findTripleQuotedStringEnd(sourceLine: String, startIndex: Int): Int {
        val closingIndex = sourceLine.indexOf("\"\"\"", startIndex = startIndex + 3)
        return if (closingIndex == -1) sourceLine.length else closingIndex + 3
    }

    private fun findQuotedValueEnd(sourceLine: String, startIndex: Int, quote: Char): Int {
        var currentIndex = startIndex + 1
        var isEscaped = false
        while (currentIndex < sourceLine.length) {
            val currentCharacter = sourceLine[currentIndex]
            if (currentCharacter == quote && !isEscaped) return currentIndex + 1
            isEscaped = currentCharacter == '\\' && !isEscaped
            if (currentCharacter != '\\') isEscaped = false
            currentIndex += 1
        }
        return sourceLine.length
    }

    private fun findAnnotationEnd(sourceLine: String, startIndex: Int): Int {
        var currentIndex = startIndex + 1
        while (
            currentIndex < sourceLine.length &&
            (sourceLine[currentIndex].isIdentifierPart() || sourceLine[currentIndex] == '.')
        ) {
            currentIndex += 1
        }
        return currentIndex
    }

    private fun findNumberEnd(sourceLine: String, startIndex: Int): Int {
        var currentIndex = startIndex + 1
        while (
            currentIndex < sourceLine.length &&
            (sourceLine[currentIndex].isLetterOrDigit() ||
                sourceLine[currentIndex] == '_' ||
                sourceLine[currentIndex] == '.')
        ) {
            currentIndex += 1
        }
        return currentIndex
    }

    private fun findIdentifierEnd(sourceLine: String, startIndex: Int): Int {
        var currentIndex = startIndex + 1
        while (currentIndex < sourceLine.length && sourceLine[currentIndex].isIdentifierPart()) {
            currentIndex += 1
        }
        return currentIndex
    }

    private fun String.hasIdentifierStartAt(index: Int): Boolean {
        return index in indices && (this[index].isLetter() || this[index] == '_')
    }

    private fun Char.isIdentifierPart(): Boolean = isLetterOrDigit() || this == '_'

    private fun String.createToken(
        startIndex: Int,
        endIndexExclusive: Int,
        kind: SyntaxTokenKind,
    ): SyntaxToken {
        require(startIndex in indices)
        require(endIndexExclusive in (startIndex + 1)..length)
        return SyntaxToken(startIndex, endIndexExclusive, kind)
    }

    private companion object {
        val KOTLIN_KEYWORDS = setOf(
            "as", "break", "by", "catch", "class", "companion", "const", "constructor",
            "continue", "crossinline", "data", "delegate", "do", "dynamic", "else", "enum",
            "expect", "external", "false", "field", "file", "final", "finally", "for", "fun",
            "get", "if", "import", "in", "infix", "init", "inline", "inner", "interface", "internal",
            "is", "lateinit", "noinline", "null", "object", "open", "operator", "out", "override",
            "package", "param", "private", "property", "protected", "public", "receiver", "reified",
            "return", "sealed", "set", "setparam", "super", "suspend", "tailrec", "this", "throw",
            "true", "try", "typealias", "typeof", "val", "value", "var", "vararg", "when", "where",
            "while",
        )
    }
}

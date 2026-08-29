package com.example.diffviewer.core.syntaxhighlight

class SwiftSyntaxLexer {
    fun tokenizeLine(sourceLine: String): List<SyntaxToken> {
        val syntaxTokens = mutableListOf<SyntaxToken>()
        var currentIndex = 0
        while (currentIndex < sourceLine.length) {
            val tokenEndIndex = when {
                sourceLine.startsWith("//", currentIndex) -> {
                    syntaxTokens += sourceLine.createToken(currentIndex, sourceLine.length, SyntaxTokenKind.COMMENT)
                    sourceLine.length
                }
                sourceLine.startsWith("/*", currentIndex) -> {
                    val endIndexExclusive = findDelimitedEnd(sourceLine, currentIndex + 2, "*/")
                    syntaxTokens += sourceLine.createToken(currentIndex, endIndexExclusive, SyntaxTokenKind.COMMENT)
                    endIndexExclusive
                }
                sourceLine.startsWith("\"\"\"", currentIndex) -> {
                    val endIndexExclusive = findDelimitedEnd(sourceLine, currentIndex + 3, "\"\"\"")
                    syntaxTokens += sourceLine.createToken(currentIndex, endIndexExclusive, SyntaxTokenKind.STRING)
                    endIndexExclusive
                }
                sourceLine[currentIndex] == '"' -> {
                    val endIndexExclusive = findStringEnd(sourceLine, currentIndex)
                    syntaxTokens += sourceLine.createToken(currentIndex, endIndexExclusive, SyntaxTokenKind.STRING)
                    endIndexExclusive
                }
                sourceLine[currentIndex] == '@' && sourceLine.hasIdentifierStartAt(currentIndex + 1) -> {
                    val endIndexExclusive = findAttributeEnd(sourceLine, currentIndex)
                    syntaxTokens += sourceLine.createToken(currentIndex, endIndexExclusive, SyntaxTokenKind.ANNOTATION)
                    endIndexExclusive
                }
                sourceLine[currentIndex] == '#' && sourceLine.hasIdentifierStartAt(currentIndex + 1) -> {
                    val endIndexExclusive = findIdentifierEnd(sourceLine, currentIndex + 1)
                    val directive = sourceLine.substring(currentIndex, endIndexExclusive)
                    if (directive in SWIFT_POUND_KEYWORDS) {
                        syntaxTokens += sourceLine.createToken(currentIndex, endIndexExclusive, SyntaxTokenKind.KEYWORD)
                    }
                    endIndexExclusive
                }
                sourceLine[currentIndex].isDigit() -> {
                    val endIndexExclusive = findNumberEnd(sourceLine, currentIndex)
                    syntaxTokens += sourceLine.createToken(currentIndex, endIndexExclusive, SyntaxTokenKind.NUMBER)
                    endIndexExclusive
                }
                sourceLine.hasIdentifierStartAt(currentIndex) -> {
                    val endIndexExclusive = findIdentifierEnd(sourceLine, currentIndex)
                    val identifier = sourceLine.substring(currentIndex, endIndexExclusive)
                    if (identifier in SWIFT_KEYWORDS) {
                        syntaxTokens += sourceLine.createToken(currentIndex, endIndexExclusive, SyntaxTokenKind.KEYWORD)
                    }
                    endIndexExclusive
                }
                else -> currentIndex + 1
            }
            currentIndex = tokenEndIndex
        }
        return syntaxTokens
    }

    private fun findDelimitedEnd(sourceLine: String, contentStartIndex: Int, delimiter: String): Int {
        val closingIndex = sourceLine.indexOf(delimiter, startIndex = contentStartIndex)
        return if (closingIndex == -1) sourceLine.length else closingIndex + delimiter.length
    }

    private fun findStringEnd(sourceLine: String, startIndex: Int): Int {
        var currentIndex = startIndex + 1
        var isEscaped = false
        while (currentIndex < sourceLine.length) {
            val currentCharacter = sourceLine[currentIndex]
            if (currentCharacter == '"' && !isEscaped) return currentIndex + 1
            isEscaped = currentCharacter == '\\' && !isEscaped
            if (currentCharacter != '\\') isEscaped = false
            currentIndex += 1
        }
        return sourceLine.length
    }

    private fun findAttributeEnd(sourceLine: String, startIndex: Int): Int {
        var currentIndex = startIndex + 1
        while (currentIndex < sourceLine.length && sourceLine[currentIndex].isIdentifierPart()) {
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
        val SWIFT_KEYWORDS = setOf(
            "Any", "Self", "Type", "actor", "any", "as", "associatedtype", "associativity",
            "async", "await", "borrowing", "break", "case", "catch", "class", "consuming",
            "continue", "convenience", "copy", "default", "defer", "deinit", "didSet", "distributed",
            "do", "dynamic", "else", "enum", "extension", "fallthrough", "false", "fileprivate",
            "final", "for", "func", "get", "guard", "if", "import", "indirect", "in", "infix",
            "init", "inout", "internal", "is", "isolated", "lazy", "left", "let", "macro",
            "mutating", "nil", "nonisolated", "nonmutating", "none", "open", "operator", "optional",
            "override", "package", "postfix", "precedence", "precedencegroup", "prefix", "private",
            "protocol", "public", "repeat", "required", "rethrows", "return", "right", "set", "some",
            "static", "struct", "subscript", "super", "switch", "throws", "true", "try", "typealias",
            "unowned", "var", "weak", "where", "while", "willSet", "yield",
        )

        val SWIFT_POUND_KEYWORDS = setOf(
            "#available", "#colorLiteral", "#column", "#dsohandle", "#else", "#elseif", "#endif",
            "#error", "#file", "#fileID", "#fileLiteral", "#filePath", "#function", "#if",
            "#imageLiteral", "#keyPath", "#line", "#selector", "#sourceLocation", "#unavailable", "#warning",
        )
    }
}

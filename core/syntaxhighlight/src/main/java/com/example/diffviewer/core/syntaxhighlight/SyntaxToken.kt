package com.example.diffviewer.core.syntaxhighlight

data class SyntaxToken(
    val startIndex: Int,
    val endIndexExclusive: Int,
    val kind: SyntaxTokenKind,
)

enum class SyntaxTokenKind {
    KEYWORD,
    STRING,
    CHARACTER,
    COMMENT,
    NUMBER,
    ANNOTATION,
}

package com.example.diffviewer.core.diffui

data class DiffFileDisplay(
    val id: String,
    val path: String,
    val isBinary: Boolean,
    val hunkDisplays: List<DiffHunkDisplay>,
)

data class DiffHunkDisplay(
    val id: String,
    val header: String,
    val lineDisplays: List<DiffLineDisplay>,
)

data class DiffLineDisplay(
    val id: String,
    val kind: DiffLineDisplayKind,
    val content: String,
    val oldLine: Int?,
    val newLine: Int?,
)

enum class DiffLineDisplayKind {
    CONTEXT,
    ADDITION,
    DELETION,
    META,
}

data class DiffDisplayConfiguration(
    val fontSizeSp: Int,
    val canDecreaseFontSize: Boolean,
    val canIncreaseFontSize: Boolean,
)

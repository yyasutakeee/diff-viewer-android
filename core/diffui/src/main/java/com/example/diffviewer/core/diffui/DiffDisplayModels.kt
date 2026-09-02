package com.example.diffviewer.core.diffui

data class DiffFileDisplay(
    val id: String,
    val path: String,
    val isBinary: Boolean,
    val contentUnavailableMessage: String?,
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
    val isLineWrappingEnabled: Boolean,
    val colorPalette: DiffColorPalette,
)

data class DiffColorPalette(
    val additionBackgroundArgb: Int,
    val additionTextArgb: Int,
    val deletionBackgroundArgb: Int,
    val deletionTextArgb: Int,
)

enum class DiffColorPreset(
    val displayName: String,
    val colorPalette: DiffColorPalette,
) {
    STANDARD(
        displayName = "標準",
        colorPalette = DiffColorPalette(
            additionBackgroundArgb = 0xFFE6F4EA.toInt(),
            additionTextArgb = 0xFF137333.toInt(),
            deletionBackgroundArgb = 0xFFFCE8E6.toInt(),
            deletionTextArgb = 0xFFB3261E.toInt(),
        ),
    ),
    DEEP(
        displayName = "ディープ",
        colorPalette = DiffColorPalette(
            additionBackgroundArgb = 0xFF0B5D1E.toInt(),
            additionTextArgb = 0xFFFFFFFF.toInt(),
            deletionBackgroundArgb = 0xFF8B1E1E.toInt(),
            deletionTextArgb = 0xFFFFFFFF.toInt(),
        ),
    ),
    BLUE(
        displayName = "ブルー",
        colorPalette = DiffColorPalette(
            additionBackgroundArgb = 0xFF0D47A1.toInt(),
            additionTextArgb = 0xFFFFFFFF.toInt(),
            deletionBackgroundArgb = 0xFF9C3D00.toInt(),
            deletionTextArgb = 0xFFFFFFFF.toInt(),
        ),
    ),
    DARK(
        displayName = "ダーク",
        colorPalette = DiffColorPalette(
            additionBackgroundArgb = 0xFF123D24.toInt(),
            additionTextArgb = 0xFF8FF0A4.toInt(),
            deletionBackgroundArgb = 0xFF4A1717.toInt(),
            deletionTextArgb = 0xFFFFA0A0.toInt(),
        ),
    ),
    HIGH_CONTRAST(
        displayName = "高コントラスト",
        colorPalette = DiffColorPalette(
            additionBackgroundArgb = 0xFF004D00.toInt(),
            additionTextArgb = 0xFFFFFFFF.toInt(),
            deletionBackgroundArgb = 0xFF8B0000.toInt(),
            deletionTextArgb = 0xFFFFFFFF.toInt(),
        ),
    ),
}

val DefaultDiffColorPalette = DiffColorPreset.STANDARD.colorPalette

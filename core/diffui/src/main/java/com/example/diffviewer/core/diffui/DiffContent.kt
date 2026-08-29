package com.example.diffviewer.core.diffui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun DiffDisplayControls(
    diffDisplayConfiguration: DiffDisplayConfiguration,
    decreaseFontSize: () -> Unit,
    increaseFontSize: () -> Unit,
    updateColorPalette: (DiffColorPalette) -> Unit,
) {
    var isColorSettingsVisible by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("折り返し表示", modifier = Modifier.weight(1f))
        Button(onClick = { isColorSettingsVisible = true }) { Text("配色") }
        Button(
            onClick = decreaseFontSize,
            enabled = diffDisplayConfiguration.canDecreaseFontSize,
        ) { Text("−") }
        Text("${diffDisplayConfiguration.fontSizeSp}sp")
        Button(
            onClick = increaseFontSize,
            enabled = diffDisplayConfiguration.canIncreaseFontSize,
        ) { Text("＋") }
    }
    if (isColorSettingsVisible) {
        DiffColorSettingsSheet(
            currentColorPalette = diffDisplayConfiguration.colorPalette,
            dismiss = { isColorSettingsVisible = false },
            applyColorPalette = { diffColorPalette ->
                updateColorPalette(diffColorPalette)
                isColorSettingsVisible = false
            },
        )
    }
}

fun LazyListScope.diffFileContent(
    diffFileDisplay: DiffFileDisplay,
    fontSizeSp: Int,
    colorPalette: DiffColorPalette,
    showFileHeader: Boolean,
) {
    val syntaxLanguage = syntaxLanguageForPath(diffFileDisplay.path)
    if (showFileHeader) {
        item(key = "file:${diffFileDisplay.id}") {
            Text(
                text = diffFileDisplay.path,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.primaryContainer)
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                fontFamily = FontFamily.Monospace,
                style = MaterialTheme.typography.titleSmall,
            )
        }
    }
    when {
        diffFileDisplay.contentUnavailableMessage != null -> {
            item(key = "unavailable:${diffFileDisplay.id}") {
                DiffEmptyMessage(diffFileDisplay.contentUnavailableMessage)
            }
        }
        diffFileDisplay.isBinary -> {
            item(key = "binary:${diffFileDisplay.id}") {
                DiffEmptyMessage("バイナリファイルの内容は表示できません")
            }
        }
        diffFileDisplay.hunkDisplays.isEmpty() -> {
            item(key = "empty:${diffFileDisplay.id}") {
                DiffEmptyMessage("表示できる行差分はありません")
            }
        }
        else -> diffFileDisplay.hunkDisplays.forEach { diffHunkDisplay ->
            item(key = "hunk:${diffFileDisplay.id}:${diffHunkDisplay.id}") {
                Text(
                    text = diffHunkDisplay.header,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.secondaryContainer)
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    fontFamily = FontFamily.Monospace,
                    fontSize = fontSizeSp.sp,
                )
            }
            items(
                items = diffHunkDisplay.lineDisplays,
                key = { diffLineDisplay ->
                    "line:${diffFileDisplay.id}:${diffHunkDisplay.id}:${diffLineDisplay.id}"
                },
            ) { diffLineDisplay ->
                DiffLineRow(
                    diffLineDisplay = diffLineDisplay,
                    fontSizeSp = fontSizeSp,
                    colorPalette = colorPalette,
                    syntaxLanguage = syntaxLanguage,
                )
            }
        }
    }
}

@Composable
private fun DiffEmptyMessage(message: String) {
    Box(
        modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(message)
    }
}

@Composable
private fun DiffLineRow(
    diffLineDisplay: DiffLineDisplay,
    fontSizeSp: Int,
    colorPalette: DiffColorPalette,
    syntaxLanguage: SyntaxLanguage?,
) {
    val backgroundColor = when (diffLineDisplay.kind) {
        DiffLineDisplayKind.ADDITION -> Color(colorPalette.additionBackgroundArgb)
        DiffLineDisplayKind.DELETION -> Color(colorPalette.deletionBackgroundArgb)
        DiffLineDisplayKind.META -> MaterialTheme.colorScheme.surfaceVariant
        DiffLineDisplayKind.CONTEXT -> Color.Transparent
    }
    val textColor = when (diffLineDisplay.kind) {
        DiffLineDisplayKind.ADDITION -> Color(colorPalette.additionTextArgb)
        DiffLineDisplayKind.DELETION -> Color(colorPalette.deletionTextArgb)
        else -> MaterialTheme.colorScheme.onSurface
    }
    val prefix = when (diffLineDisplay.kind) {
        DiffLineDisplayKind.ADDITION -> "+"
        DiffLineDisplayKind.DELETION -> "-"
        DiffLineDisplayKind.CONTEXT -> " "
        DiffLineDisplayKind.META -> "\\"
    }
    val effectiveBackgroundColor = when (diffLineDisplay.kind) {
        DiffLineDisplayKind.CONTEXT -> MaterialTheme.colorScheme.surface
        else -> backgroundColor
    }
    val highlightedText = rememberSyntaxHighlightedText(
        prefix = prefix,
        sourceLine = diffLineDisplay.content,
        syntaxLanguage = syntaxLanguage.takeUnless { diffLineDisplay.kind == DiffLineDisplayKind.META },
        baseTextColor = textColor,
        backgroundColor = effectiveBackgroundColor,
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(backgroundColor)
            .padding(vertical = 1.dp),
        verticalAlignment = Alignment.Top,
    ) {
        DiffLineNumber(diffLineDisplay.oldLine, fontSizeSp)
        DiffLineNumber(diffLineDisplay.newLine, fontSizeSp)
        Text(
            text = highlightedText,
            modifier = Modifier.weight(1f),
            color = Color.Unspecified,
            fontFamily = FontFamily.Monospace,
            fontSize = fontSizeSp.sp,
            lineHeight = (fontSizeSp + 2).sp,
            softWrap = true,
            style = TextStyle(
                platformStyle = PlatformTextStyle(includeFontPadding = false),
            ),
        )
    }
}

@Composable
private fun DiffLineNumber(lineNumber: Int?, fontSizeSp: Int) {
    Text(
        text = lineNumber?.toString()?.padStart(4).orEmpty().padStart(4),
        modifier = Modifier.width((fontSizeSp * 2.5f).coerceAtLeast(40f).dp),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontFamily = FontFamily.Monospace,
        fontSize = fontSizeSp.sp,
        maxLines = 1,
        softWrap = false,
        style = TextStyle(
            platformStyle = PlatformTextStyle(includeFontPadding = false),
        ),
    )
}

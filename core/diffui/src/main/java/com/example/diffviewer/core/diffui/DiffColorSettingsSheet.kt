package com.example.diffviewer.core.diffui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiffColorSettingsSheet(
    currentColorPalette: DiffColorPalette,
    dismiss: () -> Unit,
    applyColorPalette: (DiffColorPalette) -> Unit,
) {
    var colorHexValues by remember(currentColorPalette) {
        mutableStateOf(DiffColorHexValues.fromColorPalette(currentColorPalette))
    }
    val parsedColorPalette = colorHexValues.toColorPaletteOrNull()

    ModalBottomSheet(onDismissRequest = dismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("差分の配色", style = MaterialTheme.typography.titleLarge)
            Text("プリセット", style = MaterialTheme.typography.titleSmall)
            DiffColorPreset.entries.forEach { diffColorPreset ->
                FilterChip(
                    selected = parsedColorPalette == diffColorPreset.colorPalette,
                    onClick = {
                        colorHexValues = DiffColorHexValues.fromColorPalette(
                            diffColorPreset.colorPalette
                        )
                    },
                    label = { Text(diffColorPreset.displayName) },
                )
            }
            ColorHexField(
                label = "追加行の背景 #AARRGGBB",
                value = colorHexValues.additionBackground,
                updateValue = {
                    colorHexValues = colorHexValues.copy(additionBackground = it)
                },
            )
            ColorHexField(
                label = "追加行の文字 #AARRGGBB",
                value = colorHexValues.additionText,
                updateValue = { colorHexValues = colorHexValues.copy(additionText = it) },
            )
            ColorHexField(
                label = "削除行の背景 #AARRGGBB",
                value = colorHexValues.deletionBackground,
                updateValue = {
                    colorHexValues = colorHexValues.copy(deletionBackground = it)
                },
            )
            ColorHexField(
                label = "削除行の文字 #AARRGGBB",
                value = colorHexValues.deletionText,
                updateValue = { colorHexValues = colorHexValues.copy(deletionText = it) },
            )
            if (parsedColorPalette == null) {
                Text(
                    "色は #RRGGBB または #AARRGGBB で入力してください",
                    color = MaterialTheme.colorScheme.error,
                )
            } else {
                DiffColorPreview(parsedColorPalette)
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedButton(
                    onClick = {
                        colorHexValues = DiffColorHexValues.fromColorPalette(
                            DefaultDiffColorPalette
                        )
                    },
                    modifier = Modifier.weight(1f),
                ) { Text("標準に戻す") }
                Button(
                    onClick = { parsedColorPalette?.let(applyColorPalette) },
                    enabled = parsedColorPalette != null,
                    modifier = Modifier.weight(1f),
                ) { Text("適用") }
            }
        }
    }
}

@Composable
private fun ColorHexField(label: String, value: String, updateValue: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = updateValue,
        modifier = Modifier.fillMaxWidth(),
        label = { Text(label) },
        singleLine = true,
        isError = parseColorArgbOrNull(value) == null,
    )
}

@Composable
private fun DiffColorPreview(diffColorPalette: DiffColorPalette) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("プレビュー", style = MaterialTheme.typography.titleSmall)
        Text(
            "+追加された行",
            modifier = Modifier.fillMaxWidth().background(
                Color(diffColorPalette.additionBackgroundArgb)
            ).padding(8.dp),
            color = Color(diffColorPalette.additionTextArgb),
            fontFamily = FontFamily.Monospace,
        )
        Text(
            "-削除された行",
            modifier = Modifier.fillMaxWidth().background(
                Color(diffColorPalette.deletionBackgroundArgb)
            ).padding(8.dp),
            color = Color(diffColorPalette.deletionTextArgb),
            fontFamily = FontFamily.Monospace,
        )
    }
}

private data class DiffColorHexValues(
    val additionBackground: String,
    val additionText: String,
    val deletionBackground: String,
    val deletionText: String,
) {
    fun toColorPaletteOrNull(): DiffColorPalette? {
        return DiffColorPalette(
            additionBackgroundArgb = parseColorArgbOrNull(additionBackground) ?: return null,
            additionTextArgb = parseColorArgbOrNull(additionText) ?: return null,
            deletionBackgroundArgb = parseColorArgbOrNull(deletionBackground) ?: return null,
            deletionTextArgb = parseColorArgbOrNull(deletionText) ?: return null,
        )
    }

    companion object {
        fun fromColorPalette(diffColorPalette: DiffColorPalette): DiffColorHexValues {
            return DiffColorHexValues(
                additionBackground = diffColorPalette.additionBackgroundArgb.toColorHex(),
                additionText = diffColorPalette.additionTextArgb.toColorHex(),
                deletionBackground = diffColorPalette.deletionBackgroundArgb.toColorHex(),
                deletionText = diffColorPalette.deletionTextArgb.toColorHex(),
            )
        }
    }
}

private fun parseColorArgbOrNull(colorHex: String): Int? {
    val normalizedHex = colorHex.trim().removePrefix("#")
    val argbHex = when (normalizedHex.length) {
        6 -> "FF$normalizedHex"
        8 -> normalizedHex
        else -> return null
    }
    return argbHex.toLongOrNull(16)?.toInt()
}

private fun Int.toColorHex(): String {
    return "#${toUInt().toString(16).uppercase().padStart(8, '0')}"
}

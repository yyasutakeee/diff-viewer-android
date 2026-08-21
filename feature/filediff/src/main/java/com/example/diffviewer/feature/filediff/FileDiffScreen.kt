package com.example.diffviewer.feature.filediff

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.diffviewer.core.diffui.DiffDisplayControls
import com.example.diffviewer.core.diffui.diffFileContent

@Composable
fun FileDiffScreen(viewModel: FileDiffViewModel) {
    val fileDiffUiState by viewModel.state.collectAsStateWithLifecycle()
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = { viewModel.send(FileDiffEvent.NavigateBack) }) { Text("戻る") }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    fileDiffUiState.diffFileDisplay.path,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.titleMedium,
                    fontFamily = FontFamily.Monospace,
                )
                Text(
                    fileDiffUiState.sourceLabel,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        DiffDisplayControls(
            diffDisplayConfiguration = fileDiffUiState.diffDisplayConfiguration,
            decreaseFontSize = { viewModel.send(FileDiffEvent.DecreaseFontSize) },
            increaseFontSize = { viewModel.send(FileDiffEvent.IncreaseFontSize) },
            updateColorPalette = { diffColorPalette ->
                viewModel.send(FileDiffEvent.UpdateColorPalette(diffColorPalette))
            },
        )
        HorizontalDivider()
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            diffFileContent(
                diffFileDisplay = fileDiffUiState.diffFileDisplay,
                fontSizeSp = fileDiffUiState.diffDisplayConfiguration.fontSizeSp,
                colorPalette = fileDiffUiState.diffDisplayConfiguration.colorPalette,
                showFileHeader = false,
            )
        }
    }
}

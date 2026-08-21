package com.example.diffviewer.feature.alldiffs

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.diffviewer.core.diffui.DiffDisplayControls
import com.example.diffviewer.core.diffui.diffFileContent

@Composable
fun AllDiffsScreen(viewModel: AllDiffsViewModel) {
    val allDiffsUiState by viewModel.state.collectAsStateWithLifecycle()
    Column(modifier = Modifier.fillMaxSize()) {
        TextButton(onClick = { viewModel.send(AllDiffsEvent.NavigateBack) }) { Text("戻る") }
        Text(
            text = allDiffsUiState.title,
            modifier = Modifier.padding(horizontal = 12.dp),
            style = MaterialTheme.typography.titleLarge,
        )
        DiffDisplayControls(
            diffDisplayConfiguration = allDiffsUiState.diffDisplayConfiguration,
            decreaseFontSize = { viewModel.send(AllDiffsEvent.DecreaseFontSize) },
            increaseFontSize = { viewModel.send(AllDiffsEvent.IncreaseFontSize) },
            updateColorPalette = { diffColorPalette ->
                viewModel.send(AllDiffsEvent.UpdateColorPalette(diffColorPalette))
            },
        )
        HorizontalDivider()
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            allDiffsUiState.groupDisplays.forEach { diffFileGroupDisplay ->
                item(key = "group:${diffFileGroupDisplay.id}") {
                    Text(
                        text = diffFileGroupDisplay.title,
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.tertiaryContainer)
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
                diffFileGroupDisplay.fileDisplays.forEach { diffFileDisplay ->
                    diffFileContent(
                        diffFileDisplay = diffFileDisplay,
                        fontSizeSp = allDiffsUiState.diffDisplayConfiguration.fontSizeSp,
                        colorPalette = allDiffsUiState.diffDisplayConfiguration.colorPalette,
                        showFileHeader = true,
                    )
                }
            }
        }
    }
}

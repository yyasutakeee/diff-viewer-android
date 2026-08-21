package com.example.diffviewer

import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.example.diffviewer.core.domain.AppStore
import com.example.diffviewer.feature.filediff.FileDiffScreen
import com.example.diffviewer.feature.repository.RepositoryScreen
import kotlinx.coroutines.CoroutineScope

@Composable
fun DiffViewerApplication(appStore: AppStore, coroutineScope: CoroutineScope) {
    var fileDiffSelectionTarget by remember { mutableStateOf<FileDiffSelectionTarget?>(null) }
    val repositoryViewModelAdapter = remember(appStore, coroutineScope) {
        RepositoryViewModelAdapter(
            appStore = appStore,
            coroutineScope = coroutineScope,
            openFile = { selectedFileDiffTarget -> fileDiffSelectionTarget = selectedFileDiffTarget },
        )
    }

    Surface {
        val currentFileDiffSelectionTarget = fileDiffSelectionTarget
        if (currentFileDiffSelectionTarget == null) {
            RepositoryScreen(viewModel = repositoryViewModelAdapter)
        } else {
            val fileDiffViewModelAdapter = remember(currentFileDiffSelectionTarget) {
                FileDiffViewModelAdapter(
                    fileDiffSelectionTarget = currentFileDiffSelectionTarget,
                    navigateBack = { fileDiffSelectionTarget = null },
                )
            }
            FileDiffScreen(viewModel = fileDiffViewModelAdapter)
        }
    }
}

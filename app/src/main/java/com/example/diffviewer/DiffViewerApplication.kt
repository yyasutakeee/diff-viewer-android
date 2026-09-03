package com.example.diffviewer

import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.example.diffviewer.core.domain.AppStore
import com.example.diffviewer.feature.alldiffs.AllDiffsScreen
import com.example.diffviewer.feature.filediff.FileDiffScreen
import com.example.diffviewer.feature.repository.RepositoryScreen
import kotlinx.coroutines.CoroutineScope

@Composable
fun DiffViewerApplication(
    appStore: AppStore,
    diffDisplaySettingsStore: DiffDisplaySettingsStore,
    coroutineScope: CoroutineScope,
    hasLocalStorageAccess: Boolean,
    requestLocalStorageAccess: () -> Unit,
    chooseLocalRepository: () -> Unit,
) {
    var diffViewerDestination by remember {
        mutableStateOf<DiffViewerDestination>(DiffViewerDestination.Repository)
    }
    var diffFontSizeSp by rememberSaveable { mutableIntStateOf(DEFAULT_DIFF_FONT_SIZE_SP) }
    val diffColorPalette by diffDisplaySettingsStore.colorPalette.collectAsState()
    val isLineWrappingEnabled by diffDisplaySettingsStore.lineWrappingEnabled.collectAsState()
    val repositoryViewModelAdapter = remember(appStore, coroutineScope) {
        RepositoryViewModelAdapter(
            appStore = appStore,
            coroutineScope = coroutineScope,
            hasLocalStorageAccess = hasLocalStorageAccess,
            requestLocalStorageAccess = requestLocalStorageAccess,
            chooseLocalRepository = chooseLocalRepository,
            openFile = { fileDiffSelectionTarget ->
                diffViewerDestination = DiffViewerDestination.File(fileDiffSelectionTarget)
            },
            openAllDiffs = { allDiffsSelectionTarget ->
                diffViewerDestination = DiffViewerDestination.All(allDiffsSelectionTarget)
            },
        )
    }
    LaunchedEffect(hasLocalStorageAccess) {
        repositoryViewModelAdapter.updateLocalStorageAccess(hasLocalStorageAccess)
    }
    val decreaseFontSize = {
        diffFontSizeSp = (diffFontSizeSp - 1).coerceAtLeast(MINIMUM_DIFF_FONT_SIZE_SP)
    }
    val increaseFontSize = {
        diffFontSizeSp = (diffFontSizeSp + 1).coerceAtMost(MAXIMUM_DIFF_FONT_SIZE_SP)
    }

    Surface {
        when (val currentDestination = diffViewerDestination) {
            DiffViewerDestination.Repository -> RepositoryScreen(viewModel = repositoryViewModelAdapter)
            is DiffViewerDestination.File -> {
                val fileDiffViewModelAdapter = remember(
                    currentDestination,
                    diffFontSizeSp,
                    diffColorPalette,
                    isLineWrappingEnabled,
                ) {
                    FileDiffViewModelAdapter(
                        fileDiffSelectionTarget = currentDestination.fileDiffSelectionTarget,
                        fontSizeSp = diffFontSizeSp,
                        diffColorPalette = diffColorPalette,
                        isLineWrappingEnabled = isLineWrappingEnabled,
                        navigateBack = { diffViewerDestination = DiffViewerDestination.Repository },
                        decreaseFontSize = decreaseFontSize,
                        increaseFontSize = increaseFontSize,
                        toggleLineWrapping = diffDisplaySettingsStore::toggleLineWrapping,
                        updateColorPalette = diffDisplaySettingsStore::updateColorPalette,
                    )
                }
                FileDiffScreen(viewModel = fileDiffViewModelAdapter)
            }
            is DiffViewerDestination.All -> {
                val allDiffsViewModelAdapter = remember(
                    currentDestination,
                    diffFontSizeSp,
                    diffColorPalette,
                    isLineWrappingEnabled,
                ) {
                    AllDiffsViewModelAdapter(
                        allDiffsSelectionTarget = currentDestination.allDiffsSelectionTarget,
                        fontSizeSp = diffFontSizeSp,
                        diffColorPalette = diffColorPalette,
                        isLineWrappingEnabled = isLineWrappingEnabled,
                        navigateBack = { diffViewerDestination = DiffViewerDestination.Repository },
                        decreaseFontSize = decreaseFontSize,
                        increaseFontSize = increaseFontSize,
                        toggleLineWrapping = diffDisplaySettingsStore::toggleLineWrapping,
                        updateColorPalette = diffDisplaySettingsStore::updateColorPalette,
                    )
                }
                AllDiffsScreen(viewModel = allDiffsViewModelAdapter)
            }
        }
    }
}

private sealed interface DiffViewerDestination {
    data object Repository : DiffViewerDestination
    data class File(val fileDiffSelectionTarget: FileDiffSelectionTarget) : DiffViewerDestination
    data class All(val allDiffsSelectionTarget: AllDiffsSelectionTarget) : DiffViewerDestination
}

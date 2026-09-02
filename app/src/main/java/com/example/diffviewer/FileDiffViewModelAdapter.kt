package com.example.diffviewer

import com.example.diffviewer.core.diffui.DiffDisplayConfiguration
import com.example.diffviewer.core.diffui.DiffColorPalette
import com.example.diffviewer.feature.filediff.FileDiffEvent
import com.example.diffviewer.feature.filediff.FileDiffUiState
import com.example.diffviewer.feature.filediff.FileDiffViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class FileDiffViewModelAdapter(
    fileDiffSelectionTarget: FileDiffSelectionTarget,
    fontSizeSp: Int,
    diffColorPalette: DiffColorPalette,
    isLineWrappingEnabled: Boolean,
    private val navigateBack: () -> Unit,
    private val decreaseFontSize: () -> Unit,
    private val increaseFontSize: () -> Unit,
    private val toggleLineWrapping: () -> Unit,
    private val updateColorPalette: (DiffColorPalette) -> Unit,
) : FileDiffViewModel {
    private val mutableState = MutableStateFlow(
        FileDiffUiState(
            sourceLabel = fileDiffSelectionTarget.sourceLabel,
            diffFileDisplay = fileDiffSelectionTarget.fileDiff.toDiffFileDisplay("selected-file"),
            diffDisplayConfiguration = createDiffDisplayConfiguration(
                fontSizeSp = fontSizeSp,
                isLineWrappingEnabled = isLineWrappingEnabled,
                diffColorPalette = diffColorPalette,
            ),
        )
    )

    override val state: StateFlow<FileDiffUiState> = mutableState.asStateFlow()

    override fun send(event: FileDiffEvent) {
        when (event) {
            FileDiffEvent.NavigateBack -> navigateBack()
            FileDiffEvent.DecreaseFontSize -> decreaseFontSize()
            FileDiffEvent.IncreaseFontSize -> increaseFontSize()
            FileDiffEvent.ToggleLineWrapping -> toggleLineWrapping()
            is FileDiffEvent.UpdateColorPalette -> updateColorPalette(event.diffColorPalette)
        }
    }
}

fun createDiffDisplayConfiguration(
    fontSizeSp: Int,
    isLineWrappingEnabled: Boolean,
    diffColorPalette: DiffColorPalette,
): DiffDisplayConfiguration {
    return DiffDisplayConfiguration(
        fontSizeSp = fontSizeSp,
        canDecreaseFontSize = fontSizeSp > MINIMUM_DIFF_FONT_SIZE_SP,
        canIncreaseFontSize = fontSizeSp < MAXIMUM_DIFF_FONT_SIZE_SP,
        isLineWrappingEnabled = isLineWrappingEnabled,
        colorPalette = diffColorPalette,
    )
}

const val DEFAULT_DIFF_FONT_SIZE_SP = 12
const val MINIMUM_DIFF_FONT_SIZE_SP = 8
const val MAXIMUM_DIFF_FONT_SIZE_SP = 24

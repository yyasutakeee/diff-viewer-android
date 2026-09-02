package com.example.diffviewer.feature.filediff

import com.example.diffviewer.core.diffui.DiffDisplayConfiguration
import com.example.diffviewer.core.diffui.DiffColorPalette
import com.example.diffviewer.core.diffui.DiffFileDisplay
import kotlinx.coroutines.flow.StateFlow

interface FileDiffViewModel {
    val state: StateFlow<FileDiffUiState>
    fun send(event: FileDiffEvent)
}

sealed interface FileDiffEvent {
    data object NavigateBack : FileDiffEvent
    data object DecreaseFontSize : FileDiffEvent
    data object IncreaseFontSize : FileDiffEvent
    data object ToggleLineWrapping : FileDiffEvent
    data class UpdateColorPalette(val diffColorPalette: DiffColorPalette) : FileDiffEvent
}

data class FileDiffUiState(
    val sourceLabel: String,
    val diffFileDisplay: DiffFileDisplay,
    val diffDisplayConfiguration: DiffDisplayConfiguration,
)

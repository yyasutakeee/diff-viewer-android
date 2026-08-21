package com.example.diffviewer.feature.alldiffs

import com.example.diffviewer.core.diffui.DiffDisplayConfiguration
import com.example.diffviewer.core.diffui.DiffFileDisplay
import kotlinx.coroutines.flow.StateFlow

interface AllDiffsViewModel {
    val state: StateFlow<AllDiffsUiState>
    fun send(event: AllDiffsEvent)
}

sealed interface AllDiffsEvent {
    data object NavigateBack : AllDiffsEvent
    data object DecreaseFontSize : AllDiffsEvent
    data object IncreaseFontSize : AllDiffsEvent
}

data class AllDiffsUiState(
    val title: String,
    val groupDisplays: List<DiffFileGroupDisplay>,
    val diffDisplayConfiguration: DiffDisplayConfiguration,
)

data class DiffFileGroupDisplay(
    val id: String,
    val title: String,
    val fileDisplays: List<DiffFileDisplay>,
)

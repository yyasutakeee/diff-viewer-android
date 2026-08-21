package com.example.diffviewer

import com.example.diffviewer.feature.alldiffs.AllDiffsEvent
import com.example.diffviewer.feature.alldiffs.AllDiffsUiState
import com.example.diffviewer.feature.alldiffs.AllDiffsViewModel
import com.example.diffviewer.feature.alldiffs.DiffFileGroupDisplay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class AllDiffsViewModelAdapter(
    allDiffsSelectionTarget: AllDiffsSelectionTarget,
    fontSizeSp: Int,
    private val navigateBack: () -> Unit,
    private val decreaseFontSize: () -> Unit,
    private val increaseFontSize: () -> Unit,
) : AllDiffsViewModel {
    private val mutableState = MutableStateFlow(
        AllDiffsUiState(
            title = allDiffsSelectionTarget.title,
            groupDisplays = allDiffsSelectionTarget.groupSelectionTargets.map { groupSelectionTarget ->
                DiffFileGroupDisplay(
                    id = groupSelectionTarget.id,
                    title = groupSelectionTarget.title,
                    fileDisplays = groupSelectionTarget.fileDiffItems.mapIndexed { index, fileDiff ->
                        fileDiff.toDiffFileDisplay("${groupSelectionTarget.id}:file:$index")
                    },
                )
            },
            diffDisplayConfiguration = createDiffDisplayConfiguration(fontSizeSp),
        )
    )

    override val state: StateFlow<AllDiffsUiState> = mutableState.asStateFlow()

    override fun send(event: AllDiffsEvent) {
        when (event) {
            AllDiffsEvent.NavigateBack -> navigateBack()
            AllDiffsEvent.DecreaseFontSize -> decreaseFontSize()
            AllDiffsEvent.IncreaseFontSize -> increaseFontSize()
        }
    }
}

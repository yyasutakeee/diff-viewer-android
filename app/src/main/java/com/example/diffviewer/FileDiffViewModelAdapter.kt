package com.example.diffviewer

import com.example.diffviewer.core.domain.DiffLineKind
import com.example.diffviewer.feature.filediff.DiffHunkUiItem
import com.example.diffviewer.feature.filediff.DiffLineUiItem
import com.example.diffviewer.feature.filediff.DiffLineUiKind
import com.example.diffviewer.feature.filediff.FileDiffEvent
import com.example.diffviewer.feature.filediff.FileDiffUiState
import com.example.diffviewer.feature.filediff.FileDiffViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class FileDiffViewModelAdapter(
    fileDiffSelectionTarget: FileDiffSelectionTarget,
    private val navigateBack: () -> Unit,
) : FileDiffViewModel {
    private val mutableState = MutableStateFlow(fileDiffSelectionTarget.toUiState())

    override val state: StateFlow<FileDiffUiState> = mutableState.asStateFlow()

    override fun send(event: FileDiffEvent) {
        when (event) {
            FileDiffEvent.NavigateBack -> navigateBack()
        }
    }
}

private fun FileDiffSelectionTarget.toUiState(): FileDiffUiState = FileDiffUiState(
    path = fileDiff.path ?: "不明なファイル",
    sourceLabel = sourceLabel,
    isBinary = fileDiff.isBinary,
    hunkItems = fileDiff.hunkItems.mapIndexed { hunkIndex, diffHunk ->
        DiffHunkUiItem(
            id = "hunk:$hunkIndex",
            header = diffHunk.header,
            lineItems = diffHunk.lineItems.mapIndexed { lineIndex, diffLine ->
                DiffLineUiItem(
                    id = "line:$hunkIndex:$lineIndex",
                    kind = diffLine.kind.toUiKind(),
                    content = diffLine.content,
                    oldLine = diffLine.oldLine,
                    newLine = diffLine.newLine,
                )
            },
        )
    },
)

private fun DiffLineKind.toUiKind(): DiffLineUiKind = when (this) {
    DiffLineKind.CONTEXT -> DiffLineUiKind.CONTEXT
    DiffLineKind.ADDITION -> DiffLineUiKind.ADDITION
    DiffLineKind.DELETION -> DiffLineUiKind.DELETION
    DiffLineKind.META -> DiffLineUiKind.META
}

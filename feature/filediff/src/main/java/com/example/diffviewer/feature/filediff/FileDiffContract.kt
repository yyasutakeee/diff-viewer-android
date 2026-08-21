package com.example.diffviewer.feature.filediff

import kotlinx.coroutines.flow.StateFlow

interface FileDiffViewModel {
    val state: StateFlow<FileDiffUiState>
    fun send(event: FileDiffEvent)
}

sealed interface FileDiffEvent {
    data object NavigateBack : FileDiffEvent
}

data class FileDiffUiState(
    val path: String,
    val sourceLabel: String,
    val isBinary: Boolean,
    val hunkItems: List<DiffHunkUiItem>,
)

data class DiffHunkUiItem(
    val id: String,
    val header: String,
    val lineItems: List<DiffLineUiItem>,
)

data class DiffLineUiItem(
    val id: String,
    val kind: DiffLineUiKind,
    val content: String,
    val oldLine: Int?,
    val newLine: Int?,
)

enum class DiffLineUiKind {
    CONTEXT,
    ADDITION,
    DELETION,
    META,
}

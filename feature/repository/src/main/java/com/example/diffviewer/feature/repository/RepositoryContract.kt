package com.example.diffviewer.feature.repository

import kotlinx.coroutines.flow.StateFlow

interface RepositoryViewModel {
    val state: StateFlow<RepositoryUiState>
    fun send(event: RepositoryEvent)
}

sealed interface RepositoryEvent {
    data class Refresh(val endpoint: String, val token: String) : RepositoryEvent
    data class OpenFile(val fileId: String) : RepositoryEvent
}

data class RepositoryUiState(
    val endpoint: String = "",
    val token: String = "",
    val repositoryName: String? = null,
    val repositoryPath: String? = null,
    val branchSummary: String? = null,
    val latestCommit: LatestCommitUiItem? = null,
    val workingTreeSectionItems: List<DiffSectionUiItem> = emptyList(),
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
)

data class LatestCommitUiItem(
    val id: String,
    val subject: String,
    val fileItems: List<FileDiffUiItem>,
)

data class DiffSectionUiItem(
    val title: String,
    val fileItems: List<FileDiffUiItem>,
)

data class FileDiffUiItem(
    val id: String,
    val path: String,
    val status: String,
    val isBinary: Boolean,
    val additionCount: Int,
    val deletionCount: Int,
)

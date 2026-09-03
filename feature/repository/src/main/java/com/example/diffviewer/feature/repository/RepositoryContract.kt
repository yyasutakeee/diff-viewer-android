package com.example.diffviewer.feature.repository

import kotlinx.coroutines.flow.StateFlow

interface RepositoryViewModel {
    val state: StateFlow<RepositoryUiState>
    fun send(event: RepositoryEvent)
}

sealed interface RepositoryEvent {
    data class RefreshLocal(val repositoryPath: String) : RepositoryEvent
    data object RequestLocalStorageAccess : RepositoryEvent
    data object ChooseLocalRepository : RepositoryEvent
    data class Refresh(val endpoint: String, val token: String) : RepositoryEvent
    data class RefreshGitHub(val repositoryUrl: String, val token: String) : RepositoryEvent
    data class RefreshGitHubRepositories(val token: String) : RepositoryEvent
    data object LoadMoreGitHubRepositories : RepositoryEvent
    data class OpenFile(val fileId: String) : RepositoryEvent
    data class SelectCommit(val commitId: String) : RepositoryEvent
    data object LoadMoreCommits : RepositoryEvent
    data class OpenAllDiffs(val repositoryDiffSource: RepositoryDiffSource) : RepositoryEvent
}

enum class RepositoryConnectionSource {
    LOCAL,
    TERMUX,
    GITHUB,
}

sealed interface RepositoryDiffSource {
    data object WorkingTree : RepositoryDiffSource
    data object LatestCommit : RepositoryDiffSource
    data class Commit(val commitId: String) : RepositoryDiffSource
}

data class RepositoryUiState(
    val endpoint: String = "",
    val token: String = "",
    val githubRepositoryUrl: String = "",
    val githubToken: String = "",
    val repositoryConnectionSource: RepositoryConnectionSource = RepositoryConnectionSource.TERMUX,
    val repositoryName: String? = null,
    val localRepositoryPath: String = "",
    val hasLocalStorageAccess: Boolean = false,
    val repositoryPath: String? = null,
    val branchSummary: String? = null,
    val latestCommit: CommitDiffUiItem? = null,
    val commitHistoryItems: List<CommitHistoryUiItem> = emptyList(),
    val selectedCommit: CommitDiffUiItem? = null,
    val workingTreeSectionItems: List<DiffSectionUiItem> = emptyList(),
    val githubRepositoryItems: List<GitHubRepositoryUiItem> = emptyList(),
    val isLoading: Boolean = false,
    val isLoadingCommitHistory: Boolean = false,
    val isLoadingSelectedCommit: Boolean = false,
    val isLoadingGitHubRepositories: Boolean = false,
    val hasMoreCommits: Boolean = false,
    val errorMessage: String? = null,
    val commitHistoryErrorMessage: String? = null,
    val githubRepositoryErrorMessage: String? = null,
    val hasMoreGitHubRepositories: Boolean = false,
)

data class GitHubRepositoryUiItem(
    val nameWithOwner: String,
    val url: String,
    val visibilityLabel: String,
    val updatedAt: String,
)

data class CommitDiffUiItem(
    val id: String,
    val subject: String,
    val authorName: String,
    val authoredAt: String,
    val fileItems: List<FileDiffUiItem>,
)

data class CommitHistoryUiItem(
    val id: String,
    val subject: String,
    val authorName: String,
    val authoredAt: String,
    val isSelected: Boolean,
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
    val contentUnavailableMessage: String?,
    val additionCount: Int,
    val deletionCount: Int,
)

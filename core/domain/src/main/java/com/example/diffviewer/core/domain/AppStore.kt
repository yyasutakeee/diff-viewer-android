package com.example.diffviewer.core.domain

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class AppState(
    val connectionSettings: ConnectionSettings = ConnectionSettings(),
    val repositoryDiff: RepositoryDiff? = null,
    val commitSummaryItems: List<CommitSummary> = emptyList(),
    val nextCommitHistoryOffset: Int? = null,
    val selectedCommitId: String? = null,
    val selectedCommitDiff: CommitDiff? = null,
    val isLoadingRepositoryDiff: Boolean = false,
    val isLoadingCommitHistory: Boolean = false,
    val isLoadingSelectedCommit: Boolean = false,
    val repositoryDiffErrorMessage: String? = null,
    val commitHistoryErrorMessage: String? = null,
)

class AppStore(
    private val diffRepository: DiffRepository,
    private val githubDiffRepository: DiffRepository,
    private val connectionSettingsRepository: ConnectionSettingsRepository,
    private val coroutineScope: CoroutineScope,
) {
    private val mutableState = MutableStateFlow(
        AppState(connectionSettings = connectionSettingsRepository.loadConnectionSettings())
    )

    val state: StateFlow<AppState> = mutableState.asStateFlow()

    fun refreshRepositoryDiff(connectionSettings: ConnectionSettings) {
        if (mutableState.value.isLoadingRepositoryDiff) return
        val termuxConnectionSettings = mutableState.value.connectionSettings.copy(
            endpoint = connectionSettings.endpoint,
            token = connectionSettings.token,
            repositorySource = RepositorySource.TERMUX,
        )
        if (termuxConnectionSettings.token.isBlank()) {
            mutableState.value = mutableState.value.copy(
                connectionSettings = termuxConnectionSettings,
                isLoadingCommitHistory = false,
                isLoadingSelectedCommit = false,
                repositoryDiffErrorMessage = "アクセストークンを入力してください",
            )
            return
        }

        connectionSettingsRepository.saveConnectionSettings(termuxConnectionSettings)
        mutableState.value = mutableState.value.copy(
            connectionSettings = termuxConnectionSettings,
            repositoryDiff = null,
            commitSummaryItems = emptyList(),
            nextCommitHistoryOffset = null,
            selectedCommitId = null,
            selectedCommitDiff = null,
            isLoadingRepositoryDiff = true,
            isLoadingCommitHistory = false,
            isLoadingSelectedCommit = false,
            repositoryDiffErrorMessage = null,
        )
        coroutineScope.launch {
            runCatching {
                diffRepository.fetchRepositoryDiff(
                    endpoint = termuxConnectionSettings.endpoint,
                    token = termuxConnectionSettings.token,
                )
            }.onSuccess { repositoryDiff ->
                mutableState.value = mutableState.value.copy(
                    repositoryDiff = repositoryDiff,
                    commitSummaryItems = repositoryDiff.commitHistoryPage.commitSummaryItems,
                    nextCommitHistoryOffset = repositoryDiff.commitHistoryPage.nextOffset,
                    selectedCommitId = null,
                    selectedCommitDiff = null,
                    isLoadingRepositoryDiff = false,
                    isLoadingCommitHistory = false,
                    isLoadingSelectedCommit = false,
                    commitHistoryErrorMessage = null,
                )
            }.onFailure { error ->
                mutableState.value = mutableState.value.copy(
                    isLoadingRepositoryDiff = false,
                    repositoryDiffErrorMessage = error.message ?: "差分を取得できませんでした",
                )
            }
        }
    }

    fun refreshGitHubRepositoryDiff(githubRepositoryUrl: String) {
        if (mutableState.value.isLoadingRepositoryDiff) return
        if (githubRepositoryUrl.isBlank()) {
            mutableState.value = mutableState.value.copy(
                repositoryDiffErrorMessage = "GitHubリポジトリURLを入力してください",
            )
            return
        }
        val githubConnectionSettings = mutableState.value.connectionSettings.copy(
            githubRepositoryUrl = githubRepositoryUrl,
            repositorySource = RepositorySource.GITHUB,
        )
        connectionSettingsRepository.saveConnectionSettings(githubConnectionSettings)
        mutableState.value = mutableState.value.copy(
            connectionSettings = githubConnectionSettings,
            repositoryDiff = null,
            commitSummaryItems = emptyList(),
            nextCommitHistoryOffset = null,
            selectedCommitId = null,
            selectedCommitDiff = null,
            isLoadingRepositoryDiff = true,
            isLoadingCommitHistory = false,
            isLoadingSelectedCommit = false,
            repositoryDiffErrorMessage = null,
        )
        coroutineScope.launch {
            runCatching {
                githubDiffRepository.fetchRepositoryDiff(githubRepositoryUrl, "")
            }.onSuccess(::applyRepositoryDiff)
                .onFailure(::applyRepositoryDiffFailure)
        }
    }

    fun loadMoreCommitHistory() {
        val currentState = mutableState.value
        val nextOffset = currentState.nextCommitHistoryOffset ?: return
        if (currentState.isLoadingCommitHistory) return
        mutableState.value = currentState.copy(
            isLoadingCommitHistory = true,
            commitHistoryErrorMessage = null,
        )
        coroutineScope.launch {
            runCatching {
                when (currentState.connectionSettings.repositorySource) {
                    RepositorySource.TERMUX -> diffRepository.fetchCommitHistoryPage(
                        endpoint = currentState.connectionSettings.endpoint,
                        token = currentState.connectionSettings.token,
                        offset = nextOffset,
                    )
                    RepositorySource.GITHUB -> githubDiffRepository.fetchCommitHistoryPage(
                        endpoint = currentState.connectionSettings.githubRepositoryUrl,
                        token = "",
                        offset = nextOffset,
                    )
                }
            }.onSuccess { commitHistoryPage ->
                if (mutableState.value.connectionSettings == currentState.connectionSettings) {
                    mutableState.value = mutableState.value.copy(
                        commitSummaryItems = mutableState.value.commitSummaryItems +
                            commitHistoryPage.commitSummaryItems,
                        nextCommitHistoryOffset = commitHistoryPage.nextOffset,
                        isLoadingCommitHistory = false,
                    )
                }
            }.onFailure { error ->
                if (mutableState.value.connectionSettings == currentState.connectionSettings) {
                    mutableState.value = mutableState.value.copy(
                        isLoadingCommitHistory = false,
                        commitHistoryErrorMessage = error.message ?: "コミット履歴を取得できませんでした",
                    )
                }
            }
        }
    }

    fun selectCommit(commitId: String) {
        val currentState = mutableState.value
        if (currentState.isLoadingSelectedCommit && currentState.selectedCommitId == commitId) return
        mutableState.value = currentState.copy(
            selectedCommitId = commitId,
            selectedCommitDiff = null,
            isLoadingSelectedCommit = true,
            commitHistoryErrorMessage = null,
        )
        coroutineScope.launch {
            runCatching {
                when (currentState.connectionSettings.repositorySource) {
                    RepositorySource.TERMUX -> diffRepository.fetchCommitDiff(
                        endpoint = currentState.connectionSettings.endpoint,
                        token = currentState.connectionSettings.token,
                        commitId = commitId,
                    )
                    RepositorySource.GITHUB -> githubDiffRepository.fetchCommitDiff(
                        endpoint = currentState.connectionSettings.githubRepositoryUrl,
                        token = "",
                        commitId = commitId,
                    )
                }
            }.onSuccess { commitDiff ->
                if (
                    mutableState.value.connectionSettings == currentState.connectionSettings &&
                    mutableState.value.selectedCommitId == commitId
                ) {
                    mutableState.value = mutableState.value.copy(
                        selectedCommitDiff = commitDiff,
                        isLoadingSelectedCommit = false,
                    )
                }
            }.onFailure { error ->
                if (
                    mutableState.value.connectionSettings == currentState.connectionSettings &&
                    mutableState.value.selectedCommitId == commitId
                ) {
                    mutableState.value = mutableState.value.copy(
                        isLoadingSelectedCommit = false,
                        commitHistoryErrorMessage = error.message ?: "コミット差分を取得できませんでした",
                    )
                }
            }
        }
    }

    private fun applyRepositoryDiff(repositoryDiff: RepositoryDiff) {
        mutableState.value = mutableState.value.copy(
            repositoryDiff = repositoryDiff,
            commitSummaryItems = repositoryDiff.commitHistoryPage.commitSummaryItems,
            nextCommitHistoryOffset = repositoryDiff.commitHistoryPage.nextOffset,
            selectedCommitId = null,
            selectedCommitDiff = null,
            isLoadingRepositoryDiff = false,
            isLoadingCommitHistory = false,
            isLoadingSelectedCommit = false,
            commitHistoryErrorMessage = null,
        )
    }

    private fun applyRepositoryDiffFailure(error: Throwable) {
        mutableState.value = mutableState.value.copy(
            isLoadingRepositoryDiff = false,
            repositoryDiffErrorMessage = error.message ?: "差分を取得できませんでした",
        )
    }
}

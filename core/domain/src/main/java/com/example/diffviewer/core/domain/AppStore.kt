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
    private val connectionSettingsRepository: ConnectionSettingsRepository,
    private val coroutineScope: CoroutineScope,
) {
    private val mutableState = MutableStateFlow(
        AppState(connectionSettings = connectionSettingsRepository.loadConnectionSettings())
    )

    val state: StateFlow<AppState> = mutableState.asStateFlow()

    fun refreshRepositoryDiff(connectionSettings: ConnectionSettings) {
        if (mutableState.value.isLoadingRepositoryDiff) return
        if (connectionSettings.token.isBlank()) {
            mutableState.value = mutableState.value.copy(
                connectionSettings = connectionSettings,
                repositoryDiffErrorMessage = "アクセストークンを入力してください",
            )
            return
        }

        connectionSettingsRepository.saveConnectionSettings(connectionSettings)
        mutableState.value = mutableState.value.copy(
            connectionSettings = connectionSettings,
            isLoadingRepositoryDiff = true,
            repositoryDiffErrorMessage = null,
        )
        coroutineScope.launch {
            runCatching {
                diffRepository.fetchRepositoryDiff(
                    endpoint = connectionSettings.endpoint,
                    token = connectionSettings.token,
                )
            }.onSuccess { repositoryDiff ->
                mutableState.value = mutableState.value.copy(
                    repositoryDiff = repositoryDiff,
                    commitSummaryItems = repositoryDiff.commitHistoryPage.commitSummaryItems,
                    nextCommitHistoryOffset = repositoryDiff.commitHistoryPage.nextOffset,
                    selectedCommitId = null,
                    selectedCommitDiff = null,
                    isLoadingRepositoryDiff = false,
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
                diffRepository.fetchCommitHistoryPage(
                    endpoint = currentState.connectionSettings.endpoint,
                    token = currentState.connectionSettings.token,
                    offset = nextOffset,
                )
            }.onSuccess { commitHistoryPage ->
                mutableState.value = mutableState.value.copy(
                    commitSummaryItems = mutableState.value.commitSummaryItems +
                        commitHistoryPage.commitSummaryItems,
                    nextCommitHistoryOffset = commitHistoryPage.nextOffset,
                    isLoadingCommitHistory = false,
                )
            }.onFailure { error ->
                mutableState.value = mutableState.value.copy(
                    isLoadingCommitHistory = false,
                    commitHistoryErrorMessage = error.message ?: "コミット履歴を取得できませんでした",
                )
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
                diffRepository.fetchCommitDiff(
                    endpoint = currentState.connectionSettings.endpoint,
                    token = currentState.connectionSettings.token,
                    commitId = commitId,
                )
            }.onSuccess { commitDiff ->
                if (mutableState.value.selectedCommitId == commitId) {
                    mutableState.value = mutableState.value.copy(
                        selectedCommitDiff = commitDiff,
                        isLoadingSelectedCommit = false,
                    )
                }
            }.onFailure { error ->
                if (mutableState.value.selectedCommitId == commitId) {
                    mutableState.value = mutableState.value.copy(
                        isLoadingSelectedCommit = false,
                        commitHistoryErrorMessage = error.message ?: "コミット差分を取得できませんでした",
                    )
                }
            }
        }
    }
}

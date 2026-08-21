package com.example.diffviewer.core.domain

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class AppState(
    val connectionSettings: ConnectionSettings = ConnectionSettings(),
    val repositoryDiff: RepositoryDiff? = null,
    val isLoadingRepositoryDiff: Boolean = false,
    val repositoryDiffErrorMessage: String? = null,
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
                    isLoadingRepositoryDiff = false,
                )
            }.onFailure { error ->
                mutableState.value = mutableState.value.copy(
                    isLoadingRepositoryDiff = false,
                    repositoryDiffErrorMessage = error.message ?: "差分を取得できませんでした",
                )
            }
        }
    }
}

package com.example.diffviewer.core.domain

interface DiffRepository {
    suspend fun fetchRepositoryDiff(endpoint: String, token: String): RepositoryDiff
    suspend fun fetchCommitHistoryPage(
        endpoint: String,
        token: String,
        offset: Int,
    ): CommitHistoryPage
    suspend fun fetchCommitDiff(
        endpoint: String,
        token: String,
        commitId: String,
    ): CommitDiff
}

interface ConnectionSettingsRepository {
    fun loadConnectionSettings(): ConnectionSettings
    fun saveConnectionSettings(connectionSettings: ConnectionSettings)
}

interface GitHubRepositoryCatalog {
    suspend fun fetchRepositoryCatalogPage(token: String, page: Int): GitHubRepositoryCatalogPage
}

data class ConnectionSettings(
    val endpoint: String = "http://127.0.0.1:8765",
    val token: String = "",
    val githubRepositoryUrl: String = "https://github.com/yyasutakeee/diff-viewer-android",
    val githubToken: String = "",
    val repositorySource: RepositorySource = RepositorySource.TERMUX,
)

enum class RepositorySource {
    TERMUX,
    GITHUB,
}

package com.example.diffviewer.core.data

import com.example.diffviewer.core.domain.CommitDiff
import com.example.diffviewer.core.domain.CommitHistoryPage
import com.example.diffviewer.core.domain.DiffRepository
import com.example.diffviewer.core.domain.GitHubRepositoryCatalog
import com.example.diffviewer.core.domain.GitHubRepositoryCatalogPage
import com.example.diffviewer.core.domain.RepositoryDiff
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

class GitHubDiffRepository : DiffRepository, GitHubRepositoryCatalog {
    override suspend fun fetchRepositoryCatalogPage(
        token: String,
        page: Int,
    ): GitHubRepositoryCatalogPage = withContext(Dispatchers.IO) {
        require(page >= 1)
        val githubResponse = fetchGitHubResponse(
            "/user/repos?affiliation=owner,collaborator,organization_member" +
                "&visibility=all&sort=pushed&direction=desc" +
                "&per_page=$GITHUB_REPOSITORY_CATALOG_PAGE_SIZE&page=$page",
            token,
        )
        parseGitHubRepositoryCatalogPage(githubResponse.body, page, githubResponse.hasNextPage)
    }

    override suspend fun fetchRepositoryDiff(endpoint: String, token: String): RepositoryDiff {
        return withContext(Dispatchers.IO) {
            val githubRepositoryIdentifier = parseGitHubRepositoryIdentifier(endpoint)
            val repositoryObject = JSONObject(
                fetchGitHubResponse(githubRepositoryIdentifier.apiPath, token).body
            )
            val defaultBranch = repositoryObject.getString("default_branch")
            val commitHistoryPage = fetchCommitHistoryPageOnIoDispatcher(
                githubRepositoryIdentifier,
                token,
                1,
            )
            val latestCommitSummary = findLatestCommitSummary(commitHistoryPage)
            RepositoryDiff(
                repository = repositoryObject.getString("full_name"),
                branch = defaultBranch,
                latestCommit = fetchCommitDiffOnIoDispatcher(
                    githubRepositoryIdentifier,
                    token,
                    latestCommitSummary.id,
                ),
                commitHistoryPage = commitHistoryPage,
                sections = emptyList(),
            )
        }
    }

    override suspend fun fetchCommitHistoryPage(
        endpoint: String,
        token: String,
        offset: Int,
    ): CommitHistoryPage = withContext(Dispatchers.IO) {
        fetchCommitHistoryPageOnIoDispatcher(parseGitHubRepositoryIdentifier(endpoint), token, offset)
    }

    override suspend fun fetchCommitDiff(
        endpoint: String,
        token: String,
        commitId: String,
    ): CommitDiff = withContext(Dispatchers.IO) {
        require(COMMIT_ID_PATTERN.matches(commitId))
        fetchCommitDiffOnIoDispatcher(parseGitHubRepositoryIdentifier(endpoint), token, commitId)
    }

    private fun fetchCommitHistoryPageOnIoDispatcher(
        githubRepositoryIdentifier: GitHubRepositoryIdentifier,
        token: String,
        page: Int,
    ): CommitHistoryPage {
        require(page >= 1)
        val githubResponse = fetchGitHubResponse(
            "${githubRepositoryIdentifier.apiPath}/commits?per_page=$GITHUB_COMMIT_PAGE_SIZE&page=$page",
            token,
        )
        return parseGitHubCommitHistoryPage(githubResponse.body, page, githubResponse.hasNextPage)
    }

    private fun fetchCommitDiffOnIoDispatcher(
        githubRepositoryIdentifier: GitHubRepositoryIdentifier,
        token: String,
        commitId: String,
    ): CommitDiff {
        var page = 1
        var commitDiff: CommitDiff? = null
        var hasNextPage: Boolean
        do {
            val githubResponse = fetchGitHubResponse(
                "${githubRepositoryIdentifier.apiPath}/commits/$commitId" +
                    "?per_page=$GITHUB_COMMIT_FILE_PAGE_SIZE&page=$page",
                token,
            )
            val commitDiffPage = parseGitHubCommitDiff(
                githubResponse.body
            )
            val existingCommitDiff = commitDiff
            commitDiff = if (existingCommitDiff == null) {
                commitDiffPage
            } else {
                existingCommitDiff.copy(
                    fileDiffItems = existingCommitDiff.fileDiffItems + commitDiffPage.fileDiffItems
                )
            }
            page += 1
            hasNextPage = githubResponse.hasNextPage
        } while (hasNextPage)
        val completeCommitDiff = requireNotNull(commitDiff)
        if (completeCommitDiff.fileDiffItems.size >= GITHUB_COMMIT_FILE_LIMIT) {
            throw IOException("GitHub APIの上限3,000ファイルに達したため、完全な差分を表示できません")
        }
        return completeCommitDiff
    }

    private fun findLatestCommitSummary(commitHistoryPage: CommitHistoryPage) =
        commitHistoryPage.commitSummaryItems.firstOrNull()
            ?: throw IOException("GitHubリポジトリにコミットがありません")

    private fun fetchGitHubResponse(apiPath: String, token: String): GitHubResponse {
        val connection = URL("https://api.github.com$apiPath").openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "GET"
            connection.connectTimeout = 5_000
            connection.readTimeout = 20_000
            connection.setRequestProperty("Accept", "application/vnd.github+json")
            connection.setRequestProperty("X-GitHub-Api-Version", "2026-03-10")
            connection.setRequestProperty("User-Agent", "Diff-Viewer-Android")
            githubAuthorizationHeaderValue(token)?.let { authorizationHeaderValue ->
                connection.setRequestProperty("Authorization", authorizationHeaderValue)
            }
            val responseCode = connection.responseCode
            val responseText = (if (responseCode in 200..299) connection.inputStream else connection.errorStream)
                ?.bufferedReader()?.use { reader -> reader.readText() }.orEmpty()
            if (responseCode !in 200..299) {
                val message = runCatching { JSONObject(responseText).optString("message") }.getOrDefault("")
                throw IOException(message.ifBlank { "GitHub APIがHTTP $responseCode を返しました" })
            }
            return GitHubResponse(
                body = responseText,
                hasNextPage = connection.getHeaderField("Link")?.contains("rel=\"next\"") == true,
            )
        } finally {
            connection.disconnect()
        }
    }

    internal data class GitHubRepositoryIdentifier(val owner: String, val repository: String) {
        val apiPath: String get() = "/repos/$owner/$repository"
    }

    private data class GitHubResponse(val body: String, val hasNextPage: Boolean)

    internal companion object {
        private val COMMIT_ID_PATTERN = Regex("^[0-9a-fA-F]{40}$")
        private val REPOSITORY_PART_PATTERN = Regex("^[A-Za-z0-9_.-]+$")
        private const val GITHUB_COMMIT_FILE_PAGE_SIZE = 100
        private const val GITHUB_COMMIT_FILE_LIMIT = 3_000
        private const val GITHUB_REPOSITORY_CATALOG_PAGE_SIZE = 100

        fun parseGitHubRepositoryIdentifier(value: String): GitHubRepositoryIdentifier {
            val normalized = value.trim().removeSuffix("/").removeSuffix(".git")
                .removePrefix("https://github.com/")
            val parts = normalized.split("/")
            require(hasValidRepositoryParts(parts)) {
                "GitHub URLは https://github.com/owner/repository の形式で入力してください"
            }
            return GitHubRepositoryIdentifier(parts[0], parts[1])
        }

        private fun hasValidRepositoryParts(parts: List<String>): Boolean =
            parts.size == 2 && parts.all { part -> REPOSITORY_PART_PATTERN.matches(part) }
    }
}

internal fun githubAuthorizationHeaderValue(token: String): String? {
    val trimmedToken = token.trim()
    return if (trimmedToken.isEmpty()) null else "Bearer $trimmedToken"
}

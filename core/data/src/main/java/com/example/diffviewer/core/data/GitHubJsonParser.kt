package com.example.diffviewer.core.data

import com.example.diffviewer.core.domain.CommitDiff
import com.example.diffviewer.core.domain.CommitHistoryPage
import com.example.diffviewer.core.domain.CommitSummary
import com.example.diffviewer.core.domain.FileDiff
import com.example.diffviewer.core.domain.FileDiffStatus
import com.example.diffviewer.core.domain.GitHubRepositoryCatalogPage
import com.example.diffviewer.core.domain.GitHubRepositorySummary
import org.json.JSONArray
import org.json.JSONObject

internal fun parseGitHubRepositoryCatalogPage(
    jsonText: String,
    page: Int,
    hasNextPage: Boolean,
): GitHubRepositoryCatalogPage {
    val githubRepositorySummaryItems = JSONArray(jsonText).mapObjects { repositoryObject ->
        GitHubRepositorySummary(
            nameWithOwner = repositoryObject.getString("full_name"),
            url = repositoryObject.getString("html_url"),
            isPrivate = repositoryObject.getBoolean("private"),
            updatedAt = repositoryObject.getString("updated_at"),
        )
    }
    return GitHubRepositoryCatalogPage(
        githubRepositorySummaryItems = githubRepositorySummaryItems,
        nextPage = if (hasNextPage) page + 1 else null,
    )
}

internal fun parseGitHubCommitHistoryPage(
    jsonText: String,
    page: Int,
    hasNextPage: Boolean,
): CommitHistoryPage {
    val commitSummaryItems = JSONArray(jsonText).mapObjects { commitObject ->
        parseGitHubCommitSummary(commitObject)
    }
    return CommitHistoryPage(
        commitSummaryItems = commitSummaryItems,
        nextOffset = if (hasNextPage) page + 1 else null,
    )
}

internal fun parseGitHubCommitDiff(jsonText: String): CommitDiff {
    val commitObject = JSONObject(jsonText)
    val commitSummary = parseGitHubCommitSummary(commitObject)
    return CommitDiff(
        id = commitSummary.id,
        subject = commitSummary.subject,
        authorName = commitSummary.authorName,
        authoredAt = commitSummary.authoredAt,
        fileDiffItems = commitObject.getJSONArray("files").mapObjects(::parseGitHubFileDiff),
    )
}

private fun parseGitHubCommitSummary(commitObject: JSONObject): CommitSummary {
    val gitCommitObject = commitObject.getJSONObject("commit")
    val authorObject = gitCommitObject.getJSONObject("author")
    return CommitSummary(
        id = commitObject.getString("sha"),
        subject = gitCommitObject.getString("message").substringBefore('\n'),
        authorName = authorObject.getString("name"),
        authoredAt = authorObject.getString("date"),
    )
}

private fun parseGitHubFileDiff(fileObject: JSONObject): FileDiff {
    val status = parseGitHubFileStatus(fileObject.getString("status"))
    val fileName = fileObject.getString("filename")
    val patch = if (fileObject.has("patch")) fileObject.getString("patch") else null
    return FileDiff(
        oldPath = when (status) {
            FileDiffStatus.ADDED -> null
            FileDiffStatus.RENAMED -> fileObject.optString("previous_filename").ifBlank { fileName }
            else -> fileName
        },
        newPath = if (status == FileDiffStatus.DELETED) null else fileName,
        status = status,
        isBinary = false,
        contentUnavailableMessage = if (patch == null) {
            "GitHub APIからこのファイルの行差分が返されませんでした"
        } else null,
        hunkItems = patch?.let(::parseUnifiedPatch).orEmpty(),
    )
}

private fun parseGitHubFileStatus(status: String): FileDiffStatus = when (status) {
    "added" -> FileDiffStatus.ADDED
    "removed" -> FileDiffStatus.DELETED
    "renamed" -> FileDiffStatus.RENAMED
    else -> FileDiffStatus.MODIFIED
}

private fun <T> JSONArray.mapObjects(transform: (JSONObject) -> T): List<T> {
    return List(length()) { index -> transform(getJSONObject(index)) }
}

internal const val GITHUB_COMMIT_PAGE_SIZE = 20

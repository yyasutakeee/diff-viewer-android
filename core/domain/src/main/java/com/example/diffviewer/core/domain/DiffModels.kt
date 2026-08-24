package com.example.diffviewer.core.domain

data class RepositoryDiff(
    val repository: String,
    val branch: String,
    val latestCommit: CommitDiff,
    val commitHistoryPage: CommitHistoryPage,
    val sections: List<DiffSection>,
) {
    val changedFileCount: Int
        get() = sections.sumOf { diffSection -> diffSection.fileDiffItems.size }
}

data class CommitDiff(
    val id: String,
    val subject: String,
    val authorName: String,
    val authoredAt: String,
    val fileDiffItems: List<FileDiff>,
)

data class CommitHistoryPage(
    val commitSummaryItems: List<CommitSummary>,
    val nextOffset: Int?,
)

data class CommitSummary(
    val id: String,
    val subject: String,
    val authorName: String,
    val authoredAt: String,
)

data class GitHubRepositoryCatalogPage(
    val githubRepositorySummaryItems: List<GitHubRepositorySummary>,
    val nextPage: Int?,
)

data class GitHubRepositorySummary(
    val nameWithOwner: String,
    val url: String,
    val isPrivate: Boolean,
    val updatedAt: String,
)

data class DiffSection(
    val kind: DiffSectionKind,
    val fileDiffItems: List<FileDiff>,
)

enum class DiffSectionKind {
    UNSTAGED,
    STAGED,
    UNTRACKED,
}

data class FileDiff(
    val oldPath: String?,
    val newPath: String?,
    val status: FileDiffStatus,
    val isBinary: Boolean,
    val contentUnavailableMessage: String? = null,
    val hunkItems: List<DiffHunk>,
) {
    val path: String?
        get() = newPath ?: oldPath

    val additionCount: Int
        get() = hunkItems.sumOf { diffHunk ->
            diffHunk.lineItems.count { diffLine -> diffLine.kind == DiffLineKind.ADDITION }
        }

    val deletionCount: Int
        get() = hunkItems.sumOf { diffHunk ->
            diffHunk.lineItems.count { diffLine -> diffLine.kind == DiffLineKind.DELETION }
        }
}

enum class FileDiffStatus {
    MODIFIED,
    ADDED,
    DELETED,
    RENAMED,
    UNTRACKED,
}

data class DiffHunk(
    val header: String,
    val lineItems: List<DiffLine>,
)

data class DiffLine(
    val kind: DiffLineKind,
    val content: String,
    val oldLine: Int?,
    val newLine: Int?,
)

enum class DiffLineKind {
    CONTEXT,
    ADDITION,
    DELETION,
    META,
}

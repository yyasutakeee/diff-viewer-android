package com.example.diffviewer.core.domain

data class RepositoryDiff(
    val repository: String,
    val branch: String,
    val latestCommit: LatestCommit,
    val sections: List<DiffSection>,
) {
    val changedFileCount: Int
        get() = sections.sumOf { diffSection -> diffSection.fileDiffItems.size }
}

data class LatestCommit(
    val id: String,
    val subject: String,
    val fileDiffItems: List<FileDiff>,
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

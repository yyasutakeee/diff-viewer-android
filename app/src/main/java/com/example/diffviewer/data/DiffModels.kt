package com.example.diffviewer.data

import org.json.JSONArray
import org.json.JSONObject

data class RepositoryDiff(
    val repository: String,
    val branch: String,
    val latestCommit: LatestCommit,
    val sections: List<DiffSection>,
) {
    val changedFileCount: Int
        get() = sections.sumOf { it.fileDiffItems.size }
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
    val displayPath: String
        get() = newPath ?: oldPath ?: "不明なファイル"

    val additionCount: Int
        get() = hunkItems.sumOf { hunk -> hunk.lineItems.count { it.kind == DiffLineKind.ADDITION } }

    val deletionCount: Int
        get() = hunkItems.sumOf { hunk -> hunk.lineItems.count { it.kind == DiffLineKind.DELETION } }
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

fun parseRepositoryDiff(jsonText: String): RepositoryDiff {
    val rootObject = JSONObject(jsonText)
    return RepositoryDiff(
        repository = rootObject.getString("repository"),
        branch = rootObject.getString("branch"),
        latestCommit = parseLatestCommit(rootObject.getJSONObject("latestCommit")),
        sections = rootObject.getJSONArray("sections").mapObjects(::parseDiffSection),
    )
}

private fun parseLatestCommit(jsonObject: JSONObject): LatestCommit {
    return LatestCommit(
        id = jsonObject.getString("id"),
        subject = jsonObject.getString("subject"),
        fileDiffItems = jsonObject.getJSONArray("files").mapObjects(::parseFileDiff),
    )
}

private fun parseDiffSection(jsonObject: JSONObject): DiffSection {
    return DiffSection(
        kind = enumValueOf(jsonObject.getString("kind").uppercase()),
        fileDiffItems = jsonObject.getJSONArray("files").mapObjects(::parseFileDiff),
    )
}

private fun parseFileDiff(jsonObject: JSONObject): FileDiff {
    return FileDiff(
        oldPath = jsonObject.optionalString("oldPath"),
        newPath = jsonObject.optionalString("newPath"),
        status = enumValueOf(jsonObject.getString("status").uppercase()),
        isBinary = jsonObject.getBoolean("isBinary"),
        hunkItems = jsonObject.getJSONArray("hunks").mapObjects(::parseDiffHunk),
    )
}

private fun parseDiffHunk(jsonObject: JSONObject): DiffHunk {
    return DiffHunk(
        header = jsonObject.getString("header"),
        lineItems = jsonObject.getJSONArray("lines").mapObjects(::parseDiffLine),
    )
}

private fun parseDiffLine(jsonObject: JSONObject): DiffLine {
    return DiffLine(
        kind = enumValueOf(jsonObject.getString("kind").uppercase()),
        content = jsonObject.getString("content"),
        oldLine = jsonObject.optionalInt("oldLine"),
        newLine = jsonObject.optionalInt("newLine"),
    )
}

private fun JSONObject.optionalString(key: String): String? {
    return if (isNull(key)) null else getString(key)
}

private fun JSONObject.optionalInt(key: String): Int? {
    return if (isNull(key)) null else getInt(key)
}

private fun <T> JSONArray.mapObjects(transform: (JSONObject) -> T): List<T> {
    return List(length()) { index -> transform(getJSONObject(index)) }
}

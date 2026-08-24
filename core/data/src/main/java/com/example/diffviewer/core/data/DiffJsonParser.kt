package com.example.diffviewer.core.data

import com.example.diffviewer.core.domain.CommitDiff
import com.example.diffviewer.core.domain.CommitHistoryPage
import com.example.diffviewer.core.domain.CommitSummary
import com.example.diffviewer.core.domain.DiffHunk
import com.example.diffviewer.core.domain.DiffLine
import com.example.diffviewer.core.domain.DiffLineKind
import com.example.diffviewer.core.domain.DiffSection
import com.example.diffviewer.core.domain.DiffSectionKind
import com.example.diffviewer.core.domain.FileDiff
import com.example.diffviewer.core.domain.FileDiffStatus
import com.example.diffviewer.core.domain.RepositoryDiff
import org.json.JSONArray
import org.json.JSONObject

internal fun parseRepositoryDiff(jsonText: String): RepositoryDiff {
    val rootObject = JSONObject(jsonText)
    return RepositoryDiff(
        repository = rootObject.getString("repository"),
        branch = rootObject.getString("branch"),
        latestCommit = parseCommitDiff(rootObject.getJSONObject("latestCommit")),
        commitHistoryPage = parseCommitHistoryPage(rootObject.getJSONObject("commitHistory")),
        sections = rootObject.getJSONArray("sections").mapObjects(::parseDiffSection),
    )
}

internal fun parseCommitDiff(jsonText: String): CommitDiff = parseCommitDiff(JSONObject(jsonText))

private fun parseCommitDiff(jsonObject: JSONObject): CommitDiff = CommitDiff(
    id = jsonObject.getString("id"),
    subject = jsonObject.getString("subject"),
    authorName = jsonObject.getString("authorName"),
    authoredAt = jsonObject.getString("authoredAt"),
    fileDiffItems = jsonObject.getJSONArray("files").mapObjects(::parseFileDiff),
)

internal fun parseCommitHistoryPage(jsonText: String): CommitHistoryPage =
    parseCommitHistoryPage(JSONObject(jsonText))

private fun parseCommitHistoryPage(jsonObject: JSONObject): CommitHistoryPage = CommitHistoryPage(
    commitSummaryItems = jsonObject.getJSONArray("commits").mapObjects(::parseCommitSummary),
    nextOffset = jsonObject.optionalInt("nextOffset"),
)

private fun parseCommitSummary(jsonObject: JSONObject): CommitSummary = CommitSummary(
    id = jsonObject.getString("id"),
    subject = jsonObject.getString("subject"),
    authorName = jsonObject.getString("authorName"),
    authoredAt = jsonObject.getString("authoredAt"),
)

private fun parseDiffSection(jsonObject: JSONObject): DiffSection = DiffSection(
    kind = enumValueOf(jsonObject.getString("kind").uppercase()),
    fileDiffItems = jsonObject.getJSONArray("files").mapObjects(::parseFileDiff),
)

private fun parseFileDiff(jsonObject: JSONObject): FileDiff = FileDiff(
    oldPath = jsonObject.optionalString("oldPath"),
    newPath = jsonObject.optionalString("newPath"),
    status = enumValueOf(jsonObject.getString("status").uppercase()),
    isBinary = jsonObject.getBoolean("isBinary"),
    hunkItems = jsonObject.getJSONArray("hunks").mapObjects(::parseDiffHunk),
)

private fun parseDiffHunk(jsonObject: JSONObject): DiffHunk = DiffHunk(
    header = jsonObject.getString("header"),
    lineItems = jsonObject.getJSONArray("lines").mapObjects(::parseDiffLine),
)

private fun parseDiffLine(jsonObject: JSONObject): DiffLine = DiffLine(
    kind = enumValueOf(jsonObject.getString("kind").uppercase()),
    content = jsonObject.getString("content"),
    oldLine = jsonObject.optionalInt("oldLine"),
    newLine = jsonObject.optionalInt("newLine"),
)

private fun JSONObject.optionalString(key: String): String? = if (isNull(key)) null else getString(key)

private fun JSONObject.optionalInt(key: String): Int? = if (isNull(key)) null else getInt(key)

private fun <T> JSONArray.mapObjects(transform: (JSONObject) -> T): List<T> {
    return List(length()) { index -> transform(getJSONObject(index)) }
}

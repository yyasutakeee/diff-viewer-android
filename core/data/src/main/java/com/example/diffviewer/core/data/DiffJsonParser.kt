package com.example.diffviewer.core.data

import com.example.diffviewer.core.domain.DiffHunk
import com.example.diffviewer.core.domain.DiffLine
import com.example.diffviewer.core.domain.DiffLineKind
import com.example.diffviewer.core.domain.DiffSection
import com.example.diffviewer.core.domain.DiffSectionKind
import com.example.diffviewer.core.domain.FileDiff
import com.example.diffviewer.core.domain.FileDiffStatus
import com.example.diffviewer.core.domain.LatestCommit
import com.example.diffviewer.core.domain.RepositoryDiff
import org.json.JSONArray
import org.json.JSONObject

internal fun parseRepositoryDiff(jsonText: String): RepositoryDiff {
    val rootObject = JSONObject(jsonText)
    return RepositoryDiff(
        repository = rootObject.getString("repository"),
        branch = rootObject.getString("branch"),
        latestCommit = parseLatestCommit(rootObject.getJSONObject("latestCommit")),
        sections = rootObject.getJSONArray("sections").mapObjects(::parseDiffSection),
    )
}

private fun parseLatestCommit(jsonObject: JSONObject): LatestCommit = LatestCommit(
    id = jsonObject.getString("id"),
    subject = jsonObject.getString("subject"),
    fileDiffItems = jsonObject.getJSONArray("files").mapObjects(::parseFileDiff),
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

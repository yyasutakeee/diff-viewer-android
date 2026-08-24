package com.example.diffviewer.core.data

import com.example.diffviewer.core.domain.DiffHunk
import com.example.diffviewer.core.domain.DiffLine
import com.example.diffviewer.core.domain.DiffLineKind

private val hunkHeaderPattern = Regex("^@@ -(\\d+)(?:,(\\d+))? \\+(\\d+)(?:,(\\d+))? @@.*$")

internal fun parseGitHubPatch(patch: String): List<DiffHunk> {
    val diffHunkItems = mutableListOf<DiffHunk>()
    var currentHeader: String? = null
    var currentLineItems = mutableListOf<DiffLine>()
    var oldLineNumber: Int? = null
    var newLineNumber: Int? = null

    fun finishCurrentHunk() {
        currentHeader?.let { header -> diffHunkItems += DiffHunk(header, currentLineItems.toList()) }
    }

    patch.lineSequence().forEach { patchLine ->
        val headerMatch = hunkHeaderPattern.matchEntire(patchLine)
        if (headerMatch != null) {
            finishCurrentHunk()
            currentHeader = patchLine
            currentLineItems = mutableListOf()
            oldLineNumber = headerMatch.groupValues[1].toInt()
            newLineNumber = headerMatch.groupValues[3].toInt()
        } else if (currentHeader != null) {
            when {
                patchLine.startsWith("+") -> {
                    currentLineItems += DiffLine(DiffLineKind.ADDITION, patchLine.drop(1), null, newLineNumber)
                    newLineNumber = newLineNumber?.plus(1)
                }
                patchLine.startsWith("-") -> {
                    currentLineItems += DiffLine(DiffLineKind.DELETION, patchLine.drop(1), oldLineNumber, null)
                    oldLineNumber = oldLineNumber?.plus(1)
                }
                patchLine.startsWith(" ") -> {
                    currentLineItems += DiffLine(
                        DiffLineKind.CONTEXT,
                        patchLine.drop(1),
                        oldLineNumber,
                        newLineNumber,
                    )
                    oldLineNumber = oldLineNumber?.plus(1)
                    newLineNumber = newLineNumber?.plus(1)
                }
                else -> currentLineItems += DiffLine(DiffLineKind.META, patchLine, null, null)
            }
        }
    }
    finishCurrentHunk()
    return diffHunkItems
}

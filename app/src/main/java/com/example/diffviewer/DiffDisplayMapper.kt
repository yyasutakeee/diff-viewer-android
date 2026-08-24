package com.example.diffviewer

import com.example.diffviewer.core.diffui.DiffFileDisplay
import com.example.diffviewer.core.diffui.DiffHunkDisplay
import com.example.diffviewer.core.diffui.DiffLineDisplay
import com.example.diffviewer.core.diffui.DiffLineDisplayKind
import com.example.diffviewer.core.domain.DiffLineKind
import com.example.diffviewer.core.domain.FileDiff

fun FileDiff.toDiffFileDisplay(fileId: String): DiffFileDisplay = DiffFileDisplay(
    id = fileId,
    path = path ?: "不明なファイル",
    isBinary = isBinary,
    contentUnavailableMessage = contentUnavailableMessage,
    hunkDisplays = hunkItems.mapIndexed { hunkIndex, diffHunk ->
        DiffHunkDisplay(
            id = "$fileId:hunk:$hunkIndex",
            header = diffHunk.header,
            lineDisplays = diffHunk.lineItems.mapIndexed { lineIndex, diffLine ->
                DiffLineDisplay(
                    id = "$fileId:line:$hunkIndex:$lineIndex",
                    kind = diffLine.kind.toDiffLineDisplayKind(),
                    content = diffLine.content,
                    oldLine = diffLine.oldLine,
                    newLine = diffLine.newLine,
                )
            },
        )
    },
)

private fun DiffLineKind.toDiffLineDisplayKind(): DiffLineDisplayKind = when (this) {
    DiffLineKind.CONTEXT -> DiffLineDisplayKind.CONTEXT
    DiffLineKind.ADDITION -> DiffLineDisplayKind.ADDITION
    DiffLineKind.DELETION -> DiffLineDisplayKind.DELETION
    DiffLineKind.META -> DiffLineDisplayKind.META
}

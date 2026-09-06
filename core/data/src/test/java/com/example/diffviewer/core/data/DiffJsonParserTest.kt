package com.example.diffviewer.core.data

import com.example.diffviewer.core.domain.DiffLineKind
import com.example.diffviewer.core.domain.DiffSectionKind
import com.example.diffviewer.core.domain.FileDiffStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DiffJsonParserTest {
    @Test
    fun repositoryDiffParsesNativeJsonContract() {
        val repositoryDiff = parseRepositoryDiff(
            """
            {
              "repository":"/storage/emulated/0/Projects/sample",
              "branch":"detached HEAD",
              "latestCommit":${commitJson("abc", "files")},
              "commitHistory":{"commits":[],"nextOffset":20},
              "sections":[{"kind":"unstaged","files":[]}]
            }
            """.trimIndent(),
        )

        assertEquals("/storage/emulated/0/Projects/sample", repositoryDiff.repository)
        assertEquals("detached HEAD", repositoryDiff.branch)
        assertEquals("abc", repositoryDiff.latestCommit.id)
        assertEquals(20, repositoryDiff.commitHistoryPage.nextOffset)
        assertEquals(DiffSectionKind.UNSTAGED, repositoryDiff.sections.single().kind)
    }

    @Test
    fun commitDiffParsesFilesHunksLinesAndUnavailableMessage() {
        val commitDiff = parseCommitDiff(
            """
            {
              "id":"abc",
              "subject":"subject",
              "authorName":"Author",
              "authoredAt":"2026-01-01T00:00:00Z",
              "files":[{
                "oldPath":null,
                "newPath":"large.txt",
                "status":"untracked",
                "isBinary":false,
                "contentUnavailableMessage":"ファイルが大きすぎるため内容を表示できません",
                "hunks":[{"header":"@@ -0,0 +1,1 @@","lines":[{
                  "kind":"addition","content":"line","oldLine":null,"newLine":1
                }]}]
              }]
            }
            """.trimIndent(),
        )

        val fileDiff = commitDiff.fileDiffItems.single()
        assertNull(fileDiff.oldPath)
        assertEquals(FileDiffStatus.UNTRACKED, fileDiff.status)
        assertEquals("ファイルが大きすぎるため内容を表示できません", fileDiff.contentUnavailableMessage)
        assertEquals(DiffLineKind.ADDITION, fileDiff.hunkItems.single().lineItems.single().kind)
    }

    @Test
    fun commitHistoryPageParsesNullableNextOffset() {
        val page = parseCommitHistoryPage(
            """
            {
              "commits":[{
                "id":"abc","subject":"subject","authorName":"Author",
                "authoredAt":"2026-01-01T00:00:00Z"
              }],
              "nextOffset":null
            }
            """.trimIndent(),
        )

        assertEquals("abc", page.commitSummaryItems.single().id)
        assertNull(page.nextOffset)
    }

    private fun commitJson(id: String, filesKey: String): String =
        """{"id":"$id","subject":"subject","authorName":"Author","authoredAt":"2026-01-01T00:00:00Z","$filesKey":[]}"""
}

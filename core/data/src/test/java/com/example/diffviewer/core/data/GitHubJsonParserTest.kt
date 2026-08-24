package com.example.diffviewer.core.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class GitHubJsonParserTest {
    @Test
    fun parsesCommitAndMarksMissingPatchAsUnavailable() {
        val commitDiff = parseGitHubCommitDiff(
            """
            {
              "sha": "0123456789012345678901234567890123456789",
              "commit": {
                "message": "Add GitHub mode\n\nDetails",
                "author": {"name": "Example", "date": "2026-08-24T00:00:00Z"}
              },
              "files": [
                {
                  "filename": "Large.kt",
                  "status": "modified",
                  "additions": 1,
                  "deletions": 1,
                  "patch": "@@ -1 +1 @@\n-old\n+new"
                },
                {
                  "filename": "asset.png",
                  "status": "modified",
                  "additions": 0,
                  "deletions": 0
                }
              ]
            }
            """.trimIndent()
        )

        assertEquals("Add GitHub mode", commitDiff.subject)
        assertEquals(2, commitDiff.fileDiffItems.size)
        assertNull(commitDiff.fileDiffItems[0].contentUnavailableMessage)
        assertEquals(1, commitDiff.fileDiffItems[0].hunkItems.size)
        assertNotNull(commitDiff.fileDiffItems[1].contentUnavailableMessage)
        assertEquals(0, commitDiff.fileDiffItems[1].hunkItems.size)
    }

    @Test
    fun parsesRepositoryUrlAndOwnerPath() {
        val urlIdentifier = GitHubDiffRepository.parseGitHubRepositoryIdentifier(
            "https://github.com/example/sample.git"
        )
        val pathIdentifier = GitHubDiffRepository.parseGitHubRepositoryIdentifier("example/sample")

        assertEquals("/repos/example/sample", urlIdentifier.apiPath)
        assertEquals(urlIdentifier, pathIdentifier)
    }
}

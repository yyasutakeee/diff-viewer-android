package com.example.diffviewer.core.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class GitHubJsonParserTest {
    @Test
    fun parsesRepositoryCatalogVisibilityAndNextPage() {
        val githubRepositoryCatalogPage = parseGitHubRepositoryCatalogPage(
            jsonText = """
                [
                  {
                    "full_name": "example/private-project",
                    "html_url": "https://github.com/example/private-project",
                    "private": true,
                    "updated_at": "2026-08-24T08:00:00Z"
                  },
                  {
                    "full_name": "example/public-project",
                    "html_url": "https://github.com/example/public-project",
                    "private": false,
                    "updated_at": "2026-08-23T08:00:00Z"
                  }
                ]
            """.trimIndent(),
            page = 2,
            hasNextPage = true,
        )

        assertEquals(2, githubRepositoryCatalogPage.githubRepositorySummaryItems.size)
        assertEquals(
            "example/private-project",
            githubRepositoryCatalogPage.githubRepositorySummaryItems[0].nameWithOwner,
        )
        assertEquals(true, githubRepositoryCatalogPage.githubRepositorySummaryItems[0].isPrivate)
        assertEquals(false, githubRepositoryCatalogPage.githubRepositorySummaryItems[1].isPrivate)
        assertEquals(3, githubRepositoryCatalogPage.nextPage)
    }

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

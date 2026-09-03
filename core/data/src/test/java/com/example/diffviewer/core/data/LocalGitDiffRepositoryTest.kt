package com.example.diffviewer.core.data

import com.example.diffviewer.core.domain.DiffSectionKind
import java.io.File
import kotlinx.coroutines.runBlocking
import org.eclipse.jgit.api.Git
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class LocalGitDiffRepositoryTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun readsWorkingTreeAndCommitDataWithoutChangingRepository() = runBlocking {
        val repositoryDirectory = temporaryFolder.newFolder("repository")
        createCommittedRepository(repositoryDirectory)
        File(repositoryDirectory, "tracked.txt").writeText("changed\n")
        File(repositoryDirectory, "staged.txt").writeText("staged\n")
        File(repositoryDirectory, "untracked.txt").writeText("untracked\n")
        Git.open(repositoryDirectory).use { git ->
            git.add().addFilepattern("staged.txt").call()
        }
        val repository = LocalGitDiffRepository(temporaryFolder.root)

        val repositoryDiff = repository.fetchRepositoryDiff(repositoryDirectory.path)

        assertEquals("main", repositoryDiff.branch)
        assertEquals("initial", repositoryDiff.latestCommit.subject)
        assertEquals(1, repositoryDiff.commitHistoryPage.commitSummaryItems.size)
        assertEquals(
            listOf("tracked.txt"),
            repositoryDiff.findSection(DiffSectionKind.UNSTAGED).fileDiffItems.mapNotNull { it.path },
        )
        assertEquals(
            listOf("staged.txt"),
            repositoryDiff.findSection(DiffSectionKind.STAGED).fileDiffItems.mapNotNull { it.path },
        )
        val untrackedFile = repositoryDiff.findSection(DiffSectionKind.UNTRACKED).fileDiffItems.single()
        assertEquals("untracked.txt", untrackedFile.path)
        assertEquals("untracked", untrackedFile.hunkItems.single().lineItems.single().content)
        assertTrue(!File(repositoryDirectory, ".git/index.lock").exists())
    }

    private fun createCommittedRepository(repositoryDirectory: File) {
        Git.init().setDirectory(repositoryDirectory).setInitialBranch("main").call().use { git ->
            File(repositoryDirectory, "tracked.txt").writeText("original\n")
            git.add().addFilepattern("tracked.txt").call()
            git.commit()
                .setMessage("initial")
                .setAuthor("Test Author", "author@example.com")
                .setCommitter("Test Author", "author@example.com")
                .call()
        }
    }

    private fun com.example.diffviewer.core.domain.RepositoryDiff.findSection(
        kind: DiffSectionKind,
    ) = sections.first { diffSection -> diffSection.kind == kind }
}

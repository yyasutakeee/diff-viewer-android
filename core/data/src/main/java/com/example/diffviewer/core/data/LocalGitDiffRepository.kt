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
import com.example.diffviewer.core.domain.LocalGitRepository
import com.example.diffviewer.core.domain.RepositoryDiff
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.errors.MissingObjectException
import org.eclipse.jgit.diff.DiffEntry
import org.eclipse.jgit.diff.DiffFormatter
import org.eclipse.jgit.diff.RawText
import org.eclipse.jgit.dircache.DirCacheIterator
import org.eclipse.jgit.lib.Constants
import org.eclipse.jgit.lib.ObjectId
import org.eclipse.jgit.lib.Repository
import org.eclipse.jgit.storage.file.FileRepositoryBuilder
import org.eclipse.jgit.treewalk.AbstractTreeIterator
import org.eclipse.jgit.treewalk.CanonicalTreeParser
import org.eclipse.jgit.treewalk.EmptyTreeIterator
import org.eclipse.jgit.treewalk.FileTreeIterator
import org.eclipse.jgit.util.io.DisabledOutputStream
import org.eclipse.jgit.revwalk.RevCommit
import org.eclipse.jgit.revwalk.RevWalk

class LocalGitDiffRepository(
    private val sharedStorageRoot: File = File(SHARED_STORAGE_ROOT),
) : LocalGitRepository {
    override suspend fun fetchRepositoryDiff(repositoryPath: String): RepositoryDiff =
        withContext(Dispatchers.IO) {
            openSelectedRepository(repositoryPath).use { repository ->
                val latestCommit = findLatestCommit(repository)
                val untrackedPaths = findUntrackedPaths(repository)
                RepositoryDiff(
                    repository = repository.workTree.canonicalPath,
                    branch = findBranchName(repository),
                    latestCommit = createCommitDiff(repository, latestCommit),
                    commitHistoryPage = createCommitHistoryPage(repository, offset = 0),
                    sections = listOf(
                        DiffSection(
                            DiffSectionKind.UNSTAGED,
                            createUnstagedDiffs(repository, untrackedPaths),
                        ),
                        DiffSection(DiffSectionKind.STAGED, createStagedDiffs(repository)),
                        DiffSection(
                            DiffSectionKind.UNTRACKED,
                            createUntrackedDiffs(repository, untrackedPaths),
                        ),
                    ),
                )
            }
        }

    override suspend fun fetchCommitHistoryPage(
        repositoryPath: String,
        offset: Int,
    ): CommitHistoryPage = withContext(Dispatchers.IO) {
        require(offset >= 0)
        openSelectedRepository(repositoryPath).use { repository ->
            createCommitHistoryPage(repository, offset)
        }
    }

    override suspend fun fetchCommitDiff(
        repositoryPath: String,
        commitId: String,
    ): CommitDiff = withContext(Dispatchers.IO) {
        require(COMMIT_ID_PATTERN.matches(commitId)) { "コミットIDが不正です" }
        openSelectedRepository(repositoryPath).use { repository ->
            RevWalk(repository).use { revWalk ->
                val commit = revWalk.parseCommit(ObjectId.fromString(commitId))
                revWalk.parseTree(commit)
                createCommitDiff(repository, commit)
            }
        }
    }

    private fun openSelectedRepository(repositoryPath: String): Repository {
        val selectedDirectory = validateSelectedDirectory(repositoryPath)
        val repository = FileRepositoryBuilder()
            .findGitDir(selectedDirectory)
            .setMustExist(true)
            .build()
        if (repository.isBare || repository.workTree.canonicalFile != selectedDirectory) {
            repository.close()
            throw IOException("選択したフォルダ直下にGit作業ツリーがありません")
        }
        return repository
    }

    private fun validateSelectedDirectory(repositoryPath: String): File {
        val selectedDirectory = File(repositoryPath).canonicalFile
        val sharedStorageRoot = sharedStorageRoot.canonicalFile
        if (!selectedDirectory.isDirectory || !isInside(selectedDirectory, sharedStorageRoot)) {
            throw IOException("共有ストレージ内のフォルダを選択してください")
        }
        return selectedDirectory
    }

    private fun findLatestCommit(repository: Repository): RevCommit {
        val headId = repository.resolve(Constants.HEAD)
            ?: throw IOException("このリポジトリにはコミットがありません")
        return RevWalk(repository).use { revWalk ->
            revWalk.parseCommit(headId).also(revWalk::parseTree)
        }
    }

    private fun findBranchName(repository: Repository): String =
        if (repository.fullBranch == Constants.HEAD) "detached HEAD" else repository.branch

    private fun findUntrackedPaths(repository: Repository): Set<String> =
        Git.wrap(repository).use { git -> git.status().call().untracked }

    private fun createUnstagedDiffs(
        repository: Repository,
        untrackedPaths: Set<String>,
    ): List<FileDiff> {
        val diffEntryItems = scanDiffEntries(
            repository,
            DirCacheIterator(repository.readDirCache()),
            FileTreeIterator(repository),
        ).filterNot { diffEntry -> diffEntry.newPath in untrackedPaths }
        return createFileDiffs(repository, diffEntryItems)
    }

    private fun createStagedDiffs(repository: Repository): List<FileDiff> =
        repository.newObjectReader().use { objectReader ->
            val headTreeIterator = repository.resolve(Constants.HEAD + "^{tree}")?.let { treeId ->
                createTreeIterator(objectReader, treeId)
            } ?: EmptyTreeIterator()
            createFileDiffs(
                repository,
                scanDiffEntries(repository, headTreeIterator, DirCacheIterator(repository.readDirCache())),
            )
        }

    private fun scanDiffEntries(
        repository: Repository,
        oldTreeIterator: AbstractTreeIterator,
        newTreeIterator: AbstractTreeIterator,
    ): List<DiffEntry> = DiffFormatter(DisabledOutputStream.INSTANCE).use { formatter ->
        formatter.setRepository(repository)
        formatter.isDetectRenames = true
        formatter.scan(oldTreeIterator, newTreeIterator)
    }

    private fun createTreeIterator(
        objectReader: org.eclipse.jgit.lib.ObjectReader,
        treeId: ObjectId,
    ): CanonicalTreeParser = CanonicalTreeParser().apply { reset(objectReader, treeId) }

    private fun createFileDiffs(
        repository: Repository,
        diffEntryItems: List<DiffEntry>,
    ): List<FileDiff> = diffEntryItems.map { diffEntry -> createFileDiff(repository, diffEntry) }

    private fun createFileDiff(repository: Repository, diffEntry: DiffEntry): FileDiff {
        val patchOutput = ByteArrayOutputStream()
        val fileHeader = DiffFormatter(patchOutput).use { formatter ->
            formatter.setRepository(repository)
            formatter.setContext(3)
            formatter.format(diffEntry)
            formatter.toFileHeader(diffEntry)
        }
        val isBinary = fileHeader.patchType == org.eclipse.jgit.patch.FileHeader.PatchType.BINARY
        return FileDiff(
            oldPath = diffEntry.oldPath.takeUnless { path -> path == DiffEntry.DEV_NULL },
            newPath = diffEntry.newPath.takeUnless { path -> path == DiffEntry.DEV_NULL },
            status = diffEntry.changeType.toFileDiffStatus(),
            isBinary = isBinary,
            hunkItems = if (isBinary) {
                emptyList()
            } else {
                parseUnifiedPatch(patchOutput.toString(Charsets.UTF_8.name()))
            },
        )
    }

    private fun createUntrackedDiffs(
        repository: Repository,
        untrackedPaths: Set<String>,
    ): List<FileDiff> = untrackedPaths.sorted().map { path ->
        createUntrackedFileDiff(repository, path)
    }

    private fun createUntrackedFileDiff(repository: Repository, path: String): FileDiff {
        val file = resolveWorkTreeFile(repository, path)
        val contentBytes = readDisplayableContent(file)
        val isBinary = contentBytes?.let { bytes -> RawText.isBinary(bytes) } ?: false
        val contentUnavailableMessage = if (contentBytes == null) "ファイルが大きすぎるため内容を表示できません" else null
        return FileDiff(
            oldPath = null,
            newPath = path,
            status = FileDiffStatus.UNTRACKED,
            isBinary = isBinary,
            contentUnavailableMessage = contentUnavailableMessage,
            hunkItems = if (contentBytes == null || isBinary) {
                emptyList()
            } else {
                createAddedFileHunks(contentBytes)
            },
        )
    }

    private fun resolveWorkTreeFile(repository: Repository, path: String): File {
        val workTree = repository.workTree.canonicalFile
        val file = File(workTree, path).canonicalFile
        if (!isInside(file, workTree) || !file.isFile) {
            throw IOException("未追跡ファイルを読み取れません: $path")
        }
        return file
    }
    private fun isInside(file: File, directory: File): Boolean =
        file == directory || file.path.startsWith(directory.path + File.separator)


    private fun readDisplayableContent(file: File): ByteArray? {
        if (file.length() > MAXIMUM_UNTRACKED_FILE_BYTES) return null
        return file.inputStream().use { inputStream -> inputStream.readBytes() }
    }

    private fun createAddedFileHunks(contentBytes: ByteArray): List<DiffHunk> {
        val rawText = RawText(contentBytes)
        if (rawText.size() == 0) return emptyList()
        val lineItems = List(rawText.size()) { lineIndex ->
            DiffLine(
                kind = DiffLineKind.ADDITION,
                content = rawText.getString(lineIndex),
                oldLine = null,
                newLine = lineIndex + 1,
            )
        }
        return listOf(
            DiffHunk(
                header = "@@ -0,0 +1,${lineItems.size} @@",
                lineItems = lineItems,
            )
        )
    }

    private fun createCommitHistoryPage(repository: Repository, offset: Int): CommitHistoryPage {
        val commitItems = collectFirstParentCommits(
            repository = repository,
            offset = offset,
            limit = COMMIT_HISTORY_PAGE_SIZE + 1,
        )
        return CommitHistoryPage(
            commitSummaryItems = commitItems.take(COMMIT_HISTORY_PAGE_SIZE).map(::createCommitSummary),
            nextOffset = if (commitItems.size > COMMIT_HISTORY_PAGE_SIZE) {
                offset + COMMIT_HISTORY_PAGE_SIZE
            } else {
                null
            },
        )
    }

    private fun collectFirstParentCommits(
        repository: Repository,
        offset: Int,
        limit: Int,
    ): List<RevCommit> {
        val headId = repository.resolve(Constants.HEAD)
            ?: throw IOException("このリポジトリにはコミットがありません")
        return RevWalk(repository).use { revWalk ->
            val commitItems = mutableListOf<RevCommit>()
            var currentCommit: RevCommit? = revWalk.parseCommit(headId)
            var currentOffset = 0
            while (currentCommit != null && commitItems.size < limit) {
                if (currentOffset >= offset) commitItems += currentCommit
                currentOffset += 1
                currentCommit = if (currentCommit.parentCount == 0) {
                    null
                } else {
                    revWalk.parseCommit(currentCommit.getParent(0))
                }
            }
            commitItems
        }
    }

    private fun createCommitSummary(commit: RevCommit): CommitSummary = CommitSummary(
        id = commit.name,
        subject = commit.shortMessage,
        authorName = commit.authorIdent.name,
        authoredAt = commit.authorIdent.whenAsInstant.toString(),
    )

    private fun createCommitDiff(repository: Repository, commit: RevCommit): CommitDiff {
        return try {
            RevWalk(repository).use { revWalk ->
            val parsedCommit = revWalk.parseCommit(commit.id)
            val firstParent = parsedCommit.parents.firstOrNull()?.let(revWalk::parseCommit)
            val oldTree = firstParent?.let(revWalk::parseTree)
            val newTree = revWalk.parseTree(parsedCommit)
            repository.newObjectReader().use { objectReader ->
                val oldTreeIterator = oldTree?.let { tree ->
                    createTreeIterator(objectReader, tree.id)
                } ?: EmptyTreeIterator()
                val newTreeIterator = createTreeIterator(objectReader, newTree.id)
                val diffEntryItems = scanDiffEntries(repository, oldTreeIterator, newTreeIterator)
                CommitDiff(
                    id = parsedCommit.name,
                    subject = parsedCommit.shortMessage,
                    authorName = parsedCommit.authorIdent.name,
                    authoredAt = parsedCommit.authorIdent.whenAsInstant.toString(),
                    fileDiffItems = createFileDiffs(repository, diffEntryItems),
                )
            }
            }
        } catch (_: MissingObjectException) {
            CommitDiff(
                id = commit.name,
                subject = commit.shortMessage,
                authorName = commit.authorIdent.name,
                authoredAt = commit.authorIdent.whenAsInstant.toString(),
                fileDiffItems = emptyList(),
            )
        }
    }

    private fun DiffEntry.ChangeType.toFileDiffStatus(): FileDiffStatus = when (this) {
        DiffEntry.ChangeType.ADD -> FileDiffStatus.ADDED
        DiffEntry.ChangeType.COPY -> FileDiffStatus.ADDED
        DiffEntry.ChangeType.DELETE -> FileDiffStatus.DELETED
        DiffEntry.ChangeType.MODIFY -> FileDiffStatus.MODIFIED
        DiffEntry.ChangeType.RENAME -> FileDiffStatus.RENAMED
    }

    private companion object {
        const val SHARED_STORAGE_ROOT = "/storage/emulated/0"
        const val COMMIT_HISTORY_PAGE_SIZE = 20
        const val MAXIMUM_UNTRACKED_FILE_BYTES = 2L * 1024L * 1024L
        val COMMIT_ID_PATTERN = Regex("^[0-9a-fA-F]{40}$")
    }
}

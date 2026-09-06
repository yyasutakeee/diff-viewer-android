package com.example.diffviewer.core.data

import com.example.diffviewer.core.domain.CommitDiff
import com.example.diffviewer.core.domain.CommitHistoryPage
import com.example.diffviewer.core.domain.LocalGitRepository
import com.example.diffviewer.core.domain.RepositoryDiff
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class LocalGitDiffRepository private constructor(
    private val nativeGitBridge: NativeGitBridge,
) : LocalGitRepository {
    constructor() : this(NativeGitBridge())

    override suspend fun fetchRepositoryDiff(repositoryPath: String): RepositoryDiff =
        withContext(Dispatchers.IO) {
            callNativeGit { nativeGitBridge.fetchRepositoryDiff(repositoryPath) }
                .let(::parseRepositoryDiff)
        }

    override suspend fun fetchCommitHistoryPage(
        repositoryPath: String,
        offset: Int,
    ): CommitHistoryPage = withContext(Dispatchers.IO) {
        require(offset >= 0)
        callNativeGit { nativeGitBridge.fetchCommitHistoryPage(repositoryPath, offset) }
            .let(::parseCommitHistoryPage)
    }

    override suspend fun fetchCommitDiff(
        repositoryPath: String,
        commitId: String,
    ): CommitDiff = withContext(Dispatchers.IO) {
        require(COMMIT_ID_PATTERN.matches(commitId)) { "コミットIDが不正です" }
        callNativeGit { nativeGitBridge.fetchCommitDiff(repositoryPath, commitId) }
            .let(::parseCommitDiff)
    }

    private fun callNativeGit(operation: () -> String): String = try {
        operation()
    } catch (exception: IOException) {
        throw exception
    } catch (exception: Throwable) {
        throw IOException(exception.message ?: "端末内Gitリポジトリを読み取れません", exception)
    }

    private companion object {
        val COMMIT_ID_PATTERN = Regex("^[0-9a-fA-F]{40}$")
    }
}

package com.example.diffviewer.core.data

internal class NativeGitBridge {
    init {
        System.loadLibrary("diffviewer_git")
    }

    external fun fetchRepositoryDiff(repositoryPath: String): String

    external fun fetchCommitHistoryPage(repositoryPath: String, offset: Int): String

    external fun fetchCommitDiff(repositoryPath: String, commitId: String): String
}

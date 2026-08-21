package com.example.diffviewer

import com.example.diffviewer.core.domain.AppState
import com.example.diffviewer.core.domain.AppStore
import com.example.diffviewer.core.domain.ConnectionSettings
import com.example.diffviewer.core.domain.DiffSectionKind
import com.example.diffviewer.core.domain.FileDiff
import com.example.diffviewer.core.domain.FileDiffStatus
import com.example.diffviewer.feature.repository.DiffSectionUiItem
import com.example.diffviewer.feature.repository.FileDiffUiItem
import com.example.diffviewer.feature.repository.LatestCommitUiItem
import com.example.diffviewer.feature.repository.RepositoryEvent
import com.example.diffviewer.feature.repository.RepositoryDiffSource
import com.example.diffviewer.feature.repository.RepositoryUiState
import com.example.diffviewer.feature.repository.RepositoryViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class RepositoryViewModelAdapter(
    private val appStore: AppStore,
    coroutineScope: CoroutineScope,
    private val openFile: (FileDiffSelectionTarget) -> Unit,
    private val openAllDiffs: (AllDiffsSelectionTarget) -> Unit,
) : RepositoryViewModel {
    private val initialRepositoryUiMapping = mapRepositoryUiState(appStore.state.value)
    private val mutableState = MutableStateFlow(initialRepositoryUiMapping.repositoryUiState)
    private var fileDiffSelectionTargetsById = initialRepositoryUiMapping.fileDiffSelectionTargetsById
    private var allDiffsSelectionTargetsBySource = initialRepositoryUiMapping.allDiffsSelectionTargetsBySource

    override val state: StateFlow<RepositoryUiState> = mutableState.asStateFlow()

    init {
        coroutineScope.launch {
            appStore.state.collect { appState ->
                val repositoryUiMapping = mapRepositoryUiState(appState)
                fileDiffSelectionTargetsById = repositoryUiMapping.fileDiffSelectionTargetsById
                allDiffsSelectionTargetsBySource = repositoryUiMapping.allDiffsSelectionTargetsBySource
                mutableState.value = repositoryUiMapping.repositoryUiState
            }
        }
    }

    override fun send(event: RepositoryEvent) {
        when (event) {
            is RepositoryEvent.Refresh -> appStore.refreshRepositoryDiff(
                ConnectionSettings(endpoint = event.endpoint, token = event.token)
            )
            is RepositoryEvent.OpenFile -> findFileDiffSelectionTarget(event.fileId)?.let(openFile)
            is RepositoryEvent.OpenAllDiffs -> {
                findAllDiffsSelectionTarget(event.repositoryDiffSource)?.let(openAllDiffs)
            }
        }
    }

    private fun findFileDiffSelectionTarget(fileId: String): FileDiffSelectionTarget? {
        return fileDiffSelectionTargetsById[fileId]
    }

    private fun findAllDiffsSelectionTarget(
        repositoryDiffSource: RepositoryDiffSource,
    ): AllDiffsSelectionTarget? {
        return allDiffsSelectionTargetsBySource[repositoryDiffSource]
    }
}

data class FileDiffSelectionTarget(
    val sourceLabel: String,
    val fileDiff: FileDiff,
)

data class AllDiffsSelectionTarget(
    val title: String,
    val groupSelectionTargets: List<DiffFileGroupSelectionTarget>,
)

data class DiffFileGroupSelectionTarget(
    val id: String,
    val title: String,
    val fileDiffItems: List<FileDiff>,
)

private data class RepositoryUiMapping(
    val repositoryUiState: RepositoryUiState,
    val fileDiffSelectionTargetsById: Map<String, FileDiffSelectionTarget>,
    val allDiffsSelectionTargetsBySource: Map<RepositoryDiffSource, AllDiffsSelectionTarget>,
)

private fun mapRepositoryUiState(
    appState: AppState,
): RepositoryUiMapping {
    val fileDiffSelectionTargetsById = mutableMapOf<String, FileDiffSelectionTarget>()
    val repositoryDiff = appState.repositoryDiff
    val sectionUiItems = repositoryDiff?.sections.orEmpty().map { diffSection ->
        val sourceLabel = diffSection.kind.displayName()
        DiffSectionUiItem(
            title = sourceLabel,
            fileItems = diffSection.fileDiffItems.mapIndexed { index, fileDiff ->
                val fileId = "working:${diffSection.kind}:$index:${fileDiff.path.orEmpty()}"
                fileDiffSelectionTargetsById[fileId] = FileDiffSelectionTarget(sourceLabel, fileDiff)
                fileDiff.toUiItem(fileId)
            },
        )
    }
    val latestCommitUiItem = repositoryDiff?.latestCommit?.let { latestCommit ->
        LatestCommitUiItem(
            id = latestCommit.id,
            subject = latestCommit.subject,
            fileItems = latestCommit.fileDiffItems.mapIndexed { index, fileDiff ->
                val fileId = "latest:$index:${fileDiff.path.orEmpty()}"
                fileDiffSelectionTargetsById[fileId] = FileDiffSelectionTarget("最新コミット", fileDiff)
                fileDiff.toUiItem(fileId)
            },
        )
    }
    val repositoryUiState = RepositoryUiState(
        endpoint = appState.connectionSettings.endpoint,
        token = appState.connectionSettings.token,
        repositoryName = repositoryDiff?.repository?.substringAfterLast('/'),
        repositoryPath = repositoryDiff?.repository,
        branchSummary = repositoryDiff?.let {
            "${it.branch} ・ ${it.changedFileCount}ファイル変更"
        },
        latestCommit = latestCommitUiItem,
        workingTreeSectionItems = sectionUiItems,
        isLoading = appState.isLoadingRepositoryDiff,
        errorMessage = appState.repositoryDiffErrorMessage,
    )
    val allDiffsSelectionTargetsBySource = buildAllDiffsSelectionTargets(appState)
    return RepositoryUiMapping(
        repositoryUiState = repositoryUiState,
        fileDiffSelectionTargetsById = fileDiffSelectionTargetsById,
        allDiffsSelectionTargetsBySource = allDiffsSelectionTargetsBySource,
    )
}

private fun buildAllDiffsSelectionTargets(
    appState: AppState,
): Map<RepositoryDiffSource, AllDiffsSelectionTarget> {
    val repositoryDiff = appState.repositoryDiff ?: return emptyMap()
    val workingTreeGroupSelectionTargets = repositoryDiff.sections
        .filter { diffSection -> diffSection.fileDiffItems.isNotEmpty() }
        .map { diffSection ->
            DiffFileGroupSelectionTarget(
                id = "working:${diffSection.kind}",
                title = diffSection.kind.displayName(),
                fileDiffItems = diffSection.fileDiffItems,
            )
        }
    val latestCommitGroupSelectionTarget = DiffFileGroupSelectionTarget(
        id = "latest:${repositoryDiff.latestCommit.id}",
        title = repositoryDiff.latestCommit.subject,
        fileDiffItems = repositoryDiff.latestCommit.fileDiffItems,
    )
    return mapOf(
        RepositoryDiffSource.WORKING_TREE to AllDiffsSelectionTarget(
            title = "未コミットのすべての差分",
            groupSelectionTargets = workingTreeGroupSelectionTargets,
        ),
        RepositoryDiffSource.LATEST_COMMIT to AllDiffsSelectionTarget(
            title = "最新コミットのすべての差分",
            groupSelectionTargets = listOf(latestCommitGroupSelectionTarget),
        ),
    )
}

private fun FileDiff.toUiItem(fileId: String): FileDiffUiItem = FileDiffUiItem(
    id = fileId,
    path = path ?: "不明なファイル",
    status = status.displayName(),
    isBinary = isBinary,
    additionCount = additionCount,
    deletionCount = deletionCount,
)

private fun DiffSectionKind.displayName(): String = when (this) {
    DiffSectionKind.UNSTAGED -> "未ステージ"
    DiffSectionKind.STAGED -> "ステージ済み"
    DiffSectionKind.UNTRACKED -> "未追跡"
}

private fun FileDiffStatus.displayName(): String = when (this) {
    FileDiffStatus.MODIFIED -> "変更"
    FileDiffStatus.ADDED -> "追加"
    FileDiffStatus.DELETED -> "削除"
    FileDiffStatus.RENAMED -> "名前変更"
    FileDiffStatus.UNTRACKED -> "未追跡"
}

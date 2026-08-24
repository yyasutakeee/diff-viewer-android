package com.example.diffviewer.feature.repository

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.diffviewer.core.designsystem.AdditionTextColor
import com.example.diffviewer.core.designsystem.DeletionTextColor

@Composable
fun RepositoryScreen(viewModel: RepositoryViewModel) {
    val repositoryUiState by viewModel.state.collectAsStateWithLifecycle()
    var endpoint by rememberSaveable { mutableStateOf(repositoryUiState.endpoint) }
    var token by rememberSaveable { mutableStateOf(repositoryUiState.token) }
    var selectedSource by rememberSaveable { mutableStateOf(DiffSource.WORKING_TREE) }
    val selectedRepositoryDiffSource = selectedSource.toRepositoryDiffSource(
        repositoryUiState.selectedCommit?.id
    )

    LaunchedEffect(repositoryUiState.endpoint, repositoryUiState.token) {
        if (endpoint.isEmpty()) endpoint = repositoryUiState.endpoint
        if (token.isEmpty()) token = repositoryUiState.token
    }
    LaunchedEffect(repositoryUiState) {
        val workingTreeIsEmpty = repositoryUiState.workingTreeSectionItems.all { it.fileItems.isEmpty() }
        if (
            selectedSource == DiffSource.WORKING_TREE &&
            workingTreeIsEmpty &&
            repositoryUiState.latestCommit?.fileItems?.isNotEmpty() == true
        ) {
            selectedSource = DiffSource.LATEST_COMMIT
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text("Diff Viewer", style = MaterialTheme.typography.headlineMedium)
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                "TermuxのGit変更を読み取り専用で表示します",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        item {
            ConnectionCard(
                endpoint = endpoint,
                token = token,
                isLoading = repositoryUiState.isLoading,
                onEndpointChange = { endpoint = it },
                onTokenChange = { token = it },
                onRefresh = { viewModel.send(RepositoryEvent.Refresh(endpoint, token)) },
            )
        }
        repositoryUiState.errorMessage?.let { errorMessage -> item { ErrorCard(errorMessage) } }
        if (repositoryUiState.repositoryName != null) {
            item { RepositorySummary(repositoryUiState) }
            item { DiffSourceSelector(selectedSource) { selectedSource = it } }
            item {
                Button(
                    onClick = {
                        selectedRepositoryDiffSource?.let { repositoryDiffSource ->
                            viewModel.send(RepositoryEvent.OpenAllDiffs(repositoryDiffSource))
                        }
                    },
                    enabled = repositoryUiState.hasFiles(selectedSource),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("すべての差分を見る")
                }
            }
            when (selectedSource) {
                DiffSource.WORKING_TREE -> workingTreeItems(repositoryUiState, viewModel)
                DiffSource.LATEST_COMMIT -> latestCommitItems(repositoryUiState, viewModel)
                DiffSource.COMMIT_HISTORY -> commitHistoryItems(repositoryUiState, viewModel)
            }
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.commitHistoryItems(
    repositoryUiState: RepositoryUiState,
    viewModel: RepositoryViewModel,
) {
    item { Text("コミット履歴", style = MaterialTheme.typography.titleMedium) }
    repositoryUiState.commitHistoryItems.forEach { commitHistoryUiItem ->
        item(key = "commit:${commitHistoryUiItem.id}") {
            CommitHistoryCard(commitHistoryUiItem) {
                viewModel.send(RepositoryEvent.SelectCommit(commitHistoryUiItem.id))
            }
        }
        if (commitHistoryUiItem.isSelected) {
            if (repositoryUiState.isLoadingSelectedCommit) {
                item(key = "commit-loading:${commitHistoryUiItem.id}") {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(24.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator()
                    }
                }
            } else {
                val selectedCommit = repositoryUiState.selectedCommit
                if (selectedCommit?.id == commitHistoryUiItem.id) {
                    items(
                        items = selectedCommit.fileItems,
                        key = { fileDiffUiItem -> fileDiffUiItem.id },
                    ) { fileDiffUiItem ->
                        FileCard(fileDiffUiItem) {
                            viewModel.send(RepositoryEvent.OpenFile(fileDiffUiItem.id))
                        }
                    }
                    if (selectedCommit.fileItems.isEmpty()) {
                        item(key = "commit-empty:${commitHistoryUiItem.id}") {
                            EmptyMessage("このコミットに表示できる変更はありません")
                        }
                    }
                }
            }
        }
    }
    repositoryUiState.commitHistoryErrorMessage?.let { errorMessage ->
        item { ErrorCard(errorMessage) }
    }
    if (repositoryUiState.commitHistoryItems.isEmpty()) {
        item { EmptyMessage("コミット履歴はありません") }
    }
    if (repositoryUiState.hasMoreCommits) {
        item {
            Button(
                onClick = { viewModel.send(RepositoryEvent.LoadMoreCommits) },
                enabled = !repositoryUiState.isLoadingCommitHistory,
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (repositoryUiState.isLoadingCommitHistory) {
                    CircularProgressIndicator(
                        modifier = Modifier.width(20.dp).height(20.dp),
                        strokeWidth = 2.dp,
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("読み込み中")
                } else {
                    Text("さらに読み込む")
                }
            }
        }
    }
}

@Composable
private fun CommitHistoryCard(
    commitHistoryUiItem: CommitHistoryUiItem,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = if (commitHistoryUiItem.isSelected) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceContainer
            },
        ),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(commitHistoryUiItem.subject, style = MaterialTheme.typography.titleSmall)
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                commitHistoryUiItem.id.take(8),
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                "${commitHistoryUiItem.authorName} ・ ${commitHistoryUiItem.authoredAt}",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.workingTreeItems(
    repositoryUiState: RepositoryUiState,
    viewModel: RepositoryViewModel,
) {
    repositoryUiState.workingTreeSectionItems.forEach { diffSectionUiItem ->
        if (diffSectionUiItem.fileItems.isNotEmpty()) {
            item { Text(diffSectionUiItem.title, style = MaterialTheme.typography.titleMedium) }
            items(diffSectionUiItem.fileItems, key = { it.id }) { fileDiffUiItem ->
                FileCard(fileDiffUiItem) {
                    viewModel.send(RepositoryEvent.OpenFile(fileDiffUiItem.id))
                }
            }
        }
    }
    if (repositoryUiState.workingTreeSectionItems.all { it.fileItems.isEmpty() }) {
        item { EmptyMessage("未コミットの変更はありません") }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.latestCommitItems(
    repositoryUiState: RepositoryUiState,
    viewModel: RepositoryViewModel,
) {
    val latestCommitUiItem = repositoryUiState.latestCommit ?: return
    item {
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("最新コミット", style = MaterialTheme.typography.labelLarge)
                Text(latestCommitUiItem.subject, style = MaterialTheme.typography.titleMedium)
                Text(
                    latestCommitUiItem.id.take(8),
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
    items(latestCommitUiItem.fileItems, key = { it.id }) { fileDiffUiItem ->
        FileCard(fileDiffUiItem) { viewModel.send(RepositoryEvent.OpenFile(fileDiffUiItem.id)) }
    }
    if (latestCommitUiItem.fileItems.isEmpty()) {
        item { EmptyMessage("最新コミットに表示できる変更はありません") }
    }
}

@Composable
private fun ConnectionCard(
    endpoint: String,
    token: String,
    isLoading: Boolean,
    onEndpointChange: (String) -> Unit,
    onTokenChange: (String) -> Unit,
    onRefresh: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(
                value = endpoint,
                onValueChange = onEndpointChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("ヘルパーURL") },
                singleLine = true,
            )
            OutlinedTextField(
                value = token,
                onValueChange = onTokenChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("アクセストークン") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
            )
            Button(onClick = onRefresh, enabled = !isLoading, modifier = Modifier.fillMaxWidth()) {
                if (isLoading) {
                    CircularProgressIndicator(modifier = Modifier.width(20.dp).height(20.dp), strokeWidth = 2.dp)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("読み込み中")
                } else {
                    Text("変更を更新")
                }
            }
        }
    }
}

@Composable
private fun RepositorySummary(repositoryUiState: RepositoryUiState) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(requireNotNull(repositoryUiState.repositoryName), style = MaterialTheme.typography.titleLarge)
            Text(
                repositoryUiState.repositoryPath.orEmpty(),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(repositoryUiState.branchSummary.orEmpty())
        }
    }
}

@Composable
private fun DiffSourceSelector(selectedSource: DiffSource, onSelected: (DiffSource) -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        DiffSource.entries.forEach { diffSource ->
            FilterChip(
                selected = selectedSource == diffSource,
                onClick = { onSelected(diffSource) },
                label = { Text(diffSource.title) },
            )
        }
    }
}

@Composable
private fun FileCard(fileDiffUiItem: FileDiffUiItem, onClick: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(fileDiffUiItem.path, style = MaterialTheme.typography.titleSmall, fontFamily = FontFamily.Monospace)
            Spacer(modifier = Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(fileDiffUiItem.status, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (fileDiffUiItem.isBinary) {
                    Text("バイナリ")
                } else {
                    Text("+${fileDiffUiItem.additionCount}", color = AdditionTextColor)
                    Text("-${fileDiffUiItem.deletionCount}", color = DeletionTextColor)
                }
            }
        }
    }
}

@Composable
private fun ErrorCard(errorMessage: String) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("取得エラー", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.error)
            Spacer(modifier = Modifier.height(4.dp))
            Text(errorMessage)
        }
    }
}

@Composable
private fun EmptyMessage(message: String) {
    Box(modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp), contentAlignment = Alignment.Center) {
        Text(message)
    }
}

private enum class DiffSource(val title: String) {
    WORKING_TREE("未コミット"),
    LATEST_COMMIT("最新コミット"),
    COMMIT_HISTORY("履歴"),
}

private fun DiffSource.toRepositoryDiffSource(selectedCommitId: String?): RepositoryDiffSource? = when (this) {
    DiffSource.WORKING_TREE -> RepositoryDiffSource.WorkingTree
    DiffSource.LATEST_COMMIT -> RepositoryDiffSource.LatestCommit
    DiffSource.COMMIT_HISTORY -> selectedCommitId?.let { commitId ->
        RepositoryDiffSource.Commit(commitId)
    }
}

private fun RepositoryUiState.hasFiles(diffSource: DiffSource): Boolean = when (diffSource) {
    DiffSource.WORKING_TREE -> workingTreeSectionItems.any { diffSectionUiItem ->
        diffSectionUiItem.fileItems.isNotEmpty()
    }
    DiffSource.LATEST_COMMIT -> latestCommit?.fileItems?.isNotEmpty() == true
    DiffSource.COMMIT_HISTORY -> selectedCommit?.fileItems?.isNotEmpty() == true
}

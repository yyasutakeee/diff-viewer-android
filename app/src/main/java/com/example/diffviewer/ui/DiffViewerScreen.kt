package com.example.diffviewer.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.diffviewer.data.DiffHunk
import com.example.diffviewer.data.DiffLine
import com.example.diffviewer.data.DiffLineKind
import com.example.diffviewer.data.DiffSectionKind
import com.example.diffviewer.data.FileDiff
import com.example.diffviewer.data.FileDiffStatus
import com.example.diffviewer.data.RepositoryDiff

@Composable
fun DiffViewerScreen(
    initialEndpoint: String,
    initialToken: String,
    repositoryDiff: RepositoryDiff?,
    isLoading: Boolean,
    errorMessage: String?,
    onRefresh: (endpoint: String, token: String) -> Unit,
) {
    var endpoint by rememberSaveable { mutableStateOf(initialEndpoint) }
    var token by rememberSaveable { mutableStateOf(initialToken) }
    var selectedFile by remember { mutableStateOf<SelectedFile?>(null) }

    Surface(modifier = Modifier.fillMaxSize()) {
        if (selectedFile == null) {
            RepositoryScreen(
                endpoint = endpoint,
                token = token,
                repositoryDiff = repositoryDiff,
                isLoading = isLoading,
                errorMessage = errorMessage,
                onEndpointChange = { endpoint = it },
                onTokenChange = { token = it },
                onRefresh = { onRefresh(endpoint, token) },
                onFileSelected = { sectionKind, fileDiff ->
                    selectedFile = SelectedFile(sectionKind, fileDiff)
                },
            )
        } else {
            FileDiffScreen(
                selectedFile = requireNotNull(selectedFile),
                onBack = { selectedFile = null },
            )
        }
    }
}

@Composable
private fun RepositoryScreen(
    endpoint: String,
    token: String,
    repositoryDiff: RepositoryDiff?,
    isLoading: Boolean,
    errorMessage: String?,
    onEndpointChange: (String) -> Unit,
    onTokenChange: (String) -> Unit,
    onRefresh: () -> Unit,
    onFileSelected: (DiffSectionKind, FileDiff) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text("Diff Viewer", style = MaterialTheme.typography.headlineMedium)
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                "TermuxのGit変更を読み取り専用で表示します",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        item {
            ConnectionCard(
                endpoint = endpoint,
                token = token,
                isLoading = isLoading,
                onEndpointChange = onEndpointChange,
                onTokenChange = onTokenChange,
                onRefresh = onRefresh,
            )
        }
        if (errorMessage != null) {
            item { ErrorCard(errorMessage) }
        }
        if (repositoryDiff != null) {
            item { RepositorySummary(repositoryDiff) }
            repositoryDiff.sections.forEach { section ->
                if (section.fileDiffItems.isNotEmpty()) {
                    item {
                        Text(
                            text = section.kind.displayName(),
                            style = MaterialTheme.typography.titleMedium,
                        )
                    }
                    items(
                        items = section.fileDiffItems,
                        key = { fileDiff -> "${section.kind}:${fileDiff.displayPath}" },
                    ) { fileDiff ->
                        FileCard(
                            fileDiff = fileDiff,
                            onClick = { onFileSelected(section.kind, fileDiff) },
                        )
                    }
                }
            }
            if (repositoryDiff.changedFileCount == 0) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 32.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("変更はありません")
                    }
                }
            }
        }
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
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
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
            Button(
                onClick = onRefresh,
                enabled = !isLoading,
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier
                            .width(20.dp)
                            .height(20.dp),
                        strokeWidth = 2.dp,
                    )
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
private fun ErrorCard(errorMessage: String) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "取得エラー",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.error,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(errorMessage)
        }
    }
}

@Composable
private fun RepositorySummary(repositoryDiff: RepositoryDiff) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                repositoryDiff.repository.substringAfterLast('/'),
                style = MaterialTheme.typography.titleLarge,
            )
            Text(
                repositoryDiff.repository,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text("${repositoryDiff.branch} ・ ${repositoryDiff.changedFileCount}ファイル変更")
        }
    }
}

@Composable
private fun FileCard(fileDiff: FileDiff, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                fileDiff.displayPath,
                style = MaterialTheme.typography.titleSmall,
                fontFamily = FontFamily.Monospace,
            )
            Spacer(modifier = Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(fileDiff.status.displayName(), color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (fileDiff.isBinary) {
                    Text("バイナリ")
                } else {
                    Text("+${fileDiff.additionCount}", color = AdditionTextColor)
                    Text("-${fileDiff.deletionCount}", color = DeletionTextColor)
                }
            }
        }
    }
}

@Composable
private fun FileDiffScreen(selectedFile: SelectedFile, onBack: () -> Unit) {
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onBack) { Text("戻る") }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    selectedFile.fileDiff.displayPath,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.titleMedium,
                    fontFamily = FontFamily.Monospace,
                )
                Text(
                    selectedFile.sectionKind.displayName(),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        HorizontalDivider()
        if (selectedFile.fileDiff.isBinary) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("バイナリファイルの内容は表示できません")
            }
        } else if (selectedFile.fileDiff.hunkItems.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("表示できる行差分はありません")
            }
        } else {
            DiffHunkList(selectedFile.fileDiff.hunkItems)
        }
    }
}

@Composable
private fun DiffHunkList(hunkItems: List<DiffHunk>) {
    val horizontalScrollState = rememberScrollState()
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        hunkItems.forEachIndexed { hunkIndex, diffHunk ->
            item(key = "hunk-$hunkIndex") {
                Text(
                    text = diffHunk.header,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.secondaryContainer)
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    fontFamily = FontFamily.Monospace,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            items(
                items = diffHunk.lineItems,
                key = { diffLine ->
                    "$hunkIndex:${diffLine.kind}:${diffLine.oldLine}:${diffLine.newLine}:${diffLine.content.hashCode()}"
                },
            ) { diffLine ->
                DiffLineRow(diffLine, horizontalScrollState)
            }
        }
    }
}

@Composable
private fun DiffLineRow(
    diffLine: DiffLine,
    horizontalScrollState: androidx.compose.foundation.ScrollState,
) {
    val backgroundColor = when (diffLine.kind) {
        DiffLineKind.ADDITION -> AdditionBackgroundColor
        DiffLineKind.DELETION -> DeletionBackgroundColor
        DiffLineKind.META -> MaterialTheme.colorScheme.surfaceVariant
        DiffLineKind.CONTEXT -> Color.Transparent
    }
    val textColor = when (diffLine.kind) {
        DiffLineKind.ADDITION -> AdditionTextColor
        DiffLineKind.DELETION -> DeletionTextColor
        else -> MaterialTheme.colorScheme.onSurface
    }
    val prefix = when (diffLine.kind) {
        DiffLineKind.ADDITION -> "+"
        DiffLineKind.DELETION -> "-"
        DiffLineKind.CONTEXT -> " "
        DiffLineKind.META -> "\\"
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(backgroundColor)
            .horizontalScroll(horizontalScrollState)
            .padding(vertical = 2.dp),
    ) {
        Text(
            text = diffLine.oldLine?.toString()?.padStart(4).orEmpty().padStart(4),
            modifier = Modifier.width(40.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontFamily = FontFamily.Monospace,
            style = MaterialTheme.typography.bodySmall,
        )
        Text(
            text = diffLine.newLine?.toString()?.padStart(4).orEmpty().padStart(4),
            modifier = Modifier.width(40.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontFamily = FontFamily.Monospace,
            style = MaterialTheme.typography.bodySmall,
        )
        Text(
            text = "$prefix${diffLine.content}",
            color = textColor,
            fontFamily = FontFamily.Monospace,
            style = MaterialTheme.typography.bodySmall,
            softWrap = false,
        )
    }
}

private data class SelectedFile(
    val sectionKind: DiffSectionKind,
    val fileDiff: FileDiff,
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

private val AdditionBackgroundColor = Color(0xFFE6F4EA)
private val AdditionTextColor = Color(0xFF137333)
private val DeletionBackgroundColor = Color(0xFFFCE8E6)
private val DeletionTextColor = Color(0xFFB3261E)

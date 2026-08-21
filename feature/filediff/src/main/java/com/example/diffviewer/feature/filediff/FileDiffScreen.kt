package com.example.diffviewer.feature.filediff

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.diffviewer.core.designsystem.AdditionBackgroundColor
import com.example.diffviewer.core.designsystem.AdditionTextColor
import com.example.diffviewer.core.designsystem.DeletionBackgroundColor
import com.example.diffviewer.core.designsystem.DeletionTextColor

@Composable
fun FileDiffScreen(viewModel: FileDiffViewModel) {
    val fileDiffUiState by viewModel.state.collectAsStateWithLifecycle()
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = { viewModel.send(FileDiffEvent.NavigateBack) }) { Text("戻る") }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    fileDiffUiState.path,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.titleMedium,
                    fontFamily = FontFamily.Monospace,
                )
                Text(
                    fileDiffUiState.sourceLabel,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        HorizontalDivider()
        when {
            fileDiffUiState.isBinary -> EmptyFileMessage("バイナリファイルの内容は表示できません")
            fileDiffUiState.hunkItems.isEmpty() -> EmptyFileMessage("表示できる行差分はありません")
            else -> DiffHunkList(fileDiffUiState.hunkItems)
        }
    }
}

@Composable
private fun EmptyFileMessage(message: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(message) }
}

@Composable
private fun DiffHunkList(hunkItems: List<DiffHunkUiItem>) {
    val horizontalScrollState = rememberScrollState()
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        hunkItems.forEach { diffHunkUiItem ->
            item(key = diffHunkUiItem.id) {
                Text(
                    text = diffHunkUiItem.header,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.secondaryContainer)
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    fontFamily = FontFamily.Monospace,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            items(diffHunkUiItem.lineItems, key = { diffLineUiItem -> diffLineUiItem.id }) {
                diffLineUiItem -> DiffLineRow(diffLineUiItem, horizontalScrollState)
            }
        }
    }
}

@Composable
private fun DiffLineRow(diffLineUiItem: DiffLineUiItem, horizontalScrollState: ScrollState) {
    val backgroundColor = when (diffLineUiItem.kind) {
        DiffLineUiKind.ADDITION -> AdditionBackgroundColor
        DiffLineUiKind.DELETION -> DeletionBackgroundColor
        DiffLineUiKind.META -> MaterialTheme.colorScheme.surfaceVariant
        DiffLineUiKind.CONTEXT -> Color.Transparent
    }
    val textColor = when (diffLineUiItem.kind) {
        DiffLineUiKind.ADDITION -> AdditionTextColor
        DiffLineUiKind.DELETION -> DeletionTextColor
        else -> MaterialTheme.colorScheme.onSurface
    }
    val prefix = when (diffLineUiItem.kind) {
        DiffLineUiKind.ADDITION -> "+"
        DiffLineUiKind.DELETION -> "-"
        DiffLineUiKind.CONTEXT -> " "
        DiffLineUiKind.META -> "\\"
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(backgroundColor)
            .horizontalScroll(horizontalScrollState)
            .padding(vertical = 2.dp),
    ) {
        LineNumber(diffLineUiItem.oldLine)
        LineNumber(diffLineUiItem.newLine)
        Text(
            text = "$prefix${diffLineUiItem.content}",
            color = textColor,
            fontFamily = FontFamily.Monospace,
            style = MaterialTheme.typography.bodySmall,
            softWrap = false,
        )
    }
}

@Composable
private fun LineNumber(lineNumber: Int?) {
    Text(
        text = lineNumber?.toString()?.padStart(4).orEmpty().padStart(4),
        modifier = Modifier.width(40.dp),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontFamily = FontFamily.Monospace,
        style = MaterialTheme.typography.bodySmall,
    )
}

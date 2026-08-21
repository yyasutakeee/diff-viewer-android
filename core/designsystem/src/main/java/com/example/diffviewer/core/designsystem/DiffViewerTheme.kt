package com.example.diffviewer.core.designsystem

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val AdditionBackgroundColor = Color(0xFFE6F4EA)
val AdditionTextColor = Color(0xFF137333)
val DeletionBackgroundColor = Color(0xFFFCE8E6)
val DeletionTextColor = Color(0xFFB3261E)

@Composable
fun DiffViewerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) darkColorScheme() else lightColorScheme(),
        content = content,
    )
}

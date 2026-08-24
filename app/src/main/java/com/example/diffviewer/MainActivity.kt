package com.example.diffviewer

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.lifecycleScope
import com.example.diffviewer.core.data.GitHubDiffRepository
import com.example.diffviewer.core.data.SharedPreferencesConnectionSettingsRepository
import com.example.diffviewer.core.data.TermuxDiffRepository
import com.example.diffviewer.core.designsystem.DiffViewerTheme
import com.example.diffviewer.core.domain.AppStore

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val sharedPreferences = getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
        val connectionSettingsRepository = SharedPreferencesConnectionSettingsRepository(sharedPreferences)
        val diffDisplaySettingsStore = DiffDisplaySettingsStore(sharedPreferences)
        val appStore = AppStore(
            diffRepository = TermuxDiffRepository(),
            githubDiffRepository = GitHubDiffRepository(),
            connectionSettingsRepository = connectionSettingsRepository,
            coroutineScope = lifecycleScope,
        )

        setContent {
            DiffViewerTheme {
                DiffViewerApplication(
                    appStore = appStore,
                    diffDisplaySettingsStore = diffDisplaySettingsStore,
                    coroutineScope = lifecycleScope,
                )
            }
        }
    }

    private companion object {
        const val PREFERENCES_NAME = "diff_viewer"
    }
}

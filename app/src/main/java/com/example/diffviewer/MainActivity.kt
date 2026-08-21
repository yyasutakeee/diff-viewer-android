package com.example.diffviewer

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.lifecycleScope
import com.example.diffviewer.core.data.SharedPreferencesConnectionSettingsRepository
import com.example.diffviewer.core.data.TermuxDiffRepository
import com.example.diffviewer.core.designsystem.DiffViewerTheme
import com.example.diffviewer.core.domain.AppStore

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val connectionSettingsRepository = SharedPreferencesConnectionSettingsRepository(
            getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
        )
        val appStore = AppStore(
            diffRepository = TermuxDiffRepository(),
            connectionSettingsRepository = connectionSettingsRepository,
            coroutineScope = lifecycleScope,
        )

        setContent {
            DiffViewerTheme {
                DiffViewerApplication(appStore = appStore, coroutineScope = lifecycleScope)
            }
        }
    }

    private companion object {
        const val PREFERENCES_NAME = "diff_viewer"
    }
}

package com.example.diffviewer

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.lifecycleScope
import com.example.diffviewer.core.data.AndroidKeystoreSecretCipher
import com.example.diffviewer.core.data.GitHubDiffRepository
import com.example.diffviewer.core.data.LocalGitDiffRepository
import com.example.diffviewer.core.data.SharedPreferencesConnectionSettingsRepository
import com.example.diffviewer.core.data.TermuxDiffRepository
import com.example.diffviewer.core.designsystem.DiffViewerTheme
import com.example.diffviewer.core.domain.AppStore
import java.io.File

class MainActivity : ComponentActivity() {
    private lateinit var appStore: AppStore
    private val hasLocalStorageAccess = mutableStateOf(false)

    private val localRepositoryPicker = registerForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { treeUri ->
        treeUri?.let(::resolvePrimaryStoragePath)?.let(appStore::refreshLocalRepositoryDiff)
    }

    private val legacyStoragePermissionRequest = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) {
        updateLocalStorageAccess()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val sharedPreferences = getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
        val connectionSettingsRepository = SharedPreferencesConnectionSettingsRepository(
            sharedPreferences = sharedPreferences,
            secretCipher = AndroidKeystoreSecretCipher(),
        )
        val diffDisplaySettingsStore = DiffDisplaySettingsStore(sharedPreferences)
        val githubRepository = GitHubDiffRepository()
        appStore = AppStore(
            diffRepository = TermuxDiffRepository(),
            githubDiffRepository = githubRepository,
            localGitRepository = LocalGitDiffRepository(),
            githubRepositoryCatalog = githubRepository,
            connectionSettingsRepository = connectionSettingsRepository,
            coroutineScope = lifecycleScope,
        )
        updateLocalStorageAccess()

        setContent {
            DiffViewerTheme {
                DiffViewerApplication(
                    appStore = appStore,
                    diffDisplaySettingsStore = diffDisplaySettingsStore,
                    coroutineScope = lifecycleScope,
                    hasLocalStorageAccess = hasLocalStorageAccess.value,
                    requestLocalStorageAccess = ::requestLocalStorageAccess,
                    chooseLocalRepository = ::chooseLocalRepository,
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        updateLocalStorageAccess()
    }

    private fun requestLocalStorageAccess() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            startActivity(
                Intent(
                    Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                    android.net.Uri.parse("package:$packageName"),
                )
            )
        } else {
            legacyStoragePermissionRequest.launch(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
    }

    private fun chooseLocalRepository() {
        if (!hasLocalStorageAccess.value) {
            requestLocalStorageAccess()
            return
        }
        localRepositoryPicker.launch(null)
    }

    private fun updateLocalStorageAccess() {
        hasLocalStorageAccess.value = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun resolvePrimaryStoragePath(treeUri: android.net.Uri): String? {
        val documentId = runCatching {
            DocumentsContract.getTreeDocumentId(treeUri)
        }.getOrNull() ?: return null
        val volumeAndPath = documentId.split(":", limit = 2)
        if (!volumeAndPath.first().equals(PRIMARY_STORAGE_VOLUME, ignoreCase = true)) return null
        val relativePath = volumeAndPath.getOrElse(1) { "" }
        return File(Environment.getExternalStorageDirectory(), relativePath).canonicalPath
    }

    private companion object {
        const val PREFERENCES_NAME = "diff_viewer"
        const val PRIMARY_STORAGE_VOLUME = "primary"
    }
}

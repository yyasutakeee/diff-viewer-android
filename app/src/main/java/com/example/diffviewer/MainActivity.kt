package com.example.diffviewer

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.example.diffviewer.data.RepositoryDiff
import com.example.diffviewer.data.TermuxDiffClient
import com.example.diffviewer.ui.DiffViewerScreen
import com.example.diffviewer.ui.theme.DiffViewerTheme
import java.util.concurrent.Executors

class MainActivity : ComponentActivity() {
    private val diffLoader = DiffLoader()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val preferences = getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
        val initialEndpoint = preferences.getString(ENDPOINT_KEY, DEFAULT_ENDPOINT) ?: DEFAULT_ENDPOINT
        val initialToken = preferences.getString(TOKEN_KEY, "") ?: ""

        setContent {
            DiffViewerTheme {
                DiffViewerScreen(
                    initialEndpoint = initialEndpoint,
                    initialToken = initialToken,
                    repositoryDiff = diffLoader.repositoryDiff,
                    isLoading = diffLoader.isLoading,
                    errorMessage = diffLoader.errorMessage,
                    onRefresh = { endpoint, token ->
                        preferences.edit()
                            .putString(ENDPOINT_KEY, endpoint)
                            .putString(TOKEN_KEY, token)
                            .apply()
                        diffLoader.load(endpoint, token)
                    },
                )
            }
        }
    }

    override fun onDestroy() {
        diffLoader.close()
        super.onDestroy()
    }

    private companion object {
        const val PREFERENCES_NAME = "diff_viewer"
        const val ENDPOINT_KEY = "helper_endpoint"
        const val TOKEN_KEY = "helper_token"
        const val DEFAULT_ENDPOINT = "http://127.0.0.1:8765"
    }
}

private class DiffLoader : AutoCloseable {
    private val executor = Executors.newSingleThreadExecutor()
    private val termuxDiffClient = TermuxDiffClient()

    var repositoryDiff by mutableStateOf<RepositoryDiff?>(null)
        private set
    var isLoading by mutableStateOf(false)
        private set
    var errorMessage by mutableStateOf<String?>(null)
        private set

    fun load(endpoint: String, token: String) {
        if (isLoading) return
        if (token.isBlank()) {
            errorMessage = "アクセストークンを入力してください"
            return
        }
        isLoading = true
        errorMessage = null
        executor.execute {
            runCatching { termuxDiffClient.fetchRepositoryDiff(endpoint, token) }
                .onSuccess { repositoryDiff ->
                    android.os.Handler(mainLooper()).post {
                        this.repositoryDiff = repositoryDiff
                        isLoading = false
                    }
                }
                .onFailure { error ->
                    android.os.Handler(mainLooper()).post {
                        errorMessage = error.message ?: "差分を取得できませんでした"
                        isLoading = false
                    }
                }
        }
    }

    override fun close() {
        executor.shutdownNow()
    }

    private fun mainLooper() = android.os.Looper.getMainLooper()
}


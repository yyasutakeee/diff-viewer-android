package com.example.diffviewer.core.data

import android.content.SharedPreferences
import com.example.diffviewer.core.domain.ConnectionSettings
import com.example.diffviewer.core.domain.ConnectionSettingsRepository
import com.example.diffviewer.core.domain.RepositorySource
import com.example.diffviewer.core.domain.RecentRepository
import org.json.JSONArray
import org.json.JSONObject

class SharedPreferencesConnectionSettingsRepository(
    private val sharedPreferences: SharedPreferences,
    private val secretCipher: SecretCipher,
) : ConnectionSettingsRepository {
    override fun loadConnectionSettings(): ConnectionSettings {
        return ConnectionSettings(
            endpoint = sharedPreferences.getString(ENDPOINT_KEY, DEFAULT_ENDPOINT) ?: DEFAULT_ENDPOINT,
            token = sharedPreferences.getString(TOKEN_KEY, "") ?: "",
            githubRepositoryUrl = sharedPreferences.getString(GITHUB_REPOSITORY_URL_KEY, DEFAULT_GITHUB_URL)
                ?: DEFAULT_GITHUB_URL,
            githubToken = loadGitHubToken(),
            localRepositoryPath = sharedPreferences.getString(LOCAL_REPOSITORY_PATH_KEY, "") ?: "",
            repositorySource = runCatching {
                RepositorySource.valueOf(
                    sharedPreferences.getString(REPOSITORY_SOURCE_KEY, RepositorySource.TERMUX.name)
                        ?: RepositorySource.TERMUX.name
                )
            }.getOrDefault(RepositorySource.TERMUX),
        )
    }

    override fun saveConnectionSettings(connectionSettings: ConnectionSettings) {
        val editor = sharedPreferences.edit()
            .putString(ENDPOINT_KEY, connectionSettings.endpoint)
            .putString(TOKEN_KEY, connectionSettings.token)
            .putString(GITHUB_REPOSITORY_URL_KEY, connectionSettings.githubRepositoryUrl)
            .putString(LOCAL_REPOSITORY_PATH_KEY, connectionSettings.localRepositoryPath)
            .putString(REPOSITORY_SOURCE_KEY, connectionSettings.repositorySource.name)
        if (connectionSettings.githubToken.isBlank()) {
            editor.remove(GITHUB_TOKEN_ENCRYPTED_KEY)
        } else {
            editor.putString(
                GITHUB_TOKEN_ENCRYPTED_KEY,
                secretCipher.encrypt(connectionSettings.githubToken),
            )
        }
        editor.apply()
    }

    override fun loadRecentRepositories(): List<RecentRepository> {
        val json = sharedPreferences.getString(RECENT_REPOSITORIES_KEY, null) ?: return emptyList()
        return runCatching {
            val jsonArray = JSONArray(json)
            List(jsonArray.length()) { index ->
                val item = jsonArray.getJSONObject(index)
                RecentRepository(
                    source = RepositorySource.valueOf(item.getString("source")),
                    name = item.getString("name"),
                    location = item.getString("location"),
                )
            }
        }.getOrDefault(emptyList())
    }

    override fun saveRecentRepositories(recentRepositoryItems: List<RecentRepository>) {
        val jsonArray = JSONArray()
        recentRepositoryItems.forEach { item ->
            jsonArray.put(JSONObject().put("source", item.source.name).put("name", item.name).put("location", item.location))
        }
        sharedPreferences.edit().putString(RECENT_REPOSITORIES_KEY, jsonArray.toString()).apply()
    }

    private fun loadGitHubToken(): String {
        val encryptedToken = sharedPreferences.getString(GITHUB_TOKEN_ENCRYPTED_KEY, null) ?: return ""
        return runCatching { secretCipher.decrypt(encryptedToken) }.getOrElse {
            sharedPreferences.edit().remove(GITHUB_TOKEN_ENCRYPTED_KEY).apply()
            ""
        }
    }

    private companion object {
        const val ENDPOINT_KEY = "helper_endpoint"
        const val TOKEN_KEY = "helper_token"
        const val DEFAULT_ENDPOINT = "http://127.0.0.1:8765"
        const val GITHUB_REPOSITORY_URL_KEY = "github_repository_url"
        const val GITHUB_TOKEN_ENCRYPTED_KEY = "github_token_encrypted"
        const val LOCAL_REPOSITORY_PATH_KEY = "local_repository_path"
        const val REPOSITORY_SOURCE_KEY = "repository_source"
        const val DEFAULT_GITHUB_URL = "https://github.com/yyasutakeee/diff-viewer-android"
        const val RECENT_REPOSITORIES_KEY = "recent_repositories"
    }
}

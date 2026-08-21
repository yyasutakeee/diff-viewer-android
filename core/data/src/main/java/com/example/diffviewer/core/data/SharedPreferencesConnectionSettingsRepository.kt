package com.example.diffviewer.core.data

import android.content.SharedPreferences
import com.example.diffviewer.core.domain.ConnectionSettings
import com.example.diffviewer.core.domain.ConnectionSettingsRepository

class SharedPreferencesConnectionSettingsRepository(
    private val sharedPreferences: SharedPreferences,
) : ConnectionSettingsRepository {
    override fun loadConnectionSettings(): ConnectionSettings {
        return ConnectionSettings(
            endpoint = sharedPreferences.getString(ENDPOINT_KEY, DEFAULT_ENDPOINT) ?: DEFAULT_ENDPOINT,
            token = sharedPreferences.getString(TOKEN_KEY, "") ?: "",
        )
    }

    override fun saveConnectionSettings(connectionSettings: ConnectionSettings) {
        sharedPreferences.edit()
            .putString(ENDPOINT_KEY, connectionSettings.endpoint)
            .putString(TOKEN_KEY, connectionSettings.token)
            .apply()
    }

    private companion object {
        const val ENDPOINT_KEY = "helper_endpoint"
        const val TOKEN_KEY = "helper_token"
        const val DEFAULT_ENDPOINT = "http://127.0.0.1:8765"
    }
}

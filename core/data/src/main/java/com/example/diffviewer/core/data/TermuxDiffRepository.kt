package com.example.diffviewer.core.data

import com.example.diffviewer.core.domain.DiffRepository
import com.example.diffviewer.core.domain.RepositoryDiff
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

class TermuxDiffRepository : DiffRepository {
    override suspend fun fetchRepositoryDiff(endpoint: String, token: String): RepositoryDiff {
        return withContext(Dispatchers.IO) {
            fetchRepositoryDiffOnIoDispatcher(endpoint, token)
        }
    }

    private fun fetchRepositoryDiffOnIoDispatcher(endpoint: String, token: String): RepositoryDiff {
        val requestUrl = URL("${endpoint.trimEnd('/')}/api/v1/diff")
        if (requestUrl.protocol != "http" || requestUrl.host !in ALLOWED_HOSTS) {
            throw IOException("ヘルパーURLには127.0.0.1またはlocalhostだけを指定できます")
        }
        val connection = requestUrl.openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "GET"
            connection.connectTimeout = CONNECT_TIMEOUT_MILLISECONDS
            connection.readTimeout = READ_TIMEOUT_MILLISECONDS
            connection.setRequestProperty("Authorization", "Bearer $token")
            connection.setRequestProperty("Accept", "application/json")

            val responseCode = connection.responseCode
            val responseText = if (responseCode in 200..299) {
                connection.inputStream.bufferedReader().use { reader -> reader.readText() }
            } else {
                connection.errorStream?.bufferedReader()?.use { reader -> reader.readText() }.orEmpty()
            }
            if (responseCode !in 200..299) {
                val serverMessage = runCatching {
                    JSONObject(responseText).optString("error")
                }.getOrDefault("")
                throw IOException(serverMessage.ifBlank { "ヘルパーがHTTP $responseCode を返しました" })
            }
            return parseRepositoryDiff(responseText)
        } catch (error: IOException) {
            throw IOException("Termuxヘルパーに接続できません: ${error.message}", error)
        } finally {
            connection.disconnect()
        }
    }

    private companion object {
        const val CONNECT_TIMEOUT_MILLISECONDS = 5_000
        const val READ_TIMEOUT_MILLISECONDS = 15_000
        val ALLOWED_HOSTS = setOf("127.0.0.1", "localhost")
    }
}

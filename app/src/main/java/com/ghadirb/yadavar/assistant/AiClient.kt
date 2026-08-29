package com.ghadirb.yadavar.assistant

import com.ghadirb.yadavar.utils.PreferencesManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

/**
 * Optional OpenAI-compatible chat (GapGPT / Liara / OpenAI). Works only when the user
 * pastes a key in Settings. The on-device NLP path does not need this.
 */
object AiClient {

    suspend fun chat(
        prefs: PreferencesManager,
        systemPrompt: String,
        userPrompt: String
    ): String? = withContext(Dispatchers.IO) {
        val key = prefs.aiApiKey.trim()
        if (key.isEmpty()) return@withContext null
        val base = prefs.aiBaseUrl.trim().trimEnd('/').ifBlank { "https://api.gapgpt.app/v1" }
        val model = prefs.aiModel.trim().ifBlank { "gpt-4o-mini" }
        try {
            val url = URL("$base/chat/completions")
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "POST"
            connection.setRequestProperty("Content-Type", "application/json")
            connection.setRequestProperty("Authorization", "Bearer $key")
            connection.doOutput = true
            connection.connectTimeout = 15000
            connection.readTimeout = 20000

            val body = JSONObject().apply {
                put("model", model)
                put("messages", JSONArray().apply {
                    put(JSONObject().put("role", "system").put("content", systemPrompt))
                    put(JSONObject().put("role", "user").put("content", userPrompt))
                })
                put("max_tokens", 400)
                put("temperature", 0.4)
            }
            OutputStreamWriter(connection.outputStream).use { it.write(body.toString()) }
            if (connection.responseCode != HttpURLConnection.HTTP_OK) return@withContext null
            val response = connection.inputStream.bufferedReader().use { it.readText() }
            JSONObject(response)
                .getJSONArray("choices")
                .getJSONObject(0)
                .getJSONObject("message")
                .getString("content")
                .trim()
        } catch (_: Exception) {
            null
        }
    }
}

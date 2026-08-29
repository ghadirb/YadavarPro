package com.ghadirb.yadavar.utils

import android.content.Context
import com.ghadirb.yadavar.models.AIProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

object AIHelper {

    private fun pickKey(context: Context, requireHostedQuota: Boolean = true): Pair<com.ghadirb.yadavar.models.APIKey, Boolean>? {
        val prefs = PreferencesManager(context)
        val allActive = prefs.getAPIKeys().filter { it.isActive && it.key.isNotBlank() }
        val personal = allActive.filterNot { it.isAutoProvisioned }
        val hosted = allActive.filter { it.isAutoProvisioned }
        val chosen = personal.firstOrNull { it.provider == AIProvider.GAPGPT }
            ?: personal.firstOrNull()
            ?: hosted.firstOrNull { it.provider == AIProvider.GAPGPT }
            ?: hosted.firstOrNull()
            ?: run {
                val pasted = prefs.aiApiKey.trim()
                if (pasted.isNotBlank()) {
                    return com.ghadirb.yadavar.models.APIKey(
                        provider = AIProvider.GAPGPT,
                        key = pasted,
                        baseUrl = prefs.aiBaseUrl.ifBlank { "https://api.gapgpt.app/v1" },
                        isActive = true,
                        isAutoProvisioned = false
                    ) to false
                }
                null
            }
        chosen ?: return null
        val hostedUse = chosen.isAutoProvisioned
        if (hostedUse && requireHostedQuota && !SubscriptionManager.canUseHostedAi(context)) return null
        return chosen to hostedUse
    }

    private fun baseUrlFor(key: com.ghadirb.yadavar.models.APIKey): String {
        if (!key.baseUrl.isNullOrBlank()) return key.baseUrl.trimEnd('/')
        return when (key.provider) {
            AIProvider.GAPGPT -> "https://api.gapgpt.app/v1"
            AIProvider.LIARA -> "https://ai.liara.ir/api/69467b6ba99a2016cac892e1/v1"
            else -> "https://api.openai.com/v1"
        }
    }

    private fun modelFor(baseUrl: String, prefs: PreferencesManager): String {
        val custom = prefs.aiModel.trim()
        if (custom.isNotBlank()) return custom
        return when {
            baseUrl.contains("gapgpt.app") -> "gpt-4o-mini"
            baseUrl.contains("liara.ir") -> "openai/gpt-4o-mini"
            else -> "gpt-4o-mini"
        }
    }

    suspend fun generateText(context: Context, systemPrompt: String, userPrompt: String, maxTokens: Int = 280): String? =
        withContext(Dispatchers.IO) {
            val picked = pickKey(context) ?: return@withContext null
            val (key, hosted) = picked
            try {
                val baseUrl = baseUrlFor(key)
                val url = URL("$baseUrl/chat/completions")
                val connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "POST"
                connection.setRequestProperty("Content-Type", "application/json")
                connection.setRequestProperty("Authorization", "Bearer ${key.key}")
                connection.doOutput = true
                connection.connectTimeout = 15000
                connection.readTimeout = 20000
                val body = JSONObject().apply {
                    put("model", modelFor(baseUrl, PreferencesManager(context)))
                    put("messages", JSONArray().apply {
                        put(JSONObject().put("role", "system").put("content", systemPrompt))
                        put(JSONObject().put("role", "user").put("content", userPrompt))
                    })
                    put("max_tokens", maxTokens)
                    put("temperature", 0.2)
                }
                OutputStreamWriter(connection.outputStream).use { it.write(body.toString()) }
                if (connection.responseCode != HttpURLConnection.HTTP_OK) return@withContext null
                if (hosted) SubscriptionManager.recordAiUsage(context)
                JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
                    .getJSONArray("choices").getJSONObject(0).getJSONObject("message")
                    .getString("content").trim()
            } catch (_: Exception) {
                null
            }
        }

    suspend fun synthesizeSpeech(context: Context, text: String): File? = withContext(Dispatchers.IO) {
        val picked = pickKey(context) ?: return@withContext null
        val (key, hosted) = picked
        val result = synthesizeWithModel(context, key.key, text, "gpt-4o-mini-tts")
            ?: synthesizeWithModel(context, key.key, text, "tts-1")
        if (result != null && hosted) SubscriptionManager.recordAiUsage(context)
        result
    }

    private fun synthesizeWithModel(context: Context, apiKey: String, text: String, model: String): File? {
        return try {
            val url = URL("https://api.gapgpt.app/v1/audio/speech")
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "POST"
            connection.doOutput = true
            connection.connectTimeout = 15000
            connection.readTimeout = 20000
            connection.setRequestProperty("Authorization", "Bearer $apiKey")
            connection.setRequestProperty("Content-Type", "application/json")
            val body = JSONObject().apply {
                put("model", model)
                put("voice", "alloy")
                put("input", text.take(220))
            }
            OutputStreamWriter(connection.outputStream).use { it.write(body.toString()) }
            if (connection.responseCode != HttpURLConnection.HTTP_OK) return null
            val outFile = File(context.cacheDir, "reminder_tts_${System.currentTimeMillis()}.mp3")
            connection.inputStream.use { input -> outFile.outputStream().use { input.copyTo(it) } }
            if (outFile.length() > 0) outFile else null
        } catch (_: Exception) {
            null
        }
    }
}

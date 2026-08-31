package com.ghadirb.yadavar.utils

import android.content.Context
import android.util.Base64
import com.ghadirb.yadavar.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * Single client for the server-side AI proxy. Provider credentials are deliberately
 * absent from the APK; the server selects the provider and injects its secret key.
 */
object AIBackendClient {
    private const val CONNECT_TIMEOUT_MS = 20_000
    private const val READ_TIMEOUT_MS = 60_000

    private fun endpoint(path: String): String {
        val root = BuildConfig.AI_BACKEND_URL.trimEnd('/')
        return if (root.contains("script.google.com", ignoreCase = true)) {
            val route = when (path) {
                "/ai/chat" -> "aiChat"
                "/ai/stt" -> "aiStt"
                "/ai/tts" -> "aiTts"
                else -> path.trim('/').replace('/', '_')
            }
            root + if (root.contains("?")) "&path=$route" else "?path=$route"
        } else {
            root + path
        }
    }

    private fun open(context: Context, path: String): HttpURLConnection? {
        val root = BuildConfig.AI_BACKEND_URL.trimEnd('/')
        if (root.isBlank() || root.contains("CHANGE-ME", ignoreCase = true)) return null
        return (URL(endpoint(path)).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            doOutput = true
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Accept", "application/json")
            setRequestProperty("X-Yadavar-Device-Id", PreferencesManager(context).getOrCreateDeviceId())
        }
    }

    /** Returns the assistant's reply text, or null on failure. [lastError] is set to the
     *  server's JSON "error" field (or a transport-level description) on failure only, so
     *  callers can surface something more useful than a generic "couldn't understand" - very
     *  handy while a freshly-deployed backend is still being debugged. */
    var lastError: String? = null
        private set

    suspend fun chat(
        context: Context,
        messages: JSONArray,
        maxTokens: Int = 500,
        temperature: Double = 0.7
    ): String? = withContext(Dispatchers.IO) {
        lastError = null
        runCatching {
            val connection = open(context, "/ai/chat") ?: run { lastError = "AI_BACKEND_URL not configured"; return@runCatching null }
            val body = JSONObject()
                .put("deviceId", PreferencesManager(context).getOrCreateDeviceId())
                .put("messages", messages)
                .put("maxTokens", maxTokens)
                .put("temperature", temperature)
            connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val raw = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (code !in 200..299) { lastError = "HTTP $code: $raw"; return@runCatching null }
            val json = JSONObject(raw)
            val text = json.optString("text").trim()
            if (text.isBlank()) {
                lastError = json.optString("error").ifBlank { "empty response" }
                null
            } else text
        }.onFailure { lastError = it.message ?: it.javaClass.simpleName }.getOrNull()
    }

    suspend fun transcribe(context: Context, audioFile: File): String? = withContext(Dispatchers.IO) {
        runCatching {
            val connection = open(context, "/ai/stt") ?: return@runCatching null
            val encoded = Base64.encodeToString(audioFile.readBytes(), Base64.NO_WRAP)
            val body = JSONObject()
                .put("deviceId", PreferencesManager(context).getOrCreateDeviceId())
                .put("audioBase64", encoded)
            connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            if (connection.responseCode !in 200..299) return@runCatching null
            JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
                .optString("text").trim().takeIf { it.isNotBlank() }
        }.getOrNull()
    }

    suspend fun synthesize(context: Context, text: String): File? = withContext(Dispatchers.IO) {
        runCatching {
            val connection = open(context, "/ai/tts") ?: return@runCatching null
            val body = JSONObject()
                .put("deviceId", PreferencesManager(context).getOrCreateDeviceId())
                .put("text", text)
            connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            if (connection.responseCode !in 200..299) return@runCatching null
            val encoded = JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
                .optString("audioBase64").takeIf { it.isNotBlank() } ?: return@runCatching null
            File(context.cacheDir, "reminder_tts_${System.currentTimeMillis()}.mp3").also {
                it.writeBytes(Base64.decode(encoded, Base64.DEFAULT))
                if (it.length() == 0L) it.delete()
            }.takeIf { it.exists() && it.length() > 0 }
        }.getOrNull()
    }
}

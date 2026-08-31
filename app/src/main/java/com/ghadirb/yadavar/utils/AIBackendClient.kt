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
import java.net.URLEncoder

/**
 * Single client for the server-side AI proxy. Provider credentials stay in Apps Script
 * Script Properties; the APK only knows the /exec URL.
 *
 * Google Apps Script Web Apps drop JSON POST bodies across the
 * script.google.com → script.googleusercontent.com redirect. Query-string GET
 * is the only path that is reliable (status already worked this way). Chat and
 * TTS therefore go as GET; STT still POSTs because audio is too large for a URL.
 */
object AIBackendClient {
    private const val CONNECT_TIMEOUT_MS = 15_000
    private const val READ_TIMEOUT_MS = 35_000
    private const val CHAT_READ_TIMEOUT_MS = 20_000
    private const val TTS_READ_TIMEOUT_MS = 35_000
    private const val MAX_REDIRECTS = 5

    var lastError: String? = null
        private set

    private fun routeFor(path: String): String = when (path) {
        "/ai/chat" -> "aiChat"
        "/ai/stt" -> "aiStt"
        "/ai/tts" -> "aiTts"
        "/ai/smartAlert" -> "aiSmartAlert"
        else -> path.trim('/').replace('/', '_')
    }

    private fun rootUrl(): String = BuildConfig.AI_BACKEND_URL.trimEnd('/')

    private fun isAppsScript(): Boolean =
        rootUrl().contains("script.google.com", ignoreCase = true)

    private fun configured(): Boolean {
        val root = rootUrl()
        return root.isNotBlank() && !root.contains("CHANGE-ME", ignoreCase = true)
    }

    private fun encode(value: String): String = URLEncoder.encode(value, "UTF-8")

    private fun read(connection: HttpURLConnection): Pair<Int, String> {
        val code = connection.responseCode
        val stream = if (code in 200..299) connection.inputStream else connection.errorStream
        val raw = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
        connection.disconnect()
        return code to raw
    }

    private fun getJson(
        context: Context,
        path: String,
        extra: Map<String, String>,
        readTimeoutMs: Int = READ_TIMEOUT_MS
    ): Pair<Int, String> {
        if (!configured()) return -1 to "AI_BACKEND_URL not configured"
        val deviceId = PreferencesManager(context).getOrCreateDeviceId()
        val params = LinkedHashMap<String, String>()
        params["path"] = routeFor(path)
        params["deviceId"] = deviceId
        extra.forEach { (k, v) -> params[k] = v }
        val qs = params.entries.joinToString("&") { "${encode(it.key)}=${encode(it.value)}" }
        val root = rootUrl()
        val url = root + (if (root.contains("?")) "&" else "?") + qs
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            instanceFollowRedirects = true
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = readTimeoutMs
            setRequestProperty("Accept", "application/json")
            setRequestProperty("X-Yadavar-Device-Id", deviceId)
        }
        return read(connection)
    }

    /**
     * POST once without auto-follow, then GET the Location. Used only for STT
     * (payload too large for a query string). Chat/TTS must not use this against
     * Apps Script — the JSON body never reaches doPost.
     */
    private fun postJson(context: Context, path: String, body: JSONObject): Pair<Int, String> {
        if (!configured()) return -1 to "AI_BACKEND_URL not configured"
        val root = rootUrl()
        val route = routeFor(path)
        val deviceId = PreferencesManager(context).getOrCreateDeviceId()
        var url = if (isAppsScript()) {
            root + (if (root.contains("?")) "&" else "?") + "path=$route&deviceId=${encode(deviceId)}"
        } else {
            root + path
        }
        var bodyBytes: ByteArray? = body.toString().toByteArray(Charsets.UTF_8)
        var method = "POST"
        repeat(MAX_REDIRECTS) {
            val connection = (URL(url).openConnection() as HttpURLConnection).apply {
                instanceFollowRedirects = false
                requestMethod = method
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = READ_TIMEOUT_MS
                setRequestProperty("Accept", "application/json")
                setRequestProperty("X-Yadavar-Device-Id", deviceId)
                bodyBytes?.let {
                    doOutput = true
                    setRequestProperty("Content-Type", "application/json")
                    outputStream.use { out -> out.write(it) }
                }
            }
            val code = connection.responseCode
            if (code in 300..399) {
                val location = connection.getHeaderField("Location")
                    ?: return code to "redirect with no Location"
                connection.disconnect()
                url = location
                bodyBytes = null
                method = "GET"
                return@repeat
            }
            return read(connection)
        }
        return -1 to "too many redirects"
    }

    private fun parseText(code: Int, raw: String): String? {
        if (code !in 200..299) {
            lastError = "HTTP $code: ${raw.take(180)}"
            return null
        }
        val json = runCatching { JSONObject(raw) }.getOrNull()
        if (json == null) {
            lastError = raw.take(180).ifBlank { "empty response" }
            return null
        }
        val text = json.optString("text").trim()
        if (text.isNotBlank()) return text
        lastError = explain(json.optString("error").ifBlank { "empty response" })
        return null
    }

    private fun explain(error: String): String = when {
        error == "unknown_path" ->
            "unknown_path — نسخهٔ دیپلوی‌شده Code.gs قدیمی است. فایل جدید را جایگزین کنید و از Manage deployments نسخهٔ جدید Deploy کنید."
        error == "ai_provider_not_configured" ->
            "ai_provider_not_configured — در Script Properties مقدار GAPGPT_API_KEY را بگذارید."
        error == "ai_daily_limit_reached" ->
            "سهمیه روزانه ابری تمام شده است."
        else -> error
    }

    suspend fun chat(
        context: Context,
        messages: JSONArray,
        maxTokens: Int = 500,
        temperature: Double = 0.7
    ): String? = withContext(Dispatchers.IO) {
        lastError = null
        runCatching {
            val (code, raw) = if (isAppsScript()) {
                getJson(
                    context,
                    "/ai/chat",
                    mapOf(
                        "messages" to messages.toString(),
                        "maxTokens" to maxTokens.toString(),
                        "temperature" to temperature.toString()
                    ),
                    CHAT_READ_TIMEOUT_MS
                )
            } else {
                val body = JSONObject()
                    .put("path", routeFor("/ai/chat"))
                    .put("deviceId", PreferencesManager(context).getOrCreateDeviceId())
                    .put("messages", messages)
                    .put("maxTokens", maxTokens)
                    .put("temperature", temperature)
                postJson(context, "/ai/chat", body)
            }
            parseText(code, raw)
        }.onFailure { lastError = it.message ?: it.javaClass.simpleName }.getOrNull()
    }

    suspend fun transcribe(context: Context, audioFile: File): String? = withContext(Dispatchers.IO) {
        lastError = null
        runCatching {
            val encoded = Base64.encodeToString(audioFile.readBytes(), Base64.NO_WRAP)
            val body = JSONObject()
                .put("path", routeFor("/ai/stt"))
                .put("deviceId", PreferencesManager(context).getOrCreateDeviceId())
                .put("audioBase64", encoded)
            val (code, raw) = postJson(context, "/ai/stt", body)
            parseText(code, raw)
        }.onFailure { lastError = it.message ?: it.javaClass.simpleName }.getOrNull()
    }

    suspend fun synthesize(context: Context, text: String): File? = withContext(Dispatchers.IO) {
        lastError = null
        runCatching {
            val (code, raw) = if (isAppsScript()) {
                getJson(context, "/ai/tts", mapOf("text" to text.take(220)), TTS_READ_TIMEOUT_MS)
            } else {
                val body = JSONObject()
                    .put("path", routeFor("/ai/tts"))
                    .put("deviceId", PreferencesManager(context).getOrCreateDeviceId())
                    .put("text", text)
                postJson(context, "/ai/tts", body)
            }
            if (code !in 200..299) {
                lastError = "HTTP $code: ${raw.take(180)}"
                return@runCatching null
            }
            val json = JSONObject(raw)
            val encoded = json.optString("audioBase64")
            if (encoded.isBlank()) {
                lastError = explain(json.optString("error").ifBlank { "empty response" })
                return@runCatching null
            }
            val mime = json.optString("mimeType").lowercase()
            val ext = if (mime.contains("wav")) "wav" else "mp3"
            File(context.cacheDir, "reminder_tts_${System.currentTimeMillis()}.$ext").also {
                it.writeBytes(Base64.decode(encoded, Base64.DEFAULT))
                if (it.length() < 64L) it.delete()
            }.takeIf { it.exists() && it.length() >= 64L }
        }.onFailure { lastError = it.message ?: it.javaClass.simpleName }.getOrNull()
    }

    data class SmartAlertAudio(val text: String, val file: File)

    /**
     * One round-trip: rewrite the reminder as a spoken Persian sentence and return MP3.
     * Returns null if the deployed script is still v4 (unknown_path) so the client can
     * fall back to chat + tts separately.
     */
    suspend fun smartAlert(context: Context, title: String, description: String): SmartAlertAudio? =
        withContext(Dispatchers.IO) {
            lastError = null
            runCatching {
                val extra = mapOf(
                    "title" to title.take(120),
                    "description" to description.take(160)
                )
                val (code, raw) = if (isAppsScript()) {
                    getJson(context, "/ai/smartAlert", extra)
                } else {
                    val body = JSONObject()
                        .put("path", routeFor("/ai/smartAlert"))
                        .put("deviceId", PreferencesManager(context).getOrCreateDeviceId())
                        .put("title", title.take(120))
                        .put("description", description.take(160))
                    postJson(context, "/ai/smartAlert", body)
                }
                if (code !in 200..299) {
                    lastError = "HTTP $code: ${raw.take(180)}"
                    return@runCatching null
                }
                val json = JSONObject(raw)
                val err = json.optString("error")
                if (err == "unknown_path") {
                    lastError = "unknown_path"
                    return@runCatching null
                }
                val spoken = json.optString("text").trim()
                val encoded = json.optString("audioBase64")
                if (encoded.isBlank()) {
                    lastError = explain(err.ifBlank { "empty response" })
                    return@runCatching null
                }
                val file = File(context.cacheDir, "reminder_tts_${System.currentTimeMillis()}.mp3").also {
                    it.writeBytes(Base64.decode(encoded, Base64.DEFAULT))
                    if (it.length() < 64L) it.delete()
                }
                if (!file.exists() || file.length() < 64L) return@runCatching null
                SmartAlertAudio(spoken.ifBlank { title }, file)
            }.onFailure { lastError = it.message ?: it.javaClass.simpleName }.getOrNull()
        }
}

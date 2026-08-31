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
    private const val MAX_REDIRECTS = 5

    private fun routeFor(path: String): String = when (path) {
        "/ai/chat" -> "aiChat"
        "/ai/stt" -> "aiStt"
        "/ai/tts" -> "aiTts"
        else -> path.trim('/').replace('/', '_')
    }

    private fun endpoint(path: String): String {
        val root = BuildConfig.AI_BACKEND_URL.trimEnd('/')
        return if (root.contains("script.google.com", ignoreCase = true)) {
            val route = routeFor(path)
            root + if (root.contains("?")) "&path=$route" else "?path=$route"
        } else {
            root + path
        }
    }

    /** Returns the assistant's reply text, or null on failure. [lastError] is set to the
     *  server's JSON "error" field (or a transport-level description) on failure only, so
     *  callers can surface something more useful than a generic "couldn't understand" - very
     *  handy while a freshly-deployed backend is still being debugged. */
    var lastError: String? = null
        private set

    /**
     * Apps Script Web Apps always answer with an HTTP redirect to a script.googleusercontent.com
     * URL that actually serves the response bytes - the script has ALREADY finished running
     * against the original request (query string + POST body) by the time that redirect is
     * issued. The problem: Java's HttpURLConnection auto-follows that redirect for us by
     * default, but on a POST it does NOT resend our JSON body to the redirect target - so the
     * follow-up request silently arrives as an empty-bodied request, and anything read from
     * that second response is garbage/unrelated to what we actually asked. This is exactly
     * why a bodyless GET (like /status) worked fine while a POST-with-JSON-body (/aiChat)
     * kept coming back "unknown_path": Code.gs correctly ran and answered on the FIRST
     * request, but we were reading the WRONG (redirected, bodyless) response.
     *
     * Fix: disable automatic redirect-following, send the POST exactly once, and if we get a
     * redirect back, GET the Location URL directly (no body needed there - the content is
     * already computed) and read that instead.
     */
    private fun postJson(context: Context, path: String, body: JSONObject): Pair<Int, String> {
        val root = BuildConfig.AI_BACKEND_URL.trimEnd('/')
        if (root.isBlank() || root.contains("CHANGE-ME", ignoreCase = true)) {
            return -1 to "AI_BACKEND_URL not configured"
        }
        var url = endpoint(path)
        var bodyBytes: ByteArray? = body.toString().toByteArray(Charsets.UTF_8)
        var method = "POST"
        repeat(MAX_REDIRECTS) {
            val connection = (URL(url).openConnection() as HttpURLConnection).apply {
                instanceFollowRedirects = false
                requestMethod = method
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = READ_TIMEOUT_MS
                setRequestProperty("Accept", "application/json")
                setRequestProperty("X-Yadavar-Device-Id", PreferencesManager(context).getOrCreateDeviceId())
                bodyBytes?.let {
                    doOutput = true
                    setRequestProperty("Content-Type", "application/json")
                    outputStream.use { out -> out.write(it) }
                }
            }
            val code = connection.responseCode
            if (code in 300..399) {
                val location = connection.getHeaderField("Location") ?: return code to "redirect with no Location"
                url = location
                bodyBytes = null // the script already ran on the first request; this hop just fetches the result
                method = "GET"
                return@repeat
            }
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val raw = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            return code to raw
        }
        return -1 to "too many redirects"
    }

    suspend fun chat(
        context: Context,
        messages: JSONArray,
        maxTokens: Int = 500,
        temperature: Double = 0.7
    ): String? = withContext(Dispatchers.IO) {
        lastError = null
        runCatching {
            val body = JSONObject()
                .put("path", routeFor("/ai/chat"))
                .put("deviceId", PreferencesManager(context).getOrCreateDeviceId())
                .put("messages", messages)
                .put("maxTokens", maxTokens)
                .put("temperature", temperature)
            val (code, raw) = postJson(context, "/ai/chat", body)
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
        lastError = null
        runCatching {
            val encoded = Base64.encodeToString(audioFile.readBytes(), Base64.NO_WRAP)
            val body = JSONObject()
                .put("path", routeFor("/ai/stt"))
                .put("deviceId", PreferencesManager(context).getOrCreateDeviceId())
                .put("audioBase64", encoded)
            val (code, raw) = postJson(context, "/ai/stt", body)
            if (code !in 200..299) { lastError = "HTTP $code: $raw"; return@runCatching null }
            val json = JSONObject(raw)
            val text = json.optString("text").trim()
            if (text.isBlank()) { lastError = json.optString("error").ifBlank { "empty response" }; null } else text
        }.onFailure { lastError = it.message ?: it.javaClass.simpleName }.getOrNull()
    }

    suspend fun synthesize(context: Context, text: String): File? = withContext(Dispatchers.IO) {
        lastError = null
        runCatching {
            val body = JSONObject()
                .put("path", routeFor("/ai/tts"))
                .put("deviceId", PreferencesManager(context).getOrCreateDeviceId())
                .put("text", text)
            val (code, raw) = postJson(context, "/ai/tts", body)
            if (code !in 200..299) { lastError = "HTTP $code: $raw"; return@runCatching null }
            val json = JSONObject(raw)
            val encoded = json.optString("audioBase64")
            if (encoded.isBlank()) { lastError = json.optString("error").ifBlank { "empty response" }; return@runCatching null }
            File(context.cacheDir, "reminder_tts_${System.currentTimeMillis()}.mp3").also {
                it.writeBytes(Base64.decode(encoded, Base64.DEFAULT))
                if (it.length() == 0L) it.delete()
            }.takeIf { it.exists() && it.length() > 0 }
        }.onFailure { lastError = it.message ?: it.javaClass.simpleName }.getOrNull()
    }
}

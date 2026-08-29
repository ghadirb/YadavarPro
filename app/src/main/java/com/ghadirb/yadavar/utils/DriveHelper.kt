package com.ghadirb.yadavar.utils

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

object DriveHelper {
    suspend fun downloadFromUrl(url: String): String = withContext(Dispatchers.IO) {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.connectTimeout = 20000
        connection.readTimeout = 20000
        connection.requestMethod = "GET"
        if (connection.responseCode != HttpURLConnection.HTTP_OK) {
            throw java.io.IOException("Download error: ${connection.responseCode}")
        }
        connection.inputStream.bufferedReader().use { it.readText() }
    }
}

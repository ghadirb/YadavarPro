package com.ghadirb.yadavar.utils

import android.content.Context
import android.util.Log
import com.ghadirb.yadavar.models.AIProvider
import com.ghadirb.yadavar.models.APIKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Same hosted-key pipeline as Maliar-Pro: encrypted file on a public URL, decrypted
 * with a shared password, then stored as auto-provisioned keys. Hosted keys are only
 * used when the user is premium or still has free quota — personal keys always win.
 */
object AutoProvisioningManager {
    private const val TAG = "AutoProvisioning"
    private const val DEFAULT_PASSWORD = "12345"
    private const val OLD_KEYS_URL = "https://abrehamrahi.ir/o/public/eUFcsXOX/"
    private const val GIST_KEYS_URL =
        "https://gist.githubusercontent.com/ghadirb/626a804df3009e49045a2948dad89fe5/raw/c93c06d1b2f38c65ee30f092c134a89998326d12/keys.txt"

    suspend fun autoProvision(context: Context): Result<List<APIKey>> = withContext(Dispatchers.IO) {
        try {
            val oldResult = tryLoadFromUrl(OLD_KEYS_URL, context)
            if (oldResult.isSuccess && hasRequiredKeys(oldResult.getOrThrow())) return@withContext oldResult
            val newResult = tryLoadFromUrl(GIST_KEYS_URL, context)
            if (newResult.isSuccess) return@withContext newResult
            if (oldResult.isSuccess && oldResult.getOrNull().orEmpty().isNotEmpty()) return@withContext oldResult
            Result.failure(Exception("No key sources responded"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private suspend fun tryLoadFromUrl(url: String, context: Context): Result<List<APIKey>> =
        withContext(Dispatchers.IO) {
            try {
                val encryptedData = DriveHelper.downloadFromUrl(url)
                if (encryptedData.isBlank()) return@withContext Result.failure(Exception("Keys file is empty"))
                val decryptedData = EncryptionHelper.decrypt(encryptedData, DEFAULT_PASSWORD)
                val parsed = parseAPIKeys(decryptedData)
                if (parsed.isEmpty()) return@withContext Result.failure(Exception("No valid keys found"))
                val processed = parsed.map { key ->
                    val defaultBase = when (key.provider) {
                        AIProvider.LIARA -> "https://ai.liara.ir/api/69467b6ba99a2016cac892e1/v1"
                        AIProvider.AIML -> "https://api.aimlapi.com/v1"
                        AIProvider.GAPGPT -> "https://api.gapgpt.app/v1"
                        AIProvider.OPENROUTER -> "https://openrouter.ai/api/v1"
                        AIProvider.OPENAI -> "https://api.openai.com/v1"
                        else -> key.baseUrl
                    }
                    key.copy(isActive = true, baseUrl = key.baseUrl ?: defaultBase, isAutoProvisioned = true)
                }
                val prefs = PreferencesManager(context)
                val personal = prefs.getAPIKeys().filterNot { it.isAutoProvisioned }
                prefs.saveAPIKeys(personal + processed)
                Result.success(processed)
            } catch (e: Exception) {
                Log.w(TAG, "load failed: ${e.message}")
                Result.failure(e)
            }
        }

    private fun hasRequiredKeys(keys: List<APIKey>) =
        keys.any { it.provider == AIProvider.GAPGPT }

    private fun parseAPIKeys(data: String): List<APIKey> {
        val keys = mutableListOf<APIKey>()
        data.lines().forEach { line ->
            val trimmed = line.trim()
            if (trimmed.isBlank() || trimmed.startsWith("#")) return@forEach
            val (provider, key, baseUrl) = parseKeyLine(trimmed)
            if (provider != null && key.isNotBlank()) {
                keys.add(APIKey(provider = provider, key = key, baseUrl = baseUrl, isActive = false))
            }
        }
        return keys
    }

    private fun parseKeyLine(line: String): Triple<AIProvider?, String, String?> {
        val parts = line.split(":", limit = 3).map { it.trim() }
        if (parts.size >= 2) {
            val provider = when (parts[0].lowercase()) {
                "liara" -> AIProvider.LIARA
                "openai", "gpt" -> AIProvider.OPENAI
                "openrouter" -> AIProvider.OPENROUTER
                "aiml", "aimlapi" -> AIProvider.AIML
                "gapgpt" -> AIProvider.GAPGPT
                else -> null
            }
            if (provider != null) return Triple(provider, parts.getOrNull(1).orEmpty(), parts.getOrNull(2))
        }
        return Triple(inferProvider(line), line, null)
    }

    private fun inferProvider(raw: String): AIProvider? {
        val lower = raw.trim().lowercase()
        if (lower.startsWith("sk-or")) return AIProvider.OPENROUTER
        if (raw.trim().startsWith("eyJ")) return AIProvider.LIARA
        if (lower.startsWith("sk-") && raw.length > 50) return AIProvider.GAPGPT
        if (lower.startsWith("sk-")) return AIProvider.OPENAI
        return null
    }
}

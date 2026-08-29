package com.ghadirb.yadavar.utils

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.ghadirb.yadavar.billing.StoreChannel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * Same billing model as Maliar-Pro, applied only to the reminder assistant:
 * reminders, calendar, quiet hours, widget and backup stay free forever.
 * Shared/cloud AI chat is metered unless the user is premium or pasted a personal key.
 *
 * Replace STATUS_URL / REQUEST_URL / VERIFY_STORE_URL with your deployed backend
 * (see /server). Until then, store purchases still grant locally so you can test
 * Cafe Bazaar / Myket products before the server is live.
 */
object SubscriptionManager {

    // TODO: replace CHANGE-ME with your deployed backend (Liara / Apps Script).
    // Apps Script example: the same exec URL with ?path=status|request|verifyStore
    const val STATUS_URL = "CHANGE-ME"
    const val REQUEST_URL = "CHANGE-ME"
    const val VERIFY_STORE_URL = "CHANGE-ME"

    const val FREE_AI_LIFETIME_LIMIT = 15
    const val EXPIRY_REMINDER_DAYS_BEFORE = 3

    enum class Plan(val apiValue: String, val days: Int, val label: String) {
        MONTHLY("monthly", 30, "اشتراک ماهانه"),
        YEARLY("yearly", 365, "اشتراک سالانه")
    }

    fun backendConfigured(): Boolean =
        STATUS_URL.isNotBlank() && !STATUS_URL.startsWith("CHANGE-ME")

    fun isPremium(context: Context): Boolean {
        return PreferencesManager(context).getPremiumUntil() > System.currentTimeMillis()
    }

    fun hasPersonalKey(context: Context): Boolean {
        return PreferencesManager(context).aiApiKey.isNotBlank()
    }

    fun remainingFreeLifetime(context: Context): Int {
        val used = PreferencesManager(context).getDailyAiCount()
        return (FREE_AI_LIFETIME_LIMIT - used).coerceAtLeast(0)
    }

    fun canUseAi(context: Context): Boolean {
        if (isPremium(context)) return true
        if (hasPersonalKey(context)) return true
        val hasQuota = remainingFreeLifetime(context) > 0
        if (!hasQuota) {
            val prefs = PreferencesManager(context)
            if (!prefs.hasNotifiedQuotaExhausted()) {
                prefs.setNotifiedQuotaExhausted(true)
                NotificationHelper.notifyQuotaExhausted(context)
            }
        }
        return hasQuota
    }

    fun recordAiUsage(context: Context) {
        if (isPremium(context) || hasPersonalKey(context)) return
        val prefs = PreferencesManager(context)
        prefs.setDailyAiCount(prefs.getDailyAiCount() + 1, "lifetime")
    }

    fun upgradeMessage(context: Context): String {
        return "⚠️ سهمیه رایگان اولیه ($FREE_AI_LIFETIME_LIMIT پیام ابری) تمام شده است.\n" +
            "یادآوری‌ها، سکوت شبانه و دستورهای روی دستگاه همچنان رایگان‌اند.\n" +
            "برای گفتگوی آزاد ابری:\n" +
            "• در تنظیمات کلید GapGPT / OpenAI خودت را بگذار\n" +
            "• یا اشتراک پریمیوم را از صفحهٔ اشتراک فعال کن"
    }

    private fun appendParam(url: String, key: String, value: String): String {
        val separator = if (url.contains("?")) "&" else "?"
        val encoded = URLEncoder.encode(value, "UTF-8")
        return "$url$separator$key=$encoded"
    }

    suspend fun refreshFromServer(context: Context): Boolean = withContext(Dispatchers.IO) {
        if (!backendConfigured()) return@withContext false
        try {
            val deviceId = PreferencesManager(context).getOrCreateDeviceId()
            val url = URL(appendParam(STATUS_URL, "deviceId", deviceId))
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 25000
            connection.readTimeout = 25000
            if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                val body = connection.inputStream.bufferedReader().use { it.readText() }
                val json = JSONObject(body)
                val premiumUntil = json.optLong("premiumUntil", 0L)
                val prefs = PreferencesManager(context)
                prefs.setPremiumUntil(premiumUntil)
                prefs.setLastSubscriptionCheck(System.currentTimeMillis())
                if (premiumUntil > System.currentTimeMillis()) {
                    prefs.setNotifiedQuotaExhausted(false)
                    scheduleExpiryReminder(context, premiumUntil)
                }
                true
            } else false
        } catch (e: Exception) {
            android.util.Log.w("SubscriptionManager", "refreshFromServer failed: ${e.message}")
            false
        }
    }

    suspend fun requestPayment(context: Context, plan: Plan): String? = withContext(Dispatchers.IO) {
        if (!backendConfigured()) return@withContext null
        try {
            val deviceId = PreferencesManager(context).getOrCreateDeviceId()
            var url = appendParam(REQUEST_URL, "deviceId", deviceId)
            url = appendParam(url, "plan", plan.apiValue)
            val connection = URL(url).openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 25000
            connection.readTimeout = 25000
            if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                val response = connection.inputStream.bufferedReader().use { it.readText() }
                JSONObject(response).optString("paymentUrl").takeIf { it.isNotBlank() }
            } else null
        } catch (e: Exception) {
            android.util.Log.w("SubscriptionManager", "requestPayment failed: ${e.message}")
            null
        }
    }

    fun detectStoreChannel(context: Context): StoreChannel = StoreChannel.current()

    suspend fun verifyStorePurchase(
        context: Context,
        channel: StoreChannel,
        plan: Plan,
        purchaseToken: String
    ): Boolean = withContext(Dispatchers.IO) {
        if (!backendConfigured()) {
            // Local grant so Cafe Bazaar / Myket product testing works before the
            // verification server is deployed. Replace CHANGE-ME URLs before public release.
            android.util.Log.w("SubscriptionManager", "Backend URL is CHANGE-ME; granting premium locally")
            grantLocally(context, plan, channel)
            return@withContext true
        }
        try {
            val deviceId = PreferencesManager(context).getOrCreateDeviceId()
            var url = appendParam(VERIFY_STORE_URL, "deviceId", deviceId)
            url = appendParam(url, "plan", plan.apiValue)
            url = appendParam(url, "channel", channel.apiValue)
            url = appendParam(url, "purchaseToken", purchaseToken)
            val connection = URL(url).openConnection() as HttpURLConnection
            connection.requestMethod = "POST"
            connection.connectTimeout = 15000
            connection.readTimeout = 15000
            if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                val body = connection.inputStream.bufferedReader().use { it.readText() }
                val json = JSONObject(body)
                val premiumUntil = json.optLong("premiumUntil", 0L)
                val prefs = PreferencesManager(context)
                prefs.setPremiumUntil(premiumUntil)
                prefs.setLastStoreChannel(channel.apiValue)
                val verified = json.optBoolean("verified", premiumUntil > System.currentTimeMillis())
                if (verified && premiumUntil > System.currentTimeMillis()) {
                    prefs.setNotifiedQuotaExhausted(false)
                    scheduleExpiryReminder(context, premiumUntil)
                }
                verified
            } else false
        } catch (e: Exception) {
            android.util.Log.w("SubscriptionManager", "verifyStorePurchase failed: ${e.message}")
            false
        }
    }

    private fun grantLocally(context: Context, plan: Plan, channel: StoreChannel) {
        val prefs = PreferencesManager(context)
        val current = prefs.getPremiumUntil()
        val base = maxOf(current, System.currentTimeMillis())
        val until = base + plan.days * 24L * 60 * 60 * 1000
        prefs.setPremiumUntil(until)
        prefs.setLastStoreChannel(channel.apiValue)
        prefs.setNotifiedQuotaExhausted(false)
        scheduleExpiryReminder(context, until)
    }

    fun scheduleExpiryReminder(context: Context, premiumUntil: Long) {
        val prefs = PreferencesManager(context)
        if (prefs.getExpiryReminderScheduledFor() == premiumUntil) return
        val fireAt = premiumUntil - EXPIRY_REMINDER_DAYS_BEFORE * 24 * 60 * 60 * 1000L
        val delayMs = (fireAt - System.currentTimeMillis()).coerceAtLeast(0L)
        val request = OneTimeWorkRequestBuilder<SubscriptionReminderWorker>()
            .setInitialDelay(delayMs, TimeUnit.MILLISECONDS)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            "subscription_expiry_reminder",
            ExistingWorkPolicy.REPLACE,
            request
        )
        prefs.setExpiryReminderScheduledFor(premiumUntil)
    }

    fun premiumExpiryLabel(context: Context): String? {
        val until = PreferencesManager(context).getPremiumUntil()
        if (until <= System.currentTimeMillis()) return null
        val formatted = SimpleDateFormat("yyyy/MM/dd", Locale.US).format(Date(until))
        return "اشتراک پریمیوم شما تا $formatted فعال است"
    }
}

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
 * Free forever: reminders, jalali calendar, quiet hours, widget, backup, on-device NLP.
 * Metered (hosted GapGPT key): reminder-only cloud parse + smart TTS.
 * Personal key: unlimited hosted features at the user's own cost.
 */
object SubscriptionManager {

    // Google Apps Script Web App (server/apps-script/Code.gs), deployed by the app owner.
    // All three point at the SAME /exec URL with a different "path" query param, which
    // routeRequest_() in Code.gs reads to decide which handler to run.
    private const val AI_BACKEND_BASE =
        "https://script.google.com/macros/s/AKfycbz9aQcHMVqlcqc3qynKdDWjh-rFGUAbRs0-1OrGZhX4JiMGRBwRcA7REDpX7FKy0OP8jw/exec"
    const val STATUS_URL = "$AI_BACKEND_BASE?path=status"
    const val REQUEST_URL = "$AI_BACKEND_BASE?path=request"
    const val VERIFY_STORE_URL = "$AI_BACKEND_BASE?path=verifyStore"

    const val FREE_AI_LIFETIME_LIMIT = 15
    const val EXPIRY_REMINDER_DAYS_BEFORE = 3

    enum class Plan(val apiValue: String, val days: Int, val label: String) {
        MONTHLY("monthly", 30, "اشتراک ماهانه"),
        YEARLY("yearly", 365, "اشتراک سالانه")
    }

    fun isPremium(context: Context): Boolean {
        return PreferencesManager(context).getPremiumUntil() > System.currentTimeMillis()
    }

    fun hasPersonalKey(context: Context): Boolean {
        val prefs = PreferencesManager(context)
        if (prefs.aiApiKey.isNotBlank()) return true
        return prefs.getAPIKeys().any { it.isActive && !it.isAutoProvisioned && it.key.isNotBlank() }
    }

    fun remainingFreeLifetime(context: Context): Int {
        val used = PreferencesManager(context).getDailyAiCount()
        return (FREE_AI_LIFETIME_LIMIT - used).coerceAtLeast(0)
    }

    fun canUseHostedAi(context: Context): Boolean {
        if (isPremium(context)) return true
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

    fun canUseAi(context: Context): Boolean {
        if (hasPersonalKey(context)) return true
        return canUseHostedAi(context)
    }

    fun recordAiUsage(context: Context) {
        if (isPremium(context) || hasPersonalKey(context)) return
        val prefs = PreferencesManager(context)
        prefs.setDailyAiCount(prefs.getDailyAiCount() + 1, "lifetime")
    }

    fun upgradeMessage(context: Context): String {
        return "سهمیه رایگان ابری ($FREE_AI_LIFETIME_LIMIT بار) تمام شده است.\n" +
            "یادآوری، دستور فارسی روی دستگاه، سکوت شبانه و ویجت رایگان می‌مانند.\n" +
            "چت ابری و هشدار هوشمند گفتاری:\n" +
            "• اشتراک پریمیوم از صفحه اشتراک\n" +
            "• یا کلید GapGPT خودت در تنظیمات"
    }

    private fun appendParam(url: String, key: String, value: String): String {
        val separator = if (url.contains("?")) "&" else "?"
        val encoded = URLEncoder.encode(value, "UTF-8")
        return "$url$separator$key=$encoded"
    }

    suspend fun refreshFromServer(context: Context): Boolean = withContext(Dispatchers.IO) {
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

    /**
     * Sends a completed Bazaar/Myket in-app-purchase token to the backend so it can be
     * verified server-to-server against Bazaar's/Myket's own purchase-verification API
     * before any premium days are granted - never grant premium purely because the SDK
     * callback on-device said "success", since that response can be spoofed. If the
     * backend URL isn't configured yet (still "CHANGE-ME"), the request below simply fails
     * and this returns false - no local fallback grants premium without a server check.
     */
    suspend fun verifyStorePurchase(
        context: Context,
        channel: StoreChannel,
        plan: Plan,
        purchaseToken: String
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val deviceId = PreferencesManager(context).getOrCreateDeviceId()
            var url = appendParam(VERIFY_STORE_URL, "deviceId", deviceId)
            url = appendParam(url, "plan", plan.apiValue)
            url = appendParam(url, "channel", channel.apiValue)
            url = appendParam(url, "purchaseToken", purchaseToken)
            val connection = URL(url).openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
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

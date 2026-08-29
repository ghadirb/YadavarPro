package com.ghadirb.yadavar.utils

import android.content.Context

enum class QuietMode { SILENT, PRIORITY, VIBRATE }

class PreferencesManager(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    var quietHoursEnabled: Boolean
        get() = prefs.getBoolean(KEY_QH_ENABLED, false)
        set(value) = prefs.edit().putBoolean(KEY_QH_ENABLED, value).apply()

    var quietStartMinutes: Int
        get() = prefs.getInt(KEY_QH_START, 23 * 60)
        set(value) = prefs.edit().putInt(KEY_QH_START, value).apply()

    var quietEndMinutes: Int
        get() = prefs.getInt(KEY_QH_END, 7 * 60)
        set(value) = prefs.edit().putInt(KEY_QH_END, value).apply()

    var quietMode: QuietMode
        get() = QuietMode.entries.getOrElse(prefs.getInt(KEY_QH_MODE, 0)) { QuietMode.SILENT }
        set(value) = prefs.edit().putInt(KEY_QH_MODE, value.ordinal).apply()

    var allowCriticalInQuiet: Boolean
        get() = prefs.getBoolean(KEY_QH_CRITICAL, true)
        set(value) = prefs.edit().putBoolean(KEY_QH_CRITICAL, value).apply()

    var applySystemDnd: Boolean
        get() = prefs.getBoolean(KEY_QH_SYSTEM, false)
        set(value) = prefs.edit().putBoolean(KEY_QH_SYSTEM, value).apply()

    var quietHoursAnnounce: Boolean
        get() = prefs.getBoolean(KEY_QH_ANNOUNCE, true)
        set(value) = prefs.edit().putBoolean(KEY_QH_ANNOUNCE, value).apply()

    var focusModeEnabled: Boolean
        get() = prefs.getBoolean(KEY_FOCUS, false)
        set(value) = prefs.edit().putBoolean(KEY_FOCUS, value).apply()

    var groupByCategory: Boolean
        get() = prefs.getBoolean(KEY_GROUP, true)
        set(value) = prefs.edit().putBoolean(KEY_GROUP, value).apply()

    var aiApiKey: String
        get() = prefs.getString(KEY_AI_KEY, "").orEmpty()
        set(value) = prefs.edit().putString(KEY_AI_KEY, value).apply()

    var aiBaseUrl: String
        get() = prefs.getString(KEY_AI_URL, "https://api.gapgpt.app/v1").orEmpty()
        set(value) = prefs.edit().putString(KEY_AI_URL, value).apply()

    var aiModel: String
        get() = prefs.getString(KEY_AI_MODEL, "gpt-4o-mini").orEmpty()
        set(value) = prefs.edit().putString(KEY_AI_MODEL, value).apply()

    fun formatMinutes(total: Int): String {
        val h = (total / 60) % 24
        val m = total % 60
        return String.format("%02d:%02d", h, m)
    }

    fun getOrCreateDeviceId(): String {
        val existing = prefs.getString(KEY_DEVICE_ID, null)
        if (!existing.isNullOrBlank()) return existing
        val fresh = java.util.UUID.randomUUID().toString()
        prefs.edit().putString(KEY_DEVICE_ID, fresh).apply()
        return fresh
    }

    fun getPremiumUntil(): Long = prefs.getLong(KEY_PREMIUM_UNTIL, 0L)
    fun setPremiumUntil(epochMillis: Long) {
        prefs.edit().putLong(KEY_PREMIUM_UNTIL, epochMillis).apply()
    }
    fun getLastSubscriptionCheck(): Long = prefs.getLong(KEY_LAST_SUBSCRIPTION_CHECK, 0L)
    fun setLastSubscriptionCheck(epochMillis: Long) {
        prefs.edit().putLong(KEY_LAST_SUBSCRIPTION_CHECK, epochMillis).apply()
    }
    fun getDailyAiCount(): Int = prefs.getInt(KEY_DAILY_AI_COUNT, 0)
    fun setDailyAiCount(count: Int, date: String) {
        prefs.edit().putInt(KEY_DAILY_AI_COUNT, count).putString(KEY_DAILY_AI_COUNT_DATE, date).apply()
    }
    fun hasNotifiedQuotaExhausted(): Boolean = prefs.getBoolean(KEY_QUOTA_NOTIFIED, false)
    fun setNotifiedQuotaExhausted(notified: Boolean) {
        prefs.edit().putBoolean(KEY_QUOTA_NOTIFIED, notified).apply()
    }
    fun getExpiryReminderScheduledFor(): Long = prefs.getLong(KEY_EXPIRY_REMINDER_SCHEDULED_FOR, 0L)
    fun setExpiryReminderScheduledFor(premiumUntil: Long) {
        prefs.edit().putLong(KEY_EXPIRY_REMINDER_SCHEDULED_FOR, premiumUntil).apply()
    }
    fun getLastStoreChannel(): String? = prefs.getString(KEY_LAST_STORE_CHANNEL, null)
    fun setLastStoreChannel(channel: String) {
        prefs.edit().putString(KEY_LAST_STORE_CHANNEL, channel).apply()
    }

    companion object {
        private const val PREFS = "yadavar_settings"
        private const val KEY_DEVICE_ID = "device_id"
        private const val KEY_PREMIUM_UNTIL = "premium_until"
        private const val KEY_LAST_SUBSCRIPTION_CHECK = "last_subscription_check"
        private const val KEY_DAILY_AI_COUNT = "daily_ai_count"
        private const val KEY_DAILY_AI_COUNT_DATE = "daily_ai_count_date"
        private const val KEY_QUOTA_NOTIFIED = "quota_exhausted_notified"
        private const val KEY_EXPIRY_REMINDER_SCHEDULED_FOR = "expiry_reminder_scheduled_for"
        private const val KEY_LAST_STORE_CHANNEL = "last_store_channel"
        private const val KEY_QH_ENABLED = "quiet_hours_enabled"
        private const val KEY_QH_START = "quiet_hours_start"
        private const val KEY_QH_END = "quiet_hours_end"
        private const val KEY_QH_MODE = "quiet_hours_mode"
        private const val KEY_QH_CRITICAL = "quiet_hours_critical"
        private const val KEY_QH_SYSTEM = "quiet_hours_system_dnd"
        private const val KEY_QH_ANNOUNCE = "quiet_hours_announce"
        private const val KEY_FOCUS = "focus_mode"
        private const val KEY_GROUP = "group_by_category"
        private const val KEY_AI_KEY = "ai_api_key"
        private const val KEY_AI_URL = "ai_base_url"
        private const val KEY_AI_MODEL = "ai_model"
    }
}

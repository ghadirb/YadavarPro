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

    fun formatMinutes(total: Int): String {
        val h = (total / 60) % 24
        val m = total % 60
        return String.format("%02d:%02d", h, m)
    }

    companion object {
        private const val PREFS = "yadavar_settings"
        private const val KEY_QH_ENABLED = "quiet_hours_enabled"
        private const val KEY_QH_START = "quiet_hours_start"
        private const val KEY_QH_END = "quiet_hours_end"
        private const val KEY_QH_MODE = "quiet_hours_mode"
        private const val KEY_QH_CRITICAL = "quiet_hours_critical"
        private const val KEY_QH_SYSTEM = "quiet_hours_system_dnd"
        private const val KEY_QH_ANNOUNCE = "quiet_hours_announce"
        private const val KEY_FOCUS = "focus_mode"
        private const val KEY_GROUP = "group_by_category"
    }
}

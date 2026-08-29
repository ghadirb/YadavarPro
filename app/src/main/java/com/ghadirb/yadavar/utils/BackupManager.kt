package com.ghadirb.yadavar.utils

import com.ghadirb.yadavar.database.ReminderEntity
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

data class YadavarBackup(
    val version: Int = 1,
    val exportedAt: Long = System.currentTimeMillis(),
    val reminders: List<ReminderEntity>,
    val settings: Map<String, String>
)

object BackupManager {
    private val gson = Gson()

    fun exportJson(reminders: List<ReminderEntity>, prefs: PreferencesManager): String {
        val settings = mapOf(
            "quietHoursEnabled" to prefs.quietHoursEnabled.toString(),
            "quietStartMinutes" to prefs.quietStartMinutes.toString(),
            "quietEndMinutes" to prefs.quietEndMinutes.toString(),
            "quietMode" to prefs.quietMode.name,
            "allowCriticalInQuiet" to prefs.allowCriticalInQuiet.toString(),
            "focusModeEnabled" to prefs.focusModeEnabled.toString(),
            "groupByCategory" to prefs.groupByCategory.toString()
        )
        return gson.toJson(YadavarBackup(reminders = reminders, settings = settings))
    }

    fun parse(json: String): YadavarBackup {
        return gson.fromJson(json, object : TypeToken<YadavarBackup>() {}.type)
    }

    fun applySettings(backup: YadavarBackup, prefs: PreferencesManager) {
        val s = backup.settings
        s["quietHoursEnabled"]?.let { prefs.quietHoursEnabled = it.toBoolean() }
        s["quietStartMinutes"]?.toIntOrNull()?.let { prefs.quietStartMinutes = it }
        s["quietEndMinutes"]?.toIntOrNull()?.let { prefs.quietEndMinutes = it }
        s["quietMode"]?.let { runCatching { prefs.quietMode = QuietMode.valueOf(it) } }
        s["allowCriticalInQuiet"]?.let { prefs.allowCriticalInQuiet = it.toBoolean() }
        s["focusModeEnabled"]?.let { prefs.focusModeEnabled = it.toBoolean() }
        s["groupByCategory"]?.let { prefs.groupByCategory = it.toBoolean() }
    }
}

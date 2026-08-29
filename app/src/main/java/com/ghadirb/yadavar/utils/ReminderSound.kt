package com.ghadirb.yadavar.utils

import android.content.Context
import android.media.RingtoneManager
import android.net.Uri

object ReminderSound {
    const val DEFAULT_ALARM = "DEFAULT_ALARM"
    const val PICK_FROM_DEVICE = "PICK_FROM_DEVICE"

    data class BuiltIn(val value: String, val label: String)

    val builtIns = listOf(
        BuiltIn(DEFAULT_ALARM, "هشدار پیش‌فرض گوشی"),
        BuiltIn("RAW:reminder_voice_03", "برای رفتن آماده شوید"),
        BuiltIn("RAW:reminder_voice_05", "توجه، زمان یادآوری فرا رسیده است"),
        BuiltIn("RAW:reminder_voice_06", "توجه، یادآوری"),
        BuiltIn("RAW:reminder_voice_08", "زمان بیدار شدن است"),
        BuiltIn("RAW:reminder_voice_11", "زمان تماس رسیده است"),
        BuiltIn("RAW:reminder_voice_12", "زمان خوردن قرص شما فرا رسیده است"),
        BuiltIn("RAW:reminder_voice_13", "زمان شروع حرکت است"),
        BuiltIn("RAW:reminder_voice_14", "زمان یادآوری شما فرا رسیده است"),
        BuiltIn("RAW:reminder_voice_23", "یادآوری شما فرا رسیده است"),
        BuiltIn("RAW:reminder_voice_24", "یادآوری شما"),
        BuiltIn("RAW:reminder_voice_26", "یادآوری عقب‌افتاده"),
        BuiltIn("RAW:reminder_voice_34", "یک یادآوری برای شما داریم")
    )

    fun toUri(context: Context, value: String?): Uri? {
        val selected = value.orEmpty()
        if (selected.isBlank() || selected == DEFAULT_ALARM || selected == PICK_FROM_DEVICE) {
            return RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        }
        if (selected.startsWith("RAW:")) {
            val id = context.resources.getIdentifier(selected.removePrefix("RAW:"), "raw", context.packageName)
            return if (id != 0) Uri.parse("android.resource://${context.packageName}/$id") else null
        }
        return runCatching { Uri.parse(selected) }.getOrNull()
    }

    fun labelFor(value: String?): String = builtIns.firstOrNull { it.value == value }?.label
        ?: if (value.isNullOrBlank() || value == DEFAULT_ALARM) "هشدار پیش‌فرض گوشی" else "آهنگ انتخاب‌شده از گوشی"
}

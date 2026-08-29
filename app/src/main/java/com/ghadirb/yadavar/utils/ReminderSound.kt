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
        BuiltIn("RAW:reminder_voice_01", "صدای یادآوری ۱"),
        BuiltIn("RAW:reminder_voice_02", "صدای یادآوری ۲"),
        BuiltIn("RAW:reminder_voice_03", "صدای یادآوری ۳"),
        BuiltIn("RAW:reminder_voice_04", "صدای یادآوری ۴"),
        BuiltIn("RAW:reminder_voice_05", "توجه، زمان یادآوری فرا رسیده است"),
        BuiltIn("RAW:reminder_voice_06", "توجه، یادآوری"),
        BuiltIn("RAW:reminder_voice_07", "صدای یادآوری ۷"),
        BuiltIn("RAW:reminder_voice_08", "زمان بیدار شدن است"),
        BuiltIn("RAW:reminder_voice_09", "صدای یادآوری ۹"),
        BuiltIn("RAW:reminder_voice_10", "صدای یادآوری ۱۰"),
        BuiltIn("RAW:reminder_voice_11", "زمان تماس رسیده است"),
        BuiltIn("RAW:reminder_voice_12", "صدای یادآوری ۱۲"),
        BuiltIn("RAW:reminder_voice_13", "زمان شروع حرکت است"),
        BuiltIn("RAW:reminder_voice_14", "زمان یادآوری شما فرا رسیده است"),
        BuiltIn("RAW:reminder_voice_15", "صدای یادآوری ۱۵"),
        BuiltIn("RAW:reminder_voice_16", "صدای یادآوری ۱۶"),
        BuiltIn("RAW:reminder_voice_17", "صدای یادآوری ۱۷"),
        BuiltIn("RAW:reminder_voice_18", "صدای یادآوری ۱۸"),
        BuiltIn("RAW:reminder_voice_19", "صدای یادآوری ۱۹"),
        BuiltIn("RAW:reminder_voice_20", "یادآوری پرداخت"),
        BuiltIn("RAW:reminder_voice_21", "صدای یادآوری ۲۱"),
        BuiltIn("RAW:reminder_voice_22", "صدای یادآوری ۲۲"),
        BuiltIn("RAW:reminder_voice_23", "یادآوری شما فرا رسیده است"),
        BuiltIn("RAW:reminder_voice_24", "یادآوری شما"),
        BuiltIn("RAW:reminder_voice_25", "صدای یادآوری ۲۵"),
        BuiltIn("RAW:reminder_voice_26", "صدای یادآوری ۲۶"),
        BuiltIn("RAW:reminder_voice_27", "یادآوری قبض"),
        BuiltIn("RAW:reminder_voice_28", "یادآوری قسط"),
        BuiltIn("RAW:reminder_voice_29", "صدای یادآوری ۲۹"),
        BuiltIn("RAW:reminder_voice_30", "صدای یادآوری ۳۰"),
        BuiltIn("RAW:reminder_voice_31", "صدای یادآوری ۳۱"),
        BuiltIn("RAW:reminder_voice_32", "صدای یادآوری ۳۲"),
        BuiltIn("RAW:reminder_voice_33", "صدای یادآوری ۳۳"),
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

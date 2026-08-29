package com.ghadirb.yadavar.utils

import android.content.Context
import android.media.RingtoneManager
import android.net.Uri

/**
 * Converts the compact sound value stored in Room into a playable URI. Ported from the
 * ghadirb/Maliar-Pro codex/reminder-finalize-v2 branch's ReminderSound.kt, including its
 * 34 pre-recorded Persian voice-prompt raw assets (res/raw/reminder_voice_01..34.mp3) -
 * those clips replaced Maliar-Pro's earlier always-on background TTS service, which was
 * removed there for reliability/battery reasons. This app never runs a background
 * speech service either - a reminder just plays one short recorded clip once.
 */
object ReminderSound {
    const val DEFAULT_ALARM = "DEFAULT_ALARM"

    data class BuiltIn(val value: String, val label: String)

    val builtIns = listOf(
        BuiltIn(DEFAULT_ALARM, "هشدار پیش‌فرض گوشی"),
        BuiltIn("RAW:reminder_voice_05", "توجه، زمان یادآوری فرا رسیده است"),
        BuiltIn("RAW:reminder_voice_06", "توجه، یادآوری"),
        BuiltIn("RAW:reminder_voice_08", "زمان بیدار شدن است"),
        BuiltIn("RAW:reminder_voice_11", "زمان تماس رسیده است"),
        BuiltIn("RAW:reminder_voice_13", "زمان شروع حرکت است"),
        BuiltIn("RAW:reminder_voice_14", "زمان یادآوری شما فرا رسیده است"),
        BuiltIn("RAW:reminder_voice_20", "یادآوری پرداخت"),
        BuiltIn("RAW:reminder_voice_23", "یادآوری شما فرا رسیده است"),
        BuiltIn("RAW:reminder_voice_24", "یادآوری شما"),
        BuiltIn("RAW:reminder_voice_27", "یادآوری قبض"),
        BuiltIn("RAW:reminder_voice_28", "یادآوری قسط"),
        BuiltIn("RAW:reminder_voice_34", "یک یادآوری برای شما داریم")
        // The remaining clips in res/raw (installment/debt/income-specific phrasing) were
        // left out of this picker since they're finance wording that doesn't fit a
        // general-purpose reminder - the files are still bundled, so add them back to this
        // list any time.
    )

    fun toUri(context: Context, value: String?): Uri? {
        val selected = value.orEmpty()
        if (selected.isBlank() || selected == DEFAULT_ALARM) {
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
        ?: if (value.isNullOrBlank() || value == DEFAULT_ALARM) "هشدار پیش‌فرض گوشی" else "فایل انتخاب‌شده از گوشی"
}

package com.ghadirb.yadavar.assistant

import com.ghadirb.yadavar.database.CategoryCatalog
import com.ghadirb.yadavar.database.Priority
import com.ghadirb.yadavar.database.RepeatPattern
import com.ghadirb.yadavar.utils.PersianCalendarHelper
import java.util.Calendar
import java.util.Locale

sealed class AssistantIntent {
    data class Create(
        val title: String,
        val triggerAt: Long,
        val repeat: RepeatPattern = RepeatPattern.ONCE,
        val category: String = "",
        val priority: Priority = Priority.MEDIUM,
        val intervalDays: Int = 0
    ) : AssistantIntent()

    object Today : AssistantIntent()
    object Overdue : AssistantIntent()
    object Week : AssistantIntent()
    object EnableQuiet : AssistantIntent()
    object DisableQuiet : AssistantIntent()
    object EnableFocus : AssistantIntent()
    object DisableFocus : AssistantIntent()
    object Help : AssistantIntent()
    data class FreeChat(val text: String) : AssistantIntent()
}

/**
 * On-device Persian parser so the assistant works without an API key.
 * Examples: «فردا ساعت ۸ دارو بخور»، «هر روز ۹ صبح ورزش»، «یادآوری‌های امروز».
 */
object ReminderNlp {

    fun parse(raw: String): AssistantIntent {
        val text = raw.trim()
        if (text.isEmpty()) return AssistantIntent.Help
        val n = normalize(text)

        when {
            n == "کمک" || n == "help" || n.contains("چه کار میتونی") || n.contains("چیکار میتونی") ->
                return AssistantIntent.Help
            n.contains("سررسید") || n.contains("عقب افتاد") || n.contains("عقب‌افتاد") ->
                return AssistantIntent.Overdue
            n.contains("این هفته") -> return AssistantIntent.Week
            looksLikeListToday(n) -> return AssistantIntent.Today
            n.contains("سکوت") && isOff(n) -> return AssistantIntent.DisableQuiet
            n.contains("سکوت") || n.contains("dnd") -> return AssistantIntent.EnableQuiet
            n.contains("تمرکز") && isOff(n) -> return AssistantIntent.DisableFocus
            n.contains("تمرکز") -> return AssistantIntent.EnableFocus
        }

        return parseCreate(text, n) ?: AssistantIntent.FreeChat(text)
    }

    private fun looksLikeListToday(n: String): Boolean {
        if (n.contains("برنامه امروز") || n.contains("امروز چی")) return true
        if ((n.contains("لیست") || n.contains("یادآوری")) && n.contains("امروز")) return true
        if (n == "امروز" || n == "یادآوری های امروز" || n == "یادآوری‌های امروز") return true
        return false
    }

    private fun isOff(n: String) =
        n.contains("خاموش") || n.contains("غیر فعال") || n.contains("غیرفعال") ||
            n.contains("ببند") || n.contains("لغو") || n.contains("off")

    private fun parseCreate(original: String, n: String): AssistantIntent.Create? {
        val looksLikeCreate = n.contains("یادآوری") || n.contains("یادت باشه") ||
            n.contains("یادم باشه") || n.contains("آلارم") || n.contains("بیدار") ||
            n.contains("قرص") || n.contains("دارو") || n.contains("جلسه") ||
            n.contains("قرار") || n.contains("قبض") || n.contains("ورزش") ||
            n.contains("ساعت") || n.contains("فردا") || n.contains("پس فردا") ||
            n.contains("پسفردا") || n.contains("هر روز") || n.contains("هرروز")
        if (!looksLikeCreate) return null

        val cal = Calendar.getInstance()
        var hour = 9
        var minute = 0
        var timeFound = false

        Regex("""ساعت\s*(\d{1,2})(?:[:\.](\d{2}))?""").find(n)?.let {
            hour = it.groupValues[1].toInt()
            minute = it.groupValues[2].toIntOrNull() ?: 0
            timeFound = true
        } ?: Regex("""(\d{1,2})[:\.](\d{2})""").find(n)?.let {
            hour = it.groupValues[1].toInt()
            minute = it.groupValues[2].toInt()
            timeFound = true
        } ?: Regex("""\b(\d{1,2})\s*(صبح|ظهر|عصر|شب)?""").find(n)?.let {
            val h = it.groupValues[1].toInt()
            if (h in 0..23) {
                hour = h
                timeFound = true
            }
        }

        when {
            n.contains("امشب") && !timeFound -> { hour = 21; timeFound = true }
            n.contains("صبح") && timeFound && hour <= 12 -> { /* already morning */ }
            n.contains("ظهر") && timeFound && hour < 12 -> hour += 12
            n.contains("عصر") && timeFound && hour < 12 -> hour += 12
            n.contains("شب") && timeFound && hour < 12 -> hour = if (hour == 12) 0 else hour + 12
        }
        hour = hour.coerceIn(0, 23)
        minute = minute.coerceIn(0, 59)

        when {
            n.contains("پس فردا") || n.contains("پسفردا") -> cal.add(Calendar.DAY_OF_YEAR, 2)
            n.contains("فردا") -> cal.add(Calendar.DAY_OF_YEAR, 1)
            n.contains("امشب") -> { /* today evening */ }
        }

        cal.set(Calendar.HOUR_OF_DAY, hour)
        cal.set(Calendar.MINUTE, minute)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        if (cal.timeInMillis < System.currentTimeMillis() - 30_000) {
            cal.add(Calendar.DAY_OF_YEAR, 1)
        }

        val repeat = when {
            n.contains("روزهای کاری") || n.contains("روز کاری") -> RepeatPattern.WEEKDAYS
            n.contains("آخر هفته") || n.contains("آخرهفته") -> RepeatPattern.WEEKENDS
            n.contains("هر روز") || n.contains("هرروز") || n.contains("روزانه") -> RepeatPattern.DAILY
            n.contains("هر هفته") || n.contains("هفتگی") -> RepeatPattern.WEEKLY
            n.contains("هر ماه") || n.contains("ماهانه") -> RepeatPattern.MONTHLY
            n.contains("هر سال") || n.contains("سالانه") -> RepeatPattern.YEARLY
            else -> RepeatPattern.ONCE
        }

        val category = CategoryCatalog.defaults.firstOrNull { n.contains(it.name) }?.name
            ?: when {
                n.contains("قرص") || n.contains("دارو") -> "دارو"
                n.contains("ورزش") || n.contains("دکتر") || n.contains("پزشک") -> "سلامت"
                n.contains("خرید") || n.contains("فروشگاه") -> "خرید"
                n.contains("قبض") || n.contains("قسط") -> "قبض"
                n.contains("جلسه") || n.contains("اداره") || n.contains("کار") -> "کار"
                n.contains("مهمانی") || n.contains("تولد") -> "مهمانی"
                else -> "عمومی"
            }

        val priority = when {
            n.contains("فوری") || n.contains("بحرانی") || n.contains("حتما") -> Priority.CRITICAL
            n.contains("مهم") -> Priority.HIGH
            else -> Priority.MEDIUM
        }

        val title = extractTitle(original, n).ifBlank { original.trim() }
        return AssistantIntent.Create(
            title = title,
            triggerAt = cal.timeInMillis,
            repeat = repeat,
            category = category,
            priority = priority
        )
    }

    private fun extractTitle(original: String, n: String): String {
        var t = original.trim()
        listOf(
            "یادت باشه", "یادم باشه", "یک یادآوری", "یه یادآوری", "یادآوری کن",
            "یادآوری", "آلارم", "بذار"
        ).forEach { token ->
            t = t.replace(token, " ", ignoreCase = true)
        }
        t = t.replace(Regex("""ساعت\s*\d{1,2}([:\.]\d{2})?"""), " ")
        t = t.replace(Regex("""(پس\s*فردا|فردا|امروز|امشب|هر روز|هرروز|روزانه|هر هفته|هفتگی|هر ماه|ماهانه)"""), " ")
        t = t.replace(Regex("""\s+"""), " ").trim()
        return t.ifBlank { original.trim() }
    }

    fun normalize(text: String): String {
        val digits = mapOf(
            '۰' to '0', '۱' to '1', '۲' to '2', '۳' to '3', '۴' to '4',
            '۵' to '5', '۶' to '6', '۷' to '7', '۸' to '8', '۹' to '9',
            '٠' to '0', '١' to '1', '٢' to '2', '٣' to '3', '٤' to '4',
            '٥' to '5', '٦' to '6', '٧' to '7', '٨' to '8', '٩' to '9'
        )
        val sb = StringBuilder()
        text.lowercase(Locale("fa")).forEach { ch -> sb.append(digits[ch] ?: ch) }
        return sb.toString()
            .replace('ي', 'ی')
            .replace('ك', 'ک')
            .replace(Regex("""\s+"""), " ")
            .trim()
    }

    fun formatWhen(epoch: Long): String {
        val cal = Calendar.getInstance().apply { timeInMillis = epoch }
        val j = PersianCalendarHelper.gregorianToJalali(
            cal.get(Calendar.YEAR),
            cal.get(Calendar.MONTH) + 1,
            cal.get(Calendar.DAY_OF_MONTH)
        )
        return "${PersianCalendarHelper.format(j)}  ${"%02d:%02d".format(cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE))}"
    }
}

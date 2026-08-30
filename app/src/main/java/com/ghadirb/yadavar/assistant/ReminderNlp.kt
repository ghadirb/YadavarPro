package com.ghadirb.yadavar.assistant

import com.ghadirb.yadavar.database.CategoryCatalog
import com.ghadirb.yadavar.database.Priority
import com.ghadirb.yadavar.database.RepeatPattern
import com.ghadirb.yadavar.utils.EnumLabels
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
        val intervalDays: Int = 0,
        val intervalMinutes: Int = 0,
        val customDays: String = "",
        val alertType: String = "NOTIFICATION"
    ) : AssistantIntent()

    object Today : AssistantIntent()
    object Overdue : AssistantIntent()
    object Week : AssistantIntent()
    object EnableQuiet : AssistantIntent()
    object DisableQuiet : AssistantIntent()
    object EnableFocus : AssistantIntent()
    object DisableFocus : AssistantIntent()
    object Help : AssistantIntent()
    data class OffTopic(val text: String) : AssistantIntent()
    data class ReminderQuestion(val text: String) : AssistantIntent()
}

data class ParsedTime(val hour: Int, val minute: Int, val found: Boolean)

/**
 * On-device Persian parser. Understands minutes ("۶ و ۴۲ دقیقه"), weekday ranges
 * ("شنبه تا چهارشنبه") and refuses everyday chat so hosted API keys are only spent
 * on reminder work.
 */
object ReminderNlp {

    private val WEEKDAY_INDEX = linkedMapOf(
        "شنبه" to 6,
        "یکشنبه" to 0,
        "یک شنبه" to 0,
        "دوشنبه" to 1,
        "دو شنبه" to 1,
        "سه شنبه" to 2,
        "سه‌شنبه" to 2,
        "چهارشنبه" to 3,
        "چهار شنبه" to 3,
        "پنجشنبه" to 4,
        "پنج شنبه" to 4,
        "جمعه" to 5
    )

    private val NUMBER_WORDS = mapOf(
        "صفر" to 0, "یک" to 1, "دو" to 2, "سه" to 3, "چهار" to 4, "پنج" to 5,
        "شش" to 6, "هفت" to 7, "هشت" to 8, "نه" to 9, "ده" to 10,
        "یازده" to 11, "دوازده" to 12, "سیزده" to 13, "چهارده" to 14, "پانزده" to 15,
        "شانزده" to 16, "هفده" to 17, "هجده" to 18, "نوزده" to 19,
        "بیست" to 20, "سی" to 30, "چهل" to 40, "پنجاه" to 50
    )

    private val OFF_TOPIC = listOf(
        "جوک", "شعر", "داستان", "آب و هوا", "هوا چطور", "ترجمه", "انگلیسی",
        "برنامه نویسی", "کد بنویس", "سرمایه", "بورس", "طلا", "دلار", "بیت کوین",
        "آشپزی", "دستور پخت", "فیلم", "فوتبال", "نتیجه بازی"
    )

    fun parse(raw: String): AssistantIntent {
        val text = raw.trim()
        if (text.isEmpty()) return AssistantIntent.Help
        val n = normalize(text)

        when {
            n == "کمک" || n == "help" || n.contains("چه کار میتونی") || n.contains("چیکار میتونی") ->
                return AssistantIntent.Help
            n.contains("سررسید") || n.contains("عقب افتاد") || n.contains("عقب‌افتاد") ->
                return AssistantIntent.Overdue
            n.contains("این هفته") && (n.contains("لیست") || n.contains("یادآوری") || n.length < 18) ->
                return AssistantIntent.Week
            looksLikeListToday(n) -> return AssistantIntent.Today
            n.contains("سکوت") && isOff(n) -> return AssistantIntent.DisableQuiet
            n.contains("سکوت") || n.contains("dnd") -> return AssistantIntent.EnableQuiet
            n.contains("تمرکز") && isOff(n) -> return AssistantIntent.DisableFocus
            n.contains("تمرکز") -> return AssistantIntent.EnableFocus
        }

        if (isOffTopic(n)) return AssistantIntent.OffTopic(text)

        return parseCreate(text, n) ?: if (looksLikeReminderTalk(n)) {
            AssistantIntent.ReminderQuestion(text)
        } else {
            AssistantIntent.OffTopic(text)
        }
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

    private fun isOffTopic(n: String) = OFF_TOPIC.any { n.contains(it) }

    private fun looksLikeReminderTalk(n: String): Boolean {
        val keys = listOf(
            "یادآوری", "یادت باشه", "یادم باشه", "آلارم", "بیدار", "هشدار",
            "ساعت", "دقیقه", "فردا", "پس فردا", "امروز", "امشب",
            "شنبه", "یکشنبه", "دوشنبه", "سه شنبه", "سه‌شنبه", "چهارشنبه",
            "پنجشنبه", "جمعه", "هر روز", "هفتگی", "ماهانه",
            "دارو", "قرص", "جلسه", "قرار", "قبض", "ورزش", "تولد", "صبح", "ظهر", "عصر"
        )
        return keys.any { n.contains(it) }
    }

    private fun parseCreate(original: String, n: String): AssistantIntent.Create? {
        val time = parseTime(n)
        val weekdays = parseWeekdays(n)
        val looksLikeCreate = looksLikeReminderTalk(n) && (
            n.contains("یادآوری") || n.contains("یادت باشه") || n.contains("یادم باشه") ||
                n.contains("آلارم") || n.contains("بیدار") || n.contains("بذار") ||
                n.contains("ثبت") || n.contains("ساعت") || n.contains("فردا") ||
                n.contains("پس فردا") || n.contains("هر روز") || n.contains("هرروز") ||
                n.contains("دقیقه") || n.contains("صبح") || n.contains("ظهر") ||
                n.contains("عصر") || n.contains("شب") ||
                WEEKDAY_INDEX.keys.any { n.contains(it) } ||
                n.contains("قرص") || n.contains("دارو") || n.contains("جلسه") ||
                n.contains("قرار") || n.contains("قبض") || n.contains("ورزش") ||
                (time?.found == true) || weekdays.isNotEmpty()
            )
        if (!looksLikeCreate) return null

        // "هر ۸ ساعت" / "هر نیم ساعت" (every N hours/minutes) is a distinct pattern from
        // "ساعت ۸" (at clock-hour 8) - distinguished by the "هر" prefix coming before the
        // number, so it can't be confused with a specific time-of-day.
        val worded = replaceNumberWords(n)
        val hourInterval = Regex("""هر\s*(\d{1,3})\s*ساعت""").find(worded)?.groupValues?.get(1)?.toIntOrNull() ?: 0
        val minuteInterval = if (hourInterval == 0) {
            Regex("""هر\s*(\d{1,3})\s*دقیقه""").find(worded)?.groupValues?.get(1)?.toIntOrNull() ?: 0
        } else 0
        val subDayIntervalMinutes = hourInterval * 60 + minuteInterval
        val hasSubDayInterval = subDayIntervalMinutes > 0

        val dayInterval = Regex("""هر\s*(\d{1,2})\s*روز""").find(n)?.groupValues?.get(1)?.toIntOrNull() ?: 0
        val dateShift = when {
            n.contains("پس فردا") || n.contains("پسفردا") -> 2
            n.contains("فردا") -> 1
            else -> 0
        }

        // If nothing here actually anchors a time (no clock time, no weekday, no "tomorrow"/
        // "tonight", no day/hour/minute interval), silently defaulting to 9:00 would just be
        // a guess presented as a fact. Better to admit defeat here so parse() falls through to
        // looksLikeReminderTalk() -> ReminderQuestion -> the cloud AI fallback, which can ask
        // a genuinely ambiguous phrase to be clarified instead of committing to a wrong time.
        val hasTemporalSignal = time?.found == true || weekdays.isNotEmpty() || dateShift > 0 ||
            n.contains("امشب") || dayInterval > 0 || hasSubDayInterval
        if (!hasTemporalSignal) return null

        val cal = Calendar.getInstance()

        if (hasSubDayInterval && time?.found != true) {
            // "از الان هر ۸ ساعت ..." - no explicit clock time was given alongside the
            // interval, so the natural reading is "starting now, every N hours/minutes".
            cal.add(Calendar.MINUTE, subDayIntervalMinutes)
            cal.set(Calendar.SECOND, 0)
            cal.set(Calendar.MILLISECOND, 0)
        } else {
            val resolved = time ?: ParsedTime(9, 0, false)
            var hour = resolved.hour
            var minute = resolved.minute

            when {
                n.contains("امشب") && !resolved.found -> { hour = 21 }
                n.contains("ظهر") && resolved.found && hour < 12 -> hour += 12
                n.contains("عصر") && resolved.found && hour in 1..11 -> hour += 12
                n.contains("شب") && resolved.found && hour in 1..11 && !n.contains("امشب") -> hour += 12
                n.contains("صبح") && resolved.found && hour == 12 -> hour = 0
            }
            hour = hour.coerceIn(0, 23)
            minute = minute.coerceIn(0, 59)

            if (weekdays.isEmpty()) {
                cal.add(Calendar.DAY_OF_YEAR, dateShift)
            } else {
                moveToNextMatchingDay(cal, weekdays)
            }

            cal.set(Calendar.HOUR_OF_DAY, hour)
            cal.set(Calendar.MINUTE, minute)
            cal.set(Calendar.SECOND, 0)
            cal.set(Calendar.MILLISECOND, 0)
            if (cal.timeInMillis < System.currentTimeMillis() - 30_000) {
                if (weekdays.isNotEmpty()) {
                    cal.add(Calendar.DAY_OF_YEAR, 1)
                    moveToNextMatchingDay(cal, weekdays, includeToday = true)
                    cal.set(Calendar.HOUR_OF_DAY, hour)
                    cal.set(Calendar.MINUTE, minute)
                } else {
                    cal.add(Calendar.DAY_OF_YEAR, 1)
                }
            }
        }

        val workWeek = setOf(6, 0, 1, 2, 3)
        val weekend = setOf(4, 5)
        val repeat = when {
            hasSubDayInterval || dayInterval > 0 -> RepeatPattern.CUSTOM_INTERVAL
            n.contains("روزهای کاری") || n.contains("روز کاری") -> RepeatPattern.WEEKDAYS
            weekdays == workWeek -> RepeatPattern.WEEKDAYS
            n.contains("آخر هفته") || n.contains("آخرهفته") || weekdays == weekend -> RepeatPattern.WEEKENDS
            weekdays.size >= 1 -> RepeatPattern.CUSTOM
            n.contains("هر روز") || n.contains("هرروز") || n.contains("روزانه") -> RepeatPattern.DAILY
            n.contains("هر هفته") || n.contains("هفتگی") -> RepeatPattern.WEEKLY
            n.contains("هر ماه") || n.contains("ماهانه") -> RepeatPattern.MONTHLY
            n.contains("هر سال") || n.contains("سالانه") -> RepeatPattern.YEARLY
            else -> RepeatPattern.ONCE
        }

        val category = inferCategory(n)

        val priority = when {
            n.contains("فوری") || n.contains("بحرانی") || n.contains("حتما") -> Priority.CRITICAL
            n.contains("مهم") -> Priority.HIGH
            else -> Priority.MEDIUM
        }

        val alertType = when {
            n.contains("هوشمند") || (n.contains("با صدا بگو") || (n.contains("بگو") && n.contains("صدا"))) -> "SMART"
            n.contains("تمام صفحه") || n.contains("تمام‌صفحه") || n.contains("زنگ") -> "FULL_SCREEN"
            else -> "NOTIFICATION"
        }

        val title = extractTitle(original, n).ifBlank { original.trim() }
        return AssistantIntent.Create(
            title = title,
            triggerAt = cal.timeInMillis,
            repeat = repeat,
            category = category,
            priority = priority,
            intervalDays = dayInterval,
            intervalMinutes = subDayIntervalMinutes,
            customDays = weekdays.sorted().joinToString(","),
            alertType = alertType
        )
    }

    /** Best-effort category guess from free text - used both by the assistant parser and by
     *  the manual Add Reminder dialog to auto-select a category as the person types a title,
     *  without ever overriding a category they've picked by hand. */
    fun inferCategory(text: String): String {
        val n = normalize(text)
        return CategoryCatalog.defaults.firstOrNull { n.contains(it.name) }?.name
            ?: when {
                n.contains("قرص") || n.contains("دارو") -> "دارو"
                n.contains("ورزش") || n.contains("دکتر") || n.contains("پزشک") -> "سلامت"
                n.contains("خرید") || n.contains("فروشگاه") -> "خرید"
                n.contains("قبض") -> "قبض"
                n.contains("جلسه") || n.contains("اداره") || n.contains("کار") -> "کار"
                n.contains("مهمانی") || n.contains("تولد") -> "مهمانی"
                else -> "عمومی"
            }
    }

    internal fun parseTime(n: String): ParsedTime? {
        val worded = replaceNumberWords(n)

        Regex("""ساعت\s*(\d{1,2})\s*و\s*سه\s*ربع""").find(worded)?.let {
            return ParsedTime(it.groupValues[1].toInt(), 45, true)
        }
        Regex("""ساعت\s*(\d{1,2})\s*و\s*ربع""").find(worded)?.let {
            return ParsedTime(it.groupValues[1].toInt(), 15, true)
        }
        Regex("""ساعت\s*(\d{1,2})\s*و\s*نیم""").find(worded)?.let {
            return ParsedTime(it.groupValues[1].toInt(), 30, true)
        }
        Regex("""(\d{1,2})\s*و\s*سه\s*ربع""").find(worded)?.let {
            return ParsedTime(it.groupValues[1].toInt(), 45, true)
        }
        Regex("""(\d{1,2})\s*و\s*ربع""").find(worded)?.let {
            return ParsedTime(it.groupValues[1].toInt(), 15, true)
        }
        Regex("""(\d{1,2})\s*و\s*نیم""").find(worded)?.let {
            return ParsedTime(it.groupValues[1].toInt(), 30, true)
        }
        Regex("""ساعت\s*(\d{1,2})\s*و\s*(\d{1,2})\s*(دقیقه)?""").find(worded)?.let {
            return ParsedTime(it.groupValues[1].toInt(), it.groupValues[2].toInt(), true)
        }
        Regex("""(\d{1,2})\s*و\s*(\d{1,2})\s*دقیقه""").find(worded)?.let {
            return ParsedTime(it.groupValues[1].toInt(), it.groupValues[2].toInt(), true)
        }
        // "۶ و ۴۲ صبح" — speech often drops the word دقیقه
        Regex("""(\d{1,2})\s*و\s*(\d{1,2})(?:\s*(صبح|ظهر|عصر|شب))?""").find(worded)?.let {
            val minute = it.groupValues[2].toInt()
            if (minute in 0..59) return ParsedTime(it.groupValues[1].toInt(), minute, true)
        }
        Regex("""ساعت\s*(\d{1,2})[:\.](\d{1,2})""").find(worded)?.let {
            return ParsedTime(it.groupValues[1].toInt(), it.groupValues[2].toInt(), true)
        }
        Regex("""(\d{1,2})[:\.](\d{2})""").find(worded)?.let {
            return ParsedTime(it.groupValues[1].toInt(), it.groupValues[2].toInt(), true)
        }
        Regex("""ساعت\s*(\d{1,2})(?:\s*(صبح|ظهر|عصر|شب))?""").find(worded)?.let {
            return ParsedTime(it.groupValues[1].toInt(), 0, true)
        }
        Regex("""(?:^|[^\d])(\d{1,2})\s*(صبح|ظهر|عصر|شب)""").find(worded)?.let {
            return ParsedTime(it.groupValues[1].toInt(), 0, true)
        }
        return null
    }

    internal fun parseWeekdays(n: String): Set<Int> {
        val rangeMatch = Regex(
            """(?:از\s*)?(شنبه|یکشنبه|یک شنبه|دوشنبه|دو شنبه|سه شنبه|سه‌شنبه|چهارشنبه|چهار شنبه|پنجشنبه|پنج شنبه|جمعه)\s*تا\s*(شنبه|یکشنبه|یک شنبه|دوشنبه|دو شنبه|سه شنبه|سه‌شنبه|چهارشنبه|چهار شنبه|پنجشنبه|پنج شنبه|جمعه)"""
        ).find(n)
        if (rangeMatch != null) {
            val start = WEEKDAY_INDEX[rangeMatch.groupValues[1]]
            val end = WEEKDAY_INDEX[rangeMatch.groupValues[2]]
            if (start != null && end != null) return expandRange(start, end)
        }

        val found = mutableListOf<Pair<Int, Int>>()
        WEEKDAY_INDEX.entries.sortedByDescending { it.key.length }.forEach { (name, day) ->
            var from = 0
            while (true) {
                val at = n.indexOf(name, from)
                if (at < 0) break
                if (found.none { it.first == at }) found.add(at to day)
                from = at + name.length
            }
        }
        if (found.isEmpty()) return emptySet()
        val ordered = found.sortedBy { it.first }.map { it.second }.distinct()
        if (n.contains("تا") && ordered.size >= 2) {
            return expandRange(ordered.first(), ordered.last())
        }
        return ordered.toSet()
    }

    private fun expandRange(start: Int, end: Int): Set<Int> {
        val order = listOf(6, 0, 1, 2, 3, 4, 5) // Sat → Fri
        val si = order.indexOf(start)
        val ei = order.indexOf(end)
        if (si < 0 || ei < 0) return setOf(start, end)
        return if (si <= ei) order.subList(si, ei + 1).toSet()
        else (order.subList(si, order.size) + order.subList(0, ei + 1)).toSet()
    }

    private fun moveToNextMatchingDay(cal: Calendar, days: Set<Int>, includeToday: Boolean = false) {
        fun index(calDay: Int) = (calDay - Calendar.SUNDAY).let { if (it < 0) it + 7 else it }
        if (includeToday && index(cal.get(Calendar.DAY_OF_WEEK)) in days) return
        var guard = 0
        while (index(cal.get(Calendar.DAY_OF_WEEK)) !in days && guard < 8) {
            cal.add(Calendar.DAY_OF_YEAR, 1)
            guard++
        }
    }

    private fun replaceNumberWords(text: String): String {
        var t = text
        val compound = Regex("""(بیست|سی|چهل|پنجاه)\s*و\s*(یک|دو|سه|چهار|پنج|شش|هفت|هشت|نه)""")
        t = compound.replace(t) { m ->
            val tens = NUMBER_WORDS[m.groupValues[1]] ?: 0
            val ones = NUMBER_WORDS[m.groupValues[2]] ?: 0
            (tens + ones).toString()
        }
        NUMBER_WORDS.entries.sortedByDescending { it.key.length }.forEach { (word, num) ->
            t = t.replace(word, num.toString())
        }
        return t
    }

    private fun extractTitle(original: String, n: String): String {
        var t = n
        listOf(
            "یادت باشه", "یادم باشه", "یک یادآوری", "یه یادآوری", "یادآوری کن",
            "یادآوری", "آلارم", "بذار", "ثبت کن", "بخور"
        ).forEach { token ->
            t = t.replace(token, " ", ignoreCase = true)
        }
        t = t.replace(Regex("""ساعت\s*\d{1,2}(\s*و\s*\d{1,2}\s*(دقیقه)?)?([:\.]\d{2})?"""), " ")
        t = t.replace(Regex("""\d{1,2}\s*و\s*\d{1,2}\s*(دقیقه)?"""), " ")
        t = t.replace(Regex("""هر\s*\d{1,3}\s*(ساعت|دقیقه|روز)"""), " ")
        t = t.replace(Regex("""(پس\s*فردا|فردا|امروز|امشب|هر روز|هرروز|روزانه|هر هفته|هفتگی|هر ماه|ماهانه|صبح|ظهر|عصر|شب|از الان)"""), " ")
        t = t.replace(Regex("""(از\s*)?(شنبه|یکشنبه|دوشنبه|سه‌شنبه|سه شنبه|چهارشنبه|پنجشنبه|جمعه|تا)"""), " ")
        t = t.replace(Regex("""\s+"""), " ").trim()
        t = t.removeSuffix(" کن").trim()
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

    fun weekdayNames(csv: String): String {
        if (csv.isBlank()) return ""
        val map = EnumLabels.weekdays.associate { it.value to it.label }
        return csv.split(",").mapNotNull { map[it.trim()] }.joinToString("، ")
    }
}

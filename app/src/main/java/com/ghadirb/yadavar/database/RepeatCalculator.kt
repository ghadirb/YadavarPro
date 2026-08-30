package com.ghadirb.yadavar.database

import com.ghadirb.yadavar.utils.PersianCalendarHelper
import java.util.Calendar

/** Computes the next triggerTime for a reminder once its current alarm fires. */
object RepeatCalculator {

    fun nextTrigger(reminder: ReminderEntity): Long? {
        val cal = Calendar.getInstance().apply { timeInMillis = reminder.triggerTime }

        return when (RepeatPattern.valueOf(reminder.repeatPattern)) {
            RepeatPattern.ONCE -> null
            RepeatPattern.DAILY -> { cal.add(Calendar.DAY_OF_YEAR, 1); cal.timeInMillis }
            RepeatPattern.WEEKLY -> { cal.add(Calendar.WEEK_OF_YEAR, 1); cal.timeInMillis }
            RepeatPattern.MONTHLY -> { cal.add(Calendar.MONTH, 1); cal.timeInMillis }
            RepeatPattern.YEARLY -> PersianCalendarHelper.addJalaliYears(reminder.triggerTime, 1)
            RepeatPattern.WEEKDAYS -> nextMatchingDay(cal) { isIranWorkday(it) }
            RepeatPattern.WEEKENDS -> nextMatchingDay(cal) { it == Calendar.THURSDAY || it == Calendar.FRIDAY }
            RepeatPattern.CUSTOM -> {
                val days = reminder.customRepeatDays.split(",").mapNotNull { it.trim().toIntOrNull() }.toSet()
                if (days.isEmpty()) null else nextMatchingDay(cal) { (it - Calendar.SUNDAY) in days }
            }
            RepeatPattern.CUSTOM_INTERVAL -> {
                if (reminder.repeatIntervalDays <= 0 && reminder.repeatIntervalMinutes <= 0) null
                else {
                    if (reminder.repeatIntervalDays > 0) cal.add(Calendar.DAY_OF_YEAR, reminder.repeatIntervalDays)
                    if (reminder.repeatIntervalMinutes > 0) cal.add(Calendar.MINUTE, reminder.repeatIntervalMinutes)
                    cal.timeInMillis
                }
            }
        }
    }

    /**
     * Iran work week is Saturday–Wednesday. `Calendar.SATURDAY..WEDNESDAY` is an
     * empty range (7..4) and would loop forever — never use that.
     */
    fun isIranWorkday(calendarDay: Int): Boolean =
        calendarDay == Calendar.SATURDAY || calendarDay in Calendar.SUNDAY..Calendar.WEDNESDAY

    private fun nextMatchingDay(cal: Calendar, matches: (Int) -> Boolean): Long {
        var guard = 0
        do {
            cal.add(Calendar.DAY_OF_YEAR, 1)
            guard++
        } while (!matches(cal.get(Calendar.DAY_OF_WEEK)) && guard < 10)
        return cal.timeInMillis
    }
}

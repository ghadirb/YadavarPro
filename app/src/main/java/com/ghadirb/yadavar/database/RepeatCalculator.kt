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
            // Yearly repeats (birthdays, anniversaries) advance by a Jalali year, not a
            // naive Gregorian +365 days - see PersianCalendarHelper for why that matters.
            RepeatPattern.YEARLY -> PersianCalendarHelper.addJalaliYears(reminder.triggerTime, 1)
            RepeatPattern.WEEKDAYS -> nextMatchingDay(cal) { it in Calendar.SATURDAY..Calendar.WEDNESDAY }
            RepeatPattern.WEEKENDS -> nextMatchingDay(cal) { it == Calendar.THURSDAY || it == Calendar.FRIDAY }
            RepeatPattern.CUSTOM -> {
                val days = reminder.customRepeatDays.split(",").mapNotNull { it.trim().toIntOrNull() }.toSet()
                if (days.isEmpty()) null else nextMatchingDay(cal) { (it - Calendar.SUNDAY) in days }
            }
            RepeatPattern.CUSTOM_INTERVAL -> {
                if (reminder.repeatIntervalDays <= 0) null
                else { cal.add(Calendar.DAY_OF_YEAR, reminder.repeatIntervalDays); cal.timeInMillis }
            }
        }
    }

    // Iran's work week is Saturday-Wednesday (Thu/Fri weekend), unlike the Mon-Fri
    // default most reminder apps assume - this is the whole point of having our own
    // WEEKDAYS/WEEKENDS logic instead of reusing a generic library's.
    private fun nextMatchingDay(cal: Calendar, matches: (Int) -> Boolean): Long {
        do { cal.add(Calendar.DAY_OF_YEAR, 1) } while (!matches(cal.get(Calendar.DAY_OF_WEEK)))
        return cal.timeInMillis
    }
}

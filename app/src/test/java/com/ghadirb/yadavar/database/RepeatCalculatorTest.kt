package com.ghadirb.yadavar.database

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class RepeatCalculatorTest {
    @Test
    fun iranWorkWeekIsSaturdayToWednesday() {
        assertTrue(RepeatCalculator.isIranWorkday(Calendar.SATURDAY))
        assertTrue(RepeatCalculator.isIranWorkday(Calendar.SUNDAY))
        assertTrue(RepeatCalculator.isIranWorkday(Calendar.WEDNESDAY))
        assertFalse(RepeatCalculator.isIranWorkday(Calendar.THURSDAY))
        assertFalse(RepeatCalculator.isIranWorkday(Calendar.FRIDAY))
    }

    @Test
    fun customIntervalAdvancesByMinutesForSubDayRepeats() {
        val start = Calendar.getInstance().apply { set(2026, Calendar.JANUARY, 1, 12, 30, 0) }.timeInMillis
        val reminder = ReminderEntity(
            title = "دارو",
            triggerTime = start,
            repeatPattern = RepeatPattern.CUSTOM_INTERVAL.name,
            repeatIntervalMinutes = 480
        )
        val next = RepeatCalculator.nextTrigger(reminder)
        val cal = Calendar.getInstance().apply { timeInMillis = next!! }
        assertTrue(cal.get(Calendar.HOUR_OF_DAY) == 20 && cal.get(Calendar.MINUTE) == 30)
    }
}

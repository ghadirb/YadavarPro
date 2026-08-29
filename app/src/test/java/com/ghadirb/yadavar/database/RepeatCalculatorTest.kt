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
}

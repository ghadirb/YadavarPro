package com.ghadirb.yadavar.assistant

import com.ghadirb.yadavar.database.RepeatPattern
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class ReminderNlpTest {

    @Test
    fun parsesMinutesWithPersianDigits() {
        val time = ReminderNlp.parseTime(ReminderNlp.normalize("۶ و ۴۲ دقیقه صبح"))!!
        assertEquals(6, time.hour)
        assertEquals(42, time.minute)
    }

    @Test
    fun parsesMinutesWithoutTheWordDaghighe() {
        val time = ReminderNlp.parseTime(ReminderNlp.normalize("6 و 42 صبح"))!!
        assertEquals(6, time.hour)
        assertEquals(42, time.minute)
    }

    @Test
    fun parsesSpokenNumberWords() {
        val time = ReminderNlp.parseTime(ReminderNlp.normalize("شش و چهل و دو دقیقه صبح"))!!
        assertEquals(6, time.hour)
        assertEquals(42, time.minute)
    }

    @Test
    fun createIntentKeepsMinutes() {
        val intent = ReminderNlp.parse("۶ و ۴۲ دقیقه صبح دارو بخور") as AssistantIntent.Create
        val cal = Calendar.getInstance().apply { timeInMillis = intent.triggerAt }
        assertEquals(6, cal.get(Calendar.HOUR_OF_DAY))
        assertEquals(42, cal.get(Calendar.MINUTE))
        assertEquals("دارو", intent.category)
        assertTrue(intent.title.contains("دارو"))
        assertTrue(!intent.title.contains("42"))
    }

    @Test
    fun saturdayToWednesdayIsWorkWeek() {
        val days = ReminderNlp.parseWeekdays(ReminderNlp.normalize("شنبه تا چهارشنبه"))
        assertEquals(setOf(6, 0, 1, 2, 3), days)
        val intent = ReminderNlp.parse("شنبه تا چهارشنبه ساعت ۸ جلسه") as AssistantIntent.Create
        assertEquals(RepeatPattern.WEEKDAYS, intent.repeat)
        val cal = Calendar.getInstance().apply { timeInMillis = intent.triggerAt }
        assertEquals(8, cal.get(Calendar.HOUR_OF_DAY))
        assertEquals(0, cal.get(Calendar.MINUTE))
    }

    @Test
    fun fromSaturdayToWednesday() {
        val days = ReminderNlp.parseWeekdays(ReminderNlp.normalize("از شنبه تا چهارشنبه"))
        assertEquals(setOf(6, 0, 1, 2, 3), days)
    }

    @Test
    fun refusesEverydayChat() {
        assertTrue(ReminderNlp.parse("یه جوک بگو") is AssistantIntent.OffTopic)
        assertTrue(ReminderNlp.parse("قیمت دلار چنده") is AssistantIntent.OffTopic)
    }
}

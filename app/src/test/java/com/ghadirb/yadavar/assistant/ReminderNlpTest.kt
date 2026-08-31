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

    @Test
    fun everyEightHoursStartsCountingFromNow() {
        val before = System.currentTimeMillis()
        val intent = ReminderNlp.parse("از الان هر ۸ ساعت یادآوری خوردن دارو کن") as AssistantIntent.Create
        val after = System.currentTimeMillis()
        assertEquals(RepeatPattern.CUSTOM_INTERVAL, intent.repeat)
        assertEquals(480, intent.intervalMinutes)
        // trigger should land ~8h after "now" at parse time, not at some fixed clock hour
        assertTrue(intent.triggerAt in (before + 480 * 60_000 - 5_000)..(after + 480 * 60_000 + 5_000))
        assertEquals("دارو", intent.category)
    }

    @Test
    fun everyThirtyMinutesIsRecognized() {
        val intent = ReminderNlp.parse("هر ۳۰ دقیقه آب بخور یادآوری کن") as AssistantIntent.Create
        assertEquals(RepeatPattern.CUSTOM_INTERVAL, intent.repeat)
        assertEquals(30, intent.intervalMinutes)
    }

    @Test
    fun withNoRealTimeSignalItDefersInsteadOfGuessing() {
        // No clock time, no weekday, no "tomorrow"/"tonight", no interval - previously this
        // silently defaulted to a fixed 9:00 and looked like a confident answer. It should
        // now decline locally so the caller can fall back to asking the cloud model.
        val intent = ReminderNlp.parse("یادآوری برای فلان کار مبهم")
        assertTrue(intent is AssistantIntent.ReminderQuestion)
    }

    @Test
    fun goingInFortyMinutesFromNow() {
        val before = System.currentTimeMillis()
        val intent = ReminderNlp.parse("یادآوری رفتن بعد از 40 دقیقه از الان") as AssistantIntent.Create
        val after = System.currentTimeMillis()
        assertEquals(RepeatPattern.ONCE, intent.repeat)
        assertEquals(0, intent.intervalMinutes)
        assertEquals("رفتن", intent.title)
        assertTrue(intent.triggerAt in (before + 40 * 60_000 - 5_000)..(after + 40 * 60_000 + 5_000))
    }

    @Test
    fun spokenFortyMinutesFromNow() {
        val before = System.currentTimeMillis()
        val intent = ReminderNlp.parse("یادآوری رفتن بعد از چهل دقیقه") as AssistantIntent.Create
        val after = System.currentTimeMillis()
        assertEquals("رفتن", intent.title)
        assertEquals(RepeatPattern.ONCE, intent.repeat)
        assertTrue(intent.triggerAt in (before + 40 * 60_000 - 5_000)..(after + 40 * 60_000 + 5_000))
    }

    @Test
    fun buyTicketHalfHourLater() {
        val before = System.currentTimeMillis()
        val intent = ReminderNlp.parse("یادآوری خرید بلیط نیم ساعت بعد") as AssistantIntent.Create
        val after = System.currentTimeMillis()
        assertEquals(RepeatPattern.ONCE, intent.repeat)
        assertEquals("خرید", intent.category)
        assertTrue(intent.title.contains("خرید"))
        assertTrue(intent.title.contains("بلیط"))
        assertTrue(!intent.title.contains("نیم"))
        assertTrue(intent.triggerAt in (before + 30 * 60_000 - 5_000)..(after + 30 * 60_000 + 5_000))
    }

    @Test
    fun pillEveryEightHoursWithoutAzAlan() {
        val before = System.currentTimeMillis()
        val intent = ReminderNlp.parse("یادآوری خوردن قرص هر هشت ساعت") as AssistantIntent.Create
        val after = System.currentTimeMillis()
        assertEquals(RepeatPattern.CUSTOM_INTERVAL, intent.repeat)
        assertEquals(480, intent.intervalMinutes)
        assertEquals("دارو", intent.category)
        assertTrue(intent.title.contains("قرص"))
        assertTrue(!intent.title.contains("هشت"))
        assertTrue(intent.triggerAt in (before + 480 * 60_000 - 5_000)..(after + 480 * 60_000 + 5_000))
    }

    @Test
    fun oneHourLaterSpoken() {
        val before = System.currentTimeMillis()
        val intent = ReminderNlp.parse("یادآوری تماس یه ساعت دیگه") as AssistantIntent.Create
        val after = System.currentTimeMillis()
        assertEquals(RepeatPattern.ONCE, intent.repeat)
        assertTrue(intent.title.contains("تماس"))
        assertTrue(intent.triggerAt in (before + 60 * 60_000 - 5_000)..(after + 60 * 60_000 + 5_000))
    }

    @Test
    fun clockHourIsNotARelativeOffset() {
        val intent = ReminderNlp.parse("شنبه تا چهارشنبه ساعت ۸ جلسه") as AssistantIntent.Create
        val cal = Calendar.getInstance().apply { timeInMillis = intent.triggerAt }
        assertEquals(8, cal.get(Calendar.HOUR_OF_DAY))
        assertEquals(0, cal.get(Calendar.MINUTE))
        assertEquals(RepeatPattern.WEEKDAYS, intent.repeat)
        assertEquals(0, intent.intervalMinutes)
    }

    @Test
    fun everyHalfHourInterval() {
        val intent = ReminderNlp.parse("یادآوری آب هر نیم ساعت") as AssistantIntent.Create
        assertEquals(RepeatPattern.CUSTOM_INTERVAL, intent.repeat)
        assertEquals(30, intent.intervalMinutes)
        assertTrue(intent.title.contains("آب"))
    }

    @Test
    fun minutesLaterWithoutTheWordBad() {
        val before = System.currentTimeMillis()
        val intent = ReminderNlp.parse("یادآوری خرید بلیط 30 دقیقه دیگه") as AssistantIntent.Create
        val after = System.currentTimeMillis()
        assertEquals(RepeatPattern.ONCE, intent.repeat)
        assertTrue(intent.title.contains("خرید"))
        assertTrue(intent.triggerAt in (before + 30 * 60_000 - 5_000)..(after + 30 * 60_000 + 5_000))
    }
}

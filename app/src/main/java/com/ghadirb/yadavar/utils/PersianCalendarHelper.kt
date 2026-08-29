package com.ghadirb.yadavar.utils

import java.util.Calendar

/**
 * Minimal, dependency-free Gregorian <-> Jalali (Persian) calendar conversion.
 * Specialized-feature note: this lets reminders (birthdays, anniversaries, bill due
 * dates) be entered and displayed in the Jalali calendar Iranian users actually use day
 * to day, and lets a YEARLY-repeat reminder correctly land on the same Jalali date each
 * year (which is NOT the same Gregorian date, since Jalali/Gregorian leap years don't
 * align) rather than drifting like a naive "+365 days" repeat would.
 */
object PersianCalendarHelper {

    data class JalaliDate(val year: Int, val month: Int, val day: Int)

    fun gregorianToJalali(gy: Int, gm: Int, gd: Int): JalaliDate {
        val gDaysInMonth = intArrayOf(31, 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31)
        var gy2 = if (gm > 2) gy + 1 else gy
        var days = 355666 + (365 * gy) + ((gy2 + 3) / 4) - ((gy2 + 99) / 100) +
                ((gy2 + 399) / 400) + gd + gDaysInMonth[gm - 1]
        if (gm > 2 && isGregorianLeap(gy)) days += 1

        var jy = -1595 + (33 * (days / 12053))
        days %= 12053
        jy += 4 * (days / 1461)
        days %= 1461
        if (days > 365) {
            jy += (days - 1) / 365
            days = (days - 1) % 365
        }
        val jm: Int
        val jd: Int
        if (days < 186) {
            jm = 1 + days / 31
            jd = 1 + days % 31
        } else {
            jm = 7 + (days - 186) / 30
            jd = 1 + (days - 186) % 30
        }
        return JalaliDate(jy, jm, jd)
    }

    fun jalaliToGregorian(jy: Int, jm: Int, jd: Int): Triple<Int, Int, Int> {
        val jy2 = jy + 1595
        var days = -355668 + (365 * jy2) + ((jy2 / 33) * 8) + (((jy2 % 33) + 3) / 4) +
                jd + if (jm < 7) (jm - 1) * 31 else ((jm - 7) * 30) + 186

        var gy = 400 * (days / 146097)
        days %= 146097
        if (days > 36524) {
            gy += 100 * (--days / 36524)
            days %= 36524
            if (days >= 365) days++
        }
        gy += 4 * (days / 1461)
        days %= 1461
        if (days > 365) {
            gy += (days - 1) / 365
            days = (days - 1) % 365
        }
        var gd = days + 1
        val gDaysInMonth = intArrayOf(31, 29, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31)
        var gm = 0
        for (i in 0..11) {
            val monthLen = if (i == 1 && isGregorianLeap(gy)) 29 else gDaysInMonth[i]
            if (gd <= monthLen) { gm = i + 1; break }
            gd -= monthLen
        }
        return Triple(gy, gm, gd)
    }

    fun nowAsJalali(): JalaliDate {
        val c = Calendar.getInstance()
        return gregorianToJalali(c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH))
    }

    fun format(j: JalaliDate): String {
        val months = listOf(
            "فروردین", "اردیبهشت", "خرداد", "تیر", "مرداد", "شهریور",
            "مهر", "آبان", "آذر", "دی", "بهمن", "اسفند"
        )
        return "${j.day} ${months[j.month - 1]} ${j.year}"
    }

    fun isJalaliLeap(year: Int): Boolean {
        val r = year % 33
        return r == 1 || r == 5 || r == 9 || r == 13 || r == 17 || r == 22 || r == 26 || r == 30
    }

    fun daysInJalaliMonth(year: Int, month: Int): Int = when {
        month in 1..6 -> 31
        month in 7..11 -> 30
        else -> if (isJalaliLeap(year)) 30 else 29
    }

    private fun isGregorianLeap(year: Int): Boolean =
        (year % 4 == 0 && year % 100 != 0) || (year % 400 == 0)

    /** Adds [years] Jalali years to a Gregorian epoch-millis timestamp, keeping time-of-day. */
    fun addJalaliYears(epochMillis: Long, years: Int): Long {
        val cal = Calendar.getInstance().apply { timeInMillis = epochMillis }
        val j = gregorianToJalali(cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1, cal.get(Calendar.DAY_OF_MONTH))
        val (gy, gm, gd) = jalaliToGregorian(j.year + years, j.month, j.day)
        cal.set(gy, gm - 1, gd)
        return cal.timeInMillis
    }
}

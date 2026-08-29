package com.ghadirb.yadavar.utils

import com.ghadirb.yadavar.database.AlertType
import com.ghadirb.yadavar.database.Priority
import com.ghadirb.yadavar.database.ReminderType
import com.ghadirb.yadavar.database.RepeatPattern

data class LabeledOption(val value: String, val label: String) {
    override fun toString(): String = label
}

object EnumLabels {
    fun reminderType(name: String) = when (name) {
        "SIMPLE" -> "ساده"
        "RECURRING" -> "تکراری"
        "LOCATION_BASED" -> "مکانی"
        "BIRTHDAY" -> "تولد"
        "ANNIVERSARY" -> "سالگرد"
        "BILL_PAYMENT" -> "پرداخت قبض"
        "SUBSCRIPTION" -> "اشتراک"
        "MEDICINE" -> "دارو"
        "TASK" -> "کار"
        "CONDITIONAL" -> "شرطی"
        else -> name
    }

    fun priority(name: String) = when (name) {
        "LOW" -> "کم"
        "MEDIUM" -> "متوسط"
        "HIGH" -> "زیاد"
        "CRITICAL" -> "بحرانی"
        else -> name
    }

    fun alertType(name: String) = when (name) {
        "NONE" -> "بدون هشدار"
        "NOTIFICATION" -> "نوتیفیکیشن"
        "FULL_SCREEN" -> "تمام‌صفحه"
        "SMART" -> "هوشمند (گفتار)"
        else -> name
    }

    fun repeat(name: String) = when (name) {
        "ONCE" -> "یک‌بار"
        "DAILY" -> "روزانه"
        "WEEKLY" -> "هفتگی"
        "MONTHLY" -> "ماهانه"
        "YEARLY" -> "سالانه"
        "WEEKDAYS" -> "روزهای کاری (شنبه تا چهارشنبه)"
        "WEEKENDS" -> "آخر هفته (پنجشنبه و جمعه)"
        "CUSTOM" -> "روزهای انتخابی هفته"
        "CUSTOM_INTERVAL" -> "هر چند روز یک‌بار"
        else -> name
    }

    fun reminderTypes() = ReminderType.entries.map { LabeledOption(it.name, reminderType(it.name)) }
    fun priorities() = Priority.entries.map { LabeledOption(it.name, priority(it.name)) }
    fun alertTypes() = AlertType.entries.map { LabeledOption(it.name, alertType(it.name)) }
    fun repeats() = RepeatPattern.entries.map { LabeledOption(it.name, repeat(it.name)) }

    val weekdays = listOf(
        LabeledOption("6", "شنبه"),
        LabeledOption("0", "یکشنبه"),
        LabeledOption("1", "دوشنبه"),
        LabeledOption("2", "سه‌شنبه"),
        LabeledOption("3", "چهارشنبه"),
        LabeledOption("4", "پنجشنبه"),
        LabeledOption("5", "جمعه")
    )
}

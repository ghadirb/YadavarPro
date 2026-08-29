package com.ghadirb.yadavar.utils

import com.ghadirb.yadavar.database.CategoryCatalog
import com.ghadirb.yadavar.database.Priority
import com.ghadirb.yadavar.database.ReminderEntity
import com.ghadirb.yadavar.database.RepeatPattern
import java.util.Calendar

data class ReminderTemplate(
    val id: String,
    val title: String,
    val description: String,
    val category: String,
    val hour: Int,
    val minute: Int,
    val repeat: RepeatPattern,
    val priority: Priority = Priority.MEDIUM
)

object ReminderTemplates {
    val all = listOf(
        ReminderTemplate("workout", "ورزش روزانه", "۳۰ دقیقه پیاده‌روی یا ورزش", "سلامت", 7, 0, RepeatPattern.DAILY),
        ReminderTemplate("medicine_am", "داروی صبح", "مصرف دارو طبق نسخه", "دارو", 8, 0, RepeatPattern.DAILY, Priority.HIGH),
        ReminderTemplate("medicine_pm", "داروی شب", "مصرف دارو طبق نسخه", "دارو", 20, 0, RepeatPattern.DAILY, Priority.HIGH),
        ReminderTemplate("bill", "پرداخت قبض", "یادآوری قبض ماهانه", "قبض", 10, 0, RepeatPattern.MONTHLY, Priority.HIGH),
        ReminderTemplate("shopping", "خرید هفتگی", "لیست خرید خانه", "خرید", 18, 0, RepeatPattern.WEEKLY),
        ReminderTemplate("meeting", "جلسه کاری", "آماده‌سازی قبل از جلسه", "کار", 9, 0, RepeatPattern.WEEKDAYS, Priority.HIGH),
        ReminderTemplate("water", "نوشیدن آب", "یک لیوان آب", "سلامت", 11, 0, RepeatPattern.DAILY, Priority.LOW),
        ReminderTemplate("birthday", "تولد", "یادآوری تولد سالانه شمسی", "شخصی", 9, 0, RepeatPattern.YEARLY)
    )

    fun toReminder(template: ReminderTemplate): ReminderEntity {
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, template.hour)
            set(Calendar.MINUTE, template.minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (timeInMillis <= System.currentTimeMillis()) add(Calendar.DAY_OF_YEAR, 1)
        }
        return ReminderEntity(
            title = template.title,
            description = template.description,
            category = template.category,
            categoryColor = CategoryCatalog.colorFor(template.category),
            triggerTime = cal.timeInMillis,
            repeatPattern = template.repeat.name,
            priority = template.priority.name
        )
    }
}

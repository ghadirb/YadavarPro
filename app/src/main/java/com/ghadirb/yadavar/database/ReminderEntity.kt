package com.ghadirb.yadavar.database

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Core reminder record. Field set is a superset of what Maliar-Pro's reminder section
 * used internally (ReminderType/AlertType/RepeatPattern kept the same names so any data
 * migrated over stays compatible), generalized to drop the Maliar-specific
 * linkedCheckId/linkedInstallmentId finance links and add fields for the new
 * subscription-tracking and category-color features.
 */
@Entity(tableName = "reminders")
data class ReminderEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val title: String,
    val description: String = "",
    val reminderType: String = ReminderType.SIMPLE.name,
    val priority: String = Priority.MEDIUM.name,
    val alertType: String = AlertType.NOTIFICATION.name,
    val triggerTime: Long,
    val repeatPattern: String = RepeatPattern.ONCE.name,
    /** Used when repeatPattern is CUSTOM_INTERVAL: run again after this many calendar days
     *  (ported from Maliar-Pro's codex/reminder-finalize-v2 "interval reminders" feature). */
    val repeatIntervalDays: Int = 0,
    val customRepeatDays: String = "", // comma-separated weekday indices: "0,1,2,3,4,5,6"

    // Location-based reminders
    val locationLat: Double? = null,
    val locationLng: Double? = null,
    val locationRadius: Int = 100,
    val locationName: String = "",

    // Subscription / recurring-payment reminders (generalized version of Maliar-Pro's
    // SubscriptionReminderWorker - now a first-class reminder type instead of a bolt-on)
    val subscriptionAmount: Double? = null,
    val subscriptionCurrency: String = "IRR",
    val subscriptionCycleDays: Int? = null,

    val isCompleted: Boolean = false,
    val completedAt: Long? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val tags: String = "",
    val category: String = "",
    val categoryColor: String = "#6D5EF5",

    val relatedPerson: String = "",
    val contactName: String = "",
    val contactPhoneNumber: String = "",

    val snoozeCount: Int = 0,
    val lastSnoozed: Long? = null,
    val notes: String = "",

    /** Per-reminder sound: DEFAULT_ALARM, "RAW:<res name>" for a built-in Persian voice
     *  clip, or a persisted content:// URI for a sound picked from the device (see
     *  ReminderSound.kt) - ported from the same Maliar-Pro branch. */
    val soundUri: String = "DEFAULT_ALARM",

    /** If true, this reminder still rings during quiet hours / focus mode. */
    val bypassQuietHours: Boolean = false
)

enum class ReminderType {
    SIMPLE, RECURRING, LOCATION_BASED, BIRTHDAY, ANNIVERSARY,
    BILL_PAYMENT, SUBSCRIPTION, MEDICINE, TASK, CONDITIONAL
}

enum class Priority { LOW, MEDIUM, HIGH, CRITICAL }

enum class AlertType { NONE, NOTIFICATION, FULL_SCREEN, SMART }

enum class RepeatPattern { ONCE, DAILY, WEEKLY, MONTHLY, YEARLY, WEEKDAYS, WEEKENDS, CUSTOM, CUSTOM_INTERVAL }

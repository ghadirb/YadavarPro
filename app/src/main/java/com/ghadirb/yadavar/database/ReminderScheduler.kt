package com.ghadirb.yadavar.database

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.ghadirb.yadavar.receivers.ReminderAlarmReceiver
import kotlinx.coroutines.flow.first

object ReminderScheduler {

    private fun pendingIntent(context: Context, reminder: ReminderEntity): PendingIntent {
        val intent = Intent(context, ReminderAlarmReceiver::class.java).apply {
            putExtra("reminder_id", reminder.id)
        }
        return PendingIntent.getBroadcast(
            context,
            reminder.id.toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    fun schedule(context: Context, reminder: ReminderEntity) {
        if (reminder.reminderType == ReminderType.LOCATION_BASED.name) {
            GeofenceHelper.registerGeofence(context, reminder)
            return
        }
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pi = pendingIntent(context, reminder)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
            // Falls back to an inexact alarm rather than crashing; MainActivity should
            // prompt the user to grant "Alarms & reminders" so this path is rarely hit.
            alarmManager.set(AlarmManager.RTC_WAKEUP, reminder.triggerTime, pi)
            return
        }
        alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, reminder.triggerTime, pi)
    }

    fun cancel(context: Context, reminder: ReminderEntity) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarmManager.cancel(pendingIntent(context, reminder))
        if (reminder.reminderType == ReminderType.LOCATION_BASED.name) {
            GeofenceHelper.removeGeofence(context, reminder)
        }
    }

    /** Called from BootReceiver and from app start to re-arm every pending alarm. */
    suspend fun rescheduleAll(context: Context) {
        val dao = AppDatabase.getInstance(context).reminderDao()
        dao.getActive().first().forEach { schedule(context, it) }
    }
}

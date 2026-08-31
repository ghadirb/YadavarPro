package com.ghadirb.yadavar.database

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.ghadirb.yadavar.receivers.ReminderAlarmReceiver
import com.ghadirb.yadavar.ui.MainActivity
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

    private fun clockShowIntent(context: Context, reminder: ReminderEntity): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return PendingIntent.getActivity(
            context,
            (reminder.id xor 0x5A5A0000L).toInt(),
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

        // setAlarmClock is the only AlarmManager API that fires at the wall-clock time
        // even in Doze. setExactAndAllowWhileIdle is still batched ~30–90s (often a full
        // minute) on Samsung / Xiaomi / ColorOS, which is why reminders felt one minute late.
        try {
            alarmManager.setAlarmClock(
                AlarmManager.AlarmClockInfo(reminder.triggerTime, clockShowIntent(context, reminder)),
                pi
            )
            return
        } catch (_: SecurityException) {
            // OEM blocked alarm-clock APIs; fall through to exact / inexact.
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
            // Inexact fallback: the system may delay this. MainActivity prompts for
            // "Alarms & reminders" so this path should be rare.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, reminder.triggerTime, pi)
            } else {
                alarmManager.set(AlarmManager.RTC_WAKEUP, reminder.triggerTime, pi)
            }
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

package com.ghadirb.yadavar.utils

import android.app.AlarmManager
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.ghadirb.yadavar.database.Priority
import com.ghadirb.yadavar.database.ReminderEntity
import com.ghadirb.yadavar.receivers.QuietHoursReceiver
import java.util.Calendar

/**
 * App-level quiet hours (always works) plus optional system DND when the user
 * has granted ACCESS_NOTIFICATION_POLICY.
 */
object QuietHoursManager {

    const val ACTION_START = "com.ghadirb.yadavar.QUIET_START"
    const val ACTION_END = "com.ghadirb.yadavar.QUIET_END"

    fun isQuietNow(prefs: PreferencesManager): Boolean {
        if (prefs.focusModeEnabled) return true
        if (!prefs.quietHoursEnabled) return false
        val now = Calendar.getInstance()
        val current = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE)
        val start = prefs.quietStartMinutes
        val end = prefs.quietEndMinutes
        return if (start == end) false
        else if (start < end) current in start until end
        else current >= start || current < end
    }

    fun shouldMuteSound(prefs: PreferencesManager, reminder: ReminderEntity): Boolean {
        if (!isQuietNow(prefs)) return false
        if (reminder.bypassQuietHours) return false
        val critical = reminder.priority == Priority.CRITICAL.name
        if (critical && prefs.allowCriticalInQuiet) return false
        return when (prefs.quietMode) {
            QuietMode.SILENT -> true
            QuietMode.VIBRATE -> true
            QuietMode.PRIORITY -> reminder.priority == Priority.LOW.name || reminder.priority == Priority.MEDIUM.name
        }
    }

    fun vibrateOnly(prefs: PreferencesManager, reminder: ReminderEntity): Boolean {
        if (!isQuietNow(prefs)) return false
        if (reminder.bypassQuietHours) return false
        return prefs.quietMode == QuietMode.VIBRATE
    }

    fun schedule(context: Context) {
        val prefs = PreferencesManager(context)
        val alarm = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        scheduleEdge(context, alarm, ACTION_START, prefs.quietStartMinutes, 1001)
        scheduleEdge(context, alarm, ACTION_END, prefs.quietEndMinutes, 1002)
    }

    private fun scheduleEdge(
        context: Context,
        alarm: AlarmManager,
        action: String,
        minutes: Int,
        requestCode: Int
    ) {
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, minutes / 60)
            set(Calendar.MINUTE, minutes % 60)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (timeInMillis <= System.currentTimeMillis()) add(Calendar.DAY_OF_YEAR, 1)
        }
        val pi = PendingIntent.getBroadcast(
            context,
            requestCode,
            Intent(context, QuietHoursReceiver::class.java).setAction(action),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarm.canScheduleExactAlarms()) {
            alarm.set(AlarmManager.RTC_WAKEUP, cal.timeInMillis, pi)
        } else {
            alarm.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, cal.timeInMillis, pi)
        }
    }

    fun applySystemDnd(context: Context, enable: Boolean) {
        val prefs = PreferencesManager(context)
        if (!prefs.applySystemDnd) return
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && nm.isNotificationPolicyAccessGranted) {
            nm.setInterruptionFilter(
                if (enable) NotificationManager.INTERRUPTION_FILTER_PRIORITY
                else NotificationManager.INTERRUPTION_FILTER_ALL
            )
        }
    }

    fun hasPolicyAccess(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return true
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        return nm.isNotificationPolicyAccessGranted
    }
}

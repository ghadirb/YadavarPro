package com.ghadirb.yadavar.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.ghadirb.yadavar.database.AppDatabase
import com.ghadirb.yadavar.database.ReminderScheduler
import com.ghadirb.yadavar.database.RepeatCalculator
import com.ghadirb.yadavar.utils.NotificationHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class ReminderAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val reminderId = intent.getLongExtra("reminder_id", -1)
        if (reminderId == -1L) return

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO + SupervisorJob()).launch {
            try {
                val dao = AppDatabase.getInstance(context).reminderDao()
                val reminder = dao.getById(reminderId) ?: return@launch
                if (reminder.isCompleted) return@launch

                NotificationHelper.show(context, reminder)

                // Recurring reminders re-arm themselves for the next occurrence instead of
                // being marked done.
                val next = RepeatCalculator.nextTrigger(reminder)
                if (next != null) {
                    val updated = reminder.copy(triggerTime = next)
                    dao.update(updated)
                    ReminderScheduler.schedule(context, updated)
                }
            } finally {
                pendingResult.finish()
            }
        }
    }
}

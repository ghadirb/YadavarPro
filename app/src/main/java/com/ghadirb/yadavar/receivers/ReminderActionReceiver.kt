package com.ghadirb.yadavar.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import com.ghadirb.yadavar.database.AppDatabase
import com.ghadirb.yadavar.utils.NotificationHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

class ReminderActionReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_DONE = "com.ghadirb.yadavar.ACTION_DONE"
        const val ACTION_SNOOZE = "com.ghadirb.yadavar.ACTION_SNOOZE"
        const val ACTION_CALL = "com.ghadirb.yadavar.ACTION_CALL"
        const val DEFAULT_SNOOZE_MINUTES = 10L
    }

    override fun onReceive(context: Context, intent: Intent) {
        val reminderId = intent.getLongExtra("reminder_id", -1)
        if (reminderId == -1L) return
        val pendingResult = goAsync()

        CoroutineScope(Dispatchers.IO + SupervisorJob()).launch {
            try {
                val dao = AppDatabase.getInstance(context).reminderDao()
                when (intent.action) {
                    ACTION_DONE -> {
                        dao.markCompleted(reminderId)
                        NotificationHelper.dismiss(context, reminderId)
                    }
                    ACTION_SNOOZE -> {
                        val newTime = System.currentTimeMillis() + TimeUnit.MINUTES.toMillis(DEFAULT_SNOOZE_MINUTES)
                        dao.snooze(reminderId, newTime)
                        val updated = dao.getById(reminderId)
                        if (updated != null) {
                            com.ghadirb.yadavar.database.ReminderScheduler.schedule(context, updated)
                        }
                        NotificationHelper.dismiss(context, reminderId)
                    }
                    ACTION_CALL -> {
                        val reminder = dao.getById(reminderId)
                        if (reminder != null && reminder.contactPhoneNumber.isNotBlank()) {
                            val callIntent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:${reminder.contactPhoneNumber}")).apply {
                                flags = Intent.FLAG_ACTIVITY_NEW_TASK
                            }
                            context.startActivity(callIntent)
                        }
                        NotificationHelper.dismiss(context, reminderId)
                    }
                }
            } finally {
                pendingResult.finish()
            }
        }
    }
}

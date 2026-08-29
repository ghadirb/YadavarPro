package com.ghadirb.yadavar

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import com.ghadirb.yadavar.database.ReminderScheduler
import com.ghadirb.yadavar.utils.QuietHoursManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class YadavarApplication : Application() {

    companion object {
        const val REMINDER_CHANNEL_ID = "reminder_channel"
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        QuietHoursManager.schedule(applicationContext)
        CoroutineScope(Dispatchers.IO + SupervisorJob()).launch {
            ReminderScheduler.rescheduleAll(applicationContext)
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                REMINDER_CHANNEL_ID,
                getString(R.string.reminder_channel_name),
                NotificationManager.IMPORTANCE_HIGH
            ).apply { description = getString(R.string.reminder_channel_description) }
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }
}

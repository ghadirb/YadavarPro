package com.ghadirb.yadavar.receivers

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.ghadirb.yadavar.R
import com.ghadirb.yadavar.utils.PreferencesManager
import com.ghadirb.yadavar.utils.QuietHoursManager

class QuietHoursReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val prefs = PreferencesManager(context)
        val starting = intent.action == QuietHoursManager.ACTION_START
        if (prefs.quietHoursEnabled) {
            QuietHoursManager.applySystemDnd(context, starting)
        }
        if (prefs.quietHoursAnnounce) {
            announce(context, starting)
        }
        QuietHoursManager.schedule(context)
    }

    private fun announce(context: Context, starting: Boolean) {
        val channelId = "quiet_hours_status"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = context.getSystemService(NotificationManager::class.java)
            if (nm.getNotificationChannel(channelId) == null) {
                nm.createNotificationChannel(
                    NotificationChannel(
                        channelId,
                        context.getString(R.string.quiet_hours_channel),
                        NotificationManager.IMPORTANCE_DEFAULT
                    )
                )
            }
        }
        val title = if (starting) context.getString(R.string.quiet_hours_started)
        else context.getString(R.string.quiet_hours_ended)
        val body = if (starting) context.getString(R.string.quiet_hours_started_body)
        else context.getString(R.string.quiet_hours_ended_body)
        val n = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_notification_reminder)
            .setContentTitle(title)
            .setContentText(body)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(context).notify(9001, n)
    }
}

package com.ghadirb.yadavar.services

import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.ghadirb.yadavar.R
import com.ghadirb.yadavar.YadavarApplication
import com.ghadirb.yadavar.ui.MainActivity

class YadavarBackgroundService : Service() {

    companion object {
        const val NOTIFICATION_ID = 9001
        const val ACTION_START = "com.ghadirb.yadavar.action.START_BACKGROUND"
        const val ACTION_STOP = "com.ghadirb.yadavar.action.STOP_BACKGROUND"

        fun start(context: Context) {
            val intent = Intent(context, YadavarBackgroundService::class.java).apply { action = ACTION_START }
            ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context) {
            val intent = Intent(context, YadavarBackgroundService::class.java).apply { action = ACTION_STOP }
            context.startService(intent)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }
            else -> startForeground(NOTIFICATION_ID, buildNotification())
        }
        return START_STICKY
    }

    private fun buildNotification(): android.app.Notification {
        val open = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pi = PendingIntent.getActivity(this, 0, open, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        return NotificationCompat.Builder(this, YadavarApplication.BACKGROUND_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_reminder)
            .setContentTitle(getString(R.string.bg_running_title))
            .setContentText(getString(R.string.bg_running_body))
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setOngoing(true)
            .setSilent(true)
            .setContentIntent(pi)
            .build()
    }

    override fun onBind(intent: Intent?) = null
}

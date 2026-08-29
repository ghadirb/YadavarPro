package com.ghadirb.yadavar.utils

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.ghadirb.yadavar.R
import com.ghadirb.yadavar.YadavarApplication
import com.ghadirb.yadavar.database.ReminderEntity
import com.ghadirb.yadavar.receivers.ReminderActionReceiver
import com.ghadirb.yadavar.ui.reminders.FullScreenAlarmActivity

object NotificationHelper {

    private fun actionPendingIntent(context: Context, reminder: ReminderEntity, action: String): PendingIntent {
        val intent = Intent(context, ReminderActionReceiver::class.java).apply {
            this.action = action
            putExtra("reminder_id", reminder.id)
        }
        return PendingIntent.getBroadcast(
            context, "$action${reminder.id}".hashCode(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    // On Android O+ a notification's sound is fixed by its NotificationChannel, not by
    // Notification.Builder.setSound() (which is silently ignored there) - so a per-reminder
    // custom sound needs its own channel, one per distinct soundUri value, created lazily.
    private fun channelIdFor(context: Context, soundValue: String): String {
        val channelId = "reminder_sound_${soundValue.hashCode()}"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(NotificationManager::class.java)
            if (manager.getNotificationChannel(channelId) == null) {
                val channel = NotificationChannel(channelId, context.getString(R.string.reminder_channel_name), NotificationManager.IMPORTANCE_HIGH)
                val uri = ReminderSound.toUri(context, soundValue)
                if (uri != null) {
                    val attrs = android.media.AudioAttributes.Builder()
                        .setUsage(android.media.AudioAttributes.USAGE_ALARM)
                        .build()
                    channel.setSound(uri, attrs)
                }
                manager.createNotificationChannel(channel)
            }
        }
        return channelId
    }

    fun show(context: Context, reminder: ReminderEntity) {
        if (reminder.alertType == "FULL_SCREEN") {
            val fullScreenIntent = Intent(context, FullScreenAlarmActivity::class.java).apply {
                putExtra("reminder_id", reminder.id)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(fullScreenIntent)
            return
        }

        val channelId = channelIdFor(context, reminder.soundUri)
        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_notification_reminder)
            .setContentTitle(reminder.title)
            .setContentText(reminder.description.ifBlank { reminder.notes })
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setSound(ReminderSound.toUri(context, reminder.soundUri))
            .addAction(R.drawable.ic_check, context.getString(R.string.action_done), actionPendingIntent(context, reminder, ReminderActionReceiver.ACTION_DONE))
            .addAction(R.drawable.ic_snooze, context.getString(R.string.action_snooze), actionPendingIntent(context, reminder, ReminderActionReceiver.ACTION_SNOOZE))

        if (reminder.contactPhoneNumber.isNotBlank()) {
            builder.addAction(R.drawable.ic_call, context.getString(R.string.action_call), actionPendingIntent(context, reminder, ReminderActionReceiver.ACTION_CALL))
        }

        NotificationManagerCompat.from(context).notify(reminder.id.toInt(), builder.build())
    }

    fun dismiss(context: Context, reminderId: Long) {
        NotificationManagerCompat.from(context).cancel(reminderId.toInt())
    }
}


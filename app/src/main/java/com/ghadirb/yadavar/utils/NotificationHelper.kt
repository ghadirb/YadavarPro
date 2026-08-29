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
import com.ghadirb.yadavar.database.ReminderEntity
import com.ghadirb.yadavar.receivers.ReminderActionReceiver
import com.ghadirb.yadavar.ui.reminders.FullScreenAlarmActivity
import com.ghadirb.yadavar.ui.subscription.SubscriptionActivity

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

    private fun channelIdFor(context: Context, soundValue: String, silent: Boolean, vibrate: Boolean): String {
        val channelId = when {
            silent && vibrate -> "reminder_vibrate_${soundValue.hashCode()}"
            silent -> "reminder_silent_${soundValue.hashCode()}"
            else -> "reminder_sound_${soundValue.hashCode()}"
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(NotificationManager::class.java)
            if (manager.getNotificationChannel(channelId) == null) {
                val importance = if (silent && !vibrate) NotificationManager.IMPORTANCE_LOW
                else NotificationManager.IMPORTANCE_HIGH
                val channel = NotificationChannel(
                    channelId,
                    context.getString(R.string.reminder_channel_name),
                    importance
                )
                if (silent) {
                    channel.setSound(null, null)
                    if (vibrate) channel.vibrationPattern = longArrayOf(0, 180, 80, 180)
                    else channel.enableVibration(false)
                } else {
                    val uri = ReminderSound.toUri(context, soundValue)
                    if (uri != null) {
                        val attrs = android.media.AudioAttributes.Builder()
                            .setUsage(android.media.AudioAttributes.USAGE_ALARM)
                            .build()
                        channel.setSound(uri, attrs)
                    }
                }
                manager.createNotificationChannel(channel)
            }
        }
        return channelId
    }

    fun show(context: Context, reminder: ReminderEntity) {
        if (reminder.alertType == "FULL_SCREEN") {
            val prefs = PreferencesManager(context)
            if (!QuietHoursManager.shouldMuteSound(prefs, reminder)) {
                val fullScreenIntent = Intent(context, FullScreenAlarmActivity::class.java).apply {
                    putExtra("reminder_id", reminder.id)
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(fullScreenIntent)
                return
            }
        }

        val prefs = PreferencesManager(context)
        val mute = QuietHoursManager.shouldMuteSound(prefs, reminder)
        val vibrate = QuietHoursManager.vibrateOnly(prefs, reminder)
        val channelId = channelIdFor(context, reminder.soundUri, mute, vibrate)
        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_notification_reminder)
            .setContentTitle(reminder.title)
            .setContentText(reminder.description.ifBlank { reminder.notes })
            .setPriority(if (mute && !vibrate) NotificationCompat.PRIORITY_LOW else NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .addAction(R.drawable.ic_check, context.getString(R.string.action_done), actionPendingIntent(context, reminder, ReminderActionReceiver.ACTION_DONE))
            .addAction(R.drawable.ic_snooze, context.getString(R.string.action_snooze), actionPendingIntent(context, reminder, ReminderActionReceiver.ACTION_SNOOZE))

        if (!mute) {
            builder.setSound(ReminderSound.toUri(context, reminder.soundUri))
        } else if (vibrate) {
            builder.setVibrate(longArrayOf(0, 180, 80, 180))
            builder.setSilent(false)
        } else {
            builder.setSilent(true)
        }

        if (reminder.contactPhoneNumber.isNotBlank()) {
            builder.addAction(R.drawable.ic_call, context.getString(R.string.action_call), actionPendingIntent(context, reminder, ReminderActionReceiver.ACTION_CALL))
        }

        NotificationManagerCompat.from(context).notify(reminder.id.toInt(), builder.build())
    }

    fun dismiss(context: Context, reminderId: Long) {
        NotificationManagerCompat.from(context).cancel(reminderId.toInt())
    }

    private const val SUB_CHANNEL = "subscription_channel"
    private const val ID_QUOTA = 5001
    private const val ID_EXPIRY = 5002
    private const val ID_EXPIRED = 5003

    private fun ensureSubChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(NotificationManager::class.java)
            val channel = NotificationChannel(
                SUB_CHANNEL,
                context.getString(R.string.subscription_channel),
                NotificationManager.IMPORTANCE_HIGH
            )
            manager.createNotificationChannel(channel)
        }
    }

    private fun openSubscription(context: Context): PendingIntent {
        val intent = Intent(context, SubscriptionActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun showSub(context: Context, id: Int, title: String, text: String) {
        ensureSubChannel(context)
        val notification = NotificationCompat.Builder(context, SUB_CHANNEL)
            .setSmallIcon(R.drawable.ic_notification_reminder)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setAutoCancel(true)
            .setContentIntent(openSubscription(context))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(id, notification)
        } catch (_: SecurityException) {
        }
    }

    fun notifyQuotaExhausted(context: Context) {
        showSub(
            context, ID_QUOTA,
            context.getString(R.string.quota_exhausted_title),
            context.getString(R.string.quota_exhausted_body)
        )
    }

    fun notifyExpiryReminder(context: Context, daysLeft: Int) {
        showSub(
            context, ID_EXPIRY,
            context.getString(R.string.expiry_title),
            context.getString(R.string.expiry_body, daysLeft)
        )
    }

    fun notifyExpired(context: Context) {
        showSub(
            context, ID_EXPIRED,
            context.getString(R.string.expired_title),
            context.getString(R.string.expired_body)
        )
    }
}

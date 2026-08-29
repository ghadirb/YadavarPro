package com.ghadirb.yadavar.utils

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

class SubscriptionReminderWorker(context: Context, params: WorkerParameters) :
    CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        SubscriptionManager.refreshFromServer(applicationContext)
        val premiumUntil = PreferencesManager(applicationContext).getPremiumUntil()
        val now = System.currentTimeMillis()
        val daysLeft = ((premiumUntil - now) / (24 * 60 * 60 * 1000)).toInt()
        when {
            premiumUntil <= now -> NotificationHelper.notifyExpired(applicationContext)
            daysLeft in 0..SubscriptionManager.EXPIRY_REMINDER_DAYS_BEFORE ->
                NotificationHelper.notifyExpiryReminder(applicationContext, daysLeft.coerceAtLeast(1))
        }
        return Result.success()
    }
}

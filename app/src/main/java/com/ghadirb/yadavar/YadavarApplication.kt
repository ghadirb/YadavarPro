package com.ghadirb.yadavar

import android.app.Activity
import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import android.os.Bundle
import com.ghadirb.yadavar.database.ReminderScheduler
import com.ghadirb.yadavar.services.YadavarBackgroundService
import com.ghadirb.yadavar.utils.AutoProvisioningManager
import com.ghadirb.yadavar.utils.PreferencesManager
import com.ghadirb.yadavar.utils.QuietHoursManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicInteger

class YadavarApplication : Application() {

    companion object {
        const val REMINDER_CHANNEL_ID = "reminder_channel"
        const val BACKGROUND_CHANNEL_ID = "background_channel"
        const val SMART_TTS_CHANNEL_ID = "smart_tts_channel"

        private val startedActivityCount = AtomicInteger(0)
        fun isAppInForeground(): Boolean = startedActivityCount.get() > 0
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
        QuietHoursManager.schedule(applicationContext)
        registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
            override fun onActivityStarted(activity: Activity) { startedActivityCount.incrementAndGet() }
            override fun onActivityStopped(activity: Activity) { startedActivityCount.decrementAndGet() }
            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
            override fun onActivityResumed(activity: Activity) {}
            override fun onActivityPaused(activity: Activity) {}
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
            override fun onActivityDestroyed(activity: Activity) {}
        })
        CoroutineScope(Dispatchers.IO + SupervisorJob()).launch {
            ReminderScheduler.rescheduleAll(applicationContext)
            runCatching { AutoProvisioningManager.autoProvision(this@YadavarApplication) }
        }
        startBackgroundIfEnabled()
    }

    private fun startBackgroundIfEnabled() {
        if (!PreferencesManager(this).isBackgroundServiceEnabled()) return
        try {
            YadavarBackgroundService.start(this)
        } catch (_: Exception) {
        }
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(
                NotificationChannel(
                    REMINDER_CHANNEL_ID,
                    getString(R.string.reminder_channel_name),
                    NotificationManager.IMPORTANCE_HIGH
                ).apply { description = getString(R.string.reminder_channel_description) }
            )
            manager.createNotificationChannel(
                NotificationChannel(
                    BACKGROUND_CHANNEL_ID,
                    getString(R.string.bg_channel_name),
                    NotificationManager.IMPORTANCE_MIN
                ).apply {
                    description = getString(R.string.bg_channel_desc)
                    setShowBadge(false)
                }
            )
            manager.createNotificationChannel(
                NotificationChannel(
                    SMART_TTS_CHANNEL_ID,
                    getString(R.string.smart_tts_channel_name),
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = getString(R.string.smart_tts_channel_desc)
                    setSound(null, null)
                    enableVibration(false)
                    setShowBadge(false)
                }
            )
        }
    }
}

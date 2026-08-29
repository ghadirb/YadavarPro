package com.ghadirb.yadavar.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.ghadirb.yadavar.database.ReminderScheduler
import com.ghadirb.yadavar.services.YadavarBackgroundService
import com.ghadirb.yadavar.utils.PreferencesManager
import com.ghadirb.yadavar.utils.QuietHoursManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        QuietHoursManager.schedule(context)
        if (PreferencesManager(context).isBackgroundServiceEnabled()) {
            runCatching { YadavarBackgroundService.start(context) }
        }
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO + SupervisorJob()).launch {
            try {
                ReminderScheduler.rescheduleAll(context)
            } finally {
                pendingResult.finish()
            }
        }
    }
}

package com.ghadirb.yadavar.ui.reminders

import android.content.Intent
import android.media.RingtoneManager
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.ghadirb.yadavar.database.AppDatabase
import com.ghadirb.yadavar.databinding.ActivityFullScreenAlarmBinding
import com.ghadirb.yadavar.receivers.ReminderActionReceiver
import kotlinx.coroutines.launch

class FullScreenAlarmActivity : AppCompatActivity() {

    private lateinit var binding: ActivityFullScreenAlarmBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityFullScreenAlarmBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val reminderId = intent.getLongExtra("reminder_id", -1)
        lifecycleScope.launch {
            val reminder = AppDatabase.getInstance(applicationContext).reminderDao().getById(reminderId)
            binding.textTitle.text = reminder?.title.orEmpty()
            binding.textDescription.text = reminder?.description.orEmpty()
        }

        playAlarmFeedback()

        binding.buttonDismiss.setOnClickListener {
            sendActionBroadcast(reminderId, ReminderActionReceiver.ACTION_DONE)
            finish()
        }
        binding.buttonSnooze.setOnClickListener {
            sendActionBroadcast(reminderId, ReminderActionReceiver.ACTION_SNOOZE)
            finish()
        }
    }

    private fun sendActionBroadcast(reminderId: Long, action: String) {
        sendBroadcast(Intent(this, ReminderActionReceiver::class.java).apply {
            this.action = action
            putExtra("reminder_id", reminderId)
        })
    }

    private fun playAlarmFeedback() {
        try {
            RingtoneManager.getRingtone(this, RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)).play()
        } catch (_: Exception) { /* device has no alarm sound configured, ignore */ }

        val vibrator = getSystemService(Vibrator::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator?.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 500, 500), 0))
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        getSystemService(Vibrator::class.java)?.cancel()
    }
}

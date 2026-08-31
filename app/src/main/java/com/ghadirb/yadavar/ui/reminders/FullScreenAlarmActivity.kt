package com.ghadirb.yadavar.ui.reminders

import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.WindowManager
import kotlin.math.abs
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.ghadirb.yadavar.database.AppDatabase
import com.ghadirb.yadavar.databinding.ActivityFullScreenAlarmBinding
import com.ghadirb.yadavar.receivers.ReminderActionReceiver
import com.ghadirb.yadavar.receivers.SmartReminderTtsService
import com.ghadirb.yadavar.utils.PreferencesManager
import com.ghadirb.yadavar.utils.QuietHoursManager
import com.ghadirb.yadavar.utils.ReminderSound
import kotlinx.coroutines.launch

class FullScreenAlarmActivity : AppCompatActivity() {

    private lateinit var binding: ActivityFullScreenAlarmBinding
    private var mediaPlayer: MediaPlayer? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var reminderId: Long = -1
    private var isSmart = false
    private var userHandled = false

    /** Physical swipe right = dismiss, swipe left = snooze - additive to the buttons below,
     *  not a replacement, so the alarm is always dismissible even for someone who doesn't
     *  discover or trust the gesture. */
    private val gestureDetector by lazy {
        GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            // Returning true here is required: SimpleOnGestureListener.onDown() defaults to
            // false, which tells Android "I'm not interested in this touch," so the system
            // never delivers the follow-up MOVE/UP events and onFling() never fires. This was
            // the actual bug - the swipe hint text showed but the gesture silently did nothing.
            override fun onDown(e: MotionEvent): Boolean = true

            override fun onFling(
                e1: MotionEvent?,
                e2: MotionEvent,
                velocityX: Float,
                velocityY: Float
            ): Boolean {
                val start = e1 ?: return false
                val diffX = e2.x - start.x
                val diffY = e2.y - start.y
                if (abs(diffX) <= abs(diffY)) return false
                val thresholdPx = 100 * resources.displayMetrics.density
                if (abs(diffX) < thresholdPx || abs(velocityX) < 250) return false
                if (diffX > 0) {
                    userHandled = true
                    sendActionBroadcast(ReminderActionReceiver.ACTION_DONE)
                } else {
                    userHandled = true
                    sendActionBroadcast(ReminderActionReceiver.ACTION_SNOOZE)
                }
                finish()
                return true
            }
        })
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        }
        binding = ActivityFullScreenAlarmBinding.inflate(layoutInflater)
        setContentView(binding.root)

        reminderId = intent.getLongExtra("reminder_id", -1)
        val titleExtra = intent.getStringExtra("reminder_title")
        val descExtra = intent.getStringExtra("reminder_description")
        val spokenExtra = intent.getStringExtra("spoken_text")
        isSmart = intent.getStringExtra("alert_type") == "SMART"
        binding.textTitle.text = titleExtra.orEmpty()
        binding.textDescription.text = descExtra.orEmpty()
        binding.textHint.text = if (isSmart) getString(com.ghadirb.yadavar.R.string.smart_tts_playing) else getString(com.ghadirb.yadavar.R.string.alert_full_screen)
        binding.textHint.visibility = android.view.View.VISIBLE
        if (isSmart) {
            val spoken = spokenExtra?.takeIf { it.isNotBlank() }
                ?: SmartReminderTtsService.currentSpokenText(reminderId).ifBlank {
                    SmartReminderTtsService.spokenTemplate(titleExtra.orEmpty(), descExtra.orEmpty())
                }
            binding.textDescription.text = spoken
            SmartReminderTtsService.spokenTextListener = { id, text ->
                if (id == reminderId && text.isNotBlank()) {
                    runOnUiThread {
                        binding.textDescription.text = text
                        binding.textHint.text = getString(com.ghadirb.yadavar.R.string.smart_tts_playing)
                    }
                }
            }
        }
        @Suppress("ClickableViewAccessibility")
        binding.root.setOnTouchListener { _, event -> gestureDetector.onTouchEvent(event) }

        lifecycleScope.launch {
            val reminder = AppDatabase.getInstance(applicationContext).reminderDao().getById(reminderId)
            if (reminder != null) {
                binding.textTitle.text = reminder.title
                if (!isSmart) {
                    binding.textDescription.text = reminder.description
                }
                val mute = QuietHoursManager.shouldMuteSound(PreferencesManager(this@FullScreenAlarmActivity), reminder)
                if (!isSmart && !mute) playSound(reminder.soundUri)
                if (mute && QuietHoursManager.vibrateOnly(PreferencesManager(this@FullScreenAlarmActivity), reminder)) {
                    vibrate()
                } else if (!mute) {
                    vibrate()
                }
            } else if (!isSmart) {
                playSound(intent.getStringExtra("sound_uri"))
                vibrate()
            }
        }

        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        @Suppress("DEPRECATION")
        wakeLock = pm.newWakeLock(
            PowerManager.FULL_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP or PowerManager.ON_AFTER_RELEASE,
            "Yadavar:Alarm"
        )
        runCatching { wakeLock?.acquire(10 * 60 * 1000L) }

        binding.buttonDismiss.setOnClickListener {
            userHandled = true
            sendActionBroadcast(ReminderActionReceiver.ACTION_DONE)
            finish()
        }
        binding.buttonSnooze.setOnClickListener {
            userHandled = true
            sendActionBroadcast(ReminderActionReceiver.ACTION_SNOOZE)
            finish()
        }
    }

    private fun playSound(soundValue: String?) {
        try {
            val uri = ReminderSound.toUri(this, soundValue) ?: return
            mediaPlayer = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                setDataSource(this@FullScreenAlarmActivity, uri)
                isLooping = true
                prepare()
                start()
            }
        } catch (_: Exception) {
        }
    }

    private fun vibrate() {
        val vibrator = getSystemService(Vibrator::class.java) ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 500, 500, 500), 0))
        }
    }

    private fun sendActionBroadcast(action: String) {
        stopAlarm()
        sendBroadcast(Intent(this, ReminderActionReceiver::class.java).apply {
            this.action = action
            putExtra("reminder_id", reminderId)
        })
    }

    private fun stopAlarm() {
        runCatching {
            mediaPlayer?.apply { if (isPlaying) stop(); release() }
            mediaPlayer = null
        }
        runCatching { if (wakeLock?.isHeld == true) wakeLock?.release() }
        wakeLock = null
        getSystemService(Vibrator::class.java)?.cancel()
        // Keep the spoken loop running if the user just left the screen (Home, recents).
        // Only stop TTS when they actually dismiss or snooze.
        if (userHandled && reminderId > 0) SmartReminderTtsService.stop(reminderId)
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        // Must dismiss or snooze.
    }

    override fun onDestroy() {
        if (SmartReminderTtsService.spokenTextListener != null) {
            SmartReminderTtsService.spokenTextListener = null
        }
        super.onDestroy()
        if (isChangingConfigurations) return
        stopAlarm()
    }
}

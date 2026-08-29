package com.ghadirb.yadavar.receivers

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.util.Log
import androidx.core.app.NotificationCompat
import com.ghadirb.yadavar.R
import com.ghadirb.yadavar.YadavarApplication
import com.ghadirb.yadavar.ui.reminders.FullScreenAlarmActivity
import com.ghadirb.yadavar.utils.AIHelper
import com.ghadirb.yadavar.utils.PreferencesManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.util.Locale

class SmartReminderTtsService : Service() {

    private var tts: TextToSpeech? = null
    private val handler = Handler(Looper.getMainLooper())
    private var repeatCount = 0
    private var speakJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Main)
    private var currentReminderId: Long = -1
    private var cloudPlayer: MediaPlayer? = null
    private var audioFocusRequest: AudioFocusRequest? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        runningInstance = this
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val reminderId = intent?.getLongExtra("reminder_id", -1) ?: -1
        val title = intent?.getStringExtra("reminder_title") ?: "یادآوری"
        val description = intent?.getStringExtra("reminder_description") ?: ""
        if (reminderId <= 0) {
            stopSelfCompletely()
            return START_NOT_STICKY
        }
        try {
            if (currentReminderId != -1L && currentReminderId != reminderId) stopSpeakingLoop()
            currentReminderId = reminderId
            repeatCount = 0
            startForeground(NOTIFICATION_ID_BASE + reminderId.toInt(), buildNotification(reminderId, title))
        } catch (e: Exception) {
            Log.e(TAG, "startForeground failed", e)
            stopSelfCompletely()
            return START_NOT_STICKY
        }

        speakJob?.cancel()
        speakJob = scope.launch {
            val fallback = if (description.isBlank()) "یادآوری: $title" else "یادآوری: $title. $description"
            val natural = runCatching {
                kotlinx.coroutines.withTimeoutOrNull(4000L) {
                    AIHelper.generateText(
                        this@SmartReminderTtsService,
                        "یک جمله کوتاه محاوره‌ای فارسی برای یادآوری صوتی بساز. فقط همان جمله را بنویس.",
                        "عنوان: $title" + if (description.isNotBlank()) "\nتوضیح: $description" else ""
                    )
                }
            }.getOrNull()
            val textToSay = natural?.takeIf { it.isNotBlank() } ?: fallback
            val cloud = runCatching { AIHelper.synthesizeSpeech(this@SmartReminderTtsService, textToSay) }.getOrNull()
            requestAlarmAudioFocus()
            if (cloud != null) {
                playCloud(cloud.absolutePath)
            } else {
                initDeviceTts(textToSay)
            }
        }
        return START_STICKY
    }

    private fun playCloud(path: String) {
        if (currentReminderId == -1L) return
        if (repeatCount >= MAX_REPEATS) {
            stopSelfCompletely()
            return
        }
        repeatCount++
        try {
            cloudPlayer?.release()
            cloudPlayer = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                setDataSource(path)
                setOnCompletionListener {
                    handler.postDelayed({ if (currentReminderId != -1L) playCloud(path) }, REPEAT_INTERVAL_MS)
                }
                prepare()
                start()
            }
        } catch (e: Exception) {
            Log.e(TAG, "cloud play failed", e)
            initDeviceTts("یادآوری")
        }
    }

    private fun initDeviceTts(text: String) {
        tts = TextToSpeech(this) { status ->
            if (status != TextToSpeech.SUCCESS) return@TextToSpeech
            tts?.setLanguage(Locale("fa", "IR"))
            tts?.setSpeechRate(0.95f)
            tts?.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            speakOnce(text)
        }
    }

    private fun speakOnce(text: String) {
        if (currentReminderId == -1L) return
        if (repeatCount >= MAX_REPEATS) {
            stopSelfCompletely()
            return
        }
        repeatCount++
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "smart_$currentReminderId")
        handler.postDelayed({ if (currentReminderId != -1L) speakOnce(text) }, REPEAT_INTERVAL_MS)
    }

    private fun requestAlarmAudioFocus() {
        try {
            val am = getSystemService(AUDIO_SERVICE) as AudioManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val req = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_ALARM)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                            .build()
                    ).build()
                audioFocusRequest = req
                am.requestAudioFocus(req)
            }
        } catch (_: Exception) {
        }
    }

    private fun buildNotification(reminderId: Long, title: String): Notification {
        val mode = PreferencesManager(this).getNotificationMode()
        val fullScreen = Intent(this, FullScreenAlarmActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("reminder_id", reminderId)
            putExtra("reminder_title", title)
            putExtra("alert_type", "SMART")
        }
        val fullPi = PendingIntent.getActivity(
            this, reminderId.toInt(), fullScreen,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val done = Intent(this, ReminderActionReceiver::class.java).apply {
            action = ReminderActionReceiver.ACTION_DONE
            putExtra("reminder_id", reminderId)
        }
        val donePi = PendingIntent.getBroadcast(
            this, reminderId.toInt() + 9000, done,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val builder = NotificationCompat.Builder(this, YadavarApplication.REMINDER_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_reminder)
            .setContentTitle(title)
            .setOngoing(false)
            .setContentIntent(fullPi)
        when (mode) {
            "none" -> builder.setContentText("هشدار هوشمند در حال پخش است").setPriority(NotificationCompat.PRIORITY_LOW)
            "simple" -> builder.setContentText("در حال پخش صوتی یادآوری")
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setFullScreenIntent(fullPi, true)
            else -> builder.setContentText("برای توقف، انجام شد را بزنید")
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setFullScreenIntent(fullPi, true)
                .addAction(R.drawable.ic_check, getString(R.string.action_done), donePi)
        }
        return builder.build()
    }

    private fun stopSpeakingLoop() {
        handler.removeCallbacksAndMessages(null)
        speakJob?.cancel()
        runCatching { tts?.stop(); tts?.shutdown() }
        tts = null
        runCatching { cloudPlayer?.stop(); cloudPlayer?.release() }
        cloudPlayer = null
        try {
            val am = getSystemService(AUDIO_SERVICE) as AudioManager
            audioFocusRequest?.let {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) am.abandonAudioFocusRequest(it)
            }
        } catch (_: Exception) {
        }
        audioFocusRequest = null
        repeatCount = 0
    }

    private fun stopSelfCompletely() {
        stopSpeakingLoop()
        currentReminderId = -1
        runCatching { stopForeground(STOP_FOREGROUND_REMOVE) }
        stopSelf()
    }

    override fun onDestroy() {
        stopSpeakingLoop()
        if (runningInstance === this) runningInstance = null
        super.onDestroy()
    }

    companion object {
        private const val TAG = "SmartReminderTts"
        private const val NOTIFICATION_ID_BASE = 90000
        private const val REPEAT_INTERVAL_MS = 15000L
        private const val MAX_REPEATS = 8

        @Volatile
        private var runningInstance: SmartReminderTtsService? = null

        fun start(context: Context, reminderId: Long, title: String, description: String) {
            val intent = Intent(context, SmartReminderTtsService::class.java).apply {
                putExtra("reminder_id", reminderId)
                putExtra("reminder_title", title)
                putExtra("reminder_description", description)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(intent)
            else context.startService(intent)
        }

        fun stop(reminderId: Long = -1) {
            val instance = runningInstance ?: return
            if (reminderId == -1L || instance.currentReminderId == reminderId) instance.stopSelfCompletely()
        }
    }
}

package com.ghadirb.yadavar.receivers

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.speech.tts.TextToSpeech
import android.util.Log
import androidx.core.app.NotificationCompat
import com.ghadirb.yadavar.R
import com.ghadirb.yadavar.YadavarApplication
import com.ghadirb.yadavar.ui.reminders.FullScreenAlarmActivity
import com.ghadirb.yadavar.utils.AIBackendClient
import com.ghadirb.yadavar.utils.AIHelper
import com.ghadirb.yadavar.utils.ReminderSound
import com.ghadirb.yadavar.utils.SubscriptionManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale

/**
 * Spoken smart alert. The cloud TTS round-trip through Apps Script is often 10–20s,
 * so this service rings a local alarm immediately and swaps in the online voice
 * when (if) it arrives. Without the local fallback the user only sees a silent
 * foreground-service notification — which is what "هشدار هوشمند فقط نوتیفیکیشن داد"
 * was.
 */
class SmartReminderTtsService : Service() {

    private var tts: TextToSpeech? = null
    private val handler = Handler(Looper.getMainLooper())
    private var repeatCount = 0
    private var speakJob: Job? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var currentReminderId: Long = -1
    private var cloudPlayer: MediaPlayer? = null
    private var localPlayer: MediaPlayer? = null
    private var audioFocusRequest: AudioFocusRequest? = null
    private var pendingSpeakText: String = ""

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        runningInstance = this
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val reminderId = intent?.getLongExtra("reminder_id", -1) ?: -1
        val title = intent?.getStringExtra("reminder_title") ?: "یادآوری"
        val description = intent?.getStringExtra("reminder_description") ?: ""
        val soundUri = intent?.getStringExtra("sound_uri") ?: ReminderSound.DEFAULT_ALARM
        if (reminderId <= 0) {
            stopSelfCompletely()
            return START_NOT_STICKY
        }
        try {
            if (currentReminderId != -1L && currentReminderId != reminderId) stopSpeakingLoop()
            currentReminderId = reminderId
            repeatCount = 0
            val notification = buildNotification(reminderId, title)
            if (Build.VERSION.SDK_INT >= 34) {
                startForeground(
                    NOTIFICATION_ID_BASE + reminderId.toInt(),
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                )
            } else {
                startForeground(NOTIFICATION_ID_BASE + reminderId.toInt(), notification)
            }
        } catch (e: Exception) {
            Log.e(TAG, "startForeground failed", e)
            stopSelfCompletely()
            return START_NOT_STICKY
        }

        requestAlarmAudioFocus()
        playLocalAlarm(soundUri)

        val fallback = if (description.isBlank()) "یادآوری: $title" else "یادآوری: $title. $description"
        pendingSpeakText = fallback
        speakJob?.cancel()
        speakJob = scope.launch {
            // Do not wait on a second chat completion — that burned 4s + quota before
            // TTS even started, and the title/description is what the user needs to hear.
            val hasPersonalKey = SubscriptionManager.hasPersonalKey(this@SmartReminderTtsService)
            val cloud = withContext(Dispatchers.IO) {
                runCatching {
                    withTimeoutOrNull(TTS_TIMEOUT_MS) {
                        if (hasPersonalKey) AIHelper.synthesizeSpeech(this@SmartReminderTtsService, fallback)
                        else AIBackendClient.synthesize(this@SmartReminderTtsService, fallback)
                    }
                }.getOrNull()
            }
            if (currentReminderId != reminderId) return@launch
            if (cloud != null) {
                stopLocalAlarm()
                playCloud(cloud.absolutePath)
            } else {
                Log.w(TAG, "cloud TTS unavailable: ${AIBackendClient.lastError}")
                initDeviceTts(fallback)
            }
        }
        return START_REDELIVER_INTENT
    }

    private fun alarmAttrs(): AudioAttributes =
        AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ALARM)
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
            .build()

    private fun playLocalAlarm(soundValue: String) {
        val uri = ReminderSound.toUri(this, soundValue) ?: return
        try {
            localPlayer?.release()
            localPlayer = MediaPlayer().apply {
                setAudioAttributes(alarmAttrs())
                setDataSource(this@SmartReminderTtsService, uri)
                isLooping = true
                setWakeMode(this@SmartReminderTtsService, PowerManager.PARTIAL_WAKE_LOCK)
                setVolume(1f, 1f)
                prepare()
                start()
            }
        } catch (e: Exception) {
            Log.e(TAG, "local alarm failed", e)
        }
    }

    private fun stopLocalAlarm() {
        runCatching {
            localPlayer?.apply { if (isPlaying) stop(); release() }
        }
        localPlayer = null
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
                setAudioAttributes(alarmAttrs())
                setDataSource(path)
                isLooping = false
                setWakeMode(this@SmartReminderTtsService, PowerManager.PARTIAL_WAKE_LOCK)
                setVolume(1f, 1f)
                setOnCompletionListener {
                    handler.postDelayed({
                        if (currentReminderId != -1L) playCloud(path)
                    }, REPEAT_INTERVAL_MS)
                }
                setOnErrorListener { _, what, extra ->
                    Log.e(TAG, "cloud player error what=$what extra=$extra")
                    initDeviceTts(pendingSpeakText)
                    true
                }
                prepare()
                start()
            }
        } catch (e: Exception) {
            Log.e(TAG, "cloud play failed", e)
            initDeviceTts(pendingSpeakText)
        }
    }

    private fun initDeviceTts(text: String) {
        if (tts != null) {
            speakOnce(text)
            return
        }
        tts = TextToSpeech(this) { status ->
            if (currentReminderId == -1L) return@TextToSpeech
            if (status != TextToSpeech.SUCCESS) {
                Log.w(TAG, "device TTS init failed, keeping local alarm")
                return@TextToSpeech
            }
            val faIr = tts?.setLanguage(Locale("fa", "IR")) ?: TextToSpeech.LANG_NOT_SUPPORTED
            val langOk = faIr != TextToSpeech.LANG_MISSING_DATA && faIr != TextToSpeech.LANG_NOT_SUPPORTED
            val faOk = if (!langOk) {
                val fa = tts?.setLanguage(Locale("fa")) ?: TextToSpeech.LANG_NOT_SUPPORTED
                fa != TextToSpeech.LANG_MISSING_DATA && fa != TextToSpeech.LANG_NOT_SUPPORTED
            } else true
            if (!faOk) {
                Log.w(TAG, "no Persian TTS voice, keeping local alarm")
                return@TextToSpeech
            }
            stopLocalAlarm()
            tts?.setSpeechRate(0.95f)
            tts?.setAudioAttributes(alarmAttrs())
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
                    .setAudioAttributes(alarmAttrs())
                    .build()
                audioFocusRequest = req
                am.requestAudioFocus(req)
            } else {
                @Suppress("DEPRECATION")
                am.requestAudioFocus(null, AudioManager.STREAM_ALARM, AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
            }
        } catch (_: Exception) {
        }
    }

    private fun buildNotification(reminderId: Long, title: String): Notification {
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
        return NotificationCompat.Builder(this, YadavarApplication.SMART_TTS_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_reminder)
            .setContentTitle(title)
            .setContentText(getString(R.string.smart_tts_playing))
            .setOngoing(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setContentIntent(fullPi)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun stopSpeakingLoop() {
        handler.removeCallbacksAndMessages(null)
        speakJob?.cancel()
        runCatching { tts?.stop(); tts?.shutdown() }
        tts = null
        runCatching { cloudPlayer?.stop(); cloudPlayer?.release() }
        cloudPlayer = null
        stopLocalAlarm()
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
        scope.cancel()
        if (runningInstance === this) runningInstance = null
        super.onDestroy()
    }

    companion object {
        private const val TAG = "SmartReminderTts"
        private const val NOTIFICATION_ID_BASE = 90000
        private const val REPEAT_INTERVAL_MS = 8000L
        private const val MAX_REPEATS = 8
        private const val TTS_TIMEOUT_MS = 25_000L

        @Volatile
        private var runningInstance: SmartReminderTtsService? = null

        fun start(context: Context, reminderId: Long, title: String, description: String, soundUri: String = ReminderSound.DEFAULT_ALARM) {
            val intent = Intent(context, SmartReminderTtsService::class.java).apply {
                putExtra("reminder_id", reminderId)
                putExtra("reminder_title", title)
                putExtra("reminder_description", description)
                putExtra("sound_uri", soundUri)
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

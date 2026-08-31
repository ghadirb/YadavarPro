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
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import androidx.core.app.NotificationCompat
import com.ghadirb.yadavar.R
import com.ghadirb.yadavar.YadavarApplication
import com.ghadirb.yadavar.ui.reminders.FullScreenAlarmActivity
import com.ghadirb.yadavar.utils.AIBackendClient
import com.ghadirb.yadavar.utils.AIHelper
import com.ghadirb.yadavar.utils.NotificationHelper
import com.ghadirb.yadavar.utils.SubscriptionManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.Locale

/**
 * Smart spoken alert: online model writes a natural Persian sentence, online TTS
 * speaks it and repeats until Done / Snooze. Default phone alarm is only used if
 * both the online voice and on-device TTS fail.
 */
class SmartReminderTtsService : Service() {

    private var tts: TextToSpeech? = null
    private val handler = Handler(Looper.getMainLooper())
    private var speakJob: Job? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var currentReminderId: Long = -1
    private var currentTitle: String = ""
    private var cloudPlayer: MediaPlayer? = null
    private var audioFocusRequest: AudioFocusRequest? = null
    private var pendingSpeakText: String = ""
    private var startedAt: Long = 0L
    private var savedAlarmVolume: Int? = null
    private var usedFallbackNotification = false
    private var wakeLock: PowerManager.WakeLock? = null

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
            currentTitle = title
            startedAt = System.currentTimeMillis()
            usedFallbackNotification = false
            val notification = buildNotification(reminderId, title, getString(R.string.smart_tts_preparing))
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

        acquireWorkWakeLock()
        ensureAudibleAlarmStream()
        requestAlarmAudioFocus()

        val template = spokenTemplate(title, description)
        pendingSpeakText = template
        notifySpokenText(reminderId, template)
        speakJob?.cancel()
        speakJob = scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    withTimeoutOrNull(CLOUD_TIMEOUT_MS) {
                        prepareCloudSpeech(title, description, template)
                    }
                }.onFailure { Log.w(TAG, "cloud smart alert failed", it) }.getOrNull()
            }
            if (currentReminderId != reminderId) return@launch
            val spoken = result?.text?.takeIf { looksPersian(it) } ?: template
            pendingSpeakText = spoken
            notifySpokenText(reminderId, spoken)
            updateNotification(reminderId, title, spoken)

            val audio = result?.file
            if (audio != null && audio.exists() && audio.length() >= 64L) {
                playCloud(audio.absolutePath)
                return@launch
            }
            Log.w(TAG, "cloud TTS unavailable: ${AIBackendClient.lastError}")
            initDeviceTts(spoken)
        }
        return START_REDELIVER_INTENT
    }

    private data class CloudSpeech(val text: String, val file: File?)

    private suspend fun prepareCloudSpeech(
        title: String,
        description: String,
        template: String
    ): CloudSpeech {
        val hasPersonalKey = SubscriptionManager.hasPersonalKey(this)
        val spoken = rewriteSpoken(title, description, template, hasPersonalKey)
        pendingSpeakText = spoken
        val file = if (hasPersonalKey) {
            AIHelper.synthesizeSpeech(this, spoken)
        } else {
            AIBackendClient.synthesize(this, spoken)
        }
        return CloudSpeech(spoken, file)
    }

    private suspend fun rewriteSpoken(
        title: String,
        description: String,
        template: String,
        hasPersonalKey: Boolean
    ): String {
        val rewritten = if (hasPersonalKey) {
            AIHelper.generateText(
                this,
                SPOKEN_SYSTEM_PROMPT,
                spokenUserPrompt(title, description),
                maxTokens = 80
            )
        } else {
            val messages = JSONArray()
                .put(JSONObject().put("role", "system").put("content", SPOKEN_SYSTEM_PROMPT))
                .put(JSONObject().put("role", "user").put("content", spokenUserPrompt(title, description)))
            AIBackendClient.chat(this, messages, maxTokens = 80, temperature = 0.6)
        }
        val cleaned = rewritten?.trim()?.trim('"', '«', '»', '\'')?.take(220).orEmpty()
        return if (looksPersian(cleaned)) cleaned else template
    }

    private fun alarmAttrs(): AudioAttributes =
        AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ALARM)
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
            .build()

    private fun ensureAudibleAlarmStream() {
        try {
            val am = getSystemService(AUDIO_SERVICE) as AudioManager
            val max = am.getStreamMaxVolume(AudioManager.STREAM_ALARM)
            val cur = am.getStreamVolume(AudioManager.STREAM_ALARM)
            if (max > 0 && cur < (max / 3).coerceAtLeast(1)) {
                savedAlarmVolume = cur
                am.setStreamVolume(AudioManager.STREAM_ALARM, (max * 3 / 4).coerceAtLeast(1), 0)
            }
        } catch (_: Exception) {
        }
    }

    private fun restoreAlarmVolume() {
        val saved = savedAlarmVolume ?: return
        savedAlarmVolume = null
        try {
            val am = getSystemService(AUDIO_SERVICE) as AudioManager
            am.setStreamVolume(AudioManager.STREAM_ALARM, saved, 0)
        } catch (_: Exception) {
        }
    }

    private fun acquireWorkWakeLock() {
        if (wakeLock?.isHeld == true) return
        try {
            val pm = getSystemService(POWER_SERVICE) as PowerManager
            wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Yadavar:SmartTts").apply {
                setReferenceCounted(false)
                acquire(MAX_DURATION_MS)
            }
        } catch (_: Exception) {
        }
    }

    private fun releaseWorkWakeLock() {
        runCatching { if (wakeLock?.isHeld == true) wakeLock?.release() }
        wakeLock = null
    }

    private fun stillActive(): Boolean {
        if (currentReminderId == -1L) return false
        if (System.currentTimeMillis() - startedAt >= MAX_DURATION_MS) {
            stopSelfCompletely()
            return false
        }
        return true
    }

    private fun playCloud(path: String) {
        if (!stillActive()) return
        if (!File(path).exists()) {
            initDeviceTts(pendingSpeakText)
            return
        }
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
                        if (stillActive()) playCloud(path)
                    }, REPEAT_GAP_MS)
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
            if (status != TextToSpeech.SUCCESS || !pickTtsLanguage()) {
                Log.w(TAG, "device TTS unavailable, falling back to default notification")
                fallbackDefaultNotification()
                return@TextToSpeech
            }
            tts?.setSpeechRate(0.95f)
            tts?.setAudioAttributes(alarmAttrs())
            tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {}
                override fun onError(utteranceId: String?) {
                    handler.postDelayed({ if (stillActive()) speakOnce(pendingSpeakText) }, REPEAT_GAP_MS)
                }
                override fun onDone(utteranceId: String?) {
                    handler.postDelayed({ if (stillActive()) speakOnce(pendingSpeakText) }, REPEAT_GAP_MS)
                }
            })
            speakOnce(text)
        }
    }

    private fun pickTtsLanguage(): Boolean {
        val faIr = tts?.setLanguage(Locale("fa", "IR")) ?: TextToSpeech.LANG_NOT_SUPPORTED
        if (faIr != TextToSpeech.LANG_MISSING_DATA && faIr != TextToSpeech.LANG_NOT_SUPPORTED) return true
        val fa = tts?.setLanguage(Locale("fa")) ?: TextToSpeech.LANG_NOT_SUPPORTED
        return fa != TextToSpeech.LANG_MISSING_DATA && fa != TextToSpeech.LANG_NOT_SUPPORTED
    }

    private fun speakOnce(text: String) {
        if (!stillActive()) return
        val spoken = tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "smart_$currentReminderId")
        if (spoken == TextToSpeech.ERROR) fallbackDefaultNotification()
    }

    private fun fallbackDefaultNotification() {
        if (usedFallbackNotification) return
        usedFallbackNotification = true
        val id = currentReminderId
        val title = currentTitle
        val text = pendingSpeakText.ifBlank { title }
        if (id > 0) {
            NotificationHelper.showDefaultSoundFallback(this, id, title, text)
        }
        stopSelfCompletely()
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

    private fun fullScreenIntent(reminderId: Long, title: String): Intent =
        Intent(this, FullScreenAlarmActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("reminder_id", reminderId)
            putExtra("reminder_title", title)
            putExtra("alert_type", "SMART")
            putExtra("spoken_text", pendingSpeakText)
        }

    private fun buildNotification(reminderId: Long, title: String, body: String): Notification {
        val fullPi = PendingIntent.getActivity(
            this, reminderId.toInt(), fullScreenIntent(reminderId, title),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val donePi = PendingIntent.getBroadcast(
            this, ("done$reminderId").hashCode(),
            Intent(this, ReminderActionReceiver::class.java).apply {
                action = ReminderActionReceiver.ACTION_DONE
                putExtra("reminder_id", reminderId)
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return NotificationCompat.Builder(this, YadavarApplication.SMART_TTS_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_reminder)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setOngoing(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setContentIntent(fullPi)
            .addAction(R.drawable.ic_check, getString(R.string.action_done), donePi)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun updateNotification(reminderId: Long, title: String, body: String) {
        try {
            val nm = getSystemService(NOTIFICATION_SERVICE) as android.app.NotificationManager
            nm.notify(NOTIFICATION_ID_BASE + reminderId.toInt(), buildNotification(reminderId, title, body))
        } catch (_: Exception) {
        }
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
        restoreAlarmVolume()
        releaseWorkWakeLock()
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
        private const val REPEAT_GAP_MS = 1600L
        private const val MAX_DURATION_MS = 20 * 60 * 1000L
        private const val CLOUD_TIMEOUT_MS = 40_000L
        private const val SPOKEN_SYSTEM_PROMPT =
            "فقط فارسی محاوره‌ای. یک جمله کوتاه برای خواندن با صدای بلند برگردان. بدون انگلیسی، بدون اموجی، بدون نقل‌قول، بدون توضیح. مثال: سلام، الان وقتشه که قرص فشارتو بخوری."

        @Volatile
        private var runningInstance: SmartReminderTtsService? = null

        @Volatile
        var spokenTextListener: ((Long, String) -> Unit)? = null

        fun spokenTemplate(title: String, description: String): String {
            val t = title.trim().ifBlank { "یک کار" }
            val d = description.trim()
            return if (d.isBlank()) "سلام، الان وقتشه که $t. لطفاً انجامش بده."
            else "سلام، الان وقتشه که $t. $d"
        }

        private fun spokenUserPrompt(title: String, description: String): String {
            val d = description.trim()
            return if (d.isBlank()) "عنوان یادآوری: $title"
            else "عنوان یادآوری: $title\nتوضیح: $d"
        }

        fun looksPersian(text: String): Boolean =
            text.any { it in '\u0600'..'\u06FF' }

        private fun notifySpokenText(reminderId: Long, text: String) {
            spokenTextListener?.invoke(reminderId, text)
        }

        fun currentSpokenText(reminderId: Long): String {
            val instance = runningInstance ?: return ""
            return if (instance.currentReminderId == reminderId) instance.pendingSpeakText else ""
        }

        fun start(context: Context, reminderId: Long, title: String, description: String, soundUri: String = "") {
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

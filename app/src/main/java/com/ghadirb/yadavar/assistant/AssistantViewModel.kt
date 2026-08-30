package com.ghadirb.yadavar.assistant

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ghadirb.yadavar.database.AlertType
import com.ghadirb.yadavar.database.CategoryCatalog
import com.ghadirb.yadavar.database.ReminderEntity
import com.ghadirb.yadavar.database.ReminderRepository
import com.ghadirb.yadavar.database.ReminderType
import com.ghadirb.yadavar.database.RepeatPattern
import com.ghadirb.yadavar.utils.AIHelper
import com.ghadirb.yadavar.utils.EnumLabels
import com.ghadirb.yadavar.utils.PreferencesManager
import com.ghadirb.yadavar.utils.QuietHoursManager
import com.ghadirb.yadavar.utils.SubscriptionManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.util.Calendar

class AssistantViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = ReminderRepository(app)
    val prefs = PreferencesManager(app)

    private val _messages = MutableStateFlow(listOf(ChatMessage(id = 1L, text = WELCOME, fromUser = false)))
    val messages: StateFlow<List<ChatMessage>> = _messages

    fun send(text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return
        append(ChatMessage(text = trimmed, fromUser = true))
        viewModelScope.launch { handle(trimmed) }
    }

    private suspend fun handle(text: String) {
        when (val intent = ReminderNlp.parse(text)) {
            is AssistantIntent.Create -> saveCreate(intent)
            AssistantIntent.Today -> appendBot(listBlock("امروز", todayWindow()))
            AssistantIntent.Overdue -> appendBot(listBlock("سررسیدشده", overdue()))
            AssistantIntent.Week -> appendBot(listBlock("این هفته", weekWindow()))
            AssistantIntent.EnableQuiet -> {
                prefs.quietHoursEnabled = true
                QuietHoursManager.schedule(getApplication())
                appendBot("ساعت سکوت روشن شد (${prefs.formatMinutes(prefs.quietStartMinutes)} تا ${prefs.formatMinutes(prefs.quietEndMinutes)}).")
            }
            AssistantIntent.DisableQuiet -> {
                prefs.quietHoursEnabled = false
                QuietHoursManager.schedule(getApplication())
                appendBot("ساعت سکوت خاموش شد.")
            }
            AssistantIntent.EnableFocus -> {
                prefs.focusModeEnabled = true
                appendBot("حالت تمرکز روشن شد. فقط یادآوری بحرانی صدا می‌دهد.")
            }
            AssistantIntent.DisableFocus -> {
                prefs.focusModeEnabled = false
                appendBot("حالت تمرکز خاموش شد.")
            }
            AssistantIntent.Help -> appendBot(WELCOME)
            is AssistantIntent.OffTopic -> appendBot(OFF_TOPIC)
            is AssistantIntent.ReminderQuestion -> handleCloudParse(intent.text)
        }
    }

    private suspend fun handleCloudParse(text: String) {
        val app = getApplication<Application>()
        if (!SubscriptionManager.canUseAi(app)) {
            appendBot(SubscriptionManager.upgradeMessage(app) + "\n\nروی دستگاه نفهمیدم. مثال: «۶ و ۴۲ دقیقه صبح دارو» یا «شنبه تا چهارشنبه ساعت ۸ جلسه».")
            return
        }
        val context = buildContext()
        val raw = AIHelper.generateText(app, PARSE_SYSTEM, "وضعیت:\n$context\n\nپیام:\n$text")
        if (raw.isNullOrBlank()) {
            appendBot("نتوانستم آنلاین تحلیل کنم. مثال: «فردا ساعت ۷ و ۱۵ دقیقه جلسه».")
            return
        }
        val json = extractJson(raw)
        if (json != null) {
            val action = json.optString("action")
            if (action == "create") {
                val created = createFromJson(json, text)
                if (created != null) {
                    saveCreate(created)
                    return
                }
            }
            if (action == "list_today") {
                appendBot(listBlock("امروز", todayWindow()))
                return
            }
            if (action == "refuse" || action == "off_topic") {
                appendBot(OFF_TOPIC)
                return
            }
        }
        if (looksLikeGeneralChat(raw)) {
            appendBot(OFF_TOPIC)
        } else {
            appendBot(raw.take(400))
        }
    }

    private fun extractJson(raw: String): JSONObject? {
        val start = raw.indexOf('{')
        val end = raw.lastIndexOf('}')
        if (start < 0 || end <= start) return null
        return runCatching { JSONObject(raw.substring(start, end + 1)) }.getOrNull()
    }

    private fun looksLikeGeneralChat(raw: String): Boolean {
        val n = ReminderNlp.normalize(raw)
        return n.contains("جوک") || n.contains("شعر") || n.contains("داستان") ||
            n.contains("سرمایه") || n.contains("بورس")
    }

    private fun createFromJson(json: JSONObject, fallbackTitle: String): AssistantIntent.Create? {
        val title = json.optString("title").ifBlank { fallbackTitle }
        val hour = json.optInt("hour", 9).coerceIn(0, 23)
        val minute = json.optInt("minute", 0).coerceIn(0, 59)
        val cal = Calendar.getInstance()
        val daysArr = json.optJSONArray("days")
        val days = mutableSetOf<Int>()
        if (daysArr != null) {
            for (i in 0 until daysArr.length()) days.add(daysArr.optInt(i))
        }
        if (days.isNotEmpty()) {
            var guard = 0
            fun idx() = cal.get(Calendar.DAY_OF_WEEK) - Calendar.SUNDAY
            while (idx() !in days && guard < 8) {
                cal.add(Calendar.DAY_OF_YEAR, 1)
                guard++
            }
        } else if (json.optInt("plusDays", 0) > 0) {
            cal.add(Calendar.DAY_OF_YEAR, json.optInt("plusDays"))
        }
        cal.set(Calendar.HOUR_OF_DAY, hour)
        cal.set(Calendar.MINUTE, minute)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        if (cal.timeInMillis < System.currentTimeMillis() - 30_000) cal.add(Calendar.DAY_OF_YEAR, 1)
        val repeatName = json.optString("repeat", "ONCE")
        val repeat = runCatching { RepeatPattern.valueOf(repeatName) }.getOrDefault(
            if (days.size >= 2) RepeatPattern.CUSTOM else RepeatPattern.ONCE
        )
        return AssistantIntent.Create(
            title = title,
            triggerAt = cal.timeInMillis,
            repeat = repeat,
            category = json.optString("category").ifBlank { "عمومی" },
            customDays = days.sorted().joinToString(","),
            alertType = json.optString("alertType").ifBlank { "NOTIFICATION" }
        )
    }

    private suspend fun saveCreate(intent: AssistantIntent.Create) {
        val reminder = ReminderEntity(
            title = intent.title,
            triggerTime = intent.triggerAt,
            repeatPattern = intent.repeat.name,
            category = intent.category,
            categoryColor = CategoryCatalog.colorFor(intent.category),
            priority = intent.priority.name,
            reminderType = if (intent.repeat.name == "ONCE") ReminderType.SIMPLE.name else ReminderType.RECURRING.name,
            alertType = intent.alertType.ifBlank { AlertType.NOTIFICATION.name },
            customRepeatDays = intent.customDays,
            repeatIntervalDays = intent.intervalDays,
            repeatIntervalMinutes = intent.intervalMinutes
        )
        repo.save(reminder)
        val days = if (intent.customDays.isNotBlank()) "\nروزها: ${ReminderNlp.weekdayNames(intent.customDays)}" else ""
        val repeatLabel = when {
            intent.intervalMinutes >= 60 && intent.intervalMinutes % 60 == 0 ->
                "هر ${intent.intervalMinutes / 60} ساعت یک‌بار"
            intent.intervalMinutes > 0 -> "هر ${intent.intervalMinutes} دقیقه یک‌بار"
            intent.intervalDays > 0 -> "هر ${intent.intervalDays} روز یک‌بار"
            else -> EnumLabels.repeat(intent.repeat.name)
        }
        appendBot("ثبت شد: «${intent.title}»\n${ReminderNlp.formatWhen(intent.triggerAt)}\nتکرار: $repeatLabel$days\nدسته: ${intent.category.ifBlank { "عمومی" }}")
    }

    private suspend fun todayWindow(): List<ReminderEntity> {
        val start = startOfDay()
        return repo.active().first().filter { it.triggerTime in start until start + DAY }
    }

    private suspend fun overdue(): List<ReminderEntity> {
        val now = System.currentTimeMillis()
        return repo.active().first().filter { it.triggerTime < now }
    }

    private suspend fun weekWindow(): List<ReminderEntity> {
        val start = startOfDay()
        return repo.active().first().filter { it.triggerTime in start until start + 7 * DAY }
    }

    private suspend fun buildContext(): String {
        val today = todayWindow()
        val overdue = overdue()
        return buildString {
            append("تمرکز: ${if (prefs.focusModeEnabled) "روشن" else "خاموش"}\n")
            append("سکوت: ${if (prefs.quietHoursEnabled) "روشن" else "خاموش"}\n")
            append("سررسیدشده: ${overdue.size}\nامروز:\n")
            if (today.isEmpty()) append("  هیچ\n")
            else today.take(8).forEach { append("  - ${it.title} @ ${ReminderNlp.formatWhen(it.triggerTime)}\n") }
        }
    }

    private fun listBlock(label: String, items: List<ReminderEntity>): String {
        if (items.isEmpty()) return "برای «$label» یادآوری‌ای نیست."
        return buildString {
            append("$label (${items.size}):\n")
            items.take(12).forEach { append("• ${it.title} — ${ReminderNlp.formatWhen(it.triggerTime)}\n") }
        }
    }

    private fun appendBot(text: String) = append(ChatMessage(text = text, fromUser = false))
    private fun append(message: ChatMessage) {
        _messages.value = _messages.value + message
    }

    private fun startOfDay(): Long {
        val c = Calendar.getInstance()
        c.set(Calendar.HOUR_OF_DAY, 0)
        c.set(Calendar.MINUTE, 0)
        c.set(Calendar.SECOND, 0)
        c.set(Calendar.MILLISECOND, 0)
        return c.timeInMillis
    }

    companion object {
        private const val DAY = 24L * 60 * 60 * 1000
        private const val WELCOME =
            "سلام، دستیار یادآور پرو هستم.\n" +
                "بدون اینترنت هم می‌فهمم:\n" +
                "• ۶ و ۴۲ دقیقه صبح دارو بخور\n" +
                "• شنبه تا چهارشنبه ساعت ۸ جلسه\n" +
                "• هر روز ۹ صبح ورزش\n" +
                "• یادآوری‌های امروز / سکوت شبانه\n" +
                "چت آزاد روزمره جواب نمی‌دهم تا هزینه کلید کمتر شود. ابر فقط برای فهمیدن زمان و ثبت یادآوری است."
        private const val OFF_TOPIC =
            "من فقط دستیار یادآوری هستم؛ چت روزمره، شعر، آب‌وهوا یا مسائل مالی را جواب نمی‌دهم.\n" +
                "بگو چه چیزی، چه ساعتی و کدام روزها یادآوری شود."
        private const val PARSE_SYSTEM =
            "تو فقط استخراج‌کننده یادآوری برای اپ یادآور پرو هستی. " +
                "اگر پیام درباره ساخت/لیست/سکوت یادآوری است JSON برگردان، وگرنه {\"action\":\"refuse\"}. " +
                "هرگز جوک، شعر، مشاوره مالی یا گفتگوی آزاد ننویس. " +
                "فرمت: {\"action\":\"create\",\"title\":\"...\",\"hour\":6,\"minute\":42,\"plusDays\":0," +
                "\"days\":[6,0,1,2,3],\"repeat\":\"CUSTOM|WEEKDAYS|DAILY|ONCE\",\"category\":\"کار\",\"alertType\":\"NOTIFICATION\"}. " +
                "days از شنبه=6 یکشنبه=0 دوشنبه=1 سه‌شنبه=2 چهارشنبه=3 پنجشنبه=4 جمعه=5. " +
                "دقیقه را حتماً جدا کن. فارسی."
    }
}

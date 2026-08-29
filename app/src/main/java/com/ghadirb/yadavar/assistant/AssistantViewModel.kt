package com.ghadirb.yadavar.assistant

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ghadirb.yadavar.database.AlertType
import com.ghadirb.yadavar.database.CategoryCatalog
import com.ghadirb.yadavar.database.ReminderEntity
import com.ghadirb.yadavar.database.ReminderRepository
import com.ghadirb.yadavar.database.ReminderType
import com.ghadirb.yadavar.utils.PreferencesManager
import com.ghadirb.yadavar.utils.QuietHoursManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.Calendar

class AssistantViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = ReminderRepository(app)
    val prefs = PreferencesManager(app)

    private val _messages = MutableStateFlow(
        listOf(
            ChatMessage(
                id = 1L,
                text = WELCOME,
                fromUser = false
            )
        )
    )
    val messages: StateFlow<List<ChatMessage>> = _messages

    fun send(text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return
        append(ChatMessage(text = trimmed, fromUser = true))
        viewModelScope.launch { handle(trimmed) }
    }

    private suspend fun handle(text: String) {
        when (val intent = ReminderNlp.parse(text)) {
            is AssistantIntent.Create -> {
                val reminder = ReminderEntity(
                    title = intent.title,
                    triggerTime = intent.triggerAt,
                    repeatPattern = intent.repeat.name,
                    category = intent.category,
                    categoryColor = CategoryCatalog.colorFor(intent.category),
                    priority = intent.priority.name,
                    reminderType = if (intent.repeat.name == "ONCE") ReminderType.SIMPLE.name else ReminderType.RECURRING.name,
                    alertType = AlertType.NOTIFICATION.name
                )
                repo.save(reminder)
                appendBot("ثبت شد: «${intent.title}»\n${ReminderNlp.formatWhen(intent.triggerAt)}\nدسته: ${intent.category.ifBlank { "عمومی" }}")
            }
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
            is AssistantIntent.FreeChat -> {
                val context = buildContext()
                val ai = AiClient.chat(
                    prefs,
                    SYSTEM,
                    "وضعیت فعلی:\n$context\n\nپیام کاربر:\n${intent.text}"
                )
                appendBot(ai ?: NO_AI)
            }
        }
    }

    private suspend fun todayWindow(): List<ReminderEntity> {
        val start = startOfDay()
        val end = start + DAY
        return repo.active().first().filter { it.triggerTime in start until end }
    }

    private suspend fun overdue(): List<ReminderEntity> {
        val now = System.currentTimeMillis()
        return repo.active().first().filter { it.triggerTime < now }
    }

    private suspend fun weekWindow(): List<ReminderEntity> {
        val start = startOfDay()
        val end = start + 7 * DAY
        return repo.active().first().filter { it.triggerTime in start until end }
    }

    private suspend fun buildContext(): String {
        val today = todayWindow()
        val overdue = overdue()
        return buildString {
            append("تمرکز: ${if (prefs.focusModeEnabled) "روشن" else "خاموش"}\n")
            append("سکوت: ${if (prefs.quietHoursEnabled) "روشن" else "خاموش"}\n")
            append("سررسیدشده: ${overdue.size}\n")
            append("امروز:\n")
            if (today.isEmpty()) append("  هیچ\n")
            else today.take(8).forEach {
                append("  - ${it.title} @ ${ReminderNlp.formatWhen(it.triggerTime)}\n")
            }
        }
    }

    private fun listBlock(label: String, items: List<ReminderEntity>): String {
        if (items.isEmpty()) return "برای «$label» یادآوری‌ای نیست."
        return buildString {
            append("$label (${items.size}):\n")
            items.take(12).forEach {
                append("• ${it.title} — ${ReminderNlp.formatWhen(it.triggerTime)}\n")
            }
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
                "بدون اینترنت هم می‌توانی بگویی:\n" +
                "• فردا ساعت ۸ دارو بخور\n" +
                "• هر روز ۹ صبح ورزش\n" +
                "• یادآوری‌های امروز\n" +
                "• سکوت شبانه روشن\n" +
                "اگر کلید API را در تنظیمات بگذاری، گفتگوی آزاد هم جواب می‌دهم — فقط دربارهٔ یادآوری، نه مسائل مالی."
        private const val NO_AI =
            "این را به‌صورت دستور یادآوری نفهمیدم.\n" +
                "مثال: «فردا ساعت ۷ جلسه» یا «یادآوری‌های امروز».\n" +
                "برای گفتگوی آزاد، در تنظیمات یک کلید API (مثل GapGPT) بگذار."
        private const val SYSTEM =
            "تو دستیار اپ یادآور پرو هستی. فقط درباره یادآوری، زمان، دسته، سکوت شبانه و تمرکز حرف بزن. " +
                "هرگز مشاوره مالی، سرمایه‌گذاری یا حسابداری نده. فارسی، کوتاه و عملی جواب بده. " +
                "اگر کاربر خواست یادآوری بسازد، زمان و عنوان را واضح تأیید کن."
    }
}

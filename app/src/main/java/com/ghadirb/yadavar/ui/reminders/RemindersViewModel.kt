package com.ghadirb.yadavar.ui.reminders

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ghadirb.yadavar.database.ReminderEntity
import com.ghadirb.yadavar.database.ReminderRepository
import com.ghadirb.yadavar.utils.BackupManager
import com.ghadirb.yadavar.utils.PreferencesManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class RemindersViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = ReminderRepository(app)
    val prefs = PreferencesManager(app)

    val selectedCategory = MutableStateFlow<String?>(null)
    val showCompleted = MutableStateFlow(false)

    val categories = repo.categories().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val visibleReminders = combine(
        repo.active(),
        repo.completed(),
        selectedCategory,
        showCompleted
    ) { active, done, category, completed ->
        val source = if (completed) done else active
        if (category.isNullOrBlank()) source
        else source.filter { it.category == category }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun save(reminder: ReminderEntity) = viewModelScope.launch { repo.save(reminder) }
    fun complete(reminder: ReminderEntity) = viewModelScope.launch { repo.complete(reminder) }
    fun delete(reminder: ReminderEntity) = viewModelScope.launch { repo.delete(reminder) }
    fun getById(id: Long, onLoaded: (ReminderEntity?) -> Unit) = viewModelScope.launch {
        onLoaded(repo.getById(id))
    }

    fun exportJson(onReady: (String) -> Unit) = viewModelScope.launch {
        onReady(BackupManager.exportJson(repo.snapshot(), prefs))
    }

    fun importJson(json: String, onDone: (Result<Int>) -> Unit) = viewModelScope.launch {
        runCatching {
            val backup = BackupManager.parse(json)
            BackupManager.applySettings(backup, prefs)
            repo.replaceAll(backup.reminders)
            backup.reminders.size
        }.let(onDone)
    }
}

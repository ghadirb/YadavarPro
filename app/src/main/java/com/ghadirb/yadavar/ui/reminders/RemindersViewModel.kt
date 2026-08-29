package com.ghadirb.yadavar.ui.reminders

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ghadirb.yadavar.database.ReminderEntity
import com.ghadirb.yadavar.database.ReminderRepository
import kotlinx.coroutines.launch

class RemindersViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = ReminderRepository(app)

    val activeReminders = repo.active()

    fun add(reminder: ReminderEntity) = viewModelScope.launch { repo.add(reminder) }
    fun complete(reminder: ReminderEntity) = viewModelScope.launch { repo.complete(reminder) }
    fun delete(reminder: ReminderEntity) = viewModelScope.launch { repo.delete(reminder) }
}

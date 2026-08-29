package com.ghadirb.yadavar.database

import android.content.Context
import kotlinx.coroutines.flow.Flow

class ReminderRepository(context: Context) {
    private val appContext = context.applicationContext
    private val dao = AppDatabase.getInstance(appContext).reminderDao()

    fun active(): Flow<List<ReminderEntity>> = dao.getActive()
    fun byType(type: ReminderType): Flow<List<ReminderEntity>> = dao.getByType(type.name)
    fun categories(): Flow<List<String>> = dao.getCategories()

    suspend fun add(reminder: ReminderEntity): Long {
        val id = dao.insert(reminder)
        ReminderScheduler.schedule(appContext, reminder.copy(id = id))
        return id
    }

    suspend fun update(reminder: ReminderEntity) {
        dao.update(reminder)
        ReminderScheduler.cancel(appContext, reminder)
        if (!reminder.isCompleted) ReminderScheduler.schedule(appContext, reminder)
    }

    suspend fun complete(reminder: ReminderEntity) {
        dao.markCompleted(reminder.id)
        ReminderScheduler.cancel(appContext, reminder)
    }

    suspend fun delete(reminder: ReminderEntity) {
        ReminderScheduler.cancel(appContext, reminder)
        dao.delete(reminder)
    }
}

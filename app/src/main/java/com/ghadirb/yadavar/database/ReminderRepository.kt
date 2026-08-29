package com.ghadirb.yadavar.database

import android.content.Context
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

class ReminderRepository(context: Context) {
    private val appContext = context.applicationContext
    private val dao = AppDatabase.getInstance(appContext).reminderDao()

    fun all(): Flow<List<ReminderEntity>> = dao.getAll()
    fun active(): Flow<List<ReminderEntity>> = dao.getActive()
    fun completed(): Flow<List<ReminderEntity>> = dao.getCompleted()
    fun byType(type: ReminderType): Flow<List<ReminderEntity>> = dao.getByType(type.name)
    fun categories(): Flow<List<String>> = dao.getCategories()

    suspend fun getById(id: Long): ReminderEntity? = dao.getById(id)

    suspend fun snapshot(): List<ReminderEntity> = dao.getAll().first()

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

    suspend fun save(reminder: ReminderEntity): Long {
        return if (reminder.id == 0L) add(reminder) else {
            update(reminder)
            reminder.id
        }
    }

    suspend fun complete(reminder: ReminderEntity) {
        dao.markCompleted(reminder.id)
        ReminderScheduler.cancel(appContext, reminder)
    }

    suspend fun delete(reminder: ReminderEntity) {
        ReminderScheduler.cancel(appContext, reminder)
        dao.delete(reminder)
    }

    suspend fun replaceAll(reminders: List<ReminderEntity>) {
        snapshot().forEach { ReminderScheduler.cancel(appContext, it) }
        dao.deleteAll()
        reminders.forEach { add(it.copy(id = 0)) }
    }
}

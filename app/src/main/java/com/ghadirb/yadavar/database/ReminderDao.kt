package com.ghadirb.yadavar.database

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface ReminderDao {

    @Query("SELECT * FROM reminders ORDER BY triggerTime ASC")
    fun getAll(): Flow<List<ReminderEntity>>

    @Query("SELECT * FROM reminders WHERE isCompleted = 0 ORDER BY triggerTime ASC")
    fun getActive(): Flow<List<ReminderEntity>>

    @Query("SELECT * FROM reminders WHERE isCompleted = 0 AND triggerTime BETWEEN :from AND :to ORDER BY triggerTime ASC")
    fun getUpcoming(from: Long, to: Long): Flow<List<ReminderEntity>>

    @Query("SELECT * FROM reminders WHERE reminderType = :type AND isCompleted = 0 ORDER BY triggerTime ASC")
    fun getByType(type: String): Flow<List<ReminderEntity>>

    @Query("SELECT * FROM reminders WHERE category = :category ORDER BY triggerTime ASC")
    fun getByCategory(category: String): Flow<List<ReminderEntity>>

    @Query("SELECT * FROM reminders WHERE reminderType = 'SUBSCRIPTION' AND isCompleted = 0 ORDER BY triggerTime ASC")
    fun getActiveSubscriptions(): Flow<List<ReminderEntity>>

    @Query("SELECT * FROM reminders WHERE id = :id")
    suspend fun getById(id: Long): ReminderEntity?

    @Query("SELECT DISTINCT category FROM reminders WHERE category != '' ORDER BY category ASC")
    fun getCategories(): Flow<List<String>>

    @Insert
    suspend fun insert(reminder: ReminderEntity): Long

    @Update
    suspend fun update(reminder: ReminderEntity)

    @Delete
    suspend fun delete(reminder: ReminderEntity)

    @Query("UPDATE reminders SET isCompleted = 1, completedAt = :completedAt WHERE id = :id")
    suspend fun markCompleted(id: Long, completedAt: Long = System.currentTimeMillis())

    @Query("UPDATE reminders SET snoozeCount = snoozeCount + 1, lastSnoozed = :snoozedAt, triggerTime = :newTriggerTime WHERE id = :id")
    suspend fun snooze(id: Long, newTriggerTime: Long, snoozedAt: Long = System.currentTimeMillis())

    @Query("DELETE FROM reminders WHERE id = :id")
    suspend fun deleteById(id: Long)
}

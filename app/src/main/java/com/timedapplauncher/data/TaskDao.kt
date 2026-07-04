package com.timedapplauncher.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface TaskDao {
    @Query("SELECT * FROM schedule_tasks ORDER BY hour, minute, id")
    fun observeAll(): Flow<List<ScheduleTask>>

    @Query("SELECT * FROM schedule_tasks ORDER BY hour, minute, id")
    suspend fun getAll(): List<ScheduleTask>

    @Query("SELECT * FROM schedule_tasks WHERE id = :id")
    suspend fun getById(id: Long): ScheduleTask?

    @Query("SELECT * FROM schedule_tasks WHERE enabled = 1")
    suspend fun getEnabled(): List<ScheduleTask>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(task: ScheduleTask): Long

    @Update
    suspend fun update(task: ScheduleTask)

    @Delete
    suspend fun delete(task: ScheduleTask)
}

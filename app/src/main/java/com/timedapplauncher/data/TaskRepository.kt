package com.timedapplauncher.data

import android.content.Context
import kotlinx.coroutines.flow.Flow

class TaskRepository(context: Context) {
    private val dao = AppDatabase.get(context).taskDao()

    fun observeAll(): Flow<List<ScheduleTask>> = dao.observeAll()

    suspend fun getAll(): List<ScheduleTask> = dao.getAll()

    suspend fun getById(id: Long): ScheduleTask? = dao.getById(id)

    suspend fun getEnabled(): List<ScheduleTask> = dao.getEnabled()

    suspend fun insert(task: ScheduleTask): Long = dao.insert(task)

    suspend fun update(task: ScheduleTask) = dao.update(task)

    suspend fun delete(task: ScheduleTask) = dao.delete(task)
}

package com.timedapplauncher.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.timedapplauncher.data.ScheduleTask
import com.timedapplauncher.data.TaskRepository
import com.timedapplauncher.ui.MainActivity
import kotlinx.coroutines.runBlocking

/**
 * AlarmManager 调度器：为每条任务设置精确闹钟（exact alarm）。
 * Android 12+ 需要 SCHEDULE_EXACT_ALARM 权限。
 */
object AlarmScheduler {

    private const val TAG = "AlarmScheduler"
    const val EXTRA_TASK_ID = "extra_task_id"

    fun requestCode(taskId: Long): Int = taskId.toInt() and 0x7FFFFFFF

    /** @return 是否成功写入系统闹钟 */
    fun scheduleTask(context: Context, task: ScheduleTask): Boolean {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val triggerAt = RandomDelayCalculator.nextTriggerMillis(task) ?: run {
            cancelTask(context, task.id)
            return false
        }

        val pendingIntent = buildPendingIntent(context, task.id)
        val showIntent = PendingIntent.getActivity(
            context,
            requestCode(task.id) + 10_000,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return try {
            // setAlarmClock 在 Doze 下更可靠，且通常无需 SCHEDULE_EXACT_ALARM
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                alarmManager.setAlarmClock(
                    AlarmManager.AlarmClockInfo(triggerAt, showIntent),
                    pendingIntent
                )
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerAt,
                    pendingIntent
                )
            } else {
                alarmManager.setExact(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
            }
            Log.d(TAG, "Scheduled task ${task.id} at $triggerAt")
            true
        } catch (e: SecurityException) {
            Log.e(TAG, "Exact alarm permission missing", e)
            false
        }
    }

    fun canScheduleAlarms(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        return alarmManager.canScheduleExactAlarms()
    }

    fun cancelTask(context: Context, taskId: Long) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarmManager.cancel(buildPendingIntent(context, taskId))
    }

    /** 重新调度所有已启用任务（开机、保存、开关切换时调用） */
    fun rescheduleAll(context: Context) {
        val repo = TaskRepository(context)
        runBlocking {
            val tasks = repo.getEnabled()
            tasks.forEach { scheduleTask(context, it) }
            // 取消已禁用但仍可能有 pending 的任务
            val disabled = repo.getAll().filter { !it.enabled }
            disabled.forEach { cancelTask(context, it.id) }
        }
    }

    private fun buildPendingIntent(context: Context, taskId: Long): PendingIntent {
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            putExtra(EXTRA_TASK_ID, taskId)
        }
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        return PendingIntent.getBroadcast(context, requestCode(taskId), intent, flags)
    }
}

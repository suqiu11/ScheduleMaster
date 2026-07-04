package com.timedapplauncher.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.timedapplauncher.R
import com.timedapplauncher.alarm.AlarmScheduler
import com.timedapplauncher.data.ScheduleTask
import com.timedapplauncher.data.TaskRepository
import com.timedapplauncher.ui.LaunchTrampolineActivity
import com.timedapplauncher.util.AppLauncher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 闹钟触发后的短时前台服务：绕过 Android/MIUI 对「后台直接 startActivity」的限制。
 * 通过高优先级全屏 Intent 通知 + 前台服务内启动，在息屏/锁屏时也能拉起目标 App。
 */
class LaunchDispatchService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createLaunchChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val taskId = intent?.getLongExtra(EXTRA_TASK_ID, -1L) ?: -1L
        if (taskId <= 0) {
            stopSelf()
            return START_NOT_STICKY
        }

        startForeground(NOTIFICATION_ID, buildPlaceholderNotification())

        scope.launch {
            try {
                val task = withContext(Dispatchers.IO) {
                    TaskRepository(this@LaunchDispatchService).getById(taskId)
                }
                if (task == null || !task.enabled) {
                    Log.w(TAG, "Task $taskId missing or disabled")
                    return@launch
                }

                Log.d(TAG, "Dispatching task ${task.id}: ${task.appLabel}")
                val notification = buildLaunchNotification(task)
                startForeground(NOTIFICATION_ID, notification)

                dispatchLaunch(task)

                withContext(Dispatchers.IO) {
                    AlarmScheduler.scheduleTask(this@LaunchDispatchService, task)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Launch dispatch failed for task $taskId", e)
            } finally {
                delay(3_000)
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }

        return START_NOT_STICKY
    }

    private fun dispatchLaunch(task: ScheduleTask) {
        // 1. 前台服务内直接尝试（部分 MIUI 版本有效）
        val directOk = AppLauncher.launch(this, task.packageName, task.activityClassName)
        if (directOk) {
            Log.d(TAG, "Direct launch succeeded")
            return
        }

        // 2. 经透明中转页亮屏后再启动（配合全屏 Intent 通知）
        try {
            LaunchTrampolineActivity.start(this, task.packageName, task.activityClassName)
            Log.d(TAG, "Trampoline launch attempted")
        } catch (e: Exception) {
            Log.e(TAG, "Trampoline launch failed", e)
        }
    }

    private fun createLaunchChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.launch_channel_name),
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = getString(R.string.launch_channel_desc)
                setBypassDnd(true)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun buildPlaceholderNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.launch_notification_title))
            .setContentText(getString(R.string.launch_notification_text))
            .setSmallIcon(R.drawable.ic_notification)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setOngoing(true)
            .build()
    }

    private fun buildLaunchNotification(task: ScheduleTask): Notification {
        val launchIntent = LaunchTrampolineActivity.createIntent(
            this,
            task.packageName,
            task.activityClassName
        )
        val requestCode = AlarmScheduler.requestCode(task.id) + 50_000
        val pendingFlags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        val fullScreenPending = PendingIntent.getActivity(this, requestCode, launchIntent, pendingFlags)
        val contentPending = PendingIntent.getActivity(this, requestCode + 1, launchIntent, pendingFlags)

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.launch_notification_title))
            .setContentText(getString(R.string.launch_notification_text_for, task.displayName()))
            .setSmallIcon(R.drawable.ic_notification)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setAutoCancel(true)
            .setContentIntent(contentPending)
            .setFullScreenIntent(fullScreenPending, true)
            .build()
    }

    companion object {
        private const val TAG = "LaunchDispatchService"
        private const val CHANNEL_ID = "launch_dispatch"
        private const val NOTIFICATION_ID = 1002
        const val EXTRA_TASK_ID = "extra_task_id"

        fun start(context: Context, taskId: Long) {
            val intent = Intent(context, LaunchDispatchService::class.java).apply {
                putExtra(EXTRA_TASK_ID, taskId)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
    }
}

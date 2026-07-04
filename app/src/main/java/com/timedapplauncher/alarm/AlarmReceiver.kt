package com.timedapplauncher.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.timedapplauncher.service.LaunchDispatchService

/**
 * 接收 AlarmManager 触发的广播，交给前台服务打开目标 App（避免后台 startActivity 被系统拦截）。
 */
class AlarmReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "AlarmReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val taskId = intent.getLongExtra(AlarmScheduler.EXTRA_TASK_ID, -1L)
        if (taskId <= 0) return

        Log.d(TAG, "Alarm fired for task $taskId")
        LaunchDispatchService.start(context.applicationContext, taskId)
    }
}

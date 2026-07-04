package com.timedapplauncher.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.timedapplauncher.service.ScheduleForegroundService

/**
 * 设备重启后恢复所有已启用任务的闹钟。
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED &&
            intent.action != Intent.ACTION_LOCKED_BOOT_COMPLETED
        ) return

        AlarmScheduler.rescheduleAll(context)
        ScheduleForegroundService.startIfNeeded(context)
    }
}

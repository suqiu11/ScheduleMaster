package com.timedapplauncher

import android.app.Application
import com.timedapplauncher.alarm.AlarmScheduler
import com.timedapplauncher.service.ScheduleForegroundService

class TimedAppApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        AlarmScheduler.rescheduleAll(this)
        ScheduleForegroundService.startIfNeeded(this)
    }
}

package com.timedapplauncher.ui

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import com.timedapplauncher.util.AppLauncher

/**
 * 透明中转页：到点时先亮屏，再启动目标 App。
 * 从 AlarmReceiver 后台唤起时使用，避免息屏下只启动进程不亮屏。
 */
class LaunchTrampolineActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        applyWakeAndLockScreenFlags()
        val wakeLock = acquireScreenWakeLock()

        val packageName = intent.getStringExtra(EXTRA_PACKAGE_NAME).orEmpty()
        val activityClass = intent.getStringExtra(EXTRA_ACTIVITY_CLASS).orEmpty()
        if (packageName.isNotBlank()) {
            AppLauncher.launch(this, packageName, activityClass)
        }

        wakeLock?.let {
            if (it.isHeld) it.release()
        }
        finish()
    }

    private fun applyWakeAndLockScreenFlags() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        }
        window.addFlags(
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
        )
    }

  @Suppress("DEPRECATION")
    private fun acquireScreenWakeLock(): PowerManager.WakeLock? {
        val pm = getSystemService(POWER_SERVICE) as PowerManager
        return try {
            pm.newWakeLock(
                PowerManager.SCREEN_BRIGHT_WAKE_LOCK or
                    PowerManager.ACQUIRE_CAUSES_WAKEUP or
                    PowerManager.ON_AFTER_RELEASE,
                "ScheduleMaster:LaunchWake"
            ).apply { acquire(10_000L) }
        } catch (_: Exception) {
            null
        }
    }

    companion object {
        const val EXTRA_PACKAGE_NAME = "extra_package_name"
        const val EXTRA_ACTIVITY_CLASS = "extra_activity_class"

        fun createIntent(
            context: Context,
            packageName: String,
            activityClassName: String = ""
        ): Intent {
            return Intent(context, LaunchTrampolineActivity::class.java).apply {
                putExtra(EXTRA_PACKAGE_NAME, packageName)
                putExtra(EXTRA_ACTIVITY_CLASS, activityClassName)
                addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP or
                        Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS
                )
            }
        }

        fun start(context: Context, packageName: String, activityClassName: String = "") {
            context.startActivity(createIntent(context, packageName, activityClassName))
        }
    }
}

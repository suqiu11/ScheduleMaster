package com.timedapplauncher.util

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.util.Log

/**
 * 通过系统 Intent 启动目标 App，支持指定 LAUNCHER Activity。
 */
object AppLauncher {

    private const val TAG = "AppLauncher"

    fun launch(context: Context, packageName: String, activityClassName: String = ""): Boolean {
        if (packageName.isBlank()) return false

        val launchIntent = buildLaunchIntent(context, packageName, activityClassName)
        if (launchIntent == null) {
            Log.w(TAG, "No launch intent for $packageName / $activityClassName")
            return false
        }

        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        return try {
            context.startActivity(launchIntent)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to launch $packageName / $activityClassName", e)
            false
        }
    }

    private fun buildLaunchIntent(
        context: Context,
        packageName: String,
        activityClassName: String
    ): Intent? {
        if (activityClassName.isNotBlank()) {
            return Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_LAUNCHER)
                component = ComponentName(packageName, activityClassName)
            }
        }
        return context.packageManager.getLaunchIntentForPackage(packageName)
            ?: buildFallbackIntent(context, packageName)
    }

    private fun buildFallbackIntent(context: Context, packageName: String): Intent? {
        val intent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
            setPackage(packageName)
        }
        val activities = context.packageManager.queryIntentActivities(
            intent,
            PackageManager.MATCH_DEFAULT_ONLY
        )
        if (activities.isEmpty()) return null
        intent.component = activities[0].activityInfo.let {
            ComponentName(it.packageName, it.name)
        }
        return intent
    }
}

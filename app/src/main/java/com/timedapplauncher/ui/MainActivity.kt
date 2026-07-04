package com.timedapplauncher.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.timedapplauncher.R
import com.timedapplauncher.alarm.AlarmScheduler
import com.timedapplauncher.data.ScheduleTask
import com.timedapplauncher.data.TaskRepository
import com.timedapplauncher.databinding.ActivityMainBinding
import com.timedapplauncher.service.ScheduleForegroundService
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var repository: TaskRepository
    private lateinit var adapter: TaskAdapter

    private val editLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        refreshServiceAndAlarms()
    }

    private val notificationPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* 无权限时前台服务通知可能不显示，Alarm 仍可工作 */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)

        repository = TaskRepository(this)
        setupList()
        requestNeededPermissions()
        checkExactAlarmPermission()
        checkFullScreenIntentPermission()

        binding.fabAdd.setOnClickListener {
            editLauncher.launch(EditTaskActivity.createIntent(this))
        }
    }

    private fun setupList() {
        adapter = TaskAdapter(
            onToggle = { task, enabled -> toggleTask(task, enabled) },
            onEdit = { task -> editLauncher.launch(EditTaskActivity.createIntent(this, task.id)) },
            onDelete = { task -> confirmDelete(task) }
        )
        binding.recyclerTasks.layoutManager = LinearLayoutManager(this)
        binding.recyclerTasks.adapter = adapter

        lifecycleScope.launch {
            repository.observeAll().collect { tasks ->
                adapter.submitList(tasks)
                binding.emptyView.visibility = if (tasks.isEmpty()) View.VISIBLE else View.GONE
            }
        }
    }

    private fun toggleTask(task: ScheduleTask, enabled: Boolean) {
        lifecycleScope.launch {
            val updated = task.copy(enabled = enabled)
            repository.update(updated)
            if (enabled) {
                AlarmScheduler.scheduleTask(this@MainActivity, updated)
                ScheduleForegroundService.startIfNeeded(this@MainActivity)
            } else {
                AlarmScheduler.cancelTask(this@MainActivity, task.id)
                if (repository.getEnabled().isEmpty()) {
                    ScheduleForegroundService.stop(this@MainActivity)
                }
            }
        }
    }

    private fun confirmDelete(task: ScheduleTask) {
        AlertDialog.Builder(this)
            .setTitle("删除任务")
            .setMessage("确定删除「${task.displayName()}」？")
            .setPositiveButton("删除") { _, _ ->
                lifecycleScope.launch {
                    AlarmScheduler.cancelTask(this@MainActivity, task.id)
                    repository.delete(task)
                    if (repository.getEnabled().isEmpty()) {
                        ScheduleForegroundService.stop(this@MainActivity)
                    }
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun refreshServiceAndAlarms() {
        AlarmScheduler.rescheduleAll(this)
        ScheduleForegroundService.startIfNeeded(this)
    }

    private fun requestNeededPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    private fun checkExactAlarmPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (!AlarmScheduler.canScheduleAlarms(this)) {
                AlertDialog.Builder(this)
                    .setTitle("需要精确闹钟权限")
                    .setMessage(
                        "请在系统设置中允许本应用使用「闹钟与提醒」权限，否则定时任务无法准时触发。\n\n" +
                            getString(R.string.permission_miui_hint)
                    )
                    .setPositiveButton("去设置") { _, _ ->
                        startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                            data = android.net.Uri.parse("package:$packageName")
                        })
                    }
                    .setNegativeButton("稍后", null)
                    .show()
            }
        }
    }

    private fun checkFullScreenIntentPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val nm = getSystemService(NOTIFICATION_SERVICE) as android.app.NotificationManager
            if (!nm.canUseFullScreenIntent()) {
                AlertDialog.Builder(this)
                    .setTitle(R.string.permission_full_screen_title)
                    .setMessage(
                        getString(R.string.permission_full_screen_message) + "\n\n" +
                            getString(R.string.permission_miui_hint)
                    )
                    .setPositiveButton("去设置") { _, _ ->
                        startActivity(Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT).apply {
                            data = android.net.Uri.parse("package:$packageName")
                        })
                    }
                    .setNegativeButton("稍后", null)
                    .show()
            }
        }
    }
}

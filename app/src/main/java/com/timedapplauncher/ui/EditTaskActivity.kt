package com.timedapplauncher.ui

import android.app.TimePickerDialog
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.chip.Chip
import com.timedapplauncher.R
import com.timedapplauncher.alarm.AlarmScheduler
import com.timedapplauncher.data.ScheduleTask
import com.timedapplauncher.data.TaskRepository
import com.timedapplauncher.databinding.ActivityEditTaskBinding
import com.timedapplauncher.service.ScheduleForegroundService
import com.timedapplauncher.util.AppLauncher
import kotlinx.coroutines.launch

class EditTaskActivity : AppCompatActivity() {

    private lateinit var binding: ActivityEditTaskBinding
    private lateinit var repository: TaskRepository

    private var taskId: Long = 0
    private var hour = 8
    private var minute = 0
    private var packageName = ""
    private var activityClassName = ""
    private var appLabel = ""
    private var daysOfWeek = ScheduleTask.DEFAULT_DAYS_MON_SAT
    private var enabled = true

    private val appPickerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            packageName = result.data?.getStringExtra(AppPickerActivity.EXTRA_PACKAGE) ?: ""
            activityClassName = result.data?.getStringExtra(AppPickerActivity.EXTRA_ACTIVITY_CLASS) ?: ""
            appLabel = result.data?.getStringExtra(AppPickerActivity.EXTRA_LABEL) ?: ""
            binding.textSelectedApp.text = appLabel.ifBlank { "未选择" }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityEditTaskBinding.inflate(layoutInflater)
        setContentView(binding.root)

        repository = TaskRepository(this)
        taskId = intent.getLongExtra(EXTRA_TASK_ID, 0)

        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        title = if (taskId > 0) "编辑任务" else "添加任务"

        setupDayChips()
        setupListeners()

        if (taskId > 0) {
            lifecycleScope.launch { loadTask(taskId) }
        } else {
            updateTimeDisplay()
            binding.textSelectedApp.text = "未选择"
        }
    }

    private suspend fun loadTask(id: Long) {
        val task = repository.getById(id) ?: return
        hour = task.hour
        minute = task.minute
        packageName = task.packageName
        activityClassName = task.activityClassName
        appLabel = task.appLabel
        daysOfWeek = task.daysOfWeek
        enabled = task.enabled
        binding.editName.setText(task.name)
        binding.editMinDelay.setText(task.minDelayMinutes.toString())
        binding.editMaxDelay.setText(task.maxDelayMinutes.toString())
        binding.textSelectedApp.text = appLabel.ifBlank { packageName }
        updateTimeDisplay()
        updateDayChips()
    }

    private fun setupDayChips() {
        val dayBits = listOf(
            ScheduleTask.DAY_MON, ScheduleTask.DAY_TUE, ScheduleTask.DAY_WED,
            ScheduleTask.DAY_THU, ScheduleTask.DAY_FRI, ScheduleTask.DAY_SAT, ScheduleTask.DAY_SUN
        )
        val labels = listOf("一", "二", "三", "四", "五", "六", "日")
        labels.forEachIndexed { index, label ->
            val chip = Chip(this).apply {
                text = "周$label"
                isCheckable = true
                tag = dayBits[index]
            }
            binding.chipGroupDays.addView(chip)
        }
        updateDayChips()
    }

    private fun updateDayChips() {
        for (i in 0 until binding.chipGroupDays.childCount) {
            val chip = binding.chipGroupDays.getChildAt(i) as Chip
            val bit = chip.tag as Int
            chip.isChecked = daysOfWeek and bit != 0
        }
    }

    private fun readDaysFromChips(): Int {
        var mask = 0
        for (i in 0 until binding.chipGroupDays.childCount) {
            val chip = binding.chipGroupDays.getChildAt(i) as Chip
            if (chip.isChecked) mask = mask or (chip.tag as Int)
        }
        return mask
    }

    private fun setupListeners() {
        binding.btnPickTime.setOnClickListener {
            TimePickerDialog(this, { _, h, m ->
                hour = h
                minute = m
                updateTimeDisplay()
            }, hour, minute, true).show()
        }

        binding.btnPickApp.setOnClickListener {
            appPickerLauncher.launch(Intent(this, AppPickerActivity::class.java))
        }

        binding.btnTest.setOnClickListener {
            if (packageName.isBlank()) {
                Toast.makeText(this, "请先选择目标 App", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val ok = AppLauncher.launch(this, packageName, activityClassName)
            Toast.makeText(
                this,
                if (ok) "已尝试打开 $appLabel" else "打开失败，请检查 App 是否已安装",
                Toast.LENGTH_SHORT
            ).show()
        }

        binding.btnSave.setOnClickListener { saveTask() }
    }

    private fun updateTimeDisplay() {
        binding.textTime.text = "%02d:%02d".format(hour, minute)
    }

    private fun saveTask() {
        daysOfWeek = readDaysFromChips()
        if (daysOfWeek == 0) {
            Toast.makeText(this, "请至少选择一天", Toast.LENGTH_SHORT).show()
            return
        }
        if (packageName.isBlank()) {
            Toast.makeText(this, "请选择目标 App", Toast.LENGTH_SHORT).show()
            return
        }

        val minDelay = binding.editMinDelay.text.toString().toIntOrNull() ?: 0
        val maxDelay = binding.editMaxDelay.text.toString().toIntOrNull() ?: 0
        if (minDelay < 0 || maxDelay < 0 || maxDelay < minDelay) {
            Toast.makeText(this, "随机延时范围无效", Toast.LENGTH_SHORT).show()
            return
        }

        val task = ScheduleTask(
            id = taskId,
            name = binding.editName.text.toString().trim(),
            packageName = packageName,
            activityClassName = activityClassName,
            appLabel = appLabel,
            hour = hour,
            minute = minute,
            daysOfWeek = daysOfWeek,
            enabled = enabled,
            minDelayMinutes = minDelay,
            maxDelayMinutes = maxDelay
        )

        lifecycleScope.launch {
            val savedId = if (taskId > 0) {
                repository.update(task)
                taskId
            } else {
                repository.insert(task)
            }
            val saved = repository.getById(savedId) ?: task.copy(id = savedId)
            val scheduled = AlarmScheduler.scheduleTask(this@EditTaskActivity, saved)
            ScheduleForegroundService.startIfNeeded(this@EditTaskActivity)
            if (scheduled) {
                Toast.makeText(this@EditTaskActivity, "已保存", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this@EditTaskActivity, R.string.schedule_failed, Toast.LENGTH_LONG).show()
            }
            finish()
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    companion object {
        private const val EXTRA_TASK_ID = "extra_task_id"

        fun createIntent(context: Context, taskId: Long = 0): Intent {
            return Intent(context, EditTaskActivity::class.java).apply {
                if (taskId > 0) putExtra(EXTRA_TASK_ID, taskId)
            }
        }
    }
}

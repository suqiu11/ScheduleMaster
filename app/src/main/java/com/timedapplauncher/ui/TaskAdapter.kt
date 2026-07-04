package com.timedapplauncher.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.timedapplauncher.alarm.RandomDelayCalculator
import com.timedapplauncher.data.ScheduleTask
import com.timedapplauncher.databinding.ItemTaskBinding

class TaskAdapter(
    private val onToggle: (ScheduleTask, Boolean) -> Unit,
    private val onEdit: (ScheduleTask) -> Unit,
    private val onDelete: (ScheduleTask) -> Unit
) : ListAdapter<ScheduleTask, TaskAdapter.ViewHolder>(Diff) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemTaskBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ViewHolder(private val binding: ItemTaskBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(task: ScheduleTask) {
            binding.textTime.text = task.timeText()
            binding.textName.text = task.displayName()
            binding.textApp.text = task.appLabel.ifBlank { task.packageName }
            binding.textDays.text = task.daysText()
            binding.textRandom.text = task.randomRangeText()
            binding.textActual.text = RandomDelayCalculator.formatDelayInfo(task)
            binding.switchEnabled.setOnCheckedChangeListener(null)
            binding.switchEnabled.isChecked = task.enabled
            binding.switchEnabled.setOnCheckedChangeListener { _, checked ->
                onToggle(task, checked)
            }
            binding.root.setOnClickListener { onEdit(task) }
            binding.btnDelete.setOnClickListener { onDelete(task) }
        }
    }

    private object Diff : DiffUtil.ItemCallback<ScheduleTask>() {
        override fun areItemsTheSame(a: ScheduleTask, b: ScheduleTask) = a.id == b.id
        override fun areContentsTheSame(a: ScheduleTask, b: ScheduleTask) = a == b
    }
}

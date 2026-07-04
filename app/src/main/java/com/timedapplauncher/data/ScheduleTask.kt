package com.timedapplauncher.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 定时任务数据模型。
 *
 * @param daysOfWeek 星期位掩码：bit0=周一 … bit6=周日，默认 63 = 周一到周六
 */
@Entity(tableName = "schedule_tasks")
data class ScheduleTask(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String = "",
    val packageName: String = "",
    /** 指定 LAUNCHER Activity；空则启动该包默认入口 */
    val activityClassName: String = "",
    val appLabel: String = "",
    val hour: Int = 8,
    val minute: Int = 0,
    val daysOfWeek: Int = DEFAULT_DAYS_MON_SAT,
    val enabled: Boolean = true,
    /** 随机延时下限（分钟，含） */
    val minDelayMinutes: Int = 0,
    /** 随机延时上限（分钟，含） */
    val maxDelayMinutes: Int = 0
) {
    companion object {
        const val DAY_MON = 1 shl 0
        const val DAY_TUE = 1 shl 1
        const val DAY_WED = 1 shl 2
        const val DAY_THU = 1 shl 3
        const val DAY_FRI = 1 shl 4
        const val DAY_SAT = 1 shl 5
        const val DAY_SUN = 1 shl 6
        const val DEFAULT_DAYS_MON_SAT = DAY_MON or DAY_TUE or DAY_WED or DAY_THU or DAY_FRI or DAY_SAT

        val DAY_LABELS = listOf("一", "二", "三", "四", "五", "六", "日")
    }

    fun displayName(): String = name.ifBlank { appLabel.ifBlank { packageName } }

    fun timeText(): String = "%02d:%02d".format(hour, minute)

    fun randomRangeText(): String = when {
        minDelayMinutes == 0 && maxDelayMinutes == 0 -> "无随机"
        minDelayMinutes == maxDelayMinutes -> "固定 +${minDelayMinutes} 分"
        else -> "随机 ${minDelayMinutes}~${maxDelayMinutes} 分"
    }

    fun daysText(): String {
        val selected = DAY_LABELS.filterIndexed { index, _ -> daysOfWeek and (1 shl index) != 0 }
        return if (selected.size == 7) "每天" else "周${selected.joinToString("")}"
    }
}

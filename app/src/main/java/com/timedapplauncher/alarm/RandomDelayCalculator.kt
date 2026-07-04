package com.timedapplauncher.alarm

import com.timedapplauncher.data.ScheduleTask
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.util.Random

/**
 * 随机延时计算：在 [基准时间 + minDelay, 基准时间 + maxDelay] 内均匀随机。
 * 种子 = taskId + 日期，保证同一天同一任务重启前后结果一致。
 */
object RandomDelayCalculator {

    fun randomDelayMinutes(task: ScheduleTask, date: LocalDate): Int {
        val min = task.minDelayMinutes.coerceAtLeast(0)
        val max = task.maxDelayMinutes.coerceAtLeast(min)
        if (min == max) return min

        val seed = (task.id.toString() + date.toString()).hashCode().toLong()
        val random = Random(seed)
        return random.nextInt(max - min + 1) + min
    }

    /**
     * 计算某任务在指定日期的实际触发时刻（含随机延时）。
     */
    fun actualTriggerDateTime(task: ScheduleTask, date: LocalDate): LocalDateTime {
        val base = LocalDateTime.of(date, LocalTime.of(task.hour, task.minute))
        val delay = randomDelayMinutes(task, date)
        return base.plusMinutes(delay.toLong())
    }

    /**
     * 查找下一次触发时间（毫秒时间戳），从 now 之后开始搜索。
     */
    fun nextTriggerMillis(task: ScheduleTask, zoneId: ZoneId = ZoneId.systemDefault()): Long? {
        if (!task.enabled || task.packageName.isBlank()) return null

        val now = LocalDateTime.now(zoneId)
        val today = now.toLocalDate()

        for (dayOffset in 0..7) {
            val candidateDate = today.plusDays(dayOffset.toLong())
            val dayBit = dayBitForDate(candidateDate)
            if (task.daysOfWeek and dayBit == 0) continue

            val trigger = actualTriggerDateTime(task, candidateDate)
            if (trigger.isAfter(now)) {
                return trigger.atZone(zoneId).toInstant().toEpochMilli()
            }
        }
        return null
    }

    /** 周一=bit0 … 周日=bit6，与 ScheduleTask 位掩码一致 */
    private fun dayBitForDate(date: LocalDate): Int {
        val dow = date.dayOfWeek.value // 1=Mon … 7=Sun
        return 1 shl (dow - 1)
    }

    fun formatDelayInfo(task: ScheduleTask, date: LocalDate = LocalDate.now()): String {
        val delay = randomDelayMinutes(task, date)
        val actual = actualTriggerDateTime(task, date)
        return "今日实际 ${actual.hour}:${"%02d".format(actual.minute)}（+${delay} 分）"
    }
}

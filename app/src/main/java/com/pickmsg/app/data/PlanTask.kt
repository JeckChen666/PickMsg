package com.pickmsg.app.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 计划任务：每天在 time(HH:mm) 且 weekdayMask 含当天时触发，发一条本地通知。
 * 相比竞品的 cron 表达式做了简化（周几位掩码 + 每日一次），覆盖主要用法且免依赖。
 */
@Entity(tableName = "plan_task")
data class PlanTask(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String = "",
    val time: String = "09:00",
    val weekdays: Int = 127,
    val notifyTitle: String = "",
    val notifyContent: String = "",
    val enabled: Boolean = false,
    val lastTriggerAt: Long = 0,
)

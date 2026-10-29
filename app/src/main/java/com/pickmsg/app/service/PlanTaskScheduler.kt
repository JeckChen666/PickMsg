package com.pickmsg.app.service

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.pickmsg.app.R
import com.pickmsg.app.PickMsgApp
import com.pickmsg.app.data.PlanTask
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.Calendar

/**
 * 计划任务调度：每日 HH:mm 且星期位掩码命中时发本地通知。
 * setExactAndAllowWhileIdle（无精确闹钟权限时降级 setAndAllowWhileIdle）+ 开机重排。
 */
object PlanTaskScheduler {

    const val ACTION_PLAN_FIRE = "com.pickmsg.app.ACTION_PLAN_TASK_FIRE"

    /** 全量重排所有启用中的任务（编辑/开关/开机后调用） */
    suspend fun rescheduleAll(context: Context) {
        val dao = PickMsgApp.from(context).database.planTaskDao()
        dao.getEnabled().forEach { task -> schedule(context, task) }
    }

    fun schedule(context: Context, task: PlanTask) {
        val alarm = context.getSystemService(AlarmManager::class.java) ?: return
        val triggerAt = nextTriggerAt(task.time, task.weekdays) ?: return
        val pending = pendingIntent(context, task.id)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarm.canScheduleExactAlarms()) {
            alarm.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pending)
        } else {
            alarm.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pending)
        }
    }

    fun cancel(context: Context, taskId: Long) {
        val alarm = context.getSystemService(AlarmManager::class.java) ?: return
        alarm.cancel(pendingIntent(context, taskId))
    }

    private fun pendingIntent(context: Context, taskId: Long): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            taskId.toInt(),
            Intent(ACTION_PLAN_FIRE).setClass(context, PlanTaskReceiver::class.java).putExtra("taskId", taskId),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    /** 下一个触发时刻：从现在起逐天找 weekdayMask 命中且时间未过的一天 */
    fun nextTriggerAt(time: String, weekdays: Int, now: Calendar = Calendar.getInstance()): Long? {
        val parts = time.split(':')
        val hour = parts.getOrNull(0)?.trim()?.toIntOrNull()?.coerceIn(0, 23) ?: 9
        val minute = parts.getOrNull(1)?.trim()?.toIntOrNull()?.coerceIn(0, 59) ?: 0
        val candidate = now.clone() as Calendar
        candidate.set(Calendar.SECOND, 0)
        candidate.set(Calendar.MILLISECOND, 0)
        for (dayOffset in 0..7) {
            candidate.set(Calendar.HOUR_OF_DAY, hour)
            candidate.set(Calendar.MINUTE, minute)
            if (dayOffset > 0 || candidate.timeInMillis > now.timeInMillis) {
                // Calendar.DAY_OF_WEEK: SUNDAY=1…SATURDAY=7 → bit6=周日
                val dow = candidate.get(Calendar.DAY_OF_WEEK)
                val bit = if (dow == Calendar.SUNDAY) 6 else dow - 2
                if ((weekdays shr bit) and 1 == 1) return candidate.timeInMillis
            }
            candidate.add(Calendar.DAY_OF_YEAR, 1)
        }
        return null
    }

    fun postNotification(context: Context, task: PlanTask) {
        NotificationManagerCompat.from(context).apply {
            createNotificationChannel(
                NotificationChannelCompat.Builder(CHANNEL_PLAN_TASK, NotificationManagerCompat.IMPORTANCE_DEFAULT)
                    .setName("计划任务")
                    .build(),
            )
        }
        val notification = NotificationCompat.Builder(context, CHANNEL_PLAN_TASK)
            .setSmallIcon(R.drawable.ic_pickmsg_notification)
            .setContentTitle(task.notifyTitle.ifBlank { task.name })
            .setContentText(task.notifyContent)
            .setStyle(NotificationCompat.BigTextStyle().bigText(task.notifyContent))
            .setAutoCancel(true)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(8000 + task.id.toInt(), notification)
        } catch (e: SecurityException) {
            EventLog.log("⚠️ 缺少通知权限，计划任务通知未发出")
        }
    }

    const val CHANNEL_PLAN_TASK = "channel_plan_task"
}

/** 闹钟触发：发通知 + 回写触发时间 + 重排下一次 */
class PlanTaskReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != PlanTaskScheduler.ACTION_PLAN_FIRE) return
        val taskId = intent.getLongExtra("taskId", -1)
        if (taskId <= 0) return
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val app = PickMsgApp.from(context)
                val task = app.database.planTaskDao().getById(taskId)
                if (task != null && task.enabled) {
                    PlanTaskScheduler.postNotification(context, task)
                    app.database.planTaskDao().updateLastTrigger(taskId, System.currentTimeMillis())
                    EventLog.log("⏰ 计划任务触发: ${task.name}")
                }
                PlanTaskScheduler.rescheduleAll(context)
            } finally {
                pending.finish()
            }
        }
    }
}

/** 开机/更新后重排全部计划任务 */
class PickMsgBootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                PlanTaskScheduler.rescheduleAll(context)
                EventLog.log("🔄 开机完成，计划任务已重排")
            } finally {
                pending.finish()
            }
        }
    }
}

/** 前台服务通知按钮：启用/暂停采集（暂停 ≠ 断开） */
class ServiceControlReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            NotificationCollectorService.ACTION_TOGGLE_PAUSE -> {
                val next = !ServiceState.paused.value
                ServiceState.setPaused(next)
                EventLog.log(if (next) "⏸ 采集已暂停（前台通知）" else "▶️ 采集已恢复（前台通知）")
                NotificationCollectorService.instance?.refreshForegroundNotification()
            }
        }
    }
}

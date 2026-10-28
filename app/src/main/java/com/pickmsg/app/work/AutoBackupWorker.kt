package com.pickmsg.app.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.pickmsg.app.PickMsgApp
import com.pickmsg.app.data.BackupSerializer
import com.pickmsg.app.data.SettingsStore
import com.pickmsg.app.data.WebDavClient
import com.pickmsg.app.service.EventLog
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit

/** 周期自动备份：把规则/场景/计划任务序列化为 JSON 上传到 WebDAV */
class AutoBackupWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val config = SettingsStore.webdav.value
        if (!config.configured) {
            EventLog.log("ℹ️ 自动备份跳过：WebDAV 未配置")
            return Result.success()
        }
        return try {
            val app = PickMsgApp.from(applicationContext)
            val rules = app.database.ruleDao().observeAll().first()
            val scenes = app.database.sceneDao().getAll()
            val tasks = app.database.planTaskDao().getEnabled()
            val json = BackupSerializer.serialize(rules, scenes, tasks)
            when (val r = WebDavClient.upload(config, BACKUP_FILE, json)) {
                is WebDavClient.Result.Ok -> {
                    EventLog.log("☁️ 自动备份已上传 (${rules.size} 规则)")
                    Result.success()
                }
                is WebDavClient.Result.Err -> {
                    EventLog.log("⚠️ 自动备份上传失败: ${r.message}")
                    Result.retry()
                }
            }
        } catch (e: Exception) {
            EventLog.log("⚠️ 自动备份异常: ${e.message}")
            Result.retry()
        }
    }

    companion object {
        const val BACKUP_FILE = "pickmsg-backup-auto.json"
        private const val WORK_NAME = "pickmsg_auto_backup"

        fun schedule(context: Context, intervalHours: Int) {
            val hours = intervalHours.coerceAtLeast(1).toLong()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                PeriodicWorkRequestBuilder<AutoBackupWorker>(hours, TimeUnit.HOURS).build(),
            )
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        }
    }
}

package com.pickmsg.app.ui

import android.app.Application
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.pickmsg.app.PickMsgApp
import com.pickmsg.app.data.BackupSerializer
import com.pickmsg.app.data.CsvBuilder
import com.pickmsg.app.data.SettingsStore
import com.pickmsg.app.data.SmtpConfig
import com.pickmsg.app.data.WebDavClient
import com.pickmsg.app.data.WebDavConfig
import com.pickmsg.app.service.EventLog
import com.pickmsg.app.service.NotificationCollectorService
import com.pickmsg.app.service.ServiceState
import com.pickmsg.app.work.AutoBackupWorker
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MineViewModel(application: Application) : AndroidViewModel(application) {

    private val app = PickMsgApp.from(application)
    private val repository = app.repository

    val connected: StateFlow<Boolean> = ServiceState.connected
    val paused: StateFlow<Boolean> = ServiceState.paused
    val logs: StateFlow<List<String>> = EventLog.entries
    val foregroundEnabled: StateFlow<Boolean> = SettingsStore.foregroundEnabled
    val keepDays: StateFlow<Int> = SettingsStore.keepDays
    val smtp: StateFlow<SmtpConfig> = SettingsStore.smtp
    val webdav: StateFlow<WebDavConfig> = SettingsStore.webdav
    val autoBackupEnabled: StateFlow<Boolean> = SettingsStore.autoBackupEnabled
    val autoBackupHours: StateFlow<Int> = SettingsStore.autoBackupHours

    private val _listenerEnabled = MutableStateFlow(false)
    val listenerEnabled: StateFlow<Boolean> = _listenerEnabled.asStateFlow()

    val totalCount: StateFlow<Int> = repository.observeCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    val todayCount: StateFlow<Int> = repository.observeCountSince(startOfToday())
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    val dailyCounts: StateFlow<List<com.pickmsg.app.data.DayCount>> = repository.observeDailyCounts(7)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val ruleCounts: StateFlow<List<com.pickmsg.app.data.LabelCount>> = repository.observeRuleCounts(10)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val appCounts: StateFlow<List<com.pickmsg.app.data.AppCount>> = repository.observeAppCounts()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    fun refreshListenerEnabled() {
        _listenerEnabled.value = NotificationCollectorService.isEnabled(getApplication())
    }

    fun togglePaused() {
        ServiceState.setPaused(!ServiceState.paused.value)
    }

    fun setForegroundEnabled(value: Boolean) {
        SettingsStore.setForegroundEnabled(value)
    }

    fun setKeepDays(value: Int) {
        SettingsStore.setKeepDays(value)
    }

    fun setSmtp(config: SmtpConfig) {
        SettingsStore.setSmtp(config)
        _message.value = "SMTP 配置已保存"
    }

    fun setWebDav(config: WebDavConfig) {
        SettingsStore.setWebDav(config)
        _message.value = "WebDAV 配置已保存"
    }

    fun setAutoBackup(enabled: Boolean, hours: Int) {
        SettingsStore.setAutoBackupEnabled(enabled)
        SettingsStore.setAutoBackupHours(hours)
        if (enabled) {
            AutoBackupWorker.schedule(getApplication(), hours)
            _message.value = "自动备份已开启（每 $hours 小时）"
        } else {
            AutoBackupWorker.cancel(getApplication())
            _message.value = "自动备份已关闭"
        }
    }

    fun batteryOptimizationIgnored(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return true
        val pm = getApplication<Application>().getSystemService(PowerManager::class.java)
        return pm?.isIgnoringBatteryOptimizations(getApplication<Application>().packageName) ?: true
    }

    fun requestIgnoreBatteryOptimizations() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return
        try {
            getApplication<Application>().startActivity(
                Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                    .setData(Uri.parse("package:" + getApplication<Application>().packageName))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        } catch (e: Exception) {
            _message.value = "无法打开电池优化设置: ${e.message}"
        }
    }

    fun cleanupNow() {
        viewModelScope.launch {
            val deleted = repository.cleanupOlderThan(SettingsStore.keepDays.value)
            _message.value = "已清理 $deleted 条过期记录"
        }
    }

    fun requestSnapshot() {
        if (!NotificationCollectorService.requestSnapshot()) {
            _message.value = "服务未连接，无法读取快照"
        }
    }

    fun exportCsvTo(uri: Uri) {
        viewModelScope.launch {
            val csv = CsvBuilder.fromRecords(repository.observeRecent(1000).first())
            runCatching {
                getApplication<Application>().contentResolver.openOutputStream(uri)?.use {
                    it.write(csv.toByteArray(Charsets.UTF_8))
                } ?: error("无法写入文件")
            }.onSuccess { _message.value = "CSV 已导出" }
                .onFailure { _message.value = "CSV 导出失败: ${it.message}" }
        }
    }

    fun exportBackupTo(uri: Uri) {
        viewModelScope.launch {
            runCatching {
                val json = buildBackupJson()
                getApplication<Application>().contentResolver.openOutputStream(uri)?.use {
                    it.write(json.toByteArray(Charsets.UTF_8))
                } ?: error("无法写入文件")
            }.onSuccess { _message.value = "备份已导出" }
                .onFailure { _message.value = "备份导出失败: ${it.message}" }
        }
    }

    fun importBackupFrom(uri: Uri) {
        viewModelScope.launch {
            runCatching {
                val json = getApplication<Application>().contentResolver.openInputStream(uri)?.use {
                    it.bufferedReader().readText()
                } ?: error("无法读取文件")
                val rules = BackupSerializer.parseRules(json)
                val scenes = BackupSerializer.parseScenes(json)
                val tasks = BackupSerializer.parseTasks(json)
                rules.forEach { app.database.ruleDao().upsert(it) }
                scenes.forEach { app.database.sceneDao().upsert(it) }
                tasks.forEach { app.database.planTaskDao().upsert(it) }
                "已导入 ${rules.size} 规则 / ${scenes.size} 场景 / ${tasks.size} 计划任务"
            }.onSuccess { _message.value = it }
                .onFailure { _message.value = "导入失败: ${it.message}" }
        }
    }

    private suspend fun buildBackupJson(): String {
        val rules = app.database.ruleDao().observeAll().first()
        val scenes = app.database.sceneDao().getAll()
        val tasks = app.database.planTaskDao().observeAll().first()
        return BackupSerializer.serialize(rules, scenes, tasks)
    }

    fun testWebDav() {
        viewModelScope.launch {
            _message.value = "WebDAV 测试中…"
            EventLog.log("🔗 WebDAV 测试连接开始")
            _message.value = when (val r = WebDavClient.test(SettingsStore.webdav.value)) {
                is WebDavClient.Result.Ok -> {
                    EventLog.log("✅ WebDAV 连接正常")
                    "WebDAV 连接正常"
                }
                is WebDavClient.Result.Err -> {
                    EventLog.log("❌ WebDAV 连接失败: ${r.message}")
                    "WebDAV 连接失败: ${r.message}"
                }
            }
        }
    }

    fun uploadBackup() {
        viewModelScope.launch {
            val config = SettingsStore.webdav.value
            if (!config.configured) {
                _message.value = "请先填写 WebDAV 地址"
                return@launch
            }
            _message.value = "上传中…"
            EventLog.log("☁️ WebDAV 上传开始 url=${config.url}")
            val json = runCatching { buildBackupJson() }.getOrElse { e ->
                EventLog.log("❌ 备份序列化失败: ${e.message}")
                _message.value = "备份序列化失败: ${e.message}"
                return@launch
            }
            EventLog.log("☁️ 备份 JSON 序列化完成 ${json.length} 字符")
            _message.value = when (val r = WebDavClient.upload(config, BACKUP_FILE, json)) {
                is WebDavClient.Result.Ok -> {
                    EventLog.log("✅ 备份已上传到 WebDAV")
                    "备份已上传到 WebDAV"
                }
                is WebDavClient.Result.Err -> {
                    EventLog.log("❌ WebDAV 上传失败: ${r.message}")
                    "上传失败: ${r.message}"
                }
            }
        }
    }

    fun downloadBackupImport() {
        viewModelScope.launch {
            val config = SettingsStore.webdav.value
            if (!config.configured) {
                _message.value = "请先填写 WebDAV 地址"
                return@launch
            }
            _message.value = "下载中…"
            EventLog.log("⬇️ WebDAV 下载开始")
            when (val r = WebDavClient.download(config, BACKUP_FILE)) {
                is WebDavClient.Result.Ok -> {
                    val rules = BackupSerializer.parseRules(r.value)
                    val scenes = BackupSerializer.parseScenes(r.value)
                    val tasks = BackupSerializer.parseTasks(r.value)
                    rules.forEach { app.database.ruleDao().upsert(it) }
                    scenes.forEach { app.database.sceneDao().upsert(it) }
                    tasks.forEach { app.database.planTaskDao().upsert(it) }
                    EventLog.log("✅ 已从 WebDAV 导入 ${rules.size} 规则 / ${scenes.size} 场景 / ${tasks.size} 计划")
                    _message.value = "已从 WebDAV 导入 ${rules.size} 规则 / ${scenes.size} 场景 / ${tasks.size} 计划"
                }
                is WebDavClient.Result.Err -> {
                    EventLog.log("❌ WebDAV 下载失败: ${r.message}")
                    _message.value = "下载失败: ${r.message}"
                }
            }
        }
    }

    fun clearMessage() {
        _message.value = null
    }

    private fun startOfToday(): Long = java.util.Calendar.getInstance().apply {
        set(java.util.Calendar.HOUR_OF_DAY, 0)
        set(java.util.Calendar.MINUTE, 0)
        set(java.util.Calendar.SECOND, 0)
        set(java.util.Calendar.MILLISECOND, 0)
    }.timeInMillis

    companion object {
        const val BACKUP_FILE = "pickmsg-backup.json"
    }
}

package com.pickmsg.app.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class SmtpConfig(
    val host: String = "",
    val port: Int = 465,
    val user: String = "",
    val password: String = "",
    val ssl: Boolean = true,
) {
    val configured: Boolean get() = host.isNotBlank() && user.isNotBlank() && password.isNotBlank()
}

data class WebDavConfig(
    val url: String = "",
    val user: String = "",
    val password: String = "",
) {
    val configured: Boolean get() = url.isNotBlank()
}

/** 全局设置（SharedPreferences + StateFlow） */
object SettingsStore {

    private const val PREFS = "pickmsg_settings"

    private lateinit var prefs: SharedPreferences

    private val _foregroundEnabled = MutableStateFlow(false)
    val foregroundEnabled: StateFlow<Boolean> = _foregroundEnabled.asStateFlow()

    private val _ignoreOngoing = MutableStateFlow(false)

    /** 忽略常驻通知：进度条/状态类通知完全不入库（关闭时折叠为单条记录并累计更新次数） */
    val ignoreOngoing: StateFlow<Boolean> = _ignoreOngoing.asStateFlow()

    private val _keepDays = MutableStateFlow(7)
    val keepDays: StateFlow<Int> = _keepDays.asStateFlow()

    private val _smtp = MutableStateFlow(SmtpConfig())
    val smtp: StateFlow<SmtpConfig> = _smtp.asStateFlow()

    private val _webdav = MutableStateFlow(WebDavConfig())
    val webdav: StateFlow<WebDavConfig> = _webdav.asStateFlow()

    private val _autoBackupEnabled = MutableStateFlow(false)
    val autoBackupEnabled: StateFlow<Boolean> = _autoBackupEnabled.asStateFlow()

    private val _autoBackupHours = MutableStateFlow(24)
    val autoBackupHours: StateFlow<Int> = _autoBackupHours.asStateFlow()

    fun init(context: Context) {
        prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        _foregroundEnabled.value = prefs.getBoolean("foregroundEnabled", false)
        _ignoreOngoing.value = prefs.getBoolean("ignoreOngoing", false)
        _keepDays.value = prefs.getInt("keepDays", 7)
        _smtp.value = SmtpConfig(
            host = prefs.getString("smtpHost", "").orEmpty(),
            port = prefs.getInt("smtpPort", 465),
            user = prefs.getString("smtpUser", "").orEmpty(),
            password = prefs.getString("smtpPassword", "").orEmpty(),
            ssl = prefs.getBoolean("smtpSsl", true),
        )
        _webdav.value = WebDavConfig(
            url = prefs.getString("webdavUrl", "").orEmpty(),
            user = prefs.getString("webdavUser", "").orEmpty(),
            password = prefs.getString("webdavPassword", "").orEmpty(),
        )
        _autoBackupEnabled.value = prefs.getBoolean("autoBackupEnabled", false)
        _autoBackupHours.value = prefs.getInt("autoBackupHours", 24)
    }

    fun setForegroundEnabled(value: Boolean) = persist("foregroundEnabled", value) { _foregroundEnabled.value = it }

    fun setIgnoreOngoing(value: Boolean) = persist("ignoreOngoing", value) { _ignoreOngoing.value = it }

    fun setKeepDays(value: Int) = persist("keepDays", value) { _keepDays.value = it }

    fun setSmtp(config: SmtpConfig) {
        prefs.edit()
            .putString("smtpHost", config.host)
            .putInt("smtpPort", config.port)
            .putString("smtpUser", config.user)
            .putString("smtpPassword", config.password)
            .putBoolean("smtpSsl", config.ssl)
            .apply()
        _smtp.value = config
    }

    fun setWebDav(config: WebDavConfig) {
        prefs.edit()
            .putString("webdavUrl", config.url)
            .putString("webdavUser", config.user)
            .putString("webdavPassword", config.password)
            .apply()
        _webdav.value = config
    }

    fun setAutoBackupEnabled(value: Boolean) = persist("autoBackupEnabled", value) { _autoBackupEnabled.value = it }

    fun setAutoBackupHours(value: Int) = persist("autoBackupHours", value) { _autoBackupHours.value = it }

    /** 过期清理的节流时间戳（每 8 小时一次，搭通知处理的便车） */
    var lastCleanupAt: Long
        get() = prefs.getLong("lastCleanupAt", 0)
        set(value) = prefs.edit().putLong("lastCleanupAt", value).apply()

    /** 预设规则只在首次启动种一次；用户删除后不再复活 */
    var presetSeeded: Boolean
        get() = prefs.getBoolean("presetSeeded", false)
        set(value) = prefs.edit().putBoolean("presetSeeded", value).apply()

    private inline fun <T> persist(key: String, value: T, crossinline update: (T) -> Unit) {
        prefs.edit().apply {
            when (value) {
                is Boolean -> putBoolean(key, value)
                is Int -> putInt(key, value)
            }
        }.apply()
        @Suppress("UNCHECKED_CAST")
        update(value)
    }
}

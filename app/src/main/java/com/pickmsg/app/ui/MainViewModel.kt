package com.pickmsg.app.ui

import android.annotation.SuppressLint
import android.app.Application
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.pickmsg.app.PickMsgApp
import com.pickmsg.app.data.AppCount
import com.pickmsg.app.data.NotificationRecord
import com.pickmsg.app.service.EventLog
import com.pickmsg.app.service.NotificationCollectorService
import com.pickmsg.app.service.ServiceState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Calendar

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = PickMsgApp.from(application).repository

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val _selectedPackage = MutableStateFlow<String?>(null)
    val selectedPackage: StateFlow<String?> = _selectedPackage.asStateFlow()

    private val _listenerEnabled = MutableStateFlow(false)
    val listenerEnabled: StateFlow<Boolean> = _listenerEnabled.asStateFlow()

    val connected: StateFlow<Boolean> = ServiceState.connected
    val paused: StateFlow<Boolean> = ServiceState.paused
    val logs: StateFlow<List<String>> = EventLog.entries

    val records: StateFlow<List<NotificationRecord>> =
        combine(repository.observeRecent(), _query, _selectedPackage) { list, query, pkg ->
            list.filter { record ->
                (pkg == null || record.packageName == pkg) &&
                    (query.isBlank() ||
                        record.title.contains(query, ignoreCase = true) ||
                        record.text.contains(query, ignoreCase = true) ||
                        record.appLabel.contains(query, ignoreCase = true))
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val totalCount: StateFlow<Int> = repository.observeCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    val todayCount: StateFlow<Int> = repository.observeCountSince(startOfToday())
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    val appCounts: StateFlow<List<AppCount>> = repository.observeAppCounts()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private var testCounter = 0

    /** 回到前台时刷新授权状态（用户可能刚从系统设置返回） */
    fun refreshListenerEnabled() {
        _listenerEnabled.value = NotificationCollectorService.isEnabled(getApplication())
    }

    fun setQuery(query: String) {
        _query.value = query
    }

    fun selectPackage(packageName: String?) {
        _selectedPackage.value = if (_selectedPackage.value == packageName) null else packageName
    }

    fun togglePaused() {
        val next = !paused.value
        ServiceState.setPaused(next)
        EventLog.log(if (next) "⏸ 采集已暂停" else "▶️ 采集已恢复")
    }

    fun requestSnapshot() {
        if (!NotificationCollectorService.requestSnapshot()) {
            EventLog.log("⚠️ 服务未连接，无法读取通知栏快照")
        }
    }

    fun delete(id: Long) {
        viewModelScope.launch { repository.delete(id) }
    }

    fun setPinned(id: Long, pinned: Boolean) {
        viewModelScope.launch { repository.setPinned(id, pinned) }
    }

    fun clearAll() {
        viewModelScope.launch {
            repository.clear()
            EventLog.log("🗑 已清空全部记录")
        }
    }

    @SuppressLint("MissingPermission")
    fun sendTestNotification(ongoing: Boolean = false) {
        val context = getApplication<Application>()
        val manager = NotificationManagerCompat.from(context)
        if (!manager.areNotificationsEnabled()) {
            EventLog.log("⚠️ 本应用通知被关闭，无法发送测试通知")
            return
        }
        manager.createNotificationChannel(
            NotificationChannelCompat.Builder(TEST_CHANNEL_ID, NotificationManagerCompat.IMPORTANCE_DEFAULT)
                .setName("测试通知")
                .build(),
        )
        if (ongoing) {
            // 固定 id：重复发送 = 同一条通知的更新，用于验证常驻折叠
            val body = "常驻测试更新于 ${formatTime(System.currentTimeMillis())}。" +
                "多次发送只会折叠为一条记录，更新次数 +1。"
            val notification = NotificationCompat.Builder(context, TEST_CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle("常驻测试通知")
                .setContentText(body)
                .setStyle(NotificationCompat.BigTextStyle().bigText(body))
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .build()
            try {
                manager.notify(TEST_ONGOING_ID, notification)
                EventLog.log("📤 已发送常驻测试通知（同 key 更新）")
            } catch (e: SecurityException) {
                EventLog.log("⚠️ 缺少 POST_NOTIFICATIONS 权限")
            }
            return
        }
        val index = ++testCounter
        val body = "这是第 $index 条测试通知，发送于 ${formatDateTime(System.currentTimeMillis())}。" +
            "若它出现在列表中，说明「监听 → 提取 → 入库 → 展示」全链路正常。"
        val notification = NotificationCompat.Builder(context, TEST_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("PickMsg 测试通知 #$index")
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setAutoCancel(true)
            .build()
        try {
            manager.notify(TEST_NOTIFICATION_BASE_ID + index, notification)
            EventLog.log("📤 已发送测试通知 #$index")
        } catch (e: SecurityException) {
            EventLog.log("⚠️ 缺少 POST_NOTIFICATIONS 权限，无法发送测试通知")
        }
    }

    fun cancelOngoingTest() {
        NotificationManagerCompat.from(getApplication()).cancel(TEST_ONGOING_ID)
        EventLog.log("🗑 常驻测试通知已取消（移除后重发将生成新记录）")
    }

    private fun startOfToday(): Long = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    private companion object {
        const val TEST_CHANNEL_ID = "pickmsg_test"
        const val TEST_NOTIFICATION_BASE_ID = 1000
        const val TEST_ONGOING_ID = 99
    }
}

package com.pickmsg.app.service

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.Build
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.pickmsg.app.MainActivity
import com.pickmsg.app.PickMsgApp
import com.pickmsg.app.data.ActionType
import com.pickmsg.app.data.NotificationRecord
import com.pickmsg.app.data.RuleInfo
import com.pickmsg.app.data.Scene
import com.pickmsg.app.data.SettingsStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

/** 常驻通知管理页展示的条目 */
data class ActiveNotificationInfo(
    val key: String,
    val packageName: String,
    val appLabel: String,
    val title: String,
    val text: String,
    val isOngoing: Boolean,
    val isHidden: Boolean,
)

class NotificationCollectorService : NotificationListenerService() {

    private val serviceJob = SupervisorJob()
    private val scope = CoroutineScope(serviceJob + Dispatchers.Default)

    private val dedupLock = Any()
    private var lastKey: String? = null
    private var lastPostTime = 0L

    /** 当前在通知栏"存活"的通知 key：连接时播种，移除时剔除。
     *  常驻通知折叠更新的依据——存活期间的重复 post 是同一条通知的更新而非新通知 */
    private val liveKeys: MutableSet<String> = ConcurrentHashMap.newKeySet()

    /** 已记录过"合并常驻更新"日志的 key，避免进度条类高频更新刷爆日志 */
    private val mergeLogged = ConcurrentHashMap.newKeySet<String>()

    private var rules: List<RuleInfo> = emptyList()
    private var scenes: List<Scene> = emptyList()

    private var smsReceiverRegistered = false

    private val repository get() = PickMsgApp.from(this).repository

    override fun onCreate() {
        super.onCreate()
        createChannels()
        observeRules()
        observeForegroundToggle()
    }

    @OptIn(FlowPreview::class)
    private fun observeRules() {
        scope.launch {
            val db = PickMsgApp.from(this@NotificationCollectorService).database
            launch {
                db.ruleDao().observeAll().debounce(200).collect { rules = it.filter { r -> r.enable } }
            }
            launch {
                db.sceneDao().observeAll().debounce(200).collect { scenes = it }
            }
        }
    }

    private fun observeForegroundToggle() {
        scope.launch {
            SettingsStore.foregroundEnabled.collect { applyForeground() }
        }
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        instance = this
        ServiceState.setConnected(true)
        EventLog.log("✅ 通知监听服务已连接")
        registerSmsResultReceiver()
        applyForeground()
        // 先播种存活集合，随后的快照与回调才能正确识别"已存在通知的更新"
        runCatching { activeNotifications?.forEach { liveKeys.add(it.key) } }
        collectActiveNotifications()
        scope.launch { PlanTaskScheduler.rescheduleAll(this@NotificationCollectorService) }
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        instance = null
        ServiceState.setConnected(false)
        unregisterSmsResultReceiver()
        EventLog.log("❌ 通知监听服务已断开")
    }

    override fun onDestroy() {
        instance = null
        serviceJob.cancel()
        super.onDestroy()
    }

    // 回调内不做任何耗时操作：阻塞 binder 线程会让系统 watchdog 断开监听
    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        sbn ?: return
        scope.launch { handlePosted(sbn) }
    }

    private suspend fun handlePosted(sbn: StatusBarNotification) {
        // 系统对同一条通知可能多次回调（如 RemoteViews 更新），用 key+postTime 单槽去重
        val duplicate = synchronized(dedupLock) {
            if (sbn.key == lastKey && sbn.postTime == lastPostTime) {
                true
            } else {
                lastKey = sbn.key
                lastPostTime = sbn.postTime
                false
            }
        }
        if (duplicate) return
        if (ServiceState.paused.value) {
            EventLog.log("🔕 采集已暂停，忽略 ${sbn.packageName}")
            return
        }
        if (SettingsStore.ignoreOngoing.value && sbn.isOngoing) {
            // 忽略常驻通知：进度条/状态类完全不入库（用户显式开启）
            EventLog.log("🚫 忽略常驻通知: ${sbn.packageName}")
            return
        }
        val record = buildRecord(sbn, NotificationRecord.SOURCE_POSTED)
        if (record == null) {
            EventLog.log("📭 无文本内容，跳过 ${sbn.packageName}")
            return
        }
        // 常驻折叠：存活中的常驻通知再次 post 是同一条通知的更新，原地合并而非插新行
        if (NotificationUpdatePolicy.resolve(record.isOngoing, sbn.key in liveKeys) ==
            NotificationUpdatePolicy.Action.MERGE_IN_PLACE
        ) {
            val existingId = repository.findIdBySbnKey(sbn.key)
            if (existingId != null) {
                repository.mergeOngoingUpdate(existingId, record)
                if (mergeLogged.add(sbn.key)) {
                    EventLog.log("🔗 合并常驻更新: ${record.appLabel}（后续更新不再逐条记录）")
                }
                return
            }
            // 既有记录不存在（如被用户清空）→ 落到正常插入
        }
        val rowId = repository.insert(record)
        if (rowId == -1L) return
        liveKeys.add(sbn.key)
        EventLog.log("📬 ${record.appLabel}: ${record.title.ifEmpty { record.text.take(20) }}")
        com.pickmsg.app.widget.PickMsgWidget.tryUpdate(this)
        if (record.isSensitive) {
            // 敏感占位符在规则循环之前短路，避免把占位文本当真内容外发
            EventLog.log("🔒 敏感占位符通知，跳过规则匹配")
            return
        }
        if (sbn.packageName == packageName) {
            // 自身重发/测试的通知只采集不进规则，防止"重发 → 再命中"死循环
            return
        }
        processRules(sbn, rowId, record)
        cleanupExpiredIfDue()
    }

    private suspend fun processRules(sbn: StatusBarNotification, rowId: Long, record: NotificationRecord) {
        if (rules.isEmpty()) return
        val hits = rules.filter { RuleMatcher.matches(it, record.appKey, record.title, record.text) }
        if (hits.isEmpty()) return
        val results = mutableListOf<String>()
        for (rule in hits) {
            if (!scenesSatisfied(rule)) {
                EventLog.log("🌙 规则「${rule.name}」场景不满足，跳过")
                continue
            }
            EventLog.log("🎯命中规则: ${rule.name}")
            // 格式化管线先于一切"发送内容"的动作
            val (formattedTitle, formattedText) = RuleFormatter.apply(rule.formatOps, record.title, record.text)
            for (action in rule.actions) {
                results += ActionExecutor.execute(
                    context = this,
                    action = action,
                    sbnKey = record.sbnKey,
                    contentIntent = sbn.notification.contentIntent,
                    appLabel = record.appLabel,
                    title = formattedTitle,
                    text = formattedText,
                    canceller = { key -> cancelNotification(key) },
                    scope = scope,
                )
            }
        }
        if (results.isNotEmpty()) {
            repository.updateHitInfo(
                id = rowId,
                ruleId = hits.first().id,
                ruleName = hits.joinToString("、") { it.name },
                actionResults = results,
            )
        }
    }

    /** 规则未绑定场景直接通过；绑定后按 ANY/ALL 求值（仅统计启用中的场景） */
    private fun scenesSatisfied(rule: RuleInfo): Boolean {
        if (rule.scenesIds.isEmpty()) return true
        val bound = scenes.filter { it.enable && it.id in rule.scenesIds }
        if (bound.isEmpty()) return true
        val satisfied = bound.map { SceneEvaluator.satisfied(this, it) }
        return when (rule.sceneMatchMode) {
            com.pickmsg.app.data.SceneMatchMode.ANY -> satisfied.any { it }
            com.pickmsg.app.data.SceneMatchMode.ALL -> satisfied.all { it }
        }
    }

    /** 每 8 小时搭车清理一次过期记录（置顶的不删） */
    private suspend fun cleanupExpiredIfDue() {
        val now = System.currentTimeMillis()
        if (now - SettingsStore.lastCleanupAt < 8 * 3600_000L) return
        SettingsStore.lastCleanupAt = now
        val keepDays = SettingsStore.keepDays.value
        val deleted = repository.cleanupOlderThan(keepDays)
        if (deleted > 0) EventLog.log("🗑 已清理 $keepDays 天前的过期记录 $deleted 条")
    }

    // ---------------------------------------------------------------------------
    // 前台服务（保活）：specialUse 类型，通知带 暂停/恢复 按钮
    // ---------------------------------------------------------------------------

    fun refreshForegroundNotification() = applyForeground()

    private fun applyForeground() {
        if (!SettingsStore.foregroundEnabled.value) {
            if (foregroundLaunched) {
                stopForeground(STOP_FOREGROUND_REMOVE)
                foregroundLaunched = false
            }
            return
        }
        val notification = buildForegroundNotification()
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                ServiceCompat.startForeground(
                    this,
                    FOREGROUND_NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
                )
            } else {
                startForeground(FOREGROUND_NOTIFICATION_ID, notification)
            }
            foregroundLaunched = true
        } catch (e: Exception) {
            EventLog.log("⚠️ 前台服务启动失败: ${e.message}")
        }
    }

    private var foregroundLaunched = false

    private fun buildForegroundNotification(): Notification {
        val contentIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val paused = ServiceState.paused.value
        val toggleIntent = PendingIntent.getBroadcast(
            this,
            1,
            Intent(NotificationCollectorService.ACTION_TOGGLE_PAUSE).setClass(this, ServiceControlReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, CHANNEL_FOREGROUND)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("PickMsg 采集服务运行中")
            .setContentText(if (paused) "采集已暂停（服务保持连接）" else "正在采集系统通知")
            .setOngoing(true)
            .setContentIntent(contentIntent)
            .addAction(0, if (paused) "恢复采集" else "暂停采集", toggleIntent)
            .build()
    }

    // ---------------------------------------------------------------------------
    // 短信发送结果回写
    // ---------------------------------------------------------------------------

    private val smsSentReceiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val number = intent.getStringExtra("number").orEmpty()
            when (resultCode) {
                android.app.Activity.RESULT_OK -> EventLog.log("✅ 短信已送达: $number")
                else -> EventLog.log("❌ 短信发送失败 ($number): resultCode=$resultCode")
            }
        }
    }

    private fun registerSmsResultReceiver() {
        if (smsReceiverRegistered) return
        ContextCompat.registerReceiver(
            this,
            smsSentReceiver,
            IntentFilter(SmsSender.ACTION_SMS_SENT),
            ContextCompat.RECEIVER_EXPORTED,
        )
        smsReceiverRegistered = true
    }

    private fun unregisterSmsResultReceiver() {
        if (!smsReceiverRegistered) return
        runCatching { unregisterReceiver(smsSentReceiver) }
        smsReceiverRegistered = false
    }

    // ---------------------------------------------------------------------------
    // 常驻通知管理：快照 / 一年期 snooze 隐藏 / 100ms 召回
    // ---------------------------------------------------------------------------

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        super.onNotificationRemoved(sbn)
        sbn ?: return
        // 通知已移除：出存活集合，之后同 key 重发才视为新通知
        liveKeys.remove(sbn.key)
        // 被移除的通知若在管理列表中，刷新状态
        refreshActiveList()
    }

    private fun refreshActiveList() {
        scope.launch {
            val hiddenKeys = hiddenCache.keys
            val visible = readActiveNotifications().filter { it.key !in hiddenKeys }
            activeNotificationsFlow.value = visible + hiddenCache.values.toList()
        }
    }

    private fun readActiveNotifications(): List<ActiveNotificationInfo> {
        val sbns = try {
            activeNotifications
        } catch (e: Exception) {
            return emptyList()
        }
        return sbns.map { sbn ->
            ActiveNotificationInfo(
                key = sbn.key,
                packageName = sbn.packageName,
                appLabel = AppInfoCache.label(this, sbn.packageName),
                title = sbn.notification.extras?.getCharSequence(android.app.Notification.EXTRA_TITLE)?.toString().orEmpty(),
                text = sbn.notification.extras?.getCharSequence(android.app.Notification.EXTRA_TEXT)?.toString().orEmpty(),
                isOngoing = sbn.isOngoing,
                isHidden = false,
            )
        }
    }

    private fun collectActiveNotifications() {
        scope.launch {
            val sbns = try {
                activeNotifications
            } catch (e: Exception) {
                EventLog.log("⚠️ 读取通知栏快照失败: ${e.message}")
                null
            } ?: return@launch
            val ignoreOngoing = SettingsStore.ignoreOngoing.value
            var inserted = 0
            for (sbn in sbns) {
                if (ignoreOngoing && sbn.isOngoing) continue
                val record = buildRecord(sbn, NotificationRecord.SOURCE_SNAPSHOT) ?: continue
                // 重连快照同样走折叠：存活中的常驻通知不因快照反复堆新行
                if (NotificationUpdatePolicy.resolve(record.isOngoing, sbn.key in liveKeys) ==
                    NotificationUpdatePolicy.Action.MERGE_IN_PLACE
                ) {
                    val existingId = repository.findIdBySbnKey(sbn.key)
                    if (existingId != null) {
                        repository.mergeOngoingUpdate(existingId, record)
                        continue
                    }
                }
                if (repository.insert(record) != -1L) inserted++
            }
            liveKeys.addAll(sbns.map { it.key })
            EventLog.log("📸 通知栏快照：${sbns.size} 条，新增 $inserted 条")
        }
    }

    private fun hide(key: String) {
        scope.launch {
            runCatching { snoozeNotification(key, 31_536_000_000L) }
                .onFailure {
                    EventLog.log("⚠️ 隐藏失败: ${it.message}")
                    return@launch
                }
            // snooze 后 getActiveNotifications 不再包含该通知，会话内缓存以便恢复
            activeNotificationsFlow.value.firstOrNull { it.key == key }?.let {
                hiddenCache[key] = it.copy(isHidden = true)
            }
            EventLog.log("🙈 已隐藏常驻通知（一年期 snooze，可恢复）")
            delay(300)
            refreshActiveList()
        }
    }

    private fun restore(key: String) {
        scope.launch {
            runCatching { snoozeNotification(key, 100L) }
                .onFailure { EventLog.log("⚠️ 恢复失败: ${it.message}") }
            hiddenCache.remove(key)
            EventLog.log("👁 已恢复常驻通知")
            delay(300)
            refreshActiveList()
        }
    }

    private fun createChannels() {
        val manager = NotificationManagerCompat.from(this)
        manager.createNotificationChannel(
            NotificationChannelCompat.Builder(CHANNEL_RULE_REPOST, NotificationManagerCompat.IMPORTANCE_DEFAULT)
                .setName("规则重发通知")
                .build(),
        )
        manager.createNotificationChannel(
            // 重要性必须 ≥DEFAULT，否则通知栏不展示暂停/恢复按钮（渠道创建后重要性不可改，故用新 ID）
            NotificationChannelCompat.Builder(CHANNEL_FOREGROUND, NotificationManagerCompat.IMPORTANCE_DEFAULT)
                .setName("前台服务")
                .build(),
        )
        manager.createNotificationChannel(
            NotificationChannelCompat.Builder(PlanTaskScheduler.CHANNEL_PLAN_TASK, NotificationManagerCompat.IMPORTANCE_DEFAULT)
                .setName("计划任务")
                .build(),
        )
    }

    private fun buildRecord(sbn: StatusBarNotification, source: String): NotificationRecord? {
        val extracted = NotificationExtractor.extract(this, sbn)
        if (extracted.title.isEmpty() && extracted.text.isEmpty()) return null
        val notification = sbn.notification
        val userHash = sbn.user.hashCode()
        val appKey = if (userHash == 0 || userHash == -1) sbn.packageName else "${sbn.packageName}|$userHash"
        return NotificationRecord(
            sbnKey = sbn.key,
            packageName = sbn.packageName,
            appKey = appKey,
            appLabel = AppInfoCache.label(this, sbn.packageName),
            title = extracted.title,
            text = extracted.text,
            subText = extracted.subText,
            channelId = notification.channelId.orEmpty(),
            category = notification.category.orEmpty(),
            postTime = sbn.postTime,
            isOngoing = sbn.isOngoing,
            isClearable = sbn.isClearable,
            isSensitive = NotificationExtractor.isSensitivePlaceholder(extracted.title, extracted.text),
            source = source,
            createdAt = System.currentTimeMillis(),
        )
    }

    companion object {
        const val CHANNEL_RULE_REPOST = "channel_rule_repost"
        const val CHANNEL_FOREGROUND = "channel_foreground_service_v2"
        const val ACTION_TOGGLE_PAUSE = "com.pickmsg.app.ACTION_TOGGLE_PAUSE"

        private const val FOREGROUND_NOTIFICATION_ID = 7355608

        @Volatile
        var instance: NotificationCollectorService? = null
            private set

        private val activeNotificationsFlow = MutableStateFlow<List<ActiveNotificationInfo>>(emptyList())

        /** snooze 隐藏的通知在本会话内的缓存，供恢复 */
        private val hiddenCache = LinkedHashMap<String, ActiveNotificationInfo>()

        /** 常驻通知管理页观察此流；服务未连接时为空 */
        val activeList: StateFlow<List<ActiveNotificationInfo>> = activeNotificationsFlow.asStateFlow()

        fun requestActiveList(): Boolean {
            val service = instance ?: return false
            service.refreshActiveList()
            return true
        }

        fun hideOngoing(key: String): Boolean {
            val service = instance ?: return false
            service.hide(key)
            return true
        }

        fun restoreOngoing(key: String): Boolean {
            val service = instance ?: return false
            service.restore(key)
            return true
        }

        fun requestSnapshot(): Boolean {
            val service = instance ?: return false
            service.collectActiveNotifications()
            return true
        }

        fun isEnabled(context: Context): Boolean =
            NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)
    }
}

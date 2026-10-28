@file:OptIn(ExperimentalMaterial3Api::class)

package com.pickmsg.app.ui

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pickmsg.app.data.NotificationRecord
import com.pickmsg.app.service.EventLog
import com.pickmsg.app.service.NotificationCollectorService

private enum class GroupMode(val label: String) { TIME("按时间"), APP("按应用"), RULE("按规则") }

private sealed interface Row {
    data class Header(val text: String) : Row
    data class Item(val record: NotificationRecord) : Row
}

@Composable
fun RecordsScreen(viewModel: MainViewModel) {
    val records by viewModel.records.collectAsStateWithLifecycle()
    val listenerEnabled by viewModel.listenerEnabled.collectAsStateWithLifecycle()
    val connected by viewModel.connected.collectAsStateWithLifecycle()
    val paused by viewModel.paused.collectAsStateWithLifecycle()
    val totalCount by viewModel.totalCount.collectAsStateWithLifecycle()
    val todayCount by viewModel.todayCount.collectAsStateWithLifecycle()
    val appCounts by viewModel.appCounts.collectAsStateWithLifecycle()
    val query by viewModel.query.collectAsStateWithLifecycle()
    val selectedPackage by viewModel.selectedPackage.collectAsStateWithLifecycle()
    val logs by viewModel.logs.collectAsStateWithLifecycle()

    var selectedRecord by remember { mutableStateOf<NotificationRecord?>(null) }
    var showClearDialog by remember { mutableStateOf(false) }
    var showLogs by remember { mutableStateOf(false) }
    var groupMode by remember { mutableStateOf(GroupMode.TIME) }

    val context = LocalContext.current
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            viewModel.sendTestNotification()
        } else {
            EventLog.log("⚠️ 未授予通知权限，无法发送测试通知")
        }
    }

    val current = selectedRecord
    if (current != null) {
        BackHandler { selectedRecord = null }
        DetailScreen(
            record = current,
            onBack = { selectedRecord = null },
            onDelete = {
                viewModel.delete(current.id)
                selectedRecord = null
            },
            onTogglePin = { viewModel.setPinned(current.id, !current.isPinned) },
        )
        return
    }

    val rows = remember(records, groupMode) { buildRows(records, groupMode) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("PickMsg") },
                actions = {
                    IconButton(onClick = { showLogs = !showLogs }) {
                        Icon(Icons.Default.Info, contentDescription = "运行日志")
                    }
                    IconButton(onClick = viewModel::requestSnapshot) {
                        Icon(Icons.Default.Refresh, contentDescription = "读取通知栏快照")
                    }
                    IconButton(onClick = { showClearDialog = true }) {
                        Icon(Icons.Default.Delete, contentDescription = "清空记录")
                    }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = {
                    val needsPermission = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
                        PackageManager.PERMISSION_GRANTED
                    if (needsPermission) {
                        permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    } else {
                        viewModel.sendTestNotification()
                    }
                },
                icon = { Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null) },
                text = { Text("发送测试通知") },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding),
            contentPadding = PaddingValues(start = 16.dp, top = 8.dp, end = 16.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                StatusCard(
                    listenerEnabled = listenerEnabled,
                    connected = connected,
                    paused = paused,
                    totalCount = totalCount,
                    todayCount = todayCount,
                    appCount = appCounts.size,
                    onOpenSettings = { openListenerSettings(context) },
                    onTogglePaused = viewModel::togglePaused,
                )
            }
            if (showLogs) {
                item { LogCard(logs) }
            }
            item {
                OutlinedTextField(
                    value = query,
                    onValueChange = viewModel::setQuery,
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("搜索标题 / 内容 / 应用") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    singleLine = true,
                )
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    GroupMode.entries.forEach { mode ->
                        FilterChip(
                            selected = groupMode == mode,
                            onClick = { groupMode = mode },
                            label = { Text(mode.label) },
                        )
                    }
                }
            }
            if (appCounts.isNotEmpty()) {
                item {
                    AppFilterRow(
                        appCounts = appCounts,
                        totalCount = totalCount,
                        selectedPackage = selectedPackage,
                        onSelect = viewModel::selectPackage,
                    )
                }
            }
            if (records.isEmpty()) {
                item {
                    EmptyState(
                        listenerEnabled = listenerEnabled,
                        hasFilter = query.isNotBlank() || selectedPackage != null,
                    )
                }
            }
            rows.forEach { row ->
                when (row) {
                    is Row.Header -> item(key = "header_${row.text}") {
                        Text(
                            row.text,
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    }
                    is Row.Item -> item(key = "record_${row.record.id}") {
                        RecordItem(record = row.record, onClick = { selectedRecord = row.record })
                    }
                }
            }
        }
    }

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text("清空全部记录？") },
            text = { Text("将删除本地保存的 $totalCount 条通知记录，此操作不可恢复。") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.clearAll()
                    showClearDialog = false
                }) { Text("清空") }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) { Text("取消") }
            },
        )
    }
}

private fun buildRows(records: List<NotificationRecord>, mode: GroupMode): List<Row> {
    if (mode == GroupMode.TIME) return records.map { Row.Item(it) }
    val keyOf: (NotificationRecord) -> String = when (mode) {
        GroupMode.TIME -> fun(_: NotificationRecord) = ""
        GroupMode.APP -> fun(record: NotificationRecord) = record.appLabel
        GroupMode.RULE -> fun(record: NotificationRecord) = record.ruleName.ifBlank { "未命中规则" }
    }
    val rows = mutableListOf<Row>()
    var lastKey: String? = null
    records.forEach { record ->
        val key = keyOf(record)
        if (key != lastKey) {
            val count = records.count { keyOf(it) == key }
            rows += Row.Header("$key · $count")
            lastKey = key
        }
        rows += Row.Item(record)
    }
    return rows
}

@Composable
private fun StatusCard(
    listenerEnabled: Boolean,
    connected: Boolean,
    paused: Boolean,
    totalCount: Int,
    todayCount: Int,
    appCount: Int,
    onOpenSettings: () -> Unit,
    onTogglePaused: () -> Unit,
) {
    val containerColor = when {
        !listenerEnabled -> MaterialTheme.colorScheme.errorContainer
        paused -> MaterialTheme.colorScheme.tertiaryContainer
        else -> MaterialTheme.colorScheme.primaryContainer
    }
    val headline = when {
        !listenerEnabled -> "未授予通知使用权"
        !connected -> "已授权，等待系统连接服务…"
        paused -> "采集已暂停"
        else -> "正在采集通知"
    }
    Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = containerColor)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Notifications, contentDescription = null)
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(headline, style = MaterialTheme.typography.titleMedium)
                    Text(
                        "通知使用权：${if (listenerEnabled) "已授权" else "未授权"} · " +
                            "服务：${if (connected) "已连接" else "未连接"}",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                if (listenerEnabled) {
                    Switch(checked = !paused, onCheckedChange = { onTogglePaused() })
                }
            }
            if (!listenerEnabled) {
                Spacer(modifier = Modifier.height(12.dp))
                Button(onClick = onOpenSettings, modifier = Modifier.fillMaxWidth()) {
                    Text("前往系统设置授权")
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    "在“通知使用权”中找到 PickMsg 并允许读取通知",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround) {
                StatItem(label = "总计", value = totalCount)
                StatItem(label = "今日", value = todayCount)
                StatItem(label = "应用", value = appCount)
            }
        }
    }
}

@Composable
private fun StatItem(label: String, value: Int) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            value.toString(),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
        )
        Text(label, style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
private fun AppFilterRow(
    appCounts: List<com.pickmsg.app.data.AppCount>,
    totalCount: Int,
    selectedPackage: String?,
    onSelect: (String?) -> Unit,
) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            FilterChip(
                selected = selectedPackage == null,
                onClick = { onSelect(null) },
                label = { Text("全部 $totalCount") },
            )
        }
        items(appCounts, key = { it.packageName }) { app ->
            FilterChip(
                selected = selectedPackage == app.packageName,
                onClick = { onSelect(app.packageName) },
                label = { Text("${app.appLabel} ${app.count}") },
            )
        }
    }
}

@Composable
private fun RecordItem(record: NotificationRecord, onClick: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    record.appLabel,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(formatTime(record.postTime), style = MaterialTheme.typography.labelSmall)
            }
            if (record.title.isNotEmpty()) {
                Text(
                    record.title,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (record.text.isNotEmpty()) {
                Text(
                    record.text,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            val tags = buildList {
                if (record.isPinned) add("置顶")
                if (record.ruleName.isNotEmpty()) add("规则:${record.ruleName}")
                if (record.isOngoing) add("常驻")
                if (record.isSensitive) add("敏感占位")
                if (record.source == NotificationRecord.SOURCE_SNAPSHOT) add("快照")
            }
            if (tags.isNotEmpty()) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    tags.forEach { TagChip(it) }
                }
            }
        }
    }
}

@Composable
private fun TagChip(text: String) {
    Surface(shape = RoundedCornerShape(4.dp), color = MaterialTheme.colorScheme.secondaryContainer) {
        Text(
            text,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
        )
    }
}

@Composable
private fun EmptyState(listenerEnabled: Boolean, hasFilter: Boolean) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(Icons.Default.Notifications, contentDescription = null, tint = MaterialTheme.colorScheme.outline)
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            when {
                hasFilter -> "没有匹配的记录"
                !listenerEnabled -> "授权后，新到的通知会自动出现在这里"
                else -> "暂无记录，点击右下角发送一条测试通知试试"
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.outline,
        )
    }
}

@Composable
private fun LogCard(logs: List<String>) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text("运行日志", style = MaterialTheme.typography.titleSmall)
            if (logs.isEmpty()) {
                Text("暂无日志", style = MaterialTheme.typography.bodySmall)
            } else {
                logs.take(30).forEach { line ->
                    Text(line, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
                }
            }
        }
    }
}

/** API 30+ 跳转本应用专属开关页，低版本跳通知使用权总列表 */
fun openListenerSettings(context: Context) {
    val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS).putExtra(
            Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME,
            ComponentName(context, NotificationCollectorService::class.java).flattenToString(),
        )
    } else {
        Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
    }
    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    try {
        context.startActivity(intent)
    } catch (e: ActivityNotFoundException) {
        context.startActivity(
            Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }
}

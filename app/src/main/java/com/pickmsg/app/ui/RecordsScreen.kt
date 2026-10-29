@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pickmsg.app.data.NotificationRecord
import com.pickmsg.app.service.EventLog
import com.pickmsg.app.service.NotificationCollectorService
import com.pickmsg.app.ui.theme.JadeGlass
import com.pickmsg.app.ui.theme.PickMsgBadge
import com.pickmsg.app.ui.theme.PickMsgButton
import com.pickmsg.app.ui.theme.PickMsgFilterChip
import com.pickmsg.app.ui.theme.PickMsgOpacity
import com.pickmsg.app.ui.theme.PickMsgScaffold
import com.pickmsg.app.ui.theme.PickMsgShape
import com.pickmsg.app.ui.theme.PickMsgSize
import com.pickmsg.app.ui.theme.PickMsgSpace
import com.pickmsg.app.ui.theme.PickMsgTopBar

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
    var showMenu by remember { mutableStateOf(false) }
    var groupMode by remember { mutableStateOf(RecordGroupMode.TIME) }

    val context = LocalContext.current
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            viewModel.sendTestNotification()
        } else {
            EventLog.log("⚠️ 未授予通知权限，无法发送测试通知")
        }
    }

    val current = selectedRecord?.let { selected -> records.firstOrNull { it.id == selected.id } ?: selected }
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

    val rows = remember(records, groupMode) { buildRecordRows(records, groupMode) }

    PickMsgScaffold(
        topBar = {
            PickMsgTopBar(
                title = "PickMsg",
                actions = {
                    IconButton(onClick = viewModel::requestSnapshot) {
                        Icon(Icons.Default.Refresh, contentDescription = "读取通知栏快照")
                    }
                    Box {
                        IconButton(onClick = { showMenu = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "更多操作")
                        }
                        DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                            DropdownMenuItem(
                                text = { Text("发送测试通知") },
                                leadingIcon = { Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null) },
                                onClick = {
                                    showMenu = false
                                    val needsPermission = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                                        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
                                        PackageManager.PERMISSION_GRANTED
                                    if (needsPermission) {
                                        permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                    } else {
                                        viewModel.sendTestNotification()
                                    }
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("常驻测试通知（验证折叠）") },
                                onClick = {
                                    showMenu = false
                                    val needsPermission = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                                        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
                                        PackageManager.PERMISSION_GRANTED
                                    if (needsPermission) {
                                        permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                    } else {
                                        viewModel.sendTestNotification(ongoing = true)
                                    }
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("取消常驻测试通知") },
                                onClick = { showMenu = false; viewModel.cancelOngoingTest() },
                            )
                            DropdownMenuItem(
                                text = { Text(if (showLogs) "收起运行日志" else "运行日志") },
                                leadingIcon = { Icon(Icons.Default.Info, contentDescription = null) },
                                onClick = { showMenu = false; showLogs = !showLogs },
                            )
                            DropdownMenuItem(
                                text = { Text("清空记录", color = MaterialTheme.colorScheme.error) },
                                leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                                onClick = { showMenu = false; showClearDialog = true },
                            )
                        }
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding),
            contentPadding = PickMsgSpace.pagePadding,
            verticalArrangement = Arrangement.spacedBy(PickMsgSpace.listGap),
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
                    placeholder = { Text("搜索通知") },
                    shape = PickMsgShape.pill,
                    colors = OutlinedTextFieldDefaults.colors(
                        unfocusedContainerColor = MaterialTheme.colorScheme.surface.copy(alpha = PickMsgOpacity.glass),
                        focusedContainerColor = MaterialTheme.colorScheme.surface.copy(alpha = PickMsgOpacity.field),
                        unfocusedBorderColor = MaterialTheme.colorScheme.surface,
                    ),
                    trailingIcon = {
                        if (query.isNotEmpty()) {
                            IconButton(onClick = { viewModel.setQuery("") }) {
                                Icon(Icons.Default.Close, contentDescription = "清除搜索")
                            }
                        }
                    },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    singleLine = true,
                )
            }
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(PickMsgSpace.sm)) {
                    items(RecordGroupMode.entries) { mode ->
                        PickMsgFilterChip(
                            prominent = true,
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
                    is RecordRow.Header -> item(key = "header_${row.key}") {
                        Text(
                            row.text,
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(top = PickMsgSpace.sm),
                        )
                    }
                    is RecordRow.Item -> item(key = "record_${row.record.id}") {
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
    val statusColor = when {
        !listenerEnabled -> MaterialTheme.colorScheme.error
        !connected || paused -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.primary
    }
    val headline = when {
        !listenerEnabled -> "需要通知授权"
        !connected -> "等待服务连接"
        paused -> "采集已暂停"
        else -> "正在采集"
    }
    JadeGlass(modifier = Modifier.fillMaxWidth(), emphasized = true) {
        Column(modifier = Modifier.padding(PickMsgSpace.card)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(shape = PickMsgShape.pill, color = statusColor.copy(alpha = PickMsgOpacity.tint)) {
                    Icon(Icons.Default.Notifications, contentDescription = null, tint = statusColor, modifier = Modifier.padding(PickMsgSpace.sm).size(PickMsgSize.iconSmall))
                }
                Spacer(modifier = Modifier.width(PickMsgSpace.md))
                Column(modifier = Modifier.weight(1f)) {
                    Text(headline, style = MaterialTheme.typography.titleMedium)
                    if (listenerEnabled && !connected) {
                        Text("请检查系统通知使用权", style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                if (listenerEnabled) {
                    Switch(checked = !paused, onCheckedChange = { onTogglePaused() },
                        modifier = Modifier.semantics { contentDescription = "通知采集" })
                }
            }
            if (!listenerEnabled) {
                Spacer(modifier = Modifier.height(PickMsgSpace.md))
                PickMsgButton(onClick = onOpenSettings, modifier = Modifier.fillMaxWidth()) {
                    Text("前往系统设置授权")
                }
                Spacer(modifier = Modifier.height(PickMsgSpace.xs))
                Text(
                    "在“通知使用权”中找到 PickMsg 并允许读取通知",
                    style = MaterialTheme.typography.bodySmall,
                )
            } else if (!connected) {
                TextButton(onClick = onOpenSettings) { Text("检查通知授权") }
            }
            Spacer(modifier = Modifier.height(PickMsgSpace.lg))
            FlowRow(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround, verticalArrangement = Arrangement.spacedBy(PickMsgSpace.sm)) {
                StatItem(label = "今日", value = todayCount)
                StatItem(label = "总计", value = totalCount)
                StatItem(label = "应用", value = appCount)
            }
        }
    }
}

@Composable
private fun StatItem(label: String, value: Int) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            value.toString(),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun AppFilterRow(
    appCounts: List<com.pickmsg.app.data.AppCount>,
    totalCount: Int,
    selectedPackage: String?,
    onSelect: (String?) -> Unit,
) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(PickMsgSpace.sm)) {
        item {
            PickMsgFilterChip(
                selected = selectedPackage == null,
                onClick = { onSelect(null) },
                label = { Text("全部 $totalCount") },
            )
        }
        items(appCounts, key = { it.packageName }) { app ->
            PickMsgFilterChip(
                selected = selectedPackage == app.packageName,
                onClick = { onSelect(app.packageName) },
                label = { Text("${app.appLabel} ${app.count}") },
            )
        }
    }
}

@Composable
private fun RecordItem(record: NotificationRecord, onClick: () -> Unit) {
    JadeGlass(modifier = Modifier.fillMaxWidth(), onClick = onClick) {
        Row(modifier = Modifier.padding(PickMsgSpace.card), horizontalArrangement = Arrangement.spacedBy(PickMsgSpace.md)) {
            Surface(shape = PickMsgShape.icon, color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = PickMsgOpacity.glass)) {
                Box(Modifier.size(PickMsgSize.avatar), contentAlignment = Alignment.Center) {
                    Text(record.appLabel.take(1), color = MaterialTheme.colorScheme.onPrimaryContainer,
                        style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                }
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(PickMsgSpace.xs),
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
                    Text(formatTime(record.postTime), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                val tags = buildList {
                    if (record.isPinned) add("置顶")
                    if (record.updateCount > 0) add("更新×${record.updateCount}")
                    if (record.ruleName.isNotEmpty()) add("规则:${record.ruleName}")
                    if (record.isOngoing) add("常驻")
                    if (record.isSensitive) add("敏感占位")
                    if (record.source == NotificationRecord.SOURCE_SNAPSHOT) add("快照")
                }
                if (tags.isNotEmpty()) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(PickMsgSpace.sm), verticalArrangement = Arrangement.spacedBy(PickMsgSpace.xs)) {
                        tags.forEach { PickMsgBadge(it) }
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyState(listenerEnabled: Boolean, hasFilter: Boolean) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = PickMsgSpace.empty),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(Icons.Default.Notifications, contentDescription = null, tint = MaterialTheme.colorScheme.outline)
        Spacer(modifier = Modifier.height(PickMsgSpace.sm))
        Text(
            when {
                hasFilter -> "没有匹配的记录"
                !listenerEnabled -> "授权后，新到的通知会自动出现在这里"
                else -> "暂无通知，可在右上角菜单发送测试通知"
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun LogCard(logs: List<String>) {
    JadeGlass(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(PickMsgSpace.card), verticalArrangement = Arrangement.spacedBy(PickMsgSpace.micro)) {
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

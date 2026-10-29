@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.pickmsg.app.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pickmsg.app.data.SmtpConfig
import com.pickmsg.app.data.WebDavConfig
import com.pickmsg.app.ui.theme.PickMsgButton
import com.pickmsg.app.ui.theme.PickMsgCard
import com.pickmsg.app.ui.theme.PickMsgOutlinedButton
import com.pickmsg.app.ui.theme.PickMsgScaffold
import com.pickmsg.app.ui.theme.PickMsgShape
import com.pickmsg.app.ui.theme.PickMsgSpace
import com.pickmsg.app.ui.theme.PickMsgTopBar

@Composable
fun MineScreen(viewModel: MineViewModel) {
    val connected by viewModel.connected.collectAsStateWithLifecycle()
    val paused by viewModel.paused.collectAsStateWithLifecycle()
    val listenerEnabled by viewModel.listenerEnabled.collectAsStateWithLifecycle()
    val foregroundEnabled by viewModel.foregroundEnabled.collectAsStateWithLifecycle()
    val keepDays by viewModel.keepDays.collectAsStateWithLifecycle()
    val smtp by viewModel.smtp.collectAsStateWithLifecycle()
    val webdav by viewModel.webdav.collectAsStateWithLifecycle()
    val autoBackupEnabled by viewModel.autoBackupEnabled.collectAsStateWithLifecycle()
    val autoBackupHours by viewModel.autoBackupHours.collectAsStateWithLifecycle()
    val totalCount by viewModel.totalCount.collectAsStateWithLifecycle()
    val todayCount by viewModel.todayCount.collectAsStateWithLifecycle()
    val dailyCounts by viewModel.dailyCounts.collectAsStateWithLifecycle()
    val ruleCounts by viewModel.ruleCounts.collectAsStateWithLifecycle()
    val appCounts by viewModel.appCounts.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()

    val context = LocalContext.current
    var showOngoingTool by remember { mutableStateOf(false) }

    val csvLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        uri?.let(viewModel::exportCsvTo)
    }
    val backupExportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        uri?.let(viewModel::exportBackupTo)
    }
    val backupImportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(viewModel::importBackupFrom)
    }

    LaunchedEffect(Unit) {
        viewModel.refreshListenerEnabled()
    }

    if (showOngoingTool) {
        OngoingScreen(onBack = { showOngoingTool = false })
        return
    }

    PickMsgScaffold(
        topBar = { PickMsgTopBar(title = "我的") },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(PickMsgSpace.pagePadding),
            verticalArrangement = Arrangement.spacedBy(PickMsgSpace.fieldGap),
        ) {
            message?.let {
                PickMsgCard(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        it,
                        modifier = Modifier
                            .padding(PickMsgSpace.card)
                            .clickable { viewModel.clearMessage() },
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }

            SectionCard("服务") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "通知使用权：${if (listenerEnabled) "已授权" else "未授权"}\n" +
                            "服务：${if (connected) "已连接" else "未连接"}" +
                            if (paused) "（已暂停）" else "",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.weight(1f),
                    )
                    Switch(checked = !paused, onCheckedChange = { viewModel.togglePaused() })
                }
                if (!listenerEnabled) {
                    PickMsgButton(onClick = { openListenerSettings(context) }, modifier = Modifier.fillMaxWidth()) {
                        Text("前往系统设置授权")
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = foregroundEnabled,
                        onCheckedChange = { viewModel.setForegroundEnabled(it) },
                    )
                    Text(
                        "保持后台运行",
                        modifier = Modifier.weight(1f).clickable { viewModel.setForegroundEnabled(!foregroundEnabled) },
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                if (!viewModel.batteryOptimizationIgnored()) {
                    PickMsgOutlinedButton(onClick = viewModel::requestIgnoreBatteryOptimizations) {
                        Text("允许后台持续运行")
                    }
                }
            }

            SectionCard("工具") {
                PickMsgOutlinedButton(onClick = { showOngoingTool = true }, modifier = Modifier.fillMaxWidth()) {
                    Text("常驻通知管理（隐藏 / 恢复）")
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(PickMsgSpace.sm), verticalArrangement = Arrangement.spacedBy(PickMsgSpace.sm)) {
                    OutlinedTextField(
                        shape = PickMsgShape.control,
                        value = keepDays.toString(),
                        onValueChange = { value -> value.toIntOrNull()?.let { viewModel.setKeepDays(it.coerceIn(1, 365)) } },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("保留天数") },
                        singleLine = true,
                    )
                    PickMsgOutlinedButton(onClick = viewModel::cleanupNow) { Text("立即清理") }
                    PickMsgOutlinedButton(onClick = { csvLauncher.launch("pickmsg-records.csv") }) { Text("导出 CSV") }
                }
            }

            SectionCard("备份与恢复", collapsible = true) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(PickMsgSpace.sm), verticalArrangement = Arrangement.spacedBy(PickMsgSpace.sm)) {
                    PickMsgOutlinedButton(onClick = { backupExportLauncher.launch("pickmsg-backup.json") }) { Text("导出到文件") }
                    PickMsgOutlinedButton(onClick = {
                        backupImportLauncher.launch(arrayOf("application/json", "text/*", "*/*"))
                    }) { Text("从文件导入") }
                }
                HorizontalDivider()
                WebDavEditor(viewModel, webdav)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(PickMsgSpace.sm), verticalArrangement = Arrangement.spacedBy(PickMsgSpace.sm)) {
                    PickMsgOutlinedButton(onClick = viewModel::testWebDav) { Text("测试连接") }
                    PickMsgOutlinedButton(onClick = viewModel::uploadBackup) { Text("手动上传") }
                    PickMsgOutlinedButton(onClick = viewModel::downloadBackupImport) { Text("下载并导入") }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = autoBackupEnabled,
                        onCheckedChange = { viewModel.setAutoBackup(it, autoBackupHours) },
                    )
                    Text("自动备份", modifier = Modifier.weight(1f))
                }
                OutlinedTextField(
                    shape = PickMsgShape.control,
                    value = autoBackupHours.toString(),
                    onValueChange = { value ->
                        value.toIntOrNull()?.let { viewModel.setAutoBackup(autoBackupEnabled, it.coerceIn(1, 720)) }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("备份间隔（小时）") },
                    singleLine = true,
                )
            }

            SectionCard("邮件转发账号", collapsible = true) {
                SmtpEditor(viewModel, smtp)
            }

            SectionCard("统计") {
                FlowRow(horizontalArrangement = Arrangement.SpaceEvenly, verticalArrangement = Arrangement.spacedBy(PickMsgSpace.sm), modifier = Modifier.fillMaxWidth()) {
                    StatBox("总计", totalCount)
                    StatBox("今日", todayCount)
                    StatBox("应用", appCounts.size)
                }
                HorizontalDivider()
                Text("近 7 天", style = MaterialTheme.typography.labelMedium)
                if (dailyCounts.isEmpty()) {
                    Text("暂无数据", style = MaterialTheme.typography.bodySmall)
                } else {
                    val maxCount = dailyCounts.maxOf { it.count }.coerceAtLeast(1)
                    dailyCounts.sortedBy { it.day }.forEach { day ->
                        val date = java.text.SimpleDateFormat("MM-dd", java.util.Locale.getDefault())
                            .format(java.util.Date(day.day * 86_400_000L))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                date,
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.widthIn(min = 48.dp),
                            )
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(14.dp),
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth(day.count.toFloat() / maxCount)
                                        .height(14.dp),
                                ) {
                                    Surface(
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.fillMaxWidth().height(14.dp),
                                    ) {}
                                }
                            }
                            Text(
                                " ${day.count}",
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.widthIn(min = 40.dp),
                            )
                        }
                    }
                }
                if (ruleCounts.isNotEmpty()) {
                    HorizontalDivider()
                    Text("规则命中 TOP", style = MaterialTheme.typography.labelMedium)
                    ruleCounts.forEach {
                        Text("${it.label} · ${it.count}", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }

            SectionCard("关于") {
                Text(
                    "PickMsg · 通知采集与规则过滤\n数据保存在本机，仅按你配置的规则转发。",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

@Composable
private fun SectionCard(title: String, collapsible: Boolean = false, content: @Composable () -> Unit) {
    var expanded by rememberSaveable(title) { mutableStateOf(!collapsible) }
    PickMsgCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(PickMsgSpace.card), verticalArrangement = Arrangement.spacedBy(PickMsgSpace.sm)) {
            if (collapsible) {
                TextButton(onClick = { expanded = !expanded }, modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(0.dp)) {
                    Text(title, modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                    Icon(if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = if (expanded) "收起$title" else "展开$title")
                }
            } else {
                Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            }
            if (expanded) content()
        }
    }
}

@Composable
private fun StatBox(label: String, value: Int) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value.toString(), style = MaterialTheme.typography.headlineSmall)
        Text(label, style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
private fun WebDavEditor(viewModel: MineViewModel, config: WebDavConfig) {
    var url by remember(config.url) { mutableStateOf(config.url) }
    var user by remember(config.user) { mutableStateOf(config.user) }
    var password by remember(config.password) { mutableStateOf(config.password) }
    OutlinedTextField(
        shape = PickMsgShape.control,
        value = url,
        onValueChange = { url = it },
        modifier = Modifier.fillMaxWidth(),
        label = { Text("WebDAV 地址") },
        placeholder = { Text("https://dav.example.com/dav/pickmsg/") },
        singleLine = true,
    )
    OutlinedTextField(
        shape = PickMsgShape.control,
        value = user,
        onValueChange = { user = it },
        modifier = Modifier.fillMaxWidth(),
        label = { Text("账号") },
        singleLine = true,
    )
    OutlinedTextField(
        shape = PickMsgShape.control,
        value = password,
        onValueChange = { password = it },
        modifier = Modifier.fillMaxWidth(),
        label = { Text("密码") },
        singleLine = true,
    )
    PickMsgOutlinedButton(onClick = { viewModel.setWebDav(WebDavConfig(url.trim(), user.trim(), password)) }) {
        Text("保存 WebDAV 配置")
    }
}

@Composable
private fun SmtpEditor(viewModel: MineViewModel, config: SmtpConfig) {
    var host by remember(config.host) { mutableStateOf(config.host) }
    var port by remember(config.port) { mutableStateOf(config.port.toString()) }
    var user by remember(config.user) { mutableStateOf(config.user) }
    var password by remember(config.password) { mutableStateOf(config.password) }
    var ssl by remember(config.ssl) { mutableStateOf(config.ssl) }
    OutlinedTextField(
        shape = PickMsgShape.control,
        value = host,
        onValueChange = { host = it },
        modifier = Modifier.fillMaxWidth(),
        label = { Text("SMTP 服务器") },
        placeholder = { Text("smtp.qq.com") },
        singleLine = true,
    )
    OutlinedTextField(
        shape = PickMsgShape.control,
        value = port,
        onValueChange = { port = it.filter { c -> c.isDigit() }.take(5) },
        modifier = Modifier.fillMaxWidth(),
        label = { Text("端口") },
        singleLine = true,
    )
    OutlinedTextField(
        shape = PickMsgShape.control,
        value = user,
        onValueChange = { user = it },
        modifier = Modifier.fillMaxWidth(),
        label = { Text("账号（发件邮箱）") },
        singleLine = true,
    )
    OutlinedTextField(
        shape = PickMsgShape.control,
        value = password,
        onValueChange = { password = it },
        modifier = Modifier.fillMaxWidth(),
        label = { Text("授权码 / 密码") },
        singleLine = true,
    )
    Row(verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked = ssl, onCheckedChange = { ssl = it })
        Text("SSL（465 端口勾选；587 端口取消勾选用 STARTTLS）", style = MaterialTheme.typography.bodySmall)
    }
    PickMsgOutlinedButton(onClick = {
        viewModel.setSmtp(
            SmtpConfig(
                host = host.trim(),
                port = port.toIntOrNull() ?: 465,
                user = user.trim(),
                password = password,
                ssl = ssl,
            ),
        )
    }) {
        Text("保存 SMTP 配置")
    }
}

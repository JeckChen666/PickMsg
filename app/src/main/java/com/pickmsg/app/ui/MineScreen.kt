@file:OptIn(ExperimentalMaterial3Api::class)

package com.pickmsg.app.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pickmsg.app.data.SmtpConfig
import com.pickmsg.app.data.WebDavConfig
import com.pickmsg.app.service.NotificationCollectorService

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

    Scaffold(
        topBar = { TopAppBar(title = { Text("我的") }) },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            message?.let {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        it,
                        modifier = Modifier
                            .padding(12.dp)
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
                    Button(onClick = { openListenerSettings(context) }, modifier = Modifier.fillMaxWidth()) {
                        Text("前往系统设置授权")
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = foregroundEnabled,
                        onCheckedChange = { viewModel.setForegroundEnabled(it) },
                    )
                    Text(
                        "前台服务常驻（Android 14+ 为 specialUse 类型，通知栏带暂停按钮）",
                        modifier = Modifier.weight(1f).clickable { viewModel.setForegroundEnabled(!foregroundEnabled) },
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                if (!viewModel.batteryOptimizationIgnored()) {
                    OutlinedButton(onClick = viewModel::requestIgnoreBatteryOptimizations) {
                        Text("申请电池优化豁免（降低被杀概率）")
                    }
                }
            }

            SectionCard("工具") {
                OutlinedButton(onClick = { showOngoingTool = true }, modifier = Modifier.fillMaxWidth()) {
                    Text("常驻通知管理（隐藏 / 恢复）")
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = keepDays.toString(),
                        onValueChange = { value -> value.toIntOrNull()?.let { viewModel.setKeepDays(it.coerceIn(1, 365)) } },
                        modifier = Modifier.width(120.dp),
                        label = { Text("保留天数") },
                        singleLine = true,
                    )
                    OutlinedButton(onClick = viewModel::cleanupNow) { Text("立即清理") }
                    OutlinedButton(onClick = { csvLauncher.launch("pickmsg-records.csv") }) { Text("导出 CSV") }
                }
            }

            SectionCard("备份") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { backupExportLauncher.launch("pickmsg-backup.json") }) { Text("导出到文件") }
                    OutlinedButton(onClick = {
                        backupImportLauncher.launch(arrayOf("application/json", "text/*", "*/*"))
                    }) { Text("从文件导入") }
                }
                HorizontalDivider()
                WebDavEditor(viewModel, webdav)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = viewModel::testWebDav) { Text("测试连接") }
                    OutlinedButton(onClick = viewModel::uploadBackup) { Text("手动上传") }
                    OutlinedButton(onClick = viewModel::downloadBackupImport) { Text("下载并导入") }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = autoBackupEnabled,
                        onCheckedChange = { viewModel.setAutoBackup(it, autoBackupHours) },
                    )
                    Text("自动备份到 WebDAV", modifier = Modifier.weight(1f))
                    OutlinedTextField(
                        value = autoBackupHours.toString(),
                        onValueChange = { value ->
                            value.toIntOrNull()?.let { viewModel.setAutoBackup(autoBackupEnabled, it.coerceIn(1, 720)) }
                        },
                        modifier = Modifier.width(90.dp),
                        label = { Text("小时") },
                        singleLine = true,
                    )
                }
            }

            SectionCard("邮件转发 SMTP 账号") {
                SmtpEditor(viewModel, smtp)
            }

            SectionCard("统计") {
                Row(horizontalArrangement = Arrangement.SpaceEvenly, modifier = Modifier.fillMaxWidth()) {
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
                                modifier = Modifier.width(48.dp),
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
                                modifier = Modifier.width(40.dp),
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
                    "PickMsg 0.2.0 · 通知采集与规则过滤\n所有数据仅保存在本机；外发仅发生在你显式配置的转发动作",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

@Composable
private fun SectionCard(title: String, content: @Composable () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
            content()
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
        value = url,
        onValueChange = { url = it },
        modifier = Modifier.fillMaxWidth(),
        label = { Text("WebDAV 地址") },
        placeholder = { Text("https://dav.example.com/dav/pickmsg/") },
        singleLine = true,
    )
    OutlinedTextField(
        value = user,
        onValueChange = { user = it },
        modifier = Modifier.fillMaxWidth(),
        label = { Text("账号") },
        singleLine = true,
    )
    OutlinedTextField(
        value = password,
        onValueChange = { password = it },
        modifier = Modifier.fillMaxWidth(),
        label = { Text("密码") },
        singleLine = true,
    )
    OutlinedButton(onClick = { viewModel.setWebDav(WebDavConfig(url.trim(), user.trim(), password)) }) {
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
        value = host,
        onValueChange = { host = it },
        modifier = Modifier.fillMaxWidth(),
        label = { Text("SMTP 服务器") },
        placeholder = { Text("smtp.qq.com") },
        singleLine = true,
    )
    OutlinedTextField(
        value = port,
        onValueChange = { port = it.filter { c -> c.isDigit() }.take(5) },
        modifier = Modifier.fillMaxWidth(),
        label = { Text("端口") },
        singleLine = true,
    )
    OutlinedTextField(
        value = user,
        onValueChange = { user = it },
        modifier = Modifier.fillMaxWidth(),
        label = { Text("账号（发件邮箱）") },
        singleLine = true,
    )
    OutlinedTextField(
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
    OutlinedButton(onClick = {
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

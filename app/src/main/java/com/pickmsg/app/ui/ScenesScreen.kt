@file:OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)

package com.pickmsg.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pickmsg.app.data.NetworkKind
import com.pickmsg.app.data.Scene
import com.pickmsg.app.data.SceneCondition
import com.pickmsg.app.data.SceneConditionType

@Composable
fun ScenesScreen(viewModel: ScenesViewModel) {
    val scenes by viewModel.scenes.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf<Scene?>(null) }
    var creating by remember { mutableStateOf(false) }

    editing?.let { scene ->
        SceneEditScreen(existing = scene, viewModel = viewModel, onClose = { editing = null })
        return
    }
    if (creating) {
        SceneEditScreen(existing = null, viewModel = viewModel, onClose = { creating = false })
        return
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("场景") }) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { creating = true },
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("新建场景") },
            )
        },
    ) { padding ->
        if (scenes.isEmpty()) {
            Column(
                modifier = Modifier.fillMaxSize().padding(padding).padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Icon(Icons.Default.Tune, contentDescription = null, tint = MaterialTheme.colorScheme.outline)
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "还没有场景\n场景 = 网络 / 时间段 / 电量 / 星期 / 屏幕状态的组合，\n规则可绑定场景来限定生效时段",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.outline,
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.padding(padding),
                contentPadding = PaddingValues(start = 16.dp, top = 8.dp, end = 16.dp, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(scenes, key = { it.id }) { scene ->
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.padding(start = 16.dp, end = 8.dp, top = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(scene.name, style = MaterialTheme.typography.titleSmall)
                                Text(
                                    scene.conditions.joinToString(" 且 ") { conditionSummary(it) }.ifBlank { "无条件" },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.outline,
                                    maxLines = 3,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            Switch(checked = scene.enable, onCheckedChange = { viewModel.toggleEnable(scene) })
                        }
                        Row(modifier = Modifier.padding(start = 16.dp, bottom = 10.dp)) {
                            TextButton(
                                onClick = { editing = scene },
                                contentPadding = PaddingValues(0.dp),
                            ) { Text("编辑", style = MaterialTheme.typography.labelMedium) }
                        }
                    }
                }
            }
        }
    }
}

fun conditionSummary(c: SceneCondition): String = when (c.type) {
    SceneConditionType.NETWORK ->
        "网络:${c.networkKinds.joinToString("/") { kind ->
            when (kind) {
                NetworkKind.OFFLINE -> "离线"
                NetworkKind.MOBILE -> "流量"
                NetworkKind.WIFI -> "Wi-Fi"
                NetworkKind.BLUETOOTH -> "蓝牙"
            }
        }}" + if (c.ssids.isNotEmpty()) "(SSID:${c.ssids.joinToString("/")})" else ""
    SceneConditionType.TIME_RANGE -> "时间:${c.startTime}-${c.endTime}"
    SceneConditionType.BATTERY -> "电量:${c.minBattery}-${c.maxBattery}%"
    SceneConditionType.WEEKDAY -> "星期:" + weekdayBits(c.weekdayMask)
    SceneConditionType.SCREEN ->
        (if (c.screenOn) "亮屏" else "灭屏") + "/" + (if (c.landscape) "横屏" else "竖屏")
}

fun weekdayBits(mask: Int): String {
    val names = listOf("一", "二", "三", "四", "五", "六", "日")
    if (mask == 127) return "每天"
    return names.filterIndexed { i, _ -> (mask shr i) and 1 == 1 }.joinToString("") { it } .ifBlank { "无" }
}

@Composable
private fun SceneEditScreen(existing: Scene?, viewModel: ScenesViewModel, onClose: () -> Unit) {
    var name by remember { mutableStateOf(existing?.name ?: "") }
    var nameError by remember { mutableStateOf(false) }
    var enable by remember { mutableStateOf(existing?.enable ?: true) }
    var conditions by remember { mutableStateOf(existing?.conditions ?: emptyList()) }
    var showAddDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (existing == null) "新建场景" else "编辑场景") },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "放弃")
                    }
                },
                actions = {
                    if (existing != null) {
                        IconButton(onClick = {
                            viewModel.delete(existing)
                            onClose()
                        }) { Icon(Icons.Default.Delete, contentDescription = "删除场景") }
                    }
                    IconButton(onClick = {
                        if (name.isBlank()) {
                            nameError = true
                        } else {
                            viewModel.save(
                                Scene(
                                    id = existing?.id ?: 0,
                                    name = name.trim(),
                                    enable = enable,
                                    conditions = conditions,
                                ),
                            )
                            onClose()
                        }
                    }) { Icon(Icons.Default.Check, contentDescription = "保存") }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it; nameError = false },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("场景名称") },
                isError = nameError,
                supportingText = if (nameError) {
                    { Text("名称不能为空") }
                } else {
                    null
                },
                singleLine = true,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = enable, onCheckedChange = { enable = it })
                Text("启用该场景（未启用的场景对规则求值时跳过）", modifier = Modifier.clickable { enable = !enable })
            }

            SectionTitle("条件（同场景内为“且”关系）")
            if (conditions.isEmpty()) {
                Text("无", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
            } else {
                conditions.forEachIndexed { index, condition ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            conditionSummary(condition),
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.weight(1f),
                        )
                        IconButton(onClick = {
                            conditions = conditions.filterIndexed { i, _ -> i != index }
                        }) {
                            Icon(Icons.Default.Close, contentDescription = "删除条件", Modifier.width(20.dp))
                        }
                    }
                }
            }
            OutlinedButton(onClick = { showAddDialog = true }) {
                Icon(Icons.Default.Add, contentDescription = null, Modifier.width(18.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("添加条件")
            }
        }
    }

    if (showAddDialog) {
        ConditionDialog(
            onConfirm = { condition ->
                conditions = conditions + condition
                showAddDialog = false
            },
            onDismiss = { showAddDialog = false },
        )
    }
}

@Composable
private fun ConditionDialog(onConfirm: (SceneCondition) -> Unit, onDismiss: () -> Unit) {
    var type by remember { mutableStateOf(SceneConditionType.NETWORK) }
    var kinds by remember { mutableStateOf(setOf<NetworkKind>()) }
    var ssids by remember { mutableStateOf("") }
    var startTime by remember { mutableStateOf("09:00") }
    var endTime by remember { mutableStateOf("18:00") }
    var minBattery by remember { mutableStateOf("20") }
    var maxBattery by remember { mutableStateOf("100") }
    var weekdays by remember { mutableStateOf(127) }
    var screenOn by remember { mutableStateOf(true) }
    var landscape by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("添加条件") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SceneConditionType.entries.forEach { t ->
                        FilterChip(
                            selected = type == t,
                            onClick = { type = t },
                            label = {
                                Text(
                                    when (t) {
                                        SceneConditionType.NETWORK -> "网络"
                                        SceneConditionType.TIME_RANGE -> "时间段"
                                        SceneConditionType.BATTERY -> "电量"
                                        SceneConditionType.WEEKDAY -> "星期"
                                        SceneConditionType.SCREEN -> "屏幕"
                                    },
                                )
                            },
                        )
                    }
                }
                when (type) {
                    SceneConditionType.NETWORK -> {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            NetworkKind.entries.forEach { kind ->
                                FilterChip(
                                    selected = kind in kinds,
                                    onClick = {
                                        kinds = if (kind in kinds) kinds - kind else kinds + kind
                                    },
                                    label = {
                                        Text(
                                            when (kind) {
                                                NetworkKind.OFFLINE -> "离线"
                                                NetworkKind.MOBILE -> "流量"
                                                NetworkKind.WIFI -> "Wi-Fi"
                                                NetworkKind.BLUETOOTH -> "蓝牙"
                                            },
                                        )
                                    },
                                )
                            }
                        }
                        OutlinedTextField(
                            value = ssids,
                            onValueChange = { ssids = it },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("指定 SSID（可选，逗号分隔）") },
                            singleLine = true,
                        )
                    }
                    SceneConditionType.TIME_RANGE -> {
                        OutlinedTextField(
                            value = startTime,
                            onValueChange = { startTime = it },
                            label = { Text("开始 HH:mm") },
                            singleLine = true,
                        )
                        OutlinedTextField(
                            value = endTime,
                            onValueChange = { endTime = it },
                            label = { Text("结束 HH:mm") },
                            singleLine = true,
                        )
                    }
                    SceneConditionType.BATTERY -> {
                        OutlinedTextField(
                            value = minBattery,
                            onValueChange = { minBattery = it.filter { c -> c.isDigit() }.take(3) },
                            label = { Text("最低电量 %") },
                            singleLine = true,
                        )
                        OutlinedTextField(
                            value = maxBattery,
                            onValueChange = { maxBattery = it.filter { c -> c.isDigit() }.take(3) },
                            label = { Text("最高电量 %") },
                            singleLine = true,
                        )
                    }
                    SceneConditionType.WEEKDAY -> {
                        WeekdayChips(weekdays) { weekdays = it }
                    }
                    SceneConditionType.SCREEN -> {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(selected = screenOn, onClick = { screenOn = true }, label = { Text("亮屏") })
                            FilterChip(selected = !screenOn, onClick = { screenOn = false }, label = { Text("灭屏") })
                            FilterChip(selected = !landscape, onClick = { landscape = false }, label = { Text("竖屏") })
                            FilterChip(selected = landscape, onClick = { landscape = true }, label = { Text("横屏") })
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val condition = when (type) {
                    SceneConditionType.NETWORK -> SceneCondition(
                        type = type,
                        networkKinds = kinds.toList(),
                        ssids = ssids.split(',', '，', '、').map { it.trim() }.filter { it.isNotEmpty() },
                    )
                    SceneConditionType.TIME_RANGE -> SceneCondition(type = type, startTime = startTime, endTime = endTime)
                    SceneConditionType.BATTERY -> SceneCondition(
                        type = type,
                        minBattery = minBattery.toIntOrNull() ?: 0,
                        maxBattery = maxBattery.toIntOrNull() ?: 100,
                    )
                    SceneConditionType.WEEKDAY -> SceneCondition(type = type, weekdayMask = weekdays)
                    SceneConditionType.SCREEN -> SceneCondition(type = type, screenOn = screenOn, landscape = landscape)
                }
                onConfirm(condition)
            }) { Text("添加") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

@Composable
fun WeekdayChips(mask: Int, onChange: (Int) -> Unit) {
    val names = listOf("一", "二", "三", "四", "五", "六", "日")
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        names.forEachIndexed { index, label ->
            val bit = 1 shl index
            FilterChip(
                selected = (mask and bit) != 0,
                onClick = { onChange(mask xor bit) },
                label = { Text("周$label") },
            )
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 8.dp),
    )
}

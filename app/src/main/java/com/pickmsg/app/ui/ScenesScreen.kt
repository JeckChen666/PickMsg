@file:OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)

package com.pickmsg.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pickmsg.app.data.NetworkKind
import com.pickmsg.app.data.Scene
import com.pickmsg.app.data.SceneCondition
import com.pickmsg.app.data.SceneConditionType
import com.pickmsg.app.ui.theme.PickMsgDeleteAction
import com.pickmsg.app.ui.theme.PickMsgFilterChip
import com.pickmsg.app.ui.theme.PickMsgFloatingActionButton
import com.pickmsg.app.ui.theme.PickMsgManagementCard
import com.pickmsg.app.ui.theme.PickMsgOutlinedButton
import com.pickmsg.app.ui.theme.PickMsgScaffold
import com.pickmsg.app.ui.theme.PickMsgShape
import com.pickmsg.app.ui.theme.PickMsgSpace
import com.pickmsg.app.ui.theme.PickMsgTopBar

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

    PickMsgScaffold(
        topBar = { PickMsgTopBar(title = "场景") },
        floatingActionButton = {
            PickMsgFloatingActionButton(
                onClick = { creating = true },
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                label = "新建场景",
            )
        },
    ) { padding ->
        if (scenes.isEmpty()) {
            Column(
                modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(PickMsgSpace.xxl),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Icon(Icons.Default.Tune, contentDescription = null, tint = MaterialTheme.colorScheme.outline)
                Spacer(modifier = Modifier.height(PickMsgSpace.sm))
                Text(
                    "还没有场景\n按时间、网络等条件控制规则何时生效",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.padding(padding),
                contentPadding = PickMsgSpace.listWithFabPadding,
                verticalArrangement = Arrangement.spacedBy(PickMsgSpace.listGap),
            ) {
                items(scenes, key = { it.id }) { scene ->
                    PickMsgManagementCard(
                            title = scene.name,
                            summary = scene.conditions.joinToString(" 且 ") { conditionSummary(it) }.ifBlank { "不限条件" },
                            enabled = scene.enable, onToggle = { viewModel.toggleEnable(scene) },
                            onEdit = { editing = scene },
                        )
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
    BackHandler(onBack = onClose)
    var name by remember { mutableStateOf(existing?.name ?: "") }
    var nameError by remember { mutableStateOf(false) }
    var enable by remember { mutableStateOf(existing?.enable ?: true) }
    var conditions by remember { mutableStateOf(existing?.conditions ?: emptyList()) }
    var showAddDialog by remember { mutableStateOf(false) }

    PickMsgScaffold(
        secondaryPage = true,
        topBar = {
            PickMsgTopBar(
                title = if (existing == null) "新建场景" else "编辑场景",
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "放弃")
                    }
                },
                actions = {
                    if (existing != null) {
                        PickMsgDeleteAction(
                            label = "删除场景",
                            message = "删除后无法恢复，请确认不再需要此场景。",
                            onConfirm = { viewModel.delete(existing); onClose() },
                        )
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
                .padding(PickMsgSpace.pagePadding),
            verticalArrangement = Arrangement.spacedBy(PickMsgSpace.fieldGap),
        ) {
            OutlinedTextField(
                shape = PickMsgShape.control,
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
                Text("启用场景", modifier = Modifier.clickable { enable = !enable })
            }

            SectionTitle("同时满足以下条件")
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
            PickMsgOutlinedButton(onClick = { showAddDialog = true }) {
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
            Column(modifier = Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(PickMsgSpace.sm)) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(PickMsgSpace.sm), verticalArrangement = Arrangement.spacedBy(PickMsgSpace.sm)) {
                    SceneConditionType.entries.forEach { t ->
                        PickMsgFilterChip(
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
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(PickMsgSpace.sm), verticalArrangement = Arrangement.spacedBy(PickMsgSpace.sm)) {
                            NetworkKind.entries.forEach { kind ->
                                PickMsgFilterChip(
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
                            shape = PickMsgShape.control,
                            value = ssids,
                            onValueChange = { ssids = it },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("Wi-Fi 名称（可选）") },
                            singleLine = true,
                        )
                    }
                    SceneConditionType.TIME_RANGE -> {
                        OutlinedTextField(
                            shape = PickMsgShape.control,
                            value = startTime,
                            onValueChange = { startTime = it },
                            label = { Text("开始 HH:mm") },
                            singleLine = true,
                        )
                        OutlinedTextField(
                            shape = PickMsgShape.control,
                            value = endTime,
                            onValueChange = { endTime = it },
                            label = { Text("结束 HH:mm") },
                            singleLine = true,
                        )
                    }
                    SceneConditionType.BATTERY -> {
                        OutlinedTextField(
                            shape = PickMsgShape.control,
                            value = minBattery,
                            onValueChange = { minBattery = it.filter { c -> c.isDigit() }.take(3) },
                            label = { Text("最低电量 %") },
                            singleLine = true,
                        )
                        OutlinedTextField(
                            shape = PickMsgShape.control,
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
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(PickMsgSpace.sm), verticalArrangement = Arrangement.spacedBy(PickMsgSpace.sm)) {
                            PickMsgFilterChip(selected = screenOn, onClick = { screenOn = true }, label = { Text("亮屏") })
                            PickMsgFilterChip(selected = !screenOn, onClick = { screenOn = false }, label = { Text("灭屏") })
                            PickMsgFilterChip(selected = !landscape, onClick = { landscape = false }, label = { Text("竖屏") })
                            PickMsgFilterChip(selected = landscape, onClick = { landscape = true }, label = { Text("横屏") })
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
    FlowRow(horizontalArrangement = Arrangement.spacedBy(PickMsgSpace.sm), verticalArrangement = Arrangement.spacedBy(PickMsgSpace.sm)) {
        names.forEachIndexed { index, label ->
            val bit = 1 shl index
            PickMsgFilterChip(
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
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = PickMsgSpace.sectionGap - PickMsgSpace.fieldGap),
    )
}

@file:OptIn(ExperimentalMaterial3Api::class)

package com.pickmsg.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pickmsg.app.data.PlanTask
import com.pickmsg.app.data.Scene
import com.pickmsg.app.service.PlanTaskScheduler
import com.pickmsg.app.service.SceneEvaluator
import com.pickmsg.app.ui.theme.PickMsgBadge
import com.pickmsg.app.ui.theme.PickMsgCard
import com.pickmsg.app.ui.theme.PickMsgSpace
import com.pickmsg.app.ui.theme.PickMsgScaffold
import com.pickmsg.app.ui.theme.PickMsgTopBar
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 「场景与计划」合并页（对齐竞品的单一 Tab）：
 * 场景卡片实时显示生效状态（生效中 / 未生效 · N 项不满足），
 * 计划卡片显示下次触发时间——配置了什么、现在是什么状态一目了然。
 */
@Composable
fun ScenesPlanScreen(scenesViewModel: ScenesViewModel, planViewModel: PlanViewModel) {
    val scenes by scenesViewModel.scenes.collectAsStateWithLifecycle()
    val tasks by planViewModel.tasks.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var editingScene by remember { mutableStateOf<Scene?>(null) }
    var creatingScene by remember { mutableStateOf(false) }
    var editingTask by remember { mutableStateOf<PlanTask?>(null) }
    var creatingTask by remember { mutableStateOf(false) }

    editingScene?.let { scene ->
        SceneEditScreen(existing = scene, viewModel = scenesViewModel, onClose = { editingScene = null })
        return
    }
    if (creatingScene) {
        SceneEditScreen(existing = null, viewModel = scenesViewModel, onClose = { creatingScene = false })
        return
    }
    editingTask?.let { task ->
        PlanEditScreen(existing = task, viewModel = planViewModel, onClose = { editingTask = null })
        return
    }
    if (creatingTask) {
        PlanEditScreen(existing = null, viewModel = planViewModel, onClose = { creatingTask = false })
        return
    }

    PickMsgScaffold(
        topBar = { PickMsgTopBar(title = "场景与计划") },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(PickMsgSpace.pagePadding),
            verticalArrangement = Arrangement.spacedBy(PickMsgSpace.fieldGap),
        ) {
            // ---------------- 场景 ----------------
            SectionHeader(
                title = "场景 · ${scenes.size}",
                hint = "限定规则在什么网络 / 时间 / 状态下生效",
                onAdd = { creatingScene = true },
                addLabel = "新建场景",
            )
            if (scenes.isEmpty()) {
                EmptyHint("还没有场景。创建后可在规则的「生效场景」里绑定。")
            } else {
                scenes.forEach { scene ->
                    SceneCard(
                        scene = scene,
                        onToggle = { scenesViewModel.toggleEnable(scene) },
                        onEdit = { editingScene = scene },
                    )
                }
            }

            Spacer(modifier = Modifier.padding(vertical = PickMsgSpace.sm))

            // ---------------- 计划任务 ----------------
            SectionHeader(
                title = "计划任务 · ${tasks.size}",
                hint = "到点发一条本地通知，可用作定时提醒",
                onAdd = { creatingTask = true },
                addLabel = "新建计划",
            )
            if (tasks.isEmpty()) {
                EmptyHint("还没有计划任务。")
            } else {
                tasks.forEach { task ->
                    PlanCard(
                        task = task,
                        onToggle = { planViewModel.toggleEnable(task) },
                        onEdit = { editingTask = task },
                    )
                }
                if (!PlanViewModel.exactAlarmStatus(context)) {
                    Text(
                        "提示：未授予精确闹钟权限，触发可能有几分钟延迟（系统设置中可授权）。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline,
                    )
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String, hint: String, onAdd: () -> Unit, addLabel: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            Text(hint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        IconButton(onClick = onAdd) {
            Icon(Icons.Default.Add, contentDescription = addLabel)
        }
    }
}

@Composable
private fun SceneCard(scene: Scene, onToggle: () -> Unit, onEdit: () -> Unit) {
    val context = LocalContext.current
    // 实时生效状态：启用且有条件的场景按当前设备状态求值
    val status: Pair<String, Boolean>? = when {
        !scene.enable -> "已停用" to false
        scene.conditions.isEmpty() -> "无条件（恒生效）" to true
        else -> {
            val (ok, failed) = SceneEvaluator.detail(context, scene)
            if (ok) "● 生效中" to true else "○ 未生效 · $failed 项不满足" to false
        }
    }
    PickMsgCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(PickMsgSpace.card),
            verticalArrangement = Arrangement.spacedBy(PickMsgSpace.sm),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(scene.name, style = MaterialTheme.typography.titleSmall)
                    Text(
                        scene.conditions.joinToString(" 且 ") { conditionSummary(it) }.ifBlank { "无条件" },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Switch(checked = scene.enable, onCheckedChange = { onToggle() })
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                PickMsgBadge(status!!.first, warning = !status.second)
                Spacer(modifier = Modifier.weight(1f))
                TextButton(onClick = onEdit, contentPadding = PaddingValues(0.dp)) {
                    Text("编辑", style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}

@Composable
private fun PlanCard(task: PlanTask, onToggle: () -> Unit, onEdit: () -> Unit) {
    PickMsgCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(PickMsgSpace.card),
            verticalArrangement = Arrangement.spacedBy(PickMsgSpace.sm),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("${task.time} · ${task.name}", style = MaterialTheme.typography.titleSmall)
                    Text(
                        "星期:${weekdayBits(task.weekdays)} · ${task.notifyTitle.ifBlank { task.notifyContent.take(16) }}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Switch(checked = task.enabled, onCheckedChange = { onToggle() })
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (task.enabled) {
                    val next = PlanTaskScheduler.nextTriggerAt(task.time, task.weekdays)
                    Text(
                        if (next != null) {
                            "下次触发：" + SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date(next))
                        } else {
                            "星期掩码未命中任何一天"
                        },
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                } else {
                    PickMsgBadge("已停用", warning = true)
                    Spacer(modifier = Modifier.weight(1f))
                }
                TextButton(onClick = onEdit, contentPadding = PaddingValues(0.dp)) {
                    Text("编辑", style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}

@Composable
private fun EmptyHint(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.outline,
        modifier = Modifier.padding(vertical = PickMsgSpace.sm),
    )
}

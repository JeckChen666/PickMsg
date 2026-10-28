@file:OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)

package com.pickmsg.app.ui

import android.content.Intent
import android.os.Build
import android.provider.Settings
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pickmsg.app.data.PlanTask

@Composable
fun PlanScreen(viewModel: PlanViewModel) {
    val tasks by viewModel.tasks.collectAsStateWithLifecycle()
    val exactAllowed by viewModel.exactAlarmAllowed.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf<PlanTask?>(null) }
    var creating by remember { mutableStateOf(false) }

    editing?.let { task ->
        PlanEditScreen(existing = task, viewModel = viewModel, onClose = { editing = null })
        return
    }
    if (creating) {
        PlanEditScreen(existing = null, viewModel = viewModel, onClose = { creating = false })
        return
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("计划任务") }) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { creating = true },
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("新建计划") },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.padding(padding)) {
            if (!exactAllowed) {
                val context = LocalContext.current
                Card(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text("未授予精确闹钟权限，计划任务可能延迟触发", style = MaterialTheme.typography.bodySmall)
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                            OutlinedButton(onClick = {
                                context.startActivity(
                                    Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
                                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                                )
                            }) { Text("去授权") }
                        }
                    }
                }
            }
            if (tasks.isEmpty()) {
                Column(
                    modifier = Modifier.fillMaxSize().padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Icon(Icons.Default.Schedule, contentDescription = null, tint = MaterialTheme.colorScheme.outline)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "还没有计划任务\n在指定时间发一条本地通知，可用作定时提醒",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.outline,
                    )
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(start = 16.dp, top = 8.dp, end = 16.dp, bottom = 96.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(tasks, key = { it.id }) { task ->
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier.padding(start = 16.dp, end = 8.dp, top = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("${task.time} · ${task.name}", style = MaterialTheme.typography.titleSmall)
                                    Text(
                                        "星期:" + weekdayBits(task.weekdays) +
                                            " · " + task.notifyTitle.ifBlank { task.notifyContent.take(20) },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.outline,
                                    )
                                }
                                Switch(checked = task.enabled, onCheckedChange = { viewModel.toggleEnable(task) })
                            }
                            Row(modifier = Modifier.padding(start = 16.dp, bottom = 10.dp)) {
                                androidx.compose.material3.TextButton(
                                    onClick = { editing = task },
                                    contentPadding = PaddingValues(0.dp),
                                ) { Text("编辑", style = MaterialTheme.typography.labelMedium) }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PlanEditScreen(existing: PlanTask?, viewModel: PlanViewModel, onClose: () -> Unit) {
    var name by remember { mutableStateOf(existing?.name ?: "") }
    var nameError by remember { mutableStateOf(false) }
    var time by remember { mutableStateOf(existing?.time ?: "09:00") }
    var weekdays by remember { mutableStateOf(existing?.weekdays ?: 127) }
    var title by remember { mutableStateOf(existing?.notifyTitle ?: "") }
    var content by remember { mutableStateOf(existing?.notifyContent ?: "") }
    var enabled by remember { mutableStateOf(existing?.enabled ?: true) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (existing == null) "新建计划" else "编辑计划") },
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
                        }) { Icon(Icons.Default.Delete, contentDescription = "删除计划") }
                    }
                    IconButton(onClick = {
                        if (name.isBlank()) {
                            nameError = true
                        } else {
                            viewModel.save(
                                PlanTask(
                                    id = existing?.id ?: 0,
                                    name = name.trim(),
                                    time = time.trim(),
                                    weekdays = weekdays,
                                    notifyTitle = title.trim(),
                                    notifyContent = content,
                                    enabled = enabled,
                                    lastTriggerAt = existing?.lastTriggerAt ?: 0,
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
                label = { Text("计划名称") },
                isError = nameError,
                singleLine = true,
            )
            OutlinedTextField(
                value = time,
                onValueChange = { time = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("触发时间 HH:mm") },
                supportingText = { Text("每天在该时间且星期命中时发一条通知") },
                singleLine = true,
            )
            WeekdayChips(weekdays) { weekdays = it }
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("通知标题（留空用计划名）") },
                singleLine = true,
            )
            OutlinedTextField(
                value = content,
                onValueChange = { content = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("通知内容") },
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = enabled, onCheckedChange = { enabled = it })
                Text("启用", modifier = Modifier.clickable { enabled = !enabled })
            }
        }
    }
}

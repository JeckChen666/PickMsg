@file:OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)

package com.pickmsg.app.ui

import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pickmsg.app.data.PlanTask
import com.pickmsg.app.ui.theme.PickMsgCard
import com.pickmsg.app.ui.theme.PickMsgDeleteAction
import com.pickmsg.app.ui.theme.PickMsgFloatingActionButton
import com.pickmsg.app.ui.theme.PickMsgManagementCard
import com.pickmsg.app.ui.theme.PickMsgOutlinedButton
import com.pickmsg.app.ui.theme.PickMsgScaffold
import com.pickmsg.app.ui.theme.PickMsgShape
import com.pickmsg.app.ui.theme.PickMsgSpace
import com.pickmsg.app.ui.theme.PickMsgTopBar

@Composable
fun PlanEditScreen(existing: PlanTask?, viewModel: PlanViewModel, onClose: () -> Unit) {
    BackHandler(onBack = onClose)
    var name by remember { mutableStateOf(existing?.name ?: "") }
    var nameError by remember { mutableStateOf(false) }
    var time by remember { mutableStateOf(existing?.time ?: "09:00") }
    var weekdays by remember { mutableStateOf(existing?.weekdays ?: 127) }
    var title by remember { mutableStateOf(existing?.notifyTitle ?: "") }
    var content by remember { mutableStateOf(existing?.notifyContent ?: "") }
    var enabled by remember { mutableStateOf(existing?.enabled ?: true) }

    PickMsgScaffold(
        secondaryPage = true,
        topBar = {
            PickMsgTopBar(
                title = if (existing == null) "新建计划" else "编辑计划",
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "放弃")
                    }
                },
                actions = {
                    if (existing != null) {
                        PickMsgDeleteAction(
                            label = "删除计划",
                            message = "删除后无法恢复，请确认不再需要此计划。",
                            onConfirm = { viewModel.delete(existing); onClose() },
                        )
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
                .padding(PickMsgSpace.pagePadding),
            verticalArrangement = Arrangement.spacedBy(PickMsgSpace.fieldGap),
        ) {
            OutlinedTextField(
                shape = PickMsgShape.control,
                value = name,
                onValueChange = { name = it; nameError = false },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("计划名称") },
                isError = nameError,
                supportingText = if (nameError) { { Text("名称不能为空") } } else null,
                singleLine = true,
            )
            OutlinedTextField(
                shape = PickMsgShape.control,
                value = time,
                onValueChange = { time = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("触发时间 HH:mm") },
                supportingText = { Text("每天在该时间且星期命中时发一条通知") },
                singleLine = true,
            )
            WeekdayChips(weekdays) { weekdays = it }
            OutlinedTextField(
                shape = PickMsgShape.control,
                value = title,
                onValueChange = { title = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("通知标题（留空用计划名）") },
                singleLine = true,
            )
            OutlinedTextField(
                shape = PickMsgShape.control,
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

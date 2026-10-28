@file:OptIn(ExperimentalMaterial3Api::class)

package com.pickmsg.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.pickmsg.app.data.NotificationRecord

@Composable
fun DetailScreen(
    record: NotificationRecord,
    onBack: () -> Unit,
    onDelete: () -> Unit,
    onTogglePin: () -> Unit = {},
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("通知详情") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(onClick = onTogglePin) {
                        Icon(
                            if (record.isPinned) Icons.Filled.PushPin else Icons.Outlined.PushPin,
                            contentDescription = if (record.isPinned) "取消置顶" else "置顶",
                        )
                    }
                    IconButton(onClick = onDelete) {
                        Icon(Icons.Default.Delete, contentDescription = "删除此条")
                    }
                },
            )
        },
    ) { padding ->
        SelectionContainer {
            Column(
                modifier = Modifier
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(record.appLabel, style = MaterialTheme.typography.titleLarge)
                Text(
                    record.packageName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline,
                )
                HorizontalDivider()
                DetailField("标题", record.title)
                DetailField("内容", record.text)
                DetailField("副文本", record.subText)
                DetailField("时间", formatDateTime(record.postTime))
                DetailField("渠道 ID", record.channelId)
                DetailField("类别", record.category)
                DetailField(
                    "状态",
                    buildList {
                        add(if (record.isOngoing) "常驻通知" else "普通通知")
                        add(if (record.isClearable) "可清除" else "不可清除")
                        if (record.isSensitive) add("敏感占位符")
                        add(if (record.source == NotificationRecord.SOURCE_SNAPSHOT) "来自快照" else "实时采集")
                    }.joinToString(" · "),
                )
                if (record.ruleName.isNotEmpty()) {
                    DetailField("命中规则", record.ruleName)
                }
                if (record.actionResults.isNotEmpty()) {
                    DetailField("动作结果", record.actionResults.joinToString("；"))
                }
                DetailField("应用键", record.appKey)
                DetailField("通知 Key", record.sbnKey)
                DetailField("入库时间", formatDateTime(record.createdAt))
            }
        }
    }
}

@Composable
private fun DetailField(label: String, value: String) {
    Column {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
        Text(value.ifEmpty { "—" }, style = MaterialTheme.typography.bodyLarge)
    }
}

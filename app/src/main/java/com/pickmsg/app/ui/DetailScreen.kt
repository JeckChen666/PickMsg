@file:OptIn(ExperimentalMaterial3Api::class)

package com.pickmsg.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.pickmsg.app.data.NotificationRecord
import com.pickmsg.app.ui.theme.PickMsgCard
import com.pickmsg.app.ui.theme.PickMsgDeleteAction
import com.pickmsg.app.ui.theme.PickMsgOutlinedButton
import com.pickmsg.app.ui.theme.PickMsgScaffold
import com.pickmsg.app.ui.theme.PickMsgSpace
import com.pickmsg.app.ui.theme.PickMsgTopBar

@Composable
fun DetailScreen(
    record: NotificationRecord,
    onBack: () -> Unit,
    onDelete: () -> Unit,
    onTogglePin: () -> Unit = {},
) {
    var showDiagnostics by rememberSaveable(record.id) { mutableStateOf(false) }
    PickMsgScaffold(
        secondaryPage = true,
        topBar = {
            PickMsgTopBar(
                title = "通知详情",
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
                    PickMsgDeleteAction("删除通知", "仅删除本地记录，此操作无法恢复。", onDelete)
                },
            )
        },
    ) { padding ->
        SelectionContainer {
            Column(
                modifier = Modifier
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(PickMsgSpace.pagePadding),
                verticalArrangement = Arrangement.spacedBy(PickMsgSpace.md),
            ) {
                Text(record.appLabel, style = MaterialTheme.typography.titleLarge)
                Text(formatDateTime(record.postTime), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                PickMsgCard(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(PickMsgSpace.card), verticalArrangement = Arrangement.spacedBy(PickMsgSpace.md)) {
                        DetailField("标题", record.title)
                        DetailField("内容", record.text)
                        if (record.subText.isNotBlank()) DetailField("补充内容", record.subText)
                    }
                }
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
                PickMsgOutlinedButton(onClick = { showDiagnostics = !showDiagnostics }) {
                    Text(if (showDiagnostics) "收起诊断信息" else "查看诊断信息")
                }
                if (showDiagnostics) {
                    HorizontalDivider()
                    DetailField("应用包名", record.packageName)
                    DetailField("渠道 ID", record.channelId)
                    DetailField("类别", record.category)
                    DetailField("应用键", record.appKey)
                    DetailField("通知 Key", record.sbnKey)
                    DetailField("入库时间", formatDateTime(record.createdAt))
                }
            }
        }
    }
}

@Composable
private fun DetailField(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(PickMsgSpace.xs)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
        Text(value.ifEmpty { "—" }, style = MaterialTheme.typography.bodyLarge)
    }
}

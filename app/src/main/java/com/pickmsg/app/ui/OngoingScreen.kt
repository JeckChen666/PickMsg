@file:OptIn(ExperimentalMaterial3Api::class)

package com.pickmsg.app.ui

import androidx.compose.foundation.rememberScrollState

import androidx.compose.foundation.verticalScroll

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pickmsg.app.service.NotificationCollectorService
import com.pickmsg.app.ui.theme.PickMsgBadge
import com.pickmsg.app.ui.theme.PickMsgButton
import com.pickmsg.app.ui.theme.PickMsgCard
import com.pickmsg.app.ui.theme.PickMsgScaffold
import com.pickmsg.app.ui.theme.PickMsgSpace
import com.pickmsg.app.ui.theme.PickMsgTopBar

/**
 * 常驻通知管理：
 * 隐藏完全可逆，恢复靠会话内缓存的通知 key。
 */
@Composable
fun OngoingScreen(onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    val activeList by NotificationCollectorService.activeList.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        if (!NotificationCollectorService.requestActiveList()) {
            // 服务未连接时列表保持为空
        }
    }

    PickMsgScaffold(
        secondaryPage = true,
        topBar = {
            PickMsgTopBar(
                title = "常驻通知管理",
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(onClick = { NotificationCollectorService.requestActiveList() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "刷新")
                    }
                },
            )
        },
    ) { padding ->
        if (activeList.isEmpty()) {
            Column(
                modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(PickMsgSpace.xxl),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    if (NotificationCollectorService.instance == null) {
                        "服务未连接，无法读取通知栏"
                    } else {
                        "通知栏没有活跃通知"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.padding(padding),
                contentPadding = PickMsgSpace.pagePadding,
                verticalArrangement = Arrangement.spacedBy(PickMsgSpace.listGap),
            ) {
                items(activeList, key = { it.key }) { item ->
                    PickMsgCard(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(PickMsgSpace.card), verticalArrangement = Arrangement.spacedBy(PickMsgSpace.xs)) {
                            Row {
                                Text(
                                    item.appLabel,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.weight(1f),
                                )
                                if (item.isHidden) {
                                    PickMsgBadge("已隐藏", warning = true)
                                } else if (item.isOngoing) {
                                    PickMsgBadge("常驻")
                                }
                            }
                            if (item.title.isNotEmpty()) {
                                Text(item.title, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            }
                            if (item.text.isNotEmpty()) {
                                Text(item.text, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(PickMsgSpace.sm)) {
                                if (item.isHidden) {
                                    PickMsgButton(onClick = { NotificationCollectorService.restoreOngoing(item.key) }) {
                                        Text("恢复显示")
                                    }
                                } else {
                                    PickMsgButton(onClick = { NotificationCollectorService.hideOngoing(item.key) }) {
                                        Text("隐藏（可恢复）")
                                    }
                                }
                            }
                        }
                    }
                }
                item {
                    Spacer(modifier = Modifier.height(PickMsgSpace.xl))
                    Text(
                        "隐藏后可在此恢复显示；应用重启后，恢复能力取决于系统通知状态。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@file:OptIn(ExperimentalMaterial3Api::class)

package com.pickmsg.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pickmsg.app.service.NotificationCollectorService

/**
 * 常驻通知管理（对标竞品的 snooze 一年隐藏 / 100ms 召回）：
 * 隐藏完全可逆，恢复靠会话内缓存的通知 key。
 */
@Composable
fun OngoingScreen(onBack: () -> Unit) {
    val activeList by NotificationCollectorService.activeList.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        if (!NotificationCollectorService.requestActiveList()) {
            // 服务未连接时列表保持为空
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("常驻通知管理") },
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
                modifier = Modifier.fillMaxSize().padding(padding).padding(32.dp),
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
                    color = MaterialTheme.colorScheme.outline,
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(activeList, key = { it.key }) { item ->
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row {
                                Text(
                                    item.appLabel,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.weight(1f),
                                )
                                if (item.isHidden) {
                                    Surface(
                                        color = MaterialTheme.colorScheme.tertiaryContainer,
                                    ) {
                                        Text(
                                            "已隐藏",
                                            style = MaterialTheme.typography.labelSmall,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                        )
                                    }
                                } else if (item.isOngoing) {
                                    Surface(
                                        color = MaterialTheme.colorScheme.secondaryContainer,
                                    ) {
                                        Text(
                                            "常驻",
                                            style = MaterialTheme.typography.labelSmall,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                        )
                                    }
                                }
                            }
                            if (item.title.isNotEmpty()) {
                                Text(item.title, style = MaterialTheme.typography.titleSmall, maxLines = 1)
                            }
                            if (item.text.isNotEmpty()) {
                                Text(item.text, style = MaterialTheme.typography.bodySmall, maxLines = 2)
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                if (item.isHidden) {
                                    Button(onClick = { NotificationCollectorService.restoreOngoing(item.key) }) {
                                        Text("恢复显示")
                                    }
                                } else {
                                    Button(onClick = { NotificationCollectorService.hideOngoing(item.key) }) {
                                        Text("隐藏（可恢复）")
                                    }
                                }
                            }
                        }
                    }
                }
                item {
                    Spacer(modifier = Modifier.height(24.dp))
                    Text(
                        "“隐藏”使用一年期 snooze 实现：通知从通知栏消失但随时可恢复，比直接清除更安全。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline,
                    )
                }
            }
        }
    }
}

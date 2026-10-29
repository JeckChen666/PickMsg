@file:OptIn(ExperimentalMaterial3Api::class)

package com.pickmsg.app.ui

import androidx.compose.foundation.rememberScrollState

import androidx.compose.foundation.verticalScroll

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Rule
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pickmsg.app.data.RuleInfo
import com.pickmsg.app.service.summaryText
import com.pickmsg.app.ui.theme.PickMsgFloatingActionButton
import com.pickmsg.app.ui.theme.PickMsgManagementCard
import com.pickmsg.app.ui.theme.PickMsgScaffold
import com.pickmsg.app.ui.theme.PickMsgSpace
import com.pickmsg.app.ui.theme.PickMsgTopBar

@Composable
fun RulesScreen(viewModel: RulesViewModel) {
    val rules by viewModel.rules.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf<RuleInfo?>(null) }
    var creating by remember { mutableStateOf(false) }

    editing?.let { rule ->
        RuleEditScreen(existing = rule, viewModel = viewModel, onClose = { editing = null })
        return
    }
    if (creating) {
        RuleEditScreen(existing = null, viewModel = viewModel, onClose = { creating = false })
        return
    }

    PickMsgScaffold(
        topBar = { PickMsgTopBar(title = "规则") },
        floatingActionButton = {
            PickMsgFloatingActionButton(
                onClick = { creating = true },
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                label = "新建规则",
            )
        },
    ) { padding ->
        if (rules.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(PickMsgSpace.xxl),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Icon(Icons.AutoMirrored.Filled.Rule, contentDescription = null, tint = MaterialTheme.colorScheme.outline)
                Spacer(modifier = Modifier.height(PickMsgSpace.sm))
                Text(
                    "还没有规则\n点击“新建规则”，设置通知处理方式",
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
                items(rules, key = { it.id }) { rule ->
                    RuleListItem(
                        rule = rule,
                        onToggle = { viewModel.toggleEnable(rule) },
                        onEdit = { editing = rule },
                    )
                }
            }
        }
    }
}

@Composable
private fun RuleListItem(rule: RuleInfo, onToggle: () -> Unit, onEdit: () -> Unit) {
    PickMsgManagementCard(
        title = rule.name, summary = rule.summaryText(), enabled = rule.enable,
        onToggle = { onToggle() }, onEdit = onEdit,
    )
}

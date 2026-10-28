package com.pickmsg.app.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Rule
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier

@Composable
fun MainScreen(
    recordsViewModel: MainViewModel,
    rulesViewModel: RulesViewModel,
    scenesViewModel: ScenesViewModel,
    planViewModel: PlanViewModel,
    mineViewModel: MineViewModel,
) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val tabs = listOf(
        "通知" to Icons.Default.Notifications,
        "规则" to Icons.AutoMirrored.Filled.Rule,
        "场景" to Icons.Default.Tune,
        "计划" to Icons.Default.Schedule,
        "我的" to Icons.Default.Person,
    )

    Scaffold(
        bottomBar = {
            NavigationBar {
                tabs.forEachIndexed { index, (label, icon) ->
                    NavigationBarItem(
                        selected = tab == index,
                        onClick = { tab = index },
                        icon = { Icon(icon, contentDescription = label) },
                        label = { Text(label) },
                    )
                }
            }
        },
    ) { padding ->
        Box(modifier = Modifier.padding(padding)) {
            when (tab) {
                0 -> RecordsScreen(recordsViewModel)
                1 -> RulesScreen(rulesViewModel)
                2 -> ScenesScreen(scenesViewModel)
                3 -> PlanScreen(planViewModel)
                4 -> MineScreen(mineViewModel)
            }
        }
    }
}

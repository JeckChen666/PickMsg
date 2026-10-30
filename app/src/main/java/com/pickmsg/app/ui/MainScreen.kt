@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.pickmsg.app.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Rule
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.pickmsg.app.ui.theme.JadeBackdrop
import com.pickmsg.app.ui.theme.JadeGlass
import com.pickmsg.app.ui.theme.LocalSecondaryPage
import com.pickmsg.app.ui.theme.PickMsgElevation
import com.pickmsg.app.ui.theme.PickMsgShape
import com.pickmsg.app.ui.theme.PickMsgSpace

@Composable
fun MainScreen(
    recordsViewModel: MainViewModel,
    rulesViewModel: RulesViewModel,
    scenesViewModel: ScenesViewModel,
    planViewModel: PlanViewModel,
    mineViewModel: MineViewModel,
) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var secondaryPage by remember { mutableStateOf(false) }
    val setSecondaryPage = remember { { shown: Boolean -> secondaryPage = shown } }
    val tabs = listOf(
        "通知" to Icons.Default.Notifications,
        "规则" to Icons.AutoMirrored.Filled.Rule,
        "场景与计划" to Icons.Default.Schedule,
        "我的" to Icons.Default.Person,
    )
    // 兼容旧版本 rememberSaveable 残留的超界 tab
    if (tab > tabs.lastIndex) tab = tabs.lastIndex

    val keyboardVisible = WindowInsets.isImeVisible
    JadeBackdrop {
        Scaffold(
            modifier = Modifier.imePadding(),
            containerColor = Color.Transparent,
            contentColor = MaterialTheme.colorScheme.onBackground,
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            bottomBar = {
                if (!keyboardVisible && !secondaryPage) {
                    JadeGlass(
                        modifier = Modifier.navigationBarsPadding().padding(horizontal = PickMsgSpace.dockHorizontal, vertical = PickMsgSpace.dockVertical),
                        emphasized = true,
                        shape = PickMsgShape.dock,
                        shadowElevation = PickMsgElevation.dock,
                    ) {
                        NavigationBar(
                            containerColor = Color.Transparent,
                            tonalElevation = PickMsgElevation.flat,
                            windowInsets = WindowInsets(0, 0, 0, 0),
                        ) {
                            tabs.forEachIndexed { index, (label, icon) ->
                                NavigationBarItem(
                                    selected = tab == index,
                                    onClick = { tab = index },
                                    icon = { Icon(icon, contentDescription = label) },
                                    label = { Text(label) },
                                )
                            }
                        }
                    }
                }
            },
        ) { padding ->
            Box(modifier = Modifier.padding(padding).consumeWindowInsets(padding)) {
                CompositionLocalProvider(LocalSecondaryPage provides setSecondaryPage) {
                    when (tab) {
                        0 -> RecordsScreen(recordsViewModel)
                        1 -> RulesScreen(rulesViewModel)
                        2 -> ScenesPlanScreen(scenesViewModel, planViewModel)
                        3 -> MineScreen(mineViewModel)
                    }
                }
            }
        }
    }
}

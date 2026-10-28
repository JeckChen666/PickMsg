package com.pickmsg.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.pickmsg.app.PickMsgApp
import com.pickmsg.app.data.ActionSpec
import com.pickmsg.app.data.AppCount
import com.pickmsg.app.data.RuleInfo
import com.pickmsg.app.data.Scene
import com.pickmsg.app.service.ActionExecutor
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class RulesViewModel(application: Application) : AndroidViewModel(application) {

    private val database = PickMsgApp.from(application).database
    private val repository = PickMsgApp.from(application).repository

    val rules: StateFlow<List<RuleInfo>> = database.ruleDao().observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** 已在记录中出现过的应用，供编辑页快捷选择 */
    val appCounts: StateFlow<List<AppCount>> = repository.observeAppCounts()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** 规则可绑定的场景 */
    val scenes: StateFlow<List<Scene>> = database.sceneDao().observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _webhookTestResult = MutableStateFlow<String?>(null)
    val webhookTestResult: StateFlow<String?> = _webhookTestResult.asStateFlow()

    fun save(rule: RuleInfo) {
        viewModelScope.launch { database.ruleDao().upsert(rule) }
    }

    fun toggleEnable(rule: RuleInfo) {
        viewModelScope.launch { database.ruleDao().update(rule.copy(enable = !rule.enable)) }
    }

    fun delete(rule: RuleInfo) {
        viewModelScope.launch { database.ruleDao().deleteById(rule.id) }
    }

    /** 编辑页的 Webhook 测试按钮 */
    fun testWebhook(action: ActionSpec) {
        viewModelScope.launch {
            _webhookTestResult.value = "测试中…"
            _webhookTestResult.value = ActionExecutor.execute(
                context = getApplication(),
                action = action,
                sbnKey = "",
                contentIntent = null,
                appLabel = "测试",
                title = "PickMsg Webhook 测试",
                text = "这是一条来自 PickMsg 的测试消息",
                canceller = { },
                scope = viewModelScope,
            )
        }
    }
}

package com.pickmsg.app

import android.content.Context
import com.pickmsg.app.data.ActionSpec
import com.pickmsg.app.data.ActionType
import com.pickmsg.app.data.AppMatchMode
import com.pickmsg.app.data.FormatOp
import com.pickmsg.app.data.FormatOpType
import com.pickmsg.app.data.FormatTarget
import com.pickmsg.app.data.MatchMode
import com.pickmsg.app.data.RuleInfo
import com.pickmsg.app.data.SettingsStore
import com.pickmsg.app.service.EventLog
import kotlinx.coroutines.flow.first

/**
 * 内置预设规则：首次启动种一条「验证码自动复制」，默认停用。
 * 匹配含"验证码/校验码"的通知，正则提取出验证码本身（而非整条通知）复制到剪贴板。
 * 只种一次（SettingsStore.presetSeeded）；用户删除后不再复活。
 */
object PresetRules {

    const val CODE_RULE_NAME = "验证码自动复制（预设）"

    /** 提取验证码本身：锚定"验证码/校验码"关键词（避免订单号等长数字串被误提），
     *  允许最多 3 个非数字分隔字符（是/冒号/空格），代码支持连字符分隔（482-913），
     *  用捕获组只取数字部分 */
    const val CODE_EXTRACT_REGEX = "(?:验证码|校验码)\\D{0,3}((?:\\d[\\-\\s]?){3,7}\\d)"

    fun presetCodeRule(timestamp: Long = System.currentTimeMillis()): RuleInfo = RuleInfo(
        name = CODE_RULE_NAME,
        enable = false,
        timestamp = timestamp,
        matchAppMode = AppMatchMode.ALL,
        matchTitleMode = MatchMode.ALL,
        matchTextMode = MatchMode.CONTAINS_ANY,
        matchTextKeywords = listOf("验证码", "校验码"),
        titleOrText = false,
        formatOps = listOf(
            FormatOp(
                op = FormatOpType.REGEX_EXTRACT,
                target = FormatTarget.TEXT,
                from = CODE_EXTRACT_REGEX,
            ),
        ),
        actions = listOf(ActionSpec(type = ActionType.COPY)),
    )

    suspend fun seedIfNeeded(context: Context) {
        if (SettingsStore.presetSeeded) return
        runCatching {
            val app = PickMsgApp.from(context)
            val exists = app.database.ruleDao().observeAll().first().any { it.name == CODE_RULE_NAME }
            if (!exists) {
                app.database.ruleDao().upsert(presetCodeRule())
                EventLog.log("🌱 已内置预设规则「验证码自动复制」（默认停用，可在规则页开启）")
            }
            SettingsStore.presetSeeded = true
        }.onFailure {
            EventLog.log("⚠️ 预设规则种子失败: ${it.message}（下次启动重试）")
        }
    }
}

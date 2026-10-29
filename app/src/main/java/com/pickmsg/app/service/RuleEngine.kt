package com.pickmsg.app.service

import android.os.Build
import com.pickmsg.app.data.ActionSpec
import com.pickmsg.app.data.ActionType
import com.pickmsg.app.data.AppMatchMode
import com.pickmsg.app.data.FormatOp
import com.pickmsg.app.data.FormatOpType
import com.pickmsg.app.data.FormatTarget
import com.pickmsg.app.data.MatchMode
import com.pickmsg.app.data.RuleInfo
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.Optional
import java.util.concurrent.ConcurrentHashMap
import java.util.regex.Pattern

/**
 * 规则匹配：
 * 应用三模式 × 文本八模式 × 标题/内容且或组合。
 */
object RuleMatcher {

    // 正则编译缓存。
    // 注意 ConcurrentHashMap 不允许 null value：非法正则以 Optional.empty 缓存，
    // 否则 getOrPut(putIfAbsent(key, null)) 会 NPE 崩掉整个通知处理协程。
    private val regexCache = ConcurrentHashMap<String, Optional<Pattern>>()

    internal fun patternOf(regex: String): Pattern? =
        regexCache
            .getOrPut(regex) { Optional.ofNullable(runCatching { Pattern.compile(regex) }.getOrNull()) }
            .orElse(null)

    fun matchApp(rule: RuleInfo, appKey: String): Boolean = when (rule.matchAppMode) {
        AppMatchMode.ALL -> true
        AppMatchMode.WHITELIST -> rule.matchApps.contains(appKey)
        AppMatchMode.BLACKLIST -> !rule.matchApps.contains(appKey)
    }

    /** 返回 (标题侧命中, 内容侧命中) */
    fun matchSides(rule: RuleInfo, title: String, text: String): Pair<Boolean, Boolean> =
        matchText(
            rule.matchTitleMode, rule.matchTitleKeywords, rule.matchTitleKeywordsA, rule.matchTitleKeywordsB, title,
        ) to matchText(
            rule.matchTextMode, rule.matchTextKeywords, rule.matchTextKeywordsA, rule.matchTextKeywordsB, text,
        )

    fun matches(rule: RuleInfo, appKey: String, title: String, text: String): Boolean {
        if (!matchApp(rule, appKey)) return false
        val (titleHit, textHit) = matchSides(rule, title, text)
        return if (rule.titleOrText) titleHit || textHit else titleHit && textHit
    }

    fun matchText(mode: MatchMode, keywords: List<String>, groupA: List<String>, groupB: List<String>, text: String): Boolean {
        if (text.isEmpty()) return mode == MatchMode.ALL
        return when (mode) {
            MatchMode.ALL -> true
            MatchMode.CONTAINS_ANY -> keywords.any { text.contains(it, ignoreCase = true) }
            MatchMode.CONTAINS_ALL -> keywords.isNotEmpty() && keywords.all { text.contains(it, ignoreCase = true) }
            MatchMode.NOT_CONTAINS_ANY -> keywords.none { text.contains(it, ignoreCase = true) }
            MatchMode.CONTAINS_NONE -> keywords.none { text.contains(it, ignoreCase = true) }
            MatchMode.A_AND_B -> groupA.any { text.contains(it, ignoreCase = true) } &&
                groupB.any { text.contains(it, ignoreCase = true) }
            MatchMode.REGEX_MATCH -> keywords.any { p -> patternOf(p)?.matcher(text)?.matches() == true }
            MatchMode.REGEX_EXTRACT -> keywords.any { p -> patternOf(p)?.matcher(text)?.find() == true }
        }
    }
}

/**
 * 格式化管线："一处格式化处处生效"——
 * 管线输出同时供重发等一切"发送内容"的动作使用。
 */
object RuleFormatter {

    fun apply(ops: List<FormatOp>, title: String, text: String): Pair<String, String> {
        var t = title
        var x = text
        for (op in ops) {
            try {
                when (op.target) {
                    FormatTarget.TITLE -> t = applyOne(op, t)
                    FormatTarget.TEXT -> x = applyOne(op, x)
                }
            } catch (e: Exception) {
                EventLog.log("⚠️ 格式化操作 ${op.op} 失败: ${e.message}")
            }
        }
        return t to x
    }

    private fun applyOne(op: FormatOp, input: String): String {
        if (input.isEmpty()) return input
        return when (op.op) {
            FormatOpType.REGEX_REPLACE ->
                RuleMatcher.patternOf(op.from)?.matcher(input)?.replaceAll(op.to) ?: input
            FormatOpType.PREFIX -> op.to + input
            FormatOpType.SUFFIX -> input + op.to
            FormatOpType.REGEX_EXTRACT ->
                RuleMatcher.patternOf(op.from)?.matcher(input)?.takeIf { it.find() }?.group() ?: input
        }
    }
}

/**
 * 动作模板占位符（常用 Webhook 占位符集合）：
 * {app} {title} {content} {time} {date} {week} {mobile}
 */
object TemplateRenderer {

    fun render(template: String, appLabel: String, title: String, text: String): String {
        if (!template.contains('{')) return template
        val now = Date()
        val dayOfWeek = SimpleDateFormat("u", Locale.CHINA).format(now).toInt() % 7 // 1=周一…7=周日 → 0=周日
        return template
            .replace("{app}", appLabel)
            .replace("{title}", title)
            .replace("{content}", text)
            .replace("{time}", SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(now))
            .replace("{date}", SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(now))
            .replace("{week}", "星期" + "日一二三四五六"[dayOfWeek])
            .replace("{mobile}", Build.MODEL ?: "unknown")
    }
}

/** 生成规则列表页的摘要行 */
fun RuleInfo.summaryText(): String = buildString {
    append(matchSummaryText())
    if (formatOps.isNotEmpty()) append(" · 格式化×${formatOps.size}")
    if (actions.isNotEmpty()) {
        append(" → ")
        append(actionsSummaryText(actions))
    }
}

/** 匹配条件摘要（不含格式化与动作），供编辑页折叠分区显示 */
fun RuleInfo.matchSummaryText(): String = buildString {
    append(
        when (matchAppMode) {
            AppMatchMode.ALL -> "全部应用"
            AppMatchMode.WHITELIST -> "仅选中(${matchApps.size})"
            AppMatchMode.BLACKLIST -> "排除(${matchApps.size})"
        },
    )
    if (matchTitleMode != MatchMode.ALL) append(" · 标题:${matchTitleMode.label()}${preview(matchTitleKeywords)}")
    if (matchTextMode != MatchMode.ALL) append(" · 内容:${matchTextMode.label()}${preview(matchTextKeywords)}")
    append(" · ${if (titleOrText) "标题或内容" else "标题且内容"}")
}

/** 动作清单摘要，供编辑页折叠分区显示 */
fun actionsSummaryText(actions: List<ActionSpec>): String =
    if (actions.isEmpty()) {
        "未选择动作（仅记录）"
    } else {
        actions.joinToString("+") { a ->
            when (a.type) {
                ActionType.REMOVE -> if (a.delaySec > 0) "延迟${a.delaySec}s移除" else "移除"
                ActionType.REPOST -> "重发"
                ActionType.COPY -> "复制"
                ActionType.OPEN -> "打开原通知"
                ActionType.SOUND -> "铃声"
                ActionType.VIBRATE -> "震动"
                ActionType.TTS -> "播报"
                ActionType.WEBHOOK -> "Webhook"
                ActionType.EMAIL -> "邮件"
                ActionType.SMS -> "短信"
                ActionType.WRISTBAND -> "手环×${a.repeatTimes}"
            }
        }
    }

private fun MatchMode.label(): String = when (this) {
    MatchMode.ALL -> "无条件"
    MatchMode.CONTAINS_ANY -> "包含任一"
    MatchMode.CONTAINS_ALL -> "全部包含"
    MatchMode.NOT_CONTAINS_ANY -> "不包含任一"
    MatchMode.CONTAINS_NONE -> "均不包含"
    MatchMode.A_AND_B -> "A且B"
    MatchMode.REGEX_MATCH -> "正则"
    MatchMode.REGEX_EXTRACT -> "正则提取"
}

private fun preview(keywords: List<String>): String =
    if (keywords.isEmpty()) "" else "[${keywords.joinToString(",") { it.take(10) }}]"

package com.pickmsg.app.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/** 应用匹配模式：全部 / 仅选中（白名单）/ 排除选中（黑名单） */
enum class AppMatchMode { ALL, WHITELIST, BLACKLIST }

/**
 * 文本匹配模式（8 种；同类工具常见的第 9 种"复合模式"为嵌套递归，未实现）。
 * 关键词列表均按忽略大小写的 contains 求值。
 */
enum class MatchMode {
    /** 无条件通过 */
    ALL,
    /** 任一关键词出现即命中 */
    CONTAINS_ANY,
    /** 全部关键词都出现才命中 */
    CONTAINS_ALL,
    /** 出现任一关键词即不命中 */
    NOT_CONTAINS_ANY,
    /** 所有关键词都不出现才命中（与 NOT_CONTAINS_ANY 语义一致，保留为独立枚举值） */
    CONTAINS_NONE,
    /** A 组任一命中 且 B 组任一命中 */
    A_AND_B,
    /** 正则整串匹配（Pattern.matches） */
    REGEX_MATCH,
    /** 正则能提取到片段即命中（find） */
    REGEX_EXTRACT,
}

enum class FormatOpType {
    /** 正则替换：from→to replaceAll */
    REGEX_REPLACE,
    /** 添加前缀（to） */
    PREFIX,
    /** 添加后缀（to） */
    SUFFIX,
    /** 正则提取：取首个匹配片段整体替换 */
    REGEX_EXTRACT,
}

enum class FormatTarget { TITLE, TEXT }

data class FormatOp(
    val op: FormatOpType,
    val target: FormatTarget,
    val from: String = "",
    val to: String = "",
)

enum class ActionType {
    REMOVE,
    REPOST,
    /** 复制格式化后的内容到剪贴板 */
    COPY,
    /** 触发原通知 contentIntent（代替用户点击） */
    OPEN,
    /** 播放自定义铃声 */
    SOUND,
    /** 自定义波形震动 */
    VIBRATE,
    /** TTS 语音播报 */
    TTS,
    /** Webhook 转发 */
    WEBHOOK,
    /** 邮件转发（SMTP 账号在"我的"里配置） */
    EMAIL,
    /** 短信转发 */
    SMS,
    /** 手环转发（重复重发 N 次） */
    WRISTBAND,
}

/**
 * 动作配置。所有动作共用一个扁平结构，未用到的字段保持默认值；
 * JSON 序列化向后兼容（新字段缺失时按默认值解析）。
 */
data class ActionSpec(
    val type: ActionType,
    /** REMOVE：延迟秒数；WRISTBAND：重发结束后自动清理的延迟秒数 */
    val delaySec: Int = 0,
    /** REPOST：标题模板，空=用格式化后的原标题 */
    val titleTemplate: String = "",
    /** REPOST：内容模板，空=用格式化后的原内容 */
    val textTemplate: String = "",
    // —— SOUND ——
    val soundUri: String = "",
    /** SOUND：无视系统音量强制播放；WRISTBAND：无视蓝牙在线状态强制执行 */
    val force: Boolean = false,
    // —— VIBRATE ——
    /** 波形，如 "0:200:100:200"（等待:震动:等待:震动，毫秒） */
    val vibratePattern: String = "",
    // —— TTS ——
    val ttsTemplate: String = "",
    // —— WEBHOOK ——
    val url: String = "",
    val method: String = "POST",
    /** 每行一条 "Key: Value" */
    val headers: String = "",
    val body: String = "",
    // —— EMAIL ——
    /** 逗号分隔收件人 */
    val emailTo: String = "",
    // —— SMS ——
    val phoneNumbers: List<String> = emptyList(),
    // —— WRISTBAND ——
    val repeatTimes: Int = 0,
    val intervalSec: Int = 0,
)

/** 规则绑定的多个场景之间如何求值 */
enum class SceneMatchMode { ANY, ALL }

@Entity(tableName = "rule_info")
data class RuleInfo(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String = "",
    val enable: Boolean = false,
    val timestamp: Long = 0,
    val matchAppMode: AppMatchMode = AppMatchMode.ALL,
    val matchApps: List<String> = emptyList(),
    val matchTitleMode: MatchMode = MatchMode.ALL,
    val matchTitleKeywords: List<String> = emptyList(),
    val matchTitleKeywordsA: List<String> = emptyList(),
    val matchTitleKeywordsB: List<String> = emptyList(),
    val matchTextMode: MatchMode = MatchMode.ALL,
    val matchTextKeywords: List<String> = emptyList(),
    val matchTextKeywordsA: List<String> = emptyList(),
    val matchTextKeywordsB: List<String> = emptyList(),
    /** true=标题或内容任一命中；false=标题与内容须同时命中。默认"且"：单边配置（另一边为无条件）时行为才符合直觉 */
    val titleOrText: Boolean = false,
    /** 绑定的场景 ID；为空 = 不受场景限制 */
    val scenesIds: List<Long> = emptyList(),
    val sceneMatchMode: SceneMatchMode = SceneMatchMode.ANY,
    val formatOps: List<FormatOp> = emptyList(),
    val actions: List<ActionSpec> = emptyList(),
)

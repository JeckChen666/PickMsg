package com.pickmsg.app.service

import com.pickmsg.app.data.ActionSpec
import com.pickmsg.app.data.ActionType

/**
 * 动作效果描述：把"配置了什么"翻译成"将发生什么"，
 * 供规则编辑页的实时预览卡使用。纯函数，样例文本经占位符渲染后截断展示。
 */
object ActionPreview {

    private const val MAX_LENGTH = 60

    private fun short(s: String): String =
        if (s.length > MAX_LENGTH) s.take(MAX_LENGTH) + "…" else s

    fun describe(action: ActionSpec, appLabel: String, title: String, text: String): String = when (action.type) {
        ActionType.REMOVE ->
            if (action.delaySec > 0) "延迟 ${action.delaySec} 秒后从通知栏移除" else "立即从通知栏移除"

        ActionType.REPOST -> {
            val newTitle = TemplateRenderer.render(action.titleTemplate.ifBlank { title }, appLabel, title, text)
            val newText = TemplateRenderer.render(action.textTemplate.ifBlank { text }, appLabel, title, text)
            "重发通知 · 标题「${short(newTitle)}」内容「${short(newText)}」"
        }

        ActionType.COPY -> "复制到剪贴板：${short(text)}"

        ActionType.OPEN -> "打开原通知的目标页面（等同手动点击）"

        ActionType.SOUND ->
            if (action.force) "强制播放铃声（闹钟通道，无视音量）" else "播放自定义铃声"

        ActionType.VIBRATE -> "按波形震动 ${action.vibratePattern.ifBlank { "（默认短震）" }}"

        ActionType.TTS -> {
            val spoken = TemplateRenderer.render(action.ttsTemplate.ifBlank { "$appLabel，$title" }, appLabel, title, text)
            "语音播报：「${short(spoken)}」"
        }

        ActionType.WEBHOOK -> buildString {
            append("${action.method.uppercase()} ${action.url}")
            val body = TemplateRenderer.render(action.body, appLabel, title, text)
            if (body.isNotBlank()) append("\n请求体：${short(body)}")
        }

        ActionType.EMAIL -> "发送邮件至 ${action.emailTo.ifBlank { "（未填收件人）" }}：「${short(title)}」"

        ActionType.SMS ->
            "发送短信至 ${action.phoneNumbers.joinToString("、").ifBlank { "（未填号码）" }}：${short(text)}"

        ActionType.WRISTBAND ->
            "重复提醒 ${action.repeatTimes} 次，间隔 ${action.intervalSec} 秒"
    }
}

package com.pickmsg.app

import com.pickmsg.app.data.ActionType
import com.pickmsg.app.data.FormatOpType
import com.pickmsg.app.data.FormatTarget
import com.pickmsg.app.data.MatchMode
import com.pickmsg.app.service.RuleFormatter
import com.pickmsg.app.service.RuleMatcher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PresetRulesTest {

    private val preset = PresetRules.presetCodeRule()

    @Test
    fun `预设规则默认停用`() {
        assertFalse(preset.enable)
        assertEquals(PresetRules.CODE_RULE_NAME, preset.name)
    }

    @Test
    fun `匹配含验证码关键词的通知文本`() {
        val rule = preset
        assertTrue(RuleMatcher.matches(rule, "com.android.sms", "10658", "您的验证码是482913，请勿泄露"))
        assertTrue(RuleMatcher.matches(rule, "com.android.sms", "银行通知", "【XX银行】校验码 826 615，10 分钟内有效"))
        assertFalse(RuleMatcher.matches(rule, "com.android.sms", "10658", "您的话费余额不足"))
    }

    @Test
    fun `格式化提取验证码本身而非整条短信`() {
        val (_, extracted) = RuleFormatter.apply(preset.formatOps, "10658", "您的验证码是482913，请勿泄露")
        assertEquals("482913", extracted)

        val (_, colon) = RuleFormatter.apply(preset.formatOps, "t", "【XX银行】校验码：826615，10 分钟内有效")
        assertEquals("826615", colon)

        val (_, dashed) = RuleFormatter.apply(preset.formatOps, "t", "验证码 482-913，请勿泄露")
        assertEquals("482-913", dashed)

        // 无匹配时保持原文（复制整条短信，优于复制空串）
        val (_, fallback) = RuleFormatter.apply(preset.formatOps, "t", "没有验证码字段")
        assertEquals("没有验证码字段", fallback)
    }

    @Test
    fun `长订单号不会被误提为验证码`() {
        // 订单号 1234567890123 前是"号"，不满足 lookbehind；验证码 482913 前是"是"后的空格
        val (_, extracted) = RuleFormatter.apply(preset.formatOps, "t", "订单号 1234567890123 已发货，验证码 482913")
        assertEquals("482913", extracted)
    }

    @Test
    fun `唯一动作是复制到剪贴板`() {
        assertEquals(listOf(ActionType.COPY), preset.actions.map { it.type })
    }

    @Test
    fun `提取目标作用于内容侧`() {
        assertEquals(FormatTarget.TEXT, preset.formatOps.single().target)
        assertEquals(FormatOpType.REGEX_EXTRACT, preset.formatOps.single().op)
    }

    @Test
    fun `标题侧无条件且与内容求与 文本侧独立决定命中`() {
        assertEquals(MatchMode.ALL, preset.matchTitleMode)
        assertFalse(preset.titleOrText)
        assertEquals(MatchMode.CONTAINS_ANY, preset.matchTextMode)
    }
}

package com.pickmsg.app.service

import com.pickmsg.app.data.AppMatchMode
import com.pickmsg.app.data.MatchMode
import com.pickmsg.app.data.RuleInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RuleMatcherTest {

    private fun rule(
        appMode: AppMatchMode = AppMatchMode.ALL,
        apps: List<String> = emptyList(),
        titleMode: MatchMode = MatchMode.ALL,
        titleKeywords: List<String> = emptyList(),
        textMode: MatchMode = MatchMode.ALL,
        textKeywords: List<String> = emptyList(),
        titleOrText: Boolean = false,
    ) = RuleInfo(
        name = "t",
        matchAppMode = appMode,
        matchApps = apps,
        matchTitleMode = titleMode,
        matchTitleKeywords = titleKeywords,
        matchTextMode = textMode,
        matchTextKeywords = textKeywords,
        titleOrText = titleOrText,
    )

    // ---------- 应用匹配 ----------

    @Test
    fun `ALL 模式任何应用都通过`() {
        val r = rule(appMode = AppMatchMode.ALL, apps = listOf("com.a"))
        assertTrue(RuleMatcher.matchApp(r, "com.other"))
    }

    @Test
    fun `白名单命中与未命中`() {
        val r = rule(appMode = AppMatchMode.WHITELIST, apps = listOf("com.a", "com.b"))
        assertTrue(RuleMatcher.matchApp(r, "com.a"))
        assertFalse(RuleMatcher.matchApp(r, "com.c"))
    }

    @Test
    fun `黑名单命中与未命中`() {
        val r = rule(appMode = AppMatchMode.BLACKLIST, apps = listOf("com.a"))
        assertFalse(RuleMatcher.matchApp(r, "com.a"))
        assertTrue(RuleMatcher.matchApp(r, "com.b"))
    }

    // ---------- 文本匹配八模式 ----------

    @Test
    fun `包含任一 忽略大小写`() {
        assertTrue(RuleMatcher.matchText(MatchMode.CONTAINS_ANY, listOf("验证码"), emptyList(), emptyList(), "您的验证码482913"))
        assertTrue(RuleMatcher.matchText(MatchMode.CONTAINS_ANY, listOf("error"), emptyList(), emptyList(), "Error occurred"))
        assertFalse(RuleMatcher.matchText(MatchMode.CONTAINS_ANY, listOf("error"), emptyList(), emptyList(), "all good"))
    }

    @Test
    fun `全部包含 缺一不可且空关键词不通过`() {
        val kws = listOf("订单", "发货")
        assertTrue(RuleMatcher.matchText(MatchMode.CONTAINS_ALL, kws, emptyList(), emptyList(), "订单已发货"))
        assertFalse(RuleMatcher.matchText(MatchMode.CONTAINS_ALL, kws, emptyList(), emptyList(), "订单已取消"))
        assertFalse(RuleMatcher.matchText(MatchMode.CONTAINS_ALL, emptyList(), emptyList(), emptyList(), "任意文本"))
    }

    @Test
    fun `不包含任一 与 均不包含 语义一致`() {
        val kws = listOf("广告", "推广")
        assertTrue(RuleMatcher.matchText(MatchMode.NOT_CONTAINS_ANY, kws, emptyList(), emptyList(), "正常消息"))
        assertTrue(RuleMatcher.matchText(MatchMode.CONTAINS_NONE, kws, emptyList(), emptyList(), "正常消息"))
        assertFalse(RuleMatcher.matchText(MatchMode.NOT_CONTAINS_ANY, kws, emptyList(), emptyList(), "这是广告"))
        assertFalse(RuleMatcher.matchText(MatchMode.CONTAINS_NONE, kws, emptyList(), emptyList(), "推广内容"))
    }

    @Test
    fun `A且B 需要两组同时命中`() {
        val a = listOf("邮件")
        val b = listOf("会议", "日程")
        assertTrue(RuleMatcher.matchText(MatchMode.A_AND_B, emptyList(), a, b, "邮件：明天会议提醒"))
        assertFalse(RuleMatcher.matchText(MatchMode.A_AND_B, emptyList(), a, b, "邮件：账单已出"))
        assertFalse(RuleMatcher.matchText(MatchMode.A_AND_B, emptyList(), a, b, "会议通知"))
    }

    @Test
    fun `正则匹配是整串语义`() {
        val p = listOf("PM_[0-9]+")
        assertTrue(RuleMatcher.matchText(MatchMode.REGEX_MATCH, p, emptyList(), emptyList(), "PM_99"))
        assertFalse(RuleMatcher.matchText(MatchMode.REGEX_MATCH, p, emptyList(), emptyList(), "code PM_99 end"))
    }

    @Test
    fun `正则提取是片段语义`() {
        val p = listOf("PM_[0-9]+")
        assertTrue(RuleMatcher.matchText(MatchMode.REGEX_EXTRACT, p, emptyList(), emptyList(), "code PM_123 end"))
        assertFalse(RuleMatcher.matchText(MatchMode.REGEX_EXTRACT, p, emptyList(), emptyList(), "no code here"))
    }

    @Test
    fun `非法正则不崩溃且不命中`() {
        val bad = listOf("[unclosed")
        assertFalse(RuleMatcher.matchText(MatchMode.REGEX_MATCH, bad, emptyList(), emptyList(), "any"))
        assertFalse(RuleMatcher.matchText(MatchMode.REGEX_EXTRACT, bad, emptyList(), emptyList(), "any"))
    }

    @Test
    fun `空文本仅无条件模式通过`() {
        for (mode in MatchMode.entries) {
            assertEquals(
                "mode=$mode",
                mode == MatchMode.ALL,
                RuleMatcher.matchText(mode, listOf("kw"), emptyList(), emptyList(), ""),
            )
        }
    }

    // ---------- 标题/内容组合 ----------

    @Test
    fun `默认且 组合需双侧命中`() {
        val r = rule(
            titleMode = MatchMode.CONTAINS_ANY,
            titleKeywords = listOf("微信"),
            textMode = MatchMode.CONTAINS_ANY,
            textKeywords = listOf("红包"),
            titleOrText = false,
        )
        assertTrue(RuleMatcher.matches(r, "com.a", "微信", "收到红包"))
        assertFalse(RuleMatcher.matches(r, "com.a", "微信", "普通消息"))
        assertFalse(RuleMatcher.matches(r, "com.a", "其他", "收到红包"))
    }

    @Test
    fun `或 组合任一侧命中即可`() {
        val r = rule(
            titleMode = MatchMode.CONTAINS_ANY,
            titleKeywords = listOf("微信"),
            textMode = MatchMode.CONTAINS_ANY,
            textKeywords = listOf("红包"),
            titleOrText = true,
        )
        assertTrue(RuleMatcher.matches(r, "com.a", "微信", "普通消息"))
        assertTrue(RuleMatcher.matches(r, "com.a", "其他", "收到红包"))
        assertFalse(RuleMatcher.matches(r, "com.a", "其他", "普通消息"))
    }

    @Test
    fun `应用不匹配时文本再命中也是不匹配`() {
        val r = rule(
            appMode = AppMatchMode.WHITELIST,
            apps = listOf("com.a"),
            textMode = MatchMode.CONTAINS_ANY,
            textKeywords = listOf("kw"),
        )
        assertFalse(RuleMatcher.matches(r, "com.b", "t", "kw"))
        assertTrue(RuleMatcher.matches(r, "com.a", "t", "kw"))
    }

    @Test
    fun `matchSides 返回双侧独立结果`() {
        val r = rule(
            titleMode = MatchMode.CONTAINS_ANY,
            titleKeywords = listOf("t1"),
            textMode = MatchMode.CONTAINS_ANY,
            textKeywords = listOf("x1"),
            titleOrText = true,
        )
        val (titleOk, textOk) = RuleMatcher.matchSides(r, "t1 here", "nothing")
        assertTrue(titleOk)
        assertFalse(textOk)
    }
}

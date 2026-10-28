package com.pickmsg.app.service

import com.pickmsg.app.data.FormatOp
import com.pickmsg.app.data.FormatOpType
import com.pickmsg.app.data.FormatTarget
import org.junit.Assert.assertEquals
import org.junit.Test

class RuleFormatterTest {

    @Test
    fun `正则替换 作用于标题`() {
        val op = FormatOp(FormatOpType.REGEX_REPLACE, FormatTarget.TITLE, from = "验证码[0-9]+", to = "验证码***")
        val (t, x) = RuleFormatter.apply(listOf(op), "您的验证码482913已发送", "正文")
        assertEquals("您的验证码***已发送", t)
        assertEquals("正文", x)
    }

    @Test
    fun `前后缀 只作用于目标侧`() {
        val ops = listOf(
            FormatOp(FormatOpType.PREFIX, FormatTarget.TEXT, to = "【转发】"),
            FormatOp(FormatOpType.SUFFIX, FormatTarget.TEXT, to = "（完）"),
        )
        val (t, x) = RuleFormatter.apply(ops, "标题", "内容")
        assertEquals("标题", t)
        assertEquals("【转发】内容（完）", x)
    }

    @Test
    fun `正则提取 取首个匹配片段整体替换`() {
        val op = FormatOp(FormatOpType.REGEX_EXTRACT, FormatTarget.TEXT, from = "[0-9]+")
        val (t, x) = RuleFormatter.apply(listOf(op), "t", "订单 8812345 已确认，回执 77")
        assertEquals("t", t)
        assertEquals("8812345", x)
    }

    @Test
    fun `量词贪婪语义 提取最大匹配`() {
        // [0-9]{4,6} 贪婪取 6 位，是正则标准语义而非缺陷
        val op = FormatOp(FormatOpType.REGEX_EXTRACT, FormatTarget.TEXT, from = "[0-9]{4,6}")
        val (_, x) = RuleFormatter.apply(listOf(op), "t", "订单 8812345 已确认")
        assertEquals("881234", x)
    }

    @Test
    fun `提取无匹配时保持原文`() {
        val op = FormatOp(FormatOpType.REGEX_EXTRACT, FormatTarget.TEXT, from = "[0-9]{9,}")
        val (t, x) = RuleFormatter.apply(listOf(op), "t", "没有数字")
        assertEquals("没有数字", x)
    }

    @Test
    fun `非法正则不崩溃且文本原样`() {
        val op = FormatOp(FormatOpType.REGEX_REPLACE, FormatTarget.TEXT, from = "[bad", to = "x")
        val (t, x) = RuleFormatter.apply(listOf(op), "t", "原始文本")
        assertEquals("原始文本", x)
    }

    @Test
    fun `空文本不参与格式化`() {
        val op = FormatOp(FormatOpType.PREFIX, FormatTarget.TEXT, to = "p")
        val (t, x) = RuleFormatter.apply(listOf(op), "t", "")
        assertEquals("", x)
    }

    @Test
    fun `管线按顺序应用`() {
        val ops = listOf(
            FormatOp(FormatOpType.REGEX_REPLACE, FormatTarget.TEXT, from = "482913", to = "***"),
            FormatOp(FormatOpType.PREFIX, FormatTarget.TEXT, to = "[安全] "),
        )
        val (t, x) = RuleFormatter.apply(ops, "t", "验证码482913")
        assertEquals("[安全] 验证码***", x)
    }
}

class TemplateRendererTest {

    @Test
    fun `基础占位符全部替换`() {
        val out = TemplateRenderer.render(
            template = "{app}|{title}|{content}|{mobile}",
            appLabel = "微信",
            title = "新消息",
            text = "你好",
        )
        assertEquals("微信|新消息|你好|unknown", out)
    }

    @Test
    fun `时间类占位符被替换为非占位文本`() {
        val out = TemplateRenderer.render(
            template = "{date} {time} {week}",
            appLabel = "a", title = "t", text = "c",
        )
        assertEquals(false, out.contains('{'))
        assertEquals(false, out.contains('}'))
    }

    @Test
    fun `无占位符的模板原样返回`() {
        assertEquals("plain text", TemplateRenderer.render("plain text", "a", "t", "c"))
    }

    @Test
    fun `无大括号的优化路径不影响未知标记`() {
        // 含 '{' 但非已知占位符 → 保持原样
        assertEquals("{unknown}", TemplateRenderer.render("{unknown}", "a", "t", "c"))
    }
}

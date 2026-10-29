package com.pickmsg.app.service

import com.pickmsg.app.data.ActionSpec
import com.pickmsg.app.data.ActionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ActionPreviewTest {

    private fun describe(action: ActionSpec, title: String = "支付宝", text: String = "消费 ¥58") =
        ActionPreview.describe(action, appLabel = "支付宝", title = title, text = text)

    @Test
    fun `移除 分立即与延迟`() {
        assertEquals("立即从通知栏移除", describe(ActionSpec(type = ActionType.REMOVE)))
        assertEquals("延迟 30 秒后从通知栏移除", describe(ActionSpec(type = ActionType.REMOVE, delaySec = 30)))
    }

    @Test
    fun `重发 描述渲染后的标题与内容`() {
        val out = describe(
            ActionSpec(type = ActionType.REPOST, titleTemplate = "【转发】{title}", textTemplate = "{content}"),
        )
        assertEquals("重发通知 · 标题「【转发】支付宝」内容「消费 ¥58」", out)
    }

    @Test
    fun `重发 空模板回退原内容`() {
        val out = describe(ActionSpec(type = ActionType.REPOST))
        assertTrue(out.contains("支付宝") && out.contains("消费 ¥58"))
        assertFalse(out.contains("「」"))
    }

    @Test
    fun `复制与打开`() {
        assertEquals("复制到剪贴板：消费 ¥58", describe(ActionSpec(type = ActionType.COPY)))
        assertEquals("打开原通知的目标页面（等同手动点击）", describe(ActionSpec(type = ActionType.OPEN)))
    }

    @Test
    fun `webhook 请求体占位符已替换`() {
        val out = describe(
            ActionSpec(
                type = ActionType.WEBHOOK,
                url = "http://x/hook",
                method = "post",
                body = """{"c":"{content}"}""",
            ),
        )
        assertTrue(out.startsWith("POST http://x/hook"))
        assertTrue(out.contains("""{"c":"消费 ¥58"}"""))
    }

    @Test
    fun `TTS 空模板回退到应用名加标题`() {
        assertEquals("语音播报：「支付宝，支付宝」", describe(ActionSpec(type = ActionType.TTS)))
    }

    @Test
    fun `短信与邮件显示目标`() {
        assertTrue(describe(ActionSpec(type = ActionType.SMS, phoneNumbers = listOf("5554", "5556"))).contains("5554、5556"))
        assertTrue(describe(ActionSpec(type = ActionType.SMS)).contains("未填号码"))
        assertTrue(describe(ActionSpec(type = ActionType.EMAIL, emailTo = "a@b.c")).contains("a@b.c"))
    }

    @Test
    fun `手环显示次数与间隔`() {
        val out = describe(ActionSpec(type = ActionType.WRISTBAND, repeatTimes = 3, intervalSec = 2))
        assertEquals("重复提醒 3 次，间隔 2 秒", out)
    }

    @Test
    fun `长文本被截断省略`() {
        val longText = "长".repeat(200)
        val out = describe(ActionSpec(type = ActionType.COPY), title = "t", text = longText)
        assertTrue(out.endsWith("…"))
        assertTrue(out.length < longText.length)
    }
}

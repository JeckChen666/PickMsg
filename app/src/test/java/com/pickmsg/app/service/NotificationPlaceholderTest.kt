package com.pickmsg.app.service

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationPlaceholderTest {

    @Test
    fun `简体占位符命中`() {
        assertTrue(NotificationExtractor.isSensitivePlaceholder("已隐藏敏感通知内容", ""))
        assertTrue(NotificationExtractor.isSensitivePlaceholder("", "已隐藏敏感通知内容"))
    }

    @Test
    fun `繁体占位符命中`() {
        assertTrue(NotificationExtractor.isSensitivePlaceholder("已隱藏敏感通知內容", ""))
    }

    @Test
    fun `英文占位符忽略大小写命中`() {
        assertTrue(NotificationExtractor.isSensitivePlaceholder("Sensitive Notification Content Hidden", ""))
        assertTrue(NotificationExtractor.isSensitivePlaceholder("", "SENSITIVE NOTIFICATION CONTENT HIDDEN"))
    }

    @Test
    fun `占位符是子串时不算命中`() {
        // 仅完整等于才视为脱敏占位符，避免误杀真实内容
        assertFalse(NotificationExtractor.isSensitivePlaceholder("已隐藏敏感通知内容的说明", ""))
        assertFalse(NotificationExtractor.isSensitivePlaceholder("sensitive notification content hidden by system", ""))
    }

    @Test
    fun `普通内容不命中`() {
        assertFalse(NotificationExtractor.isSensitivePlaceholder("微信", "你好"))
        assertFalse(NotificationExtractor.isSensitivePlaceholder("", ""))
    }
}

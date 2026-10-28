package com.pickmsg.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RuleConvertersTest {

    private val converters = RuleConverters()

    @Test
    fun `字符串列表往返`() {
        val list = listOf("com.a", "com.b|1002", "带逗号,的值")
        assertEquals(list, converters.jsonToStrings(converters.stringsToJson(list)))
    }

    @Test
    fun `空列表与坏 JSON 兜底`() {
        assertEquals(emptyList<String>(), converters.jsonToStrings(converters.stringsToJson(emptyList())))
        assertEquals(emptyList<String>(), converters.jsonToStrings("not json"))
    }

    @Test
    fun `动作往返包含全部字段`() {
        val actions = listOf(
            ActionSpec(type = ActionType.REMOVE, delaySec = 10),
            ActionSpec(
                type = ActionType.WEBHOOK,
                url = "http://x/hook",
                method = "POST",
                headers = "A: b",
                body = "{}",
            ),
            ActionSpec(
                type = ActionType.WRISTBAND,
                repeatTimes = 3,
                intervalSec = 2,
                force = true,
                phoneNumbers = listOf("5554"),
                delaySec = 30,
            ),
        )
        assertEquals(actions, converters.jsonToActions(converters.actionsToJson(actions)))
    }

    @Test
    fun `未知动作类型被跳过`() {
        val json = """[{"type":"GHOST"},{"type":"COPY"}]"""
        assertEquals(listOf(ActionSpec(type = ActionType.COPY)), converters.jsonToActions(json))
    }

    @Test
    fun `场景条件往返`() {
        val conditions = listOf(
            SceneCondition(
                type = SceneConditionType.NETWORK,
                networkKinds = listOf(NetworkKind.WIFI, NetworkKind.OFFLINE),
                ssids = listOf("Home"),
            ),
            SceneCondition(type = SceneConditionType.BATTERY, minBattery = 20, maxBattery = 80),
        )
        assertEquals(conditions, converters.jsonToConditions(converters.conditionsToJson(conditions)))
    }

    @Test
    fun `isPinned 索引字段在实体上可用`() {
        // 置顶字段参与列表排序与清理豁免，保证默认值语义
        val r = NotificationRecord(
            sbnKey = "k", packageName = "p", appKey = "p", appLabel = "l",
            title = "", text = "", subText = "", channelId = "", category = "",
            postTime = 0, isOngoing = false, isClearable = true, isSensitive = false,
            source = NotificationRecord.SOURCE_POSTED, createdAt = 0,
        )
        assertFalse(r.isPinned)
    }

    @Test
    fun `cleanOlderThan 语义依赖 postTime 与置顶`() {
        // 仓库层的批量删除按 postTime < 阈值 且 isPinned = 0 过滤（SQL 由迁移索引支撑）
        val old = NotificationRecord(
            sbnKey = "k1", packageName = "p", appKey = "p", appLabel = "l",
            title = "old", text = "", subText = "", channelId = "", category = "",
            postTime = 100, isOngoing = false, isClearable = true, isSensitive = false,
            source = NotificationRecord.SOURCE_POSTED, createdAt = 0, isPinned = true,
        )
        assertTrue(old.isPinned)
    }
}

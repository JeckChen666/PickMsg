package com.pickmsg.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupSerializerTest {

    private fun fullRule() = RuleInfo(
        id = 0,
        name = "微信红包提醒",
        enable = true,
        timestamp = 1795831400000,
        matchAppMode = AppMatchMode.WHITELIST,
        matchApps = listOf("com.tencent.mm", "com.tencent.mm|1002"),
        matchTitleMode = MatchMode.CONTAINS_ANY,
        matchTitleKeywords = listOf("红包", "转账"),
        matchTitleKeywordsA = listOf("a1", "a2"),
        matchTitleKeywordsB = listOf("b1"),
        matchTextMode = MatchMode.REGEX_EXTRACT,
        matchTextKeywords = listOf("[0-9]{4,6}"),
        matchTextKeywordsA = emptyList(),
        matchTextKeywordsB = emptyList(),
        titleOrText = true,
        scenesIds = listOf(1, 3),
        sceneMatchMode = SceneMatchMode.ALL,
        formatOps = listOf(
            FormatOp(FormatOpType.PREFIX, FormatTarget.TITLE, to = "【红包】"),
            FormatOp(FormatOpType.REGEX_REPLACE, FormatTarget.TEXT, from = "[0-9]", to = "*"),
        ),
        actions = listOf(
            ActionSpec(type = ActionType.REMOVE, delaySec = 5),
            ActionSpec(
                type = ActionType.WEBHOOK,
                url = "http://10.0.2.2:8899/hook",
                method = "POST",
                headers = "X-Test: pickmsg",
                body = "{\"content\":\"{content}\"}",
            ),
            ActionSpec(type = ActionType.SMS, phoneNumbers = listOf("5554")),
            ActionSpec(type = ActionType.WRISTBAND, repeatTimes = 3, intervalSec = 2, force = true),
        ),
    )

    @Test
    fun `规则全字段 序列化再解析 保持一致`() {
        val original = fullRule()
        val json = BackupSerializer.serialize(listOf(original), emptyList(), emptyList())
        val parsed = BackupSerializer.parseRules(json).single()

        assertEquals(original.name, parsed.name)
        assertEquals(original.enable, parsed.enable)
        assertEquals(original.matchAppMode, parsed.matchAppMode)
        assertEquals(original.matchApps, parsed.matchApps)
        assertEquals(original.matchTitleMode, parsed.matchTitleMode)
        assertEquals(original.matchTitleKeywords, parsed.matchTitleKeywords)
        assertEquals(original.matchTitleKeywordsA, parsed.matchTitleKeywordsA)
        assertEquals(original.matchTitleKeywordsB, parsed.matchTitleKeywordsB)
        assertEquals(original.matchTextMode, parsed.matchTextMode)
        assertEquals(original.matchTextKeywords, parsed.matchTextKeywords)
        assertEquals(original.titleOrText, parsed.titleOrText)
        assertEquals(original.scenesIds, parsed.scenesIds)
        assertEquals(original.sceneMatchMode, parsed.sceneMatchMode)
        assertEquals(original.formatOps, parsed.formatOps)
        assertEquals(original.actions, parsed.actions)
    }

    @Test
    fun `默认全空规则 往返不丢失`() {
        val original = RuleInfo(name = "空规则")
        val json = BackupSerializer.serialize(listOf(original), emptyList(), emptyList())
        val parsed = BackupSerializer.parseRules(json).single()
        assertEquals(original.name, parsed.name)
        assertEquals(AppMatchMode.ALL, parsed.matchAppMode)
        assertEquals(MatchMode.ALL, parsed.matchTitleMode)
        assertEquals(emptyList<String>(), parsed.matchApps)
        assertEquals(emptyList<FormatOp>(), parsed.formatOps)
        assertEquals(emptyList<ActionSpec>(), parsed.actions)
    }

    @Test
    fun `场景与计划任务 往返`() {
        val scene = Scene(
            name = "仅WiFi时",
            enable = true,
            conditions = listOf(
                SceneCondition(
                    type = SceneConditionType.NETWORK,
                    networkKinds = listOf(NetworkKind.WIFI, NetworkKind.BLUETOOTH),
                    ssids = listOf("Home-5G"),
                ),
                SceneCondition(type = SceneConditionType.TIME_RANGE, startTime = "22:00", endTime = "07:00"),
                SceneCondition(type = SceneConditionType.WEEKDAY, weekdayMask = 0b0111111),
            ),
        )
        val task = PlanTask(
            name = "睡觉提醒",
            time = "23:00",
            weekdays = 127,
            notifyTitle = "该睡了",
            notifyContent = "放下手机",
            enabled = true,
            lastTriggerAt = 123L,
        )
        val json = BackupSerializer.serialize(emptyList(), listOf(scene), listOf(task))
        val parsedScene = BackupSerializer.parseScenes(json).single()
        assertEquals(scene.name, parsedScene.name)
        assertEquals(scene.enable, parsedScene.enable)
        assertEquals(scene.conditions, parsedScene.conditions)

        val parsedTask = BackupSerializer.parseTasks(json).single()
        assertEquals(task.name, parsedTask.name)
        assertEquals(task.time, parsedTask.time)
        assertEquals(task.weekdays, parsedTask.weekdays)
        assertEquals(task.notifyTitle, parsedTask.notifyTitle)
        assertEquals(task.enabled, parsedTask.enabled)
    }

    @Test
    fun `空集合序列化后解析为空`() {
        val json = BackupSerializer.serialize(emptyList(), emptyList(), emptyList())
        assertTrue(BackupSerializer.parseRules(json).isEmpty())
        assertTrue(BackupSerializer.parseScenes(json).isEmpty())
        assertTrue(BackupSerializer.parseTasks(json).isEmpty())
    }

    @Test
    fun `坏 JSON 与未知枚举不崩溃`() {
        assertTrue(BackupSerializer.parseRules("not json at all").isEmpty())
        assertTrue(BackupSerializer.parseScenes("").isEmpty())

        // 已知类型字段 + 未知动作类型 → 该动作被跳过
        val json = """
            {"rules":[{"name":"r","actions":[{"type":"NOT_A_TYPE"},{"type":"REMOVE","delaySec":3}]}],
             "scenes":[],"planTasks":[]}
        """.trimIndent()
        val parsed = BackupSerializer.parseRules(json).single()
        assertEquals(listOf(ActionSpec(type = ActionType.REMOVE, delaySec = 3)), parsed.actions)
    }

    @Test
    fun `CSV 特殊字符转义`() {
        val record = NotificationRecord(
            sbnKey = "k", packageName = "p", appKey = "p", appLabel = "l",
            title = "带,逗号", text = "带\"引号\"和\n换行", subText = "",
            channelId = "", category = "", postTime = 1L,
            isOngoing = false, isClearable = true, isSensitive = false,
            source = NotificationRecord.SOURCE_POSTED, createdAt = 1L,
            ruleName = "规则A、规则B", actionResults = listOf("已移除"),
        )
        val csv = CsvBuilder.fromRecords(listOf(record))
        // 首行表头
        assertTrue(csv.startsWith("时间,应用名,包名"))
        // 标题带逗号 → 被引号包裹；内容含引号+换行 → 引号包裹且内部引号翻倍
        assertTrue(csv.contains("\"带,逗号\""))
        assertTrue(csv.contains("\"带\"\"引号\"\"和\n换行\""))
        // 顿号不触发引号包裹（仅英文逗号/引号/换行需要）
        assertTrue(csv.contains(",规则A、规则B,"))
        // 引号内的换行属于字段内容： naive 按物理行拆分会产生 3 行（这正是 CSV 需要引号语法的原因）
        assertEquals(3, csv.trim().split("\n").size)
    }
}

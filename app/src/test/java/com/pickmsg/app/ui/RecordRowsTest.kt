package com.pickmsg.app.ui

import com.pickmsg.app.data.NotificationRecord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RecordRowsTest {
    private fun record(id: Long, app: String = "a", label: String = app, rule: String = "") = NotificationRecord(
        id = id, sbnKey = "$id", packageName = app, appKey = app, appLabel = label,
        title = "消息", text = "内容", subText = "", channelId = "", category = "", postTime = id,
        isOngoing = false, isClearable = true, isSensitive = false,
        source = NotificationRecord.SOURCE_POSTED, createdAt = id, ruleName = rule,
    )

    @Test fun timeViewPreservesRepositoryOrderIncludingPinnedItems() {
        val records = listOf(record(1).copy(isPinned = true), record(9), record(4))
        assertEquals(records.map { RecordRow.Item(it) }, buildRecordRows(records, RecordGroupMode.TIME))
    }

    @Test fun interleavedAppsProduceOneHeaderPerGroupWithoutLosingRecords() {
        val rows = buildRecordRows(listOf(record(3), record(2, "b"), record(1)), RecordGroupMode.APP)
        assertEquals(listOf("APP:a", "APP:b"), rows.filterIsInstance<RecordRow.Header>().map { it.key })
        assertEquals(listOf(3L, 1L, 2L), rows.filterIsInstance<RecordRow.Item>().map { it.record.id })
        assertEquals(listOf("a · 2", "b · 1"), rows.filterIsInstance<RecordRow.Header>().map { it.text })
    }

    @Test fun sameAppLabelDoesNotCreateDuplicateKeys() {
        val rows = buildRecordRows(listOf(record(1, "a", "同名应用"), record(2, "b", "同名应用")), RecordGroupMode.APP)
        val keys = rows.filterIsInstance<RecordRow.Header>().map { it.key }
        assertEquals(2, keys.toSet().size)
    }

    @Test fun ruleGroupsIncludeUnmatchedAndMultiRuleLabels() {
        val rows = buildRecordRows(listOf(record(3, rule = "甲、乙"), record(2), record(1, rule = "甲、乙")), RecordGroupMode.RULE)
        assertEquals(listOf("甲、乙 · 2", "未命中规则 · 1"), rows.filterIsInstance<RecordRow.Header>().map { it.text })
        assertEquals(3, rows.filterIsInstance<RecordRow.Item>().size)
    }

    @Test fun allEmptyViewsHaveNoHeaders() {
        RecordGroupMode.entries.forEach { assertTrue(buildRecordRows(emptyList(), it).isEmpty()) }
    }
}

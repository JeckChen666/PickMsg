package com.pickmsg.app.ui

import com.pickmsg.app.data.NotificationRecord

internal enum class RecordGroupMode(val label: String) { TIME("按时间"), APP("按应用"), RULE("按规则") }

internal sealed interface RecordRow {
    data class Header(val key: String, val text: String) : RecordRow
    data class Item(val record: NotificationRecord) : RecordRow
}

internal fun buildRecordRows(records: List<NotificationRecord>, mode: RecordGroupMode): List<RecordRow> {
    if (mode == RecordGroupMode.TIME) return records.map { RecordRow.Item(it) }
    // Preserve first-seen group order and the repository's pinned/time order inside each group.
    return records.groupBy {
        if (mode == RecordGroupMode.APP) it.packageName else it.ruleName
    }.flatMap { (key, group) ->
        val label = if (mode == RecordGroupMode.APP) group.first().appLabel else key.ifBlank { "未命中规则" }
        listOf(RecordRow.Header("${mode.name}:$key", "$label · ${group.size}")) + group.map { RecordRow.Item(it) }
    }
}

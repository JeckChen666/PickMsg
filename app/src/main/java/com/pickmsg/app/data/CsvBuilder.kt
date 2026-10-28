package com.pickmsg.app.data

/** 记录导出 CSV（含引号/逗号/换行转义） */
object CsvBuilder {

    fun fromRecords(records: List<NotificationRecord>): String = buildString {
        appendLine("时间,应用名,包名,标题,内容,副文本,命中规则,动作结果,来源,常驻,敏感")
        records.forEach { r ->
            appendLine(
                listOf(
                    r.postTime.toString(),
                    r.appLabel,
                    r.packageName,
                    r.title,
                    r.text,
                    r.subText,
                    r.ruleName,
                    r.actionResults.joinToString("；"),
                    r.source,
                    if (r.isOngoing) "是" else "否",
                    if (r.isSensitive) "是" else "否",
                ).joinToString(",") { escape(it) },
            )
        }
    }

    private fun escape(field: String): String =
        if (field.contains(',') || field.contains('"') || field.contains('\n')) {
            "\"${field.replace("\"", "\"\"")}\""
        } else {
            field
        }
}

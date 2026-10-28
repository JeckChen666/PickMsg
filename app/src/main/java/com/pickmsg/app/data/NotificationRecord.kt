package com.pickmsg.app.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "notification_record",
    indices = [
        Index(value = ["sbnKey", "postTime"], unique = true),
        Index(value = ["postTime"]),
        Index(value = ["packageName", "postTime"]),
        Index(value = ["ruleId"]),
        Index(value = ["isPinned", "postTime"]),
    ],
)
data class NotificationRecord(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sbnKey: String,
    val packageName: String,
    /** 主用户为包名；分身/工作资料为 "包名|userHash"，避免规则串扰 */
    val appKey: String,
    val appLabel: String,
    val title: String,
    val text: String,
    val subText: String,
    val channelId: String,
    val category: String,
    val postTime: Long,
    val isOngoing: Boolean,
    val isClearable: Boolean,
    val isSensitive: Boolean,
    val source: String,
    val createdAt: Long,
    /** 命中的规则（多条命中时 ruleName 以"、"连接，ruleId 取第一条） */
    val ruleId: Long = 0,
    val ruleName: String = "",
    val actionResults: List<String> = emptyList(),
    /** 置顶的记录在列表中最先显示，且不参与过期清理 */
    val isPinned: Boolean = false,
) {
    companion object {
        const val SOURCE_POSTED = "posted"
        const val SOURCE_SNAPSHOT = "snapshot"
    }
}

data class AppCount(
    val packageName: String,
    val appLabel: String,
    val count: Int,
)

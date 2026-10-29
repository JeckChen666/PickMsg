package com.pickmsg.app.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

data class DayCount(val day: Long, val count: Int)

data class LabelCount(val label: String, val count: Int)

@Dao
interface NotificationRecordDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(record: NotificationRecord): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(records: List<NotificationRecord>): List<Long>

    @Query("SELECT * FROM notification_record ORDER BY isPinned DESC, postTime DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<NotificationRecord>>

    @Query("SELECT COUNT(*) FROM notification_record")
    fun observeCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM notification_record WHERE postTime >= :since")
    fun observeCountSince(since: Long): Flow<Int>

    @Query(
        "SELECT packageName, appLabel, COUNT(*) AS count FROM notification_record " +
            "GROUP BY packageName ORDER BY count DESC",
    )
    fun observeAppCounts(): Flow<List<AppCount>>

    /** 近 N 天每日计数（day = epochDay） */
    @Query("SELECT postTime / 86400000 AS day, COUNT(*) AS count FROM notification_record GROUP BY day ORDER BY day DESC LIMIT :days")
    fun observeDailyCounts(days: Int): Flow<List<DayCount>>

    @Query("SELECT ruleName AS label, COUNT(*) AS count FROM notification_record WHERE ruleName != '' GROUP BY ruleName ORDER BY count DESC LIMIT :limit")
    fun observeRuleCounts(limit: Int): Flow<List<LabelCount>>

    @Query(
        "DELETE FROM notification_record WHERE id IN " +
            "(SELECT id FROM notification_record WHERE postTime < :ts AND isPinned = 0 ORDER BY postTime LIMIT 1000)",
    )
    suspend fun deleteOlderBatch(ts: Long): Int

    @Query("UPDATE notification_record SET isPinned = :pinned WHERE id = :id")
    suspend fun setPinned(id: Long, pinned: Boolean)

    /** 常驻通知折叠更新：同 key 的后续 post 原地覆盖最新内容并累计更新次数 */
    @Query(
        "UPDATE notification_record SET title = :title, text = :text, subText = :subText, " +
            "postTime = :postTime, updateCount = updateCount + 1 WHERE id = :id",
    )
    suspend fun mergeOngoingUpdate(id: Long, title: String, text: String, subText: String, postTime: Long)

    @Query("SELECT id FROM notification_record WHERE sbnKey = :sbnKey ORDER BY postTime DESC LIMIT 1")
    suspend fun findIdBySbnKey(sbnKey: String): Long?

    @Query(
        "UPDATE notification_record SET ruleId = :ruleId, ruleName = :ruleName, " +
            "actionResults = :actionResults WHERE id = :id",
    )
    suspend fun updateHitInfo(id: Long, ruleId: Long, ruleName: String, actionResults: List<String>)

    @Query("DELETE FROM notification_record WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM notification_record")
    suspend fun deleteAll()
}

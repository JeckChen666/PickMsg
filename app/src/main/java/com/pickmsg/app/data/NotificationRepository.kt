package com.pickmsg.app.data

import kotlinx.coroutines.flow.Flow

class NotificationRepository(private val dao: NotificationRecordDao) {

    fun observeRecent(limit: Int = 500): Flow<List<NotificationRecord>> = dao.observeRecent(limit)

    fun observeCount(): Flow<Int> = dao.observeCount()

    fun observeCountSince(since: Long): Flow<Int> = dao.observeCountSince(since)

    fun observeAppCounts(): Flow<List<AppCount>> = dao.observeAppCounts()

    fun observeDailyCounts(days: Int = 7): Flow<List<DayCount>> = dao.observeDailyCounts(days)

    fun observeRuleCounts(limit: Int = 10): Flow<List<LabelCount>> = dao.observeRuleCounts(limit)

    /** 返回新记录 id；-1 表示同 key+postTime 的记录已存在，被唯一索引忽略 */
    suspend fun insert(record: NotificationRecord): Long = dao.insert(record)

    suspend fun insertAll(records: List<NotificationRecord>): Int =
        if (records.isEmpty()) 0 else dao.insertAll(records).count { it != -1L }

    suspend fun updateHitInfo(id: Long, ruleId: Long, ruleName: String, actionResults: List<String>) =
        dao.updateHitInfo(id, ruleId, ruleName, actionResults)

    suspend fun setPinned(id: Long, pinned: Boolean) = dao.setPinned(id, pinned)

    /** 常驻通知折叠：查同 key 的既有记录 id，无则返回 null */
    suspend fun findIdBySbnKey(sbnKey: String): Long? = dao.findIdBySbnKey(sbnKey)

    suspend fun mergeOngoingUpdate(id: Long, record: NotificationRecord) =
        dao.mergeOngoingUpdate(id, record.title, record.text, record.subText, record.postTime)

    /**
     * 分批删除 keepDays 天前、未置顶的记录；每批 1000 条。
     * 返回删除总数。
     */
    suspend fun cleanupOlderThan(keepDays: Int): Int {
        if (keepDays <= 0) return 0
        val ts = System.currentTimeMillis() - keepDays * 86_400_000L
        var total = 0
        while (true) {
            val deleted = dao.deleteOlderBatch(ts)
            total += deleted
            if (deleted < 1000) break
        }
        return total
    }

    suspend fun delete(id: Long) = dao.deleteById(id)

    suspend fun clear() = dao.deleteAll()
}

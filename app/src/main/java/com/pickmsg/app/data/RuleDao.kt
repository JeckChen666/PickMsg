package com.pickmsg.app.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface RuleDao {

    @Query("SELECT * FROM rule_info ORDER BY timestamp DESC, id DESC")
    fun observeAll(): Flow<List<RuleInfo>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(rule: RuleInfo): Long

    @Update
    suspend fun update(rule: RuleInfo)

    @Query("DELETE FROM rule_info WHERE id = :id")
    suspend fun deleteById(id: Long)
}

@Dao
interface SceneDao {

    @Query("SELECT * FROM scene ORDER BY id DESC")
    fun observeAll(): Flow<List<Scene>>

    @Query("SELECT * FROM scene")
    suspend fun getAll(): List<Scene>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(scene: Scene): Long

    @Update
    suspend fun update(scene: Scene)

    @Query("DELETE FROM scene WHERE id = :id")
    suspend fun deleteById(id: Long)
}

@Dao
interface PlanTaskDao {

    @Query("SELECT * FROM plan_task ORDER BY time ASC, id ASC")
    fun observeAll(): Flow<List<PlanTask>>

    @Query("SELECT * FROM plan_task WHERE enabled = 1")
    suspend fun getEnabled(): List<PlanTask>

    @Query("SELECT * FROM plan_task WHERE id = :id")
    suspend fun getById(id: Long): PlanTask?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(task: PlanTask): Long

    @Update
    suspend fun update(task: PlanTask)

    @Query("UPDATE plan_task SET lastTriggerAt = :at WHERE id = :id")
    suspend fun updateLastTrigger(id: Long, at: Long)

    @Query("DELETE FROM plan_task WHERE id = :id")
    suspend fun deleteById(id: Long)
}

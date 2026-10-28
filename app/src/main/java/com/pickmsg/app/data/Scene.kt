package com.pickmsg.app.data

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class SceneConditionType { NETWORK, TIME_RANGE, BATTERY, WEEKDAY, SCREEN }

/** 网络条件子类型 */
enum class NetworkKind { OFFLINE, MOBILE, WIFI, BLUETOOTH }

/**
 * 场景条件。同一场景内多个条件为"且"关系；扁平结构 + type 判别（JSON 多态的简化版）。
 * 各类型只读自己相关的字段，其余忽略。
 */
data class SceneCondition(
    val type: SceneConditionType,
    // NETWORK
    val networkKinds: List<NetworkKind> = emptyList(),
    /** 仅 WIFI 时生效；为空 = 任意 Wi-Fi */
    val ssids: List<String> = emptyList(),
    // TIME_RANGE，"HH:mm"
    val startTime: String = "00:00",
    val endTime: String = "23:59",
    // BATTERY，百分比区间
    val minBattery: Int = 0,
    val maxBattery: Int = 100,
    // WEEKDAY 位掩码：bit0=周一 … bit6=周日，127=每天
    val weekdayMask: Int = 127,
    // SCREEN
    val screenOn: Boolean = true,
    val landscape: Boolean = false,
)

@Entity(tableName = "scene")
data class Scene(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String = "",
    val enable: Boolean = false,
    val conditions: List<SceneCondition> = emptyList(),
)

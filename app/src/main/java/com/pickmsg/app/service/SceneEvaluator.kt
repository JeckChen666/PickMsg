package com.pickmsg.app.service

import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.BatteryManager
import android.os.PowerManager
import com.pickmsg.app.data.NetworkKind
import com.pickmsg.app.data.Scene
import com.pickmsg.app.data.SceneCondition
import com.pickmsg.app.data.SceneConditionType
import java.util.Calendar

/**
 * 场景求值：场景内多条件"且"； Wi-Fi SSID / 蓝牙状态
 * 受系统权限限制，拿不到时按"不满足"处理。
 */
object SceneEvaluator {

    fun satisfied(context: Context, scene: Scene): Boolean =
        scene.conditions.isNotEmpty() && scene.conditions.all { satisfied(context, it) }

    fun satisfied(context: Context, condition: SceneCondition): Boolean {
        if (!conditionEnabled(condition)) return false
        return try {
            when (condition.type) {
                SceneConditionType.NETWORK -> condition.networkKinds.any { kindMatches(context, it, condition.ssids) }
                SceneConditionType.TIME_RANGE -> inTimeRange(condition.startTime, condition.endTime)
                SceneConditionType.BATTERY -> batteryPercent(context) in condition.minBattery..condition.maxBattery
                SceneConditionType.WEEKDAY -> {
                    val dow = Calendar.getInstance().get(Calendar.DAY_OF_WEEK) // SUNDAY=1
                    val bit = if (dow == Calendar.SUNDAY) 6 else dow - 2 // bit0=周一…bit6=周日
                    (condition.weekdayMask shr bit) and 1 == 1
                }
                SceneConditionType.SCREEN -> screenMatches(context, condition)
            }
        } catch (e: Exception) {
            EventLog.log("⚠️ 场景条件求值失败(${condition.type}): ${e.message}")
            false
        }
    }

    private fun conditionEnabled(condition: SceneCondition): Boolean = when (condition.type) {
        SceneConditionType.NETWORK -> condition.networkKinds.isNotEmpty()
        else -> true
    }

    private fun kindMatches(context: Context, kind: NetworkKind, ssids: List<String>): Boolean = when (kind) {
        NetworkKind.OFFLINE -> currentKinds(context).contains(NetworkKind.OFFLINE)
        NetworkKind.MOBILE -> currentKinds(context).contains(NetworkKind.MOBILE)
        NetworkKind.WIFI -> currentKinds(context).contains(NetworkKind.WIFI) && ssidMatches(context, ssids)
        NetworkKind.BLUETOOTH -> bluetoothEnabled(context)
    }

    /**
     * 按 transport 判断当前接入类型；不看 VALIDATED——
     * 门户 Wi-Fi（部分连接）在现实里很常见，用户"连上了 Wi-Fi"的场景应该命中。
     * 仅当完全没有活动网络时才算离线。
     */
    private fun currentKinds(context: Context): Set<NetworkKind> {
        val cm = context.getSystemService(ConnectivityManager::class.java) ?: return setOf(NetworkKind.OFFLINE)
        val network = cm.activeNetwork ?: return setOf(NetworkKind.OFFLINE)
        val caps = cm.getNetworkCapabilities(network) ?: return setOf(NetworkKind.OFFLINE)
        return when {
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> setOf(NetworkKind.WIFI)
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> setOf(NetworkKind.MOBILE)
            else -> setOf(NetworkKind.OFFLINE)
        }
    }

    private fun ssidMatches(context: Context, ssids: List<String>): Boolean {
        if (ssids.isEmpty()) return true
        return try {
            val wifi = context.applicationContext.getSystemService(android.net.wifi.WifiManager::class.java)
            val current = wifi?.connectionInfo?.ssid?.removeSurrounding("\"").orEmpty()
            ssids.any { current.equals(it.removeSurrounding("\""), ignoreCase = true) }
        } catch (e: Exception) {
            false
        }
    }

    private fun bluetoothEnabled(context: Context): Boolean = try {
        val bm = context.getSystemService(BluetoothManager::class.java)
        bm?.adapter?.isEnabled == true
    } catch (e: SecurityException) {
        false
    }

    /** 支持跨天区间（如 22:00-07:00） */
    private fun inTimeRange(start: String, end: String): Boolean {
        val nowMinutes = Calendar.getInstance().let { it.get(Calendar.HOUR_OF_DAY) * 60 + it.get(Calendar.MINUTE) }
        val startMinutes = parseMinutes(start)
        val endMinutes = parseMinutes(end)
        return if (startMinutes <= endMinutes) {
            nowMinutes in startMinutes..endMinutes
        } else {
            nowMinutes >= startMinutes || nowMinutes <= endMinutes
        }
    }

    private fun parseMinutes(text: String): Int {
        val parts = text.split(':')
        val h = parts.getOrNull(0)?.trim()?.toIntOrNull() ?: 0
        val m = parts.getOrNull(1)?.trim()?.toIntOrNull() ?: 0
        return h * 60 + m
    }

    private fun batteryPercent(context: Context): Int {
        val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED)) ?: return -1
        val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
        if (level < 0 || scale <= 0) return -1
        return level * 100 / scale
    }

    private fun screenMatches(context: Context, condition: SceneCondition): Boolean {
        val pm = context.getSystemService(PowerManager::class.java)
        val interactive = pm?.isInteractive ?: true
        if (interactive != condition.screenOn) return false
        val landscape = context.resources.configuration.orientation ==
            android.content.res.Configuration.ORIENTATION_LANDSCAPE
        return landscape == condition.landscape
    }
}

package com.pickmsg.app.service

import android.service.quicksettings.Tile
import android.service.quicksettings.TileService

/** 快捷设置磁贴：一键暂停/恢复采集 */
class PickMsgTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        updateTile()
    }

    override fun onClick() {
        super.onClick()
        val next = !ServiceState.paused.value
        ServiceState.setPaused(next)
        EventLog.log(if (next) "⏸ 采集已暂停（磁贴）" else "▶️ 采集已恢复（磁贴）")
        NotificationCollectorService.instance?.refreshForegroundNotification()
        updateTile()
    }

    private fun updateTile() {
        val tile = qsTile ?: return
        val active = ServiceState.connected.value && !ServiceState.paused.value
        tile.state = if (active) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.subtitle = if (active) "采集中" else if (ServiceState.paused.value) "已暂停" else "未连接"
        tile.updateTile()
    }
}

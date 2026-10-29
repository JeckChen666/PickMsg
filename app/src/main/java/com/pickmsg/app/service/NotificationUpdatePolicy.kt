package com.pickmsg.app.service

/**
 * 常驻通知折叠策略：
 * 进度条/状态类常驻通知每次 notify() 更新都会产生新的 postTime，
 * 若逐条入库会导致记录疯狂堆积。因此同 key 的后续 post 判定为"更新"，
 * 原地合并到既有记录；通知被移除（出存活集合）后再次发布才视为新通知。
 * 非常驻通知（如聊天消息内容变化）保持逐条入库——内容变化本身是有意义的。
 */
object NotificationUpdatePolicy {

    enum class Action { INSERT_NEW, MERGE_IN_PLACE }

    fun resolve(isOngoing: Boolean, keyIsLive: Boolean): Action =
        if (isOngoing && keyIsLive) Action.MERGE_IN_PLACE else Action.INSERT_NEW
}

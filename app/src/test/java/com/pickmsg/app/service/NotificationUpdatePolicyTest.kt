package com.pickmsg.app.service

import org.junit.Assert.assertEquals
import org.junit.Test

class NotificationUpdatePolicyTest {

    @Test
    fun `存活中的常驻通知 折叠合并`() {
        assertEquals(
            NotificationUpdatePolicy.Action.MERGE_IN_PLACE,
            NotificationUpdatePolicy.resolve(isOngoing = true, keyIsLive = true),
        )
    }

    @Test
    fun `首次出现的常驻通知 新建记录`() {
        assertEquals(
            NotificationUpdatePolicy.Action.INSERT_NEW,
            NotificationUpdatePolicy.resolve(isOngoing = true, keyIsLive = false),
        )
    }

    @Test
    fun `非常驻通知即使 key 存活也逐条入库`() {
        // 聊天等普通通知的内容变化本身有意义，不折叠
        assertEquals(
            NotificationUpdatePolicy.Action.INSERT_NEW,
            NotificationUpdatePolicy.resolve(isOngoing = false, keyIsLive = true),
        )
        assertEquals(
            NotificationUpdatePolicy.Action.INSERT_NEW,
            NotificationUpdatePolicy.resolve(isOngoing = false, keyIsLive = false),
        )
    }
}

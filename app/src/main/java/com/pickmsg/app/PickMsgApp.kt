package com.pickmsg.app

import android.app.Application
import android.content.Context
import com.pickmsg.app.data.NotificationRepository
import com.pickmsg.app.data.PickMsgDatabase
import com.pickmsg.app.data.SettingsStore
import com.pickmsg.app.service.ServiceState

class PickMsgApp : Application() {

    val database: PickMsgDatabase by lazy { PickMsgDatabase.create(this) }
    val repository: NotificationRepository by lazy { NotificationRepository(database.notificationRecordDao()) }

    override fun onCreate() {
        super.onCreate()
        ServiceState.init(this)
        SettingsStore.init(this)
    }

    companion object {
        fun from(context: Context): PickMsgApp = context.applicationContext as PickMsgApp
    }
}

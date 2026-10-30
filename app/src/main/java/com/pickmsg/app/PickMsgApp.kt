package com.pickmsg.app

import android.app.Application
import android.content.Context
import com.pickmsg.app.data.NotificationRepository
import com.pickmsg.app.data.PickMsgDatabase
import com.pickmsg.app.data.SettingsStore
import com.pickmsg.app.service.ServiceState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class PickMsgApp : Application() {

    val database: PickMsgDatabase by lazy { PickMsgDatabase.create(this) }
    val repository: NotificationRepository by lazy { NotificationRepository(database.notificationRecordDao()) }

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        ServiceState.init(this)
        SettingsStore.init(this)
        appScope.launch { PresetRules.seedIfNeeded(this@PickMsgApp) }
    }

    companion object {
        fun from(context: Context): PickMsgApp = context.applicationContext as PickMsgApp
    }
}

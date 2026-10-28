package com.pickmsg.app.service

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object ServiceState {

    private const val PREFS = "pickmsg_service"
    private const val KEY_PAUSED = "paused"

    private lateinit var prefs: SharedPreferences

    private val _connected = MutableStateFlow(false)

    /** 监听服务与系统的绑定状态，由 onListenerConnected/Disconnected 维护 */
    val connected: StateFlow<Boolean> = _connected.asStateFlow()

    private val _paused = MutableStateFlow(false)

    /** 暂停 ≠ 断开：暂停时服务保持连接但不再入库 */
    val paused: StateFlow<Boolean> = _paused.asStateFlow()

    fun init(context: Context) {
        prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        _paused.value = prefs.getBoolean(KEY_PAUSED, false)
    }

    fun setConnected(value: Boolean) {
        _connected.value = value
    }

    fun setPaused(value: Boolean) {
        _paused.value = value
        prefs.edit().putBoolean(KEY_PAUSED, value).apply()
    }
}

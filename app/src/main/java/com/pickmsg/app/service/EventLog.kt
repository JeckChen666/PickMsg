package com.pickmsg.app.service

import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 进程内滚动日志，最新在前；同时落 logcat 方便 adb 排查 */
object EventLog {

    private const val TAG = "PickMsg"
    private const val MAX_ENTRIES = 200

    private val formatter = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
    private val _entries = MutableStateFlow<List<String>>(emptyList())
    val entries: StateFlow<List<String>> = _entries.asStateFlow()

    @Synchronized
    fun log(message: String) {
        Log.d(TAG, message)
        _entries.value = (listOf("${formatter.format(Date())} $message") + _entries.value).take(MAX_ENTRIES)
    }
}

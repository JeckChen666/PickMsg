package com.pickmsg.app.ui

import android.app.Application
import android.app.AlarmManager
import android.os.Build
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.pickmsg.app.PickMsgApp
import com.pickmsg.app.data.PlanTask
import com.pickmsg.app.service.PlanTaskScheduler
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class PlanViewModel(application: Application) : AndroidViewModel(application) {

    private val app = PickMsgApp.from(application)
    private val dao = app.database.planTaskDao()

    val tasks: StateFlow<List<PlanTask>> = dao.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _exactAlarmAllowed = MutableStateFlow(exactAlarmStatus(application))
    val exactAlarmAllowed: StateFlow<Boolean> = _exactAlarmAllowed.asStateFlow()

    fun refreshExactAlarm() {
        _exactAlarmAllowed.value = exactAlarmStatus(getApplication())
    }

    fun save(task: PlanTask) {
        viewModelScope.launch {
            dao.upsert(task)
            PlanTaskScheduler.rescheduleAll(getApplication())
        }
    }

    fun toggleEnable(task: PlanTask) {
        viewModelScope.launch {
            dao.update(task.copy(enabled = !task.enabled))
            PlanTaskScheduler.rescheduleAll(getApplication())
        }
    }

    fun delete(task: PlanTask) {
        viewModelScope.launch {
            PlanTaskScheduler.cancel(getApplication(), task.id)
            dao.deleteById(task.id)
        }
    }

    companion object {
        fun exactAlarmStatus(context: Application): Boolean =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                context.getSystemService(AlarmManager::class.java)?.canScheduleExactAlarms() ?: false
            } else {
                true
            }
    }
}

package com.pickmsg.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.pickmsg.app.PickMsgApp
import com.pickmsg.app.data.Scene
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ScenesViewModel(application: Application) : AndroidViewModel(application) {

    private val dao = PickMsgApp.from(application).database.sceneDao()

    val scenes: StateFlow<List<Scene>> = dao.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun save(scene: Scene) {
        viewModelScope.launch { dao.upsert(scene) }
    }

    fun toggleEnable(scene: Scene) {
        viewModelScope.launch { dao.update(scene.copy(enable = !scene.enable)) }
    }

    fun delete(scene: Scene) {
        viewModelScope.launch { dao.deleteById(scene.id) }
    }
}

package com.pickmsg.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.pickmsg.app.ui.MainScreen
import com.pickmsg.app.ui.MainViewModel
import com.pickmsg.app.ui.MineViewModel
import com.pickmsg.app.ui.PlanViewModel
import com.pickmsg.app.ui.RulesViewModel
import com.pickmsg.app.ui.ScenesViewModel
import com.pickmsg.app.ui.theme.PickMsgTheme

class MainActivity : ComponentActivity() {

    private val recordsViewModel: MainViewModel by viewModels()
    private val rulesViewModel: RulesViewModel by viewModels()
    private val scenesViewModel: ScenesViewModel by viewModels()
    private val planViewModel: PlanViewModel by viewModels()
    private val mineViewModel: MineViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            PickMsgTheme {
                MainScreen(recordsViewModel, rulesViewModel, scenesViewModel, planViewModel, mineViewModel)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        recordsViewModel.refreshListenerEnabled()
        mineViewModel.refreshListenerEnabled()
    }
}

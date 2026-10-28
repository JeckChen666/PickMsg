package com.pickmsg.app.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.pickmsg.app.PickMsgApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * 2x2 桌面小组件：今日通知数 + 通知最多的应用。
 */
class PickMsgWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val app = PickMsgApp.from(context)
        val startOfToday = java.util.Calendar.getInstance().apply {
            set(java.util.Calendar.HOUR_OF_DAY, 0)
            set(java.util.Calendar.MINUTE, 0)
            set(java.util.Calendar.SECOND, 0)
            set(java.util.Calendar.MILLISECOND, 0)
        }.timeInMillis
        val todayCount = app.repository.observeCountSince(startOfToday).first()
        val topApps = app.repository.observeAppCounts().first().take(3)

        provideContent {
            Content(todayCount, topApps.map { "${it.appLabel} ${it.count}" })
        }
    }

    @Composable
    private fun Content(todayCount: Int, topApps: List<String>) {
        Column(
            modifier = GlanceModifier.fillMaxSize().background(ColorProvider(android.graphics.Color.WHITE)).padding(10.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Text(
                "今日通知",
                style = TextStyle(color = ColorProvider(android.graphics.Color.GRAY), fontSize = 11.sp),
            )
            Text(
                "$todayCount 条",
                style = TextStyle(
                    color = ColorProvider(android.graphics.Color.BLACK),
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                ),
            )
            topApps.forEach { line ->
                Text(
                    line,
                    style = TextStyle(color = ColorProvider(android.graphics.Color.DKGRAY), fontSize = 11.sp),
                    maxLines = 1,
                )
            }
        }
    }

    companion object {
        private var lastUpdateAt = 0L

        /** 服务入库后调用；1 分钟节流，避免通知风暴频繁刷新 */
        fun tryUpdate(context: Context) {
            val now = System.currentTimeMillis()
            if (now - lastUpdateAt < 60_000) return
            lastUpdateAt = now
            CoroutineScope(Dispatchers.Default).launch {
                runCatching { PickMsgWidget().updateAll(context) }
            }
        }
    }
}

class PickMsgWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = PickMsgWidget()
}

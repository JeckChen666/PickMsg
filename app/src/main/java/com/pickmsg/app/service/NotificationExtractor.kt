package com.pickmsg.app.service

import android.app.Notification
import android.content.Context
import android.os.Bundle
import android.service.notification.StatusBarNotification
import android.view.ContextThemeWrapper
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.RemoteViews
import android.widget.TextView

/**
 * 标题/内容四层降级提取（对标竞品 nz3）：
 * 标题：EXTRA_TITLE_BIG → EXTRA_TITLE → RemoteViews 第一个 TextView
 * 内容：EXTRA_BIG_TEXT → EXTRA_TEXT_LINES（去重合并）→ EXTRA_TEXT → RemoteViews 其余 TextView
 */
object NotificationExtractor {

    data class Extracted(val title: String, val text: String, val subText: String)

    // 锁屏/隐私模式下系统会把正文抹成占位文本，命中时只记录、不当作真实内容使用
    private val SENSITIVE_PLACEHOLDERS = listOf(
        "已隐藏敏感通知内容",
        "已隱藏敏感通知內容",
        "sensitive notification content hidden",
    )

    @Suppress("DEPRECATION")
    fun extract(context: Context, sbn: StatusBarNotification): Extracted {
        val notification = sbn.notification
        val extras: Bundle? = notification.extras
        // 离屏解析代价高，只在 extras 全部取不到时才触发，标题/内容共用一次结果
        val remoteTexts by lazy(LazyThreadSafetyMode.NONE) {
            parseRemoteViews(context, notification.contentView ?: notification.bigContentView)
        }

        var title = pickLongest(readText(extras, Notification.EXTRA_TITLE_BIG), readText(extras, Notification.EXTRA_TITLE))
        if (title.isEmpty()) title = remoteTexts.firstOrNull().orEmpty()

        var text = readText(extras, Notification.EXTRA_BIG_TEXT)
        if (text.isEmpty()) text = readTextLines(extras)
        if (text.isEmpty()) text = readText(extras, Notification.EXTRA_TEXT)
        if (text.isEmpty()) text = remoteTexts.drop(1).joinToString("\n")

        return Extracted(title = title, text = text, subText = readText(extras, Notification.EXTRA_SUB_TEXT))
    }

    fun isSensitivePlaceholder(title: String, text: String): Boolean =
        SENSITIVE_PLACEHOLDERS.any { it.equals(title, ignoreCase = true) || it.equals(text, ignoreCase = true) }

    private fun readText(extras: Bundle?, key: String): String =
        extras?.getCharSequence(key)?.toString()?.trim().orEmpty()

    private fun readTextLines(extras: Bundle?): String {
        val lines = extras?.getCharSequenceArray(Notification.EXTRA_TEXT_LINES) ?: return ""
        return lines.mapNotNull { it?.toString()?.trim()?.takeIf { line -> line.isNotEmpty() } }
            .distinct()
            .joinToString("\n")
    }

    private fun pickLongest(vararg candidates: String): String =
        candidates.maxByOrNull { it.length }.orEmpty()

    private fun parseRemoteViews(context: Context, remoteViews: RemoteViews?): List<String> {
        if (remoteViews == null) return emptyList()
        return try {
            // 用系统通知栏同款主题模拟，避免 apply() 解析主题属性时崩溃
            val themed = ContextThemeWrapper(context, android.R.style.Theme_DeviceDefault)
            val container = FrameLayout(themed).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                )
            }
            collectTextViews(remoteViews.apply(themed, container))
        } catch (e: Exception) {
            EventLog.log("⚠️ RemoteViews 解析失败: ${e.javaClass.simpleName}")
            emptyList()
        }
    }

    private fun collectTextViews(view: View): List<String> {
        val result = ArrayList<String>()
        when (view) {
            is TextView -> view.text?.toString()?.trim()?.takeIf { it.isNotEmpty() }?.let(result::add)
            is ViewGroup -> for (i in 0 until view.childCount) {
                result.addAll(collectTextViews(view.getChildAt(i)))
            }
        }
        return result
    }
}

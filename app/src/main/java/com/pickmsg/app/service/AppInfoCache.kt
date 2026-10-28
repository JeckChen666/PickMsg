package com.pickmsg.app.service

import android.content.Context
import android.content.pm.PackageManager
import java.util.concurrent.ConcurrentHashMap

object AppInfoCache {

    private val labels = ConcurrentHashMap<String, String>()

    fun label(context: Context, packageName: String): String =
        labels.getOrPut(packageName) {
            try {
                val pm = context.packageManager
                pm.getApplicationLabel(pm.getApplicationInfo(packageName, 0)).toString()
            } catch (e: PackageManager.NameNotFoundException) {
                packageName
            }
        }
}

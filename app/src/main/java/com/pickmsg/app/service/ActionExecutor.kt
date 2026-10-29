package com.pickmsg.app.service

import android.app.Notification
import android.app.PendingIntent
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.speech.tts.TextToSpeech
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.pickmsg.app.R
import com.pickmsg.app.data.ActionSpec
import com.pickmsg.app.data.SettingsStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale
import java.util.concurrent.atomic.AtomicInteger

/**
 * 动作执行器：每个动作独立 try-catch，单动作失败不影响其他动作。
 * canceller 由监听服务注入（cancelNotification 是监听连接的能力）。
 */
object ActionExecutor {

    private val repostIdCounter = AtomicInteger(3000)
    private val wristbandIdCounter = AtomicInteger(5000)

    suspend fun execute(
        context: Context,
        action: ActionSpec,
        sbnKey: String,
        contentIntent: PendingIntent?,
        appLabel: String,
        title: String,
        text: String,
        canceller: suspend (String) -> Unit,
        scope: CoroutineScope,
    ): String = try {
        when (action.type) {
            com.pickmsg.app.data.ActionType.REMOVE -> {
                if (action.delaySec > 0) {
                    scope.launch {
                        delay(action.delaySec * 1000L)
                        runCatching { canceller(sbnKey) }
                            .onFailure { EventLog.log("⚠️ 延迟移除失败: ${it.message}") }
                    }
                    "已计划 ${action.delaySec}s 后移除"
                } else {
                    canceller(sbnKey)
                    "已移除通知"
                }
            }

            com.pickmsg.app.data.ActionType.REPOST -> {
                val newTitle = TemplateRenderer.render(action.titleTemplate.ifBlank { title }, appLabel, title, text)
                val newText = TemplateRenderer.render(action.textTemplate.ifBlank { text }, appLabel, title, text)
                val notification = NotificationCompat.Builder(context, NotificationCollectorService.CHANNEL_RULE_REPOST)
                    .setSmallIcon(R.drawable.ic_pickmsg_notification)
                    .setContentTitle(newTitle)
                    .setContentText(newText)
                    .setStyle(NotificationCompat.BigTextStyle().bigText(newText))
                    .setContentIntent(contentIntent)
                    .setAutoCancel(true)
                    .build()
                notify(context, repostIdCounter.incrementAndGet(), notification)
                "已重发通知"
            }

            com.pickmsg.app.data.ActionType.COPY -> {
                val clipboard = context.getSystemService(ClipboardManager::class.java)
                clipboard?.setPrimaryClip(ClipData.newPlainText("PickMsg", text))
                "已复制到剪贴板"
            }

            com.pickmsg.app.data.ActionType.OPEN -> {
                if (contentIntent != null) {
                    contentIntent.send()
                    "已打开原通知目标"
                } else {
                    "原通知无 contentIntent，跳过打开"
                }
            }

            com.pickmsg.app.data.ActionType.SOUND -> {
                val uri = if (action.soundUri.isBlank()) {
                    RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
                } else {
                    Uri.parse(action.soundUri)
                }
                val ringtone = RingtoneManager.getRingtone(context, uri)
                if (ringtone != null) {
                    ringtone.audioAttributes = AudioAttributes.Builder()
                        .setUsage(if (action.force) AudioAttributes.USAGE_ALARM else AudioAttributes.USAGE_NOTIFICATION)
                        .build()
                    ringtone.play()
                    scope.launch {
                        delay(5_000)
                        runCatching { ringtone.stop() }
                    }
                    "已播放铃声"
                } else {
                    "铃声不可用，跳过"
                }
            }

            com.pickmsg.app.data.ActionType.VIBRATE -> {
                val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
                } else {
                    @Suppress("DEPRECATION")
                    context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                }
                if (vibrator == null) {
                    "设备不支持震动"
                } else {
                    val pattern = action.vibratePattern.split(':', '，', ',')
                        .map { it.trim().toLongOrNull() ?: 0L }
                    if (pattern.isEmpty()) {
                        vibrator.vibrate(300)
                    } else {
                        vibrator.vibrate(VibrationEffect.createWaveform(pattern.toLongArray(), -1))
                    }
                    "已震动"
                }
            }

            com.pickmsg.app.data.ActionType.TTS -> {
                val spoken = TemplateRenderer.render(
                    action.ttsTemplate.ifBlank { "$appLabel，$title" },
                    appLabel, title, text,
                )
                val ok = TtsPlayer.speak(context, spoken)
                if (ok) "已语音播报" else "TTS 初始化失败，跳过播报"
            }

            com.pickmsg.app.data.ActionType.WEBHOOK -> {
                val result = WebhookSender.send(action.url, action.method, action.headers, action.body, appLabel, title, text)
                result.fold(
                    onSuccess = { "Webhook 已发送 (HTTP $it)" },
                    onFailure = { "Webhook 失败: ${it.message}" },
                )
            }

            com.pickmsg.app.data.ActionType.EMAIL -> {
                val config = SettingsStore.smtp.value
                if (!config.configured) {
                    "SMTP 未配置，跳过邮件转发（在“我的”里配置）"
                } else {
                    val to = action.emailTo.split(',', '，', '、', ';', '；').map { it.trim() }.filter { it.isNotEmpty() }
                    if (to.isEmpty()) {
                        "邮件收件人为空，跳过"
                    } else {
                        val subject = TemplateRenderer.render(action.titleTemplate.ifBlank { title }, appLabel, title, text)
                        val body = TemplateRenderer.render(action.textTemplate.ifBlank { text }, appLabel, title, text)
                        val sent = Retrier.retry("邮件转发") { MailSender.send(config, to, subject, body).getOrThrow() }
                        if (sent != null) "邮件已发送至 ${to.size} 位收件人" else "邮件发送失败（重试耗尽）"
                    }
                }
            }

            com.pickmsg.app.data.ActionType.SMS -> {
                if (action.phoneNumbers.isEmpty()) {
                    "短信号码为空，跳过"
                } else {
                    val body = TemplateRenderer.render(action.textTemplate.ifBlank { text }, appLabel, title, text)
                    val count = SmsSender.send(context, action.phoneNumbers, body)
                    "短信已提交 $count/${action.phoneNumbers.size}（发送结果见日志）"
                }
            }

            com.pickmsg.app.data.ActionType.WRISTBAND -> {
                val times = action.repeatTimes.coerceIn(1, 20)
                val bluetoothOk = try {
                    val bm = context.getSystemService(android.bluetooth.BluetoothManager::class.java)
                    bm?.adapter?.isEnabled == true
                } catch (e: SecurityException) {
                    false
                }
                if (!bluetoothOk && !action.force) {
                    "蓝牙未开启，跳过手环转发"
                } else {
                    scope.launch {
                        repeat(times) { i ->
                            val notification = NotificationCompat.Builder(
                                context,
                                NotificationCollectorService.CHANNEL_RULE_REPOST,
                            )
                                .setSmallIcon(R.drawable.ic_pickmsg_notification)
                                .setContentTitle(title.ifBlank { appLabel })
                                .setContentText(text)
                                .setContentIntent(contentIntent)
                                .setAutoCancel(true)
                                .build()
                            val id = wristbandIdCounter.incrementAndGet()
                            notify(context, id, notification)
                            if (action.delaySec > 0) {
                                scope.launch {
                                    delay(action.delaySec * 1000L)
                                    NotificationManagerCompat.from(context).cancel(id)
                                }
                            }
                            if (i < times - 1) delay(action.intervalSec * 1000L)
                        }
                    }
                    "已按手环策略重发 ${times} 次"
                }
            }
        }
    } catch (e: Exception) {
        EventLog.log("⚠️ 动作 ${action.type} 执行失败: ${e.message}")
        "动作失败: ${e.message}"
    }

    private fun notify(context: Context, id: Int, notification: Notification) {
        try {
            NotificationManagerCompat.from(context).notify(id, notification)
        } catch (e: SecurityException) {
            EventLog.log("⚠️ 缺少 POST_NOTIFICATIONS 权限，无法发通知: ${e.message}")
            Toast.makeText(context, "缺少通知权限，无法重发", Toast.LENGTH_SHORT).show()
        }
    }
}

/** 进程内复用的 TTS 引擎，避免每次命中都冷启动 */
object TtsPlayer {

    @Volatile
    private var engine: TextToSpeech? = null

    fun speak(context: Context, text: String): Boolean {
        val tts = obtain(context) ?: return false
        return try {
            tts.speak(text, TextToSpeech.QUEUE_ADD, null, "pickmsg_${System.currentTimeMillis()}") > 0
        } catch (e: Exception) {
            EventLog.log("⚠️ TTS 播报失败: ${e.message}")
            false
        }
    }

    private fun obtain(context: Context): TextToSpeech? {
        engine?.let { return it }
        return synchronized(this) {
            engine ?: run {
                val latch = java.util.concurrent.CountDownLatch(1)
                val result = arrayOf(false)
                var created: TextToSpeech? = null
                val tts = TextToSpeech(context.applicationContext) { status ->
                    result[0] = status == TextToSpeech.SUCCESS
                    if (result[0]) {
                        created?.language = Locale.CHINA
                    }
                    latch.countDown()
                }
                created = tts
                latch.await(2, java.util.concurrent.TimeUnit.SECONDS)
                if (result[0]) {
                    engine = tts
                    tts
                } else {
                    runCatching { tts.shutdown() }
                    null
                }
            }
        }
    }
}

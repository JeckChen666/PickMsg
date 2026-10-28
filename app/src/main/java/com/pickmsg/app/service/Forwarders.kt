package com.pickmsg.app.service

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.telephony.SmsManager
import com.pickmsg.app.data.SmtpConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.util.Properties
import javax.mail.Authenticator
import javax.mail.Message
import javax.mail.PasswordAuthentication
import javax.mail.Session
import javax.mail.Transport
import javax.mail.internet.InternetAddress
import javax.mail.internet.MimeMessage

/**
 * 通用重试框架（对标竞品 iw1.m6907）：延迟表 5s/10s/15s/15s/15s，
 * 每次失败记日志，延迟表耗尽返回 null。
 */
object Retrier {

    val DEFAULT_DELAYS = listOf(5_000L, 10_000L, 15_000L, 15_000L, 15_000L)

    suspend fun <T> retry(
        mode: String,
        delays: List<Long> = DEFAULT_DELAYS,
        attempt: suspend () -> T,
    ): T? {
        for (i in delays.indices) {
            try {
                return attempt()
            } catch (e: Exception) {
                if (i == delays.lastIndex) {
                    EventLog.log("❌ $mode ${i + 1} 次尝试均失败: ${e.message}")
                    return null
                }
                EventLog.log("⚠️ $mode 第 ${i + 1} 次失败，${delays[i] / 1000}s 后重试: ${e.message}")
                delay(delays[i])
            }
        }
        return null
    }
}

/** Webhook 转发（HttpURLConnection，零依赖）；URL/headers/body 全部支持模板占位符 */
object WebhookSender {

    suspend fun send(url: String, method: String, headersText: String, bodyTemplate: String, appLabel: String, title: String, text: String): Result<Int> {
        val renderedUrl = TemplateRenderer.render(url, appLabel, title, text)
        val renderedBody = TemplateRenderer.render(bodyTemplate, appLabel, title, text)
        return withContext(Dispatchers.IO) {
            runCatching {
                val conn = URL(renderedUrl).openConnection() as HttpURLConnection
                conn.requestMethod = method.uppercase().ifBlank { "POST" }
                conn.connectTimeout = 10_000
                conn.readTimeout = 20_000
                headersText.lineSequence()
                    .map { it.trim() }
                    .filter { it.contains(':') }
                    .forEach { line ->
                        val idx = line.indexOf(':')
                        conn.setRequestProperty(line.take(idx).trim(), line.substring(idx + 1).trim())
                    }
                if (conn.requestMethod != "GET" && renderedBody.isNotBlank()) {
                    conn.doOutput = true
                    conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                    conn.outputStream.use { it.write(renderedBody.toByteArray(Charsets.UTF_8)) }
                }
                val code = conn.responseCode
                conn.disconnect()
                if (code in 200..299) Result.success(code) else Result.failure(IllegalStateException("HTTP $code"))
            }.getOrElse { Result.failure(it) }
        }
    }
}

/** 邮件转发：内嵌 JavaMail-Android 直连 SMTP（对标竞品 xc1） */
object MailSender {

    suspend fun send(config: SmtpConfig, to: List<String>, subject: String, body: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                val props = Properties().apply {
                    setProperty("mail.smtp.host", config.host)
                    setProperty("mail.smtp.port", config.port.toString())
                    setProperty("mail.smtp.auth", "true")
                    if (config.ssl) setProperty("mail.smtp.ssl.enable", "true")
                    else setProperty("mail.smtp.starttls.enable", "true")
                }
                val session = Session.getInstance(props, object : Authenticator() {
                    override fun getPasswordAuthentication() = PasswordAuthentication(config.user, config.password)
                })
                val message = MimeMessage(session).apply {
                    setFrom(InternetAddress(config.user))
                    to.filter { it.isNotBlank() }.forEach {
                        addRecipient(Message.RecipientType.TO, InternetAddress(it.trim()))
                    }
                    setSubject(subject, "UTF-8")
                    setText(body, "UTF-8")
                }
                Transport.send(message)
            }.map { }
        }
}

/** 短信转发（对标竞品 iw1/m76）：sendIntent 广播回写发送结果 */
object SmsSender {

    const val ACTION_SMS_SENT = "com.pickmsg.app.ACTION_SMS_SENT"

    fun send(context: Context, numbers: List<String>, text: String): Int {
        val manager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService(SmsManager::class.java)
        } else {
            @Suppress("DEPRECATION")
            SmsManager.getDefault()
        } ?: return 0
        var sent = 0
        numbers.forEachIndexed { index, number ->
            val sentIntent = PendingIntent.getBroadcast(
                context,
                index,
                Intent(ACTION_SMS_SENT).setPackage(context.packageName).putExtra("number", number),
                PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            try {
                if (text.length > 70) {
                    val parts = manager.divideMessage(text)
                    val intents = ArrayList(parts.map { sentIntent })
                    manager.sendMultipartTextMessage(number, null, parts, intents, null)
                } else {
                    manager.sendTextMessage(number, null, text, sentIntent, null)
                }
                sent++
            } catch (e: Exception) {
                EventLog.log("❌ 短信发送失败 ($number): ${e.message}")
            }
        }
        return sent
    }
}

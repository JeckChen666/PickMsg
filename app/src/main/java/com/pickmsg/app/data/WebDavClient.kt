package com.pickmsg.app.data

import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

/** 最小 WebDAV 客户端：PUT / GET / OPTIONS（测连接），Basic 认证，供备份上传下载 */
object WebDavClient {

    sealed interface Result<out T> {
        data class Ok<T>(val value: T) : Result<T>
        data class Err(val message: String) : Result<Nothing>
    }

    suspend fun upload(config: WebDavConfig, fileName: String, content: String): Result<Unit> =
        request(config, "${config.url.trimEnd('/')}/$fileName", "PUT", content) { it >= 200 && it < 300 }
            .let { if (it is Result.Ok) Result.Ok(Unit) else Result.Err((it as Result.Err).message) }

    suspend fun download(config: WebDavConfig, fileName: String): Result<String> =
        request(config, "${config.url.trimEnd('/')}/$fileName", "GET", null) { it >= 200 && it < 300 }
            .let { r ->
                when (r) {
                    is Result.Ok -> Result.Ok(r.value ?: "")
                    is Result.Err -> r
                }
            }

    /** OPTIONS 探测目录可达性：任何 <500 的响应都视为服务在线 */
    suspend fun test(config: WebDavConfig): Result<Unit> =
        when (val r = request(config, config.url, "OPTIONS", null) { true }) {
            is Result.Ok -> Result.Ok(Unit)
            is Result.Err -> r
        }

    private suspend fun request(
        config: WebDavConfig,
        url: String,
        method: String,
        body: String?,
        codeOk: (Int) -> Boolean,
    ): Result<String?> = withContext(Dispatchers.IO) {
        runCatching {
            val conn = URL(url).openConnection() as HttpURLConnection
            conn.requestMethod = method
            conn.connectTimeout = 10_000
            conn.readTimeout = 20_000
            val auth = "${config.user}:${config.password}"
            conn.setRequestProperty(
                "Authorization",
                "Basic " + Base64.encodeToString(auth.toByteArray(), Base64.NO_WRAP),
            )
            if (body != null) {
                conn.doOutput = true
                conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            }
            val code = conn.responseCode
            val text = runCatching { conn.inputStream?.bufferedReader()?.readText() }.getOrNull()
                ?: runCatching { conn.errorStream?.bufferedReader()?.readText() }.getOrNull()
            conn.disconnect()
            if (codeOk(code)) Result.Ok(text) else Result.Err("HTTP $code")
        }.getOrElse { Result.Err(it.message ?: it.javaClass.simpleName) }
    }
}

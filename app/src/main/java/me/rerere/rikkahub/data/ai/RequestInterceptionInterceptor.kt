package me.rerere.rikkahub.data.ai

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import me.rerere.rikkahub.data.event.AppEvent
import me.rerere.rikkahub.data.event.AppEventBus
import okhttp3.Interceptor
import okhttp3.Response
import okio.Buffer
import java.net.ConnectException

/**
 * 拦截请求日志【调试用】：在真正发起网络连接之前（位于 application interceptor 段，
 * 早于 Connect/Cache）等用户确认是否发送。取消时抛出与断网一致的 [ConnectException]。
 */
class RequestInterceptionInterceptor(
    private val eventBus: AppEventBus,
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        if (!RequestInterceptController.shouldIntercept(request.method, request.url.toString())) {
            return chain.proceed(request)
        }

        val requestBody = runCatching {
            request.body?.let { body ->
                val buffer = Buffer()
                body.writeTo(buffer)
                buffer.readUtf8()
            }
        }.getOrElse { "<${errorSummary(it)}>" }

        val decision = CompletableDeferred<Boolean>()
        runBlocking {
            eventBus.emit(
                AppEvent.RequestInterception(
                    url = request.url.toString(),
                    method = request.method,
                    headers = request.headers.toMap(),
                    body = requestBody,
                    decision = decision,
                )
            )
            if (!decision.await()) {
                throw ConnectException("Connection closed")
            }
        }
        return chain.proceed(request)
    }

    private fun errorSummary(error: Throwable): String =
        (error.message ?: error::class.simpleName ?: "Request failed").lineSequence().first().trim()

    private fun okhttp3.Headers.toMap(): Map<String, String> {
        return names().associateWith { name ->
            if (name.equals("Proxy-Authorization", ignoreCase = true)) {
                "██"
            } else {
                values(name).joinToString("\n")
            }
        }
    }
}

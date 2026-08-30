package me.rerere.rikkahub.data.ai

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import me.rerere.common.android.LogEntry

enum class RequestInterceptMode {
    OFF,
    LLM_ONLY,
    ALL,
}

fun isLlmRequest(method: String, url: String): Boolean {
    if (!method.equals("POST", ignoreCase = true)) return false
    val path = url.substringBefore('?').trimEnd('/').lowercase()
    return path.endsWith("/chat/completions") ||
        path.endsWith("/completions") ||
        path.endsWith("/responses") ||
        path.endsWith("/messages") ||
        path.endsWith("/api/chat") ||
        path.endsWith("/api/generate") ||
        path.endsWith(":generatecontent") ||
        path.endsWith(":streamgeneratecontent") ||
        path.endsWith("/chat") ||
        path.endsWith("/generate")
}

fun isLlmRequest(log: LogEntry.RequestLog): Boolean = isLlmRequest(log.method, log.url)

/**
 * 拦截请求日志【调试用】的内存开关，重启应用后恢复为 OFF。
 */
object RequestInterceptController {
    var mode by mutableStateOf(RequestInterceptMode.OFF)

    fun shouldIntercept(method: String, url: String): Boolean = when (mode) {
        RequestInterceptMode.OFF -> false
        RequestInterceptMode.ALL -> true
        RequestInterceptMode.LLM_ONLY -> isLlmRequest(method, url)
    }
}

package me.rerere.rikkahub.data.event

import kotlinx.coroutines.CompletableDeferred
import me.rerere.ai.ui.UIMessage
import kotlin.uuid.Uuid

sealed class AppEvent {
    data class Speak(val text: String) : AppEvent()
    data object OpenUsageAccessSettings : AppEvent()
    data class LocalToolAuthorization(
        val title: String,
        val decision: CompletableDeferred<Boolean>,
    ) : AppEvent()

    /** 拦截请求日志【调试用】：请求发出前等待用户确认。decision 为 true 表示确认发送。 */
    data class RequestInterception(
        val url: String,
        val method: String,
        val headers: Map<String, String>,
        val body: String?,
        val decision: CompletableDeferred<Boolean>,
    ) : AppEvent()

    /** 聊天生成过程中的流式更新，由 ChatNotificationManager 消费用于 Live Update 通知。 */
    data class ChatGenerationUpdate(
        val conversationId: Uuid,
        val lastMessage: UIMessage,
        val senderName: String,
    ) : AppEvent()

    /**
     * 聊天生成结束（完成、失败或取消）。
     * [contentPreview] 为 null 时仅取消 Live Update 通知，不发送完成通知。
     */
    data class ChatGenerationEnded(
        val conversationId: Uuid,
        val senderName: String,
        val contentPreview: String?,
    ) : AppEvent()
}

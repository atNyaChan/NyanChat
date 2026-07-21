package me.rerere.rikkahub.data.ai.transformers

import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import me.rerere.ai.core.MessageRole
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import kotlin.time.Clock

private val THINKING_REGEX = Regex("<think>([\\s\\S]*?)(?:</think>|$)", RegexOption.DOT_MATCHES_ALL)
private val CLOSING_TAG_REGEX = Regex("</think>")

// 部分供应商不会返回reasoning parts, 所以需要这个transformer
object ThinkTagTransformer : OutputMessageTransformer {
    fun parseEditedParts(parts: List<UIMessagePart>): List<UIMessagePart> {
        val now = Clock.System.now()
        return parts.flatMap { part ->
            if (part !is UIMessagePart.Text || !THINKING_REGEX.containsMatchIn(part.text)) {
                return@flatMap listOf(part)
            }

            buildList {
                var cursor = 0
                THINKING_REGEX.findAll(part.text).forEach { match ->
                    if (match.range.first > cursor) {
                        add(part.copy(text = part.text.substring(cursor, match.range.first)))
                    }
                    add(
                        UIMessagePart.Reasoning(
                            reasoning = match.groupValues.getOrNull(1)?.trim().orEmpty(),
                            createdAt = now,
                            finishedAt = now,
                        )
                    )
                    cursor = match.range.last + 1
                }
                if (cursor < part.text.length) {
                    add(part.copy(text = part.text.substring(cursor)))
                }
            }
        }
    }

    override suspend fun visualTransform(
        ctx: TransformerContext,
        messages: List<UIMessage>,
    ): List<UIMessage> {
        return messages.map { message ->
            if (message.role == MessageRole.ASSISTANT && message.hasPart<UIMessagePart.Text>()) {
                message.copy(
                    parts = message.parts.flatMap { part ->
                        if (part is UIMessagePart.Text && THINKING_REGEX.containsMatchIn(part.text)) {
                            val stripped = part.text.replace(THINKING_REGEX, "")
                            val reasoning =
                                THINKING_REGEX.find(part.text)?.groupValues?.getOrNull(1)?.trim()
                                    ?: ""
                            val hasClosingTag = CLOSING_TAG_REGEX.containsMatchIn(part.text)
                            listOf(
                                UIMessagePart.Reasoning(
                                    reasoning = reasoning,
                                    createdAt = message.createdAt.toInstant(timeZone = TimeZone.currentSystemDefault()),
                                    finishedAt = if (hasClosingTag) Clock.System.now() else null,
                                ),
                                part.copy(text = stripped),
                            )
                        } else {
                            listOf(part)
                        }
                    }
                )
            } else {
                message
            }
        }
    }

    override suspend fun onGenerationFinish(
        ctx: TransformerContext,
        messages: List<UIMessage>,
    ): List<UIMessage> {
        val now = Clock.System.now()
        return messages.map { message ->
            if (message.role == MessageRole.ASSISTANT && message.hasPart<UIMessagePart.Text>()) {
                message.copy(
                    parts = message.parts.flatMap { part ->
                        if (part is UIMessagePart.Text && THINKING_REGEX.containsMatchIn(part.text)) {
                            val stripped = part.text.replace(THINKING_REGEX, "")
                            val reasoning =
                                THINKING_REGEX.find(part.text)?.groupValues?.getOrNull(1)?.trim()
                                    ?: ""
                            listOf(
                                UIMessagePart.Reasoning(
                                    reasoning = reasoning,
                                    createdAt = message.createdAt.toInstant(timeZone = TimeZone.currentSystemDefault()),
                                    finishedAt = now,
                                ),
                                part.copy(text = stripped),
                            )
                        } else {
                            listOf(part)
                        }
                    }
                )
            } else {
                message
            }
        }
    }
}

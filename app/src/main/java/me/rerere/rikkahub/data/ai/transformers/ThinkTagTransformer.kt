package me.rerere.rikkahub.data.ai.transformers

import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import me.rerere.ai.core.MessageRole
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import kotlin.time.Clock
import kotlin.time.Instant

private val THINKING_REGEX = Regex("\\A\\s*<think>([\\s\\S]*?)(</think>|$)")
private val MID_THINKING_REGEX = Regex("<think>([\\s\\S]*?)(</think>|$)")

private fun thinkingRegex(parseMidThink: Boolean): Regex =
    if (parseMidThink) MID_THINKING_REGEX else THINKING_REGEX

// 部分供应商不会返回reasoning parts, 所以需要这个transformer
object ThinkTagTransformer : OutputMessageTransformer {
    fun parseEditedParts(
        parts: List<UIMessagePart>,
        parseMidThink: Boolean = false,
    ): List<UIMessagePart> {
        val now = Clock.System.now()
        val regex = thinkingRegex(parseMidThink)
        return parts.flatMap { part ->
            if (part !is UIMessagePart.Text || !regex.containsMatchIn(part.text)) {
                return@flatMap listOf(part)
            }

            buildList {
                var cursor = 0
                regex.findAll(part.text).forEach { match ->
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
        return messages.transformThinkTags(
            now = Clock.System.now(),
            generationFinished = false,
            parseMidThink = ctx.settings.displaySetting.parseMidThink,
        )
    }

    override suspend fun onGenerationFinish(
        ctx: TransformerContext,
        messages: List<UIMessage>,
    ): List<UIMessage> {
        return messages.transformThinkTags(
            now = Clock.System.now(),
            generationFinished = true,
            parseMidThink = ctx.settings.displaySetting.parseMidThink,
        )
    }
}

internal fun List<UIMessage>.transformThinkTags(
    now: Instant,
    generationFinished: Boolean,
    parseMidThink: Boolean,
): List<UIMessage> = map { message ->
    if (message.role != MessageRole.ASSISTANT) {
        return@map message
    }
    if (message.hasPart<UIMessagePart.Reasoning>()) {
        return@map if (generationFinished) {
            message.copy(
                parts = message.parts.map { part ->
                    if (part is UIMessagePart.Reasoning && part.finishedAt == null) {
                        part.copy(finishedAt = now)
                    } else {
                        part
                    }
                }
            )
        } else {
            message
        }
    }

    val textPartIndex = message.parts.indexOfFirst { part ->
        part is UIMessagePart.Text && part.text.isNotBlank()
    }
    val textPart = message.parts.getOrNull(textPartIndex) as? UIMessagePart.Text
        ?: return@map message
    val match = thinkingRegex(parseMidThink).find(textPart.text) ?: return@map message
    val hasClosingTag = match.groups[2]?.value == "</think>"
    val reasoning = UIMessagePart.Reasoning(
        reasoning = match.groupValues[1].trim(),
        createdAt = message.createdAt.toInstant(timeZone = TimeZone.currentSystemDefault()),
        finishedAt = if (generationFinished || hasClosingTag) now else null,
    )

    message.copy(
        parts = buildList {
            addAll(message.parts.subList(0, textPartIndex))
            if (match.range.first > 0) {
                add(textPart.copy(text = textPart.text.substring(0, match.range.first)))
            }
            add(reasoning)
            add(textPart.copy(text = textPart.text.substring(match.range.last + 1)))
            addAll(message.parts.subList(textPartIndex + 1, message.parts.size))
        }
    )
}

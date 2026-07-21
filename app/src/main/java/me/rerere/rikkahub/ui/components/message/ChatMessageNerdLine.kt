package me.rerere.rikkahub.ui.components.message

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.datetime.toJavaLocalDateTime
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.Clock02
import me.rerere.hugeicons.stroke.Download04
import me.rerere.hugeicons.stroke.Message01
import me.rerere.hugeicons.stroke.Upload02
import me.rerere.hugeicons.stroke.Zap
import me.rerere.rikkahub.ui.context.LocalSettings
import me.rerere.rikkahub.utils.formatNumber
import me.rerere.rikkahub.utils.toFixed
import java.time.Duration
import java.time.LocalDateTime

/**
 * 显示消息的技术统计信息（如 token 使用量）
 */
@Composable
fun ChatMessageNerdLine(
    message: UIMessage,
    loading: Boolean,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.5f),
) {
    val settings = LocalSettings.current.displaySetting
    val elapsedMillis by produceState(
        initialValue = message.elapsedMillis(loading),
        key1 = message.createdAt,
        key2 = message.finishedAt,
        key3 = loading,
    ) {
        if (loading) {
            while (isActive) {
                value = message.elapsedMillis(loading = true)
                delay(100)
            }
        } else {
            value = message.elapsedMillis(loading = false)
        }
    }

    ProvideTextStyle(MaterialTheme.typography.labelSmall.copy(color = color)) {
        CompositionLocalProvider(LocalContentColor provides color) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                itemVerticalAlignment = Alignment.CenterVertically,
                modifier = modifier.padding(horizontal = 4.dp),
            ) {
                val usage = message.usage
                if (settings.showTokenUsage) {
                    val characterCount = message.characterCount(includeReasoning = true)
                    val seconds = elapsedMillis / 1000f
                    val charactersPerSecond = if (elapsedMillis > 0) {
                        characterCount.toFloat() / elapsedMillis * 1000
                    } else {
                        0f
                    }
                    StatsItem(
                        icon = {
                            Icon(
                                imageVector = HugeIcons.Message01,
                                contentDescription = "Characters",
                                modifier = Modifier.size(12.dp)
                            )
                        },
                        content = {
                            Text(text = "${characterCount.formatNumber()} chars")
                        }
                    )
                    StatsItem(
                        icon = {
                            Icon(
                                imageVector = HugeIcons.Clock02,
                                contentDescription = "Duration",
                                modifier = Modifier.size(12.dp)
                            )
                        },
                        content = {
                            Text(text = "${seconds.toFixed(1)}s")
                        }
                    )
                    StatsItem(
                        icon = {
                            Icon(
                                imageVector = HugeIcons.Zap,
                                contentDescription = "Character speed",
                                modifier = Modifier.size(12.dp)
                            )
                        },
                        content = {
                            Text(text = "${charactersPerSecond.toFixed(1)} chars/s")
                        }
                    )

                    if (!loading && usage != null) {
                        // Input tokens
                        StatsItem(
                            icon = {
                                Icon(
                                    imageVector = HugeIcons.Upload02,
                                    contentDescription = "Input",
                                    tint = color,
                                    modifier = Modifier.size(12.dp)
                                )
                            },
                            content = {
                                Text(text = usage.promptTokens.formatNumber())
                                if (usage.cachedTokens > 0) {
                                    Text(text = "(${usage.cachedTokens.formatNumber()} cached)")
                                }
                            }
                        )
                        // Output tokens
                        StatsItem(
                            icon = {
                                Icon(
                                    imageVector = HugeIcons.Download04,
                                    contentDescription = "Output",
                                    modifier = Modifier.size(12.dp)
                                )
                            },
                            content = {
                                Text(text = usage.completionTokens.formatNumber())
                            }
                        )
                        // Tokens per second
                        StatsItem(
                            icon = {
                                Icon(
                                    imageVector = HugeIcons.Zap,
                                    contentDescription = "Token speed",
                                    modifier = Modifier.size(12.dp)
                                )
                            },
                            content = {
                                val tokensPerSecond = if (elapsedMillis > 0) {
                                    usage.completionTokens.toFloat() / elapsedMillis * 1000
                                } else {
                                    0f
                                }
                                Text(text = "${tokensPerSecond.toFixed(1)} tok/s")
                            }
                        )
                    }
                }
            }
        }
    }
}

internal fun UIMessage.characterCount(includeReasoning: Boolean): Int = parts.sumOf { part ->
    val text = when (part) {
        is UIMessagePart.Text -> part.text
        is UIMessagePart.Reasoning -> if (includeReasoning) part.reasoning else ""
        else -> ""
    }
    Character.codePointCount(text, 0, text.length)
}

private fun UIMessage.elapsedMillis(loading: Boolean): Long {
    val end = if (loading) {
        LocalDateTime.now()
    } else {
        finishedAt?.toJavaLocalDateTime() ?: createdAt.toJavaLocalDateTime()
    }
    return Duration.between(createdAt.toJavaLocalDateTime(), end).toMillis().coerceAtLeast(0)
}

@Composable
fun StatsItem(
    icon: @Composable () -> Unit,
    content: @Composable () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(1.dp),
    ) {
        icon()
        content()
    }
}

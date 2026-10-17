package me.rerere.rikkahub.ui.components.message

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.clickable
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.datetime.toJavaLocalDateTime
import me.rerere.ai.core.TokenUsage
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import me.rerere.ai.provider.Model
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.Clock02
import me.rerere.hugeicons.stroke.Download04
import me.rerere.hugeicons.stroke.Message01
import me.rerere.hugeicons.stroke.Repeat
import me.rerere.hugeicons.stroke.Upload02
import me.rerere.hugeicons.stroke.Zap
import me.rerere.rikkahub.ui.context.LocalSettings
import me.rerere.rikkahub.utils.formatNumber
import me.rerere.rikkahub.utils.toFixed
import me.rerere.rikkahub.utils.wordCount
import java.time.Duration
import java.time.LocalDateTime

/**
 * 显示消息的技术统计信息（如 token 使用量）
 */
@Composable
fun ChatMessageNerdLine(
    message: UIMessage,
    loading: Boolean,
    model: Model?,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.5f),
    round: Int? = null,
) {
    val settings = LocalSettings.current.displaySetting
    var expanded by remember(message.id) { mutableStateOf(false) }
    // 多轮工具调用时 usage 与生成耗时是各次模型调用的累计
    val usage = message.usage
    // 旧消息没有记录生成耗时，退回用创建到完成的总时长估算
    val generationMillis = message.generationTimeMs
    val latestMessage by rememberUpdatedState(message)
    val wordCount by produceState(
        initialValue = message.wordCount(includeReasoning = true),
        key1 = message.id,
        key2 = loading,
    ) {
        if (loading) {
            while (isActive) {
                value = latestMessage.wordCount(includeReasoning = true)
                delay(100)
            }
        } else {
            value = latestMessage.wordCount(includeReasoning = true)
        }
    }
    val elapsedMillis by produceState(
        initialValue = message.elapsedMillis(loading),
        key1 = message.createdAt,
        key2 = message.finishedAt,
        key3 = loading,
    ) {
        if (loading) {
            while (isActive) {
                value = latestMessage.elapsedMillis(loading = true)
                delay(100)
            }
        } else {
            value = latestMessage.elapsedMillis(loading = false)
        }
    }

    ProvideTextStyle(MaterialTheme.typography.labelSmall.copy(color = color)) {
        CompositionLocalProvider(LocalContentColor provides color) {
            Column(
                modifier = modifier
                    .clickable(
                        enabled = wordCount > 10_000 || usage?.let {
                            it.promptTokens > 10_000 || it.cachedTokens > 10_000 || it.completionTokens > 10_000
                        } == true,
                    ) { expanded = !expanded }
                    .padding(horizontal = 4.dp, vertical = 2.dp)
            ) {
                if (settings.showTokenUsage) {
                    val statsMillis = generationMillis ?: elapsedMillis
                    val seconds = statsMillis / 1000f
                    val cost = if (!loading && usage != null) calculateCost(usage, model) else null
                    val manuallyEdited = !loading && message.modelId == null
                    val showGenerationStats = loading || message.modelId != null
                    // 用过工具的消息会经历多次模型调用：有生成耗时时以它为准，
                    // 旧消息退回的创建到完成总时长包含工具执行与审批等待，word/s 会失真。
                    val usedTools = message.parts.any {
                        it is UIMessagePart.Tool || it is UIMessagePart.ServerTool
                    }
                    val statsTimeValid = generationMillis != null || !usedTools

                    run {
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            itemVerticalAlignment = Alignment.CenterVertically,
                        ) {
                            StatsItem(
                                icon = {
                                    Icon(
                                        imageVector = HugeIcons.Message01,
                                        contentDescription = "Words",
                                        modifier = Modifier.size(12.dp),
                                    )
                                },
                                content = {
                                    if (loading && wordCount == 0) {
                                        Text("waiting")
                                    } else {
                                        CountText(
                                            value = wordCount,
                                            expanded = expanded,
                                            suffix = if (manuallyEdited) " word (edited)" else " word",
                                        )
                                    }
                                },
                            )
                            if (showGenerationStats) {
                                StatsItem(
                                    icon = {
                                        Icon(
                                            imageVector = HugeIcons.Clock02,
                                            contentDescription = "Duration",
                                            modifier = Modifier.size(12.dp),
                                        )
                                    },
                                    content = { Text(text = "${seconds.toFixed(1)}s") },
                                )
                                if (statsTimeValid && statsMillis >= 150 && wordCount > 0) {
                                    val wordsPerSecond = wordCount.toFloat() / statsMillis * 1000
                                    StatsItem(
                                        icon = {
                                            Icon(
                                                imageVector = HugeIcons.Zap,
                                                contentDescription = "Word speed",
                                                modifier = Modifier.size(12.dp),
                                            )
                                        },
                                        content = { Text(text = "${wordsPerSecond.toFixed(1)} word/s") },
                                    )
                                }
                            }
                            if (!loading && round != null) {
                                StatsItem(
                                    icon = {
                                        Icon(
                                            imageVector = HugeIcons.Repeat,
                                            contentDescription = "Round",
                                            modifier = Modifier.size(12.dp),
                                        )
                                    },
                                    content = { Text(text = "round #$round") },
                                )
                            }
                        }
                    }

                    if (!loading && message.modelId != null && usage != null) {
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            itemVerticalAlignment = Alignment.CenterVertically,
                        ) {
                            // 调用过工具时向用户说明这行 token 的统计口径：旧消息只有最后一次调用的量，
                            // 新消息（有 generationTimeMs）是各次调用的累计值
                            if (usedTools) {
                                Text(text = if (generationMillis == null) "Last LLM Call:" else "Total:")
                            }
                            StatsItem(
                                icon = {
                                    Icon(
                                        imageVector = HugeIcons.Upload02,
                                        contentDescription = "Input",
                                        modifier = Modifier.size(12.dp),
                                    )
                                },
                                content = {
                                    CountText(usage.promptTokens, expanded, " token")
                                    if (usage.cachedTokens > 0) {
                                        Text(text = " (")
                                        CountText(usage.cachedTokens, expanded, " cached)")
                                    }
                                },
                            )
                            StatsItem(
                                icon = {
                                    Icon(
                                        imageVector = HugeIcons.Download04,
                                        contentDescription = "Output",
                                        modifier = Modifier.size(12.dp),
                                    )
                                },
                                content = { CountText(usage.completionTokens, expanded, " token") },
                            )
                            if (statsTimeValid && statsMillis >= 150) {
                                val tokensPerSecond = usage.completionTokens.toFloat() / statsMillis * 1000
                                StatsItem(
                                    icon = {
                                        Icon(
                                            imageVector = HugeIcons.Zap,
                                            contentDescription = "Token speed",
                                            modifier = Modifier.size(12.dp),
                                        )
                                    },
                                    content = { Text(text = "${tokensPerSecond.toFixed(1)} tok/s") },
                                )
                            }
                            cost?.let {
                                Text(text = "\$${formatCost(it)}")
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun calculateCost(usage: TokenUsage, model: Model?): Double? {
    val price = model?.price ?: return null
    val inputCost = if (usage.cachedTokens > 0) {
        price.cacheWrite * (usage.promptTokens - usage.cachedTokens).coerceAtLeast(0) +
            price.cacheRead * usage.cachedTokens
    } else {
        price.input * usage.promptTokens
    }
    return (inputCost + price.output * usage.completionTokens) / 1_000_000.0
}

@Composable
private fun CountText(
    value: Int,
    expanded: Boolean,
    suffix: String = "",
) {
    Text(text = (if (expanded) value.toString() else value.formatNumber()) + suffix)
}

@Composable
fun ExpandableCountStatsItem(
    value: Int,
    suffix: String = "",
    icon: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember(value) { mutableStateOf(false) }
    val clickableModifier = if (value > 10000) {
        Modifier.clickable { expanded = !expanded }
    } else {
        Modifier
    }
    StatsItem(
        modifier = modifier
            .then(clickableModifier)
            .padding(horizontal = 4.dp, vertical = 3.dp),
        icon = icon,
        content = { CountText(value, expanded, suffix) },
    )
}

private fun formatCost(cost: Double): String = when {
    cost == 0.0 -> "0"
    cost >= 0.01 -> "%.4f".format(java.util.Locale.US, cost)
    else -> "%.6f".format(java.util.Locale.US, cost)
}.trimEnd('0').trimEnd('.')

internal fun UIMessage.wordCount(includeReasoning: Boolean): Int = parts.sumOf { part ->
    val text = when (part) {
        is UIMessagePart.Text -> part.text
        is UIMessagePart.Reasoning -> if (includeReasoning) part.reasoning else ""
        else -> ""
    }
    text.wordCount()
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
    modifier: Modifier = Modifier,
    icon: @Composable () -> Unit,
    content: @Composable () -> Unit,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(1.dp),
    ) {
        icon()
        content()
    }
}

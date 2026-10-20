package me.rerere.rikkahub.ui.components.message

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.datetime.toJavaLocalDateTime
import me.rerere.ai.core.TokenUsage
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import me.rerere.ai.provider.Model
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.Clock02
import me.rerere.hugeicons.stroke.Download04
import me.rerere.hugeicons.stroke.Layers01
import me.rerere.hugeicons.stroke.Message01
import me.rerere.hugeicons.stroke.Repeat
import me.rerere.hugeicons.stroke.Upload02
import me.rerere.hugeicons.stroke.Zap
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.datastore.findModelById
import me.rerere.rikkahub.ui.context.LocalSettings
import me.rerere.rikkahub.ui.theme.rememberScreenEdgeCornerShape
import me.rerere.rikkahub.utils.formatNumber
import me.rerere.rikkahub.utils.toFixed
import me.rerere.rikkahub.utils.wordCount
import java.text.NumberFormat
import java.time.Duration
import java.time.LocalDateTime

/**
 * 显示消息的技术统计信息（如 token 使用量）
 *
 * 简洁模式只显示步数、速度、上下文占用，点开看详情；详细模式显示本轮的全部统计。
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
    if (!LocalSettings.current.displaySetting.showTokenUsage) return
    if (LocalSettings.current.displaySetting.detailedTokenUsage) {
        DetailedNerdLine(
            message = message,
            loading = loading,
            model = model,
            modifier = modifier,
            color = color,
            round = round,
        )
    } else {
        ConciseNerdLine(message = message, modifier = modifier, color = color)
    }
}

/**
 * 详细统计：字数、耗时、速度、轮次、token 用量与费用，点开还能看精确数字
 */
@Composable
private fun DetailedNerdLine(
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
                                // 多轮工具调用时说明这条消息一共请求了几次模型
                                val steps = message.stepCount ?: 1
                                StatsItem(
                                    icon = {
                                        Icon(
                                            imageVector = HugeIcons.Repeat,
                                            contentDescription = "Round",
                                            modifier = Modifier.size(12.dp),
                                        )
                                    },
                                    content = {
                                        Text(
                                            text = "round #$round" + if (steps > 1) {
                                                ", $steps steps"
                                            } else {
                                                ""
                                            }
                                        )
                                    },
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

/**
 * 简洁统计：只显示步数、速度、最后一次请求的上下文占用，点开看用量详情
 *
 * 多轮工具调用时 usage 是各步累计值，行内只显示最后一次请求的上下文占用，累计值放在详情里。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ConciseNerdLine(
    message: UIMessage,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.5f),
) {
    val settings = LocalSettings.current
    val usage = message.usage ?: return
    val stats = remember(message) { message.nerdStats(usage) }
    // 消息里的模型快照不带上下文窗口，只能从设置里按 ID 找；模型被删了就不显示窗口大小
    val contextLength = remember(settings.providers, message.modelId) {
        settings.findModelById(message.modelId)?.contextLength?.takeIf { it > 0 }
    }
    val tooltipState = rememberTooltipState(isPersistent = true)
    val scope = rememberCoroutineScope()

    TooltipBox(
        positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Above),
        tooltip = {
            Surface(
                shape = rememberScreenEdgeCornerShape(),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 3.dp,
                shadowElevation = 6.dp,
            ) {
                // 与 upstream 的 RichTooltip 一致：标题用 titleMedium，正文用 bodyMedium
                ProvideTextStyle(MaterialTheme.typography.bodyMedium) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = stringResource(R.string.chat_message_usage_title),
                            style = MaterialTheme.typography.titleMedium,
                        )
                        UsageDetails(stats, contextLength)
                    }
                }
            }
        },
        state = tooltipState,
        enableUserInput = false,
        modifier = modifier,
    ) {
        ProvideTextStyle(MaterialTheme.typography.labelSmall.copy(color = color)) {
            CompositionLocalProvider(LocalContentColor provides color) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    itemVerticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { scope.launch { tooltipState.show() } }
                        .padding(horizontal = 4.dp, vertical = 2.dp),
                ) {
                    if (stats.steps > 1) {
                        StatsItem(
                            icon = {
                                StatsIcon(
                                    HugeIcons.Repeat,
                                    stringResource(R.string.chat_message_usage_step_count),
                                )
                            },
                            content = {
                                Text(text = stringResource(R.string.chat_message_usage_steps, stats.steps))
                            },
                        )
                    }
                    if (stats.tps != null) {
                        StatsItem(
                            icon = { StatsIcon(HugeIcons.Zap, stringResource(R.string.chat_message_usage_speed)) },
                            content = { Text(text = "${stats.tps.toFixed(1)} tok/s") },
                        )
                    }
                    StatsItem(
                        icon = {
                            StatsIcon(HugeIcons.Layers01, stringResource(R.string.chat_message_usage_context))
                        },
                        content = {
                            Text(
                                text = stringResource(
                                    R.string.chat_message_usage_context_short,
                                    stats.contextTokens.formatNumber(),
                                )
                            )
                            val cached = stats.lastUsage.cachedTokens
                            if (cached > 0) {
                                Text(
                                    text = stringResource(
                                        R.string.chat_message_usage_cached_short,
                                        cached.formatNumber(),
                                    )
                                )
                            }
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun UsageDetails(stats: NerdStats, contextLength: Int?) {
    Column(
        verticalArrangement = Arrangement.spacedBy(2.dp),
        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
    ) {
        val total = stats.total
        ContextUsageBar(
            lastUsage = stats.lastUsage,
            contextLength = contextLength,
            modifier = Modifier.padding(bottom = 8.dp),
        )
        if (stats.steps > 1) {
            UsageDetailRow(
                stringResource(R.string.chat_message_usage_step_count),
                stats.steps.toString(),
            )
            UsageDetailRow(
                stringResource(R.string.chat_message_usage_total_input),
                total.promptTokens.exact(),
            )
            if (total.cachedTokens > 0) {
                UsageDetailRow(
                    stringResource(R.string.chat_message_usage_total_cached),
                    total.cachedTokens.exact(),
                )
            }
            UsageDetailRow(
                stringResource(R.string.chat_message_usage_total_output),
                total.completionTokens.exact(),
            )
        }
        if (stats.tps != null) {
            UsageDetailRow(
                stringResource(R.string.chat_message_usage_speed),
                "${stats.tps.toFixed(1)} tok/s",
            )
        }
        if (stats.durationMillis != null) {
            UsageDetailRow(
                stringResource(R.string.chat_message_usage_duration),
                "${(stats.durationMillis / 1000f).toFixed(1)}s",
            )
        }
    }
}

private data class UsageSegment(val label: String, val tokens: Int, val color: Color)

/**
 * 最后一次请求的上下文占用：缓存命中、未命中的输入、输出依次排开，剩下的是窗口里的空余
 */
@Composable
private fun ContextUsageBar(
    lastUsage: TokenUsage,
    contextLength: Int?,
    modifier: Modifier = Modifier,
) {
    val colorScheme = MaterialTheme.colorScheme
    val segments = listOf(
        UsageSegment(
            stringResource(R.string.chat_message_usage_cached),
            lastUsage.cachedTokens,
            colorScheme.primary,
        ),
        UsageSegment(
            stringResource(R.string.chat_message_usage_input),
            (lastUsage.promptTokens - lastUsage.cachedTokens).coerceAtLeast(0),
            colorScheme.primary.copy(alpha = 0.45f),
        ),
        UsageSegment(
            stringResource(R.string.chat_message_usage_output),
            lastUsage.completionTokens,
            colorScheme.tertiary,
        ),
    ).filter { it.tokens > 0 }
    val contextTokens = lastUsage.promptTokens + lastUsage.completionTokens
    // 窗口未知或已经超出时，占用本身铺满整条
    val capacity = maxOf(contextLength ?: 0, contextTokens, 1)
    val hasFreeSpace = capacity > contextTokens
    val trackColor = colorScheme.surfaceContainerHighest

    Column(
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = modifier,
    ) {
        Row(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = stringResource(R.string.chat_message_usage_context),
                color = LocalContentColor.current.copy(alpha = 0.7f),
                modifier = Modifier.weight(1f),
            )
            if (contextLength != null) {
                val fraction = contextTokens.toFloat() / contextLength
                Text(
                    text = "${contextTokens.formatNumber()} / ${contextLength.formatNumber()}" +
                        " · ${(fraction * 100).toFixed(0)}%",
                    color = if (fraction >= 0.9f) colorScheme.error else Color.Unspecified,
                )
            } else {
                Text(text = contextTokens.exact())
            }
        }
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp),
        ) {
            val gap = 2.dp.toPx()
            val radius = CornerRadius(size.height / 2)
            // 每段之间留出间隔；还有空余时最后一段和空余之间也要留
            val gaps = if (hasFreeSpace) segments.size else segments.size - 1
            val available = size.width - gap * gaps
            var x = 0f
            segments.forEach { segment ->
                if (x >= size.width) return@forEach
                // 太小的段至少画成一个圆点，不然看不出来
                val width = (segment.tokens.toFloat() / capacity * available).coerceAtLeast(size.height)
                drawRoundRect(
                    color = segment.color,
                    topLeft = Offset(x, 0f),
                    size = Size(width.coerceAtMost(size.width - x), size.height),
                    cornerRadius = radius,
                )
                x += width + gap
            }
            if (hasFreeSpace && x < size.width) {
                drawRoundRect(
                    color = trackColor,
                    topLeft = Offset(x, 0f),
                    size = Size(size.width - x, size.height),
                    cornerRadius = radius,
                )
            }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            segments.forEach { segment ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .background(segment.color, CircleShape),
                    )
                    Text(
                        text = "${segment.label} ${segment.tokens.exact()}",
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            }
        }
    }
}

@Composable
private fun UsageDetailRow(label: String, value: String) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.Start),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            text = label,
            color = LocalContentColor.current.copy(alpha = 0.7f),
            modifier = Modifier.weight(1f),
        )
        Text(text = value)
    }
}

@Composable
private fun StatsIcon(imageVector: ImageVector, contentDescription: String) {
    Icon(
        imageVector = imageVector,
        contentDescription = contentDescription,
        modifier = Modifier.size(12.dp),
    )
}

private data class NerdStats(
    val total: TokenUsage,
    val lastUsage: TokenUsage,
    val steps: Int,
    val contextTokens: Int,
    val durationMillis: Long?,
    val tps: Float?,
)

private fun UIMessage.nerdStats(usage: TokenUsage): NerdStats {
    val last = lastRequestUsage() ?: usage
    // 旧消息没有记录生成耗时，退回用创建到完成的总时长估算
    val durationMillis = finishedAt?.let { finishedAt ->
        generationTimeMs ?: Duration.between(
            createdAt.toJavaLocalDateTime(),
            finishedAt.toJavaLocalDateTime(),
        ).toMillis()
    }?.takeIf { it > 0 }
    return NerdStats(
        total = usage,
        lastUsage = last,
        steps = stepCount ?: 1,
        contextTokens = last.promptTokens + last.completionTokens,
        durationMillis = durationMillis,
        tps = durationMillis?.let { usage.completionTokens.toFloat() / it * 1000 },
    )
}

private fun Int.exact(): String = NumberFormat.getIntegerInstance().format(this)

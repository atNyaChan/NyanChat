package me.rerere.rikkahub.ui.components.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemColors
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastForEachIndexed
import me.rerere.rikkahub.ui.theme.CustomColors
import me.rerere.rikkahub.ui.theme.LocalScreenCornerAdaptationEnabled
import me.rerere.rikkahub.ui.theme.LocalScreenCornerFallbackRadius
import me.rerere.rikkahub.ui.theme.LocalScreenEdgeCornerRadii
import me.rerere.rikkahub.ui.theme.ScreenEdgeCornerRadii
import me.rerere.rikkahub.ui.theme.inset

private val CardGroupScreenInset = 16.dp
internal val CardGroupItemSpacing = 2.dp
private val CardGroupInnerCorner = 4.dp

private data class CardGroupItem(
    val onClick: (() -> Unit)?,
    val onLongClick: (() -> Unit)?,
    val modifier: Modifier,
    val overlineContent: (@Composable () -> Unit)?,
    val headlineContent: @Composable () -> Unit,
    val supportingContent: (@Composable () -> Unit)?,
    val leadingContent: (@Composable () -> Unit)?,
    val trailingContent: (@Composable () -> Unit)?,
    val colors: ListItemColors?,
)

@DslMarker
private annotation class CardGroupDsl

@CardGroupDsl
interface CardGroupScope {
    fun item(
        onClick: (() -> Unit)? = null,
        onLongClick: (() -> Unit)? = null,
        modifier: Modifier = Modifier,
        overlineContent: (@Composable () -> Unit)? = null,
        supportingContent: (@Composable () -> Unit)? = null,
        leadingContent: (@Composable () -> Unit)? = null,
        trailingContent: (@Composable () -> Unit)? = null,
        colors: ListItemColors? = null,
        headlineContent: @Composable () -> Unit,
    )

    fun FormItem(
        modifier: Modifier = Modifier,
        label: @Composable () -> Unit,
        description: (@Composable () -> Unit)? = null,
        tail: (@Composable () -> Unit)? = null,
        onClick: (() -> Unit)? = null,
        content: (@Composable ColumnScope.() -> Unit)? = null,
    )
}

private class CardGroupScopeImpl : CardGroupScope {
    val items = mutableListOf<CardGroupItem>()

    override fun item(
        onClick: (() -> Unit)?,
        onLongClick: (() -> Unit)?,
        modifier: Modifier,
        overlineContent: (@Composable () -> Unit)?,
        supportingContent: (@Composable () -> Unit)?,
        leadingContent: (@Composable () -> Unit)?,
        trailingContent: (@Composable () -> Unit)?,
        colors: ListItemColors?,
        headlineContent: @Composable () -> Unit,
    ) {
        items.add(
            CardGroupItem(
                onClick = onClick,
                onLongClick = onLongClick,
                modifier = modifier,
                overlineContent = overlineContent,
                headlineContent = headlineContent,
                supportingContent = supportingContent,
                leadingContent = leadingContent,
                trailingContent = trailingContent,
                colors = colors,
            )
        )
    }

    override fun FormItem(
        modifier: Modifier,
        label: @Composable () -> Unit,
        description: (@Composable () -> Unit)?,
        tail: (@Composable () -> Unit)?,
        onClick: (() -> Unit)?,
        content: (@Composable ColumnScope.() -> Unit)?,
    ) {
        item(
            onClick = onClick,
            modifier = modifier,
            headlineContent = label,
            supportingContent = if (description != null || content != null) {
                {
                    Column(verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)) {
                        description?.invoke()
                        content?.invoke(this)
                    }
                }
            } else {
                null
            },
            trailingContent = tail,
        )
    }
}

// 带开关的项，点整行也能切换
fun CardGroupScope.switchItem(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    supportingContent: (@Composable () -> Unit)? = null,
    leadingContent: (@Composable () -> Unit)? = null,
    headlineContent: @Composable () -> Unit,
) = item(
    onClick = if (enabled) {
        { onCheckedChange(!checked) }
    } else null,
    modifier = modifier,
    supportingContent = supportingContent,
    leadingContent = leadingContent,
    trailingContent = {
        Switch(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled)
    },
    headlineContent = headlineContent,
)

// 列表项开头带圆形底色的图标
@Composable
fun CardGroupIcon(
    icon: ImageVector,
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.secondaryContainer,
    contentColor: Color = MaterialTheme.colorScheme.onSecondaryContainer,
) {
    val animatedContainerColor by animateColorAsState(
        targetValue = containerColor,
        animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
    )
    val animatedContentColor by animateColorAsState(
        targetValue = contentColor,
        animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
    )
    Box(
        modifier = modifier
            .size(40.dp)
            .background(animatedContainerColor, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = animatedContentColor,
        )
    }
}

@Composable
private fun CardGroupListItem(
    item: CardGroupItem,
    count: Int,
    index: Int,
    continueFromPrevious: Boolean,
    continueToNext: Boolean,
    screenCornerRadii: ScreenEdgeCornerRadii?,
    fallbackCorner: Dp,
) {
    val isFirst = index == 0
    val isLast = index == count - 1

    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    // 尾部控件（下拉框等）注册的点击动作，有动作时整卡可点击
    val clickActionState = remember { ItemClickActionState() }
    val itemClickable = item.onClick != null || item.onLongClick != null || clickActionState.hasAction

    val topStartCorner by animateDpAsState(
        targetValue = if (isPressed || (isFirst && !continueFromPrevious)) {
            screenCornerRadii?.start ?: fallbackCorner
        } else {
            CardGroupInnerCorner
        },
        animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
    )
    val topEndCorner by animateDpAsState(
        targetValue = if (isPressed || (isFirst && !continueFromPrevious)) {
            screenCornerRadii?.end ?: fallbackCorner
        } else {
            CardGroupInnerCorner
        },
        animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
    )
    val bottomStartCorner by animateDpAsState(
        targetValue = if (isPressed || (isLast && !continueToNext)) {
            screenCornerRadii?.start ?: fallbackCorner
        } else {
            CardGroupInnerCorner
        },
        animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
    )
    val bottomEndCorner by animateDpAsState(
        targetValue = if (isPressed || (isLast && !continueToNext)) {
            screenCornerRadii?.end ?: fallbackCorner
        } else {
            CardGroupInnerCorner
        },
        animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
    )
    val itemShape = RoundedCornerShape(
        topStart = topStartCorner,
        topEnd = topEndCorner,
        bottomStart = bottomStartCorner,
        bottomEnd = bottomEndCorner,
    )

    ListItem(
        modifier = item.modifier
            .fillMaxWidth()
            .clip(itemShape)
            .then(
                if (itemClickable) {
                    Modifier.combinedClickable(
                        interactionSource = interactionSource,
                        indication = LocalIndication.current,
                        onClick = {
                            if (item.onClick != null) item.onClick.invoke()
                            else clickActionState.invoke()
                        },
                        onLongClick = item.onLongClick,
                    )
                } else Modifier
            ),
        overlineContent = item.overlineContent,
        supportingContent = item.supportingContent,
        leadingContent = item.leadingContent,
        trailingContent = item.trailingContent?.let { trailing ->
            {
                CompositionLocalProvider(LocalItemClickActionState provides clickActionState) {
                    trailing()
                }
            }
        },
        verticalAlignment = Alignment.CenterVertically,
        shapes = ListItemDefaults.shapes(
            itemShape,
            itemShape,
            itemShape,
            itemShape,
            itemShape,
            itemShape,
        ),
        colors = item.colors ?: CustomColors.listItemColors,
    ) { item.headlineContent() }
}

@Composable
fun CardGroup(
    modifier: Modifier = Modifier,
    title: (@Composable () -> Unit)? = null,
    continueFromPrevious: Boolean = false,
    continueToNext: Boolean = false,
    cornerInset: Dp = CardGroupScreenInset,
    content: CardGroupScope.() -> Unit,
) {
    val scope = CardGroupScopeImpl()
    scope.content()
    val screenCornerRadii = if (LocalScreenCornerAdaptationEnabled.current) {
        LocalScreenEdgeCornerRadii.current?.inset(
            horizontalInset = cornerInset,
            bottomInset = cornerInset,
        )
    } else {
        null
    }

    Column(modifier = modifier) {
        if (title != null) {
            CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.primary) {
                ProvideTextStyle(MaterialTheme.typography.titleSmallEmphasized) {
                    Box(modifier = Modifier.padding(start = 4.dp, top = 8.dp, bottom = 8.dp)) {
                        title()
                    }
                }
            }
        }
        val count = scope.items.size
        scope.items.fastForEachIndexed { index, item ->
            CardGroupListItem(
                item = item,
                count = count,
                index = index,
                continueFromPrevious = continueFromPrevious,
                continueToNext = continueToNext,
                screenCornerRadii = screenCornerRadii,
                fallbackCorner = LocalScreenCornerFallbackRadius.current,
            )
            if (index != count - 1) {
                Spacer(modifier = Modifier.height(CardGroupItemSpacing))
            }
        }
    }
}

/**
 * 计算 CardGroup 内某一项的圆角形状，供行为上无法作为 CardGroup 项渲染
 * （例如需要拖动排序）但外观要与之保持一致的卡片复用。
 */
@Composable
fun rememberCardGroupItemShape(
    isFirst: Boolean,
    isLast: Boolean,
    continueFromPrevious: Boolean = false,
    continueToNext: Boolean = false,
    cornerInset: Dp = CardGroupScreenInset,
): CornerBasedShape {
    val screenCornerRadii = if (LocalScreenCornerAdaptationEnabled.current) {
        LocalScreenEdgeCornerRadii.current?.inset(
            horizontalInset = cornerInset,
            bottomInset = cornerInset,
        )
    } else {
        null
    }
    val fallbackCorner = LocalScreenCornerFallbackRadius.current
    return RoundedCornerShape(
        topStart = if (isFirst && !continueFromPrevious) {
            screenCornerRadii?.start ?: fallbackCorner
        } else CardGroupInnerCorner,
        topEnd = if (isFirst && !continueFromPrevious) {
            screenCornerRadii?.end ?: fallbackCorner
        } else CardGroupInnerCorner,
        bottomStart = if (isLast && !continueToNext) {
            screenCornerRadii?.start ?: fallbackCorner
        } else CardGroupInnerCorner,
        bottomEnd = if (isLast && !continueToNext) {
            screenCornerRadii?.end ?: fallbackCorner
        } else CardGroupInnerCorner,
    )
}

@Composable
fun CardGroupRow(
    modifier: Modifier = Modifier,
    continueFromPrevious: Boolean = false,
    continueToNext: Boolean = false,
    // 为 true 时把下边缘圆角收成组内圆角，供与下方 CardGroup 拼成同一组时使用
    attachBelow: Boolean = false,
    cornerInset: Dp = CardGroupScreenInset,
    content: CardGroupScope.() -> Unit,
) {
    val scope = CardGroupScopeImpl()
    scope.content()
    val screenCornerRadii = if (LocalScreenCornerAdaptationEnabled.current) {
        LocalScreenEdgeCornerRadii.current?.inset(
            horizontalInset = cornerInset,
            bottomInset = cornerInset,
        )
    } else {
        null
    }
    val fallbackCorner = LocalScreenCornerFallbackRadius.current

    val count = scope.items.size
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min),
        horizontalArrangement = Arrangement.spacedBy(CardGroupItemSpacing),
    ) {
        scope.items.fastForEachIndexed { index, item ->
            val isFirst = index == 0
            val isLast = index == count - 1

            val interactionSource = remember { MutableInteractionSource() }
            val isPressed by interactionSource.collectIsPressedAsState()

            val topStartCorner by animateDpAsState(
                targetValue = if (isPressed || (isFirst && !continueFromPrevious)) {
                    screenCornerRadii?.start ?: fallbackCorner
                } else {
                    CardGroupInnerCorner
                },
                animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
            )
            val topEndCorner by animateDpAsState(
                targetValue = if (isPressed || (isLast && !continueToNext)) {
                    screenCornerRadii?.end ?: fallbackCorner
                } else {
                    CardGroupInnerCorner
                },
                animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
            )
            val bottomStartCorner by animateDpAsState(
                targetValue = if (isPressed || (isFirst && !continueFromPrevious && !attachBelow)) {
                    screenCornerRadii?.start ?: fallbackCorner
                } else {
                    CardGroupInnerCorner
                },
                animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
            )
            val bottomEndCorner by animateDpAsState(
                targetValue = if (isPressed || (isLast && !continueToNext && !attachBelow)) {
                    screenCornerRadii?.end ?: fallbackCorner
                } else {
                    CardGroupInnerCorner
                },
                animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
            )
            val itemShape = RoundedCornerShape(
                topStart = topStartCorner,
                topEnd = topEndCorner,
                bottomStart = bottomStartCorner,
                bottomEnd = bottomEndCorner,
            )

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clip(itemShape)
                    .background((item.colors ?: CustomColors.listItemColors).containerColor)
                    .then(
                        if (item.onClick != null) {
                            Modifier.clickable(
                                interactionSource = interactionSource,
                                indication = LocalIndication.current,
                                onClick = item.onClick,
                            )
                        } else Modifier
                    ),
                contentAlignment = Alignment.Center,
            ) {
                item.headlineContent()
            }
        }
    }
}

@Preview(showBackground = true, showSystemUi = true)
@Composable
private fun CardGroupPreview() {
    Scaffold(
        topBar = {
            LargeFlexibleTopAppBar(
                title = {
                    Text("Card Group")
                },
                colors = CustomColors.topBarColors,
            )
        },
        containerColor = CustomColors.topBarColors.containerColor,
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
        ) {
            CardGroup(
                modifier = Modifier.padding(horizontal = 16.dp),
                title = { Text("About") },
            ) {
                item(
                    headlineContent = { Text("First item") },
                )
                item(
                    headlineContent = { Text("Second item") },
                    supportingContent = { Text("Supporting text") },
                )
                item(
                    onClick = {},
                    headlineContent = { Text("Third item") },
                    trailingContent = { Text("→") },
                )
            }
        }
    }
}

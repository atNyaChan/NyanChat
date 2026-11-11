package me.rerere.rikkahub.ui.components.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/**
 * 底部抽屉内容的最大高度比例（相对抽屉可用高度）。
 */
const val BottomSheetMaxHeightFraction = 0.95f

/**
 * 底部抽屉的统一状态：默认隐藏，只允许隐藏/展开，支持拖拽关闭。
 */
@Composable
fun rememberAppBottomSheetState(): SheetState = rememberBottomSheetState(
    initialValue = SheetValue.Hidden,
    enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded),
)

/**
 * 底部抽屉的统一外壳：`surface` 底色、统一拖拽手柄与默认状态。
 *
 * 抽屉内容通常再套一层 [BottomSheetColumn]；需要自定义布局的抽屉直接把内容放进这里。
 */
@Composable
fun AppBottomSheet(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    sheetState: SheetState = rememberAppBottomSheetState(),
    dragHandle: @Composable (() -> Unit)? = { BottomSheetDefaults.DragHandle() },
    content: @Composable () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        modifier = modifier,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = dragHandle,
        content = { content() },
    )
}

/**
 * 底部抽屉内容的标准纵向布局：宽度撑满、高度按内容收缩（不超过可用高度的
 * [BottomSheetMaxHeightFraction]，[fillHeightFraction] 不为空时改为固定比例）、左右/底部留白
 * 与条目间距统一。
 *
 * 额外的布局修饰（`imePadding()`、`verticalScroll(...)` 等）通过 [modifier] 追加。
 */
@Composable
fun BottomSheetColumn(
    modifier: Modifier = Modifier,
    horizontalPadding: Dp = 16.dp,
    bottomPadding: Dp = 8.dp,
    verticalArrangement: Arrangement.Vertical = Arrangement.spacedBy(8.dp),
    horizontalAlignment: Alignment.Horizontal = Alignment.Start,
    fillHeightFraction: Float? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (fillHeightFraction != null) {
                    Modifier.fillMaxHeight(fillHeightFraction)
                } else {
                    Modifier.bottomSheetMaxHeight()
                }
            )
            .padding(horizontal = horizontalPadding)
            .padding(bottom = bottomPadding)
            .then(modifier),
        verticalArrangement = verticalArrangement,
        horizontalAlignment = horizontalAlignment,
        content = content,
    )
}

/**
 * 底部抽屉的统一标题：水平居中，字号统一为 `headlineSmall`。
 *
 * 个别保持左对齐的抽屉（例如带选择器头部的抽屉）不使用本组件。
 */
@Composable
fun BottomSheetTitle(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        style = MaterialTheme.typography.headlineSmall,
        textAlign = TextAlign.Center,
        modifier = modifier.fillMaxWidth(),
    )
}

/**
 * 让底部抽屉内容高度适应自身，并且最大不超过可用高度的 [fraction]。
 *
 * 与 `Modifier.fillMaxHeight` 不同：内容较少时抽屉不会被撑满，只有内容超过上限时才收缩到
 * 上限并可滚动。配合 `Modifier.weight(1f, fill = false)` 使用，可让列表按内容收缩、超出时再滚动。
 */
fun Modifier.bottomSheetMaxHeight(
    fraction: Float = BottomSheetMaxHeightFraction,
): Modifier = layout { measurable, constraints ->
    val maxHeight = if (constraints.hasBoundedHeight) {
        (constraints.maxHeight * fraction).roundToInt()
    } else {
        constraints.maxHeight
    }
    val minHeight = constraints.minHeight.coerceAtMost(maxHeight)
    val placeable = measurable.measure(
        constraints.copy(minHeight = minHeight, maxHeight = maxHeight)
    )
    layout(placeable.width, placeable.height) {
        placeable.place(0, 0)
    }
}

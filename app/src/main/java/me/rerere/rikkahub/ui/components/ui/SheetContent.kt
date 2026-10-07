package me.rerere.rikkahub.ui.components.ui

import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import kotlin.math.roundToInt

/**
 * 底部抽屉内容的最大高度比例（相对抽屉可用高度）。
 */
const val BottomSheetMaxHeightFraction = 0.95f

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

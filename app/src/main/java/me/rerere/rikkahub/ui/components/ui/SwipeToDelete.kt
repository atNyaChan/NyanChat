package me.rerere.rikkahub.ui.components.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.Delete01
import me.rerere.rikkahub.R

/**
 * 列表 item 向左滑动删除。
 *
 * 滑到底立即调用 [onDelete]，不做确认，调用方应提供撤销（如 Snackbar）。
 * 删除后立即把滑动状态收回未滑出位置，否则撤销恢复该条目时它可能仍带着“已滑出”状态，
 * 重新触发删除。 [shape] 传 item 自身的形状，删除背景才能和 item 对齐。
 * 删除入口仍应在 item 的操作菜单里保留一份，滑动不是唯一途径。
 */
@Composable
fun SwipeToDelete(
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
    shape: Shape = MaterialTheme.shapes.large,
    content: @Composable RowScope.() -> Unit,
) {
    val currentOnDelete by rememberUpdatedState(onDelete)
    val dismissState = rememberSwipeToDismissBoxState(initialValue = SwipeToDismissBoxValue.Settled)
    val scope = rememberCoroutineScope()

    SwipeToDismissBox(
        state = dismissState,
        enableDismissFromStartToEnd = false,
        // SwipeToDismissBox 只在真正落定到已划出方向时调用它（基于 settledValue，而非拖动途中）
        onDismiss = { value ->
            if (value == SwipeToDismissBoxValue.EndToStart) {
                currentOnDelete()
            }
            scope.launch { dismissState.snapTo(SwipeToDismissBoxValue.Settled) }
        },
        modifier = modifier,
        backgroundContent = {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.errorContainer, shape)
                    .padding(horizontal = 20.dp),
                contentAlignment = Alignment.CenterEnd,
            ) {
                Icon(
                    imageVector = HugeIcons.Delete01,
                    contentDescription = stringResource(R.string.common_delete),
                    tint = MaterialTheme.colorScheme.onErrorContainer,
                )
            }
        },
        content = content,
    )
}

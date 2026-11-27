package me.rerere.rikkahub.ui.hooks

import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.platform.LocalDensity

/**
 * 键盘弹出时把列表往下滚一段，保证输入框上方的最后一条消息不会被挡住。
 *
 * [enabled] 返回 false 时只记下当前键盘高度、不做滚动：聊天页在把某条用户消息置顶期间会关掉它，
 * 否则键盘收起时的滚动会抢走滚动动画的半路，把锚点挤下去。跳过滚动时也同步高度，
 * 再打开时才不会把攒下的位移一次滚出来。
 */
@Composable
fun ImeLazyListAutoScroller(
    lazyListState: LazyListState,
    enabled: () -> Boolean = { true },
) {
    val ime = WindowInsets.ime
    val localDensity = LocalDensity.current
    val currentEnabled by rememberUpdatedState(enabled)
    var imeHeigh by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        snapshotFlow {
            ime.getBottom(localDensity)
        }.collect { keyboardHeight ->
            if (keyboardHeight > 0) {
                if (currentEnabled()) {
                    lazyListState.scrollBy((keyboardHeight - imeHeigh).toFloat())
                }
                imeHeigh = keyboardHeight
            }
        }
    }
}

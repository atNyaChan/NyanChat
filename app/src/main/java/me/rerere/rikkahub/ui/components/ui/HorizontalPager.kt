package me.rerere.rikkahub.ui.components.ui

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.pager.PagerScope
import androidx.compose.foundation.pager.PagerState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

// 页签 pager：转发 androidx.compose.foundation.pager.HorizontalPager，只把 overscrollEffect 固定为 null，
// 让水平滑动翻页（含滑到首尾）不再有拉伸回弹；页签内列表等其它滚动区域仍保留平台拉伸效果。
@Composable
internal fun HorizontalPager(
    state: PagerState,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    pageSpacing: Dp = 0.dp,
    verticalAlignment: Alignment.Vertical = Alignment.CenterVertically,
    userScrollEnabled: Boolean = true,
    reverseLayout: Boolean = false,
    key: ((index: Int) -> Any)? = null,
    pageContent: @Composable PagerScope.(page: Int) -> Unit,
) {
    androidx.compose.foundation.pager.HorizontalPager(
        state = state,
        modifier = modifier,
        contentPadding = contentPadding,
        pageSpacing = pageSpacing,
        verticalAlignment = verticalAlignment,
        userScrollEnabled = userScrollEnabled,
        reverseLayout = reverseLayout,
        key = key,
        overscrollEffect = null,
        pageContent = pageContent,
    )
}

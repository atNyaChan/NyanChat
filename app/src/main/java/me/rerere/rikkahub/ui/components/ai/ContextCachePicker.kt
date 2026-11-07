package me.rerere.rikkahub.ui.components.ai

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.model.ContextCache
import me.rerere.rikkahub.ui.components.ui.ConnectedChoiceButtons
import me.rerere.rikkahub.ui.components.ui.ConnectedChoiceLabel
import me.rerere.rikkahub.ui.components.ui.Select

// 聊天页加号面板里用下拉框，助手设置里用连接式按钮组
@Composable
fun ContextCachePicker(
    value: ContextCache,
    onValueChange: (ContextCache) -> Unit,
    modifier: Modifier = Modifier,
) {
    Select(
        options = ContextCache.entries,
        selectedOption = value,
        onOptionSelected = onValueChange,
        optionToString = { it.label() },
        fitToOptions = true,
        modifier = modifier,
    )
}

@Composable
fun ContextCacheChoiceButtons(
    value: ContextCache,
    onValueChange: (ContextCache) -> Unit,
    modifier: Modifier = Modifier,
) {
    ConnectedChoiceButtons(
        options = ContextCache.entries,
        selected = value,
        onSelect = onValueChange,
        modifier = modifier,
    ) { option, selected ->
        ConnectedChoiceLabel(text = option.label(), selected = selected)
    }
}

@Composable
private fun ContextCache.label(): String = when (this) {
    ContextCache.OFF -> stringResource(R.string.common_off)
    ContextCache.FIVE_MINUTES -> "5min"
    ContextCache.ONE_HOUR -> "1h"
}

package me.rerere.rikkahub.ui.components.ai

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.model.ContextCache
import me.rerere.rikkahub.ui.components.ui.Select

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
        optionToString = {
            when (it) {
                ContextCache.OFF -> stringResource(R.string.common_off)
                ContextCache.FIVE_MINUTES -> "5min"
                ContextCache.ONE_HOUR -> "1h"
            }
        },
        fitToOptions = true,
        modifier = modifier,
    )
}

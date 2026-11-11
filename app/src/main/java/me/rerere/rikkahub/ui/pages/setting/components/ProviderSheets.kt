package me.rerere.rikkahub.ui.pages.setting.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import me.rerere.rikkahub.R
import me.rerere.rikkahub.ui.components.ui.AppBottomSheet
import me.rerere.rikkahub.ui.components.ui.BottomSheetColumn
import me.rerere.rikkahub.ui.components.ui.BottomSheetTitle

// 新建 / 编辑服务商的面板：标题、表单、底部的取消和确认；确认之前改动只留在面板里
@Composable
internal fun <T> ProviderEditSheet(
    title: String,
    initial: T,
    confirmText: String,
    onConfirm: (T) -> Unit,
    onDismiss: () -> Unit,
    content: @Composable (value: T, onValueChange: (T) -> Unit, modifier: Modifier) -> Unit,
) {
    var current by remember(initial) { mutableStateOf(initial) }

    AppBottomSheet(onDismissRequest = onDismiss) {
        BottomSheetColumn(
            horizontalPadding = 0.dp,
            fillHeightFraction = 0.95f,
            modifier = Modifier.imePadding(),
        ) {
            BottomSheetTitle(
                text = title,
                modifier = Modifier.padding(horizontal = 24.dp),
            )

            content(current, { current = it }, Modifier.weight(1f).padding(horizontal = 16.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.common_cancel))
                }
                Button(
                    onClick = {
                        onConfirm(current)
                        onDismiss()
                    }
                ) {
                    Text(confirmText)
                }
            }
        }
    }
}

package me.rerere.rikkahub.ui.pages.chat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import me.rerere.rikkahub.R

/**
 * 会话标题编辑弹窗：聊天页顶部标题与侧栏长按菜单共用。
 * 打开后自动聚焦输入框、把光标置于已有文本末尾并弹出键盘。
 */
@Composable
fun ConversationTitleEditDialog(
    title: String,
    onTitleChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onGenerateTitle: () -> Unit,
    onConfirm: () -> Unit,
) {
    val titleFocusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    // 光标默认落在已有文本末尾，打开弹窗后直接接着编辑
    var titleField by remember {
        mutableStateOf(TextFieldValue(title, TextRange(title.length)))
    }
    // 自动生成标题等外部更新文本时同步，并把光标重新放到末尾
    LaunchedEffect(title) {
        if (title != titleField.text) {
            titleField = TextFieldValue(title, TextRange(title.length))
        }
    }

    // 弹窗出现后直接把焦点放到标题输入框并弹出键盘
    LaunchedEffect(Unit) {
        withFrameNanos { }
        titleFocusRequester.requestFocus()
        keyboardController?.show()
    }

    AlertDialog(
        containerColor = MaterialTheme.colorScheme.surface,
        onDismissRequest = onDismiss,
        title = {
            Text(stringResource(R.string.chat_page_edit_title))
        },
        text = {
            OutlinedTextField(
                value = titleField,
                onValueChange = { newValue ->
                    val text = newValue.text.replace("\n", "").replace("\r", "")
                    titleField = newValue.copy(
                        text = text,
                        selection = TextRange(
                            newValue.selection.start.coerceIn(0, text.length),
                            newValue.selection.end.coerceIn(0, text.length),
                        ),
                    )
                    onTitleChange(text)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(titleFocusRequester),
                singleLine = false,
            )
        },
        confirmButton = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.common_cancel))
                }
                TextButton(onClick = onGenerateTitle) {
                    Text(stringResource(R.string.chat_page_auto_generate_title))
                }
                TextButton(onClick = onConfirm) {
                    Text(stringResource(R.string.common_confirm_action))
                }
            }
        },
    )
}

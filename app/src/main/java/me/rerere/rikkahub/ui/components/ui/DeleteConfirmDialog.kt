package me.rerere.rikkahub.ui.components.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import me.rerere.rikkahub.R
import me.rerere.ui.components.RikkaConfirmDialog

/**
 * 全局统一的删除确认弹窗。
 *
 * 标题为“Delete/删除 + 类型名”，正文按是否有对象名、是否为批量选择套用统一模板。
 * 各删除弹窗只需传入类型名（[type]）、可选的对象名（[name]）或数量（[count]），
 * 不要再各自拼接标题与正文。
 *
 * @param type 已本地化的类型名，例如“Chat”“Provider”。
 * @param name 对象名；为 null/空白时使用“删除这个 {type}”模板。
 * @param count 批量删除的数量；非空时使用批量模板，[pluralType] 为其复数形式。
 * @param extra 统一正文之后追加的额外段落（如文件夹、工作区、模型的补充说明）。
 */
@Composable
fun DeleteConfirmDialog(
    show: Boolean,
    type: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    name: String? = null,
    count: Int? = null,
    pluralType: String? = null,
    extra: (@Composable () -> Unit)? = null,
) {
    val message = when {
        count != null -> stringResource(R.string.delete_dialog_message_batch, count, pluralType ?: type)
        !name.isNullOrBlank() -> stringResource(R.string.delete_dialog_message_named, type, name)
        else -> stringResource(R.string.delete_dialog_message_unnamed, type)
    }
    RikkaConfirmDialog(
        show = show,
        title = stringResource(R.string.delete_dialog_title, type),
        confirmText = stringResource(R.string.common_delete),
        dismissText = stringResource(R.string.common_cancel),
        onConfirm = onConfirm,
        onDismiss = onDismiss,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(message)
            extra?.invoke()
        }
    }
}

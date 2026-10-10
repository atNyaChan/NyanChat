package me.rerere.rikkahub.ui.components.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * 让卡片尾部控件（下拉框、思考强度选择等）支持“整卡点击”。
 *
 * 布局尾部控件通过 [RegisterItemClickAction] 注册自己的点击动作；卡片检测到存在动作后
 * 整张卡片变为可点击，点击卡片即触发该动作（开关类控件已有独立的 switchItem 写法）。
 */
@Stable
internal class ItemClickActionState {
    private var action: (() -> Unit)? = null

    var hasAction by mutableStateOf(false)
        private set

    internal fun update(action: (() -> Unit)?) {
        this.action = action
        val hasActionNow = action != null
        if (hasAction != hasActionNow) {
            hasAction = hasActionNow
        }
    }

    fun invoke() {
        action?.invoke()
    }
}

internal val LocalItemClickActionState = staticCompositionLocalOf<ItemClickActionState?> { null }

/** 在卡片尾部控件中调用，注册整卡点击时应执行的动作。 */
@Composable
fun RegisterItemClickAction(action: () -> Unit) {
    val state = LocalItemClickActionState.current ?: return
    val currentAction = rememberUpdatedState(action)
    DisposableEffect(state) {
        state.update { currentAction.value() }
        onDispose { state.update(null) }
    }
}

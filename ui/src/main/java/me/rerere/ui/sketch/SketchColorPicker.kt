package me.rerere.ui.sketch

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import me.rerere.ui.common.ColorPicker

/**
 * 画笔用的调色面板：外面套一层卡片，颜色存进 [SketchState]。
 */
@Composable
internal fun SketchColorPicker(state: SketchState, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 4.dp,
    ) {
        ColorPicker(
            color = state.customColor ?: SketchDefaults.CustomColor,
            onColorChange = state::useCustom,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
        )
    }
}

package me.rerere.ui.common

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import java.util.Locale
import kotlin.math.roundToInt

private val ColorSliderThumbRadius = 14.dp

// 滑杆左侧标签（H / S / V）统一宽度，让三条滑杆对齐
private val ColorLabelWidth = 20.dp

// 色相转一圈：取色面板的色相轨道与画板的自定义颜色按钮共用同一份色带
internal val HueGradientColors = List(7) { Color.hsv(it * 60f, 1f, 1f) }

/**
 * 通用的 HSV 调色面板：色相、饱和度、明度三条滑杆加一个色号输入框。
 *
 * 三个分量各自用本地状态记着，拖动时只把颜色往外传，绝不从传进来的颜色反推——
 * 颜色调成黑白灰之后从颜色本身已经反推不出色相了，反推会让几条滑杆互相牵连。
 * 只有颜色被外部改掉（不是自己刚发出去的那个）时才重新同步本地分量。
 *
 * 同步放在 [SideEffect] 里而不是 `LaunchedEffect(color)`：侧效应和本次组合在同一帧里
 * 同步跑完，快速拖动时不会有某个「旧颜色」还在路上，等它跑到时又把滑杆拽回去
 * （颜色落到 8 位 RGB 会丢精度，反推出来的色相会跟原来差一点）。
 *
 * `enabled` 为 false 时只展示颜色、不接受交互（色号输入框与滑杆都会禁用）；
 * `header` 会插在色号输入框上方，供调用方放开关之类的控件。
 */
@Composable
fun ColorPicker(
    color: Color,
    onColorChange: (Color) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    header: (@Composable () -> Unit)? = null,
) {
    val initialHsv = remember {
        FloatArray(3).also { android.graphics.Color.colorToHSV(color.toArgb(), it) }
    }
    var hue by remember { mutableFloatStateOf(initialHsv[0]) }
    var saturation by remember { mutableFloatStateOf(initialHsv[1]) }
    var value by remember { mutableFloatStateOf(initialHsv[2]) }
    // 最近一次处理过的颜色：自己发出去的，或刚从外面同步进来的。
    // 不参与重组，所以用普通持有者记着，读写都不会触发多余的组合。
    val handledArgb = remember { intArrayOf(color.toArgb()) }
    val currentColor = Color.hsv(hue, saturation, value)

    fun update(newHue: Float, newSaturation: Float, newValue: Float) {
        hue = newHue
        saturation = newSaturation
        value = newValue
        val picked = Color.hsv(newHue, newSaturation, newValue)
        handledArgb[0] = picked.toArgb()
        onColorChange(picked)
    }

    // 颜色被外部改掉才重新同步，拖动过程中不会被自己刚发出去的颜色覆盖
    SideEffect {
        val argb = color.toArgb()
        if (argb != handledArgb[0]) {
            val hsv = FloatArray(3)
            android.graphics.Color.colorToHSV(argb, hsv)
            hue = hsv[0]
            saturation = hsv[1]
            value = hsv[2]
            handledArgb[0] = argb
        }
    }

    Column(modifier = modifier) {
        header?.invoke()
        // 最上面直接输色号，和拖动滑杆是同一份颜色
        ColorHexField(
            color = currentColor,
            enabled = enabled,
            onColorChange = { picked ->
                val pickedHsv = FloatArray(3)
                android.graphics.Color.colorToHSV(picked.toArgb(), pickedHsv)
                update(pickedHsv[0], pickedHsv[1], pickedHsv[2])
            },
        )
        ColorSlider(
            label = "H",
            valueText = "${hue.roundToInt()}°",
            value = hue,
            valueRange = 0f..360f,
            trackColors = HueGradientColors,
            thumbColor = Color.hsv(hue, 1f, 1f),
            enabled = enabled,
            onValueChange = { update(it, saturation, value) },
        )
        ColorSlider(
            label = "S",
            valueText = "${(saturation * 100).roundToInt()}%",
            value = saturation,
            valueRange = 0f..1f,
            trackColors = listOf(Color.hsv(hue, 0f, value), Color.hsv(hue, 1f, value)),
            thumbColor = currentColor,
            enabled = enabled,
            onValueChange = { update(hue, it, value) },
        )
        ColorSlider(
            label = "V",
            valueText = "${(value * 100).roundToInt()}%",
            value = value,
            valueRange = 0f..1f,
            trackColors = listOf(Color.Black, Color.hsv(hue, saturation, 1f)),
            thumbColor = currentColor,
            enabled = enabled,
            onValueChange = { update(hue, saturation, it) },
        )
    }
}

// 色号输入框：井号固定在最前面，只接受六位十六进制
@Composable
private fun ColorHexField(color: Color, enabled: Boolean, onColorChange: (Color) -> Unit) {
    // 跟着颜色走：拖滑杆时这里同步刷新，输入到六位时又会被规范化成大写
    var text by remember(color) { mutableStateOf(color.toHexRgb()) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "RGB",
            modifier = Modifier.padding(end = 12.dp),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        OutlinedTextField(
            value = text,
            onValueChange = { input ->
                val hex = input.filter { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' }.take(6).uppercase()
                text = hex
                if (hex.length == 6) {
                    hex.toLongOrNull(16)?.let { onColorChange(Color(0xFF000000L or it)) }
                }
            },
            modifier = Modifier.weight(1f),
            prefix = { Text("#") },
            placeholder = { Text("RRGGBB", style = MaterialTheme.typography.bodyLarge) },
            singleLine = true,
            enabled = enabled,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
        )
    }
}

// 轨道画成渐变：拖到哪里就是哪里的颜色
@Composable
private fun ColorSlider(
    label: String,
    valueText: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    trackColors: List<Color>,
    thumbColor: Color,
    enabled: Boolean,
    onValueChange: (Float) -> Unit,
) {
    val currentOnValueChange by rememberUpdatedState(onValueChange)
    val span = valueRange.endInclusive - valueRange.start
    val outline = MaterialTheme.colorScheme.outlineVariant
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .alpha(if (enabled) 1f else 0.38f),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            modifier = Modifier.width(ColorLabelWidth),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Canvas(
            modifier = Modifier
                .weight(1f)
                .height(40.dp)
                .semantics {
                    contentDescription = label
                    stateDescription = valueText
                    progressBarRangeInfo = ProgressBarRangeInfo(value, valueRange)
                    if (enabled) {
                        setProgress {
                            currentOnValueChange(it.coerceIn(valueRange))
                            true
                        }
                    } else {
                        disabled()
                    }
                }
                .pointerInput(valueRange, enabled) {
                    if (!enabled) return@pointerInput
                    // 两端各留出滑块的半径，滑块不会滑到轨道外面
                    val inset = ColorSliderThumbRadius.toPx()
                    fun update(x: Float) {
                        val fraction = ((x - inset) / (size.width - 2 * inset)).coerceIn(0f, 1f)
                        currentOnValueChange(valueRange.start + fraction * span)
                    }
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        down.consume()
                        update(down.position.x)
                        drag(down.id) {
                            it.consume()
                            update(it.position.x)
                        }
                    }
                },
        ) {
            val inset = ColorSliderThumbRadius.toPx()
            val trackHeight = 16.dp.toPx()
            drawRoundRect(
                brush = Brush.horizontalGradient(trackColors, startX = inset, endX = size.width - inset),
                topLeft = Offset(inset - trackHeight / 2, center.y - trackHeight / 2),
                size = Size(size.width - 2 * inset + trackHeight, trackHeight),
                cornerRadius = CornerRadius(trackHeight / 2),
            )
            val thumb = Offset(inset + (value - valueRange.start) / span * (size.width - 2 * inset), center.y)
            drawCircle(Color.White, radius = inset, center = thumb)
            drawCircle(outline, radius = inset, center = thumb, style = Stroke(1.dp.toPx()))
            drawCircle(thumbColor, radius = inset - 3.dp.toPx(), center = thumb)
        }
        Text(
            text = valueText,
            modifier = Modifier.widthIn(min = 48.dp),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.End,
        )
    }
}

// #RRGGBB 里的 RRGGBB，不含井号
private fun Color.toHexRgb(): String =
    String.format(Locale.ROOT, "%06X", toArgb() and 0xFFFFFF)

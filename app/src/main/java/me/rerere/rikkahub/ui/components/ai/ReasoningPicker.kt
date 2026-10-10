package me.rerere.rikkahub.ui.components.ai

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.SliderState
import androidx.compose.material3.Text
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.RoundedPolygon
import kotlinx.coroutines.flow.drop
import me.rerere.ai.core.ReasoningLevel
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.Idea
import me.rerere.hugeicons.stroke.Idea01
import me.rerere.rikkahub.R
import me.rerere.rikkahub.ui.components.ui.RegisterItemClickAction
import me.rerere.ui.components.ToggleSurface
import me.rerere.ui.icons.ReasoningHigh
import me.rerere.ui.icons.ReasoningLow
import me.rerere.ui.icons.ReasoningMedium
import kotlin.math.roundToInt
import me.rerere.rikkahub.ui.components.ui.bottomSheetMaxHeight

private val levels = ReasoningLevel.entries
private val levelCount = levels.size
private val levelShapes = levels.map { it.shape() }

@Composable
fun ReasoningButton(
    modifier: Modifier = Modifier,
    onlyIcon: Boolean = false,
    reasoningLevel: ReasoningLevel,
    onUpdateReasoningLevel: (ReasoningLevel) -> Unit,
) {
    var showPicker by remember { mutableStateOf(false) }
    RegisterItemClickAction { showPicker = true }

    if (showPicker) {
        ReasoningPicker(
            reasoningLevel = reasoningLevel,
            onDismissRequest = { showPicker = false },
            onUpdateReasoningLevel = onUpdateReasoningLevel
        )
    }

    ToggleSurface(
        checked = reasoningLevel.isEnabled,
        onClick = { showPicker = true },
        modifier = modifier,
    ) {
        Row(
            modifier = Modifier.padding(vertical = 8.dp, horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier.size(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Icon(reasoningLevel.icon(), null)
            }
            if (!onlyIcon) Text(stringResource(R.string.setting_provider_page_reasoning))
        }
    }
}

@Composable
fun ReasoningPicker(
    reasoningLevel: ReasoningLevel,
    onDismissRequest: () -> Unit = {},
    onUpdateReasoningLevel: (ReasoningLevel) -> Unit,
) {
    val currentIndex = levels.indexOf(reasoningLevel).coerceAtLeast(0)
    val sliderState = remember {
        SliderState(
            value = currentIndex.toFloat(),
            trackRange = 0f..(levelCount - 1).toFloat(),
            steps = levelCount - 2,
        )
    }
    val interactionSource = remember { MutableInteractionSource() }
    val hapticFeedback = LocalHapticFeedback.current
    // 拖动过程中就跟随滑块预览，松手后才真正提交
    val previewLevel = levels[sliderState.value.roundToInt().coerceIn(0, levelCount - 1)]

    LaunchedEffect(currentIndex) {
        sliderState.value = currentIndex.toFloat()
    }

    LaunchedEffect(sliderState) {
        snapshotFlow { sliderState.value.roundToInt() }
            .drop(1)
            .collect { hapticFeedback.performHapticFeedback(HapticFeedbackType.SegmentTick) }
    }

    ModalBottomSheet(containerColor = MaterialTheme.colorScheme.surface,
        onDismissRequest = onDismissRequest,
        sheetState = rememberBottomSheetState(initialValue = SheetValue.Hidden, enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded)),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .bottomSheetMaxHeight()
                .padding(horizontal = 24.dp)
                .padding(bottom = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            PickerValueHeader(
                title = stringResource(R.string.reasoning_picker_title),
                value = previewLevel,
                hint = stringResource(R.string.reasoning_picker_hint),
                label = { it.label() },
            ) {
                // 等级越高形状越「激烈」
                PickerHero(
                    shapes = levelShapes,
                    index = levels.indexOf(previewLevel),
                    icon = previewLevel.icon(),
                    containerColor = when {
                        !previewLevel.isEnabled -> MaterialTheme.colorScheme.surfaceContainerHighest
                        previewLevel >= ReasoningLevel.HIGH -> MaterialTheme.colorScheme.primary
                        else -> MaterialTheme.colorScheme.primaryContainer
                    },
                    contentColor = when {
                        !previewLevel.isEnabled -> MaterialTheme.colorScheme.onSurfaceVariant
                        previewLevel >= ReasoningLevel.HIGH -> MaterialTheme.colorScheme.onPrimary
                        else -> MaterialTheme.colorScheme.onPrimaryContainer
                    },
                )
            }

            Slider(
                state = sliderState,
                onValueChange = { sliderState.value = it },
                onValueChangeFinished = {
                    val snappedIndex = sliderState.value.roundToInt().coerceIn(0, levelCount - 1)
                    sliderState.value = snappedIndex.toFloat()
                    onUpdateReasoningLevel(levels[snappedIndex])
                },
                modifier = Modifier.fillMaxWidth(),
                interactionSource = interactionSource,
                thumb = {
                    SliderDefaults.Thumb(
                        interactionSource = interactionSource,
                        isVertical = false,
                        thumbSize = DpSize(4.dp, 52.dp),
                    )
                },
                track = { sliderState ->
                    SliderDefaults.Track(
                        sliderState = sliderState,
                        trackCornerSize = 12.dp,
                        modifier = Modifier.height(40.dp),
                    )
                }
            )
            // 拖动条下方的强度缩写，与七个刻度一一对应
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                levels.forEach { level ->
                    val selected = level == previewLevel
                    Text(
                        text = level.abbreviation(),
                        style = MaterialTheme.typography.labelMedium,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = if (selected) FontWeight.Black else FontWeight.Normal,
                        color = if (selected) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                }
            }
        }
    }
}

private fun ReasoningLevel.icon(): ImageVector = when (this) {
    ReasoningLevel.OFF -> HugeIcons.Idea
    ReasoningLevel.AUTO -> HugeIcons.Idea01
    ReasoningLevel.LOW -> ReasoningLow
    ReasoningLevel.MEDIUM -> ReasoningMedium
    ReasoningLevel.HIGH -> ReasoningHigh
    ReasoningLevel.XHIGH -> ReasoningHigh
    ReasoningLevel.MAX -> ReasoningHigh
}

private fun ReasoningLevel.shape(): RoundedPolygon = when (this) {
    ReasoningLevel.OFF -> MaterialShapes.Circle
    ReasoningLevel.AUTO -> MaterialShapes.Cookie4Sided
    ReasoningLevel.LOW -> MaterialShapes.Cookie6Sided
    ReasoningLevel.MEDIUM -> MaterialShapes.Cookie7Sided
    ReasoningLevel.HIGH -> MaterialShapes.Cookie9Sided
    ReasoningLevel.XHIGH -> MaterialShapes.Cookie12Sided
    ReasoningLevel.MAX -> MaterialShapes.SoftBurst
}

// 拖动条下方显示的强度缩写
private fun ReasoningLevel.abbreviation(): String = when (this) {
    ReasoningLevel.OFF -> "No"
    ReasoningLevel.AUTO -> "Au"
    ReasoningLevel.LOW -> "Lo"
    ReasoningLevel.MEDIUM -> "Mi"
    ReasoningLevel.HIGH -> "Hi"
    ReasoningLevel.XHIGH -> "xH"
    ReasoningLevel.MAX -> "Ma"
}

fun ReasoningLevel.displayLabel(): String = when (this) {
    ReasoningLevel.OFF -> "Off"
    ReasoningLevel.AUTO -> "Auto"
    ReasoningLevel.LOW -> "Low"
    ReasoningLevel.MEDIUM -> "Medium"
    ReasoningLevel.HIGH -> "High"
    ReasoningLevel.XHIGH -> "xHigh"
    ReasoningLevel.MAX -> "Max"
}

@Composable
private fun ReasoningLevel.label(): String = displayLabel()

@Composable
@Preview(showBackground = true)
private fun ReasoningPickerPreview() {
    MaterialTheme {
        var level by remember { mutableStateOf(ReasoningLevel.AUTO) }
        ReasoningPicker(
            reasoningLevel = level,
            onUpdateReasoningLevel = { level = it }
        )
    }
}

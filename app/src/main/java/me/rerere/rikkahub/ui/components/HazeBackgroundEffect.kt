package me.rerere.rikkahub.ui.components

import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.blur.HazeBlurStyle
import dev.chrisbanes.haze.blur.hazeBlur
import dev.chrisbanes.haze.blur.material3.Material3 as BlurMaterial3
import dev.chrisbanes.haze.glass.GlassDefaults
import dev.chrisbanes.haze.glass.GlassStyle
import dev.chrisbanes.haze.glass.OpticalSizeValue
import dev.chrisbanes.haze.glass.hazeGlass
import dev.chrisbanes.haze.glass.material3.Material3 as GlassMaterial3
import me.rerere.rikkahub.data.datastore.BackgroundEffectType

fun CornerBasedShape.toRoundedCornerShape(): RoundedCornerShape =
    RoundedCornerShape(
        topStart = topStart,
        topEnd = topEnd,
        bottomEnd = bottomEnd,
        bottomStart = bottomStart,
    )

@Composable
fun Modifier.hazeBackgroundEffect(
    effectType: BackgroundEffectType,
    hazeState: HazeState,
    tintColor: Color,
    shape: RoundedCornerShape,
): Modifier = when (effectType) {
    BackgroundEffectType.OFF -> this
    BackgroundEffectType.BLUR -> this.hazeBlur(
        input = HazeInput.Sources(hazeState),
        style = HazeBlurStyle.BlurMaterial3 { blurRadius(12.dp) },
    )
    BackgroundEffectType.GLASS -> this.hazeGlass(
        input = HazeInput.Sources(hazeState),
        style = GlassStyle.GlassMaterial3(
            containerColor = tintColor,
            tint = tintColor.copy(alpha = 0.72f),
        ) {
            optics(
                GlassDefaults.optics.copy(
                    blurRadius = OpticalSizeValue.Fixed(16.dp),
                    depth = OpticalSizeValue.Fixed(0.5f),
                )
            )
            shape(shape)
        },
    )
}

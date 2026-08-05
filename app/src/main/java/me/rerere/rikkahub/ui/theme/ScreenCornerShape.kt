package me.rerere.rikkahub.ui.theme

import android.os.Build
import android.view.RoundedCorner
import android.view.View
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp

private data class BottomScreenCornerRadiiPx(
    val left: Int = 0,
    val right: Int = 0,
)

data class ScreenEdgeCornerRadii(
    val start: Dp,
    val end: Dp,
)

val LocalScreenEdgeCornerRadii = compositionLocalOf<ScreenEdgeCornerRadii?> { null }
val LocalScreenCornerAdaptationEnabled = compositionLocalOf { true }
val LocalScreenCornerFallbackRadius = compositionLocalOf { 20.dp }

fun ScreenEdgeCornerRadii.inset(
    horizontalInset: Dp,
    bottomInset: Dp,
): ScreenEdgeCornerRadii? {
    val cornerInset = minOf(horizontalInset, bottomInset)
    val insetRadii = ScreenEdgeCornerRadii(
        start = start.minus(cornerInset).coerceAtLeast(0.dp),
        end = end.minus(cornerInset).coerceAtLeast(0.dp),
    )
    return insetRadii.takeUnless { it.start == 0.dp && it.end == 0.dp }
}

/**
 * Returns the display's physical bottom-corner radii, adjusted for a component's
 * distance from the screen edges. A null result means the platform did not expose
 * usable radii and callers should preserve their Material fallback.
 */
@Composable
fun rememberScreenEdgeCornerRadii(
    horizontalInset: Dp = 0.dp,
    bottomInset: Dp = 0.dp,
): ScreenEdgeCornerRadii? {
    val view = LocalView.current
    val configuration = LocalConfiguration.current
    val radiiPx by produceState(
        initialValue = readBottomScreenCornerRadii(view),
        view,
        configuration.orientation,
        configuration.screenWidthDp,
        configuration.screenHeightDp,
    ) {
        // rootWindowInsets may not be available during the first composition.
        withFrameNanos { }
        value = readBottomScreenCornerRadii(view)
    }
    val density = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current
    val cornerInset = minOf(horizontalInset, bottomInset)
    val leftRadius = with(density) { radiiPx.left.toDp() }
        .minus(cornerInset)
        .coerceAtLeast(0.dp)
    val rightRadius = with(density) { radiiPx.right.toDp() }
        .minus(cornerInset)
        .coerceAtLeast(0.dp)

    if (leftRadius == 0.dp && rightRadius == 0.dp) return null

    return if (layoutDirection == LayoutDirection.Ltr) {
        ScreenEdgeCornerRadii(start = leftRadius, end = rightRadius)
    } else {
        ScreenEdgeCornerRadii(start = rightRadius, end = leftRadius)
    }
}

fun CornerBasedShape.adaptToScreenEdgeCornerRadii(
    radii: ScreenEdgeCornerRadii?,
    squareBottom: Boolean = false,
): CornerBasedShape {
    if (radii == null) {
        return if (squareBottom) {
            copy(
                bottomStart = CornerSize(0.dp),
                bottomEnd = CornerSize(0.dp),
            )
        } else {
            this
        }
    }

    return copy(
        topStart = CornerSize(radii.start),
        topEnd = CornerSize(radii.end),
        bottomStart = CornerSize(if (squareBottom) 0.dp else radii.start),
        bottomEnd = CornerSize(if (squareBottom) 0.dp else radii.end),
    )
}

/**
 * Adapts all corners of a bottom-edge Material shape to the display's physical
 * bottom-corner radii. The bottom pair can remain square while attached to the IME.
 *
 * Android only exposes the physical corner radius from Android 12 onward. On older
 * versions, and on rectangular displays, the supplied Material shape is preserved.
 */
@Composable
fun rememberScreenEdgeCornerShape(
    horizontalInset: Dp = 16.dp,
    bottomInset: Dp = 16.dp,
    squareBottom: Boolean = false,
    enabled: Boolean = LocalScreenCornerAdaptationEnabled.current,
): CornerBasedShape {
    val baseShape = RoundedCornerShape(LocalScreenCornerFallbackRadius.current)
    val radii = if (enabled) {
        LocalScreenEdgeCornerRadii.current?.inset(
            horizontalInset = horizontalInset,
            bottomInset = bottomInset,
        )
    } else {
        null
    }
    return baseShape.adaptToScreenEdgeCornerRadii(
        radii = radii,
        squareBottom = squareBottom,
    )
}

private fun readBottomScreenCornerRadii(view: View): BottomScreenCornerRadiiPx {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
        return BottomScreenCornerRadiiPx()
    }
    val windowInsets = view.rootWindowInsets ?: return BottomScreenCornerRadiiPx()
    return BottomScreenCornerRadiiPx(
        left = windowInsets.getRoundedCorner(RoundedCorner.POSITION_BOTTOM_LEFT)?.radius ?: 0,
        right = windowInsets.getRoundedCorner(RoundedCorner.POSITION_BOTTOM_RIGHT)?.radius ?: 0,
    )
}

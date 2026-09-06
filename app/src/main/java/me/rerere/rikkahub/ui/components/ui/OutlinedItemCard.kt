package me.rerere.rikkahub.ui.components.ui

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CardColors
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.OutlinedCard
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp

val OutlinedItemCornerRadius = 16.dp

/**
 * Shared outlined container for reorderable and regular list items.
 *
 * Drag and animation modifiers remain owned by the caller so this component can
 * be used with any list implementation.
 */
@Composable
fun OutlinedItemCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    colors: CardColors = CardDefaults.outlinedCardColors(),
    shape: Shape = RoundedCornerShape(OutlinedItemCornerRadius),
    content: @Composable ColumnScope.() -> Unit,
) {
    val itemModifier = modifier.fillMaxWidth()
    when {
        onLongClick != null -> OutlinedCard(
            modifier = itemModifier
                .clip(shape)
                .combinedClickable(
                    onClick = { onClick?.invoke() },
                    onLongClick = onLongClick,
                ),
            colors = colors,
            shape = shape,
            content = content,
        )
        onClick != null -> OutlinedCard(
            onClick = onClick,
            modifier = itemModifier,
            colors = colors,
            shape = shape,
            content = content,
        )
        else -> OutlinedCard(
            modifier = itemModifier,
            colors = colors,
            shape = shape,
            content = content,
        )
    }
}

package me.rerere.rikkahub.ui.components.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ListItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import me.rerere.rikkahub.ui.theme.CustomColors
import me.rerere.rikkahub.ui.theme.rememberScreenEdgeCornerShape

private val LocalGroupedFormItem = staticCompositionLocalOf { false }

@Composable
fun FormItemGroup(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    CompositionLocalProvider(LocalGroupedFormItem provides true) {
        Column(
            modifier = modifier
                .fillMaxWidth()
                .clip(rememberScreenEdgeCornerShape()),
            verticalArrangement = Arrangement.spacedBy(2.dp),
            content = content,
        )
    }
}

@Composable
fun FormItem(
    modifier: Modifier = Modifier,
    label: @Composable () -> Unit,
    description: @Composable (() -> Unit)? = null,
    tail: (@Composable () -> Unit)? = null,
    onClick: (() -> Unit)? = null,
    content: (@Composable ColumnScope.() -> Unit)? = null,
) {
    val grouped = LocalGroupedFormItem.current
    val clickActionState = remember { ItemClickActionState() }
    ListItem(
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (grouped) {
                    Modifier
                } else {
                    Modifier.clip(rememberScreenEdgeCornerShape())
                }
            )
            .then(
                if (onClick != null || clickActionState.hasAction) {
                    Modifier.clickable(
                        onClick = onClick ?: clickActionState::invoke,
                    )
                } else {
                    Modifier
                }
            ),
        supportingContent = if (description != null || content != null) {
            {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    description?.invoke()
                    content?.invoke(this)
                }
            }
        } else {
            null
        },
        trailingContent = tail?.let { tailContent ->
            {
                CompositionLocalProvider(LocalItemClickActionState provides clickActionState) {
                    tailContent()
                }
            }
        },
        colors = CustomColors.listItemColors,
    ) {
        label()
    }
}

@Preview(showBackground = true)
@Composable
private fun FormItemPreview() {
    FormItem(
        label = { Text("Label") },
        content = {
            OutlinedTextField(
                value = "",
                onValueChange = {}
            )
        },
        description = {
            Text("Description")
        },
        tail = {
            Switch(
                checked = true,
                onCheckedChange = {}
            )
        },
        modifier = Modifier.padding(4.dp),
    )
}

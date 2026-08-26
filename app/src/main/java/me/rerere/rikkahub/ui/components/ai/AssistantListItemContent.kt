package me.rerere.rikkahub.ui.components.ai

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.ui.components.ui.UIAvatar

private val AssistantListItemHeight = 64.dp

@Composable
internal fun AssistantListItemContent(
    assistant: Assistant,
    displayName: String,
    modifier: Modifier = Modifier,
    avatarModifier: Modifier = Modifier,
    avatarOnClick: (() -> Unit)? = null,
    supportingContent: (@Composable () -> Unit)? = null,
    trailingContent: (@Composable RowScope.() -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(AssistantListItemHeight)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        UIAvatar(
            name = displayName,
            value = assistant.avatar,
            invertDefaultAvatarInDarkMode = true,
            modifier = avatarModifier.size(40.dp),
            onClick = avatarOnClick,
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = displayName,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            supportingContent?.invoke()
        }
        trailingContent?.invoke(this)
    }
}

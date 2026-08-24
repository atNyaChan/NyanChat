package me.rerere.rikkahub.ui.components.message

import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Text
import androidx.compose.material3.SheetValue
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.datetime.toJavaLocalDateTime
import me.rerere.ai.core.MessageRole
import me.rerere.ai.provider.Model
import me.rerere.ai.ui.UIMessage
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.Copy01
import me.rerere.hugeicons.stroke.Delete01
import me.rerere.hugeicons.stroke.Edit01
import me.rerere.hugeicons.stroke.FavouriteCircle
import me.rerere.hugeicons.stroke.GitFork
import me.rerere.hugeicons.stroke.Message01
import me.rerere.hugeicons.stroke.MoreVertical
import me.rerere.hugeicons.stroke.Refresh03
import me.rerere.hugeicons.stroke.Share04
import me.rerere.hugeicons.stroke.StopCircle
import me.rerere.hugeicons.stroke.Translate
import me.rerere.hugeicons.stroke.VolumeHigh
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.model.MessageNode
import me.rerere.rikkahub.ui.components.ui.RikkaConfirmDialog
import me.rerere.rikkahub.ui.context.LocalSettings
import me.rerere.rikkahub.ui.context.LocalTTSState
import me.rerere.rikkahub.utils.copyMessageToClipboard
import me.rerere.rikkahub.utils.extractQuotedContentAsText
import me.rerere.rikkahub.utils.removeBracketedContent
import me.rerere.rikkahub.utils.toLocalString
import me.rerere.rikkahub.utils.toMessageTimeString

@Composable
fun ColumnScope.ChatMessageActionButtons(
    message: UIMessage,
    node: MessageNode,
    onUpdate: (MessageNode) -> Unit,
    onRegenerate: () -> Unit,
    onOpenActionSheet: () -> Unit,
    loading: Boolean = false,
) {
    val context = LocalContext.current
    val settings = LocalSettings.current
    var isPendingDelete by remember { mutableStateOf(false) }
    var showRegenerateConfirm by remember { mutableStateOf(false) }

    LaunchedEffect(isPendingDelete) {
        if (isPendingDelete) {
            delay(3000) // 3秒后自动取消
            isPendingDelete = false
        }
    }

    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        itemVerticalAlignment = Alignment.CenterVertically,
    ) {
        val actionIconColor = MaterialTheme.colorScheme.onSurfaceVariant
        val statsColor = MaterialTheme.colorScheme.secondary.copy(alpha = 0.5f)

        if (
            message.role == MessageRole.USER &&
            settings.displaySetting.showDateTimeInMessage
        ) {
            Text(
                text = message.createdAt.toJavaLocalDateTime().toMessageTimeString(
                    todayLabel = stringResource(R.string.chat_page_today),
                    yesterdayLabel = stringResource(R.string.chat_page_yesterday),
                ),
                style = MaterialTheme.typography.labelSmall,
                color = statsColor,
                maxLines = 1,
            )
        }
        if (message.role == MessageRole.USER && settings.displaySetting.showTokenUsage) {
            ProvideTextStyle(MaterialTheme.typography.labelSmall.copy(color = statsColor)) {
                ExpandableCountStatsItem(
                    value = message.wordCount(includeReasoning = false),
                    suffix = " word",
                    icon = {
                        Icon(
                            imageVector = HugeIcons.Message01,
                            contentDescription = "Words",
                            modifier = Modifier.size(12.dp),
                            tint = statsColor,
                        )
                    },
                )
            }
        }
        Icon(
            imageVector = HugeIcons.Copy01,
            contentDescription = stringResource(R.string.copy),
            modifier = Modifier
                .clip(CircleShape)
                .clickable { context.copyMessageToClipboard(message) }
                .padding(8.dp)
                .size(16.dp),
            tint = actionIconColor
        )

        // 单个对话同时只允许一条消息在生成，生成期间禁用重试
        val regenEnabled = !loading
        Icon(
            imageVector = HugeIcons.Refresh03,
            contentDescription = stringResource(R.string.regenerate),
            modifier = Modifier
                .clip(CircleShape)
                .clickable(
                    enabled = regenEnabled,
                    onClick = {
                        if (message.role == MessageRole.USER) {
                            showRegenerateConfirm = true
                        } else {
                            onRegenerate()
                        }
                    }
                )
                .padding(8.dp)
                .size(16.dp),
            tint = if (regenEnabled) actionIconColor else actionIconColor.copy(alpha = 0.38f)
        )

        if (message.role == MessageRole.ASSISTANT) {
            val tts = LocalTTSState.current
            val isSpeaking by tts.isSpeaking.collectAsState()
            val isAvailable by tts.isAvailable.collectAsState()
            Icon(
                imageVector = if (isSpeaking) HugeIcons.StopCircle else HugeIcons.VolumeHigh,
                contentDescription = stringResource(R.string.tts),
                modifier = Modifier
                    .clip(CircleShape)
                    .clickable(
                        enabled = isAvailable,
                        interactionSource = remember { MutableInteractionSource() },
                        indication = LocalIndication.current,
                        onClick = {
                            if (!isSpeaking) {
                                val text = message.toText()
                                var textToSpeak = text
                                if (settings.displaySetting.ttsOnlyReadQuoted) {
                                    textToSpeak = textToSpeak.extractQuotedContentAsText() ?: textToSpeak
                                }
                                if (settings.displaySetting.ttsOnlyReadOutsideBrackets) {
                                    textToSpeak = textToSpeak.removeBracketedContent() ?: textToSpeak
                                }
                                tts.speak(textToSpeak)
                            } else {
                                tts.stop()
                            }
                        }
                    )
                    .padding(8.dp)
                    .size(16.dp),
                tint = if (isAvailable) actionIconColor else actionIconColor.copy(alpha = 0.38f)
            )

        }

        Icon(
            imageVector = HugeIcons.MoreVertical,
            contentDescription = stringResource(R.string.more_options),
            modifier = Modifier
                .clip(CircleShape)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = LocalIndication.current,
                    onClick = {
                        onOpenActionSheet()
                    }
                )
                .padding(8.dp)
                .size(16.dp),
            tint = actionIconColor
        )

        ChatMessageBranchSelector(
            node = node,
            onUpdate = onUpdate,
        )

        if (
            message.role != MessageRole.USER &&
            settings.displaySetting.showDateTimeInMessage
        ) {
            Text(
                text = message.createdAt.toJavaLocalDateTime().toMessageTimeString(
                    todayLabel = stringResource(R.string.chat_page_today),
                    yesterdayLabel = stringResource(R.string.chat_page_yesterday),
                ),
                style = MaterialTheme.typography.labelSmall,
                color = statsColor,
                maxLines = 1,
                modifier = Modifier.offset(x = (-4).dp),
            )
        }
    }

    // Regenerate confirmation dialog
    RikkaConfirmDialog(
        show = showRegenerateConfirm,
        title = stringResource(R.string.regenerate),
        confirmText = stringResource(R.string.common_confirm_action),
        dismissText = stringResource(R.string.common_cancel),
        onConfirm = {
            showRegenerateConfirm = false
            onRegenerate()
        },
        onDismiss = { showRegenerateConfirm = false },
        text = { Text(stringResource(R.string.regenerate_confirm_message)) }
    )
}

@Composable
fun ChatMessageActionsSheet(
    message: UIMessage,
    model: Model?,
    onDelete: () -> Unit,
    onEdit: () -> Unit,
    onShare: () -> Unit,
    onFork: () -> Unit,
    isFavorite: Boolean = false,
    onToggleFavorite: (() -> Unit)? = null,
    onTranslateRequest: (() -> Unit)? = null,
    onDismissRequest: () -> Unit
) {
    var showDeleteConfirm by remember { mutableStateOf(false) }
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = rememberBottomSheetState(initialValue = SheetValue.Hidden, enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded)),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Edit
            Card(
                onClick = {
                    onDismissRequest()
                    onEdit()
                },
                shape = me.rerere.rikkahub.ui.theme.rememberScreenEdgeCornerShape()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier
                        .padding(16.dp)
                        .fillMaxWidth()
                ) {
                    Icon(
                        imageVector = HugeIcons.Edit01,
                        contentDescription = null,
                        modifier = Modifier.padding(4.dp)
                    )
                    Text(
                        text = stringResource(R.string.edit),
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
            }

            // Translation
            if (message.role == MessageRole.ASSISTANT && onTranslateRequest != null) {
                Card(
                    onClick = {
                        onDismissRequest()
                        onTranslateRequest()
                    },
                    shape = me.rerere.rikkahub.ui.theme.rememberScreenEdgeCornerShape(),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        modifier = Modifier.padding(16.dp).fillMaxWidth(),
                    ) {
                        Icon(HugeIcons.Translate, contentDescription = null, modifier = Modifier.padding(4.dp))
                        Text(stringResource(R.string.translate), style = MaterialTheme.typography.titleMedium)
                    }
                }
            }

            // Share
            Card(
                onClick = {
                    onDismissRequest()
                    onShare()
                },
                shape = me.rerere.rikkahub.ui.theme.rememberScreenEdgeCornerShape(),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier
                        .padding(16.dp)
                        .fillMaxWidth()
                ) {
                    Icon(
                        imageVector = HugeIcons.Share04,
                        contentDescription = null,
                        modifier = Modifier.padding(4.dp)
                    )
                    Text(
                        text = stringResource(R.string.common_share),
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
            }

            // Create a Fork
            Card(
                onClick = {
                    onDismissRequest()
                    onFork()
                },
                shape = me.rerere.rikkahub.ui.theme.rememberScreenEdgeCornerShape(),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier
                        .padding(16.dp)
                        .fillMaxWidth()
                ) {
                    Icon(
                        imageVector = HugeIcons.GitFork,
                        contentDescription = null,
                        modifier = Modifier.padding(4.dp)
                    )
                    Text(
                        text = stringResource(R.string.create_fork),
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
            }

            if (onToggleFavorite != null) {
                Card(
                    onClick = {
                        onDismissRequest()
                        onToggleFavorite()
                    },
                    shape = me.rerere.rikkahub.ui.theme.rememberScreenEdgeCornerShape(),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        modifier = Modifier
                            .padding(16.dp)
                            .fillMaxWidth()
                    ) {
                        Icon(
                            imageVector = HugeIcons.FavouriteCircle,
                            contentDescription = null,
                            modifier = Modifier.padding(4.dp)
                        )
                        Text(
                            text = stringResource(
                                if (isFavorite) R.string.chat_message_remove_favorite
                                else R.string.chat_message_add_favorite
                            ),
                            style = MaterialTheme.typography.titleMedium,
                        )
                    }
                }
            }

            // Delete
            Card(
                onClick = { showDeleteConfirm = true },
                shape = me.rerere.rikkahub.ui.theme.rememberScreenEdgeCornerShape(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer
                )
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier
                        .padding(16.dp)
                        .fillMaxWidth()
                ) {
                    Icon(
                        imageVector = HugeIcons.Delete01,
                        contentDescription = null,
                        modifier = Modifier.padding(4.dp)
                    )
                    Text(
                        text = stringResource(R.string.common_delete),
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
            }

            // Message Info
            ProvideTextStyle(MaterialTheme.typography.labelSmall) {
                Text(message.createdAt.toJavaLocalDateTime().toLocalString())
                if (model != null) {
                    Text(model.displayName)
                }
            }
        }
    }
    RikkaConfirmDialog(
        show = showDeleteConfirm,
        title = stringResource(R.string.common_delete),
        confirmText = stringResource(R.string.common_confirm_action),
        dismissText = stringResource(R.string.common_cancel),
        onConfirm = {
            showDeleteConfirm = false
            onDismissRequest()
            onDelete()
        },
        onDismiss = { showDeleteConfirm = false },
        text = { Text(stringResource(R.string.chat_page_delete_message_confirm)) },
    )
}

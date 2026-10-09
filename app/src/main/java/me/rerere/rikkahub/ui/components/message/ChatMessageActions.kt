package me.rerere.rikkahub.ui.components.message

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Text
import androidx.compose.material3.SheetValue
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.datetime.toJavaLocalDateTime
import me.rerere.ai.core.MessageRole
import me.rerere.ai.provider.Model
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.ArrowLeft01
import me.rerere.hugeicons.stroke.ArrowRight01
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
import me.rerere.rikkahub.Screen
import me.rerere.rikkahub.data.model.MessageNode
import me.rerere.rikkahub.ui.components.ui.CardGroup
import me.rerere.rikkahub.ui.components.ui.CardGroupItemSpacing
import me.rerere.rikkahub.ui.components.ui.CardGroupRow
import me.rerere.ui.components.RikkaConfirmDialog
import me.rerere.rikkahub.ui.context.LocalSettings
import me.rerere.rikkahub.ui.context.LocalNavController
import me.rerere.rikkahub.ui.context.LocalToaster
import me.rerere.rikkahub.ui.context.LocalTTSState
import me.rerere.rikkahub.utils.copyMessageToClipboard
import me.rerere.rikkahub.utils.extractQuotedContentAsText
import me.rerere.rikkahub.utils.removeBracketedContent
import me.rerere.rikkahub.utils.toLocalString
import me.rerere.rikkahub.utils.toMessageTimeString
import me.rerere.rikkahub.ui.components.ui.bottomSheetMaxHeight

@Composable
fun ColumnScope.ChatMessageActionButtons(
    message: UIMessage,
    node: MessageNode,
    onUpdate: (MessageNode) -> Unit,
    onRegenerate: () -> Unit,
    onOpenActionSheet: () -> Unit,
    generating: Boolean = false,
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
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        itemVerticalAlignment = Alignment.CenterVertically,
    ) {
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

        ChatMessageActionButton(
            icon = HugeIcons.Copy01,
            contentDescription = stringResource(R.string.copy),
            onClick = { context.copyMessageToClipboard(message) },
        )

        // 单个对话同时只允许一条消息在生成，生成期间禁用所有消息的重试
        val regenEnabled = !generating
        ChatMessageActionButton(
            icon = HugeIcons.Refresh03,
            contentDescription = stringResource(R.string.regenerate),
            enabled = regenEnabled,
            onClick = {
                if (message.role == MessageRole.USER) {
                    showRegenerateConfirm = true
                } else {
                    onRegenerate()
                }
            },
        )

        if (message.role == MessageRole.ASSISTANT) {
            val tts = LocalTTSState.current
            val isSpeaking by tts.isSpeaking.collectAsState()
            val isAvailable by tts.isAvailable.collectAsState()
            // 朗读中换成带底色的方角按钮
            CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 0.dp) {
                IconToggleButton(
                    checked = isSpeaking,
                    onCheckedChange = { speak ->
                        if (speak) {
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
                    },
                    shapes = IconButtonDefaults.toggleableShapes(),
                    modifier = Modifier.size(IconButtonDefaults.extraSmallContainerSize()),
                    enabled = isAvailable,
                    colors = IconButtonDefaults.iconToggleButtonColors(
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        checkedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                        checkedContentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    ),
                ) {
                    Icon(
                        imageVector = if (isSpeaking) HugeIcons.StopCircle else HugeIcons.VolumeHigh,
                        contentDescription = stringResource(R.string.tts),
                        modifier = Modifier.size(ActionIconSize),
                    )
                }
            }
        }

        ChatMessageActionButton(
            icon = HugeIcons.MoreVertical,
            contentDescription = stringResource(R.string.more_options),
            onClick = onOpenActionSheet,
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

private val ActionIconSize = 18.dp

// 消息下方的小号图标按钮，按下时圆形收成方角
@Composable
internal fun ChatMessageActionButton(
    icon: ImageVector,
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    // 按钮排得密，不让 48dp 的最小触控尺寸把间距撑开
    CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 0.dp) {
        IconButton(
            onClick = onClick,
            shapes = IconButtonDefaults.shapes(),
            modifier = modifier.size(IconButtonDefaults.extraSmallContainerSize()),
            enabled = enabled,
            colors = IconButtonDefaults.iconButtonColors(
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            ),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                modifier = Modifier.size(ActionIconSize),
            )
        }
    }
}

private class MessageSheetAction(
    val icon: ImageVector,
    val label: String,
    val destructive: Boolean = false,
    val enabled: Boolean = true,
    val onClick: () -> Unit,
)

// CardGroupRow 的两栏项：箭头加文字，禁用时整项变淡；iconTrailing 把箭头放到文字后面
@Composable
private fun MessageMoveActionContent(
    icon: ImageVector,
    label: String,
    enabled: Boolean,
    iconTrailing: Boolean = false,
) {
    val contentColor = if (enabled) {
        MaterialTheme.colorScheme.onSurface
    } else {
        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
    }
    Row(
        modifier = Modifier.padding(horizontal = 8.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (!iconTrailing) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = contentColor,
            )
        }
        Text(text = label, color = contentColor)
        if (iconTrailing) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = contentColor,
            )
        }
    }
}

@Suppress("DEPRECATION")
private val UIMessage.containsToolCall: Boolean
    get() = parts.any { part ->
        part is UIMessagePart.Tool || part is UIMessagePart.ToolCall || part is UIMessagePart.ServerTool
    }

@Composable
fun ChatMessageActionsSheet(
    message: UIMessage,
    node: MessageNode,
    model: Model?,
    onDelete: () -> Unit,
    onEdit: () -> Unit,
    onShare: () -> Unit,
    onFork: () -> Unit,
    onUpdate: (MessageNode) -> Unit,
    isFavorite: Boolean = false,
    onToggleFavorite: (() -> Unit)? = null,
    onTranslateRequest: (() -> Unit)? = null,
    onDismissRequest: () -> Unit
) {
    var showDeleteConfirm by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val toaster = LocalToaster.current
    val navController = LocalNavController.current
    ModalBottomSheet(containerColor = MaterialTheme.colorScheme.surface,
        onDismissRequest = onDismissRequest,
        sheetState = rememberBottomSheetState(initialValue = SheetValue.Hidden, enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded)),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .bottomSheetMaxHeight()
                .padding(horizontal = 16.dp).padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            val canEdit = !message.containsToolCall
            val actions = buildList {
                add(
                    MessageSheetAction(
                        icon = HugeIcons.Edit01,
                        label = stringResource(R.string.edit),
                        enabled = canEdit,
                    ) {
                        if (canEdit) {
                            onDismissRequest()
                            onEdit()
                        } else {
                            toaster.show(context.getString(R.string.chat_message_cannot_edit_tool_call))
                        }
                    }
                )
                if (message.role == MessageRole.ASSISTANT && onTranslateRequest != null) {
                    add(
                        MessageSheetAction(HugeIcons.Translate, stringResource(R.string.translate)) {
                            onDismissRequest()
                            onTranslateRequest()
                        }
                    )
                }
                add(
                    MessageSheetAction(HugeIcons.Share04, stringResource(R.string.common_share)) {
                        onDismissRequest()
                        onShare()
                    }
                )
                add(
                    MessageSheetAction(HugeIcons.GitFork, stringResource(R.string.create_fork)) {
                        onDismissRequest()
                        onFork()
                    }
                )
                if (onToggleFavorite != null) {
                    add(
                        MessageSheetAction(
                            icon = HugeIcons.FavouriteCircle,
                            label = stringResource(
                                if (isFavorite) R.string.chat_message_remove_favorite
                                else R.string.chat_message_add_favorite
                            ),
                        ) {
                            onDismissRequest()
                            onToggleFavorite()
                        }
                    )
                }
                add(
                    MessageSheetAction(HugeIcons.Delete01, stringResource(R.string.common_delete), destructive = true) {
                        // 删除还要再确认一次，先让 sheet 留着
                        showDeleteConfirm = true
                    }
                )
            }

            // CardGroup 的 content 不是 @Composable，颜色需在进入前计算
            val destructiveItemColors = ListItemDefaults.colors(
                containerColor = MaterialTheme.colorScheme.errorContainer,
                contentColor = MaterialTheme.colorScheme.onErrorContainer,
                leadingContentColor = MaterialTheme.colorScheme.onErrorContainer,
            )
            // 禁用项（如含工具调用的消息的「编辑」）用 disabled 色呈现，但点击仍提示原因
            val disabledItemColors = ListItemDefaults.colors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                contentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
                leadingContentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
            )
            // 分支有多条消息时，把前移/后移作为同一 CardGroup 的并排首行
            val hasBranchNavigation = node.messages.size > 1
            val canMoveForward = hasBranchNavigation && node.selectIndex > 0
            val canMoveBackward = hasBranchNavigation && node.selectIndex < node.messages.lastIndex
            val moveDisabledColors = ListItemDefaults.colors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                contentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
            )
            Column(modifier = Modifier.fillMaxWidth()) {
                if (hasBranchNavigation) {
                    CardGroupRow(
                        modifier = Modifier.fillMaxWidth(),
                        attachBelow = true,
                    ) {
                        item(
                            onClick = if (canMoveForward) {
                                { onUpdate(node.moveCurrentBy(-1)) }
                            } else null,
                            colors = if (canMoveForward) null else moveDisabledColors,
                            headlineContent = {
                                MessageMoveActionContent(
                                    icon = HugeIcons.ArrowLeft01,
                                    label = stringResource(R.string.chat_page_move_forward),
                                    enabled = canMoveForward,
                                )
                            },
                        )
                        item(
                            onClick = if (canMoveBackward) {
                                { onUpdate(node.moveCurrentBy(1)) }
                            } else null,
                            colors = if (canMoveBackward) null else moveDisabledColors,
                            headlineContent = {
                                MessageMoveActionContent(
                                    icon = HugeIcons.ArrowRight01,
                                    label = stringResource(R.string.chat_page_move_backward),
                                    enabled = canMoveBackward,
                                    iconTrailing = true,
                                )
                            },
                        )
                    }
                    // 与 CardGroup 内各项之间的间距保持一致
                    Spacer(modifier = Modifier.height(CardGroupItemSpacing))
                }
                CardGroup(
                    modifier = Modifier.fillMaxWidth(),
                    continueFromPrevious = hasBranchNavigation,
                ) {
                    actions.forEach { action ->
                        item(
                            onClick = action.onClick,
                            leadingContent = {
                                Icon(
                                    imageVector = action.icon,
                                    contentDescription = null,
                                )
                            },
                            colors = when {
                                action.destructive -> destructiveItemColors
                                !action.enabled -> disabledItemColors
                                else -> null
                            },
                        ) {
                            Text(action.label)
                        }
                    }
                }
            }

            // Message Info
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                ProvideTextStyle(MaterialTheme.typography.labelSmall) {
                    Text(message.createdAt.toJavaLocalDateTime().toLocalString())
                    // 模型快照被删除后 model 可能为 null，此时用消息上记录的 modelId
                    val searchModelId = model?.id ?: message.modelId
                    if (searchModelId != null) {
                        Text(
                            text = model?.displayName?.takeIf { it.isNotBlank() }
                                ?: "($searchModelId)",
                            color = MaterialTheme.colorScheme.primary,
                            textDecoration = TextDecoration.Underline,
                            modifier = Modifier.clickable {
                                onDismissRequest()
                                navController.navigate(Screen.MessageSearch(searchModelId.toString()))
                            },
                        )
                    }
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

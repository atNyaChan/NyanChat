package me.rerere.rikkahub.ui.pages.chat

import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.Forward02
import me.rerere.hugeicons.stroke.CheckList
import me.rerere.hugeicons.stroke.Cancel01
import me.rerere.hugeicons.stroke.Pin
import me.rerere.hugeicons.stroke.PinOff
import me.rerere.hugeicons.stroke.PencilEdit01
import me.rerere.hugeicons.stroke.Delete01
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.model.Conversation
import me.rerere.rikkahub.ui.theme.extendColors
import me.rerere.rikkahub.utils.mirrorForRtl
import java.time.LocalDate
import kotlin.uuid.Uuid

/**
 * Represents different types of items in the conversation list
 */
sealed class ConversationListItem {
    data class DateHeader(
        val date: LocalDate,
        val label: String
    ) : ConversationListItem()
    data object PinnedHeader : ConversationListItem()
    data class Item(
        val conversation: Conversation
    ) : ConversationListItem()
}

@Composable
fun ColumnScope.ConversationList(
    current: Conversation,
    conversations: List<ConversationListItem>,
    conversationJobs: Collection<Uuid>,
    listState: LazyListState,
    totalConversations: Int,
    modifier: Modifier = Modifier,
    centerCurrent: Boolean = false,
    onClick: (Conversation) -> Unit = {},
    onDelete: (Conversation) -> Unit = {},
    onEditTitle: (Conversation) -> Unit = {},
    onPin: (Conversation) -> Unit = {},
    onMove: (List<Conversation>, Boolean) -> Unit = { _, _ -> },
    onDeleteSelected: (List<Conversation>) -> Unit = {},
    onLoadAllConversations: suspend () -> List<Conversation> = { emptyList() },
) {
    var selectedConversations by remember { mutableStateOf<Map<Uuid, Conversation>>(emptyMap()) }
    val scope = rememberCoroutineScope()

    // 多选状态下长按聊天时，弹出仅含「移动到…/删除」菜单的目标会话 id。
    // 状态放在父级而不是单个 item 的 remember 里，避免列表重组合时被重置。
    var batchMenuConversationId by remember { mutableStateOf<Uuid?>(null) }

    // 列表内容快照标识：会话增删/移动/置顶等引起内容变化时，用于触发一次「当前会话居中」。
    // 用整个条目序列拼接而不是数量，这样数量不变但对调顺序时也能感知变化。
    val itemContentKey = conversations.joinToString("|") { item ->
        when (item) {
            is ConversationListItem.DateHeader -> "d:${item.date}"
            is ConversationListItem.PinnedHeader -> "p"
            is ConversationListItem.Item -> "c:${item.conversation.id}"
        }
    }

    // 打开侧栏或列表内容刷新后：当前会话在列表中时尽量置于可视区域中间；
    // 当前会话不在列表中（例如切换文件夹或助手后）时回到列表最上面。
    LaunchedEffect(centerCurrent, itemContentKey, current.id, listState) {
        if (!centerCurrent) return@LaunchedEffect
        // 用户正在手动滚动时不打扰（例如浏览时触发的列表变化）
        if (listState.isScrollInProgress) return@LaunchedEffect
        val currentIndex = conversations.indexOfFirst {
            (it as? ConversationListItem.Item)?.conversation?.id == current.id
        }
        if (currentIndex < 0) {
            if (listState.firstVisibleItemIndex != 0 || listState.firstVisibleItemScrollOffset != 0) {
                listState.animateScrollToItem(0)
            }
            return@LaunchedEffect
        }
        // 当前会话已在可视区域内时不额外滚动
        if (listState.layoutInfo.visibleItemsInfo.any { it.index == currentIndex }) {
            return@LaunchedEffect
        }

        // 目标条目尚不在可视区域内（上面的可见性判断已提前返回），先瞬时（无平滑动画）定位到它，
        // 以便读取其实际高度来计算居中偏移；这里不做平滑滚动，
        // 避免“先滚到可视区域上方、再滚到中间”的两段式动画。
        listState.scrollToItem(currentIndex)

        // 等到目标条目完成布局，读取它的高度与位置
        val itemInfo = withTimeoutOrNull(2000) {
            snapshotFlow {
                listState.layoutInfo.visibleItemsInfo.firstOrNull { it.index == currentIndex }
            }
                .filterNotNull()
                .first()
        } ?: return@LaunchedEffect

        val layoutInfo = listState.layoutInfo
        val viewportSize = layoutInfo.viewportEndOffset - layoutInfo.viewportStartOffset
        val targetTop = ((viewportSize - itemInfo.size).coerceAtLeast(0)) / 2
        val currentTop = itemInfo.offset - layoutInfo.viewportStartOffset
        val delta = currentTop - targetTop
        if (delta != 0) {
            // 一次平滑滚动，直接从当前偏移滚到目标偏移（正好居中）。
            // 注：animateScrollToItem 的 scrollOffset 是 firstVisibleItemScrollOffset 语义，
            // 正数代表条目滚到视口上方、数值符号相反，因此这里用 scrollBy(delta) 而非 scrollOffset。
            listState.animateScrollBy(delta.toFloat())
        }
    }

    Column(modifier = modifier) {
        if (selectedConversations.isNotEmpty()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "${selectedConversations.size} / $totalConversations",
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = { selectedConversations = emptyMap() }) {
                    Icon(HugeIcons.Cancel01, contentDescription = stringResource(R.string.conversation_cancel_selection))
                }
                IconButton(
                    onClick = {
                        scope.launch {
                            selectedConversations = onLoadAllConversations().associateBy { it.id }
                        }
                    },
                    enabled = !(totalConversations > 0 && selectedConversations.size == totalConversations),
                ) {
                    Icon(HugeIcons.CheckList, contentDescription = stringResource(R.string.conversation_select_all))
                }
                IconButton(onClick = {
                    onMove(selectedConversations.values.toList(), true)
                    selectedConversations = emptyMap()
                }) {
                    Icon(HugeIcons.Forward02, contentDescription = stringResource(R.string.conversation_move_to), modifier = Modifier.mirrorForRtl())
                }
                IconButton(onClick = {
                    onDeleteSelected(selectedConversations.values.toList())
                    selectedConversations = emptyMap()
                }) {
                    Icon(HugeIcons.Delete01, contentDescription = stringResource(R.string.common_delete))
                }
            }
        }
        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
        if (conversations.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .fillParentMaxHeight(),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = stringResource(id = R.string.chat_page_no_conversations),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }

        items(
            items = conversations,
            key = { item ->
                when (item) {
                    is ConversationListItem.DateHeader -> "date_${item.date}"
                    is ConversationListItem.PinnedHeader -> "pinned_header"
                    is ConversationListItem.Item -> item.conversation.id.toString()
                }
            }
        ) { item ->
            when (item) {
                is ConversationListItem.DateHeader -> {
                    DateHeaderItem(
                        label = item.label,
                        modifier = Modifier.animateItem()
                    )
                }

                is ConversationListItem.PinnedHeader -> {
                    PinnedHeader(
                        modifier = Modifier.animateItem()
                    )
                }

                is ConversationListItem.Item -> {
                    ConversationItem(
                        conversation = item.conversation,
                        highlighted = selectedConversations.isEmpty() && item.conversation.id == current.id,
                        multiSelected = selectedConversations.isNotEmpty() && item.conversation.id in selectedConversations,
                        multiSelecting = selectedConversations.isNotEmpty(),
                        loading = item.conversation.id in conversationJobs,
                        onClick = { conversation ->
                            if (selectedConversations.isEmpty()) onClick(conversation)
                            else selectedConversations = selectedConversations.toMutableMap().apply {
                                if (remove(conversation.id) == null) put(conversation.id, conversation)
                            }
                        },
                        onLongClick = { conversation ->
                            selectedConversations = selectedConversations + (conversation.id to conversation)
                            batchMenuConversationId = conversation.id
                        },
                        onBeginMultiSelect = { conversation ->
                            selectedConversations = selectedConversations + (conversation.id to conversation)
                        },
                        onBatchMove = {
                            onMove(selectedConversations.values.toList(), true)
                            selectedConversations = emptyMap()
                            batchMenuConversationId = null
                        },
                        onBatchDelete = {
                            onDeleteSelected(selectedConversations.values.toList())
                            selectedConversations = emptyMap()
                            batchMenuConversationId = null
                        },
                        batchMenuExpanded = selectedConversations.isNotEmpty() && batchMenuConversationId == item.conversation.id,
                        onDismissBatchMenu = { batchMenuConversationId = null },
                        onDelete = onDelete,
                        onEditTitle = onEditTitle,
                        onPin = onPin,
                        onMove = { onMove(listOf(it), false) },
                        modifier = Modifier.animateItem()
                    )
                }
            }
        }
    }
    }
}

@Composable
private fun DateHeaderItem(
    label: String,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(Color.Transparent)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
    }
}

@Composable
private fun PinnedHeader(
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = HugeIcons.Pin,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.size(8.dp))
        Text(
            text = stringResource(R.string.pinned_chats),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
    }
}

@Composable
private fun ConversationItem(
    conversation: Conversation,
    highlighted: Boolean,
    multiSelected: Boolean,
    multiSelecting: Boolean,
    loading: Boolean,
    modifier: Modifier = Modifier,
    onBatchMove: () -> Unit = {},
    onBatchDelete: () -> Unit = {},
    batchMenuExpanded: Boolean = false,
    onDismissBatchMenu: () -> Unit = {},
    onDelete: (Conversation) -> Unit = {},
    onEditTitle: (Conversation) -> Unit = {},
    onPin: (Conversation) -> Unit = {},
    onMove: (Conversation) -> Unit = {},
    onLongClick: (Conversation) -> Unit = {},
    onBeginMultiSelect: (Conversation) -> Unit = {},
    onClick: (Conversation) -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val focusManager = LocalFocusManager.current
    val backgroundColor = if (multiSelected || highlighted) {
        MaterialTheme.colorScheme.secondaryContainer
    } else {
        Color.Transparent
    }
    var showDropdownMenu by remember {
        mutableStateOf(false)
    }
    var menuOffsetX by remember { mutableStateOf(0f) }
    Box(
        modifier = modifier
            .clip(CircleShape)
            .combinedClickable(
                interactionSource = interactionSource,
                indication = LocalIndication.current,
                onClick = { onClick(conversation) },
                onLongClick = {
                    // Also clear chat input focus when the drawer is permanently visible.
                    focusManager.clearFocus(force = true)
                    if (multiSelecting) onLongClick(conversation) else showDropdownMenu = true
                }
            )
            .pointerInput(conversation.id) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    menuOffsetX = down.position.x
                    waitForUpOrCancellation()
                }
            }
            .background(backgroundColor),
    ) {
        val contentColor = if (multiSelected || highlighted) {
            MaterialTheme.colorScheme.onSecondaryContainer
        } else {
            MaterialTheme.colorScheme.onSurface
        }
        CompositionLocalProvider(LocalContentColor provides contentColor) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
            Text(
                text = conversation.title.ifBlank { stringResource(id = R.string.chat_page_new_message) },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.weight(1f))

            // 置顶图标
            AnimatedVisibility(conversation.isPinned) {
                Icon(
                    imageVector = HugeIcons.Pin,
                    contentDescription = "Pinned",
                    modifier = Modifier.size(12.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
            AnimatedVisibility(loading) {
                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(MaterialTheme.extendColors.green6)
                        .size(4.dp)
                        .semantics {
                            contentDescription = "Loading"
                        }
                )
            }
            DropdownMenu(
                expanded = showDropdownMenu || batchMenuExpanded,
                onDismissRequest = {
                    showDropdownMenu = false
                    onDismissBatchMenu()
                },
                shape = me.rerere.rikkahub.ui.theme.rememberScreenEdgeCornerShape(),
                offset = DpOffset(
                    x = with(LocalDensity.current) { menuOffsetX.toDp() },
                    y = 0.dp,
                ),
            ) {
                if (multiSelecting) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.conversation_move_to)) },
                        onClick = {
                            showDropdownMenu = false
                            onBatchMove()
                        },
                        leadingIcon = { Icon(HugeIcons.Forward02, null, modifier = Modifier.mirrorForRtl()) },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.common_delete)) },
                        onClick = {
                            showDropdownMenu = false
                            onBatchDelete()
                        },
                        leadingIcon = { Icon(HugeIcons.Delete01, null) },
                    )
                } else {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.conversation_multi_select)) },
                        onClick = {
                            onBeginMultiSelect(conversation)
                            showDropdownMenu = false
                        },
                        leadingIcon = { Icon(HugeIcons.CheckList, null) },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.conversation_move_to)) },
                        onClick = {
                            onMove(conversation)
                            showDropdownMenu = false
                        },
                        leadingIcon = { Icon(HugeIcons.Forward02, null, modifier = Modifier.mirrorForRtl()) },
                    )
                    DropdownMenuItem(
                        text = {
                            Text(
                                if (conversation.isPinned) stringResource(R.string.unpin_chat) else stringResource(R.string.pin_chat)
                            )
                        },
                        onClick = {
                            onPin(conversation)
                            showDropdownMenu = false
                        },
                        leadingIcon = {
                            Icon(
                                if (conversation.isPinned) HugeIcons.PinOff else HugeIcons.Pin,
                                null
                            )
                        }
                    )

                    DropdownMenuItem(
                        text = {
                            Text(stringResource(R.string.chat_page_edit_title))
                        },
                        onClick = {
                            onEditTitle(conversation)
                            showDropdownMenu = false
                        },
                        leadingIcon = {
                            Icon(HugeIcons.PencilEdit01, null)
                        }
                    )

                    DropdownMenuItem(
                        text = {
                            Text(stringResource(id = R.string.common_delete))
                        },
                        onClick = {
                            onDelete(conversation)
                            showDropdownMenu = false
                        },
                        leadingIcon = {
                            Icon(HugeIcons.Delete01, null)
                        }
                    )
                }
            }
            }
        }
    }
}

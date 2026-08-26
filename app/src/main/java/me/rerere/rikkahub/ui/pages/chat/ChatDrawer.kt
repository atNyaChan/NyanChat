package me.rerere.rikkahub.ui.pages.chat

import androidx.activity.ComponentActivity
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.compose.collectAsLazyPagingItems
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.ArrowLeft01
import me.rerere.hugeicons.stroke.ArrowRight01
import me.rerere.hugeicons.stroke.ChartColumn
import me.rerere.hugeicons.stroke.Delete01
import me.rerere.hugeicons.stroke.Folder01
import me.rerere.hugeicons.stroke.FolderAdd
import me.rerere.hugeicons.stroke.Image02
import me.rerere.hugeicons.stroke.InLove
import me.rerere.hugeicons.stroke.LanguageCircle
import me.rerere.hugeicons.stroke.PencilEdit01
import me.rerere.hugeicons.stroke.Search01
import me.rerere.hugeicons.stroke.Settings03
import me.rerere.rikkahub.R
import me.rerere.rikkahub.Screen
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.data.datastore.getCurrentAssistant
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.data.model.Conversation
import me.rerere.rikkahub.data.model.Folder
import me.rerere.rikkahub.data.repository.ConversationRepository
import me.rerere.ai.core.MessageRole
import me.rerere.rikkahub.ui.components.ai.AssistantPicker
import me.rerere.rikkahub.ui.components.ui.BackupReminderCard
import me.rerere.rikkahub.ui.components.ui.Greeting
import me.rerere.rikkahub.ui.components.ui.Tooltip
import me.rerere.rikkahub.ui.components.ui.UIAvatar
import androidx.compose.ui.draw.clip
import me.rerere.rikkahub.ui.context.LocalToaster
import me.rerere.rikkahub.ui.context.Navigator
import com.dokar.sonner.ToastType
import me.rerere.rikkahub.ui.hooks.EditStateContent
import me.rerere.rikkahub.ui.hooks.readBooleanPreference
import me.rerere.rikkahub.ui.hooks.useEditState
import me.rerere.rikkahub.ui.modifier.onClick
import me.rerere.rikkahub.ui.theme.LocalScreenCornerAdaptationEnabled
import me.rerere.rikkahub.ui.theme.LocalScreenCornerFallbackRadius
import me.rerere.rikkahub.ui.theme.LocalScreenEdgeCornerRadii
import me.rerere.rikkahub.utils.navigateToChatPage
import me.rerere.rikkahub.utils.toDp
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject
import kotlin.uuid.Uuid

private data class MoveTarget(
    val assistantId: Uuid,
    val folderId: Uuid?,
    val label: String,
)

@Composable
fun ChatDrawerContent(
    navController: Navigator,
    vm: ChatVM,
    settings: Settings,
    current: Conversation,
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val toaster = LocalToaster.current
    val repo = koinInject<ConversationRepository>()

    val activity = context as ComponentActivity
    val drawerVm: ChatDrawerVM = koinViewModel(viewModelStoreOwner = activity)

    val conversations = drawerVm.conversations.collectAsLazyPagingItems()
    val folders by drawerVm.folders.collectAsStateWithLifecycle()
    val selectedFolderId by drawerVm.selectedFolderId.collectAsStateWithLifecycle()
    val allFolders by drawerVm.allFolders.collectAsStateWithLifecycle()
    val conversationListState = rememberLazyListState(
        initialFirstVisibleItemIndex = drawerVm.scrollIndex,
        initialFirstVisibleItemScrollOffset = drawerVm.scrollOffset,
    )
    val conversationCount by drawerVm.conversationCount.collectAsStateWithLifecycle()

    LaunchedEffect(conversationListState) {
        snapshotFlow {
            conversationListState.firstVisibleItemIndex to
                conversationListState.firstVisibleItemScrollOffset
        }
            .distinctUntilChanged()
            .collectLatest { (index, offset) ->
                drawerVm.saveScrollPosition(index, offset)
            }
    }

    val conversationJobs by vm.conversationJobs.collectAsStateWithLifecycle(
        initialValue = emptyMap(),
    )

    // 昵称编辑状态
    val nicknameEditState = useEditState<String> { newNickname ->
        vm.updateSettings(
            settings.copy(
                displaySetting = settings.displaySetting.copy(
                    userNickname = newNickname
                )
            )
        )
    }

    // 文件夹相关状态
    var showCreateFolderDialog by remember { mutableStateOf(false) }
    var folderToRename by remember { mutableStateOf<Folder?>(null) }
    var folderToDelete by remember { mutableStateOf<Folder?>(null) }
    var conversationToDelete by remember { mutableStateOf<Conversation?>(null) }
    var conversationsToDelete by remember { mutableStateOf<List<Conversation>>(emptyList()) }
    var conversationsToMove by remember { mutableStateOf<List<Conversation>>(emptyList()) }
    var moveStartedFromMultiSelect by remember { mutableStateOf(false) }
    var moveCreateFolderAssistant by remember { mutableStateOf<Assistant?>(null) }
    var pendingMoveTarget by remember { mutableStateOf<MoveTarget?>(null) }
    var conversationToEditTitle by remember { mutableStateOf<Conversation?>(null) }
    var editedTitle by remember { mutableStateOf("") }

    // Menu popup 状态

    val drawerEndCorner = if (LocalScreenCornerAdaptationEnabled.current) {
        LocalScreenEdgeCornerRadii.current?.end ?: LocalScreenCornerFallbackRadius.current
    } else {
        LocalScreenCornerFallbackRadius.current
    }

    ModalDrawerSheet(
        modifier = Modifier.width(300.dp),
        drawerShape = RoundedCornerShape(
            topEnd = drawerEndCorner,
            bottomEnd = drawerEndCorner,
        ),
    ) {
        Column(
            modifier = Modifier.padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            BackupReminderCard(
                settings = settings,
                onClick = { navController.navigate(Screen.Backup) },
            )

            // 用户头像和昵称自定义区域
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                UIAvatar(
                    name = settings.displaySetting.userNickname.ifBlank { stringResource(R.string.user_default_name) },
                    value = settings.displaySetting.userAvatar,
                    invertDefaultAvatarInDarkMode = true,
                    onUpdate = { newAvatar ->
                        vm.updateSettings(
                            settings.copy(
                                displaySetting = settings.displaySetting.copy(
                                    userAvatar = newAvatar
                                )
                            )
                        )
                    },
                    modifier = Modifier.size(50.dp),
                )

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(
                            text = settings.displaySetting.userNickname.ifBlank { stringResource(R.string.user_default_name) },
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.clickable {
                                nicknameEditState.open(settings.displaySetting.userNickname)
                            }
                        )

                        Icon(
                            imageVector = HugeIcons.PencilEdit01,
                            contentDescription = "Edit",
                            modifier = Modifier
                                .onClick {
                                    nicknameEditState.open(settings.displaySetting.userNickname)
                                }
                                .size(LocalTextStyle.current.fontSize.toDp())
                        )
                    }
                    Greeting(
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
            }

            DrawerActions(navController = navController)

            FolderBar(
                folders = folders,
                selectedFolderId = selectedFolderId,
                onSelect = { folderId ->
                    drawerVm.selectFolder(folderId)
                    if (current.messageNodes.none { it.currentMessage.role == MessageRole.USER }) {
                        vm.updateConversation(current.copy(folderId = folderId))
                    }
                },
                onCreate = { showCreateFolderDialog = true },
                onRename = { folderToRename = it },
                onDelete = { folderToDelete = it },
                onMoveForward = { drawerVm.moveFolder(it.id, forward = true) },
                onMoveBackward = { drawerVm.moveFolder(it.id, forward = false) },
            )

            ConversationList(
                current = current,
                conversations = conversations,
                conversationJobs = conversationJobs.keys,
                listState = conversationListState,
                totalConversations = conversationCount,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                onClick = {
                    navigateToChatPage(navController, it.id)
                },
                onEditTitle = {
                    conversationToEditTitle = it
                    editedTitle = it.title
                },
                onDelete = {
                    conversationToDelete = it
                },
                onPin = {
                    vm.updatePinnedStatus(it)
                },
                onMove = { selected, fromMultiSelect ->
                    conversationsToMove = selected
                    moveStartedFromMultiSelect = fromMultiSelect
                },
                onDeleteSelected = { conversationsToDelete = it },
                onLoadAllConversations = { drawerVm.loadAllConversationsForSelection() },
            )

            conversationToDelete?.let { conversation ->
                AlertDialog(
                    onDismissRequest = { conversationToDelete = null },
                    title = { Text(stringResource(R.string.common_delete)) },
                    text = {
                        Text(
                            stringResource(
                                R.string.chat_page_delete_conversation_confirm,
                                conversation.title.ifBlank { stringResource(R.string.chat_page_new_message) }
                            )
                        )
                    },
                    confirmButton = {
                        TextButton(onClick = {
                            conversationToDelete = null
                            scope.launch {
                                vm.deleteConversation(conversation).join()
                                conversations.refresh()
                                if (conversation.id == current.id) {
                                    // 删除后新建的空会话归入侧栏当前选中的文件夹
                                    navigateToChatPage(navController, folderId = selectedFolderId)
                                }
                            }
                        }) { Text(stringResource(R.string.common_delete)) }
                    },
                    dismissButton = {
                        TextButton(onClick = { conversationToDelete = null }) {
                            Text(stringResource(R.string.common_cancel))
                        }
                    },
                )
            }

            if (conversationsToDelete.isNotEmpty()) {
                AlertDialog(
                    onDismissRequest = { conversationsToDelete = emptyList() },
                    title = { Text(stringResource(R.string.common_delete)) },
                    text = { Text(stringResource(R.string.chat_page_delete_selected_confirm, conversationsToDelete.size)) },
                    confirmButton = {
                        TextButton(onClick = {
                            val targets = conversationsToDelete
                            conversationsToDelete = emptyList()
                            scope.launch {
                                targets.map(vm::deleteConversation).forEach { it.join() }
                                conversations.refresh()
                                if (targets.any { it.id == current.id }) {
                                    // 删除后新建的空会话归入侧栏当前选中的文件夹
                                    navigateToChatPage(navController, folderId = selectedFolderId)
                                }
                            }
                        }) { Text(stringResource(R.string.common_delete)) }
                    },
                    dismissButton = {
                        TextButton(onClick = { conversationsToDelete = emptyList() }) {
                            Text(stringResource(R.string.common_cancel))
                        }
                    },
                )
            }

            conversationToEditTitle?.let { conversation ->
                AlertDialog(
                    onDismissRequest = { conversationToEditTitle = null },
                    title = { Text(stringResource(R.string.chat_page_edit_title)) },
                    text = {
                        OutlinedTextField(
                            value = editedTitle,
                            onValueChange = { editedTitle = it },
                            singleLine = true,
                        )
                    },
                    confirmButton = {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            TextButton(onClick = { conversationToEditTitle = null }) {
                                Text(stringResource(R.string.common_cancel))
                            }
                            TextButton(onClick = {
                                vm.generateTitleCandidate(conversation) { editedTitle = it }
                            }) { Text(stringResource(R.string.chat_page_auto_generate_title)) }
                            TextButton(onClick = {
                                vm.updateConversationTitle(conversation, editedTitle)
                                conversationToEditTitle = null
                                conversations.refresh()
                            }) { Text(stringResource(R.string.common_confirm_action)) }
                        }
                    },
                )
            }

            // 助手选择器
            AssistantPicker(
                settings = settings,
                onUpdateSettings = {
                    val updateJob = vm.updateSettings(it)
                    scope.launch {
                        // A new chat resolves its assistant from SettingsStore during initialization.
                        // Wait for the selection to be persisted before navigating, otherwise the
                        // new page can race the update and bind the conversation to the old assistant.
                        updateJob.join()
                        val id = if (context.readBooleanPreference("create_new_conversation_on_start", true)) {
                            Uuid.random()
                        } else {
                            repo.getConversationsOfAssistant(it.assistantId)
                                .first()
                                .firstOrNull()
                                ?.id ?: Uuid.random()
                        }
                        navigateToChatPage(navigator = navController, chatId = id)
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                onClickSetting = {
                    val currentAssistantId = settings.assistantId
                    navController.navigate(Screen.AssistantDetail(id = currentAssistantId.toString()))
                }
            )

            Row(
                horizontalArrangement = Arrangement.SpaceAround,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp)
            ) {
                DrawerAction(
                    icon = {
                        Icon(HugeIcons.ChartColumn, stringResource(R.string.stats_page_title))
                    },
                    label = {
                        Text(stringResource(R.string.stats_page_title))
                    },
                    onClick = {
                        navController.navigate(Screen.Stats)
                    },
                )

                DrawerAction(
                    icon = {
                        Icon(HugeIcons.InLove, stringResource(R.string.favorite_page_title))
                    },
                    label = {
                        Text(stringResource(R.string.favorite_page_title))
                    },
                    onClick = {
                        navController.navigate(Screen.Favorite)
                    },
                )

                DrawerAction(
                    icon = { Icon(HugeIcons.Image02, null) },
                    label = { Text(stringResource(R.string.chat_page_menu_image_generation)) },
                    onClick = { navController.navigate(Screen.ImageGen) },
                )

                DrawerAction(
                    icon = { Icon(HugeIcons.LanguageCircle, null) },
                    label = { Text(stringResource(R.string.chat_page_menu_ai_translator)) },
                    onClick = { navController.navigate(Screen.Translator) },
                )

                Spacer(Modifier.weight(1f))

                DrawerAction(
                    icon = {
                        Icon(HugeIcons.Settings03, null)
                    },
                    label = { Text(stringResource(R.string.settings)) },
                    onClick = {
                        navController.navigate(Screen.Setting)
                    },
                )
            }
        }
    }

    // 昵称编辑对话框
    nicknameEditState.EditStateContent { nickname, onUpdate ->
        AlertDialog(
            onDismissRequest = {
                nicknameEditState.dismiss()
            },
            title = {
                Text(stringResource(R.string.chat_page_edit_nickname))
            },
            text = {
                OutlinedTextField(
                    value = nickname,
                    onValueChange = onUpdate,
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    placeholder = { Text(stringResource(R.string.chat_page_nickname_placeholder)) }
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        nicknameEditState.confirm()
                    }
                ) {
                    Text(stringResource(R.string.common_save))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        nicknameEditState.dismiss()
                    }
                ) {
                    Text(stringResource(R.string.common_cancel))
                }
            }
        )
    }

    // 新建文件夹对话框
    if (showCreateFolderDialog) {
        var name by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showCreateFolderDialog = false },
            title = { Text(stringResource(R.string.chat_page_create_folder)) },
            text = {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    placeholder = { Text(stringResource(R.string.chat_page_folder_name)) }
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        drawerVm.createFolder(name)
                        showCreateFolderDialog = false
                    },
                    enabled = name.isNotBlank()
                ) { Text(stringResource(R.string.common_save)) }
            },
            dismissButton = {
                TextButton(onClick = { showCreateFolderDialog = false }) {
                    Text(stringResource(R.string.common_cancel))
                }
            }
        )
    }

    // 重命名文件夹对话框
    folderToRename?.let { folder ->
        var name by remember(folder.id) { mutableStateOf(folder.name) }
        AlertDialog(
            onDismissRequest = { folderToRename = null },
            title = { Text(stringResource(R.string.chat_page_rename_folder)) },
            text = {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        drawerVm.renameFolder(folder.id, name)
                        folderToRename = null
                    },
                    enabled = name.isNotBlank()
                ) { Text(stringResource(R.string.common_save)) }
            },
            dismissButton = {
                TextButton(onClick = { folderToRename = null }) {
                    Text(stringResource(R.string.common_cancel))
                }
            }
        )
    }

    // 删除文件夹确认
    folderToDelete?.let { folder ->
        AlertDialog(
            onDismissRequest = { folderToDelete = null },
            title = { Text(stringResource(R.string.chat_page_delete_folder)) },
            text = { Text(stringResource(R.string.chat_page_delete_folder_confirm, folder.name)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (drawerVm.deleteFolder(folder.id)) {
                            folderToDelete = null
                            conversations.refresh()
                        } else {
                            toaster.show(context.getString(R.string.chat_page_delete_folder_generating), type = ToastType.Warning)
                        }
                    }
                ) { Text(stringResource(R.string.common_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { folderToDelete = null }) {
                    Text(stringResource(R.string.common_cancel))
                }
            }
        )
    }

    val moveTo: (MoveTarget) -> Unit = { target ->
        conversationsToMove.forEach {
            if (it.id == current.id) {
                drawerVm.selectFolderAfterAssistantChange(target.assistantId, target.folderId)
            }
            vm.moveConversationToAssistant(it, target.assistantId, target.folderId)
        }
        conversationsToMove = emptyList()
        pendingMoveTarget = null
        conversations.refresh()
    }

    val moveTargetListState = rememberLazyListState()
    LaunchedEffect(conversationsToMove, allFolders) {
        if (conversationsToMove.isNotEmpty() && moveCreateFolderAssistant == null && pendingMoveTarget == null) {
            val currentAssistant = settings.getCurrentAssistant()
            var index = 0
            var found = false
            for (assistant in settings.assistants) {
                if (assistant.id == currentAssistant.id) {
                    found = true
                    break
                }
                index += 1 // 助手标题
                index += 1 // 未分组
                index += allFolders[assistant.id].orEmpty().size
            }
            if (found) {
                moveTargetListState.scrollToItem(index)
            }
        }
    }

    if (conversationsToMove.isNotEmpty() && moveCreateFolderAssistant == null && pendingMoveTarget == null) {
        AlertDialog(
            onDismissRequest = { conversationsToMove = emptyList() },
            title = { Text(stringResource(R.string.conversation_move_to)) },
            text = {
                LazyColumn(
                    modifier = Modifier.heightIn(max = 480.dp),
                    state = moveTargetListState,
                ) {
                    settings.assistants.forEach { assistant ->
                        item(key = "assistant_${assistant.id}") {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                UIAvatar(
                                    name = assistant.name,
                                    value = assistant.avatar,
                                    invertDefaultAvatarInDarkMode = true,
                                    modifier = Modifier.size(40.dp),
                                )
                                Text(
                                    assistant.name.ifBlank { stringResource(R.string.assistant_page_default_assistant) },
                                    style = MaterialTheme.typography.titleMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f),
                                )
                                Icon(
                                    HugeIcons.FolderAdd,
                                    contentDescription = stringResource(R.string.chat_page_create_folder),
                                    modifier = Modifier
                                        .clip(CircleShape)
                                        .clickable { moveCreateFolderAssistant = assistant }
                                        .padding(8.dp)
                                        .size(20.dp),
                                )
                            }
                        }
                        item(key = "unfiled_${assistant.id}") {
                            val isCurrentPosition = conversationsToMove.all {
                                it.assistantId == assistant.id && it.folderId == null
                            }
                            Row(
                                    modifier = Modifier.fillMaxWidth().padding(start = 32.dp)
                                        .clickable(enabled = !isCurrentPosition) {
                                            pendingMoveTarget = MoveTarget(
                                                assistant.id,
                                                null,
                                                "${assistant.name} / ${context.getString(R.string.chat_page_uncategorized)}",
                                            )
                                        }.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                ) {
                                    Icon(HugeIcons.Folder01, contentDescription = null)
                                    Text(stringResource(R.string.chat_page_uncategorized), modifier = Modifier.weight(1f))
                                    if (isCurrentPosition) {
                                        Text(
                                            stringResource(R.string.chat_page_current_location),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
                                        )
                                    }
                                }
                        }
                        items(
                            items = allFolders[assistant.id].orEmpty(),
                            key = { "folder_${it.id}" },
                        ) { folder ->
                            val isCurrentPosition = conversationsToMove.all {
                                it.assistantId == assistant.id && it.folderId == folder.id
                            }
                            Row(
                                    modifier = Modifier.fillMaxWidth().padding(start = 32.dp)
                                        .clickable(enabled = !isCurrentPosition) {
                                            pendingMoveTarget = MoveTarget(assistant.id, folder.id, "${assistant.name} / ${folder.name}")
                                        }.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                ) {
                                    Icon(HugeIcons.Folder01, contentDescription = null)
                                    Text(
                                        folder.name,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f),
                                    )
                                    if (isCurrentPosition) {
                                        Text(
                                            stringResource(R.string.chat_page_current_location),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
                                        )
                                    }
                                }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { conversationsToMove = emptyList() }) {
                    Text(stringResource(R.string.common_cancel))
                }
            },
        )
    }

    pendingMoveTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { pendingMoveTarget = null },
            title = { Text(stringResource(R.string.chat_page_confirm_move)) },
            text = {
                Text(
                    if (moveStartedFromMultiSelect) {
                        stringResource(
                            R.string.chat_page_confirm_move_desc,
                            conversationsToMove.size,
                            target.label,
                        )
                    } else {
                        stringResource(
                            R.string.chat_page_confirm_single_move_desc,
                            conversationsToMove.firstOrNull()
                                ?.title
                                ?.ifBlank { stringResource(R.string.chat_page_new_message) }
                                ?: stringResource(R.string.chat_page_new_message),
                            target.label,
                        )
                    }
                )
            },
            confirmButton = {
                TextButton(onClick = { moveTo(target) }) { Text(stringResource(R.string.common_confirm_action)) }
            },
            dismissButton = {
                TextButton(onClick = { pendingMoveTarget = null }) { Text(stringResource(R.string.common_cancel)) }
            },
        )
    }

    moveCreateFolderAssistant?.let { assistant ->
        var name by remember(assistant.id) { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { moveCreateFolderAssistant = null },
            title = { Text(stringResource(R.string.chat_page_create_folder_in, assistant.name)) },
            text = {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = {
                TextButton(
                    enabled = name.isNotBlank(),
                    onClick = {
                        scope.launch {
                            drawerVm.createFolder(assistant.id, name)?.let { folder ->
                                moveCreateFolderAssistant = null
                                pendingMoveTarget = MoveTarget(assistant.id, folder.id, "${assistant.name} / ${folder.name}")
                            }
                        }
                    },
                ) { Text(stringResource(R.string.common_confirm_action)) }
            },
            dismissButton = {
                TextButton(onClick = { moveCreateFolderAssistant = null }) {
                    Text(stringResource(R.string.common_cancel))
                }
            },
        )
    }
}

@Composable
private fun DrawerActions(navController: Navigator) {
    Column {
        // 搜索入口
        Surface(
            onClick = { navController.navigate(Screen.MessageSearch()) },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp),
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.surfaceContainerLow,
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Icon(
                    imageVector = HugeIcons.Search01,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = stringResource(R.string.search_page_title),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }

    }
}

@Composable
private fun DrawerAction(
    modifier: Modifier = Modifier,
    icon: @Composable () -> Unit,
    label: @Composable () -> Unit,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        modifier = modifier,
        color = MaterialTheme.colorScheme.primaryContainer,
        shape = CircleShape,
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        Tooltip(
            tooltip = {
                label()
            }
        ) {
            Box(
                modifier = Modifier
                    .padding(10.dp)
                    .size(20.dp),
            ) {
                icon()
            }
        }
    }
}

@Composable
private fun FolderBar(
    folders: List<Folder>,
    selectedFolderId: Uuid?,
    onSelect: (Uuid?) -> Unit,
    onCreate: () -> Unit,
    onRename: (Folder) -> Unit,
    onDelete: (Folder) -> Unit,
    onMoveForward: (Folder) -> Unit,
    onMoveBackward: (Folder) -> Unit,
) {
    LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        item {
            FolderChip(
                label = stringResource(R.string.chat_page_folder_default),
                selected = selectedFolderId == null,
                onClick = { onSelect(null) },
                onLongClick = {},
            )
        }
        items(folders) { folder ->
            var menuExpanded by remember { mutableStateOf(false) }
            val folderIndex = folders.indexOf(folder)
            Box {
                FolderChip(
                    label = folder.name,
                    icon = HugeIcons.Folder01,
                    selected = selectedFolderId == folder.id,
                    onClick = { onSelect(folder.id) },
                    onLongClick = { menuExpanded = true },
                )
                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false },
                    shape = me.rerere.rikkahub.ui.theme.rememberScreenEdgeCornerShape(),
                ) {
                    if (folderIndex > 0) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.chat_page_folder_move_forward)) },
                            leadingIcon = { Icon(HugeIcons.ArrowLeft01, null) },
                            onClick = {
                                onMoveForward(folder)
                                menuExpanded = false
                            }
                        )
                    }
                    if (folderIndex < folders.lastIndex) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.chat_page_folder_move_backward)) },
                            leadingIcon = { Icon(HugeIcons.ArrowRight01, null) },
                            onClick = {
                                onMoveBackward(folder)
                                menuExpanded = false
                            }
                        )
                    }
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.common_rename)) },
                        leadingIcon = { Icon(HugeIcons.PencilEdit01, null) },
                        onClick = {
                            onRename(folder)
                            menuExpanded = false
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.common_delete)) },
                        leadingIcon = { Icon(HugeIcons.Delete01, null) },
                        onClick = {
                            onDelete(folder)
                            menuExpanded = false
                        }
                    )
                }
            }
        }
        item {
            FolderChip(
                label = stringResource(R.string.chat_page_folder_add),
                icon = HugeIcons.FolderAdd,
                selected = false,
                onClick = onCreate,
                onLongClick = {},
            )
        }
    }
}

@Composable
private fun FolderChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    icon: ImageVector? = null,
) {
    Surface(
        shape = CircleShape,
        color = if (selected) {
            MaterialTheme.colorScheme.secondaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainerLow
        },
        modifier = Modifier
            .clip(CircleShape)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick,
            )
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            if (icon != null) {
                Icon(icon, null, modifier = Modifier.size(14.dp))
            }
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

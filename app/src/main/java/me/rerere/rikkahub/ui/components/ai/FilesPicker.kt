package me.rerere.rikkahub.ui.components.ai

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Text
import androidx.compose.material3.SheetValue
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Job
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.rerere.ai.provider.ProviderSetting
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.ArrowRight01
import me.rerere.hugeicons.stroke.Camera01
import me.rerere.hugeicons.stroke.Codesandbox
import me.rerere.hugeicons.stroke.ComputerTerminal01
import me.rerere.hugeicons.stroke.Files02
import me.rerere.hugeicons.stroke.Image02
import me.rerere.hugeicons.stroke.MusicNote03
import me.rerere.hugeicons.stroke.Package
import me.rerere.hugeicons.stroke.Package01
import me.rerere.hugeicons.stroke.Settings02
import me.rerere.hugeicons.stroke.Video01
import me.rerere.rikkahub.R
import me.rerere.rikkahub.Screen
import me.rerere.rikkahub.data.ai.mcp.McpManager
import me.rerere.rikkahub.data.ai.mcp.McpStatus
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.data.datastore.getCurrentChatModel
import me.rerere.rikkahub.data.datastore.findProvider
import me.rerere.rikkahub.data.db.entity.WorkspaceEntity
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.data.model.Conversation
import me.rerere.rikkahub.data.repository.WorkspaceRepository
import me.rerere.rikkahub.ui.components.ui.ExtensionSelector
import me.rerere.rikkahub.ui.components.ui.CardGroup
import me.rerere.rikkahub.ui.components.ui.CardGroupRow
import me.rerere.rikkahub.ui.components.ui.CardGroupScope
import me.rerere.rikkahub.ui.context.LocalNavController
import me.rerere.rikkahub.ui.context.LocalSettings
import me.rerere.rikkahub.ui.hooks.ChatInputState
import me.rerere.workspace.WorkspaceShellStatus
import org.koin.compose.koinInject
import kotlin.uuid.Uuid

@Composable
internal fun FilesPicker(
    conversation: Conversation,
    assistant: Assistant,
    state: ChatInputState,
    mcpManager: McpManager,
    onCompressContext: (additionalPrompt: String, targetTokens: Int, keepRecentMessages: Int) -> Job,
    onUpdateAssistant: (Assistant) -> Unit,
    onSelectSearch: (mode: SearchMode, serviceIndex: Int?) -> Unit,
    onUpdateConversation: (Conversation) -> Unit,
    showInjectionSheet: Boolean,
    onShowInjectionSheetChange: (Boolean) -> Unit,
    showCompressDialog: Boolean,
    onShowCompressDialogChange: (Boolean) -> Unit,
    onDismiss: () -> Unit,
    onTakePic: () -> Unit,
    onPickImage: () -> Unit,
    onPickVideo: () -> Unit,
    onPickAudio: () -> Unit,
    onPickFile: () -> Unit,
) {
    val settings = LocalSettings.current
    val provider = settings.getCurrentChatModel()?.findProvider(providers = settings.providers)
    val navController = LocalNavController.current
    val workspaceRepository: WorkspaceRepository = koinInject()
    val workspaces by workspaceRepository.listFlow().collectAsState(initial = emptyList())
    var showSearchPicker by remember { mutableStateOf(false) }
    var showMcpPicker by remember { mutableStateOf(false) }
    var showWorkspaceSheet by remember { mutableStateOf(false) }
    var showCwdSheet by remember { mutableStateOf(false) }
    val boundWorkspace = remember(workspaces, assistant.workspaceId) {
        workspaces.find { it.id == assistant.workspaceId?.toString() }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 8.dp, end = 8.dp, top = 16.dp, bottom = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        CardGroupRow(
            cornerInset = 24.dp,
        ) {
            photoButtonItems(
                provider = provider,
                onTakePic = onTakePic,
                onPickImage = onPickImage,
                onPickVideo = onPickVideo,
                onPickAudio = onPickAudio,
                onPickFile = onPickFile,
            )
        }

        CardGroup(cornerInset = 24.dp) {
            if (settings.mcpServers.isNotEmpty()) {
                mcpItem(
                    assistant = assistant,
                    servers = settings.mcpServers,
                    mcpManager = mcpManager,
                    onClick = { showMcpPicker = true },
                )
            }

            item(
                trailingContent = {
                    ContextCachePicker(
                        value = assistant.contextCache,
                        onValueChange = { onUpdateAssistant(assistant.copy(contextCache = it)) },
                    )
                },
            ) {
                Text(stringResource(R.string.assistant_page_context_cache))
            }

            item(
                trailingContent = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        SearchPickerIcon(
                            enableSearch = assistant.enableWebSearch,
                            useBuiltInSearch = assistant.useBuiltInSearch,
                            settings = settings,
                            model = settings.getCurrentChatModel(),
                        )
                        Icon(
                            imageVector = HugeIcons.ArrowRight01,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                onClick = { showSearchPicker = true },
            ) {
                Text(stringResource(R.string.search_ability_search))
            }

            // Extensions (Quick Messages + Prompt Injections + Skills)
            val modeAndLorebookCount =
                if (assistant.allowConversationPromptInjection) {
                    conversation.modeInjectionIds.size + conversation.lorebookIds.size
                } else {
                    assistant.modeInjectionIds.size + assistant.lorebookIds.size
                }
            val activeCount =
                assistant.quickMessageIds.size +
                    modeAndLorebookCount +
                    assistant.enabledSkills.size
            item(
                leadingContent = {
                    Icon(
                        imageVector = HugeIcons.Package,
                        contentDescription = stringResource(R.string.assistant_page_tab_extensions),
                    )
                },
                trailingContent = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        if (activeCount > 0) {
                            Text(
                                text = activeCount.toString(),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                        Icon(
                            imageVector = HugeIcons.ArrowRight01,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                onClick = { onShowInjectionSheetChange(true) },
            ) { Text(stringResource(R.string.assistant_page_tab_extensions)) }

            // Compress History Button
            item(
                leadingContent = {
                    Icon(
                        imageVector = HugeIcons.Package01,
                        contentDescription = stringResource(R.string.chat_page_compress_context),
                    )
                },
                trailingContent = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        if (conversation.messageNodes.isNotEmpty()) {
                            Text(
                                text = stringResource(R.string.chat_page_message_count, conversation.messageNodes.size),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Icon(
                            imageVector = HugeIcons.ArrowRight01,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                onClick = { onShowCompressDialogChange(true) },
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(stringResource(R.string.chat_page_compress_context))
                    if (assistant.contextMessageLimit > 0) {
                        Text(
                            text = stringResource(R.string.chat_page_context_message_limit_current, assistant.contextMessageLimit),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.clickable {
                                onDismiss()
                                navController.navigate(
                                    Screen.AssistantBasic(
                                        id = assistant.id.toString(),
                                        scrollToContextLimit = true,
                                    )
                                )
                            },
                        )
                    }
                }
            }

            if (workspaces.isNotEmpty()) {
                workspaceItems(
                    assistant = assistant,
                    conversation = conversation,
                    boundWorkspace = boundWorkspace,
                    onClickWorkspace = { showWorkspaceSheet = true },
                    onClickCwd = { showCwdSheet = true },
                    onNavigateToDetail = { id ->
                        onDismiss()
                        navController.navigate(Screen.WorkspaceDetail(id))
                    },
                    onNavigateToTerminal = { id ->
                        onDismiss()
                        navController.navigate(Screen.WorkspaceTerminal(id))
                    },
                )
            }
        }
    }

    SearchPickerSheet(
        show = showSearchPicker,
        enableSearch = assistant.enableWebSearch,
        useBuiltInSearch = assistant.useBuiltInSearch,
        settings = settings,
        onSelectSearch = onSelectSearch,
        model = settings.getCurrentChatModel(),
        onDismiss = { showSearchPicker = false },
    )

    // MCP Picker Bottom Sheet
    if (showMcpPicker) {
        val syncingStatus by mcpManager.syncingStatus.collectAsStateWithLifecycle()
        McpPickerSheet(
            assistant = assistant,
            servers = settings.mcpServers,
            loading = syncingStatus.values.any { it == McpStatus.Connecting },
            onUpdateAssistant = onUpdateAssistant,
            onDismiss = { showMcpPicker = false },
        )
    }

    // Workspace Select Sheet
    if (showWorkspaceSheet) {
        WorkspaceSelectSheet(
            assistant = assistant,
            workspaces = workspaces,
            onSelect = { workspaceId ->
                val newId = workspaceId?.let { Uuid.parse(it) }
                if (newId != assistant.workspaceId) {
                    onUpdateAssistant(assistant.copy(workspaceId = newId))
                    if (conversation.workspaceCwd != null) {
                        onUpdateConversation(conversation.copy(workspaceCwd = null))
                    }
                }
                showWorkspaceSheet = false
            },
            onManage = {
                showWorkspaceSheet = false
                onDismiss()
                navController.navigate(Screen.Workspaces)
            },
            onDismiss = { showWorkspaceSheet = false },
        )
    }

    // Workspace CWD Sheet
    if (showCwdSheet && boundWorkspace != null) {
        WorkspaceCwdPickerSheet(
            workspaceId = boundWorkspace.id,
            currentCwd = conversation.workspaceCwd,
            onSelectCwd = { newCwd ->
                onUpdateConversation(conversation.copy(workspaceCwd = newCwd))
            },
            onDismiss = { showCwdSheet = false },
        )
    }

    // Injection Bottom Sheet
    if (showInjectionSheet) {
        InjectionQuickConfigSheet(
            conversation = conversation,
            assistant = assistant,
            settings = settings,
            onUpdateAssistant = onUpdateAssistant,
            onUpdateConversation = onUpdateConversation,
            onDismiss = { onShowInjectionSheetChange(false) },
            onDismissAll = onDismiss,
        )
    }

    // Compress Context Dialog
    if (showCompressDialog) {
        CompressContextDialog(onDismiss = {
            onShowCompressDialogChange(false)
        }, onConfirm = { additionalPrompt, targetTokens, keepRecentMessages ->
            onCompressContext(additionalPrompt, targetTokens, keepRecentMessages)
        })
    }
}

private fun CardGroupScope.workspaceItems(
    assistant: Assistant,
    conversation: Conversation,
    boundWorkspace: WorkspaceEntity?,
    onClickWorkspace: () -> Unit,
    onClickCwd: () -> Unit,
    onNavigateToDetail: (String) -> Unit,
    onNavigateToTerminal: (String) -> Unit,
) {
    item(
        leadingContent = {
            Icon(
                imageVector = HugeIcons.Codesandbox,
                contentDescription = stringResource(R.string.assistant_page_workspace),
            )
        },
        supportingContent = {
            Text(
                text = boundWorkspace?.name ?: stringResource(R.string.assistant_page_workspace_unbound),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        trailingContent = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                if (boundWorkspace != null) {
                    if (boundWorkspace.shellStatus == WorkspaceShellStatus.READY.name) {
                        IconButton(onClick = { onNavigateToTerminal(boundWorkspace.id) }) {
                            Icon(
                                imageVector = HugeIcons.ComputerTerminal01,
                                contentDescription = stringResource(R.string.workspace_terminal),
                            )
                        }
                    }
                    IconButton(onClick = { onNavigateToDetail(boundWorkspace.id) }) {
                        Icon(
                            imageVector = HugeIcons.Settings02,
                            contentDescription = stringResource(R.string.workspace_detail),
                        )
                    }
                }
                Icon(
                    imageVector = HugeIcons.ArrowRight01,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        onClick = onClickWorkspace,
    ) {
        Text(stringResource(R.string.assistant_page_workspace))
    }

    if (boundWorkspace != null) {
        item(
            trailingContent = {
                Text(
                    text = conversation.workspaceCwd ?: "/workspace",
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
            onClick = onClickCwd,
        ) {
            Text(stringResource(R.string.workspace_cwd_current))
        }
    }
}

@Composable
private fun InjectionQuickConfigSheet(
    conversation: Conversation,
    assistant: Assistant,
    settings: Settings,
    onUpdateAssistant: (Assistant) -> Unit,
    onUpdateConversation: (Conversation) -> Unit,
    onDismiss: () -> Unit,
    onDismissAll: () -> Unit,
) {
    val sheetState = rememberBottomSheetState(initialValue = SheetValue.Hidden, enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded))
    val navController = LocalNavController.current

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.75f)
                .padding(horizontal = 16.dp),
        ) {
            ExtensionSelector(
                assistant = assistant,
                settings = settings,
                onUpdate = onUpdateAssistant,
                conversation = conversation,
                onUpdateConversation = onUpdateConversation,
                modifier = Modifier.weight(1f),
                onNavigateToQuickMessages = {
                    onDismissAll()
                    navController.navigate(Screen.QuickMessages)
                },
                onNavigateToModeInjections = {
                    onDismissAll()
                    navController.navigate(Screen.ModeInjections)
                },
                onNavigateToLorebooks = {
                    onDismissAll()
                    navController.navigate(Screen.Lorebooks)
                },
                onNavigateToSkills = {
                    onDismissAll()
                    navController.navigate(Screen.Skills)
                })

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

private fun CardGroupScope.photoButtonItems(
    provider: ProviderSetting?,
    onTakePic: () -> Unit,
    onPickImage: () -> Unit,
    onPickVideo: () -> Unit,
    onPickAudio: () -> Unit,
    onPickFile: () -> Unit,
) {
    item(onClick = onTakePic) {
        BigIconTextButton(
            icon = { Icon(HugeIcons.Camera01, null) },
            text = { Text(stringResource(R.string.take_picture)) },
        )
    }
    item(onClick = onPickImage) {
        BigIconTextButton(
            icon = { Icon(HugeIcons.Image02, null) },
            text = { Text(stringResource(R.string.photo)) },
        )
    }
    if (provider != null && provider is ProviderSetting.Google) {
        item(onClick = onPickVideo) {
            BigIconTextButton(
                icon = { Icon(HugeIcons.Video01, null) },
                text = { Text(stringResource(R.string.video)) },
            )
        }
        item(onClick = onPickAudio) {
            BigIconTextButton(
                icon = { Icon(HugeIcons.MusicNote03, null) },
                text = { Text(stringResource(R.string.audio)) },
            )
        }
    }
    item(onClick = onPickFile) {
        BigIconTextButton(
            icon = { Icon(HugeIcons.Files02, null) },
            text = { Text(stringResource(R.string.file)) },
        )
    }
}

@Composable
private fun BigIconTextButton(
    modifier: Modifier = Modifier,
    icon: @Composable () -> Unit,
    text: @Composable () -> Unit,
) {
    Column(
        modifier = modifier
            .padding(horizontal = 8.dp, vertical = 10.dp)
            .wrapContentWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)) {
        icon()
        ProvideTextStyle(MaterialTheme.typography.bodySmall) {
            text()
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun BigIconTextButtonPreview() {
    CardGroupRow(
        modifier = Modifier.padding(16.dp),
    ) {
        item(onClick = {}) {
            BigIconTextButton(
                icon = { Icon(HugeIcons.Image02, null) },
                text = { Text(stringResource(R.string.photo)) },
            )
        }
        item(onClick = {}) {
            BigIconTextButton(
                icon = { Icon(HugeIcons.Camera01, null) },
                text = { Text(stringResource(R.string.take_picture)) },
            )
        }
        item(onClick = {}) {
            BigIconTextButton(
                icon = { Icon(HugeIcons.Files02, null) },
                text = { Text(stringResource(R.string.file)) },
            )
        }
    }
}

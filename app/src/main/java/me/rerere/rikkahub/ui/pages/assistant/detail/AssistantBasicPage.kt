package me.rerere.rikkahub.ui.pages.assistant.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.rerere.ai.provider.ModelType
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.ArrowRight01
import me.rerere.hugeicons.stroke.Delete01
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.data.datastore.findModelById
import me.rerere.rikkahub.data.db.entity.WorkspaceEntity
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.ui.pages.assistant.AssistantVM
import me.rerere.rikkahub.ui.components.ui.RikkaConfirmDialog
import me.rerere.rikkahub.ui.context.LocalNavController
import me.rerere.rikkahub.ui.components.ai.ModelListSheet
import me.rerere.rikkahub.ui.components.ai.rememberModelListState
import me.rerere.rikkahub.ui.components.ai.ContextCachePicker
import me.rerere.rikkahub.ui.components.ai.ReasoningButton
import me.rerere.rikkahub.ui.components.ai.SearchPickerIcon
import me.rerere.rikkahub.ui.components.ai.SearchPickerSheet
import me.rerere.rikkahub.ui.components.nav.BackButton
import me.rerere.rikkahub.ui.components.ui.CardGroup
import me.rerere.rikkahub.ui.components.ui.Select
import me.rerere.rikkahub.ui.components.ui.TagsInput
import me.rerere.rikkahub.ui.components.ui.UIAvatar
import me.rerere.rikkahub.ui.hooks.heroAnimation
import me.rerere.rikkahub.ui.theme.CustomColors
import me.rerere.rikkahub.utils.toFixed
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf
import kotlin.math.roundToInt
import kotlin.uuid.Uuid
import me.rerere.rikkahub.data.model.Tag as DataTag

@Composable
fun AssistantBasicPage(
    id: String,
    requestSettingsOnly: Boolean = false,
) {
    val vm: AssistantDetailVM = koinViewModel(
        parameters = {
            parametersOf(id)
        }
    )
    val assistantVm: AssistantVM = koinViewModel()
    val navController = LocalNavController.current
    val assistant by vm.assistant.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val providers by vm.providers.collectAsStateWithLifecycle()
    val tags by vm.tags.collectAsStateWithLifecycle()
    val workspaces by vm.workspaces.collectAsStateWithLifecycle()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    Scaffold(
        topBar = {
            LargeFlexibleTopAppBar(
                title = {
                    Text(
                        stringResource(
                            if (requestSettingsOnly) {
                                R.string.assistant_page_tab_request
                            } else {
                                R.string.assistant_page_tab_basic
                            }
                        )
                    )
                },
                navigationIcon = {
                    BackButton()
                },
                scrollBehavior = scrollBehavior,
                colors = CustomColors.topBarColors,
            )
        },
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = CustomColors.topBarColors.containerColor,
    ) { innerPadding ->
        AssistantBasicContent(
            innerPadding = innerPadding,
            assistant = assistant,
            settings = settings,
            providers = providers,
            tags = tags,
            workspaces = workspaces,
            onUpdate = { vm.update(it) },
            onUpdateSearchService = { vm.updateSearchService(it) },
            vm = vm,
            onDelete = {
                assistantVm.removeAssistant(assistant)
                navController.navigate(me.rerere.rikkahub.Screen.Assistant) {
                    popUpTo(me.rerere.rikkahub.Screen.Assistant)
                    launchSingleTop = true
                }
            },
            requestSettingsOnly = requestSettingsOnly,
        )
    }
}

@Composable
internal fun AssistantBasicContent(
    innerPadding: PaddingValues,
    assistant: Assistant,
    settings: Settings,
    providers: List<me.rerere.ai.provider.ProviderSetting>,
    tags: List<DataTag>,
    workspaces: List<WorkspaceEntity>,
    onUpdate: (Assistant) -> Unit,
    onUpdateSearchService: (Int) -> Unit,
    vm: AssistantDetailVM,
    onDelete: () -> Unit = {},
    requestSettingsOnly: Boolean = false,
) {
    val chatModelState = rememberModelListState(
        modelId = assistant.chatModelId,
        providers = providers,
        type = ModelType.CHAT,
    )
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showSearchPicker by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
            .padding(innerPadding)
            .imePadding(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        if (!requestSettingsOnly) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            UIAvatar(
                value = assistant.avatar,
                name = assistant.name.ifBlank { stringResource(R.string.assistant_page_default_assistant) },
                invertDefaultAvatarInDarkMode = true,
                onUpdate = { avatar ->
                    onUpdate(
                        assistant.copy(
                            avatar = avatar
                        )
                    )
                },
                modifier = Modifier
                    .size(80.dp)
                    .heroAnimation("assistant_${assistant.id}")
            )
        }

        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        CardGroup(
            continueToNext = true,
        ) {
            FormItem(
                label = {
                    Text(stringResource(R.string.assistant_page_name))
                },
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),

                ) {
                OutlinedTextField(
                    value = assistant.name,
                    onValueChange = {
                        onUpdate(
                            assistant.copy(
                                name = it
                            )
                        )
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }


            FormItem(
                label = {
                    Text(stringResource(R.string.assistant_page_tags))
                },
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
                TagsInput(
                    value = assistant.tags,
                    tags = tags,
                    onValueChange = { tagIds, tagList ->
                        vm.updateTags(tagIds, tagList)
                    },
                )
            }


            FormItem(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                label = {
                    Text(stringResource(R.string.assistant_page_use_assistant_avatar))
                },
                description = {
                    Text(stringResource(R.string.assistant_page_use_assistant_avatar_desc))
                },
                tail = {
                    Switch(
                        checked = assistant.useAssistantAvatar,
                        onCheckedChange = {
                            onUpdate(
                                assistant.copy(
                                    useAssistantAvatar = it
                                )
                            )
                        }
                    )
                }
            )

            FormItem(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                label = {
                    Text(stringResource(R.string.assistant_page_context_message_limit))
                },
                description = {
                    Text(stringResource(R.string.assistant_page_context_message_limit_desc))
                },
            ) {
                var contextMessageLimitInput by remember(assistant.id) {
                    mutableStateOf(
                        assistant.contextMessageLimit.takeIf { it > 0 }?.toString().orEmpty()
                    )
                }
                OutlinedTextField(
                    value = contextMessageLimitInput,
                    onValueChange = { value ->
                        if (value.isEmpty()) {
                            contextMessageLimitInput = ""
                            onUpdate(assistant.copy(contextMessageLimit = 0))
                        } else if (value.all(Char::isDigit)) {
                            value.toIntOrNull()?.let { limit ->
                                if (limit > 0) {
                                    contextMessageLimitInput = value
                                    onUpdate(assistant.copy(contextMessageLimit = limit))
                                }
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .onFocusChanged { focusState ->
                            if (!focusState.isFocused) {
                                val normalizedLimit = contextMessageLimitInput
                                    .toIntOrNull()
                                    ?.coerceAtLeast(MIN_CONTEXT_MESSAGE_LIMIT)
                                    ?: 0
                                contextMessageLimitInput = normalizedLimit
                                    .takeIf { it > 0 }
                                    ?.toString()
                                    .orEmpty()
                                onUpdate(assistant.copy(contextMessageLimit = normalizedLimit))
                            }
                        },
                    placeholder = {
                        Text(stringResource(R.string.assistant_page_context_message_limit_placeholder))
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                )
                if (assistant.contextMessageLimit > 0) {
                    Text(
                        text = stringResource(R.string.assistant_page_context_message_limit_warning),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }

            FormItem(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                label = {
                    Text(stringResource(R.string.assistant_page_gradient_background))
                },
                description = {
                    Text(stringResource(R.string.assistant_page_gradient_background_desc))
                },
                tail = {
                    Switch(
                        checked = assistant.useGradientBackground,
                        onCheckedChange = {
                            onUpdate(assistant.copy(useGradientBackground = it))
                        }
                    )
                }
            )
        }

        if (!assistant.useGradientBackground) {
            BackgroundPicker(
                background = assistant.background,
                backgroundOpacity = assistant.backgroundOpacity,
                continueFromPrevious = true,
                continueToNext = true,
                onUpdate = { background ->
                    onUpdate(assistant.copy(background = background))
                }
            )
        }
        if (!assistant.useGradientBackground && assistant.background != null) {
            val backgroundOpacity = assistant.backgroundOpacity.coerceIn(0.1f, 1f)
            CardGroup(
                continueFromPrevious = true,
                continueToNext = true,
            ) {
                FormItem(
                    label = {
                        Text(stringResource(R.string.assistant_page_background_opacity))
                    },
                    description = {
                        Text(stringResource(R.string.assistant_page_background_opacity_desc))
                    }
                ) {
                    Slider(
                        value = backgroundOpacity,
                        onValueChange = {
                            onUpdate(
                                assistant.copy(
                                    backgroundOpacity = it.toFixed(2).toFloatOrNull()
                                        ?.coerceIn(0.1f, 1f) ?: 1.0f
                                )
                            )
                        },
                        valueRange = 0.1f..1f,
                        steps = 8,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(
                        text = stringResource(
                            R.string.assistant_page_background_opacity_value,
                            (backgroundOpacity * 100).roundToInt()
                        ),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.75f),
                    )
                }
            }
        }
        CardGroup(
            continueFromPrevious = true,
        ) {
            item(
                onClick = { showSearchPicker = true },
                headlineContent = {
                    Text(stringResource(R.string.search_ability_search))
                },
                supportingContent = {
                    Text(stringResource(R.string.assistant_page_web_search_desc))
                },
                trailingContent = {
                    SearchPickerIcon(
                        enableSearch = assistant.enableWebSearch,
                        settings = settings,
                        model = settings.findModelById(assistant.chatModelId ?: settings.chatModelId),
                    )
                },
            )
            FormItem(
                label = {
                    Text(stringResource(R.string.assistant_page_workspace))
                },
                description = {
                    Text(stringResource(R.string.assistant_page_workspace_desc))
                },
            ) {
                val selectedWorkspace = workspaces.find { it.id == assistant.workspaceId?.toString() }
                Select(
                    options = listOf<WorkspaceEntity?>(null) + workspaces,
                    selectedOption = selectedWorkspace,
                    onOptionSelected = { workspace ->
                        onUpdate(
                            assistant.copy(
                                workspaceId = workspace?.id?.let { Uuid.parse(it) }
                            )
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                    optionToString = { workspace ->
                        workspace?.name ?: stringResource(R.string.workspace_no_binding)
                    },
                )
            }
        }
        SearchPickerSheet(
            show = showSearchPicker,
            enableSearch = assistant.enableWebSearch,
            settings = settings,
            onToggleSearch = { enabled ->
                onUpdate(assistant.copy(enableWebSearch = enabled))
            },
            onUpdateSearchService = onUpdateSearchService,
            model = settings.findModelById(assistant.chatModelId ?: settings.chatModelId),
            onDismiss = { showSearchPicker = false },
        )
        }
        }

        if (requestSettingsOnly) {
            CardGroup {
                item(
                    onClick = { chatModelState.open() },
                    headlineContent = {
                        Text(stringResource(R.string.assistant_page_chat_model))
                    },
                    supportingContent = {
                        Text(stringResource(R.string.assistant_page_chat_model_desc))
                    },
                    trailingContent = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Text(
                                text = chatModelState.currentModel?.displayName
                                    ?: stringResource(R.string.model_list_select_model),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                            )
                            Icon(
                                HugeIcons.ArrowRight01,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                            )
                        }
                    },
                )
            FormItem(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                label = {
                    Text(stringResource(R.string.assistant_page_temperature))
                },
                description = {
                    Text(
                        text = buildAnnotatedString {
                            append(stringResource(R.string.assistant_page_temperature_warning))
                        }
                    )
                },
                tail = {
                    Switch(
                        checked = assistant.temperature != null,
                        onCheckedChange = { enabled ->
                            onUpdate(
                                assistant.copy(
                                    temperature = if (enabled) 1.0f else null
                                )
                            )
                        }
                    )
                }
            ) {
                if (assistant.temperature != null) {
                    var temperatureInput by remember(assistant.id) {
                        mutableStateOf(assistant.temperature.toString())
                    }
                    val temperatureValue = temperatureInput.toFloatOrNull()
                    OutlinedTextField(
                        value = temperatureInput,
                        onValueChange = { value ->
                            temperatureInput = value
                            value.toFloatOrNull()?.takeIf { it in 0f..2f }?.let { temperature ->
                                onUpdate(
                                    assistant.copy(
                                        temperature = temperature
                                    )
                                )
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        isError = temperatureValue == null || temperatureValue !in 0f..2f,
                        supportingText = {
                            Text("0 - 2")
                        }
                    )
                }
            }
            FormItem(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                label = {
                    Text(stringResource(R.string.assistant_page_top_p))
                },
                description = {
                    Text(
                        text = buildAnnotatedString {
                            append(stringResource(R.string.assistant_page_top_p_warning))
                        }
                    )
                },
                tail = {
                    Switch(
                        checked = assistant.topP != null,
                        onCheckedChange = { enabled ->
                            onUpdate(
                                assistant.copy(
                                    topP = if (enabled) 1.0f else null
                                )
                            )
                        }
                    )
                }
            ) {
                assistant.topP?.let { topP ->
                    var topPInput by remember(assistant.id) {
                        mutableStateOf(topP.toString())
                    }
                    val topPValue = topPInput.toFloatOrNull()
                    OutlinedTextField(
                        value = topPInput,
                        onValueChange = { value ->
                            topPInput = value
                            value.toFloatOrNull()?.takeIf { it in 0f..1f }?.let { nextTopP ->
                                onUpdate(
                                    assistant.copy(
                                        topP = nextTopP
                                    )
                                )
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        isError = topPValue == null || topPValue !in 0f..1f,
                        supportingText = {
                            Text("0 - 1")
                        }
                    )
                }
            }
            FormItem(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                label = {
                    Text(stringResource(R.string.assistant_page_thinking_budget))
                },
                tail = {
                    ReasoningButton(
                        reasoningLevel = assistant.reasoningLevel,
                        onUpdateReasoningLevel = { level ->
                            onUpdate(assistant.copy(reasoningLevel = level))
                        }
                    )
                },
            )
            FormItem(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                label = { Text(stringResource(R.string.assistant_page_context_cache)) },
                description = {
                    Text(stringResource(R.string.assistant_page_context_cache_desc))
                },
                tail = {
                    ContextCachePicker(
                        value = assistant.contextCache,
                        onValueChange = { onUpdate(assistant.copy(contextCache = it)) },
                    )
                },
            )
            FormItem(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                label = {
                    Text(stringResource(R.string.assistant_page_stream_output))
                },
                description = {
                    Text(stringResource(R.string.assistant_page_stream_output_desc))
                },
                tail = {
                    Switch(
                        checked = assistant.streamOutput,
                        onCheckedChange = {
                            onUpdate(assistant.copy(streamOutput = it))
                        }
                    )
                }
            )
            FormItem(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                label = {
                    Text(stringResource(R.string.assistant_page_max_tokens))
                },
                description = {
                    Text(stringResource(R.string.assistant_page_max_tokens_desc))
                }
            ) {
                OutlinedTextField(
                    value = assistant.maxTokens?.toString() ?: "",
                    onValueChange = { text ->
                        val tokens = if (text.isBlank()) {
                            null
                        } else {
                            text.toIntOrNull()?.takeIf { it > 0 }
                        }
                        onUpdate(
                            assistant.copy(
                                maxTokens = tokens
                            )
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    placeholder = {
                        Text(stringResource(R.string.assistant_page_max_tokens_no_limit))
                    }
                )
            }
            FormItem(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                label = { Text(stringResource(R.string.assistant_page_custom_headers)) },
            ) {
                CustomHeaders(
                    headers = assistant.customHeaders,
                    onUpdate = { onUpdate(assistant.copy(customHeaders = it)) },
                )
            }
            FormItem(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                label = { Text(stringResource(R.string.assistant_page_custom_bodies)) },
            ) {
                CustomBodies(
                    customBodies = assistant.customBodies,
                    onUpdate = { onUpdate(assistant.copy(customBodies = it)) },
                )
            }
        }
        }

        if (!requestSettingsOnly) {
            Button(
                onClick = { showDeleteConfirm = true },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError,
                ),
            ) {
                Icon(HugeIcons.Delete01, contentDescription = null)
                Text(
                    stringResource(R.string.assistant_page_delete_assistant),
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
        }
    }
    ModelListSheet(
        state = chatModelState,
        onSelect = { onUpdate(assistant.copy(chatModelId = it.id)) },
    )
    RikkaConfirmDialog(
        show = showDeleteConfirm,
        title = stringResource(R.string.assistant_page_delete_assistant),
        confirmText = stringResource(R.string.common_delete),
        dismissText = stringResource(R.string.common_cancel),
        onConfirm = {
            showDeleteConfirm = false
            onDelete()
        },
        onDismiss = { showDeleteConfirm = false },
    ) {
        Text(stringResource(R.string.assistant_page_delete_dialog_text))
    }
}

/**
 * 上下文限制的最小有效值
 *
 * 低于此值时截断点几乎每轮都在移动, 提示词缓存命中率跌破 90%,
 * 且保留的上下文通常达不到可缓存的最小长度, 限制本身失去意义
 */
private const val MIN_CONTEXT_MESSAGE_LIMIT = 20

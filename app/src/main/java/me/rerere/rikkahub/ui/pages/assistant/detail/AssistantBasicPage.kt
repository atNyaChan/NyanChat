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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.rerere.ai.provider.ModelType
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.ArrowRight01
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.ui.components.ai.ModelListSheet
import me.rerere.rikkahub.ui.components.ai.rememberModelListState
import me.rerere.rikkahub.ui.components.ai.ContextCachePicker
import me.rerere.rikkahub.ui.components.ai.ReasoningButton
import me.rerere.rikkahub.ui.components.nav.BackButton
import me.rerere.rikkahub.ui.components.ui.CardGroup
import me.rerere.rikkahub.ui.components.ui.TagsInput
import me.rerere.rikkahub.ui.components.ui.UIAvatar
import me.rerere.rikkahub.ui.hooks.heroAnimation
import me.rerere.rikkahub.ui.theme.CustomColors
import me.rerere.rikkahub.utils.toFixed
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf
import kotlin.math.roundToInt
import me.rerere.rikkahub.data.model.Tag as DataTag

@Composable
fun AssistantBasicPage(
    id: String,
    requestSettingsOnly: Boolean = false,
    scrollToContextLimit: Boolean = false,
) {
    val vm: AssistantDetailVM = koinViewModel(
        parameters = {
            parametersOf(id)
        }
    )
    val assistant by vm.assistant.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val providers by vm.providers.collectAsStateWithLifecycle()
    val tags by vm.tags.collectAsStateWithLifecycle()
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
            onUpdate = { vm.update(it) },
            vm = vm,
            requestSettingsOnly = requestSettingsOnly,
            scrollToContextLimit = scrollToContextLimit,
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
    onUpdate: (Assistant) -> Unit,
    vm: AssistantDetailVM,
    requestSettingsOnly: Boolean = false,
    scrollToContextLimit: Boolean = false,
) {
    val chatModelState = rememberModelListState(
        modelId = assistant.chatModelId,
        providers = providers,
        type = ModelType.CHAT,
    )
    val scrollState = rememberScrollState()
    var containerTop by remember { mutableStateOf<Float?>(null) }
    var contextLimitTop by remember { mutableStateOf<Float?>(null) }
    LaunchedEffect(scrollToContextLimit, containerTop, contextLimitTop) {
        if (scrollToContextLimit && containerTop != null && contextLimitTop != null) {
            scrollState.scrollTo((contextLimitTop!! - containerTop!!).toInt().coerceAtLeast(0))
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .onGloballyPositioned { containerTop = it.positionInRoot().y }
            .padding(horizontal = 16.dp)
            .padding(top = innerPadding.calculateTopPadding())
            .padding(top = 8.dp)
            .padding(bottom = 16.dp)
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
            continueToNext = !assistant.useGradientBackground,
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
                modifier = Modifier
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .onGloballyPositioned { contextLimitTop = it.positionInRoot().y },
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
                continueToNext = assistant.background != null,
                onUpdate = { background ->
                    onUpdate(assistant.copy(background = background))
                }
            )
        }
        if (!assistant.useGradientBackground && assistant.background != null) {
            val backgroundOpacity = assistant.backgroundOpacity.coerceIn(0.1f, 1f)
            CardGroup(
                continueFromPrevious = true,
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
                            append(stringResource(R.string.assistant_page_temperature_warning).trimEnd('。', '.'))
                            append("。范围是 0 - 2，留空表示使用默认值。")
                        }
                    )
                },
            ) {
                var temperatureInput by remember(assistant.id) {
                    mutableStateOf(assistant.temperature?.toString().orEmpty())
                }
                OutlinedTextField(
                    value = temperatureInput,
                    onValueChange = { value ->
                        temperatureInput = value
                        if (value.isEmpty()) {
                            onUpdate(assistant.copy(temperature = null))
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .onFocusChanged { focusState ->
                            if (!focusState.isFocused) {
                                val value = temperatureInput.toFloatOrNull()
                                    ?.takeIf { it in 0f..2f }
                                temperatureInput = value?.toString().orEmpty()
                                onUpdate(assistant.copy(temperature = value))
                            }
                        },
                    placeholder = { Text("使用默认值") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                )
            }
            FormItem(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                label = {
                    Text(stringResource(R.string.assistant_page_top_p))
                },
                description = {
                    Text(
                        text = buildAnnotatedString {
                            append(stringResource(R.string.assistant_page_top_p_warning).trimEnd('。', '.'))
                            append("。范围是 0 - 1，留空表示使用默认值。")
                        }
                    )
                },
            ) {
                var topPInput by remember(assistant.id) {
                    mutableStateOf(assistant.topP?.toString().orEmpty())
                }
                OutlinedTextField(
                    value = topPInput,
                    onValueChange = { value ->
                        topPInput = value
                        if (value.isEmpty()) {
                            onUpdate(assistant.copy(topP = null))
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .onFocusChanged { focusState ->
                            if (!focusState.isFocused) {
                                val value = topPInput.toFloatOrNull()
                                    ?.takeIf { it in 0f..1f }
                                topPInput = value?.toString().orEmpty()
                                onUpdate(assistant.copy(topP = value))
                            }
                        },
                    placeholder = { Text("使用默认值") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                )
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

    }
    ModelListSheet(
        state = chatModelState,
        onSelect = { onUpdate(assistant.copy(chatModelId = it.id)) },
    )
}

/**
 * 上下文限制的最小有效值
 *
 * 低于此值时截断点几乎每轮都在移动, 提示词缓存命中率跌破 90%,
 * 且保留的上下文通常达不到可缓存的最小长度, 限制本身失去意义
 */
private const val MIN_CONTEXT_MESSAGE_LIMIT = 20

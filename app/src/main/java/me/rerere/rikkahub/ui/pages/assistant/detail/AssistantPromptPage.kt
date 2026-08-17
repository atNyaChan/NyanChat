package me.rerere.rikkahub.ui.pages.assistant.detail

import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.Add01
import me.rerere.hugeicons.stroke.Delete01
import me.rerere.hugeicons.stroke.Refresh03
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.fastForEach
import androidx.compose.ui.util.fastForEachIndexed
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.rerere.ai.core.MessageRole
import me.rerere.ai.provider.Model
import me.rerere.ai.ui.UIMessage
import me.rerere.ai.ui.UIMessagePart
import me.rerere.rikkahub.R
import me.rerere.rikkahub.Screen
import me.rerere.rikkahub.data.ai.transformers.DefaultPlaceholderProvider
import me.rerere.rikkahub.data.ai.transformers.TemplateTransformer
import me.rerere.rikkahub.data.ai.transformers.TransformerContext
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.data.model.Assistant
import me.rerere.rikkahub.data.model.AssistantAffectScope
import me.rerere.rikkahub.data.model.AssistantRegex
import me.rerere.rikkahub.data.model.toMessageNode
import me.rerere.rikkahub.ui.components.message.ChatMessage
import me.rerere.rikkahub.ui.components.nav.BackButton
import me.rerere.rikkahub.ui.components.ui.CardGroup
import me.rerere.rikkahub.ui.components.ui.FormItem
import me.rerere.rikkahub.ui.components.ui.ExtensionSelector
import me.rerere.rikkahub.ui.components.ui.Select
import me.rerere.rikkahub.ui.components.ui.Tag
import me.rerere.rikkahub.ui.components.ui.TextArea
import me.rerere.rikkahub.ui.components.ui.RikkaConfirmDialog
import me.rerere.rikkahub.ui.context.LocalNavController
import me.rerere.rikkahub.ui.theme.ChatFontProvider
import me.rerere.rikkahub.ui.theme.CustomColors
import me.rerere.rikkahub.ui.theme.JetbrainsMono
import me.rerere.rikkahub.ui.theme.rememberScreenEdgeCornerShape
import me.rerere.rikkahub.utils.UiState
import me.rerere.rikkahub.utils.insertAtCursor
import me.rerere.rikkahub.utils.onError
import me.rerere.rikkahub.utils.onSuccess
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject
import org.koin.core.parameter.parametersOf
import sh.calvin.reorderable.ReorderableColumn
import kotlin.uuid.Uuid

@Composable
fun AssistantPromptPage(id: String) {
    val vm: AssistantDetailVM = koinViewModel(
        parameters = {
            parametersOf(id)
        }
    )
    val assistant by vm.assistant.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    Scaffold(
        topBar = {
            LargeFlexibleTopAppBar(
                title = {
                    Text(stringResource(R.string.assistant_page_tab_prompt))
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
        AssistantPromptContent(
            innerPadding = innerPadding,
            assistant = assistant,
            settings = settings,
            onUpdate = { vm.update(it) }
        )
    }
}

@Composable
private fun AssistantPromptContent(
    innerPadding: PaddingValues,
    assistant: Assistant,
    settings: Settings,
    onUpdate: (Assistant) -> Unit
) {
    val context = LocalContext.current
    val navController = LocalNavController.current
    val templateTransformer = koinInject<TemplateTransformer>()
    var pendingPresetDeleteIndex by remember { mutableStateOf<Int?>(null) }
    var pendingRegexDeleteIndex by remember { mutableStateOf<Int?>(null) }
    var expandedRegexId by remember { mutableStateOf<Uuid?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
            .padding(innerPadding)
            .padding(top = 8.dp)
            .padding(bottom = 16.dp)
            .imePadding(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Card(
            colors = CustomColors.cardColorsOnSurfaceContainer,
            shape = rememberScreenEdgeCornerShape(),
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val systemPromptValue = rememberTextFieldState(
                    initialText = assistant.systemPrompt,
                )
                LaunchedEffect(Unit) {
                    snapshotFlow { systemPromptValue.text }.collect {
                        onUpdate(
                            assistant.copy(
                                systemPrompt = it.toString()
                            )
                        )
                    }
                }

                TextArea(
                    state = systemPromptValue,
                    label = stringResource(R.string.assistant_page_system_prompt),
                    minLines = 5,
                    maxLines = 10
                )

                Column {
                    Text(
                        text = stringResource(R.string.assistant_page_available_variables),
                        style = MaterialTheme.typography.labelSmall
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        DefaultPlaceholderProvider.placeholders.forEach { (k, info) ->
                            Tag(
                                onClick = {
                                    systemPromptValue.insertAtCursor("{{$k}}")
                                }
                            ) {
                                info.displayName()
                                Text(": {{$k}}")
                            }
                        }
                    }
                }
            }
        }

        CardGroup {
            FormItem(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                label = {
                    Text(stringResource(R.string.assistant_page_allow_conversation_system_prompt))
                },
                description = {
                    Text(stringResource(R.string.assistant_page_allow_conversation_system_prompt_desc))
                },
                tail = {
                    Switch(
                        checked = assistant.allowConversationSystemPrompt,
                        onCheckedChange = {
                            onUpdate(
                                assistant.copy(
                                    allowConversationSystemPrompt = it
                                )
                            )
                        }
                    )
                }
            )
            FormItem(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                label = {
                    Text(stringResource(R.string.assistant_page_allow_conversation_prompt_injection))
                },
                description = {
                    Text(stringResource(R.string.assistant_page_allow_conversation_prompt_injection_desc))
                },
                tail = {
                    Switch(
                        checked = assistant.allowConversationPromptInjection,
                        onCheckedChange = {
                            onUpdate(
                                assistant.copy(
                                    allowConversationPromptInjection = it
                                )
                            )
                        }
                    )
                }
            )
            FormItem(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                label = {
                    Text(stringResource(R.string.assistant_page_time_reminder))
                },
                description = {
                    Text(stringResource(R.string.assistant_page_time_reminder_desc))
                },
                tail = {
                    Switch(
                        checked = assistant.enableTimeReminder,
                        onCheckedChange = {
                            onUpdate(assistant.copy(enableTimeReminder = it))
                        }
                    )
                }
            )
        }

        Card(
            colors = CustomColors.cardColorsOnSurfaceContainer,
            shape = rememberScreenEdgeCornerShape(),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(420.dp)
                    .padding(8.dp),
            ) {
                Text(
                    text = stringResource(R.string.assistant_page_tab_extensions),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
                ExtensionSelector(
                    modifier = Modifier.weight(1f),
                    assistant = assistant,
                    settings = settings,
                    onUpdate = onUpdate,
                    onNavigateToQuickMessages = {
                        navController.navigate(Screen.QuickMessages)
                    },
                    onNavigateToModeInjections = {
                        navController.navigate(Screen.ModeInjections)
                    },
                    onNavigateToLorebooks = {
                        navController.navigate(Screen.Lorebooks)
                    },
                    onNavigateToSkills = {
                        navController.navigate(Screen.Skills)
                    },
                )
            }
        }

        val messageTemplateSection: @Composable () -> Unit = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.assistant_page_message_template_desc))
                Text(buildAnnotatedString {
                    append(stringResource(R.string.assistant_page_template_variables_label))
                    append(" ")
                    append(stringResource(R.string.assistant_page_template_variable_role))
                    append(": ")
                    withStyle(SpanStyle(color = MaterialTheme.colorScheme.primary)) {
                        append("{{ role }}")
                    }
                    append(", ")
                    append(stringResource(R.string.assistant_page_template_variable_message))
                    append(": ")
                    withStyle(SpanStyle(color = MaterialTheme.colorScheme.primary)) {
                        append("{{ message }}")
                    }
                    append(", ")
                    append(stringResource(R.string.assistant_page_template_variable_time))
                    append(": ")
                    withStyle(SpanStyle(color = MaterialTheme.colorScheme.primary)) {
                        append("{{ time }}")
                    }
                    append(", ")
                    append(stringResource(R.string.assistant_page_template_variable_date))
                    append(": ")
                    withStyle(SpanStyle(color = MaterialTheme.colorScheme.primary)) {
                        append("{{ date }}")
                    }
                })
                val missingMessage = "{{ message }}" !in assistant.messageTemplate
                OutlinedTextField(
                    value = assistant.messageTemplate,
                    onValueChange = { onUpdate(assistant.copy(messageTemplate = it)) },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 5,
                    maxLines = 15,
                    isError = missingMessage,
                    supportingText = if (missingMessage) {
                        { Text(stringResource(R.string.assistant_page_message_template_missing_message)) }
                    } else null,
                    textStyle = LocalTextStyle.current.copy(
                        fontSize = 12.sp,
                        fontFamily = JetbrainsMono,
                        lineHeight = 16.sp
                    ),
                    trailingIcon = {
                        IconButton(
                            onClick = {
                                onUpdate(assistant.copy(messageTemplate = "{{ message }}"))
                            },
                            enabled = assistant.messageTemplate != "{{ message }}",
                        ) {
                            Icon(
                                imageVector = HugeIcons.Refresh03,
                                contentDescription = null,
                            )
                        }
                    },
                )
                Column(
                    modifier = Modifier
                        .clip(MaterialTheme.shapes.small)
                        .background(MaterialTheme.colorScheme.background)
                        .padding(8.dp)
                        .fillMaxWidth()
                ) {
                Text(
                    text = stringResource(R.string.assistant_page_template_preview),
                    style = MaterialTheme.typography.titleSmall
                )
                val rawMessages = listOf(
                    UIMessage.user("你好啊"),
                    UIMessage.assistant("你好，有什么我可以帮你的吗？"),
                )
                val preview by produceState<UiState<List<UIMessage>>>(
                    UiState.Success(rawMessages),
                    assistant
                ) {
                    value = runCatching {
                        UiState.Success(
                            templateTransformer.transform(
                                ctx = TransformerContext(
                                    context = context,
                                    model = Model(modelId = "gpt-4o", displayName = "GPT-4o"),
                                    assistant = assistant,
                                    settings = settings
                                ),
                                messages = rawMessages
                            )
                        )
                    }.getOrElse {
                        UiState.Error(it)
                    }
                }
                preview.onError {
                    Text(
                        text = it.message ?: it.javaClass.name,
                        color = MaterialTheme.colorScheme.error
                    )
                }
                preview.onSuccess {
                    ChatFontProvider(displaySetting = settings.displaySetting) {
                        it.fastForEach { message ->
                            ChatMessage(
                                node = message.toMessageNode(),
                                onFork = {},
                                onRegenerate = {},
                                onEdit = {},
                                onShare = {},
                                onDelete = {},
                                onUpdate = {},
                                lastMessage = false,
                                showNerdLine = true,
                            )
                        }
                    }
                }
                }
            }
        }

        CardGroup {
            item(
                headlineContent = {
                    Text(stringResource(R.string.assistant_page_preset_messages))
                },
                supportingContent = {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(stringResource(R.string.assistant_page_preset_messages_desc))
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                assistant.presetMessages.fastForEachIndexed { index, presetMessage ->
                    CardGroup {
                        item(
                            headlineContent = {},
                            supportingContent = {
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Select(
                                        options = listOf(MessageRole.USER, MessageRole.ASSISTANT),
                                        selectedOption = presetMessage.role,
                                        onOptionSelected = { role ->
                                            onUpdate(
                                                assistant.copy(
                                                    presetMessages =
                                                        assistant.presetMessages.mapIndexed { i, msg ->
                                                            if (i == index) msg.copy(role = role) else msg
                                                        }
                                                )
                                            )
                                        },
                                        modifier = Modifier.width(160.dp)
                                    )
                                    OutlinedTextField(
                                        value = presetMessage.toText(),
                                        onValueChange = { text ->
                                            onUpdate(
                                                assistant.copy(
                                                    presetMessages =
                                                        assistant.presetMessages.mapIndexed { i, msg ->
                                                            if (i == index) {
                                                                msg.copy(parts = listOf(UIMessagePart.Text(text)))
                                                            } else {
                                                                msg
                                                            }
                                                        }
                                                )
                                            )
                                        },
                                        modifier = Modifier.fillMaxWidth(),
                                        maxLines = 6
                                    )
                                }
                            },
                            trailingContent = {
                                IconButton(onClick = { pendingPresetDeleteIndex = index }) {
                                    Icon(HugeIcons.Delete01, null)
                                }
                            },
                        )
                    }
                }
                Button(
                    onClick = {
                        val lastRole = assistant.presetMessages.lastOrNull()?.role ?: MessageRole.ASSISTANT
                        val nextRole = when (lastRole) {
                            MessageRole.USER -> MessageRole.ASSISTANT
                            MessageRole.ASSISTANT -> MessageRole.USER
                            else -> MessageRole.USER
                        }
                        onUpdate(
                            assistant.copy(
                                presetMessages = assistant.presetMessages + UIMessage(
                                    role = nextRole,
                                    parts = listOf(UIMessagePart.Text(""))
                                )
                            )
                        )
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(HugeIcons.Add01, null)
                }
            }
                    }
                },
            )

            item(
                headlineContent = {
                    Text(stringResource(R.string.assistant_page_regex_title))
                },
                supportingContent = {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(stringResource(R.string.assistant_page_regex_desc))
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            val haptic = LocalHapticFeedback.current
                            ReorderableColumn(
                                list = assistant.regexes,
                                onSettle = { fromIndex, toIndex ->
                                    val regexes = assistant.regexes.toMutableList().apply {
                                        add(toIndex, removeAt(fromIndex))
                                    }
                                    onUpdate(assistant.copy(regexes = regexes))
                                },
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) { index, regex, isDragging ->
                                key(regex.id) {
                                    ReorderableItem(modifier = Modifier.fillMaxWidth()) {
                                        AssistantRegexCard(
                                            regex = regex,
                                            onUpdate = onUpdate,
                                            assistant = assistant,
                                            index = index,
                                            onRequestDelete = {
                                                pendingRegexDeleteIndex = index
                                            },
                                            initiallyExpanded = regex.id == expandedRegexId,
                                            modifier = Modifier.scale(
                                                if (isDragging) 0.95f else 1f
                                            ).longPressDraggableHandle(
                                                enabled = assistant.regexes.size > 1,
                                                onDragStarted = {
                                                    haptic.performHapticFeedback(
                                                        HapticFeedbackType.GestureThresholdActivate
                                                    )
                                                },
                                                onDragStopped = {
                                                    haptic.performHapticFeedback(
                                                        HapticFeedbackType.GestureEnd
                                                    )
                                                },
                                            ),
                                        )
                                    }
                                }
                            }
                            Button(
                                onClick = {
                                    val regexId = Uuid.random()
                                    expandedRegexId = regexId
                                    onUpdate(
                                        assistant.copy(
                                            regexes = assistant.regexes + AssistantRegex(id = regexId)
                                        )
                                    )
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(HugeIcons.Add01, null)
                            }
                        }
                    }
                },
            )

            item(
                headlineContent = {
                    Text(stringResource(R.string.assistant_page_message_template))
                },
                supportingContent = { messageTemplateSection() },
            )
        }
    }

    DeleteItemDialog(
        show = pendingPresetDeleteIndex != null,
        itemName = stringResource(R.string.assistant_page_preset_message_singular),
        onConfirm = {
            pendingPresetDeleteIndex?.let { index ->
                onUpdate(
                    assistant.copy(
                        presetMessages = assistant.presetMessages.filterIndexed { i, _ -> i != index }
                    )
                )
            }
            pendingPresetDeleteIndex = null
        },
        onDismiss = { pendingPresetDeleteIndex = null },
    )
    DeleteItemDialog(
        show = pendingRegexDeleteIndex != null,
        itemName = stringResource(R.string.assistant_page_regex_singular),
        onConfirm = {
            pendingRegexDeleteIndex?.let { index ->
                onUpdate(
                    assistant.copy(
                        regexes = assistant.regexes.filterIndexed { i, _ -> i != index }
                    )
                )
            }
            pendingRegexDeleteIndex = null
        },
        onDismiss = { pendingRegexDeleteIndex = null },
    )
}

@Composable
private fun DeleteItemDialog(
    show: Boolean,
    itemName: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    RikkaConfirmDialog(
        show = show,
        title = stringResource(R.string.assistant_page_delete_item_title, itemName),
        confirmText = stringResource(R.string.common_delete),
        dismissText = stringResource(R.string.common_cancel),
        onConfirm = onConfirm,
        onDismiss = onDismiss,
    ) {
        Text(stringResource(R.string.assistant_page_delete_item_confirm, itemName))
    }
}

@Composable
private fun AssistantRegexCard(
    regex: AssistantRegex,
    onUpdate: (Assistant) -> Unit,
    assistant: Assistant,
    index: Int,
    onRequestDelete: () -> Unit,
    initiallyExpanded: Boolean = false,
    modifier: Modifier = Modifier,
) {
    var expanded by remember(regex.id) {
        mutableStateOf(initiallyExpanded)
    }
    ElevatedCard(
        onClick = { expanded = !expanded },
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier
                .padding(12.dp)
                .animateContentSize(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = regex.name,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .weight(1f)
                        .widthIn(max = 200.dp),
                )
                Switch(
                    checked = regex.enabled,
                    onCheckedChange = { enabled ->
                        onUpdate(
                            assistant.copy(
                                regexes = assistant.regexes.mapIndexed { i, reg ->
                                    if (i == index) {
                                        reg.copy(enabled = enabled)
                                    } else {
                                        reg
                                    }
                                }
                            )
                        )
                    },
                    modifier = Modifier.padding(start = 8.dp)
                )
            }
            if (expanded) {
                OutlinedTextField(
                    value = regex.name,
                    onValueChange = { name ->
                        onUpdate(
                            assistant.copy(
                                regexes = assistant.regexes.mapIndexed { i, reg ->
                                    if (i == index) {
                                        reg.copy(name = name)
                                    } else {
                                        reg
                                    }
                                }
                            )
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.assistant_page_regex_name)) }
                )

                OutlinedTextField(
                    value = regex.findRegex,
                    onValueChange = { findRegex ->
                        onUpdate(
                            assistant.copy(
                                regexes = assistant.regexes.mapIndexed { i, reg ->
                                    if (i == index) {
                                        reg.copy(findRegex = findRegex.trim())
                                    } else {
                                        reg
                                    }
                                }
                            )
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.assistant_page_regex_find_regex)) },
                    placeholder = { Text("e.g., \\b\\w+@\\w+\\.\\w+\\b") },
                )

                OutlinedTextField(
                    value = regex.replaceString,
                    onValueChange = { replaceString ->
                        onUpdate(
                            assistant.copy(
                                regexes = assistant.regexes.mapIndexed { i, reg ->
                                    if (i == index) {
                                        reg.copy(replaceString = replaceString)
                                    } else {
                                        reg
                                    }
                                }
                            )
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.assistant_page_regex_replace_string)) },
                    placeholder = { Text("e.g., [EMAIL]") }
                )

                Column {
                    Text(
                        text = stringResource(R.string.assistant_page_regex_affecting_scopes),
                        style = MaterialTheme.typography.labelMedium
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        AssistantAffectScope.entries.forEach { scope ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Checkbox(
                                    checked = scope in regex.affectingScope,
                                    onCheckedChange = { checked ->
                                        val newScopes = if (checked) {
                                            regex.affectingScope + scope
                                        } else {
                                            regex.affectingScope - scope
                                        }
                                        onUpdate(
                                            assistant.copy(
                                                regexes = assistant.regexes.mapIndexed { i, reg ->
                                                    if (i == index) {
                                                        reg.copy(affectingScope = newScopes)
                                                    } else {
                                                        reg
                                                    }
                                                }
                                            )
                                        )
                                    }
                                )
                                Text(
                                    text = scope.name.lowercase().replaceFirstChar { it.uppercase() },
                                    style = MaterialTheme.typography.labelMedium
                                )
                            }
                        }
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Checkbox(
                        checked = regex.visualOnly,
                        onCheckedChange = { visualOnly ->
                            onUpdate(
                                assistant.copy(
                                    regexes = assistant.regexes.mapIndexed { i, reg ->
                                        if (i == index) {
                                            reg.copy(visualOnly = visualOnly)
                                        } else {
                                            reg
                                        }
                                    }
                                )
                            )
                        }
                    )
                    Text(
                        text = stringResource(R.string.assistant_page_regex_visual_only),
                        style = MaterialTheme.typography.labelMedium
                    )
                }

                TextButton(onClick = onRequestDelete) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Icon(HugeIcons.Delete01, null)
                        Text(stringResource(R.string.common_delete))
                    }
                }
            }
        }
    }
}

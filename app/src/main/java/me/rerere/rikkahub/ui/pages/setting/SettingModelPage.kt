package me.rerere.rikkahub.ui.pages.setting

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.rerere.ai.core.ReasoningLevel
import me.rerere.ai.provider.Model
import me.rerere.ai.provider.ModelType
import me.rerere.ai.provider.ProviderSetting
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.ArrowRight01
import me.rerere.hugeicons.stroke.ArrowShrink
import me.rerere.hugeicons.stroke.BubbleChat
import me.rerere.hugeicons.stroke.ScanText
import me.rerere.hugeicons.stroke.Translate
import me.rerere.hugeicons.stroke.Zap
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.ui.components.ai.ModelListSheet
import me.rerere.rikkahub.ui.components.ai.ReasoningButton
import me.rerere.rikkahub.ui.components.ai.rememberModelListState
import me.rerere.rikkahub.ui.components.nav.BackButton
import me.rerere.rikkahub.ui.components.ui.CardGroup
import me.rerere.rikkahub.ui.components.ui.CardGroupIcon
import me.rerere.rikkahub.ui.components.ui.CardGroupScope
import me.rerere.rikkahub.ui.components.ui.switchItem
import me.rerere.rikkahub.ui.theme.CustomColors
import me.rerere.rikkahub.utils.plus
import org.koin.androidx.compose.koinViewModel
import kotlin.uuid.Uuid

@Composable
fun SettingModelPage(vm: SettingVM = koinViewModel()) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    Scaffold(
        containerColor = CustomColors.topBarColors.containerColor,
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text(stringResource(R.string.setting_model_page_title)) },
                navigationIcon = { BackButton() },
                scrollBehavior = scrollBehavior,
                colors = CustomColors.topBarColors,
            )
        },
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
    ) { contentPadding ->
        ModelSettingsPage(settings = settings, vm = vm, contentPadding = contentPadding)
    }
}

@Composable
private fun ModelSettingsPage(settings: Settings, vm: SettingVM, contentPadding: PaddingValues) {
    var editingPrompt by remember { mutableStateOf<PromptType?>(null) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = contentPadding + PaddingValues(
            start = 16.dp,
            top = 8.dp,
            end = 16.dp,
            bottom = 16.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            ModelSettingItem(
                title = stringResource(R.string.setting_model_page_chat_model),
                icon = HugeIcons.BubbleChat,
                iconContainerColor = MaterialTheme.colorScheme.primaryContainer,
                iconContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                description = stringResource(R.string.setting_model_page_chat_model_desc),
                modelId = settings.chatModelId,
                providers = settings.providers,
                onSelect = { vm.updateSettings { latest -> latest.copy(chatModelId = it.id) } },
            )
        }
        item {
            ModelSettingItem(
                title = stringResource(R.string.setting_model_page_fast_model),
                icon = HugeIcons.Zap,
                iconContainerColor = MaterialTheme.colorScheme.primaryContainer,
                iconContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                description = stringResource(R.string.setting_model_page_fast_model_desc),
                modelId = settings.fastModelId,
                providers = settings.providers,
                onSelect = { vm.updateSettings { latest -> latest.copy(fastModelId = it.id) } },
                reasoningLevel = settings.fastModelReasoningLevel,
                onUpdateReasoningLevel = {
                    vm.updateSettings { latest -> latest.copy(fastModelReasoningLevel = it) }
                },
            )
        }
        item {
            CardGroup {
                promptSettingItem(PromptType.TITLE, settings.titlePrompt) { editingPrompt = PromptType.TITLE }
            }
        }
        item {
            SuggestionSettingItem(
                settings = settings,
                vm = vm,
                onEditPrompt = { editingPrompt = it },
            )
        }
        item {
            ModelSettingItem(
                title = stringResource(R.string.setting_model_page_translate_model),
                icon = HugeIcons.Translate,
                iconContainerColor = MaterialTheme.colorScheme.primaryContainer,
                iconContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                modelId = settings.translateModeId,
                providers = settings.providers,
                onSelect = { vm.updateSettings { latest -> latest.copy(translateModeId = it.id) } },
                promptType = PromptType.TRANSLATION,
                prompt = settings.translatePrompt,
                onEditPrompt = { editingPrompt = it },
                beforePrompt = {
                    item(
                        headlineContent = { Text(stringResource(R.string.assistant_page_thinking_budget)) },
                        trailingContent = {
                            ReasoningButton(
                                reasoningLevel = me.rerere.ai.core.ReasoningLevel.fromBudgetTokens(
                                    settings.translateThinkingBudget
                                ),
                                onUpdateReasoningLevel = {
                                    vm.updateSettings { latest ->
                                        latest.copy(translateThinkingBudget = it.budgetTokens)
                                    }
                                },
                            )
                        },
                    )
                },
            )
        }
        item {
            ModelSettingItem(
                title = stringResource(R.string.setting_model_page_ocr_model),
                icon = HugeIcons.ScanText,
                iconContainerColor = MaterialTheme.colorScheme.primaryContainer,
                iconContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                modelId = settings.ocrModelId,
                providers = settings.providers,
                onSelect = { vm.updateSettings { latest -> latest.copy(ocrModelId = it.id) } },
                promptType = PromptType.OCR,
                prompt = settings.ocrPrompt,
                onEditPrompt = { editingPrompt = it },
            )
        }
        item {
            ModelSettingItem(
                title = stringResource(R.string.setting_model_page_compress_model),
                icon = HugeIcons.ArrowShrink,
                iconContainerColor = MaterialTheme.colorScheme.primaryContainer,
                iconContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                modelId = settings.compressModelId,
                providers = settings.providers,
                onSelect = { vm.updateSettings { latest -> latest.copy(compressModelId = it.id) } },
                promptType = PromptType.COMPRESS,
                prompt = settings.compressPrompt,
                onEditPrompt = { editingPrompt = it },
            )
        }
    }

    editingPrompt?.let { type ->
        PromptEditor(
            type = type,
            settings = settings,
            vm = vm,
            onDismiss = { editingPrompt = null },
        )
    }
}

@Composable
private fun SuggestionSettingItem(
    settings: Settings,
    vm: SettingVM,
    onEditPrompt: (PromptType) -> Unit,
) {
    CardGroup {
        switchItem(
            checked = settings.enableSuggestion,
            onCheckedChange = {
                vm.updateSettings { latest -> latest.copy(enableSuggestion = it) }
            },
            headlineContent = { Text(stringResource(R.string.setting_model_page_enable_suggestion)) },
        )
        promptSettingItem(PromptType.SUGGESTION, settings.suggestionPrompt) { onEditPrompt(PromptType.SUGGESTION) }
    }
}

@Composable
private fun ModelSettingItem(
    title: String,
    icon: ImageVector,
    iconContainerColor: Color,
    iconContentColor: Color,
    description: String? = null,
    modelId: Uuid?,
    providers: List<ProviderSetting>,
    onSelect: (Model) -> Unit,
    reasoningLevel: ReasoningLevel? = null,
    onUpdateReasoningLevel: ((ReasoningLevel) -> Unit)? = null,
    promptType: PromptType? = null,
    prompt: String = "",
    onEditPrompt: (PromptType) -> Unit = {},
    beforePrompt: CardGroupScope.() -> Unit = {},
) {
    val state = rememberModelListState(
        modelId = modelId,
        providers = providers,
        type = ModelType.CHAT,
    )

    Column {
        CardGroup {
            item(
                onClick = { state.open() },
                headlineContent = { Text(title) },
                leadingContent = {
                    CardGroupIcon(
                        icon = icon,
                        containerColor = iconContainerColor,
                        contentColor = iconContentColor,
                    )
                },
                trailingContent = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(
                            text = state.currentModel?.displayName
                                ?: stringResource(R.string.model_list_select_model),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Icon(
                            HugeIcons.ArrowRight01,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                },
            )
            if (reasoningLevel != null && onUpdateReasoningLevel != null) {
                item(
                    headlineContent = { Text(stringResource(R.string.assistant_page_thinking_budget)) },
                    trailingContent = {
                        ReasoningButton(
                            reasoningLevel = reasoningLevel,
                            onUpdateReasoningLevel = onUpdateReasoningLevel,
                        )
                    },
                )
            }
            beforePrompt()
            if (promptType != null) {
                promptSettingItem(promptType, prompt, showIcon = false) { onEditPrompt(promptType) }
            }
        }
        if (description != null) {
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
            )
        }
    }

    ModelListSheet(state = state, onSelect = onSelect)
}

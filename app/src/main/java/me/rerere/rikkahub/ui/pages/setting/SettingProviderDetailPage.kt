package me.rerere.rikkahub.ui.pages.setting

import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.ArrowDown01
import me.rerere.hugeicons.stroke.Add01
import me.rerere.hugeicons.stroke.Refresh03
import me.rerere.hugeicons.stroke.Package01
import me.rerere.hugeicons.stroke.Share01
import me.rerere.hugeicons.stroke.Cancel01
import me.rerere.hugeicons.stroke.Delete01
import me.rerere.hugeicons.stroke.Tools
import me.rerere.hugeicons.stroke.View
import me.rerere.hugeicons.stroke.ViewOff
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.MultiChoiceSegmentedButtonRow
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.SheetValue
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withLink
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastFilter
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dokar.sonner.ToastType
import kotlinx.coroutines.launch
import me.rerere.ai.provider.BalanceOption
import me.rerere.ai.provider.BuiltInTools
import me.rerere.ai.provider.Modality
import me.rerere.ai.provider.Model
import me.rerere.ai.provider.ModelAbility
import me.rerere.ai.provider.ModelPrice
import me.rerere.ai.provider.ModelType
import me.rerere.ai.provider.ProviderManager
import me.rerere.ai.provider.ProviderSetting
import me.rerere.ai.registry.ModelRegistry
import me.rerere.common.http.isJsonExprValid
import me.rerere.rikkahub.R
import me.rerere.rikkahub.Screen
import me.rerere.rikkahub.data.datastore.findProvider
import me.rerere.rikkahub.ui.components.ai.ModelAbilityTag
import me.rerere.rikkahub.ui.components.ai.ModelListSheet
import me.rerere.rikkahub.ui.components.ai.ModelModalityTag
import me.rerere.rikkahub.ui.components.ai.ModelTypeTag
import me.rerere.rikkahub.ui.components.ai.ProviderBalanceText
import me.rerere.rikkahub.ui.components.ai.rememberModelListState
import me.rerere.rikkahub.ui.components.ui.AutoAIIcon
import me.rerere.rikkahub.ui.components.ui.CardGroup
import me.rerere.rikkahub.ui.components.ui.CardGroupScope
import me.rerere.rikkahub.ui.components.ui.OutlinedItemCard
import me.rerere.rikkahub.ui.components.ui.ShareSheet
import me.rerere.rikkahub.ui.components.ui.Tag
import me.rerere.rikkahub.ui.components.ui.TagType
import me.rerere.rikkahub.ui.components.ui.rememberShareSheetState
import me.rerere.rikkahub.ui.context.LocalNavController
import me.rerere.rikkahub.ui.context.LocalSettings
import me.rerere.rikkahub.ui.context.LocalToaster
import me.rerere.rikkahub.ui.hooks.useEditState
import me.rerere.rikkahub.ui.pages.assistant.detail.CustomBodies
import me.rerere.rikkahub.ui.pages.assistant.detail.CustomHeaders
import me.rerere.rikkahub.ui.pages.setting.components.ProviderConfigure
import me.rerere.rikkahub.ui.pages.setting.components.ProviderConnectionTester
import me.rerere.rikkahub.ui.theme.JetbrainsMono
import me.rerere.rikkahub.ui.theme.codeFontFeatureSettings
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import kotlin.uuid.Uuid

private val ApiPathRegex = Regex("""^/[^ \t\n\r]*$""")

private fun String.isValidBaseUrl(): Boolean = this.toHttpUrlOrNull() != null

@Composable
fun SettingProviderDetailSheet(
    id: Uuid,
    onDismiss: () -> Unit,
    initialModelId: Uuid? = null,
    vm: SettingVM = koinViewModel(),
) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val toaster = LocalToaster.current
    val context = LocalContext.current
    val shareSheetState = rememberShareSheetState()
    val scope = rememberCoroutineScope()
    val providerManager = koinInject<ProviderManager>()

    val provider = settings.providers.find { it.id == id } ?: return

    var internalProvider by remember(provider) { mutableStateOf(provider) }
    var balanceRefreshTick by remember { mutableStateOf(0) }
    var showModelPicker by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    val customModelDialog = useEditState<Model> { edited ->
        internalProvider = internalProvider.addModel(edited)
    }

    val modelList by produceState(emptyList(), internalProvider.id) {
        runCatching {
            value = providerManager.getProviderByType(internalProvider)
                .listModels(internalProvider)
                .sortedBy { it.modelId }
                .toList()
        }.onFailure {
            it.printStackTrace()
        }
    }

    val sheetState = rememberBottomSheetState(
        initialValue = SheetValue.Hidden,
        enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded),
    )
    val lazyListState = rememberLazyListState()
    val reorderableLazyListState = rememberReorderableLazyListState(lazyListState) { from, to ->
        val fromIndex = from.index - MODELS_LIST_START_INDEX
        var toIndex = to.index - MODELS_LIST_START_INDEX
        if (fromIndex < 0 || fromIndex >= internalProvider.models.size) {
            return@rememberReorderableLazyListState
        }
        toIndex = toIndex.coerceIn(0, internalProvider.models.size - 1)
        if (fromIndex != toIndex) {
            internalProvider = internalProvider.moveMove(fromIndex, toIndex)
        }
    }
    LaunchedEffect(initialModelId, internalProvider.models) {
        val initialIndex = internalProvider.models.indexOfFirst { it.id == initialModelId }
        if (initialIndex >= 0) {
            lazyListState.scrollToItem(MODELS_LIST_START_INDEX + initialIndex)
        }
    }

    val onSave = {
        val newSettings = settings.copy(
            providers = settings.providers.map {
                if (internalProvider.id == it.id) {
                    internalProvider
                } else {
                    it
                }
            }
        )
        vm.updateSettings(newSettings)
        onDismiss()
    }

    val onDelete = {
        val newSettings = settings.copy(
            providers = settings.providers.filter { it.id != internalProvider.id }
        )
        vm.updateSettings(newSettings)
        onDismiss()
    }

    ModalBottomSheet(
        onDismissRequest = {
            onDismiss()
        },
        sheetState = sheetState,
        sheetGesturesEnabled = false,
        dragHandle = {
            IconButton(
                onClick = {
                    scope.launch {
                        sheetState.hide()
                        onDismiss()
                    }
                }
            ) {
                Icon(HugeIcons.ArrowDown01, contentDescription = null)
            }
        },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.95f)
                .imePadding(),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = stringResource(R.string.setting_provider_page_edit_provider),
                style = MaterialTheme.typography.titleLarge,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
            )

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                state = lazyListState,
                contentPadding = PaddingValues(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item {
                    ProviderConfigFieldsCardGroup(
                        provider = internalProvider,
                        onEdit = { internalProvider = it },
                    )
                }

                item {
                    ProviderConfigTogglesCardGroup(
                        provider = internalProvider,
                        balanceRefreshTick = balanceRefreshTick,
                        onRefreshBalance = { balanceRefreshTick += 1 },
                        onEdit = { internalProvider = it },
                    )
                }

                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = stringResource(
                                R.string.setting_provider_page_models_format,
                                internalProvider.models.size,
                            ),
                            style = MaterialTheme.typography.titleSmall,
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            IconButton(
                                onClick = { customModelDialog.open(Model()) },
                            ) {
                                Icon(
                                    HugeIcons.Add01,
                                    stringResource(R.string.setting_provider_page_add_model)
                                )
                            }
                            IconButton(
                                onClick = { showModelPicker = true },
                            ) {
                                Icon(
                                    HugeIcons.Package01,
                                    stringResource(R.string.model_list_select_model),
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                            }
                        }
                    }
                }

                if (internalProvider.models.isEmpty()) {
                    item {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Text(
                                text = stringResource(R.string.setting_provider_page_no_models),
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = stringResource(R.string.setting_provider_page_add_models_hint),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                            )
                        }
                    }
                } else {
                    items(internalProvider.models, key = { it.id }) { modelItem ->
                        ReorderableItem(
                            state = reorderableLazyListState,
                            key = modelItem.id
                        ) { isDragging ->
                            ModelCard(
                                model = modelItem,
                                openInitially = modelItem.id == initialModelId,
                                onDelete = {
                                    internalProvider = internalProvider.delModel(modelItem)
                                },
                                onEdit = { editedModel ->
                                    internalProvider = internalProvider.editModel(editedModel)
                                },
                                parentProvider = internalProvider,
                                onMigrateModelId = { sourceModel, targetModel ->
                                    vm.migrateMessageModelId(sourceModel.id, targetModel.id) { result ->
                                        result.onSuccess { count ->
                                            toaster.show(
                                                context.getString(
                                                    R.string.setting_provider_page_migrate_id_success,
                                                    count,
                                                ),
                                                type = ToastType.Success,
                                            )
                                        }.onFailure { error ->
                                            toaster.show(
                                                context.getString(
                                                    R.string.setting_provider_page_migrate_id_failed,
                                                    error.message.orEmpty(),
                                                ),
                                                type = ToastType.Error,
                                            )
                                        }
                                    }
                                },
                                modifier = Modifier
                                    .longPressDraggableHandle()
                                    .graphicsLayer {
                                        if (isDragging) {
                                            scaleX = 1.05f
                                            scaleY = 1.05f
                                        } else {
                                            scaleX = 1f
                                            scaleY = 1f
                                        }
                                    },
                            )
                        }
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(
                    onClick = {
                        showDeleteDialog = true
                    },
                    enabled = !internalProvider.builtIn,
                ) {
                    Icon(HugeIcons.Delete01, null)
                }
                ProviderConnectionTester(
                    internalProvider = internalProvider,
                )
                IconButton(
                    onClick = {
                        shareSheetState.show(internalProvider)
                    }
                ) {
                    Icon(HugeIcons.Share01, null)
                }
                Spacer(Modifier.weight(1f))
                TextButton(
                    onClick = {
                        onDismiss()
                    },
                ) {
                    Text(stringResource(R.string.common_cancel))
                }
                TextButton(
                    onClick = onSave,
                ) {
                    Text(stringResource(R.string.common_confirm_action))
                }
            }
        }
    }

    ShareSheet(shareSheetState)

    if (showDeleteDialog) {
        AlertDialog(
            containerColor = MaterialTheme.colorScheme.surface,
            onDismissRequest = { showDeleteDialog = false },
            title = {
                Text(stringResource(R.string.confirm_delete))
            },
            text = {
                Text(stringResource(R.string.setting_provider_page_delete_dialog_text))
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text(stringResource(R.string.common_cancel))
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteDialog = false
                        onDelete()
                    }
                ) {
                    Text(stringResource(R.string.common_delete))
                }
            }
        )
    }

    if (showModelPicker) {
        ModelPickerSheet(
            models = modelList,
            selectedModels = internalProvider.models,
            onModelSelected = { model ->
                internalProvider = internalProvider.addModel(
                    model.copy(
                        inputModalities = ModelRegistry.MODEL_INPUT_MODALITIES.getData(model.modelId),
                        outputModalities = ModelRegistry.MODEL_OUTPUT_MODALITIES.getData(model.modelId),
                        abilities = ModelRegistry.MODEL_ABILITIES.getData(model.modelId),
                    )
                )
            },
            onModelDeselected = { model ->
                internalProvider = internalProvider.delModel(model)
            },
            onAllModelSelected = { models ->
                internalProvider = internalProvider.copyProvider(
                    models = internalProvider.models + models.filter { model ->
                        internalProvider.models.none { existing -> existing.modelId == model.modelId }
                    }.map { model ->
                        model.copy(
                            inputModalities = ModelRegistry.MODEL_INPUT_MODALITIES.getData(model.modelId),
                            outputModalities = ModelRegistry.MODEL_OUTPUT_MODALITIES.getData(model.modelId),
                            abilities = ModelRegistry.MODEL_ABILITIES.getData(model.modelId),
                        )
                    }
                )
            },
            onAllModelDeselected = { models ->
                internalProvider = internalProvider.copyProvider(
                    models = internalProvider.models.filter { model ->
                        models.none { filtered -> filtered.modelId == model.modelId }
                    }
                )
            },
            onDismiss = { showModelPicker = false },
        )
    }

    if (customModelDialog.isEditing) {
        customModelDialog.currentState?.let { modelState ->
            val sheetState = rememberBottomSheetState(
                initialValue = SheetValue.Hidden,
                enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded)
            )
            ModalBottomSheet(
                onDismissRequest = {
                    customModelDialog.dismiss()
                },
                sheetState = sheetState,
                sheetGesturesEnabled = false,
                dragHandle = {
                    IconButton(
                        onClick = {
                            scope.launch {
                                sheetState.hide()
                                customModelDialog.dismiss()
                            }
                        }
                    ) {
                        Icon(HugeIcons.ArrowDown01, null)
                    }
                }
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .fillMaxHeight(0.95f),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Text(
                        text = stringResource(R.string.setting_provider_page_add_model),
                        style = MaterialTheme.typography.titleLarge,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp),
                    )
                    Column(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                    ) {
                        ModelSettingsForm(
                            model = modelState,
                            onModelChange = { customModelDialog.currentState = it },
                            isEdit = false,
                            parentProvider = internalProvider
                        )
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                    ) {
                        TextButton(
                            onClick = {
                                customModelDialog.dismiss()
                            },
                        ) {
                            Text(stringResource(R.string.common_cancel))
                        }
                        TextButton(
                            onClick = {
                                if (modelState.modelId.isNotBlank() && modelState.displayName.isNotBlank()) {
                                    customModelDialog.confirm()
                                }
                            },
                        ) {
                            Text(stringResource(R.string.common_add))
                        }
                    }
                }
            }
        }
    }
}

private const val MODELS_LIST_START_INDEX = 3

@Composable
private fun ProviderConfigFieldsCardGroup(
    provider: ProviderSetting,
    onEdit: (ProviderSetting) -> Unit,
) {
    CardGroup(modifier = Modifier.fillMaxWidth()) {
        when (provider) {
            is ProviderSetting.OpenAI -> {
                item(
                    supportingContent = {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = provider.name,
                                onValueChange = { onEdit(provider.copy(name = it.trim())) },
                                label = { Text(stringResource(R.string.setting_provider_page_name)) },
                                modifier = Modifier.fillMaxWidth(),
                            )
                            var keyVisible by remember { mutableStateOf(false) }
                            OutlinedTextField(
                                value = provider.apiKey,
                                onValueChange = { onEdit(provider.copy(apiKey = it.trim())) },
                                label = { Text(stringResource(R.string.setting_provider_page_api_key)) },
                                modifier = Modifier.fillMaxWidth(),
                                maxLines = 3,
                                visualTransformation = if (keyVisible) VisualTransformation.None else PasswordVisualTransformation(),
                                trailingIcon = {
                                    IconButton(onClick = { keyVisible = !keyVisible }) {
                                        Icon(
                                            if (keyVisible) HugeIcons.ViewOff else HugeIcons.View,
                                            contentDescription = null,
                                        )
                                    }
                                },
                            )
                            OutlinedTextField(
                                value = provider.baseUrl,
                                onValueChange = { onEdit(provider.copy(baseUrl = it.trim())) },
                                label = { Text(stringResource(R.string.setting_provider_page_api_base_url)) },
                                modifier = Modifier.fillMaxWidth(),
                                isError = provider.baseUrl.isNotBlank() && !provider.baseUrl.isValidBaseUrl(),
                            )
                            if (!provider.useResponseApi) {
                                OutlinedTextField(
                                    value = provider.chatCompletionsPath,
                                    onValueChange = { onEdit(provider.copy(chatCompletionsPath = it.trim())) },
                                    label = { Text(stringResource(R.string.setting_provider_page_api_path)) },
                                    modifier = Modifier.fillMaxWidth(),
                                    enabled = !provider.builtIn,
                                )
                            }
                        }
                    },
                    headlineContent = {},
                )
            }

            is ProviderSetting.Google -> {
                item(
                    supportingContent = {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = provider.name,
                                onValueChange = { onEdit(provider.copy(name = it.trim())) },
                                label = { Text(stringResource(R.string.setting_provider_page_name)) },
                                modifier = Modifier.fillMaxWidth(),
                            )
                            if (!(provider.vertexAI && provider.useServiceAccount)) {
                                var keyVisible by remember { mutableStateOf(false) }
                                OutlinedTextField(
                                    value = provider.apiKey,
                                    onValueChange = { onEdit(provider.copy(apiKey = it.trim())) },
                                    label = { Text(stringResource(R.string.setting_provider_page_api_key)) },
                                    modifier = Modifier.fillMaxWidth(),
                                    maxLines = 3,
                                    visualTransformation = if (keyVisible) VisualTransformation.None else PasswordVisualTransformation(),
                                    trailingIcon = {
                                        IconButton(onClick = { keyVisible = !keyVisible }) {
                                            Icon(
                                                if (keyVisible) HugeIcons.ViewOff else HugeIcons.View,
                                                contentDescription = null,
                                            )
                                        }
                                    },
                                )
                            }
                            if (!provider.vertexAI) {
                                OutlinedTextField(
                                    value = provider.baseUrl,
                                    onValueChange = { onEdit(provider.copy(baseUrl = it.trim())) },
                                    label = { Text(stringResource(R.string.setting_provider_page_api_base_url)) },
                                    modifier = Modifier.fillMaxWidth(),
                                    isError = provider.baseUrl.isNotBlank() && (
                                        !provider.baseUrl.isValidBaseUrl() || !provider.baseUrl.endsWith("/v1beta")
                                        ),
                                    supportingText = if (!provider.baseUrl.endsWith("/v1beta")) {
                                        { Text("The base URL usually ends with `/v1beta`") }
                                    } else null,
                                )
                            }
                        }
                    },
                    headlineContent = {},
                )
            }

            is ProviderSetting.Claude -> {
                item(
                    supportingContent = {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = provider.name,
                                onValueChange = { onEdit(provider.copy(name = it.trim())) },
                                label = { Text(stringResource(R.string.setting_provider_page_name)) },
                                modifier = Modifier.fillMaxWidth(),
                                maxLines = 3,
                            )
                            var keyVisible by remember { mutableStateOf(false) }
                            OutlinedTextField(
                                value = provider.apiKey,
                                onValueChange = { onEdit(provider.copy(apiKey = it.trim())) },
                                label = { Text(stringResource(R.string.setting_provider_page_api_key)) },
                                modifier = Modifier.fillMaxWidth(),
                                maxLines = 3,
                                visualTransformation = if (keyVisible) VisualTransformation.None else PasswordVisualTransformation(),
                                trailingIcon = {
                                    IconButton(onClick = { keyVisible = !keyVisible }) {
                                        Icon(
                                            if (keyVisible) HugeIcons.ViewOff else HugeIcons.View,
                                            contentDescription = null,
                                        )
                                    }
                                },
                            )
                            OutlinedTextField(
                                value = provider.baseUrl,
                                onValueChange = { onEdit(provider.copy(baseUrl = it.trim())) },
                                label = { Text(stringResource(R.string.setting_provider_page_api_base_url)) },
                                modifier = Modifier.fillMaxWidth(),
                                isError = provider.baseUrl.isNotBlank() && !provider.baseUrl.isValidBaseUrl(),
                            )
                        }
                    },
                    headlineContent = {},
                )
            }
        }
    }
}

@Composable
private fun ProviderConfigTogglesCardGroup(
    provider: ProviderSetting,
    balanceRefreshTick: Int,
    onRefreshBalance: () -> Unit,
    onEdit: (ProviderSetting) -> Unit,
) {
    val toaster = LocalToaster.current
    val responseAPIWarning = stringResource(R.string.setting_provider_page_response_api_warning)
    CardGroup(modifier = Modifier.fillMaxWidth()) {
        when (provider) {
            is ProviderSetting.OpenAI -> {
                FormItem(
                    label = { Text(stringResource(R.string.setting_provider_page_enable)) },
                    tail = {
                        Switch(
                            checked = provider.enabled,
                            onCheckedChange = { onEdit(provider.copy(enabled = it)) }
                        )
                    }
                )

                FormItem(
                    label = { Text(stringResource(R.string.setting_provider_page_response_api)) },
                    tail = {
                        Switch(
                            checked = provider.useResponseApi,
                            onCheckedChange = {
                                onEdit(provider.copy(useResponseApi = it))
                                if (it && provider.baseUrl.toHttpUrlOrNull()?.host != "api.openai.com") {
                                    toaster.show(
                                        message = responseAPIWarning,
                                        type = ToastType.Warning,
                                    )
                                }
                            }
                        )
                    }
                )

                FormItem(
                    label = { Text(stringResource(R.string.setting_provider_page_include_history_reasoning)) },
                    tail = {
                        Switch(
                            checked = provider.includeHistoryReasoning,
                            onCheckedChange = { onEdit(provider.copy(includeHistoryReasoning = it)) }
                        )
                    }
                )

                BalanceFormItem(
                    provider = provider,
                    balanceRefreshTick = balanceRefreshTick,
                    onRefreshBalance = onRefreshBalance,
                    onEdit = { option ->
                        onEdit(provider.copy(balanceOption = option))
                    },
                )

                if (provider.balanceOption.enabled) {
                    item(
                        supportingContent = {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedTextField(
                                    value = provider.balanceOption.apiPath,
                                    onValueChange = { onEdit(provider.copy(balanceOption = provider.balanceOption.copy(apiPath = it))) },
                                    label = { Text(stringResource(R.string.setting_provider_page_balance_api_path)) },
                                    isError = !provider.balanceOption.apiPath.matches(ApiPathRegex),
                                    modifier = Modifier.fillMaxWidth()
                                )
                                OutlinedTextField(
                                    value = provider.balanceOption.resultPath,
                                    onValueChange = { onEdit(provider.copy(balanceOption = provider.balanceOption.copy(resultPath = it))) },
                                    label = { Text(stringResource(R.string.setting_provider_page_balance_json_key)) },
                                    isError = !isJsonExprValid(provider.balanceOption.resultPath),
                                    modifier = Modifier.fillMaxWidth(),
                                    textStyle = MaterialTheme.typography.bodySmall.copy(
                                        fontFamily = JetbrainsMono,
                                        fontFeatureSettings = LocalSettings.current.displaySetting.enableCodeLigatures.codeFontFeatureSettings,
                                    )
                                )
                            }
                        },
                        headlineContent = {},
                    )
                }
            }

            is ProviderSetting.Google -> {
                FormItem(
                    label = { Text(stringResource(R.string.setting_provider_page_enable)) },
                    tail = {
                        Switch(
                            checked = provider.enabled,
                            onCheckedChange = { onEdit(provider.copy(enabled = it)) }
                        )
                    }
                )

                FormItem(
                    label = { Text(stringResource(R.string.setting_provider_page_vertex_ai)) },
                    tail = {
                        Switch(
                            checked = provider.vertexAI,
                            onCheckedChange = { onEdit(provider.copy(vertexAI = it)) }
                        )
                    }
                )

                if (provider.vertexAI) {
                    FormItem(
                        label = { Text(stringResource(R.string.setting_provider_page_use_service_account)) },
                        tail = {
                            Switch(
                                checked = provider.useServiceAccount,
                                onCheckedChange = { onEdit(provider.copy(useServiceAccount = it)) }
                            )
                        }
                    )
                }

                if (provider.vertexAI && provider.useServiceAccount) {
                    item(
                        supportingContent = {
                            GoogleServiceAccountFields(provider = provider, onEdit = onEdit)
                        },
                        headlineContent = {},
                    )
                }
            }

            is ProviderSetting.Claude -> {
                FormItem(
                    label = { Text(stringResource(R.string.setting_provider_page_enable)) },
                    tail = {
                        Switch(
                            checked = provider.enabled,
                            onCheckedChange = { onEdit(provider.copy(enabled = it)) }
                        )
                    }
                )
            }
        }
    }
}

private fun CardGroupScope.BalanceFormItem(
    provider: ProviderSetting,
    balanceRefreshTick: Int,
    onRefreshBalance: () -> Unit,
    onEdit: (BalanceOption) -> Unit,
) {
    FormItem(
        label = {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(stringResource(R.string.setting_provider_page_balance_info))
                if (provider.balanceOption.enabled) {
                    ProviderBalanceText(
                        providerSetting = provider,
                        style = MaterialTheme.typography.labelSmall,
                        refreshKey = balanceRefreshTick,
                    )
                }
            }
        },
        tail = {
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(
                    onClick = onRefreshBalance,
                    enabled = provider.balanceOption.enabled,
                ) {
                    Icon(HugeIcons.Refresh03, null)
                }
                Switch(
                    checked = provider.balanceOption.enabled,
                    onCheckedChange = { onEdit(provider.balanceOption.copy(enabled = it)) }
                )
            }
        },
    )
}

@Composable
private fun GoogleServiceAccountFields(
    provider: ProviderSetting.Google,
    onEdit: (ProviderSetting) -> Unit,
) {
    val context = LocalContext.current
    val toaster = LocalToaster.current
    val serviceAccountJsonLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        try {
            val content = context.contentResolver.openInputStream(uri)
                ?.bufferedReader()
                ?.readText()
                ?: return@rememberLauncherForActivityResult
            val json = Json.parseToJsonElement(content).jsonObject
            onEdit(
                provider.copy(
                    projectId = json["project_id"]?.jsonPrimitive?.contentOrNull?.ifEmpty { null } ?: provider.projectId,
                    serviceAccountEmail = json["client_email"]?.jsonPrimitive?.contentOrNull?.ifEmpty { null } ?: provider.serviceAccountEmail,
                    privateKey = json["private_key"]?.jsonPrimitive?.contentOrNull?.ifEmpty { null } ?: provider.privateKey,
                )
            )
            toaster.show("Service account imported", type = ToastType.Success)
        } catch (e: Exception) {
            toaster.show("Failed to import: ${e.message}", type = ToastType.Error)
        }
    }

    OutlinedButton(
        onClick = { serviceAccountJsonLauncher.launch(arrayOf("application/json", "*/*")) },
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(stringResource(R.string.setting_provider_page_import_service_account_json))
    }

    OutlinedTextField(
        value = provider.serviceAccountEmail,
        onValueChange = { onEdit(provider.copy(serviceAccountEmail = it.trim())) },
        label = { Text(stringResource(R.string.setting_provider_page_service_account_email)) },
        modifier = Modifier.fillMaxWidth(),
    )

    var privateKeyVisible by remember { mutableStateOf(false) }
    OutlinedTextField(
        value = provider.privateKey,
        onValueChange = { onEdit(provider.copy(privateKey = it.trim())) },
        label = { Text(stringResource(R.string.setting_provider_page_private_key)) },
        modifier = Modifier.fillMaxWidth(),
        maxLines = 6,
        minLines = 3,
        textStyle = MaterialTheme.typography.bodySmall.copy(
            fontFamily = JetbrainsMono,
            fontFeatureSettings = LocalSettings.current.displaySetting.enableCodeLigatures.codeFontFeatureSettings,
        ),
        visualTransformation = if (privateKeyVisible) VisualTransformation.None else PasswordVisualTransformation(),
        trailingIcon = {
            IconButton(onClick = { privateKeyVisible = !privateKeyVisible }) {
                Icon(
                    if (privateKeyVisible) HugeIcons.ViewOff else HugeIcons.View,
                    contentDescription = null,
                )
            }
        },
    )

    OutlinedTextField(
        value = provider.location,
        onValueChange = { onEdit(provider.copy(location = it.trim())) },
        label = { Text(stringResource(R.string.setting_provider_page_location)) },
        modifier = Modifier.fillMaxWidth(),
    )

    OutlinedTextField(
        value = provider.projectId,
        onValueChange = { onEdit(provider.copy(projectId = it.trim())) },
        label = { Text(stringResource(R.string.setting_provider_page_project_id)) },
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun ModelPickerSheet(
    models: List<Model>,
    selectedModels: List<Model>,
    onModelSelected: (Model) -> Unit,
    onModelDeselected: (Model) -> Unit,
    onAllModelSelected: (List<Model>) -> Unit,
    onAllModelDeselected: (List<Model>) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberBottomSheetState(
            initialValue = SheetValue.Hidden,
            enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded)
        ),
    ) {
        var filterText by remember { mutableStateOf("") }
        val filterKeywords = filterText.split(" ").filter { it.isNotBlank() }
        val filteredModels = models.fastFilter {
            if (filterKeywords.isEmpty()) {
                true
            } else {
                filterKeywords.all { keyword ->
                    it.modelId.contains(keyword, ignoreCase = true) ||
                        it.displayName.contains(keyword, ignoreCase = true)
                }
            }
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.95f)
                .padding(8.dp)
                .imePadding(),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            // 标题栏和添加所有按钮
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.setting_provider_page_avaliable_models),
                    style = MaterialTheme.typography.titleMedium
                )

                val unselectedCount = filteredModels.count { model ->
                    selectedModels.none { it.modelId == model.modelId }
                }

                TextButton(
                    onClick = {
                        if (unselectedCount > 0) {
                            onAllModelSelected(filteredModels)
                        } else {
                            onAllModelDeselected(filteredModels)
                        }
                    },
                ) {
                    Text(
                        if (unselectedCount > 0) stringResource(
                            R.string.setting_provider_page_select_all,
                            unselectedCount
                        ) else stringResource(R.string.setting_provider_page_deselect_models)
                    )
                }
            }

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(8.dp),
            ) {
                items(filteredModels) {
                    OutlinedItemCard {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                        ) {
                            Surface(
                                color = MaterialTheme.colorScheme.secondaryContainer,
                                shape = MaterialTheme.shapes.small,
                            ) {
                                AutoAIIcon(
                                    it.modelId,
                                    Modifier.size(36.dp)
                                )
                            }
                            Column(
                                verticalArrangement = Arrangement.spacedBy(
                                    4.dp
                                ),
                                modifier = Modifier.weight(1f),
                            ) {
                                Text(
                                    text = it.modelId,
                                    style = MaterialTheme.typography.titleSmall,
                                )

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                                ) {
                                    val modelMeta = remember(it) {
                                        it.copy(
                                            inputModalities = ModelRegistry.MODEL_INPUT_MODALITIES.getData(it.modelId),
                                            outputModalities = ModelRegistry.MODEL_OUTPUT_MODALITIES.getData(it.modelId),
                                            abilities = ModelRegistry.MODEL_ABILITIES.getData(it.modelId),
                                        )
                                    }
                                    ModelModalityTag(
                                        model = modelMeta,
                                    )
                                    ModelAbilityTag(
                                        model = modelMeta,
                                    )
                                }
                            }
                            IconButton(
                                onClick = {
                                    if (selectedModels.any { model -> model.modelId == it.modelId }) {
                                        // 从selectedModels中计算出要删除的model，因为删除需要id匹配，而不是ModelId
                                        onModelDeselected(
                                            selectedModels.firstOrNull { model -> model.modelId == it.modelId }
                                                ?: it
                                        )
                                    } else {
                                        onModelSelected(it)
                                    }
                                }
                            ) {
                                if (selectedModels.any { model -> model.modelId == it.modelId }) {
                                    Icon(HugeIcons.Cancel01, null)
                                } else {
                                    Icon(HugeIcons.Add01, null)
                                }
                            }
                        }
                    }
                }
            }
            OutlinedTextField(
                value = filterText,
                onValueChange = {
                    filterText = it
                },
                label = { Text(stringResource(R.string.setting_provider_page_filter_placeholder)) },
                modifier = Modifier.fillMaxWidth(),
                placeholder = {
                    Text(stringResource(R.string.setting_provider_page_filter_example))
                },
            )
        }
    }
}

@Composable
private fun ModelSettingsForm(
    model: Model,
    onModelChange: (Model) -> Unit,
    isEdit: Boolean,
    parentProvider: ProviderSetting? = null,
    onMigrateModelId: ((Model, Model) -> Unit)? = null,
    onDeleteModel: (() -> Unit)? = null,
) {
    val pagerState = rememberPagerState { 3 }
    val scope = rememberCoroutineScope()
    val settingsStore = koinInject<me.rerere.rikkahub.data.datastore.SettingsStore>()
    val settings by settingsStore.settingsFlow.collectAsStateWithLifecycle()
    val conversationRepository = koinInject<me.rerere.rikkahub.data.repository.ConversationRepository>()
    val navController = LocalNavController.current
    var migrationTarget by remember { mutableStateOf<Model?>(null) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var referencedMessageCount by remember { mutableStateOf(0) }
    val migrationModelListState = rememberModelListState(
        modelId = null,
        providers = settings.providers,
        type = model.type,
    )

    LaunchedEffect(model.id, isEdit) {
        if (isEdit) {
            referencedMessageCount = conversationRepository.countMessagesByModel(model.id)
        }
    }

    ModelListSheet(
        state = migrationModelListState,
        onSelect = { targetModel ->
            if (targetModel.id != model.id) {
                migrationTarget = targetModel
            }
        },
    )

    fun setModelId(id: String) {
        val inputModality = ModelRegistry.MODEL_INPUT_MODALITIES.getData(id)
        val outputModality = ModelRegistry.MODEL_OUTPUT_MODALITIES.getData(id)
        val abilities = ModelRegistry.MODEL_ABILITIES.getData(id)
        onModelChange(
            model.copy(
                modelId = id,
                displayName = id,
                inputModalities = inputModality,
                outputModalities = outputModality,
                abilities = abilities
            )
        )
    }

    Column {
        SecondaryTabRow(
            selectedTabIndex = pagerState.currentPage,
            containerColor = Color.Transparent,
        ) {
            Tab(
                selected = pagerState.currentPage == 0,
                onClick = {
                    scope.launch {
                        pagerState.animateScrollToPage(0)
                    }
                },
                text = { Text(stringResource(R.string.setting_provider_page_basic_settings)) }
            )
            Tab(
                selected = pagerState.currentPage == 1,
                onClick = {
                    scope.launch {
                        pagerState.animateScrollToPage(1)
                    }
                },
                text = { Text(stringResource(R.string.setting_provider_page_advanced_settings)) }
            )
            Tab(
                selected = pagerState.currentPage == 2,
                onClick = {
                    scope.launch {
                        pagerState.animateScrollToPage(2)
                    }
                },
                text = { Text(stringResource(R.string.setting_page_built_in_tools)) }
            )
        }

        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxWidth()
        ) { page ->
            when (page) {
                0 -> {
                    // 基本设置页面
                    Column(
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(vertical = 16.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        CardGroup(
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            item(
                                supportingContent = {
                                    Column(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalArrangement = Arrangement.spacedBy(8.dp),
                                    ) {
                                        OutlinedTextField(
                                            value = model.modelId,
                                            onValueChange = {
                                                if (!isEdit) {
                                                    setModelId(it.trim())
                                                }
                                            },
                                            label = { Text(stringResource(R.string.setting_provider_page_model_id)) },
                                            modifier = Modifier.fillMaxWidth(),
                                            placeholder = {
                                                if (!isEdit) {
                                                    Text(stringResource(R.string.setting_provider_page_model_id_placeholder))
                                                }
                                            },
                                            enabled = !isEdit
                                        )

                                        OutlinedTextField(
                                            value = model.displayName,
                                            onValueChange = {
                                                onModelChange(model.copy(displayName = it.trim()))
                                            },
                                            label = { Text(stringResource(if (isEdit) R.string.setting_provider_page_model_name else R.string.setting_provider_page_model_display_name)) },
                                            modifier = Modifier.fillMaxWidth(),
                                            placeholder = {
                                                if (!isEdit) {
                                                    Text(stringResource(R.string.setting_provider_page_model_display_name_placeholder))
                                                }
                                            }
                                        )
                                    }
                                },
                                headlineContent = {},
                            )

                            item(
                                supportingContent = {
                                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        ModelTypeSelector(
                                            selectedType = model.type,
                                            onTypeSelected = {
                                                onModelChange(model.copy(type = it))
                                            }
                                        )
                                    }
                                },
                                headlineContent = {},
                            )

                            if (model.type == ModelType.CHAT) {
                                item(
                                    supportingContent = {
                                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                            ModelInputModalitySelector(
                                                inputModalities = model.inputModalities,
                                                onUpdateInputModalities = {
                                                    onModelChange(model.copy(inputModalities = it))
                                                }
                                            )
                                        }
                                    },
                                    headlineContent = {},
                                )

                                item(
                                    supportingContent = {
                                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                            ModelOutputModalitySelector(
                                                outputModalities = model.outputModalities,
                                                onUpdateOutputModalities = {
                                                    onModelChange(model.copy(outputModalities = it))
                                                }
                                            )
                                        }
                                    },
                                    headlineContent = {},
                                )

                                item(
                                    supportingContent = {
                                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                            ModalAbilitySelector(
                                                abilities = model.abilities,
                                                onUpdateAbilities = {
                                                    onModelChange(model.copy(abilities = it))
                                                }
                                            )
                                        }
                                    },
                                    headlineContent = {},
                                )
                            }

                            FormItem(
                                label = {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(stringResource(R.string.setting_provider_page_set_price))
                                        Text(
                                            text = stringResource(R.string.setting_provider_page_price_unit),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                },
                                tail = {
                                    Switch(
                                        checked = model.price != null,
                                        onCheckedChange = { enabled ->
                                            onModelChange(model.copy(price = if (enabled) ModelPrice() else null))
                                        },
                                    )
                                },
                            )

                            model.price?.let { price ->
                                item(
                                    supportingContent = {
                                        Column(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalArrangement = Arrangement.spacedBy(8.dp),
                                        ) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            ) {
                                                ModelPriceField(
                                                    label = stringResource(R.string.setting_provider_page_input_price),
                                                    value = price.input,
                                                    onValueChange = { onModelChange(model.copy(price = price.copy(input = it))) },
                                                    modifier = Modifier.weight(1f),
                                                )
                                                ModelPriceField(
                                                    label = stringResource(R.string.setting_provider_page_output_price),
                                                    value = price.output,
                                                    onValueChange = { onModelChange(model.copy(price = price.copy(output = it))) },
                                                    modifier = Modifier.weight(1f),
                                                )
                                            }
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            ) {
                                                ModelPriceField(
                                                    label = stringResource(R.string.setting_provider_page_cache_read_price),
                                                    value = price.cacheRead,
                                                    onValueChange = { onModelChange(model.copy(price = price.copy(cacheRead = it))) },
                                                    modifier = Modifier.weight(1f),
                                                )
                                                ModelPriceField(
                                                    label = stringResource(R.string.setting_provider_page_cache_write_price),
                                                    value = price.cacheWrite,
                                                    onValueChange = { onModelChange(model.copy(price = price.copy(cacheWrite = it))) },
                                                    modifier = Modifier.weight(1f),
                                                )
                                            }
                                            Text(
                                                text = stringResource(R.string.setting_provider_page_price_formula_without_cache),
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                            Text(
                                                text = stringResource(R.string.setting_provider_page_price_formula_with_cache),
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        }
                                    },
                                    headlineContent = {},
                                )
                            }
                        }

                        if (isEdit && onDeleteModel != null) {
                            Button(
                                onClick = {
                                    scope.launch {
                                        referencedMessageCount = conversationRepository.countMessagesByModel(model.id)
                                        showDeleteDialog = true
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.error,
                                    contentColor = MaterialTheme.colorScheme.onError,
                                ),
                            ) {
                                Icon(HugeIcons.Delete01, contentDescription = null)
                                Spacer(Modifier.size(8.dp))
                                Text(stringResource(R.string.setting_provider_page_delete_model))
                            }
                        }
                    }
                }

                1 -> {
                    // 高级设置页面
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(vertical = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        CardGroup(
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            item(
                                supportingContent = {
                                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Text(
                                            text = stringResource(R.string.setting_provider_page_provider_override),
                                            style = MaterialTheme.typography.titleMedium,
                                        )
                                        ProviderOverrideSettings(
                                            providerOverride = model.providerOverwrite,
                                            onUpdateProviderOverride = { providerOverride ->
                                                onModelChange(model.copy(providerOverwrite = providerOverride))
                                            },
                                            parentProvider = parentProvider
                                        )
                                    }
                                },
                                headlineContent = {},
                            )
                        }

                        CardGroup(
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            item(
                                supportingContent = {
                                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Text(
                                            text = stringResource(R.string.assistant_page_tab_request),
                                            style = MaterialTheme.typography.titleMedium,
                                        )
                                        CustomHeaders(
                                            headers = model.customHeaders,
                                            onUpdate = { headers ->
                                                onModelChange(model.copy(customHeaders = headers))
                                            }
                                        )
                                        CustomBodies(
                                            customBodies = model.customBodies,
                                            onUpdate = { bodies ->
                                                onModelChange(model.copy(customBodies = bodies))
                                            }
                                        )
                                    }
                                },
                                headlineContent = {},
                            )
                        }

                        if (isEdit && onMigrateModelId != null) {
                            CardGroup(
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                item(
                                    supportingContent = {
                                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                            Text(
                                                text = stringResource(R.string.setting_provider_page_migrate_id),
                                                style = MaterialTheme.typography.titleMedium,
                                            )
                                            Text(
                                                text = stringResource(R.string.setting_provider_page_migrate_id_desc),
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                            Button(
                                                onClick = migrationModelListState::open,
                                                enabled = referencedMessageCount > 0,
                                                modifier = Modifier.fillMaxWidth(),
                                            ) {
                                                Text(stringResource(R.string.model_list_select_model))
                                            }
                                            if (referencedMessageCount > 0) {
                                                val messageCountText = stringResource(
                                                    R.string.setting_provider_page_delete_model_referenced_messages,
                                                    referencedMessageCount,
                                                )
                                                Text(
                                                    buildAnnotatedString {
                                                        append(
                                                            stringResource(
                                                                R.string.setting_provider_page_migrate_id_current_messages_prefix
                                                            )
                                                        )
                                                withLink(
                                                    LinkAnnotation.Clickable(
                                                        tag = "search_model_messages",
                                                        styles = TextLinkStyles(
                                                            style = SpanStyle(
                                                                color = MaterialTheme.colorScheme.primary,
                                                                textDecoration = TextDecoration.Underline,
                                                            )
                                                        ),
                                                        linkInteractionListener = {
                                                            navController.navigate(
                                                                Screen.MessageSearch(model.id.toString())
                                                            )
                                                        },
                                                    )
                                                ) {
                                                    append(messageCountText)
                                                }
                                            },
                                            style = MaterialTheme.typography.bodyMedium,
                                        )
                                    } else {
                                        Text(
                                            text = stringResource(
                                                R.string.setting_provider_page_migrate_id_no_messages
                                            ),
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                        }
                                    },
                                    headlineContent = {},
                                )
                            }
                        }
                    }
                }

                2 -> {
                    // 内置工具页面
                    BuiltInToolsSettings(
                        tools = model.tools,
                        onUpdateTools = { tools ->
                            onModelChange(model.copy(tools = tools))
                        }
                    )
                }
            }
        }
    }

    migrationTarget?.let { targetModel ->
        AlertDialog(
            containerColor = MaterialTheme.colorScheme.surface,
            onDismissRequest = { migrationTarget = null },
            title = { Text(stringResource(R.string.setting_provider_page_migrate_id_confirm_title)) },
            text = {
                Text(
                    stringResource(
                        R.string.setting_provider_page_migrate_id_confirm_desc,
                        model.displayName,
                        model.findProvider(settings.providers, checkOverwrite = false)?.name
                            ?: parentProvider?.name.orEmpty(),
                        targetModel.displayName,
                        targetModel.findProvider(settings.providers, checkOverwrite = false)?.name.orEmpty(),
                    )
                )
            },
            dismissButton = {
                TextButton(onClick = { migrationTarget = null }) {
                    Text(stringResource(R.string.common_cancel))
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        migrationTarget = null
                        onMigrateModelId?.invoke(model, targetModel)
                    }
                ) {
                    Text(stringResource(R.string.common_confirm_action))
                }
            },
        )
    }

    if (showDeleteDialog) {
        val providerName = (model.providerOverwrite ?: parentProvider)?.name.orEmpty()
        val referencedMessagesLink = stringResource(
            R.string.setting_provider_page_delete_model_referenced_messages,
            referencedMessageCount,
        )
        val warningSuffix = stringResource(R.string.setting_provider_page_delete_model_warning_suffix)
        val migrateLink = stringResource(R.string.setting_provider_page_delete_model_migrate_link)
        val linkStyle = TextLinkStyles(
            style = SpanStyle(
                color = MaterialTheme.colorScheme.primary,
                textDecoration = TextDecoration.Underline,
            )
        )
        AlertDialog(
            containerColor = MaterialTheme.colorScheme.surface,
            onDismissRequest = { showDeleteDialog = false },
            title = {
                Text(stringResource(R.string.setting_provider_page_delete_model))
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        stringResource(
                            R.string.setting_provider_page_delete_model_confirm_named_desc,
                            model.displayName,
                            providerName,
                        )
                    )
                    if (referencedMessageCount > 0) {
                        Text(
                            buildAnnotatedString {
                                withLink(
                                    LinkAnnotation.Clickable(
                                        tag = "search_model_messages",
                                        styles = linkStyle,
                                        linkInteractionListener = {
                                            showDeleteDialog = false
                                            navController.navigate(Screen.MessageSearch(model.id.toString()))
                                        },
                                    )
                                ) {
                                    append(referencedMessagesLink)
                                }
                                append(warningSuffix)
                                withLink(
                                    LinkAnnotation.Clickable(
                                        tag = "migrate_model_id",
                                        styles = linkStyle,
                                        linkInteractionListener = {
                                            showDeleteDialog = false
                                            migrationModelListState.open()
                                        },
                                    )
                                ) {
                                    append(migrateLink)
                                }
                            }
                        )
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text(stringResource(R.string.common_cancel))
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteDialog = false
                        onDeleteModel?.invoke()
                    }
                ) {
                    Text(
                        text = stringResource(R.string.common_delete),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            },
        )
    }
}

@Composable
private fun ModelPriceField(
    label: String,
    value: Double,
    onValueChange: (Double) -> Unit,
    modifier: Modifier = Modifier,
) {
    var text by remember { mutableStateOf(value.toString()) }
    OutlinedTextField(
        value = text,
        onValueChange = { newValue ->
            if (newValue.matches(Regex("\\d*(\\.\\d*)?"))) {
                text = newValue
                onValueChange(newValue.toDoubleOrNull() ?: 0.0)
            }
        },
        label = { Text(label) },
        prefix = { Text("\$") },
        suffix = { Text("/M") },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        singleLine = true,
        modifier = modifier.fillMaxWidth(),
    )
}

@Composable
private fun ModelTypeSelector(
    selectedType: ModelType,
    onTypeSelected: (ModelType) -> Unit
) {
    Text(
        stringResource(R.string.setting_provider_page_model_type),
        style = MaterialTheme.typography.titleSmall
    )
    SingleChoiceSegmentedButtonRow(
        modifier = Modifier.fillMaxWidth()
    ) {
        ModelType.entries.forEachIndexed { index, type ->
            SegmentedButton(
                shape = SegmentedButtonDefaults.itemShape(index, ModelType.entries.size),
                label = {
                    Text(
                        text = stringResource(
                            when (type) {
                                ModelType.CHAT -> R.string.setting_provider_page_chat_model
                                ModelType.EMBEDDING -> R.string.setting_provider_page_embedding_model
                                ModelType.IMAGE -> R.string.setting_provider_page_image_model
                            }
                        )
                    )
                },
                selected = selectedType == type,
                onClick = { onTypeSelected(type) }
            )
        }
    }
}

@Composable
private fun ModelInputModalitySelector(
    inputModalities: List<Modality>,
    onUpdateInputModalities: (List<Modality>) -> Unit
) {
    Text(
        stringResource(R.string.setting_provider_page_input_modality),
        style = MaterialTheme.typography.titleSmall
    )
    MultiChoiceSegmentedButtonRow(
        modifier = Modifier.fillMaxWidth(),
    ) {
        Modality.entries.forEachIndexed { index, modality ->
            SegmentedButton(
                checked = modality in inputModalities,
                shape = SegmentedButtonDefaults.itemShape(index, Modality.entries.size),
                onCheckedChange = {
                    if (it) {
                        onUpdateInputModalities(inputModalities + modality)
                    } else {
                        onUpdateInputModalities(inputModalities - modality)
                    }
                }
            ) {
                Text(
                    text = stringResource(
                        when (modality) {
                            Modality.TEXT -> R.string.setting_provider_page_text
                            Modality.IMAGE -> R.string.setting_provider_page_image
                        }
                    )
                )
            }
        }
    }
}

@Composable
private fun ModelOutputModalitySelector(
    outputModalities: List<Modality>,
    onUpdateOutputModalities: (List<Modality>) -> Unit
) {
    Text(
        stringResource(R.string.setting_provider_page_output_modality),
        style = MaterialTheme.typography.titleSmall
    )
    MultiChoiceSegmentedButtonRow(
        modifier = Modifier.fillMaxWidth(),
    ) {
        Modality.entries.forEachIndexed { index, modality ->
            SegmentedButton(
                checked = modality in outputModalities,
                shape = SegmentedButtonDefaults.itemShape(index, Modality.entries.size),
                onCheckedChange = {
                    if (it) {
                        onUpdateOutputModalities(outputModalities + modality)
                    } else {
                        onUpdateOutputModalities(outputModalities - modality)
                    }
                }
            ) {
                Text(
                    text = stringResource(
                        when (modality) {
                            Modality.TEXT -> R.string.setting_provider_page_text
                            Modality.IMAGE -> R.string.setting_provider_page_image
                        }
                    )
                )
            }
        }
    }
}

@Composable
fun ModalAbilitySelector(
    abilities: List<ModelAbility>,
    onUpdateAbilities: (List<ModelAbility>) -> Unit
) {
    Text(
        stringResource(R.string.setting_provider_page_abilities),
        style = MaterialTheme.typography.titleSmall
    )
    MultiChoiceSegmentedButtonRow(
        modifier = Modifier.fillMaxWidth(),
    ) {
        ModelAbility.entries.forEachIndexed { index, ability ->
            SegmentedButton(
                checked = ability in abilities,
                shape = SegmentedButtonDefaults.itemShape(index, ModelAbility.entries.size),
                onCheckedChange = {
                    if (it) {
                        onUpdateAbilities(abilities + ability)
                    } else {
                        onUpdateAbilities(abilities - ability)
                    }
                },
                label = {
                    Text(
                        text = stringResource(
                            when (ability) {
                                ModelAbility.TOOL -> R.string.setting_provider_page_tool
                                ModelAbility.REASONING -> R.string.setting_provider_page_reasoning
                            }
                        )
                    )
                }
            )
        }
    }
}

@Composable
private fun ModelCard(
    model: Model,
    openInitially: Boolean,
    modifier: Modifier = Modifier,
    onDelete: () -> Unit,
    onEdit: (Model) -> Unit,
    parentProvider: ProviderSetting,
    onMigrateModelId: (Model, Model) -> Unit,
) {
    val dialogState = useEditState<Model> {
        onEdit(it)
    }
    val scope = rememberCoroutineScope()

    LaunchedEffect(openInitially) {
        if (openInitially && !dialogState.isEditing) {
            dialogState.open(model.copy())
        }
    }

    if (dialogState.isEditing) {
        dialogState.currentState?.let { editingModel ->
            val sheetState = rememberBottomSheetState(
                initialValue = SheetValue.Hidden,
                enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded)
            )
            ModalBottomSheet(
                onDismissRequest = {
                    dialogState.dismiss()
                },
                sheetState = sheetState,
                sheetGesturesEnabled = false,
                dragHandle = {
                    IconButton(
                        onClick = {
                            scope.launch {
                                sheetState.hide()
                                dialogState.dismiss()
                            }
                        }
                    ) {
                        Icon(HugeIcons.ArrowDown01, null)
                    }
                },
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .fillMaxHeight(0.95f)
                        .imePadding(),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Text(
                        text = stringResource(R.string.setting_provider_page_edit_model),
                        style = MaterialTheme.typography.titleLarge,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp),
                    )
                    Column(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                    ) {
                        ModelSettingsForm(
                            model = editingModel,
                            onModelChange = { dialogState.currentState = it },
                            isEdit = true,
                            parentProvider = parentProvider,
                            onMigrateModelId = onMigrateModelId,
                            onDeleteModel = {
                                onDelete()
                                dialogState.dismiss()
                            },
                        )
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                    ) {
                        TextButton(
                            onClick = {
                                dialogState.dismiss()
                            },
                        ) {
                            Text(stringResource(R.string.common_cancel))
                        }
                        TextButton(
                            onClick = {
                                if (editingModel.displayName.isNotBlank()) {
                                    dialogState.confirm()
                                }
                            },
                        ) {
                            Text(stringResource(R.string.common_confirm_action))
                        }
                    }
                }
            }
        }
    }

    OutlinedItemCard(
        modifier = modifier,
        onClick = { dialogState.open(model.copy()) },
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                color = MaterialTheme.colorScheme.secondaryContainer,
                shape = MaterialTheme.shapes.small,
            ) {
                AutoAIIcon(
                    name = model.modelId,
                    modifier = Modifier.size(36.dp),
                )
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = model.displayName,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    if (model.providerOverwrite != null) {
                        Tag(type = TagType.INFO) {
                            Text(
                                model.providerOverwrite?.javaClass?.simpleName ?: model.providerOverwrite?.name
                                ?: "ProviderOverwrite"
                            )
                        }
                    }
                    ModelTypeTag(model = model)
                    ModelModalityTag(model = model)
                    ModelAbilityTag(model = model)
                }
            }
        }
    }
}

@Composable
private fun BuiltInToolsSettings(
    tools: Set<BuiltInTools>,
    onUpdateTools: (Set<BuiltInTools>) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = stringResource(R.string.setting_page_built_in_tools),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = stringResource(R.string.setting_page_built_in_tools_desc),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        val availableTools = listOf(
            BuiltInTools.Search to Pair(
                stringResource(R.string.setting_page_built_in_tools_search),
                stringResource(R.string.setting_page_built_in_tools_search_desc)
            ),
            BuiltInTools.UrlContext to Pair(
                stringResource(R.string.setting_page_built_in_tools_url_context),
                stringResource(R.string.setting_page_built_in_tools_url_context_desc)
            ),
            BuiltInTools.ImageGeneration to Pair(
                stringResource(R.string.setting_page_built_in_tools_image_generation),
                stringResource(R.string.setting_page_built_in_tools_image_generation_desc)
            )
        )

        CardGroup(
            modifier = Modifier.fillMaxWidth(),
        ) {
            availableTools.forEach { (tool, info) ->
                val (title, description) = info
                item(
                    headlineContent = { Text(title) },
                    supportingContent = { Text(description) },
                    trailingContent = {
                        Switch(
                            checked = tool in tools,
                            onCheckedChange = { checked ->
                                if (checked) {
                                    onUpdateTools(tools + tool)
                                } else {
                                    onUpdateTools(tools - tool)
                                }
                            }
                        )
                    },
                )
            }
        }
    }
}

@Composable
private fun ProviderOverrideSettings(
    providerOverride: ProviderSetting?,
    onUpdateProviderOverride: (ProviderSetting?) -> Unit,
    parentProvider: ProviderSetting?
) {
    var showProviderConfig by remember { mutableStateOf(false) }
    var editingProvider by remember { mutableStateOf<ProviderSetting?>(null) }

    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (providerOverride != null) {
            OutlinedItemCard(
                modifier = Modifier.fillMaxWidth(),
                onClick = {
                    editingProvider = providerOverride
                    showProviderConfig = true
                },
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, top = 8.dp, end = 0.dp, bottom = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AutoAIIcon(
                            providerOverride.name,
                            modifier = Modifier.size(24.dp)
                        )
                        Text(
                            text = "${providerOverride.name} (Override)",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(
                            onClick = {
                                onUpdateProviderOverride(null)
                            }
                        ) {
                            Icon(HugeIcons.Cancel01, contentDescription = "Remove override")
                        }
                    }
                }
            }
        } else {
            Button(
                onClick = {
                    editingProvider = parentProvider?.copyProvider(
                        id = Uuid.random(),
                        builtIn = false,
                        models = emptyList(), // 这里必须设置为空，不然会导致循环依赖JSON
                        description = {},
                    )
                    showProviderConfig = true
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(HugeIcons.Add01, contentDescription = null)
                Spacer(modifier = Modifier.size(8.dp))
                Text(stringResource(R.string.setting_provider_page_add_provider_override))
            }
        }

        Text(
            text = stringResource(R.string.setting_provider_page_provider_override_desc),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        // Provider configuration modal
        if (showProviderConfig && editingProvider != null) {
            ModalBottomSheet(
                onDismissRequest = {
                    showProviderConfig = false
                    editingProvider = null
                },
                sheetState = rememberBottomSheetState(
                    initialValue = SheetValue.Hidden,
                    enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded)
                )
            ) {
                var internalProvider by remember(editingProvider) { mutableStateOf(editingProvider!!) }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .fillMaxSize()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = stringResource(R.string.setting_provider_page_configure_provider_override),
                        style = MaterialTheme.typography.titleLarge,
                    )

                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        ProviderConfigure(
                            provider = internalProvider,
                            onEdit = { internalProvider = it }
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                    ) {
                        TextButton(
                            onClick = {
                                showProviderConfig = false
                                editingProvider = null
                            },
                        ) {
                            Text(stringResource(R.string.common_cancel))
                        }
                        TextButton(
                            onClick = {
                                onUpdateProviderOverride(internalProvider)
                                showProviderConfig = false
                                editingProvider = null
                            },
                        ) {
                            Text(stringResource(R.string.common_save))
                        }
                    }
                }
            }
        }
    }
}
